package com.pacman3d.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Mezclador de audio propio: efectos con volumen/paneo, bucles, musica en streaming
 * y sonidos sintetizados (muerte, corazon, trueno...). Funciona sin bloquear el juego.
 */
public final class Audio {
    private static final int SR = 44100;
    private static final Map<String, float[]> SFX = new HashMap<>();
    private static final List<Voice> voices = new ArrayList<>();
    private static volatile boolean running, enabled = true;
    private static SourceDataLine line;
    private static Music music;
    public static float masterSfx = 1f, masterMusic = 0.7f;

    public static final class Voice {
        float[] d; double pos; volatile float vol, pan; boolean loop; volatile boolean stop; float fadeOut = 0; float gain = 1f;
        public void setVolume(float v) { vol = v; }
        public void setPan(float p) { pan = p; }
        public void stop() { stop = true; }
    }

    private static final class Music {
        String res; InputStream in; AudioInputStream ais; volatile float vol = 0.7f, target = 0.7f; volatile boolean stop;
        float cur = 0f;
        byte[] buf = new byte[4096]; int have, at;
        void open() throws Exception {
            if (ais != null) ais.close();
            InputStream raw = Audio.class.getResourceAsStream(res);
            if (raw == null) { ais = null; return; }
            ais = AudioSystem.getAudioInputStream(new BufferedInputStream(raw, 1 << 16));
            have = at = 0;
        }
        /** Devuelve un frame estereo en out[0..1] (float -1..1). */
        boolean next(float[] out) {
            try {
                if (ais == null) { open(); if (ais == null) return false; }
                if (at + 4 > have) {
                    int n = ais.read(buf, 0, buf.length);
                    if (n < 4) { open(); return false; }
                    have = n - (n % 4); at = 0;
                }
                short l = (short) ((buf[at] & 255) | (buf[at + 1] << 8)), r = (short) ((buf[at + 2] & 255) | (buf[at + 3] << 8));
                at += 4;
                out[0] = l / 32768f; out[1] = r / 32768f;
                return true;
            } catch (Exception e) { return false; }
        }
    }

    // --------------------------------------------------------------- inicio
    public static void init() {
        if (running) return;
        running = true;
        try {
            String[] names = {"pistol", "rifle", "bow", "sword", "grenade", "hurt_ghost", "player_hurt", "chest", "key", "medkit", "ammo", "star", "powerup",
                    "victory", "ghost_kill", "click", "scare1", "scare2", "scare3", "scare4", "scare5", "scare6", "drone", "minigun_loop"};
            for (String n : names) load(n);
            synth();
        } catch (Throwable t) { System.err.println("Audio: " + t); }
        Thread th = new Thread(Audio::mixLoop, "audio-mixer");
        th.setDaemon(true);
        th.start();
    }

    private static void load(String n) {
        try (InputStream raw = Audio.class.getResourceAsStream("/assets/sfx/" + n + ".wav")) {
            if (raw == null) return;
            AudioInputStream a = AudioSystem.getAudioInputStream(new BufferedInputStream(raw));
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192]; int k;
            while ((k = a.read(b)) > 0) bo.write(b, 0, k);
            byte[] all = bo.toByteArray();
            float[] f = new float[all.length / 2];
            for (int i = 0; i < f.length; i++) f[i] = (short) ((all[i * 2] & 255) | (all[i * 2 + 1] << 8)) / 32768f;
            SFX.put(n, f);
        } catch (Exception e) { System.err.println("No se pudo cargar sonido " + n + ": " + e); }
    }

    private static void synth() {
        Random r = new Random(5);
        // clic en seco (sin balas)
        float[] c = new float[(int) (SR * 0.09)];
        for (int i = 0; i < c.length; i++) { float e = (float) Math.exp(-i / (SR * 0.01)); c[i] = (r.nextFloat() * 2 - 1) * e * 0.5f + (float) Math.sin(i * 0.25) * e * 0.4f; }
        SFX.put("dry", c);
        // muerte de Pac-Man (descenso clasico + ruido)
        float[] d = new float[(int) (SR * 1.9)];
        for (int i = 0; i < d.length; i++) {
            float t = i / (float) SR, f = 1000 * (float) Math.exp(-t * 1.9f) + 90;
            double ph = 0; // fase integrada aproximada
            d[i] = (float) (Math.sin(2 * Math.PI * (f * t * 0.6)) * 0.55 * Math.max(0, 1 - t / 1.9) + (r.nextFloat() - 0.5) * 0.25 * Math.max(0, 1 - t * 2));
            if (((int) (t * 14)) % 2 == 0 && t > 0.5) d[i] *= 0.4f;
        }
        SFX.put("death", d);
        // latido
        float[] h = new float[(int) (SR * 0.6)];
        for (int b = 0; b < 2; b++) {
            int off = (int) (SR * (b == 0 ? 0 : 0.22));
            for (int i = 0; i < SR * 0.18 && off + i < h.length; i++) { float t = i / (float) SR; h[off + i] += (float) (Math.sin(2 * Math.PI * 52 * t) * Math.exp(-t * 22) * (b == 0 ? 1 : 0.7)); }
        }
        SFX.put("heart", h);
        // trueno
        float[] th = new float[(int) (SR * 3.2)];
        float lp = 0;
        for (int i = 0; i < th.length; i++) { float t = i / (float) SR; lp += ((r.nextFloat() * 2 - 1) - lp) * 0.04f; th[i] = lp * (float) (Math.exp(-t * 1.1) * (0.6 + 0.4 * Math.sin(t * 9))) * 5f; }
        SFX.put("thunder", th);
        // pasos
        float[] st = new float[(int) (SR * 0.12)];
        float l2 = 0;
        for (int i = 0; i < st.length; i++) { l2 += ((r.nextFloat() * 2 - 1) - l2) * 0.12f; st[i] = l2 * (float) Math.exp(-i / (SR * 0.025)) * 1.6f; }
        SFX.put("step", st);
        // golpe de espada en el fantasma / impacto
        float[] hit = new float[(int) (SR * 0.2)];
        for (int i = 0; i < hit.length; i++) { float t = i / (float) SR; hit[i] = (float) (Math.sin(2 * Math.PI * 140 * t) * Math.exp(-t * 25) * 0.8 + (r.nextFloat() - 0.5) * Math.exp(-t * 40) * 0.5); }
        SFX.put("hit", hit);
        // derrota
        float[] lose = new float[(int) (SR * 2.6)];
        for (int i = 0; i < lose.length; i++) { float t = i / (float) SR, f = 330 * (float) Math.pow(0.5, t * 1.4); lose[i] = (float) (Math.sin(2 * Math.PI * f * t) * 0.5 * Math.max(0, 1 - t / 2.6) * (0.6 + 0.4 * Math.sin(t * 20))); }
        SFX.put("lose", lose);
        // grito de susto: barrido agudo + ruido + temblor
        float[] scr = new float[(int) (SR * 1.5)];
        double ph1 = 0, ph2 = 0;
        for (int i = 0; i < scr.length; i++) {
            float t = i / (float) SR;
            double f1 = 700 + 1600 * Math.sin(t * 5.0) * Math.exp(-t * 0.8) + 500 * t, f2 = f1 * 1.51;
            ph1 += 2 * Math.PI * f1 / SR; ph2 += 2 * Math.PI * f2 / SR;
            double saw = ((ph1 / (2 * Math.PI)) % 1.0) * 2 - 1, sq = Math.signum(Math.sin(ph2));
            float env = (float) (Math.min(1, t * 40) * Math.exp(-Math.max(0, t - 0.55) * 2.6));
            float trem = 0.7f + 0.3f * (float) Math.sin(t * 60);
            scr[i] = (float) ((saw * 0.45 + sq * 0.25 + (r.nextFloat() * 2 - 1) * 0.3) * env * trem * 0.8);
        }
        SFX.put("screech", scr);
        // lluvia (ruido filtrado en bucle)
        float[] rain = new float[SR * 3];
        float l3 = 0, l4 = 0;
        for (int i = 0; i < rain.length; i++) { float n = r.nextFloat() * 2 - 1; l3 += (n - l3) * 0.35f; l4 += (l3 - l4) * 0.5f; rain[i] = (l3 - l4) * 1.8f + n * 0.06f; }
        int xf = SR / 4;
        for (int i = 0; i < xf; i++) { float k = i / (float) xf; rain[i] = rain[i] * k + rain[rain.length - xf + i] * (1 - k); }
        SFX.put("rain", java.util.Arrays.copyOf(rain, rain.length - xf));
        // fuego/chispa de explosion de fantasma
        float[] zap = new float[(int) (SR * 0.3)];
        for (int i = 0; i < zap.length; i++) { float t = i / (float) SR; zap[i] = (float) (Math.sin(2 * Math.PI * (900 - 2200 * t) * t) * Math.exp(-t * 9) * 0.5); }
        SFX.put("zap", zap);
    }

    // --------------------------------------------------------------- API
    public static Voice play(String name, float vol) { return play(name, vol, 0f, false); }

    public static Voice play(String name, float vol, float pan) { return play(name, vol, pan, false); }

    public static synchronized Voice play(String name, float vol, float pan, boolean loop) {
        float[] d = SFX.get(name);
        if (d == null || !enabled) return null;
        Voice v = new Voice();
        v.d = d; v.vol = vol; v.pan = pan; v.loop = loop;
        synchronized (voices) {
            if (voices.size() > 56) voices.remove(0);
            voices.add(v);
        }
        return v;
    }

    public static String[] SCARES = {"scare1", "scare2", "scare3", "scare4", "scare5", "scare6"};

    public static void musicPlay(String resource, float vol) {
        if (music != null && music.res.equals(resource)) { music.target = vol; return; }
        Music m = new Music();
        m.res = resource; m.target = vol; m.cur = 0f;
        synchronized (Audio.class) { if (music != null) music.stop = true; music = m; }
    }

    public static void musicVolume(float v) { Music m = music; if (m != null) m.target = v; }
    public static void musicStop() { synchronized (Audio.class) { if (music != null) music.target = 0f; music.stop = true; } }

    // --------------------------------------------------------------- mezcla
    private static void mixLoop() {
        try {
            AudioFormat fmt = new AudioFormat(SR, 16, 2, true, false);
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, SR / 4 * 4);
            line.start();
        } catch (Throwable t) {
            System.err.println("Sin dispositivo de audio: " + t.getMessage());
            enabled = false;
            return;
        }
        final int N = 1024;
        byte[] out = new byte[N * 4];
        float[] mus = new float[2];
        Music fading = null;
        while (running) {
            Music m;
            synchronized (Audio.class) { m = music; }
            for (int i = 0; i < N; i++) {
                float l = 0, r = 0;
                synchronized (voices) {
                    for (int k = voices.size() - 1; k >= 0; k--) {
                        Voice v = voices.get(k);
                        int ip = (int) v.pos;
                        if (ip >= v.d.length) { if (v.loop && !v.stop) { v.pos = 0; ip = 0; } else { voices.remove(k); continue; } }
                        if (v.stop) { v.gain -= 1f / (SR * 0.15f); if (v.gain <= 0) { voices.remove(k); continue; } }
                        float s = v.d[ip] * v.vol * v.gain * masterSfx;
                        float p = Math.max(-1, Math.min(1, v.pan));
                        l += s * (1 - Math.max(0, p)); r += s * (1 + Math.min(0, p));
                        v.pos += 1;
                    }
                }
                if (m != null) {
                    float dv = m.target - m.cur;
                    m.cur += Math.max(-0.00004f, Math.min(0.00004f, dv));
                    if (m.stop && m.cur <= 0.001f) { synchronized (Audio.class) { if (music == m) music = null; } m = null; }
                    else if (m.next(mus)) { l += mus[0] * m.cur * masterMusic; r += mus[1] * m.cur * masterMusic; }
                }
                l = soft(l); r = soft(r);
                int il = (int) (l * 32000), ir = (int) (r * 32000);
                out[i * 4] = (byte) il; out[i * 4 + 1] = (byte) (il >> 8); out[i * 4 + 2] = (byte) ir; out[i * 4 + 3] = (byte) (ir >> 8);
            }
            line.write(out, 0, out.length);
        }
    }

    private static float soft(float x) { return x > 1 ? 1 : x < -1 ? -1 : x - x * x * x / 3f * (Math.abs(x) > 0.7f ? 0.6f : 0f); }
}
