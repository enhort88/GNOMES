from pathlib import Path
p=Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
s=p.read_text()
old='float settle=Math.min(1f,(h.age-1.25f)/.55f),pct=h.rubbleMaxHp<=0?1f:Math.max(0,Math.min(1,h.rubbleHp/h.rubbleMaxHp));\n        float shrink=.32f+.68f*(float)Math.sqrt(pct);int stones=Math.max(4,Math.round(17*pct));'
new='float settle=Math.min(1f,(h.age-1.25f)/.55f),rubblePct=h.rubbleMaxHp<=0?1f:Math.max(0,Math.min(1,h.rubbleHp/h.rubbleMaxHp));\n        float shrink=.32f+.68f*(float)Math.sqrt(rubblePct);int stones=Math.max(4,Math.round(17*rubblePct));'
if old not in s:
    raise SystemExit('collapse variable pattern not found')
s=s.replace(old,new,1)
p.write_text(s)
print('v0.6.4 compile fix applied')
