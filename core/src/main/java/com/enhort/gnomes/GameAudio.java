package com.enhort.gnomes;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.TimeUtils;

import java.util.EnumMap;

/** Centralized SFX, adaptive music and haptics with throttling for large crowds. */
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

    public enum MusicMood {
        MINE("music/mine_loop_v3.wav", .70f),
        DANGER("music/danger_loop.wav", .76f),
        BOSS("music/boss_loop.wav", .84f);

        final String path;
        final float gain;
        MusicMood(String path, float gain) { this.path = path; this.gain = gain; }
    }

    private final GameSettings settings;
    private final EnumMap<Sfx, Sound> sounds = new EnumMap<>(Sfx.class);
    private final EnumMap<Sfx, Long> lastPlayed = new EnumMap<>(Sfx.class);
    private final EnumMap<MusicMood, Music> tracks = new EnumMap<>(MusicMood.class);
    private MusicMood mood = MusicMood.MINE;

    public GameAudio(GameSettings settings) {
        this.settings = settings;
        for (Sfx sfx : Sfx.values()) {
            try { sounds.put(sfx, Gdx.audio.newSound(Gdx.files.internal(sfx.path))); }
            catch (Exception ignored) { }
            lastPlayed.put(sfx, 0L);
        }
        for (MusicMood value : MusicMood.values()) {
            try {
                Music music = Gdx.audio.newMusic(Gdx.files.internal(value.path));
                music.setLooping(true);
                tracks.put(value, music);
            } catch (Exception ignored) { }
        }
        // Old installs/working copies may briefly miss the newly generated ambient asset.
        if (!tracks.containsKey(MusicMood.MINE)) {
            try {
                Music fallback = Gdx.audio.newMusic(Gdx.files.internal("music/mine_loop_v2.wav"));
                fallback.setLooping(true);
                tracks.put(MusicMood.MINE, fallback);
            } catch (Exception ignored) { }
        }
        refreshMusic();
    }

    public void setMusicMood(MusicMood next) {
        if (next == null) next = MusicMood.MINE;
        if (next == mood) { refreshMusic(); return; }
        Music old = tracks.get(mood);
        if (old != null) old.pause();
        mood = next;
        refreshMusic();
    }

    public MusicMood musicMood() { return mood; }

    public void refreshMusic() {
        Music active = tracks.get(mood);
        if (active == null && mood != MusicMood.MINE) active = tracks.get(MusicMood.MINE);
        if (active == null) return;

        if (!settings.musicEnabled || settings.musicVolume <= 0f) {
            for (Music music : tracks.values()) if (music != null && music.isPlaying()) music.pause();
            return;
        }

        float gain = mood.gain;
        active.setVolume(Math.max(0f, Math.min(1f, settings.musicVolume * gain)));
        if (!active.isPlaying()) active.play();
        for (Music music : tracks.values()) if (music != null && music != active && music.isPlaying()) music.pause();
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
        for (Music music : tracks.values()) if (music != null) { music.stop(); music.dispose(); }
        tracks.clear();
        for (Sound sound : sounds.values()) if (sound != null) sound.dispose();
        sounds.clear();
    }
}
