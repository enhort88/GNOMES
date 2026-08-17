package com.enhort.gnomes.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.enhort.gnomes.game.model.ArtifactType;
import com.enhort.gnomes.game.model.EnemyType;
import com.enhort.gnomes.game.model.GnomeTier;
import com.enhort.gnomes.game.model.RockType;
import com.enhort.gnomes.game.model.RuneType;

import java.util.Arrays;

/** Persistent economic state. Runtime positions and combat state live in CaveScreen. */
public class GameState {
    /** Hidden test flag toggled from the main-menu GNOMES logo. */
    public static boolean FREE_SHOP = false;
    private static final String PREFS = "gnomes_save_v2";

    public long stone = 420;
    public long silver;
    public long gold;
    public long diamond;

    public int depth = 1;
    public int depthProgress;
    public int rocksBroken;
    public int enemiesDefeated;
    public int gnomesLost;
    public long stolenValue;
    public int difficulty = 2;
    public long levelEarnedValue;
    public long levelInvestedValue;

    public final int[] tierCounts = new int[GnomeTier.values().length];
    public final int[] tierLevels = new int[GnomeTier.values().length];

    /** artifactLevels is kept for save compatibility. 0 = not owned, 1 = owned. Artifacts are never levelled. */
    public final int[] artifactLevels = new int[ArtifactType.values().length];
    public final boolean[] artifactActive = new boolean[ArtifactType.values().length];

    /** Rune levels are global meta progression; SaveRepository mirrors them across all save slots. */
    public final int[] runeLevels = new int[RuneType.values().length];
    public final boolean[] runeActive = new boolean[RuneType.values().length];

    /** Legacy engraving arrays remain only so older snapshots can be loaded without throwing data away. */
    public final int[] tierRunes = new int[GnomeTier.values().length];
    public final int[] upgradeRunes = new int[3];
    public final int[] artifactRunes = new int[ArtifactType.values().length];
    public final int[] infrastructureRunes = new int[2];

    public int miningUpgrade;
    public int speedUpgrade;
    public int combatUpgrade;
    public int guardianLevel;

    public GameState() {
        tierCounts[0] = 1;
        Arrays.fill(tierLevels, 1);
        Arrays.fill(tierRunes, -1);
        Arrays.fill(upgradeRunes, -1);
        Arrays.fill(artifactRunes, -1);
        Arrays.fill(infrastructureRunes, -1);
    }

    /** Legacy single-save loader retained for old installs. New gameplay uses SaveRepository. */
    public static GameState load() {
        GameState state = new GameState();
        Preferences p = Gdx.app.getPreferences(PREFS);
        if (!p.contains("saved")) return state;
        state.stone = p.getLong("stone", state.stone);
        state.silver = p.getLong("silver", 0);
        state.gold = p.getLong("gold", 0);
        state.diamond = p.getLong("diamond", 0);
        state.depth = Math.max(1, p.getInteger("depth", 1));
        state.depthProgress = Math.max(0, p.getInteger("depthProgress", 0));
        state.rocksBroken = Math.max(0, p.getInteger("rocksBroken", 0));
        state.enemiesDefeated = Math.max(0, p.getInteger("enemiesDefeated", 0));
        state.gnomesLost = Math.max(0, p.getInteger("gnomesLost", 0));
        state.stolenValue = Math.max(0, p.getLong("stolenValue", 0));
        state.miningUpgrade = Math.max(0, p.getInteger("miningUpgrade", 0));
        state.speedUpgrade = Math.max(0, p.getInteger("speedUpgrade", 0));
        state.combatUpgrade = Math.max(0, p.getInteger("combatUpgrade", 0));
        state.guardianLevel = Math.max(0, p.getInteger("guardianLevel", 0));
        for (int i = 0; i < state.tierCounts.length; i++) {
            state.tierCounts[i] = Math.max(0, p.getInteger("tierCount_" + i, i == 0 ? 1 : 0));
            state.tierLevels[i] = Math.max(1, p.getInteger("tierLevel_" + i, 1));
        }
        for (int i = 0; i < state.artifactLevels.length; i++) {
            state.artifactLevels[i] = Math.min(1, Math.max(0, p.getInteger("artifact_" + i, 0)));
            state.artifactActive[i] = p.getBoolean("artifactActive_" + i, state.artifactLevels[i] > 0);
        }
        for (int i = 0; i < state.runeLevels.length; i++) {
            state.runeLevels[i] = Math.max(0, p.getInteger("runeLevel_" + i, 0));
            state.runeActive[i] = p.getBoolean("runeActive_" + i, state.runeLevels[i] > 0);
        }
        return state;
    }

    public void save() {
        Preferences p = Gdx.app.getPreferences(PREFS);
        p.putBoolean("saved", true);
        p.putLong("stone", stone).putLong("silver", silver).putLong("gold", gold).putLong("diamond", diamond);
        p.putInteger("depth", depth).putInteger("depthProgress", depthProgress).putInteger("rocksBroken", rocksBroken);
        p.putInteger("enemiesDefeated", enemiesDefeated).putInteger("gnomesLost", gnomesLost).putLong("stolenValue", stolenValue);
        p.putInteger("miningUpgrade", miningUpgrade).putInteger("speedUpgrade", speedUpgrade).putInteger("combatUpgrade", combatUpgrade).putInteger("guardianLevel", guardianLevel);
        for (int i = 0; i < tierCounts.length; i++) { p.putInteger("tierCount_" + i, tierCounts[i]); p.putInteger("tierLevel_" + i, tierLevels[i]); }
        for (int i = 0; i < artifactLevels.length; i++) { p.putInteger("artifact_" + i, artifactLevels[i]); p.putBoolean("artifactActive_" + i, artifactActive[i]); }
        for (int i = 0; i < runeLevels.length; i++) { p.putInteger("runeLevel_" + i, runeLevels[i]); p.putBoolean("runeActive_" + i, runeActive[i]); }
        p.flush();
    }

    public void setDifficulty(int difficulty) { this.difficulty = Math.max(1, Math.min(4, difficulty)); }

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

    public long transferCapital(long cargoValue) { return Math.max(0L, walletValue() + levelInvestedValue + Math.max(0L, cargoValue)); }
    public long transferAmount(long cargoValue) { return (long) Math.floor(transferCapital(cargoValue) * carryRatio()); }

    /** Number of workers that will accompany the guaranteed starter miner to the next depth. */
    public int carriedGnomesCount() {
        int n = tierCounts[0] / 2;
        for (int i = 1; i < tierCounts.length; i++) if (tierCounts[i] > 0) n += Math.max(1, tierCounts[i] / 2);
        return n;
    }

    /**
     * A new depth always gets one fresh basic miner, plus roughly half of the previous crew.
     * Higher evolved tiers never disappear merely because only one specimen existed.
     */
    public void beginNextDepth(long transferredValue) {
        int[] retained = new int[tierCounts.length];
        retained[0] = tierCounts[0] / 2;
        for (int i = 1; i < tierCounts.length; i++) retained[i] = tierCounts[i] <= 0 ? 0 : Math.max(1, tierCounts[i] / 2);

        depth++;
        depthProgress = 0;
        stone = Math.max(0L, transferredValue);
        silver = gold = diamond = 0;
        Arrays.fill(tierCounts, 0);
        tierCounts[0] = 1 + retained[0];
        for (int i = 1; i < tierCounts.length; i++) tierCounts[i] = retained[i];
        Arrays.fill(tierLevels, 1);
        miningUpgrade = speedUpgrade = combatUpgrade = 0;
        guardianLevel = 0;
        levelEarnedValue = 0;
        levelInvestedValue = 0;
    }

    public boolean canBuyMiner() { return tierCounts[0] < 99 && (FREE_SHOP || stone >= minerBuyCost()); }
    public double yieldFor(RockType type, int tier) { return type.yield * incomeMultiplier(tier); }

    /** Resources only become player money after a gnome physically reaches the chest. */
    public long deposit(RockType.Material material, double amount) {
        double finalAmount = amount * chestDepositMultiplier();
        long whole = Math.max(0L, Math.round(finalAmount));
        switch (material) {
            case STONE -> stone += whole;
            case SILVER -> silver += whole;
            case GOLD -> gold += whole;
            case DIAMOND -> diamond += whole;
        }
        levelEarnedValue += materialValue(material, whole);
        return whole;
    }

    private static float upgradeCurve(int level, float early, float late) {
        int a = Math.min(10, Math.max(0, level));
        int b = Math.min(20, Math.max(0, level - 10));
        int c = Math.max(0, level - 30);
        return 1f + a * early + b * late + c * late * .22f;
    }

    /** Diminishing returns stop level-30 upgrades from turning two miners into orbital weapons. */
    public float miningMultiplier(int tier) {
        return upgradeCurve(miningUpgrade, .17f, .055f) * (1f + runeEffect(RuneType.MINING));
    }

    public float speedMultiplier(int tier) {
        float artifact = artifactOn(ArtifactType.DEPTH_BOOTS) ? .18f : 0f;
        return Math.min(2.75f, upgradeCurve(speedUpgrade, .065f, .018f) * (1f + artifact + runeEffect(RuneType.HASTE)));
    }

    public float carryMultiplier(int tier) {
        return upgradeCurve(speedUpgrade, .08f, .025f) * (1f + runeEffect(RuneType.GREED) * .65f);
    }

    public float combatMultiplier(int tier) {
        float artifact = artifactOn(ArtifactType.DEMON_FANG) ? .28f : 0f;
        return upgradeCurve(combatUpgrade, .15f, .045f) * (1f + artifact + runeEffect(RuneType.WAR));
    }

    public float incomeMultiplier(int tier) { return 1f + runeEffect(RuneType.GREED); }

    public float hazardSurvivalBonus(int tier) {
        float artifact = artifactOn(ArtifactType.ANCESTOR_HELMET) ? .24f : 0f;
        return Math.min(.82f, artifact + runeEffect(RuneType.WARD));
    }

    public float regenSuppression() {
        float artifact = artifactOn(ArtifactType.HEART_OF_MOUNTAIN) ? .32f : 0f;
        return Math.min(.94f, artifact + runeEffect(RuneType.FRACTURE));
    }

    public float tierPowerMultiplier(int tier) {
        int lvl = Math.max(1, tierLevels[tier]);
        int early = Math.min(8, lvl - 1);
        int late = Math.max(0, lvl - 9);
        return 1f + early * .16f + late * .038f;
    }

    public float chestDepositMultiplier() { return 1f + runeEffect(RuneType.GREED) * .55f + runeEffect(RuneType.MINING) * .15f; }

    public float chestTheftReduction() {
        float guardian = guardianLevel <= 0 ? 0f : Math.min(.38f, .05f * guardianLevel);
        return Math.min(.78f, guardian + runeEffect(RuneType.WARD) * .65f);
    }

    public float guardianDamage() {
        if (guardianLevel <= 0) return 0f;
        float base = 8f * (1f + (guardianLevel - 1) * .55f);
        return base * (1f + runeEffect(RuneType.WAR) * .55f + runeEffect(RuneType.MINING) * .12f);
    }

    public float guardianMaxHp() {
        if (guardianLevel <= 0) return 0f;
        return 110f * (1f + guardianLevel * .75f) * (1f + depth * .08f);
    }

    public float guardianAttackInterval() { return Math.max(.30f, .78f / (1f + runeEffect(RuneType.HASTE) * .65f)); }
    public float guardianRangeMultiplier() { return 1f + runeEffect(RuneType.HASTE) * .35f; }

    public long guardianCost() {
        if (FREE_SHOP) return 0;
        return Math.round(900d * Math.pow(2.05, guardianLevel));
    }

    public boolean buyOrUpgradeGuardian() {
        long cost = guardianCost();
        if (!FREE_SHOP) {
            if (guardianLevel < 3) {
                if (stone < cost) return false;
                stone -= cost;
                levelInvestedValue += cost;
            } else {
                long silverCost = Math.max(40, cost / 95);
                if (silver < silverCost) return false;
                silver -= silverCost;
                levelInvestedValue += silverCost * 8L;
            }
        }
        guardianLevel++;
        return true;
    }

    public String guardianCostLabel() {
        if (FREE_SHOP) return "БЕСПЛАТНО";
        long cost = guardianCost();
        if (guardianLevel < 3) return cost + " кам";
        return Math.max(40, cost / 95) + " Ag";
    }

    /** Base scale shared by enemies that have existed since the early levels. */
    public float enemyHpScale(EnemyType type) {
        float d = Math.max(0, depth - 1);
        float scale = 1f + .40f * d + .015f * d * d;
        float diff = switch (difficulty) { case 1 -> .82f; case 2 -> 1f; case 3 -> 1.26f; default -> 1.58f; };
        if (type.isBoss()) scale *= 3.35f;
        if (type == EnemyType.GHOST) scale *= .78f;
        if (type == EnemyType.SUCCUBUS) scale *= 1.12f;
        return scale * diff;
    }

    public float enemyDamageScale(EnemyType type) {
        float d = Math.max(0, depth - 1);
        float scale = 1f + .18f * d + .0045f * d * d;
        float diff = switch (difficulty) { case 1 -> .78f; case 2 -> 1f; case 3 -> 1.22f; default -> 1.48f; };
        if (type.isBoss()) scale *= 1.85f;
        return scale * diff;
    }

    public float enemySpeedScale(EnemyType type) {
        float s = 1f + Math.min(.42f, Math.max(0, depth - 1) * .018f);
        if (type == EnemyType.GHOST) s *= 1.10f;
        return s;
    }

    /** Imps steal directly from the player's stored resources. Return order: stone, silver, gold, diamond. */
    public long[] stealFromChest(int depth, boolean king) {
        long[] stolen = new long[4];
        float reduction = chestTheftReduction();
        double pct = (king ? .16 : .045) + Math.min(.08, depth * (king ? .0025 : .0015));
        pct = Math.max(.005, pct * (1.0 - reduction));
        if (stone > 0) { stolen[0] = Math.min(stone, Math.max(1, Math.round(stone * pct))); stone -= stolen[0]; }
        if (silver > 0) { stolen[1] = Math.min(silver, Math.max(1, Math.round(silver * pct))); silver -= stolen[1]; }
        if (gold > 0) { stolen[2] = Math.min(gold, Math.max(1, Math.round(gold * pct))); gold -= stolen[2]; }
        if (diamond > 0) { stolen[3] = Math.min(diamond, Math.max(1, Math.round(diamond * pct))); diamond -= stolen[3]; }
        long value = stolen[0] + stolen[1] * 8L + stolen[2] * 20L + stolen[3] * 100L;
        stolenValue += value;
        return stolen;
    }

    public long[] stealGhostLoot() {
        long[] stolen = new long[4];
        double pct = .018 * (1.0 - chestTheftReduction() * .45);
        if (gold > 0) { stolen[2] = Math.min(gold, Math.max(1, Math.round(gold * pct))); gold -= stolen[2]; }
        else if (silver > 0) { stolen[1] = Math.min(silver, Math.max(1, Math.round(silver * pct))); silver -= stolen[1]; }
        else if (stone > 0) { stolen[0] = Math.min(stone, Math.max(1, Math.round(stone * pct))); stone -= stolen[0]; }
        long value = stolen[0] + stolen[1] * 8L + stolen[2] * 20L;
        stolenValue += value;
        return stolen;
    }

    public long minerBuyCost() {
        if (FREE_SHOP) return 0;
        if (depth == 1 && tierCounts[0] < 10) return Math.round(38 * Math.pow(1.11, tierCounts[0]));
        return Math.round(90 * Math.pow(1.24, tierCounts[0]));
    }

    public long tierUpgradeCost(int tier) {
        if (FREE_SHOP) return 0;
        int lvl = tierLevels[tier];
        return Math.round((95 + tier * 190L) * Math.pow(1.72, lvl - 1));
    }

    public boolean buyMiner() {
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

    /** In cheat mode MERGE is a direct visual-spawn tester and does not require ten source gnomes. */
    public boolean mergeTier(int tier) {
        if (tier < 0 || tier >= tierCounts.length - 1) return false;
        if (FREE_SHOP) { tierCounts[tier + 1]++; return true; }
        if (tierCounts[tier] < 10) return false;
        tierCounts[tier] -= 10;
        tierCounts[tier + 1]++;
        return true;
    }

    public boolean upgradeTier(int tier) {
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

    public long globalUpgradeCost(int kind) {
        if (FREE_SHOP) return 0;
        int lvl = kind == 0 ? miningUpgrade : kind == 1 ? speedUpgrade : combatUpgrade;
        return Math.round((220 + kind * 150L) * Math.pow(1.82, lvl));
    }

    public boolean buyGlobalUpgrade(int kind) {
        if (FREE_SHOP) {
            if (kind == 0) miningUpgrade++;
            else if (kind == 1) speedUpgrade++;
            else combatUpgrade++;
            return true;
        }
        long cost = globalUpgradeCost(kind);
        if (kind == 0) {
            if (stone < cost) return false;
            stone -= cost; levelInvestedValue += cost; miningUpgrade++; return true;
        }
        if (kind == 1) {
            long paid = Math.max(3, cost / 90);
            if (silver < paid) return false;
            silver -= paid; levelInvestedValue += paid * 8L; speedUpgrade++; return true;
        }
        long paid = Math.max(2, cost / 150);
        if (gold < paid) return false;
        gold -= paid; levelInvestedValue += paid * 20L; combatUpgrade++; return true;
    }

    public int artifactCost(int artifactIndex) {
        if (FREE_SHOP) return 0;
        int[] costs = {18, 32, 48, 70};
        return costs[Math.max(0, Math.min(costs.length - 1, artifactIndex))];
    }

    public boolean artifactOwned(int index) { return index >= 0 && index < artifactLevels.length && artifactLevels[index] > 0; }
    public boolean artifactOn(ArtifactType type) { int i = type.ordinal(); return artifactOwned(i) && artifactActive[i]; }

    /** Kept under the old name so older UI code still compiles. It purchases once; there are no artifact levels. */
    public boolean upgradeArtifact(int artifactIndex) { return buyArtifact(artifactIndex); }

    public boolean buyArtifact(int artifactIndex) {
        if (artifactIndex < 0 || artifactIndex >= artifactLevels.length || artifactLevels[artifactIndex] > 0) return false;
        int cost = artifactCost(artifactIndex);
        if (!FREE_SHOP) {
            if (diamond < cost) return false;
            diamond -= cost;
        }
        artifactLevels[artifactIndex] = 1;
        artifactActive[artifactIndex] = true;
        return true;
    }

    public boolean toggleArtifact(int artifactIndex) {
        if (!artifactOwned(artifactIndex)) return false;
        artifactActive[artifactIndex] = !artifactActive[artifactIndex];
        return true;
    }

    public int runeUpgradeCost(int runeIndex) {
        if (FREE_SHOP) return 0;
        if (runeIndex < 0 || runeIndex >= runeLevels.length) return Integer.MAX_VALUE;
        int lvl = runeLevels[runeIndex];
        double base = 28d + runeIndex * 11d;
        double cost = base * Math.pow(3.15, lvl);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, Math.round(cost)));
    }

    public boolean upgradeRune(int runeIndex) {
        if (runeIndex < 0 || runeIndex >= runeLevels.length || runeLevels[runeIndex] >= 12) return false;
        int cost = runeUpgradeCost(runeIndex);
        if (!FREE_SHOP) {
            if (diamond < cost) return false;
            diamond -= cost;
        }
        runeLevels[runeIndex]++;
        runeActive[runeIndex] = true;
        return true;
    }

    public boolean toggleRune(int runeIndex) {
        if (runeIndex < 0 || runeIndex >= runeLevels.length || runeLevels[runeIndex] <= 0) return false;
        runeActive[runeIndex] = !runeActive[runeIndex];
        return true;
    }

    public boolean runeIsActive(int runeIndex) { return runeIndex >= 0 && runeIndex < runeLevels.length && runeLevels[runeIndex] > 0 && runeActive[runeIndex]; }
    public float runeEffect(RuneType type) { int i = type.ordinal(); return runeIsActive(i) ? runeLevels[i] * type.effectPerLevel : 0f; }

    /* Compatibility facade for the old target-based rune UI. All runes now affect the whole expedition. */
    public int runeTargetCount() { return 1; }
    public String runeTargetTitle(int targetIndex) { return "Все гномы экспедиции"; }
    public int runeAtTarget(int targetIndex) { for (int i = 0; i < runeActive.length; i++) if (runeActive[i]) return i; return -1; }
    public boolean engraveRune(int targetIndex, int runeIndex) { return toggleRune(runeIndex); }

    public int grantRandomRuneLevel(java.util.Random random) {
        int index = random.nextInt(runeLevels.length);
        if (runeLevels[index] < 12) runeLevels[index]++;
        runeActive[index] = true;
        return index;
    }

    public int totalGnomes() {
        int total = 0;
        for (int count : tierCounts) total += count;
        return total;
    }
}
