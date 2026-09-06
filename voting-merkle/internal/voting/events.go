// Package voting traduz os eventos do sistema de votacao para as folhas opacas que os
// pacotes pkg/merkle e pkg/checkpoint entendem.
//
// E a unica parte do servico que conhece o vocabulario da eleicao. Os pacotes de arvore e de
// cadeia continuam servindo para qualquer outro fluxo de eventos.
package voting

import (
	"encoding/hex"
	"fmt"
	"regexp"
	"time"

	"github.com/joaolaureano/voting_system/voting-merkle/pkg/checkpoint"
)

var reciboValido = regexp.MustCompile(`^[0-9a-f]{64}$`)

// AcceptedVote espelha com.voting.contracts.AcceptedVoteEvent, publicado em votes.accepted.
//
// Vem depois do dedup do Flink, entao representa exatamente os votos que entraram na
// apuracao. Nao carrega o candidato - este servico responde ao publico e nao pode ser a
// fonte de um mapa de quem votou em quem.
type AcceptedVote struct {
	SchemaVersion int       `json:"schemaVersion"`
	Receipt       string    `json:"receipt"`
	VoterID       string    `json:"voterId"`
	WindowID      string    `json:"windowId"`
	CastAt        time.Time `json:"castAt"`
}

// Leaf converte o voto na folha que entra na arvore.
//
// A folha e o recibo em bytes, e nao o JSON do evento: assim quem audita so precisa do
// proprio comprovante para recalcular o hash da folha, sem depender do formato interno.
func (v AcceptedVote) Leaf() (checkpoint.Leaf, error) {
	if !reciboValido.MatchString(v.Receipt) {
		return checkpoint.Leaf{}, fmt.Errorf("recibo invalido: %q", v.Receipt)
	}
	if v.WindowID == "" {
		return checkpoint.Leaf{}, fmt.Errorf("voto %s sem janela", v.Receipt)
	}
	dados, err := hex.DecodeString(v.Receipt)
	if err != nil {
		return checkpoint.Leaf{}, fmt.Errorf("recibo %q nao e hexadecimal: %w", v.Receipt, err)
	}
	return checkpoint.Leaf{Key: v.Receipt, Data: dados}, nil
}

// WindowMarker espelha com.voting.contracts.WindowMarkerEvent, publicado em votes.windows.
//
// O Flink so o emite quando a marca d'agua passa o fim da janela. Count e o que permite selar
// por completude, em vez de por timeout.
type WindowMarker struct {
	SchemaVersion int       `json:"schemaVersion"`
	WindowID      string    `json:"windowId"`
	WindowStart   time.Time `json:"windowStart"`
	WindowEnd     time.Time `json:"windowEnd"`
	Count         int64     `json:"count"`
}

// Validate confere o marcador antes de ele virar uma expectativa da cadeia.
func (m WindowMarker) Validate() error {
	if m.WindowID == "" {
		return fmt.Errorf("marcador sem janela")
	}
	if m.Count < 0 {
		return fmt.Errorf("marcador da janela %s com contagem negativa: %d", m.WindowID, m.Count)
	}
	return nil
}

// RootRecord e o que o servico publica em merkle.roots ao selar uma janela.
//
// Carrega o elo anterior junto com a raiz: quem consome o topico do inicio consegue refazer
// a cadeia inteira e detectar qualquer reescrita, sem falar com o servico.
type RootRecord struct {
	SchemaVersion  int       `json:"schemaVersion"`
	WindowID       string    `json:"windowId"`
	Sequence       int       `json:"sequence"`
	LeafCount      int       `json:"leafCount"`
	Root           string    `json:"root"`
	PreviousHash   string    `json:"previousHash"`
	CheckpointHash string    `json:"checkpointHash"`
	SealedAt       time.Time `json:"sealedAt"`
}

// SchemaVersion atual dos registros publicados por este servico.
const CurrentSchemaVersion = 1

// NewRootRecord monta o registro publicavel a partir de um checkpoint selado.
func NewRootRecord(c *checkpoint.Checkpoint) RootRecord {
	return RootRecord{
		SchemaVersion:  CurrentSchemaVersion,
		WindowID:       c.BatchID,
		Sequence:       c.Sequence,
		LeafCount:      c.Size,
		Root:           hex.EncodeToString(c.Root),
		PreviousHash:   hex.EncodeToString(c.Previous),
		CheckpointHash: hex.EncodeToString(c.Hash),
		SealedAt:       c.SealedAt,
	}
}
