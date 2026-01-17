package com.example.plugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class LGChatCompat {

    private LGChatCompat() {}

    // Rosa do /msg
    private static final String TINY_PINK_HEX = "#ff55ff";

    // =========================================================
    // TinyMsg helpers
    // =========================================================

    public static String tinySafe(String s) {
        if (s == null) return "";
        return s.replace("<", "‹").replace(">", "›");
    }

    @Nullable
    public static Message tryTinyMsgParse(String tinyText) {
        try {
            Class<?> tinyMsg = Class.forName("fi.sulku.hytale.TinyMsg");
            Method parse = tinyMsg.getMethod("parse", String.class);
            Object out = parse.invoke(null, tinyText);
            if (out instanceof Message) return (Message) out;
        } catch (Throwable ignored) { }
        return null;
    }

    public static Message pinkMessage(String plainText) {
        String safe = tinySafe(plainText);
        String tiny = "<color:" + TINY_PINK_HEX + ">" + safe + "</color>";
        Message parsed = tryTinyMsgParse(tiny);

        // fallback rosa (legacy)
        return (parsed != null) ? parsed : Message.raw("§d" + plainText + "§r");
    }

    // =========================================================
    // Permissões (reflection)
    // =========================================================

    public static void relaxCommandPermissions(Object cmd) {
        String[] methodNames = {
                "setRequiredPermissionLevel",
                "setMinPermissionLevel",
                "setPermissionLevel",
                "setPermission",
                "setPermissionNode",
                "setRequiredPermission",
                "requirePermission"
        };

        for (String mname : methodNames) {
            try {
                Method m = cmd.getClass().getMethod(mname, int.class);
                m.invoke(cmd, 0);
            } catch (Throwable ignored) { }
            try {
                Method m = cmd.getClass().getMethod(mname, short.class);
                m.invoke(cmd, (short) 0);
            } catch (Throwable ignored) { }
            try {
                Method m = cmd.getClass().getMethod(mname, String.class);
                m.invoke(cmd, new Object[]{null}); // mais seguro
            } catch (Throwable ignored) { }
        }

        String[] fieldNames = {
                "requiredPermissionLevel",
                "minPermissionLevel",
                "permissionLevel",
                "permission",
                "permissionNode",
                "requiredPermission"
        };

        for (String fname : fieldNames) {
            try {
                Field f = cmd.getClass().getDeclaredField(fname);
                f.setAccessible(true);

                if (f.getType() == int.class || f.getType() == Integer.class) f.set(cmd, 0);
                else if (f.getType() == short.class || f.getType() == Short.class) f.set(cmd, (short) 0);
                else if (f.getType() == String.class) f.set(cmd, null);
            } catch (Throwable ignored) { }
        }
    }

    public static void requirePermissionNode(Object cmd, String node) {
        String[] names = {
                "setPermissionNode",
                "setPermission",
                "setRequiredPermission",
                "setRequiredPermissionNode",
                "requirePermission"
        };

        for (String n : names) {
            try {
                Method m = cmd.getClass().getMethod(n, String.class);
                m.invoke(cmd, node);
                return;
            } catch (Throwable ignored) { }
        }

        String[] fields = {"permissionNode", "permission", "requiredPermission"};
        for (String fName : fields) {
            try {
                Field f = cmd.getClass().getDeclaredField(fName);
                f.setAccessible(true);
                if (f.getType() == String.class) {
                    f.set(cmd, node);
                    return;
                }
            } catch (Throwable ignored) { }
        }
    }

    public static boolean hasPermissionCompat(Object sender, String node) {
        // Mantém o comportamento antigo (fallback true) para comandos/console
        return hasPermissionCompat(sender, node, true);
    }

    public static boolean hasPermissionCompat(Object sender, String node, boolean fallbackIfUnknown) {
        if (sender == null) return false;

        String[] names = {"hasPermission", "hasPermissionNode", "hasPerm", "permission"};
        for (String n : names) {
            try {
                Method m = sender.getClass().getMethod(n, String.class);
                Object r = m.invoke(sender, node);
                if (r instanceof Boolean) return (Boolean) r;
            } catch (Throwable ignored) { }
        }

        // AQUI é a correção: quem decide o fallback é o chamador
        return fallbackIfUnknown;
    }


    // =========================================================
    // Resolver nome do remetente (CommandSender não tem getUsername)
    // =========================================================

    public static String resolveSenderUsername(Object sender, UUID senderUuid) {
        String name = tryInvokeString(sender, "getUsername", "getName", "name");
        if (name != null && !name.trim().isEmpty()) return name;

        PlayerRef pr = findOnlinePlayerByUuid(senderUuid);
        if (pr != null) {
            String u = pr.getUsername();
            if (u != null && !u.trim().isEmpty()) return u;
        }

        return "unknown";
    }

    @Nullable
    private static String tryInvokeString(Object obj, String... methodNames) {
        if (obj == null) return null;
        for (String mn : methodNames) {
            try {
                Method m = obj.getClass().getMethod(mn);
                Object r = m.invoke(obj);
                if (r instanceof String s) return s;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    // =========================================================
    // Buscar players online (reflection no Universe)
    // =========================================================

    @Nullable
    public static PlayerRef findOnlinePlayerByUsername(String username) {
        if (username == null) return null;
        String target = username.trim();
        if (target.isEmpty()) return null;

        try {
            Class<?> uniCl = Class.forName("com.hypixel.hytale.server.core.universe.Universe");
            Object uni = uniCl.getMethod("get").invoke(null);
            if (uni == null) return null;

            // 1) getPlayerByUsername(String)
            try {
                Method m = uni.getClass().getMethod("getPlayerByUsername", String.class);
                Object r = m.invoke(uni, target);
                PlayerRef pr = unwrapPlayerRef(r);
                if (pr != null) return pr;
            } catch (Throwable ignored) { }

            // 2) métodos alternativos
            for (String mn : new String[]{"findPlayerByUsername", "getPlayerByName", "findPlayerByName"}) {
                try {
                    Method m = uni.getClass().getMethod(mn, String.class);
                    Object r = m.invoke(uni, target);
                    PlayerRef pr = unwrapPlayerRef(r);
                    if (pr != null) return pr;
                } catch (Throwable ignored) { }
            }

            // 3) fallback: iterar players online
            for (Method m : uni.getClass().getMethods()) {
                try {
                    String name = m.getName();
                    if (!(name.equals("getPlayers")
                            || name.equals("players")
                            || name.equals("getOnlinePlayers")
                            || name.equals("onlinePlayers"))) {
                        continue;
                    }
                    if (m.getParameterCount() != 0) continue;

                    Object r = m.invoke(uni);
                    if (r instanceof Iterable<?> it) {
                        for (Object o : it) {
                            if (o instanceof PlayerRef p) {
                                String u = p.getUsername();
                                if (u != null && u.equalsIgnoreCase(target)) return p;
                            }
                        }
                    }
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }

        return null;
    }

    @Nullable
    public static PlayerRef findOnlinePlayerByUuid(UUID uuid) {
        if (uuid == null) return null;

        try {
            Class<?> uniCl = Class.forName("com.hypixel.hytale.server.core.universe.Universe");
            Object uni = uniCl.getMethod("get").invoke(null);
            if (uni == null) return null;

            // tenta getPlayer/findPlayer(UUID)
            for (Method m : uni.getClass().getMethods()) {
                try {
                    if (m.getParameterCount() != 1) continue;
                    if (m.getParameterTypes()[0] != UUID.class) continue;

                    String n = m.getName().toLowerCase(Locale.ROOT);
                    if (!(n.contains("getplayer") || n.contains("findplayer"))) continue;

                    Object r = m.invoke(uni, uuid);
                    PlayerRef pr = unwrapPlayerRef(r);
                    if (pr != null) return pr;
                } catch (Throwable ignored) { }
            }

            // fallback: iterar players online
            for (Method m : uni.getClass().getMethods()) {
                try {
                    String name = m.getName();
                    if (!(name.equals("getPlayers")
                            || name.equals("players")
                            || name.equals("getOnlinePlayers")
                            || name.equals("onlinePlayers"))) {
                        continue;
                    }
                    if (m.getParameterCount() != 0) continue;

                    Object r = m.invoke(uni);
                    if (r instanceof Iterable<?> it) {
                        for (Object o : it) {
                            if (o instanceof PlayerRef p) {
                                if (uuid.equals(p.getUuid())) return p;
                            }
                        }
                    }
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }

        return null;
    }

    @Nullable
    private static PlayerRef unwrapPlayerRef(Object maybe) {
        if (maybe == null) return null;
        if (maybe instanceof PlayerRef) return (PlayerRef) maybe;
        if (maybe instanceof Optional<?> opt) {
            Object v = opt.orElse(null);
            if (v instanceof PlayerRef) return (PlayerRef) v;
        }
        return null;
    }
}