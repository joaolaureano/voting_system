package journal

import (
	"bytes"
	"fmt"
	"hash/crc32"
	"os"
	"path/filepath"
	"testing"

	"github.com/joaolaureano/voting_system/voting-merkle/pkg/checkpoint"
	"github.com/joaolaureano/voting_system/voting-merkle/pkg/merkle"
)

func folha(n int) checkpoint.Leaf {
	chave := fmt.Sprintf("recibo-%04d", n)
	return checkpoint.Leaf{Key: chave, Data: []byte(chave)}
}

func abrir(t *testing.T, caminho string) *Journal {
	t.Helper()
	j, err := Open(caminho)
	if err != nil {
		t.Fatalf("abrindo diario: %v", err)
	}
	return j
}

// cadeia abre o diario e retoma a cadeia dele, como faz a subida do servico.
func cadeia(t *testing.T, caminho string) (*checkpoint.Log, *Journal, []*checkpoint.Checkpoint) {
	t.Helper()
	j := abrir(t, caminho)
	log, pendentes, err := checkpoint.NewLogWithStore(j)
	if err != nil {
		j.Close()
		t.Fatalf("retomando: %v", err)
	}
	return log, j, pendentes
}

// selar preenche um lote inteiro e devolve o checkpoint resultante.
func selar(t *testing.T, log *checkpoint.Log, lote string, folhas []checkpoint.Leaf) *checkpoint.Checkpoint {
	t.Helper()
	for _, f := range folhas {
		if _, err := log.Add(lote, f); err != nil {
			t.Fatalf("Add(%s, %s): %v", lote, f.Key, err)
		}
	}
	selados, err := log.Expect(lote, len(folhas))
	if err != nil {
		t.Fatalf("Expect(%s): %v", lote, err)
	}
	if len(selados) != 1 {
		t.Fatalf("esperava selar 1 lote, selou %d", len(selados))
	}
	return selados[0]
}

func TestCadeiaSobreviveAoRestart(t *testing.T) {
	caminho := filepath.Join(t.TempDir(), "chain.wal")

	log, diario, _ := cadeia(t, caminho)
	primeiro := selar(t, log, "j1", []checkpoint.Leaf{folha(1), folha(2), folha(3)})
	segundo := selar(t, log, "j2", []checkpoint.Leaf{folha(4), folha(5)})
	if err := diario.Close(); err != nil {
		t.Fatalf("fechando: %v", err)
	}

	retomada, diario2, pendentes := cadeia(t, caminho)
	defer diario2.Close()

	if len(pendentes) != 0 {
		t.Fatalf("nada estava por selar, veio %d", len(pendentes))
	}

	cadeiaRetomada := retomada.Checkpoints()
	if len(cadeiaRetomada) != 2 {
		t.Fatalf("esperava 2 checkpoints, veio %d", len(cadeiaRetomada))
	}
	for i, esperado := range []*checkpoint.Checkpoint{primeiro, segundo} {
		vindo := cadeiaRetomada[i]
		if vindo.BatchID != esperado.BatchID || vindo.Sequence != esperado.Sequence || vindo.Size != esperado.Size {
			t.Fatalf("checkpoint %d nao confere: %+v", i, vindo)
		}
		if !bytes.Equal(vindo.Root, esperado.Root) || !bytes.Equal(vindo.Hash, esperado.Hash) {
			t.Fatalf("checkpoint %d: raiz ou elo divergem apos a retomada", i)
		}
		if !vindo.SealedAt.Equal(esperado.SealedAt) {
			t.Fatalf("checkpoint %d: instante do selo virou o da retomada", i)
		}
	}
	if err := checkpoint.VerifyChain(cadeiaRetomada); err != nil {
		t.Fatalf("cadeia retomada nao fecha: %v", err)
	}
}

// O que o eleitor faz com o recibo tem de continuar funcionando depois do restart - e essa,
// e nao a igualdade dos structs, e a razao de persistir as folhas.
func TestProvaContinuaValidaDepoisDoRestart(t *testing.T) {
	caminho := filepath.Join(t.TempDir(), "chain.wal")

	log, diario, _ := cadeia(t, caminho)
	folhas := make([]checkpoint.Leaf, 9)
	for i := range folhas {
		folhas[i] = folha(i)
	}
	selar(t, log, "j1", folhas)
	diario.Close()

	retomada, diario2, _ := cadeia(t, caminho)
	defer diario2.Close()

	alvo := folha(4)
	ponto, prova, err := retomada.Lookup(alvo.Key)
	if err != nil {
		t.Fatalf("Lookup apos retomada: %v", err)
	}
	if !merkle.Verify(ponto.Root, alvo.Data, prova) {
		t.Fatal("prova reconstruida do disco nao confere com a raiz")
	}
}

// A cadeia continua de onde parou: um lote selado depois do restart tem de se encadear no
// ultimo elo da execucao anterior.
func TestCadeiaContinuaDoUltimoElo(t *testing.T) {
	caminho := filepath.Join(t.TempDir(), "chain.wal")

	log, diario, _ := cadeia(t, caminho)
	anterior := selar(t, log, "j1", []checkpoint.Leaf{folha(1), folha(2)})
	diario.Close()

	retomada, diario2, _ := cadeia(t, caminho)
	defer diario2.Close()

	novo := selar(t, retomada, "j2", []checkpoint.Leaf{folha(3)})
	if novo.Sequence != 1 {
		t.Fatalf("sequencia deveria continuar em 1, veio %d", novo.Sequence)
	}
	if !bytes.Equal(novo.Previous, anterior.Hash) {
		t.Fatal("o lote novo nao se encadeou no ultimo elo gravado")
	}
	if err := checkpoint.VerifyChain(retomada.Checkpoints()); err != nil {
		t.Fatalf("cadeia nao fecha: %v", err)
	}
}

// Folhas gravadas sem o selo correspondente sao o rastro de uma queda entre o marcador e o
// fechamento. A retomada tem de fechar o lote, e dizer que ele precisa ser publicado.
func TestLoteCompletoSemSeloEFechadoNaRetomada(t *testing.T) {
	caminho := filepath.Join(t.TempDir(), "chain.wal")

	// Escreve a mao o que o servico teria escrito antes de cair: as folhas e o marcador,
	// sem o registro de selo.
	diario := abrir(t, caminho)
	for i := range 3 {
		f := folha(i)
		if err := diario.Append(checkpoint.Record{Kind: checkpoint.KindLeaf, BatchID: "j1", Leaf: f}); err != nil {
			t.Fatalf("append folha: %v", err)
		}
	}
	if err := diario.Append(checkpoint.Record{Kind: checkpoint.KindMarker, BatchID: "j1", Count: 3}); err != nil {
		t.Fatalf("append marcador: %v", err)
	}
	if err := diario.Close(); err != nil {
		t.Fatalf("fechando: %v", err)
	}

	retomada, diario2, pendentes := cadeia(t, caminho)
	defer diario2.Close()

	if len(pendentes) != 1 || pendentes[0].BatchID != "j1" {
		t.Fatalf("esperava um selo recuperado para j1, veio %+v", pendentes)
	}
	if len(retomada.Checkpoints()) != 1 {
		t.Fatalf("o lote nao foi selado na retomada")
	}

	// E o selo agora esta no diario: uma segunda retomada nao pode reapresenta-lo como
	// pendente de publicacao.
	diario2.Close()
	terceira, diario3, aindaPendentes := cadeia(t, caminho)
	defer diario3.Close()
	if len(aindaPendentes) != 0 {
		t.Fatalf("o selo recuperado nao foi gravado: %d pendentes", len(aindaPendentes))
	}
	if len(terceira.Checkpoints()) != 1 {
		t.Fatalf("esperava 1 checkpoint, veio %d", len(terceira.Checkpoints()))
	}
}

// A retomada recalcula a raiz das folhas gravadas. Uma folha adulterada no disco tem de
// aparecer como divergencia na partida, e nao virar uma prova errada servida com confianca.
func TestFolhaAdulteradaNoDiscoImpedeARetomada(t *testing.T) {
	caminho := filepath.Join(t.TempDir(), "chain.wal")

	log, diario, _ := cadeia(t, caminho)
	selar(t, log, "j1", []checkpoint.Leaf{folha(1), folha(2), folha(3)})
	diario.Close()

	// Troca o conteudo de uma folha mantendo o tamanho, e recalcula o CRC do registro para
	// que a adulteracao passe pelo wal - o cenario em que so a cadeia de hashes denuncia.
	conteudo, err := os.ReadFile(caminho)
	if err != nil {
		t.Fatalf("lendo: %v", err)
	}
	alvo := bytes.Index(conteudo, []byte("recibo-0002"))
	if alvo < 0 {
		t.Fatal("nao achei a folha no arquivo")
	}
	copy(conteudo[alvo:], []byte("recibo-9999"))
	recalcularCRCs(t, conteudo)
	if err := os.WriteFile(caminho, conteudo, 0o644); err != nil {
		t.Fatalf("escrevendo: %v", err)
	}

	j := abrir(t, caminho)
	defer j.Close()
	if _, _, err := checkpoint.NewLogWithStore(j); err == nil {
		t.Fatal("a retomada aceitou uma folha que nao produz a raiz selada")
	}
}

func TestIdentidadeDoDiarioAcompanhaOArquivo(t *testing.T) {
	dir := t.TempDir()

	primeiro := abrir(t, filepath.Join(dir, "a.wal"))
	id := primeiro.ID()
	primeiro.Close()

	reaberto := abrir(t, filepath.Join(dir, "a.wal"))
	defer reaberto.Close()
	if reaberto.ID() != id {
		t.Fatal("o mesmo arquivo deveria manter a identidade")
	}

	outro := abrir(t, filepath.Join(dir, "b.wal"))
	defer outro.Close()
	if outro.ID() == id {
		t.Fatal("um arquivo novo deveria ter identidade nova")
	}
}

func TestCodificacaoIdaEVolta(t *testing.T) {
	caminho := filepath.Join(t.TempDir(), "chain.wal")
	hash := func(b byte) []byte { return bytes.Repeat([]byte{b}, merkle.HashSize) }

	originais := []checkpoint.Record{
		{Kind: checkpoint.KindLeaf, BatchID: "j1", Leaf: checkpoint.Leaf{Key: "k", Data: []byte("dados")}},
		{Kind: checkpoint.KindLeaf, BatchID: "j1", Leaf: checkpoint.Leaf{Key: "vazia", Data: nil}},
		{Kind: checkpoint.KindMarker, BatchID: "j1", Count: 0},
		{Kind: checkpoint.KindMarker, BatchID: "j1", Count: 100000},
		{Kind: checkpoint.KindSealed, BatchID: "j1", Sealed: checkpoint.Sealed{
			Sequence: 7, Size: 42, Root: hash(1), Previous: hash(2), Hash: hash(3),
		}},
	}

	diario := abrir(t, caminho)
	for _, rec := range originais {
		if err := diario.Append(rec); err != nil {
			t.Fatalf("append: %v", err)
		}
	}

	var voltaram []checkpoint.Record
	if err := diario.Replay(func(rec checkpoint.Record) error {
		voltaram = append(voltaram, rec)
		return nil
	}); err != nil {
		t.Fatalf("replay: %v", err)
	}
	diario.Close()

	if len(voltaram) != len(originais) {
		t.Fatalf("esperava %d registros, veio %d", len(originais), len(voltaram))
	}
	for i, rec := range voltaram {
		esperado := originais[i]
		if rec.Kind != esperado.Kind || rec.BatchID != esperado.BatchID {
			t.Fatalf("registro %d: tipo ou lote divergem: %+v", i, rec)
		}
		switch rec.Kind {
		case checkpoint.KindLeaf:
			if rec.Leaf.Key != esperado.Leaf.Key || !bytes.Equal(rec.Leaf.Data, esperado.Leaf.Data) {
				t.Fatalf("registro %d: folha divergiu: %+v", i, rec.Leaf)
			}
		case checkpoint.KindMarker:
			if rec.Count != esperado.Count {
				t.Fatalf("registro %d: contagem %d != %d", i, rec.Count, esperado.Count)
			}
		case checkpoint.KindSealed:
			if rec.Sealed.Sequence != esperado.Sealed.Sequence || rec.Sealed.Size != esperado.Sealed.Size {
				t.Fatalf("registro %d: selo divergiu: %+v", i, rec.Sealed)
			}
			if !bytes.Equal(rec.Sealed.Root, esperado.Sealed.Root) ||
				!bytes.Equal(rec.Sealed.Previous, esperado.Sealed.Previous) ||
				!bytes.Equal(rec.Sealed.Hash, esperado.Sealed.Hash) {
				t.Fatalf("registro %d: hashes do selo divergiram", i)
			}
		}
	}
}

// recalcularCRCs reescreve o CRC de cada registro do arquivo, para que uma adulteracao de
// conteudo passe pela verificacao do wal e chegue a cadeia de hashes.
func recalcularCRCs(t *testing.T, conteudo []byte) {
	t.Helper()
	const cabecalho = 32
	off := cabecalho
	for off+8 <= len(conteudo) {
		tamanho := int(be32(conteudo[off:]))
		inicio := off + 8
		if inicio+tamanho > len(conteudo) {
			t.Fatalf("registro em %d passa do fim do arquivo", off)
		}
		putBE32(conteudo[off+4:], crcDe(conteudo[inicio:inicio+tamanho]))
		off = inicio + tamanho
	}
}

func crcDe(b []byte) uint32 { return crc32.Checksum(b, crc32.MakeTable(crc32.Castagnoli)) }

func be32(b []byte) uint32 {
	return uint32(b[0])<<24 | uint32(b[1])<<16 | uint32(b[2])<<8 | uint32(b[3])
}

func putBE32(b []byte, v uint32) {
	b[0], b[1], b[2], b[3] = byte(v>>24), byte(v>>16), byte(v>>8), byte(v)
}
