package com.pacman3d;

import com.pacman3d.ui.App;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        System.setProperty("sun.java2d.opengl", "false");
        System.setProperty("sun.java2d.noddraw", "true");
        new Thread(() -> new App().start(), "game-loop").start();
    }
}
