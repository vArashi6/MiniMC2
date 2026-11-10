package de.minibits.core;

/** Berechnet, welche Bits (globale Bit-Koordinaten) eine Form beim Setzen belegt. Wird von Server und Client benutzt, damit Anzeige und Platzierung übereinstimmen. */
public final class Placement {
    private static final double EPS = 1.0 / 64.0;

    /** Angepeiltes Bit (eine Zelle aus der Fläche heraus), globale Bit-Koordinaten. */
    public static int[] aim(double hx, double hy, double hz, int sx, int sy, int sz) {
        return new int[] {
            (int) Math.floor((hx + sx * EPS) * 16),
            (int) Math.floor((hy + sy * EPS) * 16),
            (int) Math.floor((hz + sz * EPS) * 16)
        };
    }

    /**
     * @param aim   angepeiltes Bit (globale Koordinaten)
     * @param step  Normale der angeklickten Fläche
     * @param dims  Maße {Breite, Höhe, Tiefe} der Form (nach Drehung)
     * @param snap  true = am Raster der Formgröße einrasten
     * @return {lo[3], hi[3]}, beide Ecken inklusive
     */
    public static int[][] box(int[] aim, int[] step, int[] dims, boolean snap) {
        int[] lo = new int[3];
        int[] hi = new int[3];
        for (int i = 0; i < 3; i++) {
            int size = dims[i];
            int start;
            if (size >= 16) {
                start = Math.floorDiv(aim[i], 16) * 16;              // ganzer Block auf dieser Achse
            } else if (snap) {
                start = Math.floorDiv(aim[i], size) * size;          // Raster
            } else if (step[i] != 0) {
                start = step[i] > 0 ? aim[i] : aim[i] - size + 1;    // von der Fläche weg
            } else {
                start = aim[i] - size / 2;                           // quer dazu: um das Ziel zentriert
            }
            lo[i] = start;
            hi[i] = start + size - 1;
        }
        return new int[][] {lo, hi};
    }
}
