# ApricityUI 移植到 Cleanroom 1.12.2 计划

当前进度：Cleanroom 目标已通过编译、资源处理和 MCP → SRG 重映射打包；客户端服务、资源读取、基础固定管线渲染、GuiScreen 生命周期、SimpleNetworkWrapper 屏幕消息和基础 Container/Slot 菜单已接入。服务端窗口同步、复杂数据源/过滤器、高级滤镜/动态纹理仍待验证和移植。复制的资源（包括 shader 和语言 JSON）目前仅作为基线，不代表全部已兼容 1.12。

### 2026-10-08 构建验证

- 调整代理后，目标 wrapper 的 Gradle 9.8.0 下载完成。
- `targets/cleanroom-1.12.2/gradlew.bat tasks --console plain --no-daemon` 成功，Unimined 1.4.43-kappa 和 Cleanroom 依赖解析成功。
- 在目标目录执行 `gradlew.bat build --console plain --no-daemon` 成功。测试任务为 `NO-SOURCE`，本次仅验证骨架编译和打包。
- 产物：`targets/cleanroom-1.12.2/build/libs/apricityui-1.2.7.jar`。该 JAR 仅包含工程骨架和资源，尚不能提供 AUI 页面功能。
- 代理使用命令进程环境变量及 JVM 的 `http.proxyHost`/`https.proxyHost` 等参数，未写入仓库配置。
- 根构建的 Cleanroom 任务使用目标自己的 wrapper，保证采用模板的 Gradle 9.8，而不是根 wrapper 的 8.14.4。
- Cleanroom 基础服务实现后，`targets/cleanroom-1.12.2/gradlew.bat build --console plain --no-daemon` 成功。
- Forge 1.20.1 回归构建 `buildForge1201` 成功，963 个测试通过；平台窗口服务改为由各目标显式实现，避免 Cleanroom façade 构造器污染 Forge API。

API 差异和每个模块的处理方式见 `docs/cleanroom-1.12.2-api-matrix.md`。

## 目标

在不破坏现有 Forge、Fabric、NeoForge 目标的前提下，为 ApricityUI 增加一个基于 Cleanroom 1.12.2 的独立构建目标。

目标版本和工具链：

- Minecraft：1.12.2
- Cleanroom：0.6.13-alpha
- 映射：MCP stable 39-1.12
- Java：25
- 模板：`D:/Projects/sfr/gradle_ref/CleanroomModTemplate`
- 新目标目录：`targets/cleanroom-1.12.2`
- 工作分支：`cleanroom-1.12.2`

## 当前基线

- 源分支：`snow`
- 基线提交：`6fdcb3be`
- 当前工作区在开始移植前必须保持干净。
- 现有根构建包含 Forge、Fabric、NeoForge 目标，不直接包含 Cleanroom。
- `common` 约有 303 个 Java 文件，其中一部分直接依赖 1.20.x Minecraft API。
- `origin/forge/16.5` 只作为旧 API 和历史实现参考，不作为 1.12 代码基线。

## 分支和提交策略

从 `snow` 创建 `cleanroom-1.12.2`，按阶段提交，便于回滚和审查：

1. `cleanroom: add template-based target skeleton`
2. `cleanroom: lock 1.12 dependencies and metadata`
3. `cleanroom: add platform bootstrap and resource loading`（已完成基础版）
4. `cleanroom: port rendering and input adapters`（已完成基础固定管线版）
5. `cleanroom: port containers and networking`（基础菜单和客户端槽位已完成，服务端同步与数据源待完成）
6. `cleanroom: port KubeJS integration and persistence`
7. `cleanroom: enable optional advanced features`
8. `cleanroom: verify client, server and release jar`

每个阶段都必须能构建，不能把大量未编译代码堆到最后一次提交。

## 阶段一：工程骨架

1. 复制推荐模板到 `targets/cleanroom-1.12.2`，不复制模板的 `.git`。
2. 设置 `gradle.properties`：
   - `mod_id=apricityui`
   - `mod_name=ApricityUI`
   - `root_package=com.sighs`
   - `mod_license=LGPL-2.1`
   - 版本、作者和描述与 AUI 当前元数据一致。
3. 保留模板的 Unimined、Cleanroom、Mixin、AT 配置。
4. 在根 `build.gradle` 增加 `cleanroom-1.12.2` 构建入口。
5. 用模板的空示例验证 `build`、`runClient`、`runServer`。

完成标准：模板工程能独立构建，根工程可以通过 target 入口调用它。

## 阶段二：依赖和 API 清单

1. 确认 Cleanroom 实例中可用的 KubeJS、Rhino、Architectury 和可选渲染优化模组版本。
2. 移除模板自带的无关 JEI 示例依赖，改为锁定实际需要的依赖。
3. 建立 1.12 API 对照表：
   - `PoseStack`、JOML、现代渲染类；
   - `ResourceLocation` 和资源加载；
   - `AbstractContainerScreen`、`Container`、`Slot`；
   - `DeferredRegister`、`BuiltInRegistries`；
   - 现代网络包和 `FriendlyByteBuf`；
   - `SavedData`、现代实体和世界类。
4. 对当前 `common` 和 Forge target 做编译探针，记录不可复用文件。

完成标准：依赖坐标有来源，API 差异按模块登记，后续代码不靠猜版本。

## 阶段三：基础运行和资源

1. 创建 Cleanroom 主类、客户端代理和服务端代理。
2. 将 `mods.toml` 转换为模板的 `mcmod.info`。
3. 接入 `apricityui.mixins.json` 和 `apricityui_at.cfg`。
4. 将 `pack.mcmeta` 调整为 1.12.2 的 pack 格式。
5. 接入 AUI 的 HTML、CSS、JS、字体、图片、语言和主题资源。
6. 实现 1.12 资源加载服务和基础日志。

完成标准：客户端和服务端均能启动，AUI 能被加载，资源可以从 JAR 读取。

## 阶段四：基础 UI、渲染和输入

按依赖顺序移植：

1. 平台服务接口和调度器；
2. 资源、字体和动态纹理；
3. 矩阵、裁剪、矩形、文字和图片绘制；
4. Overlay/Screen 生命周期；
5. 鼠标、键盘、滚轮和焦点；
6. HTML/CSS/JS 页面打开和销毁。

1.12 侧优先使用对应的 `GlStateManager`、`BufferBuilder`、`GuiScreen` 等 API，不能只改包名。

完成标准：能在客户端打开基础 AUI 页面，并验证文字、图片、布局、点击和输入。

## 阶段五：容器、物品和网络

1. 移植 `GuiContainer`、`Container`、`Slot` 和物品渲染。
2. 重写槽位布局、拖拽、快捷移动和物品提示逻辑。
3. 使用 Forge 1.12 网络实现替换现代网络抽象。
4. 重写服务端玩家、实体、方块实体和物品数据访问。
5. 使用 `WorldSavedData` 或 Capability 替换现代持久化实现。
6. 接入 KubeJS/Rhino，并验证依赖缺失时的降级行为。

完成标准：客户端和服务端可以打开容器页面，槽位、物品和网络同步正常。

### 当前进度（2026-10-08）

- 已完成 Cleanroom 1.12 `Container`/`GuiContainer` 桥接、window id 分配和 vanilla 容器初始同步。
- 绑定构建器已从空操作实现改为记录 PLAYER、SAVED_DATA、BLOCK_ENTITY、ENTITY 声明及其参数；参数随打开消息编码。
- 服务端菜单会解析 1.12 `IInventory`：玩家背包使用 `InventoryPlayer`，方块实体和实体使用世界中的真实库存；无显式容量时将实际槽数回传客户端。
- SAVED_DATA 已使用 `WorldSavedData` 保存槽位物品，菜单关闭时仍由 vanilla `Container` 生命周期负责同步和清理。
- 已验证：`targets/cleanroom-1.12.2/gradlew.bat build --console plain --no-daemon` 成功。
- 已执行 `runServer` 探针：FML、Cleanroom 和 AUI 完成初始化，随后只因开发环境未同意 EULA 退出。
- 已执行 `runClient` 探针：OpenGL、OpenAL、资源重载、AUI `ClientProxy` 和 12 个模组均完成加载，未出现启动异常；进程由探针主动终止。
- 资源服务已增加对开发目录和 JAR 内 `assets/apricityui` 的递归枚举，补上 1.12 `IResourceManager` 缺少现代递归 API 的差异。
- 1.12 动态纹理桥已接入 `DynamicTexture`：创建、区域像素上传、纹理管理器注册和释放均可走真实 OpenGL 纹理对象。
- 固定管线渲染桥已接入 1.12 `Framebuffer`：离屏目标、主目标句柄、深度/模板启用、清理、写入绑定、颜色纹理绑定和 `glBlitFramebuffer` 均已实现。
- Cleanroom 已加入轻量 `SLOT` DOM 元素；容器屏幕会按 DOM 几何同步 1.12 `Slot` 的位置、禁用/隐藏状态和显示尺寸，替代固定网格坐标。
- 图片渲染批次已接入 1.12 `TextureManager` + `BufferBuilder`：`TextureKey` 会解析为 `ResourceLocation`，纹理四边形支持 UV、ARGB tint 和批量提交。
- AUI 物品绘制已接入 1.12 `RenderItem`，包括物品图标、数量/覆盖文字和容器外部的物品节点。
- 容器 `canInteractWith` 已加入服务端安全校验：绑定玩家必须是打开者，方块实体和实体绑定必须保持在 8 格范围内。
- 网络范围已收缩为 AUI 实际需要的 1.12 `SimpleNetworkWrapper` 打开屏幕消息和原生 `Container` 同步；common 中的通用 chunk/serialization 工具没有被 UI 调用，继续保持平台隔离，不为它们额外制造传输协议。
- 1.12 `GuiScreen`/`GuiContainer` 已把鼠标按键、释放、拖动、滚轮和键盘字符转发到 common `Operation`，默认字体已通过 1.12 `FontRenderer` 绘制。
- Cleanroom 客户端已实现基于 classpath/JAR 的 `ElementRegister` 注解扫描，并在客户端预初始化阶段注册 AUI 内置元素和扩展元素。

阶段五仍有待完成项：槽位过滤器服务端验证、动态纹理/FBO/shader 的高级路径、可选音频后端，以及 Cleanroom 的真实客户端/服务端 UI 交互 smoke test。

## 阶段六：Mixin、AT 和可选能力

1. 针对 1.12 重新实现容器屏幕、键盘和深度相关 Mixin。
2. 根据 MCP 39 名称重写访问转换器。
3. 逐项验证 shader、Canvas、WorldWindow 和滤镜。
4. WebView/JNI 保持可选；Windows 之外和服务端必须安全禁用。
5. 对 1.12 不具备等价 API 的能力提供显式降级或功能开关。

完成标准：Mixin/AT 在 Cleanroom 中生效；高级能力逐项通过测试，未支持能力不会阻塞启动。

## 验证清单

- `gradlew -p targets/cleanroom-1.12.2 build`
- 根工程的 Cleanroom target 构建入口
- 现有 Forge 1.20.1 target 构建回归
- Cleanroom 客户端启动和基础页面 smoke test
- Cleanroom 服务端启动和无客户端类检查
- 文字、图片、CSS、JS、输入、资源加载
- 容器、槽位、物品渲染、拖拽和网络同步
- KubeJS/Rhino 集成
- 最终 JAR 的 `mcmod.info`、Mixin、AT、服务文件、资源和 DLL

## 风险和处理原则

- 当前代码与 1.12 API 差距大，禁止全局搜索替换式移植。
- 先完成基础 UI，再处理容器和高级渲染，避免所有模块相互阻塞。
- 现有平台代码不直接覆盖；Cleanroom 目标使用独立平台实现。
- 每个阶段单独提交并保持可构建，出现 API 方向错误时可以回退到最近阶段。
