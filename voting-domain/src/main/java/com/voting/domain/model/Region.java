package com.voting.domain.model;

/**
 * Localidade do eleitor. Estado e normalizado em maiusculas porque e chave de
 * agregacao: "sp" e "SP" precisam somar no mesmo balde.
 */
public record Region(String state, String city) {

    public Region {
        state = Identifiers.required(state, "state").toUpperCase();
        city = Identifiers.required(city, "city");
    }

    public static Region of(String state, String city) {
        return new Region(state, city);
    }

    /** Chave de agregacao por cidade, qualificada pelo estado para evitar homonimos. */
    public String cityKey() {
        return state + "/" + city;
    }
}
