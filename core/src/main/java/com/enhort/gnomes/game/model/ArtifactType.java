package com.enhort.gnomes.game.model;

public enum ArtifactType {
    ANCESTOR_HELMET("Шлем предков", "Защита от опасностей", 0xFFF0B85A),
    DEMON_FANG("Клык демона", "Урон врагам", 0xFFE25C45),
    HEART_OF_MOUNTAIN("Сердце горы", "Подавляет регенерацию", 0xFF8A5CFF),
    DEPTH_BOOTS("Сапоги глубин", "Скорость гномов", 0xFF65BDE8);

    public final String title;
    public final String description;
    public final int color;

    ArtifactType(String title, String description, int color) {
        this.title = title;
        this.description = description;
        this.color = color;
    }
}
