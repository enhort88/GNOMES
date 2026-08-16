package com.enhort.gnomes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.TimeUtils;

import java.util.EnumMap;

/** Centralized SFX, ambient music and haptics with throttling for large crowds. */
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
    private Music music;

    public GameAudio(GameSettings settings) {
        this.settings = settings;
        for (Sfx sfx : Sfx.values()) {
            try { sounds.put(sfx, Gdx.audio.newSound(Gdx.files.internal(sfx.path))); }
            catch (Exception ignored) { }
            lastPlayed.put(sfx, 0L);
        }
        try {
            music = Gdx.audio.newMusic(Gdx.files.internal("music/mine_loop_v2.wav"));
            music.setLooping(true);
            music.play();
            refreshMusic();
        } catch (Exception ignored) { music = null; }
    }

    public void refreshMusic() {
        if (music == null) return;
        float volume = settings.musicEnabled ? Math.max(0f, Math.min(1f, settings.musicVolume * .50f)) : 0f;
        music.setVolume(volume);
        if (!music.isPlaying()) music.play();
    }

    public void play(Sfx sfx) { play(sfx, 1f); }

    public void play(Sfx sfx, float gain) {
        refreshMusic();
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
        if (music != null) { music.stop(); music.dispose(); music = null; }
        for (Sound sound : sounds.values()) if (sound != null) sound.dispose();
        sounds.clear();
    }
}
