package cc.sirrus.apricityui.compat.cleanroom;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.model.ModelMesh;
import com.sighs.apricityui.model.ModelSpec;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.PoseMatrices;
import com.sighs.apricityui.spi.AuiModelRenderRequest;
import com.sighs.apricityui.spi.AuiModelRenderService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class CleanroomModelRenderService implements AuiModelRenderService {
    public static final CleanroomModelRenderService INSTANCE = new CleanroomModelRenderService();
    private static final ThreadPoolExecutor SKIN_LOOKUPS = new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), task -> {
                Thread thread = new Thread(task, "aui-player-skin");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardPolicy());
    private final Cache<UUID, Skin> skins = CacheBuilder.newBuilder().maximumSize(256)
            .expireAfterAccess(15, TimeUnit.MINUTES).build();
    private final Cache<String, Boolean> warnings = CacheBuilder.newBuilder().maximumSize(256).build();
    private int generation;

    private CleanroomModelRenderService() {
    }

    public void invalidate() {
        generation++;
        warnings.invalidateAll();
    }

    @Override
    public Preview create(ModelSpec spec) {
        if (spec.source().isEmpty()) return null;
        try {
            return switch (spec.type()) {
                case "player" -> new Session(spec, null, skins.asMap().computeIfAbsent(UUID.fromString(spec.source()), Skin::new));
                case "bakedmodel", "gecko" -> {
                    if ("gecko".equals(spec.type()) && !Loader.isModLoaded("geckolib")
                            && !Loader.isModLoaded("geckolib3")) {
                        throw new IllegalArgumentException("GeckoLib is not loaded");
                    }
                    ResourceLocation id = entityId(spec.source());
                    if (!EntityList.isRegistered(id)) throw new IllegalArgumentException("Unknown entity type: " + id);
                    yield new Session(spec, id, null);
                }
                default -> throw new IllegalArgumentException("Unknown model type: " + spec.type());
            };
        } catch (RuntimeException failure) {
            warn(spec, failure);
            return null;
        }
    }

    private static ResourceLocation entityId(String source) {
        ResourceLocation id = new ResourceLocation(source);
        if (EntityList.isRegistered(id) || !"minecraft".equals(id.getNamespace())) return id;
        // Shared pages can refer to vanilla entities by the names introduced after 1.12.
        return switch (id.getPath()) {
            case "iron_golem" -> new ResourceLocation("minecraft", "villager_golem");
            case "snow_golem" -> new ResourceLocation("minecraft", "snowman");
            case "mooshroom" -> new ResourceLocation("minecraft", "mushroom_cow");
            case "zombified_piglin" -> new ResourceLocation("minecraft", "zombie_pigman");
            case "evoker" -> new ResourceLocation("minecraft", "evocation_illager");
            case "vindicator" -> new ResourceLocation("minecraft", "vindication_illager");
            default -> id;
        };
    }

    private void warn(ModelSpec spec, Throwable failure) {
        String key = spec.type() + ":" + spec.source() + ":" + failure.getMessage();
        if (warnings.asMap().putIfAbsent(key, true) == null) {
            ApricityUI.LOGGER.warn("Cannot render <model type='{}'>{}: {}", spec.type(), spec.source(), failure.toString());
        }
    }

    private static final class Skin {
        ResourceLocation texture;
        boolean slim;
        int revision;

        Skin(UUID id) {
            texture = DefaultPlayerSkin.getDefaultSkin(id);
            slim = "slim".equals(DefaultPlayerSkin.getSkinType(id));
            Minecraft client = Minecraft.getMinecraft();
            // The 1.12 SkinManager only reads existing profile properties for remote players.
            SKIN_LOOKUPS.execute(() -> {
                try {
                    var service = client.getSessionService();
                    var profile = service.fillProfileProperties(new GameProfile(id, ""), true);
                    var type = MinecraftProfileTexture.Type.SKIN;
                    var skin = service.getTextures(profile, true).get(type);
                    if (skin != null) {
                        client.addScheduledTask(() -> client.getSkinManager().loadSkin(skin, type,
                                (loadedType, location, loadedSkin) -> client.addScheduledTask(() -> {
                                    texture = location;
                                    slim = "slim".equals(loadedSkin.getMetadata("model"));
                                    revision++;
                                })));
                    }
                } catch (RuntimeException failure) {
                    ApricityUI.LOGGER.debug("Player preview skin lookup failed for {}", id, failure);
                }
            });
        }
    }

    private final class Session implements Preview {
        private final ModelSpec source;
        private final ResourceLocation entityId;
        private final Skin skin;
        private WorldClient world;
        private Entity entity;
        private ModelMesh<Integer> mesh;
        private int seenGeneration = -1;
        private int seenSkinRevision = -1;
        private long animationTime = Long.MIN_VALUE;
        private boolean failed;
        private boolean closed;

        Session(ModelSpec source, ResourceLocation entityId, Skin skin) {
            this.source = source;
            this.entityId = entityId;
            this.skin = skin;
        }

        @Override
        public boolean tick() {
            if (closed) return false;
            WorldClient current = Minecraft.getMinecraft().world;
            int revision = skin == null ? 0 : skin.revision;
            boolean changed = seenGeneration != generation || seenSkinRevision != revision;
            if (world != current) {
                world = current;
                entity = null;
                changed = true;
            }
            if ("gecko".equals(source.type()) && world != null && animationTime != world.getTotalWorldTime()) {
                animationTime = world.getTotalWorldTime();
                if (entity != null) entity.ticksExisted++;
                changed = true;
            }
            if (changed) {
                mesh = null;
                failed = false;
                seenGeneration = generation;
                seenSkinRevision = revision;
            }
            return changed;
        }

        @Override
        public void render(AuiModelRenderRequest request) {
            if (closed) return;
            tick();
            if (failed || skin == null && world == null) return;
            try {
                if (mesh == null) {
                    mesh = capture();
                    if (mesh.isEmpty()) throw new IllegalStateException("Renderer emitted no supported faces");
                }
                draw(mesh, request);
            } catch (RuntimeException | LinkageError failure) {
                failed = true;
                mesh = null;
                warn(source, failure);
            }
        }

        private ModelMesh<Integer> capture() {
            Minecraft client = Minecraft.getMinecraft();
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            int modelViewDepth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            try {
                GL20.glUseProgram(0);
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
                GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GlStateManager.enableTexture2D();
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GlStateManager.color(1, 1, 1, 1);
                if (skin != null) {
                    client.getTextureManager().bindTexture(skin.texture);
                    GL11.glRotatef(180, 1, 0, 0);
                    return CleanroomModelCapture.capture(() -> renderPlayer(new ModelPlayer(0, skin.slim)));
                }
                if (entity == null) {
                    entity = EntityList.createEntityByIDFromName(entityId, world);
                    if (entity == null || !(entity instanceof EntityLivingBase) && !"gecko".equals(source.type())) {
                        throw new IllegalArgumentException("Unsupported entity type: " + entityId);
                    }
                    entity.setAlwaysRenderNameTag(false);
                    entity.setSilent(true);
                    entity.setNoGravity(true);
                }
                var renderer = client.getRenderManager().getEntityRenderObject(entity);
                if (renderer == null) throw new IllegalArgumentException("No renderer for " + entityId);
                return CleanroomModelCapture.capture(() -> renderer.doRender(entity, 0, 0, 0, 0, 0));
            } finally {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                int depth = GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH);
                while (depth-- > modelViewDepth) GL11.glPopMatrix();
                GL11.glMatrixMode(matrixMode);
                GL11.glPopAttrib();
                GL20.glUseProgram(program);
                restoreGameStateCache();
            }
        }

        @Override
        public void close() {
            closed = true;
            entity = null;
            world = null;
            mesh = null;
        }
    }

    private static void restoreGameStateCache() {
        // Legacy entity renderers mutate GlStateManager's cache. glPopAttrib only restores GL.
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        for (int unit = GL13.GL_TEXTURE0; unit <= GL13.GL_TEXTURE2; unit++) {
            GL13.glActiveTexture(unit);
            int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            boolean enabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            GlStateManager.setActiveTexture(unit);
            GlStateManager.bindTexture(binding);
            if (enabled) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
        }
        GlStateManager.setActiveTexture(activeTexture);
        if (GL11.glIsEnabled(GL11.GL_CULL_FACE)) GlStateManager.enableCull(); else GlStateManager.disableCull();
        if (GL11.glIsEnabled(GL11.GL_BLEND)) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        if (GL11.glIsEnabled(GL11.GL_ALPHA_TEST)) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
        if (GL11.glIsEnabled(GL11.GL_DEPTH_TEST)) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
        if (GL11.glIsEnabled(GL11.GL_LIGHTING)) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
        if (GL11.glIsEnabled(GL12.GL_RESCALE_NORMAL)) GlStateManager.enableRescaleNormal(); else GlStateManager.disableRescaleNormal();
        GlStateManager.depthMask(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
        GlStateManager.depthFunc(GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
        var values = BufferUtils.createFloatBuffer(16);
        GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF, values);
        GlStateManager.alphaFunc(GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC), values.get(0));
        GlStateManager.tryBlendFuncSeparate(GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, values);
        GlStateManager.color(values.get(0), values.get(1), values.get(2), values.get(3));
    }

    // ModelPlayer.render reads player state. The neutral preview only needs its model parts.
    private static void renderPlayer(ModelPlayer player) {
        for (ModelRenderer part : List.of(player.bipedHead, player.bipedBody, player.bipedLeftArm,
                player.bipedRightArm, player.bipedLeftLeg, player.bipedRightLeg, player.bipedHeadwear,
                player.bipedLeftArmwear, player.bipedRightArmwear, player.bipedLeftLegwear,
                player.bipedRightLegwear, player.bipedBodyWear)) {
            part.render(1f / 16f);
        }
    }

    private static void draw(ModelMesh<Integer> mesh, AuiModelRenderRequest request) {
        Matrix4f transform = new Matrix4f(PoseMatrices.of(request.poseStack())).translate(0, 0, Base.getGuiItemModelZ());
        var faces = mesh.layout(request.spec(), request.x(), request.y(), request.width(), request.height(),
                transform, PoseMatrices.normal(request.poseStack()));
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL20.glUseProgram(0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            GL11.glAlphaFunc(GL11.GL_GREATER, 1f / 255f);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            CleanroomRenderService.INSTANCE.setBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            int texture = -1;
            int mode = -1;
            for (var face : faces) {
                int nextMode = face.vertices().size() == 3 ? GL11.GL_TRIANGLES : GL11.GL_QUADS;
                if (texture != face.material() || mode != nextMode) {
                    if (mode != -1) GL11.glEnd();
                    texture = face.material();
                    mode = nextMode;
                    if (texture == 0) {
                        GL11.glDisable(GL11.GL_TEXTURE_2D);
                    } else {
                        GL11.glEnable(GL11.GL_TEXTURE_2D);
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
                    }
                    GL11.glBegin(mode);
                }
                for (var v : face.vertices()) {
                    float shade = 0.6f + 0.4f * Math.max(0, (v.nx() - v.ny() + v.nz()) * 0.57735f);
                    int color = v.color();
                    GL11.glColor4f((color >> 16 & 255) * shade / 255f, (color >> 8 & 255) * shade / 255f,
                            (color & 255) * shade / 255f, (color >>> 24) / 255f);
                    GL11.glTexCoord2f(v.u(), v.v());
                    GL11.glVertex3f(v.x(), v.y(), v.z());
                }
            }
            if (mode != -1) GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
            GL20.glUseProgram(program);
        }
    }
}
