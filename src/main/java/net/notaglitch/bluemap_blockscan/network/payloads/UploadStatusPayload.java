package net.notaglitch.bluemap_blockscan.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.notaglitch.bluemap_blockscan.Bluemap_blockscan;

public record UploadStatusPayload(boolean success, String message) implements CustomPacketPayload {
    public static final Type<UploadStatusPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Bluemap_blockscan.MODID, "upload_status"));

    public static final StreamCodec<FriendlyByteBuf, UploadStatusPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.success);
                buf.writeUtf(payload.message);
            },
            buf -> new UploadStatusPayload(buf.readBoolean(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
