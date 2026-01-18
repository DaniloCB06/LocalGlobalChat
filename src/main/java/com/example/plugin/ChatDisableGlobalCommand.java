package com.example.plugin;

import com.hypixel.hytale.server.core.command.system.CommandContext;

import javax.annotation.Nonnull;

public class ChatDisableGlobalCommand extends ChatDisableBaseCommand {

    public ChatDisableGlobalCommand(LocalGlobalChatPlugin plugin) {
        super("global", "Disables/enables global chat.", plugin);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!canUse(context)) {
            sendNoPermission(context);
            return;
        }

        boolean nowDisabled = plugin.toggleGlobalChatDisabled();

        if (nowDisabled) {
            plugin.broadcastSystemMessage(LocalGlobalChatPlugin.systemColor("red",
                    "Global chat has been disabled by the staff."));
        } else {
            plugin.broadcastSystemMessage(LocalGlobalChatPlugin.systemColor("green",
                    "Global chat has been re-enabled by the staff."));
        }
    }
}
