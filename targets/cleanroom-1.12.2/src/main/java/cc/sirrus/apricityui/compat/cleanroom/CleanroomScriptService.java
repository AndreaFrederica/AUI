package cc.sirrus.apricityui.compat.cleanroom;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.parser.JS;
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
    private Object nekoManager;
    private boolean nekoTemporary;
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
        // global.js already contains quotes around the UUID placeholder.  The
        // common service contract replaces the marker with the raw escaped
        // value; adding a second pair of quotes produces `""uuid""` and
        // breaks both Nashorn and the legacy KubeJS engine.
        String uuid = documentUuid == null ? "" : documentUuid.replace("\\", "\\\\").replace("\"", "\\\"");
        eval(code.replace("__AUI_DOCUMENT_UUID__", uuid), null, "global.js");
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
            if (event == null) {
                try {
                    bindings.getClass().getMethod("removeMember", String.class).invoke(bindings, "event");
                } catch (NoSuchMethodException ignored) {
                    putMember.invoke(bindings, "event", null);
                }
            } else {
                putMember.invoke(bindings, "event", event);
            }

            // Make AUI snippets first-class NekoJS scripts: this preserves the
            // runtime's CLIENT globals, module loader and error attribution.
            String scriptId = "apricityui/" + (event == null ? "global.js" : "inline.js");
            String previous = null;
            try {
                Class<?> registry = Class.forName("com.tkisor.nekojs.script.ScriptContextRegistry");
                previous = (String) registry.getMethod("switchCurrentScriptId",
                        contextClass, String.class).invoke(null, context, scriptId);
            } catch (Throwable ignored) {
                // Older NekoJS builds do not expose the registry helper.
            }
            try {
                nekoEval.invoke(context, "js", code);
            } finally {
                if (previous != null) {
                    try {
                        Class<?> registry = Class.forName("com.tkisor.nekojs.script.ScriptContextRegistry");
                        registry.getMethod("restoreCurrentScriptId", contextClass, String.class)
                                .invoke(null, context, previous);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable error) {
            ApricityUI.LOGGER.error("[AUI Cleanroom] NekoJS evaluation failed", error);
        }
    }

    private Object ensureNekoContext() {
        if (nekoContext != null && !nekoTemporary) return nekoContext;
        try {
            Class<?> contextClass = Class.forName("graal.graalvm.polyglot.Context");
            Object formal = findNekoRuntimeContext(contextClass);
            if (formal != null) {
                if (nekoTemporary && nekoContext != null) {
                    try { nekoContext.getClass().getMethod("close").invoke(nekoContext); } catch (Throwable ignored) { }
                }
                nekoContext = formal;
                nekoTemporary = false;
                nekoEval = contextClass.getMethod("eval", String.class, CharSequence.class);
                ApricityUI.LOGGER.info("[AUI Cleanroom] attached to NekoJS CLIENT runtime");
                return formal;
            }

            // Keep a compatibility fallback for the brief window before NekoJS
            // has completed preInit.  It is replaced by the official context on
            // the first later evaluation and never replaces a healthy runtime.
            Method newBuilder = contextClass.getMethod("newBuilder", String[].class);
            Object builder = newBuilder.invoke(null, (Object) new String[]{"js"});
            try {
                builder.getClass().getMethod("allowAllAccess", boolean.class).invoke(builder, true);
            } catch (NoSuchMethodException ignored) {
            }
            nekoContext = builder.getClass().getMethod("build").invoke(builder);
            nekoTemporary = true;
            nekoEval = contextClass.getMethod("eval", String.class, CharSequence.class);
            ApricityUI.LOGGER.warn("[AUI Cleanroom] NekoJS CLIENT runtime is not ready; using a temporary context");
            return nekoContext;
        } catch (Throwable error) {
            ApricityUI.LOGGER.warn("[AUI Cleanroom] NekoJS is present but its Graal context is unavailable", error);
            backend = Backend.NONE;
            return null;
        }
    }

    private Object findNekoRuntimeContext(Class<?> contextClass) {
        try {
            Class<?> mod = Class.forName("com.tkisor.nekojs.NekoJSMod");
            Object root = mod.getField("RUNTIME_ROOT").get(null);
            if (root == null) return null;
            Class<?> type = Class.forName("com.tkisor.nekojs.api.ScriptType");
            Object client = Enum.valueOf((Class) type, "CLIENT");
            Object manager = root.getClass().getMethod("scriptManagerOrNull", type).invoke(root, client);
            if (manager == null) {
                manager = root.getClass().getMethod("scriptManagerOf", type).invoke(root, client);
            }
            if (manager == null) return null;
            nekoManager = manager;

            java.lang.reflect.Field runtime = manager.getClass().getDeclaredField("runtime");
            runtime.setAccessible(true);
            Object environment = runtime.get(manager);
            if (environment == null) {
                Method create = manager.getClass().getDeclaredMethod("getOrCreateEnvironment");
                create.setAccessible(true);
                environment = create.invoke(manager);
            }
            if (environment == null) return null;
            Method context = environment.getClass().getMethod("context");
            Object result = context.invoke(environment);
            return contextClass.isInstance(result) ? result : null;
        } catch (Throwable error) {
            ApricityUI.LOGGER.debug("[AUI Cleanroom] NekoJS runtime context not ready: {}", error.toString());
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
            engine.eval(JS.rewriteForLegacyKube(code), bindings);
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
                        installKubeBindings(scriptEngine);
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

    /**
     * KubeJS 1.12 exposes its JavaScript engine without the modern KubeJS
     * binding registrar.  Install the same public AUI facade that newer
     * loaders expose so global.js and page scripts can resolve
     * {@code ApricityUI.getDocumentByUUID(...)}.
     */
    private static void installKubeBindings(ScriptEngine engine) {
        try {
            Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
            // Nashorn treats a java.lang.Class placed in ENGINE_SCOPE as an
            // opaque object.  Java.type creates the callable static facade
            // expected by AUI's browser bootstrap.
            engine.eval("var ApricityUI = Java.type('com.sighs.apricityui.ApricityUI');", bindings);
        } catch (Throwable error) {
            ApricityUI.LOGGER.debug("[AUI Cleanroom] unable to install KubeJS bindings", error);
        }
    }
}
