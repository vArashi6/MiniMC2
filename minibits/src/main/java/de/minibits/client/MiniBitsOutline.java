package de.minibits.client;

import de.minibits.MiniBits;
import de.minibits.block.MiniBitsBlockEntity;
import de.minibits.core.Placement;
import de.minibits.core.ShapePreset;
import de.minibits.item.BitItem;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Zeichnet Umrisse: rot = Bereich, der mit Linksklick entfernt wird; grün = Bereich, den eine Form mit Rechtsklick belegt. */
public final class MiniBitsOutline {
    public static void register() {
        // Den Standard-Umriss des ganzen Blocks bei Mini-Blöcken weglassen
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, state) -> {
            Minecraft mc = Minecraft.getInstance();
            return mc.level == null || !mc.level.getBlockState(state.pos()).is(MiniBits.MINI_BLOCK);
        });

        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) return;

            BlockPos pos = hit.getBlockPos();
            int facing = (mc.player.getDirection().get2DDataValue() + 2) & 3;
            int rot = (MiniBitsHud.rotation() + facing) & 3;
            int preset = MiniBitsHud.preset();
            var dir = hit.getDirection();
            var loc = hit.getLocation();

            // Entfernen-Bereich (rot)
            if (mc.level.getBlockState(pos).is(MiniBits.MINI_BLOCK) && mc.level.getBlockEntity(pos) instanceof MiniBitsBlockEntity) {
                double eps = 1.0 / 64.0;
                int bx = clamp((loc.x - pos.getX() - dir.getStepX() * eps) * 16);
                int by = clamp((loc.y - pos.getY() - dir.getStepY() * eps) * 16);
                int bz = clamp((loc.z - pos.getZ() - dir.getStepZ() * eps) * 16);
                // Wie beim Abbauen: Shift + Linksklick leert den ganzen Block, sonst die gewählte Form
                int[] b = mc.player.isShiftKeyDown() ? new int[] {0, 0, 0, 15, 15, 15} : ShapePreset.box(preset, rot, bx, by, bz);
                AABB aabb = new AABB(
                    pos.getX() + b[0] / 16.0, pos.getY() + b[1] / 16.0, pos.getZ() + b[2] / 16.0,
                    pos.getX() + (b[3] + 1) / 16.0, pos.getY() + (b[4] + 1) / 16.0, pos.getZ() + (b[5] + 1) / 16.0);
                Gizmos.cuboid(aabb.inflate(0.002), GizmoStyle.stroke(0xFFFF3030));
            }

            // Setzen-Bereich (grün), wenn ein Bit in der Hand ist
            if (mc.player.getMainHandItem().getItem() instanceof BitItem) {
                int[] aim = Placement.aim(loc.x, loc.y, loc.z, dir.getStepX(), dir.getStepY(), dir.getStepZ());
                int[] step = {dir.getStepX(), dir.getStepY(), dir.getStepZ()};
                int[][] b = Placement.box(aim, step, ShapePreset.dims(preset, rot), mc.player.isShiftKeyDown());
                AABB aabb = new AABB(b[0][0] / 16.0, b[0][1] / 16.0, b[0][2] / 16.0,
                    (b[1][0] + 1) / 16.0, (b[1][1] + 1) / 16.0, (b[1][2] + 1) / 16.0);
                Gizmos.cuboid(aabb.inflate(0.002), GizmoStyle.stroke(0xFF30FF60));
            }
        });
    }

    private static int clamp(double v) {
        return Math.max(0, Math.min(15, (int) Math.floor(v)));
    }
}
