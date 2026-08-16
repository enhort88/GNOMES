package com.enhort.gnomes.draw;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.EarClippingTriangulator;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.ShortArray;

import java.util.ArrayDeque;

/**
 * Canvas-like immediate drawing with a y-down camera, so Android-style coordinates stay intact.
 */
public class Draw implements Disposable {
    public enum Align { LEFT, CENTER }

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final OrthographicCamera cam = new OrthographicCamera();
    private final GlyphLayout layout = new GlyphLayout();
    private final EarClippingTriangulator triangulator = new EarClippingTriangulator();
    private final ArrayDeque<Matrix4> matrixStack = new ArrayDeque<>();
    private final Matrix4 transform = new Matrix4();
    private final FloatArray path = new FloatArray();
    private boolean pathClosed;

    private BitmapFont font;
    private BitmapFont fontBold;
    private Mode mode = Mode.NONE;

    public float width;
    public float height;
    public int color = 0xFFFFFFFF;
    public float strokeWidth = 2f;
    public float textSize = 12f;
    public boolean bold;
    public Align align = Align.LEFT;

    private enum Mode { NONE, FILL, LINE, TEXT }

    public void loadFonts() {
        String cyrillic = "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя";
        String extra = "×•↑↓←→◆⛏‹›…○●—–«»";
        font = generate("fonts/DejaVuSans.ttf", 32, cyrillic + extra);
        String display = Gdx.files.internal("fonts/RuslanDisplay-Regular.ttf").exists()
                ? "fonts/RuslanDisplay-Regular.ttf" : "fonts/DejaVuSans-Bold.ttf";
        fontBold = generate(display, 32, cyrillic + extra);
    }

    private static BitmapFont generate(String path, int size, String extra) {
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal(path));
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = size;
        p.flip = true;
        p.characters = FreeTypeFontGenerator.DEFAULT_CHARS + extra;
        p.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        p.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Linear;
        BitmapFont f = gen.generateFont(p);
        gen.dispose();
        f.setUseIntegerPositions(false);
        return f;
    }

    public void resize(int w, int h) {
        width = w;
        height = h;
        cam.setToOrtho(true, w, h);
        cam.update();
    }

    public void beginFrame() {
        Gdx.gl.glClearColor(14 / 255f, 17 / 255f, 20 / 255f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        transform.idt();
        shapes.setProjectionMatrix(cam.combined);
        batch.setProjectionMatrix(cam.combined);
        applyTransform();
        mode = Mode.NONE;
    }

    public void endFrame() {
        flush();
    }

    public void save() {
        matrixStack.addLast(transform.cpy());
    }

    public void restore() {
        flush();
        if (!matrixStack.isEmpty()) transform.set(matrixStack.removeLast());
        applyTransform();
    }

    public void translate(float x, float y) {
        flush();
        transform.translate(x, y, 0);
        applyTransform();
    }

    public void scale(float x, float y) {
        flush();
        transform.scale(x, y, 1);
        applyTransform();
    }

    public void rotate(float degrees) {
        flush();
        transform.rotate(0, 0, 1, degrees);
        applyTransform();
    }

    public void clipRect(float l, float t, float r, float b) {
        flush();
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        int x = Math.round(l);
        int y = Math.round(height - b);
        int w = Math.max(0, Math.round(r - l));
        int h = Math.max(0, Math.round(b - t));
        Gdx.gl.glScissor(x, y, w, h);
    }

    public void unclip() {
        flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
    }

    public void setColor(int argb) {
        this.color = argb;
    }

    public void fillRect(float l, float t, float r, float b) {
        ensure(Mode.FILL);
        applyColor();
        shapes.rect(l, t, r - l, b - t);
    }

    public void fillRoundRect(float l, float t, float r, float b, float radius) {
        float w = r - l;
        float h = b - t;
        if (w <= 0 || h <= 0) return;
        radius = Math.max(0, Math.min(radius, Math.min(w, h) * 0.5f));
        ensure(Mode.FILL);
        applyColor();
        if (radius <= 0.5f) {
            shapes.rect(l, t, w, h);
            return;
        }
        shapes.rect(l + radius, t, w - 2 * radius, h);
        shapes.rect(l, t + radius, radius, h - 2 * radius);
        shapes.rect(r - radius, t + radius, radius, h - 2 * radius);
        shapes.circle(l + radius, t + radius, radius, 18);
        shapes.circle(r - radius, t + radius, radius, 18);
        shapes.circle(l + radius, b - radius, radius, 18);
        shapes.circle(r - radius, b - radius, radius, 18);
    }

    public void fillCircle(float x, float y, float radius) {
        if (radius <= 0) return;
        ensure(Mode.FILL);
        applyColor();
        shapes.circle(x, y, radius, Math.max(12, (int) (radius * 1.6f)));
    }

    public void fillOval(float l, float t, float r, float b) {
        float w = r - l;
        float h = b - t;
        if (w <= 0 || h <= 0) return;
        ensure(Mode.FILL);
        applyColor();
        shapes.ellipse(l, t, w, h, 24);
    }

    public void strokeCircle(float x, float y, float radius) {
        ensure(Mode.LINE);
        applyColor();
        shapes.circle(x, y, radius, Math.max(16, (int) (radius * 1.8f)));
    }

    public void line(float x1, float y1, float x2, float y2) {
        ensure(Mode.FILL);
        applyColor();
        shapes.rectLine(x1, y1, x2, y2, Math.max(1f, strokeWidth));
    }

    public void pathReset() {
        path.clear();
        pathClosed = false;
    }

    public void moveTo(float x, float y) {
        if (path.size >= 2 && lastX() == x && lastY() == y) return;
        path.add(x);
        path.add(y);
    }

    public void lineTo(float x, float y) {
        if (path.size < 2) moveTo(x, y);
        else {
            path.add(x);
            path.add(y);
        }
    }

    public void quadTo(float cx, float cy, float x, float y) {
        if (path.size < 2) moveTo(x, y);
        float x0 = lastX();
        float y0 = lastY();
        for (int i = 1; i <= 8; i++) {
            float t = i / 8f;
            float u = 1f - t;
            path.add(u * u * x0 + 2 * u * t * cx + t * t * x);
            path.add(u * u * y0 + 2 * u * t * cy + t * t * y);
        }
    }

    public void closePath() {
        pathClosed = true;
        if (path.size >= 4) {
            path.add(path.get(0));
            path.add(path.get(1));
        }
    }

    public void fillPath() {
        if (path.size < 6) return;
        float[] verts = path.toArray();
        int n = verts.length / 2;
        if (pathClosed && n >= 2 && verts[0] == verts[verts.length - 2] && verts[1] == verts[verts.length - 1]) {
            n--;
        }
        if (n < 3) return;
        float[] poly = new float[n * 2];
        System.arraycopy(verts, 0, poly, 0, n * 2);
        ShortArray idx = triangulator.computeTriangles(poly);
        ensure(Mode.FILL);
        applyColor();
        if (idx.size >= 3) {
            for (int i = 0; i + 2 < idx.size; i += 3) {
                int a = idx.get(i) * 2;
                int b = idx.get(i + 1) * 2;
                int c = idx.get(i + 2) * 2;
                shapes.triangle(poly[a], poly[a + 1], poly[b], poly[b + 1], poly[c], poly[c + 1]);
            }
        } else {
            float cx = 0, cy = 0;
            for (int i = 0; i < n; i++) {
                cx += poly[i * 2];
                cy += poly[i * 2 + 1];
            }
            cx /= n;
            cy /= n;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                shapes.triangle(cx, cy, poly[i * 2], poly[i * 2 + 1], poly[j * 2], poly[j * 2 + 1]);
            }
        }
    }

    public void strokePath() {
        if (path.size < 4) return;
        ensure(Mode.FILL);
        applyColor();
        float w = Math.max(1f, strokeWidth);
        for (int i = 0; i + 3 < path.size; i += 2) {
            shapes.rectLine(path.get(i), path.get(i + 1), path.get(i + 2), path.get(i + 3), w);
        }
    }

    public void text(String s, float x, float y) {
        if (s == null || s.isEmpty()) return;
        BitmapFont f = bold && fontBold != null ? fontBold : font;
        if (f == null) return;
        ensure(Mode.TEXT);
        float effectiveSize = textSize * 2.0f;
        float scale = effectiveSize / 32f;
        f.getData().setScale(scale);
        applyFontColor(f);
        float drawX = x;
        if (align == Align.CENTER) {
            layout.setText(f, s);
            drawX = x - layout.width * 0.5f;
        }
        f.draw(batch, s, drawX, y - effectiveSize * 0.78f);
    }

    public void image(Texture texture, float l, float t, float r, float b) {
        if (texture == null || r <= l || b <= t) return;
        ensure(Mode.TEXT);
        batch.setColor(1f, 1f, 1f, 1f);
        batch.draw(texture, l, t, r - l, b - t, 0, 0, texture.getWidth(), texture.getHeight(), false, true);
    }

    private void applyFontColor(BitmapFont f) {
        int a = (color >>> 24) & 0xff;
        int r = (color >>> 16) & 0xff;
        int g = (color >>> 8) & 0xff;
        int b = color & 0xff;
        f.setColor(r / 255f, g / 255f, b / 255f, a / 255f);
    }

    private void applyColor() {
        int a = (color >>> 24) & 0xff;
        int r = (color >>> 16) & 0xff;
        int g = (color >>> 8) & 0xff;
        int b = color & 0xff;
        shapes.setColor(r / 255f, g / 255f, b / 255f, a / 255f);
    }

    private void ensure(Mode next) {
        if (mode == next) return;
        flush();
        mode = next;
        if (next == Mode.FILL) shapes.begin(ShapeRenderer.ShapeType.Filled);
        else if (next == Mode.LINE) shapes.begin(ShapeRenderer.ShapeType.Line);
        else if (next == Mode.TEXT) batch.begin();
    }

    private void flush() {
        if (mode == Mode.FILL || mode == Mode.LINE) shapes.end();
        else if (mode == Mode.TEXT) batch.end();
        mode = Mode.NONE;
    }

    private void applyTransform() {
        shapes.setTransformMatrix(transform);
        batch.setTransformMatrix(transform);
    }

    private float lastX() {
        return path.get(path.size - 2);
    }

    private float lastY() {
        return path.get(path.size - 1);
    }

    @Override
    public void dispose() {
        flush();
        shapes.dispose();
        batch.dispose();
        if (font != null) font.dispose();
        if (fontBold != null) fontBold.dispose();
    }
}
