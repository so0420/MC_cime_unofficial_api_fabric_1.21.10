package com.cime.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CimeMod implements ModInitializer {
    public static final String MOD_ID = "jamku-cime-connector";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CimeCommand.register();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ConnectionManager.onPlayerJoin(handler.getPlayer(), server);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ConnectionManager.onPlayerLeave(handler.getPlayer());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ConnectionManager.shutdown();
        });

        LOGGER.info("[Jamku CIME Connector] 모드 초기화 완료");
    }
}
