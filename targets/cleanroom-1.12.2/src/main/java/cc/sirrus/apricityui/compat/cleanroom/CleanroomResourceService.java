package cc.sirrus.apricityui.compat.cleanroom;

import com.sighs.apricityui.spi.AuiResourceService;
import com.sighs.apricityui.spi.RenderHandle;
import com.sighs.apricityui.spi.TextureKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        IResourceManager resources = manager();
        if (resources == null) return Collections.emptyMap();
        // 1.12's IResourceManager can open a known ResourceLocation but does
        // not expose the 1.13+ recursive listing API. Static AUI resources are
        // discovered from the jar/filesystem loader; known pack paths still use
        // openResource above.
        return Collections.emptyMap();
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
        // RenderType is supplied by the 1.12 BufferBuilder backend in the
        // rendering stage. Returning null makes callers use their safe fallback.
        return null;
    }
}
