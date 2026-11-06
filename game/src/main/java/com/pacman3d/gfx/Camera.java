package com.pacman3d.gfx;

/** Camara en primera persona. Sistema: x derecha, y arriba, z adelante (yaw 0). */
public final class Camera {
    public float x, y, z, yaw, pitch;
    public float fovY = (float) Math.toRadians(68);
    // base de la camara (calculada en update())
    public float fx, fy, fz, rx, ry, rz, ux, uy, uz;

    public void update() {
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        fx = sy * cp; fy = sp; fz = cy * cp;
        rx = cy; ry = 0; rz = -sy;
        ux = -sy * sp; uy = cp; uz = -cy * sp;
    }
}
