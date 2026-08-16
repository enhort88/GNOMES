package com.enhort.gnomes.game.entities;

import com.enhort.gnomes.game.model.EnemyType;

public class Enemy {
    public final EnemyType type;
    public float x;
    public float y;
    public float hp;
    public float maxHp;
    public float attackCooldown;
    public float summonCooldown;
    public float phase;

    public Enemy(EnemyType type, float x, float y, float phase) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.maxHp = type.hp;
        this.hp = maxHp;
        this.phase = phase;
        this.summonCooldown = 5f;
    }

    public boolean isAlive() {
        return hp > 0f;
    }
}
