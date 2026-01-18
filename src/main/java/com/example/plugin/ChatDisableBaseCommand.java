package com.example.plugin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.UUID;

abstract class ChatDisableBaseCommand extends CommandBase {

    protected final LocalGlobalChatPlugin plugin;

    protected ChatDisableBaseCommand(String name, String desc, LocalGlobalChatPlugin plugin) {
        super(name, desc);
        this.plugin = plugin;
    }

    protected boolean canUse(@Nonnull CommandContext context) {
        Object sender = extractSender(context);

        UUID senderUuid = extractUuid(sender);
        if (sender == null || senderUuid == null) return true;

        if (plugin.isChatAdmin(senderUuid)) return true;

        return isOperatorOrAdmin(sender, senderUuid);
    }

    protected void sendNoPermission(@Nonnull CommandContext context) {
        context.sendMessage(LocalGlobalChatPlugin.systemColor("red",
                "Only chatadmins or admins/operators can use this command. Ask a staff member to add you with /chatadmin add <player|uuid>."));
    }

    // ---------------- Sender / UUID ----------------

    private static Object extractSender(CommandContext context) {
        if (context == null) return null;
        try {
            Method m = context.getClass().getMethod("sender");
            return m.invoke(context);
        } catch (Throwable ignored) { }
        try {
            Method m = context.getClass().getMethod("getSender");
            return m.invoke(context);
        } catch (Throwable ignored) { }
        return null;
    }

    private static UUID extractUuid(Object sender) {
        if (sender == null) return null;

        if (sender instanceof PlayerRef pr) {
            try { return pr.getUuid(); } catch (Throwable ignored) { }
        }

        for (String mn : new String[]{"getUuid", "uuid", "getUniqueId", "uniqueId"}) {
            try {
                Method m = sender.getClass().getMethod(mn);
                Object r = m.invoke(sender);
                if (r instanceof UUID u) return u;
            } catch (Throwable ignored) { }
        }

        for (String mn : new String[]{"getPlayer", "player", "asPlayer"}) {
            try {
                Method m = sender.getClass().getMethod(mn);
                Object r = m.invoke(sender);
                if (r instanceof PlayerRef pr) {
                    try { return pr.getUuid(); } catch (Throwable ignored) { }
                }
            } catch (Throwable ignored) { }
        }

        return null;
    }

    // ---------------- OP/Admin detection (robust) ----------------

    private static boolean isOperatorOrAdmin(Object sender, UUID uuid) {
        if (sender != null && hasTrueBooleanMethod(sender,
                "isAdmin", "isOperator", "isOp", "isUniverseOperator", "isUniverseOp")) {
            return true;
        }

        PlayerRef pr = extractPlayerRef(sender);
        if (pr != null && hasTrueBooleanMethod(pr,
                "isAdmin", "isOperator", "isOp", "isUniverseOperator", "isUniverseOp")) {
            return true;
        }

        if (uuid != null && universeSaysOperator(uuid)) {
            return true;
        }

        return false;
    }

    private static PlayerRef extractPlayerRef(Object sender) {
        if (sender instanceof PlayerRef pr) return pr;
        if (sender == null) return null;

        for (String mn : new String[]{"getPlayer", "player", "asPlayer"}) {
            try {
                Method m = sender.getClass().getMethod(mn);
                Object r = m.invoke(sender);
                if (r instanceof PlayerRef pr) return pr;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private static boolean hasTrueBooleanMethod(Object obj, String... names) {
        for (String mn : names) {
            try {
                Method m = obj.getClass().getMethod(mn);
                Object r = m.invoke(obj);
                if (r instanceof Boolean b && b) return true;
            } catch (Throwable ignored) { }
        }
        return false;
    }

    private static boolean universeSaysOperator(UUID uuid) {
        try {
            Class<?> uniCl = Class.forName("com.hypixel.hytale.server.core.universe.Universe");
            Object uni = uniCl.getMethod("get").invoke(null);
            if (uni == null) return false;

            for (String mn : new String[]{"isOperator", "isOp", "isPlayerOperator", "isUniverseOperator"}) {
                try {
                    Method m = uni.getClass().getMethod(mn, UUID.class);
                    Object r = m.invoke(uni, uuid);
                    if (r instanceof Boolean b && b) return true;
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
        return false;
    }
}
