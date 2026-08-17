from pathlib import Path

p = Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
s = p.read_text()
old = '        d.bold=pulse>.70f;\n'
new = '        d.bold=false;\n'
if old not in s:
    raise SystemExit('objective font toggle target not found')
p.write_text(s.replace(old, new, 1))
