package com.sighs.apricityui.client;

import cc.sirrus.apricityui.compat.cleanroom.CleanroomClientService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomInput;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomRenderService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomScriptService;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.client.CursorReleaseController;
import com.sighs.apricityui.dev.DevTools;
import com.sighs.apricityui.dev.debug.ExternalDebugServer;
import com.sighs.apricityui.dev.resource.ResourcePreviewDialog;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.screen.AuiLinkedScreen;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.DocumentLayerOrder;
import com.sighs.apricityui.render.FrameTimingHud;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.task.FrameScheduler;
import com.sighs.apricityui.task.MouseMoveEngine;
import com.sighs.apricityui.ui.Tooltip;
import com.sighs.apricityui.world.WorldWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.util.ArrayList;

/**
 * Forge 1.12 lifecycle bridge for the platform-neutral AUI runtime.
 *
 * <p>1.12 has no modern screen/render callbacks, so this class is the one
 * loader-specific place where AUI enters the client tick, GUI overlay and
 * world render pipelines. Vanilla containers remain responsible for slot
 * synchronization; these callbacks only drive AUI documents and input.</p>
 */
public final class CleanroomClientEvents {
    private static final CleanroomClientEvents INSTANCE = new CleanroomClientEvents();
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static int lastDisplayWidth = -1;
    private static int lastDisplayHeight = -1;
    private static Runnable deferredBootstrap;
    private static int deferredBootstrapDelay;
    private static int deferredBootstrapTicks;
    /** The first few bootstrap ticks wait for NekoJS before reporting why they are stalled. */
    private static final java.util.Set<Integer> BOOTSTRAP_PROGRESS_TICKS =
            java.util.Set.of(1, 5, 10, 20, 30, 40);
    /**
     * NekoJS builds its CLIENT Graal context from its own END-phase client tick,
     * which always runs after this START-phase handler. Waiting for it keeps the
     * first page's script globals intact, but the wait is bounded so a stalled or
     * incompatible runtime can never keep resources and smoke pages from loading.
     */
    private static final int BOOTSTRAP_RUNTIME_DEADLINE_TICKS = 40;

    private CleanroomClientEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    /** Queue resource/script bootstrap until optional script loaders finish pre-init. */
    public static void deferBootstrap(Runnable task) {
        deferredBootstrap = task;
        deferredBootstrapDelay = 2;
        deferredBootstrapTicks = 0;
        ApricityUI.LOGGER.info("[AUI Cleanroom] deferred client bootstrap queued");
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        // Run the deferred bootstrap before anything else on this handler: a
        // failure in the frame scheduler or in the viewport sync below must not
        // be able to starve the first resource load / smoke page forever.
        runDeferredBootstrap();
        CursorReleaseController.tick();
        if (AuiServices.config().consumeClientReloadPending()) {
            ExternalDebugServer.reconcileConfiguration();
        }
        ExternalDebugServer.tick();
        FrameScheduler.tick();
        DevTools.drainLogs();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        int width = mc.displayWidth;
        int height = mc.displayHeight;
        int scaledWidth = CleanroomClientService.INSTANCE.getScaledWidth();
        int scaledHeight = CleanroomClientService.INSTANCE.getScaledHeight();
        if (width != lastDisplayWidth || height != lastDisplayHeight
                || scaledWidth != lastWidth || scaledHeight != lastHeight) {
            lastDisplayWidth = width;
            lastDisplayHeight = height;
            lastWidth = scaledWidth;
            lastHeight = scaledHeight;
            for (Document document : Document.getAll()) {
                if (document != null && !document.isDisposed()) document.applyViewport(true);
            }
            com.sighs.apricityui.init.Window.window.fireResizeEvent();
        }
    }

    /**
     * Runs the queued resource/script bootstrap as soon as the script runtime is
     * usable, without ever depending on the rest of the tick handler.
     */
    private static void runDeferredBootstrap() {
        if (deferredBootstrap == null) return;
        if (--deferredBootstrapDelay > 0) return;
        deferredBootstrapTicks++;
        boolean runtimeReady = clientRuntimeReady();
        if (runtimeReady) {
            Runnable task = deferredBootstrap;
            deferredBootstrap = null;
            ApricityUI.LOGGER.info(
                    "[AUI Cleanroom] client bootstrap starting after {} tick(s)", deferredBootstrapTicks);
            try {
                task.run();
            } catch (Throwable error) {
                ApricityUI.LOGGER.warn("[AUI Cleanroom] deferred client bootstrap failed", error);
            }
            return;
        }
        if (deferredBootstrapTicks >= BOOTSTRAP_RUNTIME_DEADLINE_TICKS) {
            // The runtime never showed up: bootstrap anyway so the first resource
            // load and the dev smoke page are never withheld by the script loader.
            Runnable task = deferredBootstrap;
            deferredBootstrap = null;
            ApricityUI.LOGGER.warn(
                    "[AUI Cleanroom] NekoJS CLIENT runtime still unavailable after {} ticks; bootstrapping anyway",
                    deferredBootstrapTicks);
            try {
                task.run();
            } catch (Throwable error) {
                ApricityUI.LOGGER.warn("[AUI Cleanroom] deferred client bootstrap failed", error);
            }
            return;
        }
        // NekoJS creates the formal CLIENT Graal context from its own client
        // tick. Poll at a low rate instead of creating a temporary context and
        // losing the first page's globals.
        if (BOOTSTRAP_PROGRESS_TICKS.contains(deferredBootstrapTicks)) {
            ApricityUI.LOGGER.info(
                    "[AUI Cleanroom] waiting for the NekoJS CLIENT runtime before the first bootstrap (tick {})",
                    deferredBootstrapTicks);
        }
        deferredBootstrapDelay = 5;
    }

    /**
     * Probes the script backend. A throwing probe is treated as "not ready" so
     * the bounded deadline below decides, instead of the exception deciding.
     */
    private static boolean clientRuntimeReady() {
        try {
            return CleanroomScriptService.INSTANCE.isClientRuntimeReady();
        } catch (Throwable error) {
            ApricityUI.LOGGER.warn("[AUI Cleanroom] client script runtime probe failed", error);
            return false;
        }
    }

    @SubscribeEvent
    public void renderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            MouseMoveEngine.poll(CleanroomClientService.INSTANCE::getMousePosition);
        }
    }

    @SubscribeEvent
    public void renderWorld(RenderWorldLastEvent event) {
        if (WorldWindow.windows.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) return;

        Matrix4f projection = readCurrentMatrix(GL11.GL_PROJECTION_MATRIX);
        Matrix4f previousProjection = AuiServices.render().getProjectionMatrix();
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            // WorldWindow emits camera-relative vertices. The legacy event has
            // already installed Minecraft's model-view camera transform, so the
            // AUI pose stack must start at identity and only carry window model
            // transforms. The captured projection is used for culling and shader
            // uniforms while fixed-pipeline geometry follows the current GL state.
            AuiServices.render().setProjectionMatrix(projection);
            for (WorldWindow window : new ArrayList<>(WorldWindow.windows)) {
                if (window == null || window.document == null) continue;
                window.render(new PoseStack(), projection, event.getPartialTicks());
            }
        } catch (Throwable error) {
            ApricityUI.LOGGER.warn("[AUI Cleanroom] world-window render failed", error);
        } finally {
            AuiServices.render().setProjectionMatrix(previousProjection);
            GL20.glUseProgram(previousProgram);
            GL11.glPopAttrib();
            CleanroomRenderService.INSTANCE.syncCachedStateFromGl();
        }
    }

    @SubscribeEvent
    public void renderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings.hideGUI) return;
        if (mc.currentScreen != null) return;

        DevTools.handleInspectMouseMove(CleanroomClientService.INSTANCE.getMousePositionDirectly());
        FrameTimingHud.beginFrame();
        PoseStack pose = new PoseStack();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            Document screenDocument = AuiServices.client().getCurrentScreenDocument();
            for (Document document : DocumentLayerOrder.backToFront(Document.getAll())) {
                if (document == null || document == screenDocument || document.inWorld || document.isManuallyRendered()) continue;
                Base.drawOverlayDocument(pose, document);
                ResourcePreviewDialog.draw(pose, document);
            }
            Cursor.drawPseudoCursor(pose);
        } catch (Throwable error) {
            ApricityUI.LOGGER.warn("[AUI Cleanroom] overlay render failed", error);
        } finally {
            GL11.glPopAttrib();
            CleanroomRenderService.INSTANCE.syncCachedStateFromGl();
            FrameTimingHud.endFrame();
            drawFrameTimingHud();
        }
    }

    @SubscribeEvent
    public void drawGuiScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (event.getGui() instanceof AuiLinkedScreen) return;
        Position mousePos = new Position(event.getMouseX(), event.getMouseY());
        Tooltip.moveActiveFromScreen(mousePos);
        DevTools.handleInspectMouseMove(mousePos);

        boolean hasPersistent = false;
        for (Document document : Document.getAll()) {
            if (document != null && !document.inWorld && !document.isManuallyRendered() && document.isReloadPersistent()) {
                hasPersistent = true;
                break;
            }
        }
        if (!hasPersistent && !FrameTimingHud.isEnabled()) return;

        FrameTimingHud.beginFrame();
        PoseStack pose = new PoseStack();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            drawPersistentScreenDocuments(pose, null);
            Cursor.drawPseudoCursor(pose);
        } catch (Throwable error) {
            ApricityUI.LOGGER.warn("[AUI Cleanroom] screen overlay render failed", error);
        } finally {
            GL11.glPopAttrib();
            CleanroomRenderService.INSTANCE.syncCachedStateFromGl();
            FrameTimingHud.endFrame();
            drawFrameTimingHud();
        }
    }

    public static void drawPersistentScreenDocuments(PoseStack pose, Document excludedDocument) {
        for (Document document : DocumentLayerOrder.backToFront(Document.getAll())) {
            if (document == null || document == excludedDocument || document.inWorld
                    || document.isManuallyRendered() || !document.isReloadPersistent()) {
                continue;
            }
            Base.drawOverlayDocument(pose, document);
            ResourcePreviewDialog.draw(pose, document);
        }
    }

    public static void drawFrameTimingHud() {
        if (!FrameTimingHud.isEnabled()) return;
        String text = FrameTimingHud.frameStatsText();
        if (text == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.fontRenderer == null) return;
        int width = mc.fontRenderer.getStringWidth(text) + 8;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(0.0f, 0.0f, 900.0f);
            Gui.drawRect(2, 2, 2 + width, 16, 0xCC000000);
            mc.fontRenderer.drawString(text, 6, 5, 0xFF00FF66, false);
        } finally {
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.popMatrix();
        }
    }

    @SubscribeEvent
    public void guiMouseInputPre(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (event.getGui() instanceof AuiLinkedScreen) return;
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && Operation.scroll(wheel / 120.0d)) {
            event.setCanceled(true);
            return;
        }
        int button = Mouse.getEventButton();
        if (button >= 0) {
            boolean consumed = Mouse.getEventButtonState()
                    ? Operation.onMouseDown(button)
                    : Operation.onMouseUp(button);
            if (consumed) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void guiKeyboardInputPre(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (event.getGui() instanceof AuiLinkedScreen) return;
        int key = CleanroomInput.toGlfwKey(Keyboard.getEventKey());
        int modifiers = CleanroomInput.currentGlfwModifiers();
        boolean pressed = Keyboard.getEventKeyState();
        boolean consumed = false;
        if (key >= 0) {
            int action = pressed ? org.lwjgl.glfw.GLFW.GLFW_PRESS : org.lwjgl.glfw.GLFW.GLFW_RELEASE;
            consumed = Operation.handleKeyInput(key, 0, action, modifiers, Keyboard.isRepeatEvent(),
                    com.sighs.apricityui.event.KeyEvent.Source.SCREEN_EVENT);
        }
        if (pressed) {
            char character = Keyboard.getEventCharacter();
            if (CleanroomInput.isPrintableChar(character, modifiers)) {
                consumed |= Operation.onCharTyped(character);
            }
        }
        if (consumed) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void mouseInput(InputEvent.MouseInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen != null) return;
        int button = Mouse.getEventButton();
        if (button < 0) return;
        boolean pressed = Mouse.getEventButtonState();
        boolean consumed = pressed ? Operation.onMouseDown(button) : Operation.onMouseUp(button);
        if (Operation.wasDevToolsInspectConsumed()) {
            event.setResult(net.minecraftforge.fml.common.eventhandler.Event.Result.DENY);
            return;
        }
        for (WorldWindow window : new ArrayList<>(WorldWindow.windows)) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                MouseEvent mouseEvent = new MouseEvent(pressed ? "mousedown" : "mouseup", realPos, button);
                MouseEvent.tiggerEvent(mouseEvent, window.document);
                consumed |= mouseEvent.isNativeConsumed();
            }
        }
        if (consumed || CursorReleaseController.isActive()) {
            // InputEvent.MouseInputEvent is not cancellable in Forge 1.12. The
            // AUI event is still dispatched before vanilla handles the same
            // physical input, matching the loader's normal event ordering.
            event.setResult(net.minecraftforge.fml.common.eventhandler.Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void mouseWheel(InputEvent.MouseInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen != null) return;
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;
        double delta = wheel / 120.0d;
        boolean consumed = Operation.scroll(delta);
        for (WorldWindow window : new ArrayList<>(WorldWindow.windows)) {
            Position realPos = window.getRealPos();
            if (realPos != null) {
                MouseEvent mouseEvent = new MouseEvent("wheel", realPos);
                mouseEvent.deltaY = -delta * 50;
                mouseEvent.scrollDelta = mouseEvent.deltaY;
                mouseEvent.cancelable = true;
                MouseEvent.tiggerEvent(mouseEvent, window.document);
                consumed |= mouseEvent.isNativeConsumed();
            }
        }
        if (consumed || CursorReleaseController.isActive()) {
            event.setResult(net.minecraftforge.fml.common.eventhandler.Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void keyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen != null) return;
        int key = CleanroomInput.toGlfwKey(Keyboard.getEventKey());
        int modifiers = CleanroomInput.currentGlfwModifiers();
        if (key >= 0) {
            int action = Keyboard.getEventKeyState()
                    ? org.lwjgl.glfw.GLFW.GLFW_PRESS : org.lwjgl.glfw.GLFW.GLFW_RELEASE;
            Operation.handleKeyInput(key, 0, action, modifiers, Keyboard.isRepeatEvent(),
                    com.sighs.apricityui.event.KeyEvent.Source.INPUT_EVENT);
        }
        if (Keyboard.getEventKeyState()) {
            char character = Keyboard.getEventCharacter();
            if (CleanroomInput.isPrintableChar(character, modifiers)) {
                Operation.onCharTyped(character);
            }
        }
    }

    private static Matrix4f readCurrentMatrix(int matrixName) {
        MATRIX.clear();
        GL11.glGetFloat(matrixName, MATRIX);
        // LWJGL fills the buffer without advancing its position.
        return new Matrix4f().set(MATRIX);
    }
}
