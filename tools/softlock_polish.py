from pathlib import Path

path = Path('core/src/main/java/com/enhort/gnomes/game/CaveScreen.java')
s = path.read_text(encoding='utf-8')

repls = []

def rep(old, new, label):
    global s
    if old not in s:
        raise SystemExit(f'missing anchor: {label}')
    s = s.replace(old, new, 1)

rep(
    '        float hp, hitFlash, death, spawn=.38f;\n        boolean dead;\n        Vein(RockType type,int cell,int side,int seed,float x,float y,float r,float hp){this.type=type;this.cell=cell;this.side=side;this.seed=seed;this.x=x;this.y=y;this.r=r;this.maxHp=hp;this.hp=hp;}\n',
    '        float hp, hitFlash, death, spawn=.38f;\n        boolean dead,recovery;\n        Vein(RockType type,int cell,int side,int seed,float x,float y,float r,float hp){this.type=type;this.cell=cell;this.side=side;this.seed=seed;this.x=x;this.y=y;this.r=r;this.maxHp=hp;this.hp=hp;}\n',
    'vein recovery flag'
)

rep(
    '    private float elapsed,enemyTimer=13f,hazardTimer=18f,saveTimer,levelClearTimer=-1f,screenShake;\n',
    '    private float elapsed,enemyTimer=13f,hazardTimer=18f,saveTimer,levelClearTimer=-1f,screenShake,recoveryTimer;\n',
    'recovery timer field'
)

rep(
    '        levelClearTimer=-1f;setupObjective();\n',
    '        levelClearTimer=-1f;recoveryTimer=0f;setupObjective();\n',
    'recovery timer reset'
)

rep(
    '        updateVeins(dt);updatePortal(dt);updateGuardian(dt);updateMobs(dt);updateHazards(dt);updateWorkers(dt*workerTimeScale);updateFx(dt);updateObjective();\n',
    '        updateVeins(dt);updatePortal(dt);updateGuardian(dt);updateMobs(dt);updateHazards(dt);updateWorkers(dt*workerTimeScale);updateFx(dt);updateObjective();updateResourceRecovery(dt);\n',
    'recovery update hook'
)

anchor = '''    private void updateVeins(float dt){
        float suppression=state.regenSuppression();
        for(Vein v:veins){
            v.hitFlash=Math.max(0,v.hitFlash-dt);v.spawn=Math.max(0,v.spawn-dt);
            if(v.dead){v.death+=dt;continue;}
            if(v.type.regenPerSecond>0&&v.hp<v.maxHp){float regen=v.type.regenPerSecond*(1-suppression)*(1+state.depth*.02f);v.hp=Math.min(v.maxHp,v.hp+regen*dt);}
        }
    }
'''
insert = anchor + '''
    /**
     * Anti-softlock economy. If every mineable vein is exhausted but the current objective still needs work,
     * the mountain slowly exposes a few fresh deposits. CLEAR_VEINS is intentionally excluded, otherwise
     * that objective would become an impressively literal form of eternal employment.
     */
    private void updateResourceRecovery(float dt){
        boolean stranded=objectiveType!=ObjectiveType.CLEAR_VEINS&&!levelObjectiveMet()&&noLivingVeins()&&state.totalGnomes()>0;
        if(!stranded){recoveryTimer=0f;return;}
        recoveryTimer+=dt;
        float delay=state.depth==1?9f:14f;
        if(recoveryTimer<delay)return;
        recoveryTimer=0f;
        spawnRecoveryVeins(state.depth==1?3:2);
    }

    private void spawnRecoveryVeins(int wanted){
        List<Integer> candidates=new ArrayList<>();
        int start=map.index(map.startCol,map.startRow);
        for(int r=0;r<map.rows;r++)for(int c=0;c<map.cols;c++){
            int cell=map.index(c,r);
            if(cell==start||!recoveryCellSafe(cell))continue;
            candidates.add(cell);
        }
        if(candidates.isEmpty())return;
        java.util.Collections.shuffle(candidates,random);
        int spawned=0;
        for(int cell:candidates){
            if(spawned>=wanted)break;
            int c=map.col(cell),r=map.row(cell),side=map.preferredSolidSide(c,r,random.nextInt());
            float pocket=Math.min(cellW,cellH)*(.22f+random.nextFloat()*.09f);
            float tangent=(random.nextFloat()-.5f)*Math.min(cellW,cellH)*.24f;
            float x=cx(c)+CaveMap.dx(side)*pocket+(side==CaveMap.N||side==CaveMap.S?tangent:0);
            float y=cy(r)+CaveMap.dy(side)*pocket+(side==CaveMap.E||side==CaveMap.W?tangent:0);
            RockType type=recoveryRockType();
            float radius=Math.min(cellW,cellH)*(.16f+random.nextFloat()*.06f);
            float hp=type.hp*(1f+Math.max(0,state.depth-1)*.035f);
            Vein v=new Vein(type,cell,side,random.nextInt(),x,y,radius,hp);
            v.recovery=true;v.spawn=.82f;veins.add(v);spawned++;
        }
        if(spawned<=0)return;
        resetWorkerRoutes();
        toast="ГОРА ОТКРЫЛА НОВЫЕ ЖИЛЫ";toastTime=2f;
        addNotice("НОВЫЕ ЖИЛЫ • "+spawned,0xFFD8B16A,2.8f);
        game.audio.play(GameAudio.Sfx.ROCK_BREAK,.42f);game.audio.vibrate(24);
    }

    private RockType recoveryRockType(){
        if(state.depth<=1)return RockType.STONE;
        float q=random.nextFloat();
        if(state.depth>=6&&q<.07f)return RockType.GOLD;
        if(q<.34f)return RockType.SILVER;
        return RockType.STONE;
    }

    private boolean recoveryCellSafe(int cell){
        if(map.isBlocked(cell))return false;
        for(CaveHazard h:hazards)if(!h.cleared&&h.cell==cell)return false;
        for(Worker w:workers)if(map.path(cellFor(w.x,w.y),cell).length>0)return true;
        return false;
    }
'''
rep(anchor, insert, 'recovery methods anchor')

rep(
    '        if(v.dead)return;v.dead=true;v.hp=0;v.death=0;w.add(v.type.material,state.yieldFor(v.type,w.tier.ordinal())*levelYieldMultiplier());\n',
    '        if(v.dead)return;v.dead=true;v.hp=0;v.death=0;w.add(v.type.material,state.yieldFor(v.type,w.tier.ordinal())*levelYieldMultiplier());\n',
    'break vein anchor'
)

rep(
    '        if(state.totalGnomes()>=5&&mobs.size()<30&&random.nextFloat()<Math.min(.075f,.025f+state.depth*.0015f))spawnGhostFrom(v);\n',
    '        if(!v.recovery&&state.totalGnomes()>=5&&mobs.size()<30&&random.nextFloat()<Math.min(.075f,.025f+state.depth*.0015f))spawnGhostFrom(v);\n',
    'no ghost from recovery vein'
)

rep(
    '    private void beginGameOver(){if(gameOver)return;gameOver=true;speedHeld=false;clearPriority(false);saveNow();game.audio.vibrate(120);}\n',
    '    private void beginGameOver(){if(gameOver)return;gameOver=true;speedHeld=false;clearPriority(false);toast="ЭКСПЕДИЦИЯ ПОГИБЛА";toastTime=0;addNotice("ПОСЛЕДНИЙ ГНОМ ПАЛ",0xFFE66658,2f);saveNow();game.audio.vibrate(120);}\n',
    'game over polish'
)

path.write_text(s, encoding='utf-8')
print('softlock polish applied')
