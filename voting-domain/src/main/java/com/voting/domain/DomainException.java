package com.voting.domain;

import java.util.Objects;

/**
 * Base das falhas que o dominio sabe nomear.
 *
 * <p>Existe para que o dominio recuse coisas no seu proprio vocabulario, em vez de em
 * {@code java.lang}. Um {@code IllegalArgumentException} obriga quem trata a ler a mensagem
 * para saber o que aconteceu; uma excecao nomeada permite decidir pelo tipo - e e isso que
 * deixa a borda mapear cada recusa para o status HTTP certo sem inspecionar texto.
 *
 * <p>Nao estendida: nao e checked. Estas sao violacoes de regra, e obrigar cada chamador a
 * declarar {@code throws} espalharia a regra por toda a pilha em vez de concentra-la aqui.
 */
public abstract class DomainException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    /**
     * Codigo estavel da recusa, pensado para atravessar a fronteira do processo.
     *
     * <p>A mensagem e para humanos e pode mudar; o codigo e contrato. Um cliente que decida
     * pelo texto quebra na primeira correcao de portugues.
     */
    public String code() {
        return code;
    }
}
