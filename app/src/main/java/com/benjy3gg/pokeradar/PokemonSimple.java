package com.benjy3gg.pokeradar;

/**
 * Created by benjy3gg on 23.07.2016.
 */

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
