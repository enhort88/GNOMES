package com.enhort.gnomes.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small deterministic perfect-maze graph used by the mine. The mine is a graph first and a picture second:
 * workers and enemies really route through the carved tunnels instead of skating through solid stone.
 */
public final class CaveMap {
    public enum Style { BRANCHING, RING }

    public static final int N = 1;
    public static final int E = 2;
    public static final int S = 4;
    public static final int W = 8;

    public final int cols;
    public final int rows;
    public final int[][] openings;
    public final int startCol;
    public final int startRow;
    public final long seed;
    public final Style style;

    private static final int[] DIRS = {N, E, S, W};
    private final Random random;
    private final boolean[] blocked;
    private int revision;
    private final LinkedHashMap<Long, int[]> pathCache = new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) { return size() > 384; }
    };

    public CaveMap(int cols, int rows, long seed) {
        this.cols = Math.max(5, cols | 1);
        this.rows = Math.max(7, rows);
        this.seed = seed;
        this.style = ((seed >>> 5) & 3L) == 0L ? Style.RING : Style.BRANCHING;
        this.random = new Random(seed);
        this.openings = new int[this.rows][this.cols];
        this.blocked = new boolean[this.rows * this.cols];
        this.startCol = this.cols / 2;
        this.startRow = this.rows - 1;
        generate();
    }

    private void generate() {
        boolean[][] seen = new boolean[rows][cols];
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        int start = index(startCol, startRow);
        stack.push(start);
        seen[startRow][startCol] = true;

        int[] dirs = {N, E, S, W};
        while (!stack.isEmpty()) {
            int cur = stack.peek();
            int c = col(cur);
            int r = row(cur);
            shuffle(dirs);
            boolean moved = false;
            for (int dir : dirs) {
                int nc = c + dx(dir);
                int nr = r + dy(dir);
                if (!inside(nc, nr) || seen[nr][nc]) continue;
                connect(c, r, nc, nr, dir);
                seen[nr][nc] = true;
                stack.push(index(nc, nr));
                moved = true;
                break;
            }
            if (!moved) stack.pop();
        }

        // More loops are intentional: the chest must not sit behind one compulsory corridor.
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
        softenDeadEnds();
    }

    private void softenDeadEnds() {
        List<Integer> ends = new ArrayList<>();
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            if (c == startCol && r == startRow) continue;
            if (degree(c, r) == 1) ends.add(index(c, r));
        }
        Collections.shuffle(ends, new Random(seed ^ 0xD6E8FEB86659FD93L));
        int connectCount = Math.max(0, Math.round(ends.size() * .62f));
        for (int i = 0; i < connectCount; i++) {
            int cell = ends.get(i), c = col(cell), r = row(cell);
            if (degree(c, r) != 1) continue;
            int[] order = {N, E, S, W};
            shuffle(order);
            int bestDir = 0, bestScore = Integer.MIN_VALUE;
            for (int dir : order) {
                if ((openings[r][c] & dir) != 0) continue;
                int nc = c + dx(dir), nr = r + dy(dir);
                if (!inside(nc, nr)) continue;
                int score = degree(nc, nr) * 5;
                if (nc > 0 && nc < cols - 1 && nr > 0 && nr < rows - 1) score += 3;
                if (score > bestScore) { bestScore = score; bestDir = dir; }
            }
            if (bestDir != 0) connect(c, r, c + dx(bestDir), r + dy(bestDir), bestDir);
        }
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

    private void connect(int c, int r, int nc, int nr, int dir) {
        openings[r][c] |= dir;
        openings[nr][nc] |= opposite(dir);
    }

    public boolean inside(int c, int r) {
        return c >= 0 && c < cols && r >= 0 && r < rows;
    }

    public boolean connected(int c, int r, int dir) {
        return inside(c, r) && (openings[r][c] & dir) != 0;
    }

    public boolean blockCell(int cell) {
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
    }

    public boolean isBlocked(int cell) {
        return cell >= 0 && cell < blocked.length && blocked[cell];
    }

    public int degree(int c, int r) {
        return Integer.bitCount(openings[r][c]);
    }

    public List<Integer> deadEnds() {
        List<Integer> out = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (c == startCol && r == startRow) continue;
                if (degree(c, r) == 1) out.add(index(c, r));
            }
        }
        Collections.shuffle(out, new Random(seed ^ 0x6A09E667F3BCC909L));
        return out;
    }

    public List<Integer> outerCells() {
        List<Integer> out = new ArrayList<>();
        for (int c = 0; c < cols; c++) {
            out.add(index(c, 0));
            if (rows > 1) out.add(index(c, rows - 1));
        }
        for (int r = 1; r < rows - 1; r++) {
            out.add(index(0, r));
            if (cols > 1) out.add(index(cols - 1, r));
        }
        return out;
    }

    /** Normal worker path: rubble is solid. */
    public int[] path(int start, int goal) { return pathInternal(start, goal, false, false); }

    /** Imps use this: a cave-in never blocks their route to the chest. */
    public int[] pathIgnoringBlocks(int start, int goal) { return pathInternal(start, goal, true, false); }

    /** Workers clearing a cave-in may enter the blocked goal cell, but cannot cross other rubble. */
    public int[] pathToBlockedGoal(int start, int goal) { return pathInternal(start, goal, false, true); }

    /**
     * Worker route around currently visible danger cells. This intentionally does not use the shared cache:
     * hazards are short-lived and their mask changes independently of the cave revision.
     */
    public int[] pathAvoiding(int start, int goal, boolean[] avoid) {
        int count = cols * rows;
        if (start < 0 || start >= count || goal < 0 || goal >= count) return new int[0];
        if (start == goal) return new int[] { start };
        if (avoid != null && goal < avoid.length && avoid[goal]) return new int[0];
        int[] parent = new int[count];
        java.util.Arrays.fill(parent, -2);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        parent[start] = -1;
        q.add(start);
        while (!q.isEmpty()) {
            int cur = q.removeFirst();
            int c = col(cur), r = row(cur), bits = openings[r][c];
            for (int dir : DIRS) {
                if ((bits & dir) == 0) continue;
                int nc = c + dx(dir), nr = r + dy(dir);
                if (!inside(nc, nr)) continue;
                int next = index(nc, nr);
                if (next != start && isBlocked(next)) continue;
                if (next != goal && avoid != null && next < avoid.length && avoid[next]) continue;
                if (parent[next] != -2) continue;
                parent[next] = cur;
                if (next == goal) return reconstruct(parent, goal);
                q.addLast(next);
            }
        }
        return new int[0];
    }

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

    private static int[] reconstruct(int[] parent, int goal) {
        int n = 1;
        for (int p = parent[goal]; p >= 0; p = parent[p]) n++;
        int[] result = new int[n];
        int cur = goal;
        for (int i = n - 1; i >= 0; i--) {
            result[i] = cur;
            cur = parent[cur];
        }
        return result;
    }

    public int index(int c, int r) { return r * cols + c; }
    public int col(int index) { return index % cols; }
    public int row(int index) { return index / cols; }

    /** Prefer a solid wall of this cell so a deposit visually sits in the wall, not in the walkway. */
    public int preferredSolidSide(int c, int r, int salt) {
        int[] order = {N, E, W, S};
        int rotate = Math.floorMod((int)(seed ^ salt), order.length);
        for (int i = 0; i < order.length; i++) {
            int dir = order[(i + rotate) % order.length];
            if ((openings[r][c] & dir) == 0) return dir;
        }
        return N;
    }

    private void shuffle(int[] a) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }

    public static int opposite(int dir) {
        return switch (dir) { case N -> S; case E -> W; case S -> N; default -> E; };
    }
    public static int dx(int dir) { return dir == E ? 1 : dir == W ? -1 : 0; }
    public static int dy(int dir) { return dir == S ? 1 : dir == N ? -1 : 0; }
}
