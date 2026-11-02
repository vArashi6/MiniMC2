package de.minibits;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Inhalt eines kompletten Mini-Blocks (16x16x16 Bits + Palette), z. B. zum Kopieren per Mittelklick. */
public record MiniData(List<Integer> bits, List<BlockState> palette) {
    public static final Codec<MiniData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.listOf().fieldOf("bits").forGetter(MiniData::bits),
        BlockState.CODEC.listOf().fieldOf("palette").forGetter(MiniData::palette)
    ).apply(i, MiniData::new));
}
