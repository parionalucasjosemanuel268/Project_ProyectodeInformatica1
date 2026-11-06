package com.pacman3d.world;

import com.pacman3d.gfx.Mesh;
import com.pacman3d.gfx.MeshBuilder;
import com.pacman3d.gfx.Texture;
import com.pacman3d.gfx.Textures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Una ciudad de Halloween: laberinto + edificios + decoracion + luces + puntos de aparicion. */
public final class Level {
    public static final float TILE = 4f;
    public static final int CHUNK = 8;

    public final int index;            // 1..3
    public final Maze maze;
    public final int ghostCount, chestCount, starCount, ammoCount, medkitCount, acidCount, spikeCount, soulCount = 20;
    public final float ghostSpeed, ghostHp, aware;
    public final float[][] wallH;
    public final List<float[]> lights = new ArrayList<>();      // x,y,z,r,g,b,radio
    public final List<float[]> blockers = new ArrayList<>();    // x,z,radio
    public final List<float[]> chestSpots = new ArrayList<>();  // x,z,yaw
    public final List<float[]> ghostSpots = new ArrayList<>();  // x,z
    public final List<float[]> starSpots = new ArrayList<>(), ammoSpots = new ArrayList<>(), medSpots = new ArrayList<>();
    public final List<float[]> acidSpots = new ArrayList<>(), spikeSpots = new ArrayList<>(), shopSpots = new ArrayList<>(), soulSpots = new ArrayList<>();
    private final List<float[]> reserved = new ArrayList<>();
    public final List<int[]> roamTiles = new ArrayList<>();   // casillas por donde pueden aparecer almas nuevas (todo el laberinto)
    public float[] keySpot;            // si la llave esta en el suelo (si no, va en un cofre)
    public boolean keyInChest;
    public Chunk[][] chunks;
    public int chunksX, chunksZ;
    public final float gateX, gateZ, gateDX, gateDZ;   // plano del porton y direccion hacia la salida
    public boolean doorOpen;
    public final long seed;
    private final Random rnd;
    private final float[] amb = {0.20f, 0.23f, 0.37f};

    public static final class Chunk {
        public Mesh[] meshes;
        public float cx, cz, rad;
    }

    public Level(int index, long seed) {
        this.index = index;
        this.seed = seed;
        rnd = new Random(seed * 31 + index);
        int cells; float braid; int plazas;
        switch (index) {
            case 1: cells = 14; braid = 0.55f; plazas = 5; acidCount = 5; spikeCount = 4; ghostCount = 20; chestCount = 16; starCount = 34; ammoCount = 16; medkitCount = 8; ghostSpeed = 4.6f; ghostHp = 100; aware = 70; break;
            case 2: cells = 19; braid = 0.30f; plazas = 6; acidCount = 9; spikeCount = 7; ghostCount = 40; chestCount = 22; starCount = 46; ammoCount = 26; medkitCount = 11; ghostSpeed = 5.1f; ghostHp = 130; aware = 90; break;
            default: cells = 24; braid = 0.12f; plazas = 8; acidCount = 13; spikeCount = 11; ghostCount = 60; chestCount = 28; starCount = 58; ammoCount = 38; medkitCount = 14; ghostSpeed = 5.6f; ghostHp = 160; aware = 120; break;
        }
        maze = new Maze(seed * 7 + index, cells, cells, braid, plazas);
        wallH = new float[maze.W][maze.H];
        for (int x = 0; x < maze.W; x++) for (int z = 0; z < maze.H; z++) wallH[x][z] = 8.5f + 4.5f * h3(x, z, 3);
        int ddx = maze.doorX - maze.inX, ddz = maze.doorZ - maze.inZ;
        gateDX = ddx; gateDZ = ddz;
        gateX = (maze.inX + 0.5f + ddx * 0.5f) * TILE;
        gateZ = (maze.inZ + 0.5f + ddz * 0.5f) * TILE;
        chooseShops();
        placeTraps();
        placeLights();
        placeWindowLights();
        build();
        populate();
    }

    // ---------------------------------------------------------------- utilidades
    float h3(int x, int z, int s) {
        int h = x * 73856093 ^ z * 19349663 ^ s * 83492791 ^ (int) seed * 2654435;
        h = (h ^ (h >>> 15)) * 0x2c1b3c6d; h ^= h >>> 12;
        return (h & 0xffff) / 65535f;
    }

    public static int tileOf(float v) { return (int) Math.floor(v / TILE); }
    public static float center(int t) { return (t + 0.5f) * TILE; }

    public boolean solidAt(float x, float z) {
        int tx = tileOf(x), tz = tileOf(z);
        if (maze.isSolid(tx, tz)) return true;
        return !doorOpen && tx == maze.doorX && tz == maze.doorZ;
    }

    public boolean tileSolid(int tx, int tz) {
        if (maze.isSolid(tx, tz)) return true;
        return !doorOpen && tx == maze.doorX && tz == maze.doorZ;
    }

    public boolean isInDoor(float x, float z) { return tileOf(x) == maze.doorX && tileOf(z) == maze.doorZ; }

    // ---------------------------------------------------------------- luces
    private boolean nearSpawn(int tx, int tz, int r) { return Math.abs(tx - maze.spawnX) <= r && Math.abs(tz - maze.spawnZ) <= r; }

    private void placeLights() {
        for (int x = 1; x < maze.W - 1; x++)
            for (int z = 1; z < maze.H - 1; z++) {
                if (maze.solid[x][z]) continue;
                float h = h3(x, z, 11);
                boolean spawnArea = nearSpawn(x, z, 1);
                if (isReserved(center(x), center(z))) continue;
                if ((h < 0.045f && !spawnArea) || (x == maze.spawnX && z == maze.spawnZ + 1)) {
                    float[] c = cornerOffset(x, z);
                    if (h3(x, z, 12) < 0.25f && !(x == maze.spawnX)) continue;   // farola rota: sin luz
                    lights.add(new float[]{center(x) + c[0], 4.1f, center(z) + c[1], 1.0f, 0.70f, 0.32f, 15f, 1f});
                    lampSpots.add(new float[]{center(x) + c[0], center(z) + c[1]});
                }
            }
    }

    private final List<float[]> lampSpots = new ArrayList<>();

    /** Esquina del tile pegada a una pared (para farolas y objetos). */
    float[] cornerOffset(int x, int z) {
        float ox = 1.6f, oz = 1.6f;
        if (maze.isSolid(x - 1, z)) ox = -1.6f; else if (maze.isSolid(x + 1, z)) ox = 1.6f; else ox = h3(x, z, 4) < 0.5f ? -1.6f : 1.6f;
        if (maze.isSolid(x, z - 1)) oz = -1.6f; else if (maze.isSolid(x, z + 1)) oz = 1.6f; else oz = h3(x, z, 5) < 0.5f ? -1.6f : 1.6f;
        return new float[]{ox, oz};
    }

    private void light(float x, float y, float z, float nx, float ny, float nz, float ao, float[] out) {
        float r = amb[0] * ao, g = amb[1] * ao, b = amb[2] * ao;
        // un poco de luz del cielo segun la normal
        float up = 0.8f + 0.2f * ny;
        r *= up; g *= up; b *= up;
        for (float[] l : lights) {
            float dx = l[0] - x, dy = l[1] - y, dz = l[2] - z;
            float d2 = dx * dx + dy * dy + dz * dz, R = l[6];
            if (d2 > R * R) continue;
            float d = (float) Math.sqrt(d2) + 1e-4f;
            float k = 1f - d / R;
            k *= k;
            float dot = (nx * dx + ny * dy + nz * dz) / d;
            dot = Math.max(0.12f, dot);
            float a = k * dot * 1.4f;
            r += l[3] * a; g += l[4] * a; b += l[5] * a;
        }
        out[0] = r; out[1] = g; out[2] = b;
    }

    // ---------------------------------------------------------------- construccion de mallas
    private static final class CB {
        final Map<Long, MeshBuilder> map = new HashMap<>();
        MeshBuilder get(Texture t, int flags) {
            long k = ((long) t.id << 8) | flags;
            return map.computeIfAbsent(k, kk -> new MeshBuilder(t, flags, 1f));
        }
    }

    private CB[][] cbs;

    private CB cb(float x, float z) {
        int cx = Math.max(0, Math.min(chunksX - 1, tileOf(x) / CHUNK)), cz = Math.max(0, Math.min(chunksZ - 1, tileOf(z) / CHUNK));
        return cbs[cx][cz];
    }

    private void build() {
        chunksX = (maze.W + CHUNK - 1) / CHUNK; chunksZ = (maze.H + CHUNK - 1) / CHUNK;
        cbs = new CB[chunksX][chunksZ];
        for (int i = 0; i < chunksX; i++) for (int j = 0; j < chunksZ; j++) cbs[i][j] = new CB();
        float[] L = new float[3];
        // ---- suelos
        for (int tx = 0; tx < maze.W; tx++)
            for (int tz = 0; tz < maze.H; tz++) {
                if (maze.solid[tx][tz]) continue;
                CB c = cbs[tx / CHUNK][tz / CHUNK];
                Texture ft = h3(tx, tz, 20) < 0.22f ? Textures.asphalt : Textures.cobble;
                MeshBuilder b = c.get(ft, 0);
                int n = 2;
                int[][] g = new int[n + 1][n + 1];
                for (int i = 0; i <= n; i++)
                    for (int j = 0; j <= n; j++) {
                        float x = (tx + i / (float) n) * TILE, z = (tz + j / (float) n) * TILE;
                        int cnt = 0;
                        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) if (maze.isSolid(tileOf(x + sx * 0.02f), tileOf(z + sz * 0.02f))) cnt++;
                        light(x, 0.2f, z, 0, 1, 0, 1f - 0.15f * cnt, L);
                        g[i][j] = b.vc(x, 0, z, (i / (float) n) * 1f, (j / (float) n) * 1f, 0, 1, 0, L[0], L[1], L[2]);
                    }
                for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) b.quad(g[i][j], g[i + 1][j], g[i + 1][j + 1], g[i][j + 1]);
            }
        // ---- paredes (edificios)
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int tx = 0; tx < maze.W; tx++)
            for (int tz = 0; tz < maze.H; tz++) {
                if (!maze.solid[tx][tz]) continue;
                for (int[] d : dirs) {
                    int nx = tx + d[0], nz = tz + d[1];
                    if (maze.isSolid(nx, nz)) continue;
                    wallFace(tx, tz, d[0], d[1], L);
                }
            }
        // el portal tras la puerta y el techo de la alcoba
        portal(L);
        // ---- decoracion
        decorate();
        // ---- ensamblar chunks
        chunks = new Chunk[chunksX][chunksZ];
        for (int i = 0; i < chunksX; i++)
            for (int j = 0; j < chunksZ; j++) {
                Chunk ch = new Chunk();
                List<Mesh> ms = new ArrayList<>();
                for (MeshBuilder b : cbs[i][j].map.values()) ms.add(b.build());
                ch.meshes = ms.toArray(new Mesh[0]);
                ch.cx = (i + 0.5f) * CHUNK * TILE; ch.cz = (j + 0.5f) * CHUNK * TILE;
                ch.rad = CHUNK * TILE * 0.75f + 6f;
                chunks[i][j] = ch;
            }
        cbs = null;
    }

    private Texture faceTexture(int tx, int tz, int dx, int dz) {
        float r = h3(tx * 3 + dx, tz * 3 + dz, 30);
        Texture t = r < 0.12f ? Textures.brick[(int) (h3(tx, tz, 31) * 3) % 3]
                : r < 0.5f ? Textures.facade[0] : r < 0.68f ? Textures.facade[1] : r < 0.80f ? Textures.facade[2] : r < 0.9f ? Textures.facade[3] : Textures.brick[0];
        if (tx == 0 || tz == 0 || tx == maze.W - 1 || tz == maze.H - 1) t = Textures.brick[1];
        return t;
    }

    /** Las ventanas encendidas iluminan la calle. */
    private void placeWindowLights() {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int tx = 1; tx < maze.W - 1; tx++)
            for (int tz = 1; tz < maze.H - 1; tz++) {
                if (!maze.solid[tx][tz]) continue;
                for (int[] d : dirs) {
                    if (maze.isSolid(tx + d[0], tz + d[1])) continue;
                    Texture t = faceTexture(tx, tz, d[0], d[1]);
                    boolean amber = t == Textures.facade[0], green = t == Textures.facade[2];
                    if (!amber && !green) continue;
                    if (h3(tx * 5 + d[0], tz * 5 + d[1], 33) > 0.45f) continue;
                    float fx = (tx + 0.5f + d[0] * 0.5f) * TILE + d[0] * 0.6f, fz = (tz + 0.5f + d[1] * 0.5f) * TILE + d[1] * 0.6f;
                    lights.add(amber ? new float[]{fx, 3.4f, fz, 0.95f, 0.55f, 0.15f, 7.5f} : new float[]{fx, 3.4f, fz, 0.2f, 0.9f, 0.4f, 7.5f});
                }
            }
    }

    private void wallFace(int tx, int tz, int dx, int dz, float[] L) {
        float H = wallH[tx][tz];
        Texture t = faceTexture(tx, tz, dx, dz);
        CB c = cbs[tx / CHUNK][tz / CHUNK];
        MeshBuilder b = c.get(t, 0);
        // arista base del plano de la cara
        float x0, z0, x1, z1;
        if (dx != 0) { x0 = x1 = (dx > 0 ? tx + 1 : tx) * TILE; z0 = tz * TILE; z1 = (tz + 1) * TILE; if (dx < 0) { float s = z0; z0 = z1; z1 = s; } }
        else { z0 = z1 = (dz > 0 ? tz + 1 : tz) * TILE; x0 = (tx + 1) * TILE; x1 = tx * TILE; if (dz < 0) { float s = x0; x0 = x1; x1 = s; } }
        int nu = 2, nv = 4;
        int[][] g = new int[nu + 1][nv + 1];
        for (int i = 0; i <= nu; i++) {
            float s = i / (float) nu;
            float x = x0 + (x1 - x0) * s, z = z0 + (z1 - z0) * s;
            for (int j = 0; j <= nv; j++) {
                float y = H * j / nv;
                float ao = 0.45f + 0.55f * Math.min(1f, y / 4f);
                light(x + dx * 0.05f, y, z + dz * 0.05f, dx, 0, dz, ao, L);
                float u = s, v = (H - y) / TILE;
                g[i][j] = b.vc(x, y, z, u, v, dx, 0, dz, L[0], L[1], L[2]);
            }
        }
        for (int i = 0; i < nu; i++) for (int j = 0; j < nv; j++) b.quad(g[i][j], g[i + 1][j], g[i + 1][j + 1], g[i][j + 1]);
        facadeDetail(tx, tz, dx, dz, H, t, c);
    }

    /** Relieve 3D en las fachadas: zocalo, cornisas, parapeto, pilastras, toldos y bajantes. */
    private void facadeDetail(int tx, int tz, int dx, int dz, float H, Texture t, CB c) {
        float cx = (tx + 0.5f + dx * 0.5f) * TILE, cz = (tz + 0.5f + dz * 0.5f) * TILE;
        float yaw = (float) Math.atan2(dx, dz);
        float[] L = bake(cx + dx * 0.4f, 1.5f, cz + dz * 0.4f);
        float k = 1.5f;
        MeshBuilder m = c.get(Textures.stone, 0).color(L[0] * k + 0.12f, L[1] * k + 0.12f, L[2] * k + 0.16f);
        // zocalo
        m.push().translate(cx + dx * 0.1f, 0.3f, cz + dz * 0.1f).rotY(yaw).box(TILE, 0.6f, 0.2f).pop();
        // cornisas entre pisos
        float[] Lm = bake(cx + dx * 0.4f, 4f, cz + dz * 0.4f);
        m.color(Lm[0] * k + 0.1f, Lm[1] * k + 0.1f, Lm[2] * k + 0.14f);
        m.push().translate(cx + dx * 0.15f, 3.9f, cz + dz * 0.15f).rotY(yaw).box(TILE, 0.22f, 0.3f).pop();
        if (H > 9.5f) m.push().translate(cx + dx * 0.15f, 7.6f, cz + dz * 0.15f).rotY(yaw).box(TILE, 0.22f, 0.3f).pop();
        // parapeto del tejado
        float[] Lt = bake(cx + dx * 0.4f, H, cz + dz * 0.4f);
        m.color(Lt[0] * k + 0.1f, Lt[1] * k + 0.1f, Lt[2] * k + 0.16f);
        m.push().translate(cx + dx * 0.2f, H - 0.3f, cz + dz * 0.2f).rotY(yaw).box(TILE, 0.6f, 0.45f).pop();
        m.push().translate(cx + dx * 0.2f, H + 0.25f, cz + dz * 0.2f).rotY(yaw).box(TILE, 0.15f, 0.6f).pop();
        float h = h3(tx * 7 + dx, tz * 7 + dz, 34);
        float px = -dz, pz = dx; // tangente
        if (h < 0.35f) { // pilastras en los extremos
            for (int s = -1; s <= 1; s += 2) m.push().translate(cx + dx * 0.15f + px * s * (TILE / 2f - 0.17f), H / 2f, cz + dz * 0.15f + pz * s * (TILE / 2f - 0.17f)).rotY(yaw).box(0.34f, H, 0.3f).pop();
        }
        boolean windowed = t != Textures.brick[0] && t != Textures.brick[1] && t != Textures.brick[2];
        if (windowed && h > 0.55f && h < 0.85f) { // toldo inclinado sobre las ventanas bajas
            float[][] cols = {{0.55f, 0.1f, 0.1f}, {0.1f, 0.35f, 0.15f}, {0.35f, 0.12f, 0.45f}, {0.5f, 0.4f, 0.1f}};
            float[] col = cols[(int) (h3(tx, tz, 35) * 4) % 4];
            MeshBuilder aw = c.get(Textures.cloth, 0).color(col[0] * (L[0] * 2 + 0.3f), col[1] * (L[1] * 2 + 0.3f), col[2] * (L[2] * 2 + 0.3f));
            aw.push().translate(cx + dx * 0.55f, 3.2f, cz + dz * 0.55f).rotY(yaw).rotX(-0.45f).box(TILE - 0.4f, 0.06f, 1.1f).pop();
        }
        if (h3(tx * 11 + dx, tz * 11 + dz, 36) < 0.22f) { // bajante
            float off = (h3(tx, tz, 37) < 0.5f ? -1 : 1) * (TILE / 2f - 0.4f);
            c.get(Textures.metal, 0).color(L[0] * 1.4f + 0.1f, L[1] * 1.4f + 0.1f, L[2] * 1.4f + 0.12f)
                    .push().translate(cx + dx * 0.1f + px * off, (H - 0.5f) / 2f, cz + dz * 0.1f + pz * off).cylinder(0.07f, 0.07f, H - 0.5f, 6, false).pop();
        }
    }

    private void portal(float[] L) {
        // fondo luminoso al otro lado del porton (visible solo cuando se abre)
        int tx = maze.doorX, tz = maze.doorZ;
        CB c = cbs[Math.min(tx / CHUNK, chunksX - 1)][Math.min(tz / CHUNK, chunksZ - 1)];
        MeshBuilder b = c.get(Textures.glow, Mesh.EMISSIVE);
        b.color(0.45f, 1.0f, 0.65f);
        float dx = gateDX, dz = gateDZ;
        float cx = (tx + 0.5f + dx * 0.5f) * TILE, cz = (tz + 0.5f + dz * 0.5f) * TILE;
        float px = -dz, pz = dx;
        float w = TILE / 2f;
        b.quadP(cx - px * w, 0, cz - pz * w, cx + px * w, 0, cz + pz * w, cx + px * w, 8, cz + pz * w, cx - px * w, 8, cz - pz * w, 0.5f, 0.5f, 0.5f, 0.5f);
        // un mundo exterior: luz que ilumina la alcoba
        lights.add(new float[]{center(tx), 3f, center(tz), 0.3f, 0.9f, 0.5f, 7f});
    }

    // ---------------------------------------------------------------- decoracion
    private void decorate() {
        float[] L = new float[3];
        buildShops();
        buildTraps();
        // farolas
        for (float[] s : lampSpots) lamp(s[0], s[1]);
        for (int tx = 1; tx < maze.W - 1; tx++)
            for (int tz = 1; tz < maze.H - 1; tz++) {
                if (maze.solid[tx][tz]) continue;
                if (nearSpawn(tx, tz, 2) && maze.plazas.get(0)[0] == tx && false) continue;
                float cx = center(tx), cz = center(tz);
                float h = h3(tx, tz, 40);
                boolean deadEnd = maze.openNeighbors(tx, tz) == 1;
                float[] co = cornerOffset(tx, tz);
                boolean spawnZone = nearSpawn(tx, tz, 2);
                if (spawnZone) { if (h < 0.15f) tomb(cx + co[0], cz + co[1], h3(tx, tz, 41) * 6.28f); continue; }
                if (deadEnd) {
                    pumpkin(cx + co[0] * 0.8f, cz + co[1] * 0.8f, yawToOpen(tx, tz));
                    if (h < 0.6f) tomb(cx - co[0] * 0.6f, cz - co[1] * 0.6f, h3(tx, tz, 42) * 6.28f);
                    web(tx, tz);
                    if (h3(tx, tz, 43) < 0.5f) bones(cx + co[0] * 0.4f, cz - co[1] * 0.3f);
                    continue;
                }
                if (h < 0.060f) pumpkin(cx + co[0], cz + co[1], h3(tx, tz, 44) * 6.28f);
                else if (h < 0.095f) tomb(cx + co[0], cz + co[1] * 0.9f, h3(tx, tz, 45) * 6.28f);
                else if (h < 0.125f) crate(cx + co[0] * 0.85f, cz + co[1] * 0.85f, h3(tx, tz, 46) * 6.28f);
                else if (h < 0.145f) barrel(cx + co[0], cz + co[1]);
                else if (h < 0.165f) bones(cx + co[0] * 0.5f, cz + co[1] * 0.4f);
                else if (h < 0.18f) cross(cx + co[0], cz + co[1], h3(tx, tz, 47) * 6.28f);
                if (h3(tx, tz, 48) < 0.07f) decal(cx + (h3(tx, tz, 49) - 0.5f) * 3f, cz + (h3(tx, tz, 50) - 0.5f) * 3f);
                if (h3(tx, tz, 51) < 0.03f) web(tx, tz);
                if (h3(tx, tz, 110) < 0.05f) floorDecal(Textures.manhole, cx + (h3(tx, tz, 111) - 0.5f) * 1.5f, cz + (h3(tx, tz, 112) - 0.5f) * 1.5f, 1.8f, 0, 2.2f);
                if (h3(tx, tz, 113) < 0.07f) floorDecal(Textures.puddle, cx + (h3(tx, tz, 114) - 0.5f) * 2.4f, cz + (h3(tx, tz, 115) - 0.5f) * 2.4f, 2.2f + h3(tx, tz, 116) * 2f, h3(tx, tz, 117) * 6.28f, 1.5f);
                if (h3(tx, tz, 118) < 0.12f) floorDecal(Textures.leafDecal, cx + (h3(tx, tz, 119) - 0.5f) * 3f, cz + (h3(tx, tz, 120) - 0.5f) * 3f, 2.6f, h3(tx, tz, 121) * 6.28f, 2.5f);
                if (h3(tx, tz, 122) < 0.04f && (maze.isSolid(tx - 1, tz) || maze.isSolid(tx + 1, tz)) && !maze.isSolid(tx, tz - 1) && !maze.isSolid(tx, tz + 1)) fence(cx + (maze.isSolid(tx - 1, tz) ? -1.7f : 1.7f), cz, 1.5708f);
            }
        // plazas: arbol muerto, tumbas, sarcofagos
        for (int i = 0; i < maze.plazas.size(); i++) {
            int[] p = maze.plazas.get(i);
            float cx = center(p[0]), cz = center(p[1]);
            if (i == 0) { fountain(cx, cz); continue; }
            deadTree(cx, cz, 0.9f + h3(p[0], p[1], 60) * 0.6f);
            blockers.add(new float[]{cx, cz, 0.7f});
            for (int k = 0; k < 8; k++) {
                float a = k * 0.785f + h3(p[0], p[1], 61 + k) * 0.5f;
                float rr = 4.3f + 1.0f * h3(p[0], p[1], 70 + k);
                float px = cx + (float) Math.cos(a) * rr, pz = cz + (float) Math.sin(a) * rr;
                if (k % 3 == 2) cross(px, pz, a); else tomb(px, pz, a + 1.57f);
            }
            for (int k = 0; k < 4; k++) { float a = 0.4f + k * 1.57f; pumpkin(cx + (float) Math.cos(a) * 2.3f, cz + (float) Math.sin(a) * 2.3f, a + 3.14f); }
            coffin(cx + 3.0f, cz - 2.0f, h3(p[0], p[1], 90) * 3f);
            if (i % 2 == 0) scarecrow(cx - 3.0f, cz + 2.5f, h3(p[0], p[1], 91) * 3f);
        }
        // faroles junto a la puerta
        float gx = gateX - gateDX * 2.0f + gateDZ * 1.7f, gz = gateZ - gateDZ * 2.0f - gateDX * 1.7f;
        torch(gx, gz);
        torch(gateX - gateDX * 2.0f - gateDZ * 1.7f, gateZ - gateDZ * 2.0f + gateDX * 1.7f);
    }

    private float yawToOpen(int tx, int tz) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (!maze.isSolid(tx + d[0], tz + d[1])) return (float) Math.atan2(d[0], d[1]);
        return 0;
    }

    private float[] bake(float x, float y, float z) {
        float[] L = new float[3];
        light(x, y, z, 0, 1, 0, 1f, L);
        return L;
    }

    private void lamp(float x, float z) {
        if (isReserved(x, z)) return;
        CB c = cb(x, z);
        float[] L = bake(x, 1f, z);
        MeshBuilder m = c.get(Textures.metal, 0).color(L[0] * 1.1f + 0.05f, L[1] * 1.1f + 0.05f, L[2] * 1.1f + 0.05f);
        m.push().translate(x, 2.0f, z).cylinder(0.11f, 0.07f, 4.0f, 8, true).pop();
        m.push().translate(x, 0.2f, z).cylinder(0.25f, 0.18f, 0.4f, 8, true).pop();
        m.push().translate(x, 4.0f, z).box(0.5f, 0.1f, 0.5f).pop();
        MeshBuilder e = c.get(Textures.white, Mesh.EMISSIVE).color(1.0f, 0.82f, 0.45f);
        e.push().translate(x, 4.3f, z).box(0.32f, 0.5f, 0.32f).pop();
        m.push().translate(x, 4.7f, z).cone(0.38f, 0.3f, 6).pop();
    }

    private void torch(float x, float z) {
        CB c = cb(x, z);
        MeshBuilder m = c.get(Textures.metal, 0).color(0.5f, 0.5f, 0.55f);
        m.push().translate(x, 0.9f, z).cylinder(0.12f, 0.1f, 1.8f, 6, true).pop();
        m.push().translate(x, 1.9f, z).cylinder(0.3f, 0.12f, 0.35f, 8, false).pop();
        c.get(Textures.white, Mesh.EMISSIVE).color(0.3f, 1.0f, 0.5f).push().translate(x, 2.05f, z).sphere(0.25f, 0.4f, 0.25f, 8, 6).pop();
        lights.add(new float[]{x, 2.2f, z, 0.25f, 0.9f, 0.45f, 8f, 1f});
    }

    private void pumpkin(float x, float z, float yaw) {
        if (isReserved(x, z)) return;
        CB c = cb(x, z);
        float s = 0.8f + 0.5f * h3((int) (x * 10), (int) (z * 10), 80);
        MeshBuilder m = c.get(Textures.pumpkin, 0).color(1.1f, 1.1f, 1.1f);
        m.push().translate(x, 0.5f * s, z).rotY(yaw).scale(s, s, s).sphere(0.62f, 0.5f, 0.62f, 14, 10).pop();
        c.get(Textures.bark, 0).color(0.7f, 0.9f, 0.5f).push().translate(x, 1.0f * s, z).rotZ(0.2f).cylinder(0.1f, 0.07f, 0.28f, 6, true).pop();
        lights.add(new float[]{x, 0.7f, z, 1.0f, 0.5f, 0.12f, 7f, 1f});
    }

    private void tomb(float x, float z, float yaw) {
        if (isReserved(x, z)) return;
        if (h3((int) (x * 5), (int) (z * 5), 123) < 0.35f) candles(x + 0.9f, z + 0.3f);
        CB c = cb(x, z);
        float[] L = bake(x, 0.8f, z);
        MeshBuilder m = c.get(Textures.stone, 0).color(L[0] * 3f + 0.35f, L[1] * 3f + 0.35f, L[2] * 3f + 0.4f);
        float tilt = (h3((int) (x * 7), (int) (z * 7), 81) - 0.5f) * 0.25f;
        m.push().translate(x, 0.65f, z).rotY(yaw).rotZ(tilt).box(1.0f, 1.3f, 0.28f).pop();
        m.push().translate(x, 1.3f, z).rotY(yaw).rotZ(tilt).rotX(1.5708f).cylinder(0.5f, 0.5f, 0.28f, 10, true).pop();
        m.push().translate(x, 0.12f, z).rotY(yaw).box(1.2f, 0.25f, 0.9f).pop();
    }

    private void cross(float x, float z, float yaw) {
        if (isReserved(x, z)) return;
        CB c = cb(x, z);
        float[] L = bake(x, 0.8f, z);
        MeshBuilder m = c.get(Textures.stone, 0).color(L[0] * 3f + 0.3f, L[1] * 3f + 0.3f, L[2] * 3f + 0.35f);
        float tilt = (h3((int) (x * 5), (int) (z * 5), 82) - 0.5f) * 0.3f;
        m.push().translate(x, 0.8f, z).rotY(yaw).rotZ(tilt).box(0.22f, 1.6f, 0.22f).pop();
        m.push().translate(x, 1.15f, z).rotY(yaw).rotZ(tilt).box(0.9f, 0.2f, 0.2f).pop();
    }

    private void crate(float x, float z, float yaw) {
        if (isReserved(x, z)) return;
        CB c = cb(x, z);
        float[] L = bake(x, 0.8f, z);
        MeshBuilder m = c.get(Textures.crate, 0).color(L[0] * 3f + 0.3f, L[1] * 3f + 0.3f, L[2] * 3f + 0.3f);
        m.push().translate(x, 0.5f, z).rotY(yaw).box(1.0f, 1.0f, 1.0f).pop();
        if (h3((int) (x * 3), (int) (z * 3), 83) < 0.5f) m.push().translate(x + 0.1f, 1.3f, z).rotY(yaw + 0.6f).box(0.7f, 0.6f, 0.7f).pop();
    }

    private void barrel(float x, float z) {
        if (isReserved(x, z)) return;
        CB c = cb(x, z);
        float[] L = bake(x, 0.8f, z);
        c.get(Textures.wood, 0).color(L[0] * 3f + 0.25f, L[1] * 3f + 0.25f, L[2] * 3f + 0.25f).push().translate(x, 0.55f, z).cylinder(0.45f, 0.45f, 1.1f, 10, true).pop();
        MeshBuilder m = c.get(Textures.metal, 0).color(L[0] * 3f + 0.2f, L[1] * 3f + 0.2f, L[2] * 3f + 0.2f);
        m.push().translate(x, 0.25f, z).cylinder(0.47f, 0.47f, 0.07f, 10, false).pop();
        m.push().translate(x, 0.85f, z).cylinder(0.47f, 0.47f, 0.07f, 10, false).pop();
    }

    private void bones(float x, float z) {
        if (isReserved(x, z)) return;
        CB c = cb(x, z);
        float[] L = bake(x, 0.3f, z);
        MeshBuilder m = c.get(Textures.bone, 0).color(L[0] * 3f + 0.4f, L[1] * 3f + 0.4f, L[2] * 3f + 0.4f);
        for (int i = 0; i < 6; i++) {
            float a = h3((int) (x * 9) + i, (int) (z * 9), 84) * 6.28f;
            m.push().translate(x + (h3(i, (int) x, 85) - 0.5f) * 0.9f, 0.08f, z + (h3(i, (int) z, 86) - 0.5f) * 0.9f).rotY(a).rotZ(1.5708f).cylinder(0.05f, 0.05f, 0.5f, 5, true).pop();
        }
        m.push().translate(x, 0.2f, z).sphere(0.2f, 0.2f, 0.22f, 8, 6).pop();
        c.get(Textures.white, 0).color(0.02f, 0.02f, 0.02f).push().translate(x - 0.07f, 0.24f, z + 0.17f).sphere(0.05f, 0.06f, 0.04f, 5, 4).pop();
        c.get(Textures.white, 0).color(0.02f, 0.02f, 0.02f).push().translate(x + 0.07f, 0.24f, z + 0.17f).sphere(0.05f, 0.06f, 0.04f, 5, 4).pop();
    }

    private void decal(float x, float z) {
        if (isReserved(x, z)) return;
        CB c = cb(x, z);
        float[] L = bake(x, 0.1f, z);
        Texture t = h3((int) (x * 3), (int) (z * 3), 87) < 0.5f ? Textures.bloodA : Textures.bloodB;
        MeshBuilder m = c.get(t, 0).color(L[0] * 2.4f + 0.2f, L[1] * 2.4f + 0.2f, L[2] * 2.4f + 0.2f);
        float s = 1.0f + 1.1f * h3((int) (x * 5), (int) (z * 5), 88);
        float a = h3((int) (x * 5), (int) (z * 5), 89) * 6.28f;
        m.push().translate(x, 0.035f, z).rotY(a).scale(s, 1f, s);
        m.quadP(-1, 0, -1, -1, 0, 1, 1, 0, 1, 1, 0, -1, 0, 0, 1, 1);
        m.pop();
    }

    private void web(int tx, int tz) {
        // telarana pegada a una pared, en una esquina alta
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (!maze.isSolid(tx + d[0], tz + d[1])) continue;
            float px = center(tx) + d[0] * (TILE / 2f - 0.04f), pz = center(tz) + d[1] * (TILE / 2f - 0.04f);
            float[] L = bake(px, 3f, pz);
            MeshBuilder m = cb(px, pz).get(Textures.cobweb, 0).color(L[0] * 2f + 0.22f, L[1] * 2f + 0.22f, L[2] * 2f + 0.28f);
            float tx2 = d[1] != 0 ? 1 : 0, tz2 = d[0] != 0 ? 1 : 0;       // tangente a la pared
            float sg = h3(tx, tz, 52) < 0.5f ? -1 : 1;
            float w = 1.9f, top = 4.6f;
            float cx = px + tx2 * sg * (TILE / 2f), cz = pz + tz2 * sg * (TILE / 2f);   // esquina
            float ex = -tx2 * sg * w, ez = -tz2 * sg * w;                                // hacia el centro
            m.quadP(cx, top - w, cz, cx + ex, top - w, cz + ez, cx + ex, top, cz + ez, cx, top, cz, 0, 0, 1, 1);
            break;
        }
    }

    private void deadTree(float x, float z, float s) {
        if (isReserved(x, z)) return;
        CB c = cb(x, z);
        float[] L = bake(x, 1.5f, z);
        MeshBuilder m = c.get(Textures.bark, 0).color(L[0] * 3f + 0.3f, L[1] * 3f + 0.3f, L[2] * 3f + 0.35f);
        m.push().translate(x, 2.2f * s, z).cylinder(0.45f * s, 0.22f * s, 4.4f * s, 8, true).pop();
        Random r = new Random((long) (x * 31 + z * 17));
        for (int i = 0; i < 7; i++) {
            float yy = (2.2f + i * 0.45f) * s, a = r.nextFloat() * 6.28f, tilt = 0.7f + r.nextFloat() * 0.5f, len = (1.8f + r.nextFloat() * 1.6f) * s;
            m.push().translate(x, yy, z).rotY(a).rotZ(tilt).translate(0, len / 2, 0).cylinder(0.14f * s, 0.04f * s, len, 5, true);
            m.translate(0, len * 0.45f, 0).rotZ(-0.9f + r.nextFloat() * 1.8f).translate(0, len * 0.3f, 0).cylinder(0.08f * s, 0.02f * s, len * 0.6f, 5, true);
            m.pop();
        }
    }

    private void candles(float x, float z) {
        if (isReserved(x, z)) return;
        CB c = cb(x, z);
        MeshBuilder wax = c.get(Textures.bone, 0).color(1.2f, 1.15f, 1.0f);
        MeshBuilder fl = c.get(Textures.white, Mesh.EMISSIVE).color(1.4f, 0.9f, 0.3f);
        for (int i = 0; i < 3; i++) {
            float ox = (h3((int) (x * 3) + i, (int) z, 101) - 0.5f) * 0.9f, oz = (h3((int) x, (int) (z * 3) + i, 102) - 0.5f) * 0.9f, hh = 0.2f + h3(i, (int) x, 103) * 0.3f;
            wax.push().translate(x + ox, hh / 2, z + oz).cylinder(0.05f, 0.05f, hh, 6, true).pop();
            fl.push().translate(x + ox, hh + 0.08f, z + oz).sphere(0.04f, 0.09f, 0.04f, 5, 4).pop();
        }
        lights.add(new float[]{x, 0.5f, z, 1.0f, 0.65f, 0.25f, 4.5f, 1f});
    }

    private void fence(float x, float z, float yaw) {
        if (isReserved(x, z)) return;
        CB c = cb(x, z);
        float[] L = bake(x, 1f, z);
        MeshBuilder m = c.get(Textures.metal, 0).color(L[0] * 1.5f + 0.1f, L[1] * 1.5f + 0.1f, L[2] * 1.5f + 0.14f);
        m.push().translate(x, 0, z).rotY(yaw);
        for (int i = -4; i <= 4; i++) {
            m.push().translate(i * 0.4f, 0.8f, 0).box(0.05f, 1.6f, 0.05f).pop();
            m.push().translate(i * 0.4f, 1.65f, 0).cone(0.06f, 0.22f, 4).pop();
        }
        m.push().translate(0, 0.35f, 0).box(3.6f, 0.06f, 0.06f).pop();
        m.push().translate(0, 1.3f, 0).box(3.6f, 0.06f, 0.06f).pop();
        m.pop();
    }

    private void scarecrow(float x, float z, float yaw) {
        if (isReserved(x, z)) return;
        CB c = cb(x, z);
        float[] L = bake(x, 1.5f, z);
        MeshBuilder w = c.get(Textures.plank, 0).color(L[0] * 2.5f + 0.2f, L[1] * 2.5f + 0.2f, L[2] * 2.5f + 0.2f);
        w.push().translate(x, 1.4f, z).cylinder(0.08f, 0.08f, 2.8f, 6, true).pop();
        w.push().translate(x, 2.0f, z).rotY(yaw).box(2.0f, 0.1f, 0.1f).pop();
        MeshBuilder b = c.get(Textures.cloth, 0).color(L[0] * 2.2f + 0.3f, L[1] * 1.6f + 0.2f, L[2] * 1.6f + 0.2f);
        b.push().translate(x, 1.6f, z).sphere(0.42f, 0.55f, 0.32f, 8, 6).pop();
        c.get(Textures.pumpkin, 0).color(1f, 1f, 1f).push().translate(x, 2.55f, z).rotY(yaw).sphere(0.3f, 0.3f, 0.3f, 10, 8).pop();
        c.get(Textures.bark, 0).color(0.4f, 0.3f, 0.2f).push().translate(x, 2.9f, z).cylinder(0.3f, 0.3f, 0.05f, 8, true).pop().push().translate(x, 3.1f, z).cone(0.22f, 0.4f, 8).pop();
        lights.add(new float[]{x, 2.5f, z, 1.0f, 0.5f, 0.1f, 5f, 1f});
        blockers.add(new float[]{x, z, 0.4f});
    }

    private void floorDecal(Texture t, float x, float z, float size, float rot, float k) {
        if (isReserved(x, z)) return;
        float[] L = bake(x, 0.1f, z);
        MeshBuilder m = cb(x, z).get(t, 0).color(L[0] * k + 0.2f, L[1] * k + 0.2f, L[2] * k + 0.2f);
        m.push().translate(x, 0.03f, z).rotY(rot).scale(size / 2, 1, size / 2);
        m.quadP(-1, 0, -1, -1, 0, 1, 1, 0, 1, 1, 0, -1, 0, 0, 1, 1);
        m.pop();
    }

    private void coffin(float x, float z, float yaw) {
        if (isReserved(x, z)) return;
        CB c = cb(x, z);
        float[] L = bake(x, 0.5f, z);
        MeshBuilder m = c.get(Textures.plank, 0).color(L[0] * 3f + 0.3f, L[1] * 3f + 0.25f, L[2] * 3f + 0.25f);
        m.push().translate(x, 0.45f, z).rotY(yaw).box(1.0f, 0.9f, 2.4f).pop();
        m.push().translate(x, 1.0f, z).rotY(yaw).translate(0.1f, 0, 0.2f).rotY(0.25f).box(1.1f, 0.18f, 2.5f).pop();
        blockers.add(new float[]{x, z, 0.9f});
    }

    private void fountain(float x, float z) {
        if (isReserved(x, z)) return;
        CB c = cb(x, z);
        float[] L = bake(x, 0.5f, z);
        MeshBuilder m = c.get(Textures.stone, 0).color(L[0] * 2f + 0.22f, L[1] * 2f + 0.22f, L[2] * 2f + 0.28f);
        m.push().translate(x, 0.3f, z).cylinder(2.0f, 1.8f, 0.6f, 12, true).pop();
        m.push().translate(x, 1.1f, z).cylinder(0.45f, 0.3f, 2.2f, 8, true).pop();
        m.push().translate(x, 2.3f, z).cylinder(1.0f, 0.5f, 0.35f, 10, true).pop();
        c.get(Textures.white, Mesh.EMISSIVE).color(0.15f, 0.5f, 0.2f).push().translate(x, 0.62f, z).cylinder(1.7f, 1.7f, 0.04f, 12, true).pop();
        c.get(Textures.white, Mesh.EMISSIVE).color(0.8f, 0.3f, 1.0f).push().translate(x, 2.9f, z).sphere(0.28f, 0.28f, 0.28f, 8, 6).pop();
        lights.add(new float[]{x, 2.9f, z, 0.6f, 0.25f, 0.9f, 10f, 1f});
        blockers.add(new float[]{x, z, 2.0f});
    }

    // ---------------------------------------------------------------- tiendas, trampas, zonas reservadas
    public boolean isReserved(float x, float z) {
        for (float[] r : reserved) { float dx = x - r[0], dz = z - r[1]; if (dx * dx + dz * dz < r[2] * r[2]) return true; }
        return false;
    }

    private boolean tileFree(int tx, int tz) { return !isReserved(center(tx), center(tz)); }

    /** 0 = nada, 1 = acido, 2 = puas. */
    public int trapAt(float x, float z) {
        for (float[] a : acidSpots) if (Math.abs(x - a[0]) < 1.0f && Math.abs(z - a[1]) < 1.0f) return 1;
        for (float[] a : spikeSpots) if (Math.abs(x - a[0]) < 1.0f && Math.abs(z - a[1]) < 1.0f) return 2;
        return 0;
    }

    private static float lx(float x, float yaw, float a, float b) { return x + a * (float) Math.cos(yaw) + b * (float) Math.sin(yaw); }
    private static float lz(float z, float yaw, float a, float b) { return z - a * (float) Math.sin(yaw) + b * (float) Math.cos(yaw); }

    /** Una tienda en el centro (plaza de inicio) y otras dos repartidas, cada una de estilo distinto. */
    private void chooseShops() {
        int[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};   // lado de la plaza: N, S, W, E (hacia fuera)
        for (int pass = 0; pass < 2 && shopSpots.size() < 3; pass++)
            for (int pi = 0; pi < maze.plazas.size() && shopSpots.size() < 3; pi++) {
                int[] pl = maze.plazas.get(pi);
                if (pass == 0 && pi > 0 && shopSpots.size() == 1 && pi == 1 && maze.plazas.size() > 3) { /* se salta una plaza para repartirlas */ }
                boolean found = false;
                for (int sd = 0; sd < 4 && !found; sd++) {
                    int di = (sd + pi + index) % 4;
                    if (pi == 0 && di >= 2) continue;               // en la plaza de inicio: solo norte o sur (aqui aparecen los jugadores)
                    int ox = dirs[di][0], oz = dirs[di][1];
                    for (int k = 0; k < 3 && !found; k++) {
                        int i = k == 0 ? 0 : k == 1 ? -1 : 1;
                        int tx = pl[0] + (ox == 0 ? i : ox), tz = pl[1] + (oz == 0 ? i : oz);        // tile del borde de la plaza
                        int wx = tx + ox, wz = tz + oz;                                                // tile de pared detras
                        if (!maze.isSolid(wx, wz) || maze.isSolid(tx, tz)) continue;
                        boolean dup = false;
                        for (float[] sp : shopSpots) if (Math.hypot(sp[0] - center(tx), sp[1] - center(tz)) < 9) dup = true;
                        if (dup) continue;
                        float wallX = ox != 0 ? (ox > 0 ? tx + 1 : tx) * TILE : center(tx), wallZ = oz != 0 ? (oz > 0 ? tz + 1 : tz) * TILE : center(tz);
                        float dirX = -ox, dirZ = -oz;
                        float sx = wallX + dirX * 1.3f, sz = wallZ + dirZ * 1.3f;
                        float yaw = (float) Math.atan2(dirX, dirZ);
                        int style = (shopSpots.size() + index - 1) % 3;
                        shopSpots.add(new float[]{sx, sz, yaw, style, dirX, dirZ});
                        reserved.add(new float[]{sx + dirX * 0.8f, sz + dirZ * 0.8f, 3.6f});
                        // el mostrador no se puede atravesar
                        for (int b = -1; b <= 1; b++) blockers.add(new float[]{lx(sx, yaw, b * 1.1f, -0.1f), lz(sz, yaw, b * 1.1f, -0.1f), 0.95f});
                        found = true;
                    }
                }
            }
    }

    private void placeTraps() {
        int[][] dist = maze.distances(maze.spawnX, maze.spawnZ);
        List<int[]> open = new ArrayList<>();
        for (int x = 1; x < maze.W - 1; x++)
            for (int z = 1; z < maze.H - 1; z++)
                if (!maze.solid[x][z] && dist[x][z] >= 6 && maze.openNeighbors(x, z) >= 2
                        && !(Math.abs(x - maze.inX) + Math.abs(z - maze.inZ) <= 2) && tileFree(x, z)) open.add(new int[]{x, z});
        java.util.Collections.shuffle(open, rnd);
        boolean[][] used = new boolean[maze.W][maze.H];
        int a = 0, sp = 0;
        for (int[] t : open) {
            if (a >= acidCount && sp >= spikeCount) break;
            boolean near = false;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (used[t[0] + dx][t[1] + dz]) near = true;
            if (near) continue;
            used[t[0]][t[1]] = true;
            float x = center(t[0]), z = center(t[1]);
            if (a < acidCount && (sp >= spikeCount || (a + sp) % 2 == 0)) { acidSpots.add(new float[]{x, z}); a++; }
            else if (sp < spikeCount) { spikeSpots.add(new float[]{x, z}); sp++; }
            reserved.add(new float[]{x, z, 1.9f});
        }
    }

    private void buildTraps() {
        for (float[] t : acidSpots) acidPool(t[0], t[1]);
        for (float[] t : spikeSpots) spikeTrap(t[0], t[1]);
    }

    private void acidPool(float x, float z) {
        CB c = cb(x, z);
        float[] L = bake(x, 0.1f, z);
        MeshBuilder rim = c.get(Textures.stone, 0).color(L[0] * 2f + 0.12f, L[1] * 2f + 0.14f, L[2] * 2f + 0.12f);
        rim.push().translate(x, 0.06f, z - 1.08f).box(2.4f, 0.14f, 0.2f).pop();
        rim.push().translate(x, 0.06f, z + 1.08f).box(2.4f, 0.14f, 0.2f).pop();
        rim.push().translate(x - 1.08f, 0.06f, z).box(0.2f, 0.14f, 2.0f).pop();
        rim.push().translate(x + 1.08f, 0.06f, z).box(0.2f, 0.14f, 2.0f).pop();
        MeshBuilder liq = c.get(Textures.acid, Mesh.EMISSIVE).color(0.55f, 0.95f, 0.5f);
        liq.push().translate(x, 0.05f, z);
        liq.quadP(-1, 0, -1, -1, 0, 1, 1, 0, 1, 1, 0, -1, 0, 0, 1, 1);
        liq.pop();
        floorDecal(Textures.bloodB, x + 1.6f, z + 0.3f, 1.4f, 1f, 1.5f);
        lights.add(new float[]{x, 0.9f, z, 0.25f, 1.0f, 0.3f, 6.5f, 1f});
    }

    private void spikeTrap(float x, float z) {
        CB c = cb(x, z);
        float[] L = bake(x, 0.3f, z);
        MeshBuilder base = c.get(Textures.metal, 0).color(L[0] * 1.5f + 0.1f, L[1] * 1.5f + 0.1f, L[2] * 1.5f + 0.12f);
        base.push().translate(x, 0.05f, z).box(2.2f, 0.1f, 2.2f).pop();
        MeshBuilder sp = c.get(Textures.spikeMetal, 0).color(L[0] * 3f + 0.35f, L[1] * 3f + 0.35f, L[2] * 3f + 0.4f);
        for (int i = -2; i <= 2; i++)
            for (int j = -2; j <= 2; j++) {
                float h = 0.65f + 0.15f * h3((int) (x * 3) + i, (int) (z * 3) + j, 130);
                sp.push().translate(x + i * 0.42f, 0.1f + h / 2, z + j * 0.42f).cone(0.1f, h, 5).pop();
            }
        MeshBuilder bl = c.get(Textures.bloodA, 0).color(L[0] * 2f + 0.25f, L[1] * 2f + 0.2f, L[2] * 2f + 0.2f);
        bl.push().translate(x, 0.12f, z).scale(1.1f, 1, 1.1f);
        bl.quadP(-1, 0, -1, -1, 0, 1, 1, 0, 1, 1, 0, -1, 0, 0, 1, 1);
        bl.pop();
        lights.add(new float[]{x, 0.8f, z, 0.9f, 0.15f, 0.1f, 5f, 1f});
    }

    // ------------------------------------------------------------ tiendas
    private MeshBuilder sh(float x, float z, float yaw, Texture t, int flags, float[] L, float k, float add) {
        MeshBuilder m = cb(x, z).get(t, flags).color(L[0] * k + add, L[1] * k + add, L[2] * k + add * 1.2f);
        m.push().translate(x, 0, z).rotY(yaw);
        return m;
    }

    private void buildShops() {
        for (float[] sp : shopSpots) {
            float x = sp[0], z = sp[1], yaw = sp[2];
            int st = (int) sp[3];
            if (st == 0) shopA(x, z, yaw); else if (st == 1) shopB(x, z, yaw); else shopC(x, z, yaw);
            // suelo de la tienda: alfombra de colores delante del mostrador
            float[] L = bake(x, 0.1f, z);
            MeshBuilder rg = sh(x, z, yaw, Textures.rug, 0, L, 1.3f, 0.1f);
            rg.push().translate(0, 0.045f, 0.95f).scale(1.8f, 1, 1.1f);
            rg.quadP(-1, 0, -1, -1, 0, 1, 1, 0, 1, 1, 0, -1, 0, 0, 1, 1);
            rg.pop().pop();
        }
    }

    private void sign(float x, float z, float yaw, int style, float cy, float lzPos) {
        MeshBuilder m = cb(x, z).get(Textures.sign[style], Mesh.EMISSIVE).color(1.05f, 1.0f, 1.0f);
        m.push().translate(x, 0, z).rotY(yaw);
        m.quadP(1.6f, cy - 1.6f, lzPos, -1.6f, cy - 1.6f, lzPos, -1.6f, cy + 1.6f, lzPos, 1.6f, cy + 1.6f, lzPos, 0, 0, 1, 1);
        m.quadP(-1.6f, cy - 1.6f, lzPos - 0.03f, 1.6f, cy - 1.6f, lzPos - 0.03f, 1.6f, cy + 1.6f, lzPos - 0.03f, -1.6f, cy + 1.6f, lzPos - 0.03f, 0, 0, 1, 1);
        m.pop();
    }

    private void lampLocal(float x, float z, float yaw, float a, float y, float b, float r, float g, float bl, float rad) {
        float wx = lx(x, yaw, a, b), wz = lz(z, yaw, a, b);
        MeshBuilder e = cb(x, z).get(Textures.white, Mesh.EMISSIVE).color(r * 1.4f, g * 1.4f, bl * 1.4f);
        e.push().translate(wx, y, wz).box(0.26f, 0.38f, 0.26f).pop();
        lights.add(new float[]{wx, y, wz, r, g, bl, rad, 1f});
    }

    /** ARMERIA: puesto de mercado de madera con toldo a rayas y armas en los estantes. */
    private void shopA(float x, float z, float yaw) {
        float[] L = bake(x, 1.5f, z);
        MeshBuilder w = sh(x, z, yaw, Textures.plank, 0, L, 2.6f, 0.2f);
        w.push().translate(0, 1.5f, -1.2f).box(4.0f, 3.0f, 0.16f).pop();
        for (int i = 0; i < 3; i++) w.push().translate(0, 1.0f + i * 0.75f, -0.95f).box(3.5f, 0.07f, 0.42f).pop();
        for (int s = -1; s <= 1; s += 2) w.push().translate(s * 1.9f, 1.6f, 0.6f).cylinder(0.09f, 0.09f, 3.2f, 6, true).pop();
        w.push().translate(0, 3.2f, 0.6f).box(4.0f, 0.1f, 0.1f).pop();
        w.pop();
        MeshBuilder c = sh(x, z, yaw, Textures.crate, 0, L, 2.6f, 0.2f);
        c.push().translate(0, 0.5f, -0.1f).box(3.4f, 1.0f, 0.9f).pop();
        c.push().translate(-2.5f, 0.45f, 0.1f).box(0.9f, 0.9f, 0.9f).pop();
        c.push().translate(-2.3f, 1.2f, 0.0f).rotY(0.5f).box(0.7f, 0.6f, 0.7f).pop();
        c.pop();
        MeshBuilder tp = sh(x, z, yaw, Textures.stone, 0, L, 2.2f, 0.2f);
        tp.push().translate(0, 1.05f, -0.1f).box(3.6f, 0.1f, 1.05f).pop();
        tp.push().translate(2.4f, 0.55f, 0.0f).box(0.8f, 0.45f, 0.5f).pop();       // yunque
        tp.push().translate(2.4f, 0.9f, 0.0f).box(1.0f, 0.2f, 0.4f).pop();
        tp.pop();
        // toldo a rayas rojo y crema
        for (int i = 0; i < 8; i++) {
            boolean red = i % 2 == 0;
            MeshBuilder t = sh(x, z, yaw, Textures.white, 0, L, 1f, 0.0f);
            t.color(red ? 0.85f * (L[0] * 2.2f + 0.25f) : 0.9f * (L[0] * 2.2f + 0.3f), red ? 0.12f * (L[1] * 2.2f + 0.25f) : 0.82f * (L[1] * 2.2f + 0.3f), red ? 0.1f * (L[2] * 2.2f + 0.25f) : 0.65f * (L[2] * 2.2f + 0.3f));
            t.push().translate(-1.75f + i * 0.5f, 3.0f, 0.1f).rotX(0.3f).box(0.5f, 0.05f, 2.3f).pop();
            t.pop();
        }
        // armas en los estantes
        MeshBuilder g = sh(x, z, yaw, Textures.metal, 0, L, 2.2f, 0.12f);
        for (int i = 0; i < 4; i++) { g.push().translate(-1.4f + i * 0.8f, 1.12f, -0.95f).rotY(0.2f * i).box(0.7f, 0.07f, 0.1f).pop(); g.push().translate(-1.4f + i * 0.8f, 1.2f, -0.95f).box(0.1f, 0.12f, 0.1f).pop(); }
        for (int i = 0; i < 3; i++) g.push().translate(-1.0f + i * 1.0f, 2.0f, -1.1f).rotZ(0.8f * (i % 2 == 0 ? 1 : -1)).box(0.06f, 0.9f, 0.03f).pop();
        for (int i = 0; i < 5; i++) g.push().translate(-1.6f + i * 0.8f, 1.9f, -0.9f).box(0.35f, 0.18f, 0.15f).pop();
        g.pop();
        sign(x, z, yaw, 0, 4.7f, -1.05f);
        lampLocal(x, z, yaw, -1.6f, 2.55f, 1.0f, 1.0f, 0.7f, 0.3f, 10f);
        lampLocal(x, z, yaw, 1.6f, 2.55f, 1.0f, 1.0f, 0.7f, 0.3f, 10f);
        barrel(lx(x, yaw, 2.5f, 0.9f), lz(z, yaw, 2.5f, 0.9f));
    }

    /** BOTICA: cabana de piedra con tejado puntiagudo morado, calderos y pociones brillantes. */
    private void shopB(float x, float z, float yaw) {
        float[] L = bake(x, 1.5f, z);
        MeshBuilder st = sh(x, z, yaw, Textures.stone, 0, L, 2.4f, 0.2f);
        st.push().translate(0, 1.6f, -1.2f).box(4.0f, 3.2f, 0.32f).pop();
        for (int s = -1; s <= 1; s += 2) st.push().translate(s * 1.95f, 1.7f, -0.7f).box(0.42f, 3.4f, 0.6f).pop();
        st.push().translate(0, 3.45f, -0.7f).box(4.4f, 0.4f, 0.7f).pop();
        st.push().translate(0, 0.5f, -0.1f).box(3.2f, 1.0f, 0.85f).pop();
        st.push().translate(0, 1.04f, -0.1f).box(3.4f, 0.1f, 1.0f).pop();
        st.pop();
        MeshBuilder roof = sh(x, z, yaw, Textures.white, 0, L, 1f, 0f);
        roof.color(0.55f * (L[0] * 2.2f + 0.3f), 0.2f * (L[1] * 2.2f + 0.3f), 0.8f * (L[2] * 2.2f + 0.3f));
        roof.push().translate(0, 4.7f, -0.7f).cone(3.0f, 2.2f, 10).pop();
        roof.color(0.1f, 0.06f, 0.14f).push().translate(0, 1.07f, -0.1f).box(1.6f, 0.02f, 0.9f).pop();     // camino de mesa negro
        roof.pop();
        // estantes con pociones
        MeshBuilder sh2 = sh(x, z, yaw, Textures.plank, 0, L, 2.2f, 0.12f);
        for (int i = 0; i < 3; i++) sh2.push().translate(0, 1.3f + i * 0.7f, -1.0f).box(3.5f, 0.07f, 0.35f).pop();
        sh2.pop();
        float[][] pc = {{0.3f, 1f, 0.3f}, {0.8f, 0.3f, 1f}, {1f, 0.3f, 0.3f}, {0.3f, 0.8f, 1f}, {1f, 0.85f, 0.2f}};
        for (int r = 0; r < 3; r++)
            for (int i = 0; i < 6; i++) {
                float[] c = pc[(i + r * 2) % pc.length];
                MeshBuilder b = cb(x, z).get(Textures.white, Mesh.EMISSIVE).color(c[0] * 1.2f, c[1] * 1.2f, c[2] * 1.2f);
                b.push().translate(x, 0, z).rotY(yaw).translate(-1.45f + i * 0.58f, 1.5f + r * 0.7f, -1.0f).sphere(0.12f, 0.16f, 0.12f, 6, 5).pop();
            }
        // caldero burbujeante
        MeshBuilder ca = sh(x, z, yaw, Textures.metal, 0, L, 1.2f, 0.05f);
        ca.push().translate(-2.4f, 0.5f, 0.6f).cylinder(0.75f, 0.55f, 0.9f, 10, true).pop();
        ca.push().translate(-2.4f, 0.1f, 0.6f).box(0.3f, 0.2f, 0.3f).pop();
        ca.pop();
        MeshBuilder gl = cb(x, z).get(Textures.acid, Mesh.EMISSIVE).color(0.4f, 1f, 0.5f);
        gl.push().translate(x, 0, z).rotY(yaw).translate(-2.4f, 0.97f, 0.6f).cylinder(0.62f, 0.62f, 0.03f, 10, true).pop();
        lights.add(new float[]{lx(x, yaw, -2.4f, 0.6f), 1.4f, lz(z, yaw, -2.4f, 0.6f), 0.3f, 1f, 0.4f, 7f, 1f});
        // calavera y hierbas colgantes
        MeshBuilder bo = sh(x, z, yaw, Textures.bone, 0, L, 2.6f, 0.3f);
        bo.push().translate(1.0f, 1.28f, 0.1f).sphere(0.2f, 0.19f, 0.21f, 8, 6).pop();
        bo.pop();
        MeshBuilder hb = sh(x, z, yaw, Textures.bark, 0, L, 1.5f, 0.1f);
        for (int i = -3; i <= 3; i++) hb.push().translate(i * 0.5f, 3.1f, -0.4f).cylinder(0.04f, 0.02f, 0.55f + 0.15f * (i % 2), 5, false).pop();
        hb.pop();
        sign(x, z, yaw, 1, 4.1f, 0.0f);
        lampLocal(x, z, yaw, -1.4f, 2.7f, 0.9f, 0.6f, 0.25f, 1.0f, 9f);
        lampLocal(x, z, yaw, 1.4f, 2.7f, 0.9f, 0.3f, 1.0f, 0.4f, 9f);
    }

    /** BAZAR: carromato de feria con lona, ruedas, farolillos de colores y tesoros. */
    private void shopC(float x, float z, float yaw) {
        float[] L = bake(x, 1.5f, z);
        MeshBuilder w = sh(x, z, yaw, Textures.plank, 0, L, 2.6f, 0.2f);
        w.color(0.2f * (L[0] * 2.5f + 0.3f), 0.7f * (L[1] * 2.5f + 0.3f), 0.7f * (L[2] * 2.5f + 0.3f));
        w.push().translate(0, 1.0f, -1.15f).box(3.8f, 2.0f, 0.14f).pop();
        w.push().translate(-1.85f, 1.0f, -0.35f).box(0.14f, 2.0f, 1.7f).pop();
        w.push().translate(1.85f, 1.0f, -0.35f).box(0.14f, 2.0f, 1.7f).pop();
        w.color(L[0] * 2.4f + 0.2f, L[1] * 1.8f + 0.15f, L[2] * 1.3f + 0.1f);
        w.push().translate(0, 0.62f, -0.4f).box(3.8f, 0.12f, 1.9f).pop();
        w.push().translate(0, 0.5f, 0.35f).box(3.4f, 0.9f, 0.6f).pop();             // mostrador
        w.push().translate(0, 1.0f, 0.35f).box(3.6f, 0.08f, 0.8f).pop();
        for (int s = -1; s <= 1; s += 2) { w.push().translate(s * 1.95f, 0.65f, -0.3f).rotZ(1.5708f).cylinder(0.7f, 0.7f, 0.16f, 12, true).pop(); }
        w.pop();
        MeshBuilder cv = sh(x, z, yaw, Textures.white, 0, L, 1f, 0f);
        cv.color(0.85f * (L[0] * 1.3f + 0.12f), 0.8f * (L[1] * 1.3f + 0.12f), 0.6f * (L[2] * 1.3f + 0.12f));
        cv.push().translate(0, 2.0f, -0.35f).rotZ(1.5708f).scale(1f, 1f, 0.78f).cylinder(1.0f, 1.0f, 3.8f, 14, true).pop();
        cv.color(0.8f * (L[0] * 2.4f + 0.3f), 0.15f * (L[1] * 2.4f + 0.3f), 0.1f * (L[2] * 2.4f + 0.3f));
        cv.push().translate(0, 1.58f, 0.7f).rotX(0.35f).box(3.9f, 0.05f, 1.1f).pop();     // visera roja
        cv.pop();
        // sacos y tesoros
        MeshBuilder sk = sh(x, z, yaw, Textures.cloth, 0, L, 2.2f, 0.2f);
        sk.push().translate(2.5f, 0.35f, 0.5f).sphere(0.45f, 0.4f, 0.45f, 8, 6).pop();
        sk.push().translate(2.9f, 0.3f, 0.1f).sphere(0.38f, 0.34f, 0.38f, 8, 6).pop();
        sk.pop();
        MeshBuilder gd = sh(x, z, yaw, Textures.gold, 0, L, 3f, 0.3f);
        gd.push().translate(-2.4f, 0.28f, 0.6f).box(0.7f, 0.5f, 0.45f).pop();
        for (int i = 0; i < 4; i++) gd.push().translate(-0.9f + i * 0.6f, 1.14f, 0.35f).sphere(0.12f, 0.12f, 0.12f, 6, 4).pop();
        gd.pop();
        // farolillos de colores en cuerda
        float[][] cols = {{1f, 0.3f, 0.3f}, {0.3f, 1f, 0.4f}, {0.4f, 0.6f, 1f}, {1f, 0.9f, 0.3f}, {1f, 0.4f, 1f}};
        for (int i = 0; i < 9; i++) {
            float[] c = cols[i % cols.length];
            float a = -1.9f + i * 0.475f, sag = (float) Math.sin((i / 8f) * Math.PI) * -0.3f;
            MeshBuilder b = cb(x, z).get(Textures.white, Mesh.EMISSIVE).color(c[0] * 1.3f, c[1] * 1.3f, c[2] * 1.3f);
            b.push().translate(lx(x, yaw, a, 1.4f), 2.9f + sag, lz(z, yaw, a, 1.4f)).sphere(0.1f, 0.13f, 0.1f, 6, 5).pop();
        }
        lights.add(new float[]{lx(x, yaw, 0, 1.3f), 2.7f, lz(z, yaw, 0, 1.3f), 1f, 0.8f, 0.4f, 10f, 1f});
        sign(x, z, yaw, 2, 4.5f, -0.6f);
        // calavera con sombrero sobre el letrero
        MeshBuilder bo = sh(x, z, yaw, Textures.bone, 0, L, 2.6f, 0.3f);
        bo.push().translate(0, 6.0f, -0.6f).sphere(0.28f, 0.26f, 0.28f, 8, 6).pop();
        bo.pop();
        lampLocal(x, z, yaw, -1.9f, 2.4f, 1.2f, 0.3f, 1.0f, 0.8f, 8f);
    }

    // ---------------------------------------------------------------- poblacion
    private void populate() {
        int[][] dist = maze.distances(maze.spawnX, maze.spawnZ);
        List<int[]> open = new ArrayList<>();
        for (int x = 1; x < maze.W - 1; x++)
            for (int z = 1; z < maze.H - 1; z++)
                if (!maze.solid[x][z] && dist[x][z] >= 0 && !(x == maze.doorX && z == maze.doorZ)) open.add(new int[]{x, z, dist[x][z]});
        java.util.Collections.shuffle(open, rnd);
        boolean[][] used = new boolean[maze.W][maze.H];
        used[maze.spawnX][maze.spawnZ] = true;
        for (float[] t : acidSpots) used[tileOf(t[0])][tileOf(t[1])] = true;
        for (float[] t : spikeSpots) used[tileOf(t[0])][tileOf(t[1])] = true;
        for (int x = 1; x < maze.W - 1; x++) for (int z = 1; z < maze.H - 1; z++) if (!tileFree(x, z)) used[x][z] = true;

        // cofres: callejones sin salida y plazas
        List<int[]> prefer = new ArrayList<>(maze.deadEnds);
        java.util.Collections.shuffle(prefer, rnd);
        for (int i = 1; i < maze.plazas.size(); i++) { int[] p = maze.plazas.get(i); prefer.add(0, new int[]{p[0] + 1, p[1] + 1}); }
        int placed = 0;
        for (int[] t : prefer) {
            if (placed >= chestCount) break;
            if (used[t[0]][t[1]] || dist[t[0]][t[1]] < 3 || maze.solid[t[0]][t[1]]) continue;
            used[t[0]][t[1]] = true;
            float[] co = cornerOffset(t[0], t[1]);
            chestSpots.add(new float[]{center(t[0]) + co[0] * 0.7f, center(t[1]) + co[1] * 0.7f, yawToOpen(t[0], t[1])});
            placed++;
        }
        for (int[] t : open) {
            if (placed >= chestCount) break;
            if (used[t[0]][t[1]] || t[2] < 4) continue;
            used[t[0]][t[1]] = true;
            float[] co = cornerOffset(t[0], t[1]);
            chestSpots.add(new float[]{center(t[0]) + co[0] * 0.7f, center(t[1]) + co[1] * 0.7f, h3(t[0], t[1], 95) * 6.28f});
            placed++;
        }
        // fantasmas: lejos del jugador
        int minD = index == 1 ? 14 : index == 2 ? 16 : 18;
        int gp = 0;
        for (int[] t : open) {
            if (gp >= ghostCount) break;
            if (t[2] < minD || used[t[0]][t[1]]) continue;
            used[t[0]][t[1]] = true;
            ghostSpots.add(new float[]{center(t[0]), center(t[1])});
            gp++;
        }
        for (int[] t : open) { // por si el mapa es corto: completa con menos distancia
            if (gp >= ghostCount) break;
            if (t[2] < 9 || used[t[0]][t[1]]) continue;
            used[t[0]][t[1]] = true;
            ghostSpots.add(new float[]{center(t[0]), center(t[1])});
            gp++;
        }
        for (int[] t : open) if (tileFree(t[0], t[1]) && trapAt(center(t[0]), center(t[1])) == 0 && t[2] >= 3) roamTiles.add(t);
        spots(open, used, soulSpots, soulCount, 7, 0.0f);
        spots(open, used, starSpots, starCount, 2, 0.0f);
        spots(open, used, ammoSpots, ammoCount, 3, 0.45f);
        spots(open, used, medSpots, medkitCount, 4, 0.45f);
        // llave: 55% en un cofre, 45% en el suelo lejos del inicio
        keyInChest = rnd.nextFloat() < 0.55f;
        if (!keyInChest) {
            int best = -1; int[] bt = null;
            for (int[] t : open) if (!used[t[0]][t[1]] && t[2] > best && rnd.nextFloat() < 0.35f) { best = t[2]; bt = t; if (best > 45) break; }
            if (bt == null) keyInChest = true; else { used[bt[0]][bt[1]] = true; keySpot = new float[]{center(bt[0]), center(bt[1])}; }
        }
    }

    private void spots(List<int[]> open, boolean[][] used, List<float[]> out, int n, int minDist, float edge) {
        for (int[] t : open) {
            if (out.size() >= n) break;
            if (used[t[0]][t[1]] || t[2] < minDist) continue;
            used[t[0]][t[1]] = true;
            float ox = 0, oz = 0;
            if (edge > 0) { float[] co = cornerOffset(t[0], t[1]); ox = co[0] * edge; oz = co[1] * edge; }
            out.add(new float[]{center(t[0]) + ox, center(t[1]) + oz});
        }
    }
}
