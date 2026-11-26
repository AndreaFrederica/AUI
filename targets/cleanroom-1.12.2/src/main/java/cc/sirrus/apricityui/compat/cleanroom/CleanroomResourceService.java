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
        AssetPath asset = assetPath(value);
        return asset == null ? null : new ResourceLocation(asset.namespace(), asset.path());
    }

    /** Converts an AUI or Minecraft asset path to its actual namespace and path. */
    private static AssetPath assetPath(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/').replaceFirst("^/+", "");
        if (normalized.isEmpty()) return null;
        if (normalized.startsWith("apricity/")) {
            String remainder = normalized.substring("apricity/".length());
            int colon = remainder.indexOf(':');
            if (colon > 0) {
                String namespace = remainder.substring(0, colon);
                String path = remainder.substring(colon + 1);
                if (!namespace.isBlank() && !path.isBlank()) return new AssetPath(namespace, "apricity/" + path);
            }
            return new AssetPath("apricityui", "apricity/" + remainder);
        }
        int colon = normalized.indexOf(':');
        if (colon > 0) {
            String namespace = normalized.substring(0, colon);
            String path = normalized.substring(colon + 1);
            if (!namespace.isBlank() && !path.isBlank()) return new AssetPath(namespace, path);
        }
        return new AssetPath("apricityui", normalized);
    }

    private record AssetPath(String namespace, String path) { }

    @Override
    public Optional<InputStream> openResource(String path) {
        IResourceManager resources = manager();
        ResourceLocation location = location(path);
        if (resources != null && location != null) {
            try {
                return Optional.of(resources.getResource(location).getInputStream());
            } catch (Exception ignored) {
                // Cleanroom's development discoverer can expose classes and
                // resources as separate roots without registering the latter
                // in IResourceManager. Fall through to the classloader path;
                // packaged jars still use the normal resource-pack route.
            }
        }
        AssetPath asset = assetPath(path);
        if (asset != null) {
            InputStream direct = CleanroomResourceService.class.getClassLoader()
                    .getResourceAsStream("assets/" + asset.namespace() + "/" + asset.path());
            if (direct != null) return Optional.of(direct);
        }
        return Optional.empty();
    }

    @Override
    public Map<String, String> listResourcePaths(String path, String suffix) {
        String prefix = path == null ? "" : path.replace('\\', '/').replaceAll("^/+|/+$", "");
        String normalizedSuffix = suffix == null ? "" : suffix;
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        try {
            Enumeration<URL> roots = CleanroomResourceService.class.getClassLoader().getResources("assets/");
            while (roots.hasMoreElements()) {
                URL url = roots.nextElement();
                if ("file".equalsIgnoreCase(url.getProtocol())) {
                    Path assets = Paths.get(url.toURI());
                    if (!Files.isDirectory(assets)) continue;
                    try (java.util.stream.Stream<Path> namespaces = Files.list(assets)) {
                        namespaces.filter(Files::isDirectory).forEach(namespace ->
                                scanAssetDirectory(namespace, namespace.getFileName().toString(), prefix, normalizedSuffix, result));
                    }
                } else if ("jar".equalsIgnoreCase(url.getProtocol())) {
                    JarURLConnection connection = (JarURLConnection) url.openConnection();
                    try (JarFile jar = connection.getJarFile()) {
                        Enumeration<JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            String name = entries.nextElement().getName();
                            String marker = "assets/";
                            if (!name.startsWith(marker) || name.endsWith("/")) continue;
                            String remainder = name.substring(marker.length());
                            int slash = remainder.indexOf('/');
                            if (slash <= 0) continue;
                            String namespace = remainder.substring(0, slash);
                            String relative = remainder.substring(slash + 1);
                            String expectedPrefix = prefix.isEmpty() ? "" : prefix + "/";
                            if (!relative.startsWith(expectedPrefix) || !relative.endsWith(normalizedSuffix)) continue;
                            String tail = relative.substring(expectedPrefix.length());
                            String key = "apricityui".equals(namespace) ? tail : namespace + ":" + tail;
                            result.putIfAbsent(key, namespace);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Resource-pack enumeration is optional in 1.12; known paths still use getResource.
        }
        return result.isEmpty() ? Collections.emptyMap() : Map.copyOf(result);
    }

    private static void scanAssetDirectory(Path namespace, String namespaceName, String prefix,
                                           String suffix, Map<String, String> result) {
        Path root = prefix.isEmpty() ? namespace : namespace.resolve(prefix);
        if (!Files.isDirectory(root)) return;
        String expectedPrefix = prefix.isEmpty() ? "" : prefix + "/";
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                String relative = namespace.relativize(file).toString().replace('\\', '/');
                if (!relative.startsWith(expectedPrefix) || !relative.endsWith(suffix)) return;
                String tail = relative.substring(expectedPrefix.length());
                String key = "apricityui".equals(namespaceName) ? tail : namespaceName + ":" + tail;
                result.putIfAbsent(key, namespaceName);
            });
        } catch (IOException ignored) {
            // Resource enumeration is optional; direct opens still work.
        }
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
