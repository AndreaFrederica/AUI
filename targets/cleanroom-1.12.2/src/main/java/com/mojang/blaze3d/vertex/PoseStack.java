package com.mojang.blaze3d.vertex;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A small 1.12 compatible implementation of the modern Blaze3D pose stack.
 *
 * <p>Minecraft 1.12 has no equivalent public class. Keeping this class in the
 * Blaze3D namespace lets the version neutral AUI renderer share one matrix API
 * while the Cleanroom target still uses the legacy GL renderer underneath.</p>
 */
public final class PoseStack {
    private final Deque<Pose> stack = new ArrayDeque<>();

    public PoseStack() {
        stack.push(new Pose(new Matrix4f(), new Matrix3f()));
    }

    public void pushPose() {
        Pose current = stack.peek();
        stack.push(new Pose(new Matrix4f(current.pose), new Matrix3f(current.normal)));
    }

    public void popPose() {
        if (stack.size() <= 1) {
            throw new IllegalStateException("Pose stack underflow");
        }
        stack.pop();
    }

    public Pose last() {
        return stack.peek();
    }

    public void translate(double x, double y, double z) {
        last().pose.translate((float) x, (float) y, (float) z);
    }

    public void scale(float x, float y, float z) {
        last().pose.scale(x, y, z);
        // Keep the normal matrix coherent for lighting-aware callers. AUI's
        // 2D helper intentionally edits only the pose matrix when needed.
        last().normal.scale(x, y, z).invert().transpose();
    }

    public void mulPose(Quaternionf rotation) {
        last().pose.rotate(rotation);
        last().normal.rotate(rotation);
    }

    public void mulPoseMatrix(Matrix4f matrix) {
        last().pose.mul(matrix);
    }

    public static final class Pose {
        private final Matrix4f pose;
        private final Matrix3f normal;

        private Pose(Matrix4f pose, Matrix3f normal) {
            this.pose = pose;
            this.normal = normal;
        }

        public Matrix4f pose() {
            return pose;
        }

        public Matrix3f normal() {
            return normal;
        }
    }
}
