package com.pacman3d.ui;

import com.pacman3d.audio.Audio;
import com.pacman3d.game.Game;
import com.pacman3d.game.Net;
import com.pacman3d.game.Player;
import com.pacman3d.game.Palette;
import com.pacman3d.game.Weapon;
import com.pacman3d.gfx.Models;
import com.pacman3d.gfx.Renderer;
import com.pacman3d.gfx.Textures;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Ventana, bucle principal, menus (inicio, tienda, comandos, creditos), pausa y pantallas de resultado. */
public final class App {
    enum S { MENU, STORE, CONTROLS, CREDITS, LEVELS, MODE, MPMENU, HOST, JOIN, CLIENTWAIT, LOADING, PLAY, PAUSE, LEVEL_DONE, OVER, FINAL }

    private static final int[][] RES = {{640, 360}, {800, 450}, {960, 540}, {1280, 720}};
    private int resLevel = 2;
    private JFrame frame;
    private Canvas canvas;
    private final Input in = new Input();
    private final Save save = new Save();
    private final Hud hud = new Hud();
    private final SceneRenderer scene = new SceneRenderer();
    private final Map<String, BufferedImage> images = new HashMap<>();
    private final Map<String, int[]> bgCache = new HashMap<>();
    private final Random rnd = new Random();
    private Renderer rend;
    private BufferedImage fb;
    private Game game;
    private S state = S.MENU;
    private Robot robot;
    private boolean tabFree, running = true, fullscreen;
    private float time, ema = 16f, lowTimer;
    private int loadFrames, pendingLevel = 1, creditedStars;
    private boolean newRun = true;
    private Cursor blank;
    private Audio.Voice drone;
    private BufferedImage vignette, logo;
    private int vigW, vigH;

    // ---- UI
    private static final class Btn { String text; Rectangle r = new Rectangle(); Runnable act; float hv; boolean enabled = true; }
    private final List<Btn> buttons = new ArrayList<>();
    private static final class Fx { float x, y, t; }
    private final List<Fx> fx = new ArrayList<>();
    private float[][] embers = new float[70][5];
    private float lightningT = 3, lightningA;
    private String toast = ""; private float toastT;
    private int hoverColor = -1;
    // ---- multijugador
    private int selLevel = 1;
    private Net net;
    private java.net.ServerSocket server;
    private volatile Net pendingNet;
    private volatile String netStatus = "";
    private volatile boolean clientReady, startFlag;
    private volatile int clientColor;
    private String ipText = "";
    private boolean startMulti, overlay;
    private float snapT;
    private int[] mpColors = {0, 1};
    private long mpSeed;
    private final Input idle = new Input();
    private final Rectangle[] swatches = new Rectangle[Palette.NAMES.length];

    // ===================================================================== arranque
    /** Carga de recursos comun (tambien usada por las pruebas sin ventana). */
    public void init(boolean withAudio) {
        Models.init();
        if (withAudio) Audio.init();
        for (float[] e : embers) respawnEmber(e, true);
        int cores = Runtime.getRuntime().availableProcessors();
        resLevel = cores >= 3 ? 2 : 1;
        scene.fancy = cores >= 4;
        setResolution(resLevel);
        try { logo = ImageIO.read(App.class.getResourceAsStream("/assets/images/logo_ue.png")); } catch (Exception e) { System.err.println("Logo no encontrado"); }
        for (String n : new String[]{"menu", "controls", "store", "results", "levels", "credits"}) {
            try { images.put(n, ImageIO.read(App.class.getResourceAsStream("/assets/images/" + n + ".jpg"))); } catch (Exception e) { System.err.println("Imagen no encontrada: " + n); }
        }
    }

    /** Dibuja un fotograma completo en un Graphics2D cualquiera (pruebas). */
    public void debugFrame(Graphics2D g, int W, int H, float dt, String cmd) {
        time += dt;
        if ("play".equals(cmd) && state == S.MENU) { pendingLevel = Integer.parseInt(System.getProperty("lvl", "1")); beginLoad(true); }
        if ("play2".equals(cmd) && state == S.MENU) { game = new Game(Integer.parseInt(System.getProperty("lvl", "1")), new int[]{0, 1}, 42L, 0, false); state = S.PLAY; }
        if ("tpv".equals(cmd) && game != null) game.me.tpv = true;
        if ("levels".equals(cmd)) state = S.LEVELS;
        if ("mode".equals(cmd)) { selLevel = 2; state = S.MODE; }
        if ("mpmenu".equals(cmd)) state = S.MPMENU;
        if ("hostscr".equals(cmd)) state = S.HOST;
        if ("joinscr".equals(cmd)) { ipText = "192.168.1.20"; state = S.JOIN; }
        if ("pause".equals(cmd) && state == S.PLAY) state = S.PAUSE;
        if ("store".equals(cmd)) state = S.STORE;
        if ("controls".equals(cmd)) state = S.CONTROLS;
        if ("credits".equals(cmd)) state = S.CREDITS;
        if ("done".equals(cmd) && state == S.PLAY) { game.state = Game.LEVEL_DONE; }
        if ("over".equals(cmd) && state == S.PLAY) { game.state = Game.GAME_OVER; game.overReason = "Los fantasmas te atraparon"; }
        update(dt, W, H);
        draw(g, W, H, dt);
    }

    public Game debugGame() { return game; }
    public Input debugInput() { return in; }

    public void start() {
        try { SwingUtilities.invokeAndWait(this::buildWindow); } catch (Exception e) { throw new RuntimeException(e); }
        Audio.musicPlay("/assets/music/menu_theme.wav", 0.8f);
        loop();
    }

    private void buildWindow() {
        init(true);
        frame = new JFrame("PACMAN 3D - Ya no está aquí");
        canvas = new Canvas();
        try { robot = new Robot(); } catch (Exception ignored) { }
        blank = Toolkit.getDefaultToolkit().createCustomCursor(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "blank");
        canvas.setPreferredSize(new Dimension(1280, 720));
        canvas.setFocusable(true);
        canvas.setFocusTraversalKeysEnabled(false);
        canvas.setIgnoreRepaint(true);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.add(canvas);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { quit(); }
            @Override public void windowDeactivated(WindowEvent e) { if (state == S.PLAY) pause(); }
        });
        listeners();
        frame.setVisible(true);
        canvas.requestFocus();
        canvas.createBufferStrategy(2);
    }

    private void setResolution(int lvl) {
        resLevel = Math.max(0, Math.min(RES.length - 1, lvl));
        rend = new Renderer(RES[resLevel][0], RES[resLevel][1]);
        DirectColorModel cm = new DirectColorModel(24, 0xFF0000, 0xFF00, 0xFF);
        DataBufferInt db = new DataBufferInt(rend.pixels, rend.pixels.length);
        WritableRaster wr = Raster.createPackedRaster(db, rend.W, rend.H, rend.W, new int[]{0xFF0000, 0xFF00, 0xFF}, null);
        fb = new BufferedImage(cm, wr, false, null);
        bgCache.clear();
    }

    private void quit() {
        creditStars();
        save.save();
        running = false;
        if (game != null) game.shutdown();
        System.exit(0);
    }

    private void creditStars() {
        if (game != null) { int w = game.me.wallet(); save.bank = Math.max(0, save.bank + w - creditedStars); creditedStars = w; save.save(); }
    }

    // ===================================================================== entrada
    private void listeners() {
        canvas.addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent e) {
                if (state != S.JOIN) return;
                char ch = e.getKeyChar();
                if (ch == 22) { try { String c = (String) Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor); ipText = (ipText + c.trim()).replaceAll("[^0-9a-zA-Z.:\\-]", ""); } catch (Exception ignored) { } return; }
                if ((Character.isLetterOrDigit(ch) || ch == '.' || ch == ':' || ch == '-') && ipText.length() < 40) ipText += ch;
            }
            @Override public void keyPressed(KeyEvent e) { key(e.getKeyCode(), true); }
            @Override public void keyReleased(KeyEvent e) { key(e.getKeyCode(), false); }
        });
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) { in.mouseClicked = true; in.fire = true; in.mouseX = e.getX(); in.mouseY = e.getY(); }
                if (e.getButton() == MouseEvent.BUTTON3) in.fireAlt = true;
            }
            @Override public void mouseReleased(MouseEvent e) { if (e.getButton() == MouseEvent.BUTTON1) in.fire = false; if (e.getButton() == MouseEvent.BUTTON3) in.fireAlt = false; }
            @Override public void mouseMoved(MouseEvent e) { motion(e); }
            @Override public void mouseDragged(MouseEvent e) { motion(e); }
            @Override public void mouseWheelMoved(MouseWheelEvent e) { in.wheel += e.getWheelRotation() > 0 ? 1 : -1; }
        };
        canvas.addMouseListener(ma); canvas.addMouseMotionListener(ma); canvas.addMouseWheelListener(ma);
    }

    private void motion(MouseEvent e) {
        if (in.captured && robot != null) {
            int cx = canvas.getWidth() / 2, cy = canvas.getHeight() / 2;
            int dx = e.getX() - cx, dy = e.getY() - cy;
            if (dx != 0 || dy != 0) {
                in.mdx += dx; in.mdy += dy;
                Point p = canvas.getLocationOnScreen();
                robot.mouseMove(p.x + cx, p.y + cy);
            }
        } else { in.mouseX = e.getX(); in.mouseY = e.getY(); }
    }

    private void key(int k, boolean down) {
        switch (k) {
            case KeyEvent.VK_W: in.w = down; break;
            case KeyEvent.VK_S: in.s = down; break;
            case KeyEvent.VK_A: in.a = down; break;
            case KeyEvent.VK_D: in.d = down; break;
            case KeyEvent.VK_LEFT: in.left = down; break;
            case KeyEvent.VK_RIGHT: in.right = down; break;
            case KeyEvent.VK_UP: in.up = down; break;
            case KeyEvent.VK_DOWN: in.down = down; break;
            case KeyEvent.VK_SPACE: in.fire = down; if (down && state != S.PLAY) in.enterPressed = true; break;
            case KeyEvent.VK_TAB: tabFree = down; break;
            case KeyEvent.VK_E: if (down) in.interactPressed = true; break;
            case KeyEvent.VK_V: if (down) in.tpvPressed = true; break;
            case KeyEvent.VK_BACK_SPACE: if (down && state == S.JOIN && !ipText.isEmpty()) ipText = ipText.substring(0, ipText.length() - 1); break;
            case KeyEvent.VK_ENTER: if (down) in.enterPressed = true; break;
            case KeyEvent.VK_ESCAPE: if (down) in.backPressed = true; break;
            case KeyEvent.VK_F2: if (down) { setResolution((resLevel + 1) % RES.length); scene.fancy = resLevel >= 1; } break;
            case KeyEvent.VK_F11: if (down) toggleFullscreen(); break;
            default:
                if (down && k >= KeyEvent.VK_1 && k <= KeyEvent.VK_8) in.slotKey = k - KeyEvent.VK_1;
        }
    }

    private void toggleFullscreen() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        fullscreen = !fullscreen;
        frame.dispose();
        frame.setUndecorated(fullscreen);
        frame.setVisible(true);
        gd.setFullScreenWindow(fullscreen ? frame : null);
        canvas.requestFocus();
        canvas.createBufferStrategy(2);
    }

    // ===================================================================== bucle
    private void loop() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min(0.1f, (now - last) / 1e9f);
            last = now;
            time += dt;
            try { frame(dt); } catch (Throwable t) { t.printStackTrace(); try { Thread.sleep(50); } catch (InterruptedException ignored) { } }
            long spent = System.nanoTime() - now;
            long target = 1_000_000_000L / 75;
            if (spent < target) try { Thread.sleep((target - spent) / 1_000_000L, (int) ((target - spent) % 1_000_000L)); } catch (InterruptedException ignored) { }
        }
    }

    private void frame(float dt) {
        BufferStrategy bs = canvas.getBufferStrategy();
        if (bs == null) { canvas.createBufferStrategy(2); return; }
        int W = canvas.getWidth(), H = canvas.getHeight();
        if (W < 10 || H < 10) return;
        boolean wantCapture = state == S.PLAY && !tabFree && !overlay && frame.isActive();
        setCapture(wantCapture);
        update(dt, W, H);
        Graphics2D g = (Graphics2D) bs.getDrawGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            draw(g, W, H, dt);
        } finally { g.dispose(); }
        bs.show();
        Toolkit.getDefaultToolkit().sync();
    }

    private void setCapture(boolean c) {
        if (c == in.captured) return;
        in.captured = c;
        canvas.setCursor(c ? blank : Cursor.getDefaultCursor());
        if (c && robot != null) { Point p = canvas.getLocationOnScreen(); robot.mouseMove(p.x + canvas.getWidth() / 2, p.y + canvas.getHeight() / 2); in.mdx = in.mdy = 0; }
    }

    // ===================================================================== logica de estados
    private void update(float dt, int W, int H) {
        toastT = Math.max(0, toastT - dt);
        for (int i = fx.size() - 1; i >= 0; i--) { fx.get(i).t += dt; if (fx.get(i).t > 0.7f) fx.remove(i); }
        switch (state) {
            case PLAY: updatePlay(dt); break;
            case LOADING:
                if (++loadFrames >= 3) {
                    if (startMulti && net != null) {
                        game = new Game(pendingLevel, mpColors, mpSeed, 0, false);
                        game.setNetActive(true);
                        net.send(Net.START, Net.pack(pendingLevel, mpSeed, mpColors[0], mpColors[1]));
                        creditedStars = 0;
                    } else if (newRun || game == null) { game = new Game(pendingLevel, new int[]{save.color}, System.nanoTime(), 0, false); creditedStars = 0; }
                    else game.startLevel(pendingLevel);
                    newRun = false; overlay = false; snapT = 0;
                    game.me.colorIndex = save.color;
                    startPlayMusic();
                    state = S.PLAY; in.fire = false; in.consumeBack();
                }
                break;
            case PAUSE: if (in.consumeBack()) resume(); break;
            case LEVELS: case MODE: case MPMENU:
                if (in.consumeBack()) { click(null); state = state == S.LEVELS ? S.MENU : state == S.MODE ? S.LEVELS : S.MODE; }
                break;
            case HOST: updateHost(); if (in.consumeBack()) { click(null); closeNet(); state = S.MPMENU; } break;
            case JOIN: updateJoin(); if (in.consumeBack()) { click(null); state = S.MPMENU; } if (in.consumeEnter()) connect(); break;
            case CLIENTWAIT: updateClientWait(); if (in.consumeBack()) { closeNet(); state = S.JOIN; } break;
            case LEVEL_DONE: case FINAL:
                netStep(dt, idle);
                if (in.consumeEnter() && !game.mirror) { click(null); if (state == S.LEVEL_DONE) nextLevel(); else toMenu(); }
                in.consumeBack();
                break;
            case OVER:
                netStep(dt, idle);
                if (in.consumeEnter() && !game.mirror) { click(null); retry(); }
                if (in.consumeBack()) toMenu();
                break;
            case MENU: case STORE: case CONTROLS: case CREDITS:
                if (in.consumeBack() && state != S.MENU) { click(null); state = S.MENU; }
                if (state == S.MENU && in.consumeEnter()) { click(null); pendingLevel = 1; startMulti = false; beginLoad(true); }
                break;
        }
        // eventos del arcade: relampagos y brasas
        lightningT -= dt;
        if (lightningT <= 0) { lightningT = 4 + rnd.nextFloat() * 8; lightningA = 1f; }
        lightningA = Math.max(0, lightningA - dt * 3.5f);
        for (float[] e : embers) { e[1] -= e[3] * dt; e[0] += (float) Math.sin(time * 0.7f + e[4]) * 12f * dt; if (e[1] < -0.05f) respawnEmber(e, false); }
    }

    private void respawnEmber(float[] e, boolean any) {
        e[0] = rnd.nextFloat(); e[1] = any ? rnd.nextFloat() : 1.05f; e[2] = 1 + rnd.nextFloat() * 3.5f; e[3] = 0.04f + rnd.nextFloat() * 0.12f; e[4] = rnd.nextFloat() * 6;
    }

    private void beginLoad(boolean fresh) {
        newRun = fresh;
        loadFrames = 0;
        state = S.LOADING;
        if (fresh && game != null) { creditStars(); game.shutdown(); }
        if (fresh) game = null;
        if (!startMulti) closeNet();
        in.fire = false;
    }

    private void startPlayMusic() {
        Audio.musicPlay("/assets/music/gameplay.wav", 0.5f);
        if (drone != null) drone.stop();
        drone = Audio.play("drone", 0.16f, 0, true);
        Audio.play("rain", 0.10f, 0, true);
    }

    private void toMenu() {
        creditStars();
        if (net != null) { if (game != null && !game.mirror) net.send(Net.MENU, new byte[0]); }
        closeNet();
        startMulti = false;
        if (drone != null) { drone.stop(); drone = null; }
        if (game != null) game.shutdown();
        state = S.MENU;
        Audio.musicPlay("/assets/music/menu_theme.wav", 0.8f);
    }

    private void closeNet() {
        if (net != null) { net.close(); net = null; }
        if (pendingNet != null) { pendingNet.close(); pendingNet = null; }
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        server = null; clientReady = false; startFlag = false;
        if (game != null) game.setNetActive(false);
    }

    private void pause() {
        if (state != S.PLAY) return;
        if (game.multi) { overlay = true; return; }
        state = S.PAUSE; in.fire = false; in.w = in.a = in.s = in.d = false;
    }

    private void resume() { state = S.PLAY; overlay = false; in.consumeMouse(); in.fire = false; }

    private void nextLevel() {
        pendingLevel = game.levelIdx + 1;
        if (game.multi && net != null) {
            net.send(Net.LEVEL, Net.pack(pendingLevel));
            game.startLevel(pendingLevel);
            game.setNetActive(true);
            state = S.PLAY; overlay = false; in.fire = false; in.consumeBack();
            startPlayMusic();
        } else beginLoad(false);
    }

    private void retry() {
        pendingLevel = 1;
        if (game.multi && net != null) {
            mpSeed = System.nanoTime();
            net.send(Net.RESTART, Net.pack(mpSeed));
            beginLoad(true);
            startMulti = true;
        } else { startMulti = false; beginLoad(true); }
    }

    // ---------------------------------------------------------------- red
    private void startHosting() {
        closeNet();
        netStatus = "";
        try {
            server = new java.net.ServerSocket(Net.PORT);
            final java.net.ServerSocket srv = server;
            Thread t = new Thread(() -> {
                try {
                    java.net.Socket sk = srv.accept();
                    Net n = new Net(sk);
                    pendingNet = n;
                } catch (Exception e) { if (!srv.isClosed()) netStatus = "Error de red: " + e.getMessage(); }
            }, "net-accept");
            t.setDaemon(true); t.start();
            state = S.HOST;
        } catch (Exception e) { toast("No se pudo abrir el puerto " + Net.PORT + " (¿ya hay una partida abierta?)"); closeNet(); }
    }

    private void updateHost() {
        if (net == null && pendingNet != null) { net = pendingNet; pendingNet = null; clientReady = false; }
        if (net != null) {
            Net.Msg m;
            while ((m = net.poll()) != null) {
                if (m.type == Net.HELLO) {
                    try {
                        java.io.DataInputStream d = new java.io.DataInputStream(new java.io.ByteArrayInputStream(m.data));
                        int ver = d.readInt(); clientColor = d.readInt();
                        if (ver != Net.VERSION) { toast("El otro jugador usa otra versión del juego"); net.close(); net = null; break; }
                        clientReady = true; Audio.play("powerup", 0.6f);
                    } catch (Exception ignored) { }
                } else if (m.type == Net.BYE) { net.close(); net = null; clientReady = false; break; }
            }
            if (net != null && net.closed) { net = null; clientReady = false; toast("El jugador 2 se desconectó"); }
        }
    }

    private void hostStart() {
        if (net == null || !clientReady) return;
        int c0 = save.color, c1 = Math.max(0, Math.min(Palette.NAMES.length - 1, clientColor));
        if (c1 == c0) c1 = (c0 + 1) % Palette.NAMES.length;
        mpColors = new int[]{c0, c1};
        mpSeed = System.nanoTime();
        pendingLevel = selLevel;
        startMulti = true;
        try { if (server != null) server.close(); } catch (Exception ignored) { }
        beginLoad(true);
    }

    private void connect() {
        String host = ipText.trim();
        if (host.isEmpty()) { toast("Escribe la IP del anfitrión"); return; }
        int port = Net.PORT;
        int ci = host.lastIndexOf(':');
        if (ci > 0) { try { port = Integer.parseInt(host.substring(ci + 1)); host = host.substring(0, ci); } catch (Exception ignored) { } }
        save.lastIp = ipText; save.save();
        netStatus = "Conectando con " + host + "...";
        closeNet();
        state = S.CLIENTWAIT;
        final String h = host; final int pt = port;
        Thread t = new Thread(() -> {
            try {
                Net n = Net.connect(h, pt, 6000);
                n.send(Net.HELLO, Net.pack(Net.VERSION, save.color));
                pendingNet = n;
                netStatus = "Conectado. Esperando a que el anfitrión comience la partida...";
            } catch (Exception e) { netStatus = "ERROR: no se pudo conectar (" + e.getClass().getSimpleName() + "). Revisa la IP y que el anfitrión esté esperando."; }
        }, "net-connect");
        t.setDaemon(true); t.start();
    }

    private void updateJoin() { }

    private void updateClientWait() {
        if (net == null && pendingNet != null) { net = pendingNet; pendingNet = null; }
        if (net == null) return;
        Net.Msg m;
        while ((m = net.poll()) != null) {
            try {
                java.io.DataInputStream d = new java.io.DataInputStream(new java.io.ByteArrayInputStream(m.data));
                if (m.type == Net.START) { startMirror(d.readInt(), d.readLong(), d.readInt(), d.readInt()); return; }
                if (m.type == Net.BYE || m.type == Net.MENU) { closeNet(); state = S.JOIN; toast("El anfitrión canceló la partida"); return; }
            } catch (Exception ignored) { }
        }
        if (net != null && net.closed) { closeNet(); state = S.JOIN; toast("Se perdió la conexión"); }
    }

    private void startMirror(int level, long seed, int c0, int c1) {
        if (game != null) game.shutdown();
        mpColors = new int[]{c0, c1};
        game = new Game(level, mpColors, seed, 1, true);
        creditedStars = 0; overlay = false;
        startPlayMusic();
        state = S.PLAY; in.fire = false; in.consumeBack();
    }

    /** Trabajo de red de cada fotograma (anfitrion: recibir entradas y enviar instantaneas; cliente: aplicar instantaneas). */
    private void netStep(float dt, Input input) {
        if (game == null || !game.multi || net == null) { if (game != null && game.mirror) { game.mirrorUpdate(dt, input); } else if (game != null) game.update(dt, input); return; }
        if (!game.mirror) {
            Net.Msg m;
            while ((m = net.poll()) != null) {
                try {
                    java.io.DataInputStream d = new java.io.DataInputStream(new java.io.ByteArrayInputStream(m.data));
                    if (m.type == Net.INPUT) game.applyRemoteInput(game.players[1], d.readFloat(), d.readFloat(), d.readFloat(), d.readFloat(), d.readBoolean(), d.readBoolean(), d.readByte(), d.readByte(), d.readByte());
                    else if (m.type == Net.BYE) remoteGone();
                } catch (Exception ignored) { }
            }
            if (net != null && net.closed) remoteGone();
            game.update(dt, input);
            snapT += dt;
            if (net != null && snapT >= 1f / 30f) { snapT = 0; try { net.send(Net.SNAP, game.writeSnapshot()); } catch (Exception ignored) { } }
        } else {
            Net.Msg m;
            while ((m = net.poll()) != null) {
                try {
                    java.io.DataInputStream d = new java.io.DataInputStream(new java.io.ByteArrayInputStream(m.data));
                    if (m.type == Net.SNAP) game.readSnapshot(m.data);
                    else if (m.type == Net.LEVEL) { game.startLevel(d.readInt()); state = S.PLAY; overlay = false; startPlayMusic(); in.fire = false; }
                    else if (m.type == Net.RESTART) { long sd = d.readLong(); startMirror(1, sd, mpColors[0], mpColors[1]); return; }
                    else if (m.type == Net.BYE || m.type == Net.MENU) { toMenu(); toast("El anfitrión terminó la partida"); return; }
                } catch (Exception ignored) { }
            }
            if (net != null && net.closed) { toMenu(); toast("Se perdió la conexión con el anfitrión"); return; }
            game.mirrorUpdate(dt, input);
            Player me = game.me;
            boolean f = input.fire;
            int slot = input.consumeSlot(), wheel = input.consumeWheel();
            int inter = input.consumeInteract() ? 1 : 0;
            if (net != null) net.send(Net.INPUT, Net.pack(me.px, me.pz, me.yaw, me.pitch, f, me.zoom, (byte) inter, (byte) slot, (byte) wheel));
        }
    }

    private void remoteGone() {
        if (net != null) { net.close(); net = null; }
        game.setNetActive(false);
        Player p = game.players[1];
        p.state = Player.OUT; p.lives = 0;
        game.say(null, "El jugador 2 se desconectó", 0xFF8080);
    }

    private void updatePlay(float dt) {
        if (in.consumeBack()) { if (game.multi && overlay) resume(); else pause(); if (state != S.PLAY) return; }
        if (in.consumeTpv()) { game.me.tpv = !game.me.tpv; Audio.play("click", 0.3f); }
        Input use = overlay ? idle : in;
        if (tabFree && !overlay) {
            in.consumeMouse();
            if (in.consumeClick()) {
                for (int i = 0; i < 8; i++) if (hud.slotRects[i] != null && hud.slotRects[i].contains(in.mouseX, in.mouseY)) { game.selectSlotPublic(i); Audio.play("click", 0.5f); }
                if (hud.abandonRect.contains(in.mouseX, in.mouseY)) { click(null); toMenu(); return; }
            }
            boolean f = in.fire; in.fire = false;
            netStep(dt, in);
            in.fire = f;
        } else {
            in.consumeClick();
            if (overlay) { in.consumeMouse(); in.consumeSlot(); in.consumeWheel(); in.consumeInteract(); }
            netStep(dt, use);
        }
        if (game.state == Game.LEVEL_DONE) { state = S.LEVEL_DONE; creditStars(); stopDrone(); }
        else if (game.state == Game.ALL_DONE) { state = S.FINAL; creditStars(); stopDrone(); }
        else if (game.state == Game.GAME_OVER) { state = S.OVER; creditStars(); stopDrone(); }
    }

    private void stopDrone() { if (drone != null) { drone.stop(); drone = null; } Audio.musicVolume(0.2f); }

    // ===================================================================== dibujo
    private void draw(Graphics2D g, int W, int H, float dt) {
        buttons.clear();
        float s = H / 720f;
        switch (state) {
            case PLAY: case PAUSE: {
                long t0 = System.nanoTime();
                scene.render(rend, game, state == S.PLAY ? dt : 0f);
                float ms = (System.nanoTime() - t0) / 1e6f;
                ema += (ms - ema) * 0.05f;
                lowTimer = ema > 30f ? lowTimer + dt : 0;
                if (lowTimer > 2.5f) {
                    if (scene.fancy) { scene.fancy = false; toast("Efectos de ambiente reducidos para más fluidez (F2: calidad)"); }
                    else if (resLevel > 0) { setResolution(resLevel - 1); toast("Calidad ajustada para mayor fluidez (F2 para cambiar)"); }
                    lowTimer = 0; ema = 16;
                }
                g.drawImage(fb, 0, 0, W, H, null);
                drawVignette(g, W, H);
                hud.draw(g, game, W, H, time, tabFree || state == S.PAUSE);
                if (state == S.PAUSE || overlay) pauseMenu(g, W, H, s);
                break;
            }
            case LOADING: loading(g, W, H, s); break;
            case LEVELS: levels(g, W, H, s); break;
            case MODE: mode(g, W, H, s); break;
            case MPMENU: mpMenu(g, W, H, s); break;
            case HOST: hostScreen(g, W, H, s); break;
            case JOIN: joinScreen(g, W, H, s); break;
            case CLIENTWAIT: clientWait(g, W, H, s); break;
            case MENU: menu(g, W, H, s); break;
            case STORE: store(g, W, H, s); break;
            case CONTROLS: controls(g, W, H, s); break;
            case CREDITS: credits(g, W, H, s); break;
            case LEVEL_DONE: case FINAL: results(g, W, H, s); break;
            case OVER: gameOver(g, W, H, s); break;
        }
        switch (state) {
            case MENU: case STORE: case CONTROLS: case CREDITS: case LEVELS: case MODE: case MPMENU: case HOST: case JOIN: case CLIENTWAIT: drawLogo(g, W, H, s); break;
            default: break;
        }
        if (toastT > 0) {
            g.setFont(Hud.font(16 * s, true));
            Hud.centerText(g, toast, W / 2f, H - 60 * s, new Color(255, 230, 140, (int) (255 * Math.min(1, toastT))));
        }
        // clics de arcade
        for (Fx f : fx) {
            float a = 1f - f.t / 0.7f, r = 8 + f.t * 120;
            g.setStroke(new BasicStroke(3f * a + 0.5f));
            g.setColor(new Color(255, 200, 60, (int) (220 * a))); g.draw(new Ellipse2D.Float(f.x - r, f.y - r, r * 2, r * 2));
            g.setColor(new Color(255, 60, 40, (int) (160 * a))); g.draw(new Ellipse2D.Float(f.x - r * 0.6f, f.y - r * 0.6f, r * 1.2f, r * 1.2f));
            for (int i = 0; i < 8; i++) { double an = i * Math.PI / 4 + f.x; g.fill(new Rectangle2D.Float((float) (f.x + Math.cos(an) * r * 1.2f), (float) (f.y + Math.sin(an) * r * 1.2f), 4, 4)); }
        }
    }

    /** Logo de la Universidad Europea en la esquina inferior derecha de los menus. */
    private void drawLogo(Graphics2D g, int W, int H, float s) {
        if (logo == null) return;
        float lw = 196 * s, lh = lw * logo.getHeight() / logo.getWidth();
        float pad = 10 * s, x = W - lw - pad * 2 - 14 * s, y = H - lh - pad * 2 - 12 * s;
        g.setColor(new Color(0, 0, 0, 90)); g.fill(new RoundRectangle2D.Float(x + 3, y + 4, lw + pad * 2, lh + pad * 2, 16, 16));
        g.setColor(new Color(255, 255, 255, 240)); g.fill(new RoundRectangle2D.Float(x, y, lw + pad * 2, lh + pad * 2, 16, 16));
        Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(logo, (int) (x + pad), (int) (y + pad), (int) lw, (int) lh, null);
        if (old != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
    }

    private void toast(String t) { toast = t; toastT = 3f; }

    private void drawVignette(Graphics2D g, int W, int H) {
        if (vignette == null || vigW != W || vigH != H) {
            vigW = W; vigH = H;
            vignette = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            Graphics2D vg = vignette.createGraphics();
            vg.setPaint(new RadialGradientPaint(W / 2f, H / 2f, Math.max(W, H) * 0.72f, new float[]{0.45f, 1f}, new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 200)}));
            vg.fillRect(0, 0, W, H);
            vg.dispose();
        }
        g.drawImage(vignette, 0, 0, null);
    }

    // ---------------------------------------------------------------- fondos con efectos
    private void backdrop(Graphics2D g, String img, int W, int H, float dark) {
        BufferedImage im = images.get(img);
        g.setColor(Color.BLACK); g.fillRect(0, 0, W, H);
        if (im != null) {
            float zoom = 1.04f + 0.03f * (float) Math.sin(time * 0.15f);
            boolean menuImg = img.equals("menu") || img.equals("store");
            float sc = Math.max(W / (float) im.getWidth(), H / (float) im.getHeight()) * zoom * (menuImg ? 1.3f : 1f);
            float iw = im.getWidth() * sc, ih = im.getHeight() * sc;
            float ox = (W - iw) / 2f + (float) Math.sin(time * 0.1f) * 14f, oy = menuImg ? H - ih + 10 : (H - ih) / 2f;
            g.drawImage(im, (int) ox, (int) oy, (int) iw, (int) ih, null);
        }
        g.setColor(new Color(0, 0, 0, (int) (255 * dark))); g.fillRect(0, 0, W, H);
        // niebla
        for (int i = 0; i < 3; i++) {
            float x = ((time * (12 + i * 7) + i * 400) % (W + 800)) - 400;
            g.setPaint(new GradientPaint(x, 0, new Color(150, 170, 200, 0), x + 400, 0, new Color(150, 170, 200, 22)));
            g.fillRect((int) x, (int) (H * (0.5f + i * 0.15f)), 800, (int) (H * 0.25f));
        }
        // brasas
        for (float[] e : embers) {
            float a = (float) (0.5 + 0.5 * Math.sin(time * 3 + e[4]));
            g.setColor(new Color(255, 120 + (int) (e[4] * 20) % 100, 30, (int) (60 + 150 * a)));
            g.fill(new Ellipse2D.Float(e[0] * W, e[1] * H, e[2] * H / 360f, e[2] * H / 360f));
        }
        if (lightningA > 0) { g.setColor(new Color(200, 210, 255, (int) (110 * lightningA * (0.5f + 0.5f * (float) Math.sin(time * 60))))); g.fillRect(0, 0, W, H); }
        drawVignette(g, W, H);
        // lineas de monitor arcade
        g.setColor(new Color(0, 0, 0, 38));
        for (int y = 0; y < H; y += 3) g.drawLine(0, y, W, y);
    }

    // ---------------------------------------------------------------- botones
    private Btn button(Graphics2D g, String text, float cx, float cy, float w, float h, Runnable act, Color col) {
        Btn b = new Btn();
        b.text = text; b.act = act;
        b.r.setBounds((int) (cx - w / 2), (int) (cy - h / 2), (int) w, (int) h);
        buttons.add(b);
        boolean hover = b.r.contains(in.mouseX, in.mouseY);
        float hv = hover ? 1 : 0;
        float pulse = hover ? 1f + 0.04f * (float) Math.sin(time * 14) : 1f;
        float s = pulse;
        float x = cx - w * s / 2, y = cy - h * s / 2;
        RoundRectangle2D rr = new RoundRectangle2D.Float(x, y, w * s, h * s, h * 0.35f, h * 0.35f);
        g.setPaint(new GradientPaint(x, y, hover ? col.brighter() : col, x, y + h, (hover ? col : col.darker()).darker().darker()));
        g.fill(rr);
        g.setStroke(new BasicStroke(hover ? 3.5f : 2f));
        g.setColor(hover ? new Color(255, 240, 150) : new Color(255, 255, 255, 140));
        g.draw(rr);
        if (hover) { g.setColor(new Color(255, 200, 80, 60)); g.setStroke(new BasicStroke(10)); g.draw(rr); }
        float fs = h * 0.46f;
        g.setFont(Hud.font(fs, true));
        while (g.getFontMetrics().stringWidth(text) > w - 24 && fs > 8) { fs -= 1; g.setFont(Hud.font(fs, true)); }
        Hud.centerText(g, text, cx, cy + h * 0.17f, hover ? Color.WHITE : new Color(255, 245, 225));
        return b;
    }

    private void handleButtons() {
        if (!in.consumeClick()) return;
        for (Btn b : buttons) if (b.r.contains(in.mouseX, in.mouseY)) { click(b); if (b.act != null) b.act.run(); break; }
    }

    private void click(Btn b) {
        Audio.play("click", 0.7f);
        Fx f = new Fx(); f.x = in.mouseX; f.y = in.mouseY; fx.add(f);
    }

    // ---------------------------------------------------------------- menu principal
    private void menu(Graphics2D g, int W, int H, float s) {
        backdrop(g, "menu", W, H, 0.25f);
        // titulo
        float shake = (rnd.nextInt(60) == 0) ? 6 * s : 0;
        float jx = (float) rnd.nextGaussian() * shake, jy = (float) rnd.nextGaussian() * shake;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (128 * s)));
        String t = "PACMAN";
        FontMetrics fm = g.getFontMetrics();
        float tx = W / 2f - fm.stringWidth(t) / 2f + jx, ty = 150 * s + jy;
        for (int i = 8; i >= 1; i--) { g.setColor(new Color(255, 40, 20, 18 + (int) (12 * Math.sin(time * 5)))); g.drawString(t, tx - i * 2, ty + i * 2); g.drawString(t, tx + i * 2, ty + i * 2); }
        g.setColor(new Color(90, 0, 0)); g.drawString(t, tx + 6 * s, ty + 7 * s);
        g.setPaint(new GradientPaint(0, ty - 100 * s, new Color(255, 245, 90), 0, ty, new Color(255, 140, 0)));
        g.drawString(t, tx, ty);
        g.setColor(new Color(0, 0, 0, 160)); g.setStroke(new BasicStroke(3f * s));
        // gotas de sangre
        for (int i = 0; i < 9; i++) {
            float dx = tx + fm.stringWidth(t) * (i + 0.5f) / 9f, len = (14 + 30 * ((i * 37) % 7) / 7f + 8 * (float) Math.sin(time * 1.5 + i)) * s;
            g.setColor(new Color(170, 0, 10)); g.fill(new RoundRectangle2D.Float(dx, ty + 4 * s, 5 * s, len, 4, 4)); g.fill(new Ellipse2D.Float(dx - 1 * s, ty + len, 7 * s, 8 * s));
        }
        g.setFont(Hud.font(30 * s, true));
        float fl = rnd.nextInt(30) == 0 ? 0.3f : 1f;
        Hud.centerText(g, "YA NO ESTÁ AQUÍ", W / 2f, 205 * s, new Color((int) (255 * fl), (int) (60 * fl), (int) (50 * fl)));
        g.setFont(Hud.font(15 * s, false));
        Hud.centerText(g, "Ciudad laberíntica de Halloween  ·  3D en primera persona  ·  100% Java", W / 2f, 232 * s, new Color(210, 200, 230));
        float bx = W / 2f, by = H * 0.50f, bw = 400 * s, bh = 58 * s, gap = 70 * s;
        by = H * 0.43f; gap = 62 * s; bh = 52 * s;
        button(g, "START", bx, by, bw, bh, () -> { pendingLevel = 1; startMulti = false; beginLoad(true); }, new Color(220, 40, 30));
        button(g, "NIVELES · SOLO O MULTIJUGADOR", bx, by + gap, bw, bh, () -> state = S.LEVELS, new Color(30, 150, 90));
        button(g, "TIENDA · PERSONALIZAR PACMAN", bx, by + gap * 2, bw, bh, () -> { state = S.STORE; }, new Color(220, 120, 20));
        button(g, "COMANDOS Y CÓMO JUGAR", bx, by + gap * 3, bw, bh, () -> state = S.CONTROLS, new Color(60, 90, 200));
        button(g, "CRÉDITOS", bx, by + gap * 4, bw, bh, () -> state = S.CREDITS, new Color(110, 60, 170));
        button(g, "SALIR", bx, by + gap * 5, bw, bh, this::quit, new Color(70, 70, 80));
        g.setFont(Hud.font(15 * s, true));
        float blink = 0.5f + 0.5f * (float) Math.sin(time * 4);
        Hud.centerText(g, "PRESIONA START O ENTER", W / 2f, H - 22 * s, new Color(255, 230, 120, (int) (120 + 135 * blink)));
        g.setFont(Hud.font(14 * s, true));
        Hud.shadowText(g, "ESTRELLAS: " + save.bank, 20 * s, 30 * s, Hud.GOLD);
        float[] c = Palette.RGB[save.color];
        Hud.pacIcon(g, W - 40 * s, 30 * s, 16 * s, new Color(c[0], c[1], c[2]), time);
        handleButtons();
    }

    // ---------------------------------------------------------------- tienda
    private void store(Graphics2D g, int W, int H, float s) {
        // escena 3D: Pac-Man girando sobre la imagen de la tienda
        int[] bg = background("store");
        rend.background = bg;
        rend.sky = null;
        rend.cam.x = 0; rend.cam.y = 1.2f; rend.cam.z = 0; rend.cam.yaw = 0; rend.cam.pitch = 0;
        rend.begin();
        rend.lantern = 1.5f; rend.fogDensity = 0.004f; rend.flash = 0.12f;
        int shown = hoverColor >= 0 ? hoverColor : save.color;
        float[] c = Palette.RGB[shown];
        float mouth = 0.12f + 0.6f * Math.abs((float) Math.sin(time * 3.2f));
        Models.drawPac(rend, -1.6f, 1.0f + 0.08f * (float) Math.sin(time * 2), 4.8f, 3.14159f + 0.8f * (float) Math.sin(time * 0.9f), 1.15f, mouth, c[0], c[1], c[2], 0.25f, 0.25f, 0.3f);
        rend.end();
        rend.background = null;
        g.drawImage(fb, 0, 0, W, H, null);
        g.setColor(new Color(0, 0, 0, 90)); g.fillRect(0, 0, W, H);
        drawVignette(g, W, H);
        g.setFont(Hud.font(46 * s, true));
        g.setColor(new Color(0, 0, 0, 150)); g.fillRect(0, 0, W, (int) (118 * s));
        Hud.centerText(g, "TIENDA DE PACMAN", W * 0.75f, 62 * s, new Color(255, 190, 40));
        g.setFont(Hud.font(16 * s, true));
        Hud.centerText(g, "ESTRELLAS: " + save.bank + "  (recógelas en las partidas para comprar colores)", W * 0.75f, 98 * s, Hud.GOLD);
        // cuadricula de colores
        hoverColor = -1;
        float gx0 = W * 0.5f, gy0 = 140 * s, cw = (W * 0.5f - 40 * s) / 3f, ch = 108 * s;
        for (int i = 0; i < Palette.NAMES.length; i++) {
            int col = i % 3, row = i / 3;
            float x = gx0 + col * cw, y = gy0 + row * (ch + 8 * s);
            Rectangle r = new Rectangle((int) x, (int) y, (int) (cw - 10 * s), (int) ch);
            swatches[i] = r;
            boolean hover = r.contains(in.mouseX, in.mouseY);
            if (hover) hoverColor = i;
            boolean sel = i == save.color;
            g.setColor(new Color(10, 8, 18, hover ? 235 : 195)); g.fillRoundRect(r.x, r.y, r.width, r.height, 18, 18);
            g.setStroke(new BasicStroke(sel ? 4f : 2f));
            g.setColor(sel ? new Color(255, 215, 50) : hover ? Color.WHITE : new Color(120, 110, 150));
            g.drawRoundRect(r.x, r.y, r.width, r.height, 18, 18);
            float[] pc = Palette.RGB[i];
            Hud.pacIcon(g, r.x + 40 * s, r.y + 40 * s, 26 * s, new Color(pc[0], pc[1], pc[2]), time + i * 0.4f);
            g.setFont(Hud.font(14 * s, true));
            Hud.shadowText(g, Palette.NAMES[i], r.x + 78 * s, r.y + 34 * s, Color.WHITE);
            g.setFont(Hud.font(13 * s, true));
            String st = sel ? "EN USO" : save.owned[i] ? "TUYO · clic: usar" : Palette.COST[i] + " ESTRELLAS";
            Color sc = sel ? new Color(100, 255, 130) : save.owned[i] ? new Color(180, 220, 255) : save.bank >= Palette.COST[i] ? Hud.GOLD : new Color(255, 100, 90);
            Hud.shadowText(g, st, r.x + 78 * s, r.y + 62 * s, sc);
            if (!save.owned[i]) { Hud.starIcon(g, r.x + r.width - 22 * s, r.y + r.height - 20 * s, 10 * s, time); }
        }
        button(g, "VOLVER", W * 0.5f + 140 * s, H - 56 * s, 260 * s, 52 * s, () -> state = S.MENU, new Color(70, 80, 160));
        if (in.mouseClicked) {
            for (int i = 0; i < swatches.length; i++) if (swatches[i] != null && swatches[i].contains(in.mouseX, in.mouseY)) {
                in.mouseClicked = false;
                Audio.play("click", 0.7f);
                Fx f = new Fx(); f.x = in.mouseX; f.y = in.mouseY; fx.add(f);
                if (save.owned[i]) { save.color = i; save.save(); }
                else if (save.bank >= Palette.COST[i]) { save.bank -= Palette.COST[i]; save.owned[i] = true; save.color = i; save.save(); Audio.play("powerup", 0.8f); toast("¡Color comprado!"); }
                else { Audio.play("dry", 0.7f); toast("No tienes suficientes estrellas"); }
            }
        }
        handleButtons();
    }

    private int[] background(String name) {
        String key = name + rend.W;
        int[] bg = bgCache.get(key);
        if (bg != null) return bg;
        BufferedImage im = images.get(name);
        BufferedImage dst = new BufferedImage(rend.W, rend.H, BufferedImage.TYPE_INT_RGB);
        Graphics2D d = dst.createGraphics();
        d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (im != null) {
            float sc = Math.max(rend.W / (float) im.getWidth(), rend.H / (float) im.getHeight()) * 1.3f;
            d.drawImage(im, (int) ((rend.W - im.getWidth() * sc) / 2), (int) (rend.H - im.getHeight() * sc + 6), (int) (im.getWidth() * sc), (int) (im.getHeight() * sc), null);
        }
        d.dispose();
        bg = new int[rend.W * rend.H];
        dst.getRGB(0, 0, rend.W, rend.H, bg, 0, rend.W);
        bgCache.put(key, bg);
        return bg;
    }

    // ---------------------------------------------------------------- comandos y explicacion
    private void controls(Graphics2D g, int W, int H, float s) {
        backdrop(g, "controls", W, H, 0.55f);
        g.setFont(Hud.font(44 * s, true));
        Hud.centerText(g, "COMANDOS Y CÓMO JUGAR", W / 2f, 62 * s, new Color(255, 190, 40));
        String[][] left = {{"W A S D", "Moverte"}, {"RATÓN", "Mirar alrededor (primera persona)"}, {"CLIC IZQ. / ESPACIO", "Disparar / atacar (mantén en el arco para tensarlo)"},
                {"1 - 8  /  RUEDA", "Cambiar arma del inventario"}, {"CLIC DERECHO", "ZOOM real al apuntar (pistola, rifle con mira, arco, minigun)"}, {"E", "Abrir cofres / comprar en tiendas / salvar almas (tócalas)"}, {"TAB (mantener)", "Liberar el ratón: clic en inventario"},
                {"V", "Cambiar entre primera y tercera persona"}, {"ESC", "Pausa · Abandonar partida"}, {"FLECHAS", "Girar la cámara con el teclado"}, {"F2 / F11", "Calidad gráfica / Pantalla completa"}};
        float y = 120 * s;
        g.setFont(Hud.font(17 * s, true));
        for (String[] r : left) {
            Hud.shadowText(g, r[0], 60 * s, y, new Color(255, 215, 90));
            g.setFont(Hud.font(16 * s, false));
            Hud.shadowText(g, r[1], 330 * s, y, Color.WHITE);
            g.setFont(Hud.font(17 * s, true));
            y += 29 * s;
        }
        String[] how = {
                "OBJETIVO: sobrevive en la ciudad laberíntica. Para salir debes cruzar la ÚNICA puerta, que se abre si eliminas a TODOS los fantasmas",
                "o si encuentras la LLAVE (en el suelo o escondida en un cofre). Tienes 5 minutos por nivel. Son 3 niveles: 20, 40 y 60 fantasmas.",
                "FANTASMAS: se mueven como en el Pac-Man real (Blinky te persigue, Pinky te embosca, Inky te flanquea, Clyde es tímido) pero aquí SE LES MATA CON ARMAS.",
                "Empiezas con PISTOLA y 24 balas: abre cofres para hallar rifle, arco, minigun, espada y granadas. Cada una tiene su daño, su zoom y su comportamiento real.",
                "Las balas son compartidas por todas las armas (la espada es infinita; la granada gasta 5). Balas, botiquines y PODERES se usan solos al tomarlos.",
                "ESTRELLAS: aumentan tu velocidad. PODER: inmunidad al daño 30 s (raro). Tienes 500 de vida y 3 vidas: si mueres revives donde caíste.",
                "Si se acaba el tiempo o pierdes las 3 vidas, vuelves al NIVEL 1. ¡Modo hardcore!",
                "TRAMPAS: el ÁCIDO verde quita 10 de vida por segundo y las PÚAS 50. TIENDAS (3 por nivel): el COFRE ARCOÍRIS vende un arma aleatoria (más probables las raras) por estrellas; puedes recomprar.",
                "ALMAS PERDIDAS: ~20 por nivel huyen de los fantasmas. Tócalas para salvarlas: revelan con una luz dorada (2 s) hacia dónde está la LLAVE. Si mueren, quedan en el suelo.",
                "MULTIJUGADOR (2 personas): en NIVELES elige un nivel > CON ALGUIEN MÁS. Uno crea la partida y el otro se une con su IP. Comparten llave, puerta y cronómetro."};
        y = 120 * s + 11 * 29 * s + 4 * s;
        g.setColor(new Color(0, 0, 0, 150)); g.fillRoundRect((int) (40 * s), (int) (y - 26 * s), W - (int) (80 * s), (int) (how.length * 23 * s + 20 * s), 16, 16);
        g.setFont(Hud.font(13.5f * s, false));
        for (String l : how) { Hud.shadowText(g, l, 56 * s, y, new Color(235, 235, 245)); y += 23 * s; }
        button(g, "VOLVER", W / 2f, H - 40 * s, 260 * s, 50 * s, () -> state = S.MENU, new Color(70, 80, 160));
        handleButtons();
    }

    private void credits(Graphics2D g, int W, int H, float s) {
        backdrop(g, "credits", W, H, 0.4f);
        g.setFont(Hud.font(52 * s, true));
        Hud.centerText(g, "CRÉDITOS", W / 2f, 90 * s, new Color(255, 190, 40));
        String[] lines = {"PACMAN 3D · YA NO ESTÁ AQUÍ", "Proyecto de la Universidad Europea", "",
                "#PROYECTO REALIZADO POR:",
                "@Pariona Lucas Jose Manuel - 225Q8973", "@Sergio Martínez Gargantilla - 225D8771", "@Araque Márquez Darío José - 05434551L", "",
                "Programación: motor 3D propio por software, 100% Java (sin librerías externas)",
                "Música, sonidos e ilustraciones de menús: los del proyecto original", "",
                "Inspirado en el clásico arcade PAC-MAN® (marca de sus propietarios). Proyecto académico sin fines comerciales.", "Gracias por jugar... si te atreves."};
        float y = 140 * s;
        for (String l : lines) {
            boolean title = l.startsWith("PACMAN"), head = l.startsWith("#"), name = l.startsWith("@");
            String txt = head || name ? l.substring(1) : l;
            g.setFont(Hud.font((title ? 26 : head ? 22 : name ? 24 : 16) * s, title || head || name));
            Hud.centerText(g, txt, W / 2f, y, title ? new Color(255, 120, 60) : head ? new Color(255, 190, 40) : name ? new Color(255, 245, 190) : Color.WHITE);
            y += (title ? 38 : name ? 36 : 30) * s;
        }
        button(g, "VOLVER", W / 2f, H - 50 * s, 260 * s, 52 * s, () -> state = S.MENU, new Color(70, 80, 160));
        handleButtons();
    }

    private static final String[][] LEVEL_INFO = {
            {"SEMILLERO DE LA NIEBLA", "20 fantasmas", "Laberinto pequeño con muchos atajos", "Ideal para empezar"},
            {"DISTRITO DE LOS MUERTOS", "40 fantasmas", "Laberinto grande y más cerrado", "Fantasmas más rápidos y duros"},
            {"CIUDADELA DEL VACÍO", "60 fantasmas", "Laberinto enorme casi sin atajos", "Solo para valientes (hardcore)"}};

    private void levels(Graphics2D g, int W, int H, float s) {
        backdrop(g, "menu", W, H, 0.6f);
        g.setFont(Hud.font(48 * s, true));
        Hud.centerText(g, "ELIGE TU NIVEL", W / 2f, 80 * s, new Color(255, 190, 40));
        float cw = 330 * s, ch = 400 * s, gap = 40 * s, x0 = W / 2f - (cw * 3 + gap * 2) / 2f;
        for (int i = 0; i < 3; i++) {
            float x = x0 + i * (cw + gap), y = 130 * s;
            Rectangle r = new Rectangle((int) x, (int) y, (int) cw, (int) ch);
            boolean hover = r.contains(in.mouseX, in.mouseY);
            Color[] cols = {new Color(60, 160, 90), new Color(220, 140, 30), new Color(210, 40, 40)};
            g.setColor(new Color(8, 6, 16, hover ? 240 : 205)); g.fillRoundRect(r.x, r.y, r.width, r.height, 26, 26);
            g.setStroke(new BasicStroke(hover ? 5f : 2.5f)); g.setColor(hover ? Color.WHITE : cols[i]); g.drawRoundRect(r.x, r.y, r.width, r.height, 26, 26);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (92 * s)));
            Hud.centerText(g, String.valueOf(i + 1), x + cw / 2, y + 110 * s, cols[i].brighter());
            g.setFont(Hud.font(24 * s, true));
            Hud.centerText(g, "NIVEL " + (i + 1), x + cw / 2, y + 160 * s, Color.WHITE);
            g.setFont(Hud.font(15 * s, true));
            String name = LEVEL_INFO[i][0];
            while (g.getFontMetrics().stringWidth(name) > cw - 20 && g.getFont().getSize() > 8) g.setFont(Hud.font(g.getFont().getSize() - 1, true));
            Hud.centerText(g, name, x + cw / 2, y + 192 * s, new Color(255, 150, 90));
            g.setFont(Hud.font(20 * s, true));
            Hud.centerText(g, LEVEL_INFO[i][1], x + cw / 2, y + 240 * s, Hud.GOLD);
            g.setFont(Hud.font(14 * s, false));
            Hud.centerText(g, LEVEL_INFO[i][2], x + cw / 2, y + 276 * s, new Color(220, 220, 235));
            Hud.centerText(g, LEVEL_INFO[i][3], x + cw / 2, y + 298 * s, new Color(220, 220, 235));
            g.setFont(Hud.font(13 * s, true));
            Hud.centerText(g, "Cronómetro 5:00 · 1 o 2 jugadores", x + cw / 2, y + 332 * s, new Color(140, 200, 255));
            g.setFont(Hud.font(18 * s, true));
            float pulse = hover ? 0.6f + 0.4f * (float) Math.sin(time * 10) : 1f;
            Hud.centerText(g, hover ? "▶ ENTRAR ◀" : "ENTRAR", x + cw / 2, y + 376 * s, new Color(255, 255, 255, (int) (255 * pulse)));
            final int lv = i + 1;
            Btn b = new Btn(); b.r = r; b.act = () -> { selLevel = lv; state = S.MODE; }; buttons.add(b);
        }
        button(g, "VOLVER", W / 2f, H - 50 * s, 260 * s, 50 * s, () -> state = S.MENU, new Color(70, 80, 160));
        handleButtons();
    }

    private void mode(Graphics2D g, int W, int H, float s) {
        backdrop(g, "menu", W, H, 0.6f);
        g.setFont(Hud.font(60 * s, true));
        Hud.centerText(g, "NIVEL " + selLevel, W / 2f, H * 0.22f, new Color(255, 120, 60));
        g.setFont(Hud.font(20 * s, true));
        Hud.centerText(g, LEVEL_INFO[selLevel - 1][0] + " · " + LEVEL_INFO[selLevel - 1][1], W / 2f, H * 0.28f, Hud.GOLD);
        g.setFont(Hud.font(24 * s, true));
        Hud.centerText(g, "¿Cómo quieres jugar?", W / 2f, H * 0.38f, Color.WHITE);
        button(g, "JUGAR SOLO", W / 2f, H * 0.5f, 440 * s, 66 * s, () -> { pendingLevel = selLevel; startMulti = false; beginLoad(true); }, new Color(40, 150, 70));
        button(g, "CON ALGUIEN MÁS (MULTIJUGADOR)", W / 2f, H * 0.62f, 440 * s, 66 * s, () -> state = S.MPMENU, new Color(50, 100, 210));
        button(g, "VOLVER", W / 2f, H * 0.78f, 300 * s, 50 * s, () -> state = S.LEVELS, new Color(70, 70, 80));
        handleButtons();
    }

    private void mpMenu(Graphics2D g, int W, int H, float s) {
        backdrop(g, "menu", W, H, 0.6f);
        g.setFont(Hud.font(44 * s, true));
        Hud.centerText(g, "MULTIJUGADOR · NIVEL " + selLevel, W / 2f, H * 0.2f, new Color(255, 190, 40));
        g.setFont(Hud.font(16 * s, false));
        Hud.centerText(g, "Uno de los dos crea la partida y el otro se une escribiendo su IP (misma red Wi-Fi/LAN, o por internet con VPN tipo Radmin/Hamachi).", W / 2f, H * 0.28f, new Color(210, 215, 235));
        button(g, "CREAR PARTIDA (SOY EL ANFITRIÓN)", W / 2f, H * 0.42f, 520 * s, 66 * s, this::startHosting, new Color(40, 150, 70));
        button(g, "UNIRSE A UNA PARTIDA", W / 2f, H * 0.54f, 520 * s, 66 * s, () -> { ipText = save.lastIp; netStatus = ""; state = S.JOIN; }, new Color(50, 100, 210));
        button(g, "VOLVER", W / 2f, H * 0.72f, 300 * s, 50 * s, () -> state = S.MODE, new Color(70, 70, 80));
        handleButtons();
    }

    private void hostScreen(Graphics2D g, int W, int H, float s) {
        backdrop(g, "menu", W, H, 0.6f);
        g.setFont(Hud.font(44 * s, true));
        Hud.centerText(g, "SALA DEL ANFITRIÓN · NIVEL " + selLevel, W / 2f, H * 0.17f, new Color(255, 190, 40));
        g.setColor(new Color(8, 6, 16, 220)); g.fillRoundRect((int) (W / 2 - 330 * s), (int) (H * 0.23f), (int) (660 * s), (int) (210 * s), 24, 24);
        g.setFont(Hud.font(18 * s, true));
        Hud.centerText(g, "Dile al otro jugador que se una con una de estas direcciones:", W / 2f, H * 0.23f + 36 * s, Color.WHITE);
        g.setFont(Hud.font(30 * s, true));
        float yy = H * 0.23f + 84 * s;
        int shown = 0;
        for (String ip : Net.localAddresses()) { if (shown++ > 3) break; Hud.centerText(g, ip + ":" + Net.PORT, W / 2f, yy, new Color(120, 230, 255)); yy += 40 * s; }
        g.setFont(Hud.font(13 * s, false));
        Hud.centerText(g, "Si no se conecta: abre el puerto " + Net.PORT + " (TCP) en el firewall o usa una VPN (Radmin/Hamachi).", W / 2f, H * 0.23f + 198 * s, new Color(190, 190, 210));
        g.setFont(Hud.font(26 * s, true));
        if (clientReady) {
            Hud.centerText(g, "¡JUGADOR 2 CONECTADO!", W / 2f, H * 0.56f, new Color(100, 255, 130));
            button(g, "COMENZAR PARTIDA", W / 2f, H * 0.68f, 460 * s, 66 * s, this::hostStart, new Color(40, 160, 70));
        } else {
            String dots = ".".repeat(1 + (int) (time * 2) % 3);
            Hud.centerText(g, "Esperando al otro jugador" + dots, W / 2f, H * 0.56f, new Color(255, 220, 120));
            if (!netStatus.isEmpty()) { g.setFont(Hud.font(15 * s, true)); Hud.centerText(g, netStatus, W / 2f, H * 0.62f, new Color(255, 120, 100)); }
        }
        button(g, "CANCELAR", W / 2f, H * 0.82f, 300 * s, 50 * s, () -> { closeNet(); state = S.MPMENU; }, new Color(170, 50, 50));
        handleButtons();
    }

    private void joinScreen(Graphics2D g, int W, int H, float s) {
        backdrop(g, "menu", W, H, 0.6f);
        g.setFont(Hud.font(44 * s, true));
        Hud.centerText(g, "UNIRSE A UNA PARTIDA", W / 2f, H * 0.2f, new Color(255, 190, 40));
        g.setFont(Hud.font(18 * s, false));
        Hud.centerText(g, "Escribe la IP que te muestra el anfitrión (ej. 192.168.1.20 o 192.168.1.20:7777). Ctrl+V pega.", W / 2f, H * 0.29f, Color.WHITE);
        float bw = 560 * s, bh = 62 * s, bx = W / 2f - bw / 2, by = H * 0.38f;
        g.setColor(new Color(8, 6, 16, 230)); g.fillRoundRect((int) bx, (int) by, (int) bw, (int) bh, 16, 16);
        g.setColor(new Color(255, 200, 60)); g.setStroke(new BasicStroke(3f)); g.drawRoundRect((int) bx, (int) by, (int) bw, (int) bh, 16, 16);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, (int) (32 * s)));
        String shown = ipText + (((int) (time * 2)) % 2 == 0 ? "_" : " ");
        Hud.shadowText(g, shown, bx + 16 * s, by + 42 * s, Color.WHITE);
        button(g, "CONECTAR", W / 2f, H * 0.58f, 400 * s, 62 * s, this::connect, new Color(40, 150, 70));
        button(g, "VOLVER", W / 2f, H * 0.72f, 300 * s, 50 * s, () -> state = S.MPMENU, new Color(70, 70, 80));
        handleButtons();
    }

    private void clientWait(Graphics2D g, int W, int H, float s) {
        backdrop(g, "menu", W, H, 0.6f);
        g.setFont(Hud.font(40 * s, true));
        Hud.centerText(g, "MULTIJUGADOR", W / 2f, H * 0.3f, new Color(255, 190, 40));
        g.setFont(Hud.font(22 * s, true));
        boolean err = netStatus.startsWith("ERROR");
        Hud.centerText(g, netStatus.isEmpty() ? "Conectando..." : netStatus, W / 2f, H * 0.45f, err ? new Color(255, 110, 90) : new Color(120, 230, 255));
        button(g, err ? "VOLVER" : "CANCELAR", W / 2f, H * 0.65f, 320 * s, 54 * s, () -> { closeNet(); state = S.JOIN; }, new Color(170, 50, 50));
        handleButtons();
    }

    private void loading(Graphics2D g, int W, int H, float s) {
        backdrop(g, "menu", W, H, 0.6f);
        g.setFont(Hud.font(80 * s, true));
        Hud.centerText(g, "NIVEL " + pendingLevel, W / 2f, H * 0.42f, new Color(255, 80, 40));
        g.setFont(Hud.font(24 * s, true));
        Hud.centerText(g, "Entrando a la ciudad...", W / 2f, H * 0.52f, Color.WHITE);
        String[] tips = {"Los fantasmas cambian entre perseguirte y dispersarse, aprovecha esos momentos.", "Las estrellas te hacen más rápido que cualquier fantasma.", "Un cofre puede ocultar la llave de la puerta.", "Las granadas matan a todo lo que esté en la explosión... ¡también a ti!"};
        g.setFont(Hud.font(17 * s, false));
        Hud.centerText(g, "Consejo: " + tips[(pendingLevel + (int) time) % tips.length], W / 2f, H * 0.62f, new Color(255, 220, 140));
    }

    private void pauseMenu(Graphics2D g, int W, int H, float s) {
        g.setColor(new Color(0, 0, 0, 170)); g.fillRect(0, 0, W, H);
        g.setFont(Hud.font(64 * s, true));
        Hud.centerText(g, "PAUSA", W / 2f, H * 0.28f, new Color(255, 190, 40));
        button(g, "CONTINUAR", W / 2f, H * 0.45f, 400 * s, 60 * s, this::resume, new Color(40, 150, 70));
        button(g, "ABANDONAR PARTIDA", W / 2f, H * 0.57f, 400 * s, 60 * s, this::toMenu, new Color(190, 40, 40));
        g.setFont(Hud.font(15 * s, false));
        Hud.centerText(g, "ESC para continuar", W / 2f, H * 0.7f, new Color(200, 200, 215));
        handleButtons();
    }

    private void results(Graphics2D g, int W, int H, float s) {
        final Player me = game.me;
        backdrop(g, "results", W, H, 0.45f);
        boolean fin = state == S.FINAL;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (92 * s)));
        float bob = (float) Math.sin(time * 3) * 6 * s;
        String t = fin ? "¡GANASTE EL JUEGO!" : "¡GANASTE!";
        FontMetrics fm = g.getFontMetrics();
        g.setColor(new Color(120, 60, 0)); g.drawString(t, W / 2f - fm.stringWidth(t) / 2f + 5, 128 * s + bob + 5);
        g.setPaint(new GradientPaint(0, 60 * s, new Color(255, 250, 120), 0, 130 * s, new Color(255, 150, 20)));
        g.drawString(t, W / 2f - fm.stringWidth(t) / 2f, 128 * s + bob);
        int pw = (int) (620 * s), ph = (int) (300 * s), px = (W - pw) / 2, py = (int) (170 * s);
        g.setColor(new Color(8, 6, 16, 215)); g.fillRoundRect(px, py, pw, ph, 24, 24);
        g.setColor(new Color(255, 180, 50)); g.setStroke(new BasicStroke(3f)); g.drawRoundRect(px, py, pw, ph, 24, 24);
        g.setFont(Hud.font(24 * s, true));
        Hud.centerText(g, fin ? "RESUMEN DE LA PARTIDA" : "NIVEL " + game.levelIdx + " COMPLETADO", W / 2f, py + 40 * s, new Color(255, 140, 70));
        float y = py + 90 * s, x1 = px + 40 * s, x2 = px + pw - 40 * s;
        String[][] rows = {{"VELOCIDAD MÁXIMA", (int) me.maxSpeedPct + "%"},
                {"FANTASMAS ELIMINADOS", (fin ? me.killedTotal : me.killedLevel) + (fin ? "" : " / " + game.level.ghostCount)},
                {"ESTRELLAS RECOGIDAS", String.valueOf(fin ? me.starsTotal : me.starsLevel)},
                {"VIDAS QUE TE QUEDARON", ""}};
        for (String[] r : rows) {
            g.setFont(Hud.font(21 * s, true));
            Hud.shadowText(g, r[0], x1, y, Color.WHITE);
            g.setFont(Hud.font(26 * s, true));
            FontMetrics f2 = g.getFontMetrics();
            if (!r[1].isEmpty()) Hud.shadowText(g, r[1], x2 - f2.stringWidth(r[1]), y, Hud.GOLD);
            else {
                float[] c = Palette.RGB[me.colorIndex];
                for (int i = 0; i < Game.START_LIVES; i++) Hud.pacIcon(g, x2 - 16 * s - i * 40 * s, y - 8 * s, 15 * s, i < me.lives ? new Color(c[0], c[1], c[2]) : new Color(60, 60, 70), time + i);
            }
            y += 54 * s;
        }
        if (!fin) { g.setFont(Hud.font(15 * s, false)); Hud.centerText(g, "Tiempo restante: " + (int) game.timeLeft / 60 + ":" + String.format("%02d", (int) game.timeLeft % 60), W / 2f, py + ph - 14 * s, new Color(190, 200, 230)); }
        if (game.multi) {
            Player o = game.other();
            g.setFont(Hud.font(15 * s, true));
            Hud.centerText(g, "Compañero (Jugador " + (o.id + 1) + "): " + (fin ? o.killedTotal : o.killedLevel) + " fantasmas · " + (fin ? o.starsTotal : o.starsLevel) + " estrellas · " + o.lives + " vidas", W / 2f, py + ph + 24 * s, new Color(150, 220, 255));
        }
        if (game.mirror) { g.setFont(Hud.font(22 * s, true)); Hud.centerText(g, "Esperando al anfitrión...", W / 2f, H - 80 * s, new Color(255, 220, 120)); button(g, "SALIR AL MENÚ", W / 2f, H - 40 * s, 300 * s, 44 * s, this::toMenu, new Color(70, 80, 160)); handleButtons(); return; }
        if (fin) button(g, "VOLVER AL MENÚ", W / 2f, H - 90 * s, 400 * s, 60 * s, this::toMenu, new Color(60, 90, 200));
        else button(g, "SIGUIENTE NIVEL  ▶", W / 2f, H - 90 * s, 400 * s, 60 * s, this::nextLevel, new Color(40, 160, 70));
        g.setFont(Hud.font(14 * s, false));
        Hud.centerText(g, "ENTER para continuar", W / 2f, H - 40 * s, new Color(200, 200, 215));
        handleButtons();
    }

    private void gameOver(Graphics2D g, int W, int H, float s) {
        backdrop(g, "menu", W, H, 0.6f);
        g.setColor(new Color(110, 0, 0, 90)); g.fillRect(0, 0, W, H);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (110 * s)));
        FontMetrics fm = g.getFontMetrics();
        float jit = rnd.nextInt(20) == 0 ? 5 : 0;
        g.setColor(new Color(230, 20, 20)); g.drawString("GAME OVER", W / 2f - fm.stringWidth("GAME OVER") / 2f + jit, H * 0.33f);
        g.setFont(Hud.font(30 * s, true));
        Hud.centerText(g, game.overReason, W / 2f, H * 0.43f, Color.WHITE);
        g.setFont(Hud.font(22 * s, false));
        Hud.centerText(g, "Los fantasmas ganaron esta vez... volverás al NIVEL 1", W / 2f, H * 0.5f, new Color(255, 190, 150));
        Hud.centerText(g, "Fantasmas eliminados: " + game.me.killedTotal + "     Estrellas: " + game.me.starsTotal, W / 2f, H * 0.56f, Hud.GOLD);
        if (game.mirror) { g.setFont(Hud.font(22 * s, true)); Hud.centerText(g, "Esperando al anfitrión...", W / 2f, H * 0.68f, new Color(255, 220, 120)); }
        else button(g, "REINTENTAR (NIVEL 1)", W / 2f, H * 0.68f, 440 * s, 60 * s, this::retry, new Color(200, 40, 40));
        button(g, "MENÚ PRINCIPAL", W / 2f, H * 0.78f, 440 * s, 60 * s, this::toMenu, new Color(70, 80, 160));
        handleButtons();
    }
}
