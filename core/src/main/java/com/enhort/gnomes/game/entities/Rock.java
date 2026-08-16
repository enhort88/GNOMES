package com.enhort.gnomes.game.entities;

import com.enhort.gnomes.game.model.RockType;

public class Rock {
    public final RockType type;
    public final float x;
    public final float y;
    public final float radius;
    public final float maxHp;
    public float hp;
    public boolean destroyed;
    public float respawnDelay;
    public final float[] polygonAngles;
    public final float[] polygonScale;

    public Rock(RockType type, float x, float y, float radius, float[] polygonAngles, float[] polygonScale) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.maxHp = type.hp;
        this.hp = maxHp;
        this.polygonAngles = polygonAngles;
        this.polygonScale = polygonScale;
    }
}
