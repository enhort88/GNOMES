package com.enhort.gnomes.ui;

import com.enhort.gnomes.draw.Draw;

/**
 * Shared visual language for GNOMES. The UI is deliberately built from cheap primitives so it stays crisp
 * on Android without turning every button into a separate texture asset.
 */
public final class UiTheme {
    public static final int GOLD = 0xFFD6AA52;
    public static final int COPPER = 0xFFC47B46;
    public static final int STEEL = 0xFF6D8A9A;
    public static final int GREEN = 0xFF68A97B;
    public static final int RED = 0xFFC85E55;

    private UiTheme() {}

    public static void panel(Draw d, float l, float t, float r, float b, float ui) {
        d.setColor(0xFF0D1012);
        d.fillRect(l, t, r, b);
        d.setColor(0xFF242A2E);
        d.fillRect(l, t, r, t + Math.max(1f, ui));
    }

    public static void button(Draw d, float l, float t, float r, float b, float ui,
                              String text, boolean enabled, int accent, boolean pressed, float textScale) {
        if (r <= l || b <= t) return;
        float radius = Math.max(5f * ui, Math.min((b - t) * .20f, 10f * ui));
        float down = pressed ? 1.5f * ui : 0f;

        // Shadow first, then a dark metal face and a thin material accent. It reads as a control rather
        // than another random grey rectangle, which humanity has already produced in sufficient quantity.
        d.setColor(0x77000000);
        d.fillRoundRect(l + 1.5f * ui, t + 3.5f * ui, r + 1.5f * ui, b + 3.5f * ui, radius);

        d.setColor(enabled ? (pressed ? 0xFF493A1E : 0xFF252C31) : 0xFF171B1E);
        d.fillRoundRect(l, t + down, r, b + down, radius);

        d.setColor(enabled ? (pressed ? 0xFF6A5225 : 0xFF3E474D) : 0xFF242A2E);
        d.fillRoundRect(l + 1f * ui, t + 1f * ui + down, r - 1f * ui, b - 1f * ui + down,
                Math.max(3f * ui, radius - 1f * ui));

        d.setColor(enabled ? (pressed ? 0xFF302B20 : 0xFF22282C) : 0xFF191D20);
        d.fillRoundRect(l + 2f * ui, t + 3f * ui + down, r - 2f * ui, b - 2f * ui + down,
                Math.max(3f * ui, radius - 2f * ui));

        if (pressed && enabled) {
            d.setColor(0x335A4315);
            d.fillRoundRect(l + 4f * ui, t + 5f * ui + down, r - 4f * ui, b - 4f * ui + down,
                    Math.max(3f * ui, radius - 3f * ui));
            d.setColor(0x99FFD35A);
            d.fillRect(l + 12f * ui, t + 4f * ui + down, r - 10f * ui, t + 5.4f * ui + down);
        }

        if (enabled) {
            d.setColor(accent);
            d.fillRoundRect(l + 3f * ui, t + 4f * ui + down, l + 6f * ui, b - 3f * ui + down, 1.5f * ui);
            d.setColor(0x44FFFFFF);
            d.fillRect(l + 10f * ui, t + 3f * ui + down, r - 8f * ui, t + 4f * ui + down);
        }

        if (text != null && !text.isEmpty()) {
            d.align = Draw.Align.CENTER;
            d.bold = true;
            d.textSize = 10f * ui * textScale;
            d.setColor(enabled ? (pressed ? 0xFFFFE8A5 : 0xFFF4F1E9) : 0xFF626A70);
            d.text(text, (l + r) * .5f, (t + b) * .5f + 3.4f * ui + down);
            d.align = Draw.Align.LEFT;
            d.bold = false;
        }
    }

    public static void tab(Draw d, float l, float t, float r, float b, float ui,
                           String text, boolean selected, int accent) {
        d.setColor(selected ? 0xFF252C31 : 0xFF15191C);
        d.fillRoundRect(l + 2f * ui, t + 3f * ui, r - 2f * ui, b - 2f * ui, 5f * ui);
        if (selected) {
            d.setColor(accent);
            d.fillRoundRect(l + 9f * ui, b - 4f * ui, r - 9f * ui, b - 2f * ui, 1f * ui);
        }
        d.align = Draw.Align.CENTER;
        d.bold = selected;
        d.textSize = 8.5f * ui;
        d.setColor(selected ? 0xFFF3F0E7 : 0xFF7E8990);
        d.text(text, (l + r) * .5f, (t + b) * .5f + 3f * ui);
        d.align = Draw.Align.LEFT;
        d.bold = false;
    }
}
