package net.notaglitch.bluemap_blockscan.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.notaglitch.bluemap_blockscan.config.BlockscanConfig;
import net.notaglitch.bluemap_blockscan.network.ClientUploadManager;
import net.notaglitch.bluemap_blockscan.scanner.BlockScanner;
import net.notaglitch.bluemap_blockscan.scanner.ScanOptions;

import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class BlockscanCommands {

    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("bluemap_blockscan")
                // /bluemap_blockscan scan
                .then(Commands.literal("scan")
                    .executes(ctx -> {
                        BlockScanner.startScan(ScanOptions.createDefault());
                        return 1;
                    })
                    .then(Commands.literal("--upload")
                        .executes(ctx -> {
                            BlockScanner.startScan(ScanOptions.createWithUpload());
                            return 1;
                        })
                    )
                    .then(Commands.argument("modid", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            Set<String> namespaces = BuiltInRegistries.BLOCK.keySet().stream()
                                    .map(net.minecraft.resources.ResourceLocation::getNamespace)
                                    .collect(Collectors.toCollection(TreeSet::new));
                            namespaces.add("all");
                            return SharedSuggestionProvider.suggest(namespaces, builder);
                        })
                        .executes(ctx -> {
                            String modid = StringArgumentType.getString(ctx, "modid");
                            if (modid.equalsIgnoreCase("all")) {
                                BlockScanner.startScan(new ScanOptions(null, false, false, false, null, null, null, null));
                            } else {
                                BlockScanner.startScan(ScanOptions.createForSingleMod(modid, false));
                            }
                            return 1;
                        })
                        .then(Commands.literal("--upload")
                            .executes(ctx -> {
                                String modid = StringArgumentType.getString(ctx, "modid");
                                if (modid.equalsIgnoreCase("all")) {
                                    BlockScanner.startScan(new ScanOptions(null, false, false, true, null, null, null, null));
                                } else {
                                    BlockScanner.startScan(ScanOptions.createForSingleMod(modid, true));
                                }
                                return 1;
                            })
                        )
                        .then(Commands.literal("--clean")
                            .executes(ctx -> {
                                BlockScanner.startScan(ScanOptions.createCleanFull(false));
                                return 1;
                            })
                            .then(Commands.literal("--upload")
                                .executes(ctx -> {
                                    BlockScanner.startScan(ScanOptions.createCleanFull(true));
                                    return 1;
                                })
                            )
                        )
                    )
                )
                // /bluemap_blockscan update
                .then(Commands.literal("update")
                    .executes(ctx -> {
                        BlockScanner.startScan(ScanOptions.createDefault());
                        return 1;
                    })
                    .then(Commands.literal("--upload")
                        .executes(ctx -> {
                            BlockScanner.startScan(ScanOptions.createWithUpload());
                            return 1;
                        })
                    )
                )
                // /bluemap_blockscan upload
                .then(Commands.literal("upload")
                    .executes(ctx -> {
                        ClientUploadManager.uploadPack(Paths.get(BlockscanConfig.outputPackPath));
                        return 1;
                    })
                )
                // /bluemap_blockscan list
                .then(Commands.literal("list")
                    .executes(ctx -> {
                        BlockScanner.listScannedMods(Paths.get(BlockscanConfig.outputPackPath));
                        return 1;
                    })
                )
                // /bluemap_blockscan help
                .then(Commands.literal("help")
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        source.sendSystemMessage(Component.literal("§6=== BlueMap Blockscan Commands ==="));
                        source.sendSystemMessage(Component.literal("§e/bluemap_blockscan update§7 - Scan newly added mods and merge them into your pack."));
                        source.sendSystemMessage(Component.literal("§e/bluemap_blockscan upload§7 - Upload your pack directly to the server & reload BlueMap (OP required)."));
                        source.sendSystemMessage(Component.literal("§e/bluemap_blockscan scan [--upload]§7 - Smart scan (auto-scans only new mods if a pack exists)."));
                        source.sendSystemMessage(Component.literal("§e/bluemap_blockscan scan <modid>§7 - Scan and update only ONE specific mod in the pack."));
                        source.sendSystemMessage(Component.literal("§e/bluemap_blockscan scan all --clean§7 - Wipe and rebuild the entire pack from scratch."));
                        source.sendSystemMessage(Component.literal("§e/bluemap_blockscan list§7 - Show scanned mods vs unscanned loaded mods."));
                        return 1;
                    })
                )
        );
    }
}
