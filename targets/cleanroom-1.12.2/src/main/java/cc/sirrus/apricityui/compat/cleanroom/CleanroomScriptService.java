package cc.sirrus.apricityui.compat.cleanroom;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.parser.JS;
import com.sighs.apricityui.spi.AuiScriptService;
import com.sighs.apricityui.startup.CleanroomStartupEvent;
import com.sighs.apricityui.startup.StartupContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
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
    private static final String DOCUMENT_UUID_BINDING = "__auiDocumentUuid";
    private static final int INLINE_SOURCE_CACHE_LIMIT = 256;

    private enum Backend {
        NONE, NEKOJS, KUBEJS;

        static Backend parse(String value) {
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "nekojs" -> NEKOJS;
                case "kubejs" -> KUBEJS;
                case "none" -> NONE;
                default -> null;
            };
        }

        boolean isInstalled() {
            return this != NONE && isLoaded(name().toLowerCase(Locale.ROOT));
        }
    }

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
    private Method nekoEvalSource;
    private Method nekoGetBindings;
    private Class<?> nekoBindingsClass;
    private Method nekoBindingsHasMember;
    private Method nekoBindingsGetMember;
    private Method nekoBindingsPutMember;
    private Method nekoBindingsRemoveMember;
    private boolean nekoRegistryChecked;
    private Method nekoSwitchScriptId;
    private Method nekoRestoreScriptId;
    private Class<?> graalSourceClass;
    private boolean graalSourceChecked;
    private String cachedGlobalCode;
    private String cachedPreparedGlobalCode;
    private Object cachedGlobalGraalSource;
    private final Map<String, Object> inlineGraalSourceCache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) {
            return size() > INLINE_SOURCE_CACHE_LIMIT;
        }
    };
    private Object kubeEngine;
    private String cachedKubeGlobalCode;
    private CompiledScript cachedKubeGlobalScript;

    private CleanroomScriptService() {
    }

    @Override
    public void startupPhase(StartupContext context) {
        switch (selectedBackend()) {
            case NEKOJS -> MinecraftForge.EVENT_BUS.post(new CleanroomStartupEvent(context));
            case KUBEJS -> LegacyKubeStartupEvent.post(context);
            case NONE -> { }
        }
    }

    @Override
    public void eval(String code, Event event, String source) {
        if (code == null || code.isBlank()) return;
        synchronized (lock) {
            switch (selectedBackend()) {
                case NEKOJS -> evalNeko(code, event, source);
                case KUBEJS -> evalKube(code, event);
                case NONE -> { }
            }
        }
    }

    @Override
    public void evalGlobal(String code, String documentUuid) {
        if (code == null || code.isBlank()) return;
        synchronized (lock) {
            String uuid = documentUuid == null ? "" : documentUuid;
            switch (selectedBackend()) {
                case NEKOJS -> evalNekoGlobal(code, uuid);
                case KUBEJS -> evalKubeGlobal(code, uuid);
                case NONE -> { }
            }
        }
    }

    @Override
    public void reload() {
        synchronized (lock) {
            clearScriptCaches();
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
            if (selected == Backend.NEKOJS) {
                ensureNekoContext();
                String globalJs = com.sighs.apricityui.loader.Loader.readGlobalJS();
                if (globalJs != null && !globalJs.isBlank()) {
                    prepareGlobalGraalSource(globalJs);
                }
            }
            if (selected == Backend.KUBEJS) {
                ScriptEngine engine = ensureKubeEngine();
                String globalJs = com.sighs.apricityui.loader.Loader.readGlobalJS();
                if (engine != null && globalJs != null && !globalJs.isBlank()) {
                    compileKubeGlobal(engine, globalJs);
                }
            }
        }
    }

    private void clearScriptCaches() {
        cachedGlobalCode = null;
        cachedPreparedGlobalCode = null;
        cachedGlobalGraalSource = null;
        inlineGraalSourceCache.clear();
        cachedKubeGlobalCode = null;
        cachedKubeGlobalScript = null;
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

        String priority = CleanroomConfigService.INSTANCE.scriptBackendPriority();
        String override = System.getProperty("apricityui.scriptBackend", "auto").trim();
        Backend forced = Backend.parse(override);
        if (forced == null && !"auto".equalsIgnoreCase(override)) {
            ApricityUI.LOGGER.warn("[AUI Cleanroom] unknown apricityui.scriptBackend '{}'; using the configured priority", override);
        }
        Backend selected = forced == null ? selectByPriority(priority)
                : forced.isInstalled() ? forced : Backend.NONE;
        ApricityUI.LOGGER.info("[AUI Cleanroom] script backend: {} ({})", selected,
                forced == null ? "priority: " + priority : "JVM override: " + override);
        backend = selected;
        return selected;
    }

    private static Backend selectByPriority(String priority) {
        if (Backend.parse(priority) == Backend.NONE) return Backend.NONE;
        boolean hasKnownBackend = false;
        for (String name : priority.split(",", -1)) {
            Backend candidate = Backend.parse(name);
            if (candidate == null || candidate == Backend.NONE) {
                ApricityUI.LOGGER.warn("[AUI Cleanroom] ignoring unknown scriptBackendPriority entry '{}'", name.trim());
                continue;
            }
            hasKnownBackend = true;
            if (candidate.isInstalled()) return candidate;
        }
        if (hasKnownBackend) return Backend.NONE;
        ApricityUI.LOGGER.warn("[AUI Cleanroom] scriptBackendPriority has no valid entries; using {}",
                CleanroomConfigService.DEFAULT_SCRIPT_BACKEND_PRIORITY);
        return selectByPriority(CleanroomConfigService.DEFAULT_SCRIPT_BACKEND_PRIORITY);
    }

    private static boolean isLoaded(String id) {
        try {
            return Loader.isModLoaded(id);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void evalNeko(String code, Event event, String sourceName) {
        try {
            Object context = ensureNekoContext();
            if (context == null) return;
            Class<?> contextClass = context.getClass();
            installNekoGlobals(contextClass, context);
            Object bindings = getNekoBindings(contextClass, context);
            ensureBindingsMethods(bindings);
            if (event == null) {
                removeBinding(bindings, "event");
            } else {
                nekoBindingsPutMember.invoke(bindings, "event", event);
            }

            String scriptId = "apricityui/" + (sourceName != null && !sourceName.isBlank()
                    ? sourceName : (event == null ? "global.js" : "inline.js"));
            String previous = switchNekoScriptId(contextClass, context, scriptId);
            try {
                try {
                    Object graalSource = getOrBuildInlineGraalSource(code, scriptId);
                    if (graalSource != null && nekoEvalSource != null) {
                        nekoEvalSource.invoke(context, graalSource);
                    } else {
                        nekoEval.invoke(context, "js", rewriteDeclarationsForSharedScope(code));
                    }
                } catch (Throwable scoped) {
                    ApricityUI.LOGGER.warn("[AUI Cleanroom] script evaluation unavailable: {}", describe(scoped));
                    throw scoped;
                }
            } finally {
                if (previous != null) {
                    restoreNekoScriptId(contextClass, context, previous);
                }
            }
        } catch (Throwable error) {
            ApricityUI.LOGGER.error("[AUI Cleanroom] NekoJS evaluation failed", error);
        }
    }

    /**
     * Evaluates {@code global.js} using a cached Graal {@code Source} and a
     * per-call {@code __auiDocumentUuid} binding, matching Forge's
     * {@code ApricityJS.evalGlobal} so GraalJS can reuse its parsed/compiled AST
     * across document opens instead of re-parsing 1,100+ lines every time.
     */
    private void evalNekoGlobal(String code, String documentUuid) {
        try {
            Object context = ensureNekoContext();
            if (context == null) return;
            Class<?> contextClass = context.getClass();
            installNekoGlobals(contextClass, context);
            Object bindings = getNekoBindings(contextClass, context);
            ensureBindingsMethods(bindings);
            removeBinding(bindings, "event");

            boolean hadUuid = hasBinding(bindings, DOCUMENT_UUID_BINDING);
            Object previousUuid = hadUuid ? nekoBindingsGetMember.invoke(bindings, DOCUMENT_UUID_BINDING) : null;
            nekoBindingsPutMember.invoke(bindings, DOCUMENT_UUID_BINDING, documentUuid);

            String previousScriptId = switchNekoScriptId(contextClass, context, "apricityui/global.js");
            try {
                Object graalSource = prepareGlobalGraalSource(code);
                if (graalSource != null && nekoEvalSource != null) {
                    nekoEvalSource.invoke(context, graalSource);
                } else {
                    nekoEval.invoke(context, "js", prepareGlobalCode(code));
                }
            } finally {
                if (previousScriptId != null) {
                    restoreNekoScriptId(contextClass, context, previousScriptId);
                }
                if (hadUuid) {
                    nekoBindingsPutMember.invoke(bindings, DOCUMENT_UUID_BINDING, previousUuid);
                } else {
                    removeBinding(bindings, DOCUMENT_UUID_BINDING);
                }
            }
        } catch (Throwable error) {
            ApricityUI.LOGGER.error("[AUI Cleanroom] NekoJS global evaluation failed", error);
        }
    }

    private Object getNekoBindings(Class<?> contextClass, Object context) throws Exception {
        if (nekoGetBindings == null || nekoGetBindings.getDeclaringClass() != contextClass) {
            nekoGetBindings = contextClass.getMethod("getBindings", String.class);
        }
        return nekoGetBindings.invoke(context, "js");
    }

    private void ensureBindingsMethods(Object bindings) throws Exception {
        Class<?> cls = bindings.getClass();
        if (cls == nekoBindingsClass && nekoBindingsPutMember != null) return;
        nekoBindingsClass = cls;
        nekoBindingsGetMember = cls.getMethod("getMember", String.class);
        nekoBindingsPutMember = cls.getMethod("putMember", String.class, Object.class);
        try {
            nekoBindingsHasMember = cls.getMethod("hasMember", String.class);
        } catch (NoSuchMethodException ignored) {
            nekoBindingsHasMember = null;
        }
        try {
            nekoBindingsRemoveMember = cls.getMethod("removeMember", String.class);
        } catch (NoSuchMethodException ignored) {
            nekoBindingsRemoveMember = null;
        }
    }

    private boolean hasBinding(Object bindings, String name) throws Exception {
        if (nekoBindingsHasMember != null) {
            return Boolean.TRUE.equals(nekoBindingsHasMember.invoke(bindings, name));
        }
        return nekoBindingsGetMember.invoke(bindings, name) != null;
    }

    private void removeBinding(Object bindings, String name) throws Exception {
        if (nekoBindingsRemoveMember != null) {
            nekoBindingsRemoveMember.invoke(bindings, name);
        } else {
            nekoBindingsPutMember.invoke(bindings, name, null);
        }
    }

    private String switchNekoScriptId(Class<?> contextClass, Object context, String scriptId) {
        ensureNekoRegistryMethods(contextClass);
        if (nekoSwitchScriptId == null) return null;
        try {
            return (String) nekoSwitchScriptId.invoke(null, context, scriptId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void restoreNekoScriptId(Class<?> contextClass, Object context, String previous) {
        if (nekoRestoreScriptId == null) return;
        try {
            nekoRestoreScriptId.invoke(null, context, previous);
        } catch (Throwable ignored) {
        }
    }

    private void ensureNekoRegistryMethods(Class<?> contextClass) {
        if (nekoRegistryChecked) return;
        nekoRegistryChecked = true;
        try {
            Class<?> registry = Class.forName("com.tkisor.nekojs.script.ScriptContextRegistry");
            nekoSwitchScriptId = registry.getMethod("switchCurrentScriptId", contextClass, String.class);
            nekoRestoreScriptId = registry.getMethod("restoreCurrentScriptId", contextClass, String.class);
        } catch (Throwable ignored) {
            nekoSwitchScriptId = null;
            nekoRestoreScriptId = null;
        }
    }

    private String prepareGlobalCode(String code) {
        if (code.equals(cachedGlobalCode) && cachedPreparedGlobalCode != null) {
            return cachedPreparedGlobalCode;
        }
        String prepared = rewriteDeclarationsForSharedScope(code)
                .replace("\"__AUI_DOCUMENT_UUID__\"", DOCUMENT_UUID_BINDING)
                .replace("'__AUI_DOCUMENT_UUID__'", DOCUMENT_UUID_BINDING)
                .replace("__AUI_DOCUMENT_UUID__", DOCUMENT_UUID_BINDING);
        cachedGlobalCode = code;
        cachedPreparedGlobalCode = prepared;
        cachedGlobalGraalSource = null;
        return prepared;
    }

    private Object prepareGlobalGraalSource(String code) {
        String prepared = prepareGlobalCode(code);
        if (cachedGlobalGraalSource != null) return cachedGlobalGraalSource;
        cachedGlobalGraalSource = buildCachedGraalSource(prepared, "apricityui/global.js");
        return cachedGlobalGraalSource;
    }

    private Object getOrBuildInlineGraalSource(String code, String name) {
        Object cached = inlineGraalSourceCache.get(code);
        if (cached != null) return cached;
        String rewritten = rewriteDeclarationsForSharedScope(code);
        Object built = buildCachedGraalSource(rewritten, name);
        if (built != null) {
            inlineGraalSourceCache.put(code, built);
        }
        return built;
    }

    private Object buildCachedGraalSource(String sourceText, String name) {
        Class<?> sourceCls = resolveGraalSourceClass();
        if (sourceCls == null) return null;
        try {
            Method newBuilder = sourceCls.getMethod("newBuilder", String.class, CharSequence.class, String.class);
            Object builder = newBuilder.invoke(null, "js", sourceText, name);
            try {
                builder.getClass().getMethod("cached", boolean.class).invoke(builder, true);
            } catch (NoSuchMethodException ignored) {
            }
            return builder.getClass().getMethod("build").invoke(builder);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Class<?> resolveGraalSourceClass() {
        if (graalSourceChecked) return graalSourceClass;
        graalSourceChecked = true;
        try {
            graalSourceClass = Class.forName("graal.graalvm.polyglot.Source");
        } catch (Throwable ignored) {
            graalSourceClass = null;
        }
        return graalSourceClass;
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
                cacheNekoEvalMethods(contextClass);
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
            cacheNekoEvalMethods(contextClass);
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

    private void cacheNekoEvalMethods(Class<?> contextClass) throws NoSuchMethodException {
        nekoEval = contextClass.getMethod("eval", String.class, CharSequence.class);
        Class<?> sourceCls = resolveGraalSourceClass();
        if (sourceCls != null) {
            try {
                nekoEvalSource = contextClass.getMethod("eval", sourceCls);
            } catch (NoSuchMethodException ignored) {
                nekoEvalSource = null;
            }
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

    private void evalKubeGlobal(String code, String documentUuid) {
        try {
            ScriptEngine engine = ensureKubeEngine();
            if (engine == null) return;
            Bindings bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
            bindings.remove("event");
            boolean hadUuid = bindings.containsKey(DOCUMENT_UUID_BINDING);
            Object previousUuid = hadUuid ? bindings.get(DOCUMENT_UUID_BINDING) : null;
            bindings.put(DOCUMENT_UUID_BINDING, documentUuid);
            try {
                CompiledScript compiled = compileKubeGlobal(engine, code);
                if (compiled != null) {
                    compiled.eval(bindings);
                } else {
                    String prepared = JS.rewriteForLegacyKube(code)
                            .replace("\"__AUI_DOCUMENT_UUID__\"", DOCUMENT_UUID_BINDING)
                            .replace("'__AUI_DOCUMENT_UUID__'", DOCUMENT_UUID_BINDING)
                            .replace("__AUI_DOCUMENT_UUID__", DOCUMENT_UUID_BINDING);
                    engine.eval(prepared, bindings);
                }
            } finally {
                if (hadUuid) bindings.put(DOCUMENT_UUID_BINDING, previousUuid);
                else bindings.remove(DOCUMENT_UUID_BINDING);
            }
        } catch (Throwable error) {
            ApricityUI.LOGGER.error("[AUI Cleanroom] legacy KubeJS global evaluation failed", error);
        }
    }

    private CompiledScript compileKubeGlobal(ScriptEngine engine, String code) {
        if (!(engine instanceof Compilable compilable)) return null;
        if (code.equals(cachedKubeGlobalCode) && cachedKubeGlobalScript != null) {
            return cachedKubeGlobalScript;
        }
        try {
            String prepared = JS.rewriteForLegacyKube(code)
                    .replace("\"__AUI_DOCUMENT_UUID__\"", DOCUMENT_UUID_BINDING)
                    .replace("'__AUI_DOCUMENT_UUID__'", DOCUMENT_UUID_BINDING)
                    .replace("__AUI_DOCUMENT_UUID__", DOCUMENT_UUID_BINDING);
            cachedKubeGlobalScript = compilable.compile(prepared);
            cachedKubeGlobalCode = code;
            return cachedKubeGlobalScript;
        } catch (Throwable ignored) {
            cachedKubeGlobalScript = null;
            cachedKubeGlobalCode = null;
            return null;
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
