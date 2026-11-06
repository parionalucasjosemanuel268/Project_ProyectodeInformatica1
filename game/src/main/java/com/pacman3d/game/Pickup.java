package com.pacman3d.game;

/** Objeto en el suelo (se usa automaticamente al tocarlo). */
public final class Pickup {
    public static final int AMMO = 0, MED = 1, STAR = 2, KEY = 3;
    public int kind, amount;
    public float x, z, phase;
    public boolean taken;
}
