from pathlib import Path

p = Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
s = p.read_text()
old = '''    private void drawObjectiveHud(Draw d){
        float pulse=.5f+.5f*(float)Math.sin(elapsed*3.0f);
        float cx=width*.66f,cy=42f*ui,w=Math.min(width*.52f,210f*ui);
        int col=levelObjectiveMet()?0xFF79C98A:0xFFE2B544;
        d.setColor(alpha(col,.035f+.055f*pulse));
        d.fillRoundRect(cx-w*.5f,cy-10f*ui,cx+w*.5f,cy+9f*ui,7f*ui);
        d.setColor(alpha(col,.22f+.36f*pulse));
        d.fillRoundRect(cx-w*.34f,cy+8.5f*ui,cx+w*.34f,cy+9.7f*ui,.7f*ui);
        d.setColor(alpha(col,.40f+.45f*pulse));
        float dot=1.4f*ui+.7f*ui*pulse;
        d.fillCircle(cx-w*.43f,cy-1f*ui,dot);d.fillCircle(cx+w*.43f,cy-1f*ui,dot);
        d.align=Draw.Align.CENTER;d.bold=pulse>.62f;d.textSize=(5.25f+.22f*pulse)*ui;d.setColor(col);d.text(levelObjectiveHud(),cx,cy);d.bold=false;
    }
'''
new = '''    private void drawObjectiveHud(Draw d){
        // Keep the objective readable, but avoid a filled translucent plate: on some Android GPUs the
        // blended rounded rectangle renders as a solid smear. A breathing text/underline cue is cleaner.
        float pulse=.5f+.5f*(float)Math.sin(elapsed*3.0f);
        float cx=width*.66f,cy=42f*ui,w=Math.min(width*.48f,190f*ui);
        int col=levelObjectiveMet()?0xFF79C98A:0xFFE2B544;
        float dot=1.0f*ui+.55f*ui*pulse;
        d.setColor(alpha(col,.42f+.42f*pulse));
        d.fillCircle(cx-w*.47f,cy-1f*ui,dot);d.fillCircle(cx+w*.47f,cy-1f*ui,dot);
        d.setColor(alpha(col,.32f+.48f*pulse));
        d.fillRoundRect(cx-w*.30f,cy+8.2f*ui,cx+w*.30f,cy+9.1f*ui,.45f*ui);
        d.align=Draw.Align.CENTER;
        d.bold=pulse>.70f;
        d.textSize=(5.15f+.16f*pulse)*ui;
        d.setColor(col);
        d.text(levelObjectiveHud(),cx,cy);
        d.bold=false;
    }
'''
if old not in s:
    raise SystemExit('objective HUD patch target not found')
p.write_text(s.replace(old,new,1))
