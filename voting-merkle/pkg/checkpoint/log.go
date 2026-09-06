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

	// store e nil quando a cadeia vive so em memoria - o modo dos testes e do uso
	// embutido. Com store, cada fato e gravado antes de mudar o estado em memoria.
	store Store
	// restoring silencia a gravacao enquanto o Replay reencena o que ja esta gravado.
	restoring bool
}

// NewLog cria uma cadeia vazia, so em memoria.
func NewLog() *Log {
	return &Log{
		pending: make(map[string]*pending),
		byBatch: make(map[string]*Checkpoint),
		byLeaf:  make(map[string]*Checkpoint),
		last:    GenesisHash(),
	}
}

// NewLogWithStore cria a cadeia sobre um diario e devolve tambem os checkpoints que o
// Replay deixou pendentes de selo - o rastro de uma queda entre gravar o marcador e gravar
// o selo. Quem chama e responsavel por publica-los, porque o restante da cadeia ja foi
// publicado na execucao anterior.
func NewLogWithStore(store Store) (*Log, []*Checkpoint, error) {
	l := NewLog()
	l.store = store

	novos, err := l.restore()
	if err != nil {
		return nil, nil, err
	}
	return l, novos, nil
}

// StoreID identifica a copia do estado local, ou "" quando a cadeia e so memoria.
func (l *Log) StoreID() string {
	if l.store == nil {
		return ""
	}
	return l.store.ID()
}

// Sync torna duravel tudo que foi registrado ate agora.
//
// Depois que retorna sem erro, um restart reconstroi a cadeia com estas folhas. E o unico
// ponto do servico em que essa promessa existe - e por isso o unico lugar de onde faz
// sentido confirmar consumo rio acima.
func (l *Log) Sync() error {
	if l.store == nil {
		return nil
	}
	l.mu.Lock()
	defer l.mu.Unlock()
	return l.store.Sync()
}

func (l *Log) persist(rec Record) error {
	if l.store == nil || l.restoring {
		return nil
	}
	return l.store.Append(rec)
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
		// Grava antes de mudar a memoria: um erro aqui deixa o estado como estava, e a
		// folha volta pela reentrega. O inverso - memoria a frente do disco - daria uma
		// arvore que so existe neste processo.
		if err := l.persist(Record{Kind: KindLeaf, BatchID: batchID, Leaf: Leaf{Key: leaf.Key, Data: dados}}); err != nil {
			return nil, err
		}
		lote.leaves[leaf.Key] = dados
	}

	// Acrescentar uma folha so pode ter completado *este* lote. Se ele ainda nao fechou,
	// nenhum outro mudou de estado desde o ultimo drain, e varrer a fila seria desperdicio.
	// Sem essa guarda, uma janela de 100 mil votos faz 100 mil varreduras para nada.
	if !lote.marked || len(lote.leaves) < lote.expected {
		return nil, nil
	}
	return l.drain()
}

// Expect anuncia que um lote fechou com exatamente `count` folhas, e devolve os checkpoints
// que isso permitiu selar.
//
// A ordem das chamadas define a ordem da cadeia.
func (l *Log) Expect(batchID string, count int) ([]*Checkpoint, error) {
	l.mu.Lock()
	defer l.mu.Unlock()

	if selado, ok := l.byBatch[batchID]; ok {
		// Marcador reentregue depois do selo: o consumo do Kafka e no minimo uma vez, e uma
		// queda entre gravar o selo e confirmar o offset faz esta chamada acontecer em toda
		// retomada. Repetir o que ja foi feito e um nao-evento; so contagem diferente e que
		// significa que alguem esta contando outra coisa.
		if selado.Size == count {
			return nil, nil
		}
		return nil, fmt.Errorf("%w: %s selado com %d, marcador diz %d",
			ErrLoteJaSelado, batchID, selado.Size, count)
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

	if err := l.persist(Record{Kind: KindMarker, BatchID: batchID, Count: count}); err != nil {
		return nil, err
	}
	lote.marked = true
	lote.expected = count
	l.queue = append(l.queue, batchID)

	// Idem: marcar um lote so pode ter completado ele proprio. Um lote sem folhas
	// (count zero) fecha aqui mesmo.
	if len(lote.leaves) < count {
		return nil, nil
	}
	return l.drain()
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
func (l *Log) drain() ([]*Checkpoint, error) {
	var novos []*Checkpoint

	for len(l.queue) > 0 {
		batchID := l.queue[0]
		lote := l.pending[batchID]
		if !lote.marked || len(lote.leaves) < lote.expected {
			break
		}

		ponto, err := l.seal(batchID, lote)
		if err != nil {
			// O lote fica na fila, ainda aberto: nada foi selado, e a cadeia nao ganha
			// um elo que o disco desconhece.
			return novos, err
		}

		l.queue = l.queue[1:]
		delete(l.pending, batchID)
		novos = append(novos, ponto)
	}
	return novos, nil
}

func (l *Log) seal(batchID string, lote *pending) (*Checkpoint, error) {
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

	if err := l.persist(Record{Kind: KindSealed, BatchID: batchID, Sealed: Sealed{
		Sequence: ponto.Sequence,
		Size:     ponto.Size,
		Root:     ponto.Root,
		Previous: ponto.Previous,
		Hash:     ponto.Hash,
		SealedAt: ponto.SealedAt,
	}}); err != nil {
		return nil, err
	}

	l.sealed = append(l.sealed, ponto)
	l.byBatch[batchID] = ponto
	for chave := range posicoes {
		l.byLeaf[chave] = ponto
	}
	l.last = ponto.Hash
	return ponto, nil
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

// restore reconstroi a cadeia a partir do diario.
//
// O ponto do desenho: a arvore de cada lote e *recalculada* das folhas gravadas, e a raiz e
// o elo resultantes sao conferidos contra o que o selo diz. Um byte trocado no disco vira um
// erro na partida, e nao uma prova errada servida com confianca meses depois.
//
// Devolve os lotes que estavam completos mas sem selo - uma queda entre gravar o marcador e
// gravar o selo. Eles sao selados agora, e a chamada e a unica que persiste durante a
// retomada.
func (l *Log) restore() ([]*Checkpoint, error) {
	l.mu.Lock()
	defer l.mu.Unlock()

	l.restoring = true
	err := l.store.Replay(func(rec Record) error {
		switch rec.Kind {
		case KindLeaf:
			return l.replayLeaf(rec)
		case KindMarker:
			return l.replayMarker(rec)
		case KindSealed:
			return l.replaySealed(rec)
		default:
			return fmt.Errorf("checkpoint: registro de tipo %d desconhecido", rec.Kind)
		}
	})
	l.restoring = false
	if err != nil {
		return nil, err
	}

	novos, err := l.drain()
	if err != nil {
		return nil, err
	}
	if len(novos) > 0 {
		// Os selos recuperados agora precisam estar no disco antes de serem anunciados:
		// e a mesma ordem que vale em regime, aplicada a partida.
		if err := l.store.Sync(); err != nil {
			return nil, err
		}
	}
	return novos, nil
}

func (l *Log) replayLeaf(rec Record) error {
	if selado, ok := l.byBatch[rec.BatchID]; ok {
		if _, incluida := selado.positions[rec.Leaf.Key]; incluida {
			return nil
		}
		return fmt.Errorf("%w (no diario): %s (folha %s)", ErrLoteJaSelado, rec.BatchID, rec.Leaf.Key)
	}
	lote := l.pendingFor(rec.BatchID)
	lote.leaves[rec.Leaf.Key] = rec.Leaf.Data
	return nil
}

func (l *Log) replayMarker(rec Record) error {
	lote := l.pendingFor(rec.BatchID)
	if lote.marked {
		return nil
	}
	lote.marked = true
	lote.expected = rec.Count
	l.queue = append(l.queue, rec.BatchID)
	return nil
}

func (l *Log) replaySealed(rec Record) error {
	// O selo so pode fechar o lote que esta na frente da fila: e a fila que define a
	// sequencia da cadeia, e um selo fora de ordem nao e um diario que a gente reconheca.
	if len(l.queue) == 0 || l.queue[0] != rec.BatchID {
		return fmt.Errorf("checkpoint: selo de %s fora da ordem do diario", rec.BatchID)
	}
	lote := l.pending[rec.BatchID]
	if len(lote.leaves) != rec.Sealed.Size {
		return fmt.Errorf("checkpoint: %s foi selado com %d folhas, o diario tem %d",
			rec.BatchID, rec.Sealed.Size, len(lote.leaves))
	}

	ponto, err := l.seal(rec.BatchID, lote)
	if err != nil {
		return err
	}
	l.queue = l.queue[1:]
	delete(l.pending, rec.BatchID)

	if err := conferir(ponto, rec.Sealed); err != nil {
		return err
	}
	// SealedAt nao entra no encadeamento, entao e o unico campo que vem do diario em vez de
	// ser recalculado: o instante do selo original, e nao o da retomada.
	ponto.SealedAt = rec.Sealed.SealedAt
	return nil
}

// conferir compara o checkpoint recalculado com o que o diario registrou.
func conferir(ponto *Checkpoint, selo Sealed) error {
	switch {
	case ponto.Sequence != selo.Sequence:
		return fmt.Errorf("checkpoint %s: sequencia %d no diario, %d ao recalcular",
			ponto.BatchID, selo.Sequence, ponto.Sequence)
	case !bytes.Equal(ponto.Root, selo.Root):
		return fmt.Errorf("checkpoint %s: raiz do diario nao confere com a recalculada das folhas",
			ponto.BatchID)
	case !bytes.Equal(ponto.Previous, selo.Previous):
		return fmt.Errorf("checkpoint %s: elo anterior do diario nao confere", ponto.BatchID)
	case !bytes.Equal(ponto.Hash, selo.Hash):
		return fmt.Errorf("checkpoint %s: hash do diario nao confere com o recalculado", ponto.BatchID)
	}
	return nil
}
