package com.enhort.gnomes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.TimeUtils;

import java.util.EnumMap;

/** Small centralized SFX/haptics service with throttling so a crowd of miners does not become a wall of noise. */
public final class GameAudio implements Disposable {
    public enum Sfx {
        UI("sfx/ui.wav", 35),
        PICK("sfx/pick.wav", 45),
        ROCK_BREAK("sfx/rock_break.wav", 80),
        COIN("sfx/coin.wav", 70),
        ENEMY("sfx/enemy.wav", 90),
        COLLAPSE("sfx/collapse.wav", 180),
        HAZARD("sfx/hazard.wav", 140),
        BOSS("sfx/boss.wav", 250);

        final String path;
        final long cooldownMs;
        Sfx(String path, long cooldownMs) { this.path = path; this.cooldownMs = cooldownMs; }
    }

    private final GameSettings settings;
    private final EnumMap<Sfx, Sound> sounds = new EnumMap<>(Sfx.class);
    private final EnumMap<Sfx, Long> lastPlayed = new EnumMap<>(Sfx.class);

    public GameAudio(GameSettings settings) {
        this.settings = settings;
        for (Sfx sfx : Sfx.values()) {
            try { sounds.put(sfx, Gdx.audio.newSound(Gdx.files.internal(sfx.path))); }
            catch (Exception ignored) { }
            lastPlayed.put(sfx, 0L);
        }
    }

    public void play(Sfx sfx) { play(sfx, 1f); }

    public void play(Sfx sfx, float gain) {
        if (!settings.soundEnabled || settings.soundVolume <= 0f) return;
        Sound sound = sounds.get(sfx);
        if (sound == null) return;
        long now = TimeUtils.millis();
        long last = lastPlayed.getOrDefault(sfx, 0L);
        if (now - last < sfx.cooldownMs) return;
        lastPlayed.put(sfx, now);
        sound.play(Math.max(0f, Math.min(1f, settings.soundVolume * gain)));
    }

    public void vibrate(int milliseconds) {
        if (!settings.vibrationEnabled || milliseconds <= 0) return;
        try { Gdx.input.vibrate(milliseconds); }
        catch (Exception ignored) { }
    }

    @Override public void dispose() {
        for (Sound sound : sounds.values()) if (sound != null) sound.dispose();
        sounds.clear();
    }
}
