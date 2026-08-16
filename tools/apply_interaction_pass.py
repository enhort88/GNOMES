from pathlib import Path
import math, random, struct, wave

ROOT = Path(__file__).resolve().parents[1]

def rep(path, old, new):
    p = ROOT / path
    s = p.read_text()
    if old not in s:
        raise SystemExit(f"pattern not found in {path}: {old[:120]!r}")
    p.write_text(s.replace(old, new, 1))

# ---------------- GameState: persistent hidden free-shop mode ----------------
path = "core/src/main/java/com/enhort/gnomes/game/GameState.java"
rep(path,
"public class GameState {\n    private static final String PREFS = \"gnomes_save_v2\";",
"public class GameState {\n    /** Hidden test flag toggled from the main-menu GNOMES logo. */\n    public static boolean FREE_SHOP = false;\n    private static final String PREFS = \"gnomes_save_v2\";")
rep(path,
"    public long guardianCost() {\n        if (guardianLevel == 0) return 300;\n        return Math.round(260 * Math.pow(1.85, guardianLevel));\n    }",
"    public long guardianCost() {\n        if (FREE_SHOP) return 0;\n        if (guardianLevel == 0) return 300;\n        return Math.round(260 * Math.pow(1.85, guardianLevel));\n    }")
rep(path,
"    public boolean buyOrUpgradeGuardian() {\n        long cost = guardianCost();\n        if (guardianLevel < 3) {\n            if (stone < cost) return false;\n            stone -= cost;\n        } else {\n            long silverCost = Math.max(4, cost / 120);\n            if (silver < silverCost) return false;\n            silver -= silverCost;\n        }\n        guardianLevel++;\n        return true;\n    }",
"    public boolean buyOrUpgradeGuardian() {\n        long cost = guardianCost();\n        if (!FREE_SHOP) {\n            if (guardianLevel < 3) {\n                if (stone < cost) return false;\n                stone -= cost;\n            } else {\n                long silverCost = Math.max(4, cost / 120);\n                if (silver < silverCost) return false;\n                silver -= silverCost;\n            }\n        }\n        guardianLevel++;\n        return true;\n    }")
rep(path,
"    public String guardianCostLabel() {\n        long cost = guardianCost();",
"    public String guardianCostLabel() {\n        if (FREE_SHOP) return \"БЕСПЛАТНО\";\n        long cost = guardianCost();")
rep(path,
"    public long minerBuyCost() {\n        return Math.round(85 * Math.pow(1.27, tierCounts[0]));\n    }",
"    public long minerBuyCost() {\n        if (FREE_SHOP) return 0;\n        return Math.round(85 * Math.pow(1.27, tierCounts[0]));\n    }")
rep(path,
"    public long tierUpgradeCost(int tier) {\n        int lvl = tierLevels[tier];",
"    public long tierUpgradeCost(int tier) {\n        if (FREE_SHOP) return 0;\n        int lvl = tierLevels[tier];")
rep(path,
"    public boolean buyMiner() {\n        long cost = minerBuyCost();\n        if (stone < cost || tierCounts[0] >= 99) return false;\n        stone -= cost;\n        tierCounts[0]++;\n        return true;\n    }",
"    public boolean buyMiner() {\n        long cost = minerBuyCost();\n        if (tierCounts[0] >= 99) return false;\n        if (!FREE_SHOP) {\n            if (stone < cost) return false;\n            stone -= cost;\n        }\n        tierCounts[0]++;\n        return true;\n    }")
rep(path,
"    public boolean upgradeTier(int tier) {\n        if (tier < 0 || tier >= tierLevels.length) return false;\n        long cost = tierUpgradeCost(tier);\n        if (tier < 2) {\n            if (stone < cost) return false;\n            stone -= cost;\n        } else if (tier < 4) {\n            if (silver < Math.max(1, cost / 90)) return false;\n            silver -= Math.max(1, cost / 90);\n        } else {\n            if (gold < Math.max(1, cost / 180)) return false;\n            gold -= Math.max(1, cost / 180);\n        }\n        tierLevels[tier]++;\n        return true;\n    }",
"    public boolean upgradeTier(int tier) {\n        if (tier < 0 || tier >= tierLevels.length) return false;\n        long cost = tierUpgradeCost(tier);\n        if (!FREE_SHOP) {\n            if (tier < 2) {\n                if (stone < cost) return false;\n                stone -= cost;\n            } else if (tier < 4) {\n                if (silver < Math.max(1, cost / 90)) return false;\n                silver -= Math.max(1, cost / 90);\n            } else {\n                if (gold < Math.max(1, cost / 180)) return false;\n                gold -= Math.max(1, cost / 180);\n            }\n        }\n        tierLevels[tier]++;\n        return true;\n    }")
rep(path,
"    public long globalUpgradeCost(int kind) {\n        int lvl = kind == 0 ? miningUpgrade : kind == 1 ? speedUpgrade : combatUpgrade;",
"    public long globalUpgradeCost(int kind) {\n        if (FREE_SHOP) return 0;\n        int lvl = kind == 0 ? miningUpgrade : kind == 1 ? speedUpgrade : combatUpgrade;")
rep(path,
"    public boolean buyGlobalUpgrade(int kind) {\n        long cost = globalUpgradeCost(kind);",
"    public boolean buyGlobalUpgrade(int kind) {\n        if (FREE_SHOP) {\n            if (kind == 0) miningUpgrade++;\n            else if (kind == 1) speedUpgrade++;\n            else combatUpgrade++;\n            return true;\n        }\n        long cost = globalUpgradeCost(kind);")
rep(path,
"    public int artifactCost(int artifactIndex) {\n        int lvl = artifactLevels[artifactIndex];",
"    public int artifactCost(int artifactIndex) {\n        if (FREE_SHOP) return 0;\n        int lvl = artifactLevels[artifactIndex];")
rep(path,
"    public boolean upgradeArtifact(int artifactIndex) {\n        int cost = artifactCost(artifactIndex);\n        if (diamond < cost) return false;\n        diamond -= cost;\n        artifactLevels[artifactIndex]++;\n        return true;\n    }",
"    public boolean upgradeArtifact(int artifactIndex) {\n        int cost = artifactCost(artifactIndex);\n        if (!FREE_SHOP) {\n            if (diamond < cost) return false;\n            diamond -= cost;\n        }\n        artifactLevels[artifactIndex]++;\n        return true;\n    }")
rep(path,
"    public int runeUpgradeCost(int runeIndex) {\n        int lvl = runeLevels[runeIndex];",
"    public int runeUpgradeCost(int runeIndex) {\n        if (FREE_SHOP) return 0;\n        int lvl = runeLevels[runeIndex];")
rep(path,
"    public boolean upgradeRune(int runeIndex) {\n        if (runeIndex < 0 || runeIndex >= runeLevels.length) return false;\n        int cost = runeUpgradeCost(runeIndex);\n        if (diamond < cost) return false;\n        diamond -= cost;\n        runeLevels[runeIndex]++;\n        return true;\n    }",
"    public boolean upgradeRune(int runeIndex) {\n        if (runeIndex < 0 || runeIndex >= runeLevels.length) return false;\n        int cost = runeUpgradeCost(runeIndex);\n        if (!FREE_SHOP) {\n            if (diamond < cost) return false;\n            diamond -= cost;\n        }\n        runeLevels[runeIndex]++;\n        return true;\n    }")

# ---------------- CaveMap: dynamic rubble cells used by collapses ----------------
path = "core/src/main/java/com/enhort/gnomes/game/CaveMap.java"
rep(path,
"    public final long seed;\n\n    private final Random random;",
"    public final long seed;\n\n    private final Random random;\n    private final boolean[] blocked;")
rep(path,
"        this.openings = new int[this.rows][this.cols];\n        this.startCol = this.cols / 2;",
"        this.openings = new int[this.rows][this.cols];\n        this.blocked = new boolean[this.rows * this.cols];\n        this.startCol = this.cols / 2;")
rep(path,
"    public boolean connected(int c, int r, int dir) {\n        return inside(c, r) && (openings[r][c] & dir) != 0;\n    }",
"    public boolean connected(int c, int r, int dir) {\n        return inside(c, r) && (openings[r][c] & dir) != 0;\n    }\n\n    public boolean blockCell(int cell) {\n        if (cell < 0 || cell >= blocked.length || cell == index(startCol, startRow)) return false;\n        blocked[cell] = true;\n        return true;\n    }\n\n    public void unblockCell(int cell) {\n        if (cell >= 0 && cell < blocked.length) blocked[cell] = false;\n    }\n\n    public boolean isBlocked(int cell) {\n        return cell >= 0 && cell < blocked.length && blocked[cell];\n    }")
rep(path,
"        if (start < 0 || start >= count || goal < 0 || goal >= count) return new int[0];\n        if (start == goal) return new int[] { start };",
"        if (start < 0 || start >= count || goal < 0 || goal >= count) return new int[0];\n        if (isBlocked(goal) && goal != start) return new int[0];\n        if (start == goal) return new int[] { start };")
rep(path,
"                int next = index(nc, nr);\n                if (parent[next] != -2) continue;",
"                int next = index(nc, nr);\n                if (next != start && isBlocked(next)) continue;\n                if (parent[next] != -2) continue;")

# ---------------- CaveScreen: manual priority, obstacles, feedback, rock identities ----------------
path = "core/src/main/java/com/enhort/gnomes/game/CaveScreen.java"
rep(path,
"import com.enhort.gnomes.GnomesGame;",
"import com.enhort.gnomes.GnomesGame;\nimport com.enhort.gnomes.GameAudio;")
rep(path,
"        float age; boolean fired;\n        CaveHazard(HazardType type,int cell,float x,float y,float r){this.type=type;this.cell=cell;this.x=x;this.y=y;this.r=r;}",
"        float age; boolean fired, obstacleActive;\n        CaveHazard(HazardType type,int cell,float x,float y,float r){this.type=type;this.cell=cell;this.x=x;this.y=y;this.r=r;}")
rep(path,
"    private boolean speedHeld;\n    private Tab tab=Tab.GNOMES;",
"    private boolean speedHeld;\n    private Vein priorityVein;\n    private float priorityPulse;\n    private Tab tab=Tab.GNOMES;")
rep(path,
"                if(speed.hit(x,y)){speedHeld=true;return true;}\n                return handleTap(x,y);",
"                if(speed.hit(x,y)){speedHeld=true;game.audio.play(GameAudio.Sfx.UI,.55f);return true;}\n                if(y>=worldT&&y<=worldB)return handleWorldTap(x,y);\n                return handleTap(x,y);")
rep(path,
"        mobs.clear();hazards.clear();fx.clear();veins.clear();",
"        mobs.clear();hazards.clear();fx.clear();veins.clear();priorityVein=null;")
rep(path,
"        if(toastTime>0)toastTime-=dt;\n        if(screenShake>0)screenShake=Math.max(0,screenShake-dt*18f*ui);",
"        if(toastTime>0)toastTime-=dt;\n        priorityPulse+=dt;\n        if(screenShake>0)screenShake=Math.max(0,screenShake-dt*18f*ui);")
rep(path,
"            if(w.stun>0){w.action=WorkerAction.STUNNED;w.vx=w.vy=0;continue;}\n            Mob enemy=nearestMob(w.x,w.y);",
"            if(w.stun>0){w.action=WorkerAction.STUNNED;w.vx=w.vy=0;continue;}\n            if(priorityVein!=null&&!priorityVein.dead){\n                w.mob=null;w.vein=priorityVein;mine(w,priorityVein,dt);continue;\n            }\n            Mob enemy=nearestMob(w.x,w.y);")
rep(path,
"            v.hp-=damage;v.hitFlash=.16f;screenShake=Math.max(screenShake,Math.min(2.8f*ui,.35f*ui+w.tier.ordinal()*.36f*ui));\n            spawnRockHit(v,w.tier.ordinal());",
"            v.hp-=damage;v.hitFlash=.16f;screenShake=Math.max(screenShake,Math.min(2.8f*ui,.35f*ui+w.tier.ordinal()*.36f*ui));\n            game.audio.play(GameAudio.Sfx.PICK,.32f+.07f*w.tier.ordinal());\n            spawnRockHit(v,w.tier.ordinal());")
rep(path,
"        state.rocksBroken++;state.depthProgress++;spawnBreak(v);screenShake=Math.max(screenShake,3.3f*ui);\n        if(v.type.ordinal()>=RockType.DIAMOND.ordinal()){toast=v.type.title.toUpperCase()+\" ДОБЫТ\";toastTime=1.4f;}",
"        state.rocksBroken++;state.depthProgress++;spawnBreak(v);screenShake=Math.max(screenShake,3.3f*ui);\n        game.audio.play(GameAudio.Sfx.ROCK_BREAK,.88f);game.audio.vibrate(26+Math.min(42,w.tier.ordinal()*7));\n        if(v==priorityVein){priorityVein=null;toast=\"ПРИОРИТЕТ ДОБЫТ • \"+v.type.title.toUpperCase();toastTime=1.5f;}\n        else if(v.type.ordinal()>=RockType.DIAMOND.ordinal()){toast=v.type.title.toUpperCase()+\" ДОБЫТ\";toastTime=1.4f;}")
rep(path,
"            w.clearCargo();spawnSparks(cx(map.startCol),cy(map.startRow),0xFFFFCC63,5);",
"            w.clearCargo();spawnSparks(cx(map.startCol),cy(map.startRow),0xFFFFCC63,5);game.audio.play(GameAudio.Sfx.COIN,.42f);")
rep(path,
"        if(w.swing>0&&!w.hitApplied&&p>.53f){w.hitApplied=true;float dmg=w.tier.combatPower*state.tierPowerMultiplier(w.tier.ordinal())*state.combatMultiplier(w.tier.ordinal());m.hp-=dmg;m.attack=.14f;spawnSparks(m.x,m.y,0xFFFFB74D,5);}",
"        if(w.swing>0&&!w.hitApplied&&p>.53f){w.hitApplied=true;float dmg=w.tier.combatPower*state.tierPowerMultiplier(w.tier.ordinal())*state.combatMultiplier(w.tier.ordinal());m.hp-=dmg;m.attack=.14f;spawnSparks(m.x,m.y,0xFFFFB74D,5);game.audio.play(GameAudio.Sfx.ENEMY,.32f);}")
rep(path,
"    private Vein chooseVein(Worker w){Vein best=null;float bd=Float.MAX_VALUE;for(Vein v:veins){if(v.dead)continue;float d=dist2(w.x,w.y,cx(map.col(v.cell)),cy(map.row(v.cell)));if(d<bd){bd=d;best=v;}}return best;}",
"    private Vein chooseVein(Worker w){if(priorityVein!=null&&!priorityVein.dead)return priorityVein;Vein best=null;float bd=Float.MAX_VALUE;for(Vein v:veins){if(v.dead||map.isBlocked(v.cell))continue;float d=dist2(w.x,w.y,cx(map.col(v.cell)),cy(map.row(v.cell)));if(d<bd){bd=d;best=v;}}return best;}")
rep(path,
"        if(before>after){toast=\"БЕС В СУНДУКЕ  −\"+format(before-after);toastTime=1.5f;spawnSparks(cx(map.startCol),cy(map.startRow),0xFFFFC04A,10);}",
"        if(before>after){toast=\"БЕС В СУНДУКЕ  −\"+format(before-after);toastTime=1.5f;spawnSparks(cx(map.startCol),cy(map.startRow),0xFFFFC04A,10);game.audio.play(GameAudio.Sfx.COIN,.65f);game.audio.vibrate(40);}")
rep(path,
"    private void spawnEnemyWave(){EnemyType type=chooseEnemyType();int n=type==EnemyType.IMP?2+random.nextInt(3):1;for(int i=0;i<n;i++)spawnMob(type);toast=type.title.toUpperCase()+(n>1?\" ×\"+n:\"\");toastTime=1.3f;}",
"    private void spawnEnemyWave(){EnemyType type=chooseEnemyType();int n=type==EnemyType.IMP?2+random.nextInt(3):1;for(int i=0;i<n;i++)spawnMob(type);toast=type.title.toUpperCase()+(n>1?\" ×\"+n:\"\");toastTime=1.3f;game.audio.play(GameAudio.Sfx.ENEMY,.48f);}")
rep(path,
"    private void spawnBoss(){EnemyType t=state.depth>=30?EnemyType.ELEMENTAL_KING:state.depth>=20?EnemyType.DEMON_KING:EnemyType.IMP_KING;spawnMob(t);toast=\"БОСС • \"+t.title.toUpperCase();toastTime=2.6f;}",
"    private void spawnBoss(){EnemyType t=state.depth>=30?EnemyType.ELEMENTAL_KING:state.depth>=20?EnemyType.DEMON_KING:EnemyType.IMP_KING;spawnMob(t);toast=\"БОСС • \"+t.title.toUpperCase();toastTime=2.6f;game.audio.play(GameAudio.Sfx.BOSS,.90f);game.audio.vibrate(90);}")
rep(path,
"    private void updateHazards(float dt){\n        for(Iterator<CaveHazard>it=hazards.iterator();it.hasNext();){CaveHazard h=it.next();h.age+=dt;if(!h.fired&&h.age>=1.25f){h.fired=true;fireHazard(h);}if(h.age>hazardDuration(h.type))it.remove();}\n    }\n    private float hazardDuration(HazardType t){return switch(t){case COLLAPSE->3.2f;case PIT->8f;case LAVA->9f;case FLOOD->4.8f;};}",
"    private void updateHazards(float dt){\n        for(Iterator<CaveHazard>it=hazards.iterator();it.hasNext();){\n            CaveHazard h=it.next();h.age+=dt;\n            if(!h.fired&&h.age>=1.25f){h.fired=true;fireHazard(h);}\n            if(h.age>hazardDuration(h.type)){\n                if(h.obstacleActive){map.unblockCell(h.cell);h.obstacleActive=false;invalidateRoutes();}\n                it.remove();\n            }\n        }\n    }\n    private float hazardDuration(HazardType t){return switch(t){case COLLAPSE->16f;case PIT->8f;case LAVA->9f;case FLOOD->4.8f;};}")
rep(path,
"    private void fireHazard(CaveHazard h){screenShake=Math.max(screenShake,h.type==HazardType.COLLAPSE?5f*ui:2f*ui);for(Worker w:new ArrayList<>(workers)){float d=distance(w.x,w.y,h.x,h.y);if(d>h.r)continue;float survive=state.hazardSurvivalBonus(w.tier.ordinal());switch(h.type){case FLOOD->w.stun=Math.max(w.stun,1.4f);case LAVA-> {w.stun=Math.max(w.stun,.7f);if(random.nextFloat()<.10f*(1-survive))loseWorker(w,\"лава\");}case PIT-> {if(random.nextFloat()<.12f*(1-survive))loseWorker(w,\"провалился в яму\");else w.stun=Math.max(w.stun,1f);}case COLLAPSE-> {if(random.nextFloat()<.16f*(1-survive))loseWorker(w,\"попал под обвал\");else w.stun=Math.max(w.stun,.9f);}}}spawnSparks(h.x,h.y,h.type==HazardType.FLOOD?0xFF70C9F4:h.type==HazardType.LAVA?0xFFFF6A24:0xFF918172,14);}",
"    private void fireHazard(CaveHazard h){\n        screenShake=Math.max(screenShake,h.type==HazardType.COLLAPSE?5f*ui:2f*ui);\n        if(h.type==HazardType.COLLAPSE&&map.blockCell(h.cell)){h.obstacleActive=true;invalidateRoutes();game.audio.play(GameAudio.Sfx.COLLAPSE,.92f);game.audio.vibrate(120);}\n        else {game.audio.play(GameAudio.Sfx.HAZARD,.62f);game.audio.vibrate(45);}\n        for(Worker w:new ArrayList<>(workers)){float d=distance(w.x,w.y,h.x,h.y);if(d>h.r)continue;float survive=state.hazardSurvivalBonus(w.tier.ordinal());switch(h.type){case FLOOD->w.stun=Math.max(w.stun,1.4f);case LAVA-> {w.stun=Math.max(w.stun,.7f);if(random.nextFloat()<.10f*(1-survive))loseWorker(w,\"лава\");}case PIT-> {if(random.nextFloat()<.12f*(1-survive))loseWorker(w,\"провалился в яму\");else w.stun=Math.max(w.stun,1f);}case COLLAPSE-> {if(random.nextFloat()<.16f*(1-survive))loseWorker(w,\"попал под обвал\");else w.stun=Math.max(w.stun,.9f);}}}\n        spawnSparks(h.x,h.y,h.type==HazardType.FLOOD?0xFF70C9F4:h.type==HazardType.LAVA?0xFFFF6A24:0xFF918172,14);\n    }\n\n    private void invalidateRoutes(){\n        for(Worker w:workers){w.goalCell=-1;w.path=new int[0];w.pathIndex=0;}\n        for(Mob m:mobs){m.goalCell=-1;m.path=new int[0];m.pathIndex=0;m.routeTimer=0;}\n    }")
rep(path,
"    private void drawVeins(Draw d){for(Vein v:veins)if(!v.dead||v.death<.55f)drawVein(d,v);}",
"    private void drawVeins(Draw d){for(Vein v:veins)if(!v.dead||v.death<.55f){drawVein(d,v);if(v==priorityVein&&!v.dead)drawPriorityMarker(d,v);}}\n    private void drawPriorityMarker(Draw d,Vein v){float p=.5f+.5f*(float)Math.sin(priorityPulse*6f);float rr=v.r*(1.35f+.10f*p);d.setColor(0x66FFD35A);d.strokeWidth=(1.4f+p*1.2f)*ui;d.strokeCircle(v.x,v.y,rr);d.setColor(0xCCFFD35A);d.pathReset();d.moveTo(v.x,v.y-v.r*1.65f);d.lineTo(v.x-v.r*.22f,v.y-v.r*1.35f);d.lineTo(v.x+v.r*.22f,v.y-v.r*1.35f);d.closePath();d.fillPath();}")
rep(path,
"        polyRock(d,v,x,y,r,adjust(v.type.color,.62f),0);polyRock(d,v,x-r*.08f,y-r*.10f,r*.82f,adjust(v.type.color,.86f),1);\n        // ore veins are embedded lines instead of three arbitrary dots.",
"        polyRock(d,v,x,y,r,adjust(v.type.color,.62f),0);polyRock(d,v,x-r*.08f,y-r*.10f,r*.82f,adjust(v.type.color,.86f),1);\n        drawRockIdentity(d,v,x,y,r,damage);\n        // ore veins are embedded lines instead of three arbitrary dots.")
rep(path,
"    private void polyRock(Draw d,Vein v,float x,float y,float r,int color,int layer){d.setColor(color);d.pathReset();int n=9;for(int i=0;i<n;i++){float a=(float)(Math.PI*2*i/n)+((v.seed>>(i%16))&7)*.009f;float sc=.80f+hash01((long)v.seed*31+i*101+layer*77)*.25f;float px=x+(float)Math.cos(a)*r*sc,py=y+(float)Math.sin(a)*r*sc;if(i==0)d.moveTo(px,py);else d.lineTo(px,py);}d.closePath();d.fillPath();}",
"    private void polyRock(Draw d,Vein v,float x,float y,float r,int color,int layer){d.setColor(color);d.pathReset();int n=9;for(int i=0;i<n;i++){float a=(float)(Math.PI*2*i/n)+((v.seed>>(i%16))&7)*.009f;float sc=.80f+hash01((long)v.seed*31+i*101+layer*77)*.25f;float px=x+(float)Math.cos(a)*r*sc,py=y+(float)Math.sin(a)*r*sc;if(i==0)d.moveTo(px,py);else d.lineTo(px,py);}d.closePath();d.fillPath();}\n\n    private void drawRockIdentity(Draw d,Vein v,float x,float y,float r,float damage){\n        switch(v.type){\n            case STONE -> {\n                d.setColor(0xFF555B61);for(int i=0;i<4;i++){float a=i*1.51f+v.seed*.00007f;d.fillOval(x+(float)Math.cos(a)*r*.38f-r*.13f,y+(float)Math.sin(a)*r*.30f-r*.07f,x+(float)Math.cos(a)*r*.38f+r*.13f,y+(float)Math.sin(a)*r*.30f+r*.07f);}\n            }\n            case SILVER -> {\n                d.setColor(0xFFE7EEF4);d.strokeWidth=Math.max(1.2f*ui,r*.075f);for(int i=-1;i<=1;i++)d.line(x-r*.46f,y+i*r*.22f,x+r*.42f,y+(i-.55f)*r*.22f);d.setColor(0xFF8F9BA6);d.fillCircle(x-r*.25f,y+r*.23f,r*.09f);d.fillCircle(x+r*.28f,y-r*.20f,r*.07f);\n            }\n            case GOLD -> {\n                d.setColor(0xFFFFD24E);for(int i=0;i<6;i++){float a=i*1.047f+v.seed*.0001f;float rr=r*(i%2==0?.34f:.22f);d.fillCircle(x+(float)Math.cos(a)*rr,y+(float)Math.sin(a)*rr,r*(.085f+(i%3)*.018f));}d.setColor(0xFFFFF0A6);d.fillCircle(x-r*.18f,y-r*.18f,r*.055f);\n            }\n            case DIAMOND -> {\n                d.setColor(0xFFB9F4FF);d.pathReset();d.moveTo(x,y-r*.62f);d.lineTo(x+r*.46f,y-r*.10f);d.lineTo(x+r*.22f,y+r*.53f);d.lineTo(x-r*.25f,y+r*.50f);d.lineTo(x-r*.48f,y-r*.08f);d.closePath();d.fillPath();d.setColor(0xFF4AB8D4);d.strokeWidth=1.4f*ui;d.line(x,y-r*.58f,x,y+r*.46f);d.line(x-r*.44f,y-r*.07f,x+r*.43f,y-r*.07f);d.line(x,y+r*.46f,x+r*.43f,y-r*.07f);\n            }\n            case OBSIDIAN -> {\n                d.setColor(0xFF17131D);for(int i=0;i<4;i++){float a=i*1.57f+.3f;d.pathReset();d.moveTo(x,y-r*.62f);d.lineTo(x+(float)Math.cos(a)*r*.64f,y+(float)Math.sin(a)*r*.55f);d.lineTo(x+(float)Math.cos(a+.7f)*r*.33f,y+(float)Math.sin(a+.7f)*r*.31f);d.closePath();d.fillPath();}d.setColor(0xFF9A72E8);d.strokeWidth=1.2f*ui;d.line(x-r*.35f,y+r*.28f,x+r*.18f,y-r*.33f);d.line(x+r*.18f,y-r*.33f,x+r*.42f,y+r*.08f);\n            }\n            case ANCIENT_CRYSTAL -> {\n                d.setColor(0x448A5CFF);d.fillCircle(x,y,r*.78f);int[] cols={0xFFBFA8FF,0xFF8A5CFF,0xFFD9CEFF};for(int i=0;i<3;i++){float ox=(i-1)*r*.28f,top=r*(.72f-(i%2)*.18f);d.setColor(cols[i]);d.pathReset();d.moveTo(x+ox,y-top);d.lineTo(x+ox+r*.18f,y+r*.38f);d.lineTo(x+ox-r*.18f,y+r*.38f);d.closePath();d.fillPath();}d.setColor(0xFFFFFFFF);d.fillCircle(x-r*.04f,y-r*.30f,r*.045f);\n            }\n        }\n    }")
rep(path,
"    private void drawHazards(Draw d){for(CaveHazard h:hazards){float warn=h.age<1.25f?.35f+.20f*(float)Math.sin(h.age*16f):.70f;switch(h.type){case COLLAPSE->{d.setColor(alpha(0xFFD57B4F,warn));d.strokeWidth=2f*ui;d.strokeCircle(h.x,h.y,h.r);if(h.age>=1.25f){d.setColor(0xFF62564B);for(int i=0;i<6;i++){float a=i*1.17f+h.age;d.fillCircle(h.x+(float)Math.cos(a)*h.r*.45f,h.y+(float)Math.sin(a)*h.r*.35f,(4+i%3*2)*ui);}}}",
"    private void drawHazards(Draw d){for(CaveHazard h:hazards){float warn=h.age<1.25f?.35f+.20f*(float)Math.sin(h.age*16f):.70f;switch(h.type){case COLLAPSE->{d.setColor(alpha(0xFFD57B4F,warn));d.strokeWidth=2f*ui;d.strokeCircle(h.x,h.y,h.r);if(h.age>=1.25f){d.setColor(0xFF50483F);for(int i=0;i<11;i++){float a=i*2.17f+h.cell*.31f;float rr=h.r*(.18f+.56f*hash01(h.cell*971L+i*71L));float sz=(4+i%4*2.1f)*ui;d.fillCircle(h.x+(float)Math.cos(a)*rr,h.y+(float)Math.sin(a)*rr*.55f,sz);}d.setColor(0xFF75695D);d.strokeWidth=2f*ui;d.line(h.x-h.r*.65f,h.y+h.r*.16f,h.x+h.r*.64f,h.y-h.r*.12f);}}}")
rep(path,
"    private boolean handleTap(float x,float y){\n        if(back.hit(x,y)){saveNow();game.openMenu();return true;}",
"    private boolean handleTap(float x,float y){\n        game.audio.play(GameAudio.Sfx.UI,.45f);\n        if(back.hit(x,y)){saveNow();game.openMenu();return true;}")
rep(path,
"        return true;\n    }\n    private void buyGlobal(int kind){",
"        return true;\n    }\n\n    private boolean handleWorldTap(float x,float y){\n        Vein best=null;float bd=Float.MAX_VALUE;\n        for(Vein v:veins){if(v.dead)continue;float q=dist2(x,y,v.x,v.y);if(q<bd){bd=q;best=v;}}\n        float pickRadius=Math.min(cellW,cellH)*.80f;\n        if(best!=null&&bd<=pickRadius*pickRadius){\n            priorityVein=best;\n            for(Worker w:workers){w.vein=best;w.mob=null;w.goalCell=-1;w.path=new int[0];w.pathIndex=0;}\n            toast=\"ПРИОРИТЕТ • \"+best.type.title.toUpperCase();toastTime=1.5f;\n            game.audio.play(GameAudio.Sfx.UI,.72f);game.audio.vibrate(18);\n        }else{\n            priorityVein=null;invalidateRoutes();toast=\"ПРИОРИТЕТ СНЯТ\";toastTime=1.0f;game.audio.play(GameAudio.Sfx.UI,.4f);\n        }\n        return true;\n    }\n\n    private void buyGlobal(int kind){")

# ---------------- MenuScreen: settings UI + hidden 10x G test mode ----------------
path = "core/src/main/java/com/enhort/gnomes/menu/MenuScreen.java"
rep(path,
"import com.enhort.gnomes.GnomesGame;",
"import com.enhort.gnomes.GnomesGame;\nimport com.enhort.gnomes.GameAudio;")
rep(path,
"    private final Box yes=new Box(),no=new Box();\n    private final Box infoCard=new Box();",
"    private final Box yes=new Box(),no=new Box();\n    private final Box infoCard=new Box();\n    private final Box soundToggle=new Box(),vibrationToggle=new Box(),volumeDown=new Box(),volumeUp=new Box(),cheatG=new Box();")
rep(path,
"    private int pendingDelete=-1;",
"    private int pendingDelete=-1;\n    private int gTapCount;\n    private float cheatNotice;")
rep(path,
"        infoCard.set((width-cardW)/2f,cardTop,(width+cardW)/2f,cardBottom);",
"        infoCard.set((width-cardW)/2f,cardTop,(width+cardW)/2f,cardBottom);\n\n        float rowL=infoCard.l+14f*ui,rowR=infoCard.r-14f*ui;\n        soundToggle.set(rowR-112f*ui,infoCard.t+18f*ui,rowR,infoCard.t+54f*ui);\n        vibrationToggle.set(rowR-112f*ui,infoCard.t+64f*ui,rowR,infoCard.t+100f*ui);\n        volumeDown.set(rowR-112f*ui,infoCard.t+110f*ui,rowR-60f*ui,infoCard.t+146f*ui);\n        volumeUp.set(rowR-52f*ui,infoCard.t+110f*ui,rowR,infoCard.t+146f*ui);\n        cheatG.set(width/2f-82f*ui,52f*ui,width/2f-45f*ui,108f*ui);")
rep(path,
"        elapsed+=Math.min(delta,.05f);",
"        elapsed+=Math.min(delta,.05f);if(cheatNotice>0)cheatNotice-=Math.min(delta,.05f);")
rep(path,
"    private void main(Draw d){heading(d,\"DEEP MINE • ALPHA 0.2\");button(d,main[0],\"ИГРАТЬ\",true);button(d,main[1],\"ПРОДОЛЖИТЬ\",game.saves.anySave());button(d,main[2],\"СОХРАНЕНИЯ\",true);button(d,main[3],\"НАСТРОЙКИ\",true);button(d,main[4],\"ОБ ИГРЕ\",true);button(d,main[5],\"ВЫХОД\",true);}",
"    private void main(Draw d){heading(d,\"DEEP MINE • ALPHA 0.2\");button(d,main[0],\"ИГРАТЬ\",true);button(d,main[1],\"ПРОДОЛЖИТЬ\",game.saves.anySave());button(d,main[2],\"СОХРАНЕНИЯ\",true);button(d,main[3],\"НАСТРОЙКИ\",true);button(d,main[4],\"ОБ ИГРЕ\",true);button(d,main[5],\"ВЫХОД\",true);if(game.settings.freeShop||cheatNotice>0){d.align=Draw.Align.CENTER;d.bold=true;d.textSize=8f*ui;d.setColor(game.settings.freeShop?0xFFFFC74A:0xFF9AA4AA);d.text(game.settings.freeShop?\"TEST MODE • ВСЁ БЕСПЛАТНО\":\"TEST MODE ВЫКЛЮЧЕН\",width/2,132f*ui);d.align=Draw.Align.LEFT;d.bold=false;}}")
old_settings = "    private void settings(Draw d){heading(d,\"НАСТРОЙКИ\");d.align=Draw.Align.CENTER;d.textSize=10f*ui;d.setColor(0xFFCED4D8);d.text(\"Визуальные эффекты: ВЫСОКИЕ\",width/2,height*.39f);d.text(\"Вибрация: будет подключена вместе со SFX\",width/2,height*.39f+34f*ui);d.textSize=8f*ui;d.setColor(0xFF869199);d.text(\"Сначала доводим саму шахту, потом прикручиваем звук и тактильную отдачу.\",width/2,height*.39f+72f*ui);d.align=Draw.Align.LEFT;button(d,back,\"НАЗАД\",true);}"
new_settings = "    private void settings(Draw d){\n        heading(d,\"НАСТРОЙКИ\");UiTheme.panel(d,infoCard.l,infoCard.t,infoCard.r,infoCard.b,ui);\n        d.bold=true;d.textSize=9f*ui;d.setColor(0xFFE7E2D7);d.text(\"ЗВУКИ\",infoCard.l+16f*ui,infoCard.t+40f*ui);d.text(\"ВИБРАЦИЯ\",infoCard.l+16f*ui,infoCard.t+86f*ui);d.text(\"ГРОМКОСТЬ\",infoCard.l+16f*ui,infoCard.t+132f*ui);d.bold=false;\n        button(d,soundToggle,game.settings.soundEnabled?\"ВКЛ\":\"ВЫКЛ\",true);button(d,vibrationToggle,game.settings.vibrationEnabled?\"ВКЛ\":\"ВЫКЛ\",true);button(d,volumeDown,\"−\",game.settings.soundVolume>0.01f);button(d,volumeUp,\"+\",game.settings.soundVolume<.99f);\n        d.align=Draw.Align.CENTER;d.bold=true;d.textSize=8.5f*ui;d.setColor(0xFFD4A745);d.text(Math.round(game.settings.soundVolume*100)+\"%\",(volumeDown.r+volumeUp.l)/2f,volumeDown.cy()+3f*ui);d.align=Draw.Align.LEFT;d.bold=false;\n        if(game.settings.freeShop){d.textSize=7.7f*ui;d.setColor(0xFFFFC74A);d.text(\"TEST MODE: покупки бесплатны\",infoCard.l+16f*ui,infoCard.b-14f*ui);}\n        button(d,back,\"НАЗАД\",true);\n    }"
rep(path, old_settings, new_settings)
rep(path,
"    private void tap(float x,float y){\n        if(mode==Mode.MAIN){if(main[0].hit(x,y)||main[2].hit(x,y)){mode=Mode.SLOTS;return;}",
"    private void tap(float x,float y){\n        if(mode==Mode.MAIN){\n            if(cheatG.hit(x,y)){gTapCount++;game.audio.play(GameAudio.Sfx.UI,.28f);if(gTapCount>=10){gTapCount=0;game.settings.toggleFreeShop();game.syncCheats();cheatNotice=2.6f;game.audio.play(GameAudio.Sfx.COIN,.9f);game.audio.vibrate(70);}return;}\n            if(main[0].hit(x,y)||main[2].hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.5f);mode=Mode.SLOTS;return;}")
rep(path,
"        else if(mode==Mode.SETTINGS||mode==Mode.ABOUT){if(back.hit(x,y))mode=Mode.MAIN;}",
"        else if(mode==Mode.SETTINGS){\n            if(soundToggle.hit(x,y)){game.settings.toggleSound();if(game.settings.soundEnabled)game.audio.play(GameAudio.Sfx.UI,.75f);return;}\n            if(vibrationToggle.hit(x,y)){game.settings.toggleVibration();if(game.settings.vibrationEnabled)game.audio.vibrate(45);game.audio.play(GameAudio.Sfx.UI,.5f);return;}\n            if(volumeDown.hit(x,y)){game.settings.setSoundVolume(game.settings.soundVolume-.10f);game.audio.play(GameAudio.Sfx.UI,.6f);return;}\n            if(volumeUp.hit(x,y)){game.settings.setSoundVolume(game.settings.soundVolume+.10f);game.audio.play(GameAudio.Sfx.UI,.6f);return;}\n            if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);mode=Mode.MAIN;}\n        }\n        else if(mode==Mode.ABOUT){if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);mode=Mode.MAIN;}}")

# ---------------- Procedural SFX assets ----------------
SFX = ROOT / "assets" / "sfx"
SFX.mkdir(parents=True, exist_ok=True)
SR = 44100
rnd = random.Random(0x474E4F4D4553)

def write_wav(name, duration, sample_fn):
    n = int(SR * duration)
    samples = []
    for i in range(n):
        t = i / SR
        v = max(-1.0, min(1.0, sample_fn(t, duration, i)))
        samples.append(struct.pack('<h', int(v * 32767)))
    with wave.open(str(SFX / name), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(b''.join(samples))

def env(t, d, power=2.0): return max(0.0, 1.0 - t/d) ** power

def noise(): return rnd.uniform(-1, 1)

write_wav('ui.wav', .09, lambda t,d,i: env(t,d,2.5)*(.32*math.sin(2*math.pi*760*t)+.20*math.sin(2*math.pi*1140*t)))
write_wav('pick.wav', .11, lambda t,d,i: env(t,d,3.2)*(.30*noise()+.38*math.sin(2*math.pi*(1150+500*t/d)*t)))
write_wav('rock_break.wav', .28, lambda t,d,i: env(t,d,1.7)*(.48*noise()+.20*math.sin(2*math.pi*95*t)+.12*math.sin(2*math.pi*180*t)))
write_wav('coin.wav', .22, lambda t,d,i: env(t,d,2.0)*(.30*math.sin(2*math.pi*920*t)+.24*math.sin(2*math.pi*1380*t)+.10*math.sin(2*math.pi*1840*t)))
write_wav('enemy.wav', .20, lambda t,d,i: env(t,d,1.8)*(.24*noise()+.36*math.sin(2*math.pi*(145-45*t/d)*t)))
write_wav('collapse.wav', .58, lambda t,d,i: env(t,d,1.2)*(.56*noise()+.30*math.sin(2*math.pi*58*t)+.18*math.sin(2*math.pi*83*t)))
write_wav('hazard.wav', .36, lambda t,d,i: env(t,d,1.4)*(.22*noise()+.28*math.sin(2*math.pi*(260+360*t/d)*t)))
write_wav('boss.wav', .82, lambda t,d,i: env(t,d,1.0)*(.34*math.sin(2*math.pi*72*t)+.22*math.sin(2*math.pi*108*t)+.10*noise()))

print('interaction pass applied')
