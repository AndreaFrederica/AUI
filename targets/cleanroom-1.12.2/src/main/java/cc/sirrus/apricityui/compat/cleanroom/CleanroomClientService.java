package cc.sirrus.apricityui.compat.cleanroom;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.screen.ApricityScreen;
import com.sighs.apricityui.screen.AuiLinkedScreen;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.spi.AuiClientService;
import com.sighs.apricityui.style.Text;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.joml.Vector3f;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.Desktop;
import java.io.File;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Files;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Cleanroom 0.6 / Minecraft 1.12 implementation of the common client SPI.
 * Minecraft 1.12 has no modern Window or camera classes, so this class is the
 * single boundary that translates the old client fields and MouseHelper API.
 */
public final class CleanroomClientService implements AuiClientService {
    public static final CleanroomClientService INSTANCE = new CleanroomClientService();
    private net.minecraftforge.fml.common.discovery.ASMDataTable annotationData;

    public void setAnnotationData(net.minecraftforge.fml.common.discovery.ASMDataTable annotationData) {
        this.annotationData = annotationData;
    }

    @Override
    public void scanAnnotationStaticMethods(Class<? extends Annotation> annotation,
            Consumer<java.lang.reflect.Method> consumer, Runnable onFinished) {
        try {
            if (annotationData == null) return;
            var loader = Thread.currentThread().getContextClassLoader();
            for (String name : annotationData.getAll(annotation.getName()).stream()
                    .map(net.minecraftforge.fml.common.discovery.ASMDataTable.ASMData::getClassName).distinct().toList()) {
                try {
                    for (var method : Class.forName(name, false, loader).getDeclaredMethods()) {
                        if (method.isAnnotationPresent(annotation)) consumer.accept(method);
                    }
                } catch (ClassNotFoundException | LinkageError failure) {
                    com.sighs.apricityui.ApricityUI.LOGGER.warn("Cannot scan startup hooks in {}", name, failure);
                }
            }
        } finally {
            onFinished.run();
        }
    }
    private volatile boolean mouseGrabbed;
    private volatile Boolean productionEnvironment;
    private final Set<String> scanPackages = new HashSet<>();
    private ScaledResolution cachedResolution;
    private int cachedDisplayWidth = -1;
    private int cachedDisplayHeight = -1;
    private int cachedGuiScale = Integer.MIN_VALUE;
    private boolean cachedUnicode;

    private CleanroomClientService() {
    }

    private static Minecraft minecraft() {
        try {
            return Minecraft.getMinecraft();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ScaledResolution resolution(Minecraft mc) {
        if (mc == null) return null;
        int displayW = mc.displayWidth;
        int displayH = mc.displayHeight;
        int guiScale = mc.gameSettings == null ? 0 : mc.gameSettings.guiScale;
        boolean unicode = mc.isUnicode();
        ScaledResolution cached = cachedResolution;
        if (cached != null && displayW == cachedDisplayWidth && displayH == cachedDisplayHeight
                && guiScale == cachedGuiScale && unicode == cachedUnicode) {
            return cached;
        }
        ScaledResolution fresh = new ScaledResolution(mc);
        cachedDisplayWidth = displayW;
        cachedDisplayHeight = displayH;
        cachedGuiScale = guiScale;
        cachedUnicode = unicode;
        cachedResolution = fresh;
        return fresh;
    }

    @Override
    public Size getWindowSize() {
        ScaledResolution scaled = resolution(minecraft());
        return scaled == null ? new Size(1920, 1080) : new Size(scaled.getScaledWidth(), scaled.getScaledHeight());
    }

    @Override
    public Position getMousePosition() {
        Minecraft mc = minecraft();
        ScaledResolution scaled = resolution(mc);
        if (mc == null || scaled == null || !Mouse.isCreated()) return new Position(0, 0);
        double scaleX = (double) scaled.getScaledWidth() / Math.max(1, mc.displayWidth);
        double scaleY = (double) scaled.getScaledHeight() / Math.max(1, mc.displayHeight);
        return new Position(Mouse.getX() * scaleX, scaled.getScaledHeight() - Mouse.getY() * scaleY - 1.0);
    }

    @Override
    public Position getMousePositionDirectly() {
        return getMousePosition();
    }

    @Override
    public double getWindowWidth() {
        Minecraft mc = minecraft();
        return mc == null ? 1920 : mc.displayWidth;
    }

    @Override
    public double getWindowHeight() {
        Minecraft mc = minecraft();
        return mc == null ? 1080 : mc.displayHeight;
    }

    @Override
    public int getScaledWidth() {
        Size size = getWindowSize();
        return (int) size.width();
    }

    @Override
    public int getScaledHeight() {
        Size size = getWindowSize();
        return (int) size.height();
    }

    @Override
    public int getDefaultFontWidth(String text, boolean bold, boolean oblique, double strokeWidth) {
        Minecraft mc = minecraft();
        if (mc != null && mc.fontRenderer != null) return mc.fontRenderer.getStringWidth(text == null ? "" : text);
        return (int) Math.ceil((text == null ? 0 : text.length()) * 8.0 + Math.max(0, strokeWidth) * 2.0);
    }

    @Override
    public void drawDefaultFont(PoseStack poseStack, Text text, String content, Position position) {
        Minecraft mc = minecraft();
        if (mc == null || mc.fontRenderer == null || text == null || position == null) return;
        String value = content == null ? "" : content;
        float scale = (float) Math.max(0.01, text.defaultFontScale());
        org.joml.Vector4f origin = poseStack == null
                ? new org.joml.Vector4f((float) position.x, (float) position.y, 0, 1)
                : poseStack.last().pose().transform(new org.joml.Vector4f((float) position.x, (float) position.y, 0, 1));
        int color = text.color == null ? 0xFFFFFFFF : text.color.getValue();
        StringBuilder formatted = new StringBuilder();
        if (text.isBold()) formatted.append('\u00a7').append('l');
        if (text.isOblique()) formatted.append('\u00a7').append('o');
        formatted.append(value);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(origin.x, origin.y, origin.z);
            GlStateManager.scale(scale, scale, 1.0F);
            int stroke = Math.max(0, (int) Math.ceil(text.strokeWidth));
            int strokeColor = text.strokeColor == null ? 0xFF000000 : text.strokeColor.getValue();
            if (stroke > 0) {
                for (int ox = -stroke; ox <= stroke; ox++) {
                    for (int oy = -stroke; oy <= stroke; oy++) {
                        if (ox * ox + oy * oy <= stroke * stroke && (ox != 0 || oy != 0)) {
                            mc.fontRenderer.drawString(formatted.toString(), ox, oy, strokeColor, false);
                        }
                    }
                }
            }
            mc.fontRenderer.drawString(formatted.toString(), 0, 0, color, false);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean isKeyPressed(String keyName) {
        if (keyName == null || !Keyboard.isCreated()) return false;
        if (keyName.startsWith("key.keyboard.")) keyName = keyName.substring("key.keyboard.".length());
        Integer code = CleanroomInput.keyCode(keyName);
        return code != null && Keyboard.isKeyDown(code);
    }

    @Override
    public boolean isMouseGrabbed() {
        if (Mouse.isCreated()) {
            return Mouse.isGrabbed();
        }
        Minecraft mc = minecraft();
        return mc != null ? mc.inGameHasFocus : mouseGrabbed;
    }

    @Override
    public Position getMousePositionForWorldInteraction() {
        return getMousePosition();
    }

    @Override
    public void openScreen(String templatePath) {
        Minecraft mc = minecraft();
        if (mc != null) mc.displayGuiScreen(new ApricityScreen(templatePath));
    }

    @Override
    public void closeScreen() {
        Minecraft mc = minecraft();
        if (mc != null) mc.displayGuiScreen(null);
    }

    @Override
    public Path getGameDirectory() {
        Minecraft mc = minecraft();
        return mc == null ? Path.of("run").toAbsolutePath() : mc.gameDir.toPath().toAbsolutePath();
    }

    @Override
    public Path getConfigDirectory() {
        return getGameDirectory().resolve("config");
    }

    @Override
    public boolean isProduction() {
        Boolean cached = productionEnvironment;
        if (cached != null) return cached;
        boolean prod = true;
        try {
            Class<?> launch = Class.forName("net.minecraft.launchwrapper.Launch");
            Object blackboard = launch.getField("blackboard").get(null);
            if (blackboard instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("fml.deobfuscatedEnvironment"))) {
                prod = false;
            }
        } catch (Throwable ignored) {
            prod = false;
        }
        productionEnvironment = prod;
        return prod;
    }

    @Override
    public void addScanPackage(String basePackage) {
        if (basePackage != null && !basePackage.isBlank()) scanPackages.add(basePackage.trim());
    }

    @Override
    public void addScanPackages(String... basePackages) {
        if (basePackages != null) for (String basePackage : basePackages) addScanPackage(basePackage);
    }

    @Override
    public void scanAnnotationClasses(Class<? extends Annotation> annotationClass,
                                      Predicate<Map<String, Object>> annotationPredicate,
                                      Consumer<Class<?>> consumer,
                                      Runnable onFinished) {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Set<String> classNames = new HashSet<>();
            for (String packageName : new ArrayList<>(scanPackages)) collectClasses(loader, packageName, classNames);
            for (String className : classNames) {
                try {
                    Class<?> type = Class.forName(className, false, loader);
                    Annotation annotation = type.getAnnotation(annotationClass);
                    if (annotation == null) continue;
                    Map<String, Object> values = new HashMap<>();
                    for (java.lang.reflect.Method method : annotation.annotationType().getDeclaredMethods()) {
                        try { values.put(method.getName(), method.invoke(annotation)); }
                        catch (ReflectiveOperationException ignored) { }
                    }
                    if (annotationPredicate == null || annotationPredicate.test(values)) consumer.accept(type);
                } catch (Throwable ignored) {
                    // A third-party class may reference an optional dependency;
                    // one failed candidate must not prevent other elements from
                    // registering.
                }
            }
        } finally {
            if (onFinished != null) onFinished.run();
        }
    }

    private static void collectClasses(ClassLoader loader, String packageName, Set<String> output) {
        String resourcePath = packageName.replace('.', '/');
        try {
            Enumeration<URL> resources = loader.getResources(resourcePath);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                if ("file".equals(url.getProtocol())) {
                    Path root = Path.of(url.toURI());
                    try (var files = Files.walk(root)) {
                        files.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".class"))
                                .forEach(path -> output.add(packageName + '.' + root.relativize(path).toString()
                                        .replace(File.separatorChar, '.').replaceAll("\\.class$", "")));
                    }
                } else if ("jar".equals(url.getProtocol())) {
                    JarURLConnection connection = (JarURLConnection) url.openConnection();
                    try (JarFile jar = connection.getJarFile()) {
                        Enumeration<JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            String name = entries.nextElement().getName();
                            if (name.startsWith(resourcePath + "/") && name.endsWith(".class")) {
                                output.add(name.substring(0, name.length() - 6).replace('/', '.'));
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) { }
    }

    @Override
    public void openUri(URI uri) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(uri);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void openFile(File file) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public long getWindowHandle() {
        return 0L;
    }

    @Override
    public net.minecraft.world.phys.Vec3 getCameraPosition() {
        Minecraft mc = minecraft();
        Entity view = mc == null ? null : mc.getRenderViewEntity();
        Vec3d position = view == null ? new Vec3d(0, 0, 0) : view.getPositionEyes(mc.getRenderPartialTicks());
        return new net.minecraft.world.phys.Vec3(position.x, position.y, position.z);
    }

    @Override
    public Vector3f getCameraLookVector() {
        Minecraft mc = minecraft();
        Entity view = mc == null ? null : mc.getRenderViewEntity();
        if (view == null) return new Vector3f(0, 0, -1);
        Vec3d look = view.getLook(mc.getRenderPartialTicks());
        return new Vector3f((float) look.x, (float) look.y, (float) look.z);
    }

    @Override
    public boolean hasClientWorld() {
        Minecraft mc = minecraft();
        return mc != null && mc.world != null;
    }

    @Override
    public boolean isClientGameplayActive() {
        Minecraft mc = minecraft();
        return mc != null && mc.world != null && mc.currentScreen == null;
    }

    @Override
    public void releaseMouse() {
        Minecraft mc = minecraft();
        if (mc != null && mc.mouseHelper != null) mc.mouseHelper.ungrabMouseCursor();
        mouseGrabbed = false;
    }

    @Override
    public void grabMouse() {
        Minecraft mc = minecraft();
        if (mc != null && mc.mouseHelper != null) mc.mouseHelper.grabMouseCursor();
        mouseGrabbed = true;
    }

    @Override
    public Window getWindow() {
        Minecraft mc = minecraft();
        ScaledResolution scaled = resolution(mc);
        int displayW = mc == null ? 1920 : mc.displayWidth;
        int displayH = mc == null ? 1080 : mc.displayHeight;
        int scaledW = scaled == null ? displayW : scaled.getScaledWidth();
        int scaledH = scaled == null ? displayH : scaled.getScaledHeight();
        double guiScale = scaled == null ? 1.0 : (double) displayW / Math.max(1, scaledW);
        return new Window(0L, displayW, displayH, scaledW, scaledH, guiScale);
    }

    /**
     * Supplies the live logical viewport used by the common browser mode.
     * Cleanroom's B3D compatibility Window has no GLFW monitor handle, so the
     * adapter is the authoritative source for resize-aware dimensions.
     */
    @Override
    public Size getBrowserViewportSize() {
        ScaledResolution scaled = resolution(minecraft());
        return scaled == null
                ? null
                : new Size(scaled.getScaledWidth(), scaled.getScaledHeight());
    }

    @Override
    public String getClipboardText() {
        Minecraft mc = minecraft();
        return mc == null ? "" : GuiScreen.getClipboardString();
    }

    @Override
    public void setClipboardText(String text) {
        Minecraft mc = minecraft();
        if (mc != null) GuiScreen.setClipboardString(text == null ? "" : text);
    }

    @Override
    public Document getCurrentScreenDocument() {
        Minecraft mc = minecraft();
        return mc != null && mc.currentScreen instanceof AuiLinkedScreen screen
                ? screen.getLinkedDocument() : null;
    }

    @Override
    public boolean isWorldOccluded(net.minecraft.world.phys.Vec3 origin,
                                   net.minecraft.world.phys.Vec3 target,
                                   double targetDistance) {
        Minecraft mc = minecraft();
        if (mc == null || mc.world == null || mc.player == null) return true;
        Vec3d from = new Vec3d(origin.x, origin.y, origin.z);
        Vec3d to = new Vec3d(target.x, target.y, target.z);
        RayTraceResult result = mc.world.rayTraceBlocks(from, to, false, true, false);
        if (result == null || result.typeOfHit == RayTraceResult.Type.MISS) return false;
        return from.distanceTo(result.hitVec) + 1.0e-4d < targetDistance;
    }

    @Override
    public void execute(Runnable runnable) {
        Minecraft mc = minecraft();
        if (mc == null || runnable == null) return;
        mc.addScheduledTask(runnable);
    }
}
