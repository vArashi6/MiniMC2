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
        int[] st = MiniBits.settingsOf(ctx.getPlayer());
        int[] box = de.minibits.core.ShapePreset.box(st[0], st[1], bx, by, bz);
        int placed = be.grid().fillEmpty(box[0], box[1], box[2], box[3], box[4], box[5], idx);
        if (placed == 0) {
            if (be.grid().isEmpty()) level.removeBlock(target, false);
            return InteractionResult.FAIL;
        }
        be.changed();
        boolean creative = ctx.getPlayer() != null && ctx.getPlayer().getAbilities().instabuild;
        if (!creative) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
