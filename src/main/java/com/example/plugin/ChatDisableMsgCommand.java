package com.example.plugin;

import com.hypixel.hytale.server.core.command.system.CommandContext;

import javax.annotation.Nonnull;

public class ChatDisableMsgCommand extends ChatDisableBaseCommand {

    public ChatDisableMsgCommand(LocalGlobalChatPlugin plugin) {
        super("msg", "Disables/enables private messages.", plugin);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!canUse(context)) {
            sendNoPermission(context);
            return;
        }

        boolean nowDisabled = plugin.toggleMsgDisabled();

        if (nowDisabled) {
            plugin.broadcastSystemMessage(LocalGlobalChatPlugin.systemColor("red",
                    "Private messages have been disabled by the staff."));
        } else {
            plugin.broadcastSystemMessage(LocalGlobalChatPlugin.systemColor("green",
                    "Private messages have been re-enabled by the staff."));
        }
    }
}
