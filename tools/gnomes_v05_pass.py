from pathlib import Path
import math, random, re, struct, wave, zlib

ROOT = Path('.')
CAVE = ROOT/'core/src/main/java/com/enhort/gnomes/game/CaveScreen.java'
MAP = ROOT/'core/src/main/java/com/enhort/gnomes/game/CaveMap.java'
MENU = ROOT/'core/src/main/java/com/enhort/gnomes/menu/MenuScreen.java'
DRAW = ROOT/'core/src/main/java/com/enhort/gnomes/draw/Draw.java'


def replace_once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected exactly one match, got {n}')
    return text.replace(old, new, 1)


def sub_once(text, pattern, repl, label):
    out, n = re.subn(pattern, repl, text, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f'{label}: expected exactly one regex match, got {n}')
    return out

# ---------------------------------------------------------------------------
# Draw: fantasy display face + images for the illustrated prologue.
# ---------------------------------------------------------------------------
s = DRAW.read_text()
s = replace_once(s, 'import com.badlogic.gdx.graphics.OrthographicCamera;\n',
                 'import com.badlogic.gdx.graphics.OrthographicCamera;\nimport com.badlogic.gdx.graphics.Texture;\n', 'Draw Texture import')
s = replace_once(s,
'''        font = generate("fonts/DejaVuSans.ttf", 32, cyrillic + extra);\n        fontBold = generate("fonts/DejaVuSans-Bold.ttf", 32, cyrillic + extra);''',
'''        font = generate("fonts/DejaVuSans.ttf", 32, cyrillic + extra);\n        String display = Gdx.files.internal("fonts/RuslanDisplay-Regular.ttf").exists()\n                ? "fonts/RuslanDisplay-Regular.ttf" : "fonts/DejaVuSans-Bold.ttf";\n        fontBold = generate(display, 32, cyrillic + extra);''', 'fantasy font')
s = replace_once(s,
'''    private void applyFontColor(BitmapFont f) {''',
'''    public void image(Texture texture, float l, float t, float r, float b) {\n        if (texture == null || r <= l || b <= t) return;\n        ensure(Mode.TEXT);\n        batch.setColor(1f, 1f, 1f, 1f);\n        batch.draw(texture, l, t, r - l, b - t, 0, 0, texture.getWidth(), texture.getHeight(), false, true);\n    }\n\n    private void applyFontColor(BitmapFont f) {''', 'Draw image method')
DRAW.write_text(s)

# ---------------------------------------------------------------------------
# CaveMap: workers can avoid visible traps in addition to hard cave-in blocks.
# ---------------------------------------------------------------------------
s = MAP.read_text()
anchor = '''    /** Workers clearing a cave-in may enter the blocked goal cell, but cannot cross other rubble. */\n    public int[] pathToBlockedGoal(int start, int goal) { return pathInternal(start, goal, false, true); }\n'''
insert = anchor + '''\n    /**\n     * Worker route around currently visible danger cells. This intentionally does not use the shared cache:\n     * hazards are short-lived and their mask changes independently of the cave revision.\n     */\n    public int[] pathAvoiding(int start, int goal, boolean[] avoid) {\n        int count = cols * rows;\n        if (start < 0 || start >= count || goal < 0 || goal >= count) return new int[0];\n        if (start == goal) return new int[] { start };\n        int[] parent = new int[count];\n        java.util.Arrays.fill(parent, -2);\n        ArrayDeque<Integer> q = new ArrayDeque<>();\n        parent[start] = -1;\n        q.add(start);\n        while (!q.isEmpty()) {\n            int cur = q.removeFirst();\n            int c = col(cur), r = row(cur), bits = openings[r][c];\n            for (int dir : DIRS) {\n                if ((bits & dir) == 0) continue;\n                int nc = c + dx(dir), nr = r + dy(dir);\n                if (!inside(nc, nr)) continue;\n                int next = index(nc, nr);\n                if (next != start && isBlocked(next)) continue;\n                if (next != goal && avoid != null && next < avoid.length && avoid[next]) continue;\n                if (parent[next] != -2) continue;\n                parent[next] = cur;\n                if (next == goal) return reconstruct(parent, goal);\n                q.addLast(next);\n            }\n        }\n        return new int[0];\n    }\n'''
s = replace_once(s, anchor, insert, 'CaveMap pathAvoiding')
MAP.write_text(s)

# ---------------------------------------------------------------------------
# Menu: bestiary book, updated progression copy, music refresh.
# ---------------------------------------------------------------------------
s = MENU.read_text()
s = replace_once(s, 'import com.enhort.gnomes.GameAudio;\n',
                 'import com.enhort.gnomes.GameAudio;\nimport com.enhort.gnomes.game.model.EnemyType;\n', 'Menu enemy import')
s = replace_once(s, 'private enum Mode { MAIN, SLOTS, DIFFICULTY, SETTINGS, ABOUT, DELETE }',
                 'private enum Mode { MAIN, SLOTS, DIFFICULTY, BESTIARY, SETTINGS, ABOUT, DELETE }', 'Menu mode')
s = replace_once(s, 'private final Box[] main=new Box[6];', 'private final Box[] main=new Box[7];', 'Menu button count')
s = replace_once(s,
'''        switch(mode){case MAIN->main(d);case SLOTS->slots(d);case DIFFICULTY->difficulty(d);case SETTINGS->settings(d);case ABOUT->about(d);case DELETE->delete(d);}''',
'''        switch(mode){case MAIN->main(d);case SLOTS->slots(d);case DIFFICULTY->difficulty(d);case BESTIARY->bestiary(d);case SETTINGS->settings(d);case ABOUT->about(d);case DELETE->delete(d);}''', 'Menu render switch')
s = replace_once(s,
'''        button(d,main[3],"НАСТРОЙКИ",true,UiTheme.STEEL,false,.92f);\n        button(d,main[4],"ОБ ИГРЕ",true,UiTheme.COPPER,false,.92f);\n        button(d,main[5],"ВЫХОД",true,UiTheme.RED,false,.92f);''',
'''        button(d,main[3],"НАСТРОЙКИ",true,UiTheme.STEEL,false,.92f);\n        button(d,main[4],"БЕСТИАРИЙ",true,UiTheme.COPPER,false,.92f);\n        button(d,main[5],"ОБ ИГРЕ",true,UiTheme.COPPER,false,.92f);\n        button(d,main[6],"ВЫХОД",true,UiTheme.RED,false,.92f);''', 'Menu main labels')
s = replace_once(s,
'''        d.text("После каждого уровня гномы и обычные апгрейды продаются.",width/2,128f*ui);''',
'''        d.text("Деньги делятся по сложности, а половина отряда идёт глубже.",width/2,128f*ui);''', 'difficulty copy')

about_anchor = '''    private void settings(Draw d){'''
bestiary_method = r'''    private void bestiary(Draw d){
        heading(d,"КНИГА ВРАГОВ");
        float l=18f*ui,r=width-18f*ui,t=135f*ui,b=back.t-10f*ui,mid=width/2f;
        d.setColor(0xFF5A3D27);d.fillRoundRect(l-3f*ui,t-3f*ui,r+3f*ui,b+3f*ui,10f*ui);
        d.setColor(0xFFF0E1B8);d.fillRoundRect(l,t,mid-3f*ui,b,7f*ui);d.fillRoundRect(mid+3f*ui,t,r,b,7f*ui);
        d.setColor(0x33805B35);d.fillRect(mid-1f*ui,t+3f*ui,mid+1f*ui,b-3f*ui);
        EnemyType[] all=EnemyType.values();int deepest=game.saves.deepestDepth();
        float row=(b-t-18f*ui)/5f;
        for(int i=0;i<all.length;i++){
            EnemyType e=all[i];int col=i/5,ri=i%5;float x=col==0?l+10f*ui:mid+12f*ui,y=t+11f*ui+ri*row;
            int unlock=enemyUnlock(e);boolean known=deepest>=unlock||game.settings.freeShop;
            drawEnemyStamp(d,e,x+14f*ui,y+20f*ui,known);
            d.bold=true;d.textSize=7.6f*ui;d.setColor(known?0xFF3B2A20:0xFF95876F);d.text(known?e.title.toUpperCase():"???",x+31f*ui,y+13f*ui);
            d.bold=false;d.textSize=6.2f*ui;d.setColor(known?0xFF6B5844:0xFF9C907C);
            d.text(known?enemyRole(e):"встречается глубже",x+31f*ui,y+29f*ui);
            if(known){d.text("база HP "+Math.round(e.hp),x+31f*ui,y+43f*ui);}
        }
        d.align=Draw.Align.CENTER;d.textSize=6.8f*ui;d.setColor(0xFF7D6B52);d.text("Записи открываются по мере погружения. В TEST MODE книга полная.",width/2,b-8f*ui);d.align=Draw.Align.LEFT;
        button(d,back,"ЗАКРЫТЬ КНИГУ",true,UiTheme.STEEL,false,.90f);
    }

    private int enemyUnlock(EnemyType e){return switch(e){case IMP->1;case GHOST->2;case DEMON->7;case SUCCUBUS->9;case IMP_KING->10;case STONE_GOLEM->12;case WATER_GOLEM->15;case FIRE_GOLEM->18;case DEMON_KING->20;case ELEMENTAL_KING->30;};}
    private String enemyRole(EnemyType e){return switch(e){case IMP->"вор • боится отпора";case DEMON->"охотник на гномов";case SUCCUBUS->"чары • сеет драку";case GHOST->"летает сквозь стены";case STONE_GOLEM->"тяжёлый элементаль";case WATER_GOLEM->"водный элементаль";case FIRE_GOLEM->"не боится лавы";case IMP_KING->"босс • вор и призыватель";case DEMON_KING->"босс • убийца";case ELEMENTAL_KING->"босс • стихии";};}
    private void drawEnemyStamp(Draw d,EnemyType e,float x,float y,boolean known){
        int c=known?e.color:0xFF9B8F79;d.setColor(0x33614A31);d.fillCircle(x,y,13f*ui);d.setColor(c);d.fillCircle(x,y,known?7f*ui:5f*ui);
        if(known&&e.isImp()){d.pathReset();d.moveTo(x-6f*ui,y-5f*ui);d.lineTo(x-11f*ui,y-12f*ui);d.lineTo(x-2f*ui,y-8f*ui);d.closePath();d.fillPath();d.pathReset();d.moveTo(x+6f*ui,y-5f*ui);d.lineTo(x+11f*ui,y-12f*ui);d.lineTo(x+2f*ui,y-8f*ui);d.closePath();d.fillPath();}
        if(!known){d.bold=true;d.textSize=8f*ui;d.setColor(0xFFF0E1B8);d.align=Draw.Align.CENTER;d.text("?",x,y+3f*ui);d.align=Draw.Align.LEFT;d.bold=false;}
    }

'''
s = replace_once(s, about_anchor, bestiary_method + about_anchor, 'Menu bestiary insertion')
s = replace_once(s,
'''            if(main[3].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.SETTINGS;return;}\n            if(main[4].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.ABOUT;return;}\n            if(main[5].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);Gdx.app.exit();return;}''',
'''            if(main[3].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.SETTINGS;return;}\n            if(main[4].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.BESTIARY;return;}\n            if(main[5].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.ABOUT;return;}\n            if(main[6].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);Gdx.app.exit();return;}''', 'Menu taps')
s = replace_once(s,
'''        }else if(mode==Mode.SETTINGS){''',
'''        }else if(mode==Mode.BESTIARY){\n            if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);mode=Mode.MAIN;}\n        }else if(mode==Mode.SETTINGS){''', 'Menu bestiary tap')
s = s.replace('game.settings.toggleSound();if(game.settings.soundEnabled)game.audio.play(GameAudio.Sfx.UI,.75f);return;', 'game.settings.toggleSound();game.audio.refreshMusic();if(game.settings.soundEnabled)game.audio.play(GameAudio.Sfx.UI,.75f);return;')
s = s.replace('game.settings.setSoundVolume(game.settings.soundVolume-.10f);game.audio.play(GameAudio.Sfx.UI,.6f);return;', 'game.settings.setSoundVolume(game.settings.soundVolume-.10f);game.audio.refreshMusic();game.audio.play(GameAudio.Sfx.UI,.6f);return;')
s = s.replace('game.settings.setSoundVolume(game.settings.soundVolume+.10f);game.audio.play(GameAudio.Sfx.UI,.6f);return;', 'game.settings.setSoundVolume(game.settings.soundVolume+.10f);game.audio.refreshMusic();game.audio.play(GameAudio.Sfx.UI,.6f);return;')
MENU.write_text(s)

# ---------------------------------------------------------------------------
# Cave gameplay pass.
# ---------------------------------------------------------------------------
s = CAVE.read_text()
s = replace_once(s, 'private enum Tab { GNOMES, UPGRADES, ARTIFACTS, RUNES }',
'''private enum Tab { GNOMES, UPGRADES, ARTIFACTS, RUNES }\n    private enum ObjectiveType { CLEAR_VEINS, GUARDIAN, DEMON_PURGE, BOSS_HUNT, TREASURE }''', 'objective enum')
s = replace_once(s,
'''        float phase, walkCycle, swing, attackCooldown, stun, routeRetry, spawn=.52f;''',
'''        float phase, walkCycle, swing, attackCooldown, stun, routeRetry, spawn=.52f, charm, allyCooldown;''', 'worker charm fields')
s = replace_once(s,
'''        float x,y,hp,maxHp,phase,walkCycle,attack,attackCooldown,summonCooldown=5f,routeTimer,spawn=.65f;\n        int[] path=new int[0]; int pathIndex; int goalCell=-1;\n        Worker target;\n        boolean dead;''',
'''        float x,y,hp,maxHp,phase,walkCycle,attack,attackCooldown,summonCooldown=5f,routeTimer,spawn=.65f,flee,hitFlash;\n        int[] path=new int[0]; int pathIndex; int goalCell=-1;\n        Worker target;\n        boolean dead,enraged,ghostSteals,retreating;''', 'mob fields')
portal_class = r'''
    private static final class Portal {
        final int cell; final float x,y; final EnemyType[] queue;
        int next; float age,spawnTimer=.52f,closeAge;
        Portal(int cell,float x,float y,EnemyType[] queue){this.cell=cell;this.x=x;this.y=y;this.queue=queue;}
        boolean done(){return next>=queue.length&&closeAge>1.0f;}
    }
'''
s = replace_once(s, '\n    private final GnomesGame game;', portal_class + '\n    private final GnomesGame game;', 'portal class')
s = replace_once(s,
'''    private final List<CaveHazard> hazards=new ArrayList<>();''',
'''    private final List<CaveHazard> hazards=new ArrayList<>();\n    private final List<Mob> pendingMobs=new ArrayList<>();\n    private Portal portal;''', 'pending mob fields')
s = replace_once(s,
'''    private boolean levelSummary,gameOver;\n    private float summaryAnim,guardianAttackAnim,guardianSpawnAnim;\n    private long summaryEarned,summaryInvested,summaryWallet,summaryCapital,summaryTransfer;''',
'''    private boolean levelSummary,gameOver;\n    private float summaryAnim,guardianAttackAnim,guardianSpawnAnim,guardianHitFlash,guardianCooldown;\n    private float guardianX,guardianY,guardianHp,guardianMaxHp;\n    private boolean guardianDead;\n    private Mob guardianTarget;\n    private long summaryEarned,summaryInvested,summaryWallet,summaryCapital,summaryTransfer;\n    private int summaryCarry;\n    private ObjectiveType objectiveType=ObjectiveType.CLEAR_VEINS;\n    private int objectiveTarget,objectiveStartKills;\n    private long objectiveTreasureTarget;\n    private boolean objectiveStarted;\n    private boolean buyHold; private float buyHoldStarted,buyRepeat;''', 'runtime state fields')

# Input: hold-to-buy.
s = replace_once(s,
'''                if(speed.hit(x,y)){speedHeld=true;game.audio.play(GameAudio.Sfx.UI,.55f);return true;}\n                if(y>=worldT&&y<=worldB){''',
'''                if(speed.hit(x,y)){speedHeld=true;game.audio.play(GameAudio.Sfx.UI,.55f);return true;}\n                if(tab==Tab.GNOMES&&selectedTier==0&&primary.hit(x,y)){buyHold=true;buyHoldStarted=elapsed;buyRepeat=.32f;return handleTap(x,y);}\n                if(y>=worldT&&y<=worldB){''', 'buy hold down')
s = replace_once(s,
'''            @Override public boolean touchUp(int sx,int sy,int pointer,int button){speedHeld=false;worldTouchActive=false;longPressEligible=false;return true;}''',
'''            @Override public boolean touchUp(int sx,int sy,int pointer,int button){speedHeld=false;buyHold=false;worldTouchActive=false;longPressEligible=false;return true;}''', 'buy hold up')
s = replace_once(s,
'''                if(!speed.hit(sx,sy))speedHeld=false;\n                if(worldTouchActive&&distance(worldTouchX,worldTouchY,sx,sy)>16f*ui)longPressEligible=false;''',
'''                if(!speed.hit(sx,sy))speedHeld=false;\n                if(buyHold&&!primary.hit(sx,sy))buyHold=false;\n                if(worldTouchActive&&distance(worldTouchX,worldTouchY,sx,sy)>16f*ui)longPressEligible=false;''', 'buy hold drag')

# New level runtime setup.
s = replace_once(s,
'''        mobs.clear();hazards.clear();fx.clear();veins.clear();clearPriority(false);objectiveReminderShown=false;levelSummary=false;gameOver=false;summaryAnim=0;''',
'''        mobs.clear();pendingMobs.clear();hazards.clear();fx.clear();veins.clear();portal=null;clearPriority(false);objectiveReminderShown=false;levelSummary=false;gameOver=false;summaryAnim=0;objectiveStarted=false;buyHold=false;''', 'generate reset')
s = replace_once(s,
'''        syncWorkers(true);\n        enemyTimer=Math.max(8f,17f-state.depth*.12f)+random.nextFloat()*8f;\n        hazardTimer=15f+random.nextFloat()*12f;\n        levelClearTimer=-1f;\n        toast="ГЛУБИНА "+state.depth+(map.style==CaveMap.Style.RING?" • КОЛЬЦЕВАЯ ШАХТА":"");toastTime=2.4f;\n        if(state.depth%10==0)spawnBoss();''',
'''        syncWorkers(true);\n        guardianX=cx(map.startCol)-Math.min(cellW,cellH)*.28f;guardianY=cy(map.startRow);guardianMaxHp=state.guardianMaxHp();guardianHp=guardianMaxHp;guardianDead=state.guardianLevel<=0;guardianTarget=null;\n        enemyTimer=Math.max(7f,18f-state.depth*.18f)+random.nextFloat()*7f;\n        hazardTimer=Math.max(8f,17f-state.depth*.20f)+random.nextFloat()*10f;\n        levelClearTimer=-1f;setupObjective();\n        toast="ГЛУБИНА "+state.depth+(map.style==CaveMap.Style.RING?" • КОЛЬЦЕВАЯ ШАХТА":"")+" • "+levelObjectiveShort();toastTime=2.8f;''', 'generate timers/objective')

# update loop wholesale
s = sub_once(s, r'    private void update\(float dt,float workerTimeScale\)\{.*?(?=\n    private void checkLongPress)', r'''    private void update(float dt,float workerTimeScale){
        if(map==null)return;
        if(toastTime>0)toastTime-=dt;
        priorityPulse+=dt;
        guardianAttackAnim=Math.max(0,guardianAttackAnim-dt);guardianSpawnAnim=Math.max(0,guardianSpawnAnim-dt);guardianHitFlash=Math.max(0,guardianHitFlash-dt);
        if(screenShake>0)screenShake=Math.max(0,screenShake-dt*18f*ui);
        checkLongPress();updateBuyHold(dt);
        if(levelSummary){summaryAnim=Math.min(3f,summaryAnim+dt);updateFx(dt);return;}
        if(gameOver){updateFx(dt);return;}

        updateVeins(dt);updatePortal(dt);updateGuardian(dt);updateMobs(dt);updateHazards(dt);updateWorkers(dt*workerTimeScale);updateFx(dt);updateObjective();

        enemyTimer-=dt;hazardTimer-=dt;saveTimer+=dt;
        if(state.totalGnomes()>=5&&enemyTimer<=0&&portal==null){spawnEnemyWave();enemyTimer=Math.max(7f,24f-state.depth*.28f)+random.nextFloat()*9f;}
        if(hazardTimer<=0){spawnHazard();hazardTimer=Math.max(8f,21f-state.depth*.22f)+random.nextFloat()*15f;}
        if(saveTimer>=4f){saveNow();saveTimer=0;}
        if(state.totalGnomes()==0&&!state.canBuyMiner()){beginGameOver();return;}
        if(levelObjectiveMet())beginLevelSummary();
    }

    private void updateBuyHold(float dt){
        if(!buyHold||tab!=Tab.GNOMES||selectedTier!=0)return;
        if(elapsed-buyHoldStarted<.36f)return;
        buyRepeat-=dt;if(buyRepeat>0)return;buyRepeat=.115f;
        if(state.buyMiner()){syncWorkers(false);toast="ГНОМЫ: "+state.tierCounts[0];toastTime=.45f;}
        else {buyHold=false;toast="НЕ ХВАТАЕТ РЕСУРСОВ";toastTime=1f;}
    }
''', 'update loop')

# guardian whole method, remove old standalone guardianCooldown declaration as part regex
s = sub_once(s, r'    private float guardianCooldown;\n    private void updateGuardian\(float dt\)\{.*?(?=\n    private void updateWorkers)', r'''    private void updateGuardian(float dt){
        if(state.guardianLevel<=0||guardianDead)return;
        guardianMaxHp=Math.max(guardianMaxHp,state.guardianMaxHp());guardianHp=Math.min(guardianMaxHp,guardianHp);
        guardianCooldown-=dt;
        float hx=cx(map.startCol)-Math.min(cellW,cellH)*.28f,hy=cy(map.startRow),leash=Math.min(cellW,cellH)*1.75f;
        Mob best=null;float bd=Float.MAX_VALUE;
        for(int pass=0;pass<2&&best==null;pass++)for(Mob m:mobs){
            if(m.dead||m.type==EnemyType.SUCCUBUS||m.type==EnemyType.GHOST)continue;
            boolean thief=m.type.isImp()||m.type.stealsChest();if((pass==0)!=thief)continue;
            float q=dist2(hx,hy,m.x,m.y);if(q<leash*leash&&q<bd){bd=q;best=m;}
        }
        guardianTarget=best;
        float speed=58f*ui*(1f+Math.min(.45f,state.guardianLevel*.04f));
        if(best==null){moveGuardian(hx,hy,speed,dt);return;}
        float di=distance(guardianX,guardianY,best.x,best.y),reach=Math.min(cellW,cellH)*.34f;
        if(di>reach){moveGuardian(best.x,best.y,speed,dt);return;}
        if(guardianCooldown<=0){guardianCooldown=state.guardianAttackInterval();guardianAttackAnim=.34f;best.hp-=state.guardianDamage();best.hitFlash=.16f;if(best.type.isImp())best.flee=.9f;spawnSparks(best.x,best.y,0xFFFFD873,4);}
    }
    private void moveGuardian(float tx,float ty,float speed,float dt){float dx=tx-guardianX,dy=ty-guardianY,di=len(dx,dy);if(di<1f)return;float step=Math.min(di,speed*dt);guardianX+=dx/di*step;guardianY+=dy/di*step;}
''', 'guardian movement')

# updateWorkers: add charm decrement/branch and safe routing remains through routeWorker.
s = replace_once(s,
'''            w.spawn=Math.max(0,w.spawn-dt);w.attackCooldown-=dt;if(w.swing>0)w.swing=Math.max(0,w.swing-dt);if(w.stun>0)w.stun-=dt;if(w.routeRetry>0)w.routeRetry-=dt;''',
'''            w.spawn=Math.max(0,w.spawn-dt);w.attackCooldown-=dt;w.allyCooldown-=dt;w.charm=Math.max(0,w.charm-dt);if(w.swing>0)w.swing=Math.max(0,w.swing-dt);if(w.stun>0)w.stun-=dt;if(w.routeRetry>0)w.routeRetry-=dt;''', 'worker timers')
s = replace_once(s,
'''            if(w.stun>0){w.action=WorkerAction.STUNNED;w.vx=w.vy=0;continue;}\n\n            if(priorityKind==PriorityKind.VEIN''',
'''            if(w.stun>0){w.action=WorkerAction.STUNNED;w.vx=w.vy=0;continue;}\n            if(w.charm>0){fightAlly(w,dt);continue;}\n\n            if(priorityKind==PriorityKind.VEIN''', 'charmed worker branch')

# fight rebalance + imp flee
s = sub_once(s, r'    private void fight\(Worker w,Mob m,float dt\)\{.*?(?=\n    private float moveSpeed)', r'''    private void fight(Worker w,Mob m,float dt){
        int cell=cellFor(m.x,m.y);
        if(!atCell(w,cell)||distance(w.x,w.y,m.x,m.y)>Math.min(cellW,cellH)*.42f){w.action=WorkerAction.WALK;routeWorker(w,cell);followWorker(w,moveSpeed(w)*1.08f,dt);return;}
        w.action=WorkerAction.FIGHT;w.vx=w.vy=0;
        if(w.attackCooldown<=0&&w.swing<=0){w.swing=.46f;w.hitApplied=false;w.attackCooldown=Math.max(.18f,.62f-w.tier.ordinal()*.05f);}
        float p=w.swing<=0?1:1-w.swing/.46f;
        if(w.swing>0&&!w.hitApplied&&p>.53f){
            w.hitApplied=true;float dmg=w.tier.combatPower*state.tierPowerMultiplier(w.tier.ordinal())*state.combatMultiplier(w.tier.ordinal());
            if(m.type.isBoss())dmg*=.52f;else if(m.type.isElemental())dmg*=.78f;
            m.hp-=dmg;m.attack=.14f;m.hitFlash=.16f;if(m.type.isImp())m.flee=.95f;spawnSparks(m.x,m.y,0xFFFFB74D,4);game.audio.play(GameAudio.Sfx.ENEMY,.32f);
        }
    }

    private void fightAlly(Worker attacker,float dt){
        Worker victim=null;float bd=Float.MAX_VALUE;for(Worker w:workers){if(w==attacker||w.charm>0)continue;float q=dist2(attacker.x,attacker.y,w.x,w.y);if(q<bd){bd=q;victim=w;}}
        if(victim==null){attacker.action=WorkerAction.STUNNED;return;}
        int cell=cellFor(victim.x,victim.y);if(!atCell(attacker,cell)||distance(attacker.x,attacker.y,victim.x,victim.y)>Math.min(cellW,cellH)*.35f){attacker.action=WorkerAction.WALK;routeWorker(attacker,cell);followWorker(attacker,moveSpeed(attacker),dt);return;}
        attacker.action=WorkerAction.FIGHT;if(attacker.allyCooldown<=0){attacker.allyCooldown=.85f;attacker.swing=.46f;victim.stun=Math.max(victim.stun,.55f);float risk=Math.min(.20f,.015f+attacker.tier.combatPower*state.combatMultiplier(attacker.tier.ordinal())*.006f);if(random.nextFloat()<risk)loseWorker(victim,"погиб от зачарованного сородича");else spawnSparks(victim.x,victim.y,0xFFFF5A8C,3);}
    }
''', 'fight and ally fight')

# route worker and stale blocked path exploit hardening
s = sub_once(s, r'    private void routeWorker\(Worker w,int goal\)\{.*?(?=\n    private void followWorker)', r'''    private void routeWorker(Worker w,int goal){
        if(w.goalCell==goal&&w.path.length>0)return;if(w.goalCell==goal&&w.routeRetry>0)return;
        w.goalCell=goal;boolean[] danger=dangerMask(w);w.path=map.pathAvoiding(cellFor(w.x,w.y),goal,danger);w.pathIndex=Math.min(1,w.path.length);w.routeRetry=w.path.length==0?.28f:0f;
    }
    private boolean[] dangerMask(Worker w){
        boolean[] mask=null;int smart=w.tier.ordinal();
        for(CaveHazard h:hazards){if(h.type==HazardType.COLLAPSE||h.cleared)continue;boolean seen=h.fired||(smart>=2&&h.age>.22f)||(smart>=4&&h.age>.05f);if(!seen)continue;if(mask==null)mask=new boolean[map.cols*map.rows];mask[h.cell]=true;if(smart>=4){int c=map.col(h.cell),r=map.row(h.cell);int[] dirs={CaveMap.N,CaveMap.E,CaveMap.S,CaveMap.W};for(int dir:dirs){int nc=c+CaveMap.dx(dir),nr=r+CaveMap.dy(dir);if(map.inside(nc,nr)&&h.type==HazardType.LAVA)mask[map.index(nc,nr)]=true;}}}
        }return mask;
    }
''', 'safe worker routing')
s = replace_once(s,
'''            int node=w.path[w.pathIndex];float tx=cx(map.col(node)),ty=cy(map.row(node));float dx=tx-w.x,dy=ty-w.y,di=len(dx,dy);''',
'''            int node=w.path[w.pathIndex];if(map.isBlocked(node)){w.path=new int[0];w.pathIndex=0;w.goalCell=-1;w.vx=w.vy=0;return;}float tx=cx(map.col(node)),ty=cy(map.row(node));float dx=tx-w.x,dy=ty-w.y,di=len(dx,dy);''', 'blocked stale route guard')

# Ghost can emerge from broken ore.
s = replace_once(s,
'''        game.audio.play(GameAudio.Sfx.ROCK_BREAK,.88f);game.audio.vibrate(26+Math.min(42,w.tier.ordinal()*7));''',
'''        game.audio.play(GameAudio.Sfx.ROCK_BREAK,.88f);game.audio.vibrate(26+Math.min(42,w.tier.ordinal()*7));\n        if(state.totalGnomes()>=5&&mobs.size()<30&&random.nextFloat()<Math.min(.075f,.025f+state.depth*.0015f))spawnGhostFrom(v);''', 'ghost rock spawn')

# Replace mob/portal section from updateMobs through spawnMob, preserving updateHazards boundary.
s = sub_once(s, r'    private void updateMobs\(float dt\)\{.*?(?=\n    private void updateHazards)', r'''    private void updatePortal(float dt){
        if(portal==null)return;portal.age+=dt;portal.spawnTimer-=dt;if(portal.next>=portal.queue.length){portal.closeAge+=dt;if(portal.done())portal=null;return;}if(portal.spawnTimer>0||portal.age<.42f)return;
        EnemyType type=portal.queue[portal.next++];Mob m=createMob(type,portal.x,portal.y);m.spawn=.42f;pendingMobs.add(m);portal.spawnTimer=.30f+(type.isBoss()?.22f:0);if(portal.next>=portal.queue.length)portal.closeAge=0;
    }

    private void updateMobs(float dt){
        pendingMobs.clear();
        for(Mob m:mobs){
            if(m.dead)continue;m.spawn=Math.max(0,m.spawn-dt);m.attack=Math.max(0,m.attack-dt);m.hitFlash=Math.max(0,m.hitFlash-dt);m.flee=Math.max(0,m.flee-dt);m.attackCooldown-=dt;m.summonCooldown-=dt;m.routeTimer-=dt;if(m.spawn>0)continue;
            if(m.hp<=0){killMob(m);continue;}
            if(m.type==EnemyType.GHOST){updateGhost(m,dt);continue;}
            if(m.type==EnemyType.SUCCUBUS){updateSuccubus(m,dt);continue;}
            if(m.type.isImp()&&m.flee>0){fleeImp(m,dt);continue;}

            boolean thief=m.type.isImp();
            boolean canHitGuard=!guardianDead&&state.guardianLevel>0&&(m.type.isDemon()||m.type.isElemental()||(m.type.isImp()&&m.enraged));
            float qGuard=canHitGuard?dist2(m.x,m.y,guardianX,guardianY):Float.MAX_VALUE;
            Worker near=nearestWorker(m.x,m.y);float qWorker=near==null?Float.MAX_VALUE:dist2(m.x,m.y,near.x,near.y);
            int goal;
            if(thief&&!m.enraged){goal=map.index(map.startCol,map.startRow);m.target=null;}
            else if(qGuard<qWorker*1.15f){goal=cellFor(guardianX,guardianY);m.target=null;}
            else {m.target=near;goal=near==null?map.index(map.startCol,map.startRow):cellFor(near.x,near.y);}
            if(m.routeTimer<=0||m.goalCell!=goal){m.goalCell=goal;m.path=(thief&&!m.enraged)?map.pathIgnoringBlocks(cellFor(m.x,m.y),goal):map.path(cellFor(m.x,m.y),goal);m.pathIndex=Math.min(1,m.path.length);m.routeTimer=.48f;}
            boolean reached=followMob(m,m.type.moveSpeed*state.enemySpeedScale(m.type)*ui,dt);
            if(reached){if(thief&&!m.enraged)robChest(m);else if(qGuard<qWorker*1.15f)attackGuardian(m);else attackWorker(m,m.target);}
            if((m.type==EnemyType.IMP_KING||m.type==EnemyType.DEMON_KING)&&m.summonCooldown<=0&&mobs.size()+pendingMobs.size()<30){m.summonCooldown=m.type==EnemyType.IMP_KING?6.5f:8f;EnemyType t=m.type==EnemyType.IMP_KING?EnemyType.IMP:EnemyType.DEMON;pendingMobs.add(createMob(t,m.x,m.y));pendingMobs.add(createMob(t,m.x,m.y));}
        }
        mobs.addAll(pendingMobs);mobs.removeIf(m->m.dead&&m.attack<-1f);
    }

    private void updateGhost(Mob m,float dt){
        float tx,ty;if(m.retreating){tx=worldL-40f*ui;ty=m.y;}else if(m.ghostSteals){tx=cx(map.startCol);ty=cy(map.startRow);}else{if(m.target==null||!workers.contains(m.target))m.target=nearestWorker(m.x,m.y);if(m.target==null){m.retreating=true;return;}tx=m.target.x;ty=m.target.y;}
        float dx=tx-m.x,dy=ty-m.y,di=len(dx,dy),speed=m.type.moveSpeed*state.enemySpeedScale(m.type)*ui;if(di>1){m.x+=dx/di*Math.min(di,speed*dt);m.y+=dy/di*Math.min(di,speed*dt);m.walkCycle+=dt*12f;}
        if(m.retreating&&m.x<worldL-20f*ui){m.dead=true;m.attack=-2f;return;}
        if(!m.retreating&&di<Math.min(cellW,cellH)*.30f&&m.attackCooldown<=0){m.attackCooldown=2f;m.attack=.42f;if(m.ghostSteals){long[] st=state.stealGhostLoot();toast=ghostLootToast(st);toastTime=1.5f;}else if(m.target!=null){float risk=Math.min(.36f,.08f+.015f*state.depth);if(random.nextFloat()<risk*(1-state.hazardSurvivalBonus(m.target.tier.ordinal())*.45f))loseWorker(m.target,"призрак вырвал душу");else m.target.stun=Math.max(m.target.stun,1.4f);}m.retreating=true;}
    }

    private void updateSuccubus(Mob m,float dt){
        m.target=nearestWorker(m.x,m.y);if(m.target==null)return;int goal=cellFor(m.target.x,m.target.y);if(m.routeTimer<=0||m.goalCell!=goal){m.goalCell=goal;m.path=map.path(cellFor(m.x,m.y),goal);m.pathIndex=Math.min(1,m.path.length);m.routeTimer=.55f;}boolean reached=followMob(m,m.type.moveSpeed*state.enemySpeedScale(m.type)*ui,dt);if(reached&&m.attackCooldown<=0){m.attackCooldown=4.2f;m.attack=.65f;m.target.charm=Math.max(m.target.charm,4.5f+random.nextFloat()*2.2f);m.target.stun=.25f;for(Mob imp:mobs)if(!imp.dead&&imp.type.isImp()&&distance(imp.x,imp.y,m.x,m.y)<Math.min(cellW,cellH)*2.2f)imp.enraged=true;spawnSparks(m.target.x,m.target.y,0xFFFF5A9C,7);toast="СУККУБ ЗАЧАРОВАЛ ГНОМА";toastTime=1.5f;}}

    private void fleeImp(Mob m,float dt){
        Worker threat=nearestWorker(m.x,m.y);int cur=cellFor(m.x,m.y);if(threat==null)return;if(m.path.length==0||m.pathIndex>=m.path.length||m.routeTimer<=0){int c=map.col(cur),r=map.row(cur),best=cur;float bd=dist2(m.x,m.y,threat.x,threat.y);int[] dirs={CaveMap.N,CaveMap.E,CaveMap.S,CaveMap.W};for(int dir:dirs)if(map.connected(c,r,dir)){int nc=c+CaveMap.dx(dir),nr=r+CaveMap.dy(dir);int next=map.index(nc,nr);float q=dist2(cx(nc),cy(nr),threat.x,threat.y);if(q>bd){bd=q;best=next;}}m.goalCell=best;m.path=map.pathIgnoringBlocks(cur,best);m.pathIndex=Math.min(1,m.path.length);m.routeTimer=.28f;}followMob(m,m.type.moveSpeed*state.enemySpeedScale(m.type)*1.85f*ui,dt);
    }

    private boolean followMob(Mob m,float speed,float dt){
        if(m.path.length==0)return false;if(m.pathIndex>=m.path.length){if(m.goalCell<0)return false;float gx=cx(map.col(m.goalCell)),gy=cy(map.row(m.goalCell));return distance(m.x,m.y,gx,gy)<Math.min(cellW,cellH)*.25f;}
        int node=m.path[m.pathIndex];float tx=cx(map.col(node)),ty=cy(map.row(node));float dx=tx-m.x,dy=ty-m.y,di=len(dx,dy);if(di<2.5f*ui){m.x=tx;m.y=ty;m.pathIndex++;if(m.pathIndex>=m.path.length)return true;node=m.path[m.pathIndex];tx=cx(map.col(node));ty=cy(map.row(node));dx=tx-m.x;dy=ty-m.y;di=len(dx,dy);}if(di>.001f){m.x+=dx/di*speed*dt;m.y+=dy/di*speed*dt;m.walkCycle+=dt*(5.5f+m.type.moveSpeed/18f);}return false;
    }

    private void robChest(Mob m){if(m.attackCooldown>0)return;m.attackCooldown=m.type==EnemyType.IMP_KING?1.7f:2.5f;m.attack=.42f;long[] stolen=state.stealFromChest(state.depth,m.type==EnemyType.IMP_KING);long value=stolen[0]+stolen[1]*8L+stolen[2]*20L+stolen[3]*100L;if(value>0){toast=lootToast(stolen);toastTime=1.7f;spawnSparks(cx(map.startCol),cy(map.startRow),0xFFFFC04A,8);game.audio.play(GameAudio.Sfx.COIN,.65f);game.audio.vibrate(40);}}
    private String lootToast(long[] st){StringBuilder b=new StringBuilder("БЕС УКРАЛ: ");if(st[0]>0)b.append("кам ").append(st[0]).append(' ');if(st[1]>0)b.append("Ag ").append(st[1]).append(' ');if(st[2]>0)b.append("Au ").append(st[2]).append(' ');if(st[3]>0)b.append("◆ ").append(st[3]);return b.toString().trim();}
    private String ghostLootToast(long[] st){long v=st[0]+st[1]*8L+st[2]*20L;return v>0?"ПРИЗРАК УНЕС СОКРОВИЩА • "+v:"ПРИЗРАК НЕ НАШЁЛ ДОБЫЧИ";}

    private void attackWorker(Mob m,Worker w){
        if(w==null||m.attackCooldown>0||m.type.isImp()||m.type==EnemyType.SUCCUBUS||m.type==EnemyType.GHOST)return;m.attackCooldown=1.0f;m.attack=.40f;float power=m.type.contactPower*state.enemyDamageScale(m.type);w.stun=Math.max(w.stun,.35f+power*.018f);spawnSparks(w.x,w.y,0xFFFF765D,4);float survive=state.hazardSurvivalBonus(w.tier.ordinal());float lethality=Math.min(.62f,.012f+power*.007f);if(m.type.isBoss())lethality=Math.min(.78f,lethality*1.35f);if(random.nextFloat()<lethality*(1-survive))loseWorker(w,m.type.title+" убил гнома");
    }
    private void attackGuardian(Mob m){
        if(guardianDead||m.attackCooldown>0||m.type==EnemyType.SUCCUBUS||m.type==EnemyType.GHOST||m.type.isImp()&&!m.enraged)return;m.attackCooldown=.95f;m.attack=.42f;float damage=(7f+m.type.contactPower*5f)*state.enemyDamageScale(m.type);guardianHp-=damage;guardianHitFlash=.22f;spawnSparks(guardianX,guardianY,0xFF75C5DE,5);if(guardianHp<=0){guardianHp=0;guardianDead=true;guardianTarget=null;toast="СТРАЖ ПАЛ В БОЮ";toastTime=2f;game.audio.vibrate(80);}
    }
    private void killMob(Mob m){
        if(m.dead)return;m.dead=true;m.attack=-2f;state.enemiesDefeated++;spawnDeath(m);screenShake=Math.max(screenShake,2.2f*ui);if(priorityKind==PriorityKind.MOB&&priorityMob==m){clearPriority(false);toast="ЦЕЛЬ УНИЧТОЖЕНА";toastTime=1.2f;}if(m.type.isBoss()){int reward=3+state.depth/8;state.diamond+=reward;toast="БОСС ПОВЕРЖЕН • ◆"+reward;toastTime=2.4f;}
    }
    private void loseWorker(Worker w,String reason){if(!workers.remove(w))return;int ti=w.tier.ordinal();if(state.tierCounts[ti]>0)state.tierCounts[ti]--;state.gnomesLost++;toast="ГНОМ ПОТЕРЯН • "+reason;toastTime=2f;spawnSparks(w.x,w.y,0xFFE6D5BD,12);}
    private Worker nearestWorker(float x,float y){Worker best=null;float bd=Float.MAX_VALUE;for(Worker w:workers){float q=dist2(x,y,w.x,w.y);if(q<bd){bd=q;best=w;}}return best;}
    private Mob nearestMob(float x,float y){Mob best=null;float bd=Float.MAX_VALUE;for(Mob m:mobs){if(m.dead||m.retreating)continue;float q=dist2(x,y,m.x,m.y);if(q<bd){bd=q;best=m;}}return best;}

    private void spawnEnemyWave(){
        EnemyType type=chooseEnemyType();if(type==EnemyType.IMP){openPortal(type,3+random.nextInt(4));}else if(type==EnemyType.DEMON){openPortal(type,2+random.nextInt(3));}else if(type==EnemyType.SUCCUBUS){EnemyType[] q={EnemyType.DEMON,EnemyType.SUCCUBUS,EnemyType.DEMON};openPortal(q);}else spawnMob(type);
        toast="ТРЕВОГА • "+type.title.toUpperCase();toastTime=1.3f;game.audio.play(GameAudio.Sfx.ENEMY,.48f);
    }
    private EnemyType chooseEnemyType(){int d=state.depth;float r=random.nextFloat();if(d>=18&&r<.12f)return EnemyType.FIRE_GOLEM;if(d>=15&&r<.24f)return EnemyType.WATER_GOLEM;if(d>=12&&r<.37f)return EnemyType.STONE_GOLEM;if(d>=9&&r<.45f&&!hasLiving(EnemyType.SUCCUBUS))return EnemyType.SUCCUBUS;if(d>=7&&r<.67f)return EnemyType.DEMON;return EnemyType.IMP;}
    private void spawnBoss(){EnemyType t=state.depth>=30?EnemyType.ELEMENTAL_KING:state.depth>=20?EnemyType.DEMON_KING:EnemyType.IMP_KING;if(t==EnemyType.ELEMENTAL_KING)spawnMob(t);else openPortal(t,1);toast="БОСС • "+t.title.toUpperCase();toastTime=2.6f;game.audio.play(GameAudio.Sfx.BOSS,.90f);game.audio.vibrate(90);}
    private void openPortal(EnemyType type,int n){EnemyType[] q=new EnemyType[n];java.util.Arrays.fill(q,type);openPortal(q);}
    private void openPortal(EnemyType[] q){if(portal!=null||q.length==0)return;List<Integer> out=map.outerCells();int cell=out.get(random.nextInt(out.size()));portal=new Portal(cell,cx(map.col(cell)),cy(map.row(cell)),q);}
    private void spawnMob(EnemyType type){List<Integer> out=map.outerCells();int cell=out.get(random.nextInt(out.size()));mobs.add(createMob(type,cx(map.col(cell)),cy(map.row(cell))));}
    private Mob createMob(EnemyType type,float x,float y){Mob m=new Mob(type,x,y,random.nextFloat()*6.28f);m.maxHp=type.hp*state.enemyHpScale(type);m.hp=m.maxHp;m.ghostSteals=type==EnemyType.GHOST&&random.nextFloat()<.62f;return m;}
    private void spawnGhostFrom(Vein v){Mob m=createMob(EnemyType.GHOST,v.x,v.y);m.spawn=.52f;m.target=nearestWorker(v.x,v.y);mobs.add(m);toast="ИЗ КАМНЯ ВЫРВАЛСЯ ПРИЗРАК";toastTime=1.5f;}
    private boolean hasLiving(EnemyType type){for(Mob m:mobs)if(!m.dead&&m.type==type)return true;return false;}
''', 'mob portal replacement')

# traps become more serious with depth.
s = replace_once(s,
'''        HazardType type=HazardType.values()[random.nextInt(HazardType.values().length)];float r=Math.min(cellW,cellH)*(type==HazardType.FLOOD?1.2f:.62f);''',
'''        HazardType type=HazardType.values()[random.nextInt(HazardType.values().length)];float danger=1f+Math.min(.45f,state.depth*.012f);float r=Math.min(cellW,cellH)*(type==HazardType.FLOOD?1.15f:.58f)*danger;''', 'hazard scaling')
s = s.replace('h.rubbleMaxHp=110f*(1f+state.depth*.10f);', 'h.rubbleMaxHp=125f*(1f+state.depth*.13f);')

# World and tunnel aesthetics.
s = sub_once(s, r'    private void drawWorld\(Draw d\)\{.*?(?=\n    private int rowForY)', r'''    private void drawWorld(Draw d){
        d.setColor(0xFF0B0A09);d.fillRect(worldL,worldT,worldR,worldB);drawRockMass(d);drawTunnels(d);drawCaveDecor(d);drawHazards(d);drawVeins(d);drawChest(d);drawPortal(d);
        for(int row=0;row<map.rows;row++){for(Worker w:workers)if(rowForY(w.y)==row)drawWorker(d,w);for(Mob m:mobs)if(rowForY(m.y)==row)drawMob(d,m);}
        if(state.guardianLevel>0&&!guardianDead)drawGuardian(d,guardianX,guardianY,Math.min(cellW,cellH)*.54f);
        drawPriorityOverlay(d);for(Fx p:fx)drawFx(d,p);drawDarkZones(d);drawAtmosphere(d);
    }
''', 'drawWorld')
s = sub_once(s, r'    private void drawRockMass\(Draw d\)\{.*?(?=\n    private void drawTunnels)', r'''    private void drawRockMass(Draw d){
        d.setColor(0xFF151614);d.fillRect(worldL,worldT,worldR,worldB);long seed=map.seed;
        int count=workers.size()>90?46:82;for(int i=0;i<count;i++){long q=seed+i*0x9E3779B97F4A7C15L;float x=worldL+hash01(q)*(worldR-worldL),y=worldT+hash01(q^0xA5A5A5A5L)*(worldB-worldT),rr=(2f+hash01(q^0x55AA55AAL)*8f)*ui;int c=i%7==0?0xFF2A3027:i%5==0?0xFF2A2722:0xFF201F1C;d.setColor(c);d.fillOval(x-rr*1.5f,y-rr*.65f,x+rr*1.5f,y+rr*.65f);if(i%13==0){d.setColor(0x553A6C3D);d.fillCircle(x-rr*.4f,y-rr*.18f,rr*.55f);d.fillCircle(x+rr*.2f,y-rr*.25f,rr*.38f);}}
    }
''', 'rock background')
s = sub_once(s, r'    private void drawTunnels\(Draw d\)\{.*?(?=\n    private void drawTorch)', r'''    private void drawTunnels(Draw d){
        float base=Math.min(cellW,cellH),outer=base*.48f,mid=base*.37f,inner=base*.29f;
        d.strokeWidth=outer;d.setColor(0xFF4A4035);drawTunnelEdges(d);
        d.strokeWidth=mid;d.setColor(0xFF292722);drawTunnelEdges(d);
        d.strokeWidth=inner;d.setColor(0xFF36312A);drawTunnelEdges(d);
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){int idx=map.index(c,r);if(map.degree(c,r)>=3){d.setColor(0xFF3B352E);d.fillCircle(cx(c),cy(r),inner*.58f);}if((idx+state.depth*3)%14==0&&!isDarkCell(idx))drawTorch(d,cx(c)-inner*.26f,cy(r)-inner*.38f,idx);if((idx*7+state.depth)%19==0){d.setColor(0xFF6A4A31);d.strokeWidth=2f*ui;d.line(cx(c)-inner*.45f,cy(r)-inner*.48f,cx(c)-inner*.45f,cy(r)+inner*.48f);d.line(cx(c)+inner*.45f,cy(r)-inner*.48f,cx(c)+inner*.45f,cy(r)+inner*.48f);d.line(cx(c)-inner*.52f,cy(r)-inner*.34f,cx(c)+inner*.52f,cy(r)-inner*.34f);}}
    }
    private void drawTunnelEdges(Draw d){for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){if(map.connected(c,r,CaveMap.E))d.line(cx(c),cy(r),cx(c+1),cy(r));if(map.connected(c,r,CaveMap.S))d.line(cx(c),cy(r),cx(c),cy(r+1));}}
    private void drawCaveDecor(Draw d){
        if(workers.size()>100)return;for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){int cell=map.index(c,r);long q=map.seed^cell*0x9E3779B97F4A7C15L;float x=cx(c),y=cy(r),k=hash01(q);if(k<.055f){d.setColor(0xFF31583A);d.strokeWidth=1.5f*ui;for(int i=0;i<4;i++)d.line(x-7f*ui+i*4f*ui,y-13f*ui,x-4f*ui+i*3f*ui,y+8f*ui);d.setColor(0xFF4F784B);for(int i=0;i<5;i++)d.fillCircle(x-7f*ui+i*4f*ui,y-5f*ui+i%2*5f*ui,2.2f*ui);}else if(k>.94f){float drip=((elapsed*(8+cell%5)+cell*17)%32)*ui;d.setColor(0x665CBCE3);d.strokeWidth=1f*ui;d.line(x,y-18f*ui,x,y-8f*ui+drip*.25f);d.fillOval(x-1.2f*ui,y-8f*ui+drip*.45f,x+1.2f*ui,y-4f*ui+drip*.45f);}if(cell%37==state.depth%37&&r<map.rows/2){d.setColor(0x18DDF6D7);d.pathReset();d.moveTo(x-24f*ui,worldT);d.lineTo(x+7f*ui,worldT);d.lineTo(x+26f*ui,y+34f*ui);d.lineTo(x-12f*ui,y+34f*ui);d.closePath();d.fillPath();}}
    }
    private boolean isDarkCell(int cell){if(state.depth<3||cell==map.index(map.startCol,map.startRow))return false;return hash01(map.seed^cell*0xD1B54A32D192ED03L)<.105f;}
    private void drawDarkZones(Draw d){for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){int cell=map.index(c,r);if(!isDarkCell(cell))continue;float rr=Math.min(cellW,cellH)*.53f;d.setColor(0xB9080909);d.fillCircle(cx(c),cy(r),rr);}for(Worker w:workers){if(isDarkCell(cellFor(w.x,w.y))){float rr=18f*ui+w.tier.ordinal()*2f*ui;d.setColor(0x226FC6E9);d.fillCircle(w.x,w.y,rr);d.setColor(0x22FFD170);d.fillCircle(w.x,w.y,rr*.55f);}}}
    private void drawPortal(Draw d){if(portal==null)return;float p=Math.min(1f,portal.age/.42f),close=portal.next>=portal.queue.length?Math.max(0,1-portal.closeAge):1f,rr=Math.min(cellW,cellH)*.34f*p*close;d.setColor(0x444E1D6D);d.fillCircle(portal.x,portal.y,rr*1.3f);for(int i=0;i<5;i++){float a=elapsed*(2.5f+i*.3f)+i*1.25f;d.setColor(i%2==0?0xFFB34CE2:0xFFE45658);d.strokeWidth=(1.2f+i*.25f)*ui;d.strokeCircle(portal.x+(float)Math.cos(a)*rr*.12f,portal.y+(float)Math.sin(a)*rr*.12f,rr*(.58f+i*.09f));}}
''', 'tunnels/decor')

# Chest no longer draws a stationary guardian.
s = s.replace('        if(state.guardianLevel>0)drawGuardian(d,x-s*.46f,y-s*.04f,s*.54f);\n', '')

# Fix pickaxe head location.
s = sub_once(s, r'    private void drawAnimatedPick\(Draw d,float x,float y,float s,float angle,float hand\)\{.*?(?=\n    private void drawSack)', r'''    private void drawAnimatedPick(Draw d,float x,float y,float s,float angle,float hand){
        d.save();d.translate(x,y);d.rotate(angle);d.setColor(0xFFF0BE8C);d.fillCircle(0,0,s*.065f);
        d.setColor(0xFF4A2E1C);d.strokeWidth=s*.105f;d.line(0,0,s*.60f,0);d.setColor(0xFF8B5B32);d.strokeWidth=s*.065f;d.line(0,0,s*.60f,0);
        d.setColor(0xFF59646A);d.strokeWidth=s*.135f;d.line(s*.54f,-s*.22f,s*.54f,s*.22f);d.setColor(0xFFDCE5E9);d.strokeWidth=s*.035f;d.line(s*.50f,-s*.18f,s*.50f,s*.15f);d.restore();
    }
''', 'pickaxe orientation')

# Mob visual switch + new character art.
s = replace_once(s,
'''    private void drawMob(Draw d,Mob m){if(m.dead)return;float s=m.type.size*ui;switch(m.type){case IMP,IMP_KING->drawImp(d,m,s);case DEMON,DEMON_KING->drawDemon(d,m,s);default->drawGolem(d,m,s);}''',
'''    private void drawMob(Draw d,Mob m){if(m.dead)return;float s=m.type.size*ui;switch(m.type){case IMP,IMP_KING->drawImp(d,m,s);case DEMON,DEMON_KING->drawDemon(d,m,s);case SUCCUBUS->drawSuccubus(d,m,s);case GHOST->drawGhost(d,m,s);default->drawGolem(d,m,s);}''', 'mob visual switch')
new_visuals = r'''
    private void drawSuccubus(Draw d,Mob m,float s){float flap=(float)Math.sin(elapsed*7+m.phase),pulse=.5f+.5f*(float)Math.sin(elapsed*4+m.phase);d.save();d.translate(m.x,m.y);d.setColor(0x33FF4F91);d.fillCircle(0,0,s*(.65f+.12f*pulse));d.setColor(0xFF57213A);d.pathReset();d.moveTo(-s*.18f,-s*.08f);d.lineTo(-s*(.72f+.08f*flap),-s*.42f);d.lineTo(-s*.46f,s*.20f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.18f,-s*.08f);d.lineTo(s*(.72f+.08f*flap),-s*.42f);d.lineTo(s*.46f,s*.20f);d.closePath();d.fillPath();d.setColor(0xFFC94979);d.fillOval(-s*.25f,-s*.10f,s*.25f,s*.48f);d.setColor(0xFFE5AC94);d.fillCircle(0,-s*.34f,s*.25f);d.setColor(0xFF33151F);d.pathReset();d.moveTo(-s*.18f,-s*.52f);d.lineTo(-s*.40f,-s*.78f);d.lineTo(-s*.08f,-s*.60f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.18f,-s*.52f);d.lineTo(s*.40f,-s*.78f);d.lineTo(s*.08f,-s*.60f);d.closePath();d.fillPath();d.setColor(0xFFFFB2D1);d.fillCircle(-s*.09f,-s*.36f,s*.035f);d.fillCircle(s*.09f,-s*.36f,s*.035f);if(m.attack>0){d.setColor(0x99FF5AA0);d.strokeWidth=2f*ui;d.strokeCircle(0,-s*.15f,s*(.55f+.15f*pulse));}d.restore();}
    private void drawGhost(Draw d,Mob m,float s){float wave=(float)Math.sin(elapsed*8+m.phase),a=m.retreating?.34f:.72f;d.save();d.translate(m.x,m.y);d.setColor(alpha(0xFF82D7E7,a*.22f));d.fillCircle(0,0,s*.72f);d.setColor(alpha(0xFFB9F2F7,a));d.pathReset();d.moveTo(-s*.33f,-s*.34f);d.quadTo(0,-s*.68f,s*.34f,-s*.33f);d.lineTo(s*.28f,s*.28f);d.lineTo(s*.12f,s*(.56f+.08f*wave));d.lineTo(-s*.03f,s*.34f);d.lineTo(-s*.20f,s*(.57f-.06f*wave));d.lineTo(-s*.34f,s*.27f);d.closePath();d.fillPath();d.setColor(0xFF203B46);d.fillOval(-s*.18f,-s*.30f,-s*.06f,-s*.15f);d.fillOval(s*.06f,-s*.30f,s*.18f,-s*.15f);d.setColor(alpha(0xFF8DE4F2,a*.35f));for(int i=0;i<3;i++)d.fillOval(-s*(.75f+i*.23f),s*(-.10f+i*.12f),-s*(.45f+i*.23f),s*(.02f+i*.12f));d.restore();}
'''
s = replace_once(s, '\n    private void drawGolem(Draw d,Mob m,float s){', new_visuals + '\n    private void drawGolem(Draw d,Mob m,float s){', 'succubus ghost art')

# Gnome panel, artifacts, global runes.
s = sub_once(s, r'    private void drawGnomePanel\(Draw d\)\{.*?(?=\n    private void drawUpgradePanel)', r'''    private void drawGnomePanel(Draw d){GnomeTier gt=GnomeTier.values()[selectedTier];float ct=contentTop();button(d,left,"‹",selectedTier>0,1.15f);button(d,right,"›",selectedTier<GnomeTier.values().length-1,1.15f);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=11f*ui;d.setColor(gt.color);d.text(gt.title,width/2,ct+17f*ui);d.textSize=9.2f*ui;d.setColor(0xFFF1D58A);d.text("ГНОМОВ: "+state.tierCounts[selectedTier],width*.34f,ct+38f*ui);d.setColor(0xFFB9C8D0);d.text("ДОБЫЧА: "+one.format(gt.miningPower*state.tierPowerMultiplier(selectedTier)*state.miningMultiplier(selectedTier))+"/удар",width*.69f,ct+38f*ui);d.align=Draw.Align.LEFT;d.bold=false;
        if(selectedTier==0)button(d,primary,"КУПИТЬ • "+format(state.minerBuyCost()),true,.72f);else statPill(d,primary,"УР. "+state.tierLevels[selectedTier]+" • боевой "+one.format(gt.combatPower*state.combatMultiplier(selectedTier)));button(d,secondary,"УЛУЧШИТЬ • "+format(state.tierUpgradeCost(selectedTier)),true,.70f);boolean merge=selectedTier<GnomeTier.values().length-1&&(GameState.FREE_SHOP||state.tierCounts[selectedTier]>=10);button(d,tertiary,GameState.FREE_SHOP?"TEST • СОЗДАТЬ СЛЕД.":"СЛИТЬ 10 → 1",merge,.65f);statPill(d,quaternary,"СУМКА • "+format((long)(gt.cargoCapacity*state.carryMultiplier(selectedTier))));}
''', 'gnome panel')
s = sub_once(s, r'    private void drawArtifactPanel\(Draw d\)\{.*?(?=\n    private void drawRunePanel)', r'''    private void drawArtifactPanel(Draw d){ArtifactType a=ArtifactType.values()[selectedArtifact];float ct=contentTop();button(d,left,"‹",selectedArtifact>0,1.15f);button(d,right,"›",selectedArtifact<ArtifactType.values().length-1,1.15f);boolean owned=state.artifactOwned(selectedArtifact),active=owned&&state.artifactActive[selectedArtifact];d.align=Draw.Align.CENTER;d.bold=true;d.textSize=10.5f*ui;d.setColor(a.color);d.text(a.title,width/2,ct+18f*ui);d.bold=false;d.textSize=8.2f*ui;d.setColor(0xFFB5BFC7);d.text(a.description+" • "+(owned?(active?"АКТИВЕН":"СНЯТ"):"НЕ КУПЛЕН"),width/2,ct+37f*ui);d.align=Draw.Align.LEFT;button(d,primary,owned?(active?"СНЯТЬ":"НАДЕТЬ"):"КУПИТЬ • ◆"+state.artifactCost(selectedArtifact),true,.70f);statPill(d,secondary,"АРТЕФАКТ ПОКУПАЕТСЯ ОДИН РАЗ");statPill(d,tertiary,owned?"✓ ПРИНАДЛЕЖИТ ВАМ":"НУЖНЫ АЛМАЗЫ");statPill(d,quaternary,active?"✦ АКТИВИРОВАН":"НЕ АКТИВЕН");}
''', 'artifact panel')
s = sub_once(s, r'    private void drawRunePanel\(Draw d\)\{.*?(?=\n\n    private void button)', r'''    private void drawRunePanel(Draw d){RuneType r=RuneType.values()[selectedRune];float ct=contentTop();button(d,left,"‹",selectedRune>0,1.15f);button(d,right,"›",selectedRune<RuneType.values().length-1,1.15f);boolean active=state.runeIsActive(selectedRune);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=10.5f*ui;d.setColor(r.color);d.text(r.title,width/2,ct+18f*ui);d.bold=false;d.textSize=8.2f*ui;d.setColor(0xFFB7C0C7);d.text(r.description+" • ур. "+state.runeLevels[selectedRune]+" • "+(active?"АКТИВНА":"СНЯТА"),width/2,ct+37f*ui);d.align=Draw.Align.LEFT;button(d,primary,"УСИЛИТЬ • ◆"+state.runeUpgradeCost(selectedRune),state.runeLevels[selectedRune]<12,.66f);button(d,secondary,active?"СНЯТЬ РУНУ":"АКТИВИРОВАТЬ",state.runeLevels[selectedRune]>0,.66f);statPill(d,tertiary,"ДЕЙСТВУЕТ НА ВСЕХ ГНОМОВ");statPill(d,quaternary,"МЕТА • НЕ СБРАСЫВАЕТСЯ");}
''', 'rune panel')

# Tap handling for artifacts/runes and guardian revival.
s = s.replace('case ARTIFACTS->{if(state.upgradeArtifact(selectedArtifact)){toast="АРТЕФАКТ УСИЛЕН";}else toast="НЕ ХВАТАЕТ АЛМАЗОВ";toastTime=1.2f;}', 'case ARTIFACTS->{if(state.artifactOwned(selectedArtifact)){state.toggleArtifact(selectedArtifact);toast=state.artifactActive[selectedArtifact]?"АРТЕФАКТ АКТИВИРОВАН":"АРТЕФАКТ СНЯТ";}else if(state.buyArtifact(selectedArtifact))toast="АРТЕФАКТ КУПЛЕН";else toast="НЕ ХВАТАЕТ АЛМАЗОВ";toastTime=1.2f;}')
s = s.replace('case UPGRADES->buyGlobal(1);case RUNES->{runeTarget=(runeTarget+1)%state.runeTargetCount();}', 'case UPGRADES->buyGlobal(1);case RUNES->{if(state.toggleRune(selectedRune))toast=state.runeIsActive(selectedRune)?"РУНА АКТИВНА":"РУНА СНЯТА";else toast="СНАЧАЛА СОЗДАЙТЕ РУНУ";toastTime=1.2f;}')
s = s.replace('case RUNES->{if(state.engraveRune(runeTarget,selectedRune))toast=state.runeAtTarget(runeTarget)==selectedRune?"РУНА НАНЕСЕНА":"РУНА СНЯТА";else toast="РУНА ЕЩЁ НЕ СОЗДАНА";toastTime=1.3f;}', 'case RUNES->{toast="РУНЫ ТЕПЕРЬ ГЛОБАЛЬНЫЕ";toastTime=1.1f;}')
s = s.replace('if(state.buyOrUpgradeGuardian()){toast="СТРАЖ СУНДУКА • ур."+state.guardianLevel;if(before==0)guardianSpawnAnim=.70f;}', 'if(state.buyOrUpgradeGuardian()){toast="СТРАЖ СУНДУКА • ур."+state.guardianLevel;guardianDead=false;guardianMaxHp=state.guardianMaxHp();guardianHp=guardianMaxHp;guardianX=cx(map.startCol)-Math.min(cellW,cellH)*.28f;guardianY=cy(map.startRow);guardianSpawnAnim=.70f;}')

# Remove obsolete per-tier rune symbol on workers.
s = sub_once(s, r'        int rune=state\.tierRunes\[w\.tier\.ordinal\(\)\];if\(rune>=0&&rune<RuneType\.values\(\)\.length&&state\.runeLevels\[rune\]>0\)drawRune\(d,w\.x\+s\*\.45f,w\.y-s\*\.60f,4\.4f\*ui,RuneType\.values\(\)\[rune\]\);\n', '', 'remove tier rune icon')

# HUD active artifact/rune badges.
s = replace_once(s,
'''        drawResource(d,section*3+5f*ui,y,0xFF67D7F2,"◆",state.diamond);\n    }''',
'''        drawResource(d,section*3+5f*ui,y,0xFF67D7F2,"◆",state.diamond);\n        drawActiveEffects(d);\n    }\n    private void drawActiveEffects(Draw d){float x=width-8f*ui,y=worldT+10f*ui;int n=0;for(int i=0;i<state.artifactActive.length;i++)if(state.artifactOwned(i)&&state.artifactActive[i]){ArtifactType a=ArtifactType.values()[i];x-=15f*ui;d.setColor(alpha(a.color,.28f));d.fillCircle(x,y,6f*ui);d.setColor(a.color);d.strokeWidth=1.2f*ui;d.strokeCircle(x,y,4f*ui);n++;}for(int i=0;i<state.runeActive.length;i++)if(state.runeIsActive(i)){x-=15f*ui;drawRune(d,x,y,3.4f*ui,RuneType.values()[i]);n++;if(n>10)break;}}''', 'active badges')

# Objectives replacing old simple methods.
s = sub_once(s, r'    private boolean levelObjectiveMet\(\).*?(?=\n\n    private void buyGlobal)', r'''    private void setupObjective(){
        if(state.depth==1)objectiveType=ObjectiveType.CLEAR_VEINS;else if(state.depth%10==0)objectiveType=ObjectiveType.BOSS_HUNT;else objectiveType=ObjectiveType.values()[Math.floorMod(state.depth+slot*3,5)];objectiveStartKills=state.enemiesDefeated;objectiveTarget=0;objectiveTreasureTarget=0;objectiveStarted=false;
        switch(objectiveType){case GUARDIAN->objectiveTarget=2+Math.min(2,state.depth/15);case DEMON_PURGE->objectiveTarget=3+Math.min(8,state.depth/3);case TREASURE->objectiveTreasureTarget=state.walletValue()+600L+state.depth*220L;default->{}}
    }
    private void updateObjective(){
        if(state.totalGnomes()<5)return;if(objectiveStarted)return;if(objectiveType==ObjectiveType.BOSS_HUNT){objectiveStarted=true;spawnBoss();}else if(objectiveType==ObjectiveType.DEMON_PURGE){objectiveStarted=true;EnemyType[] q=new EnemyType[objectiveTarget+1];for(int i=0;i<objectiveTarget;i++)q[i]=EnemyType.DEMON;q[q.length-1]=state.depth>=20?EnemyType.DEMON_KING:EnemyType.IMP_KING;openPortal(q);toast="ЗАДАНИЕ • ПЕРЕЖИТЬ НАШЕСТВИЕ";toastTime=2f;}}
    private boolean noLivingVeins(){for(Vein v:veins)if(!v.dead)return false;return true;}
    private boolean noHostiles(){if(portal!=null)return false;for(Mob m:mobs)if(!m.dead)return false;return true;}
    private boolean levelObjectiveMet(){return switch(objectiveType){case CLEAR_VEINS->noLivingVeins();case GUARDIAN->state.guardianLevel>=objectiveTarget&&!guardianDead;case TREASURE->state.walletValue()>=objectiveTreasureTarget;case DEMON_PURGE,BOSS_HUNT->objectiveStarted&&noHostiles();};}
    private String levelObjectiveShort(){return switch(objectiveType){case CLEAR_VEINS->"ЦЕЛЬ: ОЧИСТИТЬ ЖИЛЫ";case GUARDIAN->"ЦЕЛЬ: СТРАЖ ур."+objectiveTarget;case DEMON_PURGE->"ЦЕЛЬ: НАШЕСТВИЕ";case BOSS_HUNT->"ЦЕЛЬ: УБИТЬ БОССА";case TREASURE->"ЦЕЛЬ: КАПИТАЛ "+format(objectiveTreasureTarget);};}
    private String levelObjectiveToast(){return levelObjectiveShort();}
''', 'objectives')

# Level summary celebratory + carried gnomes.
s = replace_once(s,
'''        summaryEarned=state.levelEarnedValue;summaryInvested=state.levelInvestedValue;summaryWallet=state.walletValue();summaryCapital=state.transferCapital(cargoValue());summaryTransfer=state.transferAmount(cargoValue());''',
'''        summaryEarned=state.levelEarnedValue;summaryInvested=state.levelInvestedValue;summaryWallet=state.walletValue();summaryCapital=state.transferCapital(cargoValue());summaryTransfer=state.transferAmount(cargoValue());summaryCarry=state.carriedGnomesCount();''', 'summary carry')
s = sub_once(s, r'    private void drawLevelSummary\(Draw d\)\{.*?(?=\n\n    private void drawGameOver)', r'''    private void drawLevelSummary(Draw d){
        d.setColor(0xE6090705);d.fillRect(0,0,width,height);float cw=Math.min(width-30f*ui,360f*ui),l=(width-cw)/2f,t=height*.13f,b=height*.69f,p=Math.min(1f,summaryAnim/1.2f);
        for(int i=0;i<30;i++){float a=i*2.399f+summaryAnim*.3f,rr=(40f+(i%8)*19f)*ui*p,x=width/2+(float)Math.cos(a)*rr,y=t+58f*ui+(float)Math.sin(a)*rr*.55f;d.setColor(i%3==0?0x88FFD35A:i%3==1?0x8877D89A:0x88E77A55);d.fillCircle(x,y,(1.5f+i%3)*ui);}
        d.setColor(0xFF3A2516);d.fillRoundRect(l-4f*ui,t-4f*ui,l+cw+4f*ui,b+4f*ui,18f*ui);d.setColor(0xFF18130F);d.fillRoundRect(l,t,l+cw,b,15f*ui);d.setColor(0xFFF0B85A);d.fillRoundRect(l+22f*ui,t+5f*ui,l+cw-22f*ui,t+9f*ui,2f*ui);
        d.align=Draw.Align.CENTER;d.bold=true;d.textSize=25f*ui;d.setColor(0xFFFFD86B);d.text("ПОБЕДА!",width/2,t+43f*ui);d.textSize=11f*ui;d.setColor(0xFFF4EFE3);d.text("ГЛУБИНА "+state.depth+" ПОКОРЕНА",width/2,t+70f*ui);d.bold=false;d.textSize=7.3f*ui;d.setColor(0xFFC5B9A8);d.text(state.difficultyTitle()+" • "+levelObjectiveShort(),width/2,t+90f*ui);
        float x1=l+22f*ui,x2=l+cw-22f*ui,y=t+120f*ui,dy=31f*ui;d.align=Draw.Align.LEFT;d.textSize=7.6f*ui;d.setColor(0xFFC5B9A8);d.text("ЗАРАБОТАНО",x1,y);d.text("ВЛОЖЕНО",x1,y+dy);d.text("КАПИТАЛ",x1,y+dy*2);d.text("ДЕНЕГ ДАЛЬШЕ",x1,y+dy*3);d.text("ГНОМОВ ДАЛЬШЕ",x1,y+dy*4);
        d.align=Draw.Align.CENTER;d.bold=true;d.setColor(0xFFFFD56A);d.text(format(rolling(summaryEarned)),x2,y);d.text(format(rolling(summaryInvested)),x2,y+dy);d.text(format(rolling(summaryCapital)),x2,y+dy*2);d.setColor(0xFF7FDEA0);d.text(format(rolling(summaryTransfer))+"  ×"+one.format(state.carryRatio()),x2,y+dy*3);d.text("1 + "+summaryCarry,x2,y+dy*4);
        d.bold=false;d.textSize=7.2f*ui;d.setColor(0xFFA99E90);d.text("Один новый шахтёр + примерно половина старого отряда продолжат путь.",width/2,b-17f*ui);d.align=Draw.Align.LEFT;button(d,summaryOk,"В ГЛУБИНУ • УРОВЕНЬ "+(state.depth+1),summaryAnim>.75f,.78f);
    }
''', 'celebration summary')

# Better guardian visual feedback using runtime HP bar after existing method body.
# Insert immediately before drawWorker, keeping the existing detailed guardian renderer.
guard_bar = r'''
    private void drawGuardianHealth(Draw d,float x,float y,float s){if(guardianDead||guardianMaxHp<=0)return;float pct=Math.max(0,guardianHp/guardianMaxHp),bw=s*1.25f;d.setColor(0xCC101314);d.fillRoundRect(x-bw/2,y-s*.91f,x+bw/2,y-s*.82f,2f*ui);d.setColor(0xFF62BFD5);d.fillRoundRect(x-bw/2,y-s*.91f,x-bw/2+bw*pct,y-s*.82f,2f*ui);}
'''
s = replace_once(s, '\n    private void drawWorker(Draw d,Worker w){', guard_bar + '\n    private void drawWorker(Draw d,Worker w){', 'guardian hp helper')
# Ensure drawWorld calls hp too.
s = s.replace('if(state.guardianLevel>0&&!guardianDead)drawGuardian(d,guardianX,guardianY,Math.min(cellW,cellH)*.54f);', 'if(state.guardianLevel>0&&!guardianDead){float gs=Math.min(cellW,cellH)*.54f;drawGuardian(d,guardianX,guardianY,gs);drawGuardianHealth(d,guardianX,guardianY,gs);}')

# Improve guardian strike reach visually by basing spear attack on melee animation; existing renderer already uses guardianAttackAnim.
# Mob hit flash + imp fleeing animation cue: inject at start of drawImp.
s = s.replace('private void drawImp(Draw d,Mob m,float s){float hop=', 'private void drawImp(Draw d,Mob m,float s){float panic=m.flee>0?1f:0f;float hop=')
s = s.replace('float dir=m.goalCell>=0&&cx(map.col(m.goalCell))<m.x?-1:1;', 'float dir=m.goalCell>=0&&cx(map.col(m.goalCell))<m.x?-1:1;if(panic>0)hop+=Math.abs((float)Math.sin(elapsed*22+m.phase))*s*.10f;')

# Save final cave.
CAVE.write_text(s)

# ---------------------------------------------------------------------------
# Procedural runtime assets: simple music + three illustrated scene PNGs.
# ---------------------------------------------------------------------------
assets = ROOT/'assets'
(assets/'music').mkdir(parents=True, exist_ok=True)
(assets/'intro').mkdir(parents=True, exist_ok=True)

# loop-friendly mine theme: low fifths, soft bell notes and a faint mechanical pulse.
sr=22050;seconds=18;n=sr*seconds
notes=[110.0,146.83,164.81,146.83,123.47,164.81,146.83,110.0]
with wave.open(str(assets/'music/mine_loop.wav'),'wb') as wf:
    wf.setnchannels(1);wf.setsampwidth(2);wf.setframerate(sr)
    buf=bytearray()
    for i in range(n):
        t=i/sr;bar=int(t/2.25)%len(notes);f=notes[bar];phase=(t%2.25)/2.25
        drone=.20*math.sin(2*math.pi*f*t)+.11*math.sin(2*math.pi*(f*1.5)*t)
        bellf=[329.63,392.0,440.0,392.0][int(t/.5625)%4];env=math.exp(-5.5*(t%.5625));bell=.055*env*math.sin(2*math.pi*bellf*t)
        pulse=.018*math.sin(2*math.pi*55*t)*(1 if (t%.75)<.08 else 0)
        fade=min(1,t/.12,(seconds-t)/.12);v=max(-1,min(1,(drone+bell+pulse)*fade));buf+=struct.pack('<h',int(v*32767))
    wf.writeframes(buf)

# tiny RGB drawing helpers, no Pillow dependency.
def png_write(path,w,h,pix):
    raw=b''.join(b'\x00'+bytes(pix[y*w*3:(y+1)*w*3]) for y in range(h))
    def chunk(tag,data): return struct.pack('>I',len(data))+tag+data+struct.pack('>I',zlib.crc32(tag+data)&0xffffffff)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
def canvas(w,h,c): return bytearray(c*(w*h))
def put(p,w,h,x,y,c):
    if 0<=x<w and 0<=y<h:
        j=(y*w+x)*3;p[j:j+3]=bytes(c)
def rect(p,w,h,x0,y0,x1,y1,c):
    for y in range(max(0,y0),min(h,y1)):
        j=(y*w+max(0,x0))*3;span=max(0,min(w,x1)-max(0,x0));p[j:j+span*3]=bytes(c)*span
def circle(p,w,h,cx,cy,r,c):
    rr=r*r
    for y in range(max(0,cy-r),min(h,cy+r+1)):
        dx=int(math.sqrt(max(0,rr-(y-cy)*(y-cy))))
        rect(p,w,h,cx-dx,y,cx+dx+1,y+1,c)
def line(p,w,h,x0,y0,x1,y1,c,th=1):
    dx=abs(x1-x0);sx=1 if x0<x1 else -1;dy=-abs(y1-y0);sy=1 if y0<y1 else -1;err=dx+dy
    while True:
        circle(p,w,h,x0,y0,th,c)
        if x0==x1 and y0==y1: break
        e2=2*err
        if e2>=dy:err+=dy;x0+=sx
        if e2<=dx:err+=dx;y0+=sy

def gnome(p,w,h,x,y,scale,hat=(180,55,45),pick=True):
    circle(p,w,h,x,y+10*scale,12*scale,(53,38,29));rect(p,w,h,x-9*scale,y,x+9*scale,y+18*scale,(73,126,145));circle(p,w,h,x,y-8*scale,8*scale,(231,180,130));circle(p,w,h,x,y+2*scale,8*scale,(232,226,209));line(p,w,h,x-8*scale,y-14*scale,x,y-27*scale,hat,4*scale);line(p,w,h,x,y-27*scale,x+11*scale,y-15*scale,hat,4*scale);if_pick=pick
    if if_pick: line(p,w,h,x+5*scale,y+4*scale,x+25*scale,y-10*scale,(126,81,43),2*scale);line(p,w,h,x+20*scale,y-16*scale,x+28*scale,y-4*scale,(187,196,202),2*scale)

def make_scene(kind,path):
    w,h=360,210;p=canvas(w,h,(19,18,16));random.seed(100+kind)
    for i in range(70):
        x=random.randrange(w);y=random.randrange(h);r=random.randrange(2,9);circle(p,w,h,x,y,r,(34+random.randrange(12),32+random.randrange(9),28+random.randrange(8)))
    if kind==0:
        rect(p,w,h,45,130,315,190,(62,44,31));circle(p,w,h,180,118,70,(44,37,31));rect(p,w,h,145,132,215,158,(91,58,31));circle(p,w,h,180,146,16,(242,125,43));circle(p,w,h,180,142,9,(255,210,83));gnome(p,w,h,105,126,1);gnome(p,w,h,252,127,1,(210,151,55),False);rect(p,w,h,260,150,316,182,(104,63,30));rect(p,w,h,270,142,306,153,(190,139,53))
    elif kind==1:
        for x,y,c in [(48,52,(190,205,216)),(292,65,(241,183,49)),(310,148,(104,218,238))]:circle(p,w,h,x,y,22,c);circle(p,w,h,x,y,11,tuple(min(255,v+30) for v in c))
        gnome(p,w,h,112,125,1);gnome(p,w,h,190,112,1,(211,72,54));gnome(p,w,h,262,136,1,(82,150,205));rect(p,w,h,148,157,215,190,(102,61,31));rect(p,w,h,158,148,205,160,(205,151,49))
    else:
        circle(p,w,h,276,105,50,(82,31,95));circle(p,w,h,276,105,36,(160,55,105));circle(p,w,h,276,105,22,(24,14,27));
        for k in range(4):
            x=250+k*18;y=103+(k%2)*12;circle(p,w,h,x,y,10,(180,54,48));line(p,w,h,x-6,y-8,x-12,y-19,(230,210,160),2);line(p,w,h,x+6,y-8,x+12,y-19,(230,210,160),2)
        gnome(p,w,h,83,129,1,(207,65,47));gnome(p,w,h,137,137,1,(80,145,205));gnome(p,w,h,181,126,1,(217,160,59))
    png_write(path,w,h,p)

make_scene(0,assets/'intro/gnome_home.png');make_scene(1,assets/'intro/gnome_mine.png');make_scene(2,assets/'intro/gnome_enemies.png')
print('GNOMES v0.5 pass applied')
