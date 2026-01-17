package com.example.plugin;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import java.lang.reflect.Method;
import java.util.*;

public final class CommandCompat {

    private CommandCompat() {}

    public static void addAliasCompat(CommandBase cmd, String alias) {
        String[] methods = {"addAlias", "addAliases", "setAlias", "setAliases"};

        for (String mn : methods) {
            try {
                Method m = cmd.getClass().getMethod(mn, String.class);
                m.invoke(cmd, alias);
                return;
            } catch (Throwable ignored) { }

            try {
                Method m = cmd.getClass().getMethod(mn, String[].class);
                m.invoke(cmd, (Object) new String[]{alias});
                return;
            } catch (Throwable ignored) { }

            try {
                Method m = cmd.getClass().getMethod(mn, Collection.class);
                m.invoke(cmd, Arrays.asList(alias));
                return;
            } catch (Throwable ignored) { }
        }
    }

    // Alguns builds exigem declarar "aceita args". Isso tenta mexer no comando por reflection.
    public static void tryAllowArgsCompat(CommandBase cmd) {
        // tenta métodos comuns
        invokeDeep(cmd, "setMaxArgs", new Class[]{int.class}, new Object[]{Integer.MAX_VALUE});
        invokeDeep(cmd, "setMaxArguments", new Class[]{int.class}, new Object[]{Integer.MAX_VALUE});
        invokeDeep(cmd, "setMinArgs", new Class[]{int.class}, new Object[]{0});
        invokeDeep(cmd, "setMinArguments", new Class[]{int.class}, new Object[]{0});

        invokeDeep(cmd, "setGreedy", new Class[]{boolean.class}, new Object[]{true});
        invokeDeep(cmd, "setGreedyArguments", new Class[]{boolean.class}, new Object[]{true});
        invokeDeep(cmd, "allowAdditionalArguments", new Class[]{boolean.class}, new Object[]{true});
        invokeDeep(cmd, "setAllowAdditionalArguments", new Class[]{boolean.class}, new Object[]{true});
        invokeDeep(cmd, "setStrictArguments", new Class[]{boolean.class}, new Object[]{false});
        invokeDeep(cmd, "setExactArguments", new Class[]{boolean.class}, new Object[]{false});
    }

    private static boolean invokeDeep(Object obj, String name, Class<?>[] sig, Object[] args) {
        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(name, sig);
                m.setAccessible(true);
                m.invoke(obj, args);
                return true;
            } catch (Throwable ignored) { }
            c = c.getSuperclass();
        }
        return false;
    }

    public static String[] getArgsCompat(CommandContext context) {
        Object r = invokeAny(context,
                "args", "getArgs",
                "arguments", "getArguments",
                "rawArgs", "getRawArgs",
                "parameters", "getParameters"
        );

        if (r instanceof String[] sa) return sa;

        if (r instanceof Object[] oa) {
            List<String> out = new ArrayList<>();
            for (Object o : oa) if (o != null) out.add(String.valueOf(o));
            return out.toArray(new String[0]);
        }

        if (r instanceof Iterable<?> it) {
            List<String> out = new ArrayList<>();
            for (Object o : it) if (o != null) out.add(String.valueOf(o));
            return out.toArray(new String[0]);
        }

        // fallback: tenta input cru
        String raw = tryGetString(context,
                "getRawInput", "rawInput",
                "getInput", "input",
                "getCommandLine", "commandLine"
        );

        if (raw != null) {
            String s = raw.trim();
            if (s.startsWith("/")) s = s.substring(1);
            // remove o nome do comando (primeira palavra)
            int sp = s.indexOf(' ');
            if (sp < 0) return new String[0];
            s = s.substring(sp + 1).trim();
            if (s.isEmpty()) return new String[0];
            return s.split("\\s+");
        }

        return new String[0];
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

    private static String tryGetString(Object obj, String... methodNames) {
        for (String mn : methodNames) {
            try {
                Method m = obj.getClass().getMethod(mn);
                Object r = m.invoke(obj);
                if (r instanceof String s && !s.isEmpty()) return s;
            } catch (Throwable ignored) { }
        }
        return null;
    }
}
