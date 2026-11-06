package com.pacman3d.game;

/** Las 6 armas. Todas usan la misma municion, excepto la espada (infinita). */
public enum Weapon {
    PISTOL("PISTOLA", 18, 0.42f, 1, 21f, false, 1.7f),
    RIFLE("RIFLE", 34, 0.13f, 1, 58f, true, 3.5f),
    BOW("ARCO", 34, 0.9f, 1, 55f, false, 2.6f),
    MINIGUN("MINIGUN", 25, 0.045f, 1, 52f, true, 1.6f),
    SWORD("ESPADA", 17, 0.5f, 0, 0f, false, 1f),
    GRENADE("GRANADA", 999, 1.1f, 5, 16f, false, 1f);

    public final String label;
    public final int damage;
    public final float cooldown;
    public final int ammoCost;
    public final float speed;
    public final boolean auto;
    public final float zoom;   // aumento al apuntar (clic derecho); 1 = sin zoom

    Weapon(String label, int damage, float cooldown, int ammoCost, float speed, boolean auto, float zoom) {
        this.zoom = zoom;
        this.label = label; this.damage = damage; this.cooldown = cooldown; this.ammoCost = ammoCost; this.speed = speed; this.auto = auto;
    }
}
