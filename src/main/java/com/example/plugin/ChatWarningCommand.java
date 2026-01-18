package com.example.plugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public final class ChatWarningCommand extends AbstractCommand {

    private final LocalGlobalChatPlugin plugin;
    private final RequiredArg<Integer> minutesArg;

    public ChatWarningCommand(LocalGlobalChatPlugin plugin) {
        super("chatwarning", "Configures the periodic chat warning (/cw <minutes>, 0 = disable)");
        this.plugin = plugin;

        // Allow execution even without a permission provider; we enforce admin-only manually below
        LGChatCompat.relaxCommandPermissions(this);

        addAliases("cw");

        minutesArg = withRequiredArg(
                "minutes",
                "Interval in minutes (0 = disable)",
                ArgTypes.INTEGER
        );
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    @Nullable
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        // Admin/op/console only
        if (!plugin.canUseChatAdmin(context)) {
            context.sender().sendMessage(Message.raw("You don't have permission to use this command."));
            return CompletableFuture.completedFuture(null);
        }

        int mins;
        try {
            mins = context.get(minutesArg);
        } catch (Throwable t) {
            mins = 0;
        }

        plugin.setChatWarningMinutes(mins);
        context.sender().sendMessage(plugin.buildChatWarningConfigFeedback(mins));

        return CompletableFuture.completedFuture(null);
    }
}
