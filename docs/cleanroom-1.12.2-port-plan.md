# ApricityUI 移植到 Cleanroom 1.12.2 计划

当前进度：阶段二进行中。已创建移植分支、复制模板、配置 ApricityUI 元数据、接入根工程 target 入口，并复制共享资源基线。

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
3. `cleanroom: add platform bootstrap and resource loading`
4. `cleanroom: port rendering and input adapters`
5. `cleanroom: port containers and networking`
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
