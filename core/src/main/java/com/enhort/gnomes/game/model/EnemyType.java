package com.enhort.gnomes.game.model;

/**
 * Enemy archetypes. Base numbers are deliberately modest: CaveScreen/GameState scale every archetype
 * with depth and difficulty, so a level-1 imp and a level-20 imp are very different problems.
 */
public enum EnemyType {
    IMP("Бес", 18f, 46f, 1.0f, 0xFFD34D3F, 16, Family.DEMONIC),
    DEMON("Демон", 75f, 38f, 2.4f, 0xFFA52F2F, 22, Family.DEMONIC),
    SUCCUBUS("Суккуб", 125f, 34f, 2.0f, 0xFFC94979, 24, Family.DEMONIC),
    GHOST("Призрак", 92f, 72f, 1.6f, 0xFF8DDDE8, 22, Family.SPIRIT),
    STONE_GOLEM("Каменный голем", 260f, 22f, 5.0f, 0xFF7D766A, 24, Family.ELEMENTAL),
    WATER_GOLEM("Водный голем", 320f, 25f, 5.0f, 0xFF4BA3D9, 24, Family.ELEMENTAL),
    FIRE_GOLEM("Огненный голем", 360f, 27f, 6.0f, 0xFFE9652C, 25, Family.ELEMENTAL),
    IMP_KING("Король бесов", 1400f, 25f, 10f, 0xFFE2523E, 38, Family.BOSS),
    DEMON_KING("Король демонов", 3600f, 23f, 18f, 0xFF8A1F28, 44, Family.BOSS),
    ELEMENTAL_KING("Король элементалей", 9000f, 21f, 30f, 0xFF7F63D8, 49, Family.BOSS);

    public enum Family { DEMONIC, ELEMENTAL, SPIRIT, BOSS }

    public final String title;
    public final float hp;
    public final float moveSpeed;
    public final float contactPower;
    public final int color;
    public final float size;
    public final Family family;

    EnemyType(String title, float hp, float moveSpeed, float contactPower, int color, float size, Family family) {
        this.title = title;
        this.hp = hp;
        this.moveSpeed = moveSpeed;
        this.contactPower = contactPower;
        this.color = color;
        this.size = size;
        this.family = family;
    }

    public boolean isBoss() { return this == IMP_KING || this == DEMON_KING || this == ELEMENTAL_KING; }
    public boolean isImp() { return this == IMP || this == IMP_KING; }
    public boolean isDemon() { return this == DEMON || this == DEMON_KING || this == SUCCUBUS; }
    public boolean isElemental() { return this == STONE_GOLEM || this == WATER_GOLEM || this == FIRE_GOLEM || this == ELEMENTAL_KING; }
    public boolean isGhost() { return this == GHOST; }
    public boolean fliesThroughWalls() { return this == GHOST; }
    public boolean stealsChest() { return this == IMP || this == IMP_KING || this == GHOST; }
    public boolean canCharm() { return this == SUCCUBUS; }
}
