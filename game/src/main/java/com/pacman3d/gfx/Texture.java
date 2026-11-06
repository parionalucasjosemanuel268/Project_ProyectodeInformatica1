package com.pacman3d.gfx;

import java.util.ArrayList;
import java.util.List;

/**
 * Textura cuadrada de potencia de 2. El canal alfa tiene significado especial:
 * 0 = transparente (se descarta el pixel), 255 = normal, 1..254 = emisivo
 * (cuanto mas bajo, mas brilla por si mismo, ignorando la luz y la oscuridad).
 */
public final class Texture {
    static final List<Texture> REGISTRY = new ArrayList<>();
    public final int id;
    public final int size, mask, shift;
    public final int[] px;

    public Texture(int size) {
        this.size = size;
        this.mask = size - 1;
        this.shift = Integer.numberOfTrailingZeros(size);
        this.px = new int[size * size];
        synchronized (REGISTRY) {
            this.id = REGISTRY.size();
            REGISTRY.add(this);
        }
    }

    public static Texture[] table() {
        synchronized (REGISTRY) {
            return REGISTRY.toArray(new Texture[0]);
        }
    }

    public void set(int x, int y, int argb) {
        px[((y & mask) << shift) + (x & mask)] = argb;
    }

    public int get(int x, int y) {
        return px[((y & mask) << shift) + (x & mask)];
    }

    /** Textura de 1 color solido (4x4). */
    public static Texture solid(int argb) {
        Texture t = new Texture(4);
        java.util.Arrays.fill(t.px, argb);
        return t;
    }
}
