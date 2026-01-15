package com.example.plugin;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class LocalGlobalChatPlugin extends JavaPlugin {

    private static final double LOCAL_RADIUS = 50.0;
    private static final double LOCAL_RADIUS_SQ = LOCAL_RADIUS * LOCAL_RADIUS;

    // modo do chat por jogador
    private final Map<UUID, ChatMode> chatModes = new ConcurrentHashMap<>();

    // debug por jogador (toggle /chatdebug)
    private final Map<UUID, Boolean> debugModes = new ConcurrentHashMap<>();

    public LocalGlobalChatPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        getCommandRegistry().registerCommand(new GCommand(this));
        getCommandRegistry().registerCommand(new LCommand(this));
        getCommandRegistry().registerCommand(new ChatDebugCommand(this));

        // Registro do evento via reflection (pra não quebrar com diferenças de generics na sua API)
        registerChatEventListener();
    }

    // =========================================================
    // Registro de evento (reflection)
    // =========================================================

    private void registerChatEventListener() {
        Object registry = getEventRegistry();

        // Consumer RAW (sem generics) pra encaixar em qualquer assinatura com erasure
        Consumer<Object> handler = ev -> {
            if (ev instanceof PlayerChatEvent) {
                onChat((PlayerChatEvent) ev);
            }
        };

        // Tenta várias assinaturas possíveis de register(...)
        if (tryRegister(registry, handler)) {
            System.out.println("[LocalGlobalChat] PlayerChatEvent registrado com sucesso.");
        } else {
            System.err.println("[LocalGlobalChat] ERRO: nao consegui registrar PlayerChatEvent. API diferente.");
        }
    }

    private boolean tryRegister(Object registry, Consumer<Object> handler) {
        Method[] methods = registry.getClass().getMethods();

        // 1) register(Class, Consumer)
        try {
            Method m = registry.getClass().getMethod("register", Class.class, Consumer.class);
            m.invoke(registry, PlayerChatEvent.class, handler);
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
                    m.invoke(registry, priorityOrShort, PlayerChatEvent.class, handler);
                    return true;
                }
            } catch (Throwable ignored) { }
        }

        // 3) register(Class, key, Consumer)  (key geralmente pode ser null para "qualquer", dependendo da API)
        for (Method m : methods) {
            try {
                if (!m.getName().equals("register")) continue;
                if (m.getParameterCount() != 3) continue;

                Class<?>[] p = m.getParameterTypes();
                if (p[0] == Class.class && Consumer.class.isAssignableFrom(p[2]) && p[1] != Class.class) {
                    m.invoke(registry, PlayerChatEvent.class, null, handler);
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
                    m.invoke(registry, priorityOrShort, PlayerChatEvent.class, null, handler);
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
    // Lógica do chat
    // =========================================================

    private ChatMode getMode(UUID uuid) {
        return chatModes.getOrDefault(uuid, ChatMode.GLOBAL);
    }

    private void setMode(UUID uuid, ChatMode mode) {
        chatModes.put(uuid, mode);
    }

    private boolean isDebug(UUID uuid) {
        return debugModes.getOrDefault(uuid, false);
    }

    private void toggleDebug(UUID uuid) {
        debugModes.put(uuid, !isDebug(uuid));
    }

    private void onChat(PlayerChatEvent event) {
        PlayerRef sender = event.getSender();
        UUID senderUuid = sender.getUuid();

        ChatMode mode = getMode(senderUuid);
        String tag = (mode == ChatMode.LOCAL) ? "[L] " : "[G] ";

        // Prefixo + nome + mensagem
        event.setFormatter((ignoredViewer, message) ->
                Message.raw(tag + sender.getUsername() + ": " + message)
        );

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

                return (dx * dx + dy * dy + dz * dz) > LOCAL_RADIUS_SQ;
            });
        }

        // ===== DEBUG =====
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
    // Modos + comandos
    // =========================================================

    private enum ChatMode {
        GLOBAL, LOCAL
    }

    private static final class GCommand extends AbstractCommand {
        private final LocalGlobalChatPlugin plugin;

        private GCommand(LocalGlobalChatPlugin plugin) {
            super("g", "Fala no chat global");
            this.plugin = plugin;
        }

        @Override
        @Nullable
        protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
            UUID uuid = context.sender().getUuid();
            if (uuid == null) {
                context.sender().sendMessage(Message.raw("Este comando so pode ser usado por jogadores."));
                return CompletableFuture.completedFuture(null);
            }

            plugin.setMode(uuid, ChatMode.GLOBAL);
            context.sender().sendMessage(Message.raw("Agora voce esta no chat GLOBAL. [G]"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class LCommand extends AbstractCommand {
        private final LocalGlobalChatPlugin plugin;

        private LCommand(LocalGlobalChatPlugin plugin) {
            super("l", "Fala no chat local (raio de 50 blocos)");
            this.plugin = plugin;
        }

        @Override
        @Nullable
        protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
            UUID uuid = context.sender().getUuid();
            if (uuid == null) {
                context.sender().sendMessage(Message.raw("Este comando so pode ser usado por jogadores."));
                return CompletableFuture.completedFuture(null);
            }

            plugin.setMode(uuid, ChatMode.LOCAL);
            context.sender().sendMessage(Message.raw("Agora voce esta no chat LOCAL (50 blocos). [L]"));
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class ChatDebugCommand extends AbstractCommand {
        private final LocalGlobalChatPlugin plugin;

        private ChatDebugCommand(LocalGlobalChatPlugin plugin) {
            super("chatdebug", "Ativa/desativa debug do chat (mostra targets)");
            this.plugin = plugin;
        }

        @Override
        @Nullable
        protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
            UUID uuid = context.sender().getUuid();
            if (uuid == null) {
                context.sender().sendMessage(Message.raw("Este comando so pode ser usado por jogadores."));
                return CompletableFuture.completedFuture(null);
            }

            plugin.toggleDebug(uuid);
            boolean now = plugin.isDebug(uuid);

            context.sender().sendMessage(Message.raw("ChatDebug: " + (now ? "ON" : "OFF")));
            return CompletableFuture.completedFuture(null);
        }
    }
}
