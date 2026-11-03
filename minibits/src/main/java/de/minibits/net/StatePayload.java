package de.minibits.net;

import de.minibits.MiniBits;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> Client: aktuell gewählte Form und Drehung (für die Einblendung). */
public record StatePayload(int preset, int rotation) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StatePayload> TYPE = new CustomPacketPayload.Type<>(MiniBits.id("state"));
    public static final StreamCodec<ByteBuf, StatePayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, StatePayload::preset,
        ByteBufCodecs.VAR_INT, StatePayload::rotation,
        StatePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
