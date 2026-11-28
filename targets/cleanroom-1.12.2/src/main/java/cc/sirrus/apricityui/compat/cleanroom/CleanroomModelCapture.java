package cc.sirrus.apricityui.compat.cleanroom;

import com.sighs.apricityui.mixin.client.ModelBoxAccessor;
import com.sighs.apricityui.model.ModelMesh;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Captures legacy display-list model parts and direct Tessellator geometry. */
public final class CleanroomModelCapture {
    private static final ThreadLocal<ModelMesh<Integer>> ACTIVE = new ThreadLocal<>();

    private CleanroomModelCapture() {
    }

    static ModelMesh<Integer> capture(Runnable render) {
        ModelMesh<Integer> mesh = new ModelMesh<>();
        ACTIVE.set(mesh);
        try {
            render.run();
            return mesh;
        } finally {
            ACTIVE.remove();
        }
    }

    public static boolean isActive() {
        return ACTIVE.get() != null;
    }

    public static boolean part(ModelRenderer part, float scale) {
        ModelMesh<Integer> mesh = ACTIVE.get();
        if (mesh == null) return false;
        Matrix4f transform = CleanroomRenderService.INSTANCE.getModelViewMatrix();
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        for (var box : part.cubeList) {
            for (var quad : ((ModelBoxAccessor) box).apricityui$getQuads()) {
                List<ModelMesh.Vertex> face = new ArrayList<>(4);
                for (var vertex : quad.vertexPositions) {
                    var p = vertex.vector3D;
                    Vector3f position = transform.transformPosition(
                            new Vector3f((float) p.x * scale, (float) p.y * scale, (float) p.z * scale));
                    face.add(vertex(position, -1, vertex.texturePositionX, vertex.texturePositionY));
                }
                addFace(mesh, texture, face);
            }
        }
        return true;
    }

    public static boolean vertices(BufferBuilder builder) {
        ModelMesh<Integer> mesh = ACTIVE.get();
        if (mesh == null) return false;
        int verticesPerFace = switch (builder.getDrawMode()) {
            case GL11.GL_QUADS -> 4;
            case GL11.GL_TRIANGLES -> 3;
            default -> 0;
        };
        if (verticesPerFace == 0 || builder.getVertexCount() == 0) return true;
        var format = builder.getVertexFormat();
        int position = -1, uv = -1, color = -1;
        for (int i = 0; i < format.getElementCount(); i++) {
            var element = format.getElement(i);
            int offset = format.getOffset(i);
            if (element.getUsage() == VertexFormatElement.EnumUsage.POSITION
                    && element.getType() == VertexFormatElement.EnumType.FLOAT) position = offset;
            if (element.getUsage() == VertexFormatElement.EnumUsage.UV && element.getIndex() == 0
                    && element.getType() == VertexFormatElement.EnumType.FLOAT) uv = offset;
            if (element.getUsage() == VertexFormatElement.EnumUsage.COLOR
                    && element.getType() == VertexFormatElement.EnumType.UBYTE) color = offset;
        }
        if (position < 0) return true;
        Matrix4f transform = CleanroomRenderService.INSTANCE.getModelViewMatrix();
        int texture = uv < 0 || !GL11.glIsEnabled(GL11.GL_TEXTURE_2D) ? 0 : GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        ByteBuffer buffer = builder.getByteBuffer().duplicate().order(ByteOrder.nativeOrder());
        buffer.clear();
        for (int first = 0; first + verticesPerFace <= builder.getVertexCount(); first += verticesPerFace) {
            List<ModelMesh.Vertex> face = new ArrayList<>(verticesPerFace);
            for (int index = first; index < first + verticesPerFace; index++) {
                int offset = index * format.getSize();
                Vector3f point = transform.transformPosition(new Vector3f(buffer.getFloat(offset + position),
                        buffer.getFloat(offset + position + 4), buffer.getFloat(offset + position + 8)));
                float u = uv < 0 ? 0 : buffer.getFloat(offset + uv);
                float v = uv < 0 ? 0 : buffer.getFloat(offset + uv + 4);
                int argb = -1;
                if (color >= 0) {
                    int at = offset + color;
                    argb = Byte.toUnsignedInt(buffer.get(at + 3)) << 24
                            | Byte.toUnsignedInt(buffer.get(at)) << 16
                            | Byte.toUnsignedInt(buffer.get(at + 1)) << 8
                            | Byte.toUnsignedInt(buffer.get(at + 2));
                }
                face.add(vertex(point, argb, u, v));
            }
            addFace(mesh, texture, face);
        }
        return true;
    }

    private static ModelMesh.Vertex vertex(Vector3f p, int color, float u, float v) {
        return new ModelMesh.Vertex(p.x, p.y, p.z, color, u, v, 0, 0, 0, 0, 1);
    }

    private static void addFace(ModelMesh<Integer> mesh, int texture, List<ModelMesh.Vertex> face) {
        if (face.size() < 3) return;
        var a = face.get(0);
        var b = face.get(1);
        var c = face.get(2);
        Vector3f normal = new Vector3f(b.x() - a.x(), b.y() - a.y(), b.z() - a.z())
                .cross(c.x() - a.x(), c.y() - a.y(), c.z() - a.z());
        if (normal.lengthSquared() < 1.0e-12f) return;
        normal.normalize();
        mesh.add(texture, face.stream().map(v -> v.at(new Vector3f(v.x(), v.y(), v.z()), normal)).toList());
    }
}
