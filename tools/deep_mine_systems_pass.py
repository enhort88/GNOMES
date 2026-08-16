from pathlib import Path
import re


def rep(path, old, new):
    p = Path(path)
    s = p.read_text()
    if old not in s:
        raise SystemExit(f"pattern not found in {path}: {old[:180]!r}")
    p.write_text(s.replace(old, new, 1))


def sub(path, pattern, repl):
    p = Path(path)
    s = p.read_text()
    ns, n = re.subn(pattern, repl, s, count=1, flags=re.S)
    if n != 1:
        raise SystemExit(f"regex not found exactly once in {path}: {pattern[:180]}")
    p.write_text(ns)


MAP = "core/src/main/java/com/enhort/gnomes/game/CaveMap.java"
STATE = "core/src/main/java/com/enhort/gnomes/game/GameState.java"
SAVE = "core/src/main/java/com/enhort/gnomes/save/SaveRepository.java"
GAME = "core/src/main/java/com/enhort/gnomes/GnomesGame.java"
MENU = "core/src/main/java/com/enhort/gnomes/menu/MenuScreen.java"
CAVE = "core/src/main/java/com/enhort/gnomes/game/CaveScreen.java"

# ---------------------------------------------------------------------------
# CaveMap: richer mazes, ring layouts, multiple chest exits and cached routing.
# ---------------------------------------------------------------------------
rep(MAP,
    "import java.util.List;\nimport java.util.Random;",
    "import java.util.List;\nimport java.util.Random;\nimport java.util.LinkedHashMap;\nimport java.util.Map;")

rep(MAP,
    "public final class CaveMap {\n    public static final int N = 1;",
    "public final class CaveMap {\n    public enum Style { BRANCHING, RING }\n\n    public static final int N = 1;")

rep(MAP,
    "    public final long seed;\n\n    private final Random random;\n    private final boolean[] blocked;",
    "    public final long seed;\n    public final Style style;\n\n    private static final int[] DIRS = {N, E, S, W};\n    private final Random random;\n    private final boolean[] blocked;\n    private int revision;\n    private final LinkedHashMap<Long, int[]> pathCache = new LinkedHashMap<>(256, .75f, true) {\n        @Override protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) { return size() > 384; }\n    };")

rep(MAP,
    "        this.seed = seed;\n        this.random = new Random(seed);",
    "        this.seed = seed;\n        this.style = ((seed >>> 5) & 3L) == 0L ? Style.RING : Style.BRANCHING;\n        this.random = new Random(seed);")

sub(MAP,
    r"        // A few loops make the mine feel hand-carved instead of a textbook maze, while keeping navigation readable\..*?\n    \}\n\n    private void connect",
    '''        // More loops are intentional: the chest must not sit behind one compulsory corridor.
        int extra = Math.max(5, cols * rows / (style == Style.RING ? 6 : 10));
        for (int i = 0; i < extra; i++) {
            int c = random.nextInt(cols);
            int r = random.nextInt(rows);
            int dir = DIRS[random.nextInt(DIRS.length)];
            int nc = c + dx(dir), nr = r + dy(dir);
            if (!inside(nc, nr)) continue;
            if ((openings[r][c] & dir) == 0) connect(c, r, nc, nr, dir);
        }
        ensureStartJunction();
        if (style == Style.RING) carveRing();
    }

    private void ensureStartJunction() {
        if (startRow > 0) connect(startCol, startRow, startCol, startRow - 1, N);
        int side = (seed & 1L) == 0L ? E : W;
        int nc = startCol + dx(side);
        if (inside(nc, startRow)) connect(startCol, startRow, nc, startRow, side);
        if ((seed & 4L) != 0L) {
            int other = opposite(side);
            nc = startCol + dx(other);
            if (inside(nc, startRow)) connect(startCol, startRow, nc, startRow, other);
        }
    }

    private void carveRing() {
        int left = 1, right = cols - 2, top = 1, bottom = Math.max(top + 2, rows - 3);
        for (int c = left; c < right; c++) {
            connect(c, top, c + 1, top, E);
            connect(c, bottom, c + 1, bottom, E);
        }
        for (int r = top; r < bottom; r++) {
            connect(left, r, left, r + 1, S);
            connect(right, r, right, r + 1, S);
        }
        // Two spokes make the ring useful rather than decorative.
        int mid = Math.max(left + 1, Math.min(right - 1, startCol));
        for (int r = startRow; r > bottom; r--) connect(mid, r, mid, r - 1, N);
        if (mid > left) connect(mid, bottom, mid - 1, bottom, W);
        if (mid < right) connect(mid, bottom, mid + 1, bottom, E);

        if (cols >= 9 && rows >= 11) {
            int il = 3, ir = cols - 4, it = 3, ib = Math.max(it + 2, rows - 5);
            if (il < ir && it < ib) {
                for (int c = il; c < ir; c++) { connect(c, it, c + 1, it, E); connect(c, ib, c + 1, ib, E); }
                for (int r = it; r < ib; r++) { connect(il, r, il, r + 1, S); connect(ir, r, ir, r + 1, S); }
                connect(il, (it + ib) / 2, il - 1, (it + ib) / 2, W);
                connect(ir, (it + ib) / 2, ir + 1, (it + ib) / 2, E);
            }
        }
    }

    private void connect''')

rep(MAP,
    "    public boolean blockCell(int cell) {\n        if (cell < 0 || cell >= blocked.length || cell == index(startCol, startRow)) return false;\n        blocked[cell] = true;\n        return true;\n    }\n\n    public void unblockCell(int cell) {\n        if (cell >= 0 && cell < blocked.length) blocked[cell] = false;\n    }",
    '''    public boolean blockCell(int cell) {
        if (cell < 0 || cell >= blocked.length || cell == index(startCol, startRow) || blocked[cell]) return false;
        blocked[cell] = true;
        revision++;
        pathCache.clear();
        return true;
    }

    public void unblockCell(int cell) {
        if (cell >= 0 && cell < blocked.length && blocked[cell]) {
            blocked[cell] = false;
            revision++;
            pathCache.clear();
        }
    }''')

sub(MAP,
    r"    /\*\* Returns a list containing start and goal\. Empty only for invalid cells\. \*/\n    public int\[\] path\(int start, int goal\) \{.*?\n    \}\n\n    private static int\[\] reconstruct",
    '''    /** Normal worker path: rubble is solid. */
    public int[] path(int start, int goal) { return pathInternal(start, goal, false, false); }

    /** Imps use this: a cave-in never blocks their route to the chest. */
    public int[] pathIgnoringBlocks(int start, int goal) { return pathInternal(start, goal, true, false); }

    /** Workers clearing a cave-in may enter the blocked goal cell, but cannot cross other rubble. */
    public int[] pathToBlockedGoal(int start, int goal) { return pathInternal(start, goal, false, true); }

    private int[] pathInternal(int start, int goal, boolean ignoreBlocks, boolean allowBlockedGoal) {
        int count = cols * rows;
        if (start < 0 || start >= count || goal < 0 || goal >= count) return new int[0];
        if (!ignoreBlocks && isBlocked(goal) && !allowBlockedGoal && goal != start) return new int[0];
        if (start == goal) return new int[] { start };

        long key = (((long) revision) << 32) ^ (((long) start) << 16) ^ goal
                ^ (ignoreBlocks ? (1L << 62) : 0L) ^ (allowBlockedGoal ? (1L << 61) : 0L);
        int[] cached = pathCache.get(key);
        if (cached != null) return cached;

        int[] parent = new int[count];
        java.util.Arrays.fill(parent, -2);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        parent[start] = -1;
        q.add(start);
        while (!q.isEmpty()) {
            int cur = q.removeFirst();
            int c = col(cur), r = row(cur);
            int bits = openings[r][c];
            for (int dir : DIRS) {
                if ((bits & dir) == 0) continue;
                int nc = c + dx(dir), nr = r + dy(dir);
                if (!inside(nc, nr)) continue;
                int next = index(nc, nr);
                if (!ignoreBlocks && next != start && isBlocked(next) && !(allowBlockedGoal && next == goal)) continue;
                if (parent[next] != -2) continue;
                parent[next] = cur;
                if (next == goal) {
                    int[] result = reconstruct(parent, goal);
                    pathCache.put(key, result);
                    return result;
                }
                q.addLast(next);
            }
        }
        int[] empty = new int[0];
        pathCache.put(key, empty);
        return empty;
    }

    private static int[] reconstruct''')

# ---------------------------------------------------------------------------
# GameState: four difficulty modes, roguelite level capital, one starter gnome.
# ---------------------------------------------------------------------------
rep(STATE,
    "    public long stolenValue = 0;\n",
    "    public long stolenValue = 0;\n    public int difficulty = 2;\n    public long levelEarnedValue = 0;\n    public long levelInvestedValue = 0;\n")
rep(STATE, "        tierCounts[0] = 3;", "        tierCounts[0] = 1;")

rep(STATE,
    "    public double yieldFor(RockType type, int tier) {",
    '''    public void setDifficulty(int difficulty) { this.difficulty = Math.max(1, Math.min(4, difficulty)); }

    public double carryRatio() {
        return switch (difficulty) { case 1 -> .5; case 2 -> 1.0 / 3.0; case 3 -> .25; default -> 0.0; };
    }

    public String difficultyTitle() {
        return switch (difficulty) { case 1 -> "ЛЁГКАЯ"; case 2 -> "СРЕДНЯЯ"; case 3 -> "СЛОЖНАЯ"; default -> "БЕЗ ПЕРЕНОСА"; };
    }

    public long walletValue() { return stone + silver * 8L + gold * 20L + diamond * 100L; }

    public static long materialValue(RockType.Material material, long amount) {
        long weight = switch (material) { case STONE -> 1L; case SILVER -> 8L; case GOLD -> 20L; case DIAMOND -> 100L; };
        return Math.max(0L, amount) * weight;
    }

    public long transferCapital(long cargoValue) {
        return Math.max(0L, walletValue() + levelInvestedValue + Math.max(0L, cargoValue));
    }

    public long transferAmount(long cargoValue) {
        return (long) Math.floor(transferCapital(cargoValue) * carryRatio());
    }

    public void beginNextDepth(long transferredValue) {
        depth++;
        depthProgress = 0;
        stone = Math.max(0L, transferredValue);
        silver = gold = diamond = 0;
        java.util.Arrays.fill(tierCounts, 0);
        tierCounts[0] = 1;
        java.util.Arrays.fill(tierLevels, 1);
        miningUpgrade = speedUpgrade = combatUpgrade = 0;
        guardianLevel = 0;
        levelEarnedValue = 0;
        levelInvestedValue = 0;
    }

    public boolean canBuyMiner() {
        return tierCounts[0] < 99 && (FREE_SHOP || stone >= minerBuyCost());
    }

    public double yieldFor(RockType type, int tier) {''')

rep(STATE,
    "        switch (material) {\n            case STONE -> stone += whole;\n            case SILVER -> silver += whole;\n            case GOLD -> gold += whole;\n            case DIAMOND -> diamond += whole;\n        }\n        return whole;",
    "        switch (material) {\n            case STONE -> stone += whole;\n            case SILVER -> silver += whole;\n            case GOLD -> gold += whole;\n            case DIAMOND -> diamond += whole;\n        }\n        levelEarnedValue += materialValue(material, whole);\n        return whole;")

sub(STATE,
    r"    public boolean buyOrUpgradeGuardian\(\) \{.*?\n    \}\n\n    public String guardianCostLabel",
    '''    public boolean buyOrUpgradeGuardian() {
        long cost = guardianCost();
        if (!FREE_SHOP) {
            if (guardianLevel < 3) {
                if (stone < cost) return false;
                stone -= cost;
                levelInvestedValue += cost;
            } else {
                long silverCost = Math.max(4, cost / 120);
                if (silver < silverCost) return false;
                silver -= silverCost;
                levelInvestedValue += silverCost * 8L;
            }
        }
        guardianLevel++;
        return true;
    }

    public String guardianCostLabel''')

sub(STATE,
    r"    public boolean buyMiner\(\) \{.*?\n    \}\n\n    public boolean mergeTier",
    '''    public boolean buyMiner() {
        long cost = minerBuyCost();
        if (tierCounts[0] >= 99) return false;
        if (!FREE_SHOP) {
            if (stone < cost) return false;
            stone -= cost;
            levelInvestedValue += cost;
        }
        tierCounts[0]++;
        return true;
    }

    public boolean mergeTier''')

sub(STATE,
    r"    public boolean upgradeTier\(int tier\) \{.*?\n    \}\n\n    public long globalUpgradeCost",
    '''    public boolean upgradeTier(int tier) {
        if (tier < 0 || tier >= tierLevels.length) return false;
        long cost = tierUpgradeCost(tier);
        if (!FREE_SHOP) {
            if (tier < 2) {
                if (stone < cost) return false;
                stone -= cost;
                levelInvestedValue += cost;
            } else if (tier < 4) {
                long paid = Math.max(1, cost / 90);
                if (silver < paid) return false;
                silver -= paid;
                levelInvestedValue += paid * 8L;
            } else {
                long paid = Math.max(1, cost / 180);
                if (gold < paid) return false;
                gold -= paid;
                levelInvestedValue += paid * 20L;
            }
        }
        tierLevels[tier]++;
        return true;
    }

    public long globalUpgradeCost''')

sub(STATE,
    r"    public boolean buyGlobalUpgrade\(int kind\) \{.*?\n    \}\n\n    public int artifactCost",
    '''    public boolean buyGlobalUpgrade(int kind) {
        if (FREE_SHOP) {
            if (kind == 0) miningUpgrade++;
            else if (kind == 1) speedUpgrade++;
            else combatUpgrade++;
            return true;
        }
        long cost = globalUpgradeCost(kind);
        if (kind == 0) {
            if (stone < cost) return false;
            stone -= cost;
            levelInvestedValue += cost;
            miningUpgrade++;
            return true;
        }
        if (kind == 1) {
            long paid = Math.max(2, cost / 100);
            if (silver < paid) return false;
            silver -= paid;
            levelInvestedValue += paid * 8L;
            speedUpgrade++;
            return true;
        }
        long paid = Math.max(1, cost / 180);
        if (gold < paid) return false;
        gold -= paid;
        levelInvestedValue += paid * 20L;
        combatUpgrade++;
        return true;
    }

    public int artifactCost''')

# ---------------------------------------------------------------------------
# Save repository: persist difficulty and current-level accounting.
# ---------------------------------------------------------------------------
rep(SAVE,
    "    public GameState fresh(int slot) {\n        return new GameState();\n    }",
    '''    public GameState fresh(int slot) { return fresh(slot, 2); }

    public GameState fresh(int slot, int difficulty) {
        GameState state = new GameState();
        state.setDifficulty(difficulty);
        return state;
    }''')

rep(SAVE,
    "        public long stolenValue;\n        public int[] tierCounts;",
    "        public long stolenValue;\n        public int difficulty;\n        public long levelEarnedValue;\n        public long levelInvestedValue;\n        public int[] tierCounts;")

rep(SAVE,
    "            s.stolenValue = st.stolenValue;\n            s.tierCounts = st.tierCounts.clone();",
    "            s.stolenValue = st.stolenValue;\n            s.difficulty = st.difficulty;\n            s.levelEarnedValue = st.levelEarnedValue;\n            s.levelInvestedValue = st.levelInvestedValue;\n            s.tierCounts = st.tierCounts.clone();")

rep(SAVE,
    "            st.stolenValue = Math.max(0, stolenValue);\n            copy(tierCounts, st.tierCounts);",
    "            st.stolenValue = Math.max(0, stolenValue);\n            st.setDifficulty(difficulty >= 1 && difficulty <= 4 ? difficulty : 2);\n            st.levelEarnedValue = Math.max(0L, levelEarnedValue);\n            st.levelInvestedValue = Math.max(0L, levelInvestedValue);\n            copy(tierCounts, st.tierCounts);")

# ---------------------------------------------------------------------------
# Game shell and menu difficulty picker.
# ---------------------------------------------------------------------------
rep(GAME,
    "    public void playNewSlot(int slot) {\n        saves.save(slot, saves.fresh(slot));\n        playSlot(slot);\n    }",
    '''    public void playNewSlot(int slot) { playNewSlot(slot, 2); }
    public void playNewSlot(int slot, int difficulty) {
        saves.save(slot, saves.fresh(slot, difficulty));
        playSlot(slot);
    }''')

rep(MENU,
    "    private enum Mode { MAIN, SLOTS, SETTINGS, ABOUT, DELETE }",
    "    private enum Mode { MAIN, SLOTS, DIFFICULTY, SETTINGS, ABOUT, DELETE }")
rep(MENU,
    "    private final Box[] slotDelete=new Box[SaveRepository.SLOT_COUNT];\n    private final Box back=new Box();",
    "    private final Box[] slotDelete=new Box[SaveRepository.SLOT_COUNT];\n    private final Box[] difficultyButtons={new Box(),new Box(),new Box(),new Box()};\n    private final Box back=new Box();")
rep(MENU,
    "    private int pendingDelete=-1;\n    private int gTapCount;",
    "    private int pendingDelete=-1;\n    private int pendingNewSlot=-1;\n    private int gTapCount;")

rep(MENU,
    "        float confirmW=Math.min(width-42f*ui,330f*ui),confirmX=(width-confirmW)/2f;",
    '''        float diffW=Math.min(width-side*2,330f*ui),diffX=(width-diffW)/2f;
        float diffTop=Math.min(height*.25f,160f*ui),diffBottom=back.t-12f*ui,diffGap=9f*ui;
        float diffH=Math.max(42f*ui,Math.min(58f*ui,(diffBottom-diffTop-diffGap*3)/4f));
        float diffTotal=diffH*4+diffGap*3,diffY=diffTop+Math.max(0,(diffBottom-diffTop-diffTotal)*.5f);
        for(int i=0;i<4;i++)difficultyButtons[i].set(diffX,diffY+i*(diffH+diffGap),diffX+diffW,diffY+i*(diffH+diffGap)+diffH);

        float confirmW=Math.min(width-42f*ui,330f*ui),confirmX=(width-confirmW)/2f;''')

rep(MENU,
    "        switch(mode){case MAIN->main(d);case SLOTS->slots(d);case SETTINGS->settings(d);case ABOUT->about(d);case DELETE->delete(d);}",
    "        switch(mode){case MAIN->main(d);case SLOTS->slots(d);case DIFFICULTY->difficulty(d);case SETTINGS->settings(d);case ABOUT->about(d);case DELETE->delete(d);}")

rep(MENU,
    "    private void settings(Draw d){",
    '''    private void difficulty(Draw d){
        heading(d,"СЛОЖНОСТЬ ЭКСПЕДИЦИИ");
        d.align=Draw.Align.CENTER;d.textSize=7.8f*ui;d.setColor(0xFF9EAAAF);
        d.text("После каждого уровня гномы и обычные апгрейды продаются.",width/2,128f*ui);
        d.align=Draw.Align.LEFT;
        button(d,difficultyButtons[0],"ЛЁГКАЯ  •  перенос 1/2",true,UiTheme.GREEN,false,.82f);
        button(d,difficultyButtons[1],"СРЕДНЯЯ  •  перенос 1/3",true,UiTheme.GOLD,false,.82f);
        button(d,difficultyButtons[2],"СЛОЖНАЯ  •  перенос 1/4",true,UiTheme.COPPER,false,.82f);
        button(d,difficultyButtons[3],"БЕЗ ПЕРЕНОСА  •  0",true,UiTheme.RED,false,.82f);
        button(d,back,"НАЗАД",true,UiTheme.STEEL,false,.92f);
    }

    private void settings(Draw d){''')

rep(MENU,
    "                d.setColor(0xFFB8C1C7);d.text(\"глуб. \"+Math.max(1,s.depth)+\"  •  кам \"+fmt(s.stone)+\"  •  ◆ \"+fmt(s.diamond),b.l+14f*ui,b.t+b.h()*.72f);",
    "                d.setColor(0xFFB8C1C7);d.text(\"глуб. \"+Math.max(1,s.depth)+\"  •  \"+difficultyShort(s.difficulty)+\"  •  кам \"+fmt(s.stone)+\"  •  ◆ \"+fmt(s.diamond),b.l+14f*ui,b.t+b.h()*.72f);")

rep(MENU,
    "                    if(game.saves.exists(slot))game.playSlot(slot);else game.playNewSlot(slot);\n                    return;",
    "                    if(game.saves.exists(slot))game.playSlot(slot);else{pendingNewSlot=slot;mode=Mode.DIFFICULTY;}\n                    return;")

rep(MENU,
    "            if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);mode=Mode.MAIN;}\n        }else if(mode==Mode.SETTINGS){",
    '''            if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);mode=Mode.MAIN;}
        }else if(mode==Mode.DIFFICULTY){
            for(int i=0;i<difficultyButtons.length;i++)if(difficultyButtons[i].hit(x,y)){
                game.audio.play(GameAudio.Sfx.UI,.6f);int slot=pendingNewSlot;pendingNewSlot=-1;game.playNewSlot(slot,i+1);return;
            }
            if(back.hit(x,y)){game.audio.play(GameAudio.Sfx.UI,.4f);pendingNewSlot=-1;mode=Mode.SLOTS;}
        }else if(mode==Mode.SETTINGS){''')

rep(MENU,
    "    private static String fmt(long n){",
    '''    private static String difficultyShort(int d){return switch(d){case 1->"лёгк.";case 3->"сложн.";case 4->"без перен.";default->"средн.";};}

    private static String fmt(long n){''')

# ---------------------------------------------------------------------------
# CaveScreen: persistent cave-ins, imp routing, level summary/game over,
# long-press priority clear and accounting reset between depths.
# ---------------------------------------------------------------------------
rep(CAVE,
    "    private enum PriorityKind { NONE, VEIN, MOB, POINT }",
    "    private enum PriorityKind { NONE, VEIN, MOB, HAZARD, POINT }")
rep(CAVE,
    "        float phase, walkCycle, swing, attackCooldown, stun, routeRetry;",
    "        float phase, walkCycle, swing, attackCooldown, stun, routeRetry, spawn=.52f;")
rep(CAVE,
    "        float hp, hitFlash, death;",
    "        float hp, hitFlash, death, spawn=.38f;")
rep(CAVE,
    "        float x,y,hp,maxHp,phase,walkCycle,attack,attackCooldown,summonCooldown=5f,routeTimer;",
    "        float x,y,hp,maxHp,phase,walkCycle,attack,attackCooldown,summonCooldown=5f,routeTimer,spawn=.65f;")
rep(CAVE,
    "        float age; boolean fired, obstacleActive;\n        CaveHazard(HazardType type,int cell,float x,float y,float r){this.type=type;this.cell=cell;this.x=x;this.y=y;this.r=r;}",
    "        float age,rubbleHp,rubbleMaxHp; boolean fired,obstacleActive,cleared;\n        CaveHazard(HazardType type,int cell,float x,float y,float r){this.type=type;this.cell=cell;this.x=x;this.y=y;this.r=r;}")

rep(CAVE,
    "    private Mob priorityMob;\n    private int priorityCell=-1;\n    private float priorityX,priorityY,priorityPulse;\n    private boolean objectiveReminderShown;",
    '''    private Mob priorityMob;
    private CaveHazard priorityHazard;
    private int priorityCell=-1;
    private float priorityX,priorityY,priorityPulse;
    private boolean objectiveReminderShown;
    private boolean worldTouchActive,longPressEligible,longPressHandled;
    private float worldTouchStarted,worldTouchX,worldTouchY;
    private boolean levelSummary,gameOver;
    private float summaryAnim,guardianAttackAnim,guardianSpawnAnim;
    private long summaryEarned,summaryInvested,summaryWallet,summaryCapital,summaryTransfer;
    private final Box summaryOk=new Box(),gameOverOk=new Box();''')

sub(CAVE,
    r"    @Override public void show\(\)\{.*?\n    \}\n\n    @Override public void resize",
    '''    @Override public void show(){
        Gdx.input.setCatchKey(Input.Keys.BACK,true);
        Gdx.input.setInputProcessor(new InputAdapter(){
            @Override public boolean keyDown(int keycode){if(keycode==Input.Keys.BACK||keycode==Input.Keys.ESCAPE){saveNow();game.openMenu();return true;}return false;}
            @Override public boolean touchDown(int sx,int sy,int pointer,int button){
                float x=sx,y=sy;
                if(levelSummary){if(summaryOk.hit(x,y)&&summaryAnim>.75f)finishLevelTransition();return true;}
                if(gameOver){if(gameOverOk.hit(x,y)){saveNow();game.openMenu();}return true;}
                if(speed.hit(x,y)){speedHeld=true;game.audio.play(GameAudio.Sfx.UI,.55f);return true;}
                if(y>=worldT&&y<=worldB){
                    worldTouchActive=true;longPressEligible=true;longPressHandled=false;worldTouchStarted=elapsed;worldTouchX=x;worldTouchY=y;
                    return handleWorldTap(x,y);
                }
                return handleTap(x,y);
            }
            @Override public boolean touchUp(int sx,int sy,int pointer,int button){speedHeld=false;worldTouchActive=false;longPressEligible=false;return true;}
            @Override public boolean touchDragged(int sx,int sy,int pointer){
                if(!speed.hit(sx,sy))speedHeld=false;
                if(worldTouchActive&&distance(worldTouchX,worldTouchY,sx,sy)>16f*ui)longPressEligible=false;
                return true;
            }
        });
    }

    @Override public void resize''')

rep(CAVE,
    "        quaternary.set(mid+colGap,row2,width-side,actionsBottom);\n    }",
    '''        quaternary.set(mid+colGap,row2,width-side,actionsBottom);
        float ow=Math.min(width-54f*ui,330f*ui),ox=(width-ow)/2f;
        summaryOk.set(ox,height*.69f,ox+ow,height*.69f+48f*ui);
        gameOverOk.set(ox,height*.66f,ox+ow,height*.66f+48f*ui);
    }''')

rep(CAVE,
    "        mobs.clear();hazards.clear();fx.clear();veins.clear();clearPriority(false);objectiveReminderShown=false;",
    "        mobs.clear();hazards.clear();fx.clear();veins.clear();clearPriority(false);objectiveReminderShown=false;levelSummary=false;gameOver=false;summaryAnim=0;")
rep(CAVE,
    "        toast=\"ГЛУБИНА \"+state.depth;toastTime=2.4f;",
    "        toast=\"ГЛУБИНА \"+state.depth+(map.style==CaveMap.Style.RING?\" • КОЛЬЦЕВАЯ ШАХТА\":\"\");toastTime=2.4f;")

sub(CAVE,
    r"    private void buildVeins\(\)\{.*?\n    \}\n\n    private RockType chooseRockType",
    '''    private void buildVeins(){
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
            float radius=Math.min(cellW,cellH)*(.19f+random.nextFloat()*.075f);
            float hp=type.hp*(1f+Math.max(0,state.depth-1)*.055f);
            Vein v=new Vein(type,cell,side,random.nextInt(),x,y,radius,hp);v.spawn=.20f+random.nextFloat()*.38f;veins.add(v);
        }
    }

    private RockType chooseRockType''')

rep(CAVE,
    "            GnomeTier tier=GnomeTier.values()[ti];int want=Math.min(48,state.tierCounts[ti]),have=0;",
    "            GnomeTier tier=GnomeTier.values()[ti];int want=state.tierCounts[ti],have=0;")

rep(CAVE,
    "        drawHud(d);drawPanel(d);drawToast(d);d.endFrame();",
    "        drawHud(d);drawPanel(d);drawToast(d);if(levelSummary)drawLevelSummary(d);if(gameOver)drawGameOver(d);d.endFrame();")

sub(CAVE,
    r"    private void update\(float dt,float workerTimeScale\)\{.*?\n    \}\n\n    private void updateVeins",
    '''    private void update(float dt,float workerTimeScale){
        if(map==null)return;
        if(toastTime>0)toastTime-=dt;
        priorityPulse+=dt;
        guardianAttackAnim=Math.max(0,guardianAttackAnim-dt);
        guardianSpawnAnim=Math.max(0,guardianSpawnAnim-dt);
        if(screenShake>0)screenShake=Math.max(0,screenShake-dt*18f*ui);
        checkLongPress();
        if(levelSummary){summaryAnim=Math.min(3f,summaryAnim+dt);updateFx(dt);return;}
        if(gameOver){updateFx(dt);return;}

        updateVeins(dt);
        updateGuardian(dt);
        updateMobs(dt);
        updateHazards(dt);
        updateWorkers(dt*workerTimeScale);
        updateFx(dt);

        enemyTimer-=dt;hazardTimer-=dt;saveTimer+=dt;
        if(enemyTimer<=0){spawnEnemyWave();enemyTimer=Math.max(8f,24f-state.depth*.25f)+random.nextFloat()*10f;}
        if(hazardTimer<=0){spawnHazard();hazardTimer=20f+random.nextFloat()*18f;}
        if(saveTimer>=4f){saveNow();saveTimer=0;}

        if(state.totalGnomes()==0&&!state.canBuyMiner()){beginGameOver();return;}
        boolean any=false;for(Vein v:veins)if(!v.dead){any=true;break;}
        if(!any){
            if(levelObjectiveMet())beginLevelSummary();
            else if(!objectiveReminderShown){objectiveReminderShown=true;toast=levelObjectiveToast();toastTime=2.4f;}
        }
    }

    private void checkLongPress(){
        if(worldTouchActive&&longPressEligible&&!longPressHandled&&elapsed-worldTouchStarted>=.58f){
            longPressHandled=true;clearPriority(true);resetWorkerRoutes();game.audio.vibrate(24);
        }
    }

    private void updateVeins''')

rep(CAVE,
    "            v.hitFlash=Math.max(0,v.hitFlash-dt);",
    "            v.hitFlash=Math.max(0,v.hitFlash-dt);v.spawn=Math.max(0,v.spawn-dt);")

sub(CAVE,
    r"    private void updateGuardian\(float dt\)\{.*?\n    \}\n\n    private void updateWorkers",
    '''    private void updateGuardian(float dt){
        if(state.guardianLevel<=0)return;
        guardianCooldown-=dt;if(guardianCooldown>0)return;
        float gx=cx(map.startCol),gy=cy(map.startRow),range=Math.min(cellW,cellH)*2.8f*state.guardianRangeMultiplier();
        Mob best=null;float bd=Float.MAX_VALUE;
        // The paid guard earns his wage by looking for thieves first.
        for(int pass=0;pass<2&&best==null;pass++)for(Mob m:mobs){
            if(m.dead)continue;boolean thief=m.type==EnemyType.IMP||m.type==EnemyType.IMP_KING;if((pass==0)!=thief)continue;
            float q=dist2(gx,gy,m.x,m.y);if(q<range*range&&q<bd){bd=q;best=m;}
        }
        if(best!=null){guardianCooldown=state.guardianAttackInterval();guardianAttackAnim=.34f;best.hp-=state.guardianDamage();best.attack=.16f;spawnSparks(best.x,best.y,0xFFFFD873,Math.min(7,workers.size()>80?3:7));}
    }

    private void updateWorkers''')

sub(CAVE,
    r"    private void updateWorkers\(float dt\)\{.*?\n    \}\n\n    private int guardianDefenderQuota",
    '''    private void updateWorkers(float dt){
        int defendersLeft=state.guardianLevel>0?guardianDefenderQuota():workers.size();
        CaveHazard cleanup=firstActiveCollapse();
        int cleanupLeft=cleanup==null?0:Math.max(1,Math.min(8,(workers.size()+17)/18));
        for(int i=workers.size()-1;i>=0;i--){
            Worker w=workers.get(i);
            w.spawn=Math.max(0,w.spawn-dt);w.attackCooldown-=dt;if(w.swing>0)w.swing=Math.max(0,w.swing-dt);if(w.stun>0)w.stun-=dt;if(w.routeRetry>0)w.routeRetry-=dt;
            if(w.spawn>0){w.action=WorkerAction.IDLE;w.vx=w.vy=0;continue;}
            if(w.stun>0){w.action=WorkerAction.STUNNED;w.vx=w.vy=0;continue;}

            if(priorityKind==PriorityKind.VEIN&&priorityVein!=null&&!priorityVein.dead){w.mob=null;w.vein=priorityVein;mine(w,priorityVein,dt);continue;}
            if(priorityKind==PriorityKind.MOB&&priorityMob!=null&&!priorityMob.dead){w.vein=null;w.mob=priorityMob;fight(w,priorityMob,dt);continue;}
            if(priorityKind==PriorityKind.HAZARD&&priorityHazard!=null&&!priorityHazard.cleared&&priorityHazard.obstacleActive){w.vein=null;w.mob=null;clearCollapse(w,priorityHazard,dt);continue;}
            if(priorityKind==PriorityKind.POINT&&priorityCell>=0){w.vein=null;w.mob=null;moveToPriorityPoint(w,dt);continue;}

            Mob enemy=null;if(defendersLeft>0)enemy=nearestMob(w.x,w.y);
            if(enemy!=null){defendersLeft--;w.mob=enemy;w.vein=null;fight(w,enemy,dt);continue;}

            w.mob=null;
            float cap=w.tier.cargoCapacity*state.carryMultiplier(w.tier.ordinal());
            if(w.hasCargo()&&w.cargo()>=cap*.92){carryHome(w,dt);continue;}
            if(cleanupLeft>0&&cleanup!=null&&!cleanup.cleared){cleanupLeft--;w.vein=null;clearCollapse(w,cleanup,dt);continue;}
            if(w.vein==null||w.vein.dead||map.isBlocked(w.vein.cell))w.vein=chooseVein(w);
            if(w.vein!=null)mine(w,w.vein,dt); else if(w.hasCargo())carryHome(w,dt); else {w.action=WorkerAction.IDLE;w.vx=w.vy=0;}
        }
    }

    private CaveHazard firstActiveCollapse(){for(CaveHazard h:hazards)if(h.type==HazardType.COLLAPSE&&h.obstacleActive&&!h.cleared)return h;return null;}

    private void clearCollapse(Worker w,CaveHazard h,float dt){
        if(!atCell(w,h.cell)){
            w.action=WorkerAction.WALK;
            if(w.goalCell!=h.cell||w.path.length==0){w.goalCell=h.cell;w.path=map.pathToBlockedGoal(cellFor(w.x,w.y),h.cell);w.pathIndex=Math.min(1,w.path.length);}
            followWorker(w,moveSpeed(w)*.82f,dt);return;
        }
        w.action=WorkerAction.MINE;w.vx=w.vy=0;
        if(w.attackCooldown<=0&&w.swing<=0){w.swing=.58f;w.hitApplied=false;w.attackCooldown=.62f;}
        float p=w.swing<=0?1f:1f-w.swing/.58f;
        if(w.swing>0&&!w.hitApplied&&p>=.57f){
            w.hitApplied=true;float damage=Math.max(1f,w.tier.miningPower*state.tierPowerMultiplier(w.tier.ordinal())*state.miningMultiplier(w.tier.ordinal())*.55f);
            h.rubbleHp-=damage;spawnSparks(h.x,h.y,0xFF9B8A78,workers.size()>80?1:3);
            if(h.rubbleHp<=0){h.rubbleHp=0;h.cleared=true;h.obstacleActive=false;map.unblockCell(h.cell);invalidateRoutes();if(priorityHazard==h)clearPriority(false);toast="ОБВАЛ РАЗОБРАН";toastTime=1.5f;game.audio.play(GameAudio.Sfx.ROCK_BREAK,.75f);}
        }
    }

    private int guardianDefenderQuota''')

# Thieves route through rubble. Spawn animation prevents "pop-in" movement.
rep(CAVE,
    "            if(m.dead)continue;m.attack=Math.max(0,m.attack-dt);m.attackCooldown-=dt;m.summonCooldown-=dt;m.routeTimer-=dt;",
    "            if(m.dead)continue;m.spawn=Math.max(0,m.spawn-dt);m.attack=Math.max(0,m.attack-dt);m.attackCooldown-=dt;m.summonCooldown-=dt;m.routeTimer-=dt;if(m.spawn>0)continue;")
rep(CAVE,
    "            if(m.routeTimer<=0||m.goalCell!=goal){m.goalCell=goal;m.path=map.path(cellFor(m.x,m.y),goal);m.pathIndex=Math.min(1,m.path.length);m.routeTimer=.55f;}",
    "            if(m.routeTimer<=0||m.goalCell!=goal){m.goalCell=goal;m.path=thief?map.pathIgnoringBlocks(cellFor(m.x,m.y),goal):map.path(cellFor(m.x,m.y),goal);m.pathIndex=Math.min(1,m.path.length);m.routeTimer=.55f;}")

# Persistent collapses, lethal lava, and traps can kill ordinary mobs.
sub(CAVE,
    r"    private void updateHazards\(float dt\)\{.*?\n    private void invalidateRoutes",
    '''    private void updateHazards(float dt){
        for(Iterator<CaveHazard>it=hazards.iterator();it.hasNext();){
            CaveHazard h=it.next();h.age+=dt;
            if(!h.fired&&h.age>=1.25f){h.fired=true;fireHazard(h);}
            if(h.fired&&h.type==HazardType.LAVA)applyLava(h,dt);
            if(h.type==HazardType.COLLAPSE){if(h.cleared)it.remove();continue;}
            if(h.age>hazardDuration(h.type))it.remove();
        }
    }
    private float hazardDuration(HazardType t){return switch(t){case COLLAPSE->Float.MAX_VALUE;case PIT->8f;case LAVA->9f;case FLOOD->4.8f;};}
    private void spawnHazard(){
        int start=map.index(map.startCol,map.startRow),cell=start;
        for(int tries=0;tries<12&&cell==start;tries++)cell=random.nextInt(map.cols*map.rows);
        if(cell==start)return;
        HazardType type=HazardType.values()[random.nextInt(HazardType.values().length)];float r=Math.min(cellW,cellH)*(type==HazardType.FLOOD?1.2f:.62f);
        CaveHazard h=new CaveHazard(type,cell,cx(map.col(cell)),cy(map.row(cell)),r);
        if(type==HazardType.COLLAPSE){h.rubbleMaxHp=110f*(1f+state.depth*.10f);h.rubbleHp=h.rubbleMaxHp;}
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
                case PIT -> {if(random.nextFloat()<.34f*(1-survive))loseWorker(w,"провалился в яму");else w.stun=Math.max(w.stun,1.2f);}
                case COLLAPSE -> {if(random.nextFloat()<.28f*(1-survive))loseWorker(w,"погиб под обвалом");else w.stun=Math.max(w.stun,1.1f);}
            }
        }
        hitMobsWithHazard(h);
        spawnSparks(h.x,h.y,h.type==HazardType.FLOOD?0xFF70C9F4:h.type==HazardType.LAVA?0xFFFF8A28:0xFF918172,10);
    }

    private void applyLava(CaveHazard h,float dt){
        float rr=h.r*.72f;
        for(int i=workers.size()-1;i>=0;i--){Worker w=workers.get(i);if(distance(w.x,w.y,h.x,h.y)<rr)loseWorker(w,"наступил в лаву");}
        for(Mob m:mobs){
            if(m.dead||distance(m.x,m.y,h.x,h.y)>=rr)continue;
            if(m.type==EnemyType.FIRE_GOLEM)continue;
            if(m.type==EnemyType.IMP||m.type==EnemyType.DEMON)m.hp=0;
            else m.hp-=m.maxHp*.18f*dt;
        }
    }

    private void hitMobsWithHazard(CaveHazard h){
        for(Mob m:mobs){
            if(m.dead||distance(m.x,m.y,h.x,h.y)>h.r)continue;
            boolean small=m.type==EnemyType.IMP||m.type==EnemyType.DEMON;
            switch(h.type){
                case LAVA -> {if(m.type!=EnemyType.FIRE_GOLEM){if(small)m.hp=0;else m.hp-=m.maxHp*.18f;}}
                case PIT -> {if(small&&random.nextFloat()<.48f)m.hp=0;else m.hp-=m.maxHp*.08f;}
                case COLLAPSE -> {if(small&&random.nextFloat()<.34f)m.hp=0;else m.hp-=m.maxHp*.12f;}
                case FLOOD -> {if(m.type==EnemyType.FIRE_GOLEM)m.hp-=m.maxHp*.22f;else if(small)m.hp-=m.maxHp*.08f;}
            }
        }
    }

    private void invalidateRoutes''')

# World priority now includes rubble; only empty-space holds may clear everything.
sub(CAVE,
    r"    private boolean handleWorldTap\(float x,float y\)\{.*?\n    \}\n\n    private void resetWorkerRoutes",
    '''    private boolean handleWorldTap(float x,float y){
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

    private void resetWorkerRoutes''')

rep(CAVE,
    "    private void clearPriority(boolean notify){priorityKind=PriorityKind.NONE;priorityVein=null;priorityMob=null;priorityCell=-1;if(notify){toast=\"ПРИОРИТЕТ СНЯТ\";toastTime=1f;}}",
    "    private void clearPriority(boolean notify){priorityKind=PriorityKind.NONE;priorityVein=null;priorityMob=null;priorityHazard=null;priorityCell=-1;if(notify){toast=\"ПРИОРИТЕТ СНЯТ\";toastTime=1f;}}")

# Buying the guard gets a visible arrival animation; the fake Cargo button is removed later in visual pass.
rep(CAVE,
    "case UPGRADES->{if(state.buyOrUpgradeGuardian())toast=\"СТРАЖ СУНДУКА • ур.\"+state.guardianLevel;else toast=\"НЕ ХВАТАЕТ РЕСУРСОВ\";toastTime=1.3f;}",
    "case UPGRADES->{int before=state.guardianLevel;if(state.buyOrUpgradeGuardian()){toast=\"СТРАЖ СУНДУКА • ур.\"+state.guardianLevel;if(before==0)guardianSpawnAnim=.70f;}else toast=\"НЕ ХВАТАЕТ РЕСУРСОВ\";toastTime=1.3f;}")

# Level accounting overlays.
rep(CAVE,
    "    private void buyGlobal(int kind){if(state.buyGlobalUpgrade(kind)){toast=\"УЛУЧШЕНИЕ КУПЛЕНО\";}else toast=\"НЕ ХВАТАЕТ РЕСУРСОВ\";toastTime=1.2f;}\n",
    '''    private void buyGlobal(int kind){if(state.buyGlobalUpgrade(kind)){toast="УЛУЧШЕНИЕ КУПЛЕНО";}else toast="НЕ ХВАТАЕТ РЕСУРСОВ";toastTime=1.2f;}

    private long cargoValue(){
        double value=0;for(Worker w:workers)value+=w.cargoStone+w.cargoSilver*8d+w.cargoGold*20d+w.cargoDiamond*100d;
        return Math.max(0L,Math.round(value));
    }

    private void beginLevelSummary(){
        if(levelSummary)return;speedHeld=false;levelSummary=true;summaryAnim=0;
        summaryEarned=state.levelEarnedValue;summaryInvested=state.levelInvestedValue;summaryWallet=state.walletValue();summaryCapital=state.transferCapital(cargoValue());summaryTransfer=state.transferAmount(cargoValue());
        saveNow();game.audio.play(GameAudio.Sfx.COIN,.8f);game.audio.vibrate(55);
    }

    private void finishLevelTransition(){
        state.beginNextDepth(summaryTransfer);saveNow();levelSummary=false;summaryAnim=0;generateDepth(false);
    }

    private void beginGameOver(){if(gameOver)return;gameOver=true;speedHeld=false;clearPriority(false);saveNow();game.audio.vibrate(120);}

    private long rolling(long target){float p=Math.min(1f,summaryAnim/1.65f);p=1f-(1f-p)*(1f-p)*(1f-p);return Math.round(target*p);}

    private void drawLevelSummary(Draw d){
        d.setColor(0xDD050607);d.fillRect(0,0,width,height);float cw=Math.min(width-38f*ui,350f*ui),l=(width-cw)/2f,t=height*.20f,b=height*.66f;
        d.setColor(0xFF171B1E);d.fillRoundRect(l,t,l+cw,b,14f*ui);d.setColor(UiTheme.GOLD);d.fillRoundRect(l+16f*ui,t+4f*ui,l+cw-16f*ui,t+7f*ui,2f*ui);
        d.align=Draw.Align.CENTER;d.bold=true;d.textSize=15f*ui;d.setColor(0xFFF3F0E8);d.text("УРОВЕНЬ "+state.depth+" ЗАВЕРШЁН",width/2,t+34f*ui);
        d.bold=false;d.textSize=8f*ui;d.setColor(0xFF9FAAB0);d.text("СЛОЖНОСТЬ: "+state.difficultyTitle(),width/2,t+55f*ui);
        float x1=l+22f*ui,x2=l+cw-22f*ui,y=t+88f*ui,dy=35f*ui;d.align=Draw.Align.LEFT;d.textSize=8.2f*ui;
        d.setColor(0xFFB7C0C6);d.text("ЗАРАБОТАНО",x1,y);d.text("ВЛОЖЕНО В ГНОМОВ/АПГРЕЙДЫ",x1,y+dy);d.text("В СУНДУКЕ",x1,y+dy*2);d.text("КАПИТАЛ УРОВНЯ",x1,y+dy*3);d.text("ПЕРЕНОС",x1,y+dy*4);
        d.align=Draw.Align.RIGHT;d.bold=true;d.setColor(0xFFF0C85A);d.text(format(rolling(summaryEarned)),x2,y);d.text(format(rolling(summaryInvested)),x2,y+dy);d.text(format(rolling(summaryWallet)),x2,y+dy*2);d.text(format(rolling(summaryCapital)),x2,y+dy*3);d.setColor(0xFF78D092);d.text(format(rolling(summaryTransfer))+"  (×"+one.format(state.carryRatio())+")",x2,y+dy*4);
        d.bold=false;d.align=Draw.Align.CENTER;d.textSize=7.5f*ui;d.setColor(0xFF89949B);d.text("Следующий уровень начнётся с 1 обычным гномом.",width/2,b-18f*ui);d.align=Draw.Align.LEFT;
        button(d,summaryOk,"УРОВЕНЬ "+(state.depth+1)+"  •  OK",summaryAnim>.75f,.82f);
    }

    private void drawGameOver(Draw d){
        d.setColor(0xE60A0606);d.fillRect(0,0,width,height);float cw=Math.min(width-46f*ui,340f*ui),l=(width-cw)/2f,t=height*.28f,b=height*.61f;
        d.setColor(0xFF1B1515);d.fillRoundRect(l,t,l+cw,b,14f*ui);d.align=Draw.Align.CENTER;d.bold=true;d.textSize=18f*ui;d.setColor(0xFFE66658);d.text("ЭКСПЕДИЦИЯ ПОГИБЛА",width/2,t+54f*ui);d.textSize=10f*ui;d.setColor(0xFFF1E8E3);d.text("ГНОМОВ НЕ ОСТАЛОСЬ",width/2,t+94f*ui);d.bold=false;d.textSize=8.3f*ui;d.setColor(0xFFAAA29F);d.text("И денег на нового гнома тоже нет.",width/2,t+125f*ui);d.align=Draw.Align.LEFT;button(d,gameOverOk,"В МЕНЮ",true,.86f);
    }
''')

print("deep mine systems pass applied")