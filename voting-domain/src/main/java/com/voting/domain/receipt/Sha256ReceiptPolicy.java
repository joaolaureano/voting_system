package com.voting.domain.receipt;

import com.voting.domain.model.Vote;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Recibo = SHA-256(electionId | voterId | candidateId | castAt | pepper).
 *
 * <p>O <em>pepper</em> e um segredo do servidor, injetado pela borda. Sem ele, o espaco de
 * candidatos e pequeno o bastante para que quem conheca o voterId reconstrua o hash por
 * forca bruta e descubra em quem a pessoa votou. Ver a nota de privacidade no README: a
 * fase da Merkle Tree deve migrar para um commitment com nonce aleatorio por voto.
 */
public final class Sha256ReceiptPolicy implements ReceiptPolicy {

    private static final char FIELD_SEPARATOR = '|';
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final String pepper;

    public Sha256ReceiptPolicy(String pepper) {
        this.pepper = Objects.requireNonNull(pepper, "pepper");
        if (pepper.isBlank()) {
            throw new IllegalArgumentException("pepper nao pode ser vazio");
        }
    }

    @Override
    public VoteReceipt issueFor(Vote vote) {
        Objects.requireNonNull(vote, "vote");
        String payload = new StringBuilder()
                .append(vote.electionId().value()).append(FIELD_SEPARATOR)
                .append(vote.voterId().value()).append(FIELD_SEPARATOR)
                .append(vote.candidateId().value()).append(FIELD_SEPARATOR)
                .append(vote.castAt().toEpochMilli()).append(FIELD_SEPARATOR)
                .append(pepper)
                .toString();
        return new VoteReceipt(toHex(digest(payload)));
    }

    private static byte[] digest(String payload) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel nesta JVM", e);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            out[i * 2] = HEX[b >>> 4];
            out[i * 2 + 1] = HEX[b & 0x0F];
        }
        return new String(out);
    }
}
