package com.pacman3d.game;

import com.pacman3d.audio.Audio;
import com.pacman3d.ui.Input;
import com.pacman3d.world.Level;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Reglas completas del juego para 1 o 2 jugadores. El anfitrion (o el juego en solitario) simula todo;
 * el cliente multijugador usa una copia "espejo" (mirror) que solo aplica las instantaneas de red.
 */
public final class Game {
    public static final int PLAY = 0, LEVEL_DONE = 2, GAME_OVER = 3, ALL_DONE = 4;
    public static final float BASE_SPEED = 7.0f, LEVEL_TIME = 300f;
    public static final int MAX_HP = 500, START_LIVES = 3, SLOTS = 8, START_AMMO = 24;
    // eventos de red
    private static final int EV_SOUND = 1, EV_BURST = 2, EV_MSG = 3, EV_BIG = 4, EV_HURT = 5, EV_FLASH = 6, EV_SHAKE = 7, EV_HIT = 8, EV_PICK = 9, EV_LIGHT = 10, EV_DECAL = 11, EV_RING = 12, EV_DRY = 13, EV_KEYHINT = 14, EV_BLOOD = 15, EV_TRAPFX = 16;

    public static final class Msg { public String text; public int color; public float t; }

    public Level level;
    public int levelIdx, state = PLAY;
    public String overReason = "";
    public final Random rnd;
    public final long runSeed;
    public final Player[] players;
    public final Player me;
    public final boolean multi, mirror;
    public boolean hasKey;
    public float timeLeft = LEVEL_TIME;

    // ---- mundo
    public final List<Ghost> ghosts = new ArrayList<>();
    public final List<Chest> chests = new ArrayList<>();
    public final List<Pickup> pickups = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<Particle> particles = new ArrayList<>();
    public final List<Msg> messages = new ArrayList<>();
    public final List<float[]> decals = new ArrayList<>();   // x, z, edad (restos de ectoplasma)
    public final List<float[]> blood = new ArrayList<>();    // x, z, tamano, rotacion (sangre permanente)
    public final List<Soul> souls = new ArrayList<>();
    public final List<ShopChest> shops = new ArrayList<>();
    private final Map<Integer, Soul> soulById = new HashMap<>();
    public boolean[][] seen;
    public float time, flash, lightning, bigT, doorAnim;
    public String bigMsg = "";
    public int bigColor = 0xFF4040;
    public int ghostsAlive;
    public float rainTime;
    public float jumpT, jumpNext = 40f;     // susto animado (solo visual, local)
    private int nextSoulId;
    private float soulRespawnT;

    // ---- IA estilo Pac-Man
    private float modeT;
    private int modeIdx;
    public boolean chase;
    private static final float[] SCHEDULE = {4, 26, 3, 28, 3, 30, 2, 1e9f};   // menos dispersion, mas persecucion
    private float scareT = 4, thunderT = 30, stepT, heartT, ghostVoiceCd;
    private final Map<Integer, Ghost> ghostById = new HashMap<>();

    // ---- red
    private final ByteArrayOutputStream evBuf = new ByteArrayOutputStream();
    private final DataOutputStream ev = new DataOutputStream(evBuf);
    private int evCount;
    public boolean netActive;   // anfitrion con un cliente conectado

    public Game(int startLevel, int[] colors, long seed, int meIndex, boolean mirror) {
        this.runSeed = seed;
        this.rnd = new Random(seed);
        this.mirror = mirror;
        this.multi = colors.length > 1;
        players = new Player[colors.length];
        for (int i = 0; i < colors.length; i++) {
            Player p = new Player();
            p.id = i; p.colorIndex = colors[i];
            p.remote = i != meIndex && !mirror;
            players[i] = p;
            giveStartKit(p);
        }
        me = players[meIndex];
        startLevel(startLevel);
    }

    public Game(int startLevel, int color, long seed) { this(startLevel, new int[]{color}, seed, 0, false); }

    private void giveStartKit(Player p) {
        p.inv[0] = Weapon.PISTOL; p.sel = 0; p.ammo = START_AMMO;
    }

    public Player other() { return players.length > 1 ? players[1 - me.id] : null; }

    // ===================================================================== niveles
    public void startLevel(int idx) {
        levelIdx = idx;
        level = new Level(idx, runSeed);
        state = PLAY; hasKey = false;
        ghosts.clear(); ghostById.clear(); chests.clear(); pickups.clear(); projectiles.clear(); particles.clear(); messages.clear(); decals.clear();
        blood.clear(); souls.clear(); soulById.clear(); shops.clear();
        timeLeft = LEVEL_TIME; doorAnim = 0; modeT = 0; modeIdx = 0; chase = false; jumpT = 0; jumpNext = 35f + rnd.nextFloat() * 25f;
        seen = new boolean[level.maze.W][level.maze.H];
        for (Player p : players) {
            p.px = Level.center(level.maze.spawnX + (p.id == 0 ? -1 : 1)); p.pz = Level.center(level.maze.spawnZ) + (p.id == 0 ? 0 : 1.2f);
            p.sx = p.px; p.sz = p.pz;
            p.yaw = p.id == 0 ? 1.5708f : -1.5708f; p.pitch = 0; p.syaw = p.yaw;
            if (p.state == Player.OUT) p.lives = 1;
            p.state = Player.ALIVE;
            p.hp = MAX_HP; p.immuneT = 0; p.invulnT = 3f; p.eyeDrop = 0; p.vx = p.vz = 0;
            p.killedLevel = 0; p.starsLevel = 0; p.maxSpeedPct = Math.max(100, p.speedPct());
            p.fireCd = 0; p.swing = -1; p.throwT = -1; p.bowDraw = 0; p.spin = 0;
            p.hurtFlash = 0; p.shake = 0;
        }
        if (!mirror) {
            int[] types = new int[level.ghostSpots.size()];
            for (int i = 0; i < types.length; i++) types[i] = i % 4;
            for (int i = 0; i < level.ghostSpots.size(); i++) {
                float[] s = level.ghostSpots.get(i);
                Ghost g = new Ghost();
                g.id = i; g.type = types[i]; g.x = s[0]; g.z = s[1];
                g.tx = Level.tileOf(g.x); g.tz = Level.tileOf(g.z);
                g.hp = g.maxHp = level.ghostHp * (multi ? 1.25f : 1f);
                g.fast = i % 5 == 2;                                        // 1 de cada 5 es veloz
                g.speed = level.ghostSpeed * (0.95f + rnd.nextFloat() * 0.15f) * (g.fast ? 1.3f : 1f) * (g.type == 0 ? 1.06f : 1f);
                g.phase = rnd.nextFloat() * 6.28f;
                g.laneSide = (rnd.nextFloat() * 2 - 1) * 0.75f;
                g.atkCd = 1f;
                int[][] D = {{0, -1}, {-1, 0}, {0, 1}, {1, 0}};
                for (int k = 0; k < 4; k++) {
                    int[] d = D[(k + rnd.nextInt(4)) % 4];
                    if (!ghostBlocked(g.tx + d[0], g.tz + d[1])) { g.dx = d[0]; g.dz = d[1]; break; }
                }
                g.nx = g.tx + g.dx; g.nz = g.tz + g.dz;
                ghosts.add(g); ghostById.put(g.id, g);
            }
            ghostsAlive = ghosts.size();
        } else ghostsAlive = level.ghostCount;
        for (float[] sp : level.shopSpots) {
            ShopChest sc = new ShopChest();
            float dx = sp[4], dz = sp[5];
            sc.x = sp[0] + dx * 1.0f; sc.z = sp[1] + dz * 1.0f; sc.yaw = sp[2]; sc.style = (int) sp[3];
            sc.tx = sp[0] - dx * 0.85f; sc.tz = sp[1] - dz * 0.85f;
            shops.add(sc);
        }
        if (!mirror) {
            for (int i = 0; i < level.soulSpots.size(); i++) {
                float[] sp = level.soulSpots.get(i);
                Soul so = new Soul();
                so.id = i; so.female = rnd.nextBoolean(); so.variant = rnd.nextInt(4);
                so.x = sp[0]; so.z = sp[1]; so.tx = Level.tileOf(so.x); so.tz = Level.tileOf(so.z);
                so.speed = 4.5f + rnd.nextFloat() * 0.9f; so.phase = rnd.nextFloat() * 6.28f;
                int[][] D = {{0, -1}, {-1, 0}, {0, 1}, {1, 0}};
                for (int k = 0; k < 4; k++) { int[] d = D[(k + rnd.nextInt(4)) % 4]; if (!ghostBlocked(so.tx + d[0], so.tz + d[1])) { so.dx = d[0]; so.dz = d[1]; break; } }
                so.nx = so.tx + so.dx; so.nz = so.tz + so.dz;
                souls.add(so); soulById.put(so.id, so);
            }
            nextSoulId = level.soulSpots.size(); soulRespawnT = 1f;
        }
        int keyChest = level.keyInChest ? rnd.nextInt(Math.max(1, level.chestSpots.size())) : -1;
        for (int i = 0; i < level.chestSpots.size(); i++) {
            float[] s = level.chestSpots.get(i);
            Chest c = new Chest();
            c.x = s[0]; c.z = s[1]; c.yaw = s[2]; c.hasKey = i == keyChest;
            chests.add(c);
        }
        for (float[] s : level.starSpots) addPickup(Pickup.STAR, s, 1);
        for (float[] s : level.ammoSpots) addPickup(Pickup.AMMO, s, 15 + rnd.nextInt(26));
        for (float[] s : level.medSpots) addPickup(Pickup.MED, s, 150);
        if (level.keySpot != null) addPickup(Pickup.KEY, level.keySpot, 1);
        if (multi) { for (Player p : players) if (p.state == Player.ALIVE) {} }
        say(null, "NIVEL " + idx + ": ¡SOBREVIVE! Mata a todos los fantasmas o encuentra la LLAVE", 0xFFD040);
        big(null, "NIVEL " + idx, 0xFF5030, 3f);
    }

    private void addPickup(int kind, float[] s, int amount) {
        Pickup p = new Pickup();
        p.kind = kind; p.x = s[0]; p.z = s[1]; p.amount = amount; p.phase = rnd.nextFloat() * 6.28f;
        pickups.add(p);
    }

    public float timeFrac() { return timeLeft / LEVEL_TIME; }

    // ===================================================================== mensajes y efectos (con eventos de red)
    public void say(Player p, String t, int color) {
        if (p == null || p == me) {
            Msg m = new Msg(); m.text = t; m.color = color; m.t = 4.5f;
            messages.add(0, m);
            while (messages.size() > 5) messages.remove(messages.size() - 1);
        }
        if (netActive && (p == null || p.remote)) {
            try { ev.writeByte(EV_MSG); ev.writeByte(p == null ? -1 : p.id); ev.writeUTF(t); ev.writeInt(color); evCount++; } catch (IOException ignored) { }
        }
    }

    public void big(Player p, String t, int color, float secs) {
        if (p == null || p == me) { bigMsg = t; bigColor = color; bigT = secs; }
        if (netActive && (p == null || p.remote)) {
            try { ev.writeByte(EV_BIG); ev.writeByte(p == null ? -1 : p.id); ev.writeUTF(t); ev.writeInt(color); ev.writeFloat(secs); evCount++; } catch (IOException ignored) { }
        }
    }

    /** Sonido posicional. src = quien lo produce (su propio sonido suena a volumen completo). */
    public void sound(String name, float vol, float x, float z, Player src) {
        playLocal(name, vol, x, z, src == null ? -1 : src.id, me);
        if (netActive) {
            try { ev.writeByte(EV_SOUND); ev.writeUTF(name); ev.writeFloat(vol); ev.writeFloat(x); ev.writeFloat(z); ev.writeByte(src == null ? -1 : src.id); evCount++; } catch (IOException ignored) { }
        }
    }

    /** Sonido no posicional para todos. */
    public void soundAll(String name, float vol) { sound(name, vol, Float.NaN, Float.NaN, null); }

    private static void playLocal(String name, float vol, float x, float z, int srcId, Player viewer) {
        if (Float.isNaN(x) || srcId == viewer.id) { Audio.play(name, vol, 0f); return; }
        float dx = x - viewer.px, dz = z - viewer.pz, d = (float) Math.sqrt(dx * dx + dz * dz);
        float v = vol * Math.max(0f, 1f - d / 38f);
        if (v < 0.02f) return;
        float rx = (float) Math.cos(viewer.yaw), rz = -(float) Math.sin(viewer.yaw);
        float pan = d > 0.1f ? Math.max(-1, Math.min(1, (dx * rx + dz * rz) / d * 0.9f)) : 0f;
        Audio.play(name, v, pan);
    }

    private void fxFlash(float v) {
        flash = Math.max(flash, v);
        if (netActive) { try { ev.writeByte(EV_FLASH); ev.writeFloat(v); evCount++; } catch (IOException ignored) { } }
    }

    private void fxPlayer(Player p, int type, float a, float b, float c) {
        if (p == me) applyPlayerFx(p, type, a, b, c);
        else if (netActive && p.remote) { try { ev.writeByte(type); ev.writeByte(p.id); ev.writeFloat(a); ev.writeFloat(b); ev.writeFloat(c); evCount++; } catch (IOException ignored) { } }
    }

    private void applyPlayerFx(Player p, int type, float a, float b, float c) {
        switch (type) {
            case EV_SHAKE: p.shake = Math.max(p.shake, a); break;
            case EV_HURT: p.hurtFlash = Math.min(1f, a); p.shake = Math.max(p.shake, 0.6f); p.hurtDir = (float) Math.atan2(b - p.px, c - p.pz); break;
            case EV_HIT: p.hitMarker = a; break;
            case EV_PICK: p.pickFlash = Math.max(p.pickFlash, a); break;
            case EV_DRY: p.dryBanner = a; break;
            case EV_TRAPFX: p.hurtFlash = Math.max(p.hurtFlash, a); p.shake = Math.max(p.shake, 0.25f); break;
        }
    }

    // ===================================================================== bucle
    public void update(float dt, Input in) {
        dt = Math.min(dt, 0.05f);
        time += dt; rainTime += dt;
        decayEffects(dt);
        if (state == PLAY) {
            for (Player p : players) updatePlayer(p, p == me ? in : null, dt);
            timeLeft -= dt;
            thunderT -= dt;
            if (thunderT <= 0) {
                thunderT = 25 + rnd.nextFloat() * 30;
                lightning = 1f; fxFlash(0.6f);
                soundAll("thunder", 0.55f);
                if (netActive) { try { ev.writeByte(EV_LIGHT); evCount++; } catch (IOException ignored) { } }
            }
            checkTeam();
        } else {
            for (Player p : players) if (p.state == Player.DYING) updateDying(p, dt);
        }
        updateProjectiles(dt);
        updateParticles(dt);
        updateGhosts(dt);
        updateSouls(dt);
        animateWorld(dt);
    }

    private void decayEffects(float dt) {
        if (jumpT > 0) { jumpT += dt; if (jumpT > 1.9f) jumpT = 0f; }
        flash = Math.max(0, flash - dt * 5f);
        lightning = Math.max(0, lightning - dt * 2.2f);
        bigT = Math.max(0, bigT - dt);
        for (Player p : players) {
            p.shake = Math.max(0, p.shake - dt * 3f); p.hurtFlash = Math.max(0, p.hurtFlash - dt * 2.2f);
            p.pickFlash = Math.max(0, p.pickFlash - dt * 3f); p.dryBanner = Math.max(0, p.dryBanner - dt);
            p.keyHintT = Math.max(0, p.keyHintT - dt);
            p.zoomT += ((p.zoom ? 1f : 0f) - p.zoomT) * Math.min(1f, dt * 9f);
            p.muzzle = Math.max(0, p.muzzle - dt * 12f); p.recoil = Math.max(0, p.recoil - dt * 7f); p.hitMarker = Math.max(0, p.hitMarker - dt * 4f);
        }
        for (int i = messages.size() - 1; i >= 0; i--) { messages.get(i).t -= dt; if (messages.get(i).t <= 0) messages.remove(i); }
        for (int i = decals.size() - 1; i >= 0; i--) { decals.get(i)[2] += dt; if (decals.get(i)[2] > 25f) decals.remove(i); }
    }

    private void animateWorld(float dt) {
        for (Chest c : chests) if (c.open && c.openT < 1) c.openT = Math.min(1, c.openT + dt * 2.2f);
        for (ShopChest sc : shops) if (sc.anim > 0) sc.anim = Math.max(0, sc.anim - dt);
        if (level.doorOpen && doorAnim < 1) doorAnim = Math.min(1, doorAnim + dt * 0.7f);
    }

    /** Fin de partida del equipo y fin de nivel. */
    private void checkTeam() {
        boolean anyIn = false, anyLeft = false;
        for (Player p : players) { if (p.state != Player.OUT) anyLeft = true; }
        if (!anyLeft) { gameOver("Los fantasmas atraparon a todos"); return; }
        if (timeLeft <= 0) { timeLeft = 0; gameOver("¡SE ACABÓ EL TIEMPO!"); return; }
        // puerta
        float gdx = 0, gdz = 0;
        if (!level.doorOpen) {
            boolean none = true;
            for (Ghost g : ghosts) if (!g.dying) { none = false; break; }
            if (none && !ghosts.isEmpty() || (none && ghostsAlive == 0 && killedAny())) openDoor("¡LA PUERTA SE ABRIÓ!");
            else for (Player p : players) {
                if (p.state != Player.ALIVE) continue;
                float d = (float) Math.hypot(Level.center(level.maze.doorX) - p.px, Level.center(level.maze.doorZ) - p.pz);
                p.promptDoor = false;
                if (hasKey && d < 8f) { openDoor("Usaste la LLAVE: ¡la puerta se abre!"); break; }
                else if (d < 7f) p.promptDoor = true;
            }
        } else for (Player p : players) if (p.state == Player.ALIVE && level.isInDoor(p.px, p.pz)) { completeLevel(p); break; }
    }

    private boolean killedAny() { int k = 0; for (Player p : players) k += p.killedLevel; return k > 0; }

    private void openDoor(String why) {
        level.doorOpen = true;
        soundAll("key", 0.8f); soundAll("chest", 0.5f);
        say(null, why, 0x60FF80);
        big(null, "¡PUERTA ABIERTA!", 0x60FF80, 2f);
    }

    private void completeLevel(Player by) {
        state = levelIdx >= 3 ? ALL_DONE : LEVEL_DONE;
        if (multi) say(null, "Jugador " + (by.id + 1) + " cruzó la puerta: ¡nivel completado para LOS DOS!", 0x80FF90);
        for (Player p : players) stopMini(p);
        soundAll("victory", 0.95f);
        big(null, "¡GANASTE!", 0xFFE040, 4f);
    }

    private void gameOver(String reason) {
        if (state == GAME_OVER) return;
        state = GAME_OVER; overReason = reason;
        for (Player p : players) stopMini(p);
        soundAll("lose", 0.9f);
    }

    // ===================================================================== jugador
    private void updatePlayer(Player p, Input in, float dt) {
        if (p.state == Player.DYING) { updateDying(p, dt); return; }
        if (p.state == Player.OUT) return;
        boolean fire; int slot, wheel; boolean interact;
        if (!p.remote) {
            moveLocal(p, in, dt);
            fire = in.fire; slot = in.consumeSlot(); wheel = in.consumeWheel(); interact = in.consumeInteract();
        } else {
            float nx = p.rx, nz = p.rz;
            if (p.rfresh && !blocked(nx, nz, 0.5f)) { p.vx = (nx - p.px) / Math.max(dt, 0.001f); p.vz = (nz - p.pz) / Math.max(dt, 0.001f); p.px = nx; p.pz = nz; }
            p.rfresh = false;
            p.vx = Math.max(-25, Math.min(25, p.vx)); p.vz = Math.max(-25, Math.min(25, p.vz));
            p.yaw = p.ryaw; p.pitch = p.rpitch;
            { Weapon cw = p.current(); p.zoom = p.rzoom && cw != null && cw.zoom > 1.01f; }
            float speed = (float) Math.hypot(p.vx, p.vz);
            p.bobT += speed * dt * 1.35f; p.walkAnim += speed * dt;
            fire = p.rfire; slot = p.rslot; p.rslot = -1; wheel = p.rwheel; p.rwheel = 0;
            interact = p.rinteract > 0; if (interact) p.rinteract--;
        }
        float pct = p.speedPct();
        if (pct > p.maxSpeedPct) p.maxSpeedPct = pct;
        if (p.immuneT > 0) { p.immuneT -= dt; if (p.immuneT <= 0) say(p, "La inmunidad se acabó", 0x60B0FF); }
        if (p.invulnT > 0) p.invulnT -= dt;
        if (p == me) explore(p);
        if (slot >= 0) selectSlot(p, slot);
        if (wheel != 0) cycleWeapon(p, wheel);
        handleWeapon(p, fire, dt);
        applyTrap(p, dt);
        p.promptChest = false; p.promptShop = false;
        ShopChest shop = null; float sbest = 3.4f * 3.4f;
        for (ShopChest sc : shops) { float dx = sc.x - p.px, dz = sc.z - p.pz, d2 = dx * dx + dz * dz; if (d2 < sbest) { sbest = d2; shop = sc; } }
        Chest near = null; float best = 3.6f * 3.6f;
        for (Chest c : chests) {
            if (c.open) continue;
            float dx = c.x - p.px, dz = c.z - p.pz, d2 = dx * dx + dz * dz;
            if (d2 < best) { best = d2; near = c; }
        }
        if (shop != null && (near == null || sbest <= best)) { p.promptShop = true; p.shopPrice = shopPrice(); if (interact) buyFromShop(p, shop); }
        else if (near != null) { p.promptChest = true; if (interact) openChest(p, near); }
        collectPickups(p);
        if (p == me) ambient(p, dt);
        if (p.hp <= 0) die(p);
    }

    /** Movimiento y mirada del jugador local (tambien lo usa el cliente espejo). */
    public void moveLocal(Player p, Input in, float dt) {
        Weapon cw = p.current();
        p.zoom = in.fireAlt && p.state == Player.ALIVE && cw != null && cw.zoom > 1.01f;
        float zf = cw == null ? 1f : 1f + (cw.zoom - 1f) * p.zoomT;
        float[] m = in.consumeMouse();
        p.yaw += m[0] * 0.0024f / zf;
        p.pitch -= m[1] * 0.0024f / zf;
        p.yaw += ((in.right ? 1 : 0) - (in.left ? 1 : 0)) * 2.2f * dt;
        p.pitch += ((in.up ? 1 : 0) - (in.down ? 1 : 0)) * 1.6f * dt;
        p.pitch = Math.max(-1.25f, Math.min(1.25f, p.pitch));
        float fx = (float) Math.sin(p.yaw), fz = (float) Math.cos(p.yaw), rx = fz, rz = -fx;
        float mf = (in.w ? 1 : 0) - (in.s ? 1 : 0), mr = (in.d ? 1 : 0) - (in.a ? 1 : 0);
        float len = (float) Math.sqrt(mf * mf + mr * mr);
        float sp = BASE_SPEED * p.speedPct() / 100f;
        Weapon w = p.current();
        if (w == Weapon.MINIGUN && p.spin > 0.2f && in.fire) sp *= 0.7f;
        if (w == Weapon.BOW && p.bowDraw > 0.1f) sp *= 0.85f;
        sp *= 1f - 0.35f * p.zoomT;
        float tvx = 0, tvz = 0;
        if (len > 0) { tvx = (fx * mf + rx * mr) / len * sp; tvz = (fz * mf + rz * mr) / len * sp; }
        p.vx += (tvx - p.vx) * Math.min(1, dt * 12f); p.vz += (tvz - p.vz) * Math.min(1, dt * 12f);
        float speed = (float) Math.hypot(p.vx, p.vz);
        if (speed > 0.5f) { p.dirX = p.vx / speed; p.dirZ = p.vz / speed; }
        moveCollide(p, p.vx * dt, p.vz * dt);
        p.bobT += speed * dt * 1.35f; p.walkAnim += speed * dt;
        p.bob = (float) Math.sin(p.bobT) * Math.min(1, speed / BASE_SPEED) * 0.07f;
        stepT -= dt * speed;
        if (stepT <= 0 && speed > 1f) { stepT = 4.2f; Audio.play("step", 0.30f, (float) Math.sin(p.bobT) * 0.3f); }
    }

    private void explore(Player p) {
        int cx = Level.tileOf(p.px), cz = Level.tileOf(p.pz);
        for (int x = cx - 3; x <= cx + 3; x++)
            for (int z = cz - 3; z <= cz + 3; z++)
                if (x >= 0 && z >= 0 && x < level.maze.W && z < level.maze.H && (x - cx) * (x - cx) + (z - cz) * (z - cz) <= 10) seen[x][z] = true;
    }

    // ---------------------------------------------------------------- colisiones
    public boolean blocked(float x, float z, float r) {
        if (level.solidAt(x - r, z - r) || level.solidAt(x + r, z - r) || level.solidAt(x - r, z + r) || level.solidAt(x + r, z + r)) return true;
        for (float[] b : level.blockers) { float dx = x - b[0], dz = z - b[1], rr = b[2] + r * 0.7f; if (dx * dx + dz * dz < rr * rr) return true; }
        return false;
    }

    private void moveCollide(Player p, float mx, float mz) {
        float r = 0.65f;
        if (!blocked(p.px + mx, p.pz, r)) p.px += mx; else p.vx *= 0.2f;
        if (!blocked(p.px, p.pz + mz, r)) p.pz += mz; else p.vz *= 0.2f;
    }

    // ---------------------------------------------------------------- armas
    private void selectSlot(Player p, int s) {
        if (s < 0 || s >= SLOTS || s == p.sel) return;
        p.sel = s;
        p.bowDraw = 0; p.spin = 0; p.swing = -1; p.throwT = -1; p.fireCd = 0.2f;
        if (p.inv[s] != null && p == me) Audio.play("click", 0.25f);
    }

    private void cycleWeapon(Player p, int dir) {
        int n = 0;
        for (Weapon w : p.inv) if (w != null) n++;
        if (n == 0) return;
        int s = p.sel;
        for (int i = 0; i < SLOTS; i++) { s = ((s + dir) % SLOTS + SLOTS) % SLOTS; if (p.inv[s] != null) { selectSlot(p, s); return; } }
    }

    public void selectSlotPublic(int s) { selectSlot(me, s); }

    private void handleWeapon(Player p, boolean fire, float dt) {
        Weapon w = p.current();
        p.fireCd -= dt;
        if (p.swing >= 0) {
            p.swing += dt;
            if (!p.swingHit && p.swing > 0.17f) { p.swingHit = true; swordHit(p); }
            if (p.swing > 0.45f) p.swing = -1;
        }
        if (p.throwT >= 0) { p.throwT += dt; if (p.throwT > 0.5f) p.throwT = -1; }
        if (w == null) { stopMini(p); return; }
        if (w != Weapon.MINIGUN) stopMini(p);
        if (w != Weapon.BOW) p.bowDraw = 0;
        if (w != Weapon.MINIGUN) p.spin = Math.max(0, p.spin - dt * 2f);
        if (fire && w.ammoCost > 0 && w != Weapon.BOW && p.ammo < w.ammoCost && p.fireCd <= 0) {
            fxPlayer(p, EV_DRY, 2.2f, 0, 0); p.fireCd = 0.35f;
            if (p == me) Audio.play("dry", 0.6f);
            return;
        }
        switch (w) {
            case PISTOL:
                if (fire && !p.dryCd && p.fireCd <= 0) { shootBullet(p, w, 0.012f, 0.55f); p.dryCd = true; }
                if (!fire) p.dryCd = false;
                break;
            case RIFLE:
                if (fire && p.fireCd <= 0) shootBullet(p, w, 0.022f, 0.35f);
                break;
            case MINIGUN:
                if (fire && p.ammo > 0) {
                    p.spin = Math.min(1, p.spin + dt / 0.8f);
                    if (p.miniVoice == null && p == me) p.miniVoice = Audio.play("minigun_loop", 0.5f, 0, true);
                    if (p.miniVoice != null) p.miniVoice.setVolume(0.2f + 0.45f * p.spin);
                    if (p.spin > 0.85f && p.fireCd <= 0) shootBullet(p, w, 0.055f, 0.18f);
                } else { p.spin = Math.max(0, p.spin - dt / 1.1f); stopMini(p); }
                break;
            case BOW:
                if (fire && p.fireCd <= 0) {
                    if (p.ammo < 1) { p.fireCd = 0.35f; fxPlayer(p, EV_DRY, 2.2f, 0, 0); if (p == me) Audio.play("dry", 0.6f); }
                    else p.bowDraw = Math.min(1, p.bowDraw + dt / 0.85f);
                } else if (!fire && p.bowDraw > 0) {
                    if (p.bowDraw > 0.22f && p.ammo >= 1) shootArrow(p, p.bowDraw);
                    p.bowDraw = 0;
                }
                break;
            case SWORD:
                if (fire && p.fireCd <= 0 && p.swing < 0) { p.swing = 0; p.swingHit = false; p.fireCd = w.cooldown; sound("sword", 0.7f, p.px, p.pz, p); }
                break;
            case GRENADE:
                if (fire && p.fireCd <= 0 && p.throwT < 0) throwGrenade(p);
                break;
        }
    }

    private void stopMini(Player p) { if (p.miniVoice != null) { p.miniVoice.stop(); p.miniVoice = null; } }

    private float[] aim(Player p, float spread) {
        spread *= 1f - 0.65f * p.zoomT;
        float yy = p.yaw + (rnd.nextFloat() - 0.5f) * spread * 2, pp = p.pitch + (rnd.nextFloat() - 0.5f) * spread * 2;
        float cp = (float) Math.cos(pp);
        return new float[]{(float) Math.sin(yy) * cp, (float) Math.sin(pp), (float) Math.cos(yy) * cp};
    }

    private void shootBullet(Player p, Weapon w, float spread, float kick) {
        float[] d = aim(p, spread);
        Projectile pr = new Projectile();
        pr.kind = Projectile.BULLET; pr.owner = p.id;
        pr.x = p.px + d[0] * 0.9f + (float) Math.cos(p.yaw) * 0.25f; pr.y = 1.45f + d[1] * 0.9f; pr.z = p.pz + d[2] * 0.9f - (float) Math.sin(p.yaw) * 0.25f;
        pr.vx = d[0] * w.speed; pr.vy = d[1] * w.speed; pr.vz = d[2] * w.speed;
        pr.dmg = w.damage; pr.life = 3.2f;
        projectiles.add(pr);
        p.ammo -= w.ammoCost; p.shotsFired++;
        p.fireCd = w.cooldown; p.recoil = 1f; p.muzzle = 1f; fxFlash(0.3f);
        p.pitch += kick * 0.012f * (w == Weapon.MINIGUN ? 0.4f : 1f);
        p.yaw += (rnd.nextFloat() - 0.5f) * kick * 0.01f;
        fxPlayer(p, EV_SHAKE, w == Weapon.MINIGUN ? 0.25f : 0.12f, 0, 0);
        if (w != Weapon.MINIGUN) sound(w == Weapon.PISTOL ? "pistol" : "rifle", w == Weapon.PISTOL ? 0.75f : 0.6f, p.px, p.pz, p);
        else if (p.remote || p != me) { if (rnd.nextInt(3) == 0) sound("rifle", 0.35f, p.px, p.pz, p); }
        if (p.ammo == 0) { fxPlayer(p, EV_DRY, 2.5f, 0, 0); say(p, "¡SE ACABARON LAS BALAS!", 0xFF4040); }
    }

    private void shootArrow(Player p, float charge) {
        float[] d = aim(p, 0.004f);
        Projectile pr = new Projectile();
        pr.kind = Projectile.ARROW; pr.owner = p.id;
        float spd = 22 + 38 * charge;
        pr.x = p.px + d[0] * 0.9f; pr.y = 1.5f + d[1] * 0.9f; pr.z = p.pz + d[2] * 0.9f;
        pr.vx = d[0] * spd; pr.vy = d[1] * spd; pr.vz = d[2] * spd;
        pr.dmg = Weapon.BOW.damage * (0.5f + 0.5f * charge); pr.life = 5f;
        projectiles.add(pr);
        p.ammo -= 1; p.shotsFired++; p.fireCd = 0.35f; p.recoil = 0.7f;
        sound("bow", 0.8f, p.px, p.pz, p);
        if (p.ammo == 0) { fxPlayer(p, EV_DRY, 2.5f, 0, 0); say(p, "¡SE ACABARON LAS BALAS!", 0xFF4040); }
    }

    private void throwGrenade(Player p) {
        float[] d = aim(p, 0.0f);
        Projectile pr = new Projectile();
        pr.kind = Projectile.GRENADE; pr.owner = p.id;
        pr.x = p.px + d[0] * 0.8f; pr.y = 1.5f; pr.z = p.pz + d[2] * 0.8f;
        float spd = Weapon.GRENADE.speed;
        pr.vx = d[0] * spd + p.vx * 0.4f; pr.vy = d[1] * spd + 4.5f; pr.vz = d[2] * spd + p.vz * 0.4f;
        pr.fuse = 2.2f; pr.life = 6f;
        projectiles.add(pr);
        p.ammo -= Weapon.GRENADE.ammoCost; p.shotsFired++;
        p.fireCd = Weapon.GRENADE.cooldown; p.throwT = 0;
        sound("sword", 0.35f, p.px, p.pz, p);
        if (p.ammo < Weapon.GRENADE.ammoCost) { fxPlayer(p, EV_DRY, 2.2f, 0, 0); say(p, p.ammo == 0 ? "¡SE ACABARON LAS BALAS!" : "Necesitas 5 balas por granada", 0xFF4040); }
    }

    private void swordHit(Player p) {
        float fx = (float) Math.sin(p.yaw), fz = (float) Math.cos(p.yaw);
        boolean any = false;
        for (Ghost g : ghosts) {
            if (g.dying) continue;
            float dx = gx(g) - p.px, dz = gz(g) - p.pz, d = (float) Math.hypot(dx, dz);
            if (d > 3.6f) continue;
            float dot = (dx * fx + dz * fz) / Math.max(d, 0.01f);
            if (dot < 0.4f && d > 1.3f) continue;
            damageGhost(g, Weapon.SWORD.damage, gx(g), 1.6f, gz(g), p);
            any = true;
        }
        if (any) { sound("hit", 0.7f, p.px, p.pz, p); fxPlayer(p, EV_SHAKE, 0.3f, 0, 0); }
    }

    // ---------------------------------------------------------------- proyectiles
    private void updateProjectiles(float dt) {
        if (mirror) return;
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            Projectile p = projectiles.get(i);
            Player owner = players[Math.max(0, Math.min(players.length - 1, p.owner))];
            p.life -= dt;
            if (p.kind == Projectile.GRENADE) stepGrenade(p, dt, owner);
            else {
                if (p.kind == Projectile.ARROW) p.vy -= 7f * dt;
                float dist = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz) * dt;
                int steps = Math.max(1, (int) Math.ceil(dist / 0.35f));
                for (int s = 0; s < steps && !p.dead; s++) {
                    p.x += p.vx * dt / steps; p.y += p.vy * dt / steps; p.z += p.vz * dt / steps;
                    if (p.y < 0.05f || p.y > 14f || level.solidAt(p.x, p.z) && p.y < 8f) { p.dead = true; spark(p.x, p.y, p.z, 5, 1f, 0.8f, 0.4f); break; }
                    for (Soul so : souls) {
                        if (so.state != Soul.FLEE) continue;
                        float dx = so.x - p.x, dz = so.z - p.z;
                        if (dx * dx + dz * dz < 0.7f && p.y < 2.1f) { killSoul(so, owner); p.dead = true; break; }
                    }
                    if (p.dead) break;
                    for (Ghost g : ghosts) {
                        if (g.dying) continue;
                        float dx = gx(g) - p.x, dz = gz(g) - p.z;
                        if (dx * dx + dz * dz < 0.95f && p.y > g.bob - 0.1f && p.y < g.bob + 2.7f) {
                            damageGhost(g, p.dmg, p.x, p.y, p.z, owner);
                            sound("hurt_ghost", 0.25f, p.x, p.z, null);
                            p.dead = true; break;
                        }
                    }
                }
                if (p.kind == Projectile.ARROW && !p.dead && rnd.nextInt(3) == 0) spark(p.x, p.y, p.z, 1, 0.6f, 0.6f, 0.7f);
            }
            if (p.life <= 0) p.dead = true;
            if (p.dead) projectiles.remove(i);
        }
    }

    private void stepGrenade(Projectile p, float dt, Player owner) {
        p.vy -= 18f * dt;
        p.spin += dt * 9f;
        p.fuse -= dt;
        float nx = p.x + p.vx * dt, nz = p.z + p.vz * dt, ny = p.y + p.vy * dt;
        if (level.solidAt(nx, p.z)) { p.vx = -p.vx * 0.5f; nx = p.x; }
        if (level.solidAt(p.x, nz)) { p.vz = -p.vz * 0.5f; nz = p.z; }
        if (ny < 0.2f) { ny = 0.2f; p.vy = -p.vy * 0.4f; p.vx *= 0.75f; p.vz *= 0.75f; if (Math.abs(p.vy) < 1.2f) p.vy = 0; }
        p.x = nx; p.y = ny; p.z = nz;
        if (p.fuse <= 0) { explode(p.x, p.y, p.z, owner); p.dead = true; }
    }

    private void explode(float x, float y, float z, Player owner) {
        sound("grenade", 0.95f, x, z, null);
        fxFlash(1f);
        for (Player pl : players) fxPlayer(pl, EV_SHAKE, Math.max(0.2f, 1f - (float) Math.hypot(pl.px - x, pl.pz - z) / 30f), 0, 0);
        ring(x, 0.3f, z, 1f, 0.6f, 0.2f, 9f, 0.7f);
        ring(x, 0.5f, z, 1f, 0.9f, 0.5f, 6f, 0.45f);
        for (int i = 0; i < 70; i++) burst(x, y + 0.3f, z, 1, 9f, 1f, 0.45f + rnd.nextFloat() * 0.4f, 0.1f, 0.9f + rnd.nextFloat(), 0.5f + rnd.nextFloat() * 0.7f, 2f);
        for (int i = 0; i < 40; i++) burst(x, y + 0.3f, z, 1, 14f, 1f, 0.85f, 0.3f, 0.25f, 0.8f, -9f);
        int kills = 0;
        for (Ghost g : ghosts) {
            if (g.dying) continue;
            float d = (float) Math.sqrt(sq(gx(g) - x) + sq(gz(g) - z) + sq(1.2f - y));
            if (d < 7.5f && los(x, z, gx(g), gz(g))) { damageGhost(g, 9999, gx(g), 1.5f, gz(g), owner); kills++; }
        }
        for (Soul so : souls) if (so.state == Soul.FLEE && Math.hypot(so.x - x, so.z - z) < 7f && los(x, z, so.x, so.z)) killSoul(so, owner);
        for (Player pl : players) {
            if (pl.state != Player.ALIVE) continue;
            float dp = (float) Math.sqrt(sq(pl.px - x) + sq(pl.pz - z));
            if (dp < 7.5f && los(x, z, pl.px, pl.pz)) hurtPlayer(pl, 300f * (1 - dp / 7.5f), x, z);
        }
        if (kills > 1) say(owner, "¡EXPLOSIÓN! " + kills + " fantasmas eliminados", 0xFFA030);
    }

    private static float sq(float v) { return v * v; }

    public boolean los(float x0, float z0, float x1, float z1) {
        int n = (int) (Math.hypot(x1 - x0, z1 - z0) / 1.0f) + 1;
        for (int i = 1; i < n; i++) { float t = i / (float) n; if (level.solidAt(x0 + (x1 - x0) * t, z0 + (z1 - z0) * t)) return false; }
        return true;
    }

    // ---------------------------------------------------------------- fantasmas
    public float gx(Ghost g) { return g.x + g.offX; }
    public float gz(Ghost g) { return g.z + g.offZ; }

    private boolean ghostBlocked(int x, int z) {
        return level.maze.isSolid(x, z) || (x == level.maze.doorX && z == level.maze.doorZ);
    }

    private void damageGhost(Ghost g, float dmg, float x, float y, float z, Player by) {
        if (g.dying) return;
        g.hp -= dmg; g.hurtT = 0.25f;
        if (by != null) fxPlayer(by, EV_HIT, 1f, 0, 0);
        burst(x, y, z, 6, 3f, 0.55f, 1f, 0.8f, 0.22f, 0.5f, 0f);
        if (g.hp <= 0) {
            g.dying = true; g.dyingT = 0;
            if (by != null) { by.killedLevel++; by.killedTotal++; }
            sound("ghost_kill", 0.65f, gx(g), gz(g), null);
            sound("zap", 0.35f, gx(g), gz(g), null);
            ring(gx(g), 1.2f, gz(g), 0.5f, 1f, 0.8f, 4f, 0.5f);
            for (int i = 0; i < 26; i++) burst(gx(g), 1.4f, gz(g), 1, 5f, 0.5f, 1f, 0.8f, 0.4f, 1.1f, -1f);
            decal(gx(g), gz(g));
            int left = 0;
            for (Ghost o : ghosts) if (!o.dying) left++;
            ghostsAlive = left;
            if (left == 0) say(null, "¡ELIMINASTE A TODOS LOS FANTASMAS! La puerta se abre", 0x60FF80);
            else if (left <= 5) say(null, "Quedan " + left + " fantasmas", 0xC0C0FF);
        }
    }

    private void decal(float x, float z) {
        decals.add(new float[]{x, z, 0});
        if (decals.size() > 40) decals.remove(0);
        if (netActive) { try { ev.writeByte(EV_DECAL); ev.writeFloat(x); ev.writeFloat(z); evCount++; } catch (IOException ignored) { } }
    }

    private Player nearestPlayer(float x, float z) {
        Player best = null; float bd = 1e18f;
        for (Player p : players) {
            if (p.state != Player.ALIVE) continue;
            float d = sq(p.px - x) + sq(p.pz - z);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    private void updateGhosts(float dt) {
        if (mirror) { smoothGhosts(dt); return; }
        modeT += dt;
        if (modeT >= SCHEDULE[modeIdx] && modeIdx < SCHEDULE.length - 1) {
            modeT = 0; modeIdx++; chase = (modeIdx % 2) == 1;
            for (Ghost g : ghosts) g.reversePending = true;
        }
        int alive = 0;
        Ghost blinky = null;
        for (Ghost g : ghosts) if (!g.dying) { alive++; if (g.type == 0 && blinky == null) blinky = g; }
        ghostsAlive = alive;
        boolean freeze = !multi && me.state == Player.DYING;
        for (int i = ghosts.size() - 1; i >= 0; i--) {
            Ghost g = ghosts.get(i);
            g.hurtT = Math.max(0, g.hurtT - dt);
            g.bob = 0.35f + 0.22f * (float) Math.sin(time * 2.1f + g.phase);
            if (g.dying) { g.dyingT += dt; if (g.dyingT > 0.9f) { ghosts.remove(i); ghostById.remove(g.id); } continue; }
            if (freeze) continue;
            g.atkCd -= dt;
            Player tgt = nearestPlayer(g.x, g.z);
            int ptx = tgt != null ? Level.tileOf(tgt.px) : g.tx, ptz = tgt != null ? Level.tileOf(tgt.pz) : g.tz;
            float spd = g.speed * (g.type == 0 && alive <= 6 ? 1.12f : 1f) * (1f + Math.min(0.22f, (LEVEL_TIME - timeLeft) / 1000f));
            if (tgt != null && sq(g.x - tgt.px) + sq(g.z - tgt.pz) < 40f * 40f && !g.dying && g.hurtT <= 0 && sq(g.x - tgt.px) + sq(g.z - tgt.pz) < 36f) spd *= 1.12f;   // embestida cuando estan cerca
            if (g.hurtT > 0) spd *= 0.55f;
            float step = spd * dt;
            g.trail += ((step > 0 ? 1f : 0f) - g.trail) * dt * 3f;
            int guard = 0;
            while (step > 0 && guard++ < 4) {
                float cx = Level.center(g.nx), cz = Level.center(g.nz);
                float dx = cx - g.x, dz = cz - g.z, d = (float) Math.sqrt(dx * dx + dz * dz);
                if (d <= step) { step -= d; g.x = cx; g.z = cz; g.tx = g.nx; g.tz = g.nz; chooseNext(g, tgt, ptx, ptz, blinky); }
                else { g.x += dx / d * step; g.z += dz / d * step; step = 0; }
            }
            float wx = -g.dz * g.laneSide, wz = g.dx * g.laneSide;
            g.offX += (wx - g.offX) * Math.min(1, dt * 3f); g.offZ += (wz - g.offZ) * Math.min(1, dt * 3f);
            float ty = (float) Math.atan2(g.dx, g.dz);
            float da = ty - g.yawVis;
            while (da > Math.PI) da -= 6.2832f;
            while (da < -Math.PI) da += 6.2832f;
            g.yawVis += da * Math.min(1, dt * 7f);
            // estela de ectoplasma
            g.trailT -= dt;
            if (g.trailT <= 0) { g.trailT = g.fast ? 0.09f : 0.18f; if (tgt != null && sq(g.x - tgt.px) + sq(g.z - tgt.pz) < 28 * 28) burst(gx(g) - g.dx * 0.6f, g.bob + 0.5f, gz(g) - g.dz * 0.6f, 1, 0.6f, 0.4f, 0.8f, 0.7f, 0.35f, 0.9f, -0.3f); }
            // ataque por contacto
            for (Player p : players) {
                if (p.state != Player.ALIVE) continue;
                float dxp = gx(g) - p.px, dzp = gz(g) - p.pz, dp2 = dxp * dxp + dzp * dzp;
                if (dp2 < 2.4f && g.atkCd <= 0) { g.atkCd = 0.9f; hurtPlayer(p, 34 + levelIdx * 12, gx(g), gz(g)); }
            }
            if (tgt != null) {
                float dp2 = sq(gx(g) - tgt.px) + sq(gz(g) - tgt.pz);
                g.screamT -= dt;
                if (dp2 < 15 * 15 && g.screamT <= 0 && ghostVoiceCd <= 0) {
                    g.screamT = 9 + rnd.nextFloat() * 10; ghostVoiceCd = 2.2f;
                    sound(Audio.SCARES[rnd.nextInt(Audio.SCARES.length)], 0.45f, gx(g), gz(g), null);
                } else if (g.screamT <= 0) g.screamT = 3 + rnd.nextFloat() * 5;
            }
        }
        ghostVoiceCd -= dt;
    }

    private void chooseNext(Ghost g, Player tgt, int ptx, int ptz, Ghost blinky) {
        final int[][] D = {{0, -1}, {-1, 0}, {0, 1}, {1, 0}};
        int W = level.maze.W, H = level.maze.H;
        float distP = (float) Math.hypot(g.tx - ptx, g.tz - ptz);
        g.aware = tgt != null && distP <= level.aware;
        int ttx, ttz;
        boolean useChase = chase && g.aware;
        int[][] corner = {{W - 2, 1}, {1, 1}, {W - 2, H - 2}, {1, H - 2}};
        if (useChase) {
            int pdx = Math.round(tgt.dirX), pdz = Math.round(tgt.dirZ);
            if (Math.abs(tgt.dirX) > Math.abs(tgt.dirZ)) pdz = 0; else pdx = 0;
            switch (g.type) {
                case 0: ttx = ptx; ttz = ptz; break;
                case 1: ttx = ptx + 4 * pdx; ttz = ptz + 4 * pdz; break;
                case 2: {
                    int ax = ptx + 2 * pdx, az = ptz + 2 * pdz;
                    int bx = blinky != null ? blinky.tx : g.tx, bz = blinky != null ? blinky.tz : g.tz;
                    ttx = ax + (ax - bx); ttz = az + (az - bz); break;
                }
                default:
                    if (distP > 8) { ttx = ptx; ttz = ptz; } else { ttx = corner[3][0]; ttz = corner[3][1]; }
            }
        } else { ttx = corner[g.type][0]; ttz = corner[g.type][1]; }
        int bi = -1; float best = 1e18f;
        for (int i = 0; i < 4; i++) {
            int dx = D[i][0], dz = D[i][1];
            if (dx == -g.dx && dz == -g.dz) continue;
            int nx = g.tx + dx, nz = g.tz + dz;
            if (ghostBlocked(nx, nz)) continue;
            float d2 = sq(nx - ttx) + sq(nz - ttz);
            if (d2 < best) { best = d2; bi = i; }
        }
        if (g.reversePending) {
            g.reversePending = false;
            if (!ghostBlocked(g.tx - g.dx, g.tz - g.dz) && (g.dx != 0 || g.dz != 0)) { g.dx = -g.dx; g.dz = -g.dz; g.nx = g.tx + g.dx; g.nz = g.tz + g.dz; return; }
        }
        if (bi < 0) { g.dx = -g.dx; g.dz = -g.dz; }
        else { g.dx = D[bi][0]; g.dz = D[bi][1]; }
        g.nx = g.tx + g.dx; g.nz = g.tz + g.dz;
        if (ghostBlocked(g.nx, g.nz)) { g.nx = g.tx; g.nz = g.tz; }
    }

    // ---------------------------------------------------------------- jugador: dano, muerte, respawn
    public void hurtPlayer(Player p, float dmg, float fromX, float fromZ) {
        if (p.state != Player.ALIVE || p.invulnT > 0) return;
        if (p.immuneT > 0) { fxPlayer(p, EV_PICK, 0.5f, 0, 0); return; }
        p.hp -= dmg;
        fxPlayer(p, EV_HURT, 0.55f + dmg / 120f, fromX, fromZ);
        sound("player_hurt", 0.8f, p.px, p.pz, p);
        float dx = p.px - fromX, dz = p.pz - fromZ, d = (float) Math.hypot(dx, dz) + 0.01f;
        p.vx += dx / d * 6f; p.vz += dz / d * 6f;
        for (int i = 0; i < 10; i++) burst(p.px, 1.4f, p.pz, 1, 4f, 0.9f, 0.1f, 0.1f, 0.2f, 0.5f, 3f);
    }

    private void die(Player p) {
        p.lives--;
        p.state = Player.DYING; p.deathT = 0;
        stopMini(p);
        sound("death", 0.9f, p.px, p.pz, p);
        big(p, p.lives > 0 ? "¡PAC-MAN HA MUERTO!" : "¡HAS MUERTO!", 0xFF2020, 2.2f);
        if (multi) say(null, "Jugador " + (p.id + 1) + " cayó (" + p.lives + " vidas)", 0xFF8080);
    }

    private void updateDying(Player p, float dt) {
        p.deathT += dt;
        p.eyeDrop = Math.min(1.2f, p.deathT * 1.2f);
        p.pitch += (0.5f - p.pitch) * dt * 2;
        if (p.deathT > 2.4f) {
            if (p.lives > 0) {
                p.hp = MAX_HP; p.invulnT = 5f; p.state = Player.ALIVE; p.eyeDrop = 0; p.hurtFlash = 0; p.pitch = 0;
                for (Ghost g : ghosts) if (Math.hypot(gx(g) - p.px, gz(g) - p.pz) < 9) g.atkCd = 3f;
                say(p, "¡Reviviste! Te quedan " + p.lives + " vidas", 0x60FF80);
                big(p, "¡REVIVES!", 0x60FF80, 1.5f);
            } else {
                p.state = Player.OUT;
                if (!multi) gameOver("Los fantasmas te atraparon");
                else say(null, "Jugador " + (p.id + 1) + " fue eliminado", 0xFF6060);
            }
        }
    }

    // ---------------------------------------------------------------- cofres y objetos
    private void openChest(Player p, Chest c) {
        c.open = true;
        sound("chest", 0.9f, c.x, c.z, null);
        fxPlayer(p, EV_PICK, 0.6f, 0, 0);
        ring(c.x, 0.8f, c.z, 1f, 0.8f, 0.2f, 4f, 0.5f);
        for (int i = 0; i < 22; i++) burst(c.x, 1.0f, c.z, 1, 4f, 1f, 0.8f, 0.2f, 0.25f, 1f, -2f);
        if (c.hasKey) giveKey(p);
        int n = 1 + (rnd.nextFloat() < 0.4f ? 1 : 0);
        for (int i = 0; i < n; i++) {
            float r = rnd.nextFloat();
            if (r < 0.32f) giveRandomWeapon(p);
            else if (r < 0.68f) giveAmmo(p, 20 + rnd.nextInt(26));
            else heal(p, 150);
        }
        if (rnd.nextFloat() < 0.28f) { p.immuneT = 30f; sound("powerup", 0.9f, p.px, p.pz, p); say(p, "¡PODER! INMUNIDAD al daño por 30 segundos", 0x60B0FF); big(p, "¡INMUNIDAD!", 0x60B0FF, 1.6f); }
    }

    private void giveKey(Player p) {
        hasKey = true;
        sound("key", 0.9f, p.px, p.pz, p);
        say(null, (multi ? "Jugador " + (p.id + 1) + " encontró la LLAVE" : "¡LLAVE ENCONTRADA!") + " Ve a la puerta de salida", 0xFFE040);
        big(null, "¡LLAVE!", 0xFFE040, 1.6f);
    }

    private void giveRandomWeapon(Player p) {
        Weapon[] pool = {Weapon.PISTOL, Weapon.SWORD, Weapon.SWORD, Weapon.RIFLE, Weapon.RIFLE, Weapon.BOW, Weapon.BOW, Weapon.MINIGUN, Weapon.GRENADE, Weapon.GRENADE};
        Weapon w = pool[rnd.nextInt(pool.length)];
        for (int s = 0; s < SLOTS; s++) if (p.inv[s] == w) { giveAmmo(p, 25); say(p, w.label + " repetida: +25 balas", 0xC0C0C0); return; }
        for (int s = 0; s < SLOTS; s++) if (p.inv[s] == null) {
            p.inv[s] = w;
            if (p.sel < 0 || p.inv[p.sel] == null) selectSlot(p, s);
            say(p, "¡Nueva arma: " + w.label + "! (tecla " + (s + 1) + ")", 0xFFC040);
            sound("powerup", 0.5f, p.px, p.pz, p);
            if (p.ammo == 0 && w != Weapon.SWORD) giveAmmo(p, 20);
            return;
        }
        giveAmmo(p, 25);
    }

    private void giveAmmo(Player p, int n) {
        p.ammo = Math.min(999, p.ammo + n);
        sound("ammo", 0.7f, p.px, p.pz, p);
        say(p, "+" + n + " balas", 0xFFD060);
    }

    private void heal(Player p, int n) {
        p.hp = Math.min(MAX_HP, p.hp + n);
        sound("medkit", 0.8f, p.px, p.pz, p);
        say(p, "Botiquín usado: +" + n + " de vida", 0x60FF90);
    }

    private void collectPickups(Player p) {
        for (Pickup k : pickups) {
            if (k.taken) continue;
            float dx = k.x - p.px, dz = k.z - p.pz, d2 = dx * dx + dz * dz;
            float R = k.kind == Pickup.STAR ? 2.0f : 1.7f;
            if (d2 > R * R) continue;
            switch (k.kind) {
                case Pickup.STAR:
                    k.taken = true; p.starsLevel++; p.starsTotal++; p.speedSteps++;
                    sound("star", 0.7f, p.px, p.pz, p);
                    fxPlayer(p, EV_PICK, 0.35f, 0, 0);
                    ring(k.x, 1f, k.z, 1f, 0.9f, 0.2f, 2.5f, 0.4f);
                    for (int i = 0; i < 10; i++) burst(k.x, 1.0f, k.z, 1, 3f, 1f, 0.9f, 0.2f, 0.2f, 0.7f, -1f);
                    if (p.speedPct() < 200) say(p, "¡Estrella! Velocidad " + (int) p.speedPct() + "%", 0xFFE040);
                    break;
                case Pickup.AMMO:
                    if (p.ammo >= 999) continue;
                    k.taken = true; giveAmmo(p, k.amount); break;
                case Pickup.MED:
                    if (p.hp >= MAX_HP) continue;
                    k.taken = true; heal(p, k.amount); break;
                case Pickup.KEY:
                    k.taken = true; giveKey(p); break;
            }
        }
    }

    // ---------------------------------------------------------------- trampas
    private void applyTrap(Player p, float dt) {
        int tr = level.trapAt(p.px, p.pz);
        p.inTrap = tr;
        if (tr == 0 || p.invulnT > 0 || p.immuneT > 0 || p.state != Player.ALIVE) { p.trapTick = 0; return; }
        p.hp -= (tr == 1 ? 10f : 50f) * dt;          // acido: 10 de vida por segundo; puas: 50 por segundo
        p.trapTick -= dt;
        if (p.trapTick <= 0) {
            p.trapTick = tr == 1 ? 0.5f : 0.3f;
            fxPlayer(p, EV_TRAPFX, tr == 1 ? 0.25f : 0.45f, 0, 0);
            sound(tr == 1 ? "hurt_ghost" : "player_hurt", tr == 1 ? 0.45f : 0.6f, p.px, p.pz, p);
            if (tr == 1) burst(p.px, 0.4f, p.pz, 4, 2.5f, 0.3f, 1f, 0.3f, 0.2f, 0.6f, 4f);
            else burst(p.px, 0.5f, p.pz, 6, 3f, 0.9f, 0.1f, 0.1f, 0.18f, 0.5f, 6f);
        }
    }

    // ---------------------------------------------------------------- tiendas
    public int shopPrice() { return 4 + 2 * levelIdx; }

    private void buyFromShop(Player p, ShopChest sc) {
        int price = shopPrice();
        if (sc.anim > 0.3f) return;
        if (p.wallet() < price) {
            say(p, "Necesitas " + price + " estrellas (tienes " + p.wallet() + ")", 0xFF7060);
            sound("dry", 0.7f, p.px, p.pz, p);
            return;
        }
        p.starsSpent += price;
        sc.anim = 2.4f;
        sound("chest", 0.9f, sc.x, sc.z, null);
        sound("powerup", 0.8f, sc.x, sc.z, null);
        for (int i = 0; i < 4; i++) ring(sc.x, 0.4f + i * 0.35f, sc.z, 0.3f + 0.7f * (i % 2), 0.6f, 1f - 0.4f * i / 3f, 3.5f + i, 0.7f + i * 0.1f);
        for (int i = 0; i < 40; i++) burst(sc.x, 1.1f, sc.z, 1, 5f, rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat(), 0.3f, 1.1f, -1.5f);
        fxPlayer(p, EV_PICK, 0.7f, 0, 0);
        giveShopWeapon(p);
    }

    private void giveShopWeapon(Player p) {
        Weapon[] ws = {Weapon.PISTOL, Weapon.SWORD, Weapon.GRENADE, Weapon.BOW, Weapon.RIFLE, Weapon.MINIGUN};
        int[] wt = {3, 8, 16, 22, 25, 26};            // las armas raras y poderosas salen mas a menudo
        String[] rar = {"COMÚN", "COMÚN", "RARA", "RARA", "ÉPICA", "¡LEGENDARIA!"};
        int[] col = {0xC0C0C0, 0xC0C0C0, 0x60B0FF, 0x60B0FF, 0xC070FF, 0xFFA020};
        int tot = 0;
        for (int v : wt) tot += v;
        int r = rnd.nextInt(tot), k = 0;
        while (r >= wt[k]) { r -= wt[k]; k++; }
        Weapon w = ws[k];
        boolean had = false;
        for (Weapon o : p.inv) if (o == w) had = true;
        if (had) { p.ammo = Math.min(999, p.ammo + 60); say(p, w.label + " " + rar[k] + " repetida: +60 balas", col[k]); }
        else {
            for (int s2 = 0; s2 < SLOTS; s2++) if (p.inv[s2] == null) { p.inv[s2] = w; selectSlot(p, s2); say(p, "¡COFRE ARCOÍRIS! " + w.label + " " + rar[k] + " (tecla " + (s2 + 1) + ")", col[k]); break; }
            p.ammo = Math.min(999, p.ammo + 25);
        }
        big(p, w.label + " " + rar[k], col[k], 2.2f);
    }

    // ---------------------------------------------------------------- almas perdidas (NPC)
    private int fleeingSouls() { int n = 0; for (Soul s : souls) if (s.state == Soul.FLEE) n++; return n; }

    /** Cada vez que un alma muere o se salva aparece otra en un punto cualquiera del laberinto. */
    private void respawnSouls(float dt) {
        soulRespawnT -= dt;
        if (soulRespawnT > 0 || fleeingSouls() >= level.soulCount || level.roamTiles.isEmpty()) return;
        for (int tries = 0; tries < 40; tries++) {
            int[] t = level.roamTiles.get(rnd.nextInt(level.roamTiles.size()));
            float x = Level.center(t[0]), z = Level.center(t[1]);
            boolean ok = true;
            for (Player p : players) if (p.state != Player.OUT && Math.hypot(p.px - x, p.pz - z) < 32f) ok = false;
            for (Ghost g : ghosts) if (!g.dying && Math.hypot(gx(g) - x, gz(g) - z) < 12f) ok = false;
            if (!ok && tries < 35) continue;
            Soul so = new Soul();
            so.id = nextSoulId++; so.female = rnd.nextBoolean(); so.variant = rnd.nextInt(4);
            so.x = x; so.z = z; so.tx = t[0]; so.tz = t[1];
            so.speed = 4.5f + rnd.nextFloat() * 0.9f; so.phase = rnd.nextFloat() * 6.28f;
            final int[][] D = {{0, -1}, {-1, 0}, {0, 1}, {1, 0}};
            for (int k = 0; k < 4; k++) { int[] d = D[(k + rnd.nextInt(4)) % 4]; if (!ghostBlocked(so.tx + d[0], so.tz + d[1])) { so.dx = d[0]; so.dz = d[1]; break; } }
            so.nx = so.tx + so.dx; so.nz = so.tz + so.dz;
            souls.add(so); soulById.put(so.id, so);
            ring(x, 0.3f, z, 0.5f, 0.8f, 1f, 3f, 0.8f);
            for (int i = 0; i < 12; i++) burst(x, 1.0f, z, 1, 2f, 0.5f, 0.8f, 1f, 0.3f, 1.2f, -1f);
            break;
        }
        soulRespawnT = 0.8f;
        // limite de cadaveres para no saturar
        int dead = 0;
        for (Soul s : souls) if (s.state == Soul.DEAD) dead++;
        for (int i = 0; i < souls.size() && dead > 40; i++) if (souls.get(i).state == Soul.DEAD) { soulById.remove(souls.get(i).id); souls.remove(i); i--; dead--; }
    }

    private void updateSouls(float dt) {
        if (mirror) { smoothSouls(dt); return; }
        respawnSouls(dt);
        for (int i = souls.size() - 1; i >= 0; i--) {
            Soul s = souls.get(i);
            s.stateT += dt;
            if (s.state == Soul.DEAD) continue;
            if (s.state == Soul.SAVED) { if (s.stateT > 2.4f) { souls.remove(i); soulById.remove(s.id); } continue; }
            // ¿hay algun fantasma cerca?  -> corre mas
            float nearG = 99;
            for (Ghost g : ghosts) { if (g.dying) continue; float d = (float) Math.hypot(gx(g) - s.x, gz(g) - s.z); if (d < nearG) nearG = d; }
            float spd = s.speed * (nearG < 9 ? 1.18f : 0.85f);
            float step = spd * dt;
            int guard = 0;
            while (step > 0 && guard++ < 4) {
                float cx = Level.center(s.nx), cz = Level.center(s.nz);
                float dx = cx - s.x, dz = cz - s.z, d = (float) Math.sqrt(dx * dx + dz * dz);
                if (d <= step) { step -= d; s.x = cx; s.z = cz; s.tx = s.nx; s.tz = s.nz; chooseNextSoul(s); }
                else { s.x += dx / d * step; s.z += dz / d * step; step = 0; }
            }
            float ty = (float) Math.atan2(s.dx, s.dz), da = ty - s.yaw;
            while (da > Math.PI) da -= 6.2832f;
            while (da < -Math.PI) da += 6.2832f;
            s.yaw += da * Math.min(1, dt * 9f);
            s.runAmt += (1f - s.runAmt) * Math.min(1, dt * 6f);
            // los fantasmas los matan
            boolean dead = false;
            for (Ghost g : ghosts) if (!g.dying && Math.hypot(gx(g) - s.x, gz(g) - s.z) < 1.25f) { killSoul(s, null); dead = true; break; }
            if (dead) continue;
            // el jugador los salva al tocarlos
            for (Player p : players) if (p.state == Player.ALIVE && Math.hypot(p.px - s.x, p.pz - s.z) < 2.1f) { rescueSoul(p, s); break; }
        }
    }

    private void chooseNextSoul(Soul s) {
        final int[][] D = {{0, -1}, {-1, 0}, {0, 1}, {1, 0}};
        // fantasma mas cercano (en tiles)
        Ghost threat = null; float bd = 14 * 14;
        for (Ghost g : ghosts) { if (g.dying) continue; float d = sq(g.tx - s.tx) + sq(g.tz - s.tz); if (d < bd) { bd = d; threat = g; } }
        int bi = -1; float best = -1e9f;
        for (int i = 0; i < 4; i++) {
            int dx = D[i][0], dz = D[i][1];
            if (dx == -s.dx && dz == -s.dz) continue;
            int nx = s.tx + dx, nz = s.tz + dz;
            if (ghostBlocked(nx, nz)) continue;
            float sc = rnd.nextFloat() * 2.5f;
            if (threat != null) sc += sq(nx - threat.tx) + sq(nz - threat.tz);   // huir: maximiza la distancia
            else sc += rnd.nextFloat() * 6f;
            if (sc > best) { best = sc; bi = i; }
        }
        if (bi < 0) { s.dx = -s.dx; s.dz = -s.dz; } else { s.dx = D[bi][0]; s.dz = D[bi][1]; }
        s.nx = s.tx + s.dx; s.nz = s.tz + s.dz;
        if (ghostBlocked(s.nx, s.nz)) { s.nx = s.tx; s.nz = s.tz; }
    }

    private void killSoul(Soul s, Player by) {
        if (s.state != Soul.FLEE) return;
        s.state = Soul.DEAD; s.stateT = 0; s.runAmt = 0;
        sound("zap", 0.4f, s.x, s.z, null);
        sound(Audio.SCARES[rnd.nextInt(Audio.SCARES.length)], 0.25f, s.x, s.z, null);
        for (int i = 0; i < 18; i++) burst(s.x, 1.0f, s.z, 1, 3f, 0.9f, 0.1f, 0.1f, 0.22f, 0.8f, 5f);
        for (int i = 0; i < 10; i++) burst(s.x, 1.2f, s.z, 1, 2f, 0.6f, 0.8f, 1f, 0.3f, 1.2f, -1f);
        addBlood(s.x, s.z, 2.6f + rnd.nextFloat());
        if (by != null) say(by, "¡Mataste a un alma inocente!", 0xFF7070);
    }

    private void addBlood(float x, float z, float size) {
        float rot = rnd.nextFloat() * 6.28f;
        blood.add(new float[]{x, z, size, rot});
        if (blood.size() > 120) blood.remove(0);
        if (netActive) { try { ev.writeByte(EV_BLOOD); ev.writeFloat(x); ev.writeFloat(z); ev.writeFloat(size); ev.writeFloat(rot); evCount++; } catch (IOException ignored) { } }
    }

    private static final String[] CARD = {"NORTE", "NORESTE", "ESTE", "SURESTE", "SUR", "SUROESTE", "OESTE", "NOROESTE"};

    /** Direccion cardinal (NORTE = +Z, ESTE = +X) desde (x,z) hasta (tx,tz). */
    public static String cardinal(float x, float z, float tx, float tz) {
        double a = Math.toDegrees(Math.atan2(tx - x, tz - z));
        int i = (int) Math.round(a / 45.0);
        return CARD[((i % 8) + 8) % 8];
    }

    private void rescueSoul(Player p, Soul s) {
        s.state = Soul.SAVED; s.stateT = 0;
        p.soulsSaved++; p.starsTotal += 2; p.starsLevel += 2;
        sound("powerup", 0.7f, s.x, s.z, null);
        ring(s.x, 0.4f, s.z, 0.5f, 0.9f, 1f, 3f, 0.8f);
        for (int i = 0; i < 24; i++) burst(s.x, 1.0f, s.z, 1, 2.5f, 0.6f, 0.9f, 1f, 0.3f, 1.4f, -2f);
        float kx = 0, kz = 0; boolean found = false;
        if (!hasKey) {
            for (Pickup k : pickups) if (k.kind == Pickup.KEY && !k.taken) { kx = k.x; kz = k.z; found = true; break; }
            if (!found) for (Chest c : chests) if (c.hasKey && !c.open) { kx = c.x; kz = c.z; found = true; break; }
        }
        if (found) {
            String dir = cardinal(p.px, p.pz, kx, kz);
            int m = (int) Math.hypot(kx - p.px, kz - p.pz);
            say(p, "¡GRACIAS POR SALVARME! HE VISTO LA LLAVE hacia el " + dir + " (a unos " + m + " m)", 0xFFE040);
            big(p, "¡ALMA SALVADA!", 0x80E0FF, 1.8f);
            p.keyHintT = 2f; p.keyHintX = kx; p.keyHintZ = kz;
            if (p.remote && netActive) { try { ev.writeByte(EV_KEYHINT); ev.writeByte(p.id); ev.writeFloat(kx); ev.writeFloat(kz); evCount++; } catch (IOException ignored) { } }
        } else {
            say(p, hasKey ? "¡GRACIAS POR SALVARME! Ya tienes la llave: ve a la PUERTA" : "¡GRACIAS POR SALVARME! (+2 estrellas)", 0x80E0FF);
            big(p, "¡ALMA SALVADA!", 0x80E0FF, 1.8f);
        }
    }

    private void smoothSouls(float dt) {
        float k = Math.min(1, dt * 14f);
        for (Soul s : souls) {
            s.stateT += dt;
            s.x += (s.sx - s.x) * k; s.z += (s.sz - s.z) * k;
            float da = s.syaw - s.yaw;
            while (da > Math.PI) da -= 6.2832f;
            while (da < -Math.PI) da += 6.2832f;
            s.yaw += da * k;
        }
    }

    // ---------------------------------------------------------------- ambiente de terror (solo del jugador local)
    private void ambient(Player p, float dt) {
        jumpNext -= dt;
        if (jumpNext <= 0 && jumpT <= 0 && state == PLAY && p.state == Player.ALIVE) {
            jumpT = 0.001f; jumpNext = 55f + rnd.nextFloat() * 70f;
            Audio.play("screech", 0.9f, 0f);
            Audio.play(Audio.SCARES[rnd.nextInt(Audio.SCARES.length)], 0.8f, 0f);
            Audio.play("heart", 0.8f, 0f);
        }
        scareT -= dt;
        if (scareT <= 0) {
            scareT = 7 + rnd.nextFloat() * 12;
            Audio.play(Audio.SCARES[rnd.nextInt(Audio.SCARES.length)], 0.22f + rnd.nextFloat() * 0.2f, rnd.nextFloat() * 2 - 1);
        }
        float nearest = 99;
        for (Ghost g : ghosts) if (!g.dying) nearest = Math.min(nearest, (float) Math.hypot(gx(g) - p.px, gz(g) - p.pz));
        heartT -= dt;
        boolean panic = p.hp < 150 || nearest < 9;
        if (panic && heartT <= 0) { heartT = p.hp < 150 ? 0.55f : 0.5f + nearest / 20f; Audio.play("heart", 0.5f); }
    }

    // ---------------------------------------------------------------- particulas
    public void burst(float x, float y, float z, int n, float speed, float r, float g, float b, float size, float life, float grav) {
        if (netActive && !mirror) {
            try { ev.writeByte(EV_BURST); ev.writeFloat(x); ev.writeFloat(y); ev.writeFloat(z); ev.writeByte(n); ev.writeFloat(speed); ev.writeFloat(r); ev.writeFloat(g); ev.writeFloat(b); ev.writeFloat(size); ev.writeFloat(life); ev.writeFloat(grav); evCount++; } catch (IOException ignored) { }
        }
        burstLocal(x, y, z, n, speed, r, g, b, size, life, grav);
    }

    private void burstLocal(float x, float y, float z, int n, float speed, float r, float g, float b, float size, float life, float grav) {
        for (int i = 0; i < n; i++) {
            if (particles.size() > 700) return;
            Particle p = new Particle();
            p.x = x; p.y = y; p.z = z;
            float a = rnd.nextFloat() * 6.28f, e = rnd.nextFloat() * 2 - 1;
            float s = speed * (0.3f + rnd.nextFloat() * 0.7f), c = (float) Math.sqrt(1 - e * e);
            p.vx = (float) Math.cos(a) * c * s; p.vy = e * s * 0.8f + 1f; p.vz = (float) Math.sin(a) * c * s;
            p.max = p.life = life * (0.6f + rnd.nextFloat() * 0.6f);
            p.size = size; p.r = r; p.g = g; p.b = b; p.grav = grav; p.drag = 1.5f;
            particles.add(p);
        }
    }

    /** Onda expansiva (anillo) que crece hasta 'radius'. */
    public void ring(float x, float y, float z, float r, float g, float b, float radius, float life) {
        if (netActive && !mirror) {
            try { ev.writeByte(EV_RING); ev.writeFloat(x); ev.writeFloat(y); ev.writeFloat(z); ev.writeFloat(r); ev.writeFloat(g); ev.writeFloat(b); ev.writeFloat(radius); ev.writeFloat(life); evCount++; } catch (IOException ignored) { }
        }
        ringLocal(x, y, z, r, g, b, radius, life);
    }

    private void ringLocal(float x, float y, float z, float r, float g, float b, float radius, float life) {
        Particle p = new Particle();
        p.x = x; p.y = y; p.z = z; p.ring = true; p.max = p.life = life; p.size = radius; p.r = r; p.g = g; p.b = b;
        particles.add(p);
    }

    private void spark(float x, float y, float z, int n, float r, float g, float b) { burst(x, y, z, n, 3.5f, r, g, b, 0.12f, 0.35f, 8f); }

    private void updateParticles(float dt) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0) { particles.remove(i); continue; }
            if (p.ring) continue;
            p.vy -= p.grav * dt;
            float k = Math.max(0, 1 - p.drag * dt);
            p.vx *= k; p.vz *= k; if (p.grav == 0) p.vy *= k;
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
            if (p.y < 0.05f) { p.y = 0.05f; p.vy = -p.vy * 0.3f; }
        }
    }

    public void shutdown() { for (Player p : players) stopMini(p); }

    // ===================================================================== MULTIJUGADOR: instantaneas
    public byte[] writeSnapshot() throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(4096);
        DataOutputStream o = new DataOutputStream(bo);
        o.writeByte(state); o.writeFloat(timeLeft); o.writeBoolean(hasKey); o.writeBoolean(level.doorOpen);
        o.writeShort(ghostsAlive); o.writeBoolean(chase);
        if (state == GAME_OVER) o.writeUTF(overReason);
        for (Player p : players) {
            o.writeFloat(p.px); o.writeFloat(p.pz); o.writeFloat(p.yaw); o.writeFloat(p.pitch); o.writeFloat(p.vx); o.writeFloat(p.vz);
            o.writeFloat(p.hp); o.writeFloat(p.bobT); o.writeByte(p.state); o.writeByte(p.lives); o.writeShort(p.speedSteps);
            o.writeByte(p.sel); o.writeShort(p.ammo);
            for (int i = 0; i < SLOTS; i++) o.writeByte(p.inv[i] == null ? -1 : p.inv[i].ordinal());
            o.writeFloat(p.immuneT); o.writeFloat(p.invulnT); o.writeFloat(p.deathT);
            o.writeFloat(p.recoil); o.writeFloat(p.muzzle); o.writeFloat(p.swing); o.writeFloat(p.bowDraw); o.writeFloat(p.spin); o.writeFloat(p.throwT);
            o.writeShort(p.killedLevel); o.writeShort(p.starsLevel); o.writeShort(p.killedTotal); o.writeShort(p.starsTotal); o.writeFloat(p.maxSpeedPct);
            o.writeByte(p.colorIndex); o.writeFloat(p.walkAnim); o.writeShort(p.starsSpent); o.writeShort(p.soulsSaved);
        }
        o.writeShort(ghosts.size());
        for (Ghost g : ghosts) {
            o.writeShort(g.id); o.writeByte(g.type); o.writeFloat(g.x); o.writeFloat(g.z); o.writeFloat(g.offX); o.writeFloat(g.offZ);
            o.writeFloat(g.yawVis); o.writeFloat(g.hurtT); o.writeBoolean(g.dying); o.writeFloat(g.dyingT); o.writeFloat(g.trail); o.writeFloat(g.phase); o.writeBoolean(g.fast);
        }
        o.writeShort(souls.size());
        for (Soul so : souls) { o.writeShort(so.id); o.writeBoolean(so.female); o.writeByte(so.variant); o.writeByte(so.state); o.writeFloat(so.x); o.writeFloat(so.z); o.writeFloat(so.yaw); o.writeFloat(so.stateT); o.writeFloat(so.runAmt); o.writeFloat(so.phase); }
        o.writeShort(shops.size());
        for (ShopChest sc : shops) o.writeFloat(sc.anim);
        o.writeShort(projectiles.size());
        for (Projectile p : projectiles) { o.writeByte(p.kind); o.writeFloat(p.x); o.writeFloat(p.y); o.writeFloat(p.z); o.writeFloat(p.vx); o.writeFloat(p.vy); o.writeFloat(p.vz); o.writeFloat(p.spin); o.writeFloat(p.fuse); }
        o.writeShort(chests.size());
        for (Chest c : chests) o.writeBoolean(c.open);
        o.writeShort(pickups.size());
        for (Pickup k : pickups) o.writeBoolean(k.taken);
        o.writeShort(evCount);
        o.write(evBuf.toByteArray());
        evBuf.reset(); evCount = 0;
        return bo.toByteArray();
    }

    public void readSnapshot(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new java.io.ByteArrayInputStream(data));
        int st = in.readByte(); timeLeft = in.readFloat(); hasKey = in.readBoolean(); boolean door = in.readBoolean();
        ghostsAlive = in.readShort(); chase = in.readBoolean();
        if (st == GAME_OVER) overReason = in.readUTF();
        if (door && !level.doorOpen) level.doorOpen = true;
        for (Player p : players) {
            float x = in.readFloat(), z = in.readFloat(), yaw = in.readFloat(), pitch = in.readFloat(), vx = in.readFloat(), vz = in.readFloat();
            p.hp = in.readFloat(); float bobT = in.readFloat(); int ps = in.readByte(); p.lives = in.readByte(); p.speedSteps = in.readShort();
            p.sel = in.readByte(); p.ammo = in.readShort();
            for (int i = 0; i < SLOTS; i++) { int w = in.readByte(); p.inv[i] = w < 0 ? null : Weapon.values()[w]; }
            p.immuneT = in.readFloat(); p.invulnT = in.readFloat(); p.deathT = in.readFloat();
            p.recoil = Math.max(p.recoil, in.readFloat()); float mz = in.readFloat(); p.muzzle = Math.max(p.muzzle, mz); p.swing = in.readFloat(); p.bowDraw = in.readFloat(); p.spin = in.readFloat(); p.throwT = in.readFloat();
            p.killedLevel = in.readShort(); p.starsLevel = in.readShort(); p.killedTotal = in.readShort(); p.starsTotal = in.readShort(); p.maxSpeedPct = in.readFloat();
            p.colorIndex = in.readByte(); float walk = in.readFloat(); p.starsSpent = in.readShort(); p.soulsSaved = in.readShort();
            boolean wasAlive = p.state == Player.ALIVE;
            p.state = ps;
            if (p == me) {
                // el cliente manda en su posicion; solo se corrige si el anfitrion lo teletransporta (reaparicion, nuevo nivel)
                if (Math.hypot(x - p.px, z - p.pz) > 5f) { p.px = x; p.pz = z; }
                if (ps == Player.DYING) { p.yaw = yaw; p.pitch = pitch; p.eyeDrop = Math.min(1.2f, p.deathT * 1.2f); } else p.eyeDrop = 0;
            } else {
                p.sx = x; p.sz = z; p.syaw = yaw; p.spitch = pitch; p.vx = vx; p.vz = vz; p.bobT = bobT; p.walkAnim = walk;
                if (Math.hypot(x - p.px, z - p.pz) > 8f) { p.px = x; p.pz = z; }
            }
        }
        int ng = in.readShort();
        List<Ghost> list = new ArrayList<>(ng);
        for (int i = 0; i < ng; i++) {
            int id = in.readShort();
            Ghost g = ghostById.get(id);
            boolean fresh = g == null;
            if (fresh) { g = new Ghost(); g.id = id; ghostById.put(id, g); }
            g.type = in.readByte(); g.sx = in.readFloat(); g.sz = in.readFloat(); g.sox = in.readFloat(); g.soz = in.readFloat();
            g.syaw = in.readFloat(); g.hurtT = in.readFloat(); g.dying = in.readBoolean(); g.dyingT = in.readFloat(); g.trail = in.readFloat(); g.phase = in.readFloat(); g.fast = in.readBoolean();
            if (fresh) { g.x = g.sx; g.z = g.sz; g.offX = g.sox; g.offZ = g.soz; g.yawVis = g.syaw; }
            list.add(g);
        }
        for (Ghost g : ghosts) if (!list.contains(g)) ghostById.remove(g.id);
        ghosts.clear(); ghosts.addAll(list);
        int nso = in.readShort();
        List<Soul> sl = new ArrayList<>(nso);
        for (int i = 0; i < nso; i++) {
            int id = in.readShort();
            Soul so = soulById.get(id);
            boolean fresh = so == null;
            if (fresh) { so = new Soul(); so.id = id; soulById.put(id, so); }
            so.female = in.readBoolean(); so.variant = in.readByte(); so.state = in.readByte(); so.sx = in.readFloat(); so.sz = in.readFloat(); so.syaw = in.readFloat();
            so.stateT = in.readFloat(); so.runAmt = in.readFloat(); so.phase = in.readFloat();
            if (fresh || so.state != Soul.FLEE) { so.x = so.sx; so.z = so.sz; so.yaw = so.syaw; }
            sl.add(so);
        }
        for (Soul so : souls) if (!sl.contains(so)) soulById.remove(so.id);
        souls.clear(); souls.addAll(sl);
        int nsh = in.readShort();
        for (int i = 0; i < nsh; i++) { float a = in.readFloat(); if (i < shops.size()) shops.get(i).anim = a; }
        int np = in.readShort();
        projectiles.clear();
        for (int i = 0; i < np; i++) {
            Projectile p = new Projectile();
            p.kind = in.readByte(); p.x = in.readFloat(); p.y = in.readFloat(); p.z = in.readFloat(); p.vx = in.readFloat(); p.vy = in.readFloat(); p.vz = in.readFloat(); p.spin = in.readFloat(); p.fuse = in.readFloat();
            projectiles.add(p);
        }
        int nc = in.readShort();
        for (int i = 0; i < nc; i++) { boolean o = in.readBoolean(); if (i < chests.size() && o && !chests.get(i).open) chests.get(i).open = true; }
        int nk = in.readShort();
        for (int i = 0; i < nk; i++) { boolean t = in.readBoolean(); if (i < pickups.size() && t) pickups.get(i).taken = true; }
        int ne = in.readShort();
        for (int i = 0; i < ne; i++) applyEvent(in);
        if (st != state) {
            state = st;
            if (st == GAME_OVER || st == LEVEL_DONE || st == ALL_DONE) for (Player p : players) stopMini(p);
        }
    }

    private void applyEvent(DataInputStream in) throws IOException {
        int t = in.readByte();
        switch (t) {
            case EV_SOUND: { String n = in.readUTF(); float v = in.readFloat(), x = in.readFloat(), z = in.readFloat(); int src = in.readByte(); playLocal(n, v, x, z, src, me); break; }
            case EV_BURST: { float x = in.readFloat(), y = in.readFloat(), z = in.readFloat(); int n = in.readByte(); burstLocal(x, y, z, n, in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()); break; }
            case EV_RING: ringLocal(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()); break;
            case EV_MSG: { int pid = in.readByte(); String s = in.readUTF(); int c = in.readInt(); if (pid < 0 || pid == me.id) say(me, s, c); break; }
            case EV_BIG: { int pid = in.readByte(); String s = in.readUTF(); int c = in.readInt(); float secs = in.readFloat(); if (pid < 0 || pid == me.id) big(me, s, c, secs); break; }
            case EV_FLASH: flash = Math.max(flash, in.readFloat()); break;
            case EV_LIGHT: lightning = 1f; break;
            case EV_DECAL: { decals.add(new float[]{in.readFloat(), in.readFloat(), 0}); if (decals.size() > 40) decals.remove(0); break; }
            case EV_KEYHINT: { int pid = in.readByte(); float kx = in.readFloat(), kz = in.readFloat(); if (pid == me.id) { me.keyHintT = 2f; me.keyHintX = kx; me.keyHintZ = kz; } break; }
            case EV_BLOOD: { blood.add(new float[]{in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()}); if (blood.size() > 120) blood.remove(0); break; }
            case EV_SHAKE: case EV_HURT: case EV_HIT: case EV_PICK: case EV_DRY: case EV_TRAPFX: {
                int pid = in.readByte(); float a = in.readFloat(), b = in.readFloat(), c = in.readFloat();
                if (pid == me.id) applyPlayerFx(me, t, a, b, c);
                break;
            }
            default: break;
        }
    }

    /** Actualizacion del cliente espejo: movimiento propio + suavizado de lo remoto. */
    public void mirrorUpdate(float dt, Input in) {
        dt = Math.min(dt, 0.05f);
        time += dt; rainTime += dt;
        decayEffects(dt);
        if (state == PLAY && me.state == Player.ALIVE) {
            moveLocal(me, in, dt);
            explore(me);
            ambient(me, dt);
            // calcular avisos de cofre/puerta
            me.promptChest = false;
            for (Chest c : chests) if (!c.open && Math.hypot(c.x - me.px, c.z - me.pz) < 3.6f) { me.promptChest = true; break; }
            me.promptShop = false;
            for (ShopChest sc : shops) if (Math.hypot(sc.x - me.px, sc.z - me.pz) < 3.4f) { me.promptShop = true; me.shopPrice = shopPrice(); break; }
            me.inTrap = level.trapAt(me.px, me.pz);
            me.promptDoor = !level.doorOpen && Math.hypot(Level.center(level.maze.doorX) - me.px, Level.center(level.maze.doorZ) - me.pz) < 7f;
        }
        for (Player p : players) {
            if (p == me) continue;
            float k = Math.min(1, dt * 14f);
            p.px += (p.sx - p.px) * k; p.pz += (p.sz - p.pz) * k;
            float da = p.syaw - p.yaw;
            while (da > Math.PI) da -= 6.2832f;
            while (da < -Math.PI) da += 6.2832f;
            p.yaw += da * k; p.pitch += (p.spitch - p.pitch) * k;
        }
        updateParticles(dt);
        smoothGhosts(dt);
        smoothSouls(dt);
        animateWorld(dt);
    }

    private void smoothGhosts(float dt) {
        float k = Math.min(1, dt * 14f);
        for (Ghost g : ghosts) {
            g.x += (g.sx - g.x) * k; g.z += (g.sz - g.z) * k; g.offX += (g.sox - g.offX) * k; g.offZ += (g.soz - g.offZ) * k;
            float da = g.syaw - g.yawVis;
            while (da > Math.PI) da -= 6.2832f;
            while (da < -Math.PI) da += 6.2832f;
            g.yawVis += da * k;
            g.bob = 0.35f + 0.22f * (float) Math.sin(time * 2.1f + g.phase);
            g.hurtT = Math.max(0, g.hurtT - dt);
            if (g.dying) g.dyingT += dt * 0;
        }
    }

    /** Control remoto: el cliente envia su estado y el anfitrion lo aplica. */
    public void applyRemoteInput(Player p, float x, float z, float yaw, float pitch, boolean fire, boolean zoom, int interact, int slot, int wheel) {
        p.rzoom = zoom; p.rx = x; p.rz = z; p.ryaw = yaw; p.rpitch = pitch; p.rfire = fire; p.rfresh = true;
        p.rinteract += interact; if (slot >= 0) p.rslot = slot; p.rwheel += wheel;
    }

    public void setNetActive(boolean b) { netActive = b; }
}
