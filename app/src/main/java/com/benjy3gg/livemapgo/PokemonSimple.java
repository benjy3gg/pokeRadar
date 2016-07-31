package com.benjy3gg.livemapgo;

public class PokemonSimple {
    long timestampHidden;
    String encounterId;
    double latitude;
    double longitude;
    int pokemonid;
    String name;
    int notificationId;

    public PokemonSimple() {

    }

    public PokemonSimple(long timestampHidden, String encounterId, double latitude, double longitude, int pokemonid, String name) {
        this.timestampHidden = timestampHidden;
        this.encounterId = encounterId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.pokemonid = pokemonid;
        this.name = name;
        this.notificationId = -1;
    }

    public void setNotificationId(int notificationId) {
        this.notificationId = notificationId;
    }
}
