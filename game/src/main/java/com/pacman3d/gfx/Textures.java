package com.pacman3d.gfx;

import java.util.Random;

/** Todas las texturas del juego se generan por codigo (sin depender de archivos). */
public final class Textures {
    public static Texture[] brick = new Texture[3];
    public static Texture[] facade = new Texture[4];
    public static Texture cobble, asphalt, sky, wood, metal, stone, bark, pumpkin, glow, spark, cobweb, cloth;
    public static Texture white, gold, red, green, blue, dark, orange, bone, cross, ammoBox, medkit, crate, door, bloodA, bloodB, leaf, eye, star, shield, plank;
    public static Texture[] ghost = new Texture[4];
    public static Texture acid, rug, spikeMetal, rainbowGlow;
    public static Texture[] sign = new Texture[3];
    public static Texture ghostDead, ring, shadow, fogTex, puddle, leafDecal, manhole, flame, sparkStar;
    public static Texture[] tag = new Texture[2];
    private static boolean ready;

    public static synchronized void init() {
        if (ready) return;
        ready = true;
        brick[0] = brick(1, 112, 60, 48, 0.2f);
        brick[1] = brick(2, 96, 92, 92, 0.35f);
        brick[2] = brick(3, 82, 68, 74, 0.5f);
        facade[0] = facade(10, 112, 60, 48, 1);   // ventanas con luz ambar
        facade[1] = facade(11, 96, 92, 98, 0);    // oscuras rotas
        facade[2] = facade(12, 82, 68, 74, 2);    // luz verde enferma
        facade[3] = facade(13, 100, 84, 70, 3);   // tapiadas con tablas
        cobble = cobble(20);
        asphalt = asphalt(21);
        sky = sky(1024);
        wood = wood(30, 112, 70, 38);
        plank = wood(31, 70, 56, 44);
        crate = crate();
        metal = metal(32);
        stone = stoneTex(33);
        bark = bark(34);
        pumpkin = pumpkin();
        glow = glow(64, 1f);
        spark = glow(32, 2.2f);
        cobweb = cobweb();
        cloth = cloth();
        white = Texture.solid(0xFFFFFFFF);
        gold = noisy(40, 230, 180, 40, 0.15f, 32);
        red = Texture.solid(0xFFC81818);
        green = Texture.solid(0xFF30C040);
        blue = Texture.solid(0xFF3090FF);
        dark = Texture.solid(0xFF101010);
        orange = Texture.solid(0xFFFF8010);
        bone = noisy(41, 220, 214, 190, 0.1f, 32);
        cross = medkitTex();
        ammoBox = ammoTex();
        medkit = cross;
        door = doorTex();
        bloodA = blood(50);
        bloodB = blood(51);
        eye = Texture.solid(0xFF000000);
        star = Texture.solid(0xFFFFE020);
        shield = glow(64, 0.8f);
        int[][] cols = {{205, 60, 55}, {235, 130, 190}, {80, 205, 225}, {240, 150, 50}};
        for (int i = 0; i < 4; i++) ghost[i] = ghostTex(60 + i, cols[i][0], cols[i][1], cols[i][2], false);
        ghostDead = ghostTex(70, 220, 220, 230, true);
        leaf = Texture.solid(0xFF304020);
        ring = ringTex(); shadow = shadowTex(); fogTex = fogTexture(); puddle = puddleTex(); leafDecal = leafTex(); manhole = manholeTex(); flame = glow(32, 1.4f);
        acid = acidTex(); rug = rugTex(); spikeMetal = noisy(77, 150, 154, 165, 0.25f, 32);
        sign[0] = signTex("ARMERÍA", 0xFF4A2410, 0xFFFFD060, 0xFFC08030);
        sign[1] = signTex("BOTICA", 0xFF2A0F40, 0xFFB8FF90, 0xFF9A5AE0);
        sign[2] = signTex("BAZAR", 0xFF0F3A40, 0xFFFFE070, 0xFF30C0B0);
        tag[0] = tagTex("P1", 0xFF3CC8FF); tag[1] = tagTex("P2", 0xFFFF8A2A);
    }

    // ------------------------------------------------------------------ utilidades
    static int pack(int a, float r, float g, float b) {
        int ir = (int) Math.max(0, Math.min(255, r)), ig = (int) Math.max(0, Math.min(255, g)), ib = (int) Math.max(0, Math.min(255, b));
        return (a << 24) | (ir << 16) | (ig << 8) | ib;
    }

    static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 144665;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xffff) / 65535f;
    }

    /** Ruido de valor tileable con periodo p (celdas). */
    static float vnoise(float x, float y, int p, int seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        int xa = Math.floorMod(x0, p), xb = Math.floorMod(x0 + 1, p), ya = Math.floorMod(y0, p), yb = Math.floorMod(y0 + 1, p);
        float a = hash(xa, ya, seed), b = hash(xb, ya, seed), c = hash(xa, yb, seed), d = hash(xb, yb, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    static float fbm(float x, float y, int p, int seed, int oct) {
        float s = 0, amp = 0.5f, tot = 0;
        for (int i = 0; i < oct; i++) {
            s += vnoise(x, y, p, seed + i * 17) * amp; tot += amp;
            x *= 2; y *= 2; p *= 2; amp *= 0.5f;
        }
        return s / tot;
    }

    static Texture noisy(int seed, int r, int g, int b, float var, int size) {
        Texture t = new Texture(size);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                float n = 1 + (fbm(x * 8f / size, y * 8f / size, 8, seed, 3) - 0.5f) * 2 * var;
                t.px[y * size + x] = pack(255, r * n, g * n, b * n);
            }
        return t;
    }

    // ------------------------------------------------------------------ ladrillo
    static Texture brick(int seed, int br, int bg, int bb, float moss) {
        final int S = 128;
        Texture t = new Texture(S);
        Random rnd = new Random(seed);
        int rowH = 16, bw = 32;
        float[] bv = new float[8 * 6];
        for (int i = 0; i < bv.length; i++) bv[i] = 0.78f + rnd.nextFloat() * 0.45f;
        for (int y = 0; y < S; y++) {
            int row = y / rowH, ry = y % rowH;
            int off = (row % 2) * (bw / 2);
            for (int x = 0; x < S; x++) {
                int xx = (x + off) % S;
                int col = xx / bw, rx = xx % bw;
                boolean mortar = ry < 2 || rx < 2;
                float v = bv[row * 6 + col % 4];
                float n = 0.85f + fbm(x * 0.18f, y * 0.18f, 23, seed, 3) * 0.3f + (hash(x, y, seed) - 0.5f) * 0.14f;
                float r = br * v * n, g = bg * v * n, b = bb * v * n;
                if (mortar) { r = 40 * n; g = 38 * n; b = 36 * n; }
                else if (ry < 4 || rx < 4) { r *= 0.8f; g *= 0.8f; b *= 0.8f; }
                // suciedad: oscurece hacia abajo del bloque + manchas
                float dirt = fbm(x * 0.07f, y * 0.04f, 9, seed + 5, 3);
                float k = 0.7f + 0.5f * dirt;
                r *= k; g *= k; b *= k;
                // musgo
                float mo = fbm(x * 0.1f, y * 0.1f, 13, seed + 9, 3) * (0.6f + 0.8f * (y / (float) S));
                if (mo > 0.62f - moss * 0.15f && moss > 0.25f) { float q = Math.min(1, (mo - 0.55f) * 6); r = r * (1 - q) + 40 * q; g = g * (1 - q) + 70 * q; b = b * (1 - q) + 36 * q; }
                t.px[y * S + x] = pack(255, r, g, b);
            }
        }
        // grietas
        for (int c = 0; c < 3; c++) {
            float cx = rnd.nextInt(S), cy = rnd.nextInt(S);
            for (int s = 0; s < 40; s++) {
                t.set((int) cx, (int) cy, 0xFF1A1512);
                cx += rnd.nextFloat() * 2 - 0.7f; cy += rnd.nextFloat() * 2 - 0.6f;
            }
        }
        return t;
    }

    static Texture facade(int seed, int br, int bg, int bb, int kind) {
        Texture t = brick(seed, br, bg, bb, 0.3f);
        final int S = 128;
        Random rnd = new Random(seed);
        int[][] wins = {{18, 26, 34, 58}, {76, 26, 34, 58}};
        for (int[] w : wins) {
            int x0 = w[0], y0 = w[1], ww = w[2], wh = w[3];
            // marco/alfeizar
            fillRect(t, x0 - 4, y0 - 4, ww + 8, wh + 8, 0xFF2C2622);
            fillRect(t, x0 - 6, y0 + wh + 2, ww + 12, 5, 0xFF4A4038);
            for (int y = 0; y < wh; y++)
                for (int x = 0; x < ww; x++) {
                    float g = 0.6f + 0.4f * ((float) y / wh);
                    int c;
                    if (kind == 1) { // luz ambar viva con cortina y silueta
                        float flick = 0.8f + 0.2f * hash(x / 3, y / 3, seed);
                        c = pack(34, 255 * flick * g, 170 * flick * g, 60 * flick * g);
                        if (Math.abs(x - ww / 2) < 8 && y > wh / 3 && y < wh * 0.8f + 4 - Math.abs(x - ww / 2)) c = pack(34, 30, 14, 8);
                    } else if (kind == 2) { // luz verde enferma
                        float flick = 0.75f + 0.25f * hash(x / 4, y / 2, seed + 4);
                        c = pack(40, 90 * flick * g, 255 * flick * g, 110 * flick * g);
                    } else if (kind == 3) { // tapiada con tablones
                        int pl = y / 12;
                        float wd = 0.8f + 0.4f * hash(pl, x / 9, seed);
                        c = (y % 12 < 1 || (pl % 2 == 0 && x % 20 == 0)) ? 0xFF1A1410 : pack(255, 96 * wd, 70 * wd, 44 * wd);
                    } else { // oscura, rota
                        float d = 8 + 14 * g + 8 * hash(x, y, seed);
                        c = pack(255, d * 0.8f, d, d * 1.5f);
                    }
                    t.px[(y0 + y) * S + (x0 + x)] = c;
                }
            if (kind != 3) { // cruceta y cristales rotos
                fillRect(t, x0 + ww / 2 - 1, y0, 3, wh, 0xFF2C2622);
                fillRect(t, x0, y0 + wh / 2 - 1, ww, 3, 0xFF2C2622);
                for (int s = 0; s < 6; s++) {
                    int sx = x0 + rnd.nextInt(ww), sy = y0 + rnd.nextInt(wh);
                    for (int k = 0; k < 10; k++) { int px = sx + k, py = sy + (k * (s % 3 - 1)); if (px >= x0 && px < x0 + ww && py >= y0 && py < y0 + wh) t.px[py * S + px] = (t.px[py * S + px] & 0xFF000000) | 0x20202A; }
                }
            }
        }
        return t;
    }

    static void fillRect(Texture t, int x0, int y0, int w, int h, int argb) {
        for (int y = y0; y < y0 + h; y++) for (int x = x0; x < x0 + w; x++) if (x >= 0 && y >= 0 && x < t.size && y < t.size) t.px[y * t.size + x] = argb;
    }

    // ------------------------------------------------------------------ suelos
    static Texture cobble(int seed) {
        final int S = 128;
        Texture t = new Texture(S);
        int cell = 16;
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                int row = y / cell, off = (row % 2) * (cell / 2 + 2);
                int xx = (x + off) % S, cx = xx / cell;
                float jx = hash(cx, row, seed), jy = hash(row, cx, seed + 2);
                float dx = (xx % cell) - cell / 2f, dy = (y % cell) - cell / 2f;
                float d = Math.max(Math.abs(dx) / (cell * (0.42f + jx * 0.08f)), Math.abs(dy) / (cell * (0.40f + jy * 0.08f)));
                float v = 0.7f + 0.5f * hash(cx, row, seed + 7);
                float n = 0.8f + fbm(x * 0.2f, y * 0.2f, 26, seed, 3) * 0.4f;
                float r = 78 * v * n, g = 76 * v * n, b = 82 * v * n;
                if (d > 1f) { r = 20; g = 20; b = 22; }
                else if (d > 0.8f) { r *= 0.7f; g *= 0.7f; b *= 0.7f; }
                float mo = fbm(x * 0.09f, y * 0.09f, 11, seed + 3, 3);
                if (mo > 0.6f) { float q = Math.min(1, (mo - 0.6f) * 5); r = r * (1 - q) + 36 * q; g = g * (1 - q) + 62 * q; b = b * (1 - q) + 30 * q; }
                t.px[y * S + x] = pack(255, r, g, b);
            }
        return t;
    }

    static Texture asphalt(int seed) {
        final int S = 128;
        Texture t = new Texture(S);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float n = 0.6f + fbm(x * 0.3f, y * 0.3f, 38, seed, 4) * 0.7f + (hash(x, y, seed) - 0.5f) * 0.2f;
                float r = 52 * n, g = 52 * n, b = 58 * n;
                float crack = Math.abs(fbm(x * 0.05f, y * 0.05f, 6, seed + 9, 4) - 0.5f);
                if (crack < 0.012f) { r = g = b = 12; }
                t.px[y * S + x] = pack(255, r, g, b);
            }
        return t;
    }

    static Texture blood(int seed) {
        final int S = 64;
        Texture t = new Texture(S);
        Random rnd = new Random(seed);
        int blobs = 9;
        float[][] bl = new float[blobs][3];
        for (int i = 0; i < blobs; i++) { bl[i][0] = 32 + (rnd.nextFloat() - 0.5f) * 36; bl[i][1] = 32 + (rnd.nextFloat() - 0.5f) * 36; bl[i][2] = 3 + rnd.nextFloat() * (i == 0 ? 14 : 8); }
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float v = 0;
                for (float[] b : bl) { float d = (float) Math.hypot(x - b[0], y - b[1]); v = Math.max(v, 1 - d / b[2]); }
                v += (fbm(x * 0.2f, y * 0.2f, 13, seed, 3) - 0.5f) * 0.4f;
                if (v > 0.1f) { float k = 0.5f + 0.5f * v; t.px[y * S + x] = pack(255, 120 * k, 5 * k, 8 * k); } else t.px[y * S + x] = 0;
            }
        return t;
    }

    // ------------------------------------------------------------------ materiales
    static Texture wood(int seed, int r, int g, int b) {
        final int S = 128;
        Texture t = new Texture(S);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                int plank = x / 21;
                float pv = 0.75f + 0.4f * hash(plank, 0, seed);
                float grain = 0.8f + 0.35f * vnoise(x * 0.08f + plank * 9, y * 0.7f, 64, seed) + 0.1f * hash(x, y, seed);
                float k = pv * grain;
                int c = pack(255, r * k, g * k, b * k);
                if (x % 21 < 1) c = 0xFF150E08;
                if ((y % 64 < 2)) c = pack(255, r * k * 0.5f, g * k * 0.5f, b * k * 0.5f);
                t.px[y * S + x] = c;
            }
        return t;
    }

    static Texture crate() {
        Texture t = wood(36, 100, 80, 50);
        for (int i = 0; i < 128; i++) { for (int w = 0; w < 7; w++) { t.set(i, w, 0xFF2A1C10); t.set(i, 127 - w, 0xFF2A1C10); t.set(w, i, 0xFF2A1C10); t.set(127 - w, i, 0xFF2A1C10); t.set(i, i + w - 3, 0xFF33200F); } }
        return t;
    }

    static Texture metal(int seed) {
        final int S = 64;
        Texture t = new Texture(S);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float n = 0.7f + 0.3f * vnoise(x * 0.5f, y * 0.05f, 32, seed) + (hash(x, y, seed) - 0.5f) * 0.12f;
                t.px[y * S + x] = pack(255, 78 * n, 82 * n, 92 * n);
            }
        return t;
    }

    static Texture stoneTex(int seed) {
        final int S = 128;
        Texture t = new Texture(S);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float n = 0.65f + fbm(x * 0.1f, y * 0.1f, 13, seed, 4) * 0.7f + (hash(x, y, seed) - 0.5f) * 0.1f;
                float r = 112 * n, g = 112 * n, b = 118 * n;
                float mo = fbm(x * 0.08f, y * 0.08f, 10, seed + 4, 3);
                if (mo > 0.55f) { float q = Math.min(1, (mo - 0.55f) * 4); r = r * (1 - q) + 40 * q; g = g * (1 - q) + 72 * q; b = b * (1 - q) + 34 * q; }
                t.px[y * S + x] = pack(255, r, g, b);
            }
        return t;
    }

    static Texture bark(int seed) {
        final int S = 64;
        Texture t = new Texture(S);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float n = 0.5f + 0.7f * vnoise(x * 0.5f, y * 0.06f, 32, seed) * vnoise(x * 0.2f, y * 0.1f, 12, seed + 1) * 1.6f;
                t.px[y * S + x] = pack(255, 54 * n, 44 * n, 38 * n);
            }
        return t;
    }

    static Texture cloth() {
        final int S = 64;
        Texture t = new Texture(S);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float n = 0.8f + 0.2f * vnoise(x * 0.3f, y * 0.3f, 19, 7) + 0.1f * (float) Math.sin(x * 0.6f);
                t.px[y * S + x] = pack(255, 150 * n, 20 * n, 28 * n);
            }
        return t;
    }

    static Texture pumpkin() {
        final int S = 128;
        Texture t = new Texture(S);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float rib = 0.72f + 0.28f * (float) Math.abs(Math.sin(x / (float) S * Math.PI * 16));
                float n = 0.85f + 0.3f * fbm(x * 0.15f, y * 0.15f, 19, 5, 3);
                t.px[y * S + x] = pack(255, 255 * rib * n, 105 * rib * n, 12 * rib * n);
            }
        // cara tallada: brillo emisivo (alfa bajo) en u 0.12..0.38 (frente)
        int cx = (int) (0.25f * S);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                int dx = x - cx, dy = y - 60;
                boolean eyeL = tri(dx + 17, dy + 14, 10), eyeR = tri(dx - 17, dy + 14, 10);
                boolean nose = tri(dx, dy - 2, 6);
                boolean mouth = dy > 12 && dy < 36 && Math.abs(dx) < 30 - (dy - 12) * 0.5f && ((dx + 40) / 6) % 2 == 0 && dy > 14 + Math.abs(dx) * 0.2f;
                if (eyeL || eyeR || nose || mouth) t.px[y * S + x] = pack(18, 255, 235, 60);
            }
        return t;
    }

    private static boolean tri(int dx, int dy, int s) { return dy >= -s && dy <= s && Math.abs(dx) <= (dy + s) * 0.5f; }

    static Texture glow(int size, float power) {
        Texture t = new Texture(size);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5f) / size * 2 - 1, dy = (y + 0.5f) / size * 2 - 1;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float v = Math.max(0, 1 - d);
                v = (float) Math.pow(v, power);
                int c = (int) (255 * v);
                t.px[y * size + x] = (255 << 24) | (c << 16) | (c << 8) | c;
            }
        return t;
    }

    static Texture cobweb() {
        final int S = 64;
        Texture t = new Texture(S);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float dx = x, dy = y;
                float d = (float) Math.hypot(dx, dy), a = (float) Math.atan2(dy, dx);
                boolean radial = false;
                for (int k = 0; k <= 5; k++) { float ra = k * (float) (Math.PI / 2 / 5); if (Math.abs((float) Math.sin(a - ra)) * d < 0.8f) radial = true; }
                boolean ring = false;
                for (int k = 1; k < 7; k++) { float rr = k * 9; if (Math.abs(d - rr + (float) Math.sin(a * 5) * 1.2f) < 0.8f && d < 60) ring = true; }
                t.px[y * S + x] = (radial && d < 62) || ring ? pack(255, 215, 215, 225) : 0;
            }
        return t;
    }

    static Texture medkitTex() {
        Texture t = new Texture(64);
        fillRect(t, 0, 0, 64, 64, 0xFFE8E8E8);
        fillRect(t, 0, 0, 64, 4, 0xFFB0B0B0); fillRect(t, 0, 60, 64, 4, 0xFFB0B0B0);
        fillRect(t, 24, 10, 16, 44, 0xFFD01818); fillRect(t, 10, 24, 44, 16, 0xFFD01818);
        return t;
    }

    static Texture ammoTex() {
        Texture t = new Texture(64);
        fillRect(t, 0, 0, 64, 64, 0xFF4B5A33);
        fillRect(t, 0, 0, 64, 5, 0xFF2E3A1E); fillRect(t, 0, 59, 64, 5, 0xFF2E3A1E);
        for (int i = 0; i < 5; i++) { fillRect(t, 8 + i * 10, 18, 6, 26, 0xFFD8B040); fillRect(t, 8 + i * 10, 14, 6, 6, 0xFFA06030); }
        return t;
    }

    static Texture doorTex() {
        final int S = 128;
        Texture t = new Texture(S);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float n = 0.7f + 0.3f * vnoise(x * 0.4f, y * 0.04f, 51, 3);
                int c = pack(255, 84 * n, 66 * n, 52 * n);
                if (x % 32 < 2 || x % 32 > 30) c = 0xFF1A130E;
                if (y % 40 < 6 || y > 118 || y < 4) c = pack(255, 60 * n, 62 * n, 70 * n);
                int rx = (x % 32) - 16, ry = (y % 40) - 22;
                if (y % 40 >= 6 && rx * rx + ry * ry < 9) c = 0xFF9A9AA0;
                t.px[y * S + x] = c;
            }
        return t;
    }

    // ------------------------------------------------------------------ fantasma
    static Texture ghostTex(int seed, int cr, int cg, int cb, boolean dead) {
        final int S = 256;
        Texture t = new Texture(S);
        float[] base = {170 + cr * 0.25f, 170 + cg * 0.25f, 178 + cb * 0.25f};
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float u = x / (float) S, v = y / (float) S;
                float fold = 0.78f + 0.22f * (float) Math.sin(u * Math.PI * 2 * 9 + 2.2f * vnoise(u * 12, v * 4, 12, seed));
                float n = 0.85f + 0.3f * fbm(x * 0.05f, y * 0.04f, 13, seed, 3);
                float bot = 1f - Math.max(0, (v - 0.55f)) * 0.9f;          // se vuelve tenue abajo
                float tintAmt = 0.18f + 0.45f * Math.max(0, v - 0.45f);       // color hacia el borde
                float r = base[0] * (1 - tintAmt) + cr * tintAmt, g = base[1] * (1 - tintAmt) + cg * tintAmt, b = base[2] * (1 - tintAmt) + cb * tintAmt;
                float k = fold * n * (0.7f + 0.3f * bot);
                t.px[y * S + x] = pack(255, r * k, g * k, b * k);
            }
        if (!dead) {
            // cara: u 0.5 es el frente
            drawFaceEllipse(t, 0.435f * S, 0.20f * S, 0.040f * S, 0.075f * S, 0xFF050408, 0, 0, 0);
            drawFaceEllipse(t, 0.565f * S, 0.20f * S, 0.040f * S, 0.075f * S, 0xFF050408, 0, 0, 0);
            drawFaceEllipse(t, 0.435f * S, 0.215f * S, 0.017f * S, 0.034f * S, pack(14, 255, 230, 160) , cr, cg, cb);
            drawFaceEllipse(t, 0.565f * S, 0.215f * S, 0.017f * S, 0.034f * S, pack(14, 255, 230, 160), cr, cg, cb);
            drawFaceEllipse(t, 0.5f * S, 0.375f * S, 0.052f * S, 0.115f * S, 0xFF030206, 0, 0, 0);
            // dientes serrados / boca irregular
            for (int i = 0; i < 6; i++) {
                int tx = (int) (0.5f * S - 0.044f * S + i * 0.0175f * S), ty = (int) (0.27f * S);
                for (int k = 0; k < 7; k++) for (int w = 0; w < 3 - k / 3; w++) t.set(tx + w, ty + k, 0xFFB8B0A0);
            }
        } else {
            for (int y = 0; y < S; y++) for (int x = 0; x < S; x++) { int c = t.px[y * S + x]; t.px[y * S + x] = pack(255, ((c >> 16) & 255) * 1.1f, ((c >> 8) & 255) * 1.1f, (c & 255) * 1.1f); }
        }
        return t;
    }

    static void drawFaceEllipse(Texture t, float cx, float cy, float rx, float ry, int argb, int tr, int tg, int tb) {
        for (int y = (int) (cy - ry - 1); y <= cy + ry + 1; y++)
            for (int x = (int) (cx - rx - 1); x <= cx + rx + 1; x++) {
                float dx = (x - cx) / rx, dy = (y - cy) / ry;
                float d = dx * dx + dy * dy;
                if (d <= 1f) {
                    int a = argb >>> 24;
                    int c = argb;
                    if (a != 255 && a != 0) { // pupila emisiva con color del fantasma
                        c = (a << 24) | (Math.min(255, 120 + tr / 2 + (int) (100 * (1 - d))) << 16) | (Math.min(255, 70 + tg / 2) << 8) | Math.min(255, 70 + tb / 2);
                    }
                    t.set(x, y, c);
                }
            }
    }

    // ------------------------------------------------------------------ cielo
    static Texture sky(int S) {
        Texture t = new Texture(S);
        Random rnd = new Random(77);
        for (int y = 0; y < S; y++) {
            float vv = y / (float) S;                               // 0 = cenit, 1 = bajo el horizonte
            float elev = 1.2f - vv * 2.2f;                          // radianes
            float hor = (float) Math.exp(-Math.abs(elev) * 3.2f);   // brillo en el horizonte
            for (int x = 0; x < S; x++) {
                float u = x / (float) S;
                float cl = fbm(u * 6, vv * 3, 6, 11, 5);
                float cloud = Math.max(0, cl - 0.46f) * 3.2f;
                float r = 6 + 20 * (1 - vv) * 0.3f + 40 * hor, g = 7 + 14 * hor, b = 22 + 38 * (1 - vv) * 0.4f + 48 * hor;
                // nubes oscuras iluminadas por la luna
                float mx = u - 0.25f; if (mx > 0.5f) mx -= 1; if (mx < -0.5f) mx += 1;
                float md = (float) Math.hypot(mx * 2.2f, (vv - 0.30f) * 1.5f);
                float moonLight = Math.max(0, 1 - md * 2.2f);
                float cr = 20 + 90 * moonLight, cg = 18 + 70 * moonLight, cb = 38 + 70 * moonLight;
                r = r * (1 - cloud * 0.8f) + cr * cloud * 0.8f; g = g * (1 - cloud * 0.8f) + cg * cloud * 0.8f; b = b * (1 - cloud * 0.8f) + cb * cloud * 0.8f;
                r += 55 * moonLight * moonLight; g += 38 * moonLight * moonLight; b += 20 * moonLight * moonLight;
                t.px[y * S + x] = pack(255, r, g, b);
            }
        }
        // estrellas
        for (int i = 0; i < 700; i++) {
            int x = rnd.nextInt(S), y = rnd.nextInt((int) (S * 0.6f));
            int c = t.px[y * S + x];
            if (((c >> 16) & 255) < 70) { float br = 120 + rnd.nextInt(135); t.px[y * S + x] = pack(255, br, br, br * 0.95f); }
        }
        // silueta de ciudad lejana en el horizonte
        {
            int horizon = (int) (S * (1.2f / 2.2f));      // fila de elevacion 0
            Random br = new Random(12);
            int x = 0;
            while (x < S) {
                int w = 10 + br.nextInt(34), h = 12 + br.nextInt(46);
                boolean spire = br.nextInt(6) == 0;
                for (int xx = x; xx < Math.min(S, x + w); xx++) {
                    int top = horizon - h - (spire && xx > x + w / 2 - 3 && xx < x + w / 2 + 3 ? 22 : 0);
                    for (int yy = top; yy < horizon + 40; yy++) {
                        if (yy < 0 || yy >= S) continue;
                        float f = Math.min(1f, (yy - top) / 40f);
                        t.px[yy * S + xx] = pack(255, 5 + 8 * f, 5 + 7 * f, 12 + 12 * f);
                    }
                }
                for (int wy = horizon - h + 6; wy < horizon; wy += 8)
                    for (int wx = x + 4; wx < x + w - 3; wx += 6)
                        if (br.nextInt(7) == 0 && wx < S && wy >= 0) { int c = br.nextBoolean() ? pack(255, 255, 170, 60) : pack(255, 90, 255, 120); t.px[wy * S + wx] = c; t.px[wy * S + wx + 1] = c; }
                x += w + br.nextInt(6);
            }
        }
        // luna grande
        float mcx = 0.25f * S, mcy = 0.30f * S, mr = S * 0.075f;
        for (int y = (int) (mcy - mr * 3); y < mcy + mr * 3; y++)
            for (int x = (int) (mcx - mr * 3); x < mcx + mr * 3; x++) {
                float d = (float) Math.hypot(x - mcx, y - mcy);
                int c = t.px[(y & (S - 1)) * S + (x & (S - 1))];
                float r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                if (d < mr) {
                    float crat = fbm(x * 0.12f, y * 0.12f, 9, 4, 4);
                    float k = 0.8f + 0.35f * crat;
                    r = 255 * k; g = 214 * k; b = 150 * k;
                } else if (d < mr * 3) {
                    float h = (float) Math.pow(1 - (d - mr) / (mr * 2), 2.2);
                    r += 140 * h; g += 90 * h; b += 40 * h;
                }
                t.px[(y & (S - 1)) * S + (x & (S - 1))] = pack(255, r, g, b);
            }
        return t;
    }

    // ------------------------------------------------------------------ nuevas
    static Texture ringTex() {
        Texture t = new Texture(128);
        for (int y = 0; y < 128; y++)
            for (int x = 0; x < 128; x++) {
                float dx = (x + 0.5f) / 64f - 1, dy = (y + 0.5f) / 64f - 1, d = (float) Math.sqrt(dx * dx + dy * dy);
                float v = Math.max(0, 1 - Math.abs(d - 0.82f) * 7f) + Math.max(0, 0.35f - d) * 0.3f;
                if (d > 1) v = 0;
                int c = (int) (255 * Math.min(1, v));
                t.px[y * 128 + x] = (255 << 24) | (c << 16) | (c << 8) | c;
            }
        return t;
    }

    /** Sombra suave: el canal rojo es la oscuridad (blend multiplicativo). */
    static Texture shadowTex() {
        Texture t = new Texture(64);
        for (int y = 0; y < 64; y++)
            for (int x = 0; x < 64; x++) {
                float dx = (x + 0.5f) / 32f - 1, dy = (y + 0.5f) / 32f - 1, d = (float) Math.sqrt(dx * dx + dy * dy);
                float v = Math.max(0, 1 - d); v = v * v * (3 - 2 * v);
                int c = (int) (255 * v);
                t.px[y * 64 + x] = (255 << 24) | (c << 16) | (c << 8) | c;
            }
        return t;
    }

    static Texture fogTexture() {
        Texture t = new Texture(128);
        for (int y = 0; y < 128; y++)
            for (int x = 0; x < 128; x++) {
                float n = fbm(x * 0.06f, y * 0.06f, 8, 91, 4);
                float v = Math.max(0, (n - 0.35f) * 1.9f);
                int c = (int) (255 * Math.min(1, v));
                t.px[y * 128 + x] = (255 << 24) | (c << 16) | (c << 8) | c;
            }
        return t;
    }

    static Texture puddleTex() {
        final int S = 64;
        Texture t = new Texture(S);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float dx = (x - 32f) / 28f, dy = (y - 32f) / 20f;
                float d = dx * dx + dy * dy + (fbm(x * 0.15f, y * 0.15f, 10, 7, 3) - 0.5f) * 0.6f;
                if (d > 1f) { t.px[y * S + x] = 0; continue; }
                float refl = Math.max(0, 1 - d) * (0.5f + 0.5f * hash(x / 6, y / 6, 3));
                // alfa bajo = brillo propio: reflejo del cielo
                int a = refl > 0.55f ? 90 : 255;
                t.px[y * S + x] = pack(a, 14 + 70 * refl, 18 + 80 * refl, 30 + 120 * refl);
            }
        return t;
    }

    static Texture leafTex() {
        final int S = 64;
        Texture t = new Texture(S);
        Random r = new Random(4);
        for (int i = 0; i < 22; i++) {
            float cx = r.nextInt(S), cy = r.nextInt(S), a = r.nextFloat() * 6.28f;
            int[] cols = {0xFF8A3A10, 0xFFB5601A, 0xFF5A2A0C, 0xFF6B4A12};
            int c = cols[r.nextInt(cols.length)];
            for (int k = -4; k <= 4; k++) for (int w = -2; w <= 2; w++) {
                int x = (int) (cx + Math.cos(a) * k - Math.sin(a) * w), y = (int) (cy + Math.sin(a) * k + Math.cos(a) * w);
                if (Math.abs(w) * 2 < 5 - Math.abs(k) && x >= 0 && y >= 0 && x < S && y < S) t.px[y * S + x] = c;
            }
        }
        return t;
    }

    static Texture manholeTex() {
        final int S = 64;
        Texture t = new Texture(S);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float dx = x - 32f, dy = y - 32f, d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > 30) { t.px[y * S + x] = 0; continue; }
                float k = 0.55f + 0.25f * (float) Math.sin(d * 0.9f) * ((int) (d / 4) % 2) + (hash(x, y, 8) - 0.5f) * 0.12f;
                if (Math.abs(dx) < 2 || Math.abs(dy) < 2) k *= 0.6f;
                t.px[y * S + x] = pack(255, 70 * k, 70 * k, 78 * k);
            }
        return t;
    }

    static Texture tagTex(String text, int argb) {
        Texture t = new Texture(64);
        try {
            java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = im.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(0, 0, 0, 200)); g.fillRoundRect(2, 14, 60, 36, 16, 16);
            g.setColor(new java.awt.Color(argb, true)); g.setStroke(new java.awt.BasicStroke(3f)); g.drawRoundRect(2, 14, 60, 36, 16, 16);
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 26));
            java.awt.FontMetrics fm = g.getFontMetrics();
            g.drawString(text, 32 - fm.stringWidth(text) / 2, 41);
            g.dispose();
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
                int c = im.getRGB(x, y);
                int a = c >>> 24;
                t.px[y * 64 + x] = a < 40 ? 0 : (0xFF000000 | (c & 0xFFFFFF));
            }
        } catch (Throwable e) { java.util.Arrays.fill(t.px, argb); }
        return t;
    }

    static Texture acidTex() {
        final int S = 128;
        Texture t = new Texture(S);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float n = fbm(x * 0.07f, y * 0.07f, 9, 61, 4);
                float v = 0.35f + 0.65f * n;
                float vein = Math.abs(fbm(x * 0.05f + 3, y * 0.05f, 6, 62, 3) - 0.5f);
                if (vein < 0.045f) v = 1.2f;
                t.px[y * S + x] = pack(255, 40 * v, 255 * Math.min(1f, v), 30 * v);
            }
        return t;
    }

    static Texture rugTex() {
        final int S = 64;
        Texture t = new Texture(S);
        int[] cols = {0xFFB02020, 0xFF2040A0, 0xFFD0A020, 0xFF208040};
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                int b = Math.min(Math.min(x, S - 1 - x), Math.min(y, S - 1 - y));
                int c = b < 4 ? 0xFFE0C070 : b < 9 ? cols[1] : ((x / 8 + y / 8) % 2 == 0 ? cols[0] : cols[2]);
                if (b >= 9 && b < 12) c = 0xFF202020;
                float n = 0.8f + 0.2f * hash(x, y, 5);
                t.px[y * S + x] = pack(255, ((c >> 16) & 255) * n, ((c >> 8) & 255) * n, (c & 255) * n);
            }
        return t;
    }

    /** Letrero de tienda: el tablero ocupa la franja central (el resto es transparente). */
    static Texture signTex(String text, int bg, int fg, int border) {
        Texture t = new Texture(128);
        try {
            java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(128, 128, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = im.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(bg, true)); g.fillRoundRect(2, 34, 124, 60, 14, 14);
            g.setColor(new java.awt.Color(border, true)); g.setStroke(new java.awt.BasicStroke(4f)); g.drawRoundRect(4, 36, 120, 56, 12, 12);
            int size = 34;
            java.awt.Font f = new java.awt.Font(java.awt.Font.SERIF, java.awt.Font.BOLD, size);
            g.setFont(f);
            while (g.getFontMetrics().stringWidth(text) > 106 && size > 10) { size--; g.setFont(new java.awt.Font(java.awt.Font.SERIF, java.awt.Font.BOLD, size)); }
            java.awt.FontMetrics fm = g.getFontMetrics();
            g.setColor(new java.awt.Color(0, 0, 0, 160)); g.drawString(text, 64 - fm.stringWidth(text) / 2 + 2, 74 + 2);
            g.setColor(new java.awt.Color(fg, true)); g.drawString(text, 64 - fm.stringWidth(text) / 2, 74);
            g.dispose();
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                int c = im.getRGB(x, y);
                t.px[y * 128 + x] = (c >>> 24) < 60 ? 0 : (0xFF000000 | (c & 0xFFFFFF));
            }
        } catch (Throwable e) { java.util.Arrays.fill(t.px, bg); }
        return t;
    }
}
