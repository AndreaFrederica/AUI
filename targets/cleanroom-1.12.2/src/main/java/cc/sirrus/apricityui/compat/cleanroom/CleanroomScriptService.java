package cc.sirrus.apricityui.compat.cleanroom;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.spi.AuiScriptService;
import net.minecraftforge.fml.common.Loader;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;

/**
 * Optional Cleanroom script bridge.  The runtime jars are deliberately not
 * linked from the AUI classes: a plain Cleanroom installation must still load
 * when neither scripting mod is present.  The build selects one runtime with
 * {@code aui_script_backend=nekojs|kubejs|none}; an installed runtime is then
 * detected again at game start.
 */
public final class CleanroomScriptService implements AuiScriptService {
    public static final CleanroomScriptService INSTANCE = new CleanroomScriptService();

    private enum Backend { NONE, NEKOJS, KUBEJS }

    private final Object lock = new Object();
    private volatile Backend backend;
    private Object nekoContext;
    private Method nekoEval;
    private Object kubeEngine;

    private CleanroomScriptService() {
    }

    @Override
    public void eval(String code, Event event, String source) {
        if (code == null || code.isBlank()) return;
        synchronized (lock) {
            switch (selectedBackend()) {
                case NEKOJS -> evalNeko(code, event);
                case KUBEJS -> evalKube(code, event);
                case NONE -> { }
            }
        }
    }

    @Override
    public void evalGlobal(String code, String documentUuid) {
        if (code == null || code.isBlank()) return;
        String uuid = documentUuid == null ? "" : documentUuid.replace("\\", "\\\\").replace("\"", "\\\"");
        eval(code.replace("__AUI_DOCUMENT_UUID__", "\"" + uuid + "\""), null, "global.js");
    }

    @Override
    public void reload() {
        synchronized (lock) {
            Backend selected = selectedBackend();
            try {
                if (selected == Backend.NEKOJS) {
                    Class<?> mod = Class.forName("com.tkisor.nekojs.NekoJSMod");
                    Field root = mod.getField("RUNTIME_ROOT");
                    Object runtime = root.get(null);
                    if (runtime != null) {
                        Class<?> type = Class.forName("com.tkisor.nekojs.api.ScriptType");
                        Object client = Enum.valueOf((Class) type, "CLIENT");
                        runtime.getClass().getMethod("reload", type).invoke(runtime, client);
                    }
                } else if (selected == Backend.KUBEJS) {
                    // The 1.12 runtime reloads through its normal /reload path;
                    // discard only AUI's engine handle so the next eval rebinds.
                    kubeEngine = null;
                }
            } catch (Throwable error) {
                ApricityUI.LOGGER.warn("[AUI Cleanroom] script reload failed for {}", selected, error);
            }
        }
    }

    @Override
    public void warmUp() {
        synchronized (lock) {
            Backend selected = selectedBackend();
            if (selected == Backend.NEKOJS) ensureNekoContext();
            if (selected == Backend.KUBEJS) ensureKubeEngine();
        }
    }

    private Backend selectedBackend() {
        Backend cached = backend;
        if (cached != null) return cached;

        String configured = System.getProperty("apricityui.scriptBackend", "auto")
                .trim().toLowerCase(Locale.ROOT);
        boolean neko = isLoaded("nekojs");
        boolean kube = isLoaded("kubejs");
        Backend selected;
        if ("nekojs".equals(configured)) selected = neko ? Backend.NEKOJS : Backend.NONE;
        else if ("kubejs".equals(configured)) selected = kube ? Backend.KUBEJS : Backend.NONE;
        else if ("none".equals(configured)) selected = Backend.NONE;
        else if (neko && kube) {
            ApricityUI.LOGGER.error("[AUI Cleanroom] both NekoJS and KubeJS are installed; choose one with -Dapricityui.scriptBackend");
            selected = Backend.NONE;
        } else if (neko) selected = Backend.NEKOJS;
        else if (kube) selected = Backend.KUBEJS;
        else selected = Backend.NONE;

        if (selected == Backend.NONE && (!"none".equals(configured))) {
            ApricityUI.LOGGER.info("[AUI Cleanroom] no compatible script backend is active; scripts are disabled");
        } else {
            ApricityUI.LOGGER.info("[AUI Cleanroom] script backend: {}", selected);
        }
        backend = selected;
        return selected;
    }

    private static boolean isLoaded(String id) {
        try {
            return Loader.isModLoaded(id);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void evalNeko(String code, Event event) {
        try {
            Object context = ensureNekoContext();
            if (context == null) return;
            Class<?> contextClass = context.getClass();
            Object bindings = contextClass.getMethod("getBindings", String.class).invoke(context, "js");
            Method putMember = bindings.getClass().getMethod("putMember", String.class, Object.class);
            putMember.invoke(bindings, "event", event);
            nekoEval.invoke(context, "js", code);
        } catch (Throwable error) {
            ApricityUI.LOGGER.error("[AUI Cleanroom] NekoJS evaluation failed", error);
        }
    }

    private Object ensureNekoContext() {
        if (nekoContext != null) return nekoContext;
        try {
            Class<?> contextClass = Class.forName("graal.graalvm.polyglot.Context");
            Method newBuilder = contextClass.getMethod("newBuilder", String[].class);
            Object builder = newBuilder.invoke(null, (Object) new String[]{"js"});
            try {
                builder.getClass().getMethod("allowAllAccess", boolean.class).invoke(builder, true);
            } catch (NoSuchMethodException ignored) {
                // Older preview builds may expose a restricted builder only.
            }
            nekoContext = builder.getClass().getMethod("build").invoke(builder);
            nekoEval = contextClass.getMethod("eval", String.class, CharSequence.class);
            return nekoContext;
        } catch (Throwable error) {
            ApricityUI.LOGGER.warn("[AUI Cleanroom] NekoJS is present but its Graal context is unavailable", error);
            backend = Backend.NONE;
            return null;
        }
    }

    private void evalKube(String code, Event event) {
        try {
            ScriptEngine engine = ensureKubeEngine();
            if (engine == null) return;
            Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
            if (event == null) bindings.remove("event");
            else bindings.put("event", event);
            engine.eval(code, bindings);
        } catch (Throwable error) {
            ApricityUI.LOGGER.error("[AUI Cleanroom] legacy KubeJS evaluation failed", error);
        }
    }

    private ScriptEngine ensureKubeEngine() {
        if (kubeEngine instanceof ScriptEngine engine) return engine;
        try {
            Class<?> managerClass = Class.forName("dev.latvian.kubejs.script.ScriptManager");
            Object manager = managerClass.getField("instance").get(null);
            if (manager == null) return null;
            Field packs = managerClass.getDeclaredField("packs");
            packs.setAccessible(true);
            Object value = packs.get(manager);
            if (value instanceof Map<?, ?> map) {
                for (Object pack : map.values()) {
                    Field engine = pack.getClass().getField("engine");
                    Object candidate = engine.get(pack);
                    if (candidate instanceof ScriptEngine scriptEngine) {
                        kubeEngine = scriptEngine;
                        return scriptEngine;
                    }
                }
            }
        } catch (Throwable error) {
            ApricityUI.LOGGER.warn("[AUI Cleanroom] KubeJS engine is unavailable on this Java runtime", error);
        }
        return null;
    }
}
