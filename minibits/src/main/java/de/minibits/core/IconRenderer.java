package de.minibits.core;

/**
 * Erzeugt ein 16x16-Vorschaubild mit drei sichtbaren Seiten (vorne, oben, rechts) aus einem Mini-Block.
 * Reines Java. Farben: paletteRgb[index] (RGB), Index 1-basiert wie im BitGrid.
 */
public final class IconRenderer {
    private static final int FRONT = 12; // Pixel-Kantenlänge der Vorderseite
    private static final int DEPTH = 4;  // Pixel-Tiefe der Ober- und Seitenfläche

    public static int[] render(BitGrid g, int[] paletteRgb) {
        int[] out = new int[256];

        // Vorderseite: Blick von +Z nach -Z, nächstes Bit zählt
        for (int i = 0; i < FRONT; i++) {
            for (int j = 0; j < FRONT; j++) {
                int x = (i * 16 + 8) / FRONT;
                int y = 15 - (j * 16 + 8) / FRONT;
                for (int z = 15; z >= 0; z--) {
                    short v = g.get(x, y, z);
                    if (v == 0) continue;
                    put(out, i, DEPTH + j, color(paletteRgb, v, 1.0 - (15 - z) * 0.008, i, j));
                    break;
                }
            }
        }

        // Oberseite: Zeilen r=0 (hinten) bis r=3 (vorne), nach rechts versetzt
        for (int r = 0; r < DEPTH; r++) {
            int zLo = r * 4, zHi = zLo + 3;
            for (int i = 0; i < FRONT; i++) {
                int x = (i * 16 + 8) / FRONT;
                int bestY = -1, bestZ = -1;
                short mat = 0;
                for (int y = 15; y >= 0 && bestY < 0; y--) {
                    for (int z = zHi; z >= zLo; z--) {
                        short v = g.get(x, y, z);
                        if (v != 0) { bestY = y; bestZ = z; mat = v; break; }
                    }
                }
                if (bestY >= 0) put(out, i + (DEPTH - 1 - r), r, color(paletteRgb, mat, 1.16, i, r));
            }
        }

        // Rechte Seite: Spalten c=0 (vorne) bis c=3 (hinten), nach oben versetzt
        for (int c = 0; c < DEPTH; c++) {
            int zLo = (DEPTH - 1 - c) * 4, zHi = zLo + 3;
            for (int j = 0; j < FRONT; j++) {
                int y = 15 - (j * 16 + 8) / FRONT;
                short mat = 0;
                for (int x = 15; x >= 0 && mat == 0; x--) {
                    for (int z = zHi; z >= zLo; z--) {
                        short v = g.get(x, y, z);
                        if (v != 0) { mat = v; break; }
                    }
                }
                if (mat != 0) put(out, FRONT + c, DEPTH + j - c, color(paletteRgb, mat, 0.70, c, j));
            }
        }
        return out;
    }

    private static void put(int[] out, int x, int y, int argb) {
        if (x < 0 || x > 15 || y < 0 || y > 15) return;
        out[y * 16 + x] = argb;
    }

    private static int color(int[] pal, short idx, double shade, int a, int b) {
        int rgb = idx > 0 && idx < pal.length ? pal[idx] : 0x888888;
        double noise = 1.0 + 0.05 * (((a * 7 + b * 13) % 3) - 1); // leichte Körnung statt flacher Fläche
        double s = shade * noise;
        int r = clamp(((rgb >> 16) & 255) * s), gr = clamp(((rgb >> 8) & 255) * s), bl = clamp((rgb & 255) * s);
        return 0xFF000000 | (r << 16) | (gr << 8) | bl;
    }

    private static int clamp(double v) { return Math.max(0, Math.min(255, (int) Math.round(v))); }
}
