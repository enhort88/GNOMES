package com.enhort.gnomes.game.model;

public enum EnemyType {
    IMP("Бес", 18f, 46f, 1.0f, 0xFFD34D3F, 16),
    DEMON("Демон", 75f, 38f, 2.4f, 0xFFA52F2F, 22),
    STONE_GOLEM("Каменный голем", 260f, 22f, 5.0f, 0xFF7D766A, 29),
    WATER_GOLEM("Водный голем", 320f, 25f, 5.0f, 0xFF4BA3D9, 29),
    FIRE_GOLEM("Огненный голем", 360f, 27f, 6.0f, 0xFFE9652C, 29),
    IMP_KING("Король бесов", 1400f, 25f, 10f, 0xFFE2523E, 38),
    DEMON_KING("Король демонов", 3600f, 23f, 18f, 0xFF8A1F28, 44),
    ELEMENTAL_KING("Король элементалей", 9000f, 21f, 30f, 0xFF7F63D8, 49);

    public final String title;
    public final float hp;
    public final float moveSpeed;
    public final float contactPower;
    public final int color;
    public final float size;

    EnemyType(String title, float hp, float moveSpeed, float contactPower, int color, float size) {
        this.title = title;
        this.hp = hp;
        this.moveSpeed = moveSpeed;
        this.contactPower = contactPower;
        this.color = color;
        this.size = size;
    }
}
