package com.cime.mod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class CimeCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("cime")

                // /cime connect <slug>
                .then(CommandManager.literal("connect")
                    .then(CommandManager.argument("slug", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            String slug = StringArgumentType.getString(ctx, "slug");
                            ConnectionManager.connect(player, slug, ctx.getSource().getServer());
                            player.sendMessage(Text.literal("§e[CIME] " + slug + " 채널 연결을 시도합니다."));
                            return 1;
                        })
                    )
                )

                // /cime disconnect
                .then(CommandManager.literal("disconnect")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        boolean removed = ConnectionManager.disconnect(player.getUuid());
                        if (removed) {
                            player.sendMessage(Text.literal("§e[CIME] 연결이 해제되었습니다."));
                        } else {
                            player.sendMessage(Text.literal("§c[CIME] 연결된 채널이 없습니다."));
                        }
                        return 1;
                    })
                )

                // /cime status
                .then(CommandManager.literal("status")
                    .executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                        String status = ConnectionManager.getStatus(player.getUuid());
                        player.sendMessage(Text.literal(status));
                        return 1;
                    })
                )

                // /cime op connect <player> <slug>
                .then(CommandManager.literal("op")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.literal("connect")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("slug", StringArgumentType.word())
                                .executes(ctx -> {
                                    String playerName = StringArgumentType.getString(ctx, "player");
                                    String slug = StringArgumentType.getString(ctx, "slug");
                                    ServerPlayerEntity target = ctx.getSource().getServer()
                                            .getPlayerManager().getPlayer(playerName);
                                    if (target == null) {
                                        ctx.getSource().sendError(Text.literal("§c[CIME] 플레이어 " + playerName + "을(를) 찾을 수 없습니다."));
                                        return 0;
                                    }
                                    ConnectionManager.connect(target, slug, ctx.getSource().getServer());
                                    ctx.getSource().sendFeedback(() -> Text.literal("§e[CIME] " + playerName + " → " + slug + " 채널 연결을 시도합니다."), false);
                                    target.sendMessage(Text.literal("§e[CIME] " + slug + " 채널 연결을 시도합니다."));
                                    return 1;
                                })
                            )
                        )
                    )
                )

                // /cime test <player> <amount>
                .then(CommandManager.literal("test")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> {
                                String playerName = StringArgumentType.getString(ctx, "player");
                                int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                ServerPlayerEntity target = ctx.getSource().getServer()
                                        .getPlayerManager().getPlayer(playerName);
                                if (target == null) {
                                    ctx.getSource().sendError(Text.literal("§c[CIME] 플레이어 " + playerName + "을(를) 찾을 수 없습니다."));
                                    return 0;
                                }
                                String formatted = String.format("%,d", amount);
                                target.sendMessage(Text.literal("§b[CIME] " + formatted + "원 후원 받았습니다."));
                                ctx.getSource().sendFeedback(() -> Text.literal("§a[CIME] 테스트 후원 메시지를 " + playerName + "에게 전송했습니다."), false);
                                return 1;
                            })
                        )
                    )
                )
            );
        });
    }
}
