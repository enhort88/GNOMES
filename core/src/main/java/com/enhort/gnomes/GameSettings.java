package com.enhort.gnomes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/** Global user-facing settings. Kept outside save slots. */
public final class GameSettings {
    private static final String PREFS = "gnomes.settings.v1";
    private final Preferences prefs;

    public boolean soundEnabled;
    public boolean musicEnabled;
    public boolean vibrationEnabled;
    public float soundVolume;
    public float musicVolume;
    public boolean freeShop;
    public boolean introSeen;

    public GameSettings() {
        prefs = Gdx.app.getPreferences(PREFS);
        soundEnabled = prefs.getBoolean("sound", true);
        musicEnabled = prefs.getBoolean("music", true);
        vibrationEnabled = prefs.getBoolean("vibration", true);
        soundVolume = clamp(prefs.getFloat("volume", 0.75f));
        musicVolume = clamp(prefs.getFloat("musicVolume", 0.62f));
        freeShop = prefs.getBoolean("freeShop", false);
        introSeen = prefs.getBoolean("introSeen", false);
    }

    public void toggleSound() { soundEnabled = !soundEnabled; save(); }
    public void toggleMusic() { musicEnabled = !musicEnabled; save(); }
    public void toggleVibration() { vibrationEnabled = !vibrationEnabled; save(); }
    public void setSoundVolume(float value) { soundVolume = clamp(value); save(); }
    public void setMusicVolume(float value) { musicVolume = clamp(value); save(); }
    public void toggleFreeShop() { freeShop = !freeShop; save(); }
    public void markIntroSeen() { introSeen = true; save(); }

    public void save() {
        prefs.putBoolean("sound", soundEnabled);
        prefs.putBoolean("music", musicEnabled);
        prefs.putBoolean("vibration", vibrationEnabled);
        prefs.putFloat("volume", soundVolume);
        prefs.putFloat("musicVolume", musicVolume);
        prefs.putBoolean("freeShop", freeShop);
        prefs.putBoolean("introSeen", introSeen);
        prefs.flush();
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}
