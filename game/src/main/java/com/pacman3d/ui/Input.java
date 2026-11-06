package com.pacman3d.ui;

/** Estado de teclado/raton compartido entre la ventana y el juego. */
public final class Input {
    public volatile boolean w, a, s, d, left, right, up, down, fire, fireAlt;
    public volatile boolean tpvPressed, interactPressed, pausePressed, enterPressed, backPressed;
    public volatile int slotKey = -1;      // 0..7 al pulsar 1..8
    public volatile int wheel;             // + siguiente, - anterior
    public volatile float mdx, mdy;        // movimiento de raton acumulado
    public volatile boolean mouseClicked;  // clic izquierdo (menus)
    public volatile int mouseX, mouseY;
    public volatile boolean captured;

    public boolean consumeTpv() { boolean v = tpvPressed; tpvPressed = false; return v; }
    public boolean consumeInteract() { boolean v = interactPressed; interactPressed = false; return v; }
    public boolean consumePause() { boolean v = pausePressed; pausePressed = false; return v; }
    public boolean consumeEnter() { boolean v = enterPressed; enterPressed = false; return v; }
    public boolean consumeBack() { boolean v = backPressed; backPressed = false; return v; }
    public boolean consumeClick() { boolean v = mouseClicked; mouseClicked = false; return v; }
    public int consumeSlot() { int v = slotKey; slotKey = -1; return v; }
    public int consumeWheel() { int v = wheel; wheel = 0; return v; }
    public float[] consumeMouse() { float[] r = {mdx, mdy}; mdx = 0; mdy = 0; return r; }
}
