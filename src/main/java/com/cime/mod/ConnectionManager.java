package com.cime.mod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager {
    // 활성 WebSocket 연결
    private static final Map<UUID, CimeConnection> activeConnections = new ConcurrentHashMap<>();
    // 플레이어-슬러그 매핑 (접속 해제 후에도 유지, 재접속 시 자동 연결용)
    private static final Map<UUID, String> slugMappings = new ConcurrentHashMap<>();

    public static void connect(ServerPlayerEntity player, String slug, MinecraftServer server) {
        UUID uuid = player.getUuid();

        // 기존 연결 해제
        CimeConnection old = activeConnections.remove(uuid);
        if (old != null) {
            old.stop();
        }

        slugMappings.put(uuid, slug);

        CimeConnection conn = new CimeConnection(uuid, slug, server);
        activeConnections.put(uuid, conn);
        conn.start();
    }

    public static boolean disconnect(UUID uuid) {
        String slug = slugMappings.remove(uuid);
        CimeConnection conn = activeConnections.remove(uuid);
        if (conn != null) {
            conn.stop();
        }
        return slug != null;
    }

    public static void onPlayerJoin(ServerPlayerEntity player, MinecraftServer server) {
        UUID uuid = player.getUuid();
        String slug = slugMappings.get(uuid);
        if (slug != null) {
            // 기존 연결이 남아있으면 정리
            CimeConnection old = activeConnections.remove(uuid);
            if (old != null) {
                old.stop();
            }
            // 저장된 연결 정보가 있으면 자동 재연결
            CimeConnection conn = new CimeConnection(uuid, slug, server);
            activeConnections.put(uuid, conn);
            conn.start();
        }
    }

    public static void onPlayerLeave(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        CimeConnection conn = activeConnections.remove(uuid);
        if (conn != null) {
            conn.stop();
        }
        // slugMappings는 유지 → 재접속 시 자동 연결
    }

    public static String getStatus(UUID uuid) {
        String slug = slugMappings.get(uuid);
        if (slug == null) {
            return "§7[CIME] 연결된 채널이 없습니다.";
        }
        CimeConnection conn = activeConnections.get(uuid);
        if (conn != null && conn.isConnected()) {
            return "§a[CIME] " + slug + " 채널에 연결되었습니다.";
        }
        return "§e[CIME] " + slug + " 채널이 등록되어 있습니다. 연결을 시도중입니다.";
    }

    public static void shutdown() {
        for (CimeConnection conn : activeConnections.values()) {
            conn.stop();
        }
        activeConnections.clear();
    }
}
