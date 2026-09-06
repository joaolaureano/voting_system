package com.voting.domain.receipt;

import java.util.regex.Pattern;

/**
 * Comprovante que o eleitor guarda para conferir, depois, que seu voto entrou na apuracao.
 *
 * <p>E tambem a folha da Merkle Tree planejada para a proxima fase - por isso ja nasce
 * como um digest de tamanho fixo, e nao como um identificador opaco qualquer.
 */
public record VoteReceipt(String hash) {

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    public VoteReceipt {
        if (hash == null || !SHA256_HEX.matcher(hash).matches()) {
            throw new IllegalArgumentException("recibo deve ser um SHA-256 em hex minusculo");
        }
    }

    public static VoteReceipt of(String hash) {
        return new VoteReceipt(hash);
    }

    @Override
    public String toString() {
        return hash;
    }
}
