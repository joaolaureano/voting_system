package com.voting.domain.tally;

import com.voting.domain.DomainException;

/**
 * A apuracao violaria uma invariante da contagem.
 *
 * <p>Nunca deveria vir de um voto: a chave sai de {@link TallyDimension} e a contagem so cresce.
 * Se aparecer, o defeito esta no adaptador que reconstroi a apuracao a partir do estado - e
 * uma excecao nomeada e o que torna esse diagnostico imediato.
 */
public class InvalidTallyException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidTallyException(String message) {
        super("APURACAO_INVALIDA", message);
    }
}
