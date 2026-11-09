package de.minibits.block;

import com.mojang.serialization.Codec;
import de.minibits.MiniBits;
import de.minibits.MiniData;
import de.minibits.core.BitGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

/** Speichert 16x16x16 Bits. Palette: Index (1-basiert) -> BlockState. */
public class MiniBitsBlockEntity extends BlockEntity {
    private final BitGrid grid = new BitGrid();
    private volatile net.minecraft.world.phys.shapes.VoxelShape shapeCache;
    private volatile net.minecraft.world.phys.shapes.VoxelShape collisionCache;
    private volatile Snapshot snapshotCache;
    private final List<BlockState> palette = new ArrayList<>();

    public MiniBitsBlockEntity(BlockPos pos, BlockState state) {
        super(MiniBits.MINI_BLOCK_ENTITY, pos, state);
    }

    public BitGrid grid() { return grid; }
    public List<BlockState> palette() { return palette; }

    public short paletteIndex(BlockState s) {
        int i = palette.indexOf(s);
        if (i < 0) { palette.add(s); i = palette.size() - 1; }
        return (short) (i + 1);
    }

    public BlockState stateAt(short idx) {
        return idx > 0 && idx <= palette.size() ? palette.get(idx - 1) : null;
    }

    private void invalidateCaches() {
        shapeCache = null;
        collisionCache = null;
        snapshotCache = null;
    }

    /** Auswahl-/Kollisionsform, einmal berechnet und bis zur nächsten Änderung zwischengespeichert. */
    public net.minecraft.world.phys.shapes.VoxelShape shape() {
        net.minecraft.world.phys.shapes.VoxelShape s = shapeCache;
        if (s == null) {
            s = MiniBitsBlock.shapeOf(grid);
            shapeCache = s;
        }
        return s;
    }

    /** Kollisionsform: wie die Auswahlform, aber Wasser und Lava sind nicht fest (man läuft hindurch). */
    public net.minecraft.world.phys.shapes.VoxelShape collisionShape() {
        net.minecraft.world.phys.shapes.VoxelShape s = collisionCache;
        if (s == null) {
            BitGrid solid = grid.copy();
            short[] raw = solid.raw();
            boolean anyFluid = false;
            for (int i = 0; i < raw.length; i++) {
                if (raw[i] > 0 && raw[i] <= palette.size() && !palette.get(raw[i] - 1).getFluidState().isEmpty()) {
                    raw[i] = 0;
                    anyFluid = true;
                }
            }
            s = anyFluid ? MiniBitsBlock.shapeOf(solid) : shape();
            collisionCache = s;
        }
        return s;
    }

    /** Bit 1 = berührt Wasser-Bits, Bit 2 = berührt Lava-Bits (die Box ist in Weltkoordinaten). */
    public int fluidContact(BlockPos pos, net.minecraft.world.phys.AABB box) {
        boolean any = false;
        for (BlockState st : palette) {
            if (st.is(net.minecraft.world.level.block.Blocks.WATER) || st.is(net.minecraft.world.level.block.Blocks.LAVA)) { any = true; break; }
        }
        if (!any) return 0;
        int x0 = Math.max(0, (int) Math.floor((box.minX - pos.getX()) * 16)), x1 = Math.min(15, (int) Math.ceil((box.maxX - pos.getX()) * 16) - 1);
        int y0 = Math.max(0, (int) Math.floor((box.minY - pos.getY()) * 16)), y1 = Math.min(15, (int) Math.ceil((box.maxY - pos.getY()) * 16) - 1);
        int z0 = Math.max(0, (int) Math.floor((box.minZ - pos.getZ()) * 16)), z1 = Math.min(15, (int) Math.ceil((box.maxZ - pos.getZ()) * 16) - 1);
        int mask = 0;
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    short v = grid.get(x, y, z);
                    if (v <= 0 || v > palette.size()) continue;
                    BlockState st = palette.get(v - 1);
                    if (st.is(net.minecraft.world.level.block.Blocks.WATER)) mask |= 1;
                    else if (st.is(net.minecraft.world.level.block.Blocks.LAVA)) mask |= 2;
                    if (mask == 3) return 3;
                }
        return mask;
    }

    /** Nach jeder Änderung aufrufen: speichern + Client/Rendering aktualisieren. */
    public void changed() {
        invalidateCaches();
        updateDerivedState();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Leuchtkraft und Geräuschtyp aus den enthaltenen Materialien berechnen und im Blockzustand ablegen (nur Server). */
    private void updateDerivedState() {
        if (level == null || level.isClientSide()) return;
        int[] counts = new int[palette.size() + 1];
        for (short v : grid.raw()) if (v > 0 && v < counts.length) counts[v]++;

        int light = 0, best = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[best]) best = i;
            int emission = palette.get(i - 1).getLightEmission();
            if (emission > 0 && counts[i] > 0) {
                double f = Math.min(1.0, 0.25 + 0.75 * counts[i] / 512.0);
                light = Math.max(light, (int) Math.round(emission * f));
            }
        }
        int snd = best == 0 ? 0 : MiniBitsBlock.soundIndexOf(palette.get(best - 1).getSoundType());

        BlockState st = getBlockState();
        if (!st.hasProperty(MiniBitsBlock.LIGHT) || !st.hasProperty(MiniBitsBlock.SND)) return;
        if (st.getValue(MiniBitsBlock.LIGHT) != light || st.getValue(MiniBitsBlock.SND) != snd) {
            level.setBlock(worldPosition, st.setValue(MiniBitsBlock.LIGHT, light).setValue(MiniBitsBlock.SND, snd), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.store("bits", Codec.INT.listOf(), java.util.Arrays.stream(grid.toInts()).boxed().toList());
        out.store("palette", BlockState.CODEC.listOf(), palette);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        invalidateCaches();
        palette.clear();
        in.read("palette", BlockState.CODEC.listOf()).ifPresent(palette::addAll);
        grid.load(in.read("bits", Codec.INT.listOf()).map(l -> l.stream().mapToInt(Integer::intValue).toArray()).orElse(null));
        // Client: Chunk-Darstellung neu aufbauen, sobald neue Daten ankommen
        if (level != null && level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** Häufigstes Material (für das Inventar-Symbol kopierter Mini-Blöcke). */
    public BlockState dominantState() {
        int[] counts = new int[palette.size() + 1];
        for (short v : grid.raw()) if (v > 0 && v < counts.length) counts[v]++;
        int best = 0;
        for (int i = 1; i < counts.length; i++) if (counts[i] > counts[best]) best = i;
        return best == 0 ? null : palette.get(best - 1);
    }

    /** 16x16-Vorschaubild (ARGB) mit drei sichtbaren Seiten. */
    public List<Integer> iconPixels() {
        int[] pal = new int[palette.size() + 1];
        for (int i = 1; i < pal.length; i++) {
            BlockState st = palette.get(i - 1);
            int rgb = 0x88AACC;
            int col = st.getMapColor(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
            if (col != 0) rgb = col;
            pal[i] = rgb;
        }
        int[] px = de.minibits.core.IconRenderer.render(grid, pal);
        Integer[] out = new Integer[256];
        for (int i = 0; i < 256; i++) out[i] = px[i];
        return java.util.Arrays.asList(out);
    }

    public MiniData toData() {
        return new MiniData(java.util.Arrays.stream(grid.toInts()).boxed().toList(), List.copyOf(palette));
    }

    public void applyData(MiniData data, int rotation) {
        palette.clear();
        palette.addAll(data.palette());
        grid.load(data.bits().stream().mapToInt(Integer::intValue).toArray());
        grid.rotateY(rotation);
        changed();
    }

    /** Wird vom Fabric-Renderer im Chunk-Thread gelesen (unveränderlicher Schnappschuss, zwischengespeichert). */
    public Snapshot getRenderData() {
        Snapshot s = snapshotCache;
        if (s == null) {
            s = new Snapshot(grid.copy(), List.copyOf(palette));
            snapshotCache = s;
        }
        return s;
    }

    /** Unveränderlicher Stand eines Mini-Blocks für das Rendering, inklusive zwischengespeichertem Flächennetz. */
    public static final class Snapshot {
        private final BitGrid grid;
        private final List<BlockState> palette;
        private final de.minibits.core.MeshMaterials materials;
        private List<de.minibits.core.GreedyMesher.Face> faces;
        private Snapshot[] faceNeighbors;

        Snapshot(BitGrid grid, List<BlockState> palette) {
            this.grid = grid;
            this.palette = palette;
            int n = palette.size() + 1;
            int[] kind = new int[n];
            int[] id = new int[n];
            for (int i = 1; i < n; i++) {
                BlockState st = palette.get(i - 1);
                kind[i] = st.canOcclude() ? de.minibits.core.MeshMaterials.OPAQUE
                    : (st.skipRendering(st, net.minecraft.core.Direction.NORTH)
                        ? de.minibits.core.MeshMaterials.SAME_ONLY : de.minibits.core.MeshMaterials.NONE);
                id[i] = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(st);
            }
            this.materials = new de.minibits.core.MeshMaterials(kind, id);
        }

        public BitGrid grid() { return grid; }
        public List<BlockState> palette() { return palette; }

        /** Flächennetz; wird neu berechnet, wenn sich einer der Nachbar-Schnappschüsse geändert hat. */
        public synchronized List<de.minibits.core.GreedyMesher.Face> faces(Snapshot[] neighbors) {
            if (faces != null && java.util.Arrays.equals(faceNeighbors, neighbors)) return faces;
            BitGrid[] grids = new BitGrid[6];
            de.minibits.core.MeshMaterials[] mats = new de.minibits.core.MeshMaterials[6];
            for (int i = 0; i < 6; i++) {
                if (neighbors[i] != null) {
                    grids[i] = neighbors[i].grid;
                    mats[i] = neighbors[i].materials;
                }
            }
            faces = de.minibits.core.GreedyMesher.mesh(grid, materials, grids, mats);
            faceNeighbors = neighbors.clone();
            return faces;
        }
    }
}
