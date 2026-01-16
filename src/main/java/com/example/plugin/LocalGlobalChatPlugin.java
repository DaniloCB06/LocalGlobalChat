package com.example.plugin;

import com.hypixel.hytale.server.core.Message;
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
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class LocalGlobalChatPlugin extends JavaPlugin {

    // =========================
    // Persistência
    // =========================
    private static final String CONFIG_FILE_NAME = "localglobalchat.properties";
    private static final String PROP_LOCAL_RADIUS = "localRadius";

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

    public LocalGlobalChatPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        //  carrega o raio salvo antes de registrar comandos e antes do chat rodar
        loadLocalRadiusFromDisk();

        getCommandRegistry().registerCommand(new GCommand(this));
        getCommandRegistry().registerCommand(new LCommand(this));
        getCommandRegistry().registerCommand(new MsgCommand());
        getCommandRegistry().registerCommand(new ChatDebugCommand(this));
        getCommandRegistry().registerCommand(new LocalRadiusCommand(this));
        getCommandRegistry().registerCommand(new ClearChatCommand());

        // Registro do chat via reflection
        registerEventListener(PlayerChatEvent.class, ev -> onChat((PlayerChatEvent) ev));

        // (Opcional) tenta resetar para LOCAL quando o jogador entrar (se existir evento)
        tryRegisterJoinResetToLocal();
    }

    // =========================================================
    // API usada pelos comandos
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
        saveLocalRadiusToDisk();
    }

    int getLocalRadiusInt() {
        return (int) Math.round(localRadius);
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
    // Persistência em arquivo
    // =========================================================

    private void loadLocalRadiusFromDisk() {
        try {
            Path cfg = getConfigPath();
            if (!Files.exists(cfg)) {
                // sem config -> mantém default
                return;
            }

            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(cfg)) {
                p.load(in);
            }

            String raw = p.getProperty(PROP_LOCAL_RADIUS);
            if (raw == null || raw.trim().isEmpty()) return;

            int blocks = Integer.parseInt(raw.trim());
            applyLocalRadius(blocks);

            System.out.println("[LocalGlobalChat] localRadius carregado: " + getLocalRadiusInt());
        } catch (Throwable t) {
            System.err.println("[LocalGlobalChat] ERRO ao carregar localRadius. Usando default 50.");
        }
    }

    private void saveLocalRadiusToDisk() {
        try {
            Path cfg = getConfigPath();
            Files.createDirectories(cfg.getParent());

            Properties p = new Properties();

            // se já existir, preserva outras chaves futuras
            if (Files.exists(cfg)) {
                try (InputStream in = Files.newInputStream(cfg)) {
                    p.load(in);
                } catch (Throwable ignored) { }
            }

            p.setProperty(PROP_LOCAL_RADIUS, String.valueOf(getLocalRadiusInt()));

            try (OutputStream out = Files.newOutputStream(cfg)) {
                p.store(out, "LocalGlobalChat config");
            }

            System.out.println("[LocalGlobalChat] localRadius salvo: " + getLocalRadiusInt());
        } catch (Throwable t) {
            System.err.println("[LocalGlobalChat] ERRO ao salvar localRadius (sem permissao de escrita?).");
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
        // tenta métodos comuns no JavaPlugin (dependendo do build)
        Object r = invokeAny(this,
                "getDataFolder",
                "getDataDirectory",
                "getPluginDirectory",
                "getPluginFolder"
        );

        if (r instanceof Path path) return path;
        if (r instanceof java.io.File f) return f.toPath();
        if (r instanceof String s && !s.trim().isEmpty()) return Paths.get(s.trim());

        // fallback bem seguro
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
                    if (p != null && p.getUuid() != null) {
                        setMode(p.getUuid(), ChatMode.LOCAL);
                    }
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
    // Chat formatado (cores via TinyMsg se instalado)
    // =========================================================

    private void onChat(PlayerChatEvent event) {
        PlayerRef sender = event.getSender();
        UUID senderUuid = sender.getUuid();

        ChatMode mode = getMode(senderUuid);

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

        // DEBUG (somente se estiver ligado)
        if (isDebug(senderUuid)) {
            StringBuilder sb = new StringBuilder();
            sb.append("DEBUG chat=").append(mode == ChatMode.LOCAL ? "LOCAL" : "GLOBAL");
            sb.append(" targets=").append(event.getTargets().size()).append(" -> ");

            int shown = 0;
            for (PlayerRef t : event.getTargets()) {
                if (t == null) continue;
                sb.append(t.getUsername()).append(", ");
                shown++;
                if (shown >= 10) {
                    sb.append("...");
                    break;
                }
            }
            sender.sendMessage(Message.raw(sb.toString()));
        }
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
}
