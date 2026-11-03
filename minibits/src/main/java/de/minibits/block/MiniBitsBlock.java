package de.minibits.block;

import com.mojang.serialization.MapCodec;
import de.minibits.core.BitGrid;
import de.minibits.core.GreedyMesher;
import net.minecraft.core.BlockPos;
import de.minibits.MiniBits;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MiniBitsBlock extends BaseEntityBlock {

    public MiniBitsBlock(Properties properties) { super(properties); }


    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MiniBitsBlockEntity(pos, state);
    }

    /** Das Modell kommt vom benutzerdefinierten Fabric-BlockStateModel. */
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (level.getBlockEntity(pos) instanceof MiniBitsBlockEntity be) return shapeOf(be.grid());
        return Shapes.block();
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    @Override protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    /**
     * Terrain-Ambient-Occlusion: Minecraft hält den Block für einen vollen Würfel (die Form hängt vom Block-Entity ab)
     * und dunkelt deshalb den Boden rundherum ab. Mit 1.0 bleibt der Boden hell.
     */
    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override protected boolean propagatesSkylightDown(BlockState state) { return true; }

    /** Mittelklick: kopiert den gesamten Mini-Block (alle 16x16x16 Bits). */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        ItemStack stack = new ItemStack(MiniBits.MINI_BLOCK_ITEM);
        if (level.getBlockEntity(pos) instanceof MiniBitsBlockEntity be) {
            stack.set(MiniBits.MINI_DATA, be.toData());
            stack.set(MiniBits.MINI_ICON, be.iconPixels());
            BlockState dom = be.dominantState();
            if (dom != null) {
                stack.set(net.minecraft.core.component.DataComponents.ITEM_MODEL,
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(dom.getBlock()));
            }
        }
        return stack;
    }

    public static VoxelShape shapeOf(BitGrid grid) {
        VoxelShape shape = Shapes.empty();
        for (int[] b : GreedyMesher.boxes(grid)) {
            shape = Shapes.or(shape, Shapes.box(b[0] / 16.0, b[1] / 16.0, b[2] / 16.0, b[3] / 16.0, b[4] / 16.0, b[5] / 16.0));
        }
        return shape;
    }
}
