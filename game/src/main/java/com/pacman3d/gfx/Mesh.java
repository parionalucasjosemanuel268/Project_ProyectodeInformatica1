package com.pacman3d.gfx;

/** Malla de triangulos con color por vertice (luz horneada). */
public final class Mesh {
    public static final int EMISSIVE = 1;   // ignora lampara y luz: brilla solo
    public static final int NOZWRITE = 2;   // no escribe profundidad
    public static final int ADDITIVE = 4;   // suma al fondo (brillos, particulas)
    public static final int DYNLIT = 8;
    public static final int NOFOG = 32;     // ignora la niebla (rayos de luz lejanos)
    public static final int SHADOW = 16;    // oscurece lo que hay debajo (sombras)     // iluminada por vertice con normales (entidades)

    public float[] pos, uv, nrm, col;
    public int[] idx;
    public int vcount, tcount;
    public Texture tex;
    public int flags;
    public float alpha = 1f;
    public float bx, by, bz, br; // esfera envolvente

    /** Copia que comparte topologia pero tiene posiciones propias (para animar). */
    public Mesh copyShape() {
        Mesh m = new Mesh();
        m.pos = pos.clone(); m.uv = uv; m.nrm = nrm.clone(); m.col = col; m.idx = idx;
        m.vcount = vcount; m.tcount = tcount; m.tex = tex; m.flags = flags; m.alpha = alpha;
        m.bx = bx; m.by = by; m.bz = bz; m.br = br;
        return m;
    }

    public void computeBounds() {
        if (vcount == 0) return;
        float x0 = 1e30f, y0 = 1e30f, z0 = 1e30f, x1 = -1e30f, y1 = -1e30f, z1 = -1e30f;
        for (int i = 0; i < vcount; i++) {
            float x = pos[i * 3], y = pos[i * 3 + 1], z = pos[i * 3 + 2];
            if (x < x0) x0 = x;
            if (x > x1) x1 = x;
            if (y < y0) y0 = y;
            if (y > y1) y1 = y;
            if (z < z0) z0 = z;
            if (z > z1) z1 = z;
        }
        bx = (x0 + x1) / 2; by = (y0 + y1) / 2; bz = (z0 + z1) / 2;
        float dx = x1 - bx, dy = y1 - by, dz = z1 - bz;
        br = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
