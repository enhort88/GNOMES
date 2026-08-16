package com.enhort.gnomes.game.entities;

import com.enhort.gnomes.game.model.HazardType;

import java.util.HashSet;
import java.util.Set;

public class Hazard {
    public final HazardType type;
    public final float x;
    public final float y;
    public final float radius;
    public final float duration;
    public float age;
    public boolean triggered;
    public final Set<Integer> affectedGnomes = new HashSet<>();

    public Hazard(HazardType type, float x, float y, float radius, float duration) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.duration = duration;
    }
}
