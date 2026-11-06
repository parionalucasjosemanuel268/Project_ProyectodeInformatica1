package com.pacman3d.gfx;

import java.util.Arrays;

/** Constructor de mallas con pila de transformaciones y primitivas 3D. */
public final class MeshBuilder {
    private float[] pos = new float[3 * 256], uv = new float[2 * 256], nrm = new float[3 * 256], col = new float[3 * 256];
    private int[] idx = new int[3 * 256];
    private int vc, tc;
    private final Texture tex;
    private final int flags;
    private final float alpha;

    // transformacion actual: 3x3 (m[0..8]) + traslacion (m[9..11]); fila-mayor
    private float[] m = {1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0};
    private final float[][] stack = new float[32][];
    private int sp;

    public float cr = 1, cg = 1, cb = 1;  // color actual
    public float shade = 1f;              // sombreado simple por cara

    public MeshBuilder(Texture tex, int flags, float alpha) {
        this.tex = tex; this.flags = flags; this.alpha = alpha;
    }

    public MeshBuilder(Texture tex) { this(tex, 0, 1f); }

    public MeshBuilder color(float r, float g, float b) { cr = r; cg = g; cb = b; return this; }

    public int vertexCount() { return vc; }

    // ---------------- transformaciones ----------------
    public MeshBuilder push() { stack[sp++] = m.clone(); return this; }
    public MeshBuilder pop() { m = stack[--sp]; return this; }
    public MeshBuilder identity() { m = new float[]{1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0}; return this; }

    public MeshBuilder translate(float x, float y, float z) {
        m[9] += m[0] * x + m[1] * y + m[2] * z;
        m[10] += m[3] * x + m[4] * y + m[5] * z;
        m[11] += m[6] * x + m[7] * y + m[8] * z;
        return this;
    }

    public MeshBuilder scale(float x, float y, float z) {
        m[0] *= x; m[3] *= x; m[6] *= x;
        m[1] *= y; m[4] *= y; m[7] *= y;
        m[2] *= z; m[5] *= z; m[8] *= z;
        return this;
    }

    private void mulRot(float[] r) {
        float[] o = new float[9];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                o[i * 3 + j] = m[i * 3] * r[j] + m[i * 3 + 1] * r[3 + j] + m[i * 3 + 2] * r[6 + j];
        System.arraycopy(o, 0, m, 0, 9);
    }

    public MeshBuilder rotX(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        mulRot(new float[]{1, 0, 0, 0, c, -s, 0, s, c});
        return this;
    }

    public MeshBuilder rotY(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        mulRot(new float[]{c, 0, s, 0, 1, 0, -s, 0, c});
        return this;
    }

    public MeshBuilder rotZ(float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        mulRot(new float[]{c, -s, 0, s, c, 0, 0, 0, 1});
        return this;
    }

    // ---------------- vertices ----------------
    public int v(float x, float y, float z, float u, float vv, float nx, float ny, float nz) {
        return v(x, y, z, u, vv, nx, ny, nz, 1f);
    }

    public int v(float x, float y, float z, float u, float vv, float nx, float ny, float nz, float light) {
        if (vc == pos.length / 3) grow();
        int i = vc++;
        pos[i * 3] = m[0] * x + m[1] * y + m[2] * z + m[9];
        pos[i * 3 + 1] = m[3] * x + m[4] * y + m[5] * z + m[10];
        pos[i * 3 + 2] = m[6] * x + m[7] * y + m[8] * z + m[11];
        float wx = m[0] * nx + m[1] * ny + m[2] * nz, wy = m[3] * nx + m[4] * ny + m[5] * nz, wz = m[6] * nx + m[7] * ny + m[8] * nz;
        float l = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
        if (l > 1e-6f) { wx /= l; wy /= l; wz /= l; }
        nrm[i * 3] = wx; nrm[i * 3 + 1] = wy; nrm[i * 3 + 2] = wz;
        uv[i * 2] = u; uv[i * 2 + 1] = vv;
        float s = shade * light;
        col[i * 3] = cr * s; col[i * 3 + 1] = cg * s; col[i * 3 + 2] = cb * s;
        return i;
    }

    /** Vertice con color explicito (luz horneada), ignora color/shade actuales. */
    public int vc(float x, float y, float z, float u, float vv, float nx, float ny, float nz, float r, float g, float b) {
        float or = cr, og = cg, ob = cb, os = shade;
        cr = r; cg = g; cb = b; shade = 1f;
        int i = v(x, y, z, u, vv, nx, ny, nz);
        cr = or; cg = og; cb = ob; shade = os;
        return i;
    }

    public void tri(int a, int b, int c) {
        if (tc * 3 + 3 > idx.length) idx = Arrays.copyOf(idx, idx.length * 2);
        idx[tc * 3] = a; idx[tc * 3 + 1] = b; idx[tc * 3 + 2] = c;
        tc++;
    }

    public void quad(int a, int b, int c, int d) { tri(a, b, c); tri(a, c, d); }

    private void grow() {
        int n = pos.length / 3 * 2;
        pos = Arrays.copyOf(pos, n * 3); uv = Arrays.copyOf(uv, n * 2);
        nrm = Arrays.copyOf(nrm, n * 3); col = Arrays.copyOf(col, n * 3);
    }

    /** Cuadrilatero plano con normal calculada. Puntos en orden. */
    public void quadP(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                      float x3, float y3, float z3, float u0, float v0, float u1, float v1) {
        float ax = x1 - x0, ay = y1 - y0, az = z1 - z0, bx = x3 - x0, by = y3 - y0, bz = z3 - z0;
        float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
        int a = v(x0, y0, z0, u0, v1, nx, ny, nz), b = v(x1, y1, z1, u1, v1, nx, ny, nz),
            c = v(x2, y2, z2, u1, v0, nx, ny, nz), d = v(x3, y3, z3, u0, v0, nx, ny, nz);
        quad(a, b, c, d);
    }

    /** Caja centrada en el origen local con tamano total sx,sy,sz. */
    public MeshBuilder box(float sx, float sy, float sz) {
        float x = sx / 2, y = sy / 2, z = sz / 2;
        float os = shade;
        shade = os * 1.0f; quadP(-x, y, -z, -x, y, z, x, y, z, x, y, -z, 0, 0, 1, 1);
        shade = os * 0.45f; quadP(-x, -y, z, -x, -y, -z, x, -y, -z, x, -y, z, 0, 0, 1, 1);
        shade = os * 0.82f; quadP(-x, -y, z, x, -y, z, x, y, z, -x, y, z, 0, 0, 1, 1);
        shade = os * 0.70f; quadP(x, -y, -z, -x, -y, -z, -x, y, -z, x, y, -z, 0, 0, 1, 1);
        shade = os * 0.62f; quadP(x, -y, z, x, -y, -z, x, y, -z, x, y, z, 0, 0, 1, 1);
        shade = os * 0.76f; quadP(-x, -y, -z, -x, -y, z, -x, y, z, -x, y, -z, 0, 0, 1, 1);
        shade = os;
        return this;
    }

    /** Cilindro a lo largo de Y de -h/2 a h/2. radio r0 abajo, r1 arriba. */
    public MeshBuilder cylinder(float r0, float r1, float h, int seg, boolean caps) {
        float os = shade;
        float slope = (r0 - r1) / h;
        int[] lo = new int[seg + 1], hi = new int[seg + 1];
        for (int i = 0; i <= seg; i++) {
            float a = (float) (i * 2 * Math.PI / seg), c = (float) Math.cos(a), s = (float) Math.sin(a);
            float u = (float) i / seg;
            lo[i] = v(c * r0, -h / 2, s * r0, u, 1, c, slope, s);
            hi[i] = v(c * r1, h / 2, s * r1, u, 0, c, slope, s);
        }
        for (int i = 0; i < seg; i++) quad(lo[i], lo[i + 1], hi[i + 1], hi[i]);
        if (caps) {
            shade = os * 0.95f;
            if (r1 > 0) capDisc(h / 2, r1, seg, 1);
            shade = os * 0.5f;
            if (r0 > 0) capDisc(-h / 2, r0, seg, -1);
            shade = os;
        }
        return this;
    }

    private void capDisc(float y, float r, int seg, int dir) {
        int c0 = v(0, y, 0, 0.5f, 0.5f, 0, dir, 0);
        int prev = -1;
        for (int i = 0; i <= seg; i++) {
            float a = (float) (i * 2 * Math.PI / seg);
            int p = v((float) Math.cos(a) * r, y, (float) Math.sin(a) * r,
                    0.5f + 0.5f * (float) Math.cos(a), 0.5f + 0.5f * (float) Math.sin(a), 0, dir, 0);
            if (prev >= 0) tri(c0, prev, p);
            prev = p;
        }
    }

    public MeshBuilder sphere(float rx, float ry, float rz, int seg, int rings) {
        int[][] g = new int[rings + 1][seg + 1];
        for (int j = 0; j <= rings; j++) {
            float ph = (float) (Math.PI * j / rings - Math.PI / 2);
            float cy = (float) Math.sin(ph), cr2 = (float) Math.cos(ph);
            for (int i = 0; i <= seg; i++) {
                float a = (float) (i * 2 * Math.PI / seg);
                float nx = cr2 * (float) Math.cos(a), nz = cr2 * (float) Math.sin(a);
                g[j][i] = v(nx * rx, cy * ry, nz * rz, (float) i / seg, 1f - (float) j / rings, nx, cy, nz);
            }
        }
        for (int j = 0; j < rings; j++)
            for (int i = 0; i < seg; i++)
                quad(g[j][i], g[j][i + 1], g[j + 1][i + 1], g[j + 1][i]);
        return this;
    }

    public MeshBuilder cone(float r, float h, int seg) { return cylinder(r, 0f, h, seg, true); }

    public Mesh build() {
        Mesh o = new Mesh();
        o.vcount = vc; o.tcount = tc;
        o.pos = Arrays.copyOf(pos, vc * 3); o.uv = Arrays.copyOf(uv, vc * 2);
        o.nrm = Arrays.copyOf(nrm, vc * 3); o.col = Arrays.copyOf(col, vc * 3);
        o.idx = Arrays.copyOf(idx, tc * 3);
        o.tex = tex; o.flags = flags; o.alpha = alpha;
        o.computeBounds();
        return o;
    }
}
