package com.enhort.gnomes.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.enhort.gnomes.GnomesGame;
import com.enhort.gnomes.GameAudio;
import com.enhort.gnomes.draw.Draw;
import com.enhort.gnomes.ui.UiTheme;
import com.enhort.gnomes.game.model.ArtifactType;
import com.enhort.gnomes.game.model.EnemyType;
import com.enhort.gnomes.game.model.GnomeTier;
import com.enhort.gnomes.game.model.HazardType;
import com.enhort.gnomes.game.model.RockType;
import com.enhort.gnomes.game.model.RuneType;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Second-generation mine scene. Unlike the first prototype, the cave is a real tunnel graph: workers and
 * enemies route through it, ore sits in the walls, mining has anticipation/impact/recoil, rocks acquire
 * progressive cracks and missing chunks, and every enemy family has its own locomotion language.
 */
public final class CaveScreen extends ScreenAdapter {
    private enum WorkerAction { IDLE, WALK, CARRY, MINE, FIGHT, STUNNED }
    private enum PriorityKind { NONE, VEIN, MOB, HAZARD, POINT }
    private enum Tab { GNOMES, UPGRADES, ARTIFACTS, RUNES }
    private enum ObjectiveType { ASCEND_GNOME, CLEAR_VEINS, GUARDIAN, DEMON_PURGE, BOSS_HUNT, TREASURE }
    private enum LevelEvent { NONE, RICH_VEINS, QUIET, IMP_SWARM, FLOODED, ANCIENT, UNSTABLE }

    private static final class Box {
        float l,t,r,b;
        Box() {}
        Box(float l,float t,float r,float b){set(l,t,r,b);}
        void set(float l,float t,float r,float b){this.l=l;this.t=t;this.r=r;this.b=b;}
        boolean hit(float x,float y){return x>=l&&x<=r&&y>=t&&y<=b;}
        float cx(){return(l+r)*.5f;} float cy(){return(t+b)*.5f;}
    }

    private static final class Worker {
        final int visualId;
        GnomeTier tier;
        float x,y,vx,vy;
        float phase, walkCycle, swing, attackCooldown, stun, routeRetry, spawn=.52f, charm, allyCooldown, react;
        boolean hitApplied;
        WorkerAction action=WorkerAction.IDLE;
        int[] path=new int[0];
        int pathIndex;
        int goalCell=-1;
        Vein vein;
        Mob mob;
        double cargoStone,cargoSilver,cargoGold,cargoDiamond;
        Worker(int visualId,GnomeTier tier,float x,float y,float phase){this.visualId=visualId;this.tier=tier;this.x=x;this.y=y;this.phase=phase;}
        double cargo(){return cargoStone+cargoSilver+cargoGold+cargoDiamond;}
        boolean hasCargo(){return cargo()>.001;}
        void clearCargo(){cargoStone=cargoSilver=cargoGold=cargoDiamond=0;}
        void add(RockType.Material m,double n){switch(m){case STONE->cargoStone+=n;case SILVER->cargoSilver+=n;case GOLD->cargoGold+=n;case DIAMOND->cargoDiamond+=n;}}
    }

    private static final class Vein {
        final RockType type;
        final int cell, side, seed;
        final float x,y,r,maxHp;
        float hp, hitFlash, death, spawn=.38f;
        boolean dead;
        Vein(RockType type,int cell,int side,int seed,float x,float y,float r,float hp){this.type=type;this.cell=cell;this.side=side;this.seed=seed;this.x=x;this.y=y;this.r=r;this.maxHp=hp;this.hp=hp;}
    }

    private static final class Mob {
        final EnemyType type;
        float x,y,hp,maxHp,phase,walkCycle,attack,attackCooldown,summonCooldown=5f,routeTimer,spawn=.65f,flee,hitFlash;
        int[] path=new int[0]; int pathIndex; int goalCell=-1;
        Worker target;
        boolean dead,enraged,ghostSteals,retreating;
        Mob(EnemyType type,float x,float y,float phase){this.type=type;this.x=x;this.y=y;this.phase=phase;this.maxHp=type.hp;this.hp=maxHp;}
    }

    private static final class Fx {
        float x,y,vx,vy,life,maxLife,size;
        int color;
        boolean spark;
        Fx(float x,float y,float vx,float vy,float life,float size,int color,boolean spark){this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.life=this.maxLife=life;this.size=size;this.color=color;this.spark=spark;}
    }

    private static final class Notice {
        final String text; final int color; final float maxLife; float life;
        Notice(String text,int color,float life){this.text=text;this.color=color;this.life=this.maxLife=life;}
    }

    private static final class CaveHazard {
        final HazardType type; final int cell; final float x,y,r;
        float age,rubbleHp,rubbleMaxHp; boolean fired,obstacleActive,cleared;
        CaveHazard(HazardType type,int cell,float x,float y,float r){this.type=type;this.cell=cell;this.x=x;this.y=y;this.r=r;}
    }

    private static final class Portal {
        final int cell; final float x,y; final EnemyType[] queue;
        int next; float age,spawnTimer=.52f,closeAge;
        Portal(int cell,float x,float y,EnemyType[] queue){this.cell=cell;this.x=x;this.y=y;this.queue=queue;}
        boolean done(){return next>=queue.length&&closeAge>1.0f;}
    }

    private final GnomesGame game;
    private final int slot;
    private final GameState state;
    private final Random random=new Random();
    private final DecimalFormat one=new DecimalFormat("0.0");
    private final List<Worker> workers=new ArrayList<>();
    private final List<Vein> veins=new ArrayList<>();
    private final List<Mob> mobs=new ArrayList<>();
    private final List<Fx> fx=new ArrayList<>();
    private final List<CaveHazard> hazards=new ArrayList<>();
    private final List<Mob> pendingMobs=new ArrayList<>();
    private final List<Notice> notices=new ArrayList<>();
    private Portal portal;

    private CaveMap map;
    private float width,height,ui;
    private float worldL,worldT,worldR,worldB,cellW,cellH;
    private float elapsed,enemyTimer=13f,hazardTimer=18f,saveTimer,levelClearTimer=-1f,screenShake;
    private boolean speedHeld;
    private PriorityKind priorityKind=PriorityKind.NONE;
    private Vein priorityVein;
    private Mob priorityMob;
    private CaveHazard priorityHazard;
    private int priorityCell=-1;
    private float priorityX,priorityY,priorityPulse;
    private boolean objectiveReminderShown;
    private boolean worldTouchActive,longPressEligible,longPressHandled;
    private float worldTouchStarted,worldTouchX,worldTouchY;
    private boolean levelSummary,gameOver;
    private float summaryAnim,guardianAttackAnim,guardianSpawnAnim,guardianHitFlash,guardianCooldown;
    private float guardianX,guardianY,guardianHp,guardianMaxHp;
    private boolean guardianDead;
    private Mob guardianTarget;
    private int[] guardianPath=new int[0]; private int guardianPathIndex,guardianGoal=-1;
    private long summaryEarned,summaryInvested,summaryWallet,summaryCapital,summaryTransfer;
    private int summaryCarry;
    private ObjectiveType objectiveType=ObjectiveType.CLEAR_VEINS;
    private int objectiveTarget,objectiveStartKills;
    private long objectiveTreasureTarget;
    private boolean objectiveStarted;
    private boolean buyHold; private float buyHoldStarted,buyRepeat;
    private LevelEvent levelEvent=LevelEvent.NONE;
    private int unlockTier=-1; private float unlockAnim,bossIntro; private EnemyType pendingBoss;
    private final Box summaryOk=new Box(),gameOverOk=new Box(),unlockOk=new Box();
    private Tab tab=Tab.GNOMES;
    private int selectedTier,selectedArtifact,selectedRune,runeTarget;
    private int nextWorkerId=1;
    private String toast="Шахта готова";
    private float toastTime=2.8f;

    private final Box back=new Box(),speed=new Box();
    private final Box[] tabs={new Box(),new Box(),new Box(),new Box()};
    private final Box left=new Box(),right=new Box(),primary=new Box(),secondary=new Box(),tertiary=new Box(),quaternary=new Box();

    public CaveScreen(GnomesGame game,int slot){
        this.game=game; this.slot=slot; this.state=game.saves.load(slot);
        random.setSeed(0xC0FFEE1234L+slot*31L+state.depth*997L);
    }

    @Override public void show(){
        Gdx.input.setCatchKey(Input.Keys.BACK,true);
        Gdx.input.setInputProcessor(new InputAdapter(){
            @Override public boolean keyDown(int keycode){if(keycode==Input.Keys.BACK||keycode==Input.Keys.ESCAPE){saveNow();game.openMenu();return true;}return false;}
            @Override public boolean touchDown(int sx,int sy,int pointer,int button){
                float x=sx,y=sy;
                if(unlockTier>=0){if(unlockOk.hit(x,y)||unlockAnim>.35f){unlockTier=-1;game.audio.play(GameAudio.Sfx.UI,.55f);}return true;}
                if(levelSummary){if(summaryOk.hit(x,y)&&summaryAnim>.75f)finishLevelTransition();return true;}
                if(gameOver){if(gameOverOk.hit(x,y)){saveNow();game.openMenu();}return true;}
                if(speed.hit(x,y)){speedHeld=true;game.audio.play(GameAudio.Sfx.UI,.55f);return true;}
                if(tab==Tab.GNOMES&&selectedTier==0&&primary.hit(x,y)){buyHold=true;buyHoldStarted=elapsed;buyRepeat=.32f;return handleTap(x,y);}
                if(y>=worldT&&y<=worldB){
                    worldTouchActive=true;longPressEligible=true;longPressHandled=false;worldTouchStarted=elapsed;worldTouchX=x;worldTouchY=y;
                    return handleWorldTap(x,y);
                }
                return handleTap(x,y);
            }
            @Override public boolean touchUp(int sx,int sy,int pointer,int button){speedHeld=false;buyHold=false;worldTouchActive=false;longPressEligible=false;return true;}
            @Override public boolean touchDragged(int sx,int sy,int pointer){
                if(!speed.hit(sx,sy))speedHeld=false;
                if(buyHold&&!primary.hit(sx,sy))buyHold=false;
                if(worldTouchActive&&distance(worldTouchX,worldTouchY,sx,sy)>16f*ui)longPressEligible=false;
                return true;
            }
        });
    }

    @Override public void resize(int w,int h){
        width=w;height=h;
        // UI scale is constrained by both dimensions. Wide/short screens no longer inflate controls until
        // they collide with each other, which was a surprisingly ambitious career choice for a button.
        ui=Math.max(.70f,Math.min(w/420f,h/820f));
        game.draw.resize(w,h);
        worldL=0;worldR=w;worldT=80f*ui;
        float panelH=Math.min(238f*ui,Math.max(190f*ui,h*.30f));
        worldB=h-panelH;
        if(worldB<worldT+210f*ui)worldB=worldT+210f*ui;
        layoutUi();
        generateDepth(false);
    }

    private void layoutUi(){
        back.set(8f*ui,9f*ui,48f*ui,47f*ui);
        float panel=worldB;
        float tabH=Math.min(36f*ui,Math.max(29f*ui,(height-panel)*.16f));
        for(int i=0;i<4;i++)tabs[i].set(i*width/4f,panel,(i+1)*width/4f,panel+tabH);

        float speedH=Math.min(46f*ui,Math.max(36f*ui,(height-panel)*.22f));
        speed.set(12f*ui,height-speedH-9f*ui,width-12f*ui,height-9f*ui);

        float contentT=panel+tabH+5f*ui;
        float navH=Math.min(38f*ui,Math.max(30f*ui,(speed.t-contentT)*.27f));
        left.set(9f*ui,contentT+3f*ui,42f*ui,contentT+3f*ui+navH);
        right.set(width-42f*ui,contentT+3f*ui,width-9f*ui,contentT+3f*ui+navH);

        float actionsTop=Math.max(contentT+58f*ui,contentT+navH+7f*ui);
        float actionsBottom=speed.t-7f*ui;
        float gap=7f*ui;
        float side=9f*ui;
        float colGap=7f*ui;
        float rowH=Math.max(26f*ui,(actionsBottom-actionsTop-gap)*.5f);
        float mid=(width-colGap)*.5f;
        primary.set(side,actionsTop,mid,Math.min(actionsBottom,actionsTop+rowH));
        secondary.set(mid+colGap,actionsTop,width-side,Math.min(actionsBottom,actionsTop+rowH));
        float row2=Math.min(actionsBottom,actionsTop+rowH+gap);
        tertiary.set(side,row2,mid,actionsBottom);
        quaternary.set(mid+colGap,row2,width-side,actionsBottom);
        float ow=Math.min(width-54f*ui,330f*ui),ox=(width-ow)/2f;
        summaryOk.set(ox,height*.69f,ox+ow,height*.69f+48f*ui);
        gameOverOk.set(ox,height*.66f,ox+ow,height*.66f+48f*ui);
        unlockOk.set(ox,height*.66f,ox+ow,height*.66f+48f*ui);
    }

    private void generateDepth(boolean advance){
        if(width<=0||height<=0)return;
        if(advance){state.depth++;state.depthProgress=0;saveNow();}
        mobs.clear();pendingMobs.clear();hazards.clear();fx.clear();veins.clear();portal=null;clearPriority(false);objectiveReminderShown=false;levelSummary=false;gameOver=false;summaryAnim=0;objectiveStarted=false;buyHold=false;
        int cols=9;
        int rows=Math.max(9,Math.min(13,Math.round((worldB-worldT)/(width/cols))));
        long seed=0x9E3779B97F4A7C15L^(long)slot*0xBF58476D1CE4E5B9L^(long)state.depth*0x94D049BB133111EBL;
        map=new CaveMap(cols,rows,seed);
        cellW=(worldR-worldL)/map.cols; cellH=(worldB-worldT)/map.rows;
        random.setSeed(seed^0x1234FEDCBA98765L);
        levelEvent=pickLevelEvent(seed);
        buildVeins();
        syncWorkers(true);
        guardianX=cx(map.startCol)-Math.min(cellW,cellH)*.28f;guardianY=cy(map.startRow);guardianMaxHp=state.guardianMaxHp();guardianHp=guardianMaxHp;guardianDead=state.guardianLevel<=0;guardianTarget=null;guardianPath=new int[0];guardianPathIndex=0;guardianGoal=-1;
        enemyTimer=Math.max(7f,18f-state.depth*.18f)+random.nextFloat()*7f;
        hazardTimer=Math.max(8f,17f-state.depth*.20f)+random.nextFloat()*10f;
        if(levelEvent==LevelEvent.QUIET)enemyTimer*=2.2f;
        if(levelEvent==LevelEvent.IMP_SWARM)enemyTimer=Math.min(enemyTimer,7.5f);
        if(levelEvent==LevelEvent.FLOODED||levelEvent==LevelEvent.UNSTABLE)hazardTimer*=.62f;
        levelClearTimer=-1f;setupObjective();
        toast="ГЛУБИНА "+state.depth+(map.style==CaveMap.Style.RING?" • КОЛЬЦЕВАЯ ШАХТА":"")+" • "+levelObjectiveShort();toastTime=2.8f;
        if(levelEvent!=LevelEvent.NONE)addNotice("СОБЫТИЕ • "+levelEventTitle(),levelEventColor(),3.2f);
    }

    private void buildVeins(){
        List<Integer> candidates=new ArrayList<>();
        int start=map.index(map.startCol,map.startRow);
        // Dead ends remain attractive ore pockets, but every level shuffles the full mine so deposits do not
        // appear in the same visual slots over and over.
        candidates.addAll(map.deadEnds());
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){int idx=map.index(c,r);if(idx!=start&&!candidates.contains(idx))candidates.add(idx);}
        java.util.Collections.shuffle(candidates,random);
        int target=Math.min(candidates.size(),Math.max(11,12+state.depth/4));
        for(int i=0;i<target;i++){
            int cell=candidates.get(i),c=map.col(cell),r=map.row(cell);
            int side=map.preferredSolidSide(c,r,random.nextInt());
            float pocket=Math.min(cellW,cellH)*(.22f+random.nextFloat()*.12f);
            float tangent=(random.nextFloat()-.5f)*Math.min(cellW,cellH)*.28f;
            float x=cx(c)+CaveMap.dx(side)*pocket+(side==CaveMap.N||side==CaveMap.S?tangent:0);
            float y=cy(r)+CaveMap.dy(side)*pocket+(side==CaveMap.E||side==CaveMap.W?tangent:0);
            RockType type=chooseRockType(i);
            float sizeJitter=.155f+random.nextFloat()*.145f;
            float typeScale=switch(type){case OBSIDIAN->1.10f;case ANCIENT_CRYSTAL->1.16f;case DIAMOND->.92f;default->1f;};
            float radius=Math.min(cellW,cellH)*sizeJitter*typeScale;
            float hp=type.hp*(1f+Math.max(0,state.depth-1)*.055f);
            Vein v=new Vein(type,cell,side,random.nextInt(),x,y,radius,hp);v.spawn=.20f+random.nextFloat()*.38f;veins.add(v);
        }
    }

    private RockType chooseRockType(int salt){
        float r=random.nextFloat();int d=state.depth;
        float rich=levelEvent==LevelEvent.RICH_VEINS?.14f:0f,ancient=levelEvent==LevelEvent.ANCIENT?.12f:0f;
        if(d>=18&&r<.05f+ancient)return RockType.ANCIENT_CRYSTAL;
        if(d>=12&&r<.13f+ancient*.55f)return RockType.OBSIDIAN;
        if(d>=9&&r<.22f+rich*.50f)return RockType.DIAMOND;
        if(d>=5&&r<.39f+rich)return RockType.GOLD;
        if(d>=2&&r<.61f+rich*.45f)return RockType.SILVER;
        return RockType.STONE;
    }

    private float levelYieldMultiplier(){return levelEvent==LevelEvent.RICH_VEINS?1.75f:levelEvent==LevelEvent.ANCIENT?1.18f:1f;}
    private LevelEvent pickLevelEvent(long seed){
        if(state.depth<=2)return LevelEvent.NONE;
        float q=hash01(seed^0xA0761D6478BD642FL);
        if(q<.28f)return LevelEvent.NONE;
        LevelEvent[] pool={LevelEvent.RICH_VEINS,LevelEvent.QUIET,LevelEvent.IMP_SWARM,LevelEvent.FLOODED,LevelEvent.ANCIENT,LevelEvent.UNSTABLE};
        return pool[Math.floorMod((int)(seed^(seed>>>32)),pool.length)];
    }
    private String levelEventTitle(){return switch(levelEvent){case NONE->"ОБЫЧНАЯ СМЕНА";case RICH_VEINS->"БОГАТЫЕ ЖИЛЫ";case QUIET->"ТИХАЯ ШАХТА";case IMP_SWARM->"НАШЕСТВИЕ БЕСОВ";case FLOODED->"ЗАТОПЛЕННЫЕ ХОДЫ";case ANCIENT->"ДРЕВНЯЯ ПЕЩЕРА";case UNSTABLE->"НЕСТАБИЛЬНЫЙ ПЛАСТ";};}
    private int levelEventColor(){return switch(levelEvent){case RICH_VEINS->0xFFFFD35A;case QUIET->0xFF8BC6A4;case IMP_SWARM->0xFFE66B58;case FLOODED->0xFF67C7D0;case ANCIENT->0xFFC18BE8;case UNSTABLE->0xFFD39A70;default->0xFFB8C1C7;};}

    private void syncWorkers(boolean resetPositions){
        if(resetPositions)workers.clear();
        for(int ti=0;ti<GnomeTier.values().length;ti++){
            GnomeTier tier=GnomeTier.values()[ti];int want=state.tierCounts[ti],have=0;
            for(Worker w:workers)if(w.tier==tier)have++;
            while(have<want){spawnWorker(tier);have++;}
            if(have>want){for(Iterator<Worker>it=workers.iterator();it.hasNext()&&have>want;){if(it.next().tier==tier){it.remove();have--;}}}
        }
    }

    private void spawnWorker(GnomeTier tier){
        float x=cx(map.startCol)+(random.nextFloat()-.5f)*cellW*.22f;
        float y=cy(map.startRow)+(random.nextFloat()-.5f)*cellH*.18f;
        workers.add(new Worker(nextWorkerId++,tier,x,y,random.nextFloat()*6.283f));
    }

    @Override public void render(float rawDelta){
        float real=Math.min(.05f,rawDelta);
        elapsed+=real;
        update(real,speedHeld?4f:1f);
        Draw d=game.draw;d.beginFrame();
        if(screenShake>0){float sx=(float)Math.sin(elapsed*71f)*screenShake,sy=(float)Math.cos(elapsed*53f)*screenShake*.55f;d.save();d.translate(sx,sy);drawWorld(d);d.restore();}
        else drawWorld(d);
        drawHud(d);drawPanel(d);drawNoticeFeed(d);drawToast(d);drawBossPrelude(d);if(unlockTier>=0)drawTierUnlock(d);if(levelSummary)drawLevelSummary(d);if(gameOver)drawGameOver(d);d.endFrame();
    }

    private void update(float dt,float workerTimeScale){
        if(map==null)return;
        if(toastTime>0)toastTime-=dt;
        priorityPulse+=dt;
        for(Iterator<Notice>it=notices.iterator();it.hasNext();){Notice n=it.next();n.life-=dt;if(n.life<=0)it.remove();}
        for(Worker w:workers)w.react=Math.max(0,w.react-dt);
        guardianAttackAnim=Math.max(0,guardianAttackAnim-dt);guardianSpawnAnim=Math.max(0,guardianSpawnAnim-dt);guardianHitFlash=Math.max(0,guardianHitFlash-dt);
        if(screenShake>0)screenShake=Math.max(0,screenShake-dt*18f*ui);
        checkLongPress();updateBuyHold(dt);
        updateBossPrelude(dt);
        updateMusicMood();
        if(unlockTier>=0){unlockAnim=Math.min(2f,unlockAnim+dt);updateFx(dt);return;}
        if(levelSummary){summaryAnim=Math.min(3f,summaryAnim+dt);updateFx(dt);return;}
        if(gameOver){updateFx(dt);return;}

        updateVeins(dt);updatePortal(dt);updateGuardian(dt);updateMobs(dt);updateHazards(dt);updateWorkers(dt*workerTimeScale);updateFx(dt);updateObjective();

        enemyTimer-=dt;hazardTimer-=dt;saveTimer+=dt;
        if(state.totalGnomes()>=5&&enemyTimer<=0&&portal==null&&pendingBoss==null){spawnEnemyWave();enemyTimer=Math.max(7f,24f-state.depth*.28f)+random.nextFloat()*9f;if(levelEvent==LevelEvent.QUIET)enemyTimer*=2.1f;if(levelEvent==LevelEvent.IMP_SWARM)enemyTimer*=.58f;}
        if(hazardTimer<=0){spawnHazard();hazardTimer=Math.max(8f,21f-state.depth*.22f)+random.nextFloat()*15f;if(levelEvent==LevelEvent.FLOODED||levelEvent==LevelEvent.UNSTABLE)hazardTimer*=.60f;}
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

    private void checkLongPress(){
        if(worldTouchActive&&longPressEligible&&!longPressHandled&&elapsed-worldTouchStarted>=.58f){
            longPressHandled=true;clearPriority(true);resetWorkerRoutes();game.audio.vibrate(24);
        }
    }

    private void updateVeins(float dt){
        float suppression=state.regenSuppression();
        for(Vein v:veins){
            v.hitFlash=Math.max(0,v.hitFlash-dt);v.spawn=Math.max(0,v.spawn-dt);
            if(v.dead){v.death+=dt;continue;}
            if(v.type.regenPerSecond>0&&v.hp<v.maxHp){float regen=v.type.regenPerSecond*(1-suppression)*(1+state.depth*.02f);v.hp=Math.min(v.maxHp,v.hp+regen*dt);}
        }
    }

    private void updateGuardian(float dt){
        if(state.guardianLevel<=0||guardianDead)return;
        guardianMaxHp=Math.max(guardianMaxHp,state.guardianMaxHp());
        guardianHp=Math.min(guardianMaxHp,guardianHp);
        guardianCooldown-=dt;
        float hx=cx(map.startCol)-Math.min(cellW,cellH)*.28f;
        float hy=cy(map.startRow);
        float leash=Math.min(cellW,cellH)*1.75f;
        Mob best=null;
        float bd=Float.MAX_VALUE;
        for(int pass=0;pass<2&&best==null;pass++){
            for(Mob m:mobs){
                if(m.dead||m.type==EnemyType.SUCCUBUS||m.type==EnemyType.GHOST)continue;
                boolean thief=m.type==EnemyType.IMP;
                if((pass==0)!=thief)continue;
                float q=dist2(hx,hy,m.x,m.y);
                if(q<leash*leash&&q<bd){bd=q;best=m;}
            }
        }
        guardianTarget=best;
        float speed=58f*ui*(1f+Math.min(.45f,state.guardianLevel*.04f));
        if(best==null){
            moveGuardianRouted(map.index(map.startCol,map.startRow),hx,hy,speed,dt);
            return;
        }
        float di=distance(guardianX,guardianY,best.x,best.y);
        float reach=Math.min(cellW,cellH)*.34f;
        if(di>reach){
            moveGuardianRouted(cellFor(best.x,best.y),best.x,best.y,speed,dt);
            return;
        }
        guardianPath=new int[0];guardianPathIndex=0;guardianGoal=-1;
        if(guardianCooldown<=0){
            guardianCooldown=state.guardianAttackInterval();
            guardianAttackAnim=.34f;
            best.hp-=state.guardianDamage();
            best.hitFlash=.16f;
            if(best.type.isImp())best.flee=.9f;
            spawnSparks(best.x,best.y,0xFFFFD873,4);
        }
    }

    private void moveGuardianRouted(int goal,float exactX,float exactY,float speed,float dt){
        int cur=cellFor(guardianX,guardianY);
        if(cur==goal){
            guardianPath=new int[0];guardianPathIndex=0;guardianGoal=-1;
            moveGuardianDirect(exactX,exactY,speed,dt);
            return;
        }
        if(guardianGoal!=goal||guardianPath.length==0||guardianPathIndex>=guardianPath.length){
            guardianGoal=goal;
            guardianPath=map.path(cur,goal);
            guardianPathIndex=Math.min(1,guardianPath.length);
        }
        if(guardianPath.length==0||guardianPathIndex>=guardianPath.length)return;
        int node=guardianPath[guardianPathIndex];
        if(map.isBlocked(node)){guardianPath=new int[0];guardianPathIndex=0;guardianGoal=-1;return;}
        float tx=cx(map.col(node)),ty=cy(map.row(node));
        float dx=tx-guardianX,dy=ty-guardianY,di=len(dx,dy);
        if(di<2.5f*ui){guardianX=tx;guardianY=ty;guardianPathIndex++;return;}
        float step=Math.min(di,speed*dt);
        guardianX+=dx/di*step;guardianY+=dy/di*step;
    }

    private void moveGuardianDirect(float tx,float ty,float speed,float dt){
        float dx=tx-guardianX,dy=ty-guardianY,di=len(dx,dy);
        if(di<1f)return;
        float step=Math.min(di,speed*dt);
        guardianX+=dx/di*step;guardianY+=dy/di*step;
    }

    private void updateWorkers(float dt){
        int defendersLeft=state.guardianLevel>0?guardianDefenderQuota():workers.size();
        int[] veinLoad=new int[veins.size()];
        for(Worker worker:workers){int vi=veinIndex(worker.vein);if(vi>=0&&!worker.vein.dead&&!map.isBlocked(worker.vein.cell))veinLoad[vi]++;}

        for(int i=workers.size()-1;i>=0;i--){
            Worker w=workers.get(i);
            w.spawn=Math.max(0,w.spawn-dt);w.attackCooldown-=dt;w.allyCooldown-=dt;w.charm=Math.max(0,w.charm-dt);if(w.swing>0)w.swing=Math.max(0,w.swing-dt);if(w.stun>0)w.stun-=dt;if(w.routeRetry>0)w.routeRetry-=dt;
            if(w.spawn>0){w.action=WorkerAction.IDLE;w.vx=w.vy=0;continue;}
            if(w.stun>0){w.action=WorkerAction.STUNNED;w.vx=w.vy=0;continue;}
            if(w.charm>0){fightAlly(w,dt);continue;}

            // Direct player orders always win. No ambient rubble job is allowed to hijack them.
            if(priorityKind==PriorityKind.VEIN&&priorityVein!=null&&!priorityVein.dead){w.mob=null;w.vein=priorityVein;mine(w,priorityVein,dt);continue;}
            if(priorityKind==PriorityKind.MOB&&priorityMob!=null&&!priorityMob.dead){w.vein=null;w.mob=priorityMob;fight(w,priorityMob,dt);continue;}
            if(priorityKind==PriorityKind.HAZARD&&priorityHazard!=null&&!priorityHazard.cleared&&priorityHazard.obstacleActive){w.vein=null;w.mob=null;clearCollapse(w,priorityHazard,dt);continue;}
            if(priorityKind==PriorityKind.POINT&&priorityCell>=0){w.vein=null;w.mob=null;moveToPriorityPoint(w,dt);continue;}

            Mob enemy=null;if(defendersLeft>0)enemy=nearestMob(w.x,w.y);
            if(enemy!=null){defendersLeft--;if(w.mob!=enemy)w.react=.34f;w.mob=enemy;w.vein=null;fight(w,enemy,dt);continue;}

            w.mob=null;
            float cap=w.tier.cargoCapacity*state.carryMultiplier(w.tier.ordinal());
            if(w.hasCargo()&&w.cargo()>=cap*.92){carryHome(w,dt);continue;}

            int old=veinIndex(w.vein);
            boolean invalid=old<0||w.vein.dead||map.isBlocked(w.vein.cell);
            if(!invalid&&veinLoad[old]>1&&hasLowerLoadReachableVein(w,veinLoad,veinLoad[old]))invalid=true;
            if(invalid){
                if(old>=0&&old<veinLoad.length&&veinLoad[old]>0)veinLoad[old]--;
                w.vein=null;w.path=new int[0];w.pathIndex=0;w.goalCell=-1;
            }
            if(w.vein==null){
                w.vein=chooseVein(w,veinLoad);
                int ni=veinIndex(w.vein);if(ni>=0)veinLoad[ni]++;
            }
            if(w.vein!=null)mine(w,w.vein,dt); else if(w.hasCargo())carryHome(w,dt); else {w.action=WorkerAction.IDLE;w.vx=w.vy=0;}
        }
    }

    private CaveHazard firstActiveCollapse(){for(CaveHazard h:hazards)if(h.type==HazardType.COLLAPSE&&h.obstacleActive&&!h.cleared)return h;return null;}

    private void clearCollapse(Worker w,CaveHazard h,float dt){
        int approach=collapseApproachCell(w,h);
        if(approach<0){w.action=WorkerAction.IDLE;w.vx=w.vy=0;w.path=new int[0];w.pathIndex=0;w.goalCell=-1;w.routeRetry=.22f;return;}
        if(!atCell(w,approach)){
            w.action=WorkerAction.WALK;
            if(w.goalCell!=approach||w.path.length==0||w.pathIndex>=w.path.length){
                w.goalCell=approach;w.path=map.path(cellFor(w.x,w.y),approach);w.pathIndex=Math.min(1,w.path.length);
            }
            followWorker(w,moveSpeed(w)*.82f,dt);return;
        }
        // The adjacent cell is the work position. Do not make the worker creep into the blocked cell.
        w.path=new int[0];w.pathIndex=0;w.goalCell=-1;
        w.action=WorkerAction.MINE;w.vx=h.x<w.x?-2f:2f;w.vy=0;
        if(w.attackCooldown<=0&&w.swing<=0){w.swing=.58f;w.hitApplied=false;w.attackCooldown=.62f;}
        float p=w.swing<=0?1f:1f-w.swing/.58f;
        if(w.swing>0&&!w.hitApplied&&p>=.57f){
            w.hitApplied=true;float damage=Math.max(1f,w.tier.miningPower*state.tierPowerMultiplier(w.tier.ordinal())*state.miningMultiplier(w.tier.ordinal())*.55f);
            h.rubbleHp-=damage;spawnSparks(h.x,h.y,0xFF9B8A78,workers.size()>80?1:3);
            if(h.rubbleHp<=0){w.swing=0;w.attackCooldown=0;w.vx=w.vy=0;finishCollapse(h,true);}
        }
    }

    private int collapseApproachCell(Worker w,CaveHazard h){
        int hc=map.col(h.cell),hr=map.row(h.cell),start=cellFor(w.x,w.y),best=-1,bestLen=Integer.MAX_VALUE;
        int[] dirs={CaveMap.N,CaveMap.E,CaveMap.S,CaveMap.W};
        for(int dir:dirs){
            if(!map.connected(hc,hr,dir))continue;
            int nc=hc+CaveMap.dx(dir),nr=hr+CaveMap.dy(dir);if(!map.inside(nc,nr))continue;
            int cell=map.index(nc,nr);if(map.isBlocked(cell))continue;
            int[] route=map.path(start,cell);if(route.length>0&&route.length<bestLen){best=cell;bestLen=route.length;}
        }
        return best;
    }

    private int guardianDefenderQuota(){
        if(workers.isEmpty())return 0;
        float fraction=.18f/(1f+state.guardianLevel*.22f);
        return Math.max(1,Math.min(12,(int)Math.ceil(workers.size()*fraction)));
    }

    private void moveToPriorityPoint(Worker w,float dt){
        if(!atCell(w,priorityCell)){
            w.action=WorkerAction.WALK;routeWorker(w,priorityCell);followWorker(w,moveSpeed(w),dt);return;
        }
        float a=w.visualId*2.3999632f;
        float spread=Math.min(cellW,cellH)*(.035f+.055f*((w.visualId%7)/6f));
        float tx=priorityX+(float)Math.cos(a)*spread,ty=priorityY+(float)Math.sin(a)*spread;
        w.action=WorkerAction.WALK;moveDirect(w,tx,ty,moveSpeed(w),dt);
        if(distance(w.x,w.y,tx,ty)<2.5f*ui){w.action=WorkerAction.IDLE;w.vx=w.vy=0;}
    }

    private void moveDirect(Worker w,float tx,float ty,float speed,float dt){
        float dx=tx-w.x,dy=ty-w.y,di=len(dx,dy);
        if(di<.001f){w.vx=w.vy=0;return;}
        float step=Math.min(di,speed*dt),nx=dx/di,ny=dy/di;
        w.vx=nx*speed;w.vy=ny*speed;w.x+=nx*step;w.y+=ny*step;w.walkCycle+=step/(5f*ui);
    }

    private void mine(Worker w,Vein v,float dt){
        if(!atCell(w,v.cell)){
            w.action=WorkerAction.WALK;routeWorker(w,v.cell);
            if(w.path.length==0){w.vein=null;w.goalCell=-1;w.vx=w.vy=0;w.action=WorkerAction.IDLE;return;}
            followWorker(w,moveSpeed(w),dt);return;
        }
        w.action=WorkerAction.MINE;w.vx=w.vy=0;
        if(w.attackCooldown<=0&&w.swing<=0){w.swing=.58f;w.hitApplied=false;w.attackCooldown=Math.max(.20f,.72f-w.tier.ordinal()*.065f);}
        float progress=w.swing<=0?1f:1f-w.swing/.58f;
        if(w.swing>0&&!w.hitApplied&&progress>=.57f){
            w.hitApplied=true;float damage=w.tier.miningPower*state.tierPowerMultiplier(w.tier.ordinal())*state.miningMultiplier(w.tier.ordinal());
            v.hp-=damage;v.hitFlash=.16f;screenShake=Math.max(screenShake,Math.min(2.8f*ui,.35f*ui+w.tier.ordinal()*.36f*ui));
            game.audio.play(GameAudio.Sfx.PICK,.32f+.07f*w.tier.ordinal());
            spawnRockHit(v,w.tier.ordinal());
            if(v.hp<=0)breakVein(v,w);
        }
    }

    private void breakVein(Vein v,Worker w){
        if(v.dead)return;v.dead=true;v.hp=0;v.death=0;w.add(v.type.material,state.yieldFor(v.type,w.tier.ordinal())*levelYieldMultiplier());
        if(v.type==RockType.GOLD||v.type==RockType.DIAMOND||v.type==RockType.ANCIENT_CRYSTAL)w.react=.85f;
        state.rocksBroken++;state.depthProgress++;spawnBreak(v);screenShake=Math.max(screenShake,3.3f*ui);
        game.audio.play(GameAudio.Sfx.ROCK_BREAK,.88f);game.audio.vibrate(26+Math.min(42,w.tier.ordinal()*7));
        if(state.totalGnomes()>=5&&mobs.size()<30&&random.nextFloat()<Math.min(.075f,.025f+state.depth*.0015f))spawnGhostFrom(v);
        if(v==priorityVein){priorityVein=null;toast="ПРИОРИТЕТ ДОБЫТ • "+v.type.title.toUpperCase();toastTime=1.5f;}
        else if(v.type.ordinal()>=RockType.DIAMOND.ordinal()){toast=v.type.title.toUpperCase()+" ДОБЫТ";toastTime=1.4f;}
    }

    private void carryHome(Worker w,float dt){
        int home=map.index(map.startCol,map.startRow);
        if(!atCell(w,home)){w.action=WorkerAction.CARRY;routeWorker(w,home);followWorker(w,moveSpeed(w)*.94f,dt);return;}
        w.action=WorkerAction.IDLE;w.vx=w.vy=0;
        if(w.hasCargo()){
            if(w.cargoStone>0)state.deposit(RockType.Material.STONE,w.cargoStone);
            if(w.cargoSilver>0)state.deposit(RockType.Material.SILVER,w.cargoSilver);
            if(w.cargoGold>0)state.deposit(RockType.Material.GOLD,w.cargoGold);
            if(w.cargoDiamond>0)state.deposit(RockType.Material.DIAMOND,w.cargoDiamond);
            w.clearCargo();spawnSparks(cx(map.startCol),cy(map.startRow),0xFFFFCC63,5);game.audio.play(GameAudio.Sfx.COIN,.42f);
        }
    }

    private void fight(Worker w,Mob m,float dt){
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

    private float moveSpeed(Worker w){return w.tier.moveSpeed*state.speedMultiplier(w.tier.ordinal())*ui;}
    private void routeWorker(Worker w,int goal){
        if(w.goalCell==goal&&w.path.length>0)return;if(w.goalCell==goal&&w.routeRetry>0)return;
        w.goalCell=goal;boolean[] danger=dangerMask(w);w.path=map.pathAvoiding(cellFor(w.x,w.y),goal,danger);w.pathIndex=Math.min(1,w.path.length);w.routeRetry=w.path.length==0?.28f:0f;
    }
    private boolean[] dangerMask(Worker w){
        boolean[] mask=null;int smart=w.tier.ordinal();
        for(CaveHazard h:hazards){if(h.type==HazardType.COLLAPSE||h.cleared)continue;boolean seen=h.fired||(smart>=2&&h.age>.22f)||(smart>=4&&h.age>.05f);if(!seen)continue;if(mask==null)mask=new boolean[map.cols*map.rows];mask[h.cell]=true;if(smart>=4){int c=map.col(h.cell),r=map.row(h.cell);int[] dirs={CaveMap.N,CaveMap.E,CaveMap.S,CaveMap.W};for(int dir:dirs){int nc=c+CaveMap.dx(dir),nr=r+CaveMap.dy(dir);if(map.inside(nc,nr)&&h.type==HazardType.LAVA)mask[map.index(nc,nr)]=true;}}
        }return mask;
    }

    private boolean isDangerCellFor(Worker w,int cell){
        int smart=w.tier.ordinal();
        for(CaveHazard h:hazards){
            if(h.type==HazardType.COLLAPSE||h.cleared)continue;
            boolean seen=h.fired||(smart>=2&&h.age>.22f)||(smart>=4&&h.age>.05f);
            if(!seen)continue;
            if(h.cell==cell)return true;
            if(smart>=4&&h.type==HazardType.LAVA){
                int hc=map.col(h.cell),hr=map.row(h.cell),c=map.col(cell),r=map.row(cell);
                if(Math.abs(hc-c)+Math.abs(hr-r)==1)return true;
            }
        }
        return false;
    }

    private void followWorker(Worker w,float speed,float dt){
        if(w.path.length==0||w.pathIndex>=w.path.length){w.vx=w.vy=0;return;}
        float remaining=Math.max(0,speed*dt),moved=0;int guard=0;float lastNx=0,lastNy=0;
        while(remaining>.001f&&w.pathIndex<w.path.length&&guard++<20){
            int node=w.path[w.pathIndex];if(map.isBlocked(node)||isDangerCellFor(w,node)){w.path=new int[0];w.pathIndex=0;w.goalCell=-1;w.vx=w.vy=0;return;}float tx=cx(map.col(node)),ty=cy(map.row(node));float dx=tx-w.x,dy=ty-w.y,di=len(dx,dy);
            if(di<.001f){w.x=tx;w.y=ty;w.pathIndex++;continue;}
            float nx=dx/di,ny=dy/di;lastNx=nx;lastNy=ny;
            if(di<=remaining){w.x=tx;w.y=ty;w.pathIndex++;remaining-=di;moved+=di;}
            else{w.x+=nx*remaining;w.y+=ny*remaining;moved+=remaining;remaining=0;}
        }
        if(moved>0){w.vx=lastNx*speed;w.vy=lastNy*speed;w.walkCycle+=moved/(5f*ui);}else w.vx=w.vy=0;
        if(w.pathIndex>=w.path.length){w.vx=w.vy=0;}
    }

    private int veinIndex(Vein v){return v==null?-1:veins.indexOf(v);}
    private boolean hasLowerLoadReachableVein(Worker w,int[] loads,int currentLoad){
        int start=cellFor(w.x,w.y);
        for(int i=0;i<veins.size();i++){
            Vein v=veins.get(i);if(v.dead||map.isBlocked(v.cell)||loads[i]+1>=currentLoad)continue;
            if(map.path(start,v.cell).length>0)return true;
        }
        return false;
    }
    private Vein chooseVein(Worker w,int[] loads){
        Vein best=null;float bestScore=Float.MAX_VALUE;int start=cellFor(w.x,w.y);boolean[] avoid=dangerMask(w);
        for(int i=0;i<veins.size();i++){
            Vein v=veins.get(i);if(v.dead||map.isBlocked(v.cell))continue;
            int[] route=avoid==null?map.path(start,v.cell):map.pathAvoiding(start,v.cell,avoid);if(route.length==0)continue;
            // One extra worker on a vein costs far more than a few cells of walking. This produces squads,
            // not a hundred beards all queueing for the same pebble.
            float personal=Math.floorMod(w.visualId*31+v.seed,29)*3f;
            float score=loads[i]*100000f+route.length*420f+personal;
            if(score<bestScore){bestScore=score;best=v;}
        }
        return best;
    }
    private void updatePortal(float dt){
        if(portal==null)return;portal.age+=dt;portal.spawnTimer-=dt;if(portal.next>=portal.queue.length){portal.closeAge+=dt;if(portal.done())portal=null;return;}if(portal.spawnTimer>0||portal.age<.42f)return;
        EnemyType type=portal.queue[portal.next++];Mob m=createMob(type,portal.x,portal.y);m.spawn=.42f;mobs.add(m);portal.spawnTimer=.30f+(type.isBoss()?.22f:0);if(portal.next>=portal.queue.length)portal.closeAge=0;
    }

    private void updateMobs(float dt){
        pendingMobs.clear();
        for(Mob m:mobs){
            if(m.dead)continue;m.spawn=Math.max(0,m.spawn-dt);m.attack=Math.max(0,m.attack-dt);m.hitFlash=Math.max(0,m.hitFlash-dt);m.flee=Math.max(0,m.flee-dt);m.attackCooldown-=dt;m.summonCooldown-=dt;m.routeTimer-=dt;if(m.spawn>0)continue;
            if(m.hp<=0){killMob(m);continue;}
            if(m.type==EnemyType.GHOST){updateGhost(m,dt);continue;}
            if(m.type==EnemyType.SUCCUBUS){updateSuccubus(m,dt);continue;}
            if(m.type.isImp()&&m.flee>0){fleeImp(m,dt);continue;}

            boolean thief=m.type==EnemyType.IMP;
            boolean canHitGuard=!guardianDead&&state.guardianLevel>0&&(m.type.isDemon()||m.type.isElemental()||m.type==EnemyType.IMP_KING||(m.type==EnemyType.IMP&&m.enraged));
            float qGuard=canHitGuard?dist2(m.x,m.y,guardianX,guardianY):Float.MAX_VALUE;
            Worker near=nearestWorker(m.x,m.y);float qWorker=near==null?Float.MAX_VALUE:dist2(m.x,m.y,near.x,near.y);
            int goal;
            if(thief&&!m.enraged){goal=map.index(map.startCol,map.startRow);m.target=null;}
            else if(qGuard<qWorker*1.15f){goal=cellFor(guardianX,guardianY);m.target=null;}
            else {m.target=near;goal=near==null?map.index(map.startCol,map.startRow):cellFor(near.x,near.y);}
            if(m.routeTimer<=0||m.goalCell!=goal){m.goalCell=goal;m.path=m.type.isImp()?map.pathIgnoringBlocks(cellFor(m.x,m.y),goal):map.path(cellFor(m.x,m.y),goal);m.pathIndex=Math.min(1,m.path.length);m.routeTimer=.48f;}
            boolean reached=followMob(m,m.type.moveSpeed*state.enemySpeedScale(m.type)*ui,dt);
            if(reached){if(thief&&!m.enraged)robChest(m);else if(qGuard<qWorker*1.15f)attackGuardian(m);else attackWorker(m,m.target);}
            if(m.type.isBoss()&&m.summonCooldown<=0)bossSpecial(m);
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

    private void robChest(Mob m){if(m.attackCooldown>0)return;m.attackCooldown=m.type==EnemyType.IMP_KING?1.7f:2.5f;m.attack=.42f;long[] stolen=state.stealFromChest(state.depth,m.type==EnemyType.IMP_KING);long value=stolen[0]+stolen[1]*8L+stolen[2]*20L+stolen[3]*100L;if(value>0){toast=lootToast(stolen);toastTime=1.7f;addNotice(lootToast(stolen),0xFFFFB24A,2.1f);spawnSparks(cx(map.startCol),cy(map.startRow),0xFFFFC04A,8);game.audio.play(GameAudio.Sfx.COIN,.65f);game.audio.vibrate(40);}}
    private String lootToast(long[] st){StringBuilder b=new StringBuilder("БЕС УКРАЛ: ");if(st[0]>0)b.append("кам ").append(st[0]).append(' ');if(st[1]>0)b.append("Ag ").append(st[1]).append(' ');if(st[2]>0)b.append("Au ").append(st[2]).append(' ');if(st[3]>0)b.append("◆ ").append(st[3]);return b.toString().trim();}
    private String ghostLootToast(long[] st){long v=st[0]+st[1]*8L+st[2]*20L;return v>0?"ПРИЗРАК УНЕС СОКРОВИЩА • "+v:"ПРИЗРАК НЕ НАШЁЛ ДОБЫЧИ";}

    private void attackWorker(Mob m,Worker w){
        if(w==null||m.attackCooldown>0||m.type==EnemyType.IMP||m.type==EnemyType.SUCCUBUS||m.type==EnemyType.GHOST)return;m.attackCooldown=1.0f;m.attack=.40f;float power=m.type.contactPower*state.enemyDamageScale(m.type);w.stun=Math.max(w.stun,.35f+power*.018f);spawnSparks(w.x,w.y,0xFFFF765D,4);float survive=state.hazardSurvivalBonus(w.tier.ordinal());float lethality=Math.min(.62f,.012f+power*.007f);if(m.type.isBoss())lethality=Math.min(.78f,lethality*1.35f);if(random.nextFloat()<lethality*(1-survive))loseWorker(w,m.type.title+" убил гнома");
    }
    private void attackGuardian(Mob m){
        if(guardianDead||m.attackCooldown>0||m.type==EnemyType.SUCCUBUS||m.type==EnemyType.GHOST||m.type==EnemyType.IMP&&!m.enraged)return;m.attackCooldown=.95f;m.attack=.42f;float damage=(7f+m.type.contactPower*5f)*state.enemyDamageScale(m.type);guardianHp-=damage;guardianHitFlash=.22f;spawnSparks(guardianX,guardianY,0xFF75C5DE,5);if(guardianHp<=0){guardianHp=0;guardianDead=true;guardianTarget=null;toast="СТРАЖ ПАЛ В БОЮ";toastTime=2f;addNotice("СТРАЖ ПАЛ",0xFFFF6558,2.8f);game.audio.vibrate(80);}
    }
    private void killMob(Mob m){
        if(m.dead)return;m.dead=true;m.attack=-2f;state.enemiesDefeated++;spawnDeath(m);screenShake=Math.max(screenShake,2.2f*ui);if(priorityKind==PriorityKind.MOB&&priorityMob==m){clearPriority(false);toast="ЦЕЛЬ УНИЧТОЖЕНА";toastTime=1.2f;}if(m.type.isBoss()){int reward=3+state.depth/8;state.diamond+=reward;toast="БОСС ПОВЕРЖЕН • ◆"+reward;toastTime=2.4f;}
    }
    private void loseWorker(Worker w,String reason){if(!workers.remove(w))return;int ti=w.tier.ordinal();if(state.tierCounts[ti]>0)state.tierCounts[ti]--;state.gnomesLost++;toast="ГНОМ ПОТЕРЯН • "+reason;toastTime=2f;addNotice("−1 ГНОМ • "+reason.toUpperCase(),0xFFFF6A5B,2.4f);spawnSparks(w.x,w.y,0xFFE6D5BD,12);}
    private Worker nearestWorker(float x,float y){Worker best=null;float bd=Float.MAX_VALUE;for(Worker w:workers){float q=dist2(x,y,w.x,w.y);if(q<bd){bd=q;best=w;}}return best;}
    private Mob nearestMob(float x,float y){Mob best=null;float bd=Float.MAX_VALUE;for(Mob m:mobs){if(m.dead||m.retreating)continue;float q=dist2(x,y,m.x,m.y);if(q<bd){bd=q;best=m;}}return best;}

    private void spawnEnemyWave(){
        EnemyType type=levelEvent==LevelEvent.IMP_SWARM?EnemyType.IMP:chooseEnemyType();if(type==EnemyType.IMP){openPortal(type,(levelEvent==LevelEvent.IMP_SWARM?6:3)+random.nextInt(levelEvent==LevelEvent.IMP_SWARM?5:4));}else if(type==EnemyType.DEMON){openPortal(type,2+random.nextInt(3));}else if(type==EnemyType.SUCCUBUS){EnemyType[] q={EnemyType.DEMON,EnemyType.SUCCUBUS,EnemyType.DEMON};openPortal(q);}else spawnMob(type);
        toast="ТРЕВОГА • "+type.title.toUpperCase();toastTime=1.3f;game.audio.play(GameAudio.Sfx.ENEMY,.48f);
    }
    private EnemyType chooseEnemyType(){int d=state.depth;float r=random.nextFloat();if(d>=4&&r<.018f)return EnemyType.GHOST;if(d>=18&&r<.13f)return EnemyType.FIRE_GOLEM;if(d>=15&&r<.25f)return EnemyType.WATER_GOLEM;if(d>=12&&r<.38f)return EnemyType.STONE_GOLEM;if(d>=9&&r<.42f&&!hasLiving(EnemyType.SUCCUBUS))return EnemyType.SUCCUBUS;if(d>=7&&r<.66f)return EnemyType.DEMON;return EnemyType.IMP;}
    private void spawnBoss(){
        if(pendingBoss!=null||livingBoss()!=null)return;
        pendingBoss=state.depth>=30?EnemyType.ELEMENTAL_KING:state.depth>=20?EnemyType.DEMON_KING:EnemyType.IMP_KING;bossIntro=2.35f;
        toast="БОСС ПРИБЛИЖАЕТСЯ";toastTime=2.2f;addNotice("БОСС • "+pendingBoss.title.toUpperCase(),0xFFFF6B57,3f);game.audio.play(GameAudio.Sfx.BOSS,.90f);game.audio.vibrate(90);
    }
    private void updateBossPrelude(float dt){
        if(pendingBoss==null)return;bossIntro-=dt;if(bossIntro>0)return;EnemyType t=pendingBoss;pendingBoss=null;
        if(t==EnemyType.ELEMENTAL_KING)spawnMob(t);else openPortal(t,1);
    }
    private void openPortal(EnemyType type,int n){EnemyType[] q=new EnemyType[n];java.util.Arrays.fill(q,type);openPortal(q);}
    private void openPortal(EnemyType[] q){if(portal!=null||q.length==0)return;List<Integer> out=map.outerCells();int cell=out.get(random.nextInt(out.size()));portal=new Portal(cell,cx(map.col(cell)),cy(map.row(cell)),q);}
    private void spawnMob(EnemyType type){List<Integer> out=map.outerCells();int cell=out.get(random.nextInt(out.size()));mobs.add(createMob(type,cx(map.col(cell)),cy(map.row(cell))));}
    private Mob createMob(EnemyType type,float x,float y){Mob m=new Mob(type,x,y,random.nextFloat()*6.28f);m.maxHp=type.hp*state.enemyHpScale(type);m.hp=m.maxHp;m.ghostSteals=type==EnemyType.GHOST&&random.nextFloat()<.62f;return m;}
    private void spawnGhostFrom(Vein v){Mob m=createMob(EnemyType.GHOST,v.x,v.y);m.spawn=.52f;m.target=nearestWorker(v.x,v.y);mobs.add(m);toast="ИЗ КАМНЯ ВЫРВАЛСЯ ПРИЗРАК";toastTime=1.5f;}
    private boolean hasLiving(EnemyType type){for(Mob m:mobs)if(!m.dead&&m.type==type)return true;return false;}

    private void updateHazards(float dt){
        for(Iterator<CaveHazard>it=hazards.iterator();it.hasNext();){
            CaveHazard h=it.next();h.age+=dt;
            if(!h.fired&&h.age>=1.25f){h.fired=true;fireHazard(h);}
            if(h.fired){
                if(h.type==HazardType.LAVA)applyLava(h,dt);
                else if(h.type==HazardType.PIT)applyPit(h);
                else if(h.type==HazardType.COLLAPSE)settleCollapse(h,dt);
            }
            if(h.type==HazardType.COLLAPSE){if(h.cleared)it.remove();continue;}
            if(h.age>hazardDuration(h.type))it.remove();
        }
    }
    private float hazardDuration(HazardType t){return switch(t){case COLLAPSE->Float.MAX_VALUE;case PIT->8f;case LAVA->9f;case FLOOD->5.4f;};}
    private void settleCollapse(CaveHazard h,float dt){
        if(h.cleared||!h.obstacleActive||h.age<6f)return;
        // Loose rubble gradually settles and rolls away. A player can still clear it much faster with gnomes.
        h.rubbleHp-=h.rubbleMaxHp*.012f*dt;
        if(h.rubbleHp<=0)finishCollapse(h,false);
    }
    private void finishCollapse(CaveHazard h,boolean byGnomes){
        if(h.cleared)return;
        h.rubbleHp=0;h.cleared=true;h.obstacleActive=false;map.unblockCell(h.cell);
        if(priorityHazard==h)clearPriority(false);
        invalidateRoutes();
        if(byGnomes){toast="ОБВАЛ РАЗОБРАН";toastTime=1.5f;addNotice("ЗАВАЛ РАЗОБРАН",0xFFD9B47A,1.8f);game.audio.play(GameAudio.Sfx.ROCK_BREAK,.75f);}
    }
    private void spawnHazard(){
        int start=map.index(map.startCol,map.startRow),cell=start;
        for(int tries=0;tries<12&&cell==start;tries++)cell=random.nextInt(map.cols*map.rows);
        if(cell==start)return;
        HazardType type=levelEvent==LevelEvent.FLOODED?HazardType.FLOOD:levelEvent==LevelEvent.UNSTABLE&&random.nextFloat()<.72f?HazardType.COLLAPSE:HazardType.values()[random.nextInt(HazardType.values().length)];float danger=1f+Math.min(.45f,state.depth*.012f);float r=Math.min(cellW,cellH)*(type==HazardType.FLOOD?1.15f:.58f)*danger;
        CaveHazard h=new CaveHazard(type,cell,cx(map.col(cell)),cy(map.row(cell)),r);
        if(type==HazardType.COLLAPSE){h.rubbleMaxHp=125f*(1f+state.depth*.13f);h.rubbleHp=h.rubbleMaxHp;}
        hazards.add(h);toast="ОПАСНОСТЬ • "+type.title;toastTime=1.3f;
    }
    private void fireHazard(CaveHazard h){
        screenShake=Math.max(screenShake,h.type==HazardType.COLLAPSE?5f*ui:2f*ui);
        if(h.type==HazardType.COLLAPSE&&map.blockCell(h.cell)){h.obstacleActive=true;invalidateRoutes();game.audio.play(GameAudio.Sfx.COLLAPSE,.92f);game.audio.vibrate(120);}
        else {game.audio.play(GameAudio.Sfx.HAZARD,.62f);game.audio.vibrate(45);}
        for(int i=workers.size()-1;i>=0;i--){
            Worker w=workers.get(i);if(distance(w.x,w.y,h.x,h.y)>h.r)continue;float survive=state.hazardSurvivalBonus(w.tier.ordinal());
            switch(h.type){
                case FLOOD -> w.stun=Math.max(w.stun,1.8f);
                case LAVA -> loseWorker(w,"сгорел в лаве");
                case PIT -> loseWorker(w,"провалился в яму");
                case COLLAPSE -> {if(distance(w.x,w.y,h.x,h.y)<h.r*.70f)loseWorker(w,"погиб под обвалом");else if(random.nextFloat()<.24f*(1-survive))loseWorker(w,"погиб под обвалом");else w.stun=Math.max(w.stun,1.1f);}
            }
        }
        hitMobsWithHazard(h);
        spawnSparks(h.x,h.y,h.type==HazardType.FLOOD?0xFF70C9F4:h.type==HazardType.LAVA?0xFFFF8A28:0xFF918172,10);
    }

    private void applyLava(CaveHazard h,float dt){
        float rr=h.r*.72f;
        for(int i=workers.size()-1;i>=0;i--){Worker w=workers.get(i);if(distance(w.x,w.y,h.x,h.y)<rr)loseWorker(w,"наступил в лаву");}
        for(Mob m:mobs){
            if(m.dead||m.type==EnemyType.GHOST||distance(m.x,m.y,h.x,h.y)>=rr)continue;
            if(m.type==EnemyType.FIRE_GOLEM)continue;
            if(m.type==EnemyType.IMP||m.type==EnemyType.DEMON||m.type==EnemyType.SUCCUBUS)m.hp=0;
            else m.hp-=m.maxHp*.18f*dt;
        }
    }

    private void applyPit(CaveHazard h){
        float rr=h.r*.64f;
        for(int i=workers.size()-1;i>=0;i--){Worker w=workers.get(i);if(distance(w.x,w.y,h.x,h.y)<rr)loseWorker(w,"провалился в яму");}
    }

    private void hitMobsWithHazard(CaveHazard h){
        for(Mob m:mobs){
            if(m.dead||m.type==EnemyType.GHOST||distance(m.x,m.y,h.x,h.y)>h.r)continue;
            boolean small=m.type==EnemyType.IMP||m.type==EnemyType.DEMON||m.type==EnemyType.SUCCUBUS;
            switch(h.type){
                case LAVA -> {if(m.type!=EnemyType.FIRE_GOLEM){if(small)m.hp=0;else m.hp-=m.maxHp*.18f;}}
                case PIT -> {if(small&&random.nextFloat()<.48f)m.hp=0;else m.hp-=m.maxHp*.08f;}
                case COLLAPSE -> {if(small&&random.nextFloat()<.34f)m.hp=0;else m.hp-=m.maxHp*.12f;}
                case FLOOD -> {if(m.type==EnemyType.FIRE_GOLEM)m.hp-=m.maxHp*.22f;else if(small)m.hp-=m.maxHp*.08f;}
            }
        }
    }

    private void invalidateRoutes(){
        for(Worker w:workers){w.goalCell=-1;w.path=new int[0];w.pathIndex=0;}
        for(Mob m:mobs){m.goalCell=-1;m.path=new int[0];m.pathIndex=0;m.routeTimer=0;}guardianPath=new int[0];guardianPathIndex=0;guardianGoal=-1;
    }

    private void updateFx(float dt){for(Iterator<Fx>it=fx.iterator();it.hasNext();){Fx p=it.next();p.life-=dt;if(p.life<=0){it.remove();continue;}p.x+=p.vx*dt;p.y+=p.vy*dt;if(!p.spark)p.vy+=45f*ui*dt;else{p.vx*=Math.max(0,1-dt*3);p.vy*=Math.max(0,1-dt*3);}}int limit=workers.size()>120?120:workers.size()>90?165:workers.size()>70?215:400;if(fx.size()>limit)fx.subList(0,fx.size()-limit).clear();}
    private int fxBudget(int normal,int crowded){return workers.size()>90?crowded:normal;}
    private void spawnRockHit(Vein v,int power){int n=workers.size()>120?1:workers.size()>80?2:5+Math.min(5,power);for(int i=0;i<n;i++){float a=random.nextFloat()*6.28f,sp=(25+random.nextFloat()*65+power*7)*ui;fx.add(new Fx(v.x,v.y,(float)Math.cos(a)*sp,(float)Math.sin(a)*sp-.2f*sp,.35f+random.nextFloat()*.35f,(1.1f+random.nextFloat()*2.2f)*ui,adjust(v.type.color,.75f+random.nextFloat()*.35f),false));}}
    private void spawnBreak(Vein v){int n=fxBudget(18,7);for(int i=0;i<n;i++){float a=random.nextFloat()*6.28f,sp=(30+random.nextFloat()*110)*ui;fx.add(new Fx(v.x,v.y,(float)Math.cos(a)*sp,(float)Math.sin(a)*sp-35f*ui,.45f+random.nextFloat()*.65f,(1.5f+random.nextFloat()*3.4f)*ui,adjust(v.type.color,.65f+random.nextFloat()*.5f),false));}}
    private void spawnDeath(Mob m){int n=fxBudget(16+(m.type.ordinal()>=EnemyType.IMP_KING.ordinal()?12:0),6+(m.type.ordinal()>=EnemyType.IMP_KING.ordinal()?5:0));for(int i=0;i<n;i++){float a=random.nextFloat()*6.28f,sp=(35+random.nextFloat()*95)*ui;fx.add(new Fx(m.x,m.y,(float)Math.cos(a)*sp,(float)Math.sin(a)*sp,.45f+random.nextFloat()*.5f,(1.5f+random.nextFloat()*3f)*ui,m.type.color,false));}}
    private void spawnSparks(float x,float y,int color,int n){n=Math.min(n,fxBudget(n,Math.max(1,n/2)));for(int i=0;i<n;i++){float a=random.nextFloat()*6.28f,sp=(25+random.nextFloat()*85)*ui;fx.add(new Fx(x,y,(float)Math.cos(a)*sp,(float)Math.sin(a)*sp,.18f+random.nextFloat()*.3f,(1+random.nextFloat()*1.7f)*ui,color,true));}}

    private void drawWorld(Draw d){
        d.setColor(0xFF0B0A09);d.fillRect(worldL,worldT,worldR,worldB);drawRockMass(d);drawTunnels(d);drawWetCorridors(d);drawCaveDecor(d);drawHazards(d);drawVeins(d);drawChest(d);drawPortal(d);
        for(int row=0;row<map.rows;row++){for(Worker w:workers)if(rowForY(w.y)==row){drawWorker(d,w);drawWorkerStatus(d,w);}for(Mob m:mobs)if(rowForY(m.y)==row)drawMob(d,m);}
        if(state.guardianLevel>0&&!guardianDead){float gs=Math.min(cellW,cellH)*.54f;drawGuardian(d,guardianX,guardianY,gs);drawGuardianHealth(d,guardianX,guardianY,gs);}
        drawPriorityOverlay(d);for(Fx p:fx)drawFx(d,p);drawDarkZones(d);drawAtmosphere(d);drawBossHud(d);
    }

    private void drawWorkerStatus(Draw d,Worker w){
        float s=Math.max(12f*ui,w.tier.size*ui*.48f);
        if(w.hasCargo()){float x=w.x+s*.46f,y=w.y+s*.24f;d.setColor(0xFF6C4A2F);d.fillOval(x-4.2f*ui,y-3.4f*ui,x+4.2f*ui,y+4.7f*ui);d.setColor(0xFFD2A65C);d.strokeWidth=1.2f*ui;d.line(x-3f*ui,y-2.3f*ui,x+3f*ui,y-2.3f*ui);}
        if(w.mob!=null&& !w.mob.dead && (w.action==WorkerAction.WALK||w.action==WorkerAction.FIGHT)){d.align=Draw.Align.CENTER;d.bold=true;d.textSize=6f*ui;d.setColor(0xFFFFD55A);d.text("!",w.x,w.y-s*.72f);d.bold=false;d.align=Draw.Align.LEFT;}
        if(w.react>0){float p=w.react/.85f;d.setColor(alpha(0xFFFFE07A,.25f+.55f*p));for(int i=0;i<3;i++){float a=i*2.094f+elapsed*2f;d.fillCircle(w.x+(float)Math.cos(a)*s*.62f,w.y-s*.42f+(float)Math.sin(a)*s*.22f,(1.2f+p)*ui);}}
    }

    private int rowForY(float y){return Math.max(0,Math.min(map.rows-1,(int)((y-worldT)/cellH)));}

    private void drawRockMass(Draw d){
        d.setColor(0xFF272720);d.fillRect(worldL,worldT,worldR,worldB);long seed=map.seed;
        int count=workers.size()>100?44:76;
        for(int i=0;i<count;i++){
            long q=seed+i*0x9E3779B97F4A7C15L;
            float x=worldL+hash01(q)*(worldR-worldL),y=worldT+hash01(q^0xA5A5A5A5L)*(worldB-worldT);
            float rr=(8f+hash01(q^0x55AA55AAL)*18f)*ui;
            int c=i%6==0?0xFF33372E:i%4==0?0xFF302E27:i%3==0?0xFF242923:0xFF2B2A25;
            d.setColor(c);d.fillOval(x-rr*1.45f,y-rr*.62f,x+rr*1.45f,y+rr*.62f);
            d.setColor(i%5==0?0x443F5E3B:0x223F3A31);d.strokeWidth=Math.max(1f,1.1f*ui);
            d.line(x-rr*.95f,y-rr*.18f,x-rr*.12f,y+rr*.08f);d.line(x-rr*.12f,y+rr*.08f,x+rr*.76f,y-rr*.11f);
            if(i%9==0){d.setColor(0x553F6940);d.fillCircle(x-rr*.34f,y-rr*.16f,rr*.28f);d.fillCircle(x+rr*.02f,y-rr*.22f,rr*.20f);}
        }
    }

    private void drawTunnels(Draw d){
        float base=Math.min(cellW,cellH),outer=base*.50f,mid=base*.39f,inner=base*.30f;
        drawOrganicTunnelLayer(d,outer,0xFF4A4035,0x13A5B357L);
        drawOrganicTunnelLayer(d,mid,0xFF292722,0x4C957F2DL);
        drawOrganicTunnelLayer(d,inner,0xFF36312A,0x7F4A7C15L);
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int idx=map.index(c,r),degree=map.degree(c,r);if(degree<=0)continue;
            boolean light=(idx+state.depth*3)%14==0&&!isDarkCell(idx);
            if(light){
                if(isGlowMossLight(idx))drawGlowMoss(d,cx(c),cy(r),idx,inner);
                else drawTorch(d,cx(c)-inner*.26f,cy(r)-inner*.38f,idx);
            }
            if((idx*7+state.depth)%19==0){d.setColor(0xFF6A4A31);d.strokeWidth=2f*ui;d.line(cx(c)-inner*.45f,cy(r)-inner*.48f,cx(c)-inner*.45f,cy(r)+inner*.48f);d.line(cx(c)+inner*.45f,cy(r)-inner*.48f,cx(c)+inner*.45f,cy(r)+inner*.48f);d.line(cx(c)-inner*.52f,cy(r)-inner*.34f,cx(c)+inner*.52f,cy(r)-inner*.34f);}
        }
    }
    private void drawOrganicTunnelLayer(Draw d,float width,int color,long salt){
        d.setColor(color);d.strokeWidth=width;
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int cell=map.index(c,r);
            if(map.connected(c,r,CaveMap.E))drawOrganicTunnelEdge(d,cx(c),cy(r),cx(c+1),cy(r),cell,salt^0x51ED270BL);
            if(map.connected(c,r,CaveMap.S))drawOrganicTunnelEdge(d,cx(c),cy(r),cx(c),cy(r+1),cell,salt^0xB5297A4DL);
        }
        // Round every join, not only crossroads. Dead ends become small alcoves instead of chopped-off pipes.
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int degree=map.degree(c,r);if(degree<=0)continue;float x=cx(c),y=cy(r);d.setColor(color);
            if(degree==1){
                int open=firstOpening(c,r);float ex=-CaveMap.dx(open)*width*.12f,ey=-CaveMap.dy(open)*width*.12f;
                d.fillOval(x-width*.66f+ex,y-width*.56f+ey,x+width*.66f+ex,y+width*.56f+ey);
            }else d.fillCircle(x,y,width*.52f);
        }
    }
    private void drawOrganicTunnelEdge(Draw d,float x1,float y1,float x2,float y2,int cell,long salt){
        float dx=x2-x1,dy=y2-y1,di=Math.max(1f,len(dx,dy)),nx=-dy/di,ny=dx/di;
        float bend=(hash01(map.seed^salt^(long)cell*0x9E3779B97F4A7C15L)-.5f)*Math.min(cellW,cellH)*.12f;
        float mx=(x1+x2)*.5f+nx*bend,my=(y1+y2)*.5f+ny*bend;
        d.pathReset();d.moveTo(x1,y1);d.quadTo(mx,my,x2,y2);d.strokePath();
    }
    private int firstOpening(int c,int r){int bits=map.openings[r][c];if((bits&CaveMap.N)!=0)return CaveMap.N;if((bits&CaveMap.E)!=0)return CaveMap.E;if((bits&CaveMap.S)!=0)return CaveMap.S;return CaveMap.W;}
    private boolean isGlowMossLight(int cell){return hash01(map.seed^0xC6BC279692B5CC83L^(long)cell*0xD1B54A32D192ED03L)<.42f;}
    private void drawGlowMoss(Draw d,float x,float y,int cell,float inner){
        float side=((cell&1)==0?-1f:1f),px=x+side*inner*.36f,py=y-inner*.30f;
        float pulse=.82f+.18f*(float)Math.sin(elapsed*2.4f+cell*.71f);
        d.setColor(0x1432F1C2);d.fillCircle(px,py,inner*.74f*pulse);d.setColor(0x2256F7D1);d.fillCircle(px,py,inner*.42f*pulse);
        d.setColor(0xFF285C45);d.strokeWidth=1.3f*ui;for(int i=0;i<4;i++){float ox=(i-1.5f)*3.5f*ui;d.line(px+ox,py-7f*ui,px+ox+side*(i%2==0?2:-2)*ui,py+8f*ui);}
        int[] cols={0xFF53D58E,0xFF6AF4BB,0xFF3CBDA0,0xFF9AEDD0};
        for(int i=0;i<9;i++){long h=cell*131L+i*47L;float ox=(hash01(h)-.5f)*18f*ui,oy=(hash01(h^0x55AA55AAL)-.5f)*18f*ui;d.setColor(cols[i%cols.length]);d.fillCircle(px+ox,py+oy,(1.4f+(i%3)*.55f)*ui*pulse);}
    }
    private void drawWetCorridors(Draw d){
        float base=Math.min(cellW,cellH);int wet=0;
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int cell=map.index(c,r);long h=map.seed^cell*0x94D049BB133111EBL;float wetChance=.13f+(levelEvent==LevelEvent.FLOODED?.20f:0f);if(hash01(h)>wetChance)continue;
            int dir=map.connected(c,r,CaveMap.E)?CaveMap.E:(map.connected(c,r,CaveMap.S)?CaveMap.S:0);if(dir==0)continue;
            float x1=cx(c),y1=cy(r),x2=cx(c+CaveMap.dx(dir)),y2=cy(r+CaveMap.dy(dir));
            float dx=x2-x1,dy=y2-y1,di=Math.max(1f,len(dx,dy)),nx=-dy/di,ny=dx/di;
            boolean soaked=hash01(h^0xA24BAED4963EE407L)<(levelEvent==LevelEvent.FLOODED?.58f:.28f);
            if(soaked){
                drawWetRibbonLayer(d,x1,y1,x2,y2,base*.125f,h,0x77304A43);
                drawWetRibbonLayer(d,x1,y1,x2,y2,base*.090f,h^0x9E3779B9L,0xAA34756E);
                drawWetRibbonLayer(d,x1,y1,x2,y2,base*.045f,h^0x85EBCA6BL,0x554AB4AD);
                for(int i=0;i<6;i++){float q=(i+.5f)/6f,px=x1+dx*q+nx*(float)Math.sin(i*1.7f+cell)*base*.035f,py=y1+dy*q+ny*(float)Math.sin(i*1.7f+cell)*base*.035f;d.setColor(0x665FD0CB);d.fillCircle(px,py,(1.1f+i%3*.45f)*ui);}
            }else{
                int puddles=2+(int)(hash01(h^0x632BE59BD9B4E019L)*3f);
                for(int i=0;i<puddles;i++){
                    float q=.16f+(i+.5f)/puddles*.68f,jitter=(hash01(h+i*137L)-.5f)*base*.18f;
                    float px=x1+dx*q+nx*jitter,py=y1+dy*q+ny*jitter;
                    float longR=base*(.075f+.075f*hash01(h+i*271L)),shortR=base*(.035f+.045f*hash01(h+i*353L));
                    float rx=Math.abs(dx)>Math.abs(dy)?longR:shortR,ry=Math.abs(dx)>Math.abs(dy)?shortR:longR;
                    drawIrregularPuddle(d,px,py,rx,ry,h+i*911L);
                }
            }
            wet++;if(wet>12)return;
        }
    }
    private void drawWetRibbonLayer(Draw d,float x1,float y1,float x2,float y2,float half,long seed,int color){
        float dx=x2-x1,dy=y2-y1,di=Math.max(1f,len(dx,dy)),nx=-dy/di,ny=dx/di;int n=7;d.pathReset();
        for(int i=0;i<n;i++){float q=i/(float)(n-1),w=half*(.72f+.36f*hash01(seed+i*71L)),wave=(hash01(seed+i*131L)-.5f)*half*.40f;float x=x1+dx*q+nx*(w+wave),y=y1+dy*q+ny*(w+wave);if(i==0)d.moveTo(x,y);else d.lineTo(x,y);}
        for(int i=n-1;i>=0;i--){float q=i/(float)(n-1),w=half*(.72f+.36f*hash01(seed^0x55AA55AAL+i*83L)),wave=(hash01(seed^0xA5A5A5A5L+i*149L)-.5f)*half*.40f;d.lineTo(x1+dx*q-nx*(w+wave),y1+dy*q-ny*(w+wave));}
        d.closePath();d.setColor(color);d.fillPath();
    }
    private void drawIrregularPuddle(Draw d,float x,float y,float rx,float ry,long seed){
        drawPuddleLayer(d,x,y,rx*1.14f,ry*1.18f,seed,0x66304B43);
        drawPuddleLayer(d,x,y,rx,ry,seed^0x9E3779B97F4A7C15L,0xAA37766E);
        drawPuddleLayer(d,x,y,rx*.67f,ry*.54f,seed^0xBF58476D1CE4E5B9L,0x554BB4AF);
        float ripple=.70f+.10f*(float)Math.sin(elapsed*1.8f+(seed&31));d.setColor(0x665ED1C9);d.strokeWidth=1f*ui;d.strokeCircle(x-rx*.10f,y,Math.max(1.8f*ui,Math.min(rx,ry)*ripple));
    }
    private void drawPuddleLayer(Draw d,float x,float y,float rx,float ry,long seed,int color){
        int n=10;d.pathReset();for(int i=0;i<n;i++){float a=(float)(Math.PI*2*i/n),j=.72f+.38f*hash01(seed+i*97L);float px=x+(float)Math.cos(a)*rx*j,py=y+(float)Math.sin(a)*ry*(.78f+.34f*hash01(seed+i*163L));if(i==0)d.moveTo(px,py);else d.lineTo(px,py);}d.closePath();d.setColor(color);d.fillPath();
    }
    private void drawCaveDecor(Draw d){
        if(detailTier()>=2)return;
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int cell=map.index(c,r);long q=map.seed^cell*0x9E3779B97F4A7C15L;float x=cx(c),y=cy(r),k=hash01(q);
            if(k<.085f)drawPlantPatch(d,x,y,cell,(int)(hash01(q^0xA24BAED4963EE407L)*4f));
            else if(k>.945f){float drip=((elapsed*(8+cell%5)+cell*17)%32)*ui;d.setColor(0x665CBCE3);d.strokeWidth=1f*ui;d.line(x,y-18f*ui,x,y-8f*ui+drip*.25f);d.fillOval(x-1.2f*ui,y-8f*ui+drip*.45f,x+1.2f*ui,y-4f*ui+drip*.45f);}
            if(cell%37==state.depth%37&&r<map.rows/2){d.setColor(0x18DDF6D7);d.pathReset();d.moveTo(x-24f*ui,worldT);d.lineTo(x+7f*ui,worldT);d.lineTo(x+26f*ui,y+34f*ui);d.lineTo(x-12f*ui,y+34f*ui);d.closePath();d.fillPath();}
        }
    }
    private void drawPlantPatch(Draw d,float x,float y,int cell,int variant){
        float sway=(float)Math.sin(elapsed*.9f+cell)*1.2f*ui;
        switch(variant){
            case 0 -> { // thin grass tuft
                d.setColor(0xFF365F3C);d.strokeWidth=1.25f*ui;for(int i=0;i<5;i++){float ox=(i-2)*3f*ui;d.line(x+ox,y+8f*ui,x+ox*.72f+sway*(i%2==0?1:-1),y-7f*ui-(i%3)*3f*ui);}d.setColor(0xFF598053);for(int i=0;i<4;i++)d.fillCircle(x-5f*ui+i*3.5f*ui,y+1f*ui-(i%2)*4f*ui,1.7f*ui);}
            case 1 -> { // low moss carpet
                d.setColor(0xAA294C31);d.fillOval(x-12f*ui,y+2f*ui,x+12f*ui,y+9f*ui);int[] cols={0xFF3E6D40,0xFF527A47,0xFF315B39};for(int i=0;i<7;i++){d.setColor(cols[i%cols.length]);float ox=(hash01(cell*91L+i*17L)-.5f)*20f*ui,oy=(hash01(cell*71L+i*29L)-.5f)*7f*ui;d.fillCircle(x+ox,y+4f*ui+oy,(1.6f+i%3*.55f)*ui);}}
            case 2 -> { // hanging ivy
                d.setColor(0xFF315A38);d.strokeWidth=1.4f*ui;float px=x-7f*ui;for(int i=0;i<4;i++){float yy=y-13f*ui+i*7f*ui,nx=x-7f*ui+(i%2==0?4f:-2f)*ui+sway*.35f;d.line(px,yy,nx,yy+7f*ui);px=nx;d.setColor(i%2==0?0xFF4D7A49:0xFF3D6842);d.fillOval(nx-3f*ui,yy+2f*ui,nx+3f*ui,yy+6f*ui);d.setColor(0xFF315A38);}}
            default -> { // tiny cave mushrooms among short grass
                d.setColor(0xFF3B643D);d.strokeWidth=1.2f*ui;for(int i=0;i<3;i++)d.line(x-7f*ui+i*5f*ui,y+8f*ui,x-5f*ui+i*5f*ui,y-2f*ui-i*2f*ui);for(int i=0;i<3;i++){float mx=x-6f*ui+i*6f*ui,my=y+5f*ui-(i%2)*3f*ui;d.setColor(0xFFC6B38B);d.fillRect(mx-1f*ui,my-1f*ui,mx+1f*ui,my+4f*ui);d.setColor(i==1?0xFF8B5D52:0xFF6E7650);d.fillOval(mx-4f*ui,my-4f*ui,mx+4f*ui,my+1f*ui);}}
        }
    }
    private boolean isDarkCell(int cell){if(state.depth<3||cell==map.index(map.startCol,map.startRow))return false;return hash01(map.seed^cell*0xD1B54A32D192ED03L)<.105f;}
    private void drawDarkZones(Draw d){
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int cell=map.index(c,r);if(!isDarkCell(cell))continue;
            float base=Math.min(cellW,cellH),x=cx(c),y=cy(r),phase=hash01(map.seed^cell*0x6A09E667F3BCC909L)*6.28318f;
            for(int i=0;i<13;i++){
                float a=phase+i*.483f+(float)Math.sin(elapsed*.13f+i+cell)*.13f;
                float ring=base*(.08f+.31f*hash01(cell*131L+i*17L));
                float ox=(float)Math.cos(a)*ring,oy=(float)Math.sin(a)*ring*.68f;
                float rx=base*(.17f+.11f*hash01(cell*79L+i*23L));
                float ry=rx*(.42f+.24f*hash01(cell*47L+i*29L));
                d.setColor(i<4?0x28101618:0x1B12191B);d.fillOval(x+ox-rx,y+oy-ry,x+ox+rx,y+oy+ry);
            }
        }
        for(Worker w:workers)if(isDarkCell(cellFor(w.x,w.y))){
            float rr=(19f+w.tier.ordinal()*2f)*ui;
            d.setColor(0x1452A5B5);d.fillCircle(w.x,w.y,rr*1.32f);
            d.setColor(0x22D5B56A);d.fillCircle(w.x,w.y,rr*.62f);
            d.setColor(0x2CFFE09A);d.fillCircle(w.x,w.y,rr*.24f);
        }
    }
    private void drawPortal(Draw d){if(portal==null)return;float p=Math.min(1f,portal.age/.42f),close=portal.next>=portal.queue.length?Math.max(0,1-portal.closeAge):1f,rr=Math.min(cellW,cellH)*.34f*p*close;d.setColor(0x444E1D6D);d.fillCircle(portal.x,portal.y,rr*1.3f);for(int i=0;i<5;i++){float a=elapsed*(2.5f+i*.3f)+i*1.25f;d.setColor(i%2==0?0xFFB34CE2:0xFFE45658);d.strokeWidth=(1.2f+i*.25f)*ui;d.strokeCircle(portal.x+(float)Math.cos(a)*rr*.12f,portal.y+(float)Math.sin(a)*rr*.12f,rr*(.58f+i*.09f));}}

    private void drawTorch(Draw d,float x,float y,int seed){
        float flick=.82f+.18f*(float)Math.sin(elapsed*9.3f+seed);d.setColor(0x18FF9B32);d.fillCircle(x,y,25f*ui*flick);d.setColor(0x28FFB14A);d.fillCircle(x,y,13f*ui*flick);d.setColor(0xFF6E4930);d.strokeWidth=2.3f*ui;d.line(x,y+9f*ui,x,y+18f*ui);d.setColor(0xFFFF8C2E);d.fillOval(x-3.4f*ui,y-7f*ui,x+3.4f*ui,y+4f*ui);d.setColor(0xFFFFD36A);d.fillOval(x-1.5f*ui,y-4.5f*ui,x+1.5f*ui,y+1f*ui);
    }

    private void drawVeins(Draw d){for(Vein v:veins)if(!v.dead||v.death<.55f){drawVein(d,v);if(priorityKind==PriorityKind.VEIN&&v==priorityVein&&!v.dead)drawPriorityMarker(d,v);}}
    private float appearScale(float remaining,float duration){float p=1f-Math.min(1f,Math.max(0f,remaining/duration));p=ease(p);return .18f+.82f*p;}
    private void drawArrivalRing(Draw d,float x,float y,float radius,float remaining,float duration,int color){
        if(remaining<=0)return;float p=1f-Math.min(1f,remaining/duration),fade=1f-p;float r=radius*(.55f+p*.85f);
        d.setColor(alpha(color,.18f+.45f*fade));d.fillCircle(x,y,r*.50f);
        d.setColor(alpha(color,.35f+.55f*fade));d.strokeWidth=(1f+fade*1.6f)*ui;d.strokeCircle(x,y,r);
        for(int i=0;i<4;i++){float a=i*1.5708f+elapsed*4.5f;d.fillCircle(x+(float)Math.cos(a)*r,y+(float)Math.sin(a)*r,(1.2f+fade*1.4f)*ui);}
    }
    private void drawPriorityMarker(Draw d,Vein v){float p=.5f+.5f*(float)Math.sin(priorityPulse*6f);float rr=v.r*(1.35f+.10f*p);d.setColor(0x66FFD35A);d.strokeWidth=(1.4f+p*1.2f)*ui;d.strokeCircle(v.x,v.y,rr);d.setColor(0xCCFFD35A);d.pathReset();d.moveTo(v.x,v.y-v.r*1.65f);d.lineTo(v.x-v.r*.22f,v.y-v.r*1.35f);d.lineTo(v.x+v.r*.22f,v.y-v.r*1.35f);d.closePath();d.fillPath();}
    private void drawPriorityOverlay(Draw d){
        if(priorityKind==PriorityKind.NONE)return;
        float p=.5f+.5f*(float)Math.sin(priorityPulse*6f),x=priorityX,y=priorityY,r=13f*ui+p*3f*ui;
        if(priorityKind==PriorityKind.MOB&&priorityMob!=null&&!priorityMob.dead){x=priorityMob.x;y=priorityMob.y;r=priorityMob.type.size*ui*(.78f+.08f*p);}
        if(priorityKind==PriorityKind.VEIN)return;
        d.setColor(priorityKind==PriorityKind.MOB?0xAAFF6654:0xAAFFD35A);d.strokeWidth=(1.5f+p)*ui;d.strokeCircle(x,y,r);
        d.line(x-r*1.25f,y,x-r*.55f,y);d.line(x+r*.55f,y,x+r*1.25f,y);d.line(x,y-r*1.25f,x,y-r*.55f);d.line(x,y+r*.55f,x,y+r*1.25f);
    }
    private void drawVein(Draw d,Vein v){
        float death=v.dead?Math.max(0,1-v.death/.55f):1;float damage=1-Math.max(0,v.hp)/v.maxHp;float shake=v.hitFlash>0?(float)Math.sin(v.hitFlash*210f)*2.1f*ui*(v.hitFlash/.16f):0;float born=appearScale(v.spawn,.58f);float x=v.x,y=v.y,r=v.r;
        if(v.spawn>0)drawArrivalRing(d,x,y,r*1.25f,v.spawn,.58f,adjust(v.type.color,1.18f));
        d.save();d.translate(x,y);d.scale(death*born,death*born);d.translate(-x,-y);d.translate(shake,0);
        d.setColor(0x88000000);polyRock(d,v,x+2f*ui,y+3f*ui,r*1.07f,0xFF090909,0);
        polyRock(d,v,x,y,r,adjust(v.type.color,.62f),0);polyRock(d,v,x-r*.08f,y-r*.10f,r*.82f,adjust(v.type.color,.86f),1);
        drawRockIdentity(d,v,x,y,r,damage);
        // ore veins are embedded lines instead of three arbitrary dots.
        if(v.type!=RockType.STONE&&v.type!=RockType.OBSIDIAN){d.setColor(adjust(v.type.color,1.38f));d.strokeWidth=Math.max(1.4f*ui,r*.10f);for(int i=0;i<3;i++){float yy=y-r*.42f+i*r*.34f;d.line(x-r*.48f,yy,x-r*.10f,yy+r*.15f);d.line(x-r*.10f,yy+r*.15f,x+r*.45f,yy-r*.05f);}}
        drawCracks(d,v,damage);
        if(damage>.45f){d.setColor(0xFF171513);int n=1+(int)(damage*5);for(int i=0;i<n;i++){float a=(v.seed*.000013f+i*2.27f);float rr=r*(.40f+.12f*(i%2));d.fillCircle(x+(float)Math.cos(a)*rr,y+(float)Math.sin(a)*rr,r*(.075f+.025f*(i%3)));}}
        if(v.type.regenPerSecond>0&&!v.dead){float pulse=.5f+.5f*(float)Math.sin(elapsed*3.4f+v.seed);d.setColor(v.type==RockType.OBSIDIAN?0x339A72E8:0x448A5CFF);d.strokeWidth=(1.2f+pulse)*ui;d.strokeCircle(x,y,r+3f*ui+pulse*2f*ui);}
        if(v.hitFlash>0){d.setColor(0x66FFFFFF);d.fillCircle(x,y,r*(.35f+v.hitFlash));}
        d.restore();
    }

    private void polyRock(Draw d,Vein v,float x,float y,float r,int color,int layer){d.setColor(color);d.pathReset();int n=9;for(int i=0;i<n;i++){float a=(float)(Math.PI*2*i/n)+((v.seed>>(i%16))&7)*.009f;float sc=.80f+hash01((long)v.seed*31+i*101+layer*77)*.25f;float px=x+(float)Math.cos(a)*r*sc,py=y+(float)Math.sin(a)*r*sc;if(i==0)d.moveTo(px,py);else d.lineTo(px,py);}d.closePath();d.fillPath();}

    private void drawRockIdentity(Draw d,Vein v,float x,float y,float r,float damage){
        switch(v.type){
            case STONE -> {
                d.setColor(0xFF555B61);for(int i=0;i<4;i++){float a=i*1.51f+v.seed*.00007f;d.fillOval(x+(float)Math.cos(a)*r*.38f-r*.13f,y+(float)Math.sin(a)*r*.30f-r*.07f,x+(float)Math.cos(a)*r*.38f+r*.13f,y+(float)Math.sin(a)*r*.30f+r*.07f);}
            }
            case SILVER -> {
                d.setColor(0xFFE7EEF4);d.strokeWidth=Math.max(1.2f*ui,r*.075f);for(int i=-1;i<=1;i++)d.line(x-r*.46f,y+i*r*.22f,x+r*.42f,y+(i-.55f)*r*.22f);d.setColor(0xFF8F9BA6);d.fillCircle(x-r*.25f,y+r*.23f,r*.09f);d.fillCircle(x+r*.28f,y-r*.20f,r*.07f);
            }
            case GOLD -> {
                d.setColor(0xFFFFD24E);for(int i=0;i<6;i++){float a=i*1.047f+v.seed*.0001f;float rr=r*(i%2==0?.34f:.22f);d.fillCircle(x+(float)Math.cos(a)*rr,y+(float)Math.sin(a)*rr,r*(.085f+(i%3)*.018f));}d.setColor(0xFFFFF0A6);d.fillCircle(x-r*.18f,y-r*.18f,r*.055f);
            }
            case DIAMOND -> {
                d.setColor(0xFFB9F4FF);d.pathReset();d.moveTo(x,y-r*.62f);d.lineTo(x+r*.46f,y-r*.10f);d.lineTo(x+r*.22f,y+r*.53f);d.lineTo(x-r*.25f,y+r*.50f);d.lineTo(x-r*.48f,y-r*.08f);d.closePath();d.fillPath();d.setColor(0xFF4AB8D4);d.strokeWidth=1.4f*ui;d.line(x,y-r*.58f,x,y+r*.46f);d.line(x-r*.44f,y-r*.07f,x+r*.43f,y-r*.07f);d.line(x,y+r*.46f,x+r*.43f,y-r*.07f);
            }
            case OBSIDIAN -> {
                d.setColor(0xFF17131D);for(int i=0;i<4;i++){float a=i*1.57f+.3f;d.pathReset();d.moveTo(x,y-r*.62f);d.lineTo(x+(float)Math.cos(a)*r*.64f,y+(float)Math.sin(a)*r*.55f);d.lineTo(x+(float)Math.cos(a+.7f)*r*.33f,y+(float)Math.sin(a+.7f)*r*.31f);d.closePath();d.fillPath();}d.setColor(0xFF9A72E8);d.strokeWidth=1.2f*ui;d.line(x-r*.35f,y+r*.28f,x+r*.18f,y-r*.33f);d.line(x+r*.18f,y-r*.33f,x+r*.42f,y+r*.08f);
            }
            case ANCIENT_CRYSTAL -> {
                d.setColor(0x448A5CFF);d.fillCircle(x,y,r*.78f);int[] cols={0xFFBFA8FF,0xFF8A5CFF,0xFFD9CEFF};for(int i=0;i<3;i++){float ox=(i-1)*r*.28f,top=r*(.72f-(i%2)*.18f);d.setColor(cols[i]);d.pathReset();d.moveTo(x+ox,y-top);d.lineTo(x+ox+r*.18f,y+r*.38f);d.lineTo(x+ox-r*.18f,y+r*.38f);d.closePath();d.fillPath();}d.setColor(0xFFFFFFFF);d.fillCircle(x-r*.04f,y-r*.30f,r*.045f);
            }
        }
    }
    private void drawCracks(Draw d,Vein v,float damage){if(damage<.15f)return;d.setColor(0xFF171513);d.strokeWidth=(1.2f+damage*.8f)*ui;int branches=1+(int)(damage*6);for(int i=0;i<branches;i++){float a=(v.seed*.0001f+i*2.399f);float len=v.r*(.20f+.55f*damage);float x1=v.x+(float)Math.cos(a)*v.r*.08f,y1=v.y+(float)Math.sin(a)*v.r*.08f;float mx=x1+(float)Math.cos(a)*len*.52f+(float)Math.sin(a)*len*.12f, my=y1+(float)Math.sin(a)*len*.52f-(float)Math.cos(a)*len*.12f;float x2=x1+(float)Math.cos(a)*len,y2=y1+(float)Math.sin(a)*len;d.line(x1,y1,mx,my);d.line(mx,my,x2,y2);if(damage>.52f)d.line(mx,my,mx+(float)Math.cos(a+1.0f)*len*.30f,my+(float)Math.sin(a+1.0f)*len*.30f);}}

    private void drawChest(Draw d){
        float x=cx(map.startCol),y=cy(map.startRow),s=Math.min(cellW,cellH);
        d.setColor(0x66000000);d.fillOval(x-s*.38f,y+s*.18f,x+s*.38f,y+s*.34f);d.setColor(0xFF5A351E);d.fillRoundRect(x-s*.29f,y-s*.14f,x+s*.29f,y+s*.22f,4f*ui);d.setColor(0xFF8A572C);d.fillRoundRect(x-s*.30f,y-s*.28f,x+s*.30f,y-s*.02f,8f*ui);d.setColor(0xFFD4A342);d.fillRect(x-s*.035f,y-s*.15f,x+s*.035f,y+s*.12f);d.fillCircle(x,y+s*.04f,s*.045f);
    }
    private void drawGuardian(Draw d,float x,float y,float s){
        float bob=(float)Math.sin(elapsed*3.1f)*1.0f*ui,attack=guardianAttackAnim>0?(float)Math.sin((.34f-guardianAttackAnim)/.34f*Math.PI):0,dir=guardianTarget!=null&&guardianTarget.x<x?-1f:1f,appear=guardianSpawnAnim>0?appearScale(guardianSpawnAnim,.70f):1f;
        if(guardianSpawnAnim>0)drawArrivalRing(d,x,y,s*.80f,guardianSpawnAnim,.70f,0xFF70D7FF);d.save();d.translate(x,y+bob);d.scale(dir*appear,appear);
        d.setColor(0x66000000);d.fillOval(-s*.38f,s*.48f,s*.40f,s*.62f);d.setColor(0xFF44352B);d.strokeWidth=s*.13f;d.line(-s*.12f,s*.28f,-s*.20f,s*.55f);d.line(s*.12f,s*.28f,s*.20f,s*.55f);
        d.setColor(guardianHitFlash>0?0xFFF0C080:0xFF496A78);d.fillRoundRect(-s*.30f,-s*.02f,s*.30f,s*.35f,s*.08f);d.setColor(0xFFE2B080);d.fillCircle(0,-s*.28f,s*.23f);d.setColor(0xFFEEE9DB);d.pathReset();d.moveTo(-s*.20f,-s*.16f);d.quadTo(0,s*.16f,s*.20f,-s*.16f);d.lineTo(s*.10f,s*.10f);d.lineTo(-s*.10f,s*.10f);d.closePath();d.fillPath();d.setColor(0xFF70828C);d.fillRoundRect(-s*.25f,-s*.50f,s*.25f,-s*.37f,s*.04f);
        d.save();d.translate(s*.24f,-s*.02f);d.rotate(-74f+attack*118f);d.setColor(0xFF6B4329);d.strokeWidth=s*.085f;d.line(0,0,s*.70f,0);d.setColor(0xFFB9C3C9);d.pathReset();d.moveTo(s*.54f,-s*.27f);d.lineTo(s*.82f,-s*.20f);d.lineTo(s*.84f,s*.20f);d.lineTo(s*.55f,s*.30f);d.lineTo(s*.63f,0);d.closePath();d.fillPath();d.setColor(0xFFE4EDF1);d.strokeWidth=1.2f*ui;d.line(s*.58f,-s*.20f,s*.76f,-s*.15f);d.restore();d.restore();
    }
    private void drawGuardianHealth(Draw d,float x,float y,float s){if(guardianDead||guardianMaxHp<=0)return;float pct=Math.max(0,guardianHp/guardianMaxHp),bw=s*1.25f;d.setColor(0xCC101314);d.fillRoundRect(x-bw/2,y-s*.91f,x+bw/2,y-s*.82f,2f*ui);d.setColor(0xFF62BFD5);d.fillRoundRect(x-bw/2,y-s*.91f,x-bw/2+bw*pct,y-s*.82f,2f*ui);}

    private void drawWorker(Draw d,Worker w){
        float s=w.tier.size*ui,scale=appearScale(w.spawn,.52f);
        if(w.spawn>0)drawArrivalRing(d,w.x,w.y,s*.76f,w.spawn,.52f,w.tier.color);
        if(w.spawn>0){d.save();d.translate(w.x,w.y);d.scale(scale,scale);d.translate(-w.x,-w.y);}
        switch(w.tier){case MINER,VETERAN,TWIN_PICK->drawDwarf(d,w,s);case DRILL_RIG->drawDrill(d,w,s);case EXCAVATOR->drawExcavator(d,w,s);case IRON_GOLEM->drawIron(d,w,s);}
        if(w.spawn>0)d.restore();
    }

    private float strikeProgress(Worker w,float duration){return w.swing<=0?0:1-w.swing/duration;}
    private float pickAngle(Worker w){if(w.action!=WorkerAction.MINE&&w.action!=WorkerAction.FIGHT)return 8f;float p=strikeProgress(w,w.action==WorkerAction.FIGHT?.46f:.58f);if(p<.28f)return lerp(18f,-92f,ease(p/.28f));if(p<.60f)return lerp(-92f,50f,ease((p-.28f)/.32f));return lerp(50f,10f,ease((p-.60f)/.40f));}
    private void drawDwarf(Draw d,Worker w,float s){
        float moving=(w.action==WorkerAction.WALK||w.action==WorkerAction.CARRY)?1:0;float stride=(float)Math.sin(w.walkCycle+w.phase)*moving;float bounce=Math.abs((float)Math.cos(w.walkCycle+w.phase))*1.5f*ui*moving;float lean=w.action==WorkerAction.MINE?-.06f:0;float dir=facing(w);d.save();d.translate(w.x,w.y-bounce);d.scale(dir,1);
        if(w.action==WorkerAction.STUNNED)d.rotate((float)Math.sin(elapsed*17+w.phase)*6);
        d.setColor(0x55000000);d.fillOval(-s*.38f,s*.50f+bounce,s*.38f,s*.64f+bounce);
        // planted feet, separate from the torso, make walking read at tiny phone scale.
        d.setColor(0xFF3A2C25);d.strokeWidth=s*.12f;d.line(-s*.11f,s*.29f,-s*.18f+stride*s*.14f,s*.55f);d.line(s*.11f,s*.29f,s*.18f-stride*s*.14f,s*.55f);d.fillOval(-s*.30f+stride*s*.14f,s*.50f,-s*.06f+stride*s*.14f,s*.60f);d.fillOval(s*.06f-stride*s*.14f,s*.50f,s*.30f-stride*s*.14f,s*.60f);
        d.setColor(adjust(w.tier.color,.62f));d.fillOval(-s*.30f,-s*.01f,s*.30f,s*.38f);d.setColor(adjust(w.tier.color,1.28f));d.strokeWidth=s*.035f;d.line(-s*.19f,s*.03f,-s*.19f,s*.28f);d.setColor(0xFF5D3E28);d.fillRect(-s*.31f,s*.20f,s*.31f,s*.27f);d.setColor(0xFFD5A73A);d.fillRect(-s*.04f,s*.19f,s*.05f,s*.28f);d.setColor(0xFFB8833B);d.fillCircle(-s*.27f,s*.04f,s*.065f);
        d.setColor(0xFFE5B686);d.fillCircle(0,-s*.22f,s*.25f);d.fillCircle(-s*.24f,-s*.20f,s*.07f);
        // beard is layered and sways opposite the stride.
        float sway=-stride*s*.035f;d.setColor(w.tier==GnomeTier.VETERAN?0xFFD8D4C9:0xFFECE7DA);d.pathReset();d.moveTo(-s*.23f,-s*.12f);d.quadTo(sway,s*.39f,s*.25f,-s*.12f);d.quadTo(sway,s*.22f,-s*.23f,-s*.12f);d.closePath();d.fillPath();d.setColor(0xFFC9C5BA);d.pathReset();d.moveTo(-s*.08f,-s*.06f);d.quadTo(sway,s*.30f,s*.06f,-s*.03f);d.lineTo(s*.15f,-s*.11f);d.closePath();d.fillPath();
        d.setColor(0xFFF0C094);d.fillCircle(s*.20f,-s*.22f,s*.065f);d.setColor(0xFF181614);d.fillCircle(s*.13f,-s*.29f,s*.025f);
        d.setColor(w.tier.color);d.pathReset();d.moveTo(-s*.27f,-s*.39f);d.quadTo(-s*.03f,-s*.84f,s*.18f,-s*.44f);d.quadTo(s*.29f,-s*.39f,s*.34f,-s*.36f);d.lineTo(-s*.28f,-s*.34f);d.closePath();d.fillPath();d.setColor(adjust(w.tier.color,1.18f));d.fillOval(-s*.29f,-s*.40f,s*.31f,-s*.32f);
        float angle=pickAngle(w);drawAnimatedPick(d,s*.13f,-s*.01f,s,angle,1);
        if(w.tier==GnomeTier.TWIN_PICK)drawAnimatedPick(d,-s*.10f,s*.03f,s,-angle*.82f,-1);
        if(w.tier==GnomeTier.VETERAN){d.setColor(0xFFC79A3B);d.fillCircle(-s*.30f,s*.02f,s*.09f);}
        d.restore();if(w.hasCargo())drawSack(d,w,s);
    }
    private void drawAnimatedPick(Draw d,float x,float y,float s,float angle,float hand){
        d.save();d.translate(x,y);d.rotate(angle);d.setColor(0xFFF0BE8C);d.fillCircle(0,0,s*.065f);
        d.setColor(0xFF4A2E1C);d.strokeWidth=s*.105f;d.line(0,0,s*.60f,0);d.setColor(0xFF8B5B32);d.strokeWidth=s*.065f;d.line(0,0,s*.60f,0);
        d.setColor(0xFF59646A);d.strokeWidth=s*.135f;d.line(s*.54f,-s*.22f,s*.54f,s*.22f);d.setColor(0xFFDCE5E9);d.strokeWidth=s*.035f;d.line(s*.50f,-s*.18f,s*.50f,s*.15f);d.restore();
    }

    private void drawSack(Draw d,Worker w,float s){float bob=(float)Math.sin(w.walkCycle+w.phase)*1.3f*ui;d.setColor(0xFF735139);d.fillCircle(w.x-s*.38f,w.y+s*.18f+bob,Math.max(4f*ui,s*.18f));d.setColor(0xFFC7A16A);d.fillRect(w.x-s*.48f,w.y-s*.02f+bob,w.x-s*.30f,w.y+s*.04f+bob);}

    private void drawDrill(Draw d,Worker w,float s){float stride=(float)Math.sin(w.walkCycle+w.phase);float mining=w.action==WorkerAction.MINE?strikeProgress(w,.58f):0;float dir=facing(w);d.save();d.translate(w.x,w.y);d.scale(dir,1);d.setColor(0x55000000);d.fillOval(-s*.55f,s*.40f,s*.55f,s*.60f);d.setColor(0xFF403A34);d.fillRoundRect(-s*.48f,s*.12f,s*.40f,s*.45f,s*.10f);d.setColor(0xFF282827);for(int i=0;i<3;i++){float xx=-s*.32f+i*s*.31f;d.fillCircle(xx,s*.45f,s*.12f);d.setColor(0xFF60666A);d.fillCircle(xx,s*.45f,s*.052f);d.setColor(0xFF282827);}
        d.setColor(w.tier.color);d.fillRoundRect(-s*.38f,-s*.16f,s*.25f,s*.23f,s*.08f);d.setColor(0xFFE2B382);d.fillCircle(-s*.10f,-s*.24f,s*.16f);d.setColor(0xFFE8E5DC);d.pathReset();d.moveTo(-s*.22f,-s*.17f);d.lineTo(s*.05f,s*.10f);d.lineTo(s*.07f,-s*.11f);d.closePath();d.fillPath();
        float drill=1+.08f*(float)Math.sin(elapsed*34f);d.setColor(0xFFB9C4CB);d.pathReset();d.moveTo(s*.25f,-s*.07f);d.lineTo(s*.84f,s*.05f);d.lineTo(s*.25f,s*.18f);d.closePath();d.fillPath();d.setColor(0xFF68737A);d.strokeWidth=1.3f*ui;for(int i=0;i<5;i++){float xx=s*(.34f+i*.095f);float yy=s*(.01f+.06f*(float)Math.sin(elapsed*31+i*1.7f));d.line(xx,yy,xx+s*.09f,yy+s*.12f);}
        if(w.action==WorkerAction.MINE&&mining>.45f)spawnVisualDustHint(d,s);
        d.restore();if(w.hasCargo())drawSack(d,w,s);
    }
    private void spawnVisualDustHint(Draw d,float s){d.setColor(0x448E8174);for(int i=0;i<3;i++){float a=elapsed*4+i*2.1f;d.fillCircle(s*(.72f+(float)Math.cos(a)*.08f),s*(.07f+(float)Math.sin(a)*.10f),(2+i)*ui);}}

    private void drawExcavator(Draw d,Worker w,float s){
        float dir=facing(w),pulse=.5f+.5f*(float)Math.sin(elapsed*18f+w.phase);float tx=w.vein!=null?w.vein.x:(w.mob!=null?w.mob.x:w.x+dir*s),ty=w.vein!=null?w.vein.y:(w.mob!=null?w.mob.y:w.y);
        if((w.action==WorkerAction.MINE||w.action==WorkerAction.FIGHT)&&w.swing>0){d.setColor(0x3346E7FF);d.strokeWidth=5f*ui;d.line(w.x+dir*s*.38f,w.y-s*.10f,tx,ty);d.setColor(0xFF8AF3FF);d.strokeWidth=(1.2f+pulse)*ui;d.line(w.x+dir*s*.38f,w.y-s*.10f,tx,ty);d.fillCircle(tx,ty,(2.2f+pulse*2f)*ui);}
        d.save();d.translate(w.x,w.y);d.scale(dir,1);d.setColor(0x55000000);d.fillOval(-s*.62f,s*.44f,s*.62f,s*.62f);
        d.setColor(0xFF2D3235);d.fillRoundRect(-s*.58f,s*.18f,s*.52f,s*.48f,s*.10f);d.setColor(0xFF666F74);for(int i=0;i<5;i++)d.fillCircle(-s*.42f+i*s*.20f,s*.43f,s*.085f);
        d.setColor(0xFF4E6A78);d.fillRoundRect(-s*.31f,-s*.02f,s*.23f,s*.26f,s*.07f);
        d.setColor(0xFFE2B080);d.fillCircle(-s*.05f,-s*.30f,s*.18f);d.setColor(0xFFF1EEE3);d.pathReset();d.moveTo(-s*.16f,-s*.20f);d.quadTo(-s*.02f,s*.02f,s*.16f,-s*.18f);d.lineTo(s*.08f,s*.05f);d.lineTo(-s*.12f,s*.03f);d.closePath();d.fillPath();d.setColor(w.tier.color);d.pathReset();d.moveTo(-s*.22f,-s*.46f);d.lineTo(s*.02f,-s*.66f);d.lineTo(s*.20f,-s*.42f);d.closePath();d.fillPath();
        d.setColor(0xFF89979E);d.strokeWidth=s*.10f;d.line(s*.16f,s*.05f,s*.48f,-s*.12f);d.setColor(0xFFB8EAF5);d.fillRoundRect(s*.37f,-s*.22f,s*.58f,-s*.03f,s*.04f);d.setColor(0xFF62ECFF);d.fillCircle(s*.57f,-s*.13f,s*.06f);d.restore();if(w.hasCargo())drawSack(d,w,s);
    }

    private void drawIron(Draw d,Worker w,float s){
        float stride=(float)Math.sin(w.walkCycle+w.phase),dir=facing(w),tx=w.vein!=null?w.vein.x:(w.mob!=null?w.mob.x:w.x+dir*s),ty=w.vein!=null?w.vein.y:(w.mob!=null?w.mob.y:w.y),beam=(w.action==WorkerAction.MINE||w.action==WorkerAction.FIGHT)&&w.swing>0?(float)Math.sin(strikeProgress(w,w.action==WorkerAction.FIGHT?.46f:.58f)*Math.PI):0;
        if(beam>.12f){d.setColor(0x445DEBFF);d.strokeWidth=5f*ui*beam;d.line(w.x+dir*s*.10f,w.y-s*.38f,tx,ty);d.setColor(0xFFD3FBFF);d.strokeWidth=(1.1f+beam)*ui;d.line(w.x+dir*s*.10f,w.y-s*.38f,tx,ty);d.setColor(0xFF67E9FF);d.fillCircle(tx,ty,(2f+3f*beam)*ui);}
        d.save();d.translate(w.x,w.y-Math.abs(stride)*.8f*ui);d.scale(dir,1);d.setColor(0x66000000);d.fillOval(-s*.50f,s*.48f,s*.50f,s*.65f);d.setColor(0xFF56636A);d.fillRoundRect(-s*.36f,-s*.02f,s*.36f,s*.44f,s*.09f);d.setColor(0xFF9EACB5);d.fillCircle(0,-s*.34f,s*.29f);d.setColor(0xFF232A2E);d.fillRect(-s*.21f,-s*.43f,s*.21f,-s*.32f);d.setColor(beam>.12f?0xFFE6FFFF:0xFF67E9FF);d.fillCircle(s*.10f,-s*.38f,s*.055f);d.setColor(0xFF3E515A);d.fillCircle(-s*.10f,-s*.38f,s*.035f);d.setColor(0xFFCAD4D9);d.pathReset();d.moveTo(-s*.20f,-s*.18f);d.lineTo(0,s*.20f);d.lineTo(s*.20f,-s*.18f);d.lineTo(s*.08f,s*.14f);d.lineTo(-s*.08f,s*.14f);d.closePath();d.fillPath();d.setColor(0xFF69757B);d.strokeWidth=s*.15f;d.line(-s*.30f,s*.04f,-s*.52f,s*.34f);d.line(s*.30f,s*.04f,s*.52f,s*.34f);d.line(-s*.15f,s*.42f,-s*.22f+stride*s*.04f,s*.66f);d.line(s*.15f,s*.42f,s*.22f-stride*s*.04f,s*.66f);d.restore();if(w.hasCargo())drawSack(d,w,s);
    }
    private float facing(Worker w){if(Math.abs(w.vx)>1)return w.vx<0?-1:1;if(w.vein!=null&&w.action==WorkerAction.MINE)return w.vein.x<w.x?-1:1;if(w.mob!=null)return w.mob.x<w.x?-1:1;return 1;}

    private void drawMob(Draw d,Mob m){
        if(m.dead)return;float sz=m.type.size*ui,scale=appearScale(m.spawn,.65f);
        if(m.spawn>0)drawArrivalRing(d,m.x,m.y,sz*.82f,m.spawn,.65f,m.type.color);
        if(m.spawn>0){d.save();d.translate(m.x,m.y);d.scale(scale,scale);d.translate(-m.x,-m.y);}
        switch(m.type){case IMP,IMP_KING->drawImp(d,m,sz);case DEMON,DEMON_KING->drawDemon(d,m,sz);case SUCCUBUS->drawSuccubus(d,m,sz);case GHOST->drawGhost(d,m,sz);default->drawGolem(d,m,sz);}
        float pct=Math.max(0,m.hp/m.maxHp);if(pct<.999f||m.type.isBoss()){float bw=sz*1.5f;d.setColor(0xCC120E0D);d.fillRoundRect(m.x-bw/2,m.y-sz*.95f,m.x+bw/2,m.y-sz*.84f,2f*ui);d.setColor(0xFFE34F43);d.fillRoundRect(m.x-bw/2,m.y-sz*.95f,m.x-bw/2+bw*pct,m.y-sz*.84f,2f*ui);}
        if(m.spawn>0)d.restore();
    }

    private void drawImp(Draw d,Mob m,float s){
        float panic=m.flee>0?1f:0f;float hop=Math.abs((float)Math.sin(m.walkCycle+m.phase))*s*.11f,flap=(float)Math.sin(elapsed*13f+m.phase),steal=m.attack>0?(float)Math.sin((.42f-m.attack)/.42f*Math.PI):0;float dir=m.goalCell>=0&&cx(map.col(m.goalCell))<m.x?-1:1;if(panic>0)hop+=Math.abs((float)Math.sin(elapsed*22+m.phase))*s*.10f;
        d.save();d.translate(m.x,m.y-hop);d.scale(dir,1);if(m.type==EnemyType.IMP_KING){d.setColor(0x22FF4A32);d.fillCircle(0,0,s*.92f*(1+.07f*(float)Math.sin(elapsed*5)));}
        // leathery wings
        d.setColor(adjust(m.type.color,.48f));d.pathReset();d.moveTo(-s*.18f,-s*.08f);d.lineTo(-s*(.65f+.10f*flap),-s*.44f);d.lineTo(-s*.50f,s*.02f);d.lineTo(-s*.30f,s*.20f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.18f,-s*.08f);d.lineTo(s*(.65f+.10f*flap),-s*.44f);d.lineTo(s*.50f,s*.02f);d.lineTo(s*.30f,s*.20f);d.closePath();d.fillPath();
        // body/head
        d.setColor(adjust(m.type.color,.78f));d.fillOval(-s*.29f,-s*.12f,s*.29f,s*.48f);d.setColor(m.type.color);d.fillCircle(0,-s*.32f,s*.30f);
        // long ivory horns
        d.setColor(0xFFF0D8A6);d.pathReset();d.moveTo(-s*.21f,-s*.51f);d.lineTo(-s*.55f,-s*.83f);d.lineTo(-s*.34f,-s*.48f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.21f,-s*.51f);d.lineTo(s*.55f,-s*.83f);d.lineTo(s*.34f,-s*.48f);d.closePath();d.fillPath();
        d.setColor(0xFF17110F);d.fillOval(-s*.19f,-s*.40f,-s*.03f,-s*.29f);d.fillOval(s*.03f,-s*.40f,s*.19f,-s*.29f);d.setColor(0xFFFFE45C);d.fillCircle(-s*.10f,-s*.345f,s*.04f);d.fillCircle(s*.10f,-s*.345f,s*.04f);
        // legs and greedy reaching arms
        d.setColor(adjust(m.type.color,.57f));d.strokeWidth=s*.09f;float step=(float)Math.sin(m.walkCycle);d.line(-s*.12f,s*.40f,-s*.27f+step*s*.08f,s*.72f);d.line(s*.12f,s*.40f,s*.27f-step*s*.08f,s*.72f);d.line(s*.24f,s*.08f,s*(.60f+.20f*steal),s*(.19f-.08f*steal));d.line(-s*.24f,s*.08f,-s*.53f,s*.27f);
        // hooked tail makes the thief silhouette unmistakable
        d.strokeWidth=s*.055f;d.line(-s*.22f,s*.32f,-s*.52f,s*.48f);d.line(-s*.52f,s*.48f,-s*.68f,s*.31f);d.setColor(m.type.color);d.pathReset();d.moveTo(-s*.72f,s*.27f);d.lineTo(-s*.57f,s*.30f);d.lineTo(-s*.67f,s*.42f);d.closePath();d.fillPath();
        if(m.type==EnemyType.IMP_KING)drawCrown(d,0,-s*.78f,s*.70f);d.restore();
    }
    private void drawDemon(Draw d,Mob m,float s){
        float stride=(float)Math.sin(m.walkCycle+m.phase),breath=(float)Math.sin(elapsed*3.2f+m.phase)*s*.025f,slash=m.attack>0?(float)Math.sin((.40f-m.attack)/.40f*Math.PI):0;
        d.save();d.translate(m.x,m.y+breath);if(m.type==EnemyType.DEMON_KING){d.setColor(0x288A1F28);d.fillCircle(0,0,s*.98f*(1+.08f*(float)Math.sin(elapsed*4)));}
        d.setColor(0x55000000);d.fillOval(-s*.53f,s*.61f,s*.53f,s*.78f);
        // folded black-red wings behind the torso
        d.setColor(adjust(m.type.color,.42f));d.pathReset();d.moveTo(-s*.25f,-s*.06f);d.lineTo(-s*.68f,-s*.42f);d.lineTo(-s*.55f,s*.28f);d.lineTo(-s*.28f,s*.42f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.25f,-s*.06f);d.lineTo(s*.68f,-s*.42f);d.lineTo(s*.55f,s*.28f);d.lineTo(s*.28f,s*.42f);d.closePath();d.fillPath();
        // broad armored torso
        d.setColor(adjust(m.type.color,.66f));d.fillOval(-s*.39f,-s*.10f,s*.39f,s*.59f);d.setColor(adjust(m.type.color,.90f));d.fillRoundRect(-s*.30f,-s*.03f,s*.30f,s*.28f,s*.06f);d.setColor(0xFF311719);d.strokeWidth=s*.04f;d.line(-s*.23f,s*.02f,s*.23f,s*.21f);d.line(s*.23f,s*.02f,-s*.23f,s*.21f);
        d.setColor(m.type.color);d.fillCircle(0,-s*.40f,s*.34f);
        // swept horns
        d.setColor(0xFFEAD3A2);d.pathReset();d.moveTo(-s*.24f,-s*.59f);d.lineTo(-s*.65f,-s*.88f);d.lineTo(-s*.47f,-s*.51f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.24f,-s*.59f);d.lineTo(s*.65f,-s*.88f);d.lineTo(s*.47f,-s*.51f);d.closePath();d.fillPath();
        // eyes and mouth
        d.setColor(0xFF140C0D);d.fillOval(-s*.20f,-s*.47f,-s*.04f,-s*.35f);d.fillOval(s*.04f,-s*.47f,s*.20f,-s*.35f);d.setColor(0xFFFFD447);d.fillCircle(-s*.12f,-s*.41f,s*.045f);d.fillCircle(s*.12f,-s*.41f,s*.045f);d.setColor(0xFF2A1112);d.fillRect(-s*.13f,-s*.26f,s*.13f,-s*.21f);
        // legs, claw arm and a visible cleaver on attack side
        d.setColor(adjust(m.type.color,.48f));d.strokeWidth=s*.14f;d.line(-s*.18f,s*.51f,-s*.31f+stride*s*.08f,s*.82f);d.line(s*.18f,s*.51f,s*.31f-stride*s*.08f,s*.82f);d.line(s*.32f,s*.04f,s*.61f,s*.25f);
        float ax=-s*(.55f+.22f*slash),ay=s*(.13f-.24f*slash);d.line(-s*.32f,s*.04f,ax,ay);d.setColor(0xFF6D4B31);d.strokeWidth=s*.065f;d.line(ax,ay,ax-s*.18f,ay-s*.24f);d.setColor(0xFFC8D0D5);d.pathReset();d.moveTo(ax-s*.20f,ay-s*.28f);d.lineTo(ax-s*.43f,ay-s*.37f);d.lineTo(ax-s*.28f,ay-s*.10f);d.closePath();d.fillPath();
        if(m.type==EnemyType.DEMON_KING)drawCrown(d,0,-s*.88f,s*.76f);d.restore();
    }
    private void drawSuccubus(Draw d,Mob m,float s){float flap=(float)Math.sin(elapsed*7+m.phase),pulse=.5f+.5f*(float)Math.sin(elapsed*4+m.phase);d.save();d.translate(m.x,m.y);d.setColor(0x33FF4F91);d.fillCircle(0,0,s*(.65f+.12f*pulse));d.setColor(0xFF57213A);d.pathReset();d.moveTo(-s*.18f,-s*.08f);d.lineTo(-s*(.72f+.08f*flap),-s*.42f);d.lineTo(-s*.46f,s*.20f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.18f,-s*.08f);d.lineTo(s*(.72f+.08f*flap),-s*.42f);d.lineTo(s*.46f,s*.20f);d.closePath();d.fillPath();d.setColor(0xFFC94979);d.fillOval(-s*.25f,-s*.10f,s*.25f,s*.48f);d.setColor(0xFFE5AC94);d.fillCircle(0,-s*.34f,s*.25f);d.setColor(0xFF33151F);d.pathReset();d.moveTo(-s*.18f,-s*.52f);d.lineTo(-s*.40f,-s*.78f);d.lineTo(-s*.08f,-s*.60f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.18f,-s*.52f);d.lineTo(s*.40f,-s*.78f);d.lineTo(s*.08f,-s*.60f);d.closePath();d.fillPath();d.setColor(0xFFFFB2D1);d.fillCircle(-s*.09f,-s*.36f,s*.035f);d.fillCircle(s*.09f,-s*.36f,s*.035f);if(m.attack>0){d.setColor(0x99FF5AA0);d.strokeWidth=2f*ui;d.strokeCircle(0,-s*.15f,s*(.55f+.15f*pulse));}d.restore();}
    private void drawGhost(Draw d,Mob m,float s){float wave=(float)Math.sin(elapsed*8+m.phase),a=m.retreating?.34f:.72f;d.save();d.translate(m.x,m.y);d.setColor(alpha(0xFF82D7E7,a*.22f));d.fillCircle(0,0,s*.72f);d.setColor(alpha(0xFFB9F2F7,a));d.pathReset();d.moveTo(-s*.33f,-s*.34f);d.quadTo(0,-s*.68f,s*.34f,-s*.33f);d.lineTo(s*.28f,s*.28f);d.lineTo(s*.12f,s*(.56f+.08f*wave));d.lineTo(-s*.03f,s*.34f);d.lineTo(-s*.20f,s*(.57f-.06f*wave));d.lineTo(-s*.34f,s*.27f);d.closePath();d.fillPath();d.setColor(0xFF203B46);d.fillOval(-s*.18f,-s*.30f,-s*.06f,-s*.15f);d.fillOval(s*.06f,-s*.30f,s*.18f,-s*.15f);d.setColor(alpha(0xFF8DE4F2,a*.35f));for(int i=0;i<3;i++)d.fillOval(-s*(.75f+i*.23f),s*(-.10f+i*.12f),-s*(.45f+i*.23f),s*(.02f+i*.12f));d.restore();}

    private void drawGolem(Draw d,Mob m,float s){float step=(float)Math.sin(m.walkCycle+m.phase),stomp=Math.abs(step)*s*.04f;float punch=m.attack>0?(float)Math.sin((.40f-m.attack)/.40f*Math.PI):0;int col=m.type.color;d.save();d.translate(m.x,m.y-stomp);if(m.type==EnemyType.ELEMENTAL_KING){float pulse=.75f+.25f*(float)Math.sin(elapsed*4);d.setColor(0x287F63D8);d.fillCircle(0,0,s*(.90f+.10f*pulse));}
        d.setColor(0x66000000);d.fillOval(-s*.58f,s*.60f,s*.58f,s*.78f);d.setColor(adjust(col,.62f));d.fillRoundRect(-s*.37f,-s*.02f,s*.37f,s*.52f,s*.10f);d.setColor(adjust(col,.42f));d.fillCircle(0,s*.18f,s*.17f);d.setColor(adjust(col,1.42f));d.fillCircle(0,s*.18f,s*.095f);d.setColor(col);d.fillCircle(0,-s*.39f,s*.32f);d.fillCircle(-s*.47f,s*.06f,s*.22f);d.fillCircle(s*(.47f+.18f*punch),s*(.06f-.12f*punch),s*.22f);d.setColor(adjust(col,.78f));d.fillCircle(-s*.20f,s*.40f,s*.18f);d.fillCircle(s*.20f,s*.40f,s*.18f);d.setColor(0xFFEAF6FF);d.fillCircle(-s*.12f,-s*.41f,s*.047f);d.fillCircle(s*.12f,-s*.41f,s*.047f);
        if(m.type==EnemyType.FIRE_GOLEM||m.type==EnemyType.ELEMENTAL_KING){d.setColor(0xFFFFB12F);for(int i=0;i<3;i++){float a=elapsed*(3+i*.4f)+i*2.1f;d.fillCircle((float)Math.cos(a)*s*.28f,-s*.67f+(float)Math.sin(a)*s*.10f,(.05f+.02f*i)*s);}}
        if(m.type==EnemyType.WATER_GOLEM||m.type==EnemyType.ELEMENTAL_KING){d.setColor(0x664FC6F1);d.strokeWidth=s*.08f;float wave=(float)Math.sin(elapsed*5);d.line(-s*.34f,s*.15f,s*.34f,s*(.15f+.08f*wave));}
        if(m.type==EnemyType.STONE_GOLEM){d.setColor(0xFF4D4942);d.strokeWidth=s*.045f;d.line(-s*.20f,-s*.10f,s*.08f,s*.20f);d.line(s*.08f,s*.20f,s*.25f,s*.03f);}
        if(m.type==EnemyType.ELEMENTAL_KING)drawCrown(d,0,-s*.82f,s*.78f);d.restore();}
    private void drawCrown(Draw d,float x,float y,float w){d.setColor(0xFFFFD24A);d.pathReset();d.moveTo(x-w*.44f,y+w*.20f);d.lineTo(x-w*.40f,y-w*.16f);d.lineTo(x-w*.16f,y+w*.01f);d.lineTo(x,y-w*.28f);d.lineTo(x+w*.16f,y+w*.01f);d.lineTo(x+w*.40f,y-w*.16f);d.lineTo(x+w*.44f,y+w*.20f);d.closePath();d.fillPath();d.setColor(0xFFFFF1A8);d.fillCircle(x,y-w*.10f,w*.045f);}

    private void drawHazards(Draw d){
        for(CaveHazard h:hazards){
            float warning=Math.min(1f,h.age/1.25f);
            switch(h.type){
                case COLLAPSE -> drawCollapseHazard(d,h,warning);
                case FLOOD -> drawFloodHazard(d,h,warning);
                case PIT -> drawPitHazard(d,h,warning);
                case LAVA -> drawLavaHazard(d,h,warning);
            }
        }
    }

    private void drawPitHazard(Draw d,CaveHazard h,float warning){
        float pulse=.5f+.5f*(float)Math.sin(h.age*8f);
        d.setColor(0xFF514A42);for(int i=0;i<11;i++){float a=i*.571f+h.cell*.13f,rr=h.r*(.75f+.09f*(i%3));d.fillCircle(h.x+(float)Math.cos(a)*rr,h.y+(float)Math.sin(a)*rr*.55f,(3.5f+i%3*1.2f)*ui);}
        d.setColor(0xEE020202);d.fillOval(h.x-h.r*.80f,h.y-h.r*.42f,h.x+h.r*.80f,h.y+h.r*.43f);d.setColor(0xFF171411);d.fillOval(h.x-h.r*.55f,h.y-h.r*.27f,h.x+h.r*.55f,h.y+h.r*.31f);
        if(h.age<1.25f){d.setColor(alpha(0xFFE7D6B9,.35f+.35f*pulse));d.strokeWidth=(1.2f+warning)*ui;d.strokeCircle(h.x,h.y,h.r*(.70f+.06f*pulse));}
    }

    private void drawLavaHazard(Draw d,CaveHazard h,float warning){
        float t=Math.max(0,h.age-1.25f),pulse=.5f+.5f*(float)Math.sin(elapsed*5.2f+h.cell);
        // Before ignition it is cracked rock glowing from underneath, not a mysterious red stain.
        d.setColor(0xFF171311);d.fillOval(h.x-h.r*.93f,h.y-h.r*.52f,h.x+h.r*.93f,h.y+h.r*.53f);
        for(int i=0;i<7;i++){
            float a=i*.897f+h.cell*.19f,inner=h.r*.12f,outer=h.r*(.54f+.08f*(i%3));float x1=h.x+(float)Math.cos(a)*inner,y1=h.y+(float)Math.sin(a)*inner*.52f,x2=h.x+(float)Math.cos(a)*outer,y2=h.y+(float)Math.sin(a)*outer*.52f;
            d.setColor(alpha(0xFFFF5B24,h.age<1.25f?.28f+.48f*warning:.72f));d.strokeWidth=(2.7f+(i%2))*ui;d.line(x1,y1,x2,y2);d.setColor(alpha(0xFFFFD35A,h.age<1.25f?.18f+.48f*warning:.82f));d.strokeWidth=1.1f*ui;d.line(x1,y1,x2,y2);
        }
        if(h.age<1.25f)return;
        // Irregular molten pool with bright center and moving bubbles.
        d.setColor(0xCC7B1E13);d.pathReset();int n=12;for(int i=0;i<n;i++){float a=(float)(Math.PI*2*i/n),sc=.68f+hash01(h.cell*997L+i*83L)*.22f;float x=h.x+(float)Math.cos(a)*h.r*sc,y=h.y+(float)Math.sin(a)*h.r*sc*.48f;if(i==0)d.moveTo(x,y);else d.lineTo(x,y);}d.closePath();d.fillPath();
        d.setColor(0xFFFF5725);d.fillOval(h.x-h.r*.57f,h.y-h.r*.25f,h.x+h.r*.57f,h.y+h.r*.27f);d.setColor(alpha(0xFFFFB52F,.72f+.22f*pulse));d.fillOval(h.x-h.r*.36f,h.y-h.r*.15f,h.x+h.r*.36f,h.y+h.r*.17f);d.setColor(0xFFFFE07A);d.fillOval(h.x-h.r*.18f,h.y-h.r*.07f,h.x+h.r*.18f,h.y+h.r*.09f);
        for(int i=0;i<5;i++){float a=i*1.31f+h.cell*.07f,cycle=(t*(.7f+i*.09f)+i*.23f)%1f;float x=h.x+(float)Math.cos(a)*h.r*(.12f+.42f*cycle),y=h.y+(float)Math.sin(a*1.7f)*h.r*.16f;float rr=(1.5f+cycle*3f)*ui;d.setColor(cycle>.72f?0x55FFE89A:0xFFFF9D32);d.strokeWidth=1.2f*ui;d.strokeCircle(x,y,rr);}
    }

    private void drawCollapseHazard(Draw d,CaveHazard h,float warning){
        float pulse=.45f+.35f*(float)Math.sin(h.age*15f);
        if(h.age<1.25f){
            d.setColor(alpha(0xFFE0A06C,.35f+.35f*warning));d.strokeWidth=(1.2f+warning*1.4f)*ui;
            for(int i=0;i<5;i++){float ox=(i-2)*h.r*.20f;d.line(h.x+ox,h.y-h.r*.82f,h.x+ox*.55f,h.y-h.r*(.18f+.08f*(i%2)));}
            d.setColor(alpha(0xFFB7A18A,.30f+.35f*pulse));for(int i=0;i<8;i++){float x=h.x-h.r*.58f+hash01(h.cell*911L+i*73L)*h.r*1.16f;float y=h.y-h.r*.70f+((h.age*32f+i*11)%32)*ui;d.fillCircle(x,y,(1.2f+i%3*.7f)*ui);}
            return;
        }
        float settle=Math.min(1f,(h.age-1.25f)/.55f),rubblePct=h.rubbleMaxHp<=0?1f:Math.max(0,Math.min(1,h.rubbleHp/h.rubbleMaxHp));
        float shrink=.32f+.68f*(float)Math.sqrt(rubblePct);int stones=Math.max(4,Math.round(17*rubblePct));
        d.setColor(0xAA171411);d.fillOval(h.x-h.r*.88f*shrink,h.y-h.r*.10f,h.x+h.r*.88f*shrink,h.y+h.r*.48f*shrink);
        for(int i=0;i<stones;i++){
            float q=hash01(h.cell*971L+i*71L),a=i*2.17f+h.cell*.31f;float rr=h.r*(.10f+.72f*q)*shrink,sz=(4.5f+i%5*1.8f)*ui*settle*shrink;
            float x=h.x+(float)Math.cos(a)*rr,y=h.y+h.r*.12f+(float)Math.sin(a)*rr*.26f;
            d.setColor(i%3==0?0xFF75695D:i%3==1?0xFF4E4841:0xFF62584E);d.fillCircle(x,y,sz);
            if(i%4==0){d.setColor(0xFF9A8A78);d.strokeWidth=1.2f*ui;d.line(x-sz*.7f,y-sz*.2f,x+sz*.6f,y+sz*.25f);}
        }
        d.setColor(0x668F8173);for(int i=0;i<7;i++){float drift=(h.age*13f+i*17f)%38f;d.fillCircle(h.x+(i-3)*h.r*.15f,h.y-h.r*.10f-drift*ui,(2f+i%3)*ui*(1-Math.min(.85f,(h.age-1.25f)/10f)));}
        if(h.rubbleMaxHp>0&&!h.cleared){float pct=Math.max(0,h.rubbleHp/h.rubbleMaxHp),bw=h.r*1.28f,by=h.y-h.r*.72f;d.setColor(0xCC0F0D0C);d.fillRoundRect(h.x-bw/2,by,h.x+bw/2,by+5f*ui,2f*ui);d.setColor(0xFFBA8A54);d.fillRoundRect(h.x-bw/2,by,h.x-bw/2+bw*pct,by+5f*ui,2f*ui);}
    }

    private void drawFloodHazard(Draw d,CaveHazard h,float warning){
        int dir=floodDirection(h.cell);float ux=CaveMap.dx(dir),uy=CaveMap.dy(dir),px=-uy,py=ux;
        float t=Math.max(0,h.age-1.25f),len=h.r*.78f;
        if(h.age<1.25f){
            for(int i=0;i<7;i++){float q=i/6f,along=(q-.5f)*len*1.7f,wob=(float)Math.sin(i*1.7f+h.age*9f)*h.r*.10f;float x=h.x+ux*along+px*wob,y=h.y+uy*along+py*wob;d.setColor(alpha(0xFF75D5DD,.18f+.40f*warning));d.fillCircle(x,y,(1.5f+warning*2.3f+i%2)*ui);}
            return;
        }
        // Dark wet bed first. The visible water itself is a broken chain of translucent moving pools.
        for(int i=0;i<11;i++){
            float q=i/10f,along=(q-.5f)*len*2f,wob=(float)Math.sin(i*1.19f+t*4.3f)*h.r*.09f;
            float x=h.x+ux*along+px*wob,y=h.y+uy*along+py*wob;
            float longR=h.r*(.13f+.025f*(i%3)),shortR=h.r*(.075f+.012f*((i+1)%3));
            float rx=Math.abs(ux)*longR+Math.abs(px)*shortR,ry=Math.abs(uy)*longR+Math.abs(py)*shortR;
            d.setColor(0x55325457);d.fillOval(x-rx*1.15f,y-ry*1.15f,x+rx*1.15f,y+ry*1.15f);
            d.setColor(i%2==0?0xA345909C:0x994A9FAC);d.fillOval(x-rx,y-ry,x+rx,y+ry);
            d.setColor(0x555FC5CC);d.fillOval(x-rx*.72f,y-ry*.60f,x+rx*.72f,y+ry*.60f);
        }
        for(int i=0;i<13;i++){
            float q=(i*.091f+t*(.40f+.025f*(i%4)))%1f,along=(q-.5f)*len*2f,wob=(float)Math.sin(i*2.2f+t*6f)*h.r*.10f;
            float x=h.x+ux*along+px*wob,y=h.y+uy*along+py*wob;
            d.setColor(i%3==0?0xCCDEFFFF:0x8875D9E0);d.fillCircle(x,y,(1.1f+i%3*.55f)*ui);
        }
        // Small eddy at the downstream edge sells direction without drawing a giant blue bar.
        float ex=h.x+ux*len*.72f,ey=h.y+uy*len*.72f;d.setColor(0x775FD0D6);d.strokeWidth=1.1f*ui;d.strokeCircle(ex,ey,h.r*(.12f+.02f*(float)Math.sin(t*4f)));
    }
    private int floodDirection(int cell){
        int c=map.col(cell),r=map.row(cell);int[] order={(cell&1)==0?CaveMap.E:CaveMap.S,CaveMap.N,CaveMap.W,CaveMap.S,CaveMap.E};
        for(int dir:order)if(map.connected(c,r,dir))return dir;return CaveMap.E;
    }

    private void drawFx(Draw d,Fx p){float a=Math.max(0,p.life/p.maxLife);d.setColor(alpha(p.color,a));if(p.spark){d.strokeWidth=Math.max(1f,p.size*.6f);d.line(p.x,p.y,p.x-p.vx*.025f,p.y-p.vy*.025f);}else d.fillCircle(p.x,p.y,p.size*(.45f+.55f*a));}
    private void drawAtmosphere(Draw d){
        int dust=detailTier()>=2?4:detailTier()==1?8:18;for(int i=0;i<dust;i++){float x=((i*73.3f+elapsed*(3+i%4))*ui)%(width+20f*ui)-10f*ui;float y=worldT+((i*119.7f+state.depth*31)%1000)/1000f*(worldB-worldT);d.setColor(0x18D7C7AB);d.fillCircle(x,y,(.7f+i%3*.45f)*ui);}
        d.setColor(0x66000000);d.fillRect(worldL,worldT,worldR,worldT+7f*ui);d.fillRect(worldL,worldB-8f*ui,worldR,worldB);
    }

    private void drawHud(Draw d){
        d.setColor(0xFF0D1012);d.fillRect(0,0,width,worldT);
        d.setColor(0xFF252B30);d.fillRect(0,worldT-2f*ui,width,worldT);
        button(d,back,"‹",true,1.20f);
        d.align=Draw.Align.LEFT;d.bold=true;d.textSize=10.8f*ui;d.setColor(0xFFF2EFE7);d.text("GNOMES",58f*ui,21f*ui);
        d.bold=false;d.textSize=6.2f*ui;d.setColor(UiTheme.GOLD);d.text("ГЛУБИНА "+state.depth,58f*ui,42f*ui);
        d.align=Draw.Align.CENTER;d.textSize=5.4f*ui;d.setColor(levelObjectiveMet()?0xFF79C98A:0xFFE2B544);d.text(levelObjectiveHud(),width*.66f,42f*ui);
        if(levelEvent!=LevelEvent.NONE){d.textSize=4.1f*ui;d.setColor(levelEventColor());d.text(levelEventTitle(),width*.66f,57f*ui);}
        d.align=Draw.Align.LEFT;
        float y=65f*ui,section=width/4f;
        drawResource(d,7f*ui,y,0xFF888D92,"●",state.stone);
        drawResource(d,section+5f*ui,y,0xFFC6D0D8,"Ag",state.silver);
        drawResource(d,section*2+5f*ui,y,0xFFE2B544,"Au",state.gold);
        drawResource(d,section*3+5f*ui,y,0xFF67D7F2,"◆",state.diamond);
        drawActiveEffects(d);
    }
    private void drawActiveEffects(Draw d){float x=width-8f*ui,y=worldT+10f*ui;int n=0;for(int i=0;i<state.artifactActive.length;i++)if(state.artifactOwned(i)&&state.artifactActive[i]){ArtifactType a=ArtifactType.values()[i];x-=15f*ui;d.setColor(alpha(a.color,.28f));d.fillCircle(x,y,6f*ui);d.setColor(a.color);d.strokeWidth=1.2f*ui;d.strokeCircle(x,y,4f*ui);n++;}for(int i=0;i<state.runeActive.length;i++)if(state.runeIsActive(i)){x-=15f*ui;drawRune(d,x,y,3.4f*ui,RuneType.values()[i]);n++;if(n>10)break;}}
    private void drawResource(Draw d,float x,float y,int col,String icon,long n){d.setColor(col);d.fillCircle(x+5f*ui,y-4f*ui,3.6f*ui);d.bold=true;d.textSize=6.4f*ui;d.text(icon+" "+format(n),x+12f*ui,y);d.bold=false;}

    private void drawPanel(Draw d){
        UiTheme.panel(d,0,worldB,width,height,ui);
        String[] names={"ГНОМЫ","АПГРЕЙДЫ","АРТЕФ.","РУНЫ"};
        for(int i=0;i<4;i++)UiTheme.tab(d,tabs[i].l,tabs[i].t,tabs[i].r,tabs[i].b,ui,names[i],tab.ordinal()==i,UiTheme.GOLD);
        switch(tab){case GNOMES->drawGnomePanel(d);case UPGRADES->drawUpgradePanel(d);case ARTIFACTS->drawArtifactPanel(d);case RUNES->drawRunePanel(d);}
        button(d,speed,speedHeld?"ГНОМЫ РАБОТАЮТ ×4":"УСКОРИТЬ ГНОМОВ ×4",true,.86f);
    }

    private float contentTop(){return tabs[0].b+5f*ui;}
    private void drawGnomePanel(Draw d){GnomeTier gt=GnomeTier.values()[selectedTier];float ct=contentTop();button(d,left,"‹",selectedTier>0,1.15f);button(d,right,"›",selectedTier<GnomeTier.values().length-1,1.15f);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=8.2f*ui;d.setColor(gt.color);d.text(gt.title,width/2,ct+16f*ui);d.textSize=6.7f*ui;d.setColor(0xFFF1D58A);d.text("ГНОМОВ: "+state.tierCounts[selectedTier],width*.31f,ct+37f*ui);d.setColor(0xFFB9C8D0);d.text("ДОБЫЧА: "+one.format(gt.miningPower*state.tierPowerMultiplier(selectedTier)*state.miningMultiplier(selectedTier))+"/УДАР",width*.69f,ct+37f*ui);d.align=Draw.Align.LEFT;d.bold=false;drawTierProgress(d,ct+50f*ui);
        if(selectedTier==0)button(d,primary,"КУПИТЬ • "+format(state.minerBuyCost()),true,.72f);else statPill(d,primary,"УР. "+state.tierLevels[selectedTier]+" • БОЙ "+one.format(gt.combatPower*state.combatMultiplier(selectedTier)));button(d,secondary,"УЛУЧШИТЬ • "+format(state.tierUpgradeCost(selectedTier)),true,.66f);boolean merge=selectedTier<GnomeTier.values().length-1&&(GameState.FREE_SHOP||state.tierCounts[selectedTier]>=10);button(d,tertiary,GameState.FREE_SHOP?"TEST • СЛЕДУЮЩИЙ":"СЛИТЬ 10 → 1",merge,.60f);statPill(d,quaternary,"СУМКА • "+format((long)(gt.cargoCapacity*state.carryMultiplier(selectedTier))));}

    private void drawUpgradePanel(Draw d){float ct=contentTop();d.align=Draw.Align.CENTER;d.bold=true;d.textSize=7.0f*ui;d.setColor(0xFFF0F3F5);d.text("ШАХТА И ИНФРАСТРУКТУРА",width/2,ct+17f*ui);d.bold=false;d.align=Draw.Align.LEFT;button(d,primary,"КИРКИ ур."+state.miningUpgrade,true,.70f);button(d,secondary,"ЛОГИСТИКА ур."+state.speedUpgrade,true,.66f);button(d,tertiary,"БОЙ ур."+state.combatUpgrade,true,.70f);button(d,quaternary,state.guardianLevel==0?"НАНЯТЬ СТРАЖА":"СТРАЖ ур."+state.guardianLevel,true,.66f);}
    private void drawArtifactPanel(Draw d){ArtifactType a=ArtifactType.values()[selectedArtifact];float ct=contentTop();button(d,left,"‹",selectedArtifact>0,1.15f);button(d,right,"›",selectedArtifact<ArtifactType.values().length-1,1.15f);boolean owned=state.artifactOwned(selectedArtifact),active=owned&&state.artifactActive[selectedArtifact];d.align=Draw.Align.CENTER;d.bold=true;d.textSize=7.2f*ui;d.setColor(a.color);d.text(a.title,width/2,ct+14f*ui);d.bold=false;d.textSize=4.8f*ui;d.setColor(0xFFB5BFC7);d.text(ellipsize(a.description,27),width/2,ct+30f*ui);d.textSize=4.5f*ui;d.setColor(active?0xFF7FDEA0:0xFFAAB4BB);d.text(owned?(active?"АКТИВЕН":"СНЯТ"):"НЕ КУПЛЕН",width/2,ct+43f*ui);d.align=Draw.Align.LEFT;button(d,primary,owned?(active?"СНЯТЬ":"НАДЕТЬ"):"КУПИТЬ • ◆"+state.artifactCost(selectedArtifact),true,.62f);statPill(d,secondary,"ПОКУПАЕТСЯ 1 РАЗ");statPill(d,tertiary,owned?"КУПЛЕН":"НУЖНЫ ◆ АЛМАЗЫ");statPill(d,quaternary,active?"АКТИВЕН":"НЕ АКТИВЕН");}

    private void drawRunePanel(Draw d){RuneType r=RuneType.values()[selectedRune];float ct=contentTop();button(d,left,"‹",selectedRune>0,1.15f);button(d,right,"›",selectedRune<RuneType.values().length-1,1.15f);boolean active=state.runeIsActive(selectedRune);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=7.2f*ui;d.setColor(r.color);d.text(r.title,width/2,ct+14f*ui);d.bold=false;d.textSize=4.8f*ui;d.setColor(0xFFB7C0C7);d.text(ellipsize(r.description,27),width/2,ct+30f*ui);d.textSize=4.5f*ui;d.setColor(active?0xFF7FDEA0:0xFFAAB4BB);d.text("УР. "+state.runeLevels[selectedRune]+" • "+(active?"АКТИВНА":"СНЯТА"),width/2,ct+43f*ui);d.align=Draw.Align.LEFT;button(d,primary,"УСИЛИТЬ • ◆"+state.runeUpgradeCost(selectedRune),state.runeLevels[selectedRune]<12,.58f);button(d,secondary,active?"СНЯТЬ РУНУ":"АКТИВИРОВАТЬ",state.runeLevels[selectedRune]>0,.54f);statPill(d,tertiary,"НА ВСЕХ ГНОМОВ");statPill(d,quaternary,"МЕТА • НАВСЕГДА");}


    private void button(Draw d,Box b,String text,boolean enabled,float scale){
        int accent=b==speed?UiTheme.GOLD:(b==back?UiTheme.STEEL:UiTheme.COPPER);
        UiTheme.button(d,b.l,b.t,b.r,b.b,ui,text,enabled,accent,b==speed&&speedHeld,scale);
    }
    private void statPill(Draw d,Box b,String text){d.setColor(0x66191D20);d.fillRoundRect(b.l,b.t,b.r,b.b,7f*ui);d.setColor(0xFF252B30);d.fillRoundRect(b.l+1f*ui,b.t+1f*ui,b.r-1f*ui,b.b-1f*ui,6f*ui);d.clipRect(b.l+4f*ui,b.t+2f*ui,b.r-4f*ui,b.b-2f*ui);d.align=Draw.Align.CENTER;d.bold=false;d.textSize=5.3f*ui;d.setColor(0xFF9FAAAF);d.text(text,b.cx(),b.cy()+2f*ui);d.align=Draw.Align.LEFT;d.unclip();}
    private void drawToast(Draw d){if(toastTime<=0)return;float a=Math.min(1,toastTime*2);float w=Math.min(width-40f*ui,280f*ui);d.setColor(alpha(0xDD101316,a));d.fillRoundRect((width-w)/2,worldT+10f*ui,(width+w)/2,worldT+42f*ui,9f*ui);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=9.5f*ui;d.setColor(alpha(0xFFF2F4F5,a));d.text(toast,width/2,worldT+30f*ui);d.align=Draw.Align.LEFT;d.bold=false;}

    private boolean handleTap(float x,float y){
        game.audio.play(GameAudio.Sfx.UI,.45f);
        if(back.hit(x,y)){saveNow();game.openMenu();return true;}
        for(int i=0;i<tabs.length;i++)if(tabs[i].hit(x,y)){tab=Tab.values()[i];return true;}
        if(left.hit(x,y)){switch(tab){case GNOMES->selectedTier=Math.max(0,selectedTier-1);case ARTIFACTS->selectedArtifact=Math.max(0,selectedArtifact-1);case RUNES->selectedRune=Math.max(0,selectedRune-1);default->{}}return true;}
        if(right.hit(x,y)){switch(tab){case GNOMES->selectedTier=Math.min(GnomeTier.values().length-1,selectedTier+1);case ARTIFACTS->selectedArtifact=Math.min(ArtifactType.values().length-1,selectedArtifact+1);case RUNES->selectedRune=Math.min(RuneType.values().length-1,selectedRune+1);default->{}}return true;}
        if(primary.hit(x,y)){switch(tab){case GNOMES->{if(selectedTier==0&&state.buyMiner()){syncWorkers(false);toast="НОВЫЙ ГНОМ";toastTime=1.2f;}else if(selectedTier>0)toast="ГНОМЫ ЭТОГО ТИПА ПОЛУЧАЮТСЯ СЛИЯНИЕМ";}case UPGRADES->buyGlobal(0);case ARTIFACTS->{if(state.artifactOwned(selectedArtifact)){state.toggleArtifact(selectedArtifact);toast=state.artifactActive[selectedArtifact]?"АРТЕФАКТ АКТИВИРОВАН":"АРТЕФАКТ СНЯТ";}else if(state.buyArtifact(selectedArtifact))toast="АРТЕФАКТ КУПЛЕН";else toast="НЕ ХВАТАЕТ АЛМАЗОВ";toastTime=1.2f;}case RUNES->{if(state.upgradeRune(selectedRune))toast="РУНА УСИЛЕНА";else toast="НЕ ХВАТАЕТ АЛМАЗОВ";toastTime=1.2f;}}saveNow();return true;}
        if(secondary.hit(x,y)){switch(tab){case GNOMES->{if(state.upgradeTier(selectedTier))toast="ГНОМ УСИЛЕН";else toast="НЕ ХВАТАЕТ РЕСУРСОВ";toastTime=1.2f;}case UPGRADES->buyGlobal(1);case RUNES->{if(state.toggleRune(selectedRune))toast=state.runeIsActive(selectedRune)?"РУНА АКТИВНА":"РУНА СНЯТА";else toast="СНАЧАЛА СОЗДАЙТЕ РУНУ";toastTime=1.2f;}default->{}}saveNow();return true;}
        if(tertiary.hit(x,y)){switch(tab){case GNOMES->{int next=Math.min(GnomeTier.values().length-1,selectedTier+1);boolean first=selectedTier<GnomeTier.values().length-1&&state.tierCounts[next]==0;if(state.mergeTier(selectedTier)){syncWorkers(false);toast=state.depth==1&&levelObjectiveMet()?"ЦЕЛЬ ВЫПОЛНЕНА • ПРОДВИНУТЫЙ ГНОМ":"ЭВОЛЮЦИЯ • 10 → 1";toastTime=1.6f;if(first){unlockTier=next;unlockAnim=0;addNotice("ОТКРЫТ НОВЫЙ ГНОМ",GnomeTier.values()[next].color,2.4f);game.audio.play(GameAudio.Sfx.COIN,.80f);}}}case UPGRADES->buyGlobal(2);default->{}}saveNow();return true;}
        if(quaternary.hit(x,y)){switch(tab){case UPGRADES->{int before=state.guardianLevel;if(state.buyOrUpgradeGuardian()){toast="СТРАЖ СУНДУКА • ур."+state.guardianLevel;guardianDead=false;guardianMaxHp=state.guardianMaxHp();guardianHp=guardianMaxHp;guardianX=cx(map.startCol)-Math.min(cellW,cellH)*.28f;guardianY=cy(map.startRow);guardianSpawnAnim=.70f;}else toast="НЕ ХВАТАЕТ РЕСУРСОВ";toastTime=1.3f;}case RUNES->{toast="РУНЫ ТЕПЕРЬ ГЛОБАЛЬНЫЕ";toastTime=1.1f;}default->{}}saveNow();return true;}
        return true;
    }

    private boolean handleWorldTap(float x,float y){
        Mob mob=null;float mobD=Float.MAX_VALUE;
        for(Mob m:mobs){if(m.dead)continue;float q=dist2(x,y,m.x,m.y);float rr=Math.max(18f*ui,m.type.size*ui*.75f);if(q<=rr*rr&&q<mobD){mobD=q;mob=m;}}
        if(mob!=null){
            longPressEligible=false;priorityKind=PriorityKind.MOB;priorityMob=mob;priorityVein=null;priorityHazard=null;priorityCell=cellFor(mob.x,mob.y);priorityX=mob.x;priorityY=mob.y;resetWorkerRoutes();
            toast="ПРИКАЗ • АТАКОВАТЬ "+mob.type.title.toUpperCase();toastTime=1.5f;game.audio.play(GameAudio.Sfx.UI,.72f);game.audio.vibrate(18);return true;
        }

        for(CaveHazard h:hazards)if(h.type==HazardType.COLLAPSE&&h.obstacleActive&&!h.cleared&&distance(x,y,h.x,h.y)<=h.r*1.15f){
            longPressEligible=false;priorityKind=PriorityKind.HAZARD;priorityHazard=h;priorityMob=null;priorityVein=null;priorityCell=h.cell;priorityX=h.x;priorityY=h.y;resetWorkerRoutes();
            toast="ПРИКАЗ • РАЗОБРАТЬ ОБВАЛ";toastTime=1.5f;game.audio.play(GameAudio.Sfx.UI,.72f);game.audio.vibrate(18);return true;
        }

        Vein best=null;float bd=Float.MAX_VALUE;
        for(Vein v:veins){if(v.dead)continue;float q=dist2(x,y,v.x,v.y);if(q<bd){bd=q;best=v;}}
        float pickRadius=Math.min(cellW,cellH)*.80f;
        if(best!=null&&bd<=pickRadius*pickRadius){
            longPressEligible=false;priorityKind=PriorityKind.VEIN;priorityVein=best;priorityMob=null;priorityHazard=null;priorityCell=best.cell;priorityX=best.x;priorityY=best.y;resetWorkerRoutes();
            toast="ПРИКАЗ • ДОБЫВАТЬ "+best.type.title.toUpperCase();toastTime=1.5f;game.audio.play(GameAudio.Sfx.UI,.72f);game.audio.vibrate(18);return true;
        }

        priorityKind=PriorityKind.POINT;priorityVein=null;priorityMob=null;priorityHazard=null;priorityCell=cellFor(x,y);priorityX=x;priorityY=y;resetWorkerRoutes();
        toast="ПРИКАЗ • СОБРАТЬСЯ ЗДЕСЬ";toastTime=1.25f;game.audio.play(GameAudio.Sfx.UI,.60f);game.audio.vibrate(14);
        return true;
    }

    private void resetWorkerRoutes(){for(Worker w:workers){w.goalCell=-1;w.path=new int[0];w.pathIndex=0;w.routeRetry=0;}}
    private void clearPriority(boolean notify){priorityKind=PriorityKind.NONE;priorityVein=null;priorityMob=null;priorityHazard=null;priorityCell=-1;if(notify){toast="ПРИОРИТЕТ СНЯТ";toastTime=1f;}}
    private void setupObjective(){
        if(state.depth==1)objectiveType=ObjectiveType.ASCEND_GNOME;else if(state.depth%10==0)objectiveType=ObjectiveType.BOSS_HUNT;else objectiveType=ObjectiveType.values()[Math.floorMod(state.depth+slot*3,5)];objectiveStartKills=state.enemiesDefeated;objectiveTarget=0;objectiveTreasureTarget=0;objectiveStarted=false;
        switch(objectiveType){case GUARDIAN->objectiveTarget=2+Math.min(2,state.depth/15);case DEMON_PURGE->objectiveTarget=3+Math.min(8,state.depth/3);case TREASURE->objectiveTreasureTarget=state.walletValue()+600L+state.depth*220L;default->{}}
    }
    private void updateObjective(){
        if(state.totalGnomes()<5)return;if(objectiveStarted)return;if(objectiveType==ObjectiveType.BOSS_HUNT){objectiveStarted=true;spawnBoss();}else if(objectiveType==ObjectiveType.DEMON_PURGE){objectiveStarted=true;EnemyType[] q=new EnemyType[objectiveTarget+1];for(int i=0;i<objectiveTarget;i++)q[i]=EnemyType.DEMON;q[q.length-1]=state.depth>=20?EnemyType.DEMON_KING:EnemyType.IMP_KING;openPortal(q);toast="ЗАДАНИЕ • ПЕРЕЖИТЬ НАШЕСТВИЕ";toastTime=2f;}}
    private boolean noLivingVeins(){for(Vein v:veins)if(!v.dead)return false;return true;}
    private boolean noHostiles(){if(portal!=null||pendingBoss!=null)return false;for(Mob m:mobs)if(!m.dead)return false;return true;}
    private boolean levelObjectiveMet(){return switch(objectiveType){case ASCEND_GNOME->state.tierCounts[GnomeTier.VETERAN.ordinal()]>=1;case CLEAR_VEINS->noLivingVeins();case GUARDIAN->state.guardianLevel>=objectiveTarget&&!guardianDead;case TREASURE->state.walletValue()>=objectiveTreasureTarget;case DEMON_PURGE,BOSS_HUNT->objectiveStarted&&noHostiles();};}
    private String levelObjectiveShort(){return switch(objectiveType){case ASCEND_GNOME->"ЦЕЛЬ: ОТКРЫТЬ ПРОДВИНУТОГО ГНОМА";case CLEAR_VEINS->"ЦЕЛЬ: ОЧИСТИТЬ ЖИЛЫ";case GUARDIAN->"ЦЕЛЬ: СТРАЖ ур."+objectiveTarget;case DEMON_PURGE->"ЦЕЛЬ: НАШЕСТВИЕ";case BOSS_HUNT->"ЦЕЛЬ: УБИТЬ БОССА";case TREASURE->"ЦЕЛЬ: КАПИТАЛ "+format(objectiveTreasureTarget);};}
    private String levelObjectiveToast(){return levelObjectiveShort();}
    private String levelObjectiveHud(){return switch(objectiveType){
        case ASCEND_GNOME -> "ЦЕЛЬ: ПРОДВИНУТЫЙ "+Math.min(1,state.tierCounts[GnomeTier.VETERAN.ordinal()])+"/1";
        case CLEAR_VEINS -> "ЦЕЛЬ: ОЧИСТИТЬ ЖИЛЫ";
        case GUARDIAN -> "ЦЕЛЬ: СТРАЖ УР."+objectiveTarget;
        case DEMON_PURGE -> "ЦЕЛЬ: НАШЕСТВИЕ";
        case BOSS_HUNT -> "ЦЕЛЬ: УБИТЬ БОССА";
        case TREASURE -> "ЦЕЛЬ: "+format(objectiveTreasureTarget);
    };}


    private void buyGlobal(int kind){if(state.buyGlobalUpgrade(kind)){toast="УЛУЧШЕНИЕ КУПЛЕНО";}else toast="НЕ ХВАТАЕТ РЕСУРСОВ";toastTime=1.2f;}

    private long cargoValue(){
        double value=0;for(Worker w:workers)value+=w.cargoStone+w.cargoSilver*8d+w.cargoGold*20d+w.cargoDiamond*100d;
        return Math.max(0L,Math.round(value));
    }

    private void beginLevelSummary(){
        if(levelSummary)return;speedHeld=false;levelSummary=true;summaryAnim=0;
        summaryEarned=state.levelEarnedValue;summaryInvested=state.levelInvestedValue;summaryWallet=state.walletValue();summaryCapital=state.transferCapital(cargoValue());summaryTransfer=state.transferAmount(cargoValue());summaryCarry=state.carriedGnomesCount();
        saveNow();game.audio.play(GameAudio.Sfx.COIN,.8f);game.audio.vibrate(55);
    }

    private void finishLevelTransition(){
        state.beginNextDepth(summaryTransfer);saveNow();levelSummary=false;summaryAnim=0;generateDepth(false);
    }

    private void beginGameOver(){if(gameOver)return;gameOver=true;speedHeld=false;clearPriority(false);saveNow();game.audio.vibrate(120);}

    private long rolling(long target){float p=Math.min(1f,summaryAnim/1.65f);p=1f-(1f-p)*(1f-p)*(1f-p);return Math.round(target*p);}

    private void drawLevelSummary(Draw d){
        d.setColor(0xE6090705);d.fillRect(0,0,width,height);
        float cw=Math.min(width-18f*ui,390f*ui),l=(width-cw)/2f,t=Math.max(18f*ui,height*.055f),b=Math.min(height-16f*ui,height*.91f),p=Math.min(1f,summaryAnim/1.2f);
        for(int i=0;i<30;i++){float a=i*2.399f+summaryAnim*.3f,rr=(34f+(i%8)*16f)*ui*p,x=width/2+(float)Math.cos(a)*rr,y=t+46f*ui+(float)Math.sin(a)*rr*.46f;d.setColor(i%3==0?0x88FFD35A:i%3==1?0x8877D89A:0x88E77A55);d.fillCircle(x,y,(1.4f+i%3)*ui);}
        d.setColor(0xFF3A2516);d.fillRoundRect(l-4f*ui,t-4f*ui,l+cw+4f*ui,b+4f*ui,18f*ui);d.setColor(0xFF18130F);d.fillRoundRect(l,t,l+cw,b,15f*ui);d.setColor(0xFFF0B85A);d.fillRoundRect(l+22f*ui,t+5f*ui,l+cw-22f*ui,t+9f*ui,2f*ui);
        d.align=Draw.Align.CENTER;d.bold=true;d.textSize=16f*ui;d.setColor(0xFFFFD86B);d.text("ПОБЕДА!",width/2,t+38f*ui);
        d.textSize=8.2f*ui;d.setColor(0xFFF4EFE3);d.text("ГЛУБИНА "+state.depth+" ПОКОРЕНА",width/2,t+66f*ui);
        d.bold=false;d.textSize=5.3f*ui;d.setColor(0xFFC5B9A8);d.text(state.difficultyTitle()+" • "+levelObjectiveShort(),width/2,t+87f*ui);

        float x1=l+22f*ui,x2=l+cw*.76f,y=t+119f*ui,dy=29f*ui;
        d.align=Draw.Align.LEFT;d.textSize=5.8f*ui;d.setColor(0xFFC5B9A8);
        d.text("ЗАРАБОТАНО",x1,y);d.text("ВЛОЖЕНО",x1,y+dy);d.text("КАПИТАЛ",x1,y+dy*2);d.text("ДЕНЕГ ДАЛЬШЕ",x1,y+dy*3);d.text("ГНОМОВ ДАЛЬШЕ",x1,y+dy*4);
        d.align=Draw.Align.CENTER;d.bold=true;d.textSize=6.2f*ui;d.setColor(0xFFFFD56A);
        d.text(format(rolling(summaryEarned)),x2,y);d.text(format(rolling(summaryInvested)),x2,y+dy);d.text(format(rolling(summaryCapital)),x2,y+dy*2);
        d.setColor(0xFF7FDEA0);d.text(format(rolling(summaryTransfer))+" ×"+one.format(state.carryRatio()),x2,y+dy*3);d.text("1 + "+summaryCarry,x2,y+dy*4);

        d.bold=false;d.textSize=5.1f*ui;d.setColor(0xFFA99E90);d.text("1 новый шахтёр + часть старого отряда",width/2,b-74f*ui);d.text("продолжат путь глубже.",width/2,b-58f*ui);
        summaryOk.set(l+18f*ui,b-48f*ui,l+cw-18f*ui,b-10f*ui);
        d.align=Draw.Align.LEFT;button(d,summaryOk,"В ГЛУБИНУ • УРОВЕНЬ "+(state.depth+1),summaryAnim>.75f,.66f);
    }

    private void drawGameOver(Draw d){
        d.setColor(0xE60A0606);d.fillRect(0,0,width,height);float cw=Math.min(width-46f*ui,340f*ui),l=(width-cw)/2f,t=height*.28f,b=height*.61f;
        d.setColor(0xFF1B1515);d.fillRoundRect(l,t,l+cw,b,14f*ui);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=18f*ui;d.setColor(0xFFE66658);d.text("ЭКСПЕДИЦИЯ ПОГИБЛА",width/2,t+54f*ui);d.textSize=10f*ui;d.setColor(0xFFF1E8E3);d.text("ГНОМОВ НЕ ОСТАЛОСЬ",width/2,t+94f*ui);d.bold=false;d.textSize=8.3f*ui;d.setColor(0xFFAAA29F);d.text("И денег на нового гнома тоже нет.",width/2,t+125f*ui);d.align=Draw.Align.LEFT;button(d,gameOverOk,"В МЕНЮ",true,.86f);
    }

    private void routeMob(Mob m,int goal){m.goalCell=goal;m.path=map.path(cellFor(m.x,m.y),goal);m.pathIndex=Math.min(1,m.path.length);}
    private boolean atCell(Worker w,int cell){if(cell<0)return false;return distance(w.x,w.y,cx(map.col(cell)),cy(map.row(cell)))<Math.min(cellW,cellH)*.20f;}
    private int cellFor(float x,float y){int c=Math.max(0,Math.min(map.cols-1,(int)((x-worldL)/cellW)));int r=Math.max(0,Math.min(map.rows-1,(int)((y-worldT)/cellH)));return map.index(c,r);}
    private float cx(int c){return worldL+(c+.5f)*cellW;}private float cy(int r){return worldT+(r+.5f)*cellH;}

    private void saveNow(){game.saves.save(slot,state);}
    @Override public void pause(){saveNow();speedHeld=false;}
    @Override public void hide(){saveNow();speedHeld=false;}

    private void drawRune(Draw d,float x,float y,float s,RuneType r){d.setColor(alpha(r.color,.28f));d.fillCircle(x,y,s*1.65f);d.setColor(r.color);d.strokeWidth=1.1f*ui;switch(r){case MINING->{d.line(x-s,y+s,x+s,y-s);d.line(x-s*.6f,y-s,x+s*.6f,y+s);}case GREED->{d.strokeCircle(x,y,s);d.line(x,y-s,x,y+s);}case WAR->{d.line(x-s,y-s,x+s,y+s);d.line(x+s,y-s,x-s,y+s);}case HASTE->{d.line(x-s,y,x+s,y);d.line(x+s*.25f,y-s*.65f,x+s,y);d.line(x+s*.25f,y+s*.65f,x+s,y);}case WARD->{d.pathReset();d.moveTo(x,y-s);d.lineTo(x+s,y-s*.3f);d.lineTo(x+s*.6f,y+s);d.lineTo(x-s*.6f,y+s);d.lineTo(x-s,y-s*.3f);d.closePath();d.strokePath();}case FRACTURE->{d.line(x-s,y-s,x,y);d.line(x,y,x+s,y-s*.4f);d.line(x,y,x+s*.5f,y+s);}}}
    private String runeName(int idx){return idx>=0&&idx<RuneType.values().length?RuneType.values()[idx].title:"нет";}

    private int detailTier(){int load=workers.size()+mobs.size()*3+fx.size()/7;return load>205?2:load>120?1:0;}

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
            m.summonCooldown=7.6f;float rr=Math.min(cellW,cellH)*1.35f;for(int wi=workers.size()-1;wi>=0;wi--){Worker w=workers.get(wi);if(distance(w.x,w.y,m.x,m.y)<rr){w.stun=Math.max(w.stun,1.1f);if(random.nextFloat()<.08f*(1-state.hazardSurvivalBonus(w.tier.ordinal())))loseWorker(w,"стихийный выброс");}}spawnSparks(m.x,m.y,0xFF8BD7FF,12);screenShake=Math.max(screenShake,6f*ui);addNotice("СТИХИЙНЫЙ ВЫБРОС",0xFF8BD7FF,1.8f);
        }else m.summonCooldown=6f;
    }

    private static float len(float x,float y){return(float)Math.sqrt(x*x+y*y);}private static float distance(float x1,float y1,float x2,float y2){return len(x2-x1,y2-y1);}private static float dist2(float x1,float y1,float x2,float y2){float dx=x2-x1,dy=y2-y1;return dx*dx+dy*dy;}
    private static float lerp(float a,float b,float t){return a+(b-a)*Math.max(0,Math.min(1,t));}private static float ease(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    private static float hash01(long x){x^=x>>>33;x*=0xff51afd7ed558ccdL;x^=x>>>33;x*=0xc4ceb9fe1a85ec53L;x^=x>>>33;return (x&0xFFFFFF)/(float)0x1000000;}
    private static int adjust(int c,float f){int a=(c>>>24)&255,r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;r=Math.min(255,Math.max(0,(int)(r*f)));g=Math.min(255,Math.max(0,(int)(g*f)));b=Math.min(255,Math.max(0,(int)(b*f)));return(a<<24)|(r<<16)|(g<<8)|b;}
    private static int alpha(int c,float a){int aa=Math.max(0,Math.min(255,(int)(a*255)));return(aa<<24)|(c&0xFFFFFF);}
    private String format(long n){if(n>=1_000_000_000L)return one.format(n/1_000_000_000d)+"B";if(n>=1_000_000L)return one.format(n/1_000_000d)+"M";if(n>=10_000L)return one.format(n/1_000d)+"K";return Long.toString(n);}
    private static String ellipsize(String s,int n){return s.length()<=n?s:s.substring(0,Math.max(1,n-1))+"…";}
}
