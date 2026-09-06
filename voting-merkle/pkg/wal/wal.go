// Package wal e um log append-only duravel em disco, de registros opacos.
//
// Como o pacote merkle, nao sabe o que guarda: recebe []byte e devolve []byte na mesma
// ordem. Quem usa decide o que cada registro significa.
//
// O que ele garante, e que e o motivo de existir em vez de um arquivo simples:
//
//   - Uma escrita interrompida no meio nao contamina o log. Cada registro carrega tamanho e
//     CRC; na abertura, um rabo torto (a queda no meio de um append) e truncado, e os
//     registros anteriores continuam legiveis.
//
//   - O arquivo tem identidade propria, sorteada na criacao. Quem retoma consegue distinguir
//     "meu estado continua aqui" de "este volume e novo" - a diferenca entre resumir de um
//     offset salvo e reconstruir tudo do inicio.
//
//   - Sync e explicito. Escrever e barato e bufferizado; a decisao de quando pagar o fsync e
//     de quem chama, que e quem sabe a qual efeito externo essa durabilidade esta amarrada.
package wal

import (
	"bufio"
	"crypto/rand"
	"encoding/binary"
	"encoding/hex"
	"errors"
	"fmt"
	"hash/crc32"
	"io"
	"os"
	"path/filepath"
)

const (
	magic         = "MERKLWAL"
	formatVersion = 1

	headerSize = 8 + 4 + 4 + idSize // magic | versao | reservado | id
	idSize     = 16

	recordHeaderSize = 4 + 4 // tamanho | crc

	// maxRecordSize existe para que um tamanho corrompido nao vire uma alocacao de
	// gigabytes antes do CRC ter chance de reprovar o registro.
	maxRecordSize = 16 << 20
)

// ErrCorrompido indica um log ilegivel - cabecalho invalido ou registro que nao e apenas um
// rabo torto no fim do arquivo.
var ErrCorrompido = errors.New("wal: log corrompido")

var crcTable = crc32.MakeTable(crc32.Castagnoli)

// Log e um arquivo de registros append-only. Nao e seguro para uso concorrente: quem chama
// serializa os appends (no nosso caso, o mutex do checkpoint.Log).
type Log struct {
	file   *os.File
	buf    *bufio.Writer
	id     string
	offset int64 // fim do ultimo registro integro
}

// Open abre o log no caminho dado, criando-o se nao existir.
//
// Na abertura de um arquivo existente, todos os registros sao verificados e o arquivo e
// truncado no ultimo integro. Use Replay para ler o conteudo antes de qualquer append.
func Open(path string) (*Log, error) {
	if dir := filepath.Dir(path); dir != "" {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return nil, fmt.Errorf("wal: criando %s: %w", dir, err)
		}
	}

	file, err := os.OpenFile(path, os.O_RDWR|os.O_CREATE, 0o644)
	if err != nil {
		return nil, fmt.Errorf("wal: abrindo %s: %w", path, err)
	}

	info, err := file.Stat()
	if err != nil {
		file.Close()
		return nil, fmt.Errorf("wal: lendo %s: %w", path, err)
	}

	l := &Log{file: file}
	if info.Size() < headerSize {
		// Arquivo novo (ou um cabecalho que nem chegou a ser escrito inteiro): comeca do
		// zero, com identidade nova.
		if err := l.writeHeader(); err != nil {
			file.Close()
			return nil, err
		}
	} else if err := l.readHeader(); err != nil {
		file.Close()
		return nil, err
	}

	if err := l.recover(info.Size()); err != nil {
		file.Close()
		return nil, err
	}

	if _, err := file.Seek(l.offset, io.SeekStart); err != nil {
		file.Close()
		return nil, fmt.Errorf("wal: posicionando %s: %w", path, err)
	}
	l.buf = bufio.NewWriterSize(file, 64<<10)
	return l, nil
}

// ID e a identidade sorteada quando o arquivo foi criado, em hexadecimal.
//
// Sobrevive a restarts enquanto o arquivo sobreviver, e some junto com ele. E o que permite
// amarrar um progresso externo (um offset de consumo, por exemplo) a *este* estado local.
func (l *Log) ID() string { return l.id }

func (l *Log) writeHeader() error {
	cabecalho := make([]byte, headerSize)
	copy(cabecalho, magic)
	binary.BigEndian.PutUint32(cabecalho[8:], formatVersion)
	if _, err := rand.Read(cabecalho[16:]); err != nil {
		return fmt.Errorf("wal: sorteando identidade: %w", err)
	}
	if _, err := l.file.WriteAt(cabecalho, 0); err != nil {
		return fmt.Errorf("wal: escrevendo cabecalho: %w", err)
	}
	if err := l.file.Truncate(headerSize); err != nil {
		return fmt.Errorf("wal: truncando cabecalho: %w", err)
	}
	if err := l.file.Sync(); err != nil {
		return fmt.Errorf("wal: sincronizando cabecalho: %w", err)
	}
	l.id = hex.EncodeToString(cabecalho[16:])
	l.offset = headerSize
	return nil
}

func (l *Log) readHeader() error {
	cabecalho := make([]byte, headerSize)
	if _, err := l.file.ReadAt(cabecalho, 0); err != nil {
		return fmt.Errorf("wal: lendo cabecalho: %w", err)
	}
	if string(cabecalho[:8]) != magic {
		return fmt.Errorf("%w: nao e um log deste formato", ErrCorrompido)
	}
	if versao := binary.BigEndian.Uint32(cabecalho[8:]); versao != formatVersion {
		return fmt.Errorf("%w: versao %d desconhecida", ErrCorrompido, versao)
	}
	l.id = hex.EncodeToString(cabecalho[16:])
	l.offset = headerSize
	return nil
}

// recover varre os registros e fixa o fim do log no ultimo integro.
//
// A distincao que ele faz e a unica interessante aqui: um registro que acaba *antes* do que
// promete e o rastro de uma queda no meio de um append - o arquivo foi cortado, os bytes
// nunca chegaram, e a operacao perdida sera reentregue pela fonte. Truncar ali e correto.
//
// Um registro completo cujo CRC nao fecha e outra coisa: os bytes chegaram e estao errados.
// Isso e corrupcao, e vira erro. Trata-lo como rabo torto seria descartar em silencio tudo
// dali para frente - a falha mais cara possivel num log que existe para provar o passado.
func (l *Log) recover(size int64) error {
	off := int64(headerSize)
	leitor := bufio.NewReaderSize(io.NewSectionReader(l.file, headerSize, size-headerSize), 64<<10)
	cabecalho := make([]byte, recordHeaderSize)

	for off < size {
		if _, err := io.ReadFull(leitor, cabecalho); err != nil {
			if errors.Is(err, io.EOF) {
				break // fim limpo
			}
			break // cabecalho pela metade: rabo torto
		}
		tamanho := binary.BigEndian.Uint32(cabecalho[:4])
		if tamanho > maxRecordSize {
			return fmt.Errorf("%w: registro anuncia %d bytes no offset %d", ErrCorrompido, tamanho, off)
		}
		payload := make([]byte, tamanho)
		if _, err := io.ReadFull(leitor, payload); err != nil {
			break // payload cortado: rabo torto
		}
		if crc32.Checksum(payload, crcTable) != binary.BigEndian.Uint32(cabecalho[4:]) {
			return fmt.Errorf("%w: CRC nao confere no offset %d", ErrCorrompido, off)
		}
		off += recordHeaderSize + int64(tamanho)
	}

	l.offset = off
	if off == size {
		return nil
	}
	if err := l.file.Truncate(off); err != nil {
		return fmt.Errorf("wal: truncando rabo torto: %w", err)
	}
	return l.file.Sync()
}

// Append acrescenta um registro. O dado fica em buffer ate Sync.
func (l *Log) Append(payload []byte) error {
	if len(payload) > maxRecordSize {
		return fmt.Errorf("wal: registro de %d bytes excede o maximo de %d", len(payload), maxRecordSize)
	}
	var cabecalho [recordHeaderSize]byte
	binary.BigEndian.PutUint32(cabecalho[:4], uint32(len(payload)))
	binary.BigEndian.PutUint32(cabecalho[4:], crc32.Checksum(payload, crcTable))

	if _, err := l.buf.Write(cabecalho[:]); err != nil {
		return fmt.Errorf("wal: escrevendo registro: %w", err)
	}
	if _, err := l.buf.Write(payload); err != nil {
		return fmt.Errorf("wal: escrevendo registro: %w", err)
	}
	l.offset += recordHeaderSize + int64(len(payload))
	return nil
}

// Sync leva ao disco tudo que foi acrescentado ate agora.
//
// Depois que Sync retorna sem erro, os registros sobrevivem a uma queda do processo e da
// maquina. E o unico ponto em que essa promessa existe.
func (l *Log) Sync() error {
	if err := l.buf.Flush(); err != nil {
		return fmt.Errorf("wal: descarregando buffer: %w", err)
	}
	if err := l.file.Sync(); err != nil {
		return fmt.Errorf("wal: sincronizando: %w", err)
	}
	return nil
}

// Replay entrega os registros ja gravados, do mais antigo ao mais recente.
//
// O slice passado ao callback e reaproveitado entre registros: quem precisar guarda-lo copia.
func (l *Log) Replay(visitar func([]byte) error) error {
	if err := l.buf.Flush(); err != nil {
		return fmt.Errorf("wal: descarregando buffer: %w", err)
	}

	leitor := bufio.NewReaderSize(io.NewSectionReader(l.file, headerSize, l.offset-headerSize), 64<<10)
	cabecalho := make([]byte, recordHeaderSize)
	payload := make([]byte, 0, 512)

	for {
		if _, err := io.ReadFull(leitor, cabecalho); err != nil {
			if errors.Is(err, io.EOF) {
				return nil
			}
			return fmt.Errorf("%w: cabecalho de registro truncado: %v", ErrCorrompido, err)
		}
		tamanho := int(binary.BigEndian.Uint32(cabecalho[:4]))
		if tamanho > maxRecordSize {
			return fmt.Errorf("%w: registro de %d bytes", ErrCorrompido, tamanho)
		}
		if cap(payload) < tamanho {
			payload = make([]byte, tamanho)
		}
		payload = payload[:tamanho]
		if _, err := io.ReadFull(leitor, payload); err != nil {
			return fmt.Errorf("%w: registro truncado: %v", ErrCorrompido, err)
		}
		if crc32.Checksum(payload, crcTable) != binary.BigEndian.Uint32(cabecalho[4:]) {
			return fmt.Errorf("%w: CRC nao confere", ErrCorrompido)
		}
		if err := visitar(payload); err != nil {
			return err
		}
	}
}

// Empty diz se o log nao tem registro nenhum - arquivo recem-criado.
func (l *Log) Empty() bool { return l.offset == headerSize }

// Size e o tamanho do log em bytes, cabecalho incluido.
func (l *Log) Size() int64 { return l.offset }

// Close sincroniza e fecha o arquivo.
func (l *Log) Close() error {
	if err := l.Sync(); err != nil {
		l.file.Close()
		return err
	}
	return l.file.Close()
}
