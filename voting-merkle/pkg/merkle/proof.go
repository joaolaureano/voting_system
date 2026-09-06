package merkle

import "bytes"

// Proof e o caminho de uma folha ate a raiz: os hashes irmaos, de baixo para cima.
//
// Tem tamanho O(log n), entao provar que um voto entrou numa arvore de um milhao de folhas
// custa vinte hashes - e quem verifica nao precisa da arvore inteira, so da raiz publicada.
type Proof struct {
	Index int      // posicao da folha na arvore
	Size  int      // numero de folhas da arvore
	Path  [][]byte // hashes irmaos, da folha para a raiz
}

// Prove devolve a prova de inclusao da folha na posicao index.
func (t *Tree) Prove(index int) (Proof, error) {
	if index < 0 || index >= len(t.leaves) {
		return Proof{}, ErrIndexOutOfRange
	}
	return Proof{Index: index, Size: len(t.leaves), Path: path(index, t.leaves)}, nil
}

// path implementa PATH(m, D[n]) da RFC 6962.
func path(m int, hashed [][]byte) [][]byte {
	if len(hashed) <= 1 {
		return nil
	}
	k := splitPoint(len(hashed))
	if m < k {
		return append(path(m, hashed[:k]), root(hashed[k:]))
	}
	return append(path(m-k, hashed[k:]), root(hashed[:k]))
}

// Verify confere que leaf esta na posicao Index de uma arvore de Size folhas cuja raiz e root.
//
// Esta funcao e o ponto do sistema todo: quem audita roda isto com a raiz publicada, a prova
// e o proprio recibo, sem precisar de acesso ao servidor nem confiar nele.
//
// O algoritmo e o da RFC 6962: sobe da folha para a raiz mantendo o indice do no (fn) e o
// indice do ultimo no daquele nivel (sn). Comparar os dois e o que revela quando o no esta na
// borda direita da arvore, o caso em que a subarvore nao esta cheia.
//
// Os testes de fn/sn tambem sao a defesa contra provas de tamanho errado: um caminho mais
// longo que a arvore esgota sn no meio do percurso, e um mais curto termina com sn != 0.
func Verify(root, leaf []byte, proof Proof) bool {
	if proof.Index < 0 || proof.Size <= 0 || proof.Index >= proof.Size {
		return false
	}

	computed := HashLeaf(leaf)
	fn, sn := proof.Index, proof.Size-1

	for _, sibling := range proof.Path {
		if sn == 0 {
			return false // caminho mais longo que a arvore
		}
		if fn%2 == 1 || fn == sn {
			computed = HashNode(sibling, computed)
			for fn != 0 && fn%2 == 0 {
				fn >>= 1
				sn >>= 1
			}
		} else {
			computed = HashNode(computed, sibling)
		}
		fn >>= 1
		sn >>= 1
	}

	return sn == 0 && bytes.Equal(computed, root)
}
