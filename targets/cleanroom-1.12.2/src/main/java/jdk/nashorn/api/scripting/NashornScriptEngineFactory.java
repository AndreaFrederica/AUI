package jdk.nashorn.api.scripting;

import javax.script.ScriptEngine;

/**
 * Java 8 Nashorn compatibility name used by the legacy 1.12 KubeJS build.
 *
 * <p>Java 15 removed the JDK supplied Nashorn package, while the maintained
 * OpenJDK Nashorn engine uses {@code org.openjdk.nashorn}.  KubeJS 1.1.0 only
 * constructs this factory and asks it for a {@link ScriptEngine}; keeping this
 * tiny adapter lets that old API run on Cleanroom's Java 25 toolchain.</p>
 */
public final class NashornScriptEngineFactory {
    public NashornScriptEngineFactory() {
    }

    public ScriptEngine getScriptEngine() {
        // Legacy KubeJS scripts use `let`, arrow functions and other ES6
        // syntax. The standalone Nashorn engine defaults to ES5 unless this
        // switch is supplied explicitly.
        return new org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory()
                .getScriptEngine("--language=es6");
    }
}
