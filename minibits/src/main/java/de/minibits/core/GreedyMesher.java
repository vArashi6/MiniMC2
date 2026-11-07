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
        return mesh(g, null, null, null);
    }

    /** Wie {@link #mesh(BitGrid, MeshMaterials, BitGrid[], MeshMaterials[])}, alle Materialien undurchsichtig. */
    public static List<Face> mesh(BitGrid g, BitGrid[] neighbors) {
        return mesh(g, null, neighbors, null);
    }

    /**
     * mat = Materialeigenschaften dieses Blocks (null = alles undurchsichtig).
     * neighbors[dir] / nMats[dir] = Gitter und Materialien des angrenzenden Mini-Blocks in dieser Richtung (oder null).
     * Eine Fläche entfällt, wenn das Nachbar-Bit sie verdeckt (undurchsichtig, oder gleiches Glas-Material).
     */
    public static List<Face> mesh(BitGrid g, MeshMaterials mat, BitGrid[] neighbors, MeshMaterials[] nMats) {
        List<Face> out = new ArrayList<>();
        for (int dir = 0; dir < 6; dir++) meshDir(g, mat, neighbors, nMats, dir, out);
        return out;
    }

    private static void meshDir(BitGrid g, MeshMaterials mat, BitGrid[] neighbors, MeshMaterials[] nMats, int dir, List<Face> out) {
        int[] n = NORMAL[dir];
        short[][] mask = new short[BitGrid.N][BitGrid.N];
        BitGrid ng = neighbors != null ? neighbors[dir] : null;
        MeshMaterials nm = nMats != null ? nMats[dir] : null;
        for (int layer = 0; layer < BitGrid.N; layer++) {
            for (int a = 0; a < BitGrid.N; a++)
                for (int b = 0; b < BitGrid.N; b++) {
                    int x, y, z;
                    switch (dir) {
                        case DOWN, UP -> { x = a; y = layer; z = b; }
                        case NORTH, SOUTH -> { x = a; y = b; z = layer; }
                        default -> { x = layer; y = b; z = a; }
                    }
                    short here = g.get(x, y, z);
                    if (here == 0) { mask[a][b] = 0; continue; }
                    int nx = x + n[0], ny = y + n[1], nz = z + n[2];
                    short next;
                    MeshMaterials nmat;
                    if (BitGrid.inside(nx, ny, nz)) { next = g.get(nx, ny, nz); nmat = mat; }
                    else { next = ng != null ? ng.get(nx & 15, ny & 15, nz & 15) : 0; nmat = nm; }
                    boolean sameGrid = BitGrid.inside(nx, ny, nz);
                    mask[a][b] = culls(here, mat, next, nmat, sameGrid) ? 0 : here;
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

    private static boolean culls(short here, MeshMaterials hm, short next, MeshMaterials nm, boolean sameGrid) {
        if (next == 0) return false;
        int kn = nm == null ? MeshMaterials.OPAQUE : nm.kind()[next];
        if (kn == MeshMaterials.OPAQUE) return true;            // undurchsichtiger Nachbar verdeckt
        if (hm == null || nm == null) return true;
        boolean same = hm.id()[here] == nm.id()[next];
        if (!same) return false;                               // anderes Material: Fläche bleibt sichtbar
        int kh = hm.kind()[here];
        if (kh == MeshMaterials.SAME_ONLY) return true;         // Glas, Gitter, Eis: gleiche Nachbarn verdecken immer
        return sameGrid;                                        // Blätter u. ä.: innerhalb eines Mini-Blocks verdecken sie sich
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
