package net.notaglitch.bluemap_blockscan.network.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.notaglitch.bluemap_blockscan.Bluemap_blockscan;

public record FinishUploadPayload() implements CustomPacketPayload {
    public static final Type<FinishUploadPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Bluemap_blockscan.MODID, "finish_upload"));

    public static final StreamCodec<FriendlyByteBuf, FinishUploadPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {},
            buf -> new FinishUploadPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
