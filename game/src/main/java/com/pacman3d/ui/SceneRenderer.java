package com.pacman3d.ui;

import com.pacman3d.game.*;
import com.pacman3d.gfx.*;
import com.pacman3d.world.Level;

/** Dibuja el mundo 3D completo: ciudad, jugadores, fantasmas, cofres, objetos, proyectiles y efectos. */
public final class SceneRenderer {
    private static final float[][] GCOL = {{1f, 0.25f, 0.2f}, {1f, 0.5f, 0.85f}, {0.3f, 0.9f, 1f}, {1f, 0.62f, 0.2f}};
    private float flick = 1f, flickT, camDist = 3.6f;
    private final java.util.Random rnd = new java.util.Random(3);
    public boolean fancy = true;        // niebla baja, lluvia, halos (se desactiva si el equipo va justo)
    public Player viewer;
    public boolean tpv;

    private static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xffff) / 65535f;
    }

    private void camera(Renderer r, Game g) {
        Player me = g.me;
        viewer = me;
        tpv = me.tpv;
        if (me.state == Player.OUT && g.other() != null && g.other().state != Player.OUT) { viewer = g.other(); tpv = true; }
        Camera c = r.cam;
        float sh = viewer.shake;
        float yaw = viewer.yaw + (rnd.nextFloat() - 0.5f) * sh * 0.02f, pitch = viewer.pitch + (rnd.nextFloat() - 0.5f) * sh * 0.02f;
        float eyeY = 1.7f + viewer.bob - viewer.eyeDrop + (rnd.nextFloat() - 0.5f) * sh * 0.08f;
        c.yaw = yaw; c.pitch = pitch;
        if (!tpv) { c.x = viewer.px; c.y = eyeY; c.z = viewer.pz; return; }
        // tercera persona: la camara se coloca detras y un poco a la derecha, sin atravesar paredes
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch), sy = (float) Math.sin(yaw), cy = (float) Math.cos(yaw);
        float fx = sy * cp, fy = sp, fz = cy * cp, rx = cy, rz = -sy;
        float ex = viewer.px, ey = 1.55f + viewer.bob * 0.5f, ez = viewer.pz;
        float want = 3.9f, dist = want;
        for (float t = 0.4f; t <= want; t += 0.2f) {
            float x = ex - fx * t + rx * 0.7f * Math.min(1f, t / 1.5f), z = ez - fz * t + rz * 0.7f * Math.min(1f, t / 1.5f);
            float y = ey - fy * t + 0.5f;
            if (g.level.solidAt(x, z) || y < 0.25f) { dist = Math.max(0.5f, t - 0.3f); break; }
        }
        camDist += (dist - camDist) * 0.35f;
        if (dist < camDist) camDist = dist;
        float t = camDist;
        c.x = ex - fx * t + rx * 0.7f * Math.min(1f, t / 1.5f); c.z = ez - fz * t + rz * 0.7f * Math.min(1f, t / 1.5f);
        c.y = Math.max(0.35f, ey - fy * t + 0.5f);
    }

    public void render(Renderer r, Game g, float dt) {
        Level lv = g.level;
        camera(r, g);
        Camera c = r.cam;
        { Weapon cw = viewer.current(); float zf = cw == null ? 1f : 1f + (cw.zoom - 1f) * viewer.zoomT;
          r.setFov((float) (2 * Math.atan(Math.tan(Math.toRadians(34)) / zf))); }
        r.begin();
        flickT -= dt;
        if (flickT <= 0) { flickT = 0.05f + rnd.nextFloat() * 0.12f; flick = 0.82f + rnd.nextFloat() * 0.2f; if (rnd.nextInt(40) == 0) flick = 0.3f; }
        r.lantern = flick * (g.me.state == Player.DYING ? 0.5f : 1f) * (tpv ? 0.85f : 1f);
        float fl = Math.max(g.flash * 0.6f, g.lightning * 0.8f);
        r.flash = fl;
        r.flashR = 1f; r.flashG = g.lightning > 0.1f ? 0.95f : 0.8f; r.flashB = g.lightning > 0.1f ? 1f : 0.55f;
        r.fogDensity = 0.046f + 0.008f * g.levelIdx;
        r.skyDim = 1f;
        if (g.me.immuneT > 0) { r.lampR = 0.5f; r.lampG = 0.75f; r.lampB = 1.1f; } else { r.lampR = 1f; r.lampG = 0.78f; r.lampB = 0.5f; }
        r.sky = Textures.sky;
        float maxD = 58f;
        for (Level.Chunk[] row : lv.chunks)
            for (Level.Chunk ch : row) {
                float dx = ch.cx - c.x, dz = ch.cz - c.z;
                if (dx * dx + dz * dz > (maxD + ch.rad) * (maxD + ch.rad) || !r.visible(ch.cx, 5, ch.cz, ch.rad, maxD + 20)) continue;
                boolean closeChunk = dx * dx + dz * dz < (34f + ch.rad) * (34f + ch.rad);
                for (Mesh m : ch.meshes) {
                    Texture tx = m.tex;
                    boolean base = tx == Textures.cobble || tx == Textures.asphalt || tx == Textures.brick[0] || tx == Textures.brick[1] || tx == Textures.brick[2]
                            || tx == Textures.facade[0] || tx == Textures.facade[1] || tx == Textures.facade[2] || tx == Textures.facade[3];
                    if (base || closeChunk) r.mesh(m, null, 1, 1, 1);
                }
            }
        float t = g.time;
        entities(r, g, t);
        atmosphere(r, g, t);
        if (!tpv && viewer == g.me && viewer.state != Player.DYING) WeaponView.draw(r, viewer, t, g.time, Palette.RGB[viewer.colorIndex], true, null);
        r.end();
    }

    private static float[] hsv(float h) {
        h = h - (float) Math.floor(h);
        float r = Math.max(0, Math.min(1, Math.abs(h * 6 - 3) - 1)), g = Math.max(0, Math.min(1, 2 - Math.abs(h * 6 - 2))), b = Math.max(0, Math.min(1, 2 - Math.abs(h * 6 - 4)));
        return new float[]{0.35f + 0.9f * r, 0.35f + 0.9f * g, 0.35f + 0.9f * b};
    }

    private boolean near(Renderer r, float x, float y, float z, float rad, float maxD) { return r.visible(x, y, z, rad, maxD); }

    private void shadow(Renderer r, float x, float z, float size, float a) {
        r.floorQuad(Textures.shadow, x, 0.045f, z, size, 0, 1, 1, 1, a, Mesh.SHADOW);
    }

    // ------------------------------------------------------------------ jugadores (propio en 3a persona y compañero)
    private void drawPlayer(Renderer r, Game g, Player p, float t) {
        if (p.state == Player.OUT) return;
        float sc = 0.85f;
        float[] col = Palette.RGB[p.colorIndex];
        float speed = (float) Math.hypot(p.vx, p.vz);
        float move = Math.min(1f, speed / 5f);
        float bounce = Math.abs((float) Math.sin(p.walkAnim * 1.1f)) * 0.14f * move;
        float y = 0.3f + 0.95f * sc + bounce;
        float yaw = p.yaw;
        float scale = sc;
        if (p.state == Player.DYING) { float d = Math.min(1f, p.deathT / 2.2f); scale = sc * (1f - d * 0.9f); yaw += d * 14f; y = 0.3f + 0.95f * scale; }
        if (p.invulnT > 0 && ((int) (t * 12)) % 2 == 0 && p.state == Player.ALIVE && p.invulnT < 3.5f) { /* parpadeo tras reaparecer */ }
        else {
            float mouth = 0.12f + (0.12f + 0.3f * move) * Math.abs((float) Math.sin(t * 7f)) + 0.45f * p.recoil;
            float tilt = Math.min(0.28f, speed * 0.03f);
            float[] ol = {0.30f, 0.28f, 0.34f};
            Models.drawPac(r, p.px, y, p.pz, yaw, tilt, scale, mouth, col[0], col[1], col[2], ol[0], ol[1], ol[2]);
            // zapatillas que caminan
            float fx = (float) Math.sin(p.yaw), fz = (float) Math.cos(p.yaw), rx = fz, rz = -fx;
            for (int s = -1; s <= 1; s += 2) {
                float ph = p.walkAnim * 1.6f + (s > 0 ? 0 : 3.14159f);
                float step = (float) Math.sin(ph) * 0.5f * move, lift = Math.max(0f, (float) Math.cos(ph)) * 0.18f * move;
                Models.drawShoe(r, p.px + rx * s * 0.4f * scale + fx * step * scale, 0.19f + lift, p.pz + rz * s * 0.4f * scale + fz * step * scale, p.yaw, scale, 0.25f, 0.24f, 0.28f);
            }
            if (p.state == Player.ALIVE && p.current() != null) {
                float[] base = Mat.trs(p.px, y - 0.12f, p.pz, p.yaw, -p.pitch * 0.9f, 0, 1, 1, 1);
                float[] wb = Mat.mul(base, Mat.trs(0.95f * sc, 0.0f, 0.5f * sc, 0, 0, 0, 1.7f, 1.7f, 1.7f));
                WeaponView.draw(r, p, t, g.time, col, false, wb);
            }
        }
        shadow(r, p.px, p.pz, 2.2f * sc, 0.6f);
        if (p.immuneT > 0) r.billboard(Textures.glow, p.px, y, p.pz, 3.4f, 3.4f, 0.3f, 0.6f, 1f, 0.35f + 0.1f * (float) Math.sin(t * 9), Mesh.ADDITIVE);
        if (p != g.me && p.state != Player.OUT) r.billboard(Textures.tag[Math.min(1, p.id)], p.px, y + 1.9f, p.pz, 1.5f, 1.5f, 1, 1, 1, 1f, 0);
    }

    private void entities(Renderer r, Game g, float t) {
        Level lv = g.level;
        Camera c = r.cam;
        // ---- jugadores
        for (Player p : g.players) {
            if (p == viewer && !tpv) continue;
            drawPlayer(r, g, p, t);
        }
        // ---- puerta de salida
        {
            float gx = lv.gateX, gz = lv.gateZ;
            float yaw = (float) Math.atan2(lv.gateDX, lv.gateDZ);
            if (near(r, gx, 3, gz, 6, 55)) {
                float[] base = Mat.at(gx, 0, gz, yaw);
                r.mesh(Models.doorFrame, base, 1, 1, 1, 0.1f, 0.08f, 0.06f);
                float ang = g.doorAnim * 1.75f;
                r.mesh(Models.doorWing, Mat.mul(base, Mat.trs(-1.95f, 0, 0, ang, 0, 0, 1, 1, 1)), 1, 1, 1, 0.08f, 0.06f, 0.05f);
                r.mesh(Models.doorWing, Mat.mul(base, Mat.trs(1.95f, 0, 0, -ang, 0, 0, -1, 1, 1)), 1, 1, 1, 0.08f, 0.06f, 0.05f);
                boolean o = lv.doorOpen;
                float pulse = 0.7f + 0.3f * (float) Math.sin(t * 4);
                r.billboard(Textures.glow, gx - lv.gateDX * 0.6f, 6.4f, gz - lv.gateDZ * 0.6f, 3.2f, 3.2f, o ? 0.3f : 1f, o ? 1f : 0.15f, o ? 0.4f : 0.1f, 0.9f * pulse, Mesh.ADDITIVE);
                if (o) r.billboard(Textures.glow, gx + lv.gateDX * 1.5f, 3f, gz + lv.gateDZ * 1.5f, 9f, 9f, 0.3f, 1f, 0.6f, 0.6f + 0.2f * pulse, Mesh.ADDITIVE);
                // runas giratorias sobre el arco
                r.floorQuad(Textures.ring, gx, 0.06f, gz, 5.5f + (float) Math.sin(t * 2), t * 0.7f, o ? 0.2f : 1f, o ? 1f : 0.2f, 0.2f, 0.5f, Mesh.ADDITIVE | Mesh.EMISSIVE);
            }
        }
        // ---- sangre permanente
        int bi = 0;
        for (float[] b : g.blood) {
            bi++;
            if (!near(r, b[0], 0, b[1], b[2], 40)) continue;
            r.floorQuad(bi % 2 == 0 ? Textures.bloodA : Textures.bloodB, b[0], 0.04f + (bi % 7) * 0.001f, b[1], b[2], b[3], 0.75f, 0.7f, 0.7f, 1f, 0);
        }
        // ---- trampas animadas
        for (float[] a : lv.acidSpots) {
            if (!near(r, a[0], 0, a[1], 2f, 42)) continue;
            float pulse = 0.55f + 0.2f * (float) Math.sin(t * 3 + a[0]);
            r.floorQuadUV(Textures.acid, a[0], 0.085f, a[1], 2.0f, 0, 0.5f, 1f, 0.5f, pulse, Mesh.ADDITIVE | Mesh.EMISSIVE, t * 0.06f, t * 0.04f, 1f + t * 0.06f, 1f + t * 0.04f);
            r.floorQuad(Textures.ring, a[0], 0.09f, a[1], 3.0f + 0.4f * (float) Math.sin(t * 2), t * 0.4f, 0.3f, 1f, 0.3f, 0.35f, Mesh.ADDITIVE | Mesh.EMISSIVE);
            if (rnd.nextInt(9) == 0) g.burst(a[0] + (rnd.nextFloat() - 0.5f) * 1.6f, 0.15f, a[1] + (rnd.nextFloat() - 0.5f) * 1.6f, 1, 0.8f, 0.4f, 1f, 0.4f, 0.2f, 0.9f, -1f);
        }
        for (float[] a : lv.spikeSpots) {
            if (!near(r, a[0], 0, a[1], 2f, 42)) continue;
            r.floorQuad(Textures.ring, a[0], 0.09f, a[1], 3.0f, 0, 1f, 0.2f, 0.15f, 0.25f + 0.2f * (float) Math.sin(t * 5 + a[1]), Mesh.ADDITIVE | Mesh.EMISSIVE);
        }
        // ---- tiendas: tendero, cofre arcoiris y detalles vivos
        for (ShopChest sc : g.shops) {
            if (!near(r, sc.x, 1, sc.z, 5, 52)) continue;
            Models.vendors[sc.style].draw(r, sc.tx, 0.0f, sc.tz, sc.yaw, t * 1.6f, 0.0f, false, 1f, 1f, 0.95f, 0.85f, 0.45f, 0.35f, 0.25f);
            float hue = t * 0.35f + sc.x * 0.05f;
            float[] col = hsv(hue);
            float a = sc.anim;
            float open = a > 0 ? Math.max(0f, Math.min(1f, Math.min((2.4f - a) * 4f, a * 2.2f))) : 0f;
            float[] base = Mat.at(sc.x, 0.0f, sc.z, sc.yaw);
            r.mesh(Models.chestBodyW, base, col[0], col[1], col[2], 0.35f, 0.35f, 0.4f);
            r.mesh(Models.chestLidW, Mat.mul(base, Mat.trs(0, 0.8f, -0.52f, 0, -open * 1.9f, 0, 1, 1, 1)), col[0], col[1], col[2], 0.35f, 0.35f, 0.4f);
            float[] c2 = hsv(hue + 0.5f);
            r.billboard(Textures.glow, sc.x, 1.1f, sc.z, 3.6f, 3.6f, c2[0], c2[1], c2[2], 0.5f + 0.15f * (float) Math.sin(t * 4), Mesh.ADDITIVE);
            r.floorQuad(Textures.ring, sc.x, 0.07f, sc.z, 3.4f + (float) Math.sin(t * 2.5f) * 0.3f, t * 0.8f, col[0], col[1], col[2], 0.6f, Mesh.ADDITIVE | Mesh.EMISSIVE);
            r.floorQuad(Textures.ring, sc.x, 0.075f, sc.z, 2.2f, -t * 1.2f, c2[0], c2[1], c2[2], 0.5f, Mesh.ADDITIVE | Mesh.EMISSIVE);
            r.billboard(Textures.glow, sc.x, 2.3f + open, sc.z, 1.2f + open, 1.2f + open, 1f, 1f, 1f, 0.5f + 0.4f * open, Mesh.ADDITIVE);
            shadow(r, sc.x, sc.z, 2.8f, 0.6f);
            if (rnd.nextInt(6) == 0) { float[] c3 = hsv(rnd.nextFloat()); g.burst(sc.x + (rnd.nextFloat() - 0.5f) * 1.2f, 1.0f, sc.z + (rnd.nextFloat() - 0.5f) * 1.2f, 1, 1.2f, c3[0], c3[1], c3[2], 0.2f, 1.0f, -0.6f); }
            if (sc.style == 1 && rnd.nextInt(5) == 0) {
                float cx = sc.x - (float) Math.sin(sc.yaw) * 1.0f, cz = sc.z - (float) Math.cos(sc.yaw) * 1.0f;
                float wx = cx - 2.4f * (float) Math.cos(sc.yaw) + 0.6f * (float) Math.sin(sc.yaw), wz = cz + 2.4f * (float) Math.sin(sc.yaw) + 0.6f * (float) Math.cos(sc.yaw);
                g.burst(wx, 1.0f, wz, 1, 0.9f, 0.4f, 1f, 0.5f, 0.2f, 1.0f, -0.8f);
            }
        }
        // ---- almas perdidas (NPC)
        for (Soul so : g.souls) {
            if (!near(r, so.x, 1, so.z, 2f, 50)) continue;
            Models.Human h = (so.female ? Models.women : Models.men)[so.variant];
            float run = t * 11f + so.phase;
            if (so.state == Soul.FLEE) {
                h.draw(r, so.x, 0.06f + 0.1f * (float) Math.sin(t * 3 + so.phase), so.z, so.yaw, run, so.runAmt, false, 1f, 0.7f, 0.95f, 1.3f, 0.45f, 0.55f, 0.75f);
                r.billboard(Textures.glow, so.x, 1.0f, so.z, 3.2f, 3.2f, 0.4f, 0.7f, 1f, 0.28f + 0.08f * (float) Math.sin(t * 4 + so.phase), Mesh.ADDITIVE);
                shadow(r, so.x, so.z, 1.5f, 0.3f);
                if (rnd.nextInt(10) == 0) g.burst(so.x, 1.1f, so.z, 1, 0.6f, 0.5f, 0.8f, 1f, 0.25f, 1.0f, -0.4f);
            } else if (so.state == Soul.SAVED) {
                float u = Math.min(1f, so.stateT / 2.4f), sc2 = 1f - 0.5f * u;
                h.draw(r, so.x, 0.1f + so.stateT * 2.6f, so.z, so.yaw + so.stateT * 3f, run, 0f, false, sc2, 1.1f, 1.3f, 1.6f, 0.6f, 0.7f, 0.9f);
                r.billboard(Textures.glow, so.x, 1.5f + so.stateT * 2.6f, so.z, 5f * (1f - 0.4f * u), 5f * (1f - 0.4f * u), 0.8f, 1f, 1f, 0.7f * (1f - u * 0.6f), Mesh.ADDITIVE);
                r.vbillboard(Textures.glow, so.x, 7f, so.z, 1.6f, 14f, 0.6f, 0.9f, 1f, 0.55f * (1f - u), Mesh.ADDITIVE);
                if (rnd.nextInt(2) == 0) g.burst(so.x, 1.0f + so.stateT * 2.6f, so.z, 1, 1.5f, 0.7f, 0.9f, 1f, 0.25f, 1.2f, -1.5f);
            } else {
                h.draw(r, so.x, 0.2f, so.z, so.yaw, 0f, 0f, true, 1f, 1.0f, 0.55f, 0.55f, 0.45f, 0.3f, 0.3f);
            }
        }
        // ---- haz de luz hacia la llave (solo 2 segundos, visible desde cualquier lugar)
        if (viewer.keyHintT > 0) {
            float k = Math.min(1f, viewer.keyHintT * 2f) * (0.75f + 0.25f * (float) Math.sin(t * 14));
            float kx = viewer.keyHintX, kz = viewer.keyHintZ;
            r.vbillboard(Textures.glow, kx, 30f, kz, 4.0f, 70f, 1f, 0.85f, 0.25f, 0.85f * k, Mesh.ADDITIVE | Mesh.NOFOG);
            r.vbillboard(Textures.white, kx, 30f, kz, 0.5f, 70f, 1f, 1f, 0.8f, 0.9f * k, Mesh.ADDITIVE | Mesh.NOFOG);
            r.billboard(Textures.glow, kx, 1.5f, kz, 9f, 9f, 1f, 0.9f, 0.3f, 0.9f * k, Mesh.ADDITIVE | Mesh.NOFOG);
            r.floorQuad(Textures.ring, kx, 0.1f, kz, 6f + 2f * (2f - viewer.keyHintT), 0, 1f, 0.85f, 0.3f, 0.9f * k, Mesh.ADDITIVE | Mesh.EMISSIVE | Mesh.NOFOG);
        }
        // ---- cofres
        for (Chest ch : g.chests) {
            if (!near(r, ch.x, 1, ch.z, 2, 45)) continue;
            float[] base = Mat.at(ch.x, 0, ch.z, ch.yaw);
            r.mesh(Models.chestBody, base, 1, 1, 1, 0.12f, 0.1f, 0.08f);
            float[] lid = Mat.mul(base, Mat.trs(0, 0.8f, -0.5f, 0, -ch.openT * 1.9f, 0, 1, 1, 1));
            r.mesh(Models.chestLid, lid, 1, 1, 1, 0.12f, 0.1f, 0.08f);
            shadow(r, ch.x, ch.z, 2.6f, 0.6f);
            if (!ch.open) {
                float p = 0.55f + 0.35f * (float) Math.sin(t * 3 + ch.x);
                r.billboard(Textures.glow, ch.x, 1.1f, ch.z, 2.4f, 2.4f, 1f, 0.75f, 0.25f, 0.55f * p, Mesh.ADDITIVE);
                if (rnd.nextInt(14) == 0) g.burst(ch.x + (rnd.nextFloat() - 0.5f), 1.0f, ch.z + (rnd.nextFloat() - 0.5f), 1, 0.8f, 1f, 0.8f, 0.2f, 0.14f, 0.9f, -0.5f);
            } else if (ch.openT < 1) {
                r.billboard(Textures.glow, ch.x, 1.2f + ch.openT, ch.z, 3.5f * (1 - ch.openT), 3.5f * (1 - ch.openT), 1f, 0.9f, 0.4f, 0.8f, Mesh.ADDITIVE);
            }
        }
        // ---- objetos del suelo
        for (Pickup p : g.pickups) {
            if (p.taken || !near(r, p.x, 1, p.z, 1.5f, 42)) continue;
            float bob = (float) Math.sin(t * 2.4f + p.phase);
            switch (p.kind) {
                case Pickup.STAR: {
                    float[] m = Mat.trs(p.x, 1.25f + bob * 0.18f, p.z, t * 2.2f + p.phase, 0, (float) Math.sin(t * 3 + p.phase) * 0.15f, 1.5f, 1.5f, 1.5f);
                    r.mesh(Models.star, m, 1, 1, 1);
                    r.billboard(Textures.glow, p.x, 1.25f + bob * 0.18f, p.z, 2.2f, 2.2f, 1f, 0.85f, 0.2f, 0.5f, Mesh.ADDITIVE);
                    shadow(r, p.x, p.z, 1.2f, 0.35f);
                    if (rnd.nextInt(30) == 0) g.burst(p.x, 1.4f, p.z, 1, 1f, 1f, 0.9f, 0.3f, 0.12f, 0.8f, -0.4f);
                    break;
                }
                case Pickup.AMMO:
                    r.mesh(Models.ammoBox, Mat.at(p.x, 0.15f + bob * 0.05f, p.z, p.phase + t * 0.6f), 1, 1, 1, 0.1f, 0.1f, 0.08f);
                    r.billboard(Textures.glow, p.x, 0.5f, p.z, 1.8f, 1.8f, 1f, 0.8f, 0.3f, 0.35f, Mesh.ADDITIVE);
                    shadow(r, p.x, p.z, 1.3f, 0.5f);
                    break;
                case Pickup.MED:
                    r.mesh(Models.medkit, Mat.at(p.x, 0.15f + bob * 0.05f, p.z, p.phase + t * 0.6f), 1, 1, 1, 0.1f, 0.1f, 0.1f);
                    r.billboard(Textures.glow, p.x, 0.5f, p.z, 1.8f, 1.8f, 0.3f, 1f, 0.4f, 0.4f, Mesh.ADDITIVE);
                    shadow(r, p.x, p.z, 1.3f, 0.5f);
                    break;
                case Pickup.KEY:
                    r.mesh(Models.key, Mat.trs(p.x, 1.4f + bob * 0.15f, p.z, t * 2f, 0, 0, 1.8f, 1.8f, 1.8f), 1, 1, 1, 0.3f, 0.25f, 0.1f);
                    r.billboard(Textures.glow, p.x, 1.4f, p.z, 3.2f, 3.2f, 1f, 0.85f, 0.3f, 0.6f, Mesh.ADDITIVE);
                    r.floorQuad(Textures.ring, p.x, 0.06f, p.z, 2.2f, t, 1f, 0.85f, 0.3f, 0.5f, Mesh.ADDITIVE | Mesh.EMISSIVE);
                    break;
            }
        }
        // ---- fantasmas
        for (Ghost gh : g.ghosts) {
            float gx = g.gx(gh), gz = g.gz(gh);
            if (!near(r, gx, 1.5f, gz, 2.6f, 50)) continue;
            float yaw = gh.yawVis, rise = 0;
            if (gh.dying) { float d = gh.dyingT / 0.9f; yaw += d * 10; rise = d * 1.8f; }
            Mesh body = Models.ghostBody[gh.type];
            float sc = gh.dying ? Math.max(0.01f, 1f - gh.dyingT / 0.9f) : 1f;
            float wail = 1f + 0.06f * (float) Math.sin(t * 5 + gh.phase);
            Models.animateGhost(body, t, gh.phase, gh.trail, 1f);
            float tint = gh.hurtT > 0 ? 1.9f : 1.25f;
            float[] base = Mat.trs(gx, gh.bob + rise, gz, yaw, 0, 0, sc, sc * wail, sc);
            r.mesh(body, base, tint, gh.hurtT > 0 ? 1.2f : 1.25f, gh.hurtT > 0 ? 1.2f : 1.3f, 0.30f, 0.30f, 0.38f);
            float wave = (float) Math.sin(t * 3 + gh.phase) * 0.25f;
            r.mesh(Models.ghostArm, Mat.mul(base, Mat.trs(0.78f, 1.5f, 0.1f, 0, -1.25f + wave, -0.35f, 1, 1, 1)), 1.2f, 1.2f, 1.25f, 0.3f, 0.3f, 0.38f);
            r.mesh(Models.ghostArm, Mat.mul(base, Mat.trs(-0.78f, 1.5f, 0.1f, 0, -1.25f - wave, 0.35f, 1, 1, 1)), 1.2f, 1.2f, 1.25f, 0.3f, 0.3f, 0.38f);
            float[] gc = GCOL[gh.type];
            float ga = (gh.dying ? 0.6f * sc : 0.28f + 0.1f * (float) Math.sin(t * 5 + gh.phase));
            float aura = gh.fast && !gh.dying ? 1.3f : 1f;
            r.billboard(Textures.glow, gx, gh.bob + rise + 1.4f, gz, 4.0f * sc * aura, 4.4f * sc * aura, gc[0], gc[1], gc[2], ga, Mesh.ADDITIVE);
            if (gh.fast && !gh.dying) {   // los veloces brillan en rojo y dejan una estela de energia
                r.billboard(Textures.glow, gx, gh.bob + 1.5f, gz, 5.4f, 5.6f, 1f, 0.15f, 0.05f, 0.35f + 0.15f * (float) Math.sin(t * 12 + gh.phase), Mesh.ADDITIVE);
                for (int k = 1; k <= 3; k++) r.billboard(Textures.glow, gx - gh.dx * 0.9f * k, gh.bob + 1.3f, gz - gh.dz * 0.9f * k, 2.6f - 0.5f * k, 2.6f - 0.5f * k, 1f, 0.3f, 0.1f, 0.30f / k, Mesh.ADDITIVE);
            }
            float ex = (float) Math.sin(yaw), ez = (float) Math.cos(yaw);
            float eyeA = (gh.fast ? 1f : 0.9f) * sc * (0.75f + 0.25f * (float) Math.sin(t * 9 + gh.phase));
            r.billboard(Textures.glow, gx + ex * 0.78f - ez * 0.3f, gh.bob + rise + 2.3f, gz + ez * 0.78f + ex * 0.3f, 0.35f, 0.35f, 1f, 0.9f, 0.6f, eyeA, Mesh.ADDITIVE);
            r.billboard(Textures.glow, gx + ex * 0.78f + ez * 0.3f, gh.bob + rise + 2.3f, gz + ez * 0.78f - ex * 0.3f, 0.35f, 0.35f, 1f, 0.9f, 0.6f, eyeA, Mesh.ADDITIVE);
            if (!gh.dying) shadow(r, gx, gz, 2.4f, 0.45f + 0.15f * (float) Math.sin(t * 2.1f + gh.phase));
        }
        // ---- proyectiles con estela
        for (Projectile p : g.projectiles) {
            float sp = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz) + 1e-4f;
            float yaw = (float) Math.atan2(p.vx, p.vz), pitch = -(float) Math.asin(p.vy / sp);
            if (p.kind == Projectile.BULLET) {
                r.mesh(Models.bullet, Mat.trs(p.x, p.y, p.z, yaw, pitch, 0, 1, 1, 1.8f), 1, 1, 1);
                r.billboard(Textures.glow, p.x, p.y, p.z, 0.55f, 0.55f, 1f, 0.8f, 0.3f, 0.7f, Mesh.ADDITIVE);
                for (int k = 1; k <= 4; k++) r.billboard(Textures.glow, p.x - p.vx * 0.012f * k, p.y - p.vy * 0.012f * k, p.z - p.vz * 0.012f * k, 0.4f - 0.07f * k, 0.4f - 0.07f * k, 1f, 0.6f, 0.2f, 0.55f - 0.12f * k, Mesh.ADDITIVE);
            } else if (p.kind == Projectile.ARROW) {
                r.mesh(Models.arrow, Mat.trs(p.x, p.y, p.z, yaw, pitch, 0, 1, 1, 1), 1, 1, 1, 0.1f, 0.1f, 0.1f);
                for (int k = 1; k <= 3; k++) r.billboard(Textures.glow, p.x - p.vx * 0.02f * k, p.y - p.vy * 0.02f * k, p.z - p.vz * 0.02f * k, 0.3f, 0.3f, 0.7f, 0.8f, 1f, 0.35f / k, Mesh.ADDITIVE);
            } else {
                r.mesh(Models.grenade, Mat.trs(p.x, p.y, p.z, p.spin, p.spin * 0.7f, 0, 2f, 2f, 2f), 1, 1, 1, 0.1f, 0.1f, 0.1f);
                float blink = p.fuse < 1f ? (float) Math.abs(Math.sin(t * 25)) : 0.2f;
                r.billboard(Textures.glow, p.x, p.y + 0.3f, p.z, 0.9f, 0.9f, 1f, 0.2f, 0.1f, blink, Mesh.ADDITIVE);
                shadow(r, p.x, p.z, 0.9f, 0.5f);
            }
        }
        // ---- particulas y ondas
        for (Particle p : g.particles) {
            float a = Math.max(0, Math.min(1, p.life / p.max));
            if (p.ring) {
                float prog = 1f - a;
                r.floorQuad(Textures.ring, p.x, p.y, p.z, p.size * 2f * (0.15f + prog), 0, p.r, p.g, p.b, a * 0.9f, Mesh.ADDITIVE | Mesh.EMISSIVE);
            } else r.billboard(Textures.glow, p.x, p.y, p.z, p.size * (0.5f + a), p.size * (0.5f + a), p.r, p.g, p.b, a, Mesh.ADDITIVE);
        }
        // ---- ectoplasma en el suelo
        for (float[] d : g.decals) {
            if (!near(r, d[0], 0, d[1], 2, 40)) continue;
            float a = 1f - d[2] / 25f;
            r.floorQuad(Textures.glow, d[0], 0.05f, d[1], 2.4f, d[0], 0.3f, 1f, 0.6f, 0.45f * a, Mesh.ADDITIVE | Mesh.EMISSIVE);
        }
        // ---- murcielagos
        for (int i = 0; i < 8; i++) {
            float a = t * (0.25f + 0.05f * i) + i * 1.9f;
            float rad = 7 + 3.2f * i + 3 * (float) Math.sin(t * 0.5f + i);
            float bx = c.x + (float) Math.cos(a) * rad, bz = c.z + (float) Math.sin(a) * rad, by = 9.5f + 2.5f * (float) Math.sin(t * 1.3f + i * 2);
            float yaw = (float) Math.atan2(-Math.sin(a), Math.cos(a));
            float flap = (float) Math.sin(t * 14 + i) * 0.8f;
            float[] base = Mat.trs(bx, by, bz, yaw, 0, 0, 1.4f, 1.4f, 1.4f);
            r.mesh(Models.bat, base, 1, 1, 1, 0.1f, 0.1f, 0.14f);
            r.mesh(Models.batWing, Mat.mul(base, Mat.trs(0.1f, 0, 0, 0, 0, flap, 1, 1, 1)), 1, 1, 1, 0.1f, 0.1f, 0.14f);
            r.mesh(Models.batWing, Mat.mul(base, Mat.trs(-0.1f, 0, 0, 0, 0, -flap, -1, 1, 1)), 1, 1, 1, 0.1f, 0.1f, 0.14f);
        }
        if (viewer.immuneT > 0 && !tpv && viewer.state != Player.DYING)
            r.billboard(Textures.glow, c.x + c.fx * 0.9f, c.y + c.fy * 0.9f, c.z + c.fz * 0.9f, 3.5f, 3.5f, 0.2f, 0.5f, 1f, 0.12f + 0.06f * (float) Math.sin(t * 8), Mesh.ADDITIVE);
    }

    /** Atmosfera: halos de luz, niebla baja que se mueve y lluvia. */
    private void atmosphere(Renderer r, Game g, float t) {
        Camera c = r.cam;
        if (!fancy) return;
        int i = 0;
        for (float[] l : g.level.lights) {
            i++;
            if (l.length < 8) continue;
            float dx = l[0] - c.x, dz = l[2] - c.z;
            if (dx * dx + dz * dz > 42 * 42 || !near(r, l[0], l[1], l[2], 3f, 45)) continue;
            float size = Math.min(6f, l[6] * 0.32f);
            float fl = 0.8f + 0.2f * (float) Math.sin(t * 7f + i * 1.7f) * (l[6] > 12 ? 0.6f : 1.4f);
            r.billboard(Textures.glow, l[0], l[1], l[2], size, size, l[3], l[4], l[5], 0.55f * fl, Mesh.ADDITIVE);
        }
        // niebla baja a la deriva (dos capas)
        float sz = 70f;
        for (int k = 0; k < 1; k++) {
            float sc = 22f + k * 14f, dr = t * (0.012f + 0.01f * k);
            float x0 = c.x - sz / 2, z0 = c.z - sz / 2;
            r.floorQuadUV(Textures.fogTex, c.x, 0.55f + k * 0.9f, c.z, sz, 0, 0.45f, 0.55f, 0.7f, 0.10f - 0.02f * k, Mesh.ADDITIVE | Mesh.EMISSIVE,
                    x0 / sc + dr, z0 / sc + (k == 0 ? 0 : 0.3f), (x0 + sz) / sc + dr, (z0 + sz) / sc + (k == 0 ? 0 : 0.3f));
        }
        // lluvia
        for (int d = 0; d < 70; d++) {
            float hx = hash(d, 11), hz = hash(d, 23), hy = hash(d, 37);
            float x = c.x + (hx - 0.5f) * 24f, z = c.z + (hz - 0.5f) * 24f;
            float y = 12f - ((g.rainTime * (13f + hy * 5f) + hy * 12f) % 12f);
            r.vbillboard(Textures.white, x, y, z, 0.03f, 0.9f, 0.7f, 0.8f, 1f, 0.28f, Mesh.ADDITIVE);
        }
    }
}
