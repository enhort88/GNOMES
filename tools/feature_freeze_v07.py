from pathlib import Path
import re

cave = Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
statep = Path('core/src/main/java/com/enhort/gnomes/game/GameState.java')
s = cave.read_text()
g = statep.read_text()

def rep(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'CaveScreen anchor missing: {label}')
    s = s.replace(old, new, 1)

def grep(old, new, label):
    global g
    if old not in g:
        raise SystemExit(f'GameState anchor missing: {label}')
    g = g.replace(old, new, 1)

# -----------------------------------------------------------------------------
# Runtime state: events, compact event feed, tier unlock celebration and boss prelude.
# -----------------------------------------------------------------------------
rep(
'''    private enum ObjectiveType { ASCEND_GNOME, CLEAR_VEINS, GUARDIAN, DEMON_PURGE, BOSS_HUNT, TREASURE }\n''',
'''    private enum ObjectiveType { ASCEND_GNOME, CLEAR_VEINS, GUARDIAN, DEMON_PURGE, BOSS_HUNT, TREASURE }\n    private enum LevelEvent { NONE, RICH_VEINS, QUIET, IMP_SWARM, FLOODED, ANCIENT, UNSTABLE }\n''',
'enum level event')

rep(
'''    private static final class Fx {\n        float x,y,vx,vy,life,maxLife,size;\n        int color;\n        boolean spark;\n        Fx(float x,float y,float vx,float vy,float life,float size,int color,boolean spark){this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.life=this.maxLife=life;this.size=size;this.color=color;this.spark=spark;}\n    }\n''',
'''    private static final class Fx {\n        float x,y,vx,vy,life,maxLife,size;\n        int color;\n        boolean spark;\n        Fx(float x,float y,float vx,float vy,float life,float size,int color,boolean spark){this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.life=this.maxLife=life;this.size=size;this.color=color;this.spark=spark;}\n    }\n\n    private static final class Notice {\n        final String text; final int color; final float maxLife; float life;\n        Notice(String text,int color,float life){this.text=text;this.color=color;this.life=this.maxLife=life;}\n    }\n''',
'notice class')

rep(
'''        float phase, walkCycle, swing, attackCooldown, stun, routeRetry, spawn=.52f, charm, allyCooldown;\n''',
'''        float phase, walkCycle, swing, attackCooldown, stun, routeRetry, spawn=.52f, charm, allyCooldown, react;\n''',
'worker reaction')

rep(
'''    private final List<Mob> pendingMobs=new ArrayList<>();\n    private Portal portal;\n''',
'''    private final List<Mob> pendingMobs=new ArrayList<>();\n    private final List<Notice> notices=new ArrayList<>();\n    private Portal portal;\n''',
'notice list')

rep(
'''    private boolean buyHold; private float buyHoldStarted,buyRepeat;\n    private final Box summaryOk=new Box(),gameOverOk=new Box();\n''',
'''    private boolean buyHold; private float buyHoldStarted,buyRepeat;\n    private LevelEvent levelEvent=LevelEvent.NONE;\n    private int unlockTier=-1; private float unlockAnim,bossIntro; private EnemyType pendingBoss;\n    private final Box summaryOk=new Box(),gameOverOk=new Box(),unlockOk=new Box();\n''',
'feature fields')

# Input and layout for the unlock celebration.
rep(
'''                float x=sx,y=sy;\n                if(levelSummary){if(summaryOk.hit(x,y)&&summaryAnim>.75f)finishLevelTransition();return true;}\n''',
'''                float x=sx,y=sy;\n                if(unlockTier>=0){if(unlockOk.hit(x,y)||unlockAnim>.35f){unlockTier=-1;game.audio.play(GameAudio.Sfx.UI,.55f);}return true;}\n                if(levelSummary){if(summaryOk.hit(x,y)&&summaryAnim>.75f)finishLevelTransition();return true;}\n''',
'unlock input')

rep(
'''        gameOverOk.set(ox,height*.66f,ox+ow,height*.66f+48f*ui);\n    }\n''',
'''        gameOverOk.set(ox,height*.66f,ox+ow,height*.66f+48f*ui);\n        unlockOk.set(ox,height*.66f,ox+ow,height*.66f+48f*ui);\n    }\n''',
'unlock layout')

# Pick a deterministic level event before veins are built.
rep(
'''        random.setSeed(seed^0x1234FEDCBA98765L);\n        buildVeins();\n''',
'''        random.setSeed(seed^0x1234FEDCBA98765L);\n        levelEvent=pickLevelEvent(seed);\n        buildVeins();\n''',
'event before veins')

rep(
'''        enemyTimer=Math.max(7f,18f-state.depth*.18f)+random.nextFloat()*7f;\n        hazardTimer=Math.max(8f,17f-state.depth*.20f)+random.nextFloat()*10f;\n        levelClearTimer=-1f;setupObjective();\n        toast="ГЛУБИНА "+state.depth+(map.style==CaveMap.Style.RING?" • КОЛЬЦЕВАЯ ШАХТА":"")+" • "+levelObjectiveShort();toastTime=2.8f;\n''',
'''        enemyTimer=Math.max(7f,18f-state.depth*.18f)+random.nextFloat()*7f;\n        hazardTimer=Math.max(8f,17f-state.depth*.20f)+random.nextFloat()*10f;\n        if(levelEvent==LevelEvent.QUIET)enemyTimer*=2.2f;\n        if(levelEvent==LevelEvent.IMP_SWARM)enemyTimer=Math.min(enemyTimer,7.5f);\n        if(levelEvent==LevelEvent.FLOODED||levelEvent==LevelEvent.UNSTABLE)hazardTimer*=.62f;\n        levelClearTimer=-1f;setupObjective();\n        toast="ГЛУБИНА "+state.depth+(map.style==CaveMap.Style.RING?" • КОЛЬЦЕВАЯ ШАХТА":"")+" • "+levelObjectiveShort();toastTime=2.8f;\n        if(levelEvent!=LevelEvent.NONE)addNotice("СОБЫТИЕ • "+levelEventTitle(),levelEventColor(),3.2f);\n''',
'event timers')

# More interesting rock/event mix and rich-level yield.
rep(
'''    private RockType chooseRockType(int salt){\n        float r=random.nextFloat();int d=state.depth;\n        if(d>=22&&r<.07f)return RockType.ANCIENT_CRYSTAL;\n        if(d>=14&&r<.16f)return RockType.OBSIDIAN;\n        if(d>=10&&r<.25f)return RockType.DIAMOND;\n        if(d>=6&&r<.42f)return RockType.GOLD;\n        if(d>=3&&r<.62f)return RockType.SILVER;\n        return RockType.STONE;\n    }\n''',
'''    private RockType chooseRockType(int salt){\n        float r=random.nextFloat();int d=state.depth;\n        float rich=levelEvent==LevelEvent.RICH_VEINS?.14f:0f,ancient=levelEvent==LevelEvent.ANCIENT?.12f:0f;\n        if(d>=18&&r<.05f+ancient)return RockType.ANCIENT_CRYSTAL;\n        if(d>=12&&r<.13f+ancient*.55f)return RockType.OBSIDIAN;\n        if(d>=9&&r<.22f+rich*.50f)return RockType.DIAMOND;\n        if(d>=5&&r<.39f+rich)return RockType.GOLD;\n        if(d>=2&&r<.61f+rich*.45f)return RockType.SILVER;\n        return RockType.STONE;\n    }\n\n    private float levelYieldMultiplier(){return levelEvent==LevelEvent.RICH_VEINS?1.75f:levelEvent==LevelEvent.ANCIENT?1.18f:1f;}\n    private LevelEvent pickLevelEvent(long seed){\n        if(state.depth<=2)return LevelEvent.NONE;\n        float q=hash01(seed^0xA0761D6478BD642FL);\n        if(q<.28f)return LevelEvent.NONE;\n        LevelEvent[] pool={LevelEvent.RICH_VEINS,LevelEvent.QUIET,LevelEvent.IMP_SWARM,LevelEvent.FLOODED,LevelEvent.ANCIENT,LevelEvent.UNSTABLE};\n        return pool[Math.floorMod((int)(seed^(seed>>>32)),pool.length)];\n    }\n    private String levelEventTitle(){return switch(levelEvent){case NONE->"ОБЫЧНАЯ СМЕНА";case RICH_VEINS->"БОГАТЫЕ ЖИЛЫ";case QUIET->"ТИХАЯ ШАХТА";case IMP_SWARM->"НАШЕСТВИЕ БЕСОВ";case FLOODED->"ЗАТОПЛЕННЫЕ ХОДЫ";case ANCIENT->"ДРЕВНЯЯ ПЕЩЕРА";case UNSTABLE->"НЕСТАБИЛЬНЫЙ ПЛАСТ";};}\n    private int levelEventColor(){return switch(levelEvent){case RICH_VEINS->0xFFFFD35A;case QUIET->0xFF8BC6A4;case IMP_SWARM->0xFFE66B58;case FLOODED->0xFF67C7D0;case ANCIENT->0xFFC18BE8;case UNSTABLE->0xFFD39A70;default->0xFFB8C1C7;};}\n''',
'rock event mix')

rep(
'''        if(v.dead)return;v.dead=true;v.hp=0;v.death=0;w.add(v.type.material,state.yieldFor(v.type,w.tier.ordinal()));\n''',
'''        if(v.dead)return;v.dead=true;v.hp=0;v.death=0;w.add(v.type.material,state.yieldFor(v.type,w.tier.ordinal())*levelYieldMultiplier());\n        if(v.type==RockType.GOLD||v.type==RockType.DIAMOND||v.type==RockType.ANCIENT_CRYSTAL)w.react=.85f;\n''',
'rich yield and joy')

# Adaptive music and event-feed lifetimes. Unlock screen intentionally pauses gameplay.
rep(
'''        if(toastTime>0)toastTime-=dt;\n        priorityPulse+=dt;\n        guardianAttackAnim=Math.max(0,guardianAttackAnim-dt);guardianSpawnAnim=Math.max(0,guardianSpawnAnim-dt);guardianHitFlash=Math.max(0,guardianHitFlash-dt);\n''',
'''        if(toastTime>0)toastTime-=dt;\n        priorityPulse+=dt;\n        for(Iterator<Notice>it=notices.iterator();it.hasNext();){Notice n=it.next();n.life-=dt;if(n.life<=0)it.remove();}\n        for(Worker w:workers)w.react=Math.max(0,w.react-dt);\n        guardianAttackAnim=Math.max(0,guardianAttackAnim-dt);guardianSpawnAnim=Math.max(0,guardianSpawnAnim-dt);guardianHitFlash=Math.max(0,guardianHitFlash-dt);\n''',
'notice updates')

rep(
'''        checkLongPress();updateBuyHold(dt);\n        if(levelSummary){summaryAnim=Math.min(3f,summaryAnim+dt);updateFx(dt);return;}\n''',
'''        checkLongPress();updateBuyHold(dt);\n        updateBossPrelude(dt);\n        updateMusicMood();\n        if(unlockTier>=0){unlockAnim=Math.min(2f,unlockAnim+dt);updateFx(dt);return;}\n        if(levelSummary){summaryAnim=Math.min(3f,summaryAnim+dt);updateFx(dt);return;}\n''',
'music/boss/unlock update')

rep(
'''        if(state.totalGnomes()>=5&&enemyTimer<=0&&portal==null){spawnEnemyWave();enemyTimer=Math.max(7f,24f-state.depth*.28f)+random.nextFloat()*9f;}\n        if(hazardTimer<=0){spawnHazard();hazardTimer=Math.max(8f,21f-state.depth*.22f)+random.nextFloat()*15f;}\n''',
'''        if(state.totalGnomes()>=5&&enemyTimer<=0&&portal==null&&pendingBoss==null){spawnEnemyWave();enemyTimer=Math.max(7f,24f-state.depth*.28f)+random.nextFloat()*9f;if(levelEvent==LevelEvent.QUIET)enemyTimer*=2.1f;if(levelEvent==LevelEvent.IMP_SWARM)enemyTimer*=.58f;}\n        if(hazardTimer<=0){spawnHazard();hazardTimer=Math.max(8f,21f-state.depth*.22f)+random.nextFloat()*15f;if(levelEvent==LevelEvent.FLOODED||levelEvent==LevelEvent.UNSTABLE)hazardTimer*=.60f;}\n''',
'event spawn cadence')

# Do not let enemy-defense AI silently overwrite an existing mining assignment unless needed.
rep(
'''            Mob enemy=null;if(defendersLeft>0)enemy=nearestMob(w.x,w.y);\n            if(enemy!=null){defendersLeft--;w.mob=enemy;w.vein=null;fight(w,enemy,dt);continue;}\n''',
'''            Mob enemy=null;if(defendersLeft>0)enemy=nearestMob(w.x,w.y);\n            if(enemy!=null){defendersLeft--;if(w.mob!=enemy)w.react=.34f;w.mob=enemy;w.vein=null;fight(w,enemy,dt);continue;}\n''',
'worker enemy reaction')

# Compact event feed and boss/unlock overlays.
rep(
'''        drawHud(d);drawPanel(d);drawToast(d);if(levelSummary)drawLevelSummary(d);if(gameOver)drawGameOver(d);d.endFrame();\n''',
'''        drawHud(d);drawPanel(d);drawNoticeFeed(d);drawToast(d);drawBossPrelude(d);if(unlockTier>=0)drawTierUnlock(d);if(levelSummary)drawLevelSummary(d);if(gameOver)drawGameOver(d);d.endFrame();\n''',
'render overlays')

# Add status accents to workers without allocating extra sprite objects.
rep(
'''        for(int row=0;row<map.rows;row++){for(Worker w:workers)if(rowForY(w.y)==row)drawWorker(d,w);for(Mob m:mobs)if(rowForY(m.y)==row)drawMob(d,m);}\n''',
'''        for(int row=0;row<map.rows;row++){for(Worker w:workers)if(rowForY(w.y)==row){drawWorker(d,w);drawWorkerStatus(d,w);}for(Mob m:mobs)if(rowForY(m.y)==row)drawMob(d,m);}\n''',
'worker status draw')

rep(
'''        drawPriorityOverlay(d);for(Fx p:fx)drawFx(d,p);drawDarkZones(d);drawAtmosphere(d);\n    }\n''',
'''        drawPriorityOverlay(d);for(Fx p:fx)drawFx(d,p);drawDarkZones(d);drawAtmosphere(d);drawBossHud(d);\n    }\n\n    private void drawWorkerStatus(Draw d,Worker w){\n        float s=Math.max(12f*ui,w.tier.size*ui*.48f);\n        if(w.hasCargo()){float x=w.x+s*.46f,y=w.y+s*.24f;d.setColor(0xFF6C4A2F);d.fillOval(x-4.2f*ui,y-3.4f*ui,x+4.2f*ui,y+4.7f*ui);d.setColor(0xFFD2A65C);d.strokeWidth=1.2f*ui;d.line(x-3f*ui,y-2.3f*ui,x+3f*ui,y-2.3f*ui);}\n        if(w.mob!=null&& !w.mob.dead && (w.action==WorkerAction.WALK||w.action==WorkerAction.FIGHT)){d.align=Draw.Align.CENTER;d.bold=true;d.textSize=6f*ui;d.setColor(0xFFFFD55A);d.text("!",w.x,w.y-s*.72f);d.bold=false;d.align=Draw.Align.LEFT;}\n        if(w.react>0){float p=w.react/.85f;d.setColor(alpha(0xFFFFE07A,.25f+.55f*p));for(int i=0;i<3;i++){float a=i*2.094f+elapsed*2f;d.fillCircle(w.x+(float)Math.cos(a)*s*.62f,w.y-s*.42f+(float)Math.sin(a)*s*.22f,(1.2f+p)*ui);}}\n    }\n''',
'worker status helper')

# HUD event label and progression strip.
rep(
'''        d.align=Draw.Align.CENTER;d.textSize=5.4f*ui;d.setColor(levelObjectiveMet()?0xFF79C98A:0xFFE2B544);d.text(levelObjectiveHud(),width*.66f,42f*ui);d.align=Draw.Align.LEFT;\n''',
'''        d.align=Draw.Align.CENTER;d.textSize=5.4f*ui;d.setColor(levelObjectiveMet()?0xFF79C98A:0xFFE2B544);d.text(levelObjectiveHud(),width*.66f,42f*ui);\n        if(levelEvent!=LevelEvent.NONE){d.textSize=4.1f*ui;d.setColor(levelEventColor());d.text(levelEventTitle(),width*.66f,57f*ui);}\n        d.align=Draw.Align.LEFT;\n''',
'event in hud')

rep(
'''d.text("ДОБЫЧА: "+one.format(gt.miningPower*state.tierPowerMultiplier(selectedTier)*state.miningMultiplier(selectedTier))+"/УДАР",width*.69f,ct+37f*ui);d.align=Draw.Align.LEFT;d.bold=false;\n        if(selectedTier==0)button''',
'''d.text("ДОБЫЧА: "+one.format(gt.miningPower*state.tierPowerMultiplier(selectedTier)*state.miningMultiplier(selectedTier))+"/УДАР",width*.69f,ct+37f*ui);d.align=Draw.Align.LEFT;d.bold=false;drawTierProgress(d,ct+50f*ui);\n        if(selectedTier==0)button''',
'progress strip call')

# Merge unlock celebration.
rep(
'''        if(tertiary.hit(x,y)){switch(tab){case GNOMES->{if(state.mergeTier(selectedTier)){syncWorkers(false);toast=state.depth==1&&levelObjectiveMet()?"ЦЕЛЬ ВЫПОЛНЕНА • ПРОДВИНУТЫЙ ГНОМ":"ЭВОЛЮЦИЯ • 10 → 1";toastTime=1.6f;}}case UPGRADES->buyGlobal(2);default->{}}saveNow();return true;}\n''',
'''        if(tertiary.hit(x,y)){switch(tab){case GNOMES->{int next=Math.min(GnomeTier.values().length-1,selectedTier+1);boolean first=selectedTier<GnomeTier.values().length-1&&state.tierCounts[next]==0;if(state.mergeTier(selectedTier)){syncWorkers(false);toast=state.depth==1&&levelObjectiveMet()?"ЦЕЛЬ ВЫПОЛНЕНА • ПРОДВИНУТЫЙ ГНОМ":"ЭВОЛЮЦИЯ • 10 → 1";toastTime=1.6f;if(first){unlockTier=next;unlockAnim=0;addNotice("ОТКРЫТ НОВЫЙ ГНОМ",GnomeTier.values()[next].color,2.4f);game.audio.play(GameAudio.Sfx.COIN,.80f);}}}case UPGRADES->buyGlobal(2);default->{}}saveNow();return true;}\n''',
'unlock on merge')

# Imps swarm together during their event; boss spawns are staged instead of popping into existence.
rep(
'''    private void spawnEnemyWave(){\n        EnemyType type=chooseEnemyType();if(type==EnemyType.IMP){openPortal(type,3+random.nextInt(4));}else if(type==EnemyType.DEMON){openPortal(type,2+random.nextInt(3));}else if(type==EnemyType.SUCCUBUS){EnemyType[] q={EnemyType.DEMON,EnemyType.SUCCUBUS,EnemyType.DEMON};openPortal(q);}else spawnMob(type);\n''',
'''    private void spawnEnemyWave(){\n        EnemyType type=levelEvent==LevelEvent.IMP_SWARM?EnemyType.IMP:chooseEnemyType();if(type==EnemyType.IMP){openPortal(type,(levelEvent==LevelEvent.IMP_SWARM?6:3)+random.nextInt(levelEvent==LevelEvent.IMP_SWARM?5:4));}else if(type==EnemyType.DEMON){openPortal(type,2+random.nextInt(3));}else if(type==EnemyType.SUCCUBUS){EnemyType[] q={EnemyType.DEMON,EnemyType.SUCCUBUS,EnemyType.DEMON};openPortal(q);}else spawnMob(type);\n''',
'imp swarm event')

rep(
'''    private void spawnBoss(){EnemyType t=state.depth>=30?EnemyType.ELEMENTAL_KING:state.depth>=20?EnemyType.DEMON_KING:EnemyType.IMP_KING;if(t==EnemyType.ELEMENTAL_KING)spawnMob(t);else openPortal(t,1);toast="БОСС • "+t.title.toUpperCase();toastTime=2.6f;game.audio.play(GameAudio.Sfx.BOSS,.90f);game.audio.vibrate(90);}\n''',
'''    private void spawnBoss(){\n        if(pendingBoss!=null||livingBoss()!=null)return;\n        pendingBoss=state.depth>=30?EnemyType.ELEMENTAL_KING:state.depth>=20?EnemyType.DEMON_KING:EnemyType.IMP_KING;bossIntro=2.35f;\n        toast="БОСС ПРИБЛИЖАЕТСЯ";toastTime=2.2f;addNotice("БОСС • "+pendingBoss.title.toUpperCase(),0xFFFF6B57,3f);game.audio.play(GameAudio.Sfx.BOSS,.90f);game.audio.vibrate(90);\n    }\n    private void updateBossPrelude(float dt){\n        if(pendingBoss==null)return;bossIntro-=dt;if(bossIntro>0)return;EnemyType t=pendingBoss;pendingBoss=null;\n        if(t==EnemyType.ELEMENTAL_KING)spawnMob(t);else openPortal(t,1);\n    }\n''',
'boss staged entrance')

rep(
'''    private boolean noHostiles(){if(portal!=null)return false;for(Mob m:mobs)if(!m.dead)return false;return true;}\n''',
'''    private boolean noHostiles(){if(portal!=null||pendingBoss!=null)return false;for(Mob m:mobs)if(!m.dead)return false;return true;}\n''',
'pending boss hostile')

# Boss special attacks replace the old two-king summon-only behavior.
rep(
'''            if((m.type==EnemyType.IMP_KING||m.type==EnemyType.DEMON_KING)&&m.summonCooldown<=0&&mobs.size()+pendingMobs.size()<30){m.summonCooldown=m.type==EnemyType.IMP_KING?6.5f:8f;EnemyType t=m.type==EnemyType.IMP_KING?EnemyType.IMP:EnemyType.DEMON;pendingMobs.add(createMob(t,m.x,m.y));pendingMobs.add(createMob(t,m.x,m.y));}\n''',
'''            if(m.type.isBoss()&&m.summonCooldown<=0)bossSpecial(m);\n''',
'boss special call')

# Hazard event weighting.
rep(
'''        HazardType type=HazardType.values()[random.nextInt(HazardType.values().length)];float danger=1f+Math.min(.45f,state.depth*.012f);float r=Math.min(cellW,cellH)*(type==HazardType.FLOOD?1.15f:.58f)*danger;\n''',
'''        HazardType type=levelEvent==LevelEvent.FLOODED?HazardType.FLOOD:levelEvent==LevelEvent.UNSTABLE&&random.nextFloat()<.72f?HazardType.COLLAPSE:HazardType.values()[random.nextInt(HazardType.values().length)];float danger=1f+Math.min(.45f,state.depth*.012f);float r=Math.min(cellW,cellH)*(type==HazardType.FLOOD?1.15f:.58f)*danger;\n''',
'weighted hazards')

# Notice hooks for things the player must understand instantly.
rep(
'''        if(byGnomes){toast="ОБВАЛ РАЗОБРАН";toastTime=1.5f;game.audio.play(GameAudio.Sfx.ROCK_BREAK,.75f);}\n''',
'''        if(byGnomes){toast="ОБВАЛ РАЗОБРАН";toastTime=1.5f;addNotice("ЗАВАЛ РАЗОБРАН",0xFFD9B47A,1.8f);game.audio.play(GameAudio.Sfx.ROCK_BREAK,.75f);}\n''',
'collapse notice')

rep(
'''if(value>0){toast=lootToast(stolen);toastTime=1.7f;spawnSparks''',
'''if(value>0){toast=lootToast(stolen);toastTime=1.7f;addNotice(lootToast(stolen),0xFFFFB24A,2.1f);spawnSparks''',
'imp steal notice')

rep(
'''if(guardianHp<=0){guardianHp=0;guardianDead=true;guardianTarget=null;toast="СТРАЖ ПАЛ В БОЮ";toastTime=2f;game.audio.vibrate(80);}\n''',
'''if(guardianHp<=0){guardianHp=0;guardianDead=true;guardianTarget=null;toast="СТРАЖ ПАЛ В БОЮ";toastTime=2f;addNotice("СТРАЖ ПАЛ",0xFFFF6558,2.8f);game.audio.vibrate(80);}\n''',
'guardian death notice')

rep(
'''    private void loseWorker(Worker w,String reason){if(!workers.remove(w))return;int ti=w.tier.ordinal();if(state.tierCounts[ti]>0)state.tierCounts[ti]--;state.gnomesLost++;toast="ГНОМ ПОТЕРЯН • "+reason;toastTime=2f;spawnSparks(w.x,w.y,0xFFE6D5BD,12);}\n''',
'''    private void loseWorker(Worker w,String reason){if(!workers.remove(w))return;int ti=w.tier.ordinal();if(state.tierCounts[ti]>0)state.tierCounts[ti]--;state.gnomesLost++;toast="ГНОМ ПОТЕРЯН • "+reason;toastTime=2f;addNotice("−1 ГНОМ • "+reason.toUpperCase(),0xFFFF6A5B,2.4f);spawnSparks(w.x,w.y,0xFFE6D5BD,12);}\n''',
'worker death notice')

# Wet visual frequency reacts to flooded event and expensive decoration scales itself down in huge crowds.
rep(
'''            int cell=map.index(c,r);long h=map.seed^cell*0x94D049BB133111EBL;if(hash01(h)>.13f)continue;\n''',
'''            int cell=map.index(c,r);long h=map.seed^cell*0x94D049BB133111EBL;float wetChance=.13f+(levelEvent==LevelEvent.FLOODED?.20f:0f);if(hash01(h)>wetChance)continue;\n''',
'flooded wet chance')

rep(
'''            boolean soaked=hash01(h^0xA24BAED4963EE407L)<.28f;\n''',
'''            boolean soaked=hash01(h^0xA24BAED4963EE407L)<(levelEvent==LevelEvent.FLOODED?.58f:.28f);\n''',
'flooded soaked chance')

rep(
'''    private void drawCaveDecor(Draw d){\n        if(workers.size()>105)return;\n''',
'''    private void drawCaveDecor(Draw d){\n        if(detailTier()>=2)return;\n''',
'adaptive decor')

rep(
'''    private void drawAtmosphere(Draw d){\n        int dust=workers.size()>90?7:18;''',
'''    private void drawAtmosphere(Draw d){\n        int dust=detailTier()>=2?4:detailTier()==1?8:18;''',
'adaptive dust')

# Append UI/gameplay helpers before the common math helpers near the end.
anchor = '''    private static float len(float x,float y){return(float)Math.sqrt(x*x+y*y);}\n'''
helpers = r'''    private int detailTier(){int load=workers.size()+mobs.size()*3+fx.size()/7;return load>205?2:load>120?1:0;}

    private void addNotice(String text,int color,float life){
        if(text==null||text.isEmpty())return;
        notices.add(new Notice(text,color,life));
        while(notices.size()>4)notices.remove(0);
    }

    private void drawNoticeFeed(Draw d){
        if(notices.isEmpty()||levelSummary||gameOver||unlockTier>=0)return;
        int show=Math.min(3,notices.size());float y=worldT+14f*ui;
        for(int i=0;i<show;i++){
            Notice n=notices.get(notices.size()-1-i);float a=Math.min(1f,n.life/.35f),w=Math.min(width*.72f,270f*ui),h=19f*ui;
            d.setColor(alpha(0xE6101214,.78f*a));d.fillRoundRect(8f*ui,y+i*(h+4f*ui),8f*ui+w,y+i*(h+4f*ui)+h,6f*ui);
            d.setColor(alpha(n.color,a));d.fillRoundRect(8f*ui,y+i*(h+4f*ui),11f*ui,y+i*(h+4f*ui)+h,2f*ui);
            d.textSize=4.7f*ui;d.bold=true;d.setColor(alpha(0xFFF2EEE5,a));d.text(ellipsize(n.text,32),16f*ui,y+i*(h+4f*ui)+13.5f*ui);d.bold=false;
        }
    }

    private void drawTierProgress(Draw d,float y){
        float leftX=width*.21f,rightX=width*.79f,step=(rightX-leftX)/(GnomeTier.values().length-1);
        d.setColor(0xFF4A4F52);d.strokeWidth=2f*ui;d.line(leftX,y,rightX,y);
        for(int i=0;i<GnomeTier.values().length;i++){
            float x=leftX+i*step;GnomeTier tier=GnomeTier.values()[i];boolean owned=state.tierCounts[i]>0,sel=i==selectedTier;
            d.setColor(owned?tier.color:0xFF4A5054);d.fillCircle(x,y,(sel?5.2f:3.5f)*ui);
            if(sel){d.setColor(0xFFF6E1A0);d.strokeWidth=1.4f*ui;d.strokeCircle(x,y,7f*ui);}
        }
    }

    private void drawTierUnlock(Draw d){
        GnomeTier tier=GnomeTier.values()[unlockTier];float p=Math.min(1f,unlockAnim/.45f),cx=width*.5f,cy=height*.43f;
        d.setColor(0xE8000000);d.fillRect(0,0,width,height);
        float pulse=.92f+.08f*(float)Math.sin(elapsed*4.5f);d.setColor(alpha(tier.color,.18f));d.fillCircle(cx,cy,86f*ui*pulse*p);d.setColor(alpha(tier.color,.32f));d.strokeWidth=3f*ui;d.strokeCircle(cx,cy,62f*ui*pulse*p);
        d.align=Draw.Align.CENTER;d.bold=true;d.textSize=9f*ui;d.setColor(UiTheme.GOLD);d.text("НОВЫЙ ГНОМ ОТКРЫТ",cx,cy-78f*ui);
        d.textSize=14f*ui;d.setColor(tier.color);d.text(tier.title.toUpperCase(),cx,cy-45f*ui);
        // A clear emblem reads better at phone size than squeezing another full worker renderer into a modal.
        d.setColor(0xFF5B3A24);d.strokeWidth=8f*ui;d.line(cx-24f*ui,cy+24f*ui,cx+22f*ui,cy-22f*ui);d.setColor(0xFFD3D9DA);d.strokeWidth=10f*ui;d.line(cx+10f*ui,cy-31f*ui,cx+34f*ui,cy-8f*ui);
        d.textSize=6f*ui;d.setColor(0xFFC7D0D5);d.text("ДОБЫЧА "+one.format(tier.miningPower)+" • БОЙ "+one.format(tier.combatPower),cx,cy+58f*ui);
        d.align=Draw.Align.LEFT;d.bold=false;button(d,unlockOk,"В ШАХТУ",unlockAnim>.30f,.82f);
    }

    private Mob livingBoss(){for(Mob m:mobs)if(!m.dead&&m.type.isBoss())return m;return null;}

    private void drawBossPrelude(Draw d){
        if(pendingBoss==null||bossIntro<=0||levelSummary||gameOver)return;float p=Math.min(1f,bossIntro/2.35f),pulse=.5f+.5f*(float)Math.sin(elapsed*8f);
        d.setColor(alpha(0xAA160404,.30f+.20f*pulse));d.fillRect(worldL,worldT,worldR,worldB);
        d.align=Draw.Align.CENTER;d.bold=true;d.textSize=7f*ui;d.setColor(0xFFFFB05A);d.text("БОСС ПРИБЛИЖАЕТСЯ",width*.5f,(worldT+worldB)*.5f-26f*ui);
        d.textSize=12f*ui;d.setColor(0xFFFF6658);d.text(pendingBoss.title.toUpperCase(),width*.5f,(worldT+worldB)*.5f+5f*ui);d.bold=false;d.align=Draw.Align.LEFT;
    }

    private void drawBossHud(Draw d){
        Mob boss=livingBoss();if(boss==null)return;float bw=width*.64f,x=(width-bw)*.5f,y=worldT+8f*ui,h=10f*ui,p=Math.max(0,Math.min(1,boss.hp/boss.maxHp));
        d.setColor(0xDD100D0D);d.fillRoundRect(x,y,x+bw,y+h,4f*ui);d.setColor(0xFF8F2727);d.fillRoundRect(x+2f*ui,y+2f*ui,x+2f*ui+(bw-4f*ui)*p,y+h-2f*ui,3f*ui);
        d.align=Draw.Align.CENTER;d.bold=true;d.textSize=4.8f*ui;d.setColor(0xFFFFE8C5);d.text(boss.type.title.toUpperCase(),width*.5f,y-1f*ui);d.bold=false;d.align=Draw.Align.LEFT;
    }

    private void updateMusicMood(){
        GameAudio.MusicMood want=(pendingBoss!=null||livingBoss()!=null)?GameAudio.MusicMood.BOSS:(portal!=null||countLivingHostiles()>=3?GameAudio.MusicMood.DANGER:GameAudio.MusicMood.MINE);
        game.audio.setMusicMood(want);
    }
    private int countLivingHostiles(){int n=0;for(Mob m:mobs)if(!m.dead&&!m.retreating)n++;return n;}

    private void bossSpecial(Mob m){
        if(mobs.size()+pendingMobs.size()>34){m.summonCooldown=4f;return;}
        if(m.type==EnemyType.IMP_KING){
            m.summonCooldown=5.4f;for(int i=0;i<3;i++)pendingMobs.add(createMob(EnemyType.IMP,m.x+(i-1)*4f*ui,m.y));addNotice("КОРОЛЬ БЕСОВ ЗОВЁТ СТАЮ",0xFFFF8A56,1.8f);
        }else if(m.type==EnemyType.DEMON_KING){
            m.summonCooldown=6.8f;for(int i=0;i<2;i++)pendingMobs.add(createMob(EnemyType.DEMON,m.x+(i==0?-5:5)*ui,m.y));
            float rr=Math.min(cellW,cellH)*1.25f;for(Worker w:workers)if(distance(w.x,w.y,m.x,m.y)<rr)w.stun=Math.max(w.stun,.85f);screenShake=Math.max(screenShake,5f*ui);addNotice("УДАР КОРОЛЯ ДЕМОНОВ",0xFFFF5F50,1.8f);
        }else if(m.type==EnemyType.ELEMENTAL_KING){
            m.summonCooldown=7.6f;float rr=Math.min(cellW,cellH)*1.35f;for(Worker w:workers)if(distance(w.x,w.y,m.x,m.y)<rr){w.stun=Math.max(w.stun,1.1f);if(random.nextFloat()<.08f*(1-state.hazardSurvivalBonus(w.tier.ordinal())))loseWorker(w,"стихийный выброс");}spawnSparks(m.x,m.y,0xFF8BD7FF,12);screenShake=Math.max(screenShake,6f*ui);addNotice("СТИХИЙНЫЙ ВЫБРОС",0xFF8BD7FF,1.8f);
        }else m.summonCooldown=6f;
    }

'''
if anchor not in s:
    raise SystemExit('CaveScreen helper anchor missing')
s = s.replace(anchor, helpers + anchor, 1)

# -----------------------------------------------------------------------------
# Balance: bosses stay bosses even after heavy player investment. Late upgrades still help, but with
# deliberately diminishing returns rather than linear infinity.
# -----------------------------------------------------------------------------
grep(
'''    private static float upgradeCurve(int level, float early, float late) {\n        int a = Math.min(10, Math.max(0, level));\n        int b = Math.max(0, level - 10);\n        return 1f + a * early + b * late;\n    }\n''',
'''    private static float upgradeCurve(int level, float early, float late) {\n        int a = Math.min(10, Math.max(0, level));\n        int b = Math.min(20, Math.max(0, level - 10));\n        int c = Math.max(0, level - 30);\n        return 1f + a * early + b * late + c * late * .22f;\n    }\n''',
'late diminishing returns')

grep(
'''        if (type.isBoss()) scale *= 2.35f;\n''',
'''        if (type.isBoss()) scale *= 3.35f;\n''',
'boss hp balance')

grep(
'''        if (type.isBoss()) scale *= 1.65f;\n''',
'''        if (type.isBoss()) scale *= 1.85f;\n''',
'boss damage balance')

cave.write_text(s)
statep.write_text(g)
print('v0.7 feature-freeze gameplay patch applied')
