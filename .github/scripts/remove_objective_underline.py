from pathlib import Path

p = Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
s = p.read_text()
old = '''        d.setColor(alpha(col,.32f+.48f*pulse));
        d.fillRoundRect(cx-w*.30f,cy+8.2f*ui,cx+w*.30f,cy+9.1f*ui,.45f*ui);
'''
if old not in s:
    raise SystemExit('objective underline target not found')
s = s.replace(old, '', 1)
s = s.replace('// blended rounded rectangle renders as a solid smear. A breathing text/underline cue is cleaner.',
              '// blended rounded rectangle renders as a solid smear. Keep the cue to text and edge dots only.', 1)
p.write_text(s)
