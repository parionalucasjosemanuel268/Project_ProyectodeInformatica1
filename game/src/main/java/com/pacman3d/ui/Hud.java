package com.pacman3d.ui;

import com.pacman3d.game.*;
import com.pacman3d.world.Level;

import java.awt.*;
import java.awt.geom.*;

/** Interfaz durante la partida: vida, vidas, velocidad, estrellas, tiempo, minimapa, inventario de 8 espacios, municion y mensajes. */
public final class Hud {
    public static final Color RED = new Color(230, 40, 40), GOLD = new Color(255, 214, 40), GREEN = new Color(80, 230, 110), PANEL = new Color(8, 6, 14, 190);
    public final Rectangle[] slotRects = new Rectangle[8];
    public final Rectangle abandonRect = new Rectangle();
    public float scale = 1f;

    public static Font font(float size, boolean bold) { return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, Math.max(8, (int) size)); }

    public static void shadowText(Graphics2D g, String s, float x, float y, Color c) {
        g.setColor(new Color(0, 0, 0, 200));
        g.drawString(s, x + 2, y + 2);
        g.setColor(c);
        g.drawString(s, x, y);
    }

    public static void centerText(Graphics2D g, String s, float cx, float y, Color c) {
        FontMetrics fm = g.getFontMetrics();
        shadowText(g, s, cx - fm.stringWidth(s) / 2f, y, c);
    }

    public void draw(Graphics2D g, Game game, int W, int H, float time, boolean cursorFree) {
        final Player p = game.me;
        scale = H / 720f;
        float s = scale;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawOverlays(g, game, p, W, H, time);
        scope(g, p, W, H, s, time);
        keyHint(g, game, p, W, H, s, time);
        // ---------- panel de estado (arriba izquierda)
        int px = (int) (16 * s), py = (int) (14 * s), pw = (int) (330 * s), ph = (int) (172 * s);
        g.setColor(PANEL); g.fill(new RoundRectangle2D.Float(px, py, pw, ph, 18 * s, 18 * s));
        g.setColor(new Color(255, 90, 40, 160)); g.setStroke(new BasicStroke(2f * s)); g.draw(new RoundRectangle2D.Float(px, py, pw, ph, 18 * s, 18 * s));
        // vidas (Pac-Mans)
        float[] col = Palette.RGB[p.colorIndex];
        Color pc = new Color(col[0], col[1], col[2]);
        g.setFont(font(15 * s, true));
        shadowText(g, "VIDAS", px + 14 * s, py + 26 * s, Color.WHITE);
        for (int i = 0; i < Game.START_LIVES; i++) pacIcon(g, px + (92 + i * 34) * s, py + 19 * s, 12 * s, i < p.lives ? pc : new Color(60, 60, 70), time + i);
        // barra de vida
        float hpf = Math.max(0, p.hp / Game.MAX_HP);
        int bx = (int) (px + 14 * s), by = (int) (py + 42 * s), bw = (int) (pw - 28 * s), bh = (int) (24 * s);
        g.setColor(new Color(30, 10, 14)); g.fillRoundRect(bx, by, bw, bh, 10, 10);
        Color hc = hpf > 0.5f ? new Color(60, 220, 90) : hpf > 0.25f ? new Color(240, 190, 30) : new Color(235, 40, 40);
        if (p.immuneT > 0) hc = new Color(70, 160, 255);
        g.setPaint(new GradientPaint(bx, by, hc.brighter(), bx, by + bh, hc.darker()));
        g.fillRoundRect(bx, by, (int) (bw * hpf), bh, 10, 10);
        g.setColor(Color.WHITE); g.setStroke(new BasicStroke(1.5f)); g.drawRoundRect(bx, by, bw, bh, 10, 10);
        g.setFont(font(15 * s, true));
        centerText(g, "VIDA  " + (int) Math.max(0, p.hp) + " / " + Game.MAX_HP, bx + bw / 2f, by + 18 * s, Color.WHITE);
        // velocidad
        g.setFont(font(14 * s, true));
        shadowText(g, "VELOCIDAD", px + 14 * s, py + 90 * s, new Color(120, 220, 255));
        float sp = (p.speedPct() - 100) / 100f;
        int sx = (int) (px + 112 * s), sy = (int) (py + 76 * s), sw = (int) (150 * s), sh = (int) (14 * s);
        g.setColor(new Color(15, 25, 40)); g.fillRoundRect(sx, sy, sw, sh, 8, 8);
        g.setPaint(new GradientPaint(sx, sy, new Color(80, 200, 255), sx + sw, sy, new Color(255, 240, 80)));
        g.fillRoundRect(sx, sy, (int) (sw * Math.max(0.03f, sp)), sh, 8, 8);
        g.setColor(Color.WHITE); g.drawRoundRect(sx, sy, sw, sh, 8, 8);
        shadowText(g, (int) p.speedPct() + "%", sx + sw + 8 * s, sy + 12 * s, GOLD);
        // estrellas / llave
        g.setFont(font(15 * s, true));
        starIcon(g, px + 26 * s, py + 117 * s, 11 * s, time);
        shadowText(g, "ESTRELLAS " + p.wallet(), px + 44 * s, py + 123 * s, GOLD);
        g.setFont(font(14 * s, true));
        shadowText(g, "ALMAS: " + p.soulsSaved + "   VEL. MÁX " + (int) p.maxSpeedPct + "%", px + 14 * s, py + 150 * s, new Color(150, 220, 255));
        g.setFont(font(15 * s, true));
        shadowText(g, game.hasKey ? "LLAVE: SI" : "LLAVE: NO", px + 196 * s, py + 123 * s, game.hasKey ? GOLD : new Color(160, 160, 170));
        if (p.immuneT > 0) { g.setColor(new Color(90, 170, 255)); shadowText(g, "INMUNE " + (int) Math.ceil(p.immuneT) + "s", px + 226 * s, py + 150 * s, new Color(120, 190, 255)); }
        // ---------- cronometro y fantasmas (centro arriba)
        int secs = (int) Math.ceil(Math.max(0, game.timeLeft));
        String tm = String.format("%d:%02d", secs / 60, secs % 60);
        boolean low = secs <= 60;
        float pulse = low ? 0.6f + 0.4f * (float) Math.abs(Math.sin(time * 5)) : 1f;
        int tw = (int) (170 * s), th = (int) (58 * s);
        g.setColor(PANEL); g.fill(new RoundRectangle2D.Float(W / 2f - tw / 2f, 12 * s, tw, th, 16 * s, 16 * s));
        g.setFont(font(40 * s, true));
        centerText(g, tm, W / 2f, 56 * s, low ? new Color(255, (int) (70 * pulse), (int) (60 * pulse)) : Color.WHITE);
        g.setFont(font(13 * s, true));
        centerText(g, "TIEMPO", W / 2f, 26 * s, new Color(200, 200, 220));
        g.setFont(font(17 * s, true));
        centerText(g, "FANTASMAS: " + game.ghostsAlive + " / " + game.level.ghostCount + "    ELIMINADOS: " + p.killedLevel, W / 2f, 92 * s, new Color(190, 230, 255));
        g.setFont(font(15 * s, true));
        centerText(g, "NIVEL " + game.levelIdx + " / 3", W / 2f, 112 * s, new Color(255, 140, 70));
        // ---------- minimapa
        minimap(g, game, p, W, s, time);
        // ---------- inventario (abajo)
        inventory(g, game, p, W, H, s, time, cursorFree);
        // ---------- municion
        Weapon w = p.current();
        int ax = (int) (W - 270 * s), ay = (int) (H - 140 * s), aw = (int) (250 * s), ah = (int) (112 * s);
        g.setColor(PANEL); g.fill(new RoundRectangle2D.Float(ax, ay, aw, ah, 18 * s, 18 * s));
        g.setColor(new Color(255, 180, 50, 160)); g.draw(new RoundRectangle2D.Float(ax, ay, aw, ah, 18 * s, 18 * s));
        g.setFont(font(15 * s, true));
        shadowText(g, w == null ? "SIN ARMA" : w.label, ax + 14 * s, ay + 26 * s, Color.WHITE);
        g.setFont(font(13 * s, true));
        shadowText(g, "BALAS", ax + 14 * s, ay + 46 * s, new Color(255, 200, 100));
        g.setFont(font(46 * s, true));
        boolean empty = p.ammo <= 0;
        Color ac = empty ? new Color(255, 60, 60) : p.ammo < 12 ? new Color(255, 170, 50) : Color.WHITE;
        shadowText(g, String.valueOf(p.ammo), ax + 14 * s, ay + 96 * s, ac);
        if (w == Weapon.SWORD) { g.setFont(font(14 * s, true)); shadowText(g, "ESPADA: INFINITA", ax + 100 * s, ay + 96 * s, GREEN); }
        else if (w == Weapon.GRENADE) { g.setFont(font(14 * s, true)); shadowText(g, "5 BALAS / GRANADA", ax + 100 * s, ay + 96 * s, new Color(255, 190, 90)); }
        // ---------- mira
        int cx = W / 2, cy = H / 2;
        boolean scoped = w == Weapon.RIFLE && p.zoomT > 0.5f;
        g.setColor(new Color(255, 255, 255, scoped ? 0 : 190)); g.setStroke(new BasicStroke(2f * s));
        float gap = 7 * s + (w == Weapon.BOW ? (1 - p.bowDraw) * 10 * s : 0);
        g.draw(new Line2D.Float(cx - gap - 9 * s, cy, cx - gap, cy)); g.draw(new Line2D.Float(cx + gap, cy, cx + gap + 9 * s, cy));
        g.draw(new Line2D.Float(cx, cy - gap - 9 * s, cx, cy - gap)); g.draw(new Line2D.Float(cx, cy + gap, cx, cy + gap + 9 * s));
        g.fill(new Ellipse2D.Float(cx - 1.5f * s, cy - 1.5f * s, 3 * s, 3 * s));
        // ---------- mensajes
        g.setFont(font(16 * s, true));
        int my = (int) ((game.multi ? 262 : 214) * s);
        for (int i = 0; i < game.messages.size(); i++) {
            Game.Msg m = game.messages.get(i);
            float a = Math.min(1f, m.t / 1.0f);
            Color c = new Color((m.color >> 16) & 255, (m.color >> 8) & 255, m.color & 255, (int) (255 * a));
            g.setColor(new Color(0, 0, 0, (int) (150 * a)));
            int tw2 = g.getFontMetrics().stringWidth(m.text);
            g.fillRoundRect((int) (16 * s), my + i * (int) (26 * s) - (int) (16 * s), tw2 + (int) (20 * s), (int) (24 * s), 10, 10);
            g.setColor(c);
            g.drawString(m.text, 26 * s, my + i * 26 * s);
        }
        // ---------- indicadores
        g.setFont(font(22 * s, true));
        if (p.promptChest) centerText(g, "[E]  ABRIR COFRE", W / 2f, H / 2f + 80 * s, GOLD);
        if (p.promptShop) {
            float hue = (time * 0.4f) % 1f;
            Color rc = Color.getHSBColor(hue, 0.6f, 1f);
            centerText(g, "[E]  COFRE ARCOÍRIS: ARMA ALEATORIA POR " + p.shopPrice + " ESTRELLAS", W / 2f, H / 2f + 80 * s, rc);
            g.setFont(font(15 * s, true));
            centerText(g, "Tienes " + p.wallet() + " estrellas · las armas raras y poderosas salen más · puedes comprar otra vez", W / 2f, H / 2f + 104 * s, p.wallet() >= p.shopPrice ? new Color(150, 255, 160) : new Color(255, 130, 110));
            g.setFont(font(22 * s, true));
        }
        if (p.inTrap != 0 && p.state == Player.ALIVE) {
            float bl = 0.55f + 0.45f * (float) Math.abs(Math.sin(time * 9));
            g.setFont(font(34 * s, true));
            if (p.inTrap == 1) centerText(g, "¡ÁCIDO!  -10 de vida por segundo", W / 2f, H / 2f - 130 * s, new Color((int) (120 * bl), 255, (int) (90 * bl)));
            else centerText(g, "¡PÚAS!  -50 de vida por segundo", W / 2f, H / 2f - 130 * s, new Color(255, (int) (70 * bl), (int) (60 * bl)));
            g.setFont(font(22 * s, true));
        }
        if (p.promptDoor && !game.level.doorOpen) { g.setFont(font(20 * s, true)); centerText(g, "PUERTA CERRADA: busca la LLAVE o elimina a todos los fantasmas", W / 2f, H / 2f + 112 * s, new Color(255, 120, 90)); }
        if (p.dryBanner > 0 && p.ammo <= 0 && w != null && w != Weapon.SWORD) {
            g.setFont(font(40 * s, true));
            float a = Math.min(1, p.dryBanner);
            float bl = 0.6f + 0.4f * (float) Math.abs(Math.sin(time * 8));
            centerText(g, "¡SE ACABARON LAS BALAS!", W / 2f, H / 2f - 90 * s, new Color(255, (int) (50 * bl), (int) (50 * bl), (int) (255 * a)));
        } else if (p.dryBanner > 0 && w == Weapon.GRENADE && p.ammo < 5) {
            g.setFont(font(28 * s, true));
            centerText(g, "NECESITAS 5 BALAS PARA LANZAR", W / 2f, H / 2f - 90 * s, new Color(255, 120, 60));
        }
        if (game.bigT > 0) {
            float a = Math.min(1f, game.bigT / 0.8f);
            float sc = 1f + (1f - Math.min(1f, (3f - game.bigT) * 4f)) * 0.4f;
            g.setFont(font(64 * s, true));
            Color bc = new Color((game.bigColor >> 16) & 255, (game.bigColor >> 8) & 255, game.bigColor & 255, (int) (255 * a));
            centerText(g, game.bigMsg, W / 2f, H * 0.3f, bc);
        }
        // ---------- marcador de impacto
        if (p.hitMarker > 0) {
            g.setColor(new Color(255, 70, 70, (int) (255 * Math.min(1, p.hitMarker)))); g.setStroke(new BasicStroke(3f * s));
            float m = 12 * s, n = 22 * s;
            g.draw(new Line2D.Float(cx - n, cy - n, cx - m, cy - m)); g.draw(new Line2D.Float(cx + n, cy - n, cx + m, cy - m));
            g.draw(new Line2D.Float(cx - n, cy + n, cx - m, cy + m)); g.draw(new Line2D.Float(cx + n, cy + n, cx + m, cy + m));
        }
        // ---------- indicador de direccion del dano
        if (p.hurtFlash > 0.05f) {
            double a = Math.atan2(Math.sin(p.hurtDir - p.yaw), Math.cos(p.hurtDir - p.yaw));
            float rad = 150 * s;
            AffineTransform ot = g.getTransform();
            g.translate(cx, cy); g.rotate(a + Math.PI);
            g.setColor(new Color(255, 40, 40, (int) (230 * Math.min(1, p.hurtFlash * 1.6f))));
            g.fillPolygon(new int[]{0, (int) (-18 * s), (int) (18 * s)}, new int[]{(int) (-rad - 24 * s), (int) (-rad + 6 * s), (int) (-rad + 6 * s)}, 3);
            g.setTransform(ot);
        }
        // ---------- companero (multijugador)
        if (game.multi) {
            Player o = game.other();
            int tx = (int) (16 * s), ty = (int) (194 * s), tw2 = (int) (330 * s), th2 = (int) (46 * s);
            g.setColor(PANEL); g.fill(new RoundRectangle2D.Float(tx, ty, tw2, th2, 14 * s, 14 * s));
            float[] oc = Palette.RGB[o.colorIndex];
            pacIcon(g, tx + 24 * s, ty + 23 * s, 13 * s, new Color(oc[0], oc[1], oc[2]), time);
            g.setFont(font(13 * s, true));
            shadowText(g, "JUGADOR " + (o.id + 1), tx + 46 * s, ty + 18 * s, new Color(120, 220, 255));
            float of = Math.max(0, o.hp / Game.MAX_HP);
            g.setColor(new Color(30, 10, 14)); g.fillRoundRect((int) (tx + 46 * s), (int) (ty + 24 * s), (int) (180 * s), (int) (12 * s), 8, 8);
            g.setColor(o.state == Player.OUT ? new Color(90, 90, 90) : of > 0.4f ? new Color(60, 220, 90) : new Color(235, 60, 50));
            g.fillRoundRect((int) (tx + 46 * s), (int) (ty + 24 * s), (int) (180 * s * of), (int) (12 * s), 8, 8);
            for (int i = 0; i < Game.START_LIVES; i++) pacIcon(g, tx + (250 + i * 24) * s, ty + 23 * s, 8 * s, i < o.lives ? new Color(oc[0], oc[1], oc[2]) : new Color(60, 60, 70), time);
            if (o.state == Player.OUT) { g.setFont(font(12 * s, true)); shadowText(g, "ELIMINADO", tx + 46 * s, ty + 18 * s, new Color(255, 100, 100)); }
        }
        if (p.state == Player.OUT) {
            g.setFont(font(34 * s, true));
            centerText(g, "ELIMINADO - mirando a tu compañero", W / 2f, H * 0.72f, new Color(255, 120, 100));
        }
        g.setFont(font(12 * s, true));
        shadowText(g, (p.tpv ? "V: primera persona" : "V: tercera persona") + "  ·  CLIC DERECHO: zoom", 16 * s, H - 28 * s, new Color(190, 220, 255, 200));
        // ---------- susto (por encima de todo)
        jumpScare(g, game.jumpT, W, H, time);
        // ---------- boton abandonar
        int abw = (int) (200 * s), abh = (int) (34 * s);
        abandonRect.setBounds((int) (W - abw - 16 * s), (int) (H - 190 * s), abw, abh);
        g.setColor(cursorFree ? new Color(180, 30, 30, 220) : new Color(40, 20, 20, 120));
        g.fillRoundRect(abandonRect.x, abandonRect.y, abw, abh, 12, 12);
        g.setColor(new Color(255, 255, 255, cursorFree ? 255 : 120)); g.setFont(font(14 * s, true));
        centerText(g, "ABANDONAR PARTIDA", abandonRect.x + abw / 2f, abandonRect.y + 22 * s, new Color(255, 255, 255, cursorFree ? 255 : 130));
        g.setFont(font(12 * s, false));
        shadowText(g, "ESC pausa  ·  TAB ratón libre (clic en inventario)", 16 * s, H - 10 * s, new Color(200, 200, 215, 170));
    }

    private final java.util.Random jr = new java.util.Random();

    /** Susto animado: la pantalla se oscurece y parpadea y un fantasma se lanza hacia la camara (1,9 s). */
    private void jumpScare(Graphics2D g, float t, int W, int H, float time) {
        if (t <= 0) return;
        float dark = t < 0.3f ? t / 0.3f : (t > 1.55f ? Math.max(0f, (1.9f - t) / 0.35f) : 1f);
        // parpadeo de la oscuridad mientras entra
        float fl = t < 0.45f ? (((int) (t * 30)) % 2 == 0 ? 0.55f : 1f) : 1f;
        g.setColor(new Color(0, 0, 0, (int) (Math.min(1f, dark * fl) * 238)));
        g.fillRect(0, 0, W, H);
        if (t > 0.28f && t < 1.62f) {
            float u = Math.min(1f, (t - 0.28f) / 0.75f);
            float ease = 1f - (1f - u) * (1f - u);
            float scale = 0.30f + 1.25f * ease + 0.05f * (float) Math.sin(t * 40);
            boolean visible = ((int) (t * 26)) % 6 != 0 || t > 1.2f;   // parpadea como si fallara la luz
            float sx = (jr.nextFloat() - 0.5f) * 26f * ease, sy = (jr.nextFloat() - 0.5f) * 20f * ease;
            if (visible) drawScaryFace(g, W / 2f + sx, H * 0.52f + sy, H * 0.55f * scale, t);
        }
        // destellos rojos y estatica
        if ((t > 0.27f && t < 0.38f) || (t > 0.95f && t < 1.02f)) { g.setColor(new Color(200, 0, 0, 110)); g.fillRect(0, 0, W, H); }
        g.setColor(new Color(255, 255, 255, (int) (50 * dark)));
        for (int i = 0; i < 26; i++) g.fillRect(0, jr.nextInt(H), W, 1 + jr.nextInt(2));
        g.setPaint(new RadialGradientPaint(W / 2f, H / 2f, Math.max(W, H) * 0.62f, new float[]{0.45f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, (int) (230 * dark))}));
        g.fillRect(0, 0, W, H);
    }

    private void drawScaryFace(Graphics2D g, float cx, float cy, float R, float t) {
        AffineTransform ot = g.getTransform();
        g.translate(cx, cy);
        g.rotate(Math.sin(t * 17) * 0.04);
        // resplandor rojo detras
        g.setPaint(new RadialGradientPaint(0, 0, R * 1.5f, new float[]{0f, 1f}, new Color[]{new Color(200, 20, 10, 150), new Color(120, 0, 0, 0)}));
        g.fill(new Ellipse2D.Float(-R * 1.5f, -R * 1.5f, R * 3f, R * 3f));
        // sabana: cupula y cuerpo con borde desgarrado
        Path2D.Float body = new Path2D.Float();
        body.moveTo(-R * 0.95f, R * 1.7f);
        body.curveTo(-R * 1.05f, -R * 0.2f, -R * 0.8f, -R * 1.05f, 0, -R * 1.05f);
        body.curveTo(R * 0.8f, -R * 1.05f, R * 1.05f, -R * 0.2f, R * 0.95f, R * 1.7f);
        body.closePath();
        g.setPaint(new GradientPaint(0, -R, new Color(225, 232, 245), 0, R * 1.5f, new Color(95, 100, 125)));
        g.fill(body);
        g.setColor(new Color(20, 20, 30, 200)); g.setStroke(new BasicStroke(R * 0.03f)); g.draw(body);
        // pliegues
        g.setColor(new Color(60, 65, 90, 90)); g.setStroke(new BasicStroke(R * 0.02f));
        for (int i = -3; i <= 3; i++) g.draw(new Line2D.Float(i * R * 0.27f, -R * 0.9f + Math.abs(i) * R * 0.15f, i * R * 0.36f, R * 1.6f));
        // cuencas de los ojos
        float pulse = 0.8f + 0.2f * (float) Math.sin(t * 22);
        for (int s2 = -1; s2 <= 1; s2 += 2) {
            AffineTransform o2 = g.getTransform();
            g.translate(s2 * R * 0.36f, -R * 0.22f); g.rotate(s2 * -0.28);
            g.setPaint(new RadialGradientPaint(0, 0, R * 0.3f, new float[]{0f, 0.7f, 1f}, new Color[]{new Color(0, 0, 0), new Color(8, 0, 6), new Color(40, 20, 30, 0)}));
            g.fill(new Ellipse2D.Float(-R * 0.2f, -R * 0.34f, R * 0.4f, R * 0.68f));
            g.setPaint(new RadialGradientPaint(0, R * 0.04f, R * 0.12f, new float[]{0f, 0.35f, 1f}, new Color[]{new Color(255, 245, 220), new Color(255, 40, 20, (int) (255 * pulse)), new Color(255, 0, 0, 0)}));
            g.fill(new Ellipse2D.Float(-R * 0.12f, -R * 0.08f, R * 0.24f, R * 0.24f));
            g.setTransform(o2);
        }
        // nariz
        g.setColor(new Color(15, 10, 14, 210));
        g.fill(new Ellipse2D.Float(-R * 0.05f, R * 0.12f, R * 0.04f, R * 0.12f)); g.fill(new Ellipse2D.Float(R * 0.01f, R * 0.12f, R * 0.04f, R * 0.12f));
        // boca gritando
        float open = 0.82f + 0.22f * (float) Math.sin(t * 19);
        float mw = R * 0.5f, mh = R * 0.7f * open, my = R * 0.62f;
        g.setPaint(new RadialGradientPaint(0, my, mh, new float[]{0f, 0.6f, 1f}, new Color[]{new Color(60, 0, 0), new Color(10, 0, 0), new Color(0, 0, 0)}));
        g.fill(new Ellipse2D.Float(-mw, my - mh * 0.5f, mw * 2, mh));
        // dientes irregulares
        g.setColor(new Color(235, 225, 195));
        for (int i = -4; i <= 4; i++) {
            float x = i * mw * 0.2f, tipH = mh * (0.12f + 0.08f * ((i * 7) & 3) / 3f), top = my - mh * 0.5f + (float) Math.abs(i) * mh * 0.018f;
            g.fillPolygon(new int[]{(int) (x - mw * 0.09f), (int) (x + mw * 0.09f), (int) x}, new int[]{(int) top, (int) top, (int) (top + tipH)}, 3);
            float bot = my + mh * 0.5f - (float) Math.abs(i) * mh * 0.018f;
            g.fillPolygon(new int[]{(int) (x - mw * 0.09f), (int) (x + mw * 0.09f), (int) x}, new int[]{(int) bot, (int) bot, (int) (bot - tipH * 0.9f)}, 3);
        }
        // hilos oscuros que gotean
        g.setColor(new Color(10, 5, 8, 190)); g.setStroke(new BasicStroke(R * 0.025f));
        for (int i = -2; i <= 2; i++) g.draw(new Line2D.Float(i * mw * 0.35f, my + mh * 0.5f, i * mw * 0.35f + (float) Math.sin(t * 9 + i) * R * 0.03f, my + mh * 0.5f + R * (0.25f + 0.1f * (i & 1))));
        g.setTransform(ot);
    }

    /** Zoom real al apuntar: mira telescopica para el rifle y mira de hierro con vineta para el resto. */
    private void scope(Graphics2D g, Player p, int W, int H, float s, float time) {
        Weapon w = p.current();
        if (p.zoomT < 0.03f || w == null || w.zoom <= 1.01f) return;
        float z = p.zoomT;
        int cx = W / 2, cy = H / 2;
        if (w == Weapon.RIFLE) {
            float rad = H * (0.62f - 0.20f * z);
            if (z > 0.35f) {
                java.awt.geom.Area a = new java.awt.geom.Area(new Rectangle(0, 0, W, H));
                a.subtract(new java.awt.geom.Area(new Ellipse2D.Float(cx - rad, cy - rad, rad * 2, rad * 2)));
                g.setColor(new Color(0, 0, 0, (int) (255 * Math.min(1f, (z - 0.35f) * 3f))));
                g.fill(a);
                g.setStroke(new BasicStroke(4f * s)); g.setColor(new Color(15, 15, 18, (int) (255 * z)));
                g.draw(new Ellipse2D.Float(cx - rad, cy - rad, rad * 2, rad * 2));
                float a2 = Math.min(1f, (z - 0.4f) * 2f);
                g.setColor(new Color(0, 0, 0, (int) (235 * a2))); g.setStroke(new BasicStroke(1.6f * s));
                g.draw(new Line2D.Float(cx - rad, cy, cx - 26 * s, cy)); g.draw(new Line2D.Float(cx + 26 * s, cy, cx + rad, cy));
                g.draw(new Line2D.Float(cx, cy - rad, cx, cy - 26 * s)); g.draw(new Line2D.Float(cx, cy + 26 * s, cx, cy + rad));
                g.setStroke(new BasicStroke(5f * s));
                g.draw(new Line2D.Float(cx - rad, cy, cx - rad * 0.45f, cy)); g.draw(new Line2D.Float(cx + rad * 0.45f, cy, cx + rad, cy));
                g.draw(new Line2D.Float(cx, cy - rad, cx, cy - rad * 0.45f)); g.draw(new Line2D.Float(cx, cy + rad * 0.45f, cx, cy + rad));
                g.setColor(new Color(255, 40, 40, (int) (230 * a2))); g.fill(new Ellipse2D.Float(cx - 2 * s, cy - 2 * s, 4 * s, 4 * s));
                g.setColor(new Color(0, 0, 0, (int) (220 * a2)));
                for (int i = 1; i <= 4; i++) { g.fill(new Ellipse2D.Float(cx + i * 22 * s - 2 * s, cy - 2 * s, 4 * s, 4 * s)); g.fill(new Ellipse2D.Float(cx - i * 22 * s - 2 * s, cy - 2 * s, 4 * s, 4 * s)); g.fill(new Ellipse2D.Float(cx - 2 * s, cy + i * 22 * s - 2 * s, 4 * s, 4 * s)); }
                g.setFont(font(14 * s, true));
                shadowText(g, "MIRA x" + String.format("%.1f", w.zoom), cx + rad * 0.55f, cy + rad * 0.75f, new Color(200, 255, 200, (int) (255 * a2)));
            }
        } else {
            g.setPaint(new RadialGradientPaint(cx, cy, Math.max(W, H) * 0.62f, new float[]{0.35f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, (int) (170 * z))}));
            g.fillRect(0, 0, W, H);
            g.setColor(new Color(255, 255, 255, (int) (230 * z))); g.setStroke(new BasicStroke(2f * s));
            float t = 5 * s;
            g.draw(new Line2D.Float(cx - 16 * s, cy, cx - t, cy)); g.draw(new Line2D.Float(cx + t, cy, cx + 16 * s, cy)); g.draw(new Line2D.Float(cx, cy + t, cx, cy + 16 * s));
            g.setColor(new Color(255, 50, 50, (int) (255 * z))); g.fill(new Ellipse2D.Float(cx - 2 * s, cy - 2 * s, 4 * s, 4 * s));
            g.setFont(font(13 * s, true));
            shadowText(g, "ZOOM x" + String.format("%.1f", w.zoom), cx + 40 * s, cy + 50 * s, new Color(200, 255, 200, (int) (230 * z)));
        }
    }

    /** Aura dorada que senala hacia donde esta la llave (dura 2 s tras salvar un alma). */
    private void keyHint(Graphics2D g, Game game, Player p, int W, int H, float s, float time) {
        if (p.keyHintT <= 0) return;
        float a = Math.min(1f, p.keyHintT * 2f) * (0.8f + 0.2f * (float) Math.sin(time * 14));
        double dx = p.keyHintX - p.px, dz = p.keyHintZ - p.pz;
        double rel = Math.atan2(dx, dz) - p.yaw;
        while (rel > Math.PI) rel -= 2 * Math.PI;
        while (rel < -Math.PI) rel += 2 * Math.PI;
        int cx = W / 2, cy = H / 2;
        // resplandor en el borde de la pantalla del lado de la llave
        boolean right = rel > 0;
        int gw = (int) (230 * s);
        if (Math.abs(rel) > 0.55) {
            Color c1 = new Color(255, 210, 60, (int) (170 * a)), c0 = new Color(255, 210, 60, 0);
            if (right) g.setPaint(new GradientPaint(W, 0, c1, W - gw, 0, c0)); else g.setPaint(new GradientPaint(0, 0, c1, gw, 0, c0));
            g.fillRect(right ? W - gw : 0, 0, gw, H);
        }
        // flecha dorada giratoria alrededor de la mira
        AffineTransform ot = g.getTransform();
        g.translate(cx, cy); g.rotate(rel);
        float rad = 170 * s;
        g.setColor(new Color(255, 225, 70, (int) (240 * a)));
        g.fillPolygon(new int[]{0, (int) (-24 * s), (int) (24 * s)}, new int[]{(int) (-rad - 40 * s), (int) (-rad + 8 * s), (int) (-rad + 8 * s)}, 3);
        g.setStroke(new BasicStroke(6f * s)); g.draw(new Line2D.Float(0, -rad + 8 * s, 0, -rad + 60 * s));
        g.setTransform(ot);
        // aura sobre la direccion exacta cuando esta dentro del campo de vision
        if (Math.abs(rel) < 0.9) {
            float focal = H * 0.5f / (float) Math.tan(Math.toRadians(34));
            float x = cx + (float) Math.tan(rel) * focal;
            float r = 150 * s;
            g.setPaint(new RadialGradientPaint(x, H * 0.42f, r, new float[]{0f, 1f}, new Color[]{new Color(255, 225, 90, (int) (210 * a)), new Color(255, 190, 40, 0)}));
            g.fillRect((int) (x - r), (int) (H * 0.42f - r), (int) (2 * r), (int) (2 * r));
            g.setPaint(new GradientPaint(x, 0, new Color(255, 235, 120, (int) (130 * a)), x, H, new Color(255, 200, 60, (int) (20 * a))));
            g.fillRect((int) (x - 14 * s), 0, (int) (28 * s), H);
        }
        g.setFont(font(30 * s, true));
        int m = (int) Math.hypot(dx, dz);
        centerText(g, "¡LA LLAVE ESTÁ AL " + Game.cardinal(p.px, p.pz, p.keyHintX, p.keyHintZ) + "!  (" + m + " m)", W / 2f, H * 0.19f, new Color(255, 230, 90, (int) (255 * a)));
    }

    private void drawOverlays(Graphics2D g, Game game, Player p, int W, int H, float time) {
        if (p.hurtFlash > 0) {
            float a = Math.min(0.65f, p.hurtFlash * 0.75f);
            g.setPaint(new RadialGradientPaint(W / 2f, H / 2f, Math.max(W, H) * 0.65f, new float[]{0.35f, 1f}, new Color[]{new Color(180, 0, 0, 0), new Color(200, 10, 10, (int) (255 * a))}));
            g.fillRect(0, 0, W, H);
        }
        if (p.hp < 150 && p.state == Player.ALIVE) {
            float a = 0.18f + 0.12f * (float) Math.sin(time * 6);
            g.setPaint(new RadialGradientPaint(W / 2f, H / 2f, Math.max(W, H) * 0.7f, new float[]{0.5f, 1f}, new Color[]{new Color(120, 0, 0, 0), new Color(140, 0, 0, (int) (255 * a))}));
            g.fillRect(0, 0, W, H);
        }
        if (p.immuneT > 0) {
            g.setColor(new Color(60, 140, 255, (int) (28 + 14 * Math.sin(time * 6))));
            g.fillRect(0, 0, W, H);
            g.setPaint(new RadialGradientPaint(W / 2f, H / 2f, Math.max(W, H) * 0.6f, new float[]{0.55f, 1f}, new Color[]{new Color(60, 140, 255, 0), new Color(80, 160, 255, 90)}));
            g.fillRect(0, 0, W, H);
        }
        if (p.pickFlash > 0) { g.setColor(new Color(255, 230, 140, (int) (70 * Math.min(1, p.pickFlash)))); g.fillRect(0, 0, W, H); }
        if (p.state == Player.DYING) {
            float a = Math.min(1f, p.deathT / 2f);
            g.setColor(new Color(60, 0, 0, (int) (200 * a))); g.fillRect(0, 0, W, H);
        }
    }

    private void minimap(Graphics2D g, Game game, Player p, int W, float s, float time) {
        int size = (int) (150 * s), mx = (int) (W - size - 16 * s), my = (int) (14 * s);
        g.setColor(new Color(6, 5, 12, 200)); g.fillRoundRect(mx, my, size, size, 14, 14);
        Shape old = g.getClip();
        g.setClip(new RoundRectangle2D.Float(mx, my, size, size, 14, 14));
        com.pacman3d.world.Maze mz = game.level.maze;
        int R = 14;
        int cx = Level.tileOf(p.px), cz = Level.tileOf(p.pz);
        float cell = size / (float) (R * 2 + 1);
        for (int x = cx - R; x <= cx + R; x++)
            for (int z = cz - R; z <= cz + R; z++) {
                if (x < 0 || z < 0 || x >= mz.W || z >= mz.H || !game.seen[x][z]) continue;
                float rx = mx + (x - (cx - R)) * cell, ry = my + (z - (cz - R)) * cell;
                boolean solid = mz.solid[x][z];
                g.setColor(solid ? new Color(70, 40, 40, 230) : new Color(150, 130, 190, 200));
                if (x == mz.doorX && z == mz.doorZ) g.setColor(game.level.doorOpen ? new Color(80, 255, 120) : new Color(255, 70, 40));
                g.fill(new Rectangle2D.Float(rx, ry, cell + 0.5f, cell + 0.5f));
            }
        for (float[] sp : game.level.shopSpots) {
            int x = Level.tileOf(sp[0]), z = Level.tileOf(sp[1]);
            if (x < 0 || z < 0 || x >= mz.W || z >= mz.H || !game.seen[x][z]) continue;
            float rx = mx + (x - (cx - R) + 0.5f) * cell, ry = my + (z - (cz - R) + 0.5f) * cell;
            g.setColor(Color.getHSBColor((time * 0.5f) % 1f, 0.7f, 1f)); g.fill(new Rectangle2D.Float(rx - 3.5f * s, ry - 3.5f * s, 7 * s, 7 * s));
        }
        for (float[] sp : game.level.acidSpots) { int x = Level.tileOf(sp[0]), z = Level.tileOf(sp[1]); if (x < 0 || z < 0 || x >= mz.W || z >= mz.H || !game.seen[x][z]) continue; g.setColor(new Color(90, 255, 90)); g.fill(new Rectangle2D.Float(mx + (x - (cx - R) + 0.2f) * cell, my + (z - (cz - R) + 0.2f) * cell, cell * 0.6f, cell * 0.6f)); }
        for (float[] sp : game.level.spikeSpots) { int x = Level.tileOf(sp[0]), z = Level.tileOf(sp[1]); if (x < 0 || z < 0 || x >= mz.W || z >= mz.H || !game.seen[x][z]) continue; g.setColor(new Color(255, 70, 60)); g.fill(new Rectangle2D.Float(mx + (x - (cx - R) + 0.2f) * cell, my + (z - (cz - R) + 0.2f) * cell, cell * 0.6f, cell * 0.6f)); }
        for (Soul so : game.souls) {
            if (so.state != Soul.FLEE) continue;
            int x = Level.tileOf(so.x), z = Level.tileOf(so.z);
            if (x < 0 || z < 0 || x >= mz.W || z >= mz.H || !game.seen[x][z]) continue;
            g.setColor(new Color(130, 200, 255)); g.fill(new Ellipse2D.Float(mx + (so.x / Level.TILE - (cx - R)) * cell - 2f, my + (so.z / Level.TILE - (cz - R)) * cell - 2f, 4f, 4f));
        }
        for (Chest c : game.chests) {
            int x = Level.tileOf(c.x), z = Level.tileOf(c.z);
            if (x < 0 || z < 0 || x >= mz.W || z >= mz.H || !game.seen[x][z] || c.open) continue;
            float rx = mx + (x - (cx - R) + 0.5f) * cell, ry = my + (z - (cz - R) + 0.5f) * cell;
            g.setColor(GOLD); g.fill(new Rectangle2D.Float(rx - 2, ry - 2, 4, 4));
        }
        if (game.multi) {
            Player o = game.other();
            float ox = mx + (o.px / Level.TILE - (cx - R) ) * cell, oy = my + (o.pz / Level.TILE - (cz - R)) * cell;
            g.setColor(new Color(255, 160, 40)); g.fill(new Ellipse2D.Float(ox - 3.5f * s, oy - 3.5f * s, 7 * s, 7 * s));
        }
        float ppx = mx + size / 2f, ppy = my + size / 2f;
        g.setColor(Color.WHITE);
        double ang = p.yaw;
        Path2D.Float tri = new Path2D.Float();
        tri.moveTo(ppx + Math.sin(ang) * 7 * s, ppy + Math.cos(ang) * 7 * s);
        tri.lineTo(ppx + Math.sin(ang + 2.5) * 5 * s, ppy + Math.cos(ang + 2.5) * 5 * s);
        tri.lineTo(ppx + Math.sin(ang - 2.5) * 5 * s, ppy + Math.cos(ang - 2.5) * 5 * s);
        tri.closePath();
        g.fill(tri);
        // flecha hacia la puerta cuando ya puedes salir
        if (game.hasKey || game.level.doorOpen) {
            double dx = Level.center(mz.doorX) - p.px, dz = Level.center(mz.doorZ) - p.pz;
            double a = Math.atan2(dx, dz);
            g.setColor(new Color(90, 255, 130));
            g.setStroke(new BasicStroke(3f * s));
            g.draw(new Line2D.Double(ppx + Math.sin(a) * 20 * s, ppy + Math.cos(a) * 20 * s, ppx + Math.sin(a) * 38 * s, ppy + Math.cos(a) * 38 * s));
            g.fill(new Ellipse2D.Double(ppx + Math.sin(a) * 40 * s - 4, ppy + Math.cos(a) * 40 * s - 4, 8, 8));
        }
        g.setClip(old);
        g.setColor(new Color(255, 100, 50, 190)); g.setStroke(new BasicStroke(2f)); g.drawRoundRect(mx, my, size, size, 14, 14);
        g.setFont(font(11 * s, true));
        shadowText(g, "MAPA", mx + 8 * s, my + size - 6 * s, new Color(200, 200, 220));
    }

    private void inventory(Graphics2D g, Game game, Player p, int W, int H, float s, float time, boolean free) {
        int sz = (int) (62 * s), gap = (int) (8 * s);
        int total = 8 * sz + 7 * gap;
        int x0 = (W - total) / 2, y0 = H - sz - (int) (26 * s);
        for (int i = 0; i < 8; i++) {
            int x = x0 + i * (sz + gap);
            slotRects[i] = new Rectangle(x, y0, sz, sz);
            boolean sel = i == p.sel;
            g.setColor(sel ? new Color(70, 24, 12, 230) : PANEL);
            g.fillRoundRect(x, y0, sz, sz, 14, 14);
            g.setStroke(new BasicStroke(sel ? 3.5f * s : 1.5f * s));
            g.setColor(sel ? new Color(255, 190, 40) : new Color(120, 100, 140, 160));
            g.drawRoundRect(x, y0, sz, sz, 14, 14);
            if (sel) { g.setColor(new Color(255, 190, 40, (int) (50 + 30 * Math.sin(time * 6)))); g.fillRoundRect(x, y0, sz, sz, 14, 14); }
            Weapon w = p.inv[i];
            if (w != null) weaponIcon(g, w, x + sz / 2f, y0 + sz / 2f - 3 * s, sz * 0.72f);
            g.setFont(font(13 * s, true));
            shadowText(g, String.valueOf(i + 1), x + 6 * s, y0 + 15 * s, sel ? GOLD : new Color(200, 200, 220));
            if (w != null) { g.setFont(font(9 * s, true)); centerText(g, w.label, x + sz / 2f, y0 + sz - 5 * s, new Color(230, 230, 240)); }
        }
    }

    public static void weaponIcon(Graphics2D g, Weapon w, float cx, float cy, float size) {
        AffineTransform old = g.getTransform();
        g.translate(cx, cy);
        float k = size / 60f;
        g.scale(k, k);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (w) {
            case PISTOL:
                g.setColor(new Color(190, 195, 210)); g.fillRoundRect(-22, -12, 40, 13, 4, 4);
                g.setColor(new Color(70, 72, 82)); g.fillRoundRect(-20, 0, 14, 26, 4, 4); g.fillRect(14, -10, 10, 5);
                g.setColor(new Color(255, 200, 80)); g.fillRect(-2, -2, 8, 3);
                break;
            case RIFLE:
                g.setColor(new Color(140, 90, 40)); g.fillRoundRect(-30, -4, 18, 14, 4, 4);
                g.setColor(new Color(60, 62, 70)); g.fillRoundRect(-14, -9, 36, 12, 3, 3); g.fillRect(20, -6, 18, 5);
                g.fillRoundRect(-4, 2, 9, 20, 3, 3);
                g.setColor(new Color(110, 70, 30)); g.fillRoundRect(8, 1, 14, 9, 3, 3);
                break;
            case BOW:
                g.setColor(new Color(170, 110, 50)); g.draw(new Arc2D.Float(-18, -28, 36, 56, -90, 180, Arc2D.OPEN));
                g.setColor(new Color(235, 235, 225)); g.setStroke(new BasicStroke(1.5f)); g.draw(new Line2D.Float(0, -28, 0, 28));
                g.setColor(new Color(200, 200, 210)); g.setStroke(new BasicStroke(2.5f)); g.draw(new Line2D.Float(-20, 0, 24, 0)); g.fillPolygon(new int[]{24, 18, 18}, new int[]{0, -4, 4}, 3);
                break;
            case MINIGUN:
                g.setColor(new Color(70, 72, 82)); g.fillRoundRect(-26, -10, 22, 20, 5, 5);
                g.setColor(new Color(150, 154, 168));
                for (int i = -1; i <= 1; i++) g.fillRect(-4, i * 7 - 2, 34, 5);
                g.setColor(new Color(220, 170, 60)); g.fillRect(-20, 10, 14, 10);
                break;
            case SWORD:
                g.rotate(-0.78);
                g.setColor(new Color(215, 225, 245)); g.fillPolygon(new int[]{-4, 4, 4, 0, -4}, new int[]{-32, -32, 10, 18, 10}, 5);
                g.setColor(new Color(240, 190, 60)); g.fillRect(-12, 10, 24, 5);
                g.setColor(new Color(110, 55, 40)); g.fillRect(-3, 15, 6, 14);
                g.setColor(new Color(240, 190, 60)); g.fillOval(-5, 28, 10, 8);
                break;
            case GRENADE:
                g.setColor(new Color(70, 100, 50)); g.fillOval(-16, -12, 32, 36);
                g.setColor(new Color(40, 60, 30)); g.drawLine(-14, 4, 14, 4); g.drawLine(-12, 12, 12, 12);
                g.setColor(new Color(160, 160, 170)); g.fillRect(-5, -20, 10, 9);
                g.setColor(new Color(240, 220, 90)); g.draw(new Ellipse2D.Float(2, -30, 14, 12));
                break;
        }
        g.setTransform(old);
    }

    public static void pacIcon(Graphics2D g, float cx, float cy, float r, Color c, float time) {
        float m = 8 + 30 * (float) Math.abs(Math.sin(time * 6));
        g.setColor(c);
        g.fill(new Arc2D.Float(cx - r, cy - r, r * 2, r * 2, m, 360 - 2 * m, Arc2D.PIE));
        g.setColor(Color.BLACK);
        g.fill(new Ellipse2D.Float(cx - r * 0.1f, cy - r * 0.65f, r * 0.28f, r * 0.28f));
    }

    public static void starIcon(Graphics2D g, float cx, float cy, float r, float time) {
        Path2D.Float p = new Path2D.Float();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5 + Math.sin(time * 2) * 0.1;
            double rr = i % 2 == 0 ? r : r * 0.45;
            float x = (float) (cx + Math.cos(a) * rr), y = (float) (cy + Math.sin(a) * rr);
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
        g.setColor(new Color(255, 215, 40)); g.fill(p);
        g.setColor(new Color(255, 140, 20)); g.setStroke(new BasicStroke(1.5f)); g.draw(p);
    }
}
