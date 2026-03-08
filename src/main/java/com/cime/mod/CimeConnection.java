package com.cime.mod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.concurrent.*;

public class CimeConnection {
    private static final String WS_URI = "wss://edge.ivschat.ap-northeast-2.amazonaws.com/";
    private static final String API_BASE = "https://ci.me/api/app";
    private static final long RECONNECT_DELAY_MS = 15000;
    private static final long SID_CHECK_INTERVAL_MS = 30000;

    private final java.util.UUID playerUuid;
    private final String slug;
    private final MinecraftServer server;
    private final HttpClient httpClient;

    // connectLoop 전용 스레드
    private final Thread connectThread;
    // SID 체크 전용 스케줄러
    private final ScheduledExecutorService sidChecker;

    private volatile WebSocket webSocket;
    private volatile boolean active = true;
    private volatile boolean connected = false;
    private volatile String currentSid;

    public CimeConnection(java.util.UUID playerUuid, String slug, MinecraftServer server) {
        this.playerUuid = playerUuid;
        this.slug = slug;
        this.server = server;
        this.httpClient = HttpClient.newHttpClient();

        this.connectThread = new Thread(this::connectLoop,
                "CIME-connect-" + slug + "-" + playerUuid.toString().substring(0, 8));
        this.connectThread.setDaemon(true);

        this.sidChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CIME-sid-" + slug + "-" + playerUuid.toString().substring(0, 8));
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        connectThread.start();
        sidChecker.scheduleAtFixedRate(this::checkBroadcastRestart,
                SID_CHECK_INTERVAL_MS, SID_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        active = false;
        connected = false;
        connectThread.interrupt();
        sidChecker.shutdownNow();
        WebSocket ws = webSocket;
        if (ws != null) {
            try { ws.abort(); } catch (Exception ignored) {}
        }
    }

    public boolean isConnected() {
        return connected;
    }

    private void connectLoop() {
        while (active) {
            try {
                String[] tokenAndSid = fetchChatToken();
                if (tokenAndSid == null || tokenAndSid[0] == null) {
                    Thread.sleep(RECONNECT_DELAY_MS);
                    continue;
                }

                currentSid = tokenAndSid[1];
                doConnect(tokenAndSid[0]);

                // 연결이 끊어질 때까지 대기
                while (connected && active) {
                    Thread.sleep(1000);
                }

                if (!active) break;

                sendPlayerMessage("§e[CIME] " + slug + " 채널 연결이 끊어졌습니다. 재연결 대기 중...");
                Thread.sleep(RECONNECT_DELAY_MS);

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                CimeMod.LOGGER.warn("[CIME] 연결 오류 ({}): {}", slug, e.getMessage());
                try { Thread.sleep(RECONNECT_DELAY_MS); } catch (InterruptedException ie) { break; }
            }
        }
    }

    private void doConnect(String token) throws Exception {
        // 이전 WebSocket이 남아있으면 정리
        WebSocket oldWs = webSocket;
        if (oldWs != null) {
            try { oldWs.abort(); } catch (Exception ignored) {}
            webSocket = null;
        }

        CompletableFuture<WebSocket> future = httpClient.newWebSocketBuilder()
                .subprotocols(token)
                .buildAsync(URI.create(WS_URI), new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket ws) {
                        // 이미 비활성이면 즉시 닫기 (타임아웃 후 뒤늦게 연결된 경우)
                        if (!active) {
                            ws.abort();
                            return;
                        }
                        webSocket = ws;
                        connected = true;
                        sendPlayerMessage("§a[CIME] " + slug + " 채널에 연결되었습니다.");
                        ws.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        // 현재 활성 WebSocket이 아니면 무시
                        if (ws != webSocket) {
                            ws.abort();
                            return null;
                        }
                        buffer.append(data);
                        if (last) {
                            String message = buffer.toString();
                            buffer.setLength(0);
                            handleMessage(message);
                        }
                        ws.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        if (ws == webSocket) {
                            connected = false;
                        }
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        if (ws == webSocket) {
                            connected = false;
                        }
                    }
                });

        try {
            webSocket = future.get(15, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 타임아웃 시 비동기로 완료되면 정리하도록 설정
            future.thenAccept(ws -> {
                if (ws != webSocket) {
                    ws.abort();
                }
            });
            throw e;
        }
    }

    private void checkBroadcastRestart() {
        if (!active || !connected) return;
        try {
            String[] tokenAndSid = fetchChatToken();
            if (tokenAndSid != null && tokenAndSid[1] != null
                    && currentSid != null && !tokenAndSid[1].equals(currentSid)) {
                CimeMod.LOGGER.info("[CIME] 방송 재시작 감지: {} (SID 변경)", slug);
                sendPlayerMessage("§e[CIME] " + slug + " 방송이 재시작되어 재연결합니다.");
                connected = false;
                WebSocket ws = webSocket;
                if (ws != null) {
                    try { ws.abort(); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }

    private String[] fetchChatToken() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/channels/" + slug + "/chat-token"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .header("Content-Type", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject data = root.getAsJsonObject("data");
            if (data == null) return null;

            String token = getStringOrNull(data, "token");
            String sid = getStringOrNull(data, "sid");
            return new String[]{token, sid};

        } catch (Exception e) {
            CimeMod.LOGGER.warn("[CIME] 토큰 발급 실패 ({}): {}", slug, e.getMessage());
            return null;
        }
    }

    private void handleMessage(String raw) {
        try {
            JsonObject data = JsonParser.parseString(raw).getAsJsonObject();
            String type = getStringOrNull(data, "Type");
            if (!"EVENT".equals(type)) return;

            String eventName = getStringOrNull(data, "EventName");
            if (!"DONATION_CHAT".equals(eventName)) return;

            JsonObject attrs = data.getAsJsonObject("Attributes");
            if (attrs == null) return;

            String extraRaw = getStringOrNull(attrs, "extra");
            if (extraRaw == null) return;

            JsonObject extra = JsonParser.parseString(extraRaw).getAsJsonObject();
            JsonElement amtEl = extra.get("amt");
            if (amtEl == null || !amtEl.isJsonPrimitive()) return;

            long amount = amtEl.getAsLong();
            String formatted = String.format("%,d", amount);
            sendPlayerMessage("§b[CIME] " + formatted + "원 후원 받았습니다.");

        } catch (Exception e) {
            // 파싱 실패 시 무시
        }
    }

    private void sendPlayerMessage(String message) {
        server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
            if (player != null) {
                player.sendMessage(Text.literal(message));
            }
        });
    }

    private static String getStringOrNull(JsonObject obj, String key) {
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull()) return null;
        return el.getAsString();
    }
}
