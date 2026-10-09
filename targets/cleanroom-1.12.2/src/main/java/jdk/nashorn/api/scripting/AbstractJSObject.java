package jdk.nashorn.api.scripting;

/** Java 8 Nashorn compatibility base class backed by OpenJDK Nashorn. */
public abstract class AbstractJSObject
        extends org.openjdk.nashorn.api.scripting.AbstractJSObject
        implements JSObject {
    protected AbstractJSObject() {
        super();
    }
}
