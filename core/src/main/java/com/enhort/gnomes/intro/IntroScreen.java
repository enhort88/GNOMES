package com.enhort.gnomes.intro;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Texture;
import com.enhort.gnomes.GameAudio;
import com.enhort.gnomes.GnomesGame;
import com.enhort.gnomes.draw.Draw;

/** Short cinematic prologue: three full-screen story frames, no card UI and no character-count wrapping. */
public final class IntroScreen extends ScreenAdapter {
    private static final String[] IMAGE = {
            "intro/story_home.png", "intro/story_mine.png", "intro/story_enemies.png"
    };
    private static final String[] TITLE = {
            "ДОМ ПОД ГОРОЙ", "ГЛУБЖЕ", "МЫ НЕ ОДНИ"
    };
    private static final String[] BODY = {
            "Здесь наш дом. Мы строим глубже, добываем камень и храним всё ценное в старом сундуке.",
            "Чем глубже шахта, тем богаче жилы. И тем опаснее дорога обратно.",
            "Бесы идут за золотом. Демоны идут за нами. Но эта гора принадлежит гномам."
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
        width = w;
        height = h;
        ui = Math.max(.70f, Math.min(w / 420f, h / 820f));
        game.draw.resize(w, h);
    }

    @Override public void render(float delta) {
        age += Math.min(.05f, delta);
        Draw d = game.draw;
        d.beginFrame();
        d.setColor(0xFF07090B);
        d.fillRect(0, 0, width, height);

        if (images[page] != null) drawCover(d, images[page]);
        else fallbackArt(d, page);

        // Cinematic lower fade. Text lives on the picture instead of below a giant black void.
        float fadeTop = height * .50f;
        for (int i = 0; i < 10; i++) {
            float y1 = fadeTop + (height - fadeTop) * i / 10f;
            float y2 = fadeTop + (height - fadeTop) * (i + 1) / 10f;
            int alpha = Math.min(232, 18 + i * 24);
            d.setColor((alpha << 24) | 0x00050709);
            d.fillRect(0, y1, width, y2);
        }
        d.setColor(0x38000000);
        d.fillRect(0, 0, width, height * .16f);

        float textLeft = 28f * ui;
        float textRight = width - 28f * ui;
        float titleY = height * .66f;
        float bodyY = titleY + 48f * ui;

        d.align = Draw.Align.CENTER;
        d.bold = true;
        d.textSize = fitTitle(d, TITLE[page], 16f * ui, 10.5f * ui, textRight - textLeft);
        d.setColor(0xFFFFCB5B);
        d.text(TITLE[page], width / 2f, titleY);

        d.bold = false;
        d.textSize = 8.0f * ui;
        d.setColor(0xFFF0ECE4);
        drawWrapped(d, BODY[page], width / 2f, bodyY, textRight - textLeft, 26f * ui);

        float hintY = height - 31f * ui;
        float dotsY = hintY - 24f * ui;
        d.textSize = 5.9f * ui;
        d.setColor(0xFF9BA4AA);
        d.text("КОСНИТЕСЬ ЭКРАНА  •  " + (page + 1) + "/3", width / 2f, hintY);
        for (int i = 0; i < 3; i++) {
            d.setColor(i == page ? 0xFFF0B85A : 0xFF535B60);
            d.fillCircle(width / 2f + (i - 1) * 16f * ui, dotsY, 3.2f * ui);
        }
        d.align = Draw.Align.LEFT;
        d.endFrame();
    }

    private void drawCover(Draw d, Texture texture) {
        float tw = Math.max(1, texture.getWidth());
        float th = Math.max(1, texture.getHeight());
        float scale = Math.max(width / tw, height / th);
        float dw = tw * scale;
        float dh = th * scale;
        float l = (width - dw) * .5f;
        float t = (height - dh) * .5f;
        d.clipRect(0, 0, width, height);
        d.image(texture, l, t, l + dw, t + dh);
        d.unclip();
    }

    private float fitTitle(Draw d, String text, float preferred, float minimum, float maxWidth) {
        float size = preferred;
        d.textSize = size;
        while (size > minimum && d.textWidth(text) > maxWidth) {
            size -= .5f * ui;
            d.textSize = size;
        }
        return size;
    }

    private void drawWrapped(Draw d, String text, float x, float y, float maxWidth, float lineGap) {
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float yy = y;
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && d.textWidth(candidate) > maxWidth) {
                d.text(line.toString(), x, yy);
                yy += lineGap;
                line.setLength(0);
                line.append(word);
            } else {
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
        }
        if (line.length() > 0) d.text(line.toString(), x, yy);
    }

    private void next() {
        game.audio.play(GameAudio.Sfx.UI, .45f);
        age = 0;
        if (++page >= 3) finish();
    }

    private void finish() {
        game.settings.markIntroSeen();
        game.openMenu();
    }

    /**
     * Dark-fantasy fallback for development builds. Release art uses the story_*.png frames above.
     * It deliberately avoids the old flat "two gnomes standing on rectangles" placeholder composition.
     */
    private void fallbackArt(Draw d, int p) {
        int sky = p == 2 ? 0xFF170D16 : p == 1 ? 0xFF0B1718 : 0xFF15120E;
        d.setColor(sky);
        d.fillRect(0, 0, width, height);

        // Layered cave silhouettes.
        d.setColor(0xFF25211C);
        d.fillOval(-width * .28f, -height * .10f, width * .72f, height * .88f);
        d.fillOval(width * .35f, -height * .16f, width * 1.24f, height * .76f);
        d.setColor(0xFF100F0E);
        d.fillOval(width * .10f, height * .12f, width * .92f, height * .88f);

        float cx = width * .50f;
        float cy = height * .36f;
        if (p == 0) {
            // Warm settlement: hearth, chest, two small silhouettes.
            d.setColor(0x2AFF9A32); d.fillCircle(cx, cy, 105f * ui);
            d.setColor(0x66FF8A24); d.fillCircle(cx, cy, 58f * ui);
            d.setColor(0xFFFFB43A); d.fillOval(cx - 18f * ui, cy - 24f * ui, cx + 18f * ui, cy + 34f * ui);
            drawFallbackGnome(d, cx - 86f * ui, cy + 18f * ui, 1f, 0xFFB84638);
            drawFallbackGnome(d, cx + 82f * ui, cy + 24f * ui, .94f, 0xFFD8A43A);
            d.setColor(0xFF6A4528); d.fillRoundRect(cx + 48f * ui, cy + 72f * ui, cx + 126f * ui, cy + 120f * ui, 5f * ui);
            d.setColor(0xFFD09A36); d.fillRoundRect(cx + 58f * ui, cy + 63f * ui, cx + 116f * ui, cy + 78f * ui, 3f * ui);
        } else if (p == 1) {
            // Mine vista: three workers, ore glows and a wet floor reflection.
            d.setColor(0x3318B8BA); d.fillOval(width * .08f, height * .52f, width * .92f, height * .66f);
            d.setColor(0x44FFD34A); d.fillCircle(width * .79f, height * .24f, 54f * ui);
            d.setColor(0x4486DDF2); d.fillCircle(width * .20f, height * .22f, 44f * ui);
            drawFallbackGnome(d, cx - 86f * ui, cy + 20f * ui, .92f, 0xFFB84638);
            drawFallbackGnome(d, cx, cy - 12f * ui, 1.05f, 0xFFB84638);
            drawFallbackGnome(d, cx + 84f * ui, cy + 28f * ui, .90f, 0xFF4B8EC6);
        } else {
            // Threat: portal glow and a wall of horned silhouettes.
            d.setColor(0x335B1C78); d.fillCircle(width * .72f, cy, 105f * ui);
            d.setColor(0x886E238C); d.fillOval(width * .60f, cy - 92f * ui, width * .86f, cy + 92f * ui);
            d.setColor(0xFFB3393B);
            for (int i = 0; i < 4; i++) {
                float x = width * (.64f + i * .055f);
                float y = cy + (i % 2 == 0 ? 12f : -12f) * ui;
                d.fillOval(x - 13f * ui, y - 24f * ui, x + 13f * ui, y + 28f * ui);
                d.strokeWidth = 4f * ui;
                d.line(x - 8f * ui, y - 20f * ui, x - 16f * ui, y - 38f * ui);
                d.line(x + 8f * ui, y - 20f * ui, x + 16f * ui, y - 38f * ui);
            }
            drawFallbackGnome(d, cx - 105f * ui, cy + 28f * ui, .92f, 0xFFB84638);
            drawFallbackGnome(d, cx - 40f * ui, cy + 42f * ui, .86f, 0xFF4B8EC6);
        }
    }

    private void drawFallbackGnome(Draw d, float x, float y, float scale, int hat) {
        float s = 25f * ui * scale;
        d.setColor(0xFF284C5B); d.fillOval(x - s * .36f, y, x + s * .36f, y + s * .92f);
        d.setColor(0xFFE0AD7B); d.fillCircle(x, y - s * .24f, s * .35f);
        d.setColor(0xFFECE7DA); d.fillOval(x - s * .30f, y - s * .08f, x + s * .30f, y + s * .48f);
        d.setColor(hat);
        d.pathReset(); d.moveTo(x - s * .40f, y - s * .40f); d.lineTo(x, y - s * 1.02f); d.lineTo(x + s * .42f, y - s * .38f); d.closePath(); d.fillPath();
        d.setColor(0xFF6E4829); d.strokeWidth = 5f * ui * scale; d.line(x + s * .20f, y + s * .12f, x + s * .78f, y - s * .34f);
        d.setColor(0xFFC9D1D5); d.strokeWidth = 6f * ui * scale; d.line(x + s * .64f, y - s * .52f, x + s * .91f, y - s * .26f);
    }

    @Override public void dispose() {
        for (Texture texture : images) if (texture != null) texture.dispose();
    }
}
