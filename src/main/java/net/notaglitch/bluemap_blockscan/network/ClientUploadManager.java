package net.notaglitch.bluemap_blockscan.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.notaglitch.bluemap_blockscan.network.payloads.FinishUploadPayload;
import net.notaglitch.bluemap_blockscan.network.payloads.StartUploadPayload;
import net.notaglitch.bluemap_blockscan.network.payloads.UploadChunkPayload;
import net.notaglitch.bluemap_blockscan.network.payloads.UploadStatusPayload;
import net.notaglitch.bluemap_blockscan.scanner.BlockScanner;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientUploadManager {
    private static final AtomicBoolean UPLOADING = new AtomicBoolean(false);

    public static void handleStatus(UploadStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            UPLOADING.set(false);
            if (payload.success()) {
                BlockScanner.sendFeedback(Component.literal("§a✔ [Server] " + payload.message()));
            } else {
                BlockScanner.sendFeedback(Component.literal("§c✖ [Server] " + payload.message()));
            }
        });
    }

    public static void uploadPack(Path zipPath) {
        if (!Files.exists(zipPath)) {
            BlockScanner.sendFeedback(Component.literal("Cannot upload: pack does not exist at " + zipPath).withStyle(ChatFormatting.RED));
            return;
        }

        if (!UPLOADING.compareAndSet(false, true)) {
            BlockScanner.sendFeedback(Component.literal("An upload is already in progress!").withStyle(ChatFormatting.YELLOW));
            return;
        }

        Thread uploadThread = new Thread(() -> {
            try {
                long totalBytes = Files.size(zipPath);
                int chunkSize = 32 * 1024; // 32 KB per chunk
                int totalChunks = (int) Math.ceil((double) totalBytes / chunkSize);

                BlockScanner.sendFeedback(Component.literal("Starting upload of BlueMap pack (" + (totalBytes / 1024) + " KB) to server...")
                        .withStyle(ChatFormatting.AQUA));

                PacketDistributor.sendToServer(new StartUploadPayload(totalBytes, totalChunks));

                try (InputStream is = Files.newInputStream(zipPath)) {
                    byte[] buffer = new byte[chunkSize];
                    int bytesRead;
                    int chunkIndex = 0;

                    while ((bytesRead = is.read(buffer)) != -1) {
                        byte[] chunkData;
                        if (bytesRead == chunkSize) {
                            chunkData = buffer.clone();
                        } else {
                            chunkData = new byte[bytesRead];
                            System.arraycopy(buffer, 0, chunkData, 0, bytesRead);
                        }

                        PacketDistributor.sendToServer(new UploadChunkPayload(chunkIndex, chunkData));
                        chunkIndex++;

                        if (chunkIndex % 20 == 0 || chunkIndex == totalChunks) {
                            int percent = (int) ((chunkIndex / (float) totalChunks) * 100);
                            BlockScanner.sendFeedback(Component.literal(String.format("Uploading to server: %d/%d chunks (%d%%)...", chunkIndex, totalChunks, percent))
                                    .withStyle(ChatFormatting.GRAY));
                        }

                        // Slight delay every 10 chunks to avoid flooding TCP buffers
                        if (chunkIndex % 10 == 0) {
                            Thread.sleep(15);
                        }
                    }
                }

                PacketDistributor.sendToServer(new FinishUploadPayload());
                BlockScanner.sendFeedback(Component.literal("All data sent to server! Awaiting verification and reload...")
                        .withStyle(ChatFormatting.YELLOW));

            } catch (Exception e) {
                UPLOADING.set(false);
                BlockScanner.sendFeedback(Component.literal("Upload error: " + e.getMessage()).withStyle(ChatFormatting.RED));
            }
        }, "BlueMap-Blockscan-Uploader");

        uploadThread.setDaemon(true);
        uploadThread.start();
    }
}
