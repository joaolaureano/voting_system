package merkle

import (
	"crypto/sha256"
	"encoding/binary"
	"fmt"
	"testing"
)

// recibos gera folhas com a forma real das do sistema: hashes SHA-256 de 32 bytes.
func recibos(n int) [][]byte {
	out := make([][]byte, n)
	for i := range out {
		var buf [8]byte
		binary.BigEndian.PutUint64(buf[:], uint64(i))
		soma := sha256.Sum256(buf[:])
		out[i] = soma[:]
	}
	return out
}

// Tamanhos de uma janela de apuracao: de um municipio pequeno a uma janela de pico
// numa eleicao nacional.
var tamanhos = []int{1_000, 10_000, 100_000}

// BenchmarkNew mede o selamento de um lote - acontece uma vez por janela.
func BenchmarkNew(b *testing.B) {
	for _, n := range tamanhos {
		folhas := recibos(n)
		b.Run(fmt.Sprintf("folhas=%d", n), func(b *testing.B) {
			b.ReportAllocs()
			b.ResetTimer()
			for i := 0; i < b.N; i++ {
				_ = New(folhas)
			}
		})
	}
}

// BenchmarkProve mede a geracao de uma prova - acontece uma vez por eleitor que
// consulta o comprovante, e e o caminho quente depois do encerramento.
func BenchmarkProve(b *testing.B) {
	for _, n := range tamanhos {
		arvore := New(recibos(n))
		b.Run(fmt.Sprintf("folhas=%d", n), func(b *testing.B) {
			b.ReportAllocs()
			b.ResetTimer()
			for i := 0; i < b.N; i++ {
				if _, err := arvore.Prove(i % n); err != nil {
					b.Fatal(err)
				}
			}
		})
	}
}

// BenchmarkVerify mede o que um auditor externo roda.
func BenchmarkVerify(b *testing.B) {
	for _, n := range tamanhos {
		folhas := recibos(n)
		arvore := New(folhas)
		raiz := arvore.Root()
		provas := make([]Proof, 64)
		for i := range provas {
			provas[i], _ = arvore.Prove(i)
		}
		b.Run(fmt.Sprintf("folhas=%d", n), func(b *testing.B) {
			b.ReportAllocs()
			b.ResetTimer()
			for i := 0; i < b.N; i++ {
				j := i % len(provas)
				if !Verify(raiz, folhas[j], provas[j]) {
					b.Fatal("prova valida recusada")
				}
			}
		})
	}
}
