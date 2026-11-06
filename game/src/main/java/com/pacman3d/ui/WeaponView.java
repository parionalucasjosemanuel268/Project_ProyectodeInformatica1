package com.pacman3d.ui;

import com.pacman3d.game.Player;
import com.pacman3d.game.Weapon;
import com.pacman3d.gfx.Mat;
import com.pacman3d.gfx.Mesh;
import com.pacman3d.gfx.MeshBuilder;
import com.pacman3d.gfx.Renderer;
import com.pacman3d.gfx.Textures;

/** Armas en primera persona: cada una es un modelo 3D distinto con su propia animacion. */
public final class WeaponView {
    private static boolean built;
    private static Mesh hand, cuff, pistolBody, pistolSlide, rifleBody, rifleWood, bowLimb, minigunBody, minigunCluster, swordBlade, swordHilt, grenadeM, flashM, cubeM, arrowM;

    private static MeshBuilder mb(com.pacman3d.gfx.Texture t) { return new MeshBuilder(t, Mesh.DYNLIT, 1f); }

    private static void build() {
        if (built) return;
        built = true;
        // mano enguantada (se tine con el color de Pac-Man)
        MeshBuilder h = mb(Textures.white);
        h.push().translate(0, 0, 0).sphere(0.062f, 0.058f, 0.07f, 10, 8).pop();
        h.push().translate(0, -0.05f, -0.2f).rotX(1.45f).cylinder(0.05f, 0.056f, 0.42f, 8, true).pop();
        hand = h.build();
        MeshBuilder cf = mb(Textures.white);
        cf.color(0.95f, 0.95f, 0.95f).push().translate(0, -0.06f, -0.14f).rotX(1.45f).cylinder(0.062f, 0.062f, 0.05f, 8, false).pop();
        cuff = cf.build();
        // PISTOLA
        MeshBuilder p = mb(Textures.metal);
        p.color(0.55f, 0.57f, 0.62f);
        p.push().translate(0, 0.0f, 0.02f).box(0.05f, 0.04f, 0.2f).pop();                         // armazon
        p.color(0.12f, 0.12f, 0.14f);
        p.push().translate(0, -0.075f, -0.03f).rotX(0.22f).box(0.05f, 0.14f, 0.07f).pop();        // empunadura
        p.push().translate(0, -0.035f, 0.06f).box(0.02f, 0.012f, 0.07f).pop();                    // guardamonte
        p.push().translate(0, -0.03f, 0.11f).box(0.02f, 0.045f, 0.012f).pop();
        p.color(0.25f, 0.26f, 0.3f).push().translate(0, 0.0f, 0.2f).rotX(1.5708f).cylinder(0.014f, 0.014f, 0.1f, 8, true).pop(); // canon
        p.color(0.9f, 0.9f, 0.9f).push().translate(0, 0.03f, 0.13f).box(0.008f, 0.012f, 0.008f).pop();
        pistolBody = p.build();
        MeshBuilder ps = mb(Textures.metal);
        ps.color(0.38f, 0.4f, 0.45f).push().translate(0, 0.035f, 0.04f).box(0.052f, 0.04f, 0.23f).pop();
        ps.color(0.1f, 0.1f, 0.1f).push().translate(0, 0.037f, -0.06f).box(0.054f, 0.03f, 0.015f).pop();
        pistolSlide = ps.build();
        // RIFLE de asalto
        MeshBuilder r = mb(Textures.metal);
        r.color(0.2f, 0.21f, 0.23f);
        r.push().translate(0, 0.01f, 0.1f).box(0.06f, 0.08f, 0.42f).pop();
        r.push().translate(0, 0.012f, 0.5f).rotX(1.5708f).cylinder(0.014f, 0.014f, 0.38f, 8, true).pop();
        r.push().translate(0, 0.012f, 0.72f).box(0.012f, 0.045f, 0.012f).pop();
        r.push().translate(0.0f, 0.065f, 0.0f).box(0.03f, 0.05f, 0.14f).pop();                    // mira / alza
        r.color(0.1f, 0.1f, 0.1f).push().translate(0, -0.1f, 0.16f).rotX(-0.18f).box(0.045f, 0.16f, 0.07f).pop(); // cargador
        r.push().translate(0, -0.06f, -0.04f).rotX(0.3f).box(0.045f, 0.12f, 0.05f).pop();        // empunadura
        r.color(0.2f, 0.9f, 0.3f).push().translate(0, 0.095f, 0.0f).box(0.012f, 0.012f, 0.012f).pop();
        rifleBody = r.build();
        MeshBuilder rw = mb(Textures.wood);
        rw.color(1.3f, 1.2f, 1.1f);
        rw.push().translate(0, -0.005f, -0.3f).rotX(-0.1f).box(0.055f, 0.1f, 0.3f).pop();         // culata
        rw.push().translate(0, -0.015f, 0.38f).box(0.065f, 0.06f, 0.22f).pop();                    // guardamano
        rifleWood = rw.build();
        // ARCO
        MeshBuilder b = mb(Textures.wood);
        b.color(1.0f, 0.75f, 0.45f);
        for (int i = -5; i < 5; i++) {
            float y0 = i * 0.075f, y1 = (i + 1) * 0.075f;
            float z0 = 0.2f * (1f - (i / 5f) * (i / 5f)), z1 = 0.2f * (1f - ((i + 1) / 5f) * ((i + 1) / 5f));
            float len = (float) Math.hypot(y1 - y0, z1 - z0), ang = (float) Math.atan2(z1 - z0, y1 - y0);
            b.push().translate(0, (y0 + y1) / 2, (z0 + z1) / 2).rotX(-ang).box(0.03f, len + 0.01f, 0.035f).pop();
        }
        b.color(0.3f, 0.2f, 0.1f).push().translate(0, 0, 0.2f).box(0.04f, 0.12f, 0.05f).pop();
        bowLimb = b.build();
        MeshBuilder ar = mb(Textures.wood);
        ar.push().rotX(1.5708f).cylinder(0.008f, 0.008f, 0.75f, 5, false).pop();
        ar.color(0.8f, 0.8f, 0.85f).push().translate(0, 0, 0.4f).rotX(1.5708f).cone(0.02f, 0.07f, 6).pop();
        ar.color(1f, 0.3f, 0.3f).push().translate(0, 0, -0.34f).box(0.07f, 0.004f, 0.08f).pop();
        ar.push().translate(0, 0, -0.34f).box(0.004f, 0.07f, 0.08f).pop();
        arrowM = ar.build();
        // MINIGUN
        MeshBuilder m = mb(Textures.metal);
        m.color(0.28f, 0.29f, 0.32f);
        m.push().translate(0, 0, -0.05f).rotX(1.5708f).cylinder(0.075f, 0.075f, 0.3f, 12, true).pop();
        m.color(0.14f, 0.14f, 0.15f);
        m.push().translate(0, 0.1f, -0.05f).box(0.035f, 0.07f, 0.28f).pop();                       // asa
        m.push().translate(0, -0.09f, -0.18f).box(0.15f, 0.11f, 0.14f).pop();                      // caja de municion
        m.color(0.8f, 0.6f, 0.2f).push().translate(0.0f, -0.07f, 0.0f).box(0.12f, 0.03f, 0.2f).pop();
        m.color(0.18f, 0.18f, 0.2f).push().translate(0, 0, 0.14f).rotX(1.5708f).cylinder(0.085f, 0.085f, 0.05f, 12, true).pop();
        minigunBody = m.build();
        MeshBuilder mc = mb(Textures.metal);
        mc.color(0.35f, 0.36f, 0.4f);
        for (int i = 0; i < 6; i++) {
            float a = (float) (i * Math.PI / 3);
            mc.push().translate((float) Math.cos(a) * 0.045f, (float) Math.sin(a) * 0.045f, 0.4f).rotX(1.5708f).cylinder(0.014f, 0.014f, 0.5f, 8, true).pop();
        }
        mc.color(0.2f, 0.2f, 0.22f);
        mc.push().translate(0, 0, 0.3f).rotX(1.5708f).cylinder(0.075f, 0.075f, 0.025f, 12, true).pop();
        mc.push().translate(0, 0, 0.62f).rotX(1.5708f).cylinder(0.07f, 0.07f, 0.025f, 12, true).pop();
        minigunCluster = mc.build();
        // ESPADA
        MeshBuilder sb = mb(Textures.metal);
        sb.color(1.7f, 1.75f, 1.9f).push().translate(0, 0.55f, 0).box(0.045f, 0.95f, 0.012f).pop();
        sb.push().translate(0, 1.05f, 0).rotZ(0.78f).box(0.032f, 0.032f, 0.012f).pop();
        sb.color(0.7f, 0.72f, 0.8f).push().translate(0, 0.55f, 0.008f).box(0.012f, 0.9f, 0.006f).pop();
        swordBlade = sb.build();
        MeshBuilder sh = mb(Textures.metal);
        sh.color(1.4f, 1.0f, 0.3f).push().translate(0, 0.06f, 0).box(0.24f, 0.035f, 0.05f).pop();
        sh.push().translate(0, -0.12f, 0).sphere(0.03f, 0.03f, 0.03f, 8, 6).pop();
        sh.color(0.25f, 0.12f, 0.1f).push().translate(0, -0.03f, 0).cylinder(0.02f, 0.02f, 0.18f, 8, true).pop();
        swordHilt = sh.build();
        // GRANADA
        MeshBuilder g = mb(Textures.white);
        g.color(0.22f, 0.32f, 0.16f).sphere(0.075f, 0.09f, 0.075f, 12, 8);
        g.color(0.6f, 0.6f, 0.62f).push().translate(0, 0.1f, 0).cylinder(0.03f, 0.03f, 0.05f, 8, true).pop();
        g.color(0.7f, 0.7f, 0.2f).push().translate(0.05f, 0.1f, 0).box(0.1f, 0.012f, 0.025f).pop();
        g.color(0.9f, 0.9f, 0.1f).push().translate(0.0f, 0.15f, 0).rotX(1.5708f).cylinder(0.012f, 0.012f, 0.04f, 6, false).pop();
        grenadeM = g.build();
        // fogonazo
        MeshBuilder f = new MeshBuilder(Textures.glow, Mesh.EMISSIVE | Mesh.ADDITIVE, 0.95f);
        f.color(1.5f, 1.1f, 0.5f);
        f.quadP(-0.18f, 0, -0.18f, 0.18f, 0, -0.18f, 0.18f, 0, 0.18f, -0.18f, 0, 0.18f, 0, 0, 1, 1);
        f.quadP(0, -0.18f, -0.18f, 0, 0.18f, -0.18f, 0, 0.18f, 0.18f, 0, -0.18f, 0.18f, 0, 0, 1, 1);
        f.quadP(-0.14f, -0.14f, 0, 0.14f, -0.14f, 0, 0.14f, 0.14f, 0, -0.14f, 0.14f, 0, 0, 0, 1, 1);
        flashM = f.build();
        MeshBuilder c = new MeshBuilder(Textures.white, Mesh.EMISSIVE, 1f);
        c.box(1, 1, 1);
        cubeM = c.build();
    }

    private static float[] cam(Renderer r, float ox, float oy, float oz) {
        com.pacman3d.gfx.Camera c = r.cam;
        return new float[]{c.rx, c.ux, c.fx, c.ry, c.uy, c.fy, c.rz, c.uz, c.fz,
                c.x + c.rx * ox + c.ux * oy + c.fx * oz, c.y + c.ry * ox + c.uy * oy + c.fy * oz, c.z + c.rz * ox + c.uz * oy + c.fz * oz};
    }

    private static void seg(Renderer r, boolean ov, float[] B, float x0, float y0, float z0, float x1, float y1, float z1, float th, float cr, float cg, float cb) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float yaw = (float) Math.atan2(dx, dz), pitch = -(float) Math.asin(dy / Math.max(len, 1e-5f));
        float[] m = Mat.mul(B, Mat.trs((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2, yaw, pitch, 0, th, th, len));
        put(r, ov, cubeM, m, cr, cg, cb);
    }

    private static float adsT;

    private static float[] base(Renderer r, boolean ov, float[] wb, float ox, float oy, float oz) { return ov ? cam(r, ox * (1f - 0.85f * adsT), oy * (1f - 0.45f * adsT) - 0.015f * adsT, oz) : wb; }

    private static void put(Renderer r, boolean ov, Mesh m, float[] mat, float a, float b, float c) {
        if (ov) r.meshOverlay(m, mat, a, b, c); else r.mesh(m, mat, a, b, c, 0.25f, 0.22f, 0.2f);
    }

    /** Arma en primera persona (ov = true) o sostenida por un Pac-Man en el mundo (wb = matriz de la mano). */
    public static void draw(Renderer r, Player g, float time, float gt, float[] col, boolean ov, float[] wb) {
        build();
        adsT = ov ? g.zoomT : 0f;
        Weapon w = g.current();
        if (ov && w == Weapon.RIFLE && g.zoomT > 0.7f) return;   // se ve a traves de la mira telescopica
        float sway = (float) Math.sin(g.bobT * 0.5f) * 0.012f * Math.min(1f, (float) Math.hypot(g.vx, g.vz) / 6f);
        float dip = Math.abs((float) Math.sin(g.bobT * 0.5f)) * 0.012f * Math.min(1f, (float) Math.hypot(g.vx, g.vz) / 6f);
        float cr = col[0], cg = col[1], cb = col[2];
        if (g.state == Player.DYING) return;
        if (w == null) { // puno vacio, listo para pelear
            float[] B = base(r, ov, wb, 0.3f + sway, -0.3f - dip, 0.55f);
            put(r, ov, hand, Mat.mul(B, Mat.trs(0, 0, 0, -0.2f, 0.1f, 0, 1, 1, 1)), cr, cg, cb);
            return;
        }
        float rc = g.recoil;
        switch (w) {
            case PISTOL: {
                float[] B = base(r, ov, wb, 0.26f + sway, -0.24f - dip + rc * 0.01f, 0.5f - rc * 0.06f);
                float[] M = Mat.mul(B, Mat.trs(0, 0, 0, -0.04f, -rc * 0.35f - 0.02f, 0, 1, 1, 1));
                put(r, ov, pistolBody, M, 1, 1, 1);
                put(r, ov, pistolSlide, Mat.mul(M, Mat.trs(0, 0, -rc * 0.07f, 0, 0, 0, 1, 1, 1)), 1, 1, 1);
                put(r, ov, hand, Mat.mul(M, Mat.trs(0, -0.06f, -0.02f, 0, 0, 0, 1, 1, 1)), cr, cg, cb);
                put(r, ov, cuff, Mat.mul(M, Mat.trs(0, -0.06f, -0.02f, 0, 0, 0, 1, 1, 1)), 1, 1, 1);
                if (g.muzzle > 0.05f) { float s = g.muzzle * 1.2f; put(r, ov, flashM, Mat.mul(M, Mat.trs(0, 0.0f, 0.3f, 0, 0, gt * 20, s, s, s)), 1, 1, 1); }
                break;
            }
            case RIFLE: {
                float[] B = base(r, ov, wb, 0.22f + sway, -0.21f - dip, 0.5f - rc * 0.05f);
                float[] M = Mat.mul(B, Mat.trs(0, 0, 0, -0.06f, -rc * 0.12f, 0, 1, 1, 1));
                put(r, ov, rifleBody, M, 1, 1, 1);
                put(r, ov, rifleWood, M, 1, 1, 1);
                put(r, ov, hand, Mat.mul(M, Mat.trs(0.0f, -0.07f, -0.03f, 0, 0.1f, 0, 1, 1, 1)), cr, cg, cb);
                put(r, ov, hand, Mat.mul(M, Mat.trs(-0.02f, -0.05f, 0.4f, 0.1f, 0.15f, 0.3f, 1, 1, 1)), cr, cg, cb);
                if (g.muzzle > 0.05f) { float s = g.muzzle * 1.6f; put(r, ov, flashM, Mat.mul(M, Mat.trs(0, 0.012f, 0.74f, 0, 0, gt * 25, s, s, s)), 1, 1, 1); }
                break;
            }
            case MINIGUN: {
                float[] B = base(r, ov, wb, 0.2f + sway, -0.24f - dip, 0.45f - rc * 0.03f);
                float shakeX = (g.spin > 0.8f ? (float) Math.sin(time * 90) * 0.004f : 0);
                float[] M = Mat.mul(B, Mat.trs(shakeX, 0, 0, -0.05f, -rc * 0.04f, 0, 1, 1, 1));
                put(r, ov, minigunBody, M, 1, 1, 1);
                float ang = gt * (4f + 38f * g.spin) * (g.spin > 0.02f ? 1 : 0) + 0.3f;
                put(r, ov, minigunCluster, Mat.mul(M, Mat.trs(0, 0, 0, 0, 0, ang, 1, 1, 1)), 1, 1, 1);
                put(r, ov, hand, Mat.mul(M, Mat.trs(0, 0.14f, -0.05f, 0, 0.2f, 0, 1, 1, 1)), cr, cg, cb);
                put(r, ov, hand, Mat.mul(M, Mat.trs(0, -0.09f, 0.1f, 0, 0.2f, 0, 1, 1, 1)), cr, cg, cb);
                if (g.muzzle > 0.05f) { float s = g.muzzle * 1.9f; put(r, ov, flashM, Mat.mul(M, Mat.trs(0, 0, 0.93f, 0, 0, gt * 40, s, s, s)), 1, 1, 1); }
                break;
            }
            case BOW: {
                float d = g.bowDraw;
                float[] B = base(r, ov, wb, 0.12f + sway, -0.12f - dip, 0.55f);
                float[] M = Mat.mul(B, Mat.trs(0, 0, 0, 0.0f, 0.0f, 0, 1, 1, 1));
                put(r, ov, bowLimb, M, 1, 1, 1);
                float pull = -0.02f - d * 0.28f;
                // cuerda en V
                seg(r, ov, M, 0, 0.375f, 0.0f, 0, 0, pull, 0.006f, 0.95f, 0.9f, 0.75f);
                seg(r, ov, M, 0, -0.375f, 0.0f, 0, 0, pull, 0.006f, 0.95f, 0.9f, 0.75f);
                put(r, ov, arrowM, Mat.mul(M, Mat.trs(0, 0, pull + 0.34f, 0, 0, 0, 1, 1, 1)), 1, 1, 1);
                put(r, ov, hand, Mat.mul(M, Mat.trs(0, -0.0f, 0.2f, 0, 0, 0, 1, 1, 1)), cr, cg, cb);
                put(r, ov, hand, Mat.mul(M, Mat.trs(0.0f, -0.02f, pull - 0.02f, 0, 0, 0, 0.9f, 0.9f, 0.9f)), cr, cg, cb);
                break;
            }
            case SWORD: {
                float u = g.swing >= 0 ? g.swing / 0.45f : 0f;
                float e = u <= 0 ? 0 : (float) Math.sin(Math.min(1, u) * Math.PI);
                float yaw = g.swing >= 0 ? 0.9f - 2.6f * Math.min(1, u * 1.2f) : -0.05f;
                float[] B = base(r, ov, wb, 0.3f + sway - e * 0.1f, -0.38f - dip + e * 0.08f, 0.6f + e * 0.1f);
                float[] M = Mat.mul(B, Mat.trs(0, 0, 0, yaw, -1.05f + e * 0.5f, -0.25f + e * 0.9f, 1, 1, 1));
                put(r, ov, swordBlade, M, 1, 1, 1);
                put(r, ov, swordHilt, M, 1, 1, 1);
                put(r, ov, hand, Mat.mul(M, Mat.trs(0, -0.03f, 0, 0, 1.4f, 0, 1.1f, 1.1f, 1.1f)), cr, cg, cb);
                break;
            }
            case GRENADE: {
                float t = g.throwT;
                if (t > 0.18f) return;
                float up = t < 0 ? 0 : t * 3f;
                float[] B = base(r, ov, wb, 0.28f + sway, -0.25f - dip + up * 0.1f, 0.5f + up * 0.5f);
                float[] M = Mat.mul(B, Mat.trs(0, 0, 0, 0, 0, 0, 1, 1, 1));
                put(r, ov, grenadeM, M, 1, 1, 1);
                put(r, ov, hand, Mat.mul(M, Mat.trs(0.03f, -0.05f, -0.02f, 0, 0.0f, 0, 1, 1, 1)), cr, cg, cb);
                put(r, ov, hand, Mat.mul(M, Mat.trs(-0.12f, -0.1f, 0.0f, 0, 0.0f, 0, 0.9f, 0.9f, 0.9f)), cr, cg, cb);
                break;
            }
        }
    }
}
