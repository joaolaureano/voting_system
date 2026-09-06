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
//
// Os nos internos sao materializados na construcao, e nao recalculados a cada prova. Guardar
// so as folhas custaria O(n) hashes por prova - num lote de 100 mil votos, 14 ms e cem mil
// alocacoes para responder a um unico eleitor. Com os niveis em memoria, a prova e um passeio
// de O(log n) leituras, sem hash nenhum. O preco e dobrar a memoria da arvore (2n hashes em
// vez de n), o que para 100 mil folhas sao 6 MB.
type Tree struct {
	// levels[0] sao as folhas ja hasheadas; cada nivel seguinte tem metade dos nos,
	// arredondando para cima; o ultimo nivel tem so a raiz.
	levels [][][]byte
}

// New constroi a arvore a partir das folhas brutas, na ordem dada.
func New(leaves [][]byte) *Tree {
	return &Tree{levels: buildLevels(leaves)}
}

// Size e o numero de folhas.
func (t *Tree) Size() int {
	if len(t.levels) == 0 {
		return 0
	}
	return len(t.levels[0])
}

// Root devolve a raiz. Para a arvore vazia, e SHA-256 da string vazia, como manda a RFC.
func (t *Tree) Root() []byte {
	if len(t.levels) == 0 {
		empty := sha256.Sum256(nil)
		return empty[:]
	}
	topo := t.levels[len(t.levels)-1][0]
	out := make([]byte, len(topo))
	copy(out, topo)
	return out
}

// buildLevels emparelha os nos de baixo para cima, promovendo o ultimo no sem par.
//
// Isso e equivalente ao MTH(D[n]) da RFC 6962, que divide a sequencia na maior potencia de
// dois menor que n: a promocao do no impar reproduz exatamente essa divisao, sem precisar
// calcular o ponto de corte em cada nivel. E o que torna a arvore append-only - as subarvores
// fechadas a esquerda nunca sao reescritas.
//
// O hasher e reaproveitado e cada nivel sai de um unico buffer: sem isso, construir um lote
// de 100 mil folhas faria 200 mil alocacoes so para os digests.
func buildLevels(leaves [][]byte) [][][]byte {
	n := len(leaves)
	if n == 0 {
		return nil
	}

	h := sha256.New()
	nivel := make([][]byte, n)
	buf := make([]byte, n*HashSize)
	for i, folha := range leaves {
		h.Reset()
		h.Write([]byte{leafPrefix})
		h.Write(folha)
		nivel[i] = h.Sum(buf[i*HashSize : i*HashSize : (i+1)*HashSize])
	}

	levels := [][][]byte{nivel}
	for len(nivel) > 1 {
		acima := make([][]byte, (len(nivel)+1)/2)
		buf := make([]byte, len(acima)*HashSize)
		for i, j := 0, 0; i < len(nivel); i, j = i+2, j+1 {
			if i+1 == len(nivel) {
				// No sem par sobe intacto: nao existe "duplicar o ultimo" na RFC 6962,
				// e duplicar abriria o ataque de forjar uma arvore de tamanho diferente
				// com a mesma raiz.
				acima[j] = nivel[i]
				continue
			}
			h.Reset()
			h.Write([]byte{nodePrefix})
			h.Write(nivel[i])
			h.Write(nivel[i+1])
			acima[j] = h.Sum(buf[j*HashSize : j*HashSize : (j+1)*HashSize])
		}
		levels = append(levels, acima)
		nivel = acima
	}
	return levels
}
