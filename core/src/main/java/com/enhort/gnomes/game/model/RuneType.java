package com.enhort.gnomes.game.model;

public enum RuneType {
    MINING("Руна кирки", "Добыча", 0xFFF0B85A, 0.08f),
    GREED("Руна жадности", "Больше руды", 0xFFE0B33E, 0.06f),
    WAR("Руна войны", "Урон врагам", 0xFFE25C45, 0.10f),
    HASTE("Руна ветра", "Скорость", 0xFF65BDE8, 0.05f),
    WARD("Руна щита", "Выживаемость", 0xFF78C98B, 0.04f),
    FRACTURE("Руна раскола", "Против регенерации", 0xFF9B72E8, 0.07f);

    public final String title;
    public final String description;
    public final int color;
    public final float effectPerLevel;

    RuneType(String title, String description, int color, float effectPerLevel) {
        this.title = title;
        this.description = description;
        this.color = color;
        this.effectPerLevel = effectPerLevel;
    }
}
