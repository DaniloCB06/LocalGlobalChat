package com.example.plugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

public final class ClearChatCommand extends AbstractCommand {

    // Dê esse node apenas pro grupo admin no seu sistema de permissões
    private static final String PERM_NODE = "localglobalchat.admin.clearchat";

    private static final int CLEAR_LINES = 120;

    public ClearChatCommand() {
        super("clearchat", "Limpa o chat para todos os jogadores online");

        // Exige permissão (admin)
        LGChatCompat.requirePermissionNode(this, PERM_NODE);

        // (Opcional) alias /cc se o build suportar
        trySetAliases(this, "cc");

        // (Opcional) também tenta exigir nível alto (caso o seu build use níveis)
        trySetHighPermissionLevel(this, 4);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    @Nullable
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        // Console normalmente pode (equivalente a admin)
        if (context.sender().getUuid() != null) {
            if (!LGChatCompat.hasPermissionCompat(context.sender(), PERM_NODE)) {
                context.sender().sendMessage(LGChatCompat.pinkMessage("Sem permissao: " + PERM_NODE));
                return CompletableFuture.completedFuture(null);
            }
        }

        Message blank = Message.raw(" ");
        Message notice = buildNotice();

        int affected = 0;
        for (PlayerRef p : getOnlinePlayersCompat()) {
            if (p == null) continue;

            for (int i = 0; i < CLEAR_LINES; i++) {
                p.sendMessage(blank);
            }
            p.sendMessage(notice);
            affected++;
        }

        context.sender().sendMessage(Message.raw("Chat limpo para " + affected + " jogador(es)."));
        return CompletableFuture.completedFuture(null);
    }

    private static Message buildNotice() {
        String text = "Chat foi limpo pela administracao.";

        // tenta com TinyMsg (se existir), senão fallback raw
        String tiny = "<color:gray>" + LGChatCompat.tinySafe(text) + "</color>";
        Message parsed = LGChatCompat.tryTinyMsgParse(tiny);
        return (parsed != null) ? parsed : Message.raw(text);
    }

    // ---------- Online players (compat / reflection) ----------
    private static Iterable<PlayerRef> getOnlinePlayersCompat() {
        try {
            Class<?> uniCl = Class.forName("com.hypixel.hytale.server.core.universe.Universe");
            Object uni = uniCl.getMethod("get").invoke(null);
            if (uni == null) return java.util.List.of();

            for (String mn : new String[]{"getPlayers", "players", "getOnlinePlayers", "onlinePlayers"}) {
                try {
                    Method m = uni.getClass().getMethod(mn);
                    Object r = m.invoke(uni);
                    if (r instanceof Iterable<?> it) {
                        java.util.ArrayList<PlayerRef> out = new java.util.ArrayList<>();
                        for (Object o : it) if (o instanceof PlayerRef pr) out.add(pr);
                        return out;
                    }
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
        return java.util.List.of();
    }

    // ---------- Aliases (opcional) ----------
    private static void trySetAliases(Object cmd, String... aliases) {
        if (cmd == null || aliases == null || aliases.length == 0) return;

        for (String mn : new String[]{"setAliases", "addAlias", "addAliases", "alias", "aliases"}) {
            try {
                Method m = cmd.getClass().getMethod(mn, String[].class);
                m.invoke(cmd, (Object) aliases);
                return;
            } catch (Throwable ignored) { }

            try {
                Method m = cmd.getClass().getMethod(mn, String.class);
                for (String a : aliases) m.invoke(cmd, a);
                return;
            } catch (Throwable ignored) { }
        }
    }

    // ---------- Nível de permissão alto (opcional) ----------
    private static void trySetHighPermissionLevel(Object cmd, int level) {
        String[] names = {
                "setRequiredPermissionLevel", "setMinPermissionLevel",
                "setPermissionLevel", "setRequiredLevel", "setMinLevel"
        };

        for (String n : names) {
            try {
                Method m = cmd.getClass().getMethod(n, int.class);
                m.invoke(cmd, level);
                return;
            } catch (Throwable ignored) { }
            try {
                Method m = cmd.getClass().getMethod(n, short.class);
                m.invoke(cmd, (short) level);
                return;
            } catch (Throwable ignored) { }
        }
    }
}
