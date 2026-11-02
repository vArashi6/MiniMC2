package de.minibits.block;

import com.mojang.serialization.MapCodec;
import de.minibits.core.BitGrid;
import de.minibits.core.GreedyMesher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MiniBitsBlock extends BaseEntityBlock {
    public static final MapCodec<MiniBitsBlock> CODEC = simpleCodec(MiniBitsBlock::new);

    public MiniBitsBlock(Properties properties) { super(properties); }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

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

    @Override protected boolean propagatesSkylightDown(BlockState state) { return true; }

    public static VoxelShape shapeOf(BitGrid grid) {
        VoxelShape shape = Shapes.empty();
        for (int[] b : GreedyMesher.boxes(grid)) {
            shape = Shapes.or(shape, Shapes.box(b[0] / 16.0, b[1] / 16.0, b[2] / 16.0, b[3] / 16.0, b[4] / 16.0, b[5] / 16.0));
        }
        return shape;
    }
}
