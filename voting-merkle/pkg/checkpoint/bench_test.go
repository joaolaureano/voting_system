package checkpoint

import (
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"fmt"
	"testing"
)

// loteDe monta as folhas de uma janela, com a forma real: chave hex de 64 chars e
// 32 bytes de dado.
func loteDe(n int) []Leaf {
	out := make([]Leaf, n)
	for i := range out {
		var buf [8]byte
		binary.BigEndian.PutUint64(buf[:], uint64(i))
		soma := sha256.Sum256(buf[:])
		out[i] = Leaf{Key: hex.EncodeToString(soma[:]), Data: soma[:]}
	}
	return out
}

var janelas = []int{1_000, 10_000, 100_000}

// BenchmarkSeal mede o fechamento de uma janela inteira: receber as folhas, ordenar,
// construir a arvore e encadear. Acontece uma vez por janela, e precisa caber com folga
// dentro dela.
func BenchmarkSeal(b *testing.B) {
	for _, n := range janelas {
		folhas := loteDe(n)
		b.Run(fmt.Sprintf("folhas=%d", n), func(b *testing.B) {
			b.ReportAllocs()
			b.ResetTimer()
			for i := 0; i < b.N; i++ {
				log := NewLog()
				lote := fmt.Sprintf("janela-%d", i)
				if _, err := log.Expect(lote, n); err != nil {
					b.Fatal(err)
				}
				for _, f := range folhas {
					if _, err := log.Add(lote, f); err != nil {
						b.Fatal(err)
					}
				}
			}
		})
	}
}

// BenchmarkLookup mede a consulta que o eleitor faz: achar a arvore e gerar a prova.
func BenchmarkLookup(b *testing.B) {
	for _, n := range janelas {
		folhas := loteDe(n)
		log := NewLog()
		log.Expect("janela", n)
		for _, f := range folhas {
			log.Add("janela", f)
		}
		b.Run(fmt.Sprintf("folhas=%d", n), func(b *testing.B) {
			b.ReportAllocs()
			b.ResetTimer()
			for i := 0; i < b.N; i++ {
				if _, _, err := log.Lookup(folhas[i%n].Key); err != nil {
					b.Fatal(err)
				}
			}
		})
	}
}
