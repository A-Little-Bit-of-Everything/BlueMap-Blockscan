package net.notaglitch.bluemap_blockscan.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.notaglitch.bluemap_blockscan.Bluemap_blockscan;
import net.notaglitch.bluemap_blockscan.network.payloads.FinishUploadPayload;
import net.notaglitch.bluemap_blockscan.network.payloads.StartUploadPayload;
import net.notaglitch.bluemap_blockscan.network.payloads.UploadChunkPayload;
import net.notaglitch.bluemap_blockscan.network.payloads.UploadStatusPayload;

public class NetworkHandler {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Bluemap_blockscan.MODID).versioned("1.0.0").optional();

        registrar.playToServer(
                StartUploadPayload.TYPE,
                StartUploadPayload.STREAM_CODEC,
                ServerUploadHandler::handleStart
        );

        registrar.playToServer(
                UploadChunkPayload.TYPE,
                UploadChunkPayload.STREAM_CODEC,
                ServerUploadHandler::handleChunk
        );

        registrar.playToServer(
                FinishUploadPayload.TYPE,
                FinishUploadPayload.STREAM_CODEC,
                ServerUploadHandler::handleFinish
        );

        registrar.playToClient(
                UploadStatusPayload.TYPE,
                UploadStatusPayload.STREAM_CODEC,
                ClientUploadManager::handleStatus
        );
    }
}
