package com.enhort.gnomes.intro;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Texture;
import com.enhort.gnomes.GnomesGame;
import com.enhort.gnomes.draw.Draw;

/** Three tiny illustrated prologue cards. Tap advances; the whole thing is intentionally brief. */
public final class IntroScreen extends ScreenAdapter {
    private static final String[] IMAGE = {
            "intro/gnome_home.png", "intro/gnome_mine.png", "intro/gnome_enemies.png"
    };
    private static final String[] TITLE = {
            "МЫ, ГНОМЫ", "КАМЕНЬ И СОКРОВИЩА", "А ЕЩЁ ДЕМОНЫ"
    };
    private static final String[] BODY = {
            "Любим тепло очага, крепкий сундук и хороший повод снова уйти под землю.",
            "Мы копаем камень, ищем серебро, золото и алмазы. Всё найденное ещё надо донести домой.",
            "Чем глубже шахта, тем наглее бесы, сильнее демоны и страннее твари. Поэтому кирку держим правильно."
    };

    private final GnomesGame game;
    private final Texture[] images = new Texture[3];
    private int page;
    private float width, height, ui, age;

    public IntroScreen(GnomesGame game) { this.game = game; }

    @Override public void show() {
        for (int i = 0; i < images.length; i++) {
            try { images[i] = new Texture(Gdx.files.internal(IMAGE[i])); }
            catch (Exception ignored) { images[i] = null; }
        }
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) { finish(); return true; }
                return false;
            }
            @Override public boolean touchDown(int x, int y, int pointer, int button) { next(); return true; }
        });
    }

    @Override public void resize(int w, int h) {
        width = w; height = h;
        ui = Math.max(.70f, Math.min(w / 420f, h / 820f));
        game.draw.resize(w, h);
    }

    @Override public void render(float delta) {
        age += Math.min(.05f, delta);
        Draw d = game.draw;
        d.beginFrame();
        d.setColor(0xFF090B0D); d.fillRect(0, 0, width, height);

        float margin = 18f * ui;
        float top = 50f * ui;
        float imageH = Math.min(height * .54f, 420f * ui);
        float imageB = top + imageH;
        d.setColor(0xFF171A1C); d.fillRoundRect(margin, top, width - margin, imageB, 18f * ui);
        if (images[page] != null) d.image(images[page], margin + 4f * ui, top + 4f * ui, width - margin - 4f * ui, imageB - 4f * ui);
        else fallbackArt(d, margin, top, width - margin, imageB, page);

        d.align = Draw.Align.CENTER;
        d.bold = true; d.textSize = 18f * ui; d.setColor(0xFFF2C45B);
        d.text(TITLE[page], width / 2f, imageB + 48f * ui);
        d.bold = false; d.textSize = 9.5f * ui; d.setColor(0xFFE7E2D8);
        wrap(d, BODY[page], width / 2f, imageB + 80f * ui, Math.max(28, (int)(width / (8.1f * ui))));

        d.textSize = 7.6f * ui; d.setColor(0xFF8D989F);
        d.text("КОСНИТЕСЬ ЭКРАНА  •  " + (page + 1) + "/3", width / 2f, height - 34f * ui);
        for (int i = 0; i < 3; i++) {
            d.setColor(i == page ? 0xFFF0B85A : 0xFF4A5156);
            d.fillCircle(width / 2f + (i - 1) * 16f * ui, height - 57f * ui, 3.4f * ui);
        }
        d.align = Draw.Align.LEFT;
        d.endFrame();
    }

    private void next() {
        game.audio.play(com.enhort.gnomes.GameAudio.Sfx.UI, .45f);
        age = 0;
        if (++page >= 3) finish();
    }

    private void finish() {
        game.settings.markIntroSeen();
        game.openMenu();
    }

    private static void wrap(Draw d, String text, float x, float y, int chars) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float yy = y;
        for (String word : words) {
            if (line.length() > 0 && line.length() + 1 + word.length() > chars) {
                d.text(line.toString(), x, yy); yy += d.textSize * 1.55f; line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) d.text(line.toString(), x, yy);
    }

    /** Built-in fallback keeps the prologue usable even if an asset is accidentally omitted. */
    private void fallbackArt(Draw d, float l, float t, float r, float b, int p) {
        d.setColor(0xFF24211D); d.fillRect(l, t, r, b);
        float cx = (l + r) / 2f, cy = (t + b) / 2f;
        d.setColor(0xFF443A30); d.fillCircle(cx, cy, Math.min(r - l, b - t) * .30f);
        d.setColor(p == 2 ? 0xFFC8493F : 0xFFF0B85A);
        d.fillCircle(cx, cy - 20f * ui, 32f * ui);
        d.setColor(0xFFE8E2D5); d.fillOval(cx - 28f * ui, cy, cx + 28f * ui, cy + 64f * ui);
    }

    @Override public void dispose() {
        for (Texture texture : images) if (texture != null) texture.dispose();
    }
}
