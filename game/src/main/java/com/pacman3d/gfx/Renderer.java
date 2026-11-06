package com.pacman3d.gfx;

import java.util.Arrays;
import java.util.stream.IntStream;

/**
 * Rasterizador 3D por software, 100% Java: proyeccion en perspectiva, recorte,
 * z-buffer, texturas con correccion de perspectiva, niebla, lampara del jugador,
 * transparencias ordenadas y relleno en bandas multihilo.
 */
public final class Renderer {
    public final int W, H;
    public final int[] pixels;
    private final float[] zb;
    public final Camera cam = new Camera();
    private float focal, tanX, tanY;
    private final float cx, cy;

    // ---- parametros por frame ----
    public float lantern = 1f;         // intensidad de la lampara
    public float flash = 0f;           // destello global (disparos, rayos)
    public float flashR = 1f, flashG = 0.9f, flashB = 0.7f;
    public float fogDensity = 0.05f;
    public float fogR = 0.035f, fogG = 0.05f, fogB = 0.07f;
    public float lampR = 1.0f, lampG = 0.78f, lampB = 0.5f;
    public float ambR = 0.30f, ambG = 0.34f, ambB = 0.50f;   // luz de luna para entidades
    public int[] background;           // si no es null reemplaza el cielo
    public Texture sky;
    public float skyDim = 1f;

    private static final int ZN = 2560;
    private static final float ZSCALE = 40f; // posiciones por unidad
    private final float[] keep = new float[ZN], addR = new float[ZN], addG = new float[ZN], addB = new float[ZN];
    private final float[] lR = new float[ZN], lG = new float[ZN], lB = new float[ZN];
    private final float[] zero = new float[ZN];

    // ---- buffers de triangulos ----
    private static final class TriBuf {
        float[] v = new float[24 * 16384];
        int[] tex = new int[16384], flg = new int[16384], alp = new int[16384], y0 = new int[16384], y1 = new int[16384];
        float[] key = new float[16384];
        int n;

        void ensure() {
            if (n >= tex.length) {
                int c = tex.length * 2;
                v = Arrays.copyOf(v, c * 24); tex = Arrays.copyOf(tex, c); flg = Arrays.copyOf(flg, c);
                alp = Arrays.copyOf(alp, c); y0 = Arrays.copyOf(y0, c); y1 = Arrays.copyOf(y1, c); key = Arrays.copyOf(key, c);
            }
        }
    }

    private static final ThreadLocal<float[]> SCRATCH = ThreadLocal.withInitial(() -> new float[18]);
    private final TriBuf opq = new TriBuf(), trn = new TriBuf(), ovl = new TriBuf();
    private int[] transOrder = new int[0];
    private Texture[] texTab = new Texture[0];

    // scratch por vertice
    private float[] vx = new float[4096], vy = new float[4096], vz = new float[4096];
    private float[] vr = new float[4096], vg = new float[4096], vb = new float[4096];
    private int[] vo = new int[4096];
    private final float[] poly = new float[8 * 8], poly2 = new float[8 * 8];

    // columnas/filas precalculadas para el cielo
    private final float[] colAng, rowAng;

    public int trisOpaque, trisTrans;

    public Renderer(int w, int h) {
        W = w; H = h;
        pixels = new int[w * h];
        zb = new float[w * h];
        cx = w / 2f; cy = h / 2f;
        colAng = new float[w]; rowAng = new float[h];
        setFov((float) Math.toRadians(68));
    }

    /** Cambia el campo de vision vertical (zoom de armas). */
    public void setFov(float fovY) {
        if (Math.abs(fovY - cam.fovY) < 1e-4f && focal > 0) return;
        cam.fovY = fovY;
        focal = (H / 2f) / (float) Math.tan(fovY / 2);
        tanX = (W / 2f) / focal; tanY = (H / 2f) / focal;
        for (int x = 0; x < W; x++) colAng[x] = (float) Math.atan((x + 0.5f - cx) / focal);
        for (int y = 0; y < H; y++) rowAng[y] = (float) Math.atan((y + 0.5f - cy) / focal);
    }

    public float focal() { return focal; }

    // =====================================================================
    //  API
    // =====================================================================
    public void begin() {
        cam.update();
        opq.n = 0; trn.n = 0; ovl.n = 0;
        texTab = Texture.table();
        buildTables();
    }

    private void buildTables() {
        float fl = flash;
        for (int i = 0; i < ZN; i++) {
            float z = (i + 0.5f) / ZSCALE;
            float k = (float) Math.exp(-z * fogDensity);
            keep[i] = k;
            float f = (1f - k) * 255f;
            addR[i] = fogR * f; addG[i] = fogG * f; addB[i] = fogB * f;
            float a = 1f / (1f + (z * 0.24f) * (z * 0.24f));
            float la = lantern * a * 1.15f;
            float fa = fl * (0.55f + 0.45f * a);
            lR[i] = la * lampR + fa * flashR;
            lG[i] = la * lampG + fa * flashG;
            lB[i] = la * lampB + fa * flashB;
        }
    }

    /** Dibuja una malla. model = 12 floats (3x3 fila-mayor + traslacion) o null. */
    public void mesh(Mesh m, float[] model, float tr, float tg, float tb) {
        if (m.tcount == 0) return;
        boolean trans = m.alpha < 0.999f || (m.flags & (Mesh.ADDITIVE | Mesh.SHADOW)) != 0;
        emit(m, model, tr, tg, tb, 0, 0, 0, trans ? trn : opq);
    }

    /** Igual que mesh() con luz de objeto (lamparas cercanas) para entidades DYNLIT. */
    public void mesh(Mesh m, float[] model, float tr, float tg, float tb, float lr, float lg, float lb) {
        if (m.tcount == 0) return;
        boolean trans = m.alpha < 0.999f || (m.flags & (Mesh.ADDITIVE | Mesh.SHADOW)) != 0;
        emit(m, model, tr, tg, tb, lr, lg, lb, trans ? trn : opq);
    }

    /** Malla de primera persona (arma/manos): se dibuja encima de todo, sin ser tapada por paredes. */
    public void meshOverlay(Mesh m, float[] model, float tr, float tg, float tb) {
        if (m.tcount == 0) return;
        emit(m, model, tr, tg, tb, 0, 0, 0, ovl);
    }

    /** Chequeo rapido de visibilidad de una esfera en espacio mundo. */
    public boolean visible(float x, float y, float z, float r, float maxDist) {
        float dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        float zc = dx * cam.fx + dy * cam.fy + dz * cam.fz;
        if (zc < -r || zc > maxDist + r) return false;
        float xc = dx * cam.rx + dz * cam.rz, yc = dx * cam.ux + dy * cam.uy + dz * cam.uz;
        float zz = Math.max(zc, 0);
        return Math.abs(xc) <= zz * tanX + r * 1.5f && Math.abs(yc) <= zz * tanY + r * 1.5f;
    }

    /** Sprite orientado a la camara. */
    public void billboard(Texture t, float x, float y, float z, float w, float h, float r, float g, float b, float alpha, int flags) {
        float ddx = x - cam.x, ddy = y - cam.y, ddz = z - cam.z;
        float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
        if (dist < 3f) { alpha *= Math.max(0f, (dist - 0.6f) / 2.4f); if (alpha < 0.01f) return; }
        Mesh q = billMesh;
        q.uv[0] = 0; q.uv[1] = 1; q.uv[2] = 1; q.uv[3] = 1; q.uv[4] = 1; q.uv[5] = 0; q.uv[6] = 0; q.uv[7] = 0;
        q.tex = t; q.flags = flags | Mesh.EMISSIVE; q.alpha = alpha;
        Camera c = cam;
        float hw = w / 2, hh = h / 2;
        float[] p = q.pos;
        float rxx = c.rx * hw, ryy = c.ry * hw, rzz = c.rz * hw, uxx = c.ux * hh, uyy = c.uy * hh, uzz = c.uz * hh;
        p[0] = x - rxx - uxx; p[1] = y - ryy - uyy; p[2] = z - rzz - uzz;
        p[3] = x + rxx - uxx; p[4] = y + ryy - uyy; p[5] = z + rzz - uzz;
        p[6] = x + rxx + uxx; p[7] = y + ryy + uyy; p[8] = z + rzz + uzz;
        p[9] = x - rxx + uxx; p[10] = y - ryy + uyy; p[11] = z - rzz + uzz;
        mesh(q, null, r, g, b);
    }

    /** Sprite vertical (eje Y del mundo), p.ej. gotas de lluvia. */
    public void vbillboard(Texture t, float x, float y, float z, float w, float h, float r, float g, float b, float alpha, int flags) {
        Mesh q = billMesh;
        q.uv[0] = 0; q.uv[1] = 1; q.uv[2] = 1; q.uv[3] = 1; q.uv[4] = 1; q.uv[5] = 0; q.uv[6] = 0; q.uv[7] = 0;
        q.tex = t; q.flags = flags | Mesh.EMISSIVE; q.alpha = alpha;
        float rl = (float) Math.sqrt(cam.rx * cam.rx + cam.rz * cam.rz) + 1e-5f, rx = cam.rx / rl * w / 2, rz = cam.rz / rl * w / 2, hh = h / 2;
        float[] p = q.pos;
        p[0] = x - rx; p[1] = y - hh; p[2] = z - rz; p[3] = x + rx; p[4] = y - hh; p[5] = z + rz;
        p[6] = x + rx; p[7] = y + hh; p[8] = z + rz; p[9] = x - rx; p[10] = y + hh; p[11] = z - rz;
        mesh(q, null, r, g, b);
    }

    /** Cuadrado horizontal sobre el suelo (sombras, anillos, charcos). rot en radianes. */
    public void floorQuad(Texture t, float x, float y, float z, float size, float rot, float r, float g, float b, float alpha, int flags) {
        floorQuadUV(t, x, y, z, size, rot, r, g, b, alpha, flags, 0, 0, 1, 1);
    }

    public void floorQuadUV(Texture t, float x, float y, float z, float size, float rot, float r, float g, float b, float alpha, int flags, float u0, float v0, float u1, float v1) {
        Mesh q = billMesh;
        q.uv[0] = u0; q.uv[1] = v0; q.uv[2] = u1; q.uv[3] = v0; q.uv[4] = u1; q.uv[5] = v1; q.uv[6] = u0; q.uv[7] = v1;
        q.tex = t; q.flags = flags; q.alpha = alpha;
        float c = (float) Math.cos(rot) * size / 2, s = (float) Math.sin(rot) * size / 2;
        float[] p = q.pos;
        p[0] = x - c - s; p[1] = y; p[2] = z + s - c; p[3] = x + c - s; p[4] = y; p[5] = z - s - c;
        p[6] = x + c + s; p[7] = y; p[8] = z - s + c; p[9] = x - c + s; p[10] = y; p[11] = z + s + c;
        mesh(q, null, r, g, b);
    }

    private final Mesh billMesh = makeBill();

    private static Mesh makeBill() {
        Mesh q = new Mesh();
        q.pos = new float[12];
        q.uv = new float[]{0, 1, 1, 1, 1, 0, 0, 0};
        q.nrm = new float[]{0, 0, 1, 0, 0, 1, 0, 0, 1, 0, 0, 1};
        q.col = new float[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
        q.idx = new int[]{0, 1, 2, 0, 2, 3};
        q.vcount = 4; q.tcount = 2;
        return q;
    }

    // =====================================================================
    //  Transformacion / recorte
    // =====================================================================
    private void ensureScratch(int n) {
        if (vx.length < n) {
            int c = Math.max(n, vx.length * 2);
            vx = new float[c]; vy = new float[c]; vz = new float[c];
            vr = new float[c]; vg = new float[c]; vb = new float[c]; vo = new int[c];
        }
    }

    private void emit(Mesh m, float[] M, float tr, float tg, float tb, float ol, float og, float ob, TriBuf dst) {
        final int n = m.vcount;
        ensureScratch(n);
        final Camera c = cam;
        final boolean dyn = (m.flags & Mesh.DYNLIT) != 0;
        final boolean emis = (m.flags & Mesh.EMISSIVE) != 0;
        float[] P = m.pos, N = m.nrm, C = m.col;
        // lampara cerca de las entidades dinamicas
        final float lamp = lantern;
        for (int i = 0; i < n; i++) {
            float px = P[i * 3], py = P[i * 3 + 1], pz = P[i * 3 + 2];
            float wx, wy, wz;
            if (M != null) {
                wx = M[0] * px + M[1] * py + M[2] * pz + M[9];
                wy = M[3] * px + M[4] * py + M[5] * pz + M[10];
                wz = M[6] * px + M[7] * py + M[8] * pz + M[11];
            } else { wx = px; wy = py; wz = pz; }
            float dx = wx - c.x, dy = wy - c.y, dz = wz - c.z;
            float zc = dx * c.fx + dy * c.fy + dz * c.fz;
            float xc = dx * c.rx + dy * c.ry + dz * c.rz;
            float yc = dx * c.ux + dy * c.uy + dz * c.uz;
            vx[i] = xc; vy[i] = yc; vz[i] = zc;
            int o = 0;
            if (zc < 0.08f) o |= 1;
            if (xc > zc * tanX) o |= 2;
            if (xc < -zc * tanX) o |= 4;
            if (yc > zc * tanY) o |= 8;
            if (yc < -zc * tanY) o |= 16;
            vo[i] = o;
            float cr = C[i * 3], cg = C[i * 3 + 1], cb = C[i * 3 + 2];
            if (dyn) {
                float nx = N[i * 3], ny = N[i * 3 + 1], nz = N[i * 3 + 2];
                if (M != null) {
                    float ax = M[0] * nx + M[1] * ny + M[2] * nz, ay = M[3] * nx + M[4] * ny + M[5] * nz, az = M[6] * nx + M[7] * ny + M[8] * nz;
                    nx = ax; ny = ay; nz = az;
                }
                float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-4f;
                float lam = Math.max(0.12f, -(nx * dx + ny * dy + nz * dz) / d);
                float att = lamp / (1f + d * d * 0.058f) * 1.15f * lam;
                float moon = Math.max(0f, nx * 0.35f + ny * 0.8f + nz * 0.45f);
                float fl = flash * (0.6f + 0.4f * lam);
                float sr = ambR * (0.45f + 0.9f * moon) + ol + att * lampR + fl * flashR;
                float sg = ambG * (0.45f + 0.9f * moon) + og + att * lampG + fl * flashG;
                float sb = ambB * (0.45f + 0.9f * moon) + ob + att * lampB + fl * flashB;
                vr[i] = cr * tr * sr; vg[i] = cg * tg * sg; vb[i] = cb * tb * sb;
            } else {
                vr[i] = cr * tr; vg[i] = cg * tg; vb[i] = cb * tb;
            }
        }
        int fl = (m.flags & (Mesh.EMISSIVE | Mesh.ADDITIVE | Mesh.NOZWRITE | Mesh.SHADOW | Mesh.NOFOG)) | ((dyn || emis) ? 256 : 0);
        int alpha = (int) (m.alpha * 255f);
        if (alpha > 255) alpha = 255;
        int[] I = m.idx;
        float[] U = m.uv;
        int texId = m.tex.id;
        for (int t = 0; t < m.tcount; t++) {
            int a = I[t * 3], b = I[t * 3 + 1], d = I[t * 3 + 2];
            int oa = vo[a], ob2 = vo[b], oc = vo[d];
            if ((oa & ob2 & oc) != 0) continue;
            if (((oa | ob2 | oc) & 1) == 0) {
                emitProjected(dst, a, b, d, U, texId, fl, alpha);
            } else {
                clipNear(dst, a, b, d, U, texId, fl, alpha);
            }
        }
    }

    private void emitProjected(TriBuf dst, int a, int b, int c, float[] U, int texId, int fl, int alpha) {
        dst.ensure();
        int k = dst.n, o = k * 24;
        float[] v = dst.v;
        float ymin = 1e9f, ymax = -1e9f, dsum = 0;
        int[] ids = {a, b, c};
        for (int j = 0; j < 3; j++) {
            int i = ids[j];
            float z = vz[i], iz = 1f / z;
            float sx = cx + vx[i] * iz * focal, sy = cy - vy[i] * iz * focal;
            v[o + j * 8] = sx; v[o + j * 8 + 1] = sy; v[o + j * 8 + 2] = iz;
            v[o + j * 8 + 3] = U[i * 2] * iz; v[o + j * 8 + 4] = U[i * 2 + 1] * iz;
            v[o + j * 8 + 5] = vr[i]; v[o + j * 8 + 6] = vg[i]; v[o + j * 8 + 7] = vb[i];
            if (sy < ymin) ymin = sy;
            if (sy > ymax) ymax = sy;
            dsum += z;
        }
        finishTri(dst, k, ymin, ymax, dsum / 3f, texId, fl, alpha);
    }

    private void finishTri(TriBuf dst, int k, float ymin, float ymax, float depth, int texId, int fl, int alpha) {
        int y0 = (int) Math.floor(ymin), y1 = (int) Math.ceil(ymax);
        if (y1 < 0 || y0 >= H) return;
        dst.tex[k] = texId; dst.flg[k] = fl; dst.alp[k] = alpha;
        dst.y0[k] = Math.max(0, y0); dst.y1[k] = Math.min(H, y1 + 1); dst.key[k] = depth;
        dst.n = k + 1;
    }

    private void clipNear(TriBuf dst, int a, int b, int c, float[] U, int texId, int fl, int alpha) {
        // poligono con atributos: x,y,z,u,v,r,g,b
        int n = 0;
        int[] ids = {a, b, c};
        for (int j = 0; j < 3; j++) {
            int i = ids[j];
            poly[n * 8] = vx[i]; poly[n * 8 + 1] = vy[i]; poly[n * 8 + 2] = vz[i];
            poly[n * 8 + 3] = U[i * 2]; poly[n * 8 + 4] = U[i * 2 + 1];
            poly[n * 8 + 5] = vr[i]; poly[n * 8 + 6] = vg[i]; poly[n * 8 + 7] = vb[i];
            n++;
        }
        final float near = 0.1f;
        int m = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float zi = poly[i * 8 + 2], zj = poly[j * 8 + 2];
            boolean ii = zi >= near, jj = zj >= near;
            if (ii) { System.arraycopy(poly, i * 8, poly2, m * 8, 8); m++; }
            if (ii != jj) {
                float t = (near - zi) / (zj - zi);
                for (int k = 0; k < 8; k++) poly2[m * 8 + k] = poly[i * 8 + k] + (poly[j * 8 + k] - poly[i * 8 + k]) * t;
                poly2[m * 8 + 2] = near;
                m++;
            }
        }
        if (m < 3) return;
        for (int t = 1; t < m - 1; t++) {
            dst.ensure();
            int k = dst.n, o = k * 24;
            float ymin = 1e9f, ymax = -1e9f, dsum = 0;
            int[] sel = {0, t, t + 1};
            for (int j = 0; j < 3; j++) {
                int s = sel[j] * 8;
                float z = poly2[s + 2], iz = 1f / z;
                float sx = cx + poly2[s] * iz * focal, sy = cy - poly2[s + 1] * iz * focal;
                dst.v[o + j * 8] = sx; dst.v[o + j * 8 + 1] = sy; dst.v[o + j * 8 + 2] = iz;
                dst.v[o + j * 8 + 3] = poly2[s + 3] * iz; dst.v[o + j * 8 + 4] = poly2[s + 4] * iz;
                dst.v[o + j * 8 + 5] = poly2[s + 5]; dst.v[o + j * 8 + 6] = poly2[s + 6]; dst.v[o + j * 8 + 7] = poly2[s + 7];
                if (sy < ymin) ymin = sy;
                if (sy > ymax) ymax = sy;
                dsum += z;
            }
            finishTri(dst, k, ymin, ymax, dsum / 3f, texId, fl, alpha);
        }
    }

    // =====================================================================
    //  Rasterizado por bandas
    // =====================================================================
    public void end() {
        trisOpaque = opq.n; trisTrans = trn.n;
        // orden de transparentes: de lejos a cerca
        int tn = trn.n;
        if (transOrder.length < tn) transOrder = new int[Math.max(tn, transOrder.length * 2)];
        long[] keys = new long[tn];
        for (int i = 0; i < tn; i++) keys[i] = ((long) Float.floatToIntBits(trn.key[i]) << 32) | i;
        Arrays.sort(keys);
        for (int i = 0; i < tn; i++) transOrder[i] = (int) (keys[tn - 1 - i] & 0xffffffffL);

        final int bands = Math.max(4, Math.min(24, H / 24));
        final int bh = (H + bands - 1) / bands;
        IntStream.range(0, bands).parallel().forEach(bi -> {
            int y0 = bi * bh, y1 = Math.min(H, y0 + bh);
            if (y0 >= y1) return;
            Arrays.fill(zb, y0 * W, y1 * W, 0f);
            for (int t = 0; t < opq.n; t++) {
                if (opq.y1[t] <= y0 || opq.y0[t] >= y1) continue;
                fill(opq, t, y0, y1);
            }
            fillSky(y0, y1);
            for (int i = 0; i < trn.n; i++) {
                int t = transOrder[i];
                if (trn.y1[t] <= y0 || trn.y0[t] >= y1) continue;
                fill(trn, t, y0, y1);
            }
            if (ovl.n > 0) {
                Arrays.fill(zb, y0 * W, y1 * W, 0f);
                for (int t = 0; t < ovl.n; t++) {
                    if (ovl.y1[t] <= y0 || ovl.y0[t] >= y1) continue;
                    fill(ovl, t, y0, y1);
                }
            }
        });
    }

    private void fillSky(int y0, int y1) {
        final Camera c = cam;
        if (background != null) {
            for (int y = y0; y < y1; y++) {
                int row = y * W;
                for (int x = 0; x < W; x++) if (zb[row + x] == 0f) pixels[row + x] = background[row + x];
            }
            return;
        }
        if (sky == null) return;
        final int sw = sky.size, mask = sky.mask;
        final float twoPi = (float) (Math.PI * 2);
        for (int y = y0; y < y1; y++) {
            float elev = c.pitch - rowAng[y];                      // radianes sobre el horizonte
            float vv = (1.2f - elev) / 2.2f;                      // -0.2..~1.2 rad -> 0..1
            int ty = (int) (Math.max(0f, Math.min(0.999f, vv)) * sw);
            int row = y * W;
            for (int x = 0; x < W; x++) {
                if (zb[row + x] != 0f) continue;
                float ang = c.yaw + colAng[x];
                float u = ang / twoPi;
                u -= (float) Math.floor(u);
                int tx = (int) (u * sw) & mask;
                int px = sky.px[ty * sw + tx];
                int r = (int) (((px >> 16) & 255) * skyDim), g = (int) (((px >> 8) & 255) * skyDim), bl = (int) ((px & 255) * skyDim);
                if (flash > 0f) { r = Math.min(255, r + (int) (flash * 120)); g = Math.min(255, g + (int) (flash * 120)); bl = Math.min(255, bl + (int) (flash * 140)); }
                pixels[row + x] = 0xFF000000 | (r << 16) | (g << 8) | bl;
            }
        }
    }

    private void fill(TriBuf tb, int t, int by0, int by1) {
        final float[] v = tb.v;
        final int o = t * 24;
        // ordenar vertices por y
        int a = 0, b = 1, c = 2;
        float ya = v[o + 1], yb = v[o + 8 + 1], yc = v[o + 16 + 1];
        if (ya > yb) { int s = a; a = b; b = s; float f = ya; ya = yb; yb = f; }
        if (yb > yc) { int s = b; b = c; c = s; float f = yb; yb = yc; yc = f; }
        if (ya > yb) { int s = a; a = b; b = s; float f = ya; ya = yb; yb = f; }
        final int oa = o + a * 8, ob = o + b * 8, oc = o + c * 8;
        final float xa = v[oa], xb = v[ob], xc = v[oc];
        final float area = (xb - xa) * (yc - ya) - (yb - ya) * (xc - xa);
        if (area > -1e-4f && area < 1e-4f) return;
        final float inv = 1f / area;
        final float bax = xb - xa, bay = yb - ya, cax = xc - xa, cay = yc - ya;
        // gradientes de iz, uz, vz, r, g, b
        float[] sc = SCRATCH.get();
        final float[] g0 = sc, gx = sc, gy = sc;
        final int GX = 6, GY = 12;
        for (int k = 0; k < 6; k++) {
            float fa = v[oa + 2 + k], fb = v[ob + 2 + k], fc = v[oc + 2 + k];
            sc[k] = fa;
            sc[GX + k] = ((fb - fa) * cay - bay * (fc - fa)) * inv;
            sc[GY + k] = (bax * (fc - fa) - (fb - fa) * cax) * inv;
        }
        final Texture tex = texTab[tb.tex[t]];
        final int[] tp = tex.px;
        final int tshift = tex.shift, tmask = tex.mask;
        final float tsz = tex.size;
        final int flags = tb.flg[t];
        final int alpha = tb.alp[t];
        final boolean noLamp = (flags & 256) != 0;
        final boolean emissive = (flags & Mesh.EMISSIVE) != 0;
        final boolean additive = (flags & Mesh.ADDITIVE) != 0;
        final boolean shadowM = (flags & Mesh.SHADOW) != 0;
        final boolean nofog = (flags & Mesh.NOFOG) != 0;
        final boolean zwrite = (flags & Mesh.NOZWRITE) == 0 && !additive && !shadowM;
        final boolean blend = alpha < 255 || additive || shadowM;
        final float al = alpha / 255f, ial = 1f - al;
        final float[] LR = noLamp ? zero : lR, LG = noLamp ? zero : lG, LB = noLamp ? zero : lB;

        int ys = Math.max(by0, (int) Math.ceil(ya - 0.5f));
        int ye = Math.min(by1, (int) Math.ceil(yc - 0.5f));
        final float dyAC = yc - ya, dyAB = yb - ya, dyBC = yc - yb;
        for (int y = ys; y < ye; y++) {
            float py = y + 0.5f;
            float xl = xa + cax * (py - ya) / dyAC;
            float xr;
            if (py < yb) xr = xa + bax * (py - ya) / (dyAB > 1e-6f ? dyAB : 1e-6f);
            else xr = xb + (xc - xb) * (py - yb) / (dyBC > 1e-6f ? dyBC : 1e-6f);
            if (xl > xr) { float s = xl; xl = xr; xr = s; }
            int xs = Math.max(0, (int) Math.ceil(xl - 0.5f));
            int xe = Math.min(W, (int) Math.ceil(xr - 0.5f));
            if (xs >= xe) continue;
            float dx0 = xs + 0.5f - xa, dy0 = py - ya;
            float iz = sc[0] + sc[GX] * dx0 + sc[GY] * dy0;
            float uz = sc[1] + sc[GX + 1] * dx0 + sc[GY + 1] * dy0;
            float vz = sc[2] + sc[GX + 2] * dx0 + sc[GY + 2] * dy0;
            float lr = sc[3] + sc[GX + 3] * dx0 + sc[GY + 3] * dy0;
            float lg = sc[4] + sc[GX + 4] * dx0 + sc[GY + 4] * dy0;
            float lb = sc[5] + sc[GX + 5] * dx0 + sc[GY + 5] * dy0;
            final float diz = sc[GX], duz = sc[GX + 1], dvz = sc[GX + 2], dlr = sc[GX + 3], dlg = sc[GX + 4], dlb = sc[GX + 5];
            int idx = y * W + xs;
            for (int x = xs; x < xe; x++, idx++) {
                if (iz > zb[idx]) {
                    float z = 1f / iz;
                    int texel = tp[(((int) (vz * z * tsz)) & tmask) << tshift | (((int) (uz * z * tsz)) & tmask)];
                    int ta = texel >>> 24;
                    if (ta != 0 && shadowM) {
                        int d = pixels[idx];
                        float f = 1f - al * ((texel & 255) * (1f / 255f));
                        pixels[idx] = 0xFF000000 | ((int) (((d >> 16) & 255) * f) << 16) | ((int) (((d >> 8) & 255) * f) << 8) | (int) ((d & 255) * f);
                    } else if (ta != 0) {
                        int zi = (int) (z * ZSCALE);
                        if (zi >= ZN) zi = ZN - 1;
                        float Lr = lr + LR[zi], Lg = lg + LG[zi], Lb = lb + LB[zi];
                        if (ta != 255) {
                            float e = (254 - ta) * (1f / 253f);
                            Lr += (1.5f - Lr) * e; Lg += (1.5f - Lg) * e; Lb += (1.5f - Lb) * e;
                        }
                        float k = nofog ? 1f : keep[zi];
                        float ar = nofog ? 0f : addR[zi], ag = nofog ? 0f : addG[zi], ab = nofog ? 0f : addB[zi];
                        int r = (int) (((texel >> 16) & 255) * Lr * k + ar);
                        int g = (int) (((texel >> 8) & 255) * Lg * k + ag);
                        int bl = (int) ((texel & 255) * Lb * k + ab);
                        if (r > 255) r = 255;
                        if (g > 255) g = 255;
                        if (bl > 255) bl = 255;
                        if (r < 0) r = 0;
                        if (g < 0) g = 0;
                        if (bl < 0) bl = 0;
                        if (blend) {
                            int d = pixels[idx];
                            if (additive) {
                                r = Math.min(255, (int) (r * al) + ((d >> 16) & 255));
                                g = Math.min(255, (int) (g * al) + ((d >> 8) & 255));
                                bl = Math.min(255, (int) (bl * al) + (d & 255));
                            } else {
                                r = (int) (r * al + ((d >> 16) & 255) * ial);
                                g = (int) (g * al + ((d >> 8) & 255) * ial);
                                bl = (int) (bl * al + (d & 255) * ial);
                            }
                        }
                        pixels[idx] = 0xFF000000 | (r << 16) | (g << 8) | bl;
                        if (zwrite) zb[idx] = iz;
                    }
                }
                iz += diz; uz += duz; vz += dvz; lr += dlr; lg += dlg; lb += dlb;
            }
        }
    }
}
