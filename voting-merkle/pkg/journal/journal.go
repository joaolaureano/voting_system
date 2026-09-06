// Package journal grava a cadeia de checkpoints num log append-only em disco.
//
// E a implementacao de checkpoint.Store sobre pkg/wal: traduz os tres fatos da cadeia
// (folha, marcador, selo) para registros binarios e de volta.
//
// A escolha de um arquivo, e nao de um banco, e deliberada. O que a cadeia precisa e append
// e leitura sequencial na partida - o padrao de acesso mais barato que existe, e o unico que
// esta estrutura de dados admite: um checkpoint nunca e atualizado nem apagado. Um banco
// relacional cobraria uma dependencia de operacao inteira para servir de arquivo append-only,
// e ainda ofereceria um UPDATE que a cadeia existe para tornar detectavel.
//
// O formato e binario e nao autodescritivo de proposito: e estado interno de um processo, e
// nao um contrato publico. O contrato publico e merkle.roots, que continua sendo JSON.
package journal

import (
	"encoding/binary"
	"fmt"
	"time"

	"github.com/joaolaureano/voting_system/voting-merkle/pkg/checkpoint"
	"github.com/joaolaureano/voting_system/voting-merkle/pkg/merkle"
	"github.com/joaolaureano/voting_system/voting-merkle/pkg/wal"
)

// Journal implementa checkpoint.Store.
type Journal struct {
	wal *wal.Log
	buf []byte
}

// Open abre (ou cria) o diario no caminho dado.
func Open(path string) (*Journal, error) {
	log, err := wal.Open(path)
	if err != nil {
		return nil, err
	}
	return &Journal{wal: log, buf: make([]byte, 0, 512)}, nil
}

// ID e a identidade do arquivo, sorteada quando ele foi criado.
func (j *Journal) ID() string { return j.wal.ID() }

// Empty diz se o diario nunca recebeu um registro.
func (j *Journal) Empty() bool { return j.wal.Empty() }

// Size e o tamanho do diario em bytes.
func (j *Journal) Size() int64 { return j.wal.Size() }

// Append grava um registro. Fica em buffer ate Sync.
func (j *Journal) Append(rec checkpoint.Record) error {
	j.buf = j.buf[:0]
	codificado, err := encode(j.buf, rec)
	if err != nil {
		return err
	}
	j.buf = codificado
	return j.wal.Append(codificado)
}

// Sync leva ao disco o que foi gravado ate agora.
func (j *Journal) Sync() error { return j.wal.Sync() }

// Replay entrega os registros na ordem de gravacao.
func (j *Journal) Replay(visitar func(checkpoint.Record) error) error {
	return j.wal.Replay(func(payload []byte) error {
		rec, err := decode(payload)
		if err != nil {
			return err
		}
		return visitar(rec)
	})
}

// Close sincroniza e fecha o arquivo.
func (j *Journal) Close() error { return j.wal.Close() }

func encode(dst []byte, rec checkpoint.Record) ([]byte, error) {
	dst = append(dst, byte(rec.Kind))
	dst = appendBytes(dst, []byte(rec.BatchID))

	switch rec.Kind {
	case checkpoint.KindLeaf:
		dst = appendBytes(dst, []byte(rec.Leaf.Key))
		dst = appendBytes(dst, rec.Leaf.Data)
	case checkpoint.KindMarker:
		dst = binary.AppendVarint(dst, int64(rec.Count))
	case checkpoint.KindSealed:
		selo := rec.Sealed
		if len(selo.Root) != merkle.HashSize || len(selo.Previous) != merkle.HashSize || len(selo.Hash) != merkle.HashSize {
			return nil, fmt.Errorf("journal: selo de %s com hash de tamanho invalido", rec.BatchID)
		}
		dst = binary.AppendVarint(dst, int64(selo.Sequence))
		dst = binary.AppendVarint(dst, int64(selo.Size))
		dst = append(dst, selo.Root...)
		dst = append(dst, selo.Previous...)
		dst = append(dst, selo.Hash...)
		dst = binary.AppendVarint(dst, selo.SealedAt.UnixNano())
	default:
		return nil, fmt.Errorf("journal: registro de tipo %d desconhecido", rec.Kind)
	}
	return dst, nil
}

func decode(src []byte) (checkpoint.Record, error) {
	if len(src) == 0 {
		return checkpoint.Record{}, fmt.Errorf("journal: registro vazio")
	}

	rec := checkpoint.Record{Kind: checkpoint.Kind(src[0])}
	resto := src[1:]

	batchID, resto, err := readBytes(resto)
	if err != nil {
		return rec, fmt.Errorf("journal: lote ilegivel: %w", err)
	}
	rec.BatchID = string(batchID)

	switch rec.Kind {
	case checkpoint.KindLeaf:
		chave, resto, err := readBytes(resto)
		if err != nil {
			return rec, fmt.Errorf("journal: chave ilegivel: %w", err)
		}
		dados, resto, err := readBytes(resto)
		if err != nil {
			return rec, fmt.Errorf("journal: folha ilegivel: %w", err)
		}
		if len(resto) != 0 {
			return rec, fmt.Errorf("journal: %d bytes sobrando na folha", len(resto))
		}
		// Copia porque o slice do wal e reaproveitado entre registros, e a folha vai
		// direto para o lote em memoria.
		rec.Leaf = checkpoint.Leaf{Key: string(chave), Data: clone(dados)}

	case checkpoint.KindMarker:
		valor, n := binary.Varint(resto)
		if n <= 0 {
			return rec, fmt.Errorf("journal: contagem ilegivel")
		}
		rec.Count = int(valor)

	case checkpoint.KindSealed:
		selo := checkpoint.Sealed{}
		sequencia, n := binary.Varint(resto)
		if n <= 0 {
			return rec, fmt.Errorf("journal: sequencia ilegivel")
		}
		resto = resto[n:]
		tamanho, n := binary.Varint(resto)
		if n <= 0 {
			return rec, fmt.Errorf("journal: tamanho ilegivel")
		}
		resto = resto[n:]
		if len(resto) < 3*merkle.HashSize {
			return rec, fmt.Errorf("journal: selo sem os tres hashes")
		}
		selo.Sequence = int(sequencia)
		selo.Size = int(tamanho)
		selo.Root = clone(resto[:merkle.HashSize])
		selo.Previous = clone(resto[merkle.HashSize : 2*merkle.HashSize])
		selo.Hash = clone(resto[2*merkle.HashSize : 3*merkle.HashSize])
		resto = resto[3*merkle.HashSize:]
		nanos, n := binary.Varint(resto)
		if n <= 0 {
			return rec, fmt.Errorf("journal: instante do selo ilegivel")
		}
		selo.SealedAt = time.Unix(0, nanos).UTC()
		rec.Sealed = selo

	default:
		return rec, fmt.Errorf("journal: registro de tipo %d desconhecido", rec.Kind)
	}
	return rec, nil
}

func appendBytes(dst, valor []byte) []byte {
	dst = binary.AppendUvarint(dst, uint64(len(valor)))
	return append(dst, valor...)
}

func readBytes(src []byte) ([]byte, []byte, error) {
	tamanho, n := binary.Uvarint(src)
	if n <= 0 {
		return nil, nil, fmt.Errorf("tamanho ilegivel")
	}
	src = src[n:]
	if uint64(len(src)) < tamanho {
		return nil, nil, fmt.Errorf("esperava %d bytes, restam %d", tamanho, len(src))
	}
	return src[:tamanho], src[tamanho:], nil
}

func clone(src []byte) []byte {
	saida := make([]byte, len(src))
	copy(saida, src)
	return saida
}
