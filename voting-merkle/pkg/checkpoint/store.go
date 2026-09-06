package checkpoint

import "time"

// Store e onde a cadeia sobrevive a um restart.
//
// O contrato e de um diario append-only: o Log conta o que aconteceu, na ordem em que
// aconteceu, e espera receber de volta a mesma sequencia em Replay. Nao existe update nem
// delete - reescrever o passado e exatamente o que a cadeia de hashes serve para denunciar.
//
// A interface nao menciona arquivo, banco ou Kafka de proposito: quem implementa escolhe. O
// que ela exige e que Sync seja um ponto de durabilidade real, porque e a ele que o servico
// amarra o avanco do consumo.
type Store interface {
	// Append grava um registro. Pode ficar em buffer ate Sync.
	Append(Record) error
	// Sync torna duraveis os registros gravados ate agora.
	Sync() error
	// Replay entrega os registros na ordem em que foram gravados.
	Replay(func(Record) error) error
	// ID identifica *esta* copia do estado. Muda quando o estado e criado do zero.
	ID() string
	// Close libera o recurso.
	Close() error
}

// Kind distingue os tres fatos que a cadeia precisa lembrar.
type Kind uint8

const (
	// KindLeaf: uma folha entrou num lote aberto.
	KindLeaf Kind = 1
	// KindMarker: um lote foi anunciado com um total de folhas.
	KindMarker Kind = 2
	// KindSealed: um lote foi selado, com a raiz e o elo que ele produziu.
	KindSealed Kind = 3
)

// Record e uma entrada do diario.
//
// Os tres tipos vivem numa struct so porque a ordem entre eles e o que importa: um Replay
// que os separasse por tipo perderia justamente a informacao de que a folha chegou antes do
// marcador, ou depois do selo.
type Record struct {
	Kind    Kind
	BatchID string

	// KindLeaf
	Leaf Leaf
	// KindMarker
	Count int
	// KindSealed
	Sealed Sealed
}

// Sealed e o que se guarda de um checkpoint fechado.
//
// A arvore nao entra: ela e reconstruida das folhas no Replay, e o resultado e conferido
// contra estes campos. Guardar a raiz e recalcula-la e o que transforma o restart numa
// auditoria do proprio disco - um byte trocado numa folha aparece como raiz divergente, em
// vez de virar uma prova errada servida com confianca.
type Sealed struct {
	Sequence int
	Size     int
	Root     []byte
	Previous []byte
	Hash     []byte
	SealedAt time.Time
}
