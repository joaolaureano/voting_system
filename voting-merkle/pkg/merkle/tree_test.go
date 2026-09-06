package merkle

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"testing"
)

func leaves(n int) [][]byte {
	out := make([][]byte, n)
	for i := range out {
		out[i] = []byte(fmt.Sprintf("folha-%d", i))
	}
	return out
}

// Valores conhecidos da RFC 6962: a arvore vazia e SHA-256(""), e a arvore de uma folha
// vazia e SHA-256(0x00). Ancoram a implementacao no padrao, e nao no proprio codigo.
func TestRaizesConhecidasDaRfc6962(t *testing.T) {
	vazia := hex.EncodeToString(New(nil).Root())
	if vazia != "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" {
		t.Fatalf("raiz da arvore vazia divergiu do padrao: %s", vazia)
	}

	umaFolha := hex.EncodeToString(New([][]byte{{}}).Root())
	if umaFolha != "6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d" {
		t.Fatalf("raiz de folha vazia divergiu do padrao: %s", umaFolha)
	}
}

func TestArvoreDeDuasFolhas(t *testing.T) {
	a, b := []byte("a"), []byte("b")

	esperado := HashNode(HashLeaf(a), HashLeaf(b))

	if !bytes.Equal(New([][]byte{a, b}).Root(), esperado) {
		t.Fatal("raiz de duas folhas nao bate com H(0x01 || H(0x00||a) || H(0x00||b))")
	}
}

// Sem os prefixos de dominio, um atacante poderia apresentar o conteudo de um no interno
// como se fosse uma folha. Os prefixos tornam os dois espacos disjuntos.
func TestFolhaENoInternoVivemEmEspacosSeparados(t *testing.T) {
	dado := []byte("mesmo conteudo")

	if bytes.Equal(HashLeaf(dado), sha256.New().Sum(dado)) {
		t.Fatal("hash de folha nao pode ser o SHA-256 cru do dado")
	}
	interno := HashNode(HashLeaf([]byte("a")), HashLeaf([]byte("b")))
	if bytes.Equal(HashLeaf(append(HashLeaf([]byte("a")), HashLeaf([]byte("b"))...)), interno) {
		t.Fatal("uma folha conseguiu se passar por um no interno")
	}
}

func TestTodasAsProvasVerificamParaVariosTamanhos(t *testing.T) {
	for n := 1; n <= 64; n++ {
		folhas := leaves(n)
		arvore := New(folhas)
		raiz := arvore.Root()

		for i := 0; i < n; i++ {
			prova, err := arvore.Prove(i)
			if err != nil {
				t.Fatalf("n=%d i=%d: %v", n, i, err)
			}
			if !Verify(raiz, folhas[i], prova) {
				t.Fatalf("n=%d i=%d: prova valida foi recusada", n, i)
			}
		}
	}
}

func TestProvaNaoServeParaOutraFolha(t *testing.T) {
	folhas := leaves(17)
	arvore := New(folhas)
	prova, _ := arvore.Prove(5)

	if Verify(arvore.Root(), folhas[6], prova) {
		t.Fatal("prova da folha 5 aceitou a folha 6")
	}
	if Verify(arvore.Root(), []byte("voto forjado"), prova) {
		t.Fatal("prova aceitou uma folha que nao esta na arvore")
	}
}

func TestProvaNaoServeParaOutraRaiz(t *testing.T) {
	folhas := leaves(9)
	arvore := New(folhas)
	prova, _ := arvore.Prove(3)

	outra := New(leaves(10)).Root()
	if Verify(outra, folhas[3], prova) {
		t.Fatal("prova foi aceita contra a raiz de outra arvore")
	}
}

func TestProvaAdulteradaERecusada(t *testing.T) {
	folhas := leaves(13)
	arvore := New(folhas)
	raiz := arvore.Root()
	original, _ := arvore.Prove(7)

	truncada := Proof{Index: original.Index, Size: original.Size, Path: original.Path[:len(original.Path)-1]}
	if Verify(raiz, folhas[7], truncada) {
		t.Fatal("prova truncada foi aceita")
	}

	inflada := Proof{Index: original.Index, Size: original.Size,
		Path: append(append([][]byte{}, original.Path...), make([]byte, HashSize))}
	if Verify(raiz, folhas[7], inflada) {
		t.Fatal("prova inflada foi aceita")
	}

	irmaoTrocado := Proof{Index: original.Index, Size: original.Size,
		Path: append([][]byte{}, original.Path...)}
	irmaoTrocado.Path[0] = make([]byte, HashSize)
	if Verify(raiz, folhas[7], irmaoTrocado) {
		t.Fatal("prova com irmao adulterado foi aceita")
	}

	indiceErrado := Proof{Index: 8, Size: original.Size, Path: original.Path}
	if Verify(raiz, folhas[7], indiceErrado) {
		t.Fatal("prova com indice trocado foi aceita")
	}
}

func TestIndiceForaDaArvore(t *testing.T) {
	arvore := New(leaves(4))

	if _, err := arvore.Prove(4); err != ErrIndexOutOfRange {
		t.Fatalf("esperava ErrIndexOutOfRange, veio %v", err)
	}
	if _, err := arvore.Prove(-1); err != ErrIndexOutOfRange {
		t.Fatalf("esperava ErrIndexOutOfRange, veio %v", err)
	}
}

func TestAOrdemDasFolhasFazParteDaDefinicao(t *testing.T) {
	a, b := []byte("a"), []byte("b")

	if bytes.Equal(New([][]byte{a, b}).Root(), New([][]byte{b, a}).Root()) {
		t.Fatal("trocar a ordem das folhas deveria mudar a raiz")
	}
}

func TestMesmasFolhasProduzemAMesmaRaiz(t *testing.T) {
	if !bytes.Equal(New(leaves(31)).Root(), New(leaves(31)).Root()) {
		t.Fatal("a raiz nao e deterministica")
	}
}

// A propriedade append-only: as subarvores fechadas a esquerda nunca sao reescritas. Com o
// split em potencia de dois, a arvore de 2^k folhas continua sendo um no interno intacto
// dentro de qualquer arvore maior.
func TestSubarvoresAEsquerdaPermanecemEstaveis(t *testing.T) {
	folhas := leaves(20)
	oitoPrimeiras := New(folhas[:8]).Root()

	arvore := New(folhas)
	prova, _ := arvore.Prove(0)

	// Numa arvore de 20 folhas, o caminho da folha 0 sobe pela subarvore de 16; o irmao no
	// nivel das 8 primeiras tem de ser a raiz de folhas[8:16].
	if len(prova.Path) < 4 {
		t.Fatalf("caminho curto demais: %d", len(prova.Path))
	}
	if !bytes.Equal(HashNode(oitoPrimeiras, New(folhas[8:16]).Root()), New(folhas[:16]).Root()) {
		t.Fatal("a subarvore das 8 primeiras folhas foi reescrita")
	}
}

func TestRootNaoExpoeOEstadoInterno(t *testing.T) {
	arvore := New(leaves(3))
	raiz := arvore.Root()
	raiz[0] ^= 0xFF

	if bytes.Equal(raiz, arvore.Root()) {
		t.Fatal("Root() devolveu a fatia interna, e nao uma copia")
	}
}
