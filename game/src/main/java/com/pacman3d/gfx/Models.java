package com.pacman3d.gfx;

/** Modelos 3D del juego, todos construidos por codigo. */
public final class Models {
    private Models() {}

    // ------------------------------------------------------------ FANTASMA
    public static final int GSEG = 20, GROWS = 12;
    public static final float GR = 0.85f, GYS = 1.95f, GHEM = 0.12f;
    public static Mesh[] ghostBody = new Mesh[4];
    public static Mesh ghostBodyDead;
    public static Mesh ghostArm, ghostGlowQuad;
    public static Mesh pacTop, pacBottom, pacInside, pacEyes;
    public static Mesh shoe, rainDrop, chestBodyW, chestLidW;
    public static Human[] men = new Human[4], women = new Human[4], vendors = new Human[3];
    public static Mesh chestBody, chestLid, star, key, ammoBox, medkit, doorWing, doorFrame, bat, batWing, arrow, bullet, grenade, cube, orb, shieldOrb;
    private static boolean ready;

    public static synchronized void init() {
        if (ready) return;
        ready = true;
        Textures.init();
        for (int i = 0; i < 4; i++) ghostBody[i] = buildGhost(Textures.ghost[i]);
        ghostBodyDead = buildGhost(Textures.ghostDead);
        MeshBuilder a = new MeshBuilder(Textures.ghost[0], Mesh.DYNLIT, 0.9f);
        a.translate(0, -0.45f, 0).cylinder(0.28f, 0.1f, 0.9f, 6, false);
        ghostArm = a.build();
        buildPac();
        buildItems();
        MeshBuilder sh = new MeshBuilder(Textures.white, Mesh.DYNLIT, 1f);
        sh.color(0.95f, 0.95f, 0.95f).push().translate(0, 0, 0.1f).sphere(0.3f, 0.17f, 0.44f, 10, 6).pop();
        sh.color(0.85f, 0.1f, 0.1f).push().translate(0, 0.08f, 0.0f).box(0.62f, 0.05f, 0.18f).pop();
        sh.color(0.15f, 0.15f, 0.18f).push().translate(0, -0.1f, 0.1f).box(0.6f, 0.05f, 0.9f).pop();
        shoe = sh.build();
        buildRainbowChest();
        float[][] shirts = {{0.35f, 0.55f, 0.9f}, {0.9f, 0.35f, 0.3f}, {0.35f, 0.8f, 0.45f}, {0.9f, 0.8f, 0.3f}};
        float[][] pants = {{0.2f, 0.2f, 0.35f}, {0.35f, 0.3f, 0.2f}, {0.15f, 0.15f, 0.15f}, {0.3f, 0.25f, 0.4f}};
        float[][] hairs = {{0.15f, 0.1f, 0.06f}, {0.7f, 0.5f, 0.2f}, {0.08f, 0.08f, 0.1f}, {0.55f, 0.2f, 0.1f}};
        float[] skin = {0.95f, 0.78f, 0.65f};
        for (int i = 0; i < 4; i++) {
            men[i] = buildHuman(false, shirts[i], pants[i], hairs[i], skin, 0.6f, null);
            women[i] = buildHuman(true, shirts[(i + 1) % 4], pants[(i + 2) % 4], hairs[(i + 3) % 4], skin, 0.6f, null);
        }
        vendors[0] = buildHuman(false, new float[]{0.55f, 0.15f, 0.12f}, new float[]{0.25f, 0.2f, 0.15f}, new float[]{0.3f, 0.2f, 0.1f}, skin, 0.96f, new float[]{0.35f, 0.22f, 0.1f});
        vendors[1] = buildHuman(true, new float[]{0.4f, 0.15f, 0.6f}, new float[]{0.2f, 0.1f, 0.3f}, new float[]{0.1f, 0.08f, 0.12f}, new float[]{0.7f, 0.85f, 0.65f}, 0.96f, new float[]{0.18f, 0.08f, 0.28f});
        vendors[2] = buildHuman(false, new float[]{0.15f, 0.55f, 0.55f}, new float[]{0.6f, 0.45f, 0.15f}, new float[]{0.1f, 0.1f, 0.1f}, skin, 0.96f, new float[]{0.9f, 0.75f, 0.2f});
    }

    private static Mesh buildGhost(Texture tex) {
        MeshBuilder b = new MeshBuilder(tex, Mesh.DYNLIT, 0.88f);
        for (int row = 0; row < GROWS; row++)
            for (int i = 0; i <= GSEG; i++) {
                float u = i / (float) GSEG, a = (u - 0.5f) * 6.2831853f;
                b.v(0, 0, 0, u, row / (float) (GROWS - 1) * 0.985f, (float) Math.sin(a), 0.2f, (float) Math.cos(a));
            }
        for (int row = 0; row < GROWS - 1; row++)
            for (int i = 0; i < GSEG; i++) {
                int p = row * (GSEG + 1) + i;
                b.quad(p, p + 1, p + GSEG + 2, p + GSEG + 1);
            }
        Mesh m = b.build();
        animateGhost(m, 0f, 0f, 0f, 1f);
        m.br = 2.6f; m.by = 1.2f;
        return m;
    }

    /** Anima el manto: ondulacion del borde y arrastre. Modifica m.pos. */
    public static void animateGhost(Mesh m, float time, float phase, float trail, float scale) {
        float[] p = m.pos;
        for (int row = 0; row < GROWS; row++) {
            for (int i = 0; i <= GSEG; i++) {
                float a = ((i / (float) GSEG) - 0.5f) * 6.2831853f;
                float sa = (float) Math.sin(a), ca = (float) Math.cos(a);
                float x, y, z;
                if (row <= 6) {
                    float th = (float) (Math.PI / 2 * row / 6.0);
                    float r = GR * (float) Math.sin(th);
                    x = r * sa; z = r * ca; y = GYS + GR * 1.05f * (float) Math.cos(th);
                } else {
                    float t = (row - 6) / 5f;
                    float r = GR * (1f + 0.12f * t);
                    float wave = 0.26f * (float) Math.sin(4 * a + time * 3.2f + phase) + 0.14f * (float) Math.sin(7 * a - time * 4.1f + phase * 2)
                            + 0.1f * (float) Math.sin(11 * a + time * 6f);
                    y = GYS - t * (GYS - GHEM) + t * t * wave;
                    x = r * sa + (float) Math.sin(time * 1.7f + phase) * 0.08f * t;
                    z = r * ca - trail * t * t * 0.55f;
                }
                int k = (row * (GSEG + 1) + i) * 3;
                p[k] = x * scale; p[k + 1] = y * scale; p[k + 2] = z * scale;
            }
        }
    }

    // ------------------------------------------------------------ PAC-MAN
    private static void buildPac() {
        MeshBuilder t = new MeshBuilder(Textures.white, Mesh.DYNLIT, 1f);
        halfSphere(t, 1f);
        pacTop = t.build();
        MeshBuilder b = new MeshBuilder(Textures.white, Mesh.DYNLIT, 1f);
        halfSphere(b, -1f);
        pacBottom = b.build();
        MeshBuilder in = new MeshBuilder(Textures.white, Mesh.DYNLIT, 1f);
        in.color(0.55f, 0.05f, 0.08f);
        in.cylinder(0.98f, 0.98f, 0.02f, 24, true);
        pacInside = in.build();
        MeshBuilder e = new MeshBuilder(Textures.white, Mesh.DYNLIT, 1f);
        e.color(0.02f, 0.02f, 0.02f);
        e.push().translate(-0.36f, 0.5f, 0.6f).sphere(0.12f, 0.2f, 0.1f, 8, 6).pop();
        e.push().translate(0.36f, 0.5f, 0.6f).sphere(0.12f, 0.2f, 0.1f, 8, 6).pop();
        pacEyes = e.build();
    }

    private static void halfSphere(MeshBuilder b, float sgn) {
        int seg = 22, rings = 9;
        int[][] g = new int[rings + 1][seg + 1];
        for (int j = 0; j <= rings; j++) {
            float ph = (float) (Math.PI / 2 * j / rings);
            float cy = (float) Math.sin(ph) * sgn, cr = (float) Math.cos(ph);
            for (int i = 0; i <= seg; i++) {
                float a = (float) (i * 2 * Math.PI / seg);
                float nx = cr * (float) Math.cos(a), nz = cr * (float) Math.sin(a);
                g[j][i] = b.v(nx, cy, nz, i / (float) seg, j / (float) rings, nx, cy, nz);
            }
        }
        for (int j = 0; j < rings; j++) for (int i = 0; i < seg; i++) b.quad(g[j][i], g[j][i + 1], g[j + 1][i + 1], g[j + 1][i]);
    }

    /** Dibuja un Pac-Man con la boca abierta 'mouth' (rad). */
    public static void drawPac(Renderer r, float x, float y, float z, float yaw, float scale, float mouth, float cr, float cg, float cb, float lr, float lg, float lb) {
        drawPac(r, x, y, z, yaw, 0f, scale, mouth, cr, cg, cb, lr, lg, lb);
    }

    public static void drawPac(Renderer r, float x, float y, float z, float yaw, float tilt, float scale, float mouth, float cr, float cg, float cb, float lr, float lg, float lb) {
        float[] base = Mat.trs(x, y, z, yaw, tilt, 0, scale, scale, scale);
        // la boca mira a +z local; las mitades giran sobre el eje x en la parte trasera
        float[] top = Mat.mul(base, Mat.trs(0, 0, 0, 0, -mouth, 0, 1, 1, 1));
        float[] bot = Mat.mul(base, Mat.trs(0, 0, 0, 0, mouth, 0, 1, 1, 1));
        r.mesh(pacTop, top, cr, cg, cb, lr, lg, lb);
        r.mesh(pacBottom, bot, cr, cg, cb, lr, lg, lb);
        r.mesh(pacInside, top, 1, 1, 1, lr, lg, lb);
        r.mesh(pacInside, bot, 1, 1, 1, lr, lg, lb);
        r.mesh(pacEyes, Mat.mul(base, Mat.trs(0, 0, 0, 0, -mouth, 0, 1, 1, 1)), 1, 1, 1, lr, lg, lb);
    }

    public static void drawShoe(Renderer r, float x, float y, float z, float yaw, float scale, float lr, float lg, float lb) {
        r.mesh(shoe, Mat.trs(x, y, z, yaw, 0, 0, scale, scale, scale), 1, 1, 1, lr, lg, lb);
    }

    // ------------------------------------------------------------ PERSONAS (almas y tenderos)
    public static final class Human {
        public Mesh body, arm, leg;

        /** run = fase de la carrera, amount 0 (quieto) .. 1 (corriendo). lying = tirado en el suelo. */
        public void draw(Renderer r, float x, float y, float z, float yaw, float run, float amount, boolean lying, float scale, float tr, float tg, float tb, float ol, float og, float ob) {
            float bob = lying ? 0 : Math.abs((float) Math.sin(run)) * 0.09f * amount;
            float lean = lying ? -1.5708f : 0.2f * amount;
            float[] base = Mat.trs(x, lying ? 0.2f : y + bob, z, yaw, lean, 0, scale, scale, scale);
            r.mesh(body, base, tr, tg, tb, ol, og, ob);
            float sw = (float) Math.sin(run) * 0.95f * amount, idle = (float) Math.sin(run * 0.5f) * 0.05f;
            float la = lying ? 0.5f : -sw + idle, ra = lying ? -0.4f : sw - idle;
            r.mesh(leg, Mat.mul(base, Mat.trs(-0.13f, 0.88f, 0, 0, lying ? 0.1f : sw, 0, 1, 1, 1)), tr, tg, tb, ol, og, ob);
            r.mesh(leg, Mat.mul(base, Mat.trs(0.13f, 0.88f, 0, 0, lying ? -0.2f : -sw, 0, 1, 1, 1)), tr, tg, tb, ol, og, ob);
            r.mesh(arm, Mat.mul(base, Mat.trs(-0.34f, 1.55f, 0, 0, la, lying ? 0.7f : 0.12f, 1, 1, 1)), tr, tg, tb, ol, og, ob);
            r.mesh(arm, Mat.mul(base, Mat.trs(0.34f, 1.55f, 0, 0, ra, lying ? -0.7f : -0.12f, 1, 1, 1)), tr, tg, tb, ol, og, ob);
        }
    }

    private static Human buildHuman(boolean female, float[] shirt, float[] pants, float[] hair, float[] skin, float alpha, float[] hat) {
        Human h = new Human();
        MeshBuilder b = new MeshBuilder(Textures.white, Mesh.DYNLIT, alpha);
        // torso
        b.color(shirt[0], shirt[1], shirt[2]);
        if (female) {
            b.push().translate(0, 1.27f, 0).box(0.42f, 0.62f, 0.23f).pop();
            b.color(shirt[0] * 0.8f, shirt[1] * 0.8f, shirt[2] * 0.9f);
            b.push().translate(0, 0.92f, 0).cylinder(0.5f, 0.24f, 0.92f, 12, true).pop();             // vestido
        } else {
            b.push().translate(0, 1.25f, 0).box(0.54f, 0.74f, 0.28f).pop();
            b.color(pants[0], pants[1], pants[2]);
            b.push().translate(0, 0.86f, 0).box(0.48f, 0.2f, 0.26f).pop();
        }
        // cuello, cabeza, cara
        b.color(skin[0], skin[1], skin[2]);
        b.push().translate(0, 1.64f, 0).cylinder(0.07f, 0.07f, 0.12f, 6, true).pop();
        b.push().translate(0, 1.82f, 0).sphere(0.17f, 0.2f, 0.18f, 10, 8).pop();
        b.push().translate(0, 1.78f, 0.17f).box(0.04f, 0.07f, 0.05f).pop();
        b.color(0.05f, 0.05f, 0.08f);
        b.push().translate(-0.065f, 1.85f, 0.15f).sphere(0.025f, 0.03f, 0.02f, 5, 4).pop();
        b.push().translate(0.065f, 1.85f, 0.15f).sphere(0.025f, 0.03f, 0.02f, 5, 4).pop();
        b.color(0.6f, 0.15f, 0.15f);
        b.push().translate(0, 1.72f, 0.16f).box(0.07f, 0.015f, 0.02f).pop();
        // pelo
        b.color(hair[0], hair[1], hair[2]);
        b.push().translate(0, 1.93f, -0.02f).sphere(0.19f, 0.13f, 0.2f, 10, 6).pop();
        if (female) { b.push().translate(0, 1.55f, -0.14f).box(0.34f, 0.6f, 0.08f).pop(); b.push().translate(0.0f, 1.8f, -0.1f).sphere(0.2f, 0.22f, 0.12f, 8, 6).pop(); }
        if (hat != null) {
            b.color(hat[0], hat[1], hat[2]);
            if (female) { b.push().translate(0, 2.0f, 0).cylinder(0.34f, 0.34f, 0.05f, 12, true).pop(); b.push().translate(0, 2.35f, 0).cone(0.19f, 0.7f, 10).pop(); }
            else { b.push().translate(0, 2.02f, 0).cylinder(0.2f, 0.2f, 0.22f, 10, true).pop(); b.push().translate(0, 1.93f, 0).cylinder(0.3f, 0.3f, 0.04f, 10, true).pop(); }
        }
        h.body = b.build();
        // brazo: manga + mano
        MeshBuilder a = new MeshBuilder(Textures.white, Mesh.DYNLIT, alpha);
        a.color(shirt[0], shirt[1], shirt[2]).push().translate(0, -0.2f, 0).cylinder(0.075f, 0.065f, 0.42f, 6, true).pop();
        a.color(skin[0], skin[1], skin[2]).push().translate(0, -0.55f, 0).cylinder(0.06f, 0.05f, 0.3f, 6, true).pop();
        a.push().translate(0, -0.72f, 0).sphere(0.06f, 0.07f, 0.06f, 6, 5).pop();
        h.arm = a.build();
        // pierna
        MeshBuilder l = new MeshBuilder(Textures.white, Mesh.DYNLIT, alpha);
        if (female) l.color(skin[0], skin[1], skin[2]); else l.color(pants[0], pants[1], pants[2]);
        l.push().translate(0, -0.4f, 0).cylinder(0.09f, 0.075f, 0.8f, 6, true).pop();
        l.color(0.12f, 0.1f, 0.1f).push().translate(0, -0.84f, 0.05f).box(0.15f, 0.1f, 0.3f).pop();
        h.leg = l.build();
        return h;
    }

    private static void buildRainbowChest() {
        MeshBuilder c = new MeshBuilder(Textures.white, Mesh.DYNLIT, 1f);
        c.color(1f, 1f, 1f).translate(0, 0.4f, 0).box(1.6f, 0.8f, 1.05f);
        MeshBuilder bands = new MeshBuilder(Textures.white, Mesh.DYNLIT, 1f);
        bands.color(1.2f, 0.9f, 0.35f);
        bands.push().translate(-0.55f, 0.4f, 0).box(0.14f, 0.86f, 1.1f).pop();
        bands.push().translate(0.55f, 0.4f, 0).box(0.14f, 0.86f, 1.1f).pop();
        bands.push().translate(0, 0.02f, 0).box(1.66f, 0.07f, 1.1f).pop();
        bands.push().translate(0, 0.62f, 0.55f).box(0.3f, 0.3f, 0.08f).pop();
        chestBodyW = merge(c.build(), bands.build());
        MeshBuilder l = new MeshBuilder(Textures.white, Mesh.DYNLIT, 1f);
        l.color(1f, 1f, 1f).push().translate(0, 0, 0.52f).rotZ(1.5708f).cylinder(0.52f, 0.52f, 1.6f, 14, true).pop();
        l.color(1.2f, 0.9f, 0.35f).push().translate(0, 0.0f, 0.52f).rotZ(1.5708f).cylinder(0.54f, 0.54f, 0.14f, 14, true).pop();
        chestLidW = l.build();
    }

    // ------------------------------------------------------------ OBJETOS
    private static void buildItems() {
        // cofre
        MeshBuilder c = new MeshBuilder(Textures.wood, Mesh.DYNLIT, 1f);
        c.translate(0, 0.4f, 0).box(1.5f, 0.8f, 1.0f);
        MeshBuilder bands = new MeshBuilder(Textures.wood, Mesh.DYNLIT, 1f);
        bands.color(0.28f, 0.28f, 0.3f);
        bands.push().translate(-0.5f, 0.4f, 0).box(0.12f, 0.84f, 1.04f).pop();
        bands.push().translate(0.5f, 0.4f, 0).box(0.12f, 0.84f, 1.04f).pop();
        bands.push().translate(0, 0.02f, 0).box(1.56f, 0.06f, 1.06f).pop();
        chestBody = merge(c.build(), bands.build());
        MeshBuilder l = new MeshBuilder(Textures.wood, Mesh.DYNLIT, 1f);
        l.push().translate(0, 0, 0.5f).rotZ(1.5708f).rotX(0f).cylinder(0.5f, 0.5f, 1.5f, 14, true).pop(); // eje X, borde en la bisagra
        l.shade = 1f;
        chestLid = l.build();
        // estrella
        MeshBuilder s = new MeshBuilder(Textures.star, Mesh.EMISSIVE, 1f);
        s.color(1.1f, 0.9f, 0.2f);
        float ro = 0.55f, ri = 0.24f, th = 0.13f;
        int f0 = s.v(0, 0, th, 0.5f, 0.5f, 0, 0, 1), b0 = s.v(0, 0, -th, 0.5f, 0.5f, 0, 0, -1);
        int[] fv = new int[10], bv = new int[10];
        for (int i = 0; i < 10; i++) {
            float a = (float) (Math.PI / 2 + i * Math.PI / 5), rr = i % 2 == 0 ? ro : ri;
            float px = (float) Math.cos(a) * rr, py = (float) Math.sin(a) * rr;
            fv[i] = s.v(px, py, th * 0.3f, 0.5f, 0.5f, 0, 0, 1);
            bv[i] = s.v(px, py, -th * 0.3f, 0.5f, 0.5f, 0, 0, -1);
        }
        for (int i = 0; i < 10; i++) { int j = (i + 1) % 10; s.tri(f0, fv[i], fv[j]); s.tri(b0, bv[j], bv[i]); s.quad(fv[i], bv[i], bv[j], fv[j]); }
        star = s.build();
        // llave
        MeshBuilder k = new MeshBuilder(Textures.gold, Mesh.DYNLIT, 1f);
        k.color(1.4f, 1.1f, 0.4f);
        k.push().translate(0, 0.35f, 0).rotX(1.5708f).cylinder(0.2f, 0.2f, 0.1f, 12, true).pop();
        k.push().translate(0, -0.05f, 0).box(0.1f, 0.8f, 0.1f).pop();
        k.push().translate(0.12f, -0.35f, 0).box(0.25f, 0.1f, 0.1f).pop();
        k.push().translate(0.1f, -0.2f, 0).box(0.2f, 0.1f, 0.1f).pop();
        key = k.build();
        // caja de municion
        MeshBuilder am = new MeshBuilder(Textures.ammoBox, Mesh.DYNLIT, 1f);
        am.translate(0, 0.25f, 0).box(0.8f, 0.5f, 0.5f);
        ammoBox = am.build();
        MeshBuilder md = new MeshBuilder(Textures.medkit, Mesh.DYNLIT, 1f);
        md.translate(0, 0.25f, 0).box(0.7f, 0.5f, 0.5f);
        medkit = md.build();
        // puerta
        MeshBuilder dw = new MeshBuilder(Textures.door, Mesh.DYNLIT, 1f);
        dw.translate(0.95f, 2.4f, 0).box(1.9f, 4.8f, 0.2f);
        doorWing = dw.build();
        MeshBuilder df = new MeshBuilder(Textures.metal, Mesh.DYNLIT, 1f);
        df.push().translate(-2.05f, 2.7f, 0).box(0.3f, 5.4f, 0.5f).pop();
        df.push().translate(2.05f, 2.7f, 0).box(0.3f, 5.4f, 0.5f).pop();
        df.push().translate(0, 5.3f, 0).box(4.4f, 0.6f, 0.6f).pop();
        df.push().translate(0, 5.9f, 0).box(3.0f, 0.5f, 0.5f).pop();
        doorFrame = df.build();
        // murcielago
        MeshBuilder bt = new MeshBuilder(Textures.dark, Mesh.DYNLIT, 1f);
        bt.color(0.35f, 0.3f, 0.35f).sphere(0.16f, 0.14f, 0.28f, 6, 4);
        bat = bt.build();
        MeshBuilder bw = new MeshBuilder(Textures.dark, Mesh.DYNLIT, 1f);
        bw.color(0.35f, 0.3f, 0.35f);
        int w0 = bw.v(0, 0, 0.18f, 0, 0, 0, 1, 0), w1 = bw.v(1.0f, 0.1f, 0.25f, 0, 0, 0, 1, 0), w2 = bw.v(0.7f, 0.0f, -0.35f, 0, 0, 0, 1, 0), w3 = bw.v(0.2f, 0, -0.2f, 0, 0, 0, 1, 0);
        bw.tri(w0, w1, w2); bw.tri(w0, w2, w3);
        batWing = bw.build();
        // flecha / bala / granada
        MeshBuilder ar = new MeshBuilder(Textures.wood, Mesh.DYNLIT, 1f);
        ar.push().rotX(1.5708f).cylinder(0.025f, 0.025f, 1.1f, 5, false).pop();
        ar.push().translate(0, 0, 0.6f).rotX(1.5708f).color(0.7f, 0.7f, 0.75f).cone(0.06f, 0.2f, 6).pop();
        ar.color(0.9f, 0.9f, 0.9f).push().translate(0, 0, -0.5f).box(0.2f, 0.01f, 0.2f).pop();
        ar.push().translate(0, 0, -0.5f).box(0.01f, 0.2f, 0.2f).pop();
        arrow = ar.build();
        MeshBuilder bu = new MeshBuilder(Textures.white, Mesh.EMISSIVE, 1f);
        bu.color(1.4f, 1.1f, 0.5f).sphere(0.1f, 0.1f, 0.22f, 6, 4);
        bullet = bu.build();
        MeshBuilder gr = new MeshBuilder(Textures.white, Mesh.DYNLIT, 1f);
        gr.color(0.25f, 0.35f, 0.18f).sphere(0.18f, 0.22f, 0.18f, 10, 8);
        gr.color(0.5f, 0.5f, 0.5f).push().translate(0, 0.24f, 0).cylinder(0.07f, 0.07f, 0.1f, 6, true).pop();
        grenade = gr.build();
        MeshBuilder cu = new MeshBuilder(Textures.white, Mesh.EMISSIVE, 1f);
        cu.box(1, 1, 1);
        cube = cu.build();
        MeshBuilder ob = new MeshBuilder(Textures.white, Mesh.EMISSIVE | Mesh.ADDITIVE, 0.45f);
        ob.sphere(1f, 1f, 1f, 14, 10);
        shieldOrb = ob.build();
    }

    /** Une dos mallas (misma textura no requerida: usa la de la primera, colores de ambas). */
    private static Mesh merge(Mesh a, Mesh b) {
        Mesh m = new Mesh();
        int va = a.vcount, vb = b.vcount;
        m.pos = cat(a.pos, b.pos); m.uv = cat(a.uv, b.uv); m.nrm = cat(a.nrm, b.nrm); m.col = cat(a.col, b.col);
        m.idx = new int[a.idx.length + b.idx.length];
        System.arraycopy(a.idx, 0, m.idx, 0, a.idx.length);
        for (int i = 0; i < b.idx.length; i++) m.idx[a.idx.length + i] = b.idx[i] + va;
        m.vcount = va + vb; m.tcount = a.tcount + b.tcount; m.tex = a.tex; m.flags = a.flags; m.alpha = a.alpha;
        m.computeBounds();
        return m;
    }

    private static float[] cat(float[] a, float[] b) {
        float[] o = new float[a.length + b.length];
        System.arraycopy(a, 0, o, 0, a.length); System.arraycopy(b, 0, o, a.length, b.length);
        return o;
    }
}
