from pathlib import Path
import re

ROOT = Path('.')
CAVE = ROOT / 'core/src/main/java/com/enhort/gnomes/game/CaveScreen.java'
SAVE = ROOT / 'core/src/main/java/com/enhort/gnomes/save/SaveRepository.java'
MENU = ROOT / 'core/src/main/java/com/enhort/gnomes/menu/MenuScreen.java'


def replace_once(text: str, old: str, new: str, label: str) -> str:
    n = text.count(old)
    if n != 1:
        raise SystemExit(f'{label}: expected exactly one match, got {n}')
    return text.replace(old, new, 1)


s = CAVE.read_text()

# Guardian movement uses the tunnel graph instead of straight-line movement through rock.
s = replace_once(
    s,
    '    private float guardianX,guardianY,guardianHp,guardianMaxHp;\n'
    '    private boolean guardianDead;\n'
    '    private Mob guardianTarget;',
    '    private float guardianX,guardianY,guardianHp,guardianMaxHp;\n'
    '    private boolean guardianDead;\n'
    '    private Mob guardianTarget;\n'
    '    private int[] guardianPath=new int[0]; private int guardianPathIndex,guardianGoal=-1;',
    'guardian fields'
)
s = replace_once(
    s,
    'guardianDead=state.guardianLevel<=0;guardianTarget=null;',
    'guardianDead=state.guardianLevel<=0;guardianTarget=null;guardianPath=new int[0];guardianPathIndex=0;guardianGoal=-1;',
    'guardian level reset'
)

pattern = r'    private void updateGuardian\(float dt\)\{.*?(?=\n    private void updateWorkers)'
replacement = '''    private void updateGuardian(float dt){
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
'''
s, n = re.subn(pattern, replacement, s, count=1, flags=re.S)
if n != 1:
    raise SystemExit('guardian method replacement failed')

# Visible hazards invalidate both new and stale worker routes.
s = replace_once(
    s,
    '            int node=w.path[w.pathIndex];if(map.isBlocked(node)){w.path=new int[0];w.pathIndex=0;w.goalCell=-1;w.vx=w.vy=0;return;}float tx=',
    '            int node=w.path[w.pathIndex];if(map.isBlocked(node)||isDangerCellFor(w,node)){w.path=new int[0];w.pathIndex=0;w.goalCell=-1;w.vx=w.vy=0;return;}float tx=',
    'dynamic danger route guard'
)
s = replace_once(
    s,
    '        }return mask;\n    }\n\n    private void followWorker',
    '''        }return mask;
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

    private void followWorker''',
    'danger helper'
)

# Normal imps steal and flee. The Imp King behaves as a real boss and can attack.
s = replace_once(s, 'boolean thief=m.type.isImp();', 'boolean thief=m.type==EnemyType.IMP;', 'normal imp thief')
s = replace_once(
    s,
    'boolean canHitGuard=!guardianDead&&state.guardianLevel>0&&(m.type.isDemon()||m.type.isElemental()||(m.type.isImp()&&m.enraged));',
    'boolean canHitGuard=!guardianDead&&state.guardianLevel>0&&(m.type.isDemon()||m.type.isElemental()||m.type==EnemyType.IMP_KING||(m.type==EnemyType.IMP&&m.enraged));',
    'guardian enemy targeting'
)
s = replace_once(
    s,
    'm.path=(thief&&!m.enraged)?map.pathIgnoringBlocks(cellFor(m.x,m.y),goal):map.path(cellFor(m.x,m.y),goal);',
    'm.path=m.type.isImp()?map.pathIgnoringBlocks(cellFor(m.x,m.y),goal):map.path(cellFor(m.x,m.y),goal);',
    'imp cave-in routing'
)
s = replace_once(
    s,
    'if(w==null||m.attackCooldown>0||m.type.isImp()||m.type==EnemyType.SUCCUBUS||m.type==EnemyType.GHOST)return;',
    'if(w==null||m.attackCooldown>0||m.type==EnemyType.IMP||m.type==EnemyType.SUCCUBUS||m.type==EnemyType.GHOST)return;',
    'imp king worker attack'
)
s = replace_once(
    s,
    'if(guardianDead||m.attackCooldown>0||m.type==EnemyType.SUCCUBUS||m.type==EnemyType.GHOST||m.type.isImp()&&!m.enraged)return;',
    'if(guardianDead||m.attackCooldown>0||m.type==EnemyType.SUCCUBUS||m.type==EnemyType.GHOST||m.type==EnemyType.IMP&&!m.enraged)return;',
    'imp king guardian attack'
)

# Ghosts are rare in ordinary waves; most still emerge from broken rock.
s = replace_once(
    s,
    'private EnemyType chooseEnemyType(){int d=state.depth;float r=random.nextFloat();if(d>=18&&r<.12f)return EnemyType.FIRE_GOLEM;if(d>=15&&r<.24f)return EnemyType.WATER_GOLEM;if(d>=12&&r<.37f)return EnemyType.STONE_GOLEM;if(d>=9&&r<.45f&&!hasLiving(EnemyType.SUCCUBUS))return EnemyType.SUCCUBUS;if(d>=7&&r<.67f)return EnemyType.DEMON;return EnemyType.IMP;}',
    'private EnemyType chooseEnemyType(){int d=state.depth;float r=random.nextFloat();if(d>=4&&r<.018f)return EnemyType.GHOST;if(d>=18&&r<.13f)return EnemyType.FIRE_GOLEM;if(d>=15&&r<.25f)return EnemyType.WATER_GOLEM;if(d>=12&&r<.38f)return EnemyType.STONE_GOLEM;if(d>=9&&r<.42f&&!hasLiving(EnemyType.SUCCUBUS))return EnemyType.SUCCUBUS;if(d>=7&&r<.66f)return EnemyType.DEMON;return EnemyType.IMP;}',
    'enemy wave probabilities'
)

# Ghosts fly over floor hazards. Succubi are demonic and can die in lethal traps.
s = replace_once(
    s,
    'if(m.dead||distance(m.x,m.y,h.x,h.y)>=rr)continue;',
    'if(m.dead||m.type==EnemyType.GHOST||distance(m.x,m.y,h.x,h.y)>=rr)continue;',
    'lava ghost immunity'
)
s = replace_once(
    s,
    'if(m.type==EnemyType.IMP||m.type==EnemyType.DEMON)m.hp=0;',
    'if(m.type==EnemyType.IMP||m.type==EnemyType.DEMON||m.type==EnemyType.SUCCUBUS)m.hp=0;',
    'lava succubus lethality'
)
s = replace_once(
    s,
    'if(m.dead||distance(m.x,m.y,h.x,h.y)>h.r)continue;\n            boolean small=m.type==EnemyType.IMP||m.type==EnemyType.DEMON;',
    'if(m.dead||m.type==EnemyType.GHOST||distance(m.x,m.y,h.x,h.y)>h.r)continue;\n            boolean small=m.type==EnemyType.IMP||m.type==EnemyType.DEMON||m.type==EnemyType.SUCCUBUS;',
    'hazard demonic targets'
)

# Cave-in route invalidation also applies to the mobile chest guard.
s = replace_once(
    s,
    '        for(Mob m:mobs){m.goalCell=-1;m.path=new int[0];m.pathIndex=0;m.routeTimer=0;}\n    }',
    '        for(Mob m:mobs){m.goalCell=-1;m.path=new int[0];m.pathIndex=0;m.routeTimer=0;}guardianPath=new int[0];guardianPathIndex=0;guardianGoal=-1;\n    }',
    'guardian route invalidation'
)

CAVE.write_text(s)

# Preserve legacy rune progress before the new account-wide rune profile is initialized.
q = SAVE.read_text()
q = replace_once(
    q,
    '    private final Json json = new Json();\n\n    public boolean exists(int slot)',
    '''    private final Json json = new Json();

    public SaveRepository() { migrateAllLegacyRuneProgress(); }

    private void migrateAllLegacyRuneProgress() {
        if (meta.getBoolean("initialized", false)) return;
        int[] best = new int[com.enhort.gnomes.game.model.RuneType.values().length];
        boolean[] active = new boolean[best.length];
        for (int slot = 1; slot <= SLOT_COUNT; slot++) {
            if (!prefs.contains(key(slot))) continue;
            try {
                Snapshot snap = json.fromJson(Snapshot.class, prefs.getString(key(slot)));
                if (snap == null || snap.runeLevels == null) continue;
                for (int i = 0; i < Math.min(best.length, snap.runeLevels.length); i++) {
                    if (snap.runeLevels[i] > best[i]) best[i] = snap.runeLevels[i];
                    boolean wasActive = snap.runeActive == null || i >= snap.runeActive.length || snap.runeActive[i];
                    active[i] |= snap.runeLevels[i] > 0 && wasActive;
                }
            } catch (Exception ignored) { }
        }
        Preferences old = Gdx.app.getPreferences("gnomes_save_v2");
        for (int i = 0; i < best.length; i++) {
            best[i] = Math.max(best[i], old.getInteger("runeLevel_" + i, 0));
            if (best[i] > 0) active[i] = true;
            meta.putInteger("runeLevel_" + i, best[i]);
            meta.putBoolean("runeActive_" + i, active[i]);
        }
        meta.putBoolean("initialized", true);
        meta.flush();
    }

    public boolean exists(int slot)''',
    'legacy rune migration'
)
SAVE.write_text(q)

menu = MENU.read_text().replace('DEEP MINE • ALPHA 0.4', 'DEEP MINE • ALPHA 0.5')
MENU.write_text(menu)

print('GNOMES v0.5 stabilization applied')
