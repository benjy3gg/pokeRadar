package com.benjy3gg.pokeradar;

public class PokemonSimple {
    long timestampHidden;
    String encounterId;
    double latitude;
    double longitude;
    int pokemonid;
    String name;

    public PokemonSimple() {

    }

    public PokemonSimple(long timestampHidden, String encounterId, double latitude, double longitude, int pokemonid, String name) {
        this.timestampHidden = timestampHidden;
        this.encounterId = encounterId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.pokemonid = pokemonid;
        this.name = name;
    }
}
