package com.enhort.gnomes.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.enhort.gnomes.GnomesGame;

public class Lwjgl3Launcher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("GNOMES");
        config.setWindowedMode(420, 840);
        config.setForegroundFPS(120);
        config.useVsync(true);
        config.setResizable(true);
        new Lwjgl3Application(new GnomesGame(), config);
    }
}
