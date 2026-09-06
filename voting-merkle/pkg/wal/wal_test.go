package wal

import (
	"bytes"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"testing"
)

func abrir(t *testing.T, caminho string) *Log {
	t.Helper()
	log, err := Open(caminho)
	if err != nil {
		t.Fatalf("abrindo: %v", err)
	}
	t.Cleanup(func() { log.Close() })
	return log
}

func lerTudo(t *testing.T, log *Log) [][]byte {
	t.Helper()
	var saida [][]byte
	if err := log.Replay(func(p []byte) error {
		saida = append(saida, append([]byte(nil), p...))
		return nil
	}); err != nil {
		t.Fatalf("replay: %v", err)
	}
	return saida
}

func TestLogVazioNaoTemRegistros(t *testing.T) {
	log := abrir(t, filepath.Join(t.TempDir(), "x.wal"))

	if !log.Empty() {
		t.Fatal("log recem-criado deveria estar vazio")
	}
	if regs := lerTudo(t, log); len(regs) != 0 {
		t.Fatalf("esperava 0 registros, veio %d", len(regs))
	}
}

func TestRegistrosVoltamNaOrdem(t *testing.T) {
	log := abrir(t, filepath.Join(t.TempDir(), "x.wal"))

	for i := range 100 {
		if err := log.Append([]byte(fmt.Sprintf("registro-%d", i))); err != nil {
			t.Fatalf("append %d: %v", i, err)
		}
	}

	regs := lerTudo(t, log)
	if len(regs) != 100 {
		t.Fatalf("esperava 100 registros, veio %d", len(regs))
	}
	for i, r := range regs {
		if esperado := fmt.Sprintf("registro-%d", i); string(r) != esperado {
			t.Fatalf("registro %d: %q != %q", i, r, esperado)
		}
	}
}

// A identidade e o que amarra um progresso externo a esta copia do estado: precisa
// sobreviver ao restart e sumir junto com o arquivo.
func TestIdentidadeSobreviveAoRestartEMudaComArquivoNovo(t *testing.T) {
	dir := t.TempDir()
	caminho := filepath.Join(dir, "x.wal")

	primeiro := abrir(t, caminho)
	id := primeiro.ID()
	if err := primeiro.Close(); err != nil {
		t.Fatalf("fechando: %v", err)
	}

	segundo := abrir(t, caminho)
	if segundo.ID() != id {
		t.Fatalf("mesmo arquivo deveria manter a identidade: %s != %s", segundo.ID(), id)
	}

	outro := abrir(t, filepath.Join(dir, "y.wal"))
	if outro.ID() == id {
		t.Fatal("arquivo novo deveria ter identidade nova")
	}
}

func TestReabrirEnxergaOQueFoiSincronizado(t *testing.T) {
	caminho := filepath.Join(t.TempDir(), "x.wal")

	log := abrir(t, caminho)
	for _, r := range []string{"a", "bb", "ccc"} {
		if err := log.Append([]byte(r)); err != nil {
			t.Fatalf("append: %v", err)
		}
	}
	if err := log.Close(); err != nil {
		t.Fatalf("fechando: %v", err)
	}

	regs := lerTudo(t, abrir(t, caminho))
	if len(regs) != 3 || string(regs[2]) != "ccc" {
		t.Fatalf("registros nao voltaram: %q", regs)
	}
}

// O rabo torto e o rastro esperado de uma queda no meio de um append: o registro incompleto
// some, e todos os anteriores continuam legiveis.
func TestRaboTortoEDescartadoSemLevarOResto(t *testing.T) {
	caminho := filepath.Join(t.TempDir(), "x.wal")

	log := abrir(t, caminho)
	for _, r := range []string{"primeiro", "segundo", "terceiro"} {
		if err := log.Append([]byte(r)); err != nil {
			t.Fatalf("append: %v", err)
		}
	}
	if err := log.Close(); err != nil {
		t.Fatalf("fechando: %v", err)
	}

	info, err := os.Stat(caminho)
	if err != nil {
		t.Fatalf("stat: %v", err)
	}
	// Corta no meio do ultimo registro, como faria uma queda de energia.
	if err := os.Truncate(caminho, info.Size()-4); err != nil {
		t.Fatalf("truncando: %v", err)
	}

	reaberto := abrir(t, caminho)
	regs := lerTudo(t, reaberto)
	if len(regs) != 2 {
		t.Fatalf("esperava 2 registros integros, veio %d", len(regs))
	}
	if string(regs[0]) != "primeiro" || string(regs[1]) != "segundo" {
		t.Fatalf("registros errados: %q", regs)
	}

	// E o log continua utilizavel: o append seguinte cai depois do ponto de corte.
	if err := reaberto.Append([]byte("depois")); err != nil {
		t.Fatalf("append apos recuperacao: %v", err)
	}
	if regs := lerTudo(t, reaberto); len(regs) != 3 || string(regs[2]) != "depois" {
		t.Fatalf("append apos recuperacao nao apareceu: %q", regs)
	}
}

// Um bit trocado no meio do arquivo nao tem a explicacao do rabo torto: nao pode passar
// como se o registro nao existisse.
func TestBitTrocadoNoMeioViraErro(t *testing.T) {
	caminho := filepath.Join(t.TempDir(), "x.wal")

	log := abrir(t, caminho)
	for _, r := range []string{"aaaaaaaa", "bbbbbbbb", "cccccccc"} {
		if err := log.Append([]byte(r)); err != nil {
			t.Fatalf("append: %v", err)
		}
	}
	if err := log.Close(); err != nil {
		t.Fatalf("fechando: %v", err)
	}

	conteudo, err := os.ReadFile(caminho)
	if err != nil {
		t.Fatalf("lendo: %v", err)
	}
	alvo := bytes.Index(conteudo, []byte("aaaaaaaa"))
	if alvo < 0 {
		t.Fatal("nao achei o primeiro registro no arquivo")
	}
	conteudo[alvo] = 'z'
	if err := os.WriteFile(caminho, conteudo, 0o644); err != nil {
		t.Fatalf("escrevendo: %v", err)
	}

	// A abertura ja recusa: o servico nao sobe servindo provas de um disco que mentiu.
	if _, err := Open(caminho); !errors.Is(err, ErrCorrompido) {
		t.Fatalf("esperava ErrCorrompido, veio %v", err)
	}
}

func TestCabecalhoEstranhoNaoEAbertoComoLog(t *testing.T) {
	caminho := filepath.Join(t.TempDir(), "x.wal")
	if err := os.WriteFile(caminho, bytes.Repeat([]byte("lixo"), 64), 0o644); err != nil {
		t.Fatalf("escrevendo: %v", err)
	}

	if _, err := Open(caminho); !errors.Is(err, ErrCorrompido) {
		t.Fatalf("esperava ErrCorrompido, veio %v", err)
	}
}

func TestReplayInterrompePeloErroDeQuemLe(t *testing.T) {
	log := abrir(t, filepath.Join(t.TempDir(), "x.wal"))
	for i := range 10 {
		if err := log.Append([]byte{byte(i)}); err != nil {
			t.Fatalf("append: %v", err)
		}
	}

	parar := errors.New("chega")
	vistos := 0
	err := log.Replay(func([]byte) error {
		vistos++
		if vistos == 3 {
			return parar
		}
		return nil
	})
	if !errors.Is(err, parar) {
		t.Fatalf("esperava o erro de quem le, veio %v", err)
	}
	if vistos != 3 {
		t.Fatalf("replay deveria ter parado em 3, viu %d", vistos)
	}
}
