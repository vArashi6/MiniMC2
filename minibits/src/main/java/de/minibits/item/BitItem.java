package de.minibits.item;

import de.minibits.MiniBits;
import de.minibits.block.MiniBitsBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
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

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        ItemStack stack = ctx.getItemInHand();
        BlockState material = stack.get(MiniBits.BIT_STATE);
        if (material == null) return InteractionResult.PASS;

        BlockPos clicked = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        Vec3 hit = ctx.getClickLocation();

        // Zielposition in Bit-Koordinaten relativ zum angeklickten Block, leicht aus der Fläche heraus verschoben
        double eps = 1.0 / 64.0;
        double lx = hit.x - clicked.getX() + face.getStepX() * eps;
        double ly = hit.y - clicked.getY() + face.getStepY() * eps;
        double lz = hit.z - clicked.getZ() + face.getStepZ() * eps;
        BlockPos target = clicked;
        int bx = (int) Math.floor(lx * 16), by = (int) Math.floor(ly * 16), bz = (int) Math.floor(lz * 16);
        // Außerhalb des Blocks -> Nachbarblock
        int ox = Math.floorDiv(bx, 16), oy = Math.floorDiv(by, 16), oz = Math.floorDiv(bz, 16);
        target = clicked.offset(ox, oy, oz);
        bx = Math.floorMod(bx, 16); by = Math.floorMod(by, 16); bz = Math.floorMod(bz, 16);

        if (level.isClientSide()) return InteractionResult.SUCCESS;

        // Zielblock vorbereiten: leer/ersetzbar -> Mini-Block
        BlockState existing = level.getBlockState(target);
        if (existing.getBlock() != MiniBits.MINI_BLOCK) {
            if (!existing.canBeReplaced()) return InteractionResult.FAIL;
            level.setBlock(target, MiniBits.MINI_BLOCK.defaultBlockState(), 3);
        }
        if (!(level.getBlockEntity(target) instanceof MiniBitsBlockEntity be)) return InteractionResult.FAIL;

        short idx = be.paletteIndex(material);
        int placed;
        if (ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown()) {
            // Shift: 4x4x4-Bereich füllen (ausgerichtet)
            int x0 = bx / 4 * 4, y0 = by / 4 * 4, z0 = bz / 4 * 4;
            placed = be.grid().fill(x0, y0, z0, x0 + 3, y0 + 3, z0 + 3, idx);
        } else {
            placed = be.grid().set(bx, by, bz, idx) ? 1 : 0;
        }
        if (placed == 0) return InteractionResult.FAIL;
        be.changed();
        boolean creative = ctx.getPlayer() != null && ctx.getPlayer().getAbilities().instabuild;
        if (!creative) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
