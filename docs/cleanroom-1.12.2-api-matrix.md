# Cleanroom 1.12.2 API 移植矩阵

这份矩阵记录当前 AUI 代码与 1.12.2 的差异。状态含义：

- `待移植`：尚未接入 Cleanroom target。
- `可复用`：预计可以直接复用，仍需编译确认。
- `需重写`：依赖现代 Minecraft/Forge API，必须写 1.12 实现。
- `可选`：基础 UI 完成后再处理。

| AUI 模块 | 当前代码位置 | 1.12 处理方式 | 状态 |
| --- | --- | --- | --- |
| Mod 生命周期 | `targets/forge-1.20.1/.../forge/ApricityUIForge.java` | 改为 `@Mod` + `@SidedProxy` + FML state events | 需重写 |
| 客户端初始化 | `.../ClientServicesBootstrap.java`、`.../ClientLoaderForge.java` | 使用客户端代理中的 `FMLPreInitializationEvent`/`FMLInitializationEvent` 等生命周期和 Forge 事件总线；1.12 没有 `FMLClientSetupEvent` | 需重写 |
| DOM、CSS、HTML、JS | `common/src/main/java/com/sighs/apricityui/{dom,parser,style,element}` | 先逐文件编译；剥离 `PoseStack` 和现代资源类型 | 部分可复用 |
| 资源扫描 | `common/.../loader/Loader.java`、`ClientLoader.java` | 适配 1.12 `IResourceManager` 和资源包 API | 需适配 |
| 绘制矩阵 | `common/.../render`、`spi/AuiRenderService.java` | 使用 1.12 矩阵/GL 状态和 `BufferBuilder` | 需重写 |
| 动态纹理 | `common/.../element/Canvas.java`、`Iframe.java` | 适配 1.12 `DynamicTexture`、纹理管理器和像素上传 | 需重写 |
| Screen/Overlay | `targets/forge-1.20.1/.../screen`、`client/Client.java` | 改为 `GuiScreen`、Forge 客户端事件和鼠标键盘事件 | 需重写 |
| 容器和槽位 | `targets/forge-1.20.1/.../{screen,container,slot}` | 改为 `Container`、`GuiContainer`、1.12 `Slot` 和点击协议 | 需重写 |
| 物品渲染 | `targets/forge-1.20.1/.../forge/ItemRenderService.java` | 使用 1.12 `RenderItem` 和旧版模型接口 | 需重写 |
| 注册表 | `targets/forge-1.20.1/.../registry/ApricityMenus.java` | 使用 `RegistryEvent`/`GameRegistry`，不使用 `DeferredRegister` | 需重写 |
| 网络 | `common/.../network`、`targets/forge-1.20.1/.../network` | 使用 `SimpleNetworkWrapper`、`IMessage`、`ByteBuf` | 需重写 |
| 数据持久化 | `targets/forge-1.20.1/.../config/ApricitySavedData.java` | 使用 `WorldSavedData` 或 Capability | 需重写 |
| KubeJS/Rhino | `common/.../util/kjs`、`targets/.../script` | 锁定 Cleanroom 可用版本后重新接入 | 待依赖确认 |
| Mixin | `common/.../mixin`、`targets/.../mixin` | 重新确定 1.12 目标类和注入点 | 需重写 |
| Access Transformer | `targets/forge-1.20.1/.../META-INF/accesstransformer.cfg` | 按 MCP 39 名称制作单一 `apricityui_at.cfg` | 待移植 |
| Shader/滤镜 | `common/src/main/resources/assets/apricityui/shaders` | 先验证 1.12 shader JSON/GL 状态兼容性 | 可选 |
| WorldWindow | `common/.../world/WorldWindow.java` | 适配 1.12 世界矩阵、光线检测和渲染事件 | 可选 |
| Canvas | `common/.../element/Canvas.java` | 依赖动态纹理和 1.12 上传 API | 可选 |
| Iframe/WebView | `common/.../element/Iframe.java`、`webview` | 保持 Windows 可选能力，服务端无操作 | 可选 |

## 实体、世界与数据 API 的具体映射

1.12.2 具备这些能力；移植工作是把 AUI 使用的现代名称转换为 1.12 的原生类型，并由 Cleanroom 平台适配器承接生命周期差异。

| 现代入口 | 1.12.2 原生实现 | 适配要点 |
| --- | --- | --- |
| `Entity` | `net.minecraft.entity.Entity` | 实体 ID、位置、旋转和世界引用直接转发 |
| `Player` / `ServerPlayer` | `EntityPlayer` / `EntityPlayerMP` | 服务端发包、打开容器和关闭容器走 MP 类型 |
| `Level` / `ServerLevel` | `World` / `WorldServer` | 客户端世界和服务端世界分别从 `Minecraft.world`、服务器世界管理器取得 |
| `Vec3` | `Vec3d` | 数值字段与距离计算保持双精度 |
| `BlockPos` | `net.minecraft.util.math.BlockPos` | 现代 `net.minecraft.core.BlockPos` façade 只负责转换 |
| `HitResult` / `BlockHitResult` | `RayTraceResult` | 射线类型、命中点和方块面转换 |
| `ClipContext` | `World.rayTraceBlocks(...)` | 将 block/fluid mode 转换为 1.12 的布尔参数 |
| `ItemStack` | `net.minecraft.item.ItemStack` | 物品、NBT、槽位同步通过原生堆栈操作 |
| `CompoundTag` | `NBTTagCompound` | 读写字段名映射，禁止用空壳对象吞掉数据 |
| `FriendlyByteBuf` | `PacketBuffer` | 网络适配器负责 VarInt、资源位置、NBT 和物品编码 |
| `Component` | `ITextComponent` / `TextComponentString` | 仅在显示文本边界转换 |
| `ResourceLocation` | `net.minecraft.util.ResourceLocation` | 保留 namespace:path 语义 |
| `Container` / `AbstractContainerMenu` | `Container` / `GuiContainer` | 菜单同步和点击协议按 1.12 的 windowId/slotClick 实现 |

目标代码中的 `net.minecraft.*` façade 只用于让 common 源码保持稳定；真实运行逻辑必须在 `cc.sirrus.apricityui.compat.cleanroom` 中调用上述 1.12 原生 API。当前 façade 已通过编译探针，实体、网络和容器的运行时实现仍按阶段五继续替换。

## 第一批移植边界

第一批只引入以下能力：

1. Mod 加载、客户端代理和资源读取；
2. 基础 Screen/Overlay 生命周期；
3. 文字、矩形、图片和基础布局；
4. HTML/CSS/JS 页面扫描和打开；
5. 鼠标、键盘和滚轮事件。

容器、网络、KubeJS、WorldWindow、Canvas、Iframe 和高级 shader 进入后续批次。这样可以先验证 1.12 渲染和资源管线，再决定哪些现代功能需要降级。
