package net.notaglitch.bluemap_blockscan.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.notaglitch.bluemap_blockscan.Bluemap_blockscan;

public record StartUploadPayload(long totalBytes, int totalChunks) implements CustomPacketPayload {
    public static final Type<StartUploadPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Bluemap_blockscan.MODID, "start_upload"));

    public static final StreamCodec<FriendlyByteBuf, StartUploadPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarLong(payload.totalBytes);
                buf.writeVarInt(payload.totalChunks);
            },
            buf -> new StartUploadPayload(buf.readVarLong(), buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
