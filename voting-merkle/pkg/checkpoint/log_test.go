package checkpoint

import (
	"bytes"
	"errors"
	"fmt"
	"math/rand"
	"sync"
	"testing"

	"github.com/joaolaureano/voting_system/voting-merkle/pkg/merkle"
)

func folha(n int) Leaf {
	chave := fmt.Sprintf("recibo-%04d", n)
	return Leaf{Key: chave, Data: []byte(chave)}
}

func addTodas(t *testing.T, log *Log, lote string, folhas []Leaf) []*Checkpoint {
	t.Helper()
	var selados []*Checkpoint
	for _, f := range folhas {
		novos, err := log.Add(lote, f)
		if err != nil {
			t.Fatalf("Add(%s, %s): %v", lote, f.Key, err)
		}
		selados = append(selados, novos...)
	}
	return selados
}

func TestSelaQuandoCompletaAContagemDoMarcador(t *testing.T) {
	log := NewLog()

	if selados := addTodas(t, log, "j1", []Leaf{folha(1), folha(2)}); len(selados) != 0 {
		t.Fatal("selou sem marcador")
	}

	selados, err := log.Expect("j1", 3)
	if err != nil || len(selados) != 0 {
		t.Fatalf("selou faltando uma folha: %v %d", err, len(selados))
	}

	selados, err = log.Add("j1", folha(3))
	if err != nil {
		t.Fatal(err)
	}
	if len(selados) != 1 || selados[0].Size != 3 {
		t.Fatalf("esperava um checkpoint de 3 folhas, veio %+v", selados)
	}
}

func TestMarcadorPodeChegarAntesDasFolhas(t *testing.T) {
	log := NewLog()

	if _, err := log.Expect("j1", 2); err != nil {
		t.Fatal(err)
	}
	selados := addTodas(t, log, "j1", []Leaf{folha(1), folha(2)})

	if len(selados) != 1 {
		t.Fatalf("esperava selar ao completar, veio %d", len(selados))
	}
}

// A ordem de chegada das folhas varia entre execucoes porque elas vem de varias particoes.
// A raiz nao pode variar junto.
func TestAOrdemDeChegadaNaoMudaARaiz(t *testing.T) {
	folhas := make([]Leaf, 50)
	for i := range folhas {
		folhas[i] = folha(i)
	}

	primeira := NewLog()
	primeira.Expect("j1", len(folhas))
	addTodas(t, primeira, "j1", folhas)

	embaralhadas := append([]Leaf{}, folhas...)
	rand.New(rand.NewSource(7)).Shuffle(len(embaralhadas), func(i, j int) {
		embaralhadas[i], embaralhadas[j] = embaralhadas[j], embaralhadas[i]
	})

	segunda := NewLog()
	segunda.Expect("j1", len(embaralhadas))
	addTodas(t, segunda, "j1", embaralhadas)

	if !bytes.Equal(primeira.Head().Root, segunda.Head().Root) {
		t.Fatal("a raiz mudou com a ordem de chegada das folhas")
	}
}

func TestFolhaRepetidaNaoInflaOLote(t *testing.T) {
	log := NewLog()
	log.Expect("j1", 2)

	addTodas(t, log, "j1", []Leaf{folha(1), folha(1), folha(1)})
	if log.Head() != nil {
		t.Fatal("reentrega da mesma folha completou o lote")
	}

	selados, err := log.Add("j1", folha(2))
	if err != nil || len(selados) != 1 || selados[0].Size != 2 {
		t.Fatalf("esperava um lote de 2, veio %v %+v", err, selados)
	}
}

func TestFolhaAMaisDoQueOMarcadorAnunciouEErro(t *testing.T) {
	log := NewLog()
	log.Expect("j1", 1)
	addTodas(t, log, "j1", []Leaf{folha(1)})

	if _, err := log.Add("j1", folha(2)); err == nil {
		t.Fatal("aceitou folha para um lote ja selado")
	}
}

// A cadeia segue a ordem dos marcadores. Se o lote seguinte fica pronto antes do anterior,
// ele espera - do contrario a sequencia dependeria de quem terminou primeiro.
func TestOsLotesSaoSeladosNaOrdemDosMarcadores(t *testing.T) {
	log := NewLog()
	log.Expect("j1", 2)
	log.Expect("j2", 1)

	selados := addTodas(t, log, "j2", []Leaf{folha(10)})
	if len(selados) != 0 {
		t.Fatal("j2 furou a fila enquanto j1 estava incompleto")
	}

	selados = addTodas(t, log, "j1", []Leaf{folha(1), folha(2)})
	if len(selados) != 2 {
		t.Fatalf("esperava j1 e j2 selados juntos, veio %d", len(selados))
	}
	if selados[0].BatchID != "j1" || selados[1].BatchID != "j2" {
		t.Fatalf("ordem errada: %s, %s", selados[0].BatchID, selados[1].BatchID)
	}
	if selados[0].Sequence != 0 || selados[1].Sequence != 1 {
		t.Fatal("sequencia fora de ordem")
	}
}

func TestACadeiaEncadeiaEVerifica(t *testing.T) {
	log := NewLog()
	for i := 0; i < 5; i++ {
		lote := fmt.Sprintf("j%d", i)
		log.Expect(lote, 2)
		addTodas(t, log, lote, []Leaf{folha(i * 10), folha(i*10 + 1)})
	}

	cadeia := log.Checkpoints()
	if len(cadeia) != 5 {
		t.Fatalf("esperava 5 checkpoints, veio %d", len(cadeia))
	}
	if !bytes.Equal(cadeia[0].Previous, GenesisHash()) {
		t.Fatal("o primeiro checkpoint nao aponta para o genesis")
	}
	for i := 1; i < len(cadeia); i++ {
		if !bytes.Equal(cadeia[i].Previous, cadeia[i-1].Hash) {
			t.Fatalf("elo quebrado entre %d e %d", i-1, i)
		}
	}
	if err := VerifyChain(cadeia); err != nil {
		t.Fatalf("cadeia integra foi recusada: %v", err)
	}
}

// Reescrever um lote antigo tem de invalidar tudo o que veio depois - e essa a propriedade
// que a cadeia existe para dar.
func TestReescreverUmLoteAntigoQuebraACadeia(t *testing.T) {
	log := NewLog()
	for i := 0; i < 3; i++ {
		lote := fmt.Sprintf("j%d", i)
		log.Expect(lote, 1)
		addTodas(t, log, lote, []Leaf{folha(i)})
	}

	cadeia := log.Checkpoints()
	cadeia[0].Root[0] ^= 0xFF

	if err := VerifyChain(cadeia); err == nil {
		t.Fatal("cadeia adulterada passou na verificacao")
	}
}

func TestProvaDeInclusaoBateComARaizPublicada(t *testing.T) {
	log := NewLog()
	folhas := make([]Leaf, 23)
	for i := range folhas {
		folhas[i] = folha(i)
	}
	log.Expect("j1", len(folhas))
	addTodas(t, log, "j1", folhas)

	for _, f := range folhas {
		ponto, prova, err := log.Lookup(f.Key)
		if err != nil {
			t.Fatalf("Lookup(%s): %v", f.Key, err)
		}
		if !merkle.Verify(ponto.Root, f.Data, prova) {
			t.Fatalf("prova de %s nao bate com a raiz", f.Key)
		}
	}
}

func TestFolhaDesconhecidaNaoTemProva(t *testing.T) {
	log := NewLog()
	log.Expect("j1", 1)
	addTodas(t, log, "j1", []Leaf{folha(1)})

	if _, _, err := log.Lookup("recibo-inexistente"); err != ErrFolhaAusente {
		t.Fatalf("esperava ErrFolhaAusente, veio %v", err)
	}
}

func TestUsoConcorrente(t *testing.T) {
	log := NewLog()
	const total = 200
	log.Expect("j1", total)

	var wg sync.WaitGroup
	for i := 0; i < total; i++ {
		wg.Add(1)
		go func(n int) {
			defer wg.Done()
			if _, err := log.Add("j1", folha(n)); err != nil {
				t.Errorf("Add concorrente: %v", err)
			}
		}(i)
	}
	wg.Wait()

	head := log.Head()
	if head == nil || head.Size != total {
		t.Fatalf("esperava um lote de %d, veio %+v", total, head)
	}
}

func TestLoteVazioTemAArvoreVaziaDaRfc(t *testing.T) {
	log := NewLog()
	selados, err := log.Expect("j1", 0)
	if err != nil {
		t.Fatal(err)
	}
	if len(selados) != 1 || selados[0].Size != 0 {
		t.Fatalf("lote sem folhas deveria selar imediatamente: %+v", selados)
	}
	if !bytes.Equal(selados[0].Root, merkle.New(nil).Root()) {
		t.Fatal("raiz do lote vazio divergiu da arvore vazia")
	}
}

// O consumo e no minimo uma vez: uma queda entre selar e confirmar o offset faz o marcador
// chegar de novo em toda retomada. Repetir nao pode ser tratado como defeito.
func TestMarcadorReentregueDepoisDoSeloEIgnorado(t *testing.T) {
	log := NewLog()
	addTodas(t, log, "j1", []Leaf{folha(1), folha(2)})
	if _, err := log.Expect("j1", 2); err != nil {
		t.Fatalf("Expect: %v", err)
	}

	selados, err := log.Expect("j1", 2)
	if err != nil {
		t.Fatalf("marcador reentregue virou erro: %v", err)
	}
	if len(selados) != 0 {
		t.Fatalf("marcador reentregue selou de novo: %d", len(selados))
	}
	if n := len(log.Checkpoints()); n != 1 {
		t.Fatalf("esperava 1 checkpoint, veio %d", n)
	}
}

// Contagem diferente para um lote ja selado nao e reentrega: e alguem contando outra coisa.
func TestMarcadorComOutraContagemDepoisDoSeloFalha(t *testing.T) {
	log := NewLog()
	addTodas(t, log, "j1", []Leaf{folha(1), folha(2)})
	if _, err := log.Expect("j1", 2); err != nil {
		t.Fatalf("Expect: %v", err)
	}

	if _, err := log.Expect("j1", 3); !errors.Is(err, ErrLoteJaSelado) {
		t.Fatalf("esperava ErrLoteJaSelado, veio %v", err)
	}
}
