package de.minibits;

import de.minibits.block.MiniBitsBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Rückgängig/Wiederholen für Mini-Bits (pro Spieler, bis zu 50 Schritte, nur im Speicher des Servers). */
public final class UndoHistory {
    public static final int MAX = 50;

    /** Eine einzelne Änderung: Bit-Index im Block, Zustand davor und danach (null = leer). */
    public record Cell(BlockPos pos, int index, BlockState before, BlockState after) {}

    private static final Map<UUID, Deque<List<Cell>>> UNDO = new ConcurrentHashMap<>();
    private static final Map<UUID, Deque<List<Cell>>> REDO = new ConcurrentHashMap<>();

    public static int index(int x, int y, int z) { return (x << 8) | (y << 4) | z; }

    /** Neuen Schritt ablegen; löscht die Wiederholen-Liste. */
    public static void push(ServerPlayer player, List<Cell> cells) {
        if (player == null || cells.isEmpty()) return;
        Deque<List<Cell>> undo = UNDO.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        undo.addLast(cells);
        while (undo.size() > MAX) undo.removeFirst();
        Deque<List<Cell>> redo = REDO.get(player.getUUID());
        if (redo != null) redo.clear();
    }

    public static void undo(ServerPlayer player) {
        Deque<List<Cell>> undo = UNDO.get(player.getUUID());
        if (undo == null || undo.isEmpty()) {
            player.sendSystemMessage(Component.literal("Nichts zum Rückgängigmachen"));
            return;
        }
        List<Cell> cells = undo.removeLast();
        apply(player.level(), cells, true);
        Deque<List<Cell>> redo = REDO.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        redo.addLast(cells);
        while (redo.size() > MAX) redo.removeFirst();
        player.sendSystemMessage(Component.literal("Rückgängig (" + undo.size() + " weitere möglich)"));
    }

    public static void redo(ServerPlayer player) {
        Deque<List<Cell>> redo = REDO.get(player.getUUID());
        if (redo == null || redo.isEmpty()) {
            player.sendSystemMessage(Component.literal("Nichts zum Wiederholen"));
            return;
        }
        List<Cell> cells = redo.removeLast();
        apply(player.level(), cells, false);
        Deque<List<Cell>> undo = UNDO.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        undo.addLast(cells);
        while (undo.size() > MAX) undo.removeFirst();
        player.sendSystemMessage(Component.literal("Wiederholt (" + redo.size() + " weitere möglich)"));
    }

    private static void apply(Level level, List<Cell> cells, boolean undoMode) {
        Map<BlockPos, List<Cell>> byBlock = new LinkedHashMap<>();
        for (Cell c : cells) byBlock.computeIfAbsent(c.pos(), k -> new ArrayList<>()).add(c);

        for (Map.Entry<BlockPos, List<Cell>> e : byBlock.entrySet()) {
            BlockPos pos = e.getKey();
            BlockState existing = level.getBlockState(pos);
            boolean isMini = existing.is(MiniBits.MINI_BLOCK);

            boolean anyTarget = false;
            for (Cell c : e.getValue()) if ((undoMode ? c.before() : c.after()) != null) { anyTarget = true; break; }
            if (!isMini) {
                if (!anyTarget || !existing.canBeReplaced()) continue; // Position ist inzwischen anders belegt
                level.setBlock(pos, MiniBits.MINI_BLOCK.defaultBlockState(), 3);
            }
            if (!(level.getBlockEntity(pos) instanceof MiniBitsBlockEntity be)) continue;

            for (Cell c : e.getValue()) {
                BlockState target = undoMode ? c.before() : c.after();
                int i = c.index();
                int x = (i >> 8) & 15, y = (i >> 4) & 15, z = i & 15;
                be.grid().set(x, y, z, target == null ? (short) 0 : be.paletteIndex(target));
            }
            if (be.grid().isEmpty()) level.removeBlock(pos, false);
            else be.changed();
        }
    }
}
