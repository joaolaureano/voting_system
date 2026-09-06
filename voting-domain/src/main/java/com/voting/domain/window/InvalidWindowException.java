package com.voting.domain.window;

import com.voting.domain.DomainException;

/**
 * A janela de apuracao nao descreve um intervalo valido.
 *
 * <p>Uma janela degenerada nao e um detalhe: ela e a unidade de commitment da Merkle Tree, e
 * uma janela vazia ou invertida produziria uma raiz que nao corresponde a nenhum conjunto de
 * votos.
 */
public class InvalidWindowException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidWindowException(String message) {
        super("JANELA_INVALIDA", message);
    }
}
