package com.pacman3d.game;

/** Colores de Pac-Man disponibles en la tienda (precio en estrellas). */
public final class Palette {
    private Palette() {}

    public static final String[] NAMES = {"Amarillo clasico", "Rojo sangre", "Azul medianoche", "Verde toxico", "Rosa fantasma", "Naranja calabaza",
            "Morado bruja", "Cian espectral", "Blanco hueso", "Negro abismo", "Oro maldito", "Lima radiactivo"};
    public static final float[][] RGB = {{1.00f, 0.88f, 0.05f}, {0.90f, 0.10f, 0.10f}, {0.15f, 0.35f, 0.95f}, {0.20f, 0.85f, 0.25f},
            {1.00f, 0.45f, 0.75f}, {1.00f, 0.50f, 0.05f}, {0.60f, 0.20f, 0.90f}, {0.20f, 0.90f, 0.95f}, {0.90f, 0.90f, 0.82f},
            {0.10f, 0.10f, 0.13f}, {1.00f, 0.75f, 0.15f}, {0.65f, 1.00f, 0.10f}};
    public static final int[] COST = {0, 0, 0, 0, 25, 25, 40, 40, 60, 80, 120, 150};
}
