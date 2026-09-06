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
//
// Sobe pelos niveis ja materializados juntando o irmao de cada no. Nao calcula hash nenhum:
// tudo de que precisa foi computado uma vez, na construcao da arvore.
//
// Quando um no nao tem irmao naquele nivel - o ultimo no de um nivel de tamanho impar - ele
// sobe intacto e nao contribui com nada para o caminho.
func (t *Tree) Prove(index int) (Proof, error) {
	n := t.Size()
	if index < 0 || index >= n {
		return Proof{}, ErrIndexOutOfRange
	}

	caminho := make([][]byte, 0, len(t.levels))
	idx := index
	for nivel := 0; nivel < len(t.levels)-1; nivel++ {
		nos := t.levels[nivel]
		if irmao := idx ^ 1; irmao < len(nos) {
			caminho = append(caminho, nos[irmao])
		}
		idx >>= 1
	}
	return Proof{Index: index, Size: n, Path: caminho}, nil
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
