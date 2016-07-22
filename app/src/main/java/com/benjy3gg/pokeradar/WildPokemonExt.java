package com.benjy3gg.pokeradar;

import POGOProtos.Map.Pokemon.WildPokemonOuterClass;
import POGOProtos.Map.Pokemon.WildPokemonOuterClass.WildPokemon;

/**
 * Created by bkopp on 22/07/16.
 */

public class WildPokemonExt {

    public WildPokemon pokemon;
    public long timestampHidden;

    public WildPokemonExt(WildPokemon pokemon, long timestampHidden) {
        this.pokemon = pokemon;
        this.timestampHidden = timestampHidden;
    }

}
