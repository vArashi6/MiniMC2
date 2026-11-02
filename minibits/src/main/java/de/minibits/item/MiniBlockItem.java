package de.minibits.item;

import de.minibits.MiniBits;
import de.minibits.MiniData;
import de.minibits.block.MiniBitsBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Kopierter Mini-Block (per Mittelklick). Beim Platzieren wird der gesamte Inhalt übernommen, auf Wunsch gedreht. */
public class MiniBlockItem extends BlockItem {
    public MiniBlockItem(Block block, Properties properties) { super(block, properties); }

    /** Rechtsklick in die Luft: Drehung beim Platzieren um 90° weiterschalten. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        int[] st = MiniBits.settingsOf(player).clone();
        st[1] = (st[1] + 1) & 3;
        MiniBits.setSettings(player, st);
        player.sendSystemMessage(Component.literal("Drehung " + (st[1] * 90) + "°"));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        boolean ok = super.placeBlock(context, state);
        if (ok && !context.getLevel().isClientSide()) {
            MiniData data = context.getItemInHand().get(MiniBits.MINI_DATA);
            if (data != null && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof MiniBitsBlockEntity be) {
                be.applyData(data, MiniBits.settingsOf(context.getPlayer())[1]);
            }
        }
        return ok;
    }
}
