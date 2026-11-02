package de.minibits.core;

import java.util.ArrayList;
import java.util.List;

/** Fasst gleichartige, sichtbare Bit-Flächen zu großen Rechtecken zusammen (greedy meshing). */
public final class GreedyMesher {

    /** Richtungen in der Reihenfolge von net.minecraft.core.Direction: DOWN, UP, NORTH, SOUTH, WEST, EAST. */
    public static final int DOWN = 0, UP = 1, NORTH = 2, SOUTH = 3, WEST = 4, EAST = 5;

    /**
     * Rechteck in Bit-Einheiten (0..16).
     * plane = Position der Fläche entlang der Normalenachse, a0/b0 = Start, w/h = Größe in den beiden anderen Achsen.
     * Achsenzuordnung: DOWN/UP: a=x, b=z | NORTH/SOUTH: a=x, b=y | WEST/EAST: a=z, b=y
     */
    public record Face(int dir, int plane, int a0, int b0, int w, int h, short material) {}

    private static final int[][] NORMAL = {
        {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    public static List<Face> mesh(BitGrid g) {
        List<Face> out = new ArrayList<>();
        for (int dir = 0; dir < 6; dir++) meshDir(g, dir, out);
        return out;
    }

    private static void meshDir(BitGrid g, int dir, List<Face> out) {
        int[] n = NORMAL[dir];
        short[][] mask = new short[BitGrid.N][BitGrid.N];
        for (int layer = 0; layer < BitGrid.N; layer++) {
            for (int a = 0; a < BitGrid.N; a++)
                for (int b = 0; b < BitGrid.N; b++) {
                    int[] p = toXYZ(dir, layer, a, b);
                    short here = g.get(p[0], p[1], p[2]);
                    short next = g.get(p[0] + n[0], p[1] + n[1], p[2] + n[2]);
                    mask[a][b] = (here != 0 && next == 0) ? here : 0;
                }
            for (int a = 0; a < BitGrid.N; a++)
                for (int b = 0; b < BitGrid.N; b++) {
                    short m = mask[a][b];
                    if (m == 0) continue;
                    int w = 1;
                    while (a + w < BitGrid.N && mask[a + w][b] == m) w++;
                    int h = 1;
                    outer:
                    while (b + h < BitGrid.N) {
                        for (int i = 0; i < w; i++) if (mask[a + i][b + h] != m) break outer;
                        h++;
                    }
                    for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) mask[a + i][b + j] = 0;
                    int plane = (n[0] + n[1] + n[2] > 0) ? layer + 1 : layer;
                    out.add(new Face(dir, plane, a, b, w, h, m));
                }
        }
    }

    private static int[] toXYZ(int dir, int layer, int a, int b) {
        return switch (dir) {
            case DOWN, UP -> new int[] {a, layer, b};
            case NORTH, SOUTH -> new int[] {a, b, layer};
            default -> new int[] {layer, b, a};
        };
    }

    /** Fasst belegte Bits zu möglichst wenigen Quadern für die Kollisions-/Auswahlform zusammen. {x0,y0,z0,x1,y1,z1} in Bit-Einheiten. */
    public static List<int[]> boxes(BitGrid g) {
        boolean[] used = new boolean[BitGrid.SIZE];
        List<int[]> out = new ArrayList<>();
        for (int x = 0; x < BitGrid.N; x++)
            for (int y = 0; y < BitGrid.N; y++)
                for (int z = 0; z < BitGrid.N; z++) {
                    if (used[BitGrid.index(x, y, z)] || g.get(x, y, z) == 0) continue;
                    int x1 = x;
                    while (x1 + 1 < BitGrid.N && free(g, used, x1 + 1, y, z)) x1++;
                    int y1 = y;
                    while (y1 + 1 < BitGrid.N && rowFree(g, used, x, x1, y1 + 1, z, z)) y1++;
                    int z1 = z;
                    while (z1 + 1 < BitGrid.N && slabFree(g, used, x, x1, y, y1, z1 + 1)) z1++;
                    for (int i = x; i <= x1; i++)
                        for (int j = y; j <= y1; j++)
                            for (int k = z; k <= z1; k++) used[BitGrid.index(i, j, k)] = true;
                    out.add(new int[] {x, y, z, x1 + 1, y1 + 1, z1 + 1});
                }
        return out;
    }

    private static boolean free(BitGrid g, boolean[] used, int x, int y, int z) {
        return g.get(x, y, z) != 0 && !used[BitGrid.index(x, y, z)];
    }

    private static boolean rowFree(BitGrid g, boolean[] used, int x0, int x1, int y, int z0, int z1) {
        for (int x = x0; x <= x1; x++) if (!free(g, used, x, y, z0)) return false;
        return true;
    }

    private static boolean slabFree(BitGrid g, boolean[] used, int x0, int x1, int y0, int y1, int z) {
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) if (!free(g, used, x, y, z)) return false;
        return true;
    }
}
