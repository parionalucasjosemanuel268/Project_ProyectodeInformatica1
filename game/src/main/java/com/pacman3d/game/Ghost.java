package com.pacman3d.game;

/** Fantasma. Tipos como en el Pac-Man original: 0 Blinky, 1 Pinky, 2 Inky, 3 Clyde. */
public final class Ghost {
    public int id, type;
    public float x, z, hp, maxHp, speed, phase, hurtT, atkCd, dyingT, laneSide, offX, offZ, yawVis, bob, screamT;
    public int tx, tz, nx, nz, dx, dz;
    public boolean dying, reversePending, aware, fast;
    public float trail, trailT;
    // objetivos de interpolacion (cliente)
    public float sx, sz, sox, soz, syaw;

    public boolean alive() { return !dying; }
}
