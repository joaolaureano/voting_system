// Package merkle implementa a arvore de Merkle append-only da RFC 6962, a mesma do
// Certificate Transparency.
//
// O pacote nao sabe o que e um voto, uma eleicao ou um topico Kafka: ele recebe folhas
// opacas e devolve raizes e provas. Seguir a RFC, em vez de inventar um esquema proprio,
// tem uma consequencia pratica - qualquer verificador de CT existente consegue conferir
// nossas provas, e um auditor nao precisa confiar no nosso codigo para checar o resultado.
//
// Os prefixos de dominio (0x00 para folha, 0x01 para no interno) sao o que impede o ataque
// classico de segunda pre-imagem, em que uma folha e forjada para se passar por um no
// interno.
package merkle

import (
	"crypto/sha256"
	"errors"
	"math/bits"
)

const (
	leafPrefix = 0x00
	nodePrefix = 0x01
)

// HashSize e o tamanho em bytes de um hash SHA-256.
const HashSize = sha256.Size

// ErrIndexOutOfRange indica um indice de folha fora da arvore.
var ErrIndexOutOfRange = errors.New("merkle: indice de folha fora da arvore")

// HashLeaf devolve o hash de uma folha: SHA-256(0x00 || data).
func HashLeaf(data []byte) []byte {
	h := sha256.New()
	h.Write([]byte{leafPrefix})
	h.Write(data)
	return h.Sum(nil)
}

// HashNode devolve o hash de um no interno: SHA-256(0x01 || esquerda || direita).
func HashNode(left, right []byte) []byte {
	h := sha256.New()
	h.Write([]byte{nodePrefix})
	h.Write(left)
	h.Write(right)
	return h.Sum(nil)
}

// Tree e uma arvore de Merkle imutavel sobre uma sequencia de folhas.
//
// A ordem das folhas faz parte da definicao da arvore: duas ordens diferentes das mesmas
// folhas dao raizes diferentes. Quem constroi a arvore e responsavel por impor uma ordem
// canonica se quiser que a raiz seja reproduzivel.
type Tree struct {
	leaves [][]byte // ja hasheadas com HashLeaf
	root   []byte
}

// New constroi a arvore a partir das folhas brutas, na ordem dada.
func New(leaves [][]byte) *Tree {
	hashed := make([][]byte, len(leaves))
	for i, leaf := range leaves {
		hashed[i] = HashLeaf(leaf)
	}
	return &Tree{leaves: hashed, root: root(hashed)}
}

// Size e o numero de folhas.
func (t *Tree) Size() int { return len(t.leaves) }

// Root devolve a raiz. Para a arvore vazia, e SHA-256 da string vazia, como manda a RFC.
func (t *Tree) Root() []byte {
	out := make([]byte, len(t.root))
	copy(out, t.root)
	return out
}

// root implementa MTH(D[n]) da RFC 6962.
func root(hashed [][]byte) []byte {
	switch len(hashed) {
	case 0:
		empty := sha256.Sum256(nil)
		return empty[:]
	case 1:
		return hashed[0]
	default:
		k := splitPoint(len(hashed))
		return HashNode(root(hashed[:k]), root(hashed[k:]))
	}
}

// splitPoint devolve a maior potencia de dois estritamente menor que n.
//
// E este split - e nao a divisao ao meio - que faz a arvore ser append-only: acrescentar
// folhas nunca reescreve as subarvores ja fechadas a esquerda.
func splitPoint(n int) int {
	if n < 2 {
		return 0
	}
	return 1 << (bits.Len(uint(n-1)) - 1)
}
