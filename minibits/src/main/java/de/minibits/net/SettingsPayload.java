package de.minibits.net;

import de.minibits.MiniBits;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> Server: gewählte Form (Preset) und Drehung (0-3 x 90°). */
public record SettingsPayload(int preset, int rotation) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SettingsPayload> TYPE = new CustomPacketPayload.Type<>(MiniBits.id("settings"));
    public static final StreamCodec<ByteBuf, SettingsPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SettingsPayload::preset,
        ByteBufCodecs.VAR_INT, SettingsPayload::rotation,
        SettingsPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
