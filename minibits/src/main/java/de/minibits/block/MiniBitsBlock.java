package de.minibits.block;

import com.mojang.serialization.MapCodec;
import de.minibits.core.BitGrid;
import de.minibits.core.GreedyMesher;
import net.minecraft.core.BlockPos;
import de.minibits.MiniBits;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
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

    /** Leuchtkraft des Blocks (0-15), abgeleitet aus den enthaltenen Materialien. */
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    /** Index des Geräuschtyps (Schritte, Abbau), abgeleitet vom häufigsten Material. */
    public static final IntegerProperty SND = IntegerProperty.create("snd", 0, 15);
    /** Überwiegende Flüssigkeit: 0 keine, 1 Wasser, 2 Lava (ungefährlich), 3 Lava (heiß). Für Shader-Pakete und Feuer-Ausbreitung. */
    public static final IntegerProperty FLUID = IntegerProperty.create("fluid", 0, 3);

    public static final SoundType[] SOUNDS = {
        SoundType.STONE, SoundType.WOOD, SoundType.GRAVEL, SoundType.GRASS,
        SoundType.SAND, SoundType.SNOW, SoundType.GLASS, SoundType.METAL,
        SoundType.WOOL, SoundType.NETHERRACK, SoundType.BASALT, SoundType.BONE_BLOCK,
        SoundType.DEEPSLATE, SoundType.TUFF, SoundType.COPPER, SoundType.AMETHYST
    };

    public static int soundIndexOf(SoundType type) {
        for (int i = 0; i < SOUNDS.length; i++) if (SOUNDS[i] == type) return i;
        return 0;
    }

    public MiniBitsBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIGHT, 0).setValue(SND, 0).setValue(FLUID, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(LIGHT, SND, FLUID);
    }

    /** Heiße Lava breitet wie das Original Feuer aus (zufällige Ticks, nur wenn Heiße Lava überwiegt). */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(FLUID) == 3;
    }

    @Override
    protected void randomTick(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        for (int i = 0; i < 3; i++) {
            BlockPos p = pos.relative(net.minecraft.core.Direction.getRandom(random));
            BlockState bs = level.getBlockState(p);
            if (bs.isAir()) {
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                    if (level.getBlockState(p.relative(d)).ignitedByLava()) {
                        level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
                        return;
                    }
                }
            } else if (bs.ignitedByLava() && level.getBlockState(p.above()).isAir()) {
                level.setBlockAndUpdate(p.above(), net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
                return;
            }
        }
    }

    @Override
    public SoundType getSoundType(BlockState state) {
        return SOUNDS[state.getValue(SND)];
    }


    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MiniBitsBlockEntity(pos, state);
    }

    /** Das Modell kommt vom benutzerdefinierten Fabric-BlockStateModel. */
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (level.getBlockEntity(pos) instanceof MiniBitsBlockEntity be) return be.shape();
        return Shapes.block();
    }

    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (level.getBlockEntity(pos) instanceof MiniBitsBlockEntity be) return be.collisionShape();
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
