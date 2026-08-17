from pathlib import Path
import re

def sub1(s, pattern, repl, name, flags=0):
    ns, n = re.subn(pattern, repl, s, count=1, flags=flags)
    if n != 1:
        raise SystemExit(f"patch target not found or ambiguous: {name} ({n})")
    return ns

gp = Path("core/src/main/java/com/enhort/gnomes/game/GameState.java")
g = gp.read_text()

g = sub1(g,
    r'''    public long guardianCost\(\) \{.*?    public String guardianCostLabel\(\) \{.*?\n    \}''',
    '''    public long guardianCost() {
        if (FREE_SHOP) return 0;
        return Math.round(24d * Math.pow(1.85, guardianLevel));
    }

    public boolean buyOrUpgradeGuardian() {
        long cost = guardianCost();
        if (!FREE_SHOP) {
            if (silver < cost) return false;
            silver -= cost;
            levelInvestedValue += cost * 8L;
        }
        guardianLevel++;
        return true;
    }

    public String guardianCostLabel() {
        return FREE_SHOP ? "БЕСПЛАТНО" : guardianCost() + " Ag";
    }''',
    "guardian economy", re.S)

g = sub1(g,
    r'''    public boolean upgradeTier\(int tier\) \{.*?\n    \}\n\n    public long globalUpgradeCost''',
    '''    public boolean upgradeTier(int tier) {
        if (tier < 0 || tier >= tierLevels.length) return false;
        long cost = tierUpgradeCost(tier);
        if (!FREE_SHOP) {
            if (stone < cost) return false;
            stone -= cost;
            levelInvestedValue += cost;
        }
        tierLevels[tier]++;
        return true;
    }

    public long globalUpgradeCost''',
    "tier upgrades", re.S)

g = sub1(g,
    r'''    public long globalUpgradeCost\(int kind\) \{.*?\n    \}\n\n    public int artifactCost''',
    '''    public long globalUpgradeCost(int kind) {
        if (FREE_SHOP) return 0;
        int lvl = kind == 0 ? miningUpgrade : kind == 1 ? speedUpgrade : combatUpgrade;
        return Math.round((18 + kind * 7L) * Math.pow(1.65, lvl));
    }

    public boolean buyGlobalUpgrade(int kind) {
        if (kind < 0 || kind > 2) return false;
        if (FREE_SHOP) {
            if (kind == 0) miningUpgrade++;
            else if (kind == 1) speedUpgrade++;
            else combatUpgrade++;
            return true;
        }
        long cost = globalUpgradeCost(kind);
        if (silver < cost) return false;
        silver -= cost;
        levelInvestedValue += cost * 8L;
        if (kind == 0) miningUpgrade++;
        else if (kind == 1) speedUpgrade++;
        else combatUpgrade++;
        return true;
    }

    public int artifactCost''',
    "global upgrades", re.S)

if 'int[] costs = {18, 32, 48, 70};' not in g:
    raise SystemExit("artifact cost table missing")
g = g.replace('int[] costs = {18, 32, 48, 70};', 'int[] costs = {8, 14, 22, 34};', 1)

g = sub1(g,
    r'''        if \(!FREE_SHOP\) \{\n            if \(diamond < cost\) return false;\n            diamond -= cost;\n        \}\n        artifactLevels\[artifactIndex\] = 1;''',
    '''        if (!FREE_SHOP) {
            if (gold < cost) return false;
            gold -= cost;
            levelInvestedValue += cost * 20L;
        }
        artifactLevels[artifactIndex] = 1;''',
    "artifact gold")

needle = '''        levelEarnedValue += materialValue(material, whole);
        return whole;
    }

    private static float upgradeCurve'''
if needle not in g:
    raise SystemExit("deposit insertion point missing")
g = g.replace(needle, '''        levelEarnedValue += materialValue(material, whole);
        return whole;
    }

    /** Direct find from a dead-end cache; chest multipliers do not apply. */
    public long grantBonus(RockType.Material material, long amount) {
        long whole = Math.max(0L, amount);
        switch (material) {
            case STONE -> stone += whole;
            case SILVER -> silver += whole;
            case GOLD -> gold += whole;
            case DIAMOND -> diamond += whole;
        }
        levelEarnedValue += materialValue(material, whole);
        return whole;
    }

    private static float upgradeCurve''', 1)
gp.write_text(g)

cp = Path("core/src/main/java/com/enhort/gnomes/game/CaveScreen.java")
s = cp.read_text()

needle = '    private static final class CaveHazard {'
insert = '''    private static final class BonusCache {
        final int cell; final RockType.Material material; final long amount; final float x,y;
        boolean taken; float pulse;
        BonusCache(int cell,RockType.Material material,long amount,float x,float y){this.cell=cell;this.material=material;this.amount=amount;this.x=x;this.y=y;}
    }

'''
if 'private static final class BonusCache' not in s:
    if needle not in s: raise SystemExit("BonusCache insertion point missing")
    s = s.replace(needle, insert + needle, 1)

old='    private final List<Notice> notices=new ArrayList<>();\n    private Portal portal;'
new='    private final List<Notice> notices=new ArrayList<>();\n    private final List<BonusCache> bonuses=new ArrayList<>();\n    private Portal portal;'
if old not in s: raise SystemExit("bonus list point missing")
s=s.replace(old,new,1)

old='mobs.clear();pendingMobs.clear();hazards.clear();fx.clear();veins.clear();portal=null;'
if old not in s: raise SystemExit("clear bonuses point missing")
s=s.replace(old,'mobs.clear();pendingMobs.clear();hazards.clear();fx.clear();veins.clear();bonuses.clear();portal=null;',1)

old='        buildVeins();\n        syncWorkers(true);'
if old not in s: raise SystemExit("build bonuses point missing")
s=s.replace(old,'        buildVeins();\n        buildBonuses();\n        syncWorkers(true);',1)

bonus_methods = r'''    private void buildBonuses(){
        List<Integer> ends=map.deadEnds();
        java.util.Collections.shuffle(ends,random);
        int home=map.index(map.startCol,map.startRow),limit=Math.min(3,1+state.depth/8),made=0;
        for(int cell:ends){
            if(made>=limit)break;
            if(cell==home||hasLivingVeinAt(cell))continue;
            if(random.nextFloat()>.34f)continue;
            RockType.Material material=chooseBonusMaterial();
            long amount=bonusAmount(material);
            bonuses.add(new BonusCache(cell,material,amount,cx(map.col(cell)),cy(map.row(cell))));made++;
        }
    }
    private boolean hasLivingVeinAt(int cell){for(Vein v:veins)if(!v.dead&&v.cell==cell)return true;return false;}
    private RockType.Material chooseBonusMaterial(){
        float q=random.nextFloat();
        if(state.depth>=9&&q<.07f)return RockType.Material.DIAMOND;
        if(state.depth>=5&&q<.34f)return RockType.Material.GOLD;
        if(state.depth>=2&&q<.72f)return RockType.Material.SILVER;
        return RockType.Material.STONE;
    }
    private long bonusAmount(RockType.Material material){return switch(material){
        case STONE -> 70L+state.depth*22L+random.nextInt(70);
        case SILVER -> 5L+state.depth*2L+random.nextInt(8);
        case GOLD -> 2L+state.depth/2L+random.nextInt(5);
        case DIAMOND -> 1L+state.depth/12L;
    };}
    private String bonusLabel(BonusCache b){return switch(b.material){case STONE->"● "+b.amount;case SILVER->"Ag "+b.amount;case GOLD->"Au "+b.amount;case DIAMOND->"◆ "+b.amount;};}
    private int bonusColor(BonusCache b){return switch(b.material){case STONE->0xFFB7BDC2;case SILVER->0xFFE2EAF0;case GOLD->0xFFFFD35A;case DIAMOND->0xFF67D7F2;};}
    private void updateBonuses(float dt){
        for(BonusCache b:bonuses){
            if(b.taken)continue;b.pulse+=dt;
            for(Worker w:workers){
                if(distance(w.x,w.y,b.x,b.y)>Math.min(cellW,cellH)*.24f)continue;
                b.taken=true;state.grantBonus(b.material,b.amount);spawnSparks(b.x,b.y,bonusColor(b),8);game.audio.play(GameAudio.Sfx.COIN,.76f);game.audio.vibrate(24);
                toast="ТАЙНИК • +"+bonusLabel(b);toastTime=1.7f;addNotice("НАЙДЕН ТАЙНИК • +"+bonusLabel(b),bonusColor(b),2.4f);
                if(priorityKind==PriorityKind.POINT&&priorityCell==b.cell){clearPriority(false);resetWorkerRoutes();}
                break;
            }
        }
    }
    private void drawBonuses(Draw d){
        for(BonusCache b:bonuses){if(b.taken)continue;float p=.5f+.5f*(float)Math.sin(elapsed*3.8f+b.cell),sz=Math.min(cellW,cellH)*.18f;
            d.setColor(alpha(bonusColor(b),.10f+.08f*p));d.fillCircle(b.x,b.y,sz*1.75f);
            d.setColor(0xFF5A351E);d.fillRoundRect(b.x-sz*.62f,b.y-sz*.30f,b.x+sz*.62f,b.y+sz*.42f,sz*.15f);
            d.setColor(0xFF8A572D);d.fillRoundRect(b.x-sz*.58f,b.y-sz*.46f,b.x+sz*.58f,b.y-sz*.08f,sz*.18f);
            d.setColor(0xFFFFC85A);d.fillRect(b.x-sz*.09f,b.y-sz*.16f,b.x+sz*.09f,b.y+sz*.18f);
            d.setColor(bonusColor(b));for(int i=0;i<3;i++){float a=elapsed*1.7f+i*2.094f;d.fillCircle(b.x+(float)Math.cos(a)*sz*.86f,b.y+(float)Math.sin(a)*sz*.66f,(1.2f+p)*ui);}
        }}
'''
marker='    private RockType chooseRockType(int salt){'
if 'private void buildBonuses()' not in s:
    if marker not in s: raise SystemExit("bonus method marker missing")
    s=s.replace(marker,bonus_methods+'\n'+marker,1)

old='updateHazards(dt);updateWorkers(dt*workerTimeScale);updateFx(dt);'
if old not in s: raise SystemExit("bonus update point missing")
s=s.replace(old,'updateHazards(dt);updateWorkers(dt*workerTimeScale);updateBonuses(dt);updateFx(dt);',1)

old='drawHazards(d);drawVeins(d);drawChest(d);drawPortal(d);'
if old not in s: raise SystemExit("bonus draw point missing")
s=s.replace(old,'drawHazards(d);drawVeins(d);drawBonuses(d);drawChest(d);drawPortal(d);',1)

needle='''        Vein best=null;float bd=Float.MAX_VALUE;
        for(Vein v:veins){if(v.dead)continue;float q=dist2(x,y,v.x,v.y);if(q<bd){bd=q;best=v;}}'''
if 'ПРИКАЗ • ЗАБРАТЬ ТАЙНИК' not in s:
    if needle not in s: raise SystemExit("bonus tap point missing")
    s=s.replace(needle,'''        BonusCache bonus=null;float bonusD=Float.MAX_VALUE;
        for(BonusCache b:bonuses){if(b.taken)continue;float q=dist2(x,y,b.x,b.y);if(q<bonusD){bonusD=q;bonus=b;}}
        float bonusRadius=Math.min(cellW,cellH)*.62f;
        if(bonus!=null&&bonusD<=bonusRadius*bonusRadius){
            longPressEligible=false;priorityKind=PriorityKind.POINT;priorityVein=null;priorityMob=null;priorityHazard=null;priorityCell=bonus.cell;priorityX=bonus.x;priorityY=bonus.y;resetWorkerRoutes();
            toast="ПРИКАЗ • ЗАБРАТЬ ТАЙНИК";toastTime=1.35f;game.audio.play(GameAudio.Sfx.UI,.72f);game.audio.vibrate(16);return true;
        }

'''+needle,1)

old='if(levelEvent!=LevelEvent.NONE){d.textSize=4.1f*ui;d.setColor(levelEventColor());d.text(levelEventTitle(),width*.66f,57f*ui);}'
if old not in s: raise SystemExit("event HUD point missing")
s=s.replace(old,'if(levelEvent!=LevelEvent.NONE){d.textSize=3.7f*ui;d.setColor(levelEventColor());d.text(levelEventTitle(),width*.66f,52f*ui);}',1)
if 'float y=65f*ui,section=width/4f;' not in s: raise SystemExit("resource HUD point missing")
s=s.replace('float y=65f*ui,section=width/4f;','float y=69f*ui,section=width/4f;',1)

old='''        switch(tab){case GNOMES->drawGnomePanel(d);case UPGRADES->drawUpgradePanel(d);case ARTIFACTS->drawArtifactPanel(d);case RUNES->drawRunePanel(d);}
        button(d,speed,speedHeld?"ГНОМЫ РАБОТАЮТ ×4":"УСКОРИТЬ ГНОМОВ ×4",true,.86f);
    }'''
new='''        switch(tab){case GNOMES->drawGnomePanel(d);case UPGRADES->drawUpgradePanel(d);case ARTIFACTS->drawArtifactPanel(d);case RUNES->drawRunePanel(d);}
        if(speedHeld)drawSpeedGlow(d);
        button(d,speed,speedHeld?"УСКОРЕНИЕ":"УСКОРИТЬ ГНОМОВ",true,.86f);
    }
    private void drawSpeedGlow(Draw d){
        float pulse=.5f+.5f*(float)Math.sin(elapsed*11f);
        for(int i=3;i>=1;i--){float pad=(3f+i*3.5f+pulse*2f)*ui;d.setColor(alpha(0xFFFFD45C,.045f+i*.025f));d.fillRoundRect(speed.l-pad,speed.t-pad,speed.r+pad,speed.b+pad,(12f+i*3f)*ui);}
        d.setColor(alpha(0xFFFFE88A,.65f+.25f*pulse));
        for(int i=0;i<10;i++){float q=(elapsed*(.28f+i*.013f)+i*.103f)%1f,x=speed.l+(speed.r-speed.l)*q,y=(i&1)==0?speed.t-3f*ui:speed.b+3f*ui;d.fillCircle(x,y,(1.4f+(i%3)*.45f)*ui);}
    }'''
if old not in s: raise SystemExit("speed block missing")
s=s.replace(old,new,1)

if 'button(d,primary,"КУПИТЬ • "+format(state.minerBuyCost()),true,.72f)' not in s: raise SystemExit("miner label missing")
s=s.replace('button(d,primary,"КУПИТЬ • "+format(state.minerBuyCost()),true,.72f)',
            'button(d,primary,"КУПИТЬ • ● "+format(state.minerBuyCost()),true,.72f)',1)
if 'button(d,secondary,"УЛУЧШИТЬ • "+format(state.tierUpgradeCost(selectedTier)),true,.66f)' not in s: raise SystemExit("tier label missing")
s=s.replace('button(d,secondary,"УЛУЧШИТЬ • "+format(state.tierUpgradeCost(selectedTier)),true,.66f)',
            'button(d,secondary,"УЛУЧШИТЬ • ● "+format(state.tierUpgradeCost(selectedTier)),true,.66f)',1)

s = sub1(s,
    r'''    private void drawUpgradePanel\(Draw d\)\{.*?\}\n    private void drawArtifactPanel''',
    '''    private void drawUpgradePanel(Draw d){float ct=contentTop();d.align=Draw.Align.CENTER;d.bold=true;d.textSize=6.5f*ui;d.setColor(0xFFF0F3F5);d.text("ШАХТА И ИНФРАСТРУКТУРА • СЕРЕБРО",width/2,ct+17f*ui);d.bold=false;d.align=Draw.Align.LEFT;button(d,primary,"КИРКИ ур."+state.miningUpgrade+" • Ag "+state.globalUpgradeCost(0),true,.62f);button(d,secondary,"ЛОГИСТИКА ур."+state.speedUpgrade+" • Ag "+state.globalUpgradeCost(1),true,.58f);button(d,tertiary,"БОЙ ур."+state.combatUpgrade+" • Ag "+state.globalUpgradeCost(2),true,.62f);button(d,quaternary,(state.guardianLevel==0?"НАНЯТЬ СТРАЖА":"СТРАЖ ур."+state.guardianLevel)+" • "+state.guardianCostLabel(),true,.56f);}
    private void drawArtifactPanel''',
    "upgrade panel", re.S)

if '"КУПИТЬ • ◆"+state.artifactCost(selectedArtifact)' not in s: raise SystemExit("artifact label missing")
s=s.replace('"КУПИТЬ • ◆"+state.artifactCost(selectedArtifact)',
            '"КУПИТЬ • Au "+state.artifactCost(selectedArtifact)',1)
if 'owned?"КУПЛЕН":"НУЖНЫ ◆ АЛМАЗЫ"' not in s: raise SystemExit("artifact pill missing")
s=s.replace('owned?"КУПЛЕН":"НУЖНЫ ◆ АЛМАЗЫ"',
            'owned?"КУПЛЕН":"НУЖНО ЗОЛОТО"',1)
if 'else toast="НЕ ХВАТАЕТ АЛМАЗОВ";toastTime=1.2f;}case RUNES' not in s: raise SystemExit("artifact toast missing")
s=s.replace('else toast="НЕ ХВАТАЕТ АЛМАЗОВ";toastTime=1.2f;}case RUNES',
            'else toast="НЕ ХВАТАЕТ ЗОЛОТА";toastTime=1.2f;}case RUNES',1)
if 'private void buyGlobal(int kind){if(state.buyGlobalUpgrade(kind)){toast="УЛУЧШЕНИЕ КУПЛЕНО";}else toast="НЕ ХВАТАЕТ РЕСУРСОВ";toastTime=1.2f;}' not in s: raise SystemExit("global toast missing")
s=s.replace('private void buyGlobal(int kind){if(state.buyGlobalUpgrade(kind)){toast="УЛУЧШЕНИЕ КУПЛЕНО";}else toast="НЕ ХВАТАЕТ РЕСУРСОВ";toastTime=1.2f;}',
            'private void buyGlobal(int kind){if(state.buyGlobalUpgrade(kind)){toast="УЛУЧШЕНИЕ КУПЛЕНО";}else toast="НЕ ХВАТАЕТ СЕРЕБРА";toastTime=1.2f;}',1)

cp.write_text(s)
