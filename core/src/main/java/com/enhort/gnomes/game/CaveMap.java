package com.enhort.gnomes.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Small deterministic perfect-maze graph used by the mine. The mine is a graph first and a picture second:
 * workers and enemies really route through the carved tunnels instead of skating through solid stone.
 */
public final class CaveMap {
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

    private final Random random;
    private final boolean[] blocked;

    public CaveMap(int cols, int rows, long seed) {
        this.cols = Math.max(5, cols | 1);
        this.rows = Math.max(7, rows);
        this.seed = seed;
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

        // A few loops make the mine feel hand-carved instead of a textbook maze, while keeping navigation readable.
        int extra = Math.max(2, cols * rows / 18);
        for (int i = 0; i < extra; i++) {
            int c = random.nextInt(cols);
            int r = random.nextInt(rows);
            int dir = dirs[random.nextInt(dirs.length)];
            int nc = c + dx(dir), nr = r + dy(dir);
            if (!inside(nc, nr)) continue;
            if ((openings[r][c] & dir) == 0) connect(c, r, nc, nr, dir);
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
        if (cell < 0 || cell >= blocked.length || cell == index(startCol, startRow)) return false;
        blocked[cell] = true;
        return true;
    }

    public void unblockCell(int cell) {
        if (cell >= 0 && cell < blocked.length) blocked[cell] = false;
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

    /** Returns a list containing start and goal. Empty only for invalid cells. */
    public int[] path(int start, int goal) {
        int count = cols * rows;
        if (start < 0 || start >= count || goal < 0 || goal >= count) return new int[0];
        if (isBlocked(goal) && goal != start) return new int[0];
        if (start == goal) return new int[] { start };

        int[] parent = new int[count];
        java.util.Arrays.fill(parent, -2);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        parent[start] = -1;
        q.add(start);
        while (!q.isEmpty()) {
            int cur = q.removeFirst();
            int c = col(cur), r = row(cur);
            int bits = openings[r][c];
            for (int dir : new int[]{N,E,S,W}) {
                if ((bits & dir) == 0) continue;
                int nc = c + dx(dir), nr = r + dy(dir);
                if (!inside(nc, nr)) continue;
                int next = index(nc, nr);
                if (next != start && isBlocked(next)) continue;
                if (parent[next] != -2) continue;
                parent[next] = cur;
                if (next == goal) return reconstruct(parent, goal);
                q.addLast(next);
            }
        }
        return new int[0];
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
