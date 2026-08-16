package com.enhort.gnomes;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.enhort.gnomes.draw.Draw;
import com.enhort.gnomes.game.MineScreen;

public class GnomesGame extends ApplicationAdapter {
    private Draw draw;
    private MineScreen mine;

    @Override
    public void create() {
        draw = new Draw();
        draw.loadFonts();
        mine = new MineScreen();
        Gdx.input.setInputProcessor(mine);
    }

    @Override
    public void resize(int width, int height) {
        draw.resize(width, height);
        mine.resize(width, height);
    }

    @Override
    public void render() {
        draw.beginFrame();
        mine.render(draw, Gdx.graphics.getDeltaTime());
        draw.endFrame();
    }

    @Override
    public void pause() {
        if (mine != null) mine.pause();
    }

    @Override
    public void resume() {
        if (mine != null) mine.resume();
    }

    @Override
    public void dispose() {
        if (mine != null) mine.pause();
        if (draw != null) draw.dispose();
    }
}
