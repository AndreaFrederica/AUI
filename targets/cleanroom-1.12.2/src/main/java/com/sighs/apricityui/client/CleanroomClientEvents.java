package com.sighs.apricityui.client;

import cc.sirrus.apricityui.compat.cleanroom.CleanroomClientService;
import cc.sirrus.apricityui.compat.cleanroom.CleanroomInput;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.resource.ResourcePreviewDialog;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.screen.AuiLinkedScreen;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.DocumentLayerOrder;
import com.sighs.apricityui.render.Operation;
import com.sighs.apricityui.style.Cursor;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.task.FrameScheduler;
import com.sighs.apricityui.task.MouseMoveEngine;
import com.sighs.apricityui.world.WorldWindow;
import net.minecraft.client.Minecraft;
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

    private CleanroomClientEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        FrameScheduler.tick();
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
        }
    }

    @SubscribeEvent
    public void renderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings.hideGUI) return;

        PoseStack pose = new PoseStack();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            Document screenDocument = AuiServices.client().getCurrentScreenDocument();
            for (Document document : DocumentLayerOrder.backToFront(Document.getAll())) {
                if (document == null || document == screenDocument || document.inWorld || document.isManuallyRendered()) continue;
                // A vanilla screen gets the same persistent overlay pass as the
                // modern ScreenEvent bridge. AUI-owned screens draw their linked
                // document themselves and are excluded above.
                if (mc.currentScreen != null && !document.isReloadPersistent()) continue;
                Base.drawOverlayDocument(pose, document);
                ResourcePreviewDialog.draw(pose, document);
            }
            Cursor.drawPseudoCursor(pose);
        } catch (Throwable error) {
            ApricityUI.LOGGER.warn("[AUI Cleanroom] overlay render failed", error);
        } finally {
            GL11.glPopAttrib();
        }
    }

    @SubscribeEvent
    public void mouseInput(InputEvent.MouseInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen instanceof AuiLinkedScreen) return;
        int button = Mouse.getEventButton();
        if (button < 0) return;
        boolean consumed = Mouse.getEventButtonState()
                ? Operation.onMouseDown(button)
                : Operation.onMouseUp(button);
        if (consumed) {
            // InputEvent.MouseInputEvent is not cancellable in Forge 1.12. The
            // AUI event is still dispatched before vanilla handles the same
            // physical input, matching the loader's normal event ordering.
            event.setResult(net.minecraftforge.fml.common.eventhandler.Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void mouseWheel(InputEvent.MouseInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen instanceof AuiLinkedScreen) return;
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && Operation.scroll(wheel)) {
            event.setResult(net.minecraftforge.fml.common.eventhandler.Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void keyInput(InputEvent.KeyInputEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.currentScreen instanceof AuiLinkedScreen) return;
        int key = CleanroomInput.toGlfwKey(Keyboard.getEventKey());
        if (key >= 0) {
            int action = Keyboard.getEventKeyState()
                    ? org.lwjgl.glfw.GLFW.GLFW_PRESS : org.lwjgl.glfw.GLFW.GLFW_RELEASE;
            Operation.handleKeyInput(key, 0, action, 0, false,
                    com.sighs.apricityui.event.KeyEvent.Source.INPUT_EVENT);
        }
        if (Keyboard.getEventKeyState()) {
            char character = Keyboard.getEventCharacter();
            if (character != 0) Operation.onCharTyped(character);
        }
    }

    private static Matrix4f readCurrentMatrix(int matrixName) {
        MATRIX.clear();
        GL11.glGetFloat(matrixName, MATRIX);
        MATRIX.flip();
        return new Matrix4f().set(MATRIX);
    }
}
