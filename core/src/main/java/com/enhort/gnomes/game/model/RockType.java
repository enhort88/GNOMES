package com.enhort.gnomes.game.model;

public enum RockType {
    STONE("Камень", 42f, 0f, 0xFF6B7076, Material.STONE, 9),
    SILVER("Серебро", 135f, 0f, 0xFFBEC7D2, Material.SILVER, 3),
    GOLD("Золото", 420f, 0f, 0xFFD6A936, Material.GOLD, 2),
    DIAMOND("Алмаз", 1650f, 0f, 0xFF67D7F2, Material.DIAMOND, 1),
    OBSIDIAN("Обсидиан", 6200f, 8f, 0xFF4A405D, Material.STONE, 55),
    ANCIENT_CRYSTAL("Древний кристалл", 26000f, 65f, 0xFF8A5CFF, Material.DIAMOND, 5);

    public enum Material { STONE, SILVER, GOLD, DIAMOND }

    public final String title;
    public final float hp;
    public final float regenPerSecond;
    public final int color;
    public final Material material;
    public final int yield;

    RockType(String title, float hp, float regenPerSecond, int color, Material material, int yield) {
        this.title = title;
        this.hp = hp;
        this.regenPerSecond = regenPerSecond;
        this.color = color;
        this.material = material;
        this.yield = yield;
    }
}
