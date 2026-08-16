package com.enhort.gnomes.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.enhort.gnomes.GnomesGame;
import com.enhort.gnomes.draw.Draw;
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
    private enum Tab { GNOMES, UPGRADES, ARTIFACTS, RUNES }

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
        float phase, walkCycle, swing, attackCooldown, stun;
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
        float hp, hitFlash, death;
        boolean dead;
        Vein(RockType type,int cell,int side,int seed,float x,float y,float r,float hp){this.type=type;this.cell=cell;this.side=side;this.seed=seed;this.x=x;this.y=y;this.r=r;this.maxHp=hp;this.hp=hp;}
    }

    private static final class Mob {
        final EnemyType type;
        float x,y,hp,maxHp,phase,walkCycle,attack,attackCooldown,summonCooldown=5f,routeTimer;
        int[] path=new int[0]; int pathIndex; int goalCell=-1;
        Worker target;
        boolean dead;
        Mob(EnemyType type,float x,float y,float phase){this.type=type;this.x=x;this.y=y;this.phase=phase;this.maxHp=type.hp;this.hp=maxHp;}
    }

    private static final class Fx {
        float x,y,vx,vy,life,maxLife,size;
        int color;
        boolean spark;
        Fx(float x,float y,float vx,float vy,float life,float size,int color,boolean spark){this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.life=this.maxLife=life;this.size=size;this.color=color;this.spark=spark;}
    }

    private static final class CaveHazard {
        final HazardType type; final int cell; final float x,y,r;
        float age; boolean fired;
        CaveHazard(HazardType type,int cell,float x,float y,float r){this.type=type;this.cell=cell;this.x=x;this.y=y;this.r=r;}
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

    private CaveMap map;
    private float width,height,ui;
    private float worldL,worldT,worldR,worldB,cellW,cellH;
    private float elapsed,enemyTimer=13f,hazardTimer=18f,saveTimer,levelClearTimer=-1f,screenShake;
    private boolean speedHeld;
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
                if(speed.hit(x,y)){speedHeld=true;return true;}
                return handleTap(x,y);
            }
            @Override public boolean touchUp(int sx,int sy,int pointer,int button){speedHeld=false;return true;}
            @Override public boolean touchDragged(int sx,int sy,int pointer){if(!speed.hit(sx,sy))speedHeld=false;return true;}
        });
    }

    @Override public void resize(int w,int h){
        width=w;height=h;ui=Math.max(.72f,w/420f);
        game.draw.resize(w,h);
        worldL=0;worldR=w;worldT=82f*ui;worldB=h-252f*ui;
        if(worldB<worldT+250f*ui)worldB=worldT+250f*ui;
        layoutUi();
        generateDepth(false);
    }

    private void layoutUi(){
        back.set(8f*ui,10f*ui,48f*ui,48f*ui);
        float panel=worldB;
        float tabH=36f*ui;
        for(int i=0;i<4;i++)tabs[i].set(i*width/4f,panel,(i+1)*width/4f,panel+tabH);
        float contentT=panel+tabH+8f*ui;
        left.set(10f*ui,contentT+30f*ui,45f*ui,contentT+70f*ui);
        right.set(width-45f*ui,contentT+30f*ui,width-10f*ui,contentT+70f*ui);
        primary.set(56f*ui,contentT+84f*ui,width/2f-5f*ui,contentT+126f*ui);
        secondary.set(width/2f+5f*ui,contentT+84f*ui,width-56f*ui,contentT+126f*ui);
        tertiary.set(56f*ui,contentT+132f*ui,width/2f-5f*ui,contentT+170f*ui);
        quaternary.set(width/2f+5f*ui,contentT+132f*ui,width-56f*ui,contentT+170f*ui);
        speed.set(16f*ui,height-58f*ui,width-16f*ui,height-10f*ui);
    }

    private void generateDepth(boolean advance){
        if(width<=0||height<=0)return;
        if(advance){state.depth++;state.depthProgress=0;saveNow();}
        mobs.clear();hazards.clear();fx.clear();veins.clear();
        int cols=9;
        int rows=Math.max(9,Math.min(13,Math.round((worldB-worldT)/(width/cols))));
        long seed=0x9E3779B97F4A7C15L^(long)slot*0xBF58476D1CE4E5B9L^(long)state.depth*0x94D049BB133111EBL;
        map=new CaveMap(cols,rows,seed);
        cellW=(worldR-worldL)/map.cols; cellH=(worldB-worldT)/map.rows;
        random.setSeed(seed^0x1234FEDCBA98765L);
        buildVeins();
        syncWorkers(true);
        enemyTimer=Math.max(8f,17f-state.depth*.12f)+random.nextFloat()*8f;
        hazardTimer=15f+random.nextFloat()*12f;
        levelClearTimer=-1f;
        toast="ГЛУБИНА "+state.depth;toastTime=2.4f;
        if(state.depth%10==0)spawnBoss();
    }

    private void buildVeins(){
        List<Integer> candidates=new ArrayList<>(map.deadEnds());
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int idx=map.index(c,r);
            if(idx==map.index(map.startCol,map.startRow)||candidates.contains(idx))continue;
            if(((idx*37+state.depth*11)%5)==0)candidates.add(idx);
        }
        int target=Math.min(candidates.size(),Math.max(11,12+state.depth/4));
        for(int i=0;i<target;i++){
            int cell=candidates.get(i);
            int c=map.col(cell),r=map.row(cell);
            int side=map.preferredSolidSide(c,r,i*71+state.depth);
            float pocket=Math.min(cellW,cellH)*.28f;
            float x=cx(c)+CaveMap.dx(side)*pocket;
            float y=cy(r)+CaveMap.dy(side)*pocket;
            RockType type=chooseRockType(i);
            float radius=Math.min(cellW,cellH)*(.20f+random.nextFloat()*.055f);
            float hp=type.hp*(1f+Math.max(0,state.depth-1)*.055f);
            veins.add(new Vein(type,cell,side,random.nextInt(),x,y,radius,hp));
        }
    }

    private RockType chooseRockType(int salt){
        float r=random.nextFloat();int d=state.depth;
        if(d>=22&&r<.07f)return RockType.ANCIENT_CRYSTAL;
        if(d>=14&&r<.16f)return RockType.OBSIDIAN;
        if(d>=10&&r<.25f)return RockType.DIAMOND;
        if(d>=6&&r<.42f)return RockType.GOLD;
        if(d>=3&&r<.62f)return RockType.SILVER;
        return RockType.STONE;
    }

    private void syncWorkers(boolean resetPositions){
        if(resetPositions)workers.clear();
        for(int ti=0;ti<GnomeTier.values().length;ti++){
            GnomeTier tier=GnomeTier.values()[ti];int want=Math.min(48,state.tierCounts[ti]),have=0;
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
        float dt=real*(speedHeld?4f:1f);
        elapsed+=real;
        update(dt);
        Draw d=game.draw;d.beginFrame();
        if(screenShake>0){float sx=(float)Math.sin(elapsed*71f)*screenShake,sy=(float)Math.cos(elapsed*53f)*screenShake*.55f;d.save();d.translate(sx,sy);drawWorld(d);d.restore();}
        else drawWorld(d);
        drawHud(d);drawPanel(d);drawToast(d);d.endFrame();
    }

    private void update(float dt){
        if(map==null)return;
        if(toastTime>0)toastTime-=dt;
        if(screenShake>0)screenShake=Math.max(0,screenShake-dt*18f*ui);
        updateVeins(dt);updateGuardian(dt);updateMobs(dt);updateHazards(dt);updateWorkers(dt);updateFx(dt);
        enemyTimer-=dt;hazardTimer-=dt;saveTimer+=dt;
        if(enemyTimer<=0){spawnEnemyWave();enemyTimer=Math.max(8f,24f-state.depth*.25f)+random.nextFloat()*10f;}
        if(hazardTimer<=0){spawnHazard();hazardTimer=20f+random.nextFloat()*18f;}
        if(saveTimer>=4f){saveNow();saveTimer=0;}
        boolean any=false;for(Vein v:veins)if(!v.dead){any=true;break;}
        if(!any&&levelClearTimer<0){levelClearTimer=1.4f;toast="УРОВЕНЬ ОЧИЩЕН";toastTime=1.4f;}
        if(levelClearTimer>=0){levelClearTimer-=dt;if(levelClearTimer<=0)generateDepth(true);}
    }

    private void updateVeins(float dt){
        float suppression=state.regenSuppression();
        for(Vein v:veins){
            v.hitFlash=Math.max(0,v.hitFlash-dt);
            if(v.dead){v.death+=dt;continue;}
            if(v.type.regenPerSecond>0&&v.hp<v.maxHp){float regen=v.type.regenPerSecond*(1-suppression)*(1+state.depth*.02f);v.hp=Math.min(v.maxHp,v.hp+regen*dt);}
        }
    }

    private float guardianCooldown;
    private void updateGuardian(float dt){
        if(state.guardianLevel<=0)return;
        guardianCooldown-=dt;if(guardianCooldown>0)return;
        float gx=cx(map.startCol),gy=cy(map.startRow),range=Math.min(cellW,cellH)*2.8f*state.guardianRangeMultiplier();
        Mob best=null;float bd=Float.MAX_VALUE;
        for(Mob m:mobs){if(m.dead)continue;float q=dist2(gx,gy,m.x,m.y);if(q<range*range&&q<bd){bd=q;best=m;}}
        if(best!=null){guardianCooldown=state.guardianAttackInterval();best.hp-=state.guardianDamage();best.attack=.16f;spawnSparks(best.x,best.y,0xFFFFD873,7);}
    }

    private void updateWorkers(float dt){
        for(Worker w:new ArrayList<>(workers)){
            w.attackCooldown-=dt;if(w.swing>0)w.swing=Math.max(0,w.swing-dt);if(w.stun>0)w.stun-=dt;
            if(w.stun>0){w.action=WorkerAction.STUNNED;w.vx=w.vy=0;continue;}
            Mob enemy=nearestMob(w.x,w.y);
            if(enemy!=null){w.mob=enemy;w.vein=null;fight(w,enemy,dt);continue;}
            w.mob=null;
            float cap=w.tier.cargoCapacity*state.carryMultiplier(w.tier.ordinal());
            if(w.hasCargo()&&w.cargo()>=cap*.92){carryHome(w,dt);continue;}
            if(w.vein==null||w.vein.dead)w.vein=chooseVein(w);
            if(w.vein!=null)mine(w,w.vein,dt); else if(w.hasCargo())carryHome(w,dt); else {w.action=WorkerAction.IDLE;w.vx=w.vy=0;}
        }
    }

    private void mine(Worker w,Vein v,float dt){
        if(!atCell(w,v.cell)){w.action=WorkerAction.WALK;routeWorker(w,v.cell);followWorker(w,moveSpeed(w),dt);return;}
        w.action=WorkerAction.MINE;w.vx=w.vy=0;
        if(w.attackCooldown<=0&&w.swing<=0){w.swing=.58f;w.hitApplied=false;w.attackCooldown=Math.max(.20f,.72f-w.tier.ordinal()*.065f);}
        float progress=w.swing<=0?1f:1f-w.swing/.58f;
        if(w.swing>0&&!w.hitApplied&&progress>=.57f){
            w.hitApplied=true;float damage=w.tier.miningPower*state.tierPowerMultiplier(w.tier.ordinal())*state.miningMultiplier(w.tier.ordinal());
            v.hp-=damage;v.hitFlash=.16f;screenShake=Math.max(screenShake,Math.min(2.8f*ui,.35f*ui+w.tier.ordinal()*.36f*ui));
            spawnRockHit(v,w.tier.ordinal());
            if(v.hp<=0)breakVein(v,w);
        }
    }

    private void breakVein(Vein v,Worker w){
        if(v.dead)return;v.dead=true;v.hp=0;v.death=0;w.add(v.type.material,state.yieldFor(v.type,w.tier.ordinal()));
        state.rocksBroken++;state.depthProgress++;spawnBreak(v);screenShake=Math.max(screenShake,3.3f*ui);
        if(v.type.ordinal()>=RockType.DIAMOND.ordinal()){toast=v.type.title.toUpperCase()+" ДОБЫТ";toastTime=1.4f;}
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
            w.clearCargo();spawnSparks(cx(map.startCol),cy(map.startRow),0xFFFFCC63,5);
        }
    }

    private void fight(Worker w,Mob m,float dt){
        int cell=cellFor(m.x,m.y);
        if(!atCell(w,cell)||distance(w.x,w.y,m.x,m.y)>Math.min(cellW,cellH)*.42f){w.action=WorkerAction.WALK;routeWorker(w,cell);followWorker(w,moveSpeed(w)*1.08f,dt);return;}
        w.action=WorkerAction.FIGHT;w.vx=w.vy=0;
        if(w.attackCooldown<=0&&w.swing<=0){w.swing=.46f;w.hitApplied=false;w.attackCooldown=Math.max(.18f,.62f-w.tier.ordinal()*.05f);}
        float p=w.swing<=0?1:1-w.swing/.46f;
        if(w.swing>0&&!w.hitApplied&&p>.53f){w.hitApplied=true;float dmg=w.tier.combatPower*state.tierPowerMultiplier(w.tier.ordinal())*state.combatMultiplier(w.tier.ordinal());m.hp-=dmg;m.attack=.14f;spawnSparks(m.x,m.y,0xFFFFB74D,5);}
    }

    private float moveSpeed(Worker w){return w.tier.moveSpeed*state.speedMultiplier(w.tier.ordinal())*ui;}
    private void routeWorker(Worker w,int goal){if(w.goalCell==goal&&w.path.length>0)return;w.goalCell=goal;w.path=map.path(cellFor(w.x,w.y),goal);w.pathIndex=Math.min(1,w.path.length);}
    private void followWorker(Worker w,float speed,float dt){
        if(w.path.length==0||w.pathIndex>=w.path.length){w.vx=w.vy=0;return;}
        int node=w.path[w.pathIndex];float tx=cx(map.col(node)),ty=cy(map.row(node));float dx=tx-w.x,dy=ty-w.y,di=len(dx,dy);
        if(di<2.5f*ui){w.x=tx;w.y=ty;w.pathIndex++;if(w.pathIndex>=w.path.length){w.vx=w.vy=0;return;}node=w.path[w.pathIndex];tx=cx(map.col(node));ty=cy(map.row(node));dx=tx-w.x;dy=ty-w.y;di=len(dx,dy);}
        if(di>.001f){w.vx=dx/di*speed;w.vy=dy/di*speed;w.x+=w.vx*dt;w.y+=w.vy*dt;w.walkCycle+=dt*(7.5f+speed/(38f*ui));}
    }

    private Vein chooseVein(Worker w){Vein best=null;float bd=Float.MAX_VALUE;for(Vein v:veins){if(v.dead)continue;float d=dist2(w.x,w.y,cx(map.col(v.cell)),cy(map.row(v.cell)));if(d<bd){bd=d;best=v;}}return best;}

    private void updateMobs(float dt){
        List<Mob> summons=new ArrayList<>();
        for(Mob m:mobs){
            if(m.dead)continue;m.attack=Math.max(0,m.attack-dt);m.attackCooldown-=dt;m.summonCooldown-=dt;m.routeTimer-=dt;
            if(m.hp<=0){killMob(m);continue;}
            boolean thief=m.type==EnemyType.IMP||m.type==EnemyType.IMP_KING;
            int goal;if(thief){goal=map.index(map.startCol,map.startRow);m.target=null;}else{m.target=nearestWorker(m.x,m.y);goal=m.target==null?map.index(map.startCol,map.startRow):cellFor(m.target.x,m.target.y);}
            if(m.routeTimer<=0||m.goalCell!=goal){m.goalCell=goal;m.path=map.path(cellFor(m.x,m.y),goal);m.pathIndex=Math.min(1,m.path.length);m.routeTimer=.55f;}
            boolean reached=followMob(m,m.type.moveSpeed*ui,dt);
            if(reached){
                if(thief)robChest(m);else attackWorker(m,m.target);
            }
            if((m.type==EnemyType.IMP_KING||m.type==EnemyType.DEMON_KING)&&m.summonCooldown<=0){m.summonCooldown=m.type==EnemyType.IMP_KING?6.5f:8f;if(mobs.size()+summons.size()<14){EnemyType t=m.type==EnemyType.IMP_KING?EnemyType.IMP:EnemyType.DEMON;summons.add(new Mob(t,m.x,m.y,random.nextFloat()*6.28f));}}
        }
        mobs.addAll(summons);mobs.removeIf(m->m.dead&&m.attack<-1f);
    }

    private boolean followMob(Mob m,float speed,float dt){
        if(m.path.length==0||m.pathIndex>=m.path.length)return true;
        int node=m.path[m.pathIndex];float tx=cx(map.col(node)),ty=cy(map.row(node));float dx=tx-m.x,dy=ty-m.y,di=len(dx,dy);
        if(di<2.5f*ui){m.x=tx;m.y=ty;m.pathIndex++;if(m.pathIndex>=m.path.length)return true;node=m.path[m.pathIndex];tx=cx(map.col(node));ty=cy(map.row(node));dx=tx-m.x;dy=ty-m.y;di=len(dx,dy);}
        if(di>.001f){m.x+=dx/di*speed*dt;m.y+=dy/di*speed*dt;m.walkCycle+=dt*(5.5f+m.type.moveSpeed/18f);}
        return false;
    }

    private void robChest(Mob m){
        if(m.attackCooldown>0)return;m.attackCooldown=m.type==EnemyType.IMP_KING?1.7f:2.5f;m.attack=.42f;
        long before=state.stone+state.silver*8+state.gold*20+state.diamond*100;
        state.stealFromChest(state.depth,m.type==EnemyType.IMP_KING);
        long after=state.stone+state.silver*8+state.gold*20+state.diamond*100;
        if(before>after){toast="БЕС В СУНДУКЕ  −"+format(before-after);toastTime=1.5f;spawnSparks(cx(map.startCol),cy(map.startRow),0xFFFFC04A,10);}
        // A thief visibly retreats after every grab instead of becoming a stationary resource vacuum.
        List<Integer> outer=map.outerCells();int goal=outer.get(random.nextInt(outer.size()));m.goalCell=goal;m.path=map.path(cellFor(m.x,m.y),goal);m.pathIndex=Math.min(1,m.path.length);m.routeTimer=1.2f;
    }

    private void attackWorker(Mob m,Worker w){
        if(w==null||m.attackCooldown>0)return;m.attackCooldown=1.0f;m.attack=.40f;w.stun=Math.max(w.stun,.38f+m.type.contactPower*.025f);spawnSparks(w.x,w.y,0xFFFF765D,5);
        if(m.type.ordinal()>=EnemyType.IMP_KING.ordinal()&&random.nextFloat()<.025f*(1-state.hazardSurvivalBonus(w.tier.ordinal())))loseWorker(w,m.type.title+" сбил гнома");
    }

    private void killMob(Mob m){
        if(m.dead)return;m.dead=true;m.attack=-2f;state.enemiesDefeated++;spawnDeath(m);screenShake=Math.max(screenShake,2.2f*ui);
        if(m.type.ordinal()>=EnemyType.IMP_KING.ordinal()){int rune=state.grantRandomRuneLevel(random);toast="БОСС ПОВЕРЖЕН • "+RuneType.values()[rune].title;toastTime=2.4f;}
    }

    private void loseWorker(Worker w,String reason){
        if(!workers.remove(w))return;int ti=w.tier.ordinal();if(state.tierCounts[ti]>0)state.tierCounts[ti]--;state.gnomesLost++;toast="ГНОМ ПОТЕРЯН • "+reason;toastTime=2f;spawnSparks(w.x,w.y,0xFFE6D5BD,12);
    }

    private Worker nearestWorker(float x,float y){Worker best=null;float bd=Float.MAX_VALUE;for(Worker w:workers){float d=dist2(x,y,w.x,w.y);if(d<bd){bd=d;best=w;}}return best;}
    private Mob nearestMob(float x,float y){Mob best=null;float bd=Float.MAX_VALUE;for(Mob m:mobs){if(m.dead)continue;float d=dist2(x,y,m.x,m.y);if(d<bd){bd=d;best=m;}}return best;}

    private void spawnEnemyWave(){EnemyType type=chooseEnemyType();int n=type==EnemyType.IMP?2+random.nextInt(3):1;for(int i=0;i<n;i++)spawnMob(type);toast=type.title.toUpperCase()+(n>1?" ×"+n:"");toastTime=1.3f;}
    private EnemyType chooseEnemyType(){int d=state.depth;float r=random.nextFloat();if(d>=18&&r<.12f)return EnemyType.FIRE_GOLEM;if(d>=15&&r<.25f)return EnemyType.WATER_GOLEM;if(d>=12&&r<.40f)return EnemyType.STONE_GOLEM;if(d>=7&&r<.62f)return EnemyType.DEMON;return EnemyType.IMP;}
    private void spawnBoss(){EnemyType t=state.depth>=30?EnemyType.ELEMENTAL_KING:state.depth>=20?EnemyType.DEMON_KING:EnemyType.IMP_KING;spawnMob(t);toast="БОСС • "+t.title.toUpperCase();toastTime=2.6f;}
    private void spawnMob(EnemyType type){List<Integer> out=map.outerCells();int cell=out.get(random.nextInt(out.size()));Mob m=new Mob(type,cx(map.col(cell)),cy(map.row(cell)),random.nextFloat()*6.28f);if(type.ordinal()>=EnemyType.IMP_KING.ordinal()){float k=1+Math.max(0,state.depth-10)*.07f;m.maxHp*=k;m.hp=m.maxHp;}mobs.add(m);}

    private void updateHazards(float dt){
        for(Iterator<CaveHazard>it=hazards.iterator();it.hasNext();){CaveHazard h=it.next();h.age+=dt;if(!h.fired&&h.age>=1.25f){h.fired=true;fireHazard(h);}if(h.age>hazardDuration(h.type))it.remove();}
    }
    private float hazardDuration(HazardType t){return switch(t){case COLLAPSE->3.2f;case PIT->8f;case LAVA->9f;case FLOOD->4.8f;};}
    private void spawnHazard(){int cell=random.nextInt(map.cols*map.rows);if(cell==map.index(map.startCol,map.startRow))cell=map.index(0,0);HazardType type=HazardType.values()[random.nextInt(HazardType.values().length)];float r=Math.min(cellW,cellH)*(type==HazardType.FLOOD?1.2f:.62f);hazards.add(new CaveHazard(type,cell,cx(map.col(cell)),cy(map.row(cell)),r));toast="ОПАСНОСТЬ • "+type.title;toastTime=1.3f;}
    private void fireHazard(CaveHazard h){screenShake=Math.max(screenShake,h.type==HazardType.COLLAPSE?5f*ui:2f*ui);for(Worker w:new ArrayList<>(workers)){float d=distance(w.x,w.y,h.x,h.y);if(d>h.r)continue;float survive=state.hazardSurvivalBonus(w.tier.ordinal());switch(h.type){case FLOOD->w.stun=Math.max(w.stun,1.4f);case LAVA-> {w.stun=Math.max(w.stun,.7f);if(random.nextFloat()<.10f*(1-survive))loseWorker(w,"лава");}case PIT-> {if(random.nextFloat()<.12f*(1-survive))loseWorker(w,"провалился в яму");else w.stun=Math.max(w.stun,1f);}case COLLAPSE-> {if(random.nextFloat()<.16f*(1-survive))loseWorker(w,"попал под обвал");else w.stun=Math.max(w.stun,.9f);}}}spawnSparks(h.x,h.y,h.type==HazardType.FLOOD?0xFF70C9F4:h.type==HazardType.LAVA?0xFFFF6A24:0xFF918172,14);}

    private void updateFx(float dt){for(Iterator<Fx>it=fx.iterator();it.hasNext();){Fx p=it.next();p.life-=dt;if(p.life<=0){it.remove();continue;}p.x+=p.vx*dt;p.y+=p.vy*dt;if(!p.spark)p.vy+=45f*ui*dt;else{p.vx*=Math.max(0,1-dt*3);p.vy*=Math.max(0,1-dt*3);}}if(fx.size()>420)fx.subList(0,fx.size()-420).clear();}
    private void spawnRockHit(Vein v,int power){for(int i=0;i<5+Math.min(5,power);i++){float a=random.nextFloat()*6.28f,s=(25+random.nextFloat()*65+power*7)*ui;fx.add(new Fx(v.x,v.y,(float)Math.cos(a)*s,(float)Math.sin(a)*s-.2f*s,.35f+random.nextFloat()*.35f,(1.1f+random.nextFloat()*2.2f)*ui,adjust(v.type.color,.75f+random.nextFloat()*.35f),false));}}
    private void spawnBreak(Vein v){for(int i=0;i<22;i++){float a=random.nextFloat()*6.28f,s=(30+random.nextFloat()*110)*ui;fx.add(new Fx(v.x,v.y,(float)Math.cos(a)*s,(float)Math.sin(a)*s-35f*ui,.45f+random.nextFloat()*.65f,(1.5f+random.nextFloat()*3.4f)*ui,adjust(v.type.color,.65f+random.nextFloat()*.5f),false));}}
    private void spawnDeath(Mob m){for(int i=0;i<18+(m.type.ordinal()>=EnemyType.IMP_KING.ordinal()?16:0);i++){float a=random.nextFloat()*6.28f,s=(35+random.nextFloat()*95)*ui;fx.add(new Fx(m.x,m.y,(float)Math.cos(a)*s,(float)Math.sin(a)*s,.45f+random.nextFloat()*.5f,(1.5f+random.nextFloat()*3f)*ui,m.type.color,false));}}
    private void spawnSparks(float x,float y,int color,int n){for(int i=0;i<n;i++){float a=random.nextFloat()*6.28f,s=(25+random.nextFloat()*85)*ui;fx.add(new Fx(x,y,(float)Math.cos(a)*s,(float)Math.sin(a)*s,.18f+random.nextFloat()*.3f,(1+random.nextFloat()*1.7f)*ui,color,true));}}

    private void drawWorld(Draw d){
        d.setColor(0xFF0B0A09);d.fillRect(worldL,worldT,worldR,worldB);drawRockMass(d);drawTunnels(d);drawHazards(d);drawVeins(d);drawChest(d);
        List<Object> actors=new ArrayList<>();actors.addAll(workers);actors.addAll(mobs);actors.sort(Comparator.comparingDouble(o->o instanceof Worker?wY((Worker)o):((Mob)o).y));
        for(Object o:actors){if(o instanceof Worker)drawWorker(d,(Worker)o);else drawMob(d,(Mob)o);}
        for(Fx p:fx)drawFx(d,p);drawAtmosphere(d);
    }
    private float wY(Worker w){return w.y;}

    private void drawRockMass(Draw d){
        d.setColor(0xFF11100E);d.fillRect(worldL,worldT,worldR,worldB);
        int count=74;long s=map.seed;
        for(int i=0;i<count;i++){long q=s+i*0x9E3779B97F4A7C15L;float x=worldL+hash01(q)*(worldR-worldL),y=worldT+hash01(q^0xA5A5A5A5L)*(worldB-worldT);float r=(1.2f+hash01(q^0x55AA55AAL)*4f)*ui;d.setColor(i%4==0?0xFF29251F:0xFF1C1A17);d.fillOval(x-r*1.7f,y-r*.55f,x+r*1.7f,y+r*.55f);}
    }

    private void drawTunnels(Draw d){
        float cw=Math.min(cellW,cellH)*.58f,edge=cw*1.16f,inner=cw*.83f;
        d.strokeWidth=edge;d.setColor(0xFF40382F);
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){int a=map.index(c,r);if(map.connected(c,r,CaveMap.E))d.line(cx(c),cy(r),cx(c+1),cy(r));if(map.connected(c,r,CaveMap.S))d.line(cx(c),cy(r),cx(c),cy(r+1));d.fillCircle(cx(c),cy(r),edge*.49f);}
        d.strokeWidth=cw;d.setColor(0xFF24211D);
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){if(map.connected(c,r,CaveMap.E))d.line(cx(c),cy(r),cx(c+1),cy(r));if(map.connected(c,r,CaveMap.S))d.line(cx(c),cy(r),cx(c),cy(r+1));d.fillCircle(cx(c),cy(r),cw*.49f);}
        d.strokeWidth=inner;d.setColor(0xFF2F2A24);
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){if(map.connected(c,r,CaveMap.E))d.line(cx(c),cy(r),cx(c+1),cy(r));if(map.connected(c,r,CaveMap.S))d.line(cx(c),cy(r),cx(c),cy(r+1));d.fillCircle(cx(c),cy(r),inner*.49f);}
        // rail-like tool marks and occasional torches make the routes readable without turning the cave into a grid UI.
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){int idx=map.index(c,r);if((idx+state.depth*3)%13==0)drawTorch(d,cx(c),cy(r)-cw*.28f,idx);}
    }

    private void drawTorch(Draw d,float x,float y,int seed){
        float flick=.82f+.18f*(float)Math.sin(elapsed*9.3f+seed);d.setColor(0x18FF9B32);d.fillCircle(x,y,25f*ui*flick);d.setColor(0x28FFB14A);d.fillCircle(x,y,13f*ui*flick);d.setColor(0xFF6E4930);d.strokeWidth=2.3f*ui;d.line(x,y+9f*ui,x,y+18f*ui);d.setColor(0xFFFF8C2E);d.fillOval(x-3.4f*ui,y-7f*ui,x+3.4f*ui,y+4f*ui);d.setColor(0xFFFFD36A);d.fillOval(x-1.5f*ui,y-4.5f*ui,x+1.5f*ui,y+1f*ui);
    }

    private void drawVeins(Draw d){for(Vein v:veins)if(!v.dead||v.death<.55f)drawVein(d,v);}
    private void drawVein(Draw d,Vein v){
        float death=v.dead?Math.max(0,1-v.death/.55f):1;float damage=1-Math.max(0,v.hp)/v.maxHp;float shake=v.hitFlash>0?(float)Math.sin(v.hitFlash*210f)*2.1f*ui*(v.hitFlash/.16f):0;
        d.save();d.translate(shake,0);d.scale(death,death);float x=v.x,y=v.y,r=v.r;
        d.setColor(0x88000000);polyRock(d,v,x+2f*ui,y+3f*ui,r*1.07f,0xFF090909,0);
        polyRock(d,v,x,y,r,adjust(v.type.color,.62f),0);polyRock(d,v,x-r*.08f,y-r*.10f,r*.82f,adjust(v.type.color,.86f),1);
        // ore veins are embedded lines instead of three arbitrary dots.
        if(v.type!=RockType.STONE&&v.type!=RockType.OBSIDIAN){d.setColor(adjust(v.type.color,1.38f));d.strokeWidth=Math.max(1.4f*ui,r*.10f);for(int i=0;i<3;i++){float yy=y-r*.42f+i*r*.34f;d.line(x-r*.48f,yy,x-r*.10f,yy+r*.15f);d.line(x-r*.10f,yy+r*.15f,x+r*.45f,yy-r*.05f);}}
        drawCracks(d,v,damage);
        if(damage>.45f){d.setColor(0xFF171513);int n=1+(int)(damage*5);for(int i=0;i<n;i++){float a=(v.seed*.000013f+i*2.27f);float rr=r*(.40f+.12f*(i%2));d.fillCircle(x+(float)Math.cos(a)*rr,y+(float)Math.sin(a)*rr,r*(.075f+.025f*(i%3)));}}
        if(v.type.regenPerSecond>0&&!v.dead){float pulse=.5f+.5f*(float)Math.sin(elapsed*3.4f+v.seed);d.setColor(v.type==RockType.OBSIDIAN?0x339A72E8:0x448A5CFF);d.strokeWidth=(1.2f+pulse)*ui;d.strokeCircle(x,y,r+3f*ui+pulse*2f*ui);}
        if(v.hitFlash>0){d.setColor(0x66FFFFFF);d.fillCircle(x,y,r*(.35f+v.hitFlash));}
        d.restore();
    }

    private void polyRock(Draw d,Vein v,float x,float y,float r,int color,int layer){d.setColor(color);d.pathReset();int n=9;for(int i=0;i<n;i++){float a=(float)(Math.PI*2*i/n)+((v.seed>>(i%16))&7)*.009f;float sc=.80f+hash01((long)v.seed*31+i*101+layer*77)*.25f;float px=x+(float)Math.cos(a)*r*sc,py=y+(float)Math.sin(a)*r*sc;if(i==0)d.moveTo(px,py);else d.lineTo(px,py);}d.closePath();d.fillPath();}
    private void drawCracks(Draw d,Vein v,float damage){if(damage<.15f)return;d.setColor(0xFF171513);d.strokeWidth=(1.2f+damage*.8f)*ui;int branches=1+(int)(damage*6);for(int i=0;i<branches;i++){float a=(v.seed*.0001f+i*2.399f);float len=v.r*(.20f+.55f*damage);float x1=v.x+(float)Math.cos(a)*v.r*.08f,y1=v.y+(float)Math.sin(a)*v.r*.08f;float mx=x1+(float)Math.cos(a)*len*.52f+(float)Math.sin(a)*len*.12f, my=y1+(float)Math.sin(a)*len*.52f-(float)Math.cos(a)*len*.12f;float x2=x1+(float)Math.cos(a)*len,y2=y1+(float)Math.sin(a)*len;d.line(x1,y1,mx,my);d.line(mx,my,x2,y2);if(damage>.52f)d.line(mx,my,mx+(float)Math.cos(a+1.0f)*len*.30f,my+(float)Math.sin(a+1.0f)*len*.30f);}}

    private void drawChest(Draw d){
        float x=cx(map.startCol),y=cy(map.startRow),s=Math.min(cellW,cellH);
        d.setColor(0x66000000);d.fillOval(x-s*.38f,y+s*.18f,x+s*.38f,y+s*.34f);d.setColor(0xFF5A351E);d.fillRoundRect(x-s*.29f,y-s*.14f,x+s*.29f,y+s*.22f,4f*ui);d.setColor(0xFF8A572C);d.fillRoundRect(x-s*.30f,y-s*.28f,x+s*.30f,y-s*.02f,8f*ui);d.setColor(0xFFD4A342);d.fillRect(x-s*.035f,y-s*.15f,x+s*.035f,y+s*.12f);d.fillCircle(x,y+s*.04f,s*.045f);
        if(state.guardianLevel>0)drawGuardian(d,x-s*.46f,y-s*.04f,s*.54f);
    }
    private void drawGuardian(Draw d,float x,float y,float s){float bob=(float)Math.sin(elapsed*3.1f)*1.1f*ui;d.save();d.translate(x,y+bob);d.setColor(0x55000000);d.fillOval(-s*.28f,s*.42f,s*.28f,s*.53f);d.setColor(0xFF314B5B);d.fillOval(-s*.22f,-s*.02f,s*.22f,s*.37f);d.setColor(0xFFE4B483);d.fillCircle(0,-s*.18f,s*.20f);d.setColor(0xFFD8D5CC);d.pathReset();d.moveTo(-s*.17f,-s*.10f);d.lineTo(0,s*.20f);d.lineTo(s*.17f,-s*.10f);d.closePath();d.fillPath();d.setColor(0xFF597D91);d.pathReset();d.moveTo(-s*.21f,-s*.31f);d.lineTo(0,-s*.62f);d.lineTo(s*.22f,-s*.31f);d.closePath();d.fillPath();d.setColor(0xFFC7D1D7);d.strokeWidth=s*.09f;d.line(s*.23f,-s*.02f,s*.48f,s*.32f);d.line(s*.36f,s*.12f,s*.54f,-s*.05f);d.restore();}

    private void drawWorker(Draw d,Worker w){float s=w.tier.size*ui;switch(w.tier){case MINER,VETERAN,TWIN_PICK->drawDwarf(d,w,s);case DRILL_RIG->drawDrill(d,w,s);case EXCAVATOR->drawExcavator(d,w,s);case IRON_GOLEM->drawIron(d,w,s);}int rune=state.tierRunes[w.tier.ordinal()];if(rune>=0&&rune<RuneType.values().length&&state.runeLevels[rune]>0)drawRune(d,w.x+s*.45f,w.y-s*.60f,4.4f*ui,RuneType.values()[rune]);}

    private float strikeProgress(Worker w,float duration){return w.swing<=0?0:1-w.swing/duration;}
    private float pickAngle(Worker w){if(w.action!=WorkerAction.MINE&&w.action!=WorkerAction.FIGHT)return 8f;float p=strikeProgress(w,w.action==WorkerAction.FIGHT?.46f:.58f);if(p<.28f)return lerp(18f,-92f,ease(p/.28f));if(p<.60f)return lerp(-92f,50f,ease((p-.28f)/.32f));return lerp(50f,10f,ease((p-.60f)/.40f));}
    private void drawDwarf(Draw d,Worker w,float s){
        float moving=(w.action==WorkerAction.WALK||w.action==WorkerAction.CARRY)?1:0;float stride=(float)Math.sin(w.walkCycle+w.phase)*moving;float bounce=Math.abs((float)Math.cos(w.walkCycle+w.phase))*1.5f*ui*moving;float lean=w.action==WorkerAction.MINE?-.06f:0;float dir=facing(w);d.save();d.translate(w.x,w.y-bounce);d.scale(dir,1);
        if(w.action==WorkerAction.STUNNED)d.rotate((float)Math.sin(elapsed*17+w.phase)*6);
        d.setColor(0x55000000);d.fillOval(-s*.38f,s*.50f+bounce,s*.38f,s*.64f+bounce);
        // planted feet, separate from the torso, make walking read at tiny phone scale.
        d.setColor(0xFF3A2C25);d.strokeWidth=s*.12f;d.line(-s*.11f,s*.29f,-s*.18f+stride*s*.14f,s*.55f);d.line(s*.11f,s*.29f,s*.18f-stride*s*.14f,s*.55f);d.fillOval(-s*.30f+stride*s*.14f,s*.50f,-s*.06f+stride*s*.14f,s*.60f);d.fillOval(s*.06f-stride*s*.14f,s*.50f,s*.30f-stride*s*.14f,s*.60f);
        d.setColor(adjust(w.tier.color,.62f));d.fillOval(-s*.30f,-s*.01f,s*.30f,s*.38f);d.setColor(0xFF5D3E28);d.fillRect(-s*.31f,s*.20f,s*.31f,s*.27f);d.setColor(0xFFD5A73A);d.fillRect(-s*.04f,s*.19f,s*.05f,s*.28f);
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
    private void drawAnimatedPick(Draw d,float x,float y,float s,float angle,float hand){d.save();d.translate(x,y);d.rotate(angle);d.setColor(0xFFE3B385);d.fillCircle(0,0,s*.07f);d.setColor(0xFF765033);d.strokeWidth=s*.075f;d.line(0,0,s*.50f,s*.35f);d.setColor(0xFFC8D0D5);d.strokeWidth=s*.09f;d.line(-s*.11f,-s*.02f,s*.17f,-s*.02f);d.setColor(0xFFF0F4F6);d.strokeWidth=s*.026f;d.line(-s*.08f,-s*.045f,s*.12f,-s*.045f);d.restore();}
    private void drawSack(Draw d,Worker w,float s){float bob=(float)Math.sin(w.walkCycle+w.phase)*1.3f*ui;d.setColor(0xFF735139);d.fillCircle(w.x-s*.38f,w.y+s*.18f+bob,Math.max(4f*ui,s*.18f));d.setColor(0xFFC7A16A);d.fillRect(w.x-s*.48f,w.y-s*.02f+bob,w.x-s*.30f,w.y+s*.04f+bob);}

    private void drawDrill(Draw d,Worker w,float s){float stride=(float)Math.sin(w.walkCycle+w.phase);float mining=w.action==WorkerAction.MINE?strikeProgress(w,.58f):0;float dir=facing(w);d.save();d.translate(w.x,w.y);d.scale(dir,1);d.setColor(0x55000000);d.fillOval(-s*.55f,s*.40f,s*.55f,s*.60f);d.setColor(0xFF403A34);d.fillRoundRect(-s*.48f,s*.12f,s*.40f,s*.45f,s*.10f);d.setColor(0xFF282827);for(int i=0;i<3;i++){float xx=-s*.32f+i*s*.31f;d.fillCircle(xx,s*.45f,s*.12f);d.setColor(0xFF60666A);d.fillCircle(xx,s*.45f,s*.052f);d.setColor(0xFF282827);}
        d.setColor(w.tier.color);d.fillRoundRect(-s*.38f,-s*.16f,s*.25f,s*.23f,s*.08f);d.setColor(0xFFE2B382);d.fillCircle(-s*.10f,-s*.24f,s*.16f);d.setColor(0xFFE8E5DC);d.pathReset();d.moveTo(-s*.22f,-s*.17f);d.lineTo(s*.05f,s*.10f);d.lineTo(s*.07f,-s*.11f);d.closePath();d.fillPath();
        float drill=1+.08f*(float)Math.sin(elapsed*34f);d.setColor(0xFFB9C4CB);d.pathReset();d.moveTo(s*.25f,-s*.07f);d.lineTo(s*.84f,s*.05f);d.lineTo(s*.25f,s*.18f);d.closePath();d.fillPath();d.setColor(0xFF68737A);d.strokeWidth=1.3f*ui;for(int i=0;i<5;i++){float xx=s*(.34f+i*.095f);float yy=s*(.01f+.06f*(float)Math.sin(elapsed*31+i*1.7f));d.line(xx,yy,xx+s*.09f,yy+s*.12f);}
        if(w.action==WorkerAction.MINE&&mining>.45f)spawnVisualDustHint(d,s);
        d.restore();if(w.hasCargo())drawSack(d,w,s);
    }
    private void spawnVisualDustHint(Draw d,float s){d.setColor(0x448E8174);for(int i=0;i<3;i++){float a=elapsed*4+i*2.1f;d.fillCircle(s*(.72f+(float)Math.cos(a)*.08f),s*(.07f+(float)Math.sin(a)*.10f),(2+i)*ui);}}

    private void drawExcavator(Draw d,Worker w,float s){float walk=(float)Math.sin(w.walkCycle+w.phase);float p=w.action==WorkerAction.MINE?strikeProgress(w,.58f):.2f;float dir=facing(w);d.save();d.translate(w.x,w.y);d.scale(dir,1);d.setColor(0x55000000);d.fillOval(-s*.68f,s*.43f,s*.68f,s*.65f);d.setColor(0xFF2D3031);d.fillRoundRect(-s*.58f,s*.18f,s*.47f,s*.50f,s*.12f);d.setColor(0xFF5B6165);for(int i=0;i<4;i++)d.fillCircle(-s*.40f+i*s*.25f,s*.45f,s*.105f);d.setColor(w.tier.color);d.fillRoundRect(-s*.43f,-s*.20f,s*.18f,s*.28f,s*.08f);d.setColor(0xFF82C9E5);d.fillRect(-s*.29f,-s*.13f,s*.06f,s*.04f);d.setColor(0xFFE3B383);d.fillCircle(-s*.12f,-s*.04f,s*.11f);
        float lift=w.action==WorkerAction.MINE?(float)Math.sin(Math.min(1,p)*Math.PI):.25f;float ax=s*(.50f+.07f*lift),ay=-s*(.34f+.23f*lift);d.setColor(0xFFD3A13A);d.strokeWidth=s*.13f;d.line(s*.08f,-s*.02f,ax,ay);d.line(ax,ay,s*.76f,s*(.10f-.18f*lift));d.setColor(0xFFB87F28);d.pathReset();d.moveTo(s*.62f,s*(.05f-.18f*lift));d.lineTo(s*.95f,s*(.02f-.18f*lift));d.lineTo(s*.78f,s*(.35f-.18f*lift));d.closePath();d.fillPath();d.restore();}

    private void drawIron(Draw d,Worker w,float s){float stride=(float)Math.sin(w.walkCycle+w.phase);float p=w.action==WorkerAction.MINE||w.action==WorkerAction.FIGHT?strikeProgress(w,w.action==WorkerAction.FIGHT?.46f:.58f):0;float dir=facing(w);d.save();d.translate(w.x,w.y-Math.abs(stride)*1.2f*ui);d.scale(dir,1);d.setColor(0x66000000);d.fillOval(-s*.53f,s*.49f,s*.53f,s*.70f);d.setColor(0xFF59636A);d.fillRoundRect(-s*.38f,-s*.04f,s*.38f,s*.46f,s*.10f);d.setColor(0xFF9FACB5);d.fillCircle(0,-s*.34f,s*.29f);d.setColor(0xFF242B30);d.fillRect(-s*.21f,-s*.43f,s*.21f,-s*.33f);d.setColor(0xFF6EE8FF);d.fillCircle(-s*.10f,-s*.38f,s*.035f);d.fillCircle(s*.10f,-s*.38f,s*.035f);d.setColor(0xFFCDD6DB);d.pathReset();d.moveTo(-s*.22f,-s*.20f);d.lineTo(0,s*.23f);d.lineTo(s*.22f,-s*.20f);d.lineTo(s*.08f,s*.16f);d.lineTo(-s*.08f,s*.16f);d.closePath();d.fillPath();d.setColor(0xFF69747B);d.strokeWidth=s*.17f;float punch=(float)Math.sin(p*Math.PI)*s*.20f;d.line(-s*.34f,s*.02f,-s*.56f,s*.34f);d.line(s*.34f,s*.02f,s*.58f+punch,s*.24f);d.line(-s*.17f,s*.42f,-s*.24f+stride*s*.05f,s*.68f);d.line(s*.17f,s*.42f,s*.24f-stride*s*.05f,s*.68f);drawAnimatedPick(d,s*.50f+punch,s*.20f,s,pickAngle(w),1);d.restore();}
    private float facing(Worker w){if(Math.abs(w.vx)>1)return w.vx<0?-1:1;if(w.vein!=null&&w.action==WorkerAction.MINE)return w.vein.x<w.x?-1:1;if(w.mob!=null)return w.mob.x<w.x?-1:1;return 1;}

    private void drawMob(Draw d,Mob m){if(m.dead)return;float s=m.type.size*ui;switch(m.type){case IMP,IMP_KING->drawImp(d,m,s);case DEMON,DEMON_KING->drawDemon(d,m,s);default->drawGolem(d,m,s);}float pct=Math.max(0,m.hp/m.maxHp);if(pct<.999f||m.type.ordinal()>=EnemyType.IMP_KING.ordinal()){float bw=s*1.5f;d.setColor(0xCC120E0D);d.fillRoundRect(m.x-bw/2,m.y-s*.95f,m.x+bw/2,m.y-s*.84f,2f*ui);d.setColor(0xFFE34F43);d.fillRoundRect(m.x-bw/2,m.y-s*.95f,m.x-bw/2+bw*pct,m.y-s*.84f,2f*ui);}}
    private void drawImp(Draw d,Mob m,float s){float hop=Math.abs((float)Math.sin(m.walkCycle+m.phase))*s*.10f;float flap=(float)Math.sin(elapsed*13f+m.phase);float steal=m.attack>0?(float)Math.sin((.42f-m.attack)/.42f*Math.PI):0;float dir=m.goalCell>=0&&cx(map.col(m.goalCell))<m.x?-1:1;d.save();d.translate(m.x,m.y-hop);d.scale(dir,1);if(m.type==EnemyType.IMP_KING){d.setColor(0x22FF4A32);d.fillCircle(0,0,s*.85f*(1+.07f*(float)Math.sin(elapsed*5)));}
        d.setColor(adjust(m.type.color,.64f));d.pathReset();d.moveTo(-s*.20f,-s*.05f);d.lineTo(-s*(.57f+.10f*flap),-s*.35f);d.lineTo(-s*.42f,s*.12f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.20f,-s*.05f);d.lineTo(s*(.57f+.10f*flap),-s*.35f);d.lineTo(s*.42f,s*.12f);d.closePath();d.fillPath();
        d.setColor(m.type.color);d.fillOval(-s*.30f,-s*.18f,s*.30f,s*.48f);d.fillCircle(0,-s*.30f,s*.28f);d.setColor(0xFFE8D2A8);d.pathReset();d.moveTo(-s*.22f,-s*.48f);d.lineTo(-s*.48f,-s*.72f);d.lineTo(-s*.09f,-s*.57f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.22f,-s*.48f);d.lineTo(s*.48f,-s*.72f);d.lineTo(s*.09f,-s*.57f);d.closePath();d.fillPath();d.setColor(0xFFFFE45C);d.fillOval(-s*.16f,-s*.36f,-s*.05f,-s*.29f);d.fillOval(s*.05f,-s*.36f,s*.16f,-s*.29f);
        d.setColor(adjust(m.type.color,.78f));d.strokeWidth=s*.08f;d.line(-s*.12f,s*.39f,-s*.24f+(float)Math.sin(m.walkCycle)*s*.08f,s*.69f);d.line(s*.12f,s*.39f,s*.24f-(float)Math.sin(m.walkCycle)*s*.08f,s*.69f);d.line(s*.24f,s*.10f,s*(.54f+.18f*steal),s*.25f);
        d.strokeWidth=s*.055f;d.line(-s*.28f,s*.24f,-s*.48f,s*.42f);d.line(-s*.48f,s*.42f,-s*.40f,s*.57f);if(m.type==EnemyType.IMP_KING)drawCrown(d,0,-s*.72f,s*.65f);d.restore();}
    private void drawDemon(Draw d,Mob m,float s){float stride=(float)Math.sin(m.walkCycle+m.phase);float breath=(float)Math.sin(elapsed*3.2f+m.phase)*s*.025f;float slash=m.attack>0?(float)Math.sin((.40f-m.attack)/.40f*Math.PI):0;d.save();d.translate(m.x,m.y+breath);if(m.type==EnemyType.DEMON_KING){d.setColor(0x228A1F28);d.fillCircle(0,0,s*.90f*(1+.08f*(float)Math.sin(elapsed*4)));}
        d.setColor(0x55000000);d.fillOval(-s*.48f,s*.58f,s*.48f,s*.73f);d.setColor(adjust(m.type.color,.72f));d.fillOval(-s*.36f,-s*.12f,s*.36f,s*.57f);d.setColor(m.type.color);d.fillCircle(0,-s*.37f,s*.32f);d.setColor(0xFFE5CF9D);d.pathReset();d.moveTo(-s*.25f,-s*.56f);d.lineTo(-s*.54f,-s*.82f);d.lineTo(-s*.10f,-s*.66f);d.closePath();d.fillPath();d.pathReset();d.moveTo(s*.25f,-s*.56f);d.lineTo(s*.54f,-s*.82f);d.lineTo(s*.10f,-s*.66f);d.closePath();d.fillPath();d.setColor(0xFFFFD74F);d.fillCircle(-s*.13f,-s*.40f,s*.045f);d.fillCircle(s*.13f,-s*.40f,s*.045f);d.setColor(adjust(m.type.color,.55f));d.strokeWidth=s*.13f;d.line(-s*.18f,s*.50f,-s*.29f+stride*s*.07f,s*.79f);d.line(s*.18f,s*.50f,s*.29f-stride*s*.07f,s*.79f);d.line(-s*.32f,s*.03f,-s*(.62f+.18f*slash),s*(.18f-.22f*slash));d.line(s*.32f,s*.03f,s*.60f,s*.22f);if(m.type==EnemyType.DEMON_KING)drawCrown(d,0,-s*.82f,s*.70f);d.restore();}
    private void drawGolem(Draw d,Mob m,float s){float step=(float)Math.sin(m.walkCycle+m.phase),stomp=Math.abs(step)*s*.04f;float punch=m.attack>0?(float)Math.sin((.40f-m.attack)/.40f*Math.PI):0;int col=m.type.color;d.save();d.translate(m.x,m.y-stomp);if(m.type==EnemyType.ELEMENTAL_KING){float pulse=.75f+.25f*(float)Math.sin(elapsed*4);d.setColor(0x287F63D8);d.fillCircle(0,0,s*(.90f+.10f*pulse));}
        d.setColor(0x66000000);d.fillOval(-s*.58f,s*.60f,s*.58f,s*.78f);d.setColor(adjust(col,.62f));d.fillRoundRect(-s*.37f,-s*.02f,s*.37f,s*.52f,s*.10f);d.setColor(col);d.fillCircle(0,-s*.39f,s*.32f);d.fillCircle(-s*.47f,s*.06f,s*.22f);d.fillCircle(s*(.47f+.18f*punch),s*(.06f-.12f*punch),s*.22f);d.setColor(adjust(col,.78f));d.fillCircle(-s*.20f,s*.40f,s*.18f);d.fillCircle(s*.20f,s*.40f,s*.18f);d.setColor(0xFFEAF6FF);d.fillCircle(-s*.12f,-s*.41f,s*.047f);d.fillCircle(s*.12f,-s*.41f,s*.047f);
        if(m.type==EnemyType.FIRE_GOLEM||m.type==EnemyType.ELEMENTAL_KING){d.setColor(0xFFFFB12F);for(int i=0;i<3;i++){float a=elapsed*(3+i*.4f)+i*2.1f;d.fillCircle((float)Math.cos(a)*s*.28f,-s*.67f+(float)Math.sin(a)*s*.10f,(.05f+.02f*i)*s);}}
        if(m.type==EnemyType.WATER_GOLEM||m.type==EnemyType.ELEMENTAL_KING){d.setColor(0x664FC6F1);d.strokeWidth=s*.08f;float wave=(float)Math.sin(elapsed*5);d.line(-s*.34f,s*.15f,s*.34f,s*(.15f+.08f*wave));}
        if(m.type==EnemyType.STONE_GOLEM){d.setColor(0xFF4D4942);d.strokeWidth=s*.045f;d.line(-s*.20f,-s*.10f,s*.08f,s*.20f);d.line(s*.08f,s*.20f,s*.25f,s*.03f);}
        if(m.type==EnemyType.ELEMENTAL_KING)drawCrown(d,0,-s*.82f,s*.78f);d.restore();}
    private void drawCrown(Draw d,float x,float y,float w){d.setColor(0xFFFFD24A);d.pathReset();d.moveTo(x-w*.44f,y+w*.20f);d.lineTo(x-w*.40f,y-w*.16f);d.lineTo(x-w*.16f,y+w*.01f);d.lineTo(x,y-w*.28f);d.lineTo(x+w*.16f,y+w*.01f);d.lineTo(x+w*.40f,y-w*.16f);d.lineTo(x+w*.44f,y+w*.20f);d.closePath();d.fillPath();d.setColor(0xFFFFF1A8);d.fillCircle(x,y-w*.10f,w*.045f);}

    private void drawHazards(Draw d){for(CaveHazard h:hazards){float warn=h.age<1.25f?.35f+.20f*(float)Math.sin(h.age*16f):.70f;switch(h.type){case COLLAPSE->{d.setColor(alpha(0xFFD57B4F,warn));d.strokeWidth=2f*ui;d.strokeCircle(h.x,h.y,h.r);if(h.age>=1.25f){d.setColor(0xFF62564B);for(int i=0;i<6;i++){float a=i*1.17f+h.age;d.fillCircle(h.x+(float)Math.cos(a)*h.r*.45f,h.y+(float)Math.sin(a)*h.r*.35f,(4+i%3*2)*ui);}}}case PIT->{d.setColor(0xDD020202);d.fillOval(h.x-h.r,h.y-h.r*.55f,h.x+h.r,h.y+h.r*.55f);d.setColor(0xFF514A42);d.strokeWidth=2f*ui;d.strokeCircle(h.x,h.y,h.r*.75f);}case LAVA->{d.setColor(alpha(0xFFFF5625,warn));d.fillOval(h.x-h.r,h.y-h.r*.45f,h.x+h.r,h.y+h.r*.45f);d.setColor(0xFFFFC13B);for(int i=0;i<4;i++){float a=elapsed*(1+i*.12f)+i;d.fillCircle(h.x+(float)Math.cos(a)*h.r*.55f,h.y+(float)Math.sin(a*1.4f)*h.r*.24f,(2.5f+i%2*1.5f)*ui);}}case FLOOD->{float p=Math.max(0,h.age-1.25f);d.setColor(alpha(0xFF4BAEE0,warn));float yy=h.y+(float)Math.sin(p*4f)*h.r*.14f;d.fillRoundRect(h.x-h.r,yy-h.r*.24f,h.x+h.r,yy+h.r*.24f,h.r*.18f);d.setColor(0x99DDF7FF);for(int i=0;i<5;i++){float xx=h.x-h.r+(i*.41f+(p*.9f)%1f)*h.r*2;d.fillCircle(xx,yy-h.r*.10f+(i%2)*h.r*.12f,2f*ui);}}}}
    }

    private void drawFx(Draw d,Fx p){float a=Math.max(0,p.life/p.maxLife);d.setColor(alpha(p.color,a));if(p.spark){d.strokeWidth=Math.max(1f,p.size*.6f);d.line(p.x,p.y,p.x-p.vx*.025f,p.y-p.vy*.025f);}else d.fillCircle(p.x,p.y,p.size*(.45f+.55f*a));}
    private void drawAtmosphere(Draw d){
        for(int i=0;i<18;i++){float x=((i*73.3f+elapsed*(3+i%4))*ui)%(width+20f*ui)-10f*ui;float y=worldT+((i*119.7f+state.depth*31)%1000)/1000f*(worldB-worldT);d.setColor(0x18D7C7AB);d.fillCircle(x,y,(.7f+i%3*.45f)*ui);}
        d.setColor(0x66000000);d.fillRect(worldL,worldT,worldR,worldT+7f*ui);d.fillRect(worldL,worldB-8f*ui,worldR,worldB);
    }

    private void drawHud(Draw d){
        d.setColor(0xFF111418);d.fillRect(0,0,width,worldT);d.setColor(0xFF20262C);d.fillRect(0,worldT-1f*ui,width,worldT);
        button(d,back,"‹",true,1.25f);d.align=Draw.Align.LEFT;d.bold=true;d.textSize=13f*ui;d.setColor(0xFFF0F3F5);d.text("GNOMES",58f*ui,24f*ui);d.bold=false;d.textSize=9.5f*ui;d.setColor(0xFF8E9AA3);d.text("ГЛУБИНА "+state.depth,58f*ui,43f*ui);
        float y=66f*ui;drawResource(d,12f*ui,y,0xFF888D92,"●",state.stone);drawResource(d,112f*ui,y,0xFFC6D0D8,"Ag",state.silver);drawResource(d,211f*ui,y,0xFFE2B544,"Au",state.gold);drawResource(d,309f*ui,y,0xFF67D7F2,"◆",state.diamond);
    }
    private void drawResource(Draw d,float x,float y,int col,String icon,long n){d.setColor(col);d.fillCircle(x+5f*ui,y-4f*ui,4f*ui);d.bold=true;d.textSize=9f*ui;d.text(icon+" "+format(n),x+13f*ui,y);d.bold=false;}

    private void drawPanel(Draw d){
        d.setColor(0xFF121519);d.fillRect(0,worldB,width,height);String[] names={"ГНОМЫ","АПГРЕЙДЫ","АРТЕФ.","РУНЫ"};for(int i=0;i<4;i++){if(tab.ordinal()==i){d.setColor(0xFF2A3239);d.fillRoundRect(tabs[i].l+2f*ui,tabs[i].t+2f*ui,tabs[i].r-2f*ui,tabs[i].b-2f*ui,5f*ui);}d.align=Draw.Align.CENTER;d.bold=tab.ordinal()==i;d.textSize=9f*ui;d.setColor(tab.ordinal()==i?0xFFF2F5F7:0xFF89949C);d.text(names[i],tabs[i].cx(),tabs[i].cy()+3f*ui);}d.align=Draw.Align.LEFT;d.bold=false;
        switch(tab){case GNOMES->drawGnomePanel(d);case UPGRADES->drawUpgradePanel(d);case ARTIFACTS->drawArtifactPanel(d);case RUNES->drawRunePanel(d);}button(d,speed,speedHeld?"УСКОРЕНИЕ ×4":"УДЕРЖИВАЙ • УСКОРИТЬ ×4",true,.92f);
    }

    private float contentTop(){return worldB+44f*ui;}
    private void drawGnomePanel(Draw d){GnomeTier gt=GnomeTier.values()[selectedTier];float ct=contentTop();button(d,left,"‹",selectedTier>0,1.15f);button(d,right,"›",selectedTier<GnomeTier.values().length-1,1.15f);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=11f*ui;d.setColor(gt.color);d.text(gt.title,width/2,ct+18f*ui);d.bold=false;d.textSize=8.6f*ui;d.setColor(0xFFB6C0C8);d.text("×"+state.tierCounts[selectedTier]+"   ур. "+state.tierLevels[selectedTier]+"   добыча "+one.format(gt.miningPower*state.tierPowerMultiplier(selectedTier)),width/2,ct+37f*ui);d.align=Draw.Align.LEFT;
        if(selectedTier==0)button(d,primary,"КУПИТЬ • "+format(state.minerBuyCost()),true,.72f);else button(d,primary,"ЭТОТ ТИП",false,.72f);button(d,secondary,"УЛУЧШИТЬ • "+format(state.tierUpgradeCost(selectedTier)),true,.70f);button(d,tertiary,"СЛИТЬ 10 → 1",selectedTier<GnomeTier.values().length-1&&state.tierCounts[selectedTier]>=10,.68f);button(d,quaternary,"ГРУЗ "+format((long)(gt.cargoCapacity*state.carryMultiplier(selectedTier))),false,.66f);}
    private void drawUpgradePanel(Draw d){float ct=contentTop();d.align=Draw.Align.CENTER;d.bold=true;d.textSize=10.5f*ui;d.setColor(0xFFF0F3F5);d.text("ШАХТА И ИНФРАСТРУКТУРА",width/2,ct+18f*ui);d.bold=false;d.align=Draw.Align.LEFT;button(d,primary,"КИРКИ ур."+state.miningUpgrade,true,.70f);button(d,secondary,"ЛОГИСТИКА ур."+state.speedUpgrade,true,.66f);button(d,tertiary,"БОЙ ур."+state.combatUpgrade,true,.70f);button(d,quaternary,state.guardianLevel==0?"НАНЯТЬ СТРАЖА":"СТРАЖ ур."+state.guardianLevel,true,.66f);}
    private void drawArtifactPanel(Draw d){ArtifactType a=ArtifactType.values()[selectedArtifact];float ct=contentTop();button(d,left,"‹",selectedArtifact>0,1.15f);button(d,right,"›",selectedArtifact<ArtifactType.values().length-1,1.15f);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=10.5f*ui;d.setColor(a.color);d.text(a.title,width/2,ct+18f*ui);d.bold=false;d.textSize=8.5f*ui;d.setColor(0xFFB5BFC7);d.text(a.description+" • ур. "+state.artifactLevels[selectedArtifact],width/2,ct+37f*ui);d.align=Draw.Align.LEFT;button(d,primary,"УСИЛИТЬ • ◆"+state.artifactCost(selectedArtifact),true,.72f);button(d,secondary,"ПОСТОЯННЫЙ ЭФФЕКТ",false,.60f);button(d,tertiary,"РУНА: "+runeName(state.artifactRunes[selectedArtifact]),false,.60f);button(d,quaternary,"",false,.60f);}
    private void drawRunePanel(Draw d){RuneType r=RuneType.values()[selectedRune];float ct=contentTop();button(d,left,"‹",selectedRune>0,1.15f);button(d,right,"›",selectedRune<RuneType.values().length-1,1.15f);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=10.5f*ui;d.setColor(r.color);d.text(r.title,width/2,ct+18f*ui);d.bold=false;d.textSize=8.2f*ui;d.setColor(0xFFB7C0C7);d.text(r.description+" • ур. "+state.runeLevels[selectedRune],width/2,ct+37f*ui);d.align=Draw.Align.LEFT;button(d,primary,"УСИЛИТЬ • ◆"+state.runeUpgradeCost(selectedRune),true,.68f);button(d,secondary,"ЦЕЛЬ ›",true,.68f);button(d,tertiary,ellipsize(state.runeTargetTitle(runeTarget),19),false,.58f);button(d,quaternary,state.runeAtTarget(runeTarget)==selectedRune?"СНЯТЬ РУНУ":"НАНЕСТИ РУНУ",state.runeLevels[selectedRune]>0,.62f);}

    private void button(Draw d,Box b,String text,boolean enabled,float scale){d.setColor(enabled?0xFF263039:0xFF1B2024);d.fillRoundRect(b.l,b.t,b.r,b.b,6f*ui);d.setColor(enabled?0xFF526775:0xFF2B3338);d.strokeWidth=1f*ui;d.line(b.l+5f*ui,b.t,b.r-5f*ui,b.t);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=10f*ui*scale;d.setColor(enabled?0xFFF1F4F6:0xFF68727A);if(text!=null&&!text.isEmpty())d.text(text,b.cx(),b.cy()+3.2f*ui);d.align=Draw.Align.LEFT;d.bold=false;}
    private void drawToast(Draw d){if(toastTime<=0)return;float a=Math.min(1,toastTime*2);float w=Math.min(width-40f*ui,280f*ui);d.setColor(alpha(0xDD101316,a));d.fillRoundRect((width-w)/2,worldT+10f*ui,(width+w)/2,worldT+42f*ui,9f*ui);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=9.5f*ui;d.setColor(alpha(0xFFF2F4F5,a));d.text(toast,width/2,worldT+30f*ui);d.align=Draw.Align.LEFT;d.bold=false;}

    private boolean handleTap(float x,float y){
        if(back.hit(x,y)){saveNow();game.openMenu();return true;}
        for(int i=0;i<tabs.length;i++)if(tabs[i].hit(x,y)){tab=Tab.values()[i];return true;}
        if(left.hit(x,y)){switch(tab){case GNOMES->selectedTier=Math.max(0,selectedTier-1);case ARTIFACTS->selectedArtifact=Math.max(0,selectedArtifact-1);case RUNES->selectedRune=Math.max(0,selectedRune-1);default->{}}return true;}
        if(right.hit(x,y)){switch(tab){case GNOMES->selectedTier=Math.min(GnomeTier.values().length-1,selectedTier+1);case ARTIFACTS->selectedArtifact=Math.min(ArtifactType.values().length-1,selectedArtifact+1);case RUNES->selectedRune=Math.min(RuneType.values().length-1,selectedRune+1);default->{}}return true;}
        if(primary.hit(x,y)){switch(tab){case GNOMES->{if(selectedTier==0&&state.buyMiner()){syncWorkers(false);toast="НОВЫЙ ГНОМ";toastTime=1.2f;}else if(selectedTier>0)toast="ГНОМЫ ЭТОГО ТИПА ПОЛУЧАЮТСЯ СЛИЯНИЕМ";}case UPGRADES->buyGlobal(0);case ARTIFACTS->{if(state.upgradeArtifact(selectedArtifact)){toast="АРТЕФАКТ УСИЛЕН";}else toast="НЕ ХВАТАЕТ АЛМАЗОВ";toastTime=1.2f;}case RUNES->{if(state.upgradeRune(selectedRune))toast="РУНА УСИЛЕНА";else toast="НЕ ХВАТАЕТ АЛМАЗОВ";toastTime=1.2f;}}saveNow();return true;}
        if(secondary.hit(x,y)){switch(tab){case GNOMES->{if(state.upgradeTier(selectedTier))toast="ГНОМ УСИЛЕН";else toast="НЕ ХВАТАЕТ РЕСУРСОВ";toastTime=1.2f;}case UPGRADES->buyGlobal(1);case RUNES->{runeTarget=(runeTarget+1)%state.runeTargetCount();}default->{}}saveNow();return true;}
        if(tertiary.hit(x,y)){switch(tab){case GNOMES->{if(state.mergeTier(selectedTier)){syncWorkers(false);toast="ЭВОЛЮЦИЯ • 10 → 1";toastTime=1.4f;}}case UPGRADES->buyGlobal(2);default->{}}saveNow();return true;}
        if(quaternary.hit(x,y)){switch(tab){case UPGRADES->{if(state.buyOrUpgradeGuardian())toast="СТРАЖ СУНДУКА • ур."+state.guardianLevel;else toast="НЕ ХВАТАЕТ РЕСУРСОВ";toastTime=1.3f;}case RUNES->{if(state.engraveRune(runeTarget,selectedRune))toast=state.runeAtTarget(runeTarget)==selectedRune?"РУНА НАНЕСЕНА":"РУНА СНЯТА";else toast="РУНА ЕЩЁ НЕ СОЗДАНА";toastTime=1.3f;}default->{}}saveNow();return true;}
        return true;
    }
    private void buyGlobal(int kind){if(state.buyGlobalUpgrade(kind)){toast="УЛУЧШЕНИЕ КУПЛЕНО";}else toast="НЕ ХВАТАЕТ РЕСУРСОВ";toastTime=1.2f;}

    private void routeMob(Mob m,int goal){m.goalCell=goal;m.path=map.path(cellFor(m.x,m.y),goal);m.pathIndex=Math.min(1,m.path.length);}
    private boolean atCell(Worker w,int cell){if(cell<0)return false;return distance(w.x,w.y,cx(map.col(cell)),cy(map.row(cell)))<Math.min(cellW,cellH)*.20f;}
    private int cellFor(float x,float y){int c=Math.max(0,Math.min(map.cols-1,(int)((x-worldL)/cellW)));int r=Math.max(0,Math.min(map.rows-1,(int)((y-worldT)/cellH)));return map.index(c,r);}
    private float cx(int c){return worldL+(c+.5f)*cellW;}private float cy(int r){return worldT+(r+.5f)*cellH;}

    private void saveNow(){game.saves.save(slot,state);}
    @Override public void pause(){saveNow();speedHeld=false;}
    @Override public void hide(){saveNow();speedHeld=false;}

    private void drawRune(Draw d,float x,float y,float s,RuneType r){d.setColor(alpha(r.color,.28f));d.fillCircle(x,y,s*1.65f);d.setColor(r.color);d.strokeWidth=1.1f*ui;switch(r){case MINING->{d.line(x-s,y+s,x+s,y-s);d.line(x-s*.6f,y-s,x+s*.6f,y+s);}case GREED->{d.strokeCircle(x,y,s);d.line(x,y-s,x,y+s);}case WAR->{d.line(x-s,y-s,x+s,y+s);d.line(x+s,y-s,x-s,y+s);}case HASTE->{d.line(x-s,y,x+s,y);d.line(x+s*.25f,y-s*.65f,x+s,y);d.line(x+s*.25f,y+s*.65f,x+s,y);}case WARD->{d.pathReset();d.moveTo(x,y-s);d.lineTo(x+s,y-s*.3f);d.lineTo(x+s*.6f,y+s);d.lineTo(x-s*.6f,y+s);d.lineTo(x-s,y-s*.3f);d.closePath();d.strokePath();}case FRACTURE->{d.line(x-s,y-s,x,y);d.line(x,y,x+s,y-s*.4f);d.line(x,y,x+s*.5f,y+s);}}}
    private String runeName(int idx){return idx>=0&&idx<RuneType.values().length?RuneType.values()[idx].title:"нет";}

    private static float len(float x,float y){return(float)Math.sqrt(x*x+y*y);}private static float distance(float x1,float y1,float x2,float y2){return len(x2-x1,y2-y1);}private static float dist2(float x1,float y1,float x2,float y2){float dx=x2-x1,dy=y2-y1;return dx*dx+dy*dy;}
    private static float lerp(float a,float b,float t){return a+(b-a)*Math.max(0,Math.min(1,t));}private static float ease(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    private static float hash01(long x){x^=x>>>33;x*=0xff51afd7ed558ccdL;x^=x>>>33;x*=0xc4ceb9fe1a85ec53L;x^=x>>>33;return (x&0xFFFFFF)/(float)0x1000000;}
    private static int adjust(int c,float f){int a=(c>>>24)&255,r=(c>>>16)&255,g=(c>>>8)&255,b=c&255;r=Math.min(255,Math.max(0,(int)(r*f)));g=Math.min(255,Math.max(0,(int)(g*f)));b=Math.min(255,Math.max(0,(int)(b*f)));return(a<<24)|(r<<16)|(g<<8)|b;}
    private static int alpha(int c,float a){int aa=Math.max(0,Math.min(255,(int)(a*255)));return(aa<<24)|(c&0xFFFFFF);}
    private String format(long n){if(n>=1_000_000_000L)return one.format(n/1_000_000_000d)+"B";if(n>=1_000_000L)return one.format(n/1_000_000d)+"M";if(n>=10_000L)return one.format(n/1_000d)+"K";return Long.toString(n);}
    private static String ellipsize(String s,int n){return s.length()<=n?s:s.substring(0,Math.max(1,n-1))+"…";}
}
