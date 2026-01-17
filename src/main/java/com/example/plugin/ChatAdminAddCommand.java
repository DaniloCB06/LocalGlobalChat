package com.example.plugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.util.UUID;

public class ChatAdminAddCommand extends CommandBase {

    private final LocalGlobalChatPlugin plugin;

    private final RequiredArg<String> targetArg =
            this.withRequiredArg("target", "localglobalchat.commands.chatadmin.add.target", ArgTypes.STRING);

    public ChatAdminAddCommand(LocalGlobalChatPlugin plugin) {
        super("add", "localglobalchat.commands.chatadmin.add.desc");
        this.plugin = plugin;

        LGChatCompat.requirePermissionNode(this, LocalGlobalChatPlugin.PERM_CHAT_ADMIN);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!plugin.canUseChatAdmin(context)) {
            context.sendMessage(LocalGlobalChatPlugin.systemColor("red", "Você não tem permissão."));
            return;
        }

        String token = targetArg.get(context);
        UUID uuid = plugin.resolvePlayerOrUuid(token);

        if (uuid == null) {
            context.sendMessage(LocalGlobalChatPlugin.systemColor("red",
                    "Player não encontrado online e UUID inválido. Use um UUID para offline."));
            return;
        }

        boolean added = plugin.addChatAdmin(uuid);
        String name = plugin.resolveOnlineName(uuid);

        if (added) {
            context.sendMessage(LocalGlobalChatPlugin.systemColor("green",
                    "Adicionado ao chatadmin: " + uuid + (name != null ? " (" + name + ")" : "")));
        } else {
            context.sendMessage(Message.raw("Esse UUID já está na lista: " + uuid));
        }
    }
}
