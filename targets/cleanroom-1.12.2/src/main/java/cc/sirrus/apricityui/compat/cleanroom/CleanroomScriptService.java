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
import java.util.regex.Pattern;

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
    private Object nekoGlobalsContext;
    private boolean nekoTemporary;
    private String lastProbeMissDetail;
    private int probeMissRepeats;
    private boolean probeSuccessLogged;
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

    /**
     * Returns whether the loader's real client script context is available.
     * Cleanroom fires FML init before NekoJS has installed its CLIENT runtime;
     * callers use this probe to defer the first resource/script bootstrap.
     */
    public boolean isClientRuntimeReady() {
        synchronized (lock) {
            Backend selected = selectedBackend();
            if (selected != Backend.NEKOJS) return true;
            try {
                Class<?> contextClass = Class.forName("graal.graalvm.polyglot.Context");
                return findNekoRuntimeContext(contextClass) != null;
            } catch (Throwable ignored) {
                return false;
            }
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
            installNekoGlobals(contextClass, context);
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
                try {
                    nekoEval.invoke(context, "js", rewriteDeclarationsForSharedScope(code));
                } catch (Throwable scoped) {
                    ApricityUI.LOGGER.warn("[AUI Cleanroom] script evaluation unavailable: {}", describe(scoped));
                    throw scoped;
                }
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

    private static final Pattern TOP_LEVEL_DECLARATION = Pattern.compile(
            "(?m)^([ \\t]*)(?:let|const)(?=[ \\t]+[A-Za-z_$])");


    /**
     * Rewrites statement-level {@code let}/{@code const} to {@code var}.
     *
     * <p>NekoJS keeps one Graal global scope for the whole context, and AUI
     * re-evaluates {@code global.js} once per document. A second evaluation of
     * {@code let document = ...} therefore fails with
     * "Variable has already been declared", and the aborted evaluation leaves the
     * stale binding behind. {@code var} has the browser-like replaceable semantics
     * AUI needs. This is the same compatibility layer KubeJS gets from
     * {@code JS.rewriteForLegacyKube}; unlike that helper's
     * {@code \b(?:let|const)\b} pass it only matches declaration position, so
     * property names and string literals are untouched.</p>
     */
    private static String rewriteDeclarationsForSharedScope(String source) {
        if (source == null || source.isBlank()) return source;
        return TOP_LEVEL_DECLARATION.matcher(source).replaceAll("$1var");
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
                installNekoGlobals(contextClass, formal);
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
            // The guest global must exist before the very first eval in *any* context,
            // otherwise the first global.js aborts half-way and leaves its `let`
            // declarations bound in the shared context scope.
            installNekoGlobals(contextClass, nekoContext);
            ApricityUI.LOGGER.warn("[AUI Cleanroom] NekoJS CLIENT runtime is not ready; using a temporary context");
            return nekoContext;
        } catch (Throwable error) {
            ApricityUI.LOGGER.warn("[AUI Cleanroom] NekoJS is present but its Graal context is unavailable", error);
            backend = Backend.NONE;
            return null;
        }
    }

    /**
     * Installs AUI's browser facade into the NekoJS guest context.
     *
     * <p>NekoJS has no equivalent of KubeJS {@code @KJSBindings}, so without this the
     * shared {@code global.js} bootstrap cannot resolve {@code ApricityUI} and every
     * page script fails. The facade class is the same one annotated with
     * {@code @KJSBindings("ApricityUI")}, so the guest sees the identical surface.</p>
     */
    private void installNekoGlobals(Class<?> contextClass, Object context) {
        if (context == nekoGlobalsContext) return;
        try {
            Object bindings = contextClass.getMethod("getBindings", String.class).invoke(context, "js");
            Method getMember = bindings.getClass().getMethod("getMember", String.class);
            Method putMember = bindings.getClass().getMethod("putMember", String.class, Object.class);
            Object facade = null;
            // A raw Class object does not expose its statics to this guest's host access,
            // so resolve it exactly like NekoJS does for its own class bindings:
            // Java.type(<name>) returns the proper host class object.
            try {
                Object java = getMember.invoke(bindings, "Java");
                facade = java.getClass().getMethod("invokeMember", String.class, Object[].class)
                        .invoke(java, "type",
                                (Object) new Object[]{"com.sighs.apricityui.util.kjs.ApricityUIClientUtil"});
            } catch (Throwable hostLookupFailed) {
                ApricityUI.LOGGER.debug("[AUI Cleanroom] Java.type facade lookup failed: {}",
                        describe(hostLookupFailed));
            }
            if (facade == null) {
                facade = Class.forName("com.sighs.apricityui.util.kjs.ApricityUIClientUtil");
            }
            putMember.invoke(bindings, "ApricityUI", facade);
            // Graal host objects may report successful defineProperty calls while
            // silently discarding the property. The common DOM bridge uses this
            // marker to route Element values through a mutable JS facade.
            putMember.invoke(bindings, "__auiNekoJS", Boolean.TRUE);
            nekoGlobalsContext = context;
            ApricityUI.LOGGER.info("[AUI Cleanroom] installed the ApricityUI guest global for NekoJS (facade={})",
                    facade instanceof Class ? "host-class" : "java-type");
        } catch (Throwable error) {
            ApricityUI.LOGGER.warn("[AUI Cleanroom] unable to install the ApricityUI guest global for NekoJS", error);
        }
    }

    /**
     * Locates NekoJS's CLIENT Graal context.
     *
     * <p>Deliberately never touches {@code com.tkisor.nekojs.api.ScriptType}: its
     * static initializer requires NekoJS's platform to be initialized, so simply
     * naming it during early startup throws and would mask the real state. The
     * managers are read from {@code NekoRuntimeRoot.scriptManagers}
     * ({@code Map<ScriptType, ScriptManager>}) and the CLIENT entry is matched by
     * enum constant name, which never initializes the enum class.</p>
     */
    private Object findNekoRuntimeContext(Class<?> contextClass) {
        String step = "start";
        try {
            Class<?> mod = Class.forName("com.tkisor.nekojs.NekoJSMod");
            Object root = mod.getField("RUNTIME_ROOT").get(null);
            if (root == null) {
                logProbeMiss("step=root RUNTIME_ROOT is null");
                return null;
            }
            Class<?> rootClass = root.getClass();

            step = "manager-lookup";
            Object manager = null;
            String how = "none";
            Field managersField = findFieldByName(rootClass, "scriptManagers");
            if (managersField != null) {
                Object managers = managersField.get(root);
                if (managers instanceof Map<?, ?> map && !map.isEmpty()) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getValue() == null) continue;
                        if (!isClientScriptType(entry.getKey())) continue;
                        manager = entry.getValue();
                        how = "scriptManagers map";
                        break;
                    }
                    if (manager == null) {
                        how = "scriptManagers map has no CLIENT entry (keys="
                                + describeKeys(map) + ")";
                    }
                } else {
                    how = "scriptManagers map is empty";
                }
            }
            if (manager == null) {
                // Fall back to the public accessors, matched by parameter type name so a
                // separate class loader cannot hide them.
                Method orNull = findMethodByParameterNames(rootClass, "scriptManagerOrNull", "ScriptType");
                Method of = findMethodByParameterNames(rootClass, "scriptManagerOf", "ScriptType");
                if (orNull != null) {
                    try {
                        manager = orNull.invoke(root, clientTypeOrNull());
                        if (manager != null) how = "scriptManagerOrNull";
                    } catch (Throwable lookupError) {
                        how = "scriptManagerOrNull threw " + describe(lookupError);
                    }
                }
                if (manager == null && of != null) {
                    try {
                        manager = of.invoke(root, clientTypeOrNull());
                        if (manager != null) how = "scriptManagerOf";
                    } catch (Throwable createError) {
                        logProbeMiss("step=manager-lookup scriptManagerOf(CLIENT) threw "
                                + describe(createError) + " (map=" + how + ")");
                        return null;
                    }
                }
            }
            if (manager == null) {
                logProbeMiss("step=manager-lookup NekoJS has no CLIENT manager yet (" + how + ")");
                return null;
            }
            nekoManager = manager;

            step = "runtime-field";
            Class<?> managerClass = manager.getClass();
            Field runtime = findFieldByName(managerClass, "runtime");
            if (runtime == null) {
                logProbeMiss("step=runtime-field ScriptManager (" + managerClass.getName() + ") has no 'runtime' field");
                return null;
            }
            Object environment = runtime.get(manager);
            if (environment == null) {
                logProbeMiss("step=runtime-field CLIENT manager exists via " + how
                        + " but its runtime environment is not built yet");
                return null;
            }

            step = "context";
            Method contextMethod = findMethodByParameterNames(environment.getClass(), "context");
            if (contextMethod == null) {
                logProbeMiss("step=context " + environment.getClass().getName() + " exposes no context()");
                return null;
            }
            Object result = contextMethod.invoke(environment);
            if (result == null || !contextClass.isInstance(result)) {
                logProbeMiss("step=context expected " + contextClass.getName() + " but got "
                        + (result == null ? "null" : result.getClass().getName()));
                return null;
            }
            logProbeSuccess(how);
            return result;
        } catch (Throwable error) {
            logProbeMiss("step=" + step + " " + describe(error));
            return null;
        }
    }

    /**
     * Returns NekoJS's CLIENT {@code ScriptType} constant, or {@code null} when the enum
     * class cannot be initialized yet. Never throws.
     */
    private static Object clientTypeOrNull() {
        try {
            Class<?> type = Class.forName("com.tkisor.nekojs.api.ScriptType");
            return Enum.valueOf((Class) type, "CLIENT");
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Matches a ScriptType constant by name without initializing the enum class. */
    private static boolean isClientScriptType(Object candidate) {
        if (candidate == null) return false;
        String name = candidate.toString();
        return "CLIENT".equals(name) || "ScriptType.CLIENT".equals(name);
    }

    private static String describeKeys(Map<?, ?> map) {
        StringBuilder builder = new StringBuilder();
        for (Object key : map.keySet()) {
            if (builder.length() > 0) builder.append(',');
            builder.append(String.valueOf(key));
        }
        return builder.toString();
    }

    private static String describe(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        return cause.getClass().getName() + ": " + cause.getMessage();
    }

    /**
     * Reports a probe miss whenever the reason changes, so a permanently unavailable runtime
     * always says why. A repeat of the same reason is only counted, never re-printed.
     */
    private void logProbeMiss(String detail) {
        if (detail.equals(lastProbeMissDetail)) {
            probeMissRepeats++;
            return;
        }
        if (lastProbeMissDetail != null) {
            ApricityUI.LOGGER.warn("[AUI Cleanroom] NekoJS CLIENT runtime probe: {} (x{} before this)",
                    lastProbeMissDetail, probeMissRepeats + 1);
        }
        lastProbeMissDetail = detail;
        probeMissRepeats = 0;
        ApricityUI.LOGGER.warn("[AUI Cleanroom] NekoJS CLIENT runtime probe: {}", detail);
    }

    private void logProbeSuccess(String how) {
        if (probeSuccessLogged) return;
        probeSuccessLogged = true;
        if (lastProbeMissDetail != null) {
            ApricityUI.LOGGER.info("[AUI Cleanroom] NekoJS CLIENT runtime probe recovered: {} (after {} miss(es): {})",
                    how, probeMissRepeats + 1, lastProbeMissDetail);
        }
        ApricityUI.LOGGER.info("[AUI Cleanroom] NekoJS CLIENT runtime located via {}", how);
    }

    /**
     * Resolves a method by parameter <em>type names</em> instead of by Class identity.
     * Cleanroom can hand NekoJS and AUI classes from different loaders, in which case
     * {@code getMethod(name, type)} misses even though the method is right there.
     */
    private static Method findMethodByParameterNames(Class<?> owner, String name, String... parameterTypeNames) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name)) continue;
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length != parameterTypeNames.length) continue;
                boolean matches = true;
                for (int i = 0; i < parameters.length; i++) {
                    if (!parameters[i].getName().equals(parameterTypeNames[i])) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    try {
                        method.setAccessible(true);
                    } catch (Throwable ignored) {
                        // Fall through: a public method is still invokable.
                    }
                    return method;
                }
            }
        }
        return null;
    }

    private static Field findFieldByName(Class<?> owner, String name) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // keep walking up
            }
        }
        return null;
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
