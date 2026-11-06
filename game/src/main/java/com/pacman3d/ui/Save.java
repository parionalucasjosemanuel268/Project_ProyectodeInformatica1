package com.pacman3d.ui;

import com.pacman3d.game.Palette;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/** Guarda estrellas ahorradas, colores comprados y color elegido en ~/.pacman3d/save.properties */
public final class Save {
    public int bank, color;
    public String lastIp = "";
    public final boolean[] owned = new boolean[Palette.NAMES.length];
    private final File file = new File(System.getProperty("user.home"), ".pacman3d/save.properties");

    public Save() {
        for (int i = 0; i < owned.length; i++) owned[i] = Palette.COST[i] == 0;
        try (FileInputStream in = new FileInputStream(file)) {
            Properties p = new Properties();
            p.load(in);
            bank = Integer.parseInt(p.getProperty("bank", "0"));
            color = Integer.parseInt(p.getProperty("color", "0"));
            lastIp = p.getProperty("lastIp", "");
            String o = p.getProperty("owned", "");
            for (String s : o.split(",")) if (!s.isEmpty()) { int i = Integer.parseInt(s); if (i >= 0 && i < owned.length) owned[i] = true; }
        } catch (Exception ignored) { }
        if (color < 0 || color >= owned.length || !owned[color]) color = 0;
    }

    public void save() {
        try {
            file.getParentFile().mkdirs();
            Properties p = new Properties();
            p.setProperty("bank", String.valueOf(bank));
            p.setProperty("color", String.valueOf(color));
            p.setProperty("lastIp", lastIp);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < owned.length; i++) if (owned[i]) sb.append(i).append(',');
            p.setProperty("owned", sb.toString());
            try (FileOutputStream out = new FileOutputStream(file)) { p.store(out, "PACMAN 3D"); }
        } catch (Exception ignored) { }
    }
}
