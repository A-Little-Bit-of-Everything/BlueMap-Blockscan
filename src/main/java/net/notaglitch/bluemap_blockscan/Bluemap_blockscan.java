package net.notaglitch.bluemap_blockscan;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.notaglitch.bluemap_blockscan.command.BlockscanCommands;
import net.notaglitch.bluemap_blockscan.config.BlockscanConfig;
import net.notaglitch.bluemap_blockscan.network.NetworkHandler;
import org.slf4j.Logger;

@Mod(Bluemap_blockscan.MODID)
public class Bluemap_blockscan {
    public static final String MODID = "bluemap_blockscan";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Bluemap_blockscan(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Initializing BlueMap Blockscan for NeoForge 1.21.1...");

        // Register configuration
        modContainer.registerConfig(ModConfig.Type.CLIENT, BlockscanConfig.SPEC);
        modEventBus.addListener(this::onConfigLoad);

        // Register networking payloads on both client and server
        modEventBus.addListener(NetworkHandler::register);

        // Only register client-specific command listeners on physical client
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
        }
    }

    private void onConfigLoad(final ModConfigEvent event) {
        BlockscanConfig.onLoad(event);
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        BlockscanCommands.register(event);
    }
}
