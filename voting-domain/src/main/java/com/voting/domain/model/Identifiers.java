package com.voting.domain.model;

/**
 * Utilitario interno de validacao dos value objects de identidade.
 * Nao faz parte da linguagem ubiqua: e um detalhe de implementacao do pacote.
 */
final class Identifiers {

    private Identifiers() {
    }

    static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " nao pode ser vazio");
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException(field + " excede 128 caracteres");
        }
        return normalized;
    }
}
