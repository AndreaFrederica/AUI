package cc.sirrus.apricityui.compat.cleanroom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.render.LegacyBackgroundTarget;
import com.sighs.apricityui.spi.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL15;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
    private StartupTexture startupTexture;
    private boolean depth = true, depthMask = true, cull;
    private ShaderHandle backgroundShader;
    private boolean backgroundShaderAttempted;
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
    private boolean vertexDiagnosticLogged;
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewportScratch = BufferUtils.createIntBuffer(4);
    private final IntBuffer scissorScratch = BufferUtils.createIntBuffer(4);
    private IntBuffer textureUploadBuffer = BufferUtils.createIntBuffer(4096);
    private IntBuffer blankStripBuffer;
    private int blankStripFillColor = Integer.MIN_VALUE;
    private int positionColorVbo = 0;
    private FloatBuffer positionColorBuffer = BufferUtils.createFloatBuffer(4096 * 7);
    private CleanroomRenderService() { }

    private static final class Mesh {
        final BufferBuilder buffer;
        final int glMode;
        final MeshFormat format;
        float[] vertexData = new float[256 * 7];
        int vertexCount = 0;
        Mesh(BufferBuilder buffer, int glMode, MeshFormat format) {
            this.buffer = buffer; this.glMode = glMode; this.format = format;
        }
        void addVertex(float x, float y, float z, int r, int g, int b, int a) {
            int offset = vertexCount * 7;
            if (offset + 7 > vertexData.length) {
                vertexData = Arrays.copyOf(vertexData, Math.max(vertexData.length * 2, offset + 7));
            }
            vertexData[offset] = x;
            vertexData[offset + 1] = y;
            vertexData[offset + 2] = z;
            vertexData[offset + 3] = r / 255.0f;
            vertexData[offset + 4] = g / 255.0f;
            vertexData[offset + 5] = b / 255.0f;
            vertexData[offset + 6] = a / 255.0f;
            vertexCount++;
        }
        void addVertexUV(float x, float y, float z, float u, float v) {
            int offset = vertexCount * 5;
            if (offset + 5 > vertexData.length) {
                vertexData = Arrays.copyOf(vertexData, Math.max(vertexData.length * 2, offset + 5));
            }
            vertexData[offset] = x;
            vertexData[offset + 1] = y;
            vertexData[offset + 2] = z;
            vertexData[offset + 3] = u;
            vertexData[offset + 4] = v;
            vertexCount++;
        }
    }

    @Override public void setProjectionMatrix(Matrix4f matrix) {
        projection = matrix == null ? new Matrix4f() : new Matrix4f(matrix);
        uploadProjection();
    }
    @Override public Matrix4f getProjectionMatrix() { return new Matrix4f(projection); }

    @Override
    public Matrix4f getModelViewMatrix() {
        matrixBuffer.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, matrixBuffer);
        return new Matrix4f(matrixBuffer);
    }

    @Override
    public void setModelViewMatrix(Matrix4f matrix) {
        int mode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        matrixBuffer.clear();
        matrix.get(matrixBuffer);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadMatrix(matrixBuffer);
        GL11.glMatrixMode(mode);
    }

    @Override public void clearDepthBuffer() {
        boolean write = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        try {
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glClearDepth(1.0d);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        } finally {
            GL11.glClearDepth(clearDepth);
            GL11.glDepthMask(write);
            if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }
    }

    @Override public void enableDepthTest() { depth = true; GL11.glEnable(GL11.GL_DEPTH_TEST); }
    @Override public void disableDepthTest() { depth = false; GL11.glDisable(GL11.GL_DEPTH_TEST); }
    @Override public void enableBlend() { GL11.glEnable(GL11.GL_BLEND); }
    @Override public void setBlendFunc(int srcFactor, int dstFactor) { GL11.glBlendFunc(srcFactor, dstFactor); }
    @Override public MeshBuilder beginMesh(MeshMode mode, MeshFormat format) {
        int glMode = mode == MeshMode.QUADS ? GL11.GL_QUADS : GL11.GL_TRIANGLES;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        return MeshBuilder.of(new Mesh(buffer, glMode, format));
    }
    private static float transformX(Matrix4f m, float x, float y, float z) {
        return m == null ? x : m.m00() * x + m.m10() * y + m.m20() * z + m.m30();
    }
    private static float transformY(Matrix4f m, float x, float y, float z) {
        return m == null ? y : m.m01() * x + m.m11() * y + m.m21() * z + m.m31();
    }
    private static float transformZ(Matrix4f m, float x, float y, float z) {
        return m == null ? z : m.m02() * x + m.m12() * y + m.m22() * z + m.m32();
    }
    @Override public void emitVertex(Object mesh, Matrix4f mat, float x, float y, float z, int r, int g, int b, int a) {
        Mesh m = (Mesh) mesh;
        float px = transformX(mat, x, y, z);
        float py = transformY(mat, x, y, z);
        float pz = transformZ(mat, x, y, z);
        if (!vertexDiagnosticLogged) {
            vertexDiagnosticLogged = true;
            com.sighs.apricityui.ApricityUI.LOGGER.info(
                    "[AUI Cleanroom] first vertex input=({}, {}, {}) transformed=({}, {}, {}) color=({}, {}, {}, {}) matrix={}",
                    x, y, z, px, py, pz, r, g, b, a, mat);
        }
        if (m.format == MeshFormat.POSITION_COLOR || m.format == MeshFormat.POSITION) {
            m.addVertex(px, py, pz, r, g, b, a);
        } else {
            m.buffer.pos(px, py, pz).color(r, g, b, a).endVertex();
        }
    }
    @Override public void submitMesh(Object mesh) {
        Mesh m = mesh instanceof Mesh value ? value : null;
        if (m != null && (m.format == MeshFormat.POSITION_COLOR || m.format == MeshFormat.POSITION)) {
            drawModernPositionColor(m);
        } else if (m != null && m.format == MeshFormat.POSITION_TEX) {
            drawModernPositionTex(m);
        } else {
            Tessellator.getInstance().draw();
        }
        // BufferBuilder's POSITION_COLOR / filter shader pass is owned by AUI only.
        // Restore Minecraft's fixed/texture pipeline before font and texture batches.
        GL20.glUseProgram(0);
        activeShader = null;
        if (!positionColorDiagnosticLogged) {
            positionColorDiagnosticLogged = true;
            try {
                viewportScratch.clear();
                GL11.glGetInteger(GL11.GL_VIEWPORT, viewportScratch);
                scissorScratch.clear();
                GL11.glGetInteger(GL11.GL_SCISSOR_BOX, scissorScratch);
                com.sighs.apricityui.ApricityUI.LOGGER.info(
                        "[AUI Cleanroom] first mesh submitted program={} viewport={}x{}+{},{} scissor={} box={}x{}+{},{} glError={}",
                        GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), viewportScratch.get(2), viewportScratch.get(3), viewportScratch.get(0), viewportScratch.get(1),
                        GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), scissorScratch.get(2), scissorScratch.get(3), scissorScratch.get(0), scissorScratch.get(1),
                        GL11.glGetError());
            } catch (Throwable error) {
                com.sighs.apricityui.ApricityUI.LOGGER.debug("[AUI Cleanroom] mesh diagnostic failed", error);
            }
        }
    }

    private void drawModernPositionColor(Mesh mesh) {
        if (mesh.vertexCount == 0) return;
        setPositionColorShader();
        if (activeShader == null) return;
        int floatCount = mesh.vertexCount * 7;
        if (positionColorBuffer.capacity() < floatCount) {
            positionColorBuffer = BufferUtils.createFloatBuffer(Math.max(positionColorBuffer.capacity() * 2, floatCount));
        }
        positionColorBuffer.clear();
        positionColorBuffer.put(mesh.vertexData, 0, floatCount);
        positionColorBuffer.flip();
        if (positionColorVbo == 0) {
            positionColorVbo = GL15.glGenBuffers();
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionColorVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, positionColorBuffer, GL15.GL_STREAM_DRAW);
        int stride = 7 * Float.BYTES;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, stride, 3L * Float.BYTES);
        GL11.glDrawArrays(mesh.glMode, 0, mesh.vertexCount);
        GL20.glDisableVertexAttribArray(1);
        GL20.glDisableVertexAttribArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL20.glUseProgram(0);
        activeShader = null;
    }

    /**
     * Submits a POSITION_TEX quad/triangle mesh using generic vertex attributes
     * (location 0 = Position, location 1 = UV0) so #version 150 filter/blur/mask/blend
     * shaders receive actual UVs instead of Tessellator's legacy gl_MultiTexCoord0.
     */
    private void drawModernPositionTex(Mesh mesh) {
        if (mesh.vertexCount == 0) return;
        int floatCount = mesh.vertexCount * 5;
        if (positionColorBuffer.capacity() < floatCount) {
            positionColorBuffer = BufferUtils.createFloatBuffer(Math.max(positionColorBuffer.capacity() * 2, floatCount));
        }
        positionColorBuffer.clear();
        positionColorBuffer.put(mesh.vertexData, 0, floatCount);
        positionColorBuffer.flip();
        if (positionColorVbo == 0) {
            positionColorVbo = GL15.glGenBuffers();
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionColorVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, positionColorBuffer, GL15.GL_STREAM_DRAW);
        int stride = 5 * Float.BYTES;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 3L * Float.BYTES);
        GL11.glDrawArrays(mesh.glMode, 0, mesh.vertexCount);
        GL20.glDisableVertexAttribArray(1);
        GL20.glDisableVertexAttribArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
    @Override public Object beginTextureBatch(RenderHandle render) {
        ResourceLocation location = render == null ? null : render.as();
        if (location == null) return null;
        try {
            Minecraft.getMinecraft().getTextureManager().bindTexture(location);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
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
        float px = transformX(matrix, x, y, 0);
        float py = transformY(matrix, x, y, 0);
        float pz = transformZ(matrix, x, y, 0);
        buffer.pos(px, py, pz).tex(u, v).color(r, g, b, a).endVertex();
    }
    @Override public void flushTextureBatch(Object batch, RenderHandle render) {
        if (batch instanceof TextureBatch) Tessellator.getInstance().draw();
    }
    private static final class TextureBatch { final BufferBuilder buffer; TextureBatch(BufferBuilder buffer) { this.buffer = buffer; } }
    @Override public void emitVertexUV(Object mesh, Matrix4f mat, float x, float y, float z, float u, float v) {
        Mesh m = (Mesh) mesh;
        m.addVertexUV(transformX(mat, x, y, z), transformY(mat, x, y, z), transformZ(mat, x, y, z), u, v);
    }
    @Override public FboHandle createOffscreenTarget(int width, int height, boolean useDepth) {
        if (width <= 0 || height <= 0) return null;
        int previousFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int previousTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            // Framebuffer's constructor binds FBO 0 at the end of createBindFramebuffer();
            // restore the caller's FBO and texture binding so mid-pass pool allocations never corrupt state.
            Framebuffer framebuffer = new Framebuffer(width, height, useDepth);
            framebuffer.setFramebufferFilter(GL11.GL_LINEAR);
            return FboHandle.of(framebuffer, width, height);
        } catch (Throwable ignored) {
            return null;
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTex);
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
        if (framebuffer == null) return;
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        framebuffer.bindFramebuffer(false);
        framebuffer.setFramebufferColor(r, g, b, a);
        GL11.glClearColor(r, g, b, a);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
    }
    @Override public void bindWrite(FboHandle target, boolean setViewport) {
        Framebuffer framebuffer = framebuffer(target);
        if (framebuffer != null) framebuffer.bindFramebuffer(setViewport);
    }
    @Override public void bindColorTexture(FboHandle target, int unit) {
        Framebuffer framebuffer = framebuffer(target);
        if (framebuffer == null) return;
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + Math.max(0, unit));
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, framebuffer.framebufferTexture);
        GL13.glActiveTexture(previousActiveTexture);
    }
    @Override public void blitFramebuffer(FboHandle source, FboHandle target, int srcX0, int srcY0, int srcX1, int srcY1) {
        Framebuffer from = framebuffer(source), to = framebuffer(target);
        if (from == null || to == null) return;
        int previousReadFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from.framebufferObject);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to.framebufferObject);
        GL30.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, srcX0, srcY0, srcX1, srcY1,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
    }
    private static Framebuffer framebuffer(FboHandle handle) {
        return handle == null ? null : handle.as();
    }
    @Override public Object createDynamicTexture(String name, Object nativeImage, boolean linear) {
        if (!(nativeImage instanceof com.mojang.blaze3d.platform.NativeImage image)) return null;
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) return null;
        try {
            CleanroomDynamicTexture texture = new CleanroomDynamicTexture(width, height, linear);
            int glId = texture.getGlTextureId();
            int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int prevRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
            int prevSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
            int prevSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
            int prevAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
                applyTextureParameters(linear);
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
                if (image.hasPixels()) {
                    int[] src = image.pixels();
                    IntBuffer upload = ensureTextureUploadBuffer(width * height);
                    upload.clear();
                    upload.put(src, 0, width * height);
                    upload.flip();
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, upload);
                } else {
                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                            GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
                    // Clear VRAM once in compact strips so linear-filtered atlas gaps never sample garbage.
                    // Font coverage atlases use straight-alpha transparent white (0x00FFFFFF) to avoid dark fringes.
                    int clearColor = name != null && name.startsWith("apricityui:font/") ? 0x00FFFFFF : 0x00000000;
                    clearTextureInStrips(width, height, clearColor);
                }
            } finally {
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, prevRowLength);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, prevSkipRows);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, prevSkipPixels);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevAlignment);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousBinding);
            }
            return texture;
        } catch (Throwable error) {
            com.sighs.apricityui.ApricityUI.LOGGER.warn("[AUI Cleanroom] createDynamicTexture failed name={} {}x{}", name, width, height, error);
            return null;
        }
    }

    @Override public void uploadTextureRegion(Object texture, Object nativeImage, int x, int y, int width, int height, boolean linear) {
        if (!(nativeImage instanceof com.mojang.blaze3d.platform.NativeImage image)) return;
        int[] src = image.pixels();
        if (src == null || width <= 0 || height <= 0) return;
        int imgW = image.getWidth();
        int imgH = image.getHeight();
        int clampedX = Math.max(0, x);
        int clampedY = Math.max(0, y);
        int clampedW = Math.min(width - (clampedX - x), imgW - clampedX);
        int clampedH = Math.min(height - (clampedY - y), imgH - clampedY);
        if (clampedW <= 0 || clampedH <= 0) return;

        int glId;
        boolean filterChanged = false;
        if (texture instanceof CleanroomDynamicTexture dynamic) {
            glId = dynamic.getGlTextureId();
            if (dynamic.linear != linear) {
                dynamic.linear = linear;
                filterChanged = true;
            }
        } else if (texture instanceof AbstractTexture abstractTexture) {
            glId = abstractTexture.getGlTextureId();
            filterChanged = true;
        } else {
            return;
        }

        try {
            IntBuffer upload = ensureTextureUploadBuffer(clampedW * clampedH);
            upload.clear();
            for (int row = 0; row < clampedH; row++) {
                upload.put(src, (clampedY + row) * imgW + clampedX, clampedW);
            }
            upload.flip();

            int previousBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int prevRowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
            int prevSkipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
            int prevSkipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
            int prevAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
            try {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
                if (filterChanged) {
                    applyTextureParameters(linear);
                }
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, clampedX, clampedY, clampedW, clampedH,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, upload);
            } finally {
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, prevRowLength);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, prevSkipRows);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, prevSkipPixels);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, prevAlignment);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousBinding);
            }
        } catch (Throwable error) {
            com.sighs.apricityui.ApricityUI.LOGGER.warn("[AUI Cleanroom] uploadTextureRegion failed rect=({},{} {}x{})",
                    clampedX, clampedY, clampedW, clampedH, error);
        }
    }

    private IntBuffer ensureTextureUploadBuffer(int pixelCount) {
        if (textureUploadBuffer.capacity() < pixelCount) {
            int nextCapacity = Math.max(pixelCount, textureUploadBuffer.capacity() * 2);
            textureUploadBuffer = BufferUtils.createIntBuffer(nextCapacity);
        }
        return textureUploadBuffer;
    }

    private void clearTextureInStrips(int width, int height, int abgrFill) {
        int stripRows = Math.max(1, Math.min(height, Math.max(1, 65536 / Math.max(1, width))));
        int stripPixels = width * stripRows;
        if (blankStripBuffer == null || blankStripBuffer.capacity() < stripPixels) {
            blankStripBuffer = BufferUtils.createIntBuffer(stripPixels);
            blankStripFillColor = Integer.MIN_VALUE;
        }
        if (blankStripFillColor != abgrFill) {
            blankStripBuffer.clear();
            int cap = blankStripBuffer.capacity();
            for (int i = 0; i < cap; i++) {
                blankStripBuffer.put(abgrFill);
            }
            blankStripFillColor = abgrFill;
        }
        for (int row = 0; row < height; row += stripRows) {
            int rows = Math.min(stripRows, height - row);
            blankStripBuffer.position(0);
            blankStripBuffer.limit(width * rows);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, row, width, rows,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, blankStripBuffer);
        }
        blankStripBuffer.clear();
    }

    private static void applyTextureParameters(boolean linear) {
        int filter = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    private static final class CleanroomDynamicTexture extends AbstractTexture {
        final int width;
        final int height;
        boolean linear;

        CleanroomDynamicTexture(int width, int height, boolean linear) {
            this.width = width;
            this.height = height;
            this.linear = linear;
        }

        @Override
        public void loadTexture(IResourceManager resourceManager) {
            // Backing storage is uploaded explicitly via createDynamicTexture / uploadTextureRegion.
        }
    }

    // The splash has its own GL thread. Keep its texture out of Minecraft's texture manager.
    private record StartupTexture(int id, int width, int height) {
    }

    @Override
    public Object createDynamicTexture(String name, int width, int height, boolean linear) {
        if (width <= 0 || height <= 0) return null;
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int id = GL11.glGenTextures();
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            applyTextureParameters(linear);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            return new StartupTexture(id, width, height);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
    }

    @Override
    public boolean uploadTexturePixels(Object texture, ByteBuffer pixels, int offset, int x, int y, int width, int height) {
        if (!(texture instanceof StartupTexture target) || !pixels.isDirect()
                || width <= 0 || height <= 0 || x < 0 || y < 0
                || width > target.width - x || height > target.height - y
                || offset < 0 || offset + (long) width * height * 4 > pixels.limit()) return false;
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_PIXEL_STORE_BIT);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.id);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, x, y, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                    pixels.duplicate().position(offset).limit(offset + width * height * 4));
            return true;
        } finally {
            GL11.glPopClientAttrib();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
    }

    @Override
    public void drawScreenTexture(Object graphics, Object texture, Object location,
                                  int x, int y, int width, int height, float alpha) {
        if (!(texture instanceof StartupTexture target) || alpha <= 0) return;
        int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, width, height, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            GL20.glUseProgram(0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.id);
            GL11.glColor4f(1, 1, 1, alpha);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x, y);
            GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x + width, y);
            GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x + width, y + height);
            GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x, y + height);
            GL11.glEnd();
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrix);
            GL11.glPopAttrib();
            GL20.glUseProgram(program);
        }
    }

    @Override public void setImagePixel(Object nativeImage, int x, int y, int pixel) {
        if (nativeImage instanceof com.mojang.blaze3d.platform.NativeImage image) image.setPixelRGBA(x, y, pixel);
    }
    @Override public void writeImagePixels(Object nativeImage, int x, int y, int width, int height, int[] abgrPixels) {
        if (nativeImage instanceof com.mojang.blaze3d.platform.NativeImage image) {
            image.writePixels(x, y, width, height, abgrPixels);
        }
    }
    @Override public void closeTexture(Object texture) {
        if (texture instanceof StartupTexture target) GL11.glDeleteTextures(target.id);
        if (texture instanceof AbstractTexture abstractTexture) abstractTexture.deleteGlTexture();
    }
    @Override public void registerTexture(Object texture, Object location) {
        if (texture instanceof StartupTexture target) {
            startupTexture = target;
            return;
        }
        if (!(texture instanceof AbstractTexture abstractTexture) || !(location instanceof ResourceLocation id)) return;
        try { Minecraft.getMinecraft().getTextureManager().loadTexture(id, abstractTexture); }
        catch (Throwable ignored) { }
    }
    @Override public void releaseTexture(Object location) {
        if (!(location instanceof ResourceLocation id)) return;
        if ("apricityui:startup/loading".equals(id.toString()) && startupTexture != null) {
            closeTexture(startupTexture);
            startupTexture = null;
            return;
        }
        try { Minecraft.getMinecraft().getTextureManager().deleteTexture(id); }
        catch (Throwable ignored) { }
    }
    @Override public void setShader(Object shader) {
        activeShader = shader instanceof ShaderHandle handle ? handle : null;
        GL20.glUseProgram(activeShader == null ? 0 : activeShader.program);
        if (activeShader != null) {
            uploadProjection();
            uniform("ModelViewMat", location -> {
                Matrix4f modelView = getModelViewMatrix();
                matrixBuffer.clear();
                modelView.get(matrixBuffer);
                GL20.glUniformMatrix4(location, false, matrixBuffer);
            });
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
    @Override
    public Object getBackgroundShader() {
        if (!backgroundShaderAttempted) {
            backgroundShaderAttempted = true;
            backgroundShader = compileShader("background", "background");
        }
        return backgroundShader;
    }

    @Override
    public RenderStateScope pushBackgroundTarget(FboHandle target) {
        return LegacyBackgroundTarget.open(this, target);
    }

    @Override
    public void destroyBackgroundTarget(FboHandle target) {
        LegacyBackgroundTarget.destroy(this, target);
    }

    @Override public Object getFilterShader() {
        ShaderHandle current = filterShader;
        if (current != null) return current;
        synchronized (this) {
            if (filterShader == null) filterShader = compileShader("filter", "filter", ShaderKind.FILTER);
            return filterShader;
        }
    }
    @Override public Object getFilterBlurShader() {
        ShaderHandle current = blurShader;
        if (current != null) return current;
        synchronized (this) {
            if (blurShader == null) blurShader = compileShader("filter_blur", "filter_blur", ShaderKind.BLUR);
            return blurShader;
        }
    }
    @Override public Object getFilterBlendShader() {
        ShaderHandle current = blendShader;
        if (current != null) return current;
        synchronized (this) {
            if (blendShader == null) blendShader = compileShader("filter", "filter_blend", ShaderKind.BLEND);
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
    @Override public boolean isOnRenderThread() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            return mc == null || mc.isCallingFromMinecraftThread();
        } catch (Throwable ignored) {
            return true;
        }
    }
    @Override public void recordRenderCall(Runnable task) {
        if (task == null) return;
        if (isOnRenderThread()) {
            task.run();
            return;
        }
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc != null) {
                mc.addScheduledTask(task);
                return;
            }
        } catch (Throwable ignored) {
        }
        task.run();
    }
    @Override public String getGLVersionString() { return GL11.glGetString(GL11.GL_VERSION); }
    /** Cleanroom's main 1.12 framebuffer is depth-only; use scissor clips instead. */
    @Override public boolean currentTargetHasStencil() { return false; }
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
        // JOML writes absolute buffer indices without advancing position.
        // flip() here would upload zero matrices and leave ProjMat all zero.
        GL20.glUniformMatrix4(location, false, matrixBuffer);
    }

    private static void applyShaderBlend(ShaderHandle shader) {
        switch (shader.kind) {
            case FILTER -> {
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                        GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
            case BLUR, BLEND -> {
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ZERO,
                        GL11.GL_ONE, GL11.GL_ZERO);
            }
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
            // Bind fixed sampler units (Sampler0 -> unit 0, Sampler1 -> unit 1) once at link time.
            int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            GL20.glUseProgram(program);
            int sampler0 = GL20.glGetUniformLocation(program, "Sampler0");
            if (sampler0 >= 0) GL20.glUniform1i(sampler0, 0);
            int sampler1 = GL20.glGetUniformLocation(program, "Sampler1");
            if (sampler1 >= 0) GL20.glUniform1i(sampler1, 1);
            GL20.glUseProgram(previousProgram);
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
                    "#version 120\nattribute vec3 Position;\nattribute vec4 Color;\nuniform mat4 ProjMat;\nvarying vec4 vertexColor;\nvoid main(){ gl_Position = ProjMat * vec4(Position, 1.0); vertexColor = Color; }\n");
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
        final boolean blend, depthTest, cull, scissor, stencil, texture2d;
        final boolean depthMask;
        final int depthFunc;
        final int srcRgb, dstRgb, srcAlpha, dstAlpha;
        final int activeTexture;
        final int texture0, texture1;
        final int drawFramebuffer, readFramebuffer;
        final int viewportX, viewportY, viewportWidth, viewportHeight;
        final int program;
        final Matrix4f projection;

        private RenderStateSnapshot(boolean blend, boolean depthTest, boolean cull, boolean scissor,
                                    boolean stencil, boolean texture2d, boolean depthMask, int depthFunc,
                                    int srcRgb, int dstRgb, int srcAlpha, int dstAlpha,
                                    int activeTexture, int texture0, int texture1, int drawFramebuffer, int readFramebuffer,
                                    int viewportX, int viewportY, int viewportWidth, int viewportHeight,
                                    int program, Matrix4f projection) {
            this.blend = blend; this.depthTest = depthTest; this.cull = cull;
            this.scissor = scissor; this.stencil = stencil; this.texture2d = texture2d;
            this.depthMask = depthMask;
            this.depthFunc = depthFunc; this.srcRgb = srcRgb; this.dstRgb = dstRgb;
            this.srcAlpha = srcAlpha; this.dstAlpha = dstAlpha; this.activeTexture = activeTexture;
            this.texture0 = texture0; this.texture1 = texture1;
            this.drawFramebuffer = drawFramebuffer; this.readFramebuffer = readFramebuffer;
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
            int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            IntBuffer viewport = service.viewportScratch;
            viewport.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            return new RenderStateSnapshot(
                    GL11.glIsEnabled(GL11.GL_BLEND), GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE), GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                    GL11.glIsEnabled(GL11.GL_STENCIL_TEST), GL11.glIsEnabled(GL11.GL_TEXTURE_2D),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    active, texture0, texture1, drawFramebuffer, readFramebuffer,
                    viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3),
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), new Matrix4f(service.projection)
            );
        }

        void restore(CleanroomRenderService service) {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            setEnabled(GL11.GL_BLEND, blend);
            setEnabled(GL11.GL_DEPTH_TEST, depthTest);
            setEnabled(GL11.GL_CULL_FACE, cull);
            setEnabled(GL11.GL_SCISSOR_TEST, scissor);
            setEnabled(GL11.GL_STENCIL_TEST, stencil);
            setEnabled(GL11.GL_TEXTURE_2D, texture2d);
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
            service.syncCachedStateFromGl();
        }

        private static void setEnabled(int capability, boolean enabled) {
            if (enabled) GL11.glEnable(capability); else GL11.glDisable(capability);
        }
    }

    private enum ShaderKind {
        NONE, POSITION_COLOR, FILTER, BLUR, BLEND, MASK, MASK_INTERSECT, MASK_SUBTRACT, MASK_EXCLUDE
    }
}
