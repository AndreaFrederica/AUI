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

## 第一批移植边界

第一批只引入以下能力：

1. Mod 加载、客户端代理和资源读取；
2. 基础 Screen/Overlay 生命周期；
3. 文字、矩形、图片和基础布局；
4. HTML/CSS/JS 页面扫描和打开；
5. 鼠标、键盘和滚轮事件。

容器、网络、KubeJS、WorldWindow、Canvas、Iframe 和高级 shader 进入后续批次。这样可以先验证 1.12 渲染和资源管线，再决定哪些现代功能需要降级。
