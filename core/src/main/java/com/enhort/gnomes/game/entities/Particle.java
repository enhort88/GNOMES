package com.enhort.gnomes.game.entities;

public class Particle {
    public float x;
    public float y;
    public float vx;
    public float vy;
    public float life;
    public float maxLife;
    public float size;
    public int color;

    public Particle(float x, float y, float vx, float vy, float life, float size, int color) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.life = life;
        this.maxLife = life;
        this.size = size;
        this.color = color;
    }
}
