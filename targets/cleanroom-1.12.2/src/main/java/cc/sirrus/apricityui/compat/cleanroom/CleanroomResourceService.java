package cc.sirrus.apricityui.compat.cleanroom;

import com.sighs.apricityui.spi.AuiResourceService;
import com.sighs.apricityui.spi.RenderHandle;
import com.sighs.apricityui.spi.TextureKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.InputStream;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Resource-pack bridge for the 1.12 IResourceManager API. */
public final class CleanroomResourceService implements AuiResourceService {
    public static final CleanroomResourceService INSTANCE = new CleanroomResourceService();

    private CleanroomResourceService() { }

    private static IResourceManager manager() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            return mc == null ? null : mc.getResourceManager();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ResourceLocation location(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.startsWith("/") ? value.substring(1) : value;
        if (normalized.startsWith("minecraft:")) return new ResourceLocation(normalized);
        if (normalized.startsWith("apricityui:")) return new ResourceLocation(normalized);
        return new ResourceLocation("apricityui", normalized);
    }

    @Override
    public Optional<InputStream> openResource(String path) {
        IResourceManager resources = manager();
        ResourceLocation location = location(path);
        if (resources == null || location == null) return Optional.empty();
        try {
            return Optional.of(resources.getResource(location).getInputStream());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Map<String, String> listResourcePaths(String path, String suffix) {
        String prefix = path == null ? "" : path.replace('\\', '/').replaceAll("^/+|/+$", "");
        String normalizedSuffix = suffix == null ? "" : suffix;
        String root = "assets/apricityui/" + (prefix.isEmpty() ? "" : prefix + "/");
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        try {
            Enumeration<URL> roots = CleanroomResourceService.class.getClassLoader().getResources(root);
            while (roots.hasMoreElements()) {
                URL url = roots.nextElement();
                if ("file".equalsIgnoreCase(url.getProtocol())) {
                    Path directory = Paths.get(url.toURI());
                    if (Files.isDirectory(directory)) {
                        try (java.util.stream.Stream<Path> files = Files.walk(directory)) {
                            files.filter(Files::isRegularFile).forEach(file -> {
                                String relative = directory.relativize(file).toString().replace('\\', '/');
                                if (relative.endsWith(normalizedSuffix)) result.putIfAbsent(relative, "apricityui");
                            });
                        }
                    }
                } else if ("jar".equalsIgnoreCase(url.getProtocol())) {
                    JarURLConnection connection = (JarURLConnection) url.openConnection();
                    try (JarFile jar = connection.getJarFile()) {
                        Enumeration<JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            String name = entries.nextElement().getName();
                            if (!name.startsWith(root) || name.endsWith("/") || !name.endsWith(normalizedSuffix)) continue;
                            result.putIfAbsent(name.substring(root.length()), "apricityui");
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Resource-pack enumeration is optional in 1.12; known paths still use getResource.
        }
        return result.isEmpty() ? Collections.emptyMap() : Map.copyOf(result);
    }

    @Override
    public TextureKey locationOf(String key) {
        return TextureKey.of(key == null ? "" : key);
    }

    @Override
    public TextureKey tryParseTextureKey(String src) {
        return location(src) == null ? null : TextureKey.of(src);
    }

    @Override
    public Object textureLocation(TextureKey key) {
        return key == null ? null : location(key.value());
    }

    @Override
    public RenderHandle smoothRenderType(TextureKey key, boolean blur, boolean depthTest) {
        ResourceLocation location = key == null ? null : location(key.value());
        return location == null ? null : RenderHandle.of(location);
    }
}
