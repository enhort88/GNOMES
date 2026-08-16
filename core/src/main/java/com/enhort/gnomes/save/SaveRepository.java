package com.enhort.gnomes.save;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Json;
import com.enhort.gnomes.game.GameState;

/** Five independent JSON save slots, mirroring the structure used by DOT//CORE. */
public final class SaveRepository {
    public static final int SLOT_COUNT = 5;
    private static final String PREFS_NAME = "gnomes.saves.v3";

    private final Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
    private final Json json = new Json();

    public boolean exists(int slot) {
        return valid(slot) && prefs.contains(key(slot));
    }

    public GameState fresh(int slot) { return fresh(slot, 2); }

    public GameState fresh(int slot, int difficulty) {
        GameState state = new GameState();
        state.setDifficulty(difficulty);
        return state;
    }

    public GameState load(int slot) {
        if (!exists(slot)) return fresh(slot);
        try {
            Snapshot s = json.fromJson(Snapshot.class, prefs.getString(key(slot)));
            return s == null ? fresh(slot) : s.toState();
        } catch (Exception ignored) {
            return fresh(slot);
        }
    }

    public Snapshot summary(int slot) {
        if (!exists(slot)) return null;
        try {
            return json.fromJson(Snapshot.class, prefs.getString(key(slot)));
        } catch (Exception ignored) {
            return null;
        }
    }

    public void save(int slot, GameState state) {
        if (!valid(slot) || state == null) return;
        Snapshot s = Snapshot.fromState(state);
        s.savedAt = System.currentTimeMillis();
        prefs.putString(key(slot), json.toJson(s));
        prefs.putInteger("lastSlot", slot);
        prefs.flush();
    }

    public void delete(int slot) {
        if (!valid(slot)) return;
        prefs.remove(key(slot));
        if (prefs.getInteger("lastSlot", 1) == slot) {
            int replacement = 1;
            for (int i = 1; i <= SLOT_COUNT; i++) {
                if (i != slot && exists(i)) { replacement = i; break; }
            }
            prefs.putInteger("lastSlot", replacement);
        }
        prefs.flush();
    }

    public int lastSlot() {
        int slot = prefs.getInteger("lastSlot", 1);
        return valid(slot) ? slot : 1;
    }

    public boolean anySave() {
        for (int i = 1; i <= SLOT_COUNT; i++) if (exists(i)) return true;
        return false;
    }

    private static boolean valid(int slot) { return slot >= 1 && slot <= SLOT_COUNT; }
    private static String key(int slot) { return "slot." + slot; }

    /** Serializable snapshot. Runtime-only positions are deliberately regenerated per depth. */
    public static final class Snapshot {
        public long stone;
        public long silver;
        public long gold;
        public long diamond;
        public int depth;
        public int depthProgress;
        public int rocksBroken;
        public int enemiesDefeated;
        public int gnomesLost;
        public long stolenValue;
        public int difficulty;
        public long levelEarnedValue;
        public long levelInvestedValue;
        public int[] tierCounts;
        public int[] tierLevels;
        public int[] artifactLevels;
        public int[] runeLevels;
        public int[] tierRunes;
        public int[] upgradeRunes;
        public int[] artifactRunes;
        public int[] infrastructureRunes;
        public int miningUpgrade;
        public int speedUpgrade;
        public int combatUpgrade;
        public int guardianLevel;
        public long savedAt;

        public Snapshot() {}

        public static Snapshot fromState(GameState st) {
            Snapshot s = new Snapshot();
            s.stone = st.stone;
            s.silver = st.silver;
            s.gold = st.gold;
            s.diamond = st.diamond;
            s.depth = st.depth;
            s.depthProgress = st.depthProgress;
            s.rocksBroken = st.rocksBroken;
            s.enemiesDefeated = st.enemiesDefeated;
            s.gnomesLost = st.gnomesLost;
            s.stolenValue = st.stolenValue;
            s.difficulty = st.difficulty;
            s.levelEarnedValue = st.levelEarnedValue;
            s.levelInvestedValue = st.levelInvestedValue;
            s.tierCounts = st.tierCounts.clone();
            s.tierLevels = st.tierLevels.clone();
            s.artifactLevels = st.artifactLevels.clone();
            s.runeLevels = st.runeLevels.clone();
            s.tierRunes = st.tierRunes.clone();
            s.upgradeRunes = st.upgradeRunes.clone();
            s.artifactRunes = st.artifactRunes.clone();
            s.infrastructureRunes = st.infrastructureRunes.clone();
            s.miningUpgrade = st.miningUpgrade;
            s.speedUpgrade = st.speedUpgrade;
            s.combatUpgrade = st.combatUpgrade;
            s.guardianLevel = st.guardianLevel;
            return s;
        }

        public GameState toState() {
            GameState st = new GameState();
            st.stone = stone;
            st.silver = silver;
            st.gold = gold;
            st.diamond = diamond;
            st.depth = Math.max(1, depth);
            st.depthProgress = Math.max(0, depthProgress);
            st.rocksBroken = Math.max(0, rocksBroken);
            st.enemiesDefeated = Math.max(0, enemiesDefeated);
            st.gnomesLost = Math.max(0, gnomesLost);
            st.stolenValue = Math.max(0, stolenValue);
            st.setDifficulty(difficulty >= 1 && difficulty <= 4 ? difficulty : 2);
            st.levelEarnedValue = Math.max(0L, levelEarnedValue);
            st.levelInvestedValue = Math.max(0L, levelInvestedValue);
            copy(tierCounts, st.tierCounts);
            copy(tierLevels, st.tierLevels);
            copy(artifactLevels, st.artifactLevels);
            copy(runeLevels, st.runeLevels);
            copy(tierRunes, st.tierRunes);
            copy(upgradeRunes, st.upgradeRunes);
            copy(artifactRunes, st.artifactRunes);
            copy(infrastructureRunes, st.infrastructureRunes);
            st.miningUpgrade = Math.max(0, miningUpgrade);
            st.speedUpgrade = Math.max(0, speedUpgrade);
            st.combatUpgrade = Math.max(0, combatUpgrade);
            st.guardianLevel = Math.max(0, guardianLevel);
            return st;
        }

        private static void copy(int[] src, int[] dst) {
            if (src == null || dst == null) return;
            System.arraycopy(src, 0, dst, 0, Math.min(src.length, dst.length));
        }
    }
}
