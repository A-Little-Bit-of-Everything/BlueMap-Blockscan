package net.notaglitch.bluemap_blockscan.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.notaglitch.bluemap_blockscan.Bluemap_blockscan;

public record UploadChunkPayload(int chunkIndex, byte[] data) implements CustomPacketPayload {
    public static final Type<UploadChunkPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Bluemap_blockscan.MODID, "upload_chunk"));

    public static final StreamCodec<FriendlyByteBuf, UploadChunkPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.chunkIndex);
                buf.writeByteArray(payload.data);
            },
            buf -> new UploadChunkPayload(buf.readVarInt(), buf.readByteArray())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
