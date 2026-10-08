package cc.sirrus.apricityui.compat.cleanroom;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.spi.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

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
    private CleanroomRenderService() { }

    private static final class Mesh {
        final BufferBuilder buffer;
        Mesh(BufferBuilder buffer) { this.buffer = buffer; }
    }

    @Override public void setProjectionMatrix(Matrix4f matrix) { projection = matrix == null ? new Matrix4f() : new Matrix4f(matrix); }
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
    @Override public void submitMesh(Object mesh) { Tessellator.getInstance().draw(); }
    @Override public Object beginTextureBatch(RenderHandle render) { return null; }
    @Override public void emitTextureQuad(Object batch, Matrix4f mat, float x, float y, float width, float height, float u0, float v0, float u1, float v1) { }
    @Override public void flushTextureBatch(Object batch, RenderHandle render) { }
    @Override public void emitVertexUV(Object mesh, Matrix4f mat, float x, float y, float z, float u, float v) {
        Mesh m = (Mesh) mesh; float[] p = transform(mat, x, y, z); m.buffer.pos(p[0], p[1], p[2]).tex(u, v).endVertex();
    }
    @Override public FboHandle createOffscreenTarget(int width, int height, boolean useDepth) { return null; }
    @Override public FboHandle getMainRenderTarget() { return null; }
    @Override public void enableStencil(FboHandle target) { }
    @Override public void destroyBuffers(FboHandle target) { }
    @Override public void clear(FboHandle target, float r, float g, float b, float a) { GL11.glClearColor(r, g, b, a); GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT); }
    @Override public void bindWrite(FboHandle target, boolean setViewport) { }
    @Override public void bindColorTexture(FboHandle target, int unit) { }
    @Override public void blitFramebuffer(FboHandle source, FboHandle target, int srcX0, int srcY0, int srcX1, int srcY1) { }
    @Override public Object createDynamicTexture(String name, Object nativeImage, boolean linear) { return null; }
    @Override public void uploadTextureRegion(Object texture, Object nativeImage, int x, int y, int width, int height, boolean linear) { }
    @Override public void setImagePixel(Object nativeImage, int x, int y, int pixel) { if (nativeImage instanceof com.mojang.blaze3d.platform.NativeImage image) image.setPixelRGBA(x, y, pixel); }
    @Override public void closeTexture(Object texture) { if (texture instanceof DynamicTexture dynamic) dynamic.deleteGlTexture(); }
    @Override public void registerTexture(Object texture, Object location) { }
    @Override public void releaseTexture(Object location) { }
    @Override public void setShader(Object shader) { }
    @Override public void setPositionColorShader() { }
    @Override public void setShaderColor(float a, float r, float g, float b) { GL11.glColor4f(r, g, b, a); }
    @Override public Object getFilterShader() { return null; }
    @Override public Object getFilterBlurShader() { return null; }
    @Override public void setDepthFunc(int func) { GL11.glDepthFunc(func); }
    @Override public void setDepthMask(boolean write) { depthMask = write; GL11.glDepthMask(write); }
    @Override public boolean isDepthTestEnabled() { return depth; }
    @Override public boolean isDepthMaskEnabled() { return depthMask; }
    @Override public void setBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) { GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha); }
    @Override public void disableBlend() { GL11.glDisable(GL11.GL_BLEND); }
    @Override public void enableCull() { cull = true; GL11.glEnable(GL11.GL_CULL_FACE); }
    @Override public void disableCull() { cull = false; GL11.glDisable(GL11.GL_CULL_FACE); }
    @Override public boolean isCullEnabled() { return cull; }
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
    @Override public void setShaderUniformFloat(String name, float value) { }
    @Override public void setShaderUniform2f(String name, float a, float b) { }
    @Override public void setShaderUniform3f(String name, float a, float b, float c) { }
    @Override public void setShaderUniform4f(String name, float a, float b, float c, float d) { }
    @Override public void setShaderUniformI(String name, int value) { }
}
