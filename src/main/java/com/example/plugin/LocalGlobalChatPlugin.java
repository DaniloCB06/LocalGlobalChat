package com.example.plugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class LocalGlobalChatPlugin extends JavaPlugin {

    // =========================
    // Persistência
    // =========================
    private static final String CONFIG_FILE_NAME = "localglobalchat.properties";
    private static final String PROP_LOCAL_RADIUS = "localRadius";
    private static final String PROP_CHAT_ADMINS = "chatAdmins"; // csv de UUIDs

    // =========================
    // Raio configurável do chat local (default: 50 blocos)
    // =========================
    private static volatile double localRadius = 50.0;
    private static volatile double localRadiusSq = localRadius * localRadius;

    // TinyMessage/TinyMsg (se estiver instalado no servidor)
    private static final String TINY_GLOBAL = "green";
    private static final String TINY_LOCAL = "yellow";
    private static final String TINY_TEXT = "white";

    // modo do chat por jogador
    private final Map<UUID, ChatMode> chatModes = new ConcurrentHashMap<>();

    // debug por jogador (toggle /chatdebug)
    private final Map<UUID, Boolean> debugModes = new ConcurrentHashMap<>();

    // =========================
    // Chat Disable (global)
    // =========================
    public static final String PERM_CHAT_DISABLE = "localglobalchat.chatdisable";
    public static final String PERM_CHAT_BYPASS  = "localglobalchat.chatdisable.bypass";

    // Admin list (bypass confiável via disco)
    public static final String PERM_CHAT_ADMIN   = "localglobalchat.chatadmin";

    private final AtomicBoolean chatDisabled = new AtomicBoolean(false);

    // ChatAdmins persistidos em disco (bypass e perm de admin do plugin)
    private final Set<UUID> chatAdmins = ConcurrentHashMap.newKeySet();

    public LocalGlobalChatPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // carrega config (localRadius + chatAdmins) antes de registrar comandos e antes do chat rodar
        loadConfigFromDisk();

        getCommandRegistry().registerCommand(new GCommand(this));
        getCommandRegistry().registerCommand(new LCommand(this));
        getCommandRegistry().registerCommand(new MsgCommand());
        getCommandRegistry().registerCommand(new ChatDebugCommand(this));
        getCommandRegistry().registerCommand(new LocalRadiusCommand(this));
        getCommandRegistry().registerCommand(new ClearChatCommand());
        getCommandRegistry().registerCommand(new ChatDisableCommand(this));

        // >>> IMPORTANTE: registre SOMENTE o comando raiz (collection) /chatadmin
        // (não registre ChatAdminAddCommand/Remove/List direto no registry)
        getCommandRegistry().registerCommand(new ChatAdminCommand(this));

        // Registro do chat via reflection
        registerEventListener(PlayerChatEvent.class, ev -> onChat((PlayerChatEvent) ev));

        // (Opcional) tenta resetar para LOCAL quando o jogador entrar (se existir evento)
        tryRegisterJoinResetToLocal();
    }

    // =========================================================
    // API usada pelos comandos (modo/debug/radius)
    // =========================================================

    ChatMode getMode(UUID uuid) {
        return chatModes.getOrDefault(uuid, ChatMode.LOCAL);
    }

    void setMode(UUID uuid, ChatMode mode) {
        chatModes.put(uuid, mode);
    }

    boolean isDebug(UUID uuid) {
        return debugModes.getOrDefault(uuid, false);
    }

    void toggleDebug(UUID uuid) {
        debugModes.put(uuid, !isDebug(uuid));
    }

    // persiste no disco
    void setLocalRadius(int blocks) {
        applyLocalRadius(blocks);
        saveConfigToDisk();
    }

    int getLocalRadiusInt() {
        return (int) Math.round(localRadius);
    }

    // =========================================================
    // CHATADMINS API (usado por /chatadmin add/remove/list)
    // =========================================================

    public boolean canUseChatAdmin(CommandContext context) {
        Object sender = extractSender(context);

        // Console sempre pode
        if (sender == null || !(sender instanceof PlayerRef)) return true;

        PlayerRef p = (PlayerRef) sender;
        UUID uuid = safeUuid(p);
        if (uuid != null && isChatAdmin(uuid)) return true;

        // tenta permissões (se o provider expuser)
        if (LGChatCompat.hasPermissionCompat(sender, PERM_CHAT_ADMIN)) return true;
        if (LGChatCompat.hasPermissionCompat(sender, PERM_CHAT_DISABLE)) return true;

        // tenta op/admin via reflection
        return isAdminLevelCompat(p) || isUniverseOpCompat(p);
    }

    public UUID resolvePlayerOrUuid(String token) {
        if (token == null) return null;
        String t = token.trim();
        if (t.isEmpty()) return null;

        // UUID direto
        try {
            return UUID.fromString(t);
        } catch (Throwable ignored) { }

        // Username online
        PlayerRef pr = findOnlinePlayerByUsername(t);
        if (pr != null) return safeUuid(pr);

        return null;
    }

    public String resolveOnlineName(UUID uuid) {
        if (uuid == null) return null;
        PlayerRef pr = findOnlinePlayerByUuid(uuid);
        try {
            return pr != null ? pr.getUsername() : null;
        } catch (Throwable ignored) { }
        return null;
    }

    public boolean addChatAdmin(UUID uuid) {
        if (uuid == null) return false;
        boolean added = chatAdmins.add(uuid);
        if (added) saveConfigToDisk();
        return added;
    }

    public boolean removeChatAdmin(UUID uuid) {
        if (uuid == null) return false;
        boolean removed = chatAdmins.remove(uuid);
        if (removed) saveConfigToDisk();
        return removed;
    }

    public Set<UUID> getChatAdminsSnapshot() {
        return new HashSet<>(chatAdmins);
    }

    public boolean isChatAdmin(UUID uuid) {
        return uuid != null && chatAdmins.contains(uuid);
    }

    // =========================================================
    // Implementação interna do raio
    // =========================================================

    private static void applyLocalRadius(int blocks) {
        if (blocks < 1) blocks = 1;
        if (blocks > 1000) blocks = 1000;

        localRadius = blocks;
        localRadiusSq = localRadius * localRadius;
    }

    // =========================================================
    // Persistência em arquivo (radius + chatAdmins)
    // =========================================================

    private void loadConfigFromDisk() {
        try {
            Path cfg = getConfigPath();
            if (!Files.exists(cfg)) return;

            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(cfg)) {
                p.load(in);
            }

            // localRadius
            String rawRadius = p.getProperty(PROP_LOCAL_RADIUS);
            if (rawRadius != null && !rawRadius.trim().isEmpty()) {
                try {
                    int blocks = Integer.parseInt(rawRadius.trim());
                    applyLocalRadius(blocks);
                    System.out.println("[LocalGlobalChat] localRadius carregado: " + getLocalRadiusInt());
                } catch (Throwable ignored) {
                    System.err.println("[LocalGlobalChat] ERRO ao parsear localRadius. Usando default 50.");
                }
            }

            // chatAdmins
            chatAdmins.clear();
            String rawAdmins = p.getProperty(PROP_CHAT_ADMINS);
            if (rawAdmins != null && !rawAdmins.trim().isEmpty()) {
                String[] parts = rawAdmins.split("[,;\\s]+");
                for (String s : parts) {
                    if (s == null) continue;
                    String v = s.trim();
                    if (v.isEmpty()) continue;
                    try {
                        chatAdmins.add(UUID.fromString(v));
                    } catch (Throwable ignored) { }
                }
            }

            System.out.println("[LocalGlobalChat] chatAdmins carregados: " + chatAdmins.size());
        } catch (Throwable t) {
            System.err.println("[LocalGlobalChat] ERRO ao carregar config.");
        }
    }

    private void saveConfigToDisk() {
        try {
            Path cfg = getConfigPath();
            Files.createDirectories(cfg.getParent());

            Properties p = new Properties();

            // preserva outras chaves
            if (Files.exists(cfg)) {
                try (InputStream in = Files.newInputStream(cfg)) {
                    p.load(in);
                } catch (Throwable ignored) { }
            }

            p.setProperty(PROP_LOCAL_RADIUS, String.valueOf(getLocalRadiusInt()));

            // salva chatAdmins como CSV
            if (chatAdmins.isEmpty()) {
                p.remove(PROP_CHAT_ADMINS);
            } else {
                StringBuilder sb = new StringBuilder();
                for (UUID u : chatAdmins) {
                    if (u == null) continue;
                    if (sb.length() > 0) sb.append(",");
                    sb.append(u);
                }
                p.setProperty(PROP_CHAT_ADMINS, sb.toString());
            }

            try (OutputStream out = Files.newOutputStream(cfg)) {
                p.store(out, "LocalGlobalChat config");
            }
        } catch (Throwable t) {
            System.err.println("[LocalGlobalChat] ERRO ao salvar config (sem permissao de escrita?).");
        }
    }

    private Path getConfigPath() {
        return getDataDirSafe().resolve(CONFIG_FILE_NAME);
    }

    /**
     * Tenta achar uma pasta de dados do plugin via reflection.
     * Se não achar, usa: ./plugins/LocalGlobalChat/
     */
    private Path getDataDirSafe() {
        Object r = invokeAny(this,
                "getDataFolder",
                "getDataDirectory",
                "getPluginDirectory",
                "getPluginFolder"
        );

        if (r instanceof Path path) return path;
        if (r instanceof java.io.File f) return f.toPath();
        if (r instanceof String s && !s.trim().isEmpty()) return Paths.get(s.trim());

        return Paths.get("plugins", "LocalGlobalChat");
    }

    // =========================================================
    // Registro de eventos via reflection
    // =========================================================

    private void registerEventListener(Class<?> eventClass, Consumer<Object> handler) {
        Object registry = getEventRegistry();
        if (!tryRegister(registry, eventClass, handler)) {
            System.err.println("[LocalGlobalChat] ERRO: nao consegui registrar evento: " + eventClass.getName());
        }
    }

    private boolean tryRegister(Object registry, Class<?> eventClass, Consumer<Object> handler) {
        Method[] methods = registry.getClass().getMethods();

        // 1) register(Class, Consumer)
        try {
            Method m = registry.getClass().getMethod("register", Class.class, Consumer.class);
            m.invoke(registry, eventClass, handler);
            return true;
        } catch (Throwable ignored) { }

        // 2) register(priorityOrShort, Class, Consumer)
        for (Method m : methods) {
            try {
                if (!m.getName().equals("register")) continue;
                if (m.getParameterCount() != 3) continue;

                Class<?>[] p = m.getParameterTypes();
                if (p[1] == Class.class && Consumer.class.isAssignableFrom(p[2])) {
                    Object priorityOrShort = defaultValueFor(p[0]);
                    m.invoke(registry, priorityOrShort, eventClass, handler);
                    return true;
                }
            } catch (Throwable ignored) { }
        }

        // 3) register(Class, key, Consumer)
        for (Method m : methods) {
            try {
                if (!m.getName().equals("register")) continue;
                if (m.getParameterCount() != 3) continue;

                Class<?>[] p = m.getParameterTypes();
                if (p[0] == Class.class && Consumer.class.isAssignableFrom(p[2]) && p[1] != Class.class) {
                    m.invoke(registry, eventClass, null, handler);
                    return true;
                }
            } catch (Throwable ignored) { }
        }

        // 4) register(priorityOrShort, Class, key, Consumer)
        for (Method m : methods) {
            try {
                if (!m.getName().equals("register")) continue;
                if (m.getParameterCount() != 4) continue;

                Class<?>[] p = m.getParameterTypes();
                if (p[1] == Class.class && Consumer.class.isAssignableFrom(p[3]) && p[2] != Class.class) {
                    Object priorityOrShort = defaultValueFor(p[0]);
                    m.invoke(registry, priorityOrShort, eventClass, null, handler);
                    return true;
                }
            } catch (Throwable ignored) { }
        }

        return false;
    }

    private static Object defaultValueFor(Class<?> type) {
        try {
            if (type == short.class || type == Short.class) return (short) 0;
            if (type == int.class || type == Integer.class) return 0;
            if (type == long.class || type == Long.class) return 0L;

            if (type.isEnum()) {
                Object[] values = type.getEnumConstants();
                if (values != null && values.length > 0) return values[0];
            }
        } catch (Throwable ignored) { }
        return null;
    }

    // =========================================================
    // (Opcional) resetar para LOCAL ao entrar (se existir evento)
    // =========================================================

    private void tryRegisterJoinResetToLocal() {
        String[] candidates = new String[] {
                "com.hypixel.hytale.server.core.event.events.player.PlayerJoinEvent",
                "com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent",
                "com.hypixel.hytale.server.core.event.events.player.PlayerLoginEvent",
                "com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent"
        };

        for (String cn : candidates) {
            try {
                Class<?> joinEventClass = Class.forName(cn);

                Consumer<Object> handler = ev -> {
                    PlayerRef p = extractPlayerRef(ev);
                    UUID u = safeUuid(p);
                    if (u != null) setMode(u, ChatMode.LOCAL);
                };

                if (tryRegister(getEventRegistry(), joinEventClass, handler)) {
                    System.out.println("[LocalGlobalChat] JoinEvent registrado: " + cn);
                    return;
                }
            } catch (Throwable ignored) { }
        }

        System.out.println("[LocalGlobalChat] JoinEvent nao encontrado (ok). Default LOCAL ainda funciona.");
    }

    private static PlayerRef extractPlayerRef(Object event) {
        String[] methods = {"getPlayer", "getSender", "player", "sender"};
        for (String mname : methods) {
            try {
                Method m = event.getClass().getMethod(mname);
                Object r = m.invoke(event);
                if (r instanceof PlayerRef) return (PlayerRef) r;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    // =========================================================
    // Chat
    // =========================================================

    private void onChat(PlayerChatEvent event) {
        PlayerRef sender = event.getSender();
        UUID senderUuid = safeUuid(sender);

        boolean disabled = chatDisabled.get();
        boolean bypass = canBypassChatDisabled(sender);

        // Debug mesmo quando bloqueado
        if (senderUuid != null && isDebug(senderUuid)) {
            sender.sendMessage(Message.raw(
                    "DEBUG blocked=" + (disabled && !bypass) +
                            " chatDisabled=" + disabled +
                            " diskAdmin=" + (senderUuid != null && isChatAdmin(senderUuid)) +
                            " perm.disable=" + LGChatCompat.hasPermissionCompat(sender, PERM_CHAT_DISABLE) +
                            " perm.bypass=" + LGChatCompat.hasPermissionCompat(sender, PERM_CHAT_BYPASS) +
                            " op/admin=" + (isAdminLevelCompat(sender) || isUniverseOpCompat(sender))
            ));
        }

        // Se chat estiver desativado e o jogador NÃO tiver bypass -> bloqueia
        if (disabled && !bypass) {
            try { event.getTargets().clear(); } catch (Throwable ignored) { }
            cancelEventCompat(event);
            sender.sendMessage(systemColor("red", "O chat está desativado no momento"));
            return;
        }

        ChatMode mode = (senderUuid != null) ? getMode(senderUuid) : ChatMode.LOCAL;

        event.setFormatter((ignoredViewer, message) -> formatChat(mode, sender.getUsername(), message));

        // Local: filtra targets por distância
        if (mode == ChatMode.LOCAL) {
            UUID senderWorld = sender.getWorldUuid();

            double x0 = getX(sender);
            double y0 = getY(sender);
            double z0 = getZ(sender);

            event.getTargets().removeIf(target -> {
                if (target == null) return true;
                if (!Objects.equals(senderWorld, target.getWorldUuid())) return true;

                double dx = getX(target) - x0;
                double dy = getY(target) - y0;
                double dz = getZ(target) - z0;

                return (dx * dx + dy * dy + dz * dz) > localRadiusSq;
            });
        }
    }

    // toggle seguro sem updateAndGet
    boolean toggleChatDisabled() {
        while (true) {
            boolean cur = chatDisabled.get();
            boolean next = !cur;
            if (chatDisabled.compareAndSet(cur, next)) return next;
        }
    }

    private boolean canBypassChatDisabled(PlayerRef p) {
        UUID u = safeUuid(p);

        // bypass por DISCO (chatAdmins)
        if (u != null && isChatAdmin(u)) return true;

        // tenta permissões (se provider expuser)
        if (LGChatCompat.hasPermissionCompat(p, PERM_CHAT_DISABLE, false)) return true;
        if (LGChatCompat.hasPermissionCompat(p, PERM_CHAT_BYPASS, false)) return true;

        // tenta op/admin via reflection
        return isAdminLevelCompat(p) || isUniverseOpCompat(p);
    }

    private boolean isAdminLevelCompat(PlayerRef p) {
        if (p == null) return false;

        for (String mn : new String[]{"isAdmin", "isOperator", "isOp"}) {
            try {
                Method m = p.getClass().getMethod(mn);
                Object r = m.invoke(p);
                if (r instanceof Boolean b) return b;
            } catch (Throwable ignored) { }
        }

        for (String mn : new String[]{"getPermissionLevel", "getOpLevel", "getOperatorLevel"}) {
            try {
                Method m = p.getClass().getMethod(mn);
                Object r = m.invoke(p);
                if (r instanceof Number n) return n.intValue() >= 2;
            } catch (Throwable ignored) { }
        }

        return false;
    }

    private boolean isUniverseOpCompat(PlayerRef p) {
        if (p == null) return false;

        // alguns builds expõem direto no PlayerRef
        for (String mn : new String[]{"isUniverseOperator", "isUniverseOp", "isOperator"}) {
            try {
                Method m = p.getClass().getMethod(mn);
                Object r = m.invoke(p);
                if (r instanceof Boolean b) return b;
            } catch (Throwable ignored) { }
        }

        // fallback: tenta Universe.get().isOperator(uuid) ou algo similar
        try {
            UUID u = safeUuid(p);
            if (u == null) return false;

            Class<?> uniCl = Class.forName("com.hypixel.hytale.server.core.universe.Universe");
            Object uni = uniCl.getMethod("get").invoke(null);
            if (uni == null) return false;

            for (String mn : new String[]{"isOperator", "isOp", "isPlayerOperator", "isUniverseOperator"}) {
                try {
                    Method m = uni.getClass().getMethod(mn, UUID.class);
                    Object r = m.invoke(uni, u);
                    if (r instanceof Boolean b) return b;
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }

        return false;
    }

    void broadcastSystemMessage(Message msg) {
        broadcastCompat(msg);
    }

    // Mensagem colorida (TinyMsg se existir; fallback §)
    static Message systemColor(String tinyColor, String text) {
        String safe = LGChatCompat.tinySafe(text);
        String tiny = "<color:" + tinyColor + ">" + safe + "</color>";
        Message parsed = LGChatCompat.tryTinyMsgParse(tiny);
        if (parsed != null) return parsed;

        String legacy;
        if ("red".equalsIgnoreCase(tinyColor)) legacy = "§c";
        else if ("green".equalsIgnoreCase(tinyColor)) legacy = "§a";
        else if ("yellow".equalsIgnoreCase(tinyColor)) legacy = "§e";
        else legacy = "§f";

        return Message.raw(legacy + text + "§r");
    }

    // Broadcast compatível via reflection (Universe / lista de players)
    private static void broadcastCompat(Message msg) {
        try {
            Class<?> uniCl = Class.forName("com.hypixel.hytale.server.core.universe.Universe");
            Object uni = uniCl.getMethod("get").invoke(null);
            if (uni == null) return;

            for (String mn : new String[]{"broadcastMessage", "broadcast", "sendMessageToAll", "broadcastToAll"}) {
                try {
                    Method m = uni.getClass().getMethod(mn, Message.class);
                    m.invoke(uni, msg);
                    return;
                } catch (Throwable ignored) { }
            }

            for (String mn : new String[]{"getPlayers", "players", "getOnlinePlayers", "onlinePlayers"}) {
                try {
                    Method m = uni.getClass().getMethod(mn);
                    Object r = m.invoke(uni);
                    if (r instanceof Iterable<?> it) {
                        for (Object o : it) {
                            if (o instanceof PlayerRef p) {
                                p.sendMessage(msg);
                            }
                        }
                    }
                    return;
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
    }

    // Cancelar evento via reflection (compat entre builds)
    private static void cancelEventCompat(Object event) {
        if (event == null) return;

        for (String mn : new String[]{"setCancelled", "setCanceled", "setCanceledFlag"}) {
            try {
                Method m = event.getClass().getMethod(mn, boolean.class);
                m.invoke(event, true);
                return;
            } catch (Throwable ignored) { }
        }

        try {
            Method m = event.getClass().getMethod("cancel");
            m.invoke(event);
        } catch (Throwable ignored) { }
    }

    private static Message formatChat(ChatMode mode, String username, String msg) {
        String tag = (mode == ChatMode.LOCAL) ? "[L] " : "[G] ";
        String col = (mode == ChatMode.LOCAL) ? TINY_LOCAL : TINY_GLOBAL;

        String safeUser = LGChatCompat.tinySafe(username);
        String safeMsg  = LGChatCompat.tinySafe(msg);

        String tiny =
                "<color:" + col + ">" + tag + safeUser + "</color>" +
                        "<color:" + TINY_TEXT + ">: " + safeMsg + "</color>";

        String plain = tag + username + ": " + msg;

        Message parsed = LGChatCompat.tryTinyMsgParse(tiny);
        return (parsed != null) ? parsed : Message.raw(plain);
    }

    // =========================================================
    // Posição (reflection pra suportar builds diferentes)
    // =========================================================

    private static double getX(PlayerRef p) { return getAxis(p, "x"); }
    private static double getY(PlayerRef p) { return getAxis(p, "y"); }
    private static double getZ(PlayerRef p) { return getAxis(p, "z"); }

    private static double getAxis(PlayerRef p, String axis) {
        try {
            Object transform = p.getTransform();

            Object pos = invokeAny(transform,
                    "getPosition", "position",
                    "getTranslation", "translation",
                    "getLocation", "location"
            );

            Object base = (pos != null) ? pos : transform;

            Object value = invokeAny(base,
                    "get" + axis.toUpperCase(), axis
            );

            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        } catch (Throwable ignored) { }

        return 0.0;
    }

    private static Object invokeAny(Object obj, String... methodNames) {
        if (obj == null) return null;
        for (String name : methodNames) {
            try {
                Method m = obj.getClass().getMethod(name);
                m.setAccessible(true);
                return m.invoke(obj);
            } catch (Throwable ignored) { }
        }
        return null;
    }

    // =========================================================
    // Sender / Players online (compat)
    // =========================================================

    private static Object extractSender(CommandContext context) {
        if (context == null) return null;
        try {
            Method senderM = context.getClass().getMethod("sender");
            return senderM.invoke(context);
        } catch (Throwable ignored) { }
        try {
            Method senderM = context.getClass().getMethod("getSender");
            return senderM.invoke(context);
        } catch (Throwable ignored) { }
        return null;
    }

    private static UUID safeUuid(PlayerRef p) {
        try {
            return p != null ? p.getUuid() : null;
        } catch (Throwable ignored) { }
        return null;
    }

    private static PlayerRef findOnlinePlayerByUsername(String username) {
        if (username == null) return null;
        String u = username.trim();
        if (u.isEmpty()) return null;

        for (PlayerRef p : getOnlinePlayersCompat()) {
            try {
                if (p != null && p.getUsername() != null && p.getUsername().equalsIgnoreCase(u)) return p;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private static PlayerRef findOnlinePlayerByUuid(UUID uuid) {
        if (uuid == null) return null;

        for (PlayerRef p : getOnlinePlayersCompat()) {
            try {
                if (p != null && uuid.equals(p.getUuid())) return p;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    private static List<PlayerRef> getOnlinePlayersCompat() {
        try {
            Class<?> uniCl = Class.forName("com.hypixel.hytale.server.core.universe.Universe");
            Object uni = uniCl.getMethod("get").invoke(null);
            if (uni == null) return Collections.emptyList();

            for (String mn : new String[]{"getPlayers", "players", "getOnlinePlayers", "onlinePlayers"}) {
                try {
                    Method m = uni.getClass().getMethod(mn);
                    Object r = m.invoke(uni);
                    if (r instanceof Iterable<?> it) {
                        List<PlayerRef> out = new ArrayList<>();
                        for (Object o : it) if (o instanceof PlayerRef pr) out.add(pr);
                        return out;
                    }
                } catch (Throwable ignored) { }
            }
        } catch (Throwable ignored) { }
        return Collections.emptyList();
    }
}
