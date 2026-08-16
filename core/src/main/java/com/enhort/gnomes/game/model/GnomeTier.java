package com.enhort.gnomes.game.model;

public enum GnomeTier {
    MINER("Обычный гном", 1.0f, 72f, 0.8f, 0xFF69B9E7, 18, 18f),
    VETERAN("Продвинутый", 4.0f, 78f, 2.0f, 0xFF78C98B, 20, 34f),
    TWIN_PICK("Две кирки", 14.0f, 84f, 5.5f, 0xFFF0B85A, 22, 68f),
    DRILL_RIG("Буровая", 60.0f, 66f, 24.0f, 0xFFD77A45, 26, 160f),
    EXCAVATOR("Лазерный экскаватор", 280.0f, 55f, 110.0f, 0xFFF4D35E, 28, 430f),
    IRON_GOLEM("Лазерный голем", 1500.0f, 48f, 650.0f, 0xFFB7C5D1, 24, 1200f);

    public final String title;
    public final float miningPower;
    public final float moveSpeed;
    public final float combatPower;
    public final int color;
    public final float size;
    public final float cargoCapacity;

    GnomeTier(String title, float miningPower, float moveSpeed, float combatPower, int color, float size, float cargoCapacity) {
        this.title = title;
        this.miningPower = miningPower;
        this.moveSpeed = moveSpeed;
        this.combatPower = combatPower;
        this.color = color;
        this.size = size;
        this.cargoCapacity = cargoCapacity;
    }
}
