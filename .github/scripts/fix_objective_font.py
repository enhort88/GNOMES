from pathlib import Path

p = Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
s = p.read_text()
old = 'd.align=Draw.Align.CENTER;d.bold=pulse>.62f;d.textSize=(5.25f+.22f*pulse)*ui;d.setColor(col);d.text(levelObjectiveHud(),cx,cy);d.bold=false;'
new = 'd.align=Draw.Align.CENTER;d.bold=false;d.textSize=(5.25f+.22f*pulse)*ui;d.setColor(col);d.text(levelObjectiveHud(),cx,cy);'
if old not in s:
    raise SystemExit('objective font toggle target not found')
p.write_text(s.replace(old, new, 1))
