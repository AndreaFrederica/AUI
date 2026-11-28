package cc.sirrus.apricityui.compat.cleanroom;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.spi.AuiConfigService;
import com.sighs.apricityui.spi.AuiServices;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Persistent file-backed {@link AuiConfigService} for Cleanroom 1.12.2.
 * Stores settings in {@code <configDir>/apricityui.properties} matching the
 * Fabric target's property schema and defaults.
 */
public final class CleanroomConfigService implements AuiConfigService {
    public static final CleanroomConfigService INSTANCE = new CleanroomConfigService();
    static final String DEFAULT_SCRIPT_BACKEND_PRIORITY = "nekojs,kubejs";
    private static final String SCRIPT_BACKEND_PRIORITY = "scriptBackendPriority";

    private final Object lock = new Object();
    private final Properties values = new Properties();
    private final AtomicBoolean reloadPending = new AtomicBoolean();
    private volatile boolean loaded;
    private volatile Path configPath;

    private CleanroomConfigService() {
    }

    private Path resolvePath() {
        Path existing = configPath;
        if (existing != null) return existing;
        Path dir = null;
        try {
            dir = AuiServices.client().getConfigDirectory();
        } catch (Throwable ignored) {
        }
        if (dir == null) {
            dir = Path.of("config").toAbsolutePath().normalize();
        }
        Path resolved = dir.resolve("apricityui.properties");
        configPath = resolved;
        return resolved;
    }

    private void ensureLoaded() {
        if (loaded) return;
        synchronized (lock) {
            if (loaded) return;
            Path path = resolvePath();
            if (Files.isRegularFile(path)) {
                try (InputStream input = Files.newInputStream(path)) {
                    values.load(input);
                } catch (IOException error) {
                    ApricityUI.LOGGER.warn("[AUI Config] failed to load {}", path, error);
                }
            }
            loaded = true;
        }
    }

    private boolean bool(String key, boolean fallback) {
        ensureLoaded();
        synchronized (lock) {
            String raw = values.getProperty(key);
            if (raw == null) return fallback;
            return Boolean.parseBoolean(raw.trim());
        }
    }

    private int integer(String key, int fallback, int min, int max) {
        ensureLoaded();
        synchronized (lock) {
            String raw = values.getProperty(key);
            if (raw == null) return fallback;
            try {
                int parsed = Integer.parseInt(raw.trim());
                return Math.max(min, Math.min(max, parsed));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
    }

    private float decimal(String key, float fallback, float min, float max) {
        ensureLoaded();
        synchronized (lock) {
            String raw = values.getProperty(key);
            if (raw == null) return fallback;
            try {
                float parsed = Float.parseFloat(raw.trim());
                if (!Float.isFinite(parsed)) return fallback;
                return Math.max(min, Math.min(max, parsed));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
    }

    private void set(String key, Object value) {
        ensureLoaded();
        synchronized (lock) {
            values.setProperty(key, String.valueOf(value));
        }
    }

    String scriptBackendPriority() {
        ensureLoaded();
        synchronized (lock) {
            String configured = values.getProperty(SCRIPT_BACKEND_PRIORITY);
            if (configured != null) return configured;
            values.setProperty(SCRIPT_BACKEND_PRIORITY, DEFAULT_SCRIPT_BACKEND_PRIORITY);
            save();
            return DEFAULT_SCRIPT_BACKEND_PRIORITY;
        }
    }

    @Override public boolean debugAutoReload() { return bool("debugAutoReload", false); }
    @Override public void setDebugAutoReload(boolean value) { set("debugAutoReload", value); }
    @Override public boolean aiAutoScreenshot() { return bool("aiAutoScreenshot", false); }
    @Override public void setAiAutoScreenshot(boolean value) { set("aiAutoScreenshot", value); }
    @Override public boolean frameTimingHud() { return bool("frameTimingHud", false); }
    @Override public void setFrameTimingHud(boolean value) { set("frameTimingHud", value); }
    @Override public boolean remoteDebug() { return bool("remoteDebug", false); }
    @Override public void setRemoteDebug(boolean value) { set("remoteDebug", value); }
    @Override public boolean viewportZoomPassThrough() { return bool("viewportZoomPassThrough", true); }
    @Override public void setViewportZoomPassThrough(boolean value) { set("viewportZoomPassThrough", value); }
    @Override public boolean blockMouseEventsWhenCursorHidden() { return bool("blockMouseEventsWhenCursorHidden", true); }
    @Override public void setBlockMouseEventsWhenCursorHidden(boolean value) { set("blockMouseEventsWhenCursorHidden", value); }
    @Override public float worldWindowDepthOffsetScale() { return decimal("worldWindowDepthOffsetScale", 0.01f, 0.0f, 1.0f); }
    @Override public void setWorldWindowDepthOffsetScale(double value) {
        double clamped = Double.isFinite(value) ? Math.max(0.0d, Math.min(1.0d, value)) : 0.01d;
        set("worldWindowDepthOffsetScale", clamped);
    }
    @Override public int worldWindowMaxDisplayDistance() { return integer("worldWindowMaxDisplayDistance", 128, 1, 2048); }
    @Override public void setWorldWindowMaxDisplayDistance(int value) {
        set("worldWindowMaxDisplayDistance", Math.max(1, Math.min(2048, value)));
    }
    @Override public boolean worldWindowLodEnabled() { return bool("worldWindowLodEnabled", false); }
    @Override public void setWorldWindowLodEnabled(boolean value) { set("worldWindowLodEnabled", value); }
    @Override public int worldWindowFullDetailDistance() { return integer("worldWindowFullDetailDistance", 16, 1, 2048); }
    @Override public void setWorldWindowFullDetailDistance(int value) {
        set("worldWindowFullDetailDistance", Math.max(1, Math.min(2048, value)));
    }
    @Override public int worldWindowReducedDetailDistance() { return integer("worldWindowReducedDetailDistance", 48, 1, 2048); }
    @Override public void setWorldWindowReducedDetailDistance(int value) {
        set("worldWindowReducedDetailDistance", Math.max(1, Math.min(2048, value)));
    }
    @Override public boolean initialCommitSliceEnabled() { return bool("initialCommitSliceEnabled", true); }
    @Override public void setInitialCommitSliceEnabled(boolean value) { set("initialCommitSliceEnabled", value); }
    @Override public float initialCommitSliceMs() { return decimal("initialCommitSliceMs", 16.0f, 0.5f, 1000.0f); }
    @Override public void setInitialCommitSliceMs(double value) {
        double clamped = Double.isFinite(value) ? Math.max(0.5d, Math.min(1000.0d, value)) : 16.0d;
        set("initialCommitSliceMs", clamped);
    }

    @Override
    public void save() {
        ensureLoaded();
        Path path = resolvePath();
        synchronized (lock) {
            try {
                Path parent = path.getParent();
                if (parent != null) Files.createDirectories(parent);
                try (OutputStream output = Files.newOutputStream(path)) {
                    values.store(output, "ApricityUI Cleanroom 1.12.2 configuration");
                }
            } catch (IOException error) {
                ApricityUI.LOGGER.warn("[AUI Config] failed to save {}", path, error);
            }
        }
    }

    @Override
    public void markClientReloadPending() {
        reloadPending.set(true);
    }

    @Override
    public boolean consumeClientReloadPending() {
        return reloadPending.getAndSet(false);
    }
}
