package com.enhort.gnomes.save;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Json;
import com.enhort.gnomes.game.GameState;

/** Five independent expedition saves plus one account-wide rune profile. */
public final class SaveRepository {
    public static final int SLOT_COUNT = 5;
    private static final String PREFS_NAME = "gnomes.saves.v3";
    private static final String META_NAME = "gnomes.meta.v1";

    private final Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
    private final Preferences meta = Gdx.app.getPreferences(META_NAME);
    private final Json json = new Json();

    public boolean exists(int slot) { return valid(slot) && prefs.contains(key(slot)); }

    public GameState fresh(int slot) { return fresh(slot, 2); }

    public GameState fresh(int slot, int difficulty) {
        GameState state = new GameState();
        state.setDifficulty(difficulty);
        applyMeta(state);
        return state;
    }

    public GameState load(int slot) {
        if (!exists(slot)) return fresh(slot);
        try {
            Snapshot s = json.fromJson(Snapshot.class, prefs.getString(key(slot)));
            GameState state = s == null ? fresh(slot) : s.toState();
            importLegacyMetaIfNeeded(state);
            applyMeta(state);
            return state;
        } catch (Exception ignored) {
            return fresh(slot);
        }
    }

    public Snapshot summary(int slot) {
        if (!exists(slot)) return null;
        try { return json.fromJson(Snapshot.class, prefs.getString(key(slot))); }
        catch (Exception ignored) { return null; }
    }

    public void save(int slot, GameState state) {
        if (!valid(slot) || state == null) return;
        persistMeta(state);
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
            for (int i = 1; i <= SLOT_COUNT; i++) if (i != slot && exists(i)) { replacement = i; break; }
            prefs.putInteger("lastSlot", replacement);
        }
        prefs.flush();
        // Deliberately do NOT delete META_NAME: runes belong to the player, not an expedition slot.
    }

    public int lastSlot() {
        int slot = prefs.getInteger("lastSlot", 1);
        return valid(slot) ? slot : 1;
    }

    public boolean anySave() {
        for (int i = 1; i <= SLOT_COUNT; i++) if (exists(i)) return true;
        return false;
    }

    public int deepestDepth() {
        int deepest = 1;
        for (int i = 1; i <= SLOT_COUNT; i++) {
            Snapshot s = summary(i);
            if (s != null) deepest = Math.max(deepest, s.depth);
        }
        return deepest;
    }

    private void importLegacyMetaIfNeeded(GameState state) {
        if (meta.getBoolean("initialized", false)) return;
        boolean hadRune = false;
        for (int i = 0; i < state.runeLevels.length; i++) {
            if (state.runeLevels[i] > 0) {
                hadRune = true;
                meta.putInteger("runeLevel_" + i, state.runeLevels[i]);
                meta.putBoolean("runeActive_" + i, true);
            }
        }
        // Even a profile with no old runes is considered initialized. Otherwise every empty slot would migrate again.
        meta.putBoolean("initialized", true);
        meta.putBoolean("legacyImported", hadRune);
        meta.flush();
    }

    private void applyMeta(GameState state) {
        if (!meta.getBoolean("initialized", false)) {
            meta.putBoolean("initialized", true);
            meta.flush();
        }
        for (int i = 0; i < state.runeLevels.length; i++) {
            state.runeLevels[i] = Math.max(0, meta.getInteger("runeLevel_" + i, 0));
            state.runeActive[i] = meta.getBoolean("runeActive_" + i, state.runeLevels[i] > 0);
        }
    }

    private void persistMeta(GameState state) {
        meta.putBoolean("initialized", true);
        for (int i = 0; i < state.runeLevels.length; i++) {
            meta.putInteger("runeLevel_" + i, Math.max(0, state.runeLevels[i]));
            meta.putBoolean("runeActive_" + i, state.runeActive[i] && state.runeLevels[i] > 0);
        }
        meta.flush();
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
        public boolean[] artifactActive;
        public int[] runeLevels;
        public boolean[] runeActive;
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
            s.artifactActive = st.artifactActive.clone();
            s.runeLevels = st.runeLevels.clone();
            s.runeActive = st.runeActive.clone();
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
            st.stone = Math.max(0, stone);
            st.silver = Math.max(0, silver);
            st.gold = Math.max(0, gold);
            st.diamond = Math.max(0, diamond);
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
            copy(artifactActive, st.artifactActive);
            copy(runeLevels, st.runeLevels);
            copy(runeActive, st.runeActive);
            copy(tierRunes, st.tierRunes);
            copy(upgradeRunes, st.upgradeRunes);
            copy(artifactRunes, st.artifactRunes);
            copy(infrastructureRunes, st.infrastructureRunes);
            for (int i = 0; i < st.artifactLevels.length; i++) {
                st.artifactLevels[i] = st.artifactLevels[i] > 0 ? 1 : 0;
                if (artifactActive == null && st.artifactLevels[i] > 0) st.artifactActive[i] = true;
            }
            st.miningUpgrade = Math.max(0, miningUpgrade);
            st.speedUpgrade = Math.max(0, speedUpgrade);
            st.combatUpgrade = Math.max(0, combatUpgrade);
            st.guardianLevel = Math.max(0, guardianLevel);
            if (st.totalGnomes() <= 0) st.tierCounts[0] = 1;
            return st;
        }

        private static void copy(int[] src, int[] dst) {
            if (src == null || dst == null) return;
            System.arraycopy(src, 0, dst, 0, Math.min(src.length, dst.length));
        }

        private static void copy(boolean[] src, boolean[] dst) {
            if (src == null || dst == null) return;
            System.arraycopy(src, 0, dst, 0, Math.min(src.length, dst.length));
        }
    }
}
