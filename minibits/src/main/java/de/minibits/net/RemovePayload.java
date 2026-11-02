package de.minibits.net;

import de.minibits.MiniBits;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> Server: Bit(s) an dieser Stelle eines Mini-Blocks entfernen (Bit-Koordinaten 0-15). */
public record RemovePayload(BlockPos pos, int bx, int by, int bz) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RemovePayload> TYPE = new CustomPacketPayload.Type<>(MiniBits.id("remove"));
    public static final StreamCodec<ByteBuf, RemovePayload> CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, RemovePayload::pos,
        ByteBufCodecs.VAR_INT, RemovePayload::bx,
        ByteBufCodecs.VAR_INT, RemovePayload::by,
        ByteBufCodecs.VAR_INT, RemovePayload::bz,
        RemovePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
