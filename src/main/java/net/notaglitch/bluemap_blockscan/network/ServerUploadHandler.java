package net.notaglitch.bluemap_blockscan.network;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.notaglitch.bluemap_blockscan.config.BlockscanConfig;
import net.notaglitch.bluemap_blockscan.network.payloads.FinishUploadPayload;
import net.notaglitch.bluemap_blockscan.network.payloads.StartUploadPayload;
import net.notaglitch.bluemap_blockscan.network.payloads.UploadChunkPayload;
import net.notaglitch.bluemap_blockscan.network.payloads.UploadStatusPayload;
import org.slf4j.Logger;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerUploadHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static class UploadSession {
        final Path tempFile;
        final FileOutputStream fos;
        final long totalBytes;
        final int totalChunks;
        long bytesReceived = 0;
        int chunksReceived = 0;

        UploadSession(Path tempFile, FileOutputStream fos, long totalBytes, int totalChunks) {
            this.tempFile = tempFile;
            this.fos = fos;
            this.totalBytes = totalBytes;
            this.totalChunks = totalChunks;
        }
    }

    private static final Map<UUID, UploadSession> ACTIVE_SESSIONS = new ConcurrentHashMap<>();

    public static void handleStart(StartUploadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            if (!player.hasPermissions(2)) {
                LOGGER.warn("Player {} attempted to upload BlueMap pack without OP permissions!", player.getScoreboardName());
                context.reply(new UploadStatusPayload(false, "Permission denied: OP level 2+ is required to upload BlueMap packs."));
                return;
            }

            try {
                Path destPath = Paths.get(BlockscanConfig.outputPackPath);
                if (destPath.getParent() != null) {
                    Files.createDirectories(destPath.getParent());
                }

                Path tempFile = destPath.resolveSibling("bluemap-blockscan.upload." + player.getUUID() + ".tmp");
                FileOutputStream fos = new FileOutputStream(tempFile.toFile());

                UploadSession session = new UploadSession(tempFile, fos, payload.totalBytes(), payload.totalChunks());
                ACTIVE_SESSIONS.put(player.getUUID(), session);

                LOGGER.info("Player {} started uploading BlueMap pack ({} bytes, {} chunks)...",
                        player.getScoreboardName(), payload.totalBytes(), payload.totalChunks());
            } catch (Exception e) {
                LOGGER.error("Failed to initialize upload session for {}", player.getScoreboardName(), e);
                context.reply(new UploadStatusPayload(false, "Server failed to initialize upload: " + e.getMessage()));
            }
        });
    }

    public static void handleChunk(UploadChunkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            UploadSession session = ACTIVE_SESSIONS.get(player.getUUID());
            if (session == null) {
                return;
            }

            try {
                session.fos.write(payload.data());
                session.bytesReceived += payload.data().length;
                session.chunksReceived++;
            } catch (IOException e) {
                LOGGER.error("Error writing chunk {} for player {}", payload.chunkIndex(), player.getScoreboardName(), e);
                cleanupSession(player.getUUID());
                context.reply(new UploadStatusPayload(false, "Failed writing chunk " + payload.chunkIndex() + ": " + e.getMessage()));
            }
        });
    }

    public static void handleFinish(FinishUploadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;

            UploadSession session = ACTIVE_SESSIONS.remove(player.getUUID());
            if (session == null) {
                context.reply(new UploadStatusPayload(false, "No active upload session found."));
                return;
            }

            try {
                session.fos.flush();
                session.fos.close();

                Path destPath = Paths.get(BlockscanConfig.outputPackPath);
                if (destPath.getParent() != null) {
                    Files.createDirectories(destPath.getParent());
                }

                try {
                    Files.move(session.tempFile, destPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(session.tempFile, destPath, StandardCopyOption.REPLACE_EXISTING);
                }

                LOGGER.info("Successfully received BlueMap pack from {} ({} bytes). Saved to {}",
                        player.getScoreboardName(), session.bytesReceived, destPath.toAbsolutePath());

                // Try running /bluemap reload on the server
                MinecraftServer server = player.getServer();
                if (server != null) {
                    CommandSourceStack stack = server.createCommandSourceStack().withSuppressedOutput();
                    try {
                        server.getCommands().performPrefixedCommand(stack, "bluemap reload");
                        LOGGER.info("Executed '/bluemap reload' following pack upload.");
                    } catch (Exception e) {
                        LOGGER.debug("Could not automatically run '/bluemap reload': {}", e.getMessage());
                    }
                }

                context.reply(new UploadStatusPayload(true, "BlueMap pack uploaded successfully to server and reloaded!"));

            } catch (Exception e) {
                LOGGER.error("Failed to finalize upload for player {}", player.getScoreboardName(), e);
                context.reply(new UploadStatusPayload(false, "Server error finalizing pack: " + e.getMessage()));
            }
        });
    }

    private static void cleanupSession(UUID uuid) {
        UploadSession session = ACTIVE_SESSIONS.remove(uuid);
        if (session != null) {
            try {
                session.fos.close();
                Files.deleteIfExists(session.tempFile);
            } catch (Exception ignored) {}
        }
    }
}
