from pathlib import Path
import re

gp=Path('core/src/main/java/com/enhort/gnomes/game/GameState.java')
g=gp.read_text()
g,n=re.subn(r'''    public long tierUpgradeCost\(int tier\) \{.*?\n    \}''','''    public long tierUpgradeCost(int tier) {
        if (FREE_SHOP) return 0;
        int lvl = tierLevels[tier];
        return Math.round((12 + tier * 8L) * Math.pow(1.58, lvl - 1));
    }''',g,count=1,flags=re.S)
if n!=1: raise SystemExit('tierUpgradeCost patch failed')
g,n=re.subn(r'''    public boolean upgradeTier\(int tier\) \{.*?\n    \}\n\n    public long globalUpgradeCost''','''    public boolean upgradeTier(int tier) {
        if (tier < 0 || tier >= tierLevels.length) return false;
        long cost = tierUpgradeCost(tier);
        if (!FREE_SHOP) {
            if (silver < cost) return false;
            silver -= cost;
            levelInvestedValue += cost * 8L;
        }
        tierLevels[tier]++;
        return true;
    }

    public long globalUpgradeCost''',g,count=1,flags=re.S)
if n!=1: raise SystemExit('upgradeTier patch failed')
gp.write_text(g)

cp=Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
s=cp.read_text()
old='button(d,secondary,"УЛУЧШИТЬ • ● "+format(state.tierUpgradeCost(selectedTier)),true,.66f)'
new='button(d,secondary,"УЛУЧШИТЬ • Ag "+format(state.tierUpgradeCost(selectedTier)),true,.66f)'
if old not in s: raise SystemExit('tier UI cost label missing')
s=s.replace(old,new,1)
old='case GNOMES->{if(state.upgradeTier(selectedTier))toast="ГНОМ УСИЛЕН";else toast="НЕ ХВАТАЕТ РЕСУРСОВ";toastTime=1.2f;}'
new='case GNOMES->{if(state.upgradeTier(selectedTier))toast="ГНОМ УСИЛЕН";else toast="НЕ ХВАТАЕТ СЕРЕБРА";toastTime=1.2f;}'
if old not in s: raise SystemExit('tier upgrade toast missing')
s=s.replace(old,new,1)
cp.write_text(s)
