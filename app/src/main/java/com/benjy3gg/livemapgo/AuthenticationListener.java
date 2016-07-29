package com.benjy3gg.livemapgo;

import com.pokegoapi.api.PokemonGo;

/**
 * Created by benjy3gg on 28.07.2016.
 */

public interface AuthenticationListener {
    void onGoAvailable(PokemonGo go);
    void onGoFailed(String reason);
    void onGoNeedsAuthentification();
}
