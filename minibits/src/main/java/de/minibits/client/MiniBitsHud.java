package de.minibits.client;

import de.minibits.core.ShapePreset;
import de.minibits.item.BitItem;
import de.minibits.item.MiniBlockItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;

/** Einblendung: Formenliste (vorherige/nächste) und Vorschau (Oben/Vorne/Seite). Erscheint nach jeder Änderung für ein paar Sekunden. */
public final class MiniBitsHud {
    private static int preset = 0;
    private static int rotation = 0;
    private static long changedAt = -100000L;

    public static int preset() { return preset; }
    public static int rotation() { return rotation; }

    public static void setState(int p, int r) {
        preset = p;
        rotation = r;
        changedAt = System.currentTimeMillis();
    }

    public static void reset() {
        preset = 0;
        rotation = 0;
        changedAt = -100000L;
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Item held = mc.player.getMainHandItem().getItem();
        boolean bit = held instanceof BitItem;
        boolean whole = held instanceof MiniBlockItem;
        if (!bit && !whole) return;
        if (System.currentTimeMillis() - changedAt > 1000) return;

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int x0 = w / 2 + 70;
        int y0 = h / 2 - 50;
        var font = mc.font;

        if (whole) {
            g.fill(x0 - 4, y0 - 4, x0 + 90, y0 + 14, 0xAA000000);
            g.text(font, "Drehung +" + (rotation * 90) + "° (zur Blickrichtung)", x0, y0, 0xFFFFFFFF, true);
            return;
        }

        g.fill(x0 - 4, y0 - 4, x0 + 178, y0 + 84, 0xAA000000);

        // Liste: zwei vorher, aktuell, zwei nachher
        int n = ShapePreset.count();
        for (int k = -2; k <= 2; k++) {
            int idx = Math.floorMod(preset + k, n);
            String name = ShapePreset.ALL[idx].name();
            int color = k == 0 ? 0xFFFFFF55 : (Math.abs(k) == 1 ? 0xFFBBBBBB : 0xFF777777);
            g.text(font, (k == 0 ? "> " : "  ") + name, x0, y0 + (k + 2) * 11, color, true);
        }
        g.text(font, "Drehung +" + (rotation * 90) + "° (zur Blickrichtung)", x0, y0 + 62, 0xFF55FFFF, true);

        // Vorschau: Oben (x,z), Vorne (x,y), Seite (z,y)
        int facing = (mc.player.getDirection().get2DDataValue() + 2) & 3;
        int[] d = ShapePreset.dims(preset, (rotation + facing) & 3);
        int S = 2;
        int px = x0 + 108;
        int py = y0;
        frame(g, px, py);
        frame(g, px, py + 34);
        frame(g, px + 34, py + 34);
        int ox = (16 - d[0]) / 2, oy = (16 - d[1]) / 2, oz = (16 - d[2]) / 2;
        int top = py + 34 + (16 - oy - d[1]) * S;
        rect(g, px + ox * S, py + oz * S, d[0] * S, d[2] * S);
        rect(g, px + ox * S, top, d[0] * S, d[1] * S);
        rect(g, px + 34 + oz * S, top, d[2] * S, d[1] * S);
    }

    private static void frame(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 32, y + 32, 0xFF777777);
        g.fill(x + 1, y + 1, x + 31, y + 31, 0xFF111111);
    }

    private static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFFFFAA33);
    }
}
