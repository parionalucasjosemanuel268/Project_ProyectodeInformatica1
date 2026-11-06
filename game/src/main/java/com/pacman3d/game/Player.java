package com.pacman3d.game;

import com.pacman3d.audio.Audio;

/** Un jugador (local o remoto). Todo el estado individual vive aqui. */
public final class Player {
    public static final int ALIVE = 0, DYING = 1, OUT = 2;

    public int id, colorIndex;
    public boolean remote;                // controlado por otro ordenador (lado anfitrion)
    public int state = ALIVE;
    public float px, pz, yaw, pitch, hp = Game.MAX_HP, bobT, bob, vx, vz, dirX, dirZ = 1, eyeDrop, walkAnim;
    public int lives = Game.START_LIVES, speedSteps;
    public Weapon[] inv = new Weapon[Game.SLOTS];
    public int sel = -1, ammo;
    public float immuneT, invulnT, deathT;
    public int killedLevel, starsLevel, killedTotal, starsTotal, shotsFired;
    public float maxSpeedPct = 100;

    // arma en mano
    public float fireCd, recoil, swing = -1, bowDraw, spin, throwT = -1, muzzle;
    public boolean swingHit, dryCd, tpv;
    public Audio.Voice miniVoice;

    // efectos solo visuales (del jugador local)
    public float dryBanner, hurtFlash, pickFlash, shake, hitMarker;
    public boolean promptChest, promptDoor, promptShop, zoom, promptSoul;
    public float zoomT, keyHintT, keyHintX, keyHintZ, trapFlash, trapTick;
    public int inTrap, starsSpent, soulsSaved, shopPrice;
    public float hurtDir;

    // entrada remota (la escribe el hilo de red)
    public volatile float rx, rz, ryaw, rpitch;
    public volatile boolean rfire, rfresh, rzoom;
    public volatile int rinteract, rslot = -1, rwheel;

    // interpolacion en el cliente
    public float sx, sz, syaw, spitch;

    public int wallet() { return starsTotal - starsSpent; }

    public float speedPct() { return Math.min(200f, 100f + speedSteps * 4f); }

    public Weapon current() { return sel >= 0 && sel < inv.length ? inv[sel] : null; }

    public boolean alive() { return state == ALIVE; }
}
