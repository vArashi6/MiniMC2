package de.minibits.core;

import java.util.Arrays;

/** 16x16x16 Raster. Wert 0 = leer, sonst Palettenindex (1-basiert). Reines Java, keine Minecraft-Abhängigkeit. */
public final class BitGrid {
    public static final int N = 16;
    public static final int SIZE = N * N * N;

    private final short[] cells = new short[SIZE];

    public static int index(int x, int y, int z) {
        return (x << 8) | (y << 4) | z;
    }

    public static boolean inside(int x, int y, int z) {
        return (x | y | z) >= 0 && x < N && y < N && z < N;
    }

    public short get(int x, int y, int z) {
        return inside(x, y, z) ? cells[index(x, y, z)] : 0;
    }

    /** @return true, wenn sich etwas geändert hat */
    public boolean set(int x, int y, int z, short value) {
        if (!inside(x, y, z)) return false;
        int i = index(x, y, z);
        if (cells[i] == value) return false;
        cells[i] = value;
        return true;
    }

    /** Füllt die Box [x0..x1] x [y0..y1] x [z0..z1] (inklusive). Gibt Anzahl geänderter Bits zurück. */
    public int fill(int x0, int y0, int z0, int x1, int y1, int z1, short value) {
        int changed = 0;
        for (int x = Math.max(0, Math.min(x0, x1)); x <= Math.min(N - 1, Math.max(x0, x1)); x++)
            for (int y = Math.max(0, Math.min(y0, y1)); y <= Math.min(N - 1, Math.max(y0, y1)); y++)
                for (int z = Math.max(0, Math.min(z0, z1)); z <= Math.min(N - 1, Math.max(z0, z1)); z++)
                    if (set(x, y, z, value)) changed++;
        return changed;
    }

    public int count() {
        int c = 0;
        for (short s : cells) if (s != 0) c++;
        return c;
    }

    public boolean isEmpty() {
        for (short s : cells) if (s != 0) return false;
        return true;
    }

    public short[] raw() {
        return cells;
    }

    public void load(int[] data) {
        Arrays.fill(cells, (short) 0);
        if (data == null) return;
        for (int i = 0; i < Math.min(SIZE, data.length); i++) cells[i] = (short) data[i];
    }

    public int[] toInts() {
        int[] out = new int[SIZE];
        for (int i = 0; i < SIZE; i++) out[i] = cells[i];
        return out;
    }

    public BitGrid copy() {
        BitGrid g = new BitGrid();
        System.arraycopy(cells, 0, g.cells, 0, SIZE);
        return g;
    }
}
