package com.pacman3d.game;

/** Bala, flecha o granada. */
public final class Projectile {
    public static final int BULLET = 0, ARROW = 1, GRENADE = 2;
    public int kind, owner;
    public float x, y, z, vx, vy, vz, dmg, life, fuse, spin;
    public boolean dead;
}
