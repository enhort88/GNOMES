package com.enhort.gnomes.game.model;

public enum HazardType {
    COLLAPSE("ОБВАЛ"),
    PIT("ПРОВАЛ"),
    LAVA("ЛАВА"),
    FLOOD("ПОТОК ВОДЫ");

    public final String title;

    HazardType(String title) {
        this.title = title;
    }
}
