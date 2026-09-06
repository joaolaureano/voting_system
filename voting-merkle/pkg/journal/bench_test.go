package journal

import (
	"fmt"
	"path/filepath"
	"testing"

	"github.com/joaolaureano/voting_system/voting-merkle/pkg/checkpoint"
)

// Os dois caminhos que a persistencia acrescenta ao servico: o custo por voto de gravar a
// folha, e o custo de partida de reconstruir a cadeia do disco.
//
// O segundo e o que justifica o trabalho inteiro. Antes, a partida custava reler dois
// topicos do Kafka desde o inicio; a pergunta e quanto custa reler o arquivo local.

func folhasDeBenchmark(n int) []checkpoint.Leaf {
	folhas := make([]checkpoint.Leaf, n)
	for i := range folhas {
		chave := fmt.Sprintf("%064x", i)
		folhas[i] = checkpoint.Leaf{Key: chave, Data: []byte(chave)}
	}
	return folhas
}

// preencher grava uma cadeia com janelas de 1000 folhas e devolve o caminho do diario.
func preencher(b *testing.B, total int) string {
	b.Helper()
	caminho := filepath.Join(b.TempDir(), "chain.wal")

	diario, err := Open(caminho)
	if err != nil {
		b.Fatalf("abrindo: %v", err)
	}
	log, _, err := checkpoint.NewLogWithStore(diario)
	if err != nil {
		b.Fatalf("retomando: %v", err)
	}

	const porJanela = 1000
	folhas := folhasDeBenchmark(total)
	for inicio := 0; inicio < total; inicio += porJanela {
		fim := min(inicio+porJanela, total)
		janela := fmt.Sprintf("w%06d", inicio/porJanela)
		for _, f := range folhas[inicio:fim] {
			if _, err := log.Add(janela, f); err != nil {
				b.Fatalf("Add: %v", err)
			}
		}
		if _, err := log.Expect(janela, fim-inicio); err != nil {
			b.Fatalf("Expect: %v", err)
		}
	}
	if err := diario.Close(); err != nil {
		b.Fatalf("fechando: %v", err)
	}
	return caminho
}

// BenchmarkAppend mede a gravacao de uma folha sem fsync: e o custo que cada voto paga no
// caminho quente. O fsync fica de fora de proposito - ele acontece uma vez por lote de
// confirmacao, nao uma vez por voto.
func BenchmarkAppend(b *testing.B) {
	diario, err := Open(filepath.Join(b.TempDir(), "chain.wal"))
	if err != nil {
		b.Fatalf("abrindo: %v", err)
	}
	defer diario.Close()

	folhas := folhasDeBenchmark(1024)
	b.ReportAllocs()
	b.ResetTimer()
	for i := 0; b.Loop(); i++ {
		rec := checkpoint.Record{Kind: checkpoint.KindLeaf, BatchID: "w000000", Leaf: folhas[i%len(folhas)]}
		if err := diario.Append(rec); err != nil {
			b.Fatalf("append: %v", err)
		}
	}
}

// BenchmarkSync mede o fsync: o preco de tornar duravel o que ja foi gravado, pago uma vez
// por lote confirmado.
func BenchmarkSync(b *testing.B) {
	diario, err := Open(filepath.Join(b.TempDir(), "chain.wal"))
	if err != nil {
		b.Fatalf("abrindo: %v", err)
	}
	defer diario.Close()

	folhas := folhasDeBenchmark(256)
	b.ReportAllocs()
	b.ResetTimer()
	for i := 0; b.Loop(); i++ {
		for _, f := range folhas {
			if err := diario.Append(checkpoint.Record{Kind: checkpoint.KindLeaf, BatchID: "w000000", Leaf: f}); err != nil {
				b.Fatalf("append: %v", err)
			}
		}
		if err := diario.Sync(); err != nil {
			b.Fatalf("sync: %v", err)
		}
	}
}

// BenchmarkRestore mede a partida: ler o diario inteiro, refazer cada arvore das folhas e
// conferir cada raiz contra o selo gravado.
func BenchmarkRestore(b *testing.B) {
	for _, total := range []int{1_000, 10_000, 100_000} {
		caminho := preencher(b, total)

		b.Run(fmt.Sprintf("%d_folhas", total), func(b *testing.B) {
			b.ReportAllocs()
			for b.Loop() {
				diario, err := Open(caminho)
				if err != nil {
					b.Fatalf("abrindo: %v", err)
				}
				log, _, err := checkpoint.NewLogWithStore(diario)
				if err != nil {
					b.Fatalf("retomando: %v", err)
				}
				if got := len(log.Checkpoints()); got == 0 {
					b.Fatalf("retomada vazia")
				}
				diario.Close()
			}
		})
	}
}
