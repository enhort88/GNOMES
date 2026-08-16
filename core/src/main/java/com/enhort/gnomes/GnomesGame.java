package com.enhort.gnomes;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import com.enhort.gnomes.draw.Draw;
import com.enhort.gnomes.game.CaveScreen;
import com.enhort.gnomes.game.GameState;
import com.enhort.gnomes.menu.MenuScreen;
import com.enhort.gnomes.save.SaveRepository;

/** Application shell. Gameplay no longer owns the app lifecycle, so menu/save screens work like DOT//CORE. */
public final class GnomesGame extends Game {
    public Draw draw;
    public SaveRepository saves;
    public GameSettings settings;
    public GameAudio audio;

    @Override public void create() {
        draw = new Draw();
        draw.loadFonts();
        settings = new GameSettings();
        GameState.FREE_SHOP = settings.freeShop;
        audio = new GameAudio(settings);
        saves = new SaveRepository();
        openMenu();
    }

    public void changeScreen(Screen next) {
        Screen old = getScreen();
        setScreen(next);
        if (old != null && old != next) old.dispose();
    }

    public void openMenu() { changeScreen(new MenuScreen(this)); }
    public void playSlot(int slot) { changeScreen(new CaveScreen(this, slot)); }
    public void playNewSlot(int slot) {
        saves.save(slot, saves.fresh(slot));
        playSlot(slot);
    }

    public void syncCheats() {
        GameState.FREE_SHOP = settings != null && settings.freeShop;
    }

    @Override public void resize(int width, int height) {
        if (draw != null) draw.resize(width, height);
        super.resize(width, height);
    }

    @Override public void dispose() {
        super.dispose();
        if (audio != null) audio.dispose();
        if (draw != null) draw.dispose();
    }
}
