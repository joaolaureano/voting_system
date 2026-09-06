// Command vetores emite os vetores de teste que o frontend usa para conferir sua propria
// implementacao da RFC 6962.
//
// Existe para que voting-web nao teste a si mesmo. O verificador do navegador e uma segunda
// implementacao do mesmo algoritmo, e uma segunda implementacao so tem valor se for
// confrontada com a primeira: as provas aqui saem de pkg/checkpoint, e o teste em JavaScript
// tem de chegar aos mesmos hashes.
//
//	go run ./cmd/vetores > ../voting-web/test/vetores.json
package main

import (
	"encoding/hex"
	"encoding/json"
	"fmt"
	"os"

	"github.com/joaolaureano/voting_system/voting-merkle/pkg/checkpoint"
)

type saida struct {
	Janelas []janela `json:"janelas"`
}

type janela struct {
	WindowID       string  `json:"windowId"`
	Sequence       int     `json:"sequence"`
	LeafCount      int     `json:"leafCount"`
	Root           string  `json:"root"`
	PreviousHash   string  `json:"previousHash"`
	CheckpointHash string  `json:"checkpointHash"`
	Provas         []prova `json:"provas"`
}

type prova struct {
	Receipt   string   `json:"receipt"`
	LeafIndex int      `json:"leafIndex"`
	TreeSize  int      `json:"treeSize"`
	Path      []string `json:"path"`
}

func main() {
	// Tamanhos escolhidos pelas bordas: 1 folha (arvore de um no so), 2 (cheia), 3, 7 e 12
	// (niveis impares, em que o ultimo no sobe sem irmao) - os casos em que uma verificacao
	// mal portada erra.
	tamanhos := []int{1, 2, 3, 7, 12}

	log := checkpoint.NewLog()
	var resultado saida

	for i, tamanho := range tamanhos {
		windowID := fmt.Sprintf("2026-01-01T00:%02d:00Z/15s", i)
		recibos := make([]string, tamanho)

		for j := range tamanho {
			// Recibos com a mesma cara dos reais: 64 hexadecimais.
			recibos[j] = fmt.Sprintf("%064x", i*1000+j)
			dados, err := hex.DecodeString(recibos[j])
			if err != nil {
				panic(err)
			}
			if _, err := log.Add(windowID, checkpoint.Leaf{Key: recibos[j], Data: dados}); err != nil {
				panic(err)
			}
		}
		if _, err := log.Expect(windowID, tamanho); err != nil {
			panic(err)
		}

		ponto := log.Checkpoints()[i]
		j := janela{
			WindowID:       ponto.BatchID,
			Sequence:       ponto.Sequence,
			LeafCount:      ponto.Size,
			Root:           hex.EncodeToString(ponto.Root),
			PreviousHash:   hex.EncodeToString(ponto.Previous),
			CheckpointHash: hex.EncodeToString(ponto.Hash),
		}

		for _, recibo := range recibos {
			_, p, err := log.Lookup(recibo)
			if err != nil {
				panic(err)
			}
			caminho := make([]string, len(p.Path))
			for k, irmao := range p.Path {
				caminho[k] = hex.EncodeToString(irmao)
			}
			j.Provas = append(j.Provas, prova{
				Receipt:   recibo,
				LeafIndex: p.Index,
				TreeSize:  p.Size,
				Path:      caminho,
			})
		}
		resultado.Janelas = append(resultado.Janelas, j)
	}

	enc := json.NewEncoder(os.Stdout)
	enc.SetIndent("", "  ")
	if err := enc.Encode(resultado); err != nil {
		panic(err)
	}
}
