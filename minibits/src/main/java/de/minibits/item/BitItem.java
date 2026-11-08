package de.minibits.item;

import de.minibits.MiniBits;
import de.minibits.block.MiniBitsBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class BitItem extends Item {
    public BitItem(Properties properties) { super(properties); }

    /** Nur volle, nicht-blockentity Würfel mit normalem Rendering. */
    public static boolean isUsable(BlockState s) {
        return !s.isAir() && !s.hasBlockEntity() && s.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL
            && s.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
            && s.getFluidState().isEmpty();
    }

    @Override
    public Component getName(ItemStack stack) {
        BlockState s = stack.get(MiniBits.BIT_STATE);
        return s == null ? super.getName(stack)
            : Component.translatable("item.minibits.bit.of", s.getBlock().getName());
    }

    /** Rechtsklick in die Luft: nächste Form. Shift + Rechtsklick in die Luft: vorherige Form. (Drehen: Linksklick in die Luft.) */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            MiniBits.applyCommand(sp, player.isShiftKeyDown() ? 1 : 0);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();
        BlockState material = stack.get(MiniBits.BIT_STATE);
        if (material == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        Direction face = ctx.getClickedFace();
        Vec3 hit = ctx.getClickLocation();
        Player player = ctx.getPlayer();

        // Angepeiltes Bit (globale Bit-Koordinaten), eine Zelle aus der Fläche heraus
        double eps = 1.0 / 64.0;
        int[] aim = {
            (int) Math.floor((hit.x + face.getStepX() * eps) * 16),
            (int) Math.floor((hit.y + face.getStepY() * eps) * 16),
            (int) Math.floor((hit.z + face.getStepZ() * eps) * 16)
        };
        int[] step = {face.getStepX(), face.getStepY(), face.getStepZ()};

        int[] st = MiniBits.settingsOf(player);
        // Ausrichtung wie bei Treppen: nach Blickrichtung, zusätzlich um die manuelle Drehung verschoben
        int rot = (st[1] + MiniBits.facingRotation(player)) & 3;
        int[] dims = de.minibits.core.ShapePreset.dims(st[0], rot);
        boolean snap = player != null && player.isShiftKeyDown(); // Shift: am Raster einrasten

        int[] lo = new int[3];
        int[] hi = new int[3];
        for (int i = 0; i < 3; i++) {
            int size = dims[i];
            int start;
            if (size >= 16) {
                start = Math.floorDiv(aim[i], 16) * 16;           // ganzer Block auf dieser Achse
            } else if (snap) {
                start = Math.floorDiv(aim[i], size) * size;       // Raster
            } else if (step[i] != 0) {
                start = step[i] > 0 ? aim[i] : aim[i] - size + 1; // von der Fläche weg
            } else {
                start = aim[i] - size / 2;                        // quer dazu: um das Ziel zentriert (darf überstehen)
            }
            lo[i] = start;
            hi[i] = start + size - 1;
        }

        java.util.List<de.minibits.UndoHistory.Cell> log = new java.util.ArrayList<>();
        int placed = placeBox(level, lo, hi, material, log);
        if (placed == 0) return InteractionResult.FAIL;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) de.minibits.UndoHistory.push(sp, log);
        net.minecraft.world.level.block.SoundType sound = material.getSoundType();
        level.playSound(null, net.minecraft.core.BlockPos.containing(hit), sound.getPlaceSound(),
            net.minecraft.sounds.SoundSource.BLOCKS, (sound.getVolume() + 1.0f) / 2.0f, sound.getPitch() * 0.8f);
        boolean creative = player != null && player.getAbilities().instabuild;
        if (!creative) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    /** Setzt die Box (globale Bit-Koordinaten, inklusive) und legt dabei bei Bedarf Nachbar-Mini-Blöcke an. */
    private static int placeBox(Level level, int[] lo, int[] hi, BlockState material, java.util.List<de.minibits.UndoHistory.Cell> log) {
        int total = 0;
        for (int bx = Math.floorDiv(lo[0], 16); bx <= Math.floorDiv(hi[0], 16); bx++)
            for (int by = Math.floorDiv(lo[1], 16); by <= Math.floorDiv(hi[1], 16); by++)
                for (int bz = Math.floorDiv(lo[2], 16); bz <= Math.floorDiv(hi[2], 16); bz++) {
                    BlockPos bp = new BlockPos(bx, by, bz);
                    if (!level.isInWorldBounds(bp)) continue;
                    BlockState existing = level.getBlockState(bp);
                    boolean wasMini = existing.is(MiniBits.MINI_BLOCK);
                    if (!wasMini && !existing.canBeReplaced()) continue;

                    int x0 = Math.max(lo[0], bx * 16) - bx * 16, x1 = Math.min(hi[0], bx * 16 + 15) - bx * 16;
                    int y0 = Math.max(lo[1], by * 16) - by * 16, y1 = Math.min(hi[1], by * 16 + 15) - by * 16;
                    int z0 = Math.max(lo[2], bz * 16) - bz * 16, z1 = Math.min(hi[2], bz * 16 + 15) - bz * 16;

                    if (!wasMini) level.setBlock(bp, MiniBits.MINI_BLOCK.defaultBlockState(), 3);
                    if (!(level.getBlockEntity(bp) instanceof MiniBitsBlockEntity be)) continue;
                    short idx = be.paletteIndex(material);
                    int n = 0;
                    for (int x = x0; x <= x1; x++)
                        for (int y = y0; y <= y1; y++)
                            for (int z = z0; z <= z1; z++) {
                                if (be.grid().get(x, y, z) != 0) continue;
                                be.grid().set(x, y, z, idx);
                                log.add(new de.minibits.UndoHistory.Cell(bp, de.minibits.UndoHistory.index(x, y, z), null, material));
                                n++;
                            }
                    if (n > 0) {
                        be.changed();
                        total += n;
                    } else if (!wasMini && be.grid().isEmpty()) {
                        level.removeBlock(bp, false);
                    }
                }
        return total;
    }
}
