package cc.sirrus.apricityui.compat.cleanroom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.spi.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL20;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.BufferUtils;

/**
 * Minimal fixed-function 1.12 renderer bridge. It deliberately uses the old
 * Tessellator/GlStateManager path; no 1.20 RenderSystem types cross this class.
 * Filter shaders and FBOs are installed incrementally, while basic rectangles,
 * clipping and depth state already work on Cleanroom.
 */
public final class CleanroomRenderService implements AuiRenderService {
    public static final CleanroomRenderService INSTANCE = new CleanroomRenderService();
    private Matrix4f projection = new Matrix4f();
    private boolean depth = true, depthMask = true, cull;
    private volatile ShaderHandle filterShader;
    private volatile ShaderHandle blurShader;
    private volatile ShaderHandle blendShader;
    private volatile ShaderHandle maskShader;
    private volatile ShaderHandle maskShaderLuminance;
    private volatile ShaderHandle maskIntersectShader;
    private volatile ShaderHandle maskSubtractShader;
    private volatile ShaderHandle maskExcludeShader;
    private volatile ShaderHandle positionColorShader;
    private boolean positionColorShaderAttempted;
    private boolean positionColorDiagnosticLogged;
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private CleanroomRenderService() { }

    private static final class Mesh {
        final BufferBuilder buffer;
        Mesh(BufferBuilder buffer) { this.buffer = buffer; }
    }

    @Override public void setProjectionMatrix(Matrix4f matrix) {
        projection = matrix == null ? new Matrix4f() : new Matrix4f(matrix);
        uploadProjection();
    }
    @Override public Matrix4f getProjectionMatrix() { return new Matrix4f(projection); }
    @Override public void enableDepthTest() { depth = true; GL11.glEnable(GL11.GL_DEPTH_TEST); }
    @Override public void disableDepthTest() { depth = false; GL11.glDisable(GL11.GL_DEPTH_TEST); }
    @Override public void enableBlend() { GL11.glEnable(GL11.GL_BLEND); }
    @Override public void setBlendFunc(int srcFactor, int dstFactor) { GL11.glBlendFunc(srcFactor, dstFactor); }
    @Override public MeshBuilder beginMesh(MeshMode mode, MeshFormat format) {
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        int glMode = mode == MeshMode.QUADS ? GL11.GL_QUADS : GL11.GL_TRIANGLES;
        VertexFormat vertexFormat = format == MeshFormat.POSITION_TEX ? DefaultVertexFormats.POSITION_TEX
                : format == MeshFormat.POSITION ? DefaultVertexFormats.POSITION : DefaultVertexFormats.POSITION_COLOR;
        buffer.begin(glMode, vertexFormat);
        return MeshBuilder.of(new Mesh(buffer));
    }
    private static float[] transform(Matrix4f matrix, float x, float y, float z) {
        if (matrix == null) return new float[]{x, y, z};
        org.joml.Vector4f v = matrix.transform(new org.joml.Vector4f(x, y, z, 1));
        return new float[]{v.x, v.y, v.z};
    }
    @Override public void emitVertex(Object mesh, Matrix4f mat, float x, float y, float z, int r, int g, int b, int a) {
        Mesh m = (Mesh) mesh; float[] p = transform(mat, x, y, z);
        m.buffer.pos(p[0], p[1], p[2]).color(r, g, b, a).endVertex();
    }
    @Override public void submitMesh(Object mesh) {
        Tessellator.getInstance().draw();
        // BufferBuilder's POSITION_COLOR pass is owned by AUI only.  Restore
        // Minecraft's fixed/texture pipeline before font and texture batches;
        // leaving this program bound makes every subsequent textured draw use
        // the color-only fragment shader and appear black.
        GL20.glUseProgram(0);
        activeShader = null;
        if (!positionColorDiagnosticLogged) {
            positionColorDiagnosticLogged = true;
            try {
                java.nio.IntBuffer viewport = BufferUtils.createIntBuffer(4);
                GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
                java.nio.IntBuffer scissor = BufferUtils.createIntBuffer(4);
                GL11.glGetInteger(GL11.GL_SCISSOR_BOX, scissor);
                com.sighs.apricityui.ApricityUI.LOGGER.info(
                        "[AUI Cleanroom] first mesh submitted program={} viewport={}x{}+{},{} scissor={} box={}x{}+{},{} glError={}",
                        GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), viewport.get(2), viewport.get(3), viewport.get(0), viewport.get(1),
                        GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), scissor.get(2), scissor.get(3), scissor.get(0), scissor.get(1),
                        GL11.glGetError());
            } catch (Throwable error) {
                com.sighs.apricityui.ApricityUI.LOGGER.debug("[AUI Cleanroom] mesh diagnostic failed", error);
            }
        }
    }
    @Override public Object beginTextureBatch(RenderHandle render) {
        ResourceLocation location = render == null ? null : render.as();
        if (location == null) return null;
        try {
            Minecraft.getMinecraft().getTextureManager().bindTexture(location);
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            return new TextureBatch(buffer);
        } catch (Throwable ignored) {
            return null;
        }
    }
    @Override public void emitTextureQuad(Object batch, Matrix4f mat, float x, float y, float width, float height,
                                          float u0, float v0, float u1, float v1) {
        emitTextureQuad(batch, mat, x, y, width, height, u0, v0, u1, v1, 0xFFFFFFFF);
    }
    @Override public void emitTextureQuad(Object batch, Matrix4f mat, float x, float y, float width, float height,
                                          float u0, float v0, float u1, float v1, int colorArgb) {
        if (!(batch instanceof TextureBatch textureBatch)) return;
        int a = colorArgb >>> 24, r = colorArgb >>> 16 & 255, g = colorArgb >>> 8 & 255, b = colorArgb & 255;
        vertex(textureBatch.buffer, mat, x, y, u0, v0, r, g, b, a);
        vertex(textureBatch.buffer, mat, x, y + height, u0, v1, r, g, b, a);
        vertex(textureBatch.buffer, mat, x + width, y + height, u1, v1, r, g, b, a);
        vertex(textureBatch.buffer, mat, x + width, y, u1, v0, r, g, b, a);
    }
    private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float u, float v,
                               int r, int g, int b, int a) {
        float[] p = transform(matrix, x, y, 0);
        buffer.pos(p[0], p[1], p[2]).tex(u, v).color(r, g, b, a).endVertex();
    }
    @Override public void flushTextureBatch(Object batch, RenderHandle render) {
        if (batch instanceof TextureBatch) Tessellator.getInstance().draw();
    }
    private static final class TextureBatch { final BufferBuilder buffer; TextureBatch(BufferBuilder buffer) { this.buffer = buffer; } }
    @Override public void emitVertexUV(Object mesh, Matrix4f mat, float x, float y, float z, float u, float v) {
        Mesh m = (Mesh) mesh; float[] p = transform(mat, x, y, z); m.buffer.pos(p[0], p[1], p[2]).tex(u, v).endVertex();
    }
    @Override public FboHandle createOffscreenTarget(int width, int height, boolean useDepth) {
        if (width <= 0 || height <= 0) return null;
        try {
            Framebuffer framebuffer = new Framebuffer(width, height, useDepth);
            framebuffer.setFramebufferFilter(GL11.GL_LINEAR);
            return FboHandle.of(framebuffer, width, height);
        } catch (Throwable ignored) {
            return null;
        }
    }
    @Override public FboHandle getMainRenderTarget() {
        try {
            Framebuffer framebuffer = Minecraft.getMinecraft().getFramebuffer();
            return framebuffer == null ? null : FboHandle.of(framebuffer,
                    framebuffer.framebufferWidth, framebuffer.framebufferHeight);
        } catch (Throwable ignored) {
            return null;
        }
    }
    @Override public void enableStencil(FboHandle target) {
        Framebuffer framebuffer = framebuffer(target);
        if (framebuffer != null) framebuffer.enableStencil();
    }
    @Override public void destroyBuffers(FboHandle target) {
        Framebuffer framebuffer = framebuffer(target);
        if (framebuffer != null && framebuffer != Minecraft.getMinecraft().getFramebuffer()) framebuffer.deleteFramebuffer();
    }
    @Override public void clear(FboHandle target, float r, float g, float b, float a) {
        Framebuffer framebuffer = framebuffer(target);
        if (framebuffer != null) framebuffer.setFramebufferColor(r, g, b, a);
        GL11.glClearColor(r, g, b, a);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
    }
    @Override public void bindWrite(FboHandle target, boolean setViewport) {
        Framebuffer framebuffer = framebuffer(target);
        if (framebuffer != null) framebuffer.bindFramebuffer(setViewport);
    }
    @Override public void bindColorTexture(FboHandle target, int unit) {
        Framebuffer framebuffer = framebuffer(target);
        if (framebuffer == null) return;
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + Math.max(0, unit));
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, framebuffer.framebufferTexture);
    }
    @Override public void blitFramebuffer(FboHandle source, FboHandle target, int srcX0, int srcY0, int srcX1, int srcY1) {
        Framebuffer from = framebuffer(source), to = framebuffer(target);
        if (from == null || to == null) return;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.framebufferObject);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.framebufferObject);
        GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, srcX0, srcY0, srcX1, srcY1,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, to.framebufferObject);
    }
    private static Framebuffer framebuffer(FboHandle handle) {
        return handle == null ? null : handle.as();
    }
    @Override public Object createDynamicTexture(String name, Object nativeImage, boolean linear) {
        if (!(nativeImage instanceof com.mojang.blaze3d.platform.NativeImage image)) return null;
        try {
            DynamicTexture texture = new DynamicTexture(image.bufferedImage());
            texture.updateDynamicTexture();
            return texture;
        } catch (Throwable ignored) {
            return null;
        }
    }
    @Override public void uploadTextureRegion(Object texture, Object nativeImage, int x, int y, int width, int height, boolean linear) {
        if (!(texture instanceof DynamicTexture dynamic) || !(nativeImage instanceof com.mojang.blaze3d.platform.NativeImage image)) return;
        try {
            int[] pixels = dynamic.getTextureData();
            int textureWidth = image.getWidth();
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int destination = (y + row) * textureWidth + x + col;
                    if (destination >= 0 && destination < pixels.length) {
                        pixels[destination] = image.getPixelRGBA(x + col, y + row);
                    }
                }
            }
            dynamic.updateDynamicTexture();
        } catch (Throwable ignored) { }
    }
    @Override public void setImagePixel(Object nativeImage, int x, int y, int pixel) { if (nativeImage instanceof com.mojang.blaze3d.platform.NativeImage image) image.setPixelRGBA(x, y, pixel); }
    @Override public void closeTexture(Object texture) { if (texture instanceof DynamicTexture dynamic) dynamic.deleteGlTexture(); }
    @Override public void registerTexture(Object texture, Object location) {
        if (!(texture instanceof DynamicTexture dynamic) || !(location instanceof ResourceLocation id)) return;
        try { Minecraft.getMinecraft().getTextureManager().loadTexture(id, dynamic); }
        catch (Throwable ignored) { }
    }
    @Override public void releaseTexture(Object location) {
        if (!(location instanceof ResourceLocation id)) return;
        try { Minecraft.getMinecraft().getTextureManager().deleteTexture(id); }
        catch (Throwable ignored) { }
    }
    @Override public void setShader(Object shader) {
        activeShader = shader instanceof ShaderHandle handle ? handle : null;
        GL20.glUseProgram(activeShader == null ? 0 : activeShader.program);
        if (activeShader != null) {
            uploadProjection();
            applyShaderBlend(activeShader);
        }
    }
    @Override public void setPositionColorShader() {
        ShaderHandle shader = positionColorShader;
        if (shader == null && !positionColorShaderAttempted) {
            synchronized (this) {
                shader = positionColorShader;
                if (shader == null && !positionColorShaderAttempted) {
                    positionColorShaderAttempted = true;
                    shader = compilePositionColorShader();
                    positionColorShader = shader;
                }
            }
        }
        activeShader = shader;
        GL20.glUseProgram(shader == null ? 0 : shader.program);
        if (shader != null) uploadProjection();
    }
    @Override public void setShaderColor(float a, float r, float g, float b) { GL11.glColor4f(r, g, b, a); }
    @Override public Object getFilterShader() {
        ShaderHandle current = filterShader;
        if (current != null) return current;
        synchronized (this) {
            if (filterShader == null) filterShader = compileShader("filter", "filter");
            return filterShader;
        }
    }
    @Override public Object getFilterBlurShader() {
        ShaderHandle current = blurShader;
        if (current != null) return current;
        synchronized (this) {
            if (blurShader == null) blurShader = compileShader("filter_blur", "filter_blur");
            return blurShader;
        }
    }
    @Override public Object getFilterBlendShader() {
        ShaderHandle current = blendShader;
        if (current != null) return current;
        synchronized (this) {
            if (blendShader == null) blendShader = compileShader("filter", "filter_blend", ShaderKind.NONE);
            return blendShader;
        }
    }
    @Override public Object getFilterMaskShader(boolean luminance) {
        ShaderHandle current = luminance ? maskShaderLuminance : maskShader;
        if (current != null) return current;
        synchronized (this) {
            if (luminance) {
                if (maskShaderLuminance == null) maskShaderLuminance = compileShader("filter", "filter_mask", ShaderKind.MASK);
                return maskShaderLuminance;
            }
            if (maskShader == null) maskShader = compileShader("filter", "filter_mask", ShaderKind.MASK);
            return maskShader;
        }
    }
    @Override public Object getFilterMaskMergeShader(AuiRenderService.MaskCompositeOp op) {
        if (op == null) return null;
        synchronized (this) {
            return switch (op) {
                case INTERSECT -> maskIntersectShader == null
                        ? (maskIntersectShader = compileShader("filter", "filter_mask_merge", ShaderKind.MASK_INTERSECT))
                        : maskIntersectShader;
                case SUBTRACT -> maskSubtractShader == null
                        ? (maskSubtractShader = compileShader("filter", "filter_mask_merge", ShaderKind.MASK_SUBTRACT))
                        : maskSubtractShader;
                case EXCLUDE -> maskExcludeShader == null
                        ? (maskExcludeShader = compileShader("filter", "filter_mask_merge", ShaderKind.MASK_EXCLUDE))
                        : maskExcludeShader;
            };
        }
    }
    @Override public void setDepthFunc(int func) { GL11.glDepthFunc(func); }
    @Override public void setDepthMask(boolean write) { depthMask = write; GL11.glDepthMask(write); }
    @Override public boolean isDepthTestEnabled() { return depth; }
    @Override public boolean isDepthMaskEnabled() { return depthMask; }
    @Override public void setBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) { GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha); }
    @Override public AuiRenderService.RenderStateScope pushFilterRenderState() {
        try {
            final RenderStateSnapshot snapshot = RenderStateSnapshot.capture(this);
            return () -> snapshot.restore(this);
        } catch (Throwable ignored) {
            return AuiRenderService.RenderStateScope.NOOP;
        }
    }
    @Override public void disableBlend() { GL11.glDisable(GL11.GL_BLEND); }
    @Override public void enableCull() { cull = true; GL11.glEnable(GL11.GL_CULL_FACE); }
    @Override public void disableCull() { cull = false; GL11.glDisable(GL11.GL_CULL_FACE); }
    @Override public boolean isCullEnabled() { return cull; }

    /**
     * Reconciles the small state cache after a loader event restores raw OpenGL
     * attributes around an AUI pass. Legacy Forge hooks may use glPushAttrib/
     * glPopAttrib, which do not update Java-side booleans or the active shader.
     */
    public void syncCachedStateFromGl() {
        depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        activeShader = null;
    }
    @Override public void enablePolygonOffset() { GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL); }
    @Override public void disablePolygonOffset() { GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL); }
    @Override public void polygonOffset(float factor, float units) { GL11.glPolygonOffset(factor, units); }
    @Override public void enableScissorTest() { GL11.glEnable(GL11.GL_SCISSOR_TEST); }
    @Override public void scissorBox(int x, int y, int width, int height) { GL11.glScissor(x, y, width, height); }
    @Override public void disableScissorTest() { GL11.glDisable(GL11.GL_SCISSOR_TEST); }
    @Override public void enableStencilTest() { GL11.glEnable(GL11.GL_STENCIL_TEST); }
    @Override public void disableStencilTest() { GL11.glDisable(GL11.GL_STENCIL_TEST); }
    @Override public void setStencilMask(int mask) { GL11.glStencilMask(mask); }
    @Override public void setStencilFunc(int func, int ref, int mask) { GL11.glStencilFunc(func, ref, mask); }
    @Override public void setStencilOp(int sfail, int dpfail, int dppass) { GL11.glStencilOp(sfail, dpfail, dppass); }
    @Override public void clearStencilBuffer() { GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT); }
    @Override public void setColorMask(boolean red, boolean green, boolean blue, boolean alpha) { GL11.glColorMask(red, green, blue, alpha); }
    @Override public boolean isOnRenderThread() { return true; }
    @Override public void recordRenderCall(Runnable task) { if (task != null) task.run(); }
    @Override public String getGLVersionString() { return GL11.glGetString(GL11.GL_VERSION); }
    @Override public void flushSharedBuffers() { }
    @Override public void setShaderUniformFloat(String name, float value) { uniform(name, location -> GL20.glUniform1f(location, value)); }
    @Override public void setShaderUniform2f(String name, float a, float b) { uniform(name, location -> GL20.glUniform2f(location, a, b)); }
    @Override public void setShaderUniform3f(String name, float a, float b, float c) { uniform(name, location -> GL20.glUniform3f(location, a, b, c)); }
    @Override public void setShaderUniform4f(String name, float a, float b, float c, float d) { uniform(name, location -> GL20.glUniform4f(location, a, b, c, d)); }
    @Override public void setShaderUniformI(String name, int value) { uniform(name, location -> GL20.glUniform1i(location, value)); }

    private void uniform(String name, java.util.function.IntConsumer writer) {
        if (name == null) return;
        ShaderHandle shader = activeShader;
        if (shader == null) return;
        int location = shader.uniforms.computeIfAbsent(name, key -> GL20.glGetUniformLocation(shader.program, key));
        if (location >= 0) writer.accept(location);
    }

    private void uploadProjection() {
        ShaderHandle shader = activeShader;
        if (shader == null) return;
        int location = shader.uniforms.computeIfAbsent("ProjMat",
                key -> GL20.glGetUniformLocation(shader.program, key));
        if (location < 0) return;
        matrixBuffer.clear();
        projection.get(matrixBuffer);
        matrixBuffer.flip();
        GL20.glUniformMatrix4(location, false, matrixBuffer);
    }

    private static void applyShaderBlend(ShaderHandle shader) {
        switch (shader.kind) {
            case MASK -> {
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(GL11.GL_ZERO, GL11.GL_SRC_ALPHA,
                        GL11.GL_ZERO, GL11.GL_SRC_ALPHA);
            }
            case MASK_INTERSECT -> {
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(GL11.GL_DST_ALPHA, GL11.GL_ZERO,
                        GL11.GL_DST_ALPHA, GL11.GL_ZERO);
            }
            case MASK_SUBTRACT -> {
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(GL11.GL_ONE_MINUS_DST_ALPHA, GL11.GL_ZERO,
                        GL11.GL_ONE_MINUS_DST_ALPHA, GL11.GL_ZERO);
            }
            case MASK_EXCLUDE -> {
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(GL11.GL_ONE_MINUS_DST_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                        GL11.GL_ONE_MINUS_DST_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
            default -> { }
        }
    }

    private ShaderHandle activeShader;

    private ShaderHandle compileShader(String vertexName, String fragmentName) {
        return compileShader(vertexName, fragmentName, ShaderKind.NONE);
    }

    private ShaderHandle compileShader(String vertexName, String fragmentName, ShaderKind kind) {
        try {
            String vertex = readShader(vertexName + ".vsh");
            String fragment = readShader(fragmentName + ".fsh");
            int vertexId = compile(GL20.GL_VERTEX_SHADER, vertex);
            int fragmentId = compile(GL20.GL_FRAGMENT_SHADER, fragment);
            int program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexId);
            GL20.glAttachShader(program, fragmentId);
            GL20.glBindAttribLocation(program, 0, "Position");
            GL20.glBindAttribLocation(program, 1, "UV0");
            GL20.glBindAttribLocation(program, 2, "Color");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetProgramInfoLog(program, 4096);
                GL20.glDeleteProgram(program);
                throw new IllegalStateException(log);
            }
            GL20.glDeleteShader(vertexId);
            GL20.glDeleteShader(fragmentId);
            return new ShaderHandle(program, kind);
        } catch (Throwable error) {
            com.sighs.apricityui.ApricityUI.LOGGER.warn("Cleanroom shader {} could not be compiled; using fixed pipeline", vertexName, error);
            return null;
        }
    }

    /** Simple vertex-color program used for AUI's POSITION_COLOR batches. */
    private ShaderHandle compilePositionColorShader() {
        try {
            int vertexId = compile(GL20.GL_VERTEX_SHADER,
                    "#version 120\nattribute vec3 Position;\nattribute vec4 Color;\nvarying vec4 vertexColor;\nvoid main(){ gl_Position = gl_ModelViewProjectionMatrix * vec4(Position, 1.0); vertexColor = Color; }\n");
            int fragmentId = compile(GL20.GL_FRAGMENT_SHADER,
                    "#version 120\nvarying vec4 vertexColor;\nvoid main(){ gl_FragColor = vertexColor; }\n");
            int program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexId);
            GL20.glAttachShader(program, fragmentId);
            GL20.glBindAttribLocation(program, 0, "Position");
            GL20.glBindAttribLocation(program, 1, "Color");
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetProgramInfoLog(program, 4096);
                GL20.glDeleteProgram(program);
                throw new IllegalStateException(log);
            }
            GL20.glDeleteShader(vertexId);
            GL20.glDeleteShader(fragmentId);
            com.sighs.apricityui.ApricityUI.LOGGER.info("[AUI Cleanroom] POSITION_COLOR shader enabled (program={})", program);
            return new ShaderHandle(program, ShaderKind.POSITION_COLOR);
        } catch (Throwable error) {
            com.sighs.apricityui.ApricityUI.LOGGER.warn("[AUI Cleanroom] POSITION_COLOR shader unavailable; using fixed pipeline", error);
            return null;
        }
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 4096);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException(log);
        }
        return shader;
    }

    private static String readShader(String file) throws Exception {
        ResourceLocation location = new ResourceLocation("apricityui", "shaders/core/" + file);
        try (InputStream input = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class ShaderHandle {
        final int program;
        final ShaderKind kind;
        final Map<String, Integer> uniforms = new ConcurrentHashMap<>();
        ShaderHandle(int program, ShaderKind kind) { this.program = program; this.kind = kind; }
    }

    private static final class RenderStateSnapshot {
        final boolean blend, depthTest, cull, scissor, stencil;
        final boolean depthMask;
        final int depthFunc;
        final int srcRgb, dstRgb, srcAlpha, dstAlpha;
        final int activeTexture;
        final int texture0, texture1;
        final int viewportX, viewportY, viewportWidth, viewportHeight;
        final int program;
        final Matrix4f projection;

        private RenderStateSnapshot(boolean blend, boolean depthTest, boolean cull, boolean scissor,
                                    boolean stencil, boolean depthMask, int depthFunc,
                                    int srcRgb, int dstRgb, int srcAlpha, int dstAlpha,
                                    int activeTexture, int texture0, int texture1,
                                    int viewportX, int viewportY, int viewportWidth, int viewportHeight,
                                    int program, Matrix4f projection) {
            this.blend = blend; this.depthTest = depthTest; this.cull = cull;
            this.scissor = scissor; this.stencil = stencil; this.depthMask = depthMask;
            this.depthFunc = depthFunc; this.srcRgb = srcRgb; this.dstRgb = dstRgb;
            this.srcAlpha = srcAlpha; this.dstAlpha = dstAlpha; this.activeTexture = activeTexture;
            this.texture0 = texture0; this.texture1 = texture1;
            this.viewportX = viewportX; this.viewportY = viewportY;
            this.viewportWidth = viewportWidth; this.viewportHeight = viewportHeight;
            this.program = program; this.projection = projection;
        }

        static RenderStateSnapshot capture(CleanroomRenderService service) {
            int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            int texture1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL13.glActiveTexture(active);
            java.nio.IntBuffer viewport = BufferUtils.createIntBuffer(4);
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            return new RenderStateSnapshot(
                    GL11.glIsEnabled(GL11.GL_BLEND), GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE), GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    GL11.glIsEnabled(GL11.GL_STENCIL_TEST), GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    active, texture0, texture1, viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3),
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), new Matrix4f(service.projection)
            );
        }

        void restore(CleanroomRenderService service) {
            setEnabled(GL11.GL_BLEND, blend);
            setEnabled(GL11.GL_DEPTH_TEST, depthTest);
            setEnabled(GL11.GL_CULL_FACE, cull);
            setEnabled(GL11.GL_SCISSOR_TEST, scissor);
            setEnabled(GL11.GL_STENCIL_TEST, stencil);
            GL11.glDepthMask(depthMask);
            GL11.glDepthFunc(depthFunc);
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture1);
            GL13.glActiveTexture(activeTexture);
            GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            service.projection = new Matrix4f(projection);
            GL20.glUseProgram(program);
            service.activeShader = null;
        }

        private static void setEnabled(int capability, boolean enabled) {
            if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability);
        }
    }

    private enum ShaderKind {
        NONE, POSITION_COLOR, MASK, MASK_INTERSECT, MASK_SUBTRACT, MASK_EXCLUDE
    }
}
