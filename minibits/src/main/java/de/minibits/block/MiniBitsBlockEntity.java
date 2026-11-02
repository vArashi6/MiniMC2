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

    /** Nach jeder Änderung aufrufen: speichern + Client/Rendering aktualisieren. */
    public void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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

    /** Wird vom Fabric-Renderer im Chunk-Thread gelesen (unveränderlicher Schnappschuss). */
    public Snapshot getRenderData() {
        return new Snapshot(grid.copy(), List.copyOf(palette));
    }

    public record Snapshot(BitGrid grid, List<BlockState> palette) {}
}
