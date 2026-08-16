package com.enhort.gnomes.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.enhort.gnomes.game.model.ArtifactType;
import com.enhort.gnomes.game.model.GnomeTier;
import com.enhort.gnomes.game.model.RockType;
import com.enhort.gnomes.game.model.RuneType;

public class GameState {
    /** Hidden test flag toggled from the main-menu GNOMES logo. */
    public static boolean FREE_SHOP = false;
    private static final String PREFS = "gnomes_save_v2";

    public long stone = 420;
    public long silver = 0;
    public long gold = 0;
    public long diamond = 0;

    public int depth = 1;
    public int depthProgress = 0;
    public int rocksBroken = 0;
    public int enemiesDefeated = 0;
    public int gnomesLost = 0;
    public long stolenValue = 0;
    public int difficulty = 2;
    public long levelEarnedValue = 0;
    public long levelInvestedValue = 0;

    public final int[] tierCounts = new int[GnomeTier.values().length];
    public final int[] tierLevels = new int[GnomeTier.values().length];
    public final int[] artifactLevels = new int[ArtifactType.values().length];
    public final int[] runeLevels = new int[RuneType.values().length];
    public final int[] tierRunes = new int[GnomeTier.values().length];
    public final int[] upgradeRunes = new int[3];
    public final int[] artifactRunes = new int[ArtifactType.values().length];
    /** 0 = chest, 1 = chest guardian. */
    public final int[] infrastructureRunes = new int[2];

    public int miningUpgrade = 0;
    public int speedUpgrade = 0;
    public int combatUpgrade = 0;
    public int guardianLevel = 0;

    public GameState() {
        tierCounts[0] = 1;
        for (int i = 0; i < tierLevels.length; i++) tierLevels[i] = 1;
        java.util.Arrays.fill(tierRunes, -1);
        java.util.Arrays.fill(upgradeRunes, -1);
        java.util.Arrays.fill(artifactRunes, -1);
        java.util.Arrays.fill(infrastructureRunes, -1);

        // One starter rune makes the rune system visible from the first launch.
        runeLevels[RuneType.MINING.ordinal()] = 1;
        tierRunes[GnomeTier.MINER.ordinal()] = RuneType.MINING.ordinal();
    }

    public static GameState load() {
        GameState state = new GameState();
        Preferences p = Gdx.app.getPreferences(PREFS);
        if (!p.contains("saved")) return state;

        state.stone = p.getLong("stone", state.stone);
        state.silver = p.getLong("silver", 0);
        state.gold = p.getLong("gold", 0);
        state.diamond = p.getLong("diamond", 0);
        state.depth = p.getInteger("depth", 1);
        state.depthProgress = p.getInteger("depthProgress", 0);
        state.rocksBroken = p.getInteger("rocksBroken", 0);
        state.enemiesDefeated = p.getInteger("enemiesDefeated", 0);
        state.gnomesLost = p.getInteger("gnomesLost", 0);
        state.stolenValue = p.getLong("stolenValue", 0);
        state.miningUpgrade = p.getInteger("miningUpgrade", 0);
        state.speedUpgrade = p.getInteger("speedUpgrade", 0);
        state.combatUpgrade = p.getInteger("combatUpgrade", 0);
        state.guardianLevel = p.getInteger("guardianLevel", 0);

        for (int i = 0; i < state.tierCounts.length; i++) {
            state.tierCounts[i] = p.getInteger("tierCount_" + i, i == 0 ? 3 : 0);
            state.tierLevels[i] = p.getInteger("tierLevel_" + i, 1);
        }
        for (int i = 0; i < state.artifactLevels.length; i++) {
            state.artifactLevels[i] = p.getInteger("artifact_" + i, 0);
        }
        for (int i = 0; i < state.runeLevels.length; i++) {
            state.runeLevels[i] = p.getInteger("runeLevel_" + i, state.runeLevels[i]);
        }
        for (int i = 0; i < state.tierRunes.length; i++) {
            state.tierRunes[i] = p.getInteger("tierRune_" + i, state.tierRunes[i]);
        }
        for (int i = 0; i < state.upgradeRunes.length; i++) {
            state.upgradeRunes[i] = p.getInteger("upgradeRune_" + i, -1);
        }
        for (int i = 0; i < state.artifactRunes.length; i++) {
            state.artifactRunes[i] = p.getInteger("artifactRune_" + i, -1);
        }
        for (int i = 0; i < state.infrastructureRunes.length; i++) {
            state.infrastructureRunes[i] = p.getInteger("infraRune_" + i, -1);
        }
        return state;
    }

    public void save() {
        Preferences p = Gdx.app.getPreferences(PREFS);
        p.putBoolean("saved", true);
        p.putLong("stone", stone);
        p.putLong("silver", silver);
        p.putLong("gold", gold);
        p.putLong("diamond", diamond);
        p.putInteger("depth", depth);
        p.putInteger("depthProgress", depthProgress);
        p.putInteger("rocksBroken", rocksBroken);
        p.putInteger("enemiesDefeated", enemiesDefeated);
        p.putInteger("gnomesLost", gnomesLost);
        p.putLong("stolenValue", stolenValue);
        p.putInteger("miningUpgrade", miningUpgrade);
        p.putInteger("speedUpgrade", speedUpgrade);
        p.putInteger("combatUpgrade", combatUpgrade);
        p.putInteger("guardianLevel", guardianLevel);
        for (int i = 0; i < tierCounts.length; i++) {
            p.putInteger("tierCount_" + i, tierCounts[i]);
            p.putInteger("tierLevel_" + i, tierLevels[i]);
        }
        for (int i = 0; i < artifactLevels.length; i++) p.putInteger("artifact_" + i, artifactLevels[i]);
        for (int i = 0; i < runeLevels.length; i++) p.putInteger("runeLevel_" + i, runeLevels[i]);
        for (int i = 0; i < tierRunes.length; i++) p.putInteger("tierRune_" + i, tierRunes[i]);
        for (int i = 0; i < upgradeRunes.length; i++) p.putInteger("upgradeRune_" + i, upgradeRunes[i]);
        for (int i = 0; i < artifactRunes.length; i++) p.putInteger("artifactRune_" + i, artifactRunes[i]);
        for (int i = 0; i < infrastructureRunes.length; i++) p.putInteger("infraRune_" + i, infrastructureRunes[i]);
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

    public double yieldFor(RockType type, int tier) {
        return type.yield * incomeMultiplier(tier);
    }

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

    public float miningMultiplier(int tier) {
        return (float) Math.pow(1.20, miningUpgrade) * (1f + runeBonus(RuneType.MINING, tier));
    }

    public float speedMultiplier(int tier) {
        return (float) Math.pow(1.10, speedUpgrade)
                * (1f + 0.12f * artifactLevels[ArtifactType.DEPTH_BOOTS.ordinal()])
                * (1f + runeBonus(RuneType.HASTE, tier));
    }

    public float carryMultiplier(int tier) {
        return (1f + 0.12f * speedUpgrade)
                * (1f + 0.55f * runeBonus(RuneType.GREED, tier));
    }

    public float combatMultiplier(int tier) {
        return (float) Math.pow(1.20, combatUpgrade)
                * (1f + 0.20f * artifactLevels[ArtifactType.DEMON_FANG.ordinal()])
                * (1f + runeBonus(RuneType.WAR, tier));
    }

    public float incomeMultiplier(int tier) {
        return 1f + runeBonus(RuneType.GREED, tier);
    }

    public float hazardSurvivalBonus(int tier) {
        return Math.min(0.90f,
                0.15f * artifactLevels[ArtifactType.ANCESTOR_HELMET.ordinal()]
                        + runeBonus(RuneType.WARD, tier));
    }

    public float regenSuppression() {
        return Math.min(0.90f,
                0.15f * artifactLevels[ArtifactType.HEART_OF_MOUNTAIN.ordinal()]
                        + runeBonusGlobal(RuneType.FRACTURE));
    }

    public float tierPowerMultiplier(int tier) {
        return (float) Math.pow(1.22, Math.max(0, tierLevels[tier] - 1));
    }

    public float chestDepositMultiplier() {
        float greed = infrastructureRuneBonus(0, RuneType.GREED);
        float mining = infrastructureRuneBonus(0, RuneType.MINING);
        return 1f + greed + mining * 0.35f;
    }

    public float chestTheftReduction() {
        float ward = infrastructureRuneBonus(0, RuneType.WARD);
        float guardian = guardianLevel <= 0 ? 0f : Math.min(0.45f, 0.06f * guardianLevel);
        return Math.min(0.85f, ward + guardian);
    }

    public float guardianDamage() {
        if (guardianLevel <= 0) return 0f;
        float base = 5.5f * (float) Math.pow(1.52, guardianLevel - 1);
        float war = infrastructureRuneBonus(1, RuneType.WAR);
        float mining = infrastructureRuneBonus(1, RuneType.MINING) * 0.25f;
        return base * (1f + war + mining);
    }

    public float guardianAttackInterval() {
        float haste = infrastructureRuneBonus(1, RuneType.HASTE);
        return Math.max(0.20f, 0.78f / (1f + haste));
    }

    public float guardianRangeMultiplier() {
        return 1f + infrastructureRuneBonus(1, RuneType.HASTE) * 0.75f;
    }

    public long guardianCost() {
        if (FREE_SHOP) return 0;
        if (guardianLevel == 0) return 300;
        return Math.round(260 * Math.pow(1.85, guardianLevel));
    }

    public boolean buyOrUpgradeGuardian() {
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

    public String guardianCostLabel() {
        if (FREE_SHOP) return "БЕСПЛАТНО";
        long cost = guardianCost();
        if (guardianLevel < 3) return cost + " кам";
        return Math.max(4, cost / 120) + " Ag";
    }

    /**
     * Imps steal directly from the player's stored resources. The chest itself cannot be damaged.
     * Return order: stone, silver, gold, diamond.
     */
    public long[] stealFromChest(int depth, boolean king) {
        long[] stolen = new long[4];
        float reduction = chestTheftReduction();
        double pct = (king ? 0.16 : 0.045) + Math.min(0.08, depth * (king ? 0.0025 : 0.0015));
        pct = Math.max(0.005, pct * (1.0 - reduction));

        if (stone > 0) { stolen[0] = Math.min(stone, Math.max(1, Math.round(stone * pct))); stone -= stolen[0]; }
        if (silver > 0) { stolen[1] = Math.min(silver, Math.max(1, Math.round(silver * pct))); silver -= stolen[1]; }
        if (gold > 0) { stolen[2] = Math.min(gold, Math.max(1, Math.round(gold * pct))); gold -= stolen[2]; }
        if (diamond > 0) { stolen[3] = Math.min(diamond, Math.max(1, Math.round(diamond * pct))); diamond -= stolen[3]; }

        long value = stolen[0] + stolen[1] * 8L + stolen[2] * 20L + stolen[3] * 100L;
        stolenValue += value;
        return stolen;
    }

    public long minerBuyCost() {
        if (FREE_SHOP) return 0;
        if (depth == 1 && tierCounts[0] < 10) return Math.round(35 * Math.pow(1.10, tierCounts[0]));
        return Math.round(85 * Math.pow(1.27, tierCounts[0]));
    }

    public long tierUpgradeCost(int tier) {
        if (FREE_SHOP) return 0;
        int lvl = tierLevels[tier];
        return Math.round((70 + tier * 160L) * Math.pow(1.62, lvl - 1));
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

    public boolean mergeTier(int tier) {
        if (tier < 0 || tier >= tierCounts.length - 1 || tierCounts[tier] < 10) return false;
        tierCounts[tier] -= 10;
        tierCounts[tier + 1] += 1;
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
        return Math.round((180 + kind * 120L) * Math.pow(1.72, lvl));
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

    public int artifactCost(int artifactIndex) {
        if (FREE_SHOP) return 0;
        int lvl = artifactLevels[artifactIndex];
        return 1 + artifactIndex + lvl * (2 + artifactIndex);
    }

    public boolean upgradeArtifact(int artifactIndex) {
        int cost = artifactCost(artifactIndex);
        if (!FREE_SHOP) {
            if (diamond < cost) return false;
            diamond -= cost;
        }
        artifactLevels[artifactIndex]++;
        return true;
    }

    public int runeUpgradeCost(int runeIndex) {
        if (FREE_SHOP) return 0;
        int lvl = runeLevels[runeIndex];
        return 1 + runeIndex + lvl * (2 + runeIndex);
    }

    public boolean upgradeRune(int runeIndex) {
        if (runeIndex < 0 || runeIndex >= runeLevels.length) return false;
        int cost = runeUpgradeCost(runeIndex);
        if (!FREE_SHOP) {
            if (diamond < cost) return false;
            diamond -= cost;
        }
        runeLevels[runeIndex]++;
        return true;
    }

    public int runeTargetCount() {
        return tierRunes.length + upgradeRunes.length + artifactRunes.length + infrastructureRunes.length;
    }

    public String runeTargetTitle(int targetIndex) {
        if (targetIndex < tierRunes.length) return GnomeTier.values()[targetIndex].title;
        targetIndex -= tierRunes.length;
        if (targetIndex < upgradeRunes.length) {
            return switch (targetIndex) {
                case 0 -> "Улучшение: кирки";
                case 1 -> "Улучшение: логистика";
                default -> "Улучшение: бой";
            };
        }
        targetIndex -= upgradeRunes.length;
        if (targetIndex < artifactRunes.length) return "Артефакт: " + ArtifactType.values()[targetIndex].title;
        targetIndex -= artifactRunes.length;
        return targetIndex == 0 ? "Сундук" : "Страж сундука";
    }

    public int runeAtTarget(int targetIndex) {
        if (targetIndex < tierRunes.length) return tierRunes[targetIndex];
        targetIndex -= tierRunes.length;
        if (targetIndex < upgradeRunes.length) return upgradeRunes[targetIndex];
        targetIndex -= upgradeRunes.length;
        if (targetIndex < artifactRunes.length) return artifactRunes[targetIndex];
        targetIndex -= artifactRunes.length;
        return infrastructureRunes[targetIndex];
    }

    public boolean engraveRune(int targetIndex, int runeIndex) {
        if (runeIndex < 0 || runeIndex >= runeLevels.length || runeLevels[runeIndex] <= 0) return false;
        int existing = runeAtTarget(targetIndex);
        int value = existing == runeIndex ? -1 : runeIndex;
        if (targetIndex < tierRunes.length) {
            tierRunes[targetIndex] = value;
            return true;
        }
        targetIndex -= tierRunes.length;
        if (targetIndex < upgradeRunes.length) {
            upgradeRunes[targetIndex] = value;
            return true;
        }
        targetIndex -= upgradeRunes.length;
        if (targetIndex < artifactRunes.length) {
            artifactRunes[targetIndex] = value;
            return true;
        }
        targetIndex -= artifactRunes.length;
        infrastructureRunes[targetIndex] = value;
        return true;
    }

    public int grantRandomRuneLevel(java.util.Random random) {
        int index = random.nextInt(runeLevels.length);
        runeLevels[index]++;
        return index;
    }

    private float runeBonus(RuneType type, int tier) {
        int local = tier >= 0 && tier < tierRunes.length && tierRunes[tier] == type.ordinal() ? 1 : 0;
        int global = globalRuneEngravings(type);
        return (local + global) * runeLevels[type.ordinal()] * type.effectPerLevel;
    }

    private float infrastructureRuneBonus(int infrastructureIndex, RuneType type) {
        if (infrastructureIndex < 0 || infrastructureIndex >= infrastructureRunes.length) return 0f;
        if (infrastructureRunes[infrastructureIndex] != type.ordinal()) return 0f;
        return runeLevels[type.ordinal()] * type.effectPerLevel;
    }

    private float runeBonusGlobal(RuneType type) {
        int all = globalRuneEngravings(type);
        for (int rune : tierRunes) if (rune == type.ordinal()) all++;
        return all * runeLevels[type.ordinal()] * type.effectPerLevel;
    }

    private int globalRuneEngravings(RuneType type) {
        int count = 0;
        for (int rune : upgradeRunes) if (rune == type.ordinal()) count++;
        for (int rune : artifactRunes) if (rune == type.ordinal()) count++;
        return count;
    }

    public int totalGnomes() {
        int total = 0;
        for (int count : tierCounts) total += count;
        return total;
    }
}
