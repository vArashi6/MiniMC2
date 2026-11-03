package de.minibits.core;

/** Vorgefertigte Formen zum Setzen/Entfernen. Reines Java. */
public final class ShapePreset {
    public record Preset(String name, int w, int h, int d) {}

    public static final Preset[] ALL = {
        new Preset("1x1x1 Bit", 1, 1, 1),
        new Preset("2x2x2", 2, 2, 2),
        new Preset("4x4x4", 4, 4, 4),
        new Preset("8x8x8", 8, 8, 8),
        new Preset("1x1x16 Leiste", 1, 1, 16),
        new Preset("1x16x1 Säule", 1, 16, 1),
        new Preset("4x4x16 Balken", 4, 4, 16),
        new Preset("16x1x16 Boden", 16, 1, 16),
        new Preset("16x16x1 Wand", 16, 16, 1),
        new Preset("16x16x16 Block", 16, 16, 16),
    };

    public static int count() { return ALL.length; }

    /**
     * Box für Preset + Drehung (0-3 × 90° um Y), ausgerichtet am Raster ihrer Größe, um das Bit (bx,by,bz).
     * Ergebnis {x0,y0,z0,x1,y1,z1}, beide Ecken inklusive.
     */
    public static int[] box(int preset, int rotation, int bx, int by, int bz) {
        Preset p = ALL[Math.floorMod(preset, ALL.length)];
        boolean swap = (rotation & 1) == 1;
        int w = swap ? p.d() : p.w();
        int d = swap ? p.w() : p.d();
        int h = p.h();
        int x0 = bx / w * w, y0 = by / h * h, z0 = bz / d * d;
        return new int[] {x0, y0, z0, x0 + w - 1, y0 + h - 1, z0 + d - 1};
    }
    /** Maße {Breite, Höhe, Tiefe} des Presets nach Drehung. */
    public static int[] dims(int preset, int rotation) {
        Preset p = ALL[Math.floorMod(preset, ALL.length)];
        boolean swap = (rotation & 1) == 1;
        return new int[] {swap ? p.d() : p.w(), p.h(), swap ? p.w() : p.d()};
    }
}
