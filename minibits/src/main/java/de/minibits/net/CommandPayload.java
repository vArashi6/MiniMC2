package de.minibits.net;

import de.minibits.MiniBits;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> Server: 0 = nächste Form, 1 = vorherige Form, 2 = um 90° drehen. */
public record CommandPayload(int cmd) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CommandPayload> TYPE = new CustomPacketPayload.Type<>(MiniBits.id("command"));
    public static final StreamCodec<ByteBuf, CommandPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, CommandPayload::cmd,
        CommandPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
