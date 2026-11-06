package com.pacman3d.game;

/** Alma perdida (NPC): hombre o mujer translucido que huye de los fantasmas. */
public final class Soul {
    public static final int FLEE = 0, DEAD = 1, SAVED = 2;
    public int id, variant, state;
    public boolean female;
    public float x, z, yaw, phase, speed, stateT, runAmt, vx, vz;
    public int tx, tz, nx, nz, dx, dz;
    public float sx, sz, syaw;     // objetivos de interpolacion (cliente)
}
