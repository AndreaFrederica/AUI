package jdk.nashorn.api.scripting;

/**
 * Compatibility exception name used in KubeJS 1.12 event handlers. The actual
 * OpenJDK Nashorn engine reports its own exception type; this class exists so
 * legacy KubeJS method signatures and catch blocks remain linkable on Java 25.
 */
public abstract class NashornException extends RuntimeException {
    protected NashornException(String message) {
        super(message);
    }

    protected NashornException(String message, Throwable cause) {
        super(message, cause);
    }
}
