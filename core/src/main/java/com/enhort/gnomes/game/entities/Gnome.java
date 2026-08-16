package com.enhort.gnomes.game.entities;

import com.enhort.gnomes.game.model.GnomeTier;
import com.enhort.gnomes.game.model.RockType;

public class Gnome {
    private static int NEXT_ID = 1;

    public final int id = NEXT_ID++;
    public GnomeTier tier;
    public float x;
    public float y;
    public float vx;
    public float vy;
    public float phase;
    public float attackCooldown;
    public float stunTime;
    public Rock targetRock;
    public Enemy targetEnemy;

    public double cargoStone;
    public double cargoSilver;
    public double cargoGold;
    public double cargoDiamond;

    public Gnome(GnomeTier tier, float x, float y, float phase) {
        this.tier = tier;
        this.x = x;
        this.y = y;
        this.phase = phase;
    }

    public boolean isStunned() {
        return stunTime > 0f;
    }

    public boolean hasCargo() {
        return cargoLoad() > 0.0001;
    }

    public double cargoLoad() {
        return cargoStone + cargoSilver + cargoGold + cargoDiamond;
    }

    public void addCargo(RockType.Material material, double amount) {
        switch (material) {
            case STONE -> cargoStone += amount;
            case SILVER -> cargoSilver += amount;
            case GOLD -> cargoGold += amount;
            case DIAMOND -> cargoDiamond += amount;
        }
    }

    public void clearCargo() {
        cargoStone = 0;
        cargoSilver = 0;
        cargoGold = 0;
        cargoDiamond = 0;
    }
}
