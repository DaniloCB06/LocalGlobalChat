package com.example.plugin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;

public class ChatDisableCommand extends CommandBase {

    private final LocalGlobalChatPlugin plugin;

    public ChatDisableCommand(LocalGlobalChatPlugin plugin) {
        super("chatdisable", "localglobalchat.commands.chardisable.desc");
        this.plugin = plugin;

        // Permissão (admins)
        LGChatCompat.requirePermissionNode(this, LocalGlobalChatPlugin.PERM_CHAT_DISABLE);

        // Alias /cdb (compatível via reflection)
        addAliasCompat("cdb");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        // Double-check (além do requiredPermission do comando)
        if (!senderHasPermission(context, LocalGlobalChatPlugin.PERM_CHAT_DISABLE)) {
            context.sendMessage(LocalGlobalChatPlugin.systemColor("red",
                    "Você não tem permissão para usar este comando."));
            return;
        }

        boolean nowDisabled = plugin.toggleChatDisabled();

        if (nowDisabled) {
            plugin.broadcastSystemMessage(LocalGlobalChatPlugin.systemColor("red",
                    "O chat foi desativado pela administração"));
        } else {
            plugin.broadcastSystemMessage(LocalGlobalChatPlugin.systemColor("green",
                    "O chat foi reativado pela administração"));
        }
    }

    private static boolean senderHasPermission(CommandContext context, String node) {
        try {
            // contexto.sender() -> hasPermission("node")
            Method senderM = context.getClass().getMethod("sender");
            Object sender = senderM.invoke(context);
            return LGChatCompat.hasPermissionCompat(sender, node);
        } catch (Throwable ignored) {
            // fallback: se o build não expõe sender(), deixa o servidor validar pelo permission node do comando
            return true;
        }
    }

    private void addAliasCompat(String alias) {
        String[] methods = {"addAlias", "addAliases", "setAlias", "setAliases"};

        for (String mn : methods) {
            // (String)
            try {
                Method m = this.getClass().getMethod(mn, String.class);
                m.invoke(this, alias);
                return;
            } catch (Throwable ignored) { }

            // (String[])
            try {
                Method m = this.getClass().getMethod(mn, String[].class);
                m.invoke(this, (Object) new String[]{alias});
                return;
            } catch (Throwable ignored) { }

            // (Collection)
            try {
                Method m = this.getClass().getMethod(mn, Collection.class);
                m.invoke(this, Arrays.asList(alias));
                return;
            } catch (Throwable ignored) { }
        }
    }
}
