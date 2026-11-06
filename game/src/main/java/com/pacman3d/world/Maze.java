package com.pacman3d.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Laberinto de calles en cuadricula. solid[x][z] = edificio/pared. Cada tile mide Level.TILE. */
public final class Maze {
    public final int W, H;
    public final boolean[][] solid;
    public int doorX, doorZ;      // tile de la puerta (borde)
    public int inX, inZ;          // tile interior junto a la puerta
    public int spawnX, spawnZ;
    public final List<int[]> plazas = new ArrayList<>();   // centros de plazas
    public final List<int[]> deadEnds = new ArrayList<>();

    public Maze(long seed, int cellsW, int cellsH, float braid, int plazaCount) {
        W = cellsW * 2 + 1; H = cellsH * 2 + 1;
        solid = new boolean[W][H];
        for (boolean[] c : solid) java.util.Arrays.fill(c, true);
        Random r = new Random(seed);
        carve(r, cellsW, cellsH);
        // plaza central para aparecer
        int mcx = (cellsW / 2) * 2 + 1, mcz = (cellsH / 2) * 2 + 1;
        clearPlaza(mcx, mcz);
        spawnX = mcx; spawnZ = mcz;
        // plazas aleatorias
        int tries = 0;
        while (plazas.size() < plazaCount + 1 && tries++ < 400) {
            int cx = (1 + r.nextInt(cellsW)) * 2 - 1, cz = (1 + r.nextInt(cellsH)) * 2 - 1;
            if (cx < 4 || cz < 4 || cx > W - 5 || cz > H - 5) continue;
            boolean far = true;
            for (int[] p : plazas) if (Math.abs(p[0] - cx) + Math.abs(p[1] - cz) < 9) far = false;
            if (far) clearPlaza(cx, cz);
        }
        // abrir callejones sin salida para crear bucles (como en el Pac-Man real)
        for (int x = 1; x < W - 1; x++)
            for (int z = 1; z < H - 1; z++) {
                if (solid[x][z] || openNeighbors(x, z) != 1 || r.nextFloat() > braid) continue;
                int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                shuffle(dirs, r);
                for (int[] d : dirs) {
                    int wx = x + d[0], wz = z + d[1], bx = x + 2 * d[0], bz = z + 2 * d[1];
                    if (wx <= 0 || wz <= 0 || wx >= W - 1 || wz >= H - 1 || bx <= 0 || bz <= 0 || bx >= W - 1 || bz >= H - 1) continue;
                    if (solid[wx][wz] && !solid[bx][bz]) { solid[wx][wz] = false; break; }
                }
            }
        for (int x = 1; x < W - 1; x++)
            for (int z = 1; z < H - 1; z++)
                if (!solid[x][z] && openNeighbors(x, z) == 1) deadEnds.add(new int[]{x, z});
        placeDoor(r);
    }

    private void carve(Random r, int cw, int ch) {
        boolean[][] seen = new boolean[cw][ch];
        ArrayDeque<int[]> st = new ArrayDeque<>();
        int sx = r.nextInt(cw), sz = r.nextInt(ch);
        seen[sx][sz] = true;
        solid[sx * 2 + 1][sz * 2 + 1] = false;
        st.push(new int[]{sx, sz});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!st.isEmpty()) {
            int[] c = st.peek();
            shuffle(dirs, r);
            boolean moved = false;
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (nx < 0 || nz < 0 || nx >= cw || nz >= ch || seen[nx][nz]) continue;
                seen[nx][nz] = true;
                solid[c[0] * 2 + 1 + d[0]][c[1] * 2 + 1 + d[1]] = false;
                solid[nx * 2 + 1][nz * 2 + 1] = false;
                st.push(new int[]{nx, nz});
                moved = true;
                break;
            }
            if (!moved) st.pop();
        }
    }

    private static void shuffle(int[][] a, Random r) {
        for (int i = a.length - 1; i > 0; i--) { int j = r.nextInt(i + 1); int[] t = a[i]; a[i] = a[j]; a[j] = t; }
    }

    private void clearPlaza(int cx, int cz) {
        for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++) if (x > 0 && z > 0 && x < W - 1 && z < H - 1) solid[x][z] = false;
        plazas.add(new int[]{cx, cz});
    }

    public int openNeighbors(int x, int z) {
        int n = 0;
        if (!isSolid(x + 1, z)) n++;
        if (!isSolid(x - 1, z)) n++;
        if (!isSolid(x, z + 1)) n++;
        if (!isSolid(x, z - 1)) n++;
        return n;
    }

    public boolean isSolid(int x, int z) {
        return x < 0 || z < 0 || x >= W || z >= H || solid[x][z];
    }

    /** Distancias BFS (en tiles) desde un punto. -1 = inalcanzable. */
    public int[][] distances(int sx, int sz) {
        int[][] d = new int[W][H];
        for (int[] row : d) java.util.Arrays.fill(row, -1);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        d[sx][sz] = 0;
        q.add(new int[]{sx, sz});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] k : dirs) {
                int nx = c[0] + k[0], nz = c[1] + k[1];
                if (isSolid(nx, nz) || d[nx][nz] >= 0) continue;
                d[nx][nz] = d[c[0]][c[1]] + 1;
                q.add(new int[]{nx, nz});
            }
        }
        return d;
    }

    private void placeDoor(Random r) {
        int[][] d = distances(spawnX, spawnZ);
        int best = -1, bx = 0, bz = 0, ix = 0, iz = 0;
        for (int x = 1; x < W - 1; x++) {
            check(d, x, 0, x, 1); check(d, x, H - 1, x, H - 2);
        }
        for (int z = 1; z < H - 1; z++) {
            check(d, 0, z, 1, z); check(d, W - 1, z, W - 2, z);
        }
        for (int x = 1; x < W - 1; x++) { if (!solid[x][1] && d[x][1] > best) { best = d[x][1]; bx = x; bz = 0; ix = x; iz = 1; } if (!solid[x][H - 2] && d[x][H - 2] > best) { best = d[x][H - 2]; bx = x; bz = H - 1; ix = x; iz = H - 2; } }
        for (int z = 1; z < H - 1; z++) { if (!solid[1][z] && d[1][z] > best) { best = d[1][z]; bx = 0; bz = z; ix = 1; iz = z; } if (!solid[W - 2][z] && d[W - 2][z] > best) { best = d[W - 2][z]; bx = W - 1; bz = z; ix = W - 2; iz = z; } }
        doorX = bx; doorZ = bz; inX = ix; inZ = iz;
        solid[doorX][doorZ] = false;   // el tile de la puerta es "abierto" pero Level lo bloquea mientras este cerrada
    }

    private void check(int[][] d, int a, int b, int c, int e) { }
}
