package voting

import (
	"encoding/hex"
	"encoding/json"
	"testing"
)

const reciboExemplo = "38641edba45fbe7d06bcde8c6be5658dae3b2f5c23713db06baa05adf05392b8"

func TestVotoDoFlinkViraFolha(t *testing.T) {
	payload := `{"schemaVersion":1,"receipt":"` + reciboExemplo + `",
	             "voterId":"voter-1","windowId":"2026-10-04T13:05:00Z",
	             "castAt":"2026-10-04T13:05:37.412Z"}`

	var voto AcceptedVote
	if err := json.Unmarshal([]byte(payload), &voto); err != nil {
		t.Fatal(err)
	}

	folha, err := voto.Leaf()
	if err != nil {
		t.Fatal(err)
	}
	if folha.Key != reciboExemplo {
		t.Fatalf("chave deveria ser o recibo, veio %q", folha.Key)
	}
	// A folha e o recibo em bytes: quem audita so precisa do comprovante para refazer a conta.
	esperado, _ := hex.DecodeString(reciboExemplo)
	if hex.EncodeToString(folha.Data) != hex.EncodeToString(esperado) {
		t.Fatal("a folha nao e o recibo em bytes")
	}
}

func TestReciboInvalidoNaoViraFolha(t *testing.T) {
	casos := map[string]string{
		"vazio":        "",
		"curto":        "abc123",
		"maiusculo":    "38641EDBA45FBE7D06BCDE8C6BE5658DAE3B2F5C23713DB06BAA05ADF05392B8",
		"nao hex":      "zz641edba45fbe7d06bcde8c6be5658dae3b2f5c23713db06baa05adf05392b8",
		"longo demais": reciboExemplo + "00",
	}
	for nome, recibo := range casos {
		t.Run(nome, func(t *testing.T) {
			voto := AcceptedVote{Receipt: recibo, WindowID: "2026-10-04T13:05:00Z"}
			if _, err := voto.Leaf(); err == nil {
				t.Fatalf("aceitou recibo %q", recibo)
			}
		})
	}
}

func TestVotoSemJanelaNaoViraFolha(t *testing.T) {
	voto := AcceptedVote{Receipt: reciboExemplo}
	if _, err := voto.Leaf(); err == nil {
		t.Fatal("aceitou voto sem janela")
	}
}

func TestMarcadorDoFlinkEValidado(t *testing.T) {
	payload := `{"schemaVersion":1,"windowId":"2026-10-04T13:05:00Z",
	             "windowStart":"2026-10-04T13:05:00Z","windowEnd":"2026-10-04T13:06:00Z","count":8412}`

	var marcador WindowMarker
	if err := json.Unmarshal([]byte(payload), &marcador); err != nil {
		t.Fatal(err)
	}
	if err := marcador.Validate(); err != nil {
		t.Fatal(err)
	}
	if marcador.Count != 8412 {
		t.Fatalf("contagem errada: %d", marcador.Count)
	}

	if err := (WindowMarker{Count: 1}).Validate(); err == nil {
		t.Fatal("aceitou marcador sem janela")
	}
	if err := (WindowMarker{WindowID: "j", Count: -1}).Validate(); err == nil {
		t.Fatal("aceitou contagem negativa")
	}
}

// O topico que alimenta a arvore nao pode carregar o candidato: ele e a base da consulta
// publica, e viraria um mapa de quem votou em quem.
func TestOVotoAceitoNaoCarregaOCandidato(t *testing.T) {
	dados, err := json.Marshal(AcceptedVote{
		SchemaVersion: 1, Receipt: reciboExemplo, VoterID: "voter-1",
		WindowID: "2026-10-04T13:05:00Z",
	})
	if err != nil {
		t.Fatal(err)
	}
	var campos map[string]any
	if err := json.Unmarshal(dados, &campos); err != nil {
		t.Fatal(err)
	}
	for _, proibido := range []string{"candidateId", "partyId", "candidate", "party"} {
		if _, existe := campos[proibido]; existe {
			t.Fatalf("o evento expos %q", proibido)
		}
	}
}
