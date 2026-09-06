package api

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/joaolaureano/voting_system/voting-merkle/internal/voting"
	"github.com/joaolaureano/voting_system/voting-merkle/pkg/checkpoint"
	"github.com/joaolaureano/voting_system/voting-merkle/pkg/merkle"
)

// recibo produz um hash com a cara de um recibo real do sistema.
func recibo(n int) string {
	soma := sha256.Sum256([]byte(fmt.Sprintf("voto-%d", n)))
	return hex.EncodeToString(soma[:])
}

// cadeiaComJanelas monta um log como o consumidor faria, a partir dos eventos do Flink.
func cadeiaComJanelas(t *testing.T, janelas map[string][]string) *checkpoint.Log {
	t.Helper()
	log := checkpoint.NewLog()

	for janela, recibos := range janelas {
		if _, err := log.Expect(janela, len(recibos)); err != nil {
			t.Fatal(err)
		}
		for _, r := range recibos {
			folha, err := voting.AcceptedVote{Receipt: r, WindowID: janela}.Leaf()
			if err != nil {
				t.Fatal(err)
			}
			if _, err := log.Add(janela, folha); err != nil {
				t.Fatal(err)
			}
		}
	}
	return log
}

func get(t *testing.T, h http.Handler, caminho string) (*httptest.ResponseRecorder, map[string]any) {
	t.Helper()
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, caminho, nil))

	var corpo map[string]any
	_ = json.Unmarshal(rec.Body.Bytes(), &corpo)
	return rec, corpo
}

// O teste que importa: a prova devolvida pela API tem de fechar com a raiz publicada,
// usando so o pacote merkle puro - exatamente o que um auditor externo faria.
func TestAProvaDaApiVerificaContraARaiz(t *testing.T) {
	recibos := make([]string, 37)
	for i := range recibos {
		recibos[i] = recibo(i)
	}
	log := cadeiaComJanelas(t, map[string][]string{"2026-10-04T13:05:00Z": recibos})
	handler := NewServer(log).Handler()

	for _, r := range recibos {
		rec, corpo := get(t, handler, "/proof/"+r)
		if rec.Code != http.StatusOK {
			t.Fatalf("GET /proof/%s: HTTP %d", r, rec.Code)
		}

		var resposta ProofResponse
		if err := json.Unmarshal(rec.Body.Bytes(), &resposta); err != nil {
			t.Fatal(err)
		}
		_ = corpo

		raiz, err := hex.DecodeString(resposta.Root)
		if err != nil {
			t.Fatal(err)
		}
		caminho := make([][]byte, len(resposta.Path))
		for i, irmao := range resposta.Path {
			if caminho[i], err = hex.DecodeString(irmao); err != nil {
				t.Fatal(err)
			}
		}
		folha, _ := hex.DecodeString(r)

		prova := merkle.Proof{Index: resposta.LeafIndex, Size: resposta.TreeSize, Path: caminho}
		if !merkle.Verify(raiz, folha, prova) {
			t.Fatalf("a prova de %s nao fecha com a raiz publicada", r)
		}
	}
}

func TestReciboDeOutraJanelaTemSuaPropriaRaiz(t *testing.T) {
	log := cadeiaComJanelas(t, map[string][]string{
		"2026-10-04T13:05:00Z": {recibo(1), recibo(2)},
	})
	if _, err := log.Expect("2026-10-04T13:06:00Z", 1); err != nil {
		t.Fatal(err)
	}
	folha, _ := voting.AcceptedVote{Receipt: recibo(3), WindowID: "2026-10-04T13:06:00Z"}.Leaf()
	if _, err := log.Add("2026-10-04T13:06:00Z", folha); err != nil {
		t.Fatal(err)
	}

	handler := NewServer(log).Handler()
	_, primeira := get(t, handler, "/proof/"+recibo(1))
	_, segunda := get(t, handler, "/proof/"+recibo(3))

	if primeira["root"] == segunda["root"] {
		t.Fatal("janelas diferentes deveriam ter raizes diferentes")
	}
	if segunda["previousHash"] != primeira["checkpointHash"] {
		t.Fatal("a segunda janela nao encadeou na primeira")
	}
}

// Um recibo ausente pode significar tres coisas muito diferentes. A resposta nao pode deixar
// o eleitor concluir que seu voto foi descartado quando a janela so nao fechou ainda.
func TestReciboNaoSeladoRespondeSemAcusarDescarte(t *testing.T) {
	log := cadeiaComJanelas(t, map[string][]string{"2026-10-04T13:05:00Z": {recibo(1)}})

	rec, corpo := get(t, NewServer(log).Handler(), "/proof/"+recibo(99))

	if rec.Code != http.StatusNotFound {
		t.Fatalf("esperava 404, veio %d", rec.Code)
	}
	if corpo["error"] != "RECIBO_NAO_SELADO" {
		t.Fatalf("codigo de erro inesperado: %v", corpo["error"])
	}
	mensagem, _ := corpo["message"].(string)
	for _, termo := range []string{"aberta", "duplicidade"} {
		if !contains(mensagem, termo) {
			t.Fatalf("a mensagem deveria explicar %q: %q", termo, mensagem)
		}
	}
}

func TestReciboEmMaiusculoEAceito(t *testing.T) {
	r := recibo(1)
	log := cadeiaComJanelas(t, map[string][]string{"2026-10-04T13:05:00Z": {r}})

	rec, _ := get(t, NewServer(log).Handler(), "/proof/"+upper(r))
	if rec.Code != http.StatusOK {
		t.Fatalf("recibo em maiusculo deveria ser aceito, veio %d", rec.Code)
	}
}

func TestListaDeRaizesEEncadeada(t *testing.T) {
	log := checkpoint.NewLog()
	for i := 0; i < 3; i++ {
		janela := fmt.Sprintf("2026-10-04T13:0%d:00Z", i)
		log.Expect(janela, 1)
		folha, _ := voting.AcceptedVote{Receipt: recibo(i), WindowID: janela}.Leaf()
		log.Add(janela, folha)
	}

	rec := httptest.NewRecorder()
	NewServer(log).Handler().ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/roots", nil))

	var raizes []RootResponse
	if err := json.Unmarshal(rec.Body.Bytes(), &raizes); err != nil {
		t.Fatal(err)
	}
	if len(raizes) != 3 {
		t.Fatalf("esperava 3 raizes, veio %d", len(raizes))
	}
	for i := 1; i < len(raizes); i++ {
		if raizes[i].PreviousHash != raizes[i-1].CheckpointHash {
			t.Fatalf("elo quebrado entre %d e %d", i-1, i)
		}
		if raizes[i].Sequence != i {
			t.Fatalf("sequencia errada em %d", i)
		}
	}
}

func TestHealthzMostraOEstadoDaCadeia(t *testing.T) {
	log := cadeiaComJanelas(t, map[string][]string{"2026-10-04T13:05:00Z": {recibo(1)}})
	// Uma janela anunciada e ainda incompleta.
	log.Expect("2026-10-04T13:06:00Z", 5)

	rec, corpo := get(t, NewServer(log).Handler(), "/healthz")

	if rec.Code != http.StatusOK || corpo["status"] != "UP" {
		t.Fatalf("healthz: %d %v", rec.Code, corpo)
	}
	if corpo["sealedCheckpoints"].(float64) != 1 {
		t.Fatalf("checkpoints selados: %v", corpo["sealedCheckpoints"])
	}
	if corpo["pendingWindows"].(float64) != 1 {
		t.Fatalf("janelas pendentes: %v", corpo["pendingWindows"])
	}
}

func TestJanelaNaoSeladaResponde404(t *testing.T) {
	log := cadeiaComJanelas(t, map[string][]string{"2026-10-04T13:05:00Z": {recibo(1)}})

	rec, corpo := get(t, NewServer(log).Handler(), "/roots/2026-10-04T99:99:00Z")
	if rec.Code != http.StatusNotFound || corpo["error"] != "JANELA_NAO_SELADA" {
		t.Fatalf("esperava 404 JANELA_NAO_SELADA, veio %d %v", rec.Code, corpo)
	}
}

func contains(s, sub string) bool {
	for i := 0; i+len(sub) <= len(s); i++ {
		if s[i:i+len(sub)] == sub {
			return true
		}
	}
	return false
}

func upper(s string) string {
	out := []byte(s)
	for i, c := range out {
		if c >= 'a' && c <= 'z' {
			out[i] = c - 32
		}
	}
	return string(out)
}
