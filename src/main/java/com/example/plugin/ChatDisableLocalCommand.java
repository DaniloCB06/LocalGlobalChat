package com.example.plugin;

import com.hypixel.hytale.server.core.command.system.CommandContext;

import javax.annotation.Nonnull;

public class ChatDisableLocalCommand extends ChatDisableBaseCommand {

    public ChatDisableLocalCommand(LocalGlobalChatPlugin plugin) {
        super("local", "Disables/enables local chat.", plugin);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!canUse(context)) {
            sendNoPermission(context);
            return;
        }

        boolean nowDisabled = plugin.toggleLocalChatDisabled();

        if (nowDisabled) {
            plugin.broadcastSystemMessage(LocalGlobalChatPlugin.systemColor("red",
                    "Local chat has been disabled by the staff."));
        } else {
            plugin.broadcastSystemMessage(LocalGlobalChatPlugin.systemColor("green",
                    "Local chat has been re-enabled by the staff."));
        }
    }
}
