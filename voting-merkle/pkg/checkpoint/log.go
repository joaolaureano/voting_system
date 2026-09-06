// Package checkpoint mantem uma cadeia de lotes selados, cada um com sua arvore de Merkle.
//
// Como o pacote merkle, nao sabe o que e um voto: recebe folhas opacas agrupadas em lotes
// identificados por uma string. Quem usa decide o que e um lote - uma janela de tempo, um
// bloco de N registros, um dia de operacao.
//
// Duas propriedades e que dao valor a cadeia:
//
//   - Dentro de um lote, as folhas sao ordenadas pela chave antes de virar arvore. A raiz
//     passa a ser funcao do conjunto, e nao da ordem de chegada - o que importa quando as
//     folhas vem de varias particoes Kafka em paralelo e a ordem varia a cada execucao.
//
//   - Cada checkpoint inclui o hash do anterior. Reescrever um lote antigo muda todos os
//     hashes seguintes, entao a ultima raiz publicada compromete a historia inteira.
package checkpoint

import (
	"bytes"
	"crypto/sha256"
	"errors"
	"fmt"
	"sort"
	"sync"
	"time"

	"github.com/joaolaureano/voting_system/voting-merkle/pkg/merkle"
)

// chainPrefix separa o hash da cadeia dos hashes da arvore, pelo mesmo motivo que a RFC 6962
// separa folha de no interno: dominios distintos nao podem colidir.
const chainPrefix = 0x02

var (
	// ErrLoteJaSelado indica folha ou marcador chegando para um lote ja fechado.
	ErrLoteJaSelado = errors.New("checkpoint: lote ja selado")
	// ErrLoteDesconhecido indica prova pedida para um lote que nunca foi selado.
	ErrLoteDesconhecido = errors.New("checkpoint: lote desconhecido")
	// ErrFolhaAusente indica que a chave nao esta em nenhum lote selado.
	ErrFolhaAusente = errors.New("checkpoint: folha ausente")
	// ErrExcedeuOEsperado indica mais folhas distintas do que o marcador anunciou.
	ErrExcedeuOEsperado = errors.New("checkpoint: lote recebeu mais folhas que o esperado")
)

// GenesisHash e o antecessor do primeiro checkpoint.
func GenesisHash() []byte { return make([]byte, merkle.HashSize) }

// Leaf e uma folha com sua chave de ordenacao e busca.
//
// A chave e o que o mundo externo apresenta para pedir uma prova - no caso da votacao, o
// recibo. Data e o que de fato entra na arvore.
type Leaf struct {
	Key  string
	Data []byte
}

// Checkpoint e um lote selado.
type Checkpoint struct {
	BatchID  string
	Sequence int
	Size     int
	Root     []byte
	Previous []byte
	Hash     []byte
	SealedAt time.Time

	tree      *merkle.Tree
	positions map[string]int
}

// Prove devolve a prova de inclusao da folha com aquela chave neste checkpoint.
func (c *Checkpoint) Prove(key string) (merkle.Proof, error) {
	index, ok := c.positions[key]
	if !ok {
		return merkle.Proof{}, ErrFolhaAusente
	}
	return c.tree.Prove(index)
}

// pending e um lote ainda aberto.
type pending struct {
	leaves   map[string][]byte
	expected int
	marked   bool
}

// Log e a cadeia de checkpoints. Seguro para uso concorrente.
type Log struct {
	mu sync.RWMutex

	pending map[string]*pending
	// fila dos lotes anunciados, na ordem em que os marcadores chegaram. E ela que define a
	// sequencia da cadeia: os lotes sao selados nesta ordem, e nao na ordem em que ficam
	// completos, senao a cadeia dependeria de quem terminou primeiro.
	queue []string

	sealed  []*Checkpoint
	byBatch map[string]*Checkpoint
	byLeaf  map[string]*Checkpoint
	last    []byte
}

// NewLog cria uma cadeia vazia.
func NewLog() *Log {
	return &Log{
		pending: make(map[string]*pending),
		byBatch: make(map[string]*Checkpoint),
		byLeaf:  make(map[string]*Checkpoint),
		last:    GenesisHash(),
	}
}

// Add registra uma folha num lote ainda aberto e devolve os checkpoints que isso fechou.
//
// Folhas repetidas com a mesma chave sao ignoradas: o consumo do Kafka e no minimo uma vez,
// entao uma reentrega nao pode inflar o lote.
func (l *Log) Add(batchID string, leaf Leaf) ([]*Checkpoint, error) {
	l.mu.Lock()
	defer l.mu.Unlock()

	if selado, ok := l.byBatch[batchID]; ok {
		// Chegou depois do fechamento: so e aceitavel se ja estiver dentro da arvore.
		if _, incluida := selado.positions[leaf.Key]; incluida {
			return nil, nil
		}
		return nil, fmt.Errorf("%w: %s (folha %s)", ErrLoteJaSelado, batchID, leaf.Key)
	}

	lote := l.pendingFor(batchID)
	if _, repetida := lote.leaves[leaf.Key]; !repetida {
		if lote.marked && len(lote.leaves) >= lote.expected {
			return nil, fmt.Errorf("%w: %s esperava %d", ErrExcedeuOEsperado, batchID, lote.expected)
		}
		dados := make([]byte, len(leaf.Data))
		copy(dados, leaf.Data)
		lote.leaves[leaf.Key] = dados
	}

	// Acrescentar uma folha so pode ter completado *este* lote. Se ele ainda nao fechou,
	// nenhum outro mudou de estado desde o ultimo drain, e varrer a fila seria desperdicio.
	// Sem essa guarda, uma janela de 100 mil votos faz 100 mil varreduras para nada.
	if !lote.marked || len(lote.leaves) < lote.expected {
		return nil, nil
	}
	return l.drain(), nil
}

// Expect anuncia que um lote fechou com exatamente `count` folhas, e devolve os checkpoints
// que isso permitiu selar.
//
// A ordem das chamadas define a ordem da cadeia.
func (l *Log) Expect(batchID string, count int) ([]*Checkpoint, error) {
	l.mu.Lock()
	defer l.mu.Unlock()

	if _, ok := l.byBatch[batchID]; ok {
		return nil, fmt.Errorf("%w: %s", ErrLoteJaSelado, batchID)
	}

	lote := l.pendingFor(batchID)
	if lote.marked {
		if lote.expected != count {
			return nil, fmt.Errorf("checkpoint: marcador conflitante para %s: %d != %d",
				batchID, lote.expected, count)
		}
		return nil, nil
	}
	if len(lote.leaves) > count {
		return nil, fmt.Errorf("%w: %s tem %d, marcador diz %d",
			ErrExcedeuOEsperado, batchID, len(lote.leaves), count)
	}

	lote.marked = true
	lote.expected = count
	l.queue = append(l.queue, batchID)

	// Idem: marcar um lote so pode ter completado ele proprio. Um lote sem folhas
	// (count zero) fecha aqui mesmo.
	if len(lote.leaves) < count {
		return nil, nil
	}
	return l.drain(), nil
}

func (l *Log) pendingFor(batchID string) *pending {
	lote, ok := l.pending[batchID]
	if !ok {
		lote = &pending{leaves: make(map[string][]byte)}
		l.pending[batchID] = lote
	}
	return lote
}

// drain sela, em ordem de fila, todos os lotes ja completos.
//
// Para no primeiro incompleto de proposito: selar o seguinte antes dele quebraria a
// sequencia da cadeia.
func (l *Log) drain() []*Checkpoint {
	var novos []*Checkpoint

	for len(l.queue) > 0 {
		batchID := l.queue[0]
		lote := l.pending[batchID]
		if !lote.marked || len(lote.leaves) < lote.expected {
			break
		}

		l.queue = l.queue[1:]
		delete(l.pending, batchID)
		novos = append(novos, l.seal(batchID, lote))
	}
	return novos
}

func (l *Log) seal(batchID string, lote *pending) *Checkpoint {
	chaves := make([]string, 0, len(lote.leaves))
	for chave := range lote.leaves {
		chaves = append(chaves, chave)
	}
	// Ordem canonica: a raiz vira funcao do conjunto de folhas, nao da ordem de chegada.
	sort.Strings(chaves)

	folhas := make([][]byte, len(chaves))
	posicoes := make(map[string]int, len(chaves))
	for i, chave := range chaves {
		folhas[i] = lote.leaves[chave]
		posicoes[chave] = i
	}

	arvore := merkle.New(folhas)
	raiz := arvore.Root()
	anterior := l.last

	ponto := &Checkpoint{
		BatchID:   batchID,
		Sequence:  len(l.sealed),
		Size:      len(folhas),
		Root:      raiz,
		Previous:  anterior,
		Hash:      chainHash(anterior, raiz, batchID, len(folhas)),
		SealedAt:  time.Now().UTC(),
		tree:      arvore,
		positions: posicoes,
	}

	l.sealed = append(l.sealed, ponto)
	l.byBatch[batchID] = ponto
	for chave := range posicoes {
		l.byLeaf[chave] = ponto
	}
	l.last = ponto.Hash
	return ponto
}

// chainHash encadeia um checkpoint ao anterior.
//
// Inclui batchID e size, e nao so a raiz: sem eles, dois lotes distintos com o mesmo conjunto
// de folhas produziriam elos identicos.
func chainHash(previous, root []byte, batchID string, size int) []byte {
	h := sha256.New()
	h.Write([]byte{chainPrefix})
	h.Write(previous)
	h.Write(root)
	h.Write([]byte(batchID))
	fmt.Fprintf(h, "|%d", size)
	return h.Sum(nil)
}

// Lookup devolve o checkpoint que contem aquela folha e a prova de inclusao.
func (l *Log) Lookup(key string) (*Checkpoint, merkle.Proof, error) {
	l.mu.RLock()
	defer l.mu.RUnlock()

	ponto, ok := l.byLeaf[key]
	if !ok {
		return nil, merkle.Proof{}, ErrFolhaAusente
	}
	prova, err := ponto.Prove(key)
	return ponto, prova, err
}

// Checkpoints devolve a cadeia selada, do mais antigo ao mais recente.
func (l *Log) Checkpoints() []*Checkpoint {
	l.mu.RLock()
	defer l.mu.RUnlock()

	saida := make([]*Checkpoint, len(l.sealed))
	copy(saida, l.sealed)
	return saida
}

// Head devolve o ultimo checkpoint selado, ou nil se a cadeia esta vazia.
func (l *Log) Head() *Checkpoint {
	l.mu.RLock()
	defer l.mu.RUnlock()

	if len(l.sealed) == 0 {
		return nil
	}
	return l.sealed[len(l.sealed)-1]
}

// Pending devolve quantos lotes ainda esperam folhas ou marcador.
func (l *Log) Pending() int {
	l.mu.RLock()
	defer l.mu.RUnlock()
	return len(l.pending)
}

// VerifyChain refaz os elos da cadeia e diz se ela esta intacta.
func VerifyChain(chain []*Checkpoint) error {
	anterior := GenesisHash()
	for i, ponto := range chain {
		if !bytes.Equal(ponto.Previous, anterior) {
			return fmt.Errorf("checkpoint %d (%s): elo anterior nao confere", i, ponto.BatchID)
		}
		esperado := chainHash(ponto.Previous, ponto.Root, ponto.BatchID, ponto.Size)
		if !bytes.Equal(ponto.Hash, esperado) {
			return fmt.Errorf("checkpoint %d (%s): hash nao confere", i, ponto.BatchID)
		}
		anterior = ponto.Hash
	}
	return nil
}
