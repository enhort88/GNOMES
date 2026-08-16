package com.enhort.gnomes.game;

import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.utils.TimeUtils;
import com.enhort.gnomes.draw.Draw;
import com.enhort.gnomes.game.entities.Enemy;
import com.enhort.gnomes.game.entities.Gnome;
import com.enhort.gnomes.game.entities.Hazard;
import com.enhort.gnomes.game.entities.Particle;
import com.enhort.gnomes.game.entities.Rock;
import com.enhort.gnomes.game.model.ArtifactType;
import com.enhort.gnomes.game.model.EnemyType;
import com.enhort.gnomes.game.model.GnomeTier;
import com.enhort.gnomes.game.model.HazardType;
import com.enhort.gnomes.game.model.RockType;
import com.enhort.gnomes.game.model.RuneType;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MineScreen extends InputAdapter {
    public static final class Rect {
        public float left, top, right, bottom;
        public Rect() {}
        public Rect(float l, float t, float r, float b) { set(l, t, r, b); }
        public Rect(Rect o) { set(o.left, o.top, o.right, o.bottom); }
        public void set(float l, float t, float r, float b) { left=l; top=t; right=r; bottom=b; }
        public float centerX() { return (left + right) * 0.5f; }
        public float centerY() { return (top + bottom) * 0.5f; }
        public float width() { return right - left; }
        public float height() { return bottom - top; }
        public boolean contains(float x, float y) { return x >= left && x <= right && y >= top && y <= bottom; }
    }

    public static Rect rect(float l, float t, float r, float b) { return new Rect(l, t, r, b); }

    private static final int TAB_GNOMES = 0;
    private static final int TAB_UPGRADES = 1;
    private static final int TAB_ARTIFACTS = 2;
    private static final int TAB_RUNES = 3;

    private final Random random = new Random();
    private final DecimalFormat compact = new DecimalFormat("0.0");

    private final List<Gnome> gnomes = new ArrayList<>();
    private final List<Rock> rocks = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Hazard> hazards = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<HitRegion> hitRegions = new ArrayList<>();
    private final Gnome[] previewGnomes = new Gnome[GnomeTier.values().length];

    private GameState state;

    private float ui = 1f;
    private float worldTop;
    private float worldBottom;
    private final Rect worldRect = new Rect();
    private final Rect collectRect = new Rect();
    private final Rect guardianRect = new Rect();
    private final Rect speedRect = new Rect();
    private float chestX;
    private float chestY;
    private float guardianAttackCooldown;

    private long lastFrameNanos;
    private long lastSaveMillis;
    private boolean running = true;
    private boolean speedHeld = false;
    private int selectedTab = TAB_GNOMES;
    private int gnomePage = 0;
    private int runePage = 0;
    private int runeTarget = 0;
    private float touchDownX;
    private float touchDownY;
    private boolean touchMoved;

    private float elapsed;
    private float enemyTimer = 18f;
    private float hazardTimer = 12f;
    private float messageTimer = 0f;
    private String message = "Шахта открыта";
    private String subMessage = "Гномы уже работают. Им, в отличие от людей, даже совещание не понадобилось.";

    public float width;
    public float height;

    public MineScreen() {
        state = GameState.load();
        for (int i = 0; i < previewGnomes.length; i++) previewGnomes[i] = new Gnome(GnomeTier.values()[i], 0, 0, i);
        lastSaveMillis = TimeUtils.millis();
    }

    public void resize(int w, int h) {
        width = w;
        height = h;
        ui = w / 420f;
        worldTop = 112f * ui;
        worldBottom = h - 306f * ui;
        worldRect.set(0, worldTop, w, worldBottom);
        chestX = worldRect.centerX();
        chestY = worldRect.bottom - 48f * ui;
        rebuildWorld();
    }

    public void pause() {
        running = false;
        speedHeld = false;
        state.save();
    }

    public void resume() {
        running = true;
        lastFrameNanos = 0;
    }

    public void render(Draw gfx, float rawDt) {
        if (!running) return;
        float dt = Math.min(rawDt, 0.05f);
        update(dt * (speedHeld ? 4f : 1f));
        hitRegions.clear();
        drawTopHud(gfx);
        drawMine(gfx);
        drawBottomPanel(gfx);
        if (messageTimer > 0f) drawMessage(gfx);
    }

    private void rebuildWorld() {
        rocks.clear();
        enemies.clear();
        hazards.clear();
        particles.clear();
        createInitialGnomes();
        int rockCount = 15;
        for (int i = 0; i < rockCount; i++) spawnRock();
    }

    private void createInitialGnomes() {
        gnomes.clear();
        if (worldRect.height() <= 0) return;
        for (int tier = 0; tier < state.tierCounts.length; tier++) {
            int visibleCount = Math.min(60, state.tierCounts[tier]);
            for (int i = 0; i < visibleCount; i++) {
                spawnGnome(GnomeTier.values()[tier]);
            }
        }
    }

    private void syncVisualGnomeCounts() {
        for (int tier = 0; tier < state.tierCounts.length; tier++) {
            GnomeTier gt = GnomeTier.values()[tier];
            int target = Math.min(60, state.tierCounts[tier]);
            int have = 0;
            for (Gnome g : gnomes) if (g.tier == gt) have++;
            while (have < target) {
                spawnGnome(gt);
                have++;
            }
            if (have > target) {
                Iterator<Gnome> it = gnomes.iterator();
                while (it.hasNext() && have > target) {
                    if (it.next().tier == gt) {
                        it.remove();
                        have--;
                    }
                }
            }
        }
    }

    private void spawnGnome(GnomeTier tier) {
        float x = chestX + rand(-65f, 65f) * ui;
        float y = chestY + rand(-48f, 20f) * ui;
        clampIntoWorldHolder holder = clamp(x, y, tier.size * ui);
        gnomes.add(new Gnome(tier, holder.x, holder.y, random.nextFloat() * 6.283f));
    }

    private void update(float dt) {
        if (worldRect.height() <= 0) return;
        elapsed += dt;
        if (messageTimer > 0) messageTimer -= dt;

        updateRocks(dt);
        updateGuardian(dt);
        updateEnemies(dt);
        updateHazards(dt);
        updateGnomes(dt);
        updateParticles(dt);
        handleSpawns(dt);
        handleDepthProgress();

        long now = TimeUtils.millis();
        if (now - lastSaveMillis > 5000) {
            state.save();
            lastSaveMillis = now;
        }
    }

    private void updateRocks(float dt) {
        float regenSuppression = state.regenSuppression();
        int alive = 0;
        Iterator<Rock> it = rocks.iterator();
        while (it.hasNext()) {
            Rock rock = it.next();
            if (rock.destroyed) {
                rock.respawnDelay -= dt;
                if (rock.respawnDelay <= 0f) it.remove();
                continue;
            }
            alive++;
            if (rock.type.regenPerSecond > 0 && rock.hp > 0 && rock.hp < rock.maxHp) {
                float regen = rock.type.regenPerSecond * (1f - regenSuppression);
                rock.hp = Math.min(rock.maxHp, rock.hp + regen * dt);
            }
        }
        while (alive < 15) {
            spawnRock();
            alive++;
        }
    }

    private void updateGuardian(float dt) {
        if (state.guardianLevel <= 0) return;
        guardianAttackCooldown -= dt;
        if (guardianAttackCooldown > 0f) return;

        Enemy target = null;
        float bestD = Float.MAX_VALUE;
        float range = 112f * ui * state.guardianRangeMultiplier();
        for (Enemy e : enemies) {
            if (!e.isAlive()) continue;
            // The guard is primarily anti-theft, but will shoot anything that comes close.
            float d = distanceSq(chestX, chestY, e.x, e.y);
            float priority = isChestThief(e) ? d * 0.35f : d;
            if (d <= range * range && priority < bestD) {
                bestD = priority;
                target = e;
            }
        }
        if (target != null) {
            guardianAttackCooldown = state.guardianAttackInterval();
            target.hp -= state.guardianDamage();
            addParticles(target.x, target.y, 0xFFFFD36B, 4, 45f);
        }
    }

    private void updateEnemies(float dt) {
        List<Enemy> summons = new ArrayList<>();
        Iterator<Enemy> it = enemies.iterator();
        while (it.hasNext()) {
            Enemy enemy = it.next();
            if (!enemy.isAlive()) {
                state.enemiesDefeated++;
                addParticles(enemy.x, enemy.y, enemy.type.color, 16, 80f);
                if (enemy.type.ordinal() >= EnemyType.IMP_KING.ordinal()) {
                    int rune = state.grantRandomRuneLevel(random);
                    addMessage(enemy.type.title + " повержен", "Найдена " + RuneType.values()[rune].title + ".");
                } else {
                    addMessage(enemy.type.title + " повержен", "Гномы возвращаются к добыче.");
                }
                it.remove();
                continue;
            }

            enemy.attackCooldown -= dt;
            enemy.summonCooldown -= dt;

            if (isChestThief(enemy)) {
                float dx = chestX - enemy.x;
                float dy = chestY - enemy.y;
                float dist = len(dx, dy);
                float reach = (enemy.type.size * 0.50f + 25f) * ui;
                if (dist > reach) {
                    float inv = dist > 0.01f ? 1f / dist : 0f;
                    enemy.x += dx * inv * enemy.type.moveSpeed * ui * dt;
                    enemy.y += dy * inv * enemy.type.moveSpeed * ui * dt;
                } else if (enemy.attackCooldown <= 0f) {
                    enemy.attackCooldown = enemy.type == EnemyType.IMP_KING ? 1.8f : 2.6f;
                    long[] stolen = state.stealFromChest(state.depth, enemy.type == EnemyType.IMP_KING);
                    long value = stolen[0] + stolen[1] * 8L + stolen[2] * 20L + stolen[3] * 100L;
                    if (value > 0) {
                        addParticles(chestX, chestY, 0xFFFFC84B, 10, 70f);
                        addMessage("Бес залез в сундук", theftText(stolen));
                    } else {
                        addMessage("Бес у сундука", "Воровать уже нечего. Неловкий момент даже для беса.");
                    }
                    // Kick the thief away so each robbery is a visible raid, not a resource vacuum.
                    float kick = 55f * ui;
                    enemy.x += (enemy.x < chestX ? -kick : kick);
                    enemy.y += rand(-25f, 25f) * ui;
                }
            } else {
                Gnome nearest = nearestGnome(enemy.x, enemy.y);
                if (nearest != null) {
                    float dx = nearest.x - enemy.x;
                    float dy = nearest.y - enemy.y;
                    float dist = len(dx, dy);
                    if (dist > (enemy.type.size + nearest.tier.size) * 0.55f * ui) {
                        float inv = dist > 0.01f ? 1f / dist : 0f;
                        enemy.x += dx * inv * enemy.type.moveSpeed * ui * dt;
                        enemy.y += dy * inv * enemy.type.moveSpeed * ui * dt;
                    } else if (enemy.attackCooldown <= 0f) {
                        enemy.attackCooldown = 1.2f;
                        nearest.stunTime = Math.max(nearest.stunTime, 0.55f + enemy.type.contactPower * 0.03f);
                        addParticles(nearest.x, nearest.y, 0xFFFF9A4F, 5, 40f);
                        if (enemy.type.ordinal() >= EnemyType.IMP_KING.ordinal() && random.nextFloat() < 0.035f) {
                            loseGnome(nearest, enemy.type.title + " сбил гнома в пропасть");
                        }
                    }
                }
            }

            if ((enemy.type == EnemyType.IMP_KING || enemy.type == EnemyType.DEMON_KING) && enemy.summonCooldown <= 0f) {
                enemy.summonCooldown = enemy.type == EnemyType.IMP_KING ? 7f : 9f;
                EnemyType summonType = enemy.type == EnemyType.IMP_KING ? EnemyType.IMP : EnemyType.DEMON;
                if (enemies.size() + summons.size() < 12) {
                    Enemy summoned = new Enemy(summonType, enemy.x + rand(-32, 32) * ui,
                            enemy.y + rand(-32, 32) * ui, random.nextFloat() * 6.28f);
                    summons.add(summoned);
                }
            }

            clampEnemy(enemy);
        }
        enemies.addAll(summons);
    }

    private boolean isChestThief(Enemy enemy) {
        return enemy.type == EnemyType.IMP || enemy.type == EnemyType.IMP_KING;
    }

    private String theftText(long[] stolen) {
        StringBuilder b = new StringBuilder("Украдено: ");
        boolean any = false;
        if (stolen[0] > 0) { b.append(stolen[0]).append(" кам"); any = true; }
        if (stolen[1] > 0) { if (any) b.append(", "); b.append(stolen[1]).append(" Ag"); any = true; }
        if (stolen[2] > 0) { if (any) b.append(", "); b.append(stolen[2]).append(" Au"); any = true; }
        if (stolen[3] > 0) { if (any) b.append(", "); b.append(stolen[3]).append(" ◆"); }
        return b.toString();
    }

    private void updateHazards(float dt) {
        Iterator<Hazard> it = hazards.iterator();
        while (it.hasNext()) {
            Hazard hazard = it.next();
            hazard.age += dt;
            if (!hazard.triggered && hazard.age >= 1.6f) {
                hazard.triggered = true;
                triggerHazard(hazard);
            }
            if (hazard.type == HazardType.LAVA && hazard.age > 1.6f) {
                for (Gnome g : new ArrayList<>(gnomes)) {
                    if (!hazard.affectedGnomes.contains(g.id) && distance(g.x, g.y, hazard.x, hazard.y) < hazard.radius) {
                        hazard.affectedGnomes.add(g.id);
                        if (random.nextFloat() < 0.10f * (1f - state.hazardSurvivalBonus(g.tier.ordinal()))) {
                            loseGnome(g, "Гном не успел выбраться из лавы");
                        } else {
                            g.stunTime = Math.max(g.stunTime, 4f);
                        }
                    }
                }
            }
            if (hazard.age >= hazard.duration) it.remove();
        }
    }

    private void triggerHazard(Hazard hazard) {
        switch (hazard.type) {
            case COLLAPSE -> {
                addParticles(hazard.x, hazard.y, 0xFF9B8B78, 24, 120f);
                for (Gnome g : new ArrayList<>(gnomes)) {
                    if (distance(g.x, g.y, hazard.x, hazard.y) < hazard.radius) {
                        float survival = state.hazardSurvivalBonus(g.tier.ordinal());
                        float lossChance = 0.19f * (1f - survival);
                        if (random.nextFloat() < lossChance) loseGnome(g, "Гнома засыпало обвалом");
                        else g.stunTime = Math.max(g.stunTime, 5f);
                    }
                }
            }
            case PIT -> {
                Gnome victim = nearestGnome(hazard.x, hazard.y);
                if (victim != null && distance(victim.x, victim.y, hazard.x, hazard.y) < hazard.radius * 1.7f) {
                    float survival = state.hazardSurvivalBonus(victim.tier.ordinal());
                    if (random.nextFloat() < 0.42f * (1f - survival)) loseGnome(victim, "Гном провалился в яму");
                    else victim.stunTime = Math.max(victim.stunTime, 4f);
                }
            }
            case LAVA -> addMessage("Лава!", "Часть маршрутов временно опасна.");
            case FLOOD -> {
                addMessage("Прорыв воды", "Поток смывает гномов с рабочих мест.");
                for (Gnome g : new ArrayList<>(gnomes)) {
                    float survival = state.hazardSurvivalBonus(g.tier.ordinal());
                    g.y += rand(30, 90) * ui;
                    g.x += rand(-45, 45) * ui;
                    g.stunTime = Math.max(g.stunTime, 2.2f);
                    if (random.nextFloat() < 0.035f * (1f - survival)) loseGnome(g, "Гнома унесло потоком");
                    else clampGnome(g);
                }
            }
        }
    }

    private void updateGnomes(float dt) {
        for (Gnome g : new ArrayList<>(gnomes)) {
            if (g.stunTime > 0f) {
                g.stunTime -= dt;
                g.vx *= 0.85f;
                g.vy *= 0.85f;
                continue;
            }
            g.attackCooldown -= dt;

            // Threats override work. Even a loaded miner drops everything except the sack and joins the fight.
            Enemy enemy = nearestEnemy(g.x, g.y);
            if (enemy != null) {
                g.targetEnemy = enemy;
                g.targetRock = null;
                moveOrFightEnemy(g, enemy, dt);
            } else {
                g.targetEnemy = null;
                float capacity = g.tier.cargoCapacity * state.carryMultiplier(g.tier.ordinal());
                if (g.hasCargo() && g.cargoLoad() >= capacity) {
                    g.targetRock = null;
                    moveToChestAndDeposit(g, dt);
                } else {
                    if (g.targetRock == null || g.targetRock.destroyed) g.targetRock = nearestRock(g.x, g.y);
                    if (g.targetRock != null) moveOrMineRock(g, g.targetRock, dt);
                    else if (g.hasCargo()) moveToChestAndDeposit(g, dt);
                }
            }
            clampGnome(g);
        }
    }

    private void moveToChestAndDeposit(Gnome g, float dt) {
        float dx = chestX - g.x;
        float dy = chestY - g.y;
        float dist = len(dx, dy);
        float reach = 30f * ui + g.tier.size * 0.35f * ui;
        if (dist > reach) {
            moveGnome(g, dx, dy, g.tier.moveSpeed * state.speedMultiplier(g.tier.ordinal()), dt);
            return;
        }

        g.vx = g.vy = 0;
        if (!g.hasCargo()) return;

        long deposited = 0;
        if (g.cargoStone > 0) deposited += state.deposit(RockType.Material.STONE, g.cargoStone);
        if (g.cargoSilver > 0) deposited += state.deposit(RockType.Material.SILVER, g.cargoSilver) * 8L;
        if (g.cargoGold > 0) deposited += state.deposit(RockType.Material.GOLD, g.cargoGold) * 20L;
        if (g.cargoDiamond > 0) deposited += state.deposit(RockType.Material.DIAMOND, g.cargoDiamond) * 100L;
        g.clearCargo();
        addParticles(chestX, chestY - 8f * ui, 0xFFFFC84B, 5, 35f);
        if (deposited >= 1000) addMessage("Сундук пополнен", "+" + formatNumber(deposited) + " стоимости.");
    }

    private void moveOrFightEnemy(Gnome g, Enemy enemy, float dt) {
        float dx = enemy.x - g.x;
        float dy = enemy.y - g.y;
        float dist = len(dx, dy);
        float reach = (g.tier.size + enemy.type.size) * 0.48f * ui;
        if (dist > reach) {
            moveGnome(g, dx, dy, g.tier.moveSpeed * state.speedMultiplier(g.tier.ordinal()), dt);
        } else {
            g.vx = g.vy = 0;
            if (g.attackCooldown <= 0f) {
                g.attackCooldown = Math.max(0.18f, 0.68f - g.tier.ordinal() * 0.06f);
                float damage = g.tier.combatPower * state.tierPowerMultiplier(g.tier.ordinal()) * state.combatMultiplier(g.tier.ordinal());
                enemy.hp -= damage;
                addParticles(enemy.x, enemy.y, 0xFFFFC35A, 4, 55f);
            }
        }
    }

    private void moveOrMineRock(Gnome g, Rock rock, float dt) {
        float dx = rock.x - g.x;
        float dy = rock.y - g.y;
        float dist = len(dx, dy);
        float reach = rock.radius + g.tier.size * 0.33f * ui;
        if (dist > reach) {
            moveGnome(g, dx, dy, g.tier.moveSpeed * state.speedMultiplier(g.tier.ordinal()), dt);
        } else {
            g.vx = g.vy = 0;
            if (g.attackCooldown <= 0f) {
                g.attackCooldown = Math.max(0.14f, 0.74f - g.tier.ordinal() * 0.08f);
                float damage = g.tier.miningPower * state.tierPowerMultiplier(g.tier.ordinal()) * state.miningMultiplier(g.tier.ordinal());
                rock.hp -= damage;
                addParticles(rock.x + rand(-6, 6) * ui, rock.y + rand(-6, 6) * ui, rock.type.color, 3, 35f);
                if (rock.hp <= 0f && !rock.destroyed) breakRock(rock, g);
            }
        }
    }

    private void moveGnome(Gnome g, float dx, float dy, float speed, float dt) {
        float dist = len(dx, dy);
        if (dist < 0.001f) return;
        float inv = 1f / dist;
        g.vx = dx * inv * speed * ui;
        g.vy = dy * inv * speed * ui;
        g.x += g.vx * dt;
        g.y += g.vy * dt;
    }

    private void updateParticles(float dt) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0f) {
                it.remove();
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vy += 70f * ui * dt;
        }
    }

    private void handleSpawns(float dt) {
        enemyTimer -= dt;
        hazardTimer -= dt;
        if (enemyTimer <= 0f) {
            spawnEnemyWave();
            enemyTimer = Math.max(14f, 34f - state.depth * 0.45f) + random.nextFloat() * 12f;
        }
        if (hazardTimer <= 0f) {
            spawnHazard();
            hazardTimer = 23f + random.nextFloat() * 22f;
        }
    }

    private void handleDepthProgress() {
        int threshold = 12 + state.depth * 2;
        if (state.depthProgress >= threshold) {
            state.depthProgress -= threshold;
            state.depth++;
            addMessage("ГЛУБИНА " + state.depth, depthUnlockText());
            if (state.depth % 10 == 0) spawnBossForDepth();
        }
    }

    private String depthUnlockText() {
        if (state.depth == 3) return "В породе появилось серебро.";
        if (state.depth == 6) return "Найдены золотые жилы.";
        if (state.depth == 10) return "Алмазы. Теперь шахта официально перестала быть скромным хобби.";
        if (state.depth == 14) return "Обсидиан регенерирует. Его нельзя бросать недобитым.";
        if (state.depth == 22) return "Древние кристаллы восстанавливаются почти сразу.";
        return "Порода становится прочнее, а неприятности изобретательнее.";
    }

    private void breakRock(Rock rock, Gnome miner) {
        rock.destroyed = true;
        rock.respawnDelay = 0.45f;
        miner.addCargo(rock.type.material, state.yieldFor(rock.type, miner.tier.ordinal()));
        state.rocksBroken++;
        state.depthProgress += 1 + rock.type.ordinal() / 2;
        addParticles(rock.x, rock.y, rock.type.color, 12, 90f);
    }

    private void loseGnome(Gnome g, String reason) {
        if (!gnomes.contains(g)) return;
        int tier = g.tier.ordinal();
        if (state.tierCounts[tier] > 0) state.tierCounts[tier]--;
        gnomes.remove(g);
        state.gnomesLost++;
        addParticles(g.x, g.y, 0xFFE7D8BE, 10, 80f);
        addMessage("Гном потерян", reason + ". Осталось: " + state.totalGnomes());
    }

    private void spawnRock() {
        if (worldRect.height() <= 0) return;
        RockType type = chooseRockType();
        float radius = (16f + random.nextFloat() * 11f + type.ordinal() * 1.4f) * ui;
        float x = rand(worldRect.left + radius + 8f * ui, worldRect.right - radius - 8f * ui);
        float y = rand(worldRect.top + radius + 8f * ui, worldRect.bottom - radius - 8f * ui);
        for (int attempt = 0; attempt < 12; attempt++) {
            boolean overlaps = distance(x, y, chestX, chestY) < radius + 65f * ui;
            for (Rock existing : rocks) {
                if (!existing.destroyed && distance(x, y, existing.x, existing.y) < radius + existing.radius + 5f * ui) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) break;
            x = rand(worldRect.left + radius, worldRect.right - radius);
            y = rand(worldRect.top + radius, worldRect.bottom - radius);
        }
        int points = 7;
        float[] angles = new float[points];
        float[] scales = new float[points];
        for (int i = 0; i < points; i++) {
            angles[i] = (float) (Math.PI * 2 * i / points + rand(-0.10f, 0.10f));
            scales[i] = rand(0.78f, 1.08f);
        }
        rocks.add(new Rock(type, x, y, radius, angles, scales));
    }

    private RockType chooseRockType() {
        float r = random.nextFloat();
        int d = state.depth;
        if (d >= 22 && r < 0.07f) return RockType.ANCIENT_CRYSTAL;
        if (d >= 14 && r < 0.16f) return RockType.OBSIDIAN;
        if (d >= 10 && r < 0.25f) return RockType.DIAMOND;
        if (d >= 6 && r < 0.42f) return RockType.GOLD;
        if (d >= 3 && r < 0.62f) return RockType.SILVER;
        return RockType.STONE;
    }

    private void spawnEnemyWave() {
        EnemyType type = chooseEnemyType();
        int count = type == EnemyType.IMP ? 2 + random.nextInt(3) : 1;
        for (int i = 0; i < count; i++) spawnEnemy(type);
        addMessage(type.title + (count > 1 ? " ×" + count : ""), "Гномы бросили камни и побежали разбираться.");
    }

    private EnemyType chooseEnemyType() {
        int d = state.depth;
        float r = random.nextFloat();
        if (d >= 18 && r < 0.12f) return EnemyType.FIRE_GOLEM;
        if (d >= 15 && r < 0.25f) return EnemyType.WATER_GOLEM;
        if (d >= 12 && r < 0.40f) return EnemyType.STONE_GOLEM;
        if (d >= 7 && r < 0.62f) return EnemyType.DEMON;
        return EnemyType.IMP;
    }

    private void spawnBossForDepth() {
        EnemyType boss;
        if (state.depth >= 30) boss = EnemyType.ELEMENTAL_KING;
        else if (state.depth >= 20) boss = EnemyType.DEMON_KING;
        else boss = EnemyType.IMP_KING;
        spawnEnemy(boss);
        addMessage("БОСС: " + boss.title, "Вся шахта прекращает добычу, пока эта проблема не перестанет двигаться.");
    }

    private void spawnEnemy(EnemyType type) {
        boolean fromLeft = random.nextBoolean();
        float x = fromLeft ? worldRect.left + 8f * ui : worldRect.right - 8f * ui;
        float y = rand(worldRect.top + 30f * ui, worldRect.bottom - 30f * ui);
        spawnEnemyAt(type, x, y);
    }

    private void spawnEnemyAt(EnemyType type, float x, float y) {
        Enemy e = new Enemy(type, x, y, random.nextFloat() * 6.28f);
        if (type.ordinal() >= EnemyType.IMP_KING.ordinal()) {
            float scale = 1f + Math.max(0, state.depth - 10) * 0.08f;
            e.maxHp *= scale;
            e.hp = e.maxHp;
        }
        enemies.add(e);
    }

    private void spawnHazard() {
        HazardType type = HazardType.values()[random.nextInt(HazardType.values().length)];
        float x = rand(worldRect.left + 45f * ui, worldRect.right - 45f * ui);
        float y = rand(worldRect.top + 45f * ui, worldRect.bottom - 45f * ui);
        float radius = (type == HazardType.FLOOD ? 90f : 45f + random.nextFloat() * 25f) * ui;
        float duration = switch (type) {
            case COLLAPSE -> 5.2f;
            case PIT -> 11f;
            case LAVA -> 12f;
            case FLOOD -> 5.5f;
        };
        hazards.add(new Hazard(type, x, y, radius, duration));
        addMessage("Опасность: " + type.title, "Красная зона предупреждает заранее. Иногда даже шахта проявляет больше такта, чем интерфейсы банков.");
    }

    private Gnome nearestGnome(float x, float y) {
        Gnome best = null;
        float bestD = Float.MAX_VALUE;
        for (Gnome g : gnomes) {
            float d = distanceSq(x, y, g.x, g.y);
            if (d < bestD) {
                bestD = d;
                best = g;
            }
        }
        return best;
    }

    private Enemy nearestEnemy(float x, float y) {
        Enemy best = null;
        float bestD = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (!e.isAlive()) continue;
            float d = distanceSq(x, y, e.x, e.y);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private Rock nearestRock(float x, float y) {
        Rock best = null;
        float bestD = Float.MAX_VALUE;
        for (Rock r : rocks) {
            if (r.destroyed) continue;
            float d = distanceSq(x, y, r.x, r.y);
            if (d < bestD) {
                bestD = d;
                best = r;
            }
        }
        return best;
    }

    private void addParticles(float x, float y, int color, int count, float speed) {
        for (int i = 0; i < count; i++) {
            float a = random.nextFloat() * 6.283f;
            float s = random.nextFloat() * speed * ui;
            particles.add(new Particle(x, y, (float) Math.cos(a) * s, (float) Math.sin(a) * s,
                    rand(0.25f, 0.7f), rand(1.3f, 3.6f) * ui, color));
        }
        if (particles.size() > 260) particles.subList(0, particles.size() - 260).clear();
    }

    private void addMessage(String title, String text) {
        message = title;
        subMessage = text;
        messageTimer = 3.8f;
    }

    private void drawTopHud(Draw gfx) {
        float w = width;
        gfx.fillRect(0, 0, w, worldTop);

        drawResource(gfx, 14f * ui, 24f * ui, 0xFF8B8F94, "КАМ", state.stone);
        drawResource(gfx, 104f * ui, 24f * ui, 0xFFC5CED8, "Ag", state.silver);
        drawResource(gfx, 184f * ui, 24f * ui, 0xFFE0B33E, "Au", state.gold);
        drawResource(gfx, 264f * ui, 24f * ui, 0xFF69DDF7, "◆", state.diamond);

        gfx.setColor(0xFFCBD3D9);
        gfx.textSize = 12f * ui;
        gfx.bold = true;
        gfx.text("ГЛУБИНА " + state.depth, 14f * ui, 67f * ui);
        gfx.bold = false;

        float progressW = 210f * ui;
        float progressX = 14f * ui;
        float progressY = 78f * ui;
        int threshold = 12 + state.depth * 2;
        float pct = Math.min(1f, state.depthProgress / (float) threshold);
        roundedRect(gfx, progressX, progressY, progressX + progressW, progressY + 12f * ui, 6f * ui, 0xFF0C0F12);
        roundedRect(gfx, progressX, progressY, progressX + progressW * pct, progressY + 12f * ui, 6f * ui, 0xFF72C26A);

        collectRect.set(width - 124f * ui, 58f * ui, width - 10f * ui, 101f * ui);
        long inTransit = cargoValueInTransit();
        roundedRect(gfx, collectRect.left, collectRect.top, collectRect.right, collectRect.bottom, 8f * ui,
                inTransit > 0 ? 0xFF8A6232 : 0xFF36404A);
        gfx.align = Draw.Align.CENTER;
        gfx.setColor(0xFFFFFFFF);
        gfx.textSize = 10.5f * ui;
        gfx.bold = true;
        gfx.text("В ПУТИ К СУНДУКУ", collectRect.centerX(), collectRect.top + 17f * ui);
        gfx.textSize = 11f * ui;
        gfx.bold = false;
        gfx.text(formatNumber(inTransit), collectRect.centerX(), collectRect.top + 33f * ui);
        gfx.align = Draw.Align.LEFT;
    }

    private void drawResource(Draw gfx, float x, float y, int color, String label, long value) {
        gfx.setColor(color);
        gfx.fillCircle(x + 8f * ui, y + 2f * ui, 7f * ui);
        gfx.setColor(0xFFE8ECEF);
        gfx.textSize = 10f * ui;
        gfx.bold = true;
        gfx.text(label, x, y + 26f * ui);
        gfx.textSize = 14f * ui;
        gfx.text(formatNumber(value), x + 19f * ui, y + 7f * ui);
        gfx.bold = false;
    }

    private void drawMine(Draw gfx) {
        gfx.save();
        gfx.clipRect(worldRect.left, worldRect.top, worldRect.right, worldRect.bottom);
        gfx.setColor(0xFF30302B);
        gfx.fillRect(worldRect.left, worldRect.top, worldRect.right, worldRect.bottom);

        drawCaveFloor(gfx);
        drawChest(gfx);
        for (Rock rock : rocks) if (!rock.destroyed) drawRock(gfx, rock);
        for (Hazard hazard : hazards) drawHazard(gfx, hazard);
        for (Enemy enemy : enemies) drawEnemy(gfx, enemy);
        for (Gnome gnome : gnomes) drawGnome(gfx, gnome);
        for (Particle particle : particles) drawParticle(gfx, particle);

        if (!enemies.isEmpty()) drawEnemyBanner(gfx);
        gfx.restore();
        gfx.unclip();
    }

    private void drawChest(Draw gfx) {
        float w = 66f * ui;
        float h = 38f * ui;

        // shadow
        gfx.setColor(0x66000000);
        gfx.fillOval(chestX - w * .62f, chestY + h * .30f, chestX + w * .62f, chestY + h * .64f);

        // chest body and lid
        gfx.setColor(0xFF6E4525);
        gfx.fillRoundRect(chestX - w * .50f, chestY - h * .18f, chestX + w * .50f, chestY + h * .42f, 5f * ui);
        gfx.setColor(0xFF8B5A2B);
        gfx.fillRoundRect(chestX - w * .53f, chestY - h * .48f, chestX + w * .53f, chestY + h * .02f, 9f * ui);
        gfx.setColor(0xFFD0A84D);
        gfx.fillRect(chestX - 4f * ui, chestY - h * .48f, chestX + 4f * ui, chestY + h * .42f);
        gfx.fillRect(chestX - w * .53f, chestY - 2f * ui, chestX + w * .53f, chestY + 3f * ui);
        gfx.setColor(0xFFFFD46A);
        gfx.fillCircle(chestX, chestY + 7f * ui, 4.5f * ui);
        gfx.setColor(0xFF3D2A18);
        gfx.fillCircle(chestX, chestY + 8f * ui, 1.5f * ui);

        // Label
        gfx.align = Draw.Align.CENTER;
        gfx.bold = true;
        gfx.textSize = 8f * ui;
        gfx.setColor(0xFFEBD9B6);
        gfx.text("СУНДУК", chestX, chestY - h * .66f);
        gfx.bold = false;

        int chestRune = state.infrastructureRunes[0];
        if (chestRune >= 0 && chestRune < RuneType.values().length && state.runeLevels[chestRune] > 0) {
            drawRuneGlyph(gfx, chestX + w * .58f, chestY - h * .36f, 5f * ui, RuneType.values()[chestRune]);
        }

        if (state.guardianLevel > 0) {
            drawChestGuardian(gfx, chestX - 48f * ui, chestY + 1f * ui);
        }

        float buttonW = 122f * ui;
        guardianRect.set(chestX - buttonW / 2f, chestY + 24f * ui, chestX + buttonW / 2f, chestY + 43f * ui);
        int buttonColor = state.guardianLevel > 0 ? 0xFF2B6E54 : 0xFF315E8C;
        roundedRect(gfx, guardianRect.left, guardianRect.top, guardianRect.right, guardianRect.bottom, 5f * ui, buttonColor);
        gfx.align = Draw.Align.CENTER;
        gfx.setColor(0xFFFFFFFF);
        gfx.textSize = 7.6f * ui;
        gfx.bold = true;
        String guardText = state.guardianLevel == 0
                ? "НАНЯТЬ СТРАЖА • " + state.guardianCostLabel()
                : "СТРАЖ ур." + state.guardianLevel + " ↑ • " + state.guardianCostLabel();
        gfx.text(guardText, guardianRect.centerX(), guardianRect.centerY() + 2.8f * ui);
        gfx.align = Draw.Align.LEFT;
        gfx.bold = false;
        hitRegions.add(new HitRegion(new Rect(guardianRect), Action.GUARDIAN_UPGRADE, -1));
    }

    private void drawChestGuardian(Draw gfx, float x, float y) {
        float s = 18f * ui;
        gfx.save();
        gfx.translate(x, y);
        gfx.setColor(0x55000000);
        gfx.fillOval(-s*.45f, s*.42f, s*.45f, s*.62f);
        gfx.setColor(0xFF435B6E);
        gfx.fillOval(-s*.30f, -s*.03f, s*.30f, s*.42f);
        gfx.setColor(0xFFE5B989);
        gfx.fillCircle(0, -s*.24f, s*.26f);
        gfx.setColor(0xFFECE8DE);
        gfx.pathReset();
        gfx.moveTo(-s*.24f, -s*.15f);
        gfx.lineTo(0, s*.31f);
        gfx.lineTo(s*.24f, -s*.15f);
        gfx.closePath();
        gfx.fillPath();
        gfx.setColor(0xFF708BA0);
        gfx.pathReset();
        gfx.moveTo(-s*.30f, -s*.38f);
        gfx.lineTo(0, -s*.75f);
        gfx.lineTo(s*.30f, -s*.38f);
        gfx.closePath();
        gfx.fillPath();
        // spear
        gfx.strokeWidth = 2.2f * ui;
        gfx.setColor(0xFF9B7445);
        gfx.line(s*.38f, -s*.40f, s*.38f, s*.60f);
        gfx.setColor(0xFFCED8DE);
        gfx.pathReset();
        gfx.moveTo(s*.38f, -s*.72f);
        gfx.lineTo(s*.25f, -s*.44f);
        gfx.lineTo(s*.51f, -s*.44f);
        gfx.closePath();
        gfx.fillPath();
        gfx.restore();

        int guardRune = state.infrastructureRunes[1];
        if (guardRune >= 0 && guardRune < RuneType.values().length && state.runeLevels[guardRune] > 0) {
            drawRuneGlyph(gfx, x - 12f * ui, y - 18f * ui, 4.5f * ui, RuneType.values()[guardRune]);
        }
    }

    private void drawCaveFloor(Draw gfx) {
        gfx.setColor(0x22222222);
        for (int i = 0; i < 22; i++) {
            float x = ((i * 97 + 31) % 421) * ui;
            float y = worldRect.top + (((i * 71 + 43) % 390) / 390f) * worldRect.height();
            gfx.fillOval(x - 14f * ui, y - 4f * ui, x + 18f * ui, y + 4f * ui);
        }

        // rough cliff edges
        gfx.setColor(0xFF111315);
        gfx.pathReset();
        gfx.moveTo(0, worldRect.top);
        gfx.lineTo(0, worldRect.bottom);
        gfx.lineTo(25f * ui, worldRect.bottom);
        for (int i = 7; i >= 0; i--) {
            float y = worldRect.top + i * worldRect.height() / 7f;
            gfx.lineTo((18f + ((i * 13) % 24)) * ui, y);
        }
        gfx.closePath();
        gfx.fillPath();

        gfx.pathReset();
        gfx.moveTo(width, worldRect.top);
        gfx.lineTo(width, worldRect.bottom);
        gfx.lineTo(width - 22f * ui, worldRect.bottom);
        for (int i = 7; i >= 0; i--) {
            float y = worldRect.top + i * worldRect.height() / 7f;
            gfx.lineTo(width - (17f + ((i * 17) % 25)) * ui, y);
        }
        gfx.closePath();
        gfx.fillPath();
    }

    private void drawRock(Draw gfx, Rock r) {
        float hpPct = Math.max(0, r.hp / r.maxHp);
        int base = adjustBrightness(r.type.color, 0.75f + 0.25f * hpPct);
        gfx.setColor(base);
        gfx.pathReset();
        for (int i = 0; i < r.polygonAngles.length; i++) {
            float px = r.x + (float) Math.cos(r.polygonAngles[i]) * r.radius * r.polygonScale[i];
            float py = r.y + (float) Math.sin(r.polygonAngles[i]) * r.radius * r.polygonScale[i];
            if (i == 0) gfx.moveTo(px, py); else gfx.lineTo(px, py);
        }
        gfx.closePath();
        gfx.fillPath();

        gfx.setColor(adjustBrightness(r.type.color, 1.25f));
        gfx.pathReset();
        gfx.moveTo(r.x - r.radius * 0.55f, r.y - r.radius * 0.12f);
        gfx.lineTo(r.x - r.radius * 0.08f, r.y - r.radius * 0.62f);
        gfx.lineTo(r.x + r.radius * 0.40f, r.y - r.radius * 0.20f);
        gfx.lineTo(r.x + r.radius * 0.10f, r.y + r.radius * 0.05f);
        gfx.closePath();
        gfx.fillPath();

        if (r.type == RockType.GOLD || r.type == RockType.SILVER || r.type == RockType.DIAMOND || r.type == RockType.ANCIENT_CRYSTAL) {
            gfx.setColor(adjustBrightness(r.type.color, 1.45f));
            for (int i = 0; i < 3; i++) {
                float a = i * 2.1f + r.x * 0.01f;
                gfx.fillCircle(r.x + (float)Math.cos(a) * r.radius * 0.38f,
                        r.y + (float)Math.sin(a) * r.radius * 0.38f, 2.2f * ui);
            }
        }

        if (hpPct < 0.72f) {
            gfx.setColor(0xFF252525);
            gfx.strokeWidth = 1.7f * ui;
            gfx.pathReset();
            gfx.moveTo(r.x - 3f * ui, r.y - r.radius * 0.6f);
            gfx.lineTo(r.x + 2f * ui, r.y - 6f * ui);
            gfx.lineTo(r.x - 4f * ui, r.y + 1f * ui);
            gfx.lineTo(r.x + 6f * ui, r.y + r.radius * 0.55f);
            gfx.strokePath();
        }

        if (r.type.regenPerSecond > 0) {
            gfx.strokeWidth = 1.5f * ui;
            gfx.setColor(0xAA9E74FF);
            gfx.fillCircle(r.x, r.y, r.radius + 4f * ui + (float)Math.sin(elapsed * 3f) * 2f * ui);
        }
    }

    private void drawHazard(Draw gfx, Hazard h) {
        float warningAlpha = h.age < 1.6f ? 0.35f + 0.25f * (float)Math.sin(h.age * 12f) : 0.65f;
        switch (h.type) {
            case COLLAPSE -> {
                gfx.setColor(withAlpha(0xFFD96C42, warningAlpha));
                gfx.fillCircle(h.x, h.y, h.radius);
                if (h.age >= 1.6f) {
                    gfx.setColor(0xFF706156);
                    for (int i = 0; i < 7; i++) {
                        float a = i * 0.9f;
                        float rr = h.radius * 0.45f;
                        gfx.fillCircle(h.x + (float)Math.cos(a)*rr, h.y + (float)Math.sin(a)*rr,
                                (8 + i % 3 * 3) * ui);
                    }
                }
            }
            case PIT -> {
                gfx.setColor(0xFF010101);
                gfx.fillCircle(h.x, h.y, h.radius);
                gfx.setColor(0xFF62594F);
                gfx.strokeWidth = 3f * ui;
                gfx.strokeCircle(h.x, h.y, h.radius);
            }
            case LAVA -> {
                gfx.setColor(withAlpha(0xFFFF5B19, warningAlpha));
                gfx.fillOval(h.x - h.radius, h.y - h.radius * 0.62f, h.x + h.radius, h.y + h.radius * 0.62f);
                gfx.setColor(0xFFFFC22E);
                for (int i = 0; i < 4; i++) {
                    float a = elapsed * (0.8f + i * 0.12f) + i;
                    gfx.fillCircle(h.x + (float)Math.cos(a) * h.radius * 0.55f,
                            h.y + (float)Math.sin(a * 1.4f) * h.radius * 0.3f, 4f * ui);
                }
            }
            case FLOOD -> {
                float yy = h.y + (h.age - 1.6f) * 55f * ui;
                gfx.setColor(withAlpha(0xFF4CAFE2, warningAlpha));
                gfx.fillRect(worldRect.left, yy - 24f * ui, worldRect.right, yy + 24f * ui);
                gfx.setColor(0xAAE0F7FF);
                for (int i = 0; i < 9; i++) {
                    float xx = (i * 53f * ui + elapsed * 70f * ui) % width;
                    gfx.fillCircle(xx, yy - 8f * ui + (i % 3) * 8f * ui, 3f * ui);
                }
            }
        }

        if (h.age < 1.6f) {
            gfx.align = Draw.Align.CENTER;
            gfx.bold = true;
            gfx.textSize = 11f * ui;
            gfx.setColor(0xFFFFFFFF);
            gfx.text("! " + h.type.title, h.x, h.y - h.radius - 7f * ui);
            gfx.bold = false;
            gfx.align = Draw.Align.LEFT;
        }
    }

    private void drawEnemy(Draw gfx, Enemy e) {
        float s = e.type.size * ui;
        float bob = (float)Math.sin(elapsed * 5f + e.phase) * 2f * ui;
        gfx.save();
        gfx.translate(e.x, e.y + bob);
        if (e.type == EnemyType.IMP || e.type == EnemyType.DEMON || e.type == EnemyType.IMP_KING || e.type == EnemyType.DEMON_KING) {
            drawDemon(gfx, e, s);
        } else {
            drawElemental(gfx, e, s);
        }
        gfx.restore();

        float hpPct = Math.max(0f, e.hp / e.maxHp);
        float barW = s * 1.6f;
        roundedRect(gfx, e.x - barW/2, e.y - s * 1.05f, e.x + barW/2, e.y - s * 0.90f, 2f * ui, 0xCC111111);
        roundedRect(gfx, e.x - barW/2, e.y - s * 1.05f, e.x - barW/2 + barW * hpPct, e.y - s * 0.90f, 2f * ui, 0xFFE44F45);
    }

    private void drawDemon(Draw gfx, Enemy e, float s) {
        gfx.setColor(e.type.color);
        gfx.fillCircle(0, -s * 0.10f, s * 0.42f);
        gfx.fillOval(-s*0.34f, s*0.08f, s*0.34f, s*0.70f);
        // horns
        gfx.setColor(0xFFE6D2A7);
        gfx.pathReset(); gfx.moveTo(-s*.30f,-s*.32f); gfx.lineTo(-s*.58f,-s*.62f); gfx.lineTo(-s*.10f,-s*.47f); gfx.closePath(); gfx.fillPath();
        gfx.pathReset(); gfx.moveTo(s*.30f,-s*.32f); gfx.lineTo(s*.58f,-s*.62f); gfx.lineTo(s*.10f,-s*.47f); gfx.closePath(); gfx.fillPath();
        // eyes
        gfx.setColor(0xFFFFE65D);
        gfx.fillCircle(-s*.13f,-s*.11f,s*.055f); gfx.fillCircle(s*.13f,-s*.11f,s*.055f);
        // legs
        gfx.strokeWidth = s*.12f;        gfx.line(-s*.16f,s*.60f,-s*.28f,s*.90f); gfx.line(s*.16f,s*.60f,s*.28f,s*.90f);
        if (e.type.ordinal() >= EnemyType.IMP_KING.ordinal()) {
            drawCrown(gfx, 0, -s*.60f, s*.7f);
        }
    }

    private void drawElemental(Draw gfx, Enemy e, float s) {
        int color = e.type.color;
        gfx.setColor(adjustBrightness(color, 0.75f));
        gfx.fillRoundRect(-s*.35f,-s*.18f,s*.35f,s*.62f, s*.12f);
        gfx.setColor(color);
        gfx.fillCircle(0,-s*.35f,s*.34f);
        gfx.fillRect(-s*.60f,-s*.05f,-s*.28f,s*.35f);
        gfx.fillRect(s*.28f,-s*.05f,s*.60f,s*.35f);
        gfx.setColor(0xFFFFFFFF);
        gfx.fillCircle(-s*.12f,-s*.37f,s*.055f); gfx.fillCircle(s*.12f,-s*.37f,s*.055f);
        if (e.type == EnemyType.FIRE_GOLEM) {
            gfx.setColor(0xFFFFC23A);
            gfx.pathReset(); gfx.moveTo(0,-s*.70f); gfx.lineTo(-s*.2f,-s*.38f); gfx.lineTo(0,-s*.45f); gfx.lineTo(s*.2f,-s*.38f); gfx.closePath(); gfx.fillPath();
        }
        if (e.type == EnemyType.ELEMENTAL_KING) drawCrown(gfx,0,-s*.78f,s*.75f);
    }

    private void drawCrown(Draw gfx, float x, float y, float w) {
        gfx.setColor(0xFFFFD74D);
        gfx.pathReset();
        gfx.moveTo(x-w*.45f,y+w*.22f); gfx.lineTo(x-w*.40f,y-w*.18f); gfx.lineTo(x-w*.15f,y+w*.02f);
        gfx.lineTo(x,y-w*.28f); gfx.lineTo(x+w*.15f,y+w*.02f); gfx.lineTo(x+w*.40f,y-w*.18f); gfx.lineTo(x+w*.45f,y+w*.22f);
        gfx.closePath(); gfx.fillPath();
    }

    private void drawGnome(Draw gfx, Gnome g) {
        float s = g.tier.size * ui;
        float moveAmount = Math.min(1f, len(g.vx, g.vy) / (30f * ui));
        float gait = (float)Math.sin(elapsed * 10f + g.phase) * moveAmount;
        float dir = g.vx < -1f ? -1f : 1f;
        gfx.save();
        gfx.translate(g.x, g.y);
        gfx.scale(dir, 1f);
        if (g.isStunned()) gfx.rotate((float)Math.sin(elapsed*13f+g.phase)*5f);

        switch (g.tier) {
            case MINER, VETERAN, TWIN_PICK -> drawWalkingGnome(gfx, g, s, gait);
            case DRILL_RIG -> drawDrillRig(gfx, g, s, gait);
            case EXCAVATOR -> drawExcavator(gfx, g, s, gait);
            case IRON_GOLEM -> drawIronGnome(gfx, g, s, gait);
        }
        gfx.restore();

        if (g.hasCargo()) drawCargo(gfx, g, s);

        int runeIndex = state.tierRunes[g.tier.ordinal()];
        if (runeIndex >= 0 && runeIndex < RuneType.values().length && state.runeLevels[runeIndex] > 0) {
            drawRuneGlyph(gfx, g.x + s * 0.55f, g.y - s * 0.68f, 5.5f * ui, RuneType.values()[runeIndex]);
        }

        if (g.isStunned()) {
            gfx.setColor(0xFFFFD44A);
            for (int i = 0; i < 3; i++) {
                float a = elapsed * 4f + i * 2.1f;
                gfx.fillCircle(g.x + (float)Math.cos(a)*s*.55f, g.y - s*.85f + (float)Math.sin(a)*s*.18f, 2f*ui);
            }
        }
    }

    private void drawCargo(Draw gfx, Gnome g, float s) {
        float capacity = g.tier.cargoCapacity * state.carryMultiplier(g.tier.ordinal());
        float pct = (float) Math.min(1.0, g.cargoLoad() / Math.max(1f, capacity));
        float x = g.x - s * 0.45f;
        float y = g.y + s * 0.18f;
        gfx.setColor(0xFF765132);
        gfx.fillCircle(x, y, Math.max(4f * ui, s * .18f));
        gfx.setColor(0xFFC8A269);
        gfx.fillRect(x - s*.13f, y - s*.21f, x + s*.13f, y - s*.11f);
        // tiny fullness bar
        float bw = Math.max(12f * ui, s * .55f);
        roundedRect(gfx, g.x - bw/2, g.y + s*.68f, g.x + bw/2, g.y + s*.74f, 2f*ui, 0xAA16191C);
        roundedRect(gfx, g.x - bw/2, g.y + s*.68f, g.x - bw/2 + bw*pct, g.y + s*.74f, 2f*ui, 0xFFD0A84D);
    }

    private void drawWalkingGnome(Draw gfx, Gnome g, float s, float gait) {
        // shadow
        gfx.setColor(0x55000000);
        gfx.fillOval(-s*.42f,s*.48f,s*.42f,s*.64f);
        // legs
        gfx.line(-s*.12f,s*.36f,-s*.20f+gait*s*.11f,s*.63f);
        gfx.line(s*.12f,s*.36f,s*.20f-gait*s*.11f,s*.63f);
        // body
        gfx.setColor(adjustBrightness(g.tier.color,0.72f));
        gfx.fillOval(-s*.31f,-s*.02f,s*.31f,s*.43f);
        // head
        gfx.setColor(0xFFE5B989);
        gfx.fillCircle(0,-s*.22f,s*.27f);
        // beard
        gfx.setColor(g.tier == GnomeTier.VETERAN ? 0xFFE4E1D7 : 0xFFF1EFE8);
        gfx.pathReset(); gfx.moveTo(-s*.25f,-s*.13f); gfx.quadTo(0,s*.40f,s*.27f,-s*.13f); gfx.quadTo(0,s*.16f,-s*.25f,-s*.13f); gfx.closePath(); gfx.fillPath();
        // nose
        gfx.setColor(0xFFF0C29A); gfx.fillCircle(s*.20f,-s*.21f,s*.07f);
        // eye
        gfx.setColor(0xFF171717); gfx.fillCircle(s*.15f,-s*.29f,s*.025f);
        // hat
        gfx.setColor(g.tier.color);
        gfx.pathReset(); gfx.moveTo(-s*.27f,-s*.40f); gfx.quadTo(0,-s*.91f,s*.19f,-s*.43f); gfx.lineTo(s*.31f,-s*.38f); gfx.closePath(); gfx.fillPath();
        // arms + pickaxe(s)
        drawPickaxe(gfx, s*.14f, -s*.05f, s, g.attackCooldown < 0.18f ? -28f : 12f);
        if (g.tier == GnomeTier.TWIN_PICK) drawPickaxe(gfx, -s*.13f, 0, s, g.attackCooldown < 0.18f ? 28f : -15f);
        if (g.tier == GnomeTier.VETERAN) {
            gfx.setColor(0xFFF6C356); gfx.fillRect(-s*.33f,-s*.02f,-s*.25f,s*.20f);
        }
    }

    private void drawPickaxe(Draw gfx, float x, float y, float s, float angle) {
        gfx.save(); gfx.translate(x,y); gfx.rotate(angle);
        gfx.setColor(0xFF7E5633); gfx.strokeWidth = s*.08f;        gfx.line(0,-s*.12f,s*.48f,s*.29f);
        gfx.setColor(0xFFCAD2D8); gfx.strokeWidth = s*.09f;
        gfx.line(-s*.09f,-s*.13f,s*.15f,-s*.13f);
    }

    private void drawDrillRig(Draw gfx, Gnome g, float s, float gait) {
        gfx.setColor(0x55000000); gfx.fillOval(-s*.52f,s*.42f,s*.54f,s*.64f);
        gfx.setColor(0xFF52463B); gfx.fillRoundRect(-s*.46f,s*.15f,s*.43f,s*.48f, s*.10f);
        gfx.setColor(g.tier.color); gfx.fillRoundRect(-s*.36f,-s*.12f,s*.32f,s*.31f, s*.08f);
        gfx.setColor(0xFFE6B887); gfx.fillCircle(-s*.10f,-s*.22f,s*.18f);
        gfx.setColor(0xFFF0EEE7); gfx.pathReset(); gfx.moveTo(-s*.25f,-s*.17f); gfx.lineTo(s*.08f,s*.13f); gfx.lineTo(s*.10f,-s*.08f); gfx.closePath(); gfx.fillPath();
        // drill
        gfx.setColor(0xFFB9C6CF);
        gfx.pathReset(); gfx.moveTo(s*.30f,-s*.02f); gfx.lineTo(s*.75f,s*.08f); gfx.lineTo(s*.30f,s*.18f); gfx.closePath(); gfx.fillPath();
        gfx.setColor(0xFF68747C); gfx.strokeWidth = 1.5f*ui;
        for(int i=0;i<4;i++) gfx.line(s*(.36f+i*.09f),s*.01f,s*(.36f+i*.09f),s*.15f);
    }

    private void drawExcavator(Draw gfx, Gnome g, float s, float gait) {
        gfx.setColor(0x55000000); gfx.fillOval(-s*.65f,s*.43f,s*.63f,s*.66f);
        gfx.setColor(0xFF3B3E40); gfx.fillRoundRect(-s*.55f,s*.20f,s*.48f,s*.52f, s*.12f);
        gfx.setColor(g.tier.color); gfx.fillRoundRect(-s*.42f,-s*.18f,s*.19f,s*.31f, s*.08f);
        gfx.setColor(0xFF8ED0E8); gfx.fillRect(-s*.28f,-s*.10f,s*.07f,s*.08f);
        gfx.setColor(0xFFE6B887); gfx.fillCircle(-s*.12f,-s*.05f,s*.12f);
        // articulated arm and bucket
        gfx.setColor(0xFFD9A83C); gfx.strokeWidth = s*.13f;        gfx.line(s*.10f,-s*.03f,s*.55f,-s*.43f); gfx.line(s*.55f,-s*.43f,s*.72f,s*.13f);
        gfx.setColor(0xFFC48E2C); gfx.pathReset(); gfx.moveTo(s*.57f,s*.10f); gfx.lineTo(s*.92f,s*.07f); gfx.lineTo(s*.76f,s*.38f); gfx.closePath(); gfx.fillPath();
    }

    private void drawIronGnome(Draw gfx, Gnome g, float s, float gait) {
        gfx.setColor(0x55000000); gfx.fillOval(-s*.55f,s*.43f,s*.55f,s*.68f);
        gfx.setColor(0xFF7D8992); gfx.fillRoundRect(-s*.36f,-s*.08f,s*.36f,s*.48f, s*.10f);
        gfx.setColor(0xFFB7C5D1); gfx.fillCircle(0,-s*.33f,s*.30f);
        gfx.setColor(0xFF28323A); gfx.fillRect(-s*.20f,-s*.42f,s*.20f,-s*.33f);
        gfx.setColor(0xFF68E8FF); gfx.fillCircle(-s*.10f,-s*.375f,s*.035f); gfx.fillCircle(s*.10f,-s*.375f,s*.035f);
        // metal beard plates
        gfx.setColor(0xFFDDE5EA); gfx.pathReset(); gfx.moveTo(-s*.23f,-s*.20f); gfx.lineTo(0,s*.22f); gfx.lineTo(s*.23f,-s*.20f); gfx.lineTo(s*.10f,s*.18f); gfx.lineTo(-s*.10f,s*.18f); gfx.closePath(); gfx.fillPath();
        gfx.setColor(0xFF6B747B); gfx.strokeWidth = s*.18f;        gfx.line(-s*.34f,s*.02f,-s*.56f,s*.34f); gfx.line(s*.34f,s*.02f,s*.56f,s*.34f);
        gfx.line(-s*.18f,s*.42f,-s*.24f,s*.67f); gfx.line(s*.18f,s*.42f,s*.24f,s*.67f);
        drawPickaxe(gfx,s*.48f,s*.24f,s, -15f);
    }

    private void drawParticle(Draw gfx, Particle p) {
        float a = Math.max(0, p.life / p.maxLife);
        gfx.setColor(withAlpha(p.color, a));
        gfx.fillCircle(p.x, p.y, p.size * (0.5f + a * 0.5f));
    }

    private void drawEnemyBanner(Draw gfx) {
        Enemy strongest = enemies.get(0);
        for (Enemy e : enemies) if (e.type.ordinal() > strongest.type.ordinal()) strongest = e;
        float w = 180f * ui;
        float x = (width - w) / 2f;
        roundedRect(gfx, x, worldTop + 8f * ui, x + w, worldTop + 35f * ui, 8f * ui, 0xCC6C2020);
        gfx.align = Draw.Align.CENTER;
        gfx.setColor(0xFFFFFFFF); gfx.bold = true; gfx.textSize = 11f*ui;
        gfx.text(strongest.type.title.toUpperCase(Locale.ROOT) + "  •  " + enemies.size(), x+w/2, worldTop+26f*ui);
        gfx.align = Draw.Align.LEFT; gfx.bold = false;
    }

    private void drawBottomPanel(Draw gfx) {
        float top = worldBottom;
        gfx.fillRect(0, top, width, height);

        float tabH = 42f * ui;
        String[] tabs = {"ГНОМЫ", "АПГРЕЙДЫ", "АРТЕФ.", "РУНЫ"};
        for (int i=0;i<tabs.length;i++) {
            float left=i*width/(float)tabs.length, right=(i+1)*width/(float)tabs.length;
            if (selectedTab==i) roundedRect(gfx,left+3f*ui,top+3f*ui,right-3f*ui,top+tabH,5f*ui,0xFF303842);
            gfx.align = Draw.Align.CENTER; gfx.textSize = 11f*ui; gfx.bold = selectedTab==i;
            gfx.setColor(selectedTab==i?0xFFF3F5F7:0xFFB6BEC5);
            gfx.text(tabs[i],(left+right)/2,top+27f*ui);
            hitRegions.add(new HitRegion(rect(left,top,right,top+tabH), Action.TAB, i));
        }
        gfx.align = Draw.Align.LEFT; gfx.bold = false;

        float contentTop=top+tabH+4f*ui;
        float contentBottom=height-72f*ui;
        if (selectedTab==TAB_GNOMES) drawGnomeCards(gfx,contentTop,contentBottom);
        else if (selectedTab==TAB_UPGRADES) drawUpgrades(gfx,contentTop,contentBottom);
        else if (selectedTab==TAB_ARTIFACTS) drawArtifacts(gfx,contentTop,contentBottom);
        else drawRunes(gfx,contentTop,contentBottom);

        speedRect.set(8f*ui,height-65f*ui,width-8f*ui,height-8f*ui);
        int speedColor=speedHeld?0xFF3480F0:0xFF1764D7;
        roundedRect(gfx,speedRect.left,speedRect.top,speedRect.right,speedRect.bottom,9f*ui,speedColor);
        gfx.align = Draw.Align.CENTER; gfx.setColor(0xFFFFFFFF); gfx.bold = true; gfx.textSize = 18f*ui;
        gfx.text(speedHeld?"УСКОРЕНИЕ ×4":"УДЕРЖИВАЙ: УСКОРИТЬ ×4",speedRect.centerX(),speedRect.centerY()+6f*ui);
        gfx.align = Draw.Align.LEFT; gfx.bold = false;
        hitRegions.add(new HitRegion(new Rect(speedRect), Action.SPEED, -1));
    }

    private void drawGnomeCards(Draw gfx,float top,float bottom) {
        float gap=5f*ui;
        float cardW=(width-gap*4)/3f;
        int start=gnomePage*3;
        for(int slot=0;slot<3;slot++) {
            int tier=start+slot;
            if(tier>=GnomeTier.values().length) continue;
            float left=gap+slot*(cardW+gap);
            Rect card=rect(left,top,left+cardW,bottom);
            drawGnomeCard(gfx,card,tier);
        }
        gfx.align = Draw.Align.CENTER; gfx.textSize = 8f*ui; gfx.setColor(0xFFAAB3BA);
        gfx.text(gnomePage==0?"●  ○   свайп →":"○  ●   ← свайп",width/2f,bottom-2f*ui);
        gfx.align = Draw.Align.LEFT;
    }

    private void drawGnomeCard(Draw gfx,Rect card,int tier) {
        GnomeTier gt=GnomeTier.values()[tier];
        roundedRect(gfx,card.left,card.top,card.right,card.bottom-8f*ui,6f*ui,0xFF20262B);
        gfx.align = Draw.Align.CENTER; gfx.setColor(gt.color); gfx.textSize = 10.5f*ui; gfx.bold = true;
        gfx.text(gt.title,card.centerX(),card.top+17f*ui);
        gfx.setColor(0xFFEEF1F3); gfx.textSize = 9f*ui; gfx.bold = false;
        gfx.text("×"+state.tierCounts[tier]+"  ур. "+state.tierLevels[tier],card.centerX(),card.top+31f*ui);

        gfx.save(); gfx.translate(card.centerX(),card.top+66f*ui); gfx.scale(.80f,.80f);
        Gnome fake=previewGnomes[tier];
        switch(gt){
            case MINER,VETERAN,TWIN_PICK->drawWalkingGnome(gfx,fake,30f*ui,0);
            case DRILL_RIG->drawDrillRig(gfx,fake,30f*ui,0);
            case EXCAVATOR->drawExcavator(gfx,fake,30f*ui,0);
            case IRON_GOLEM->drawIronGnome(gfx,fake,30f*ui,0);
        }
        gfx.restore();
        int equippedRune=state.tierRunes[tier];
        if(equippedRune>=0 && equippedRune<RuneType.values().length && state.runeLevels[equippedRune]>0){
            drawRuneGlyph(gfx,card.right-13f*ui,card.top+46f*ui,6f*ui,RuneType.values()[equippedRune]);
        }

        float power=gt.miningPower*state.tierPowerMultiplier(tier)*state.miningMultiplier(tier);
        float cargo=gt.cargoCapacity*state.carryMultiplier(tier);
        gfx.setColor(0xFFBFC8CE); gfx.textSize = 8.0f*ui;
        gfx.text("⛏ "+formatFloat(power)+" • груз "+formatFloat(cargo),card.centerX(),card.top+106f*ui);

        float buttonTop=card.bottom-57f*ui;
        Rect action=rect(card.left+4f*ui,buttonTop,card.right-4f*ui,buttonTop+24f*ui);
        String actionText;
        boolean enabled;
        if(tier<GnomeTier.values().length-1 && state.tierCounts[tier]>=10){ actionText="СЛИТЬ 10 → 1"; enabled=true; }
        else if(tier==0){ long cost=state.minerBuyCost(); actionText="+ ГНОМ  "+formatNumber(cost); enabled=state.stone>=cost; }
        else { actionText="НУЖНО 10"; enabled=false; }
        roundedRect(gfx,action.left,action.top,action.right,action.bottom,4f*ui,enabled?0xFF2172D9:0xFF3A4147);
        gfx.setColor(enabled?0xFFFFFFFF:0xFF8E969C); gfx.textSize = 8.5f*ui; gfx.bold = true;
        gfx.text(actionText,action.centerX(),action.centerY()+3f*ui);
        if(enabled) hitRegions.add(new HitRegion(action, tier==0 && state.tierCounts[tier]<10 ? Action.BUY_MINER:Action.MERGE,tier));

        Rect level=rect(card.left+4f*ui,buttonTop+28f*ui,card.right-4f*ui,buttonTop+52f*ui);
        long levelCost=state.tierUpgradeCost(tier);
        String currency=tier<2?"кам":tier<4?"Ag":"Au";
        long displayCost=tier<2?levelCost:tier<4?Math.max(1,levelCost/90):Math.max(1,levelCost/180);
        roundedRect(gfx,level.left,level.top,level.right,level.bottom,4f*ui,0xFF2C9B55);
        gfx.setColor(0xFFFFFFFF); gfx.textSize = 8.0f*ui;
        gfx.text("УСИЛИТЬ  "+formatNumber(displayCost)+" "+currency,level.centerX(),level.centerY()+3f*ui);
        hitRegions.add(new HitRegion(level,Action.UPGRADE_TIER,tier));
        gfx.align = Draw.Align.LEFT; gfx.bold = false;
    }

    private void drawUpgrades(Draw gfx,float top,float bottom) {
        String[] titles={"Кирки","Логистика","Боевая подготовка"};
        String[] desc={"+20% ко всей добыче","+10% скорость, +12% груз","+20% к урону врагам"};
        int[] levels={state.miningUpgrade,state.speedUpgrade,state.combatUpgrade};
        int[] colors={0xFFDAA847,0xFF55B7DB,0xFFD9574E};
        float gap=7f*ui, h=(bottom-top-gap*4)/3f;
        for(int i=0;i<3;i++){
            float y=top+gap+i*(h+gap);
            Rect r=rect(8f*ui,y,width-8f*ui,y+h);
            roundedRect(gfx,r.left,r.top,r.right,r.bottom,7f*ui,0xFF20262B);
            gfx.setColor(colors[i]); gfx.fillCircle(r.left+22f*ui,r.centerY(),12f*ui);
            gfx.setColor(0xFFF1F3F4); gfx.textSize = 11f*ui; gfx.bold = true; gfx.text(titles[i],r.left+43f*ui,r.top+18f*ui);
            gfx.bold = false; gfx.setColor(0xFFADB7BE); gfx.textSize = 8.5f*ui; gfx.text(desc[i],r.left+43f*ui,r.top+33f*ui);
            gfx.setColor(0xFFDCE2E6); gfx.text("ур. "+levels[i],r.left+43f*ui,r.top+47f*ui);
            int upgradeRune=state.upgradeRunes[i];
            if(upgradeRune>=0 && upgradeRune<RuneType.values().length && state.runeLevels[upgradeRune]>0)
                drawRuneGlyph(gfx,r.left+23f*ui,r.bottom-16f*ui,5f*ui,RuneType.values()[upgradeRune]);
            long raw=state.globalUpgradeCost(i); long cost=i==0?raw:i==1?Math.max(2,raw/100):Math.max(1,raw/180);
            String curr=i==0?"кам":i==1?"Ag":"Au";
            Rect buy=rect(r.right-103f*ui,r.top+10f*ui,r.right-8f*ui,r.bottom-10f*ui);
            roundedRect(gfx,buy.left,buy.top,buy.right,buy.bottom,5f*ui,0xFF226CD0);
            gfx.align = Draw.Align.CENTER; gfx.setColor(0xFFFFFFFF); gfx.bold = true; gfx.textSize = 9f*ui; gfx.text("УЛУЧШИТЬ",buy.centerX(),buy.centerY()-2f*ui);
            gfx.textSize = 8f*ui; gfx.bold = false; gfx.text(formatNumber(cost)+" "+curr,buy.centerX(),buy.centerY()+11f*ui); gfx.align = Draw.Align.LEFT;
            hitRegions.add(new HitRegion(buy,Action.GLOBAL_UPGRADE,i));
        }
    }

    private void drawArtifacts(Draw gfx,float top,float bottom) {
        ArtifactType[] arr=ArtifactType.values();
        float gap=7f*ui; float cardW=(width-gap*3)/2f; float cardH=(bottom-top-gap*3)/2f;
        for(int i=0;i<arr.length;i++){
            int row=i/2,col=i%2;
            Rect r=rect(gap+col*(cardW+gap),top+gap+row*(cardH+gap),gap+col*(cardW+gap)+cardW,top+gap+row*(cardH+gap)+cardH);
            ArtifactType a=arr[i];
            roundedRect(gfx,r.left,r.top,r.right,r.bottom,7f*ui,0xFF20262B);
            gfx.setColor(a.color); gfx.fillCircle(r.left+23f*ui,r.top+24f*ui,12f*ui);
            gfx.setColor(0xFFF1F3F4); gfx.textSize = 9.5f*ui; gfx.bold = true; gfx.text(a.title,r.left+42f*ui,r.top+21f*ui);
            gfx.setColor(0xFFB5BEC4); gfx.textSize = 7.5f*ui; gfx.bold = false; gfx.text(a.description,r.left+10f*ui,r.top+45f*ui);
            gfx.setColor(0xFFDCE2E6); gfx.text("ур. "+state.artifactLevels[i],r.left+10f*ui,r.top+60f*ui);
            int artifactRune=state.artifactRunes[i];
            if(artifactRune>=0 && artifactRune<RuneType.values().length && state.runeLevels[artifactRune]>0)
                drawRuneGlyph(gfx,r.right-16f*ui,r.top+56f*ui,5f*ui,RuneType.values()[artifactRune]);
            int cost=state.artifactCost(i);
            Rect buy=rect(r.left+8f*ui,r.bottom-28f*ui,r.right-8f*ui,r.bottom-6f*ui);
            roundedRect(gfx,buy.left,buy.top,buy.right,buy.bottom,4f*ui,0xFF6E4FC4);
            gfx.align = Draw.Align.CENTER; gfx.setColor(0xFFFFFFFF); gfx.textSize = 8f*ui; gfx.bold = true; gfx.text("УСИЛИТЬ  ◆ "+cost,buy.centerX(),buy.centerY()+3f*ui); gfx.align = Draw.Align.LEFT; gfx.bold = false;
            hitRegions.add(new HitRegion(buy,Action.ARTIFACT_UPGRADE,i));
        }
    }

    private void drawRunes(Draw gfx,float top,float bottom) {
        float selectorTop=top+4f*ui;
        float selectorBottom=selectorTop+34f*ui;
        Rect prev=rect(8f*ui,selectorTop,42f*ui,selectorBottom);
        Rect next=rect(width-42f*ui,selectorTop,width-8f*ui,selectorBottom);
        roundedRect(gfx,prev.left,prev.top,prev.right,prev.bottom,6f*ui,0xFF303840);
        roundedRect(gfx,next.left,next.top,next.right,next.bottom,6f*ui,0xFF303840);
        gfx.align = Draw.Align.CENTER; gfx.bold = true; gfx.textSize = 18f*ui; gfx.setColor(0xFFDDE4E8);
        gfx.text("‹",prev.centerX(),prev.centerY()+6f*ui); gfx.text("›",next.centerX(),next.centerY()+6f*ui);
        hitRegions.add(new HitRegion(prev,Action.RUNE_TARGET_PREV,-1)); hitRegions.add(new HitRegion(next,Action.RUNE_TARGET_NEXT,-1));

        int equipped=state.runeAtTarget(runeTarget);
        String target=state.runeTargetTitle(runeTarget);
        gfx.textSize = 9.5f*ui; gfx.setColor(0xFFF1F3F4);
        gfx.text(ellipsize(target,28),width/2f,selectorTop+13f*ui);
        gfx.bold = false; gfx.textSize = 7.5f*ui;
        if(equipped>=0 && equipped<RuneType.values().length){
            gfx.setColor(RuneType.values()[equipped].color);
            gfx.text("нанесена: "+RuneType.values()[equipped].title,width/2f,selectorTop+27f*ui);
        } else {
            gfx.setColor(0xFF9EA8AF); gfx.text("слот пуст",width/2f,selectorTop+27f*ui);
        }

        float cardsTop=selectorBottom+5f*ui;
        float cardsBottom=bottom-8f*ui;
        float gap=5f*ui;
        float cardW=(width-gap*4)/3f;
        int start=runePage*3;
        for(int slot=0;slot<3;slot++){
            int runeIndex=start+slot;
            if(runeIndex>=RuneType.values().length) continue;
            float left=gap+slot*(cardW+gap);
            drawRuneCard(gfx,rect(left,cardsTop,left+cardW,cardsBottom),runeIndex);
        }
        gfx.align = Draw.Align.CENTER; gfx.textSize = 7.5f*ui; gfx.setColor(0xFFAAB3BA);
        gfx.text(runePage==0?"●  ○   свайп →":"○  ●   ← свайп",width/2f,bottom-1f*ui);
        gfx.align = Draw.Align.LEFT; gfx.bold = false;
    }

    private void drawRuneCard(Draw gfx,Rect card,int runeIndex){
        RuneType rune=RuneType.values()[runeIndex];
        boolean equipped=state.runeAtTarget(runeTarget)==runeIndex;
        roundedRect(gfx,card.left,card.top,card.right,card.bottom,6f*ui,equipped?0xFF2C3040:0xFF20262B);
        drawRuneGlyph(gfx,card.centerX(),card.top+20f*ui,10f*ui,rune);
        gfx.align = Draw.Align.CENTER; gfx.bold = true; gfx.setColor(rune.color); gfx.textSize = 8.8f*ui;
        gfx.text(rune.title,card.centerX(),card.top+39f*ui);
        gfx.bold = false; gfx.setColor(0xFFB5BEC4); gfx.textSize = 7.1f*ui;
        gfx.text(rune.description,card.centerX(),card.top+51f*ui);
        int lvl=state.runeLevels[runeIndex];
        int pct=Math.round(rune.effectPerLevel*lvl*100f);
        gfx.setColor(0xFFDCE2E6); gfx.textSize = 7.4f*ui;
        gfx.text("ур. "+lvl+(lvl>0?"  •  +"+pct+"%":""),card.centerX(),card.top+63f*ui);

        Rect engrave=rect(card.left+4f*ui,card.bottom-51f*ui,card.right-4f*ui,card.bottom-29f*ui);
        int engraveColor=lvl>0?(equipped?0xFF6952A8:0xFF356A8D):0xFF3A4147;
        roundedRect(gfx,engrave.left,engrave.top,engrave.right,engrave.bottom,4f*ui,engraveColor);
        gfx.setColor(lvl>0?0xFFFFFFFF:0xFF8E969C); gfx.bold = true; gfx.textSize = 7.7f*ui;
        gfx.text(lvl<=0?"НЕ СОЗДАНА":equipped?"СНЯТЬ":"НАНЕСТИ",engrave.centerX(),engrave.centerY()+3f*ui);
        if(lvl>0) hitRegions.add(new HitRegion(engrave,Action.RUNE_ENGRAVE,runeIndex));

        Rect upgrade=rect(card.left+4f*ui,card.bottom-25f*ui,card.right-4f*ui,card.bottom-4f*ui);
        int cost=state.runeUpgradeCost(runeIndex);
        roundedRect(gfx,upgrade.left,upgrade.top,upgrade.right,upgrade.bottom,4f*ui,0xFF6E4FC4);
        gfx.setColor(0xFFFFFFFF); gfx.textSize = 7.2f*ui; gfx.bold = false;
        gfx.text((lvl==0?"СОЗДАТЬ":"УСИЛИТЬ")+"  ◆ "+cost,upgrade.centerX(),upgrade.centerY()+3f*ui);
        hitRegions.add(new HitRegion(upgrade,Action.RUNE_UPGRADE,runeIndex));
        gfx.align = Draw.Align.LEFT; gfx.bold = false;
    }

    private void drawRuneGlyph(Draw gfx,float x,float y,float size,RuneType rune){
        gfx.setColor(withAlpha(rune.color,0.22f)); gfx.fillCircle(x,y,size*1.45f);
        gfx.setColor(rune.color);
        gfx.pathReset(); gfx.moveTo(x,y-size); gfx.lineTo(x+size*.72f,y); gfx.lineTo(x,y+size); gfx.lineTo(x-size*.72f,y); gfx.closePath(); gfx.fillPath();
        gfx.setColor(0xEEFFFFFF); gfx.strokeWidth = Math.max(1f,size*.12f);
        gfx.pathReset();
        switch(rune){
            case MINING -> { gfx.moveTo(x-size*.32f,y+size*.25f); gfx.lineTo(x+size*.32f,y-size*.25f); gfx.moveTo(x-size*.12f,y-size*.36f); gfx.lineTo(x+size*.36f,y-size*.12f); }
            case GREED -> { gfx.strokeCircle(x,y,size*.28f); gfx.moveTo(x-size*.35f,y); gfx.lineTo(x+size*.35f,y); }
            case WAR -> { gfx.moveTo(x-size*.30f,y-size*.35f); gfx.lineTo(x+size*.30f,y+size*.35f); gfx.moveTo(x+size*.30f,y-size*.35f); gfx.lineTo(x-size*.30f,y+size*.35f); }
            case HASTE -> { gfx.moveTo(x-size*.36f,y-size*.20f); gfx.lineTo(x+size*.05f,y-size*.20f); gfx.lineTo(x-size*.08f,y+size*.02f); gfx.lineTo(x+size*.36f,y+size*.02f); gfx.moveTo(x-size*.28f,y+size*.25f); gfx.lineTo(x+size*.20f,y+size*.25f); }
            case WARD -> { gfx.moveTo(x,y-size*.40f); gfx.lineTo(x+size*.30f,y-size*.20f); gfx.lineTo(x+size*.22f,y+size*.24f); gfx.lineTo(x,y+size*.42f); gfx.lineTo(x-size*.22f,y+size*.24f); gfx.lineTo(x-size*.30f,y-size*.20f); gfx.closePath(); }
            case FRACTURE -> { gfx.moveTo(x-size*.10f,y-size*.42f); gfx.lineTo(x+size*.06f,y-size*.06f); gfx.lineTo(x-size*.10f,y+size*.08f); gfx.lineTo(x+size*.15f,y+size*.42f); gfx.moveTo(x-size*.34f,y+size*.10f); gfx.lineTo(x-size*.10f,y+size*.08f); gfx.moveTo(x+size*.06f,y-size*.06f); gfx.lineTo(x+size*.34f,y-size*.20f); }
        }
        gfx.strokePath();
    }

    private void drawMessage(Draw gfx) {
        float alpha=Math.min(1f,messageTimer<0.5f?messageTimer/0.5f:1f);
        float w=Math.min(width-24f*ui,330f*ui),h=56f*ui,x=(width-w)/2f,y=worldTop+46f*ui;
        roundedRect(gfx,x,y,x+w,y+h,8f*ui,withAlpha(0xFF101417,0.92f*alpha));
        gfx.align = Draw.Align.CENTER; gfx.setColor(withAlpha(0xFFFFFFFF,alpha)); gfx.bold = true; gfx.textSize = 11f*ui; gfx.text(message,x+w/2,y+19f*ui);
        gfx.bold = false; gfx.setColor(withAlpha(0xFFD0D6DA,alpha)); gfx.textSize = 7.5f*ui;
        gfx.text(ellipsize(subMessage,48),x+w/2,y+36f*ui);
        gfx.align = Draw.Align.LEFT;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        float x = screenX, y = screenY;
        touchDownX = x; touchDownY = y; touchMoved = false;
        if (speedRect.contains(x, y)) { speedHeld = true; return true; }
        return true;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        float x = screenX, y = screenY;
        if (Math.abs(x - touchDownX) > 12f * ui || Math.abs(y - touchDownY) > 12f * ui) touchMoved = true;
        if (speedHeld && !speedRect.contains(x, y)) speedHeld = false;
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        float x = screenX, y = screenY;
        if (speedHeld) { speedHeld = false; return true; }
        float dx = x - touchDownX;
        if (selectedTab == TAB_GNOMES && touchDownY > worldBottom + 40f * ui && touchDownY < height - 72f * ui && Math.abs(dx) > 45f * ui) {
            gnomePage = dx < 0 ? 1 : 0; return true;
        }
        if (selectedTab == TAB_RUNES && touchDownY > worldBottom + 74f * ui && touchDownY < height - 72f * ui && Math.abs(dx) > 45f * ui) {
            runePage = dx < 0 ? 1 : 0; return true;
        }
        if (!touchMoved || Math.abs(dx) < 18f * ui) performHit(x, y);
        return true;
    }

    private void performHit(float x,float y) {
        for(int i=hitRegions.size()-1;i>=0;i--){
            HitRegion hr=hitRegions.get(i);
            if(!hr.rect.contains(x,y)) continue;
            switch(hr.action){
                case TAB -> selectedTab=hr.arg;
                case BUY_MINER -> { if(state.buyMiner()){ syncVisualGnomeCounts(); addMessage("Новый гном","Ещё одна каска в шахте. Производительность растёт, техника безопасности делает вид, что тоже."); } else addMessage("Не хватает камня","Сначала расковыряй ещё немного породы."); }
                case MERGE -> { if(state.mergeTier(hr.arg)){ syncVisualGnomeCounts(); addMessage("ЭВОЛЮЦИЯ","10 гномов превратились в одного более опасного для геологии."); } }
                case UPGRADE_TIER -> { if(state.upgradeTier(hr.arg)) addMessage("Гном усилен",GnomeTier.values()[hr.arg].title+" теперь копает мощнее."); else addMessage("Не хватает ресурсов","Улучшение пока слишком дорого."); }
                case GLOBAL_UPGRADE -> { if(state.buyGlobalUpgrade(hr.arg)) addMessage("Улучшение куплено","Эффект действует на всю шахту."); else addMessage("Не хватает ресурсов","Шахта требует капиталовложений. Какая неожиданность."); }
                case ARTIFACT_UPGRADE -> { if(state.upgradeArtifact(hr.arg)) addMessage("Артефакт пробуждён",ArtifactType.values()[hr.arg].title+" теперь действует сильнее."); else addMessage("Нужны алмазы","Артефакты, как назло, не принимают обещания и энтузиазм."); }
                case RUNE_TARGET_PREV -> runeTarget=(runeTarget-1+state.runeTargetCount())%state.runeTargetCount();
                case RUNE_TARGET_NEXT -> runeTarget=(runeTarget+1)%state.runeTargetCount();
                case RUNE_ENGRAVE -> {
                    int before=state.runeAtTarget(runeTarget);
                    if(state.engraveRune(runeTarget,hr.arg)) {
                        String action=before==hr.arg?"Руна снята":"Руна нанесена";
                        addMessage(action,RuneType.values()[hr.arg].title+" • "+state.runeTargetTitle(runeTarget));
                    } else addMessage("Руна не создана","Сначала усили её хотя бы до первого уровня.");
                }
                case RUNE_UPGRADE -> {
                    if(state.upgradeRune(hr.arg)) addMessage("Руна усилена",RuneType.values()[hr.arg].title+" теперь ур. "+state.runeLevels[hr.arg]);
                    else addMessage("Нужны алмазы","Для усиления руны нужны алмазы.");
                }
                case GUARDIAN_UPGRADE -> {
                    if (state.buyOrUpgradeGuardian()) {
                        addMessage(state.guardianLevel == 1 ? "Страж нанят" : "Страж усилен",
                                "Защита сундука: ур. " + state.guardianLevel + ".");
                    } else {
                        addMessage("Не хватает ресурсов", "На стража пока не хватает.");
                    }
                }
                case SPEED -> { }
            }
            state.save();
            
            return;
        }
    }

    private long cargoValueInTransit() {
        double value = 0;
        for (Gnome g : gnomes) {
            value += g.cargoStone + g.cargoSilver * 8.0 + g.cargoGold * 20.0 + g.cargoDiamond * 100.0;
        }
        return Math.max(0L, Math.round(value));
    }

    private void clampGnome(Gnome g) {
        float pad=g.tier.size*.45f*ui;
        g.x=Math.max(worldRect.left+pad,Math.min(worldRect.right-pad,g.x));
        g.y=Math.max(worldRect.top+pad,Math.min(worldRect.bottom-pad,g.y));
    }

    private void clampEnemy(Enemy e) {
        float pad=e.type.size*.5f*ui;
        e.x=Math.max(worldRect.left+pad,Math.min(worldRect.right-pad,e.x));
        e.y=Math.max(worldRect.top+pad,Math.min(worldRect.bottom-pad,e.y));
    }

    private clampIntoWorldHolder clamp(float x,float y,float size){
        return new clampIntoWorldHolder(Math.max(worldRect.left+size,Math.min(worldRect.right-size,x)),Math.max(worldRect.top+size,Math.min(worldRect.bottom-size,y)));
    }

    private void roundedRect(Draw gfx,float l,float t,float r,float b,float radius,int color){
        gfx.setColor(color);
        gfx.fillRoundRect(l, t, r, b, radius);
    }

    private float rand(float min,float max){ return min+random.nextFloat()*(max-min); }
    private static float len(float x,float y){ return (float)Math.sqrt(x*x+y*y); }
    private static float distance(float x1,float y1,float x2,float y2){ return len(x2-x1,y2-y1); }
    private static float distanceSq(float x1,float y1,float x2,float y2){ float dx=x2-x1,dy=y2-y1; return dx*dx+dy*dy; }

    private String formatFloat(float v){
        if(v>=1000) return formatNumber((long)v);
        if(v>=100) return String.valueOf(Math.round(v));
        return compact.format(v);
    }

    private String formatNumber(long n){
        if(n>=1_000_000_000L) return compact.format(n/1_000_000_000d)+"B";
        if(n>=1_000_000L) return compact.format(n/1_000_000d)+"M";
        if(n>=10_000L) return compact.format(n/1_000d)+"K";
        return String.valueOf(n);
    }

    private static int adjustBrightness(int color,float factor){
        int a=argbA(color); int r=Math.min(255,Math.max(0,(int)(argbR(color)*factor))); int g=Math.min(255,Math.max(0,(int)(argbG(color)*factor))); int b=Math.min(255,Math.max(0,(int)(argbB(color)*factor))); return (a<<24)|(r<<16)|(g<<8)|b;
    }
    private static int withAlpha(int color,float alpha){ return (Math.max(0,Math.min(255,(int)(255*alpha)))<<24)|(argbR(color)<<16)|(argbG(color)<<8)|argbB(color); }
    private static String ellipsize(String s,int max){ return s.length()<=max?s:s.substring(0,max-1)+"…"; }

    private enum Action { TAB,BUY_MINER,MERGE,UPGRADE_TIER,GLOBAL_UPGRADE,ARTIFACT_UPGRADE,RUNE_TARGET_PREV,RUNE_TARGET_NEXT,RUNE_ENGRAVE,RUNE_UPGRADE,GUARDIAN_UPGRADE,SPEED }
    private static class HitRegion { final Rect rect; final Action action; final int arg; HitRegion(Rect rect,Action action,int arg){this.rect=new Rect(rect);this.action=action;this.arg=arg;} }
    private static int argbA(int c){ return (c>>>24)&0xff; }
    private static int argbR(int c){ return (c>>>16)&0xff; }
    private static int argbG(int c){ return (c>>>8)&0xff; }
    private static int argbB(int c){ return c&0xff; }

    private static class clampIntoWorldHolder { final float x,y; clampIntoWorldHolder(float x,float y){this.x=x;this.y=y;} }
}
