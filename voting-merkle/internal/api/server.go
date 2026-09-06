// Package api expoe a cadeia por HTTP: e por aqui que o eleitor confere seu comprovante.
package api

import (
	"encoding/hex"
	"encoding/json"
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/joaolaureano/voting_system/voting-merkle/pkg/checkpoint"
)

// ProofResponse e a resposta de GET /proof/{recibo}.
//
// Traz tudo que a verificacao exige, para que o cliente nao precise voltar ao servidor nem
// confiar nele: com o recibo, a prova e a raiz, qualquer um refaz a conta. O campo
// Verification descreve a formula, para que a resposta seja auditavel sem documentacao.
type ProofResponse struct {
	Receipt        string    `json:"receipt"`
	WindowID       string    `json:"windowId"`
	Sequence       int       `json:"sequence"`
	LeafIndex      int       `json:"leafIndex"`
	TreeSize       int       `json:"treeSize"`
	Root           string    `json:"root"`
	PreviousHash   string    `json:"previousHash"`
	CheckpointHash string    `json:"checkpointHash"`
	SealedAt       time.Time `json:"sealedAt"`
	Path           []string  `json:"path"`
	Verification   string    `json:"verification"`
}

// RootResponse resume um checkpoint selado.
type RootResponse struct {
	WindowID       string    `json:"windowId"`
	Sequence       int       `json:"sequence"`
	LeafCount      int       `json:"leafCount"`
	Root           string    `json:"root"`
	PreviousHash   string    `json:"previousHash"`
	CheckpointHash string    `json:"checkpointHash"`
	SealedAt       time.Time `json:"sealedAt"`
}

type errorResponse struct {
	Error   string `json:"error"`
	Message string `json:"message"`
}

// Server serve a cadeia.
type Server struct {
	log *checkpoint.Log
}

// NewServer cria o servidor sobre um log existente.
func NewServer(log *checkpoint.Log) *Server { return &Server{log: log} }

// Handler monta as rotas.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", s.healthz)
	mux.HandleFunc("GET /proof/{receipt}", s.proof)
	mux.HandleFunc("GET /roots", s.roots)
	mux.HandleFunc("GET /roots/{windowId}", s.root)
	return mux
}

func (s *Server) healthz(w http.ResponseWriter, _ *http.Request) {
	head := s.log.Head()
	corpo := map[string]any{
		"status":            "UP",
		"sealedCheckpoints": len(s.log.Checkpoints()),
		"pendingWindows":    s.log.Pending(),
	}
	if head != nil {
		corpo["head"] = hex.EncodeToString(head.Hash)
		corpo["headWindow"] = head.BatchID
	}
	writeJSON(w, http.StatusOK, corpo)
}

// proof responde "seu voto entrou?" com uma prova, e nao com a palavra do servidor.
func (s *Server) proof(w http.ResponseWriter, r *http.Request) {
	recibo := strings.ToLower(r.PathValue("receipt"))

	ponto, prova, err := s.log.Lookup(recibo)
	if err != nil {
		if errors.Is(err, checkpoint.ErrFolhaAusente) {
			// 404 aqui nao quer dizer "voto invalido": pode ser uma janela ainda aberta,
			// um voto recusado por duplicidade, ou um recibo inventado. A mensagem evita
			// que o eleitor conclua a pior das tres.
			writeJSON(w, http.StatusNotFound, errorResponse{
				Error: "RECIBO_NAO_SELADO",
				Message: "recibo nao esta em nenhuma janela selada; " +
					"a janela pode ainda estar aberta, ou o voto foi recusado por duplicidade",
			})
			return
		}
		writeJSON(w, http.StatusInternalServerError, errorResponse{Error: "ERRO_INTERNO", Message: err.Error()})
		return
	}

	caminho := make([]string, len(prova.Path))
	for i, irmao := range prova.Path {
		caminho[i] = hex.EncodeToString(irmao)
	}

	writeJSON(w, http.StatusOK, ProofResponse{
		Receipt:        recibo,
		WindowID:       ponto.BatchID,
		Sequence:       ponto.Sequence,
		LeafIndex:      prova.Index,
		TreeSize:       prova.Size,
		Root:           hex.EncodeToString(ponto.Root),
		PreviousHash:   hex.EncodeToString(ponto.Previous),
		CheckpointHash: hex.EncodeToString(ponto.Hash),
		SealedAt:       ponto.SealedAt,
		Path:           caminho,
		Verification: "RFC 6962: folha = SHA-256(0x00 || bytes(receipt)); " +
			"no = SHA-256(0x01 || esquerda || direita); suba o path ate obter root",
	})
}

func (s *Server) roots(w http.ResponseWriter, _ *http.Request) {
	cadeia := s.log.Checkpoints()
	saida := make([]RootResponse, len(cadeia))
	for i, ponto := range cadeia {
		saida[i] = toRootResponse(ponto)
	}
	writeJSON(w, http.StatusOK, saida)
}

func (s *Server) root(w http.ResponseWriter, r *http.Request) {
	janela := r.PathValue("windowId")
	for _, ponto := range s.log.Checkpoints() {
		if ponto.BatchID == janela {
			writeJSON(w, http.StatusOK, toRootResponse(ponto))
			return
		}
	}
	writeJSON(w, http.StatusNotFound, errorResponse{
		Error:   "JANELA_NAO_SELADA",
		Message: "janela " + janela + " ainda nao foi selada",
	})
}

func toRootResponse(c *checkpoint.Checkpoint) RootResponse {
	return RootResponse{
		WindowID:       c.BatchID,
		Sequence:       c.Sequence,
		LeafCount:      c.Size,
		Root:           hex.EncodeToString(c.Root),
		PreviousHash:   hex.EncodeToString(c.Previous),
		CheckpointHash: hex.EncodeToString(c.Hash),
		SealedAt:       c.SealedAt,
	}
}

func writeJSON(w http.ResponseWriter, status int, corpo any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(corpo)
}
