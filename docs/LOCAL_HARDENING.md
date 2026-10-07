# HearText 本地完善与验证

## 范围与依据

2026-10-07 接手基线：`ba7d491`，工作区干净。未发现项目或祖先目录的 AGENTS.md。
以当前代码及运行结果为准；README 和 Cursor 历史提供产品背景。
历史明确取消云端 AI TTS、录音与声音克隆；只保留系统及离线 TTS。
PDF 只阅读；不恢复已取消的滚动阅读模式，不新增社交、支付或产品方向。

仅修改本项目，不发布、部署、推送或触碰生产数据及签名配置。

## 有边界的清单

- [x] 建立可重复本地构建、单元测试、Lint 与模拟器验证入口。
- [ ] 导入与保存：EPUB/TXT/PDF、损坏输入、重复下载、进度和书签保存、删除及重启恢复。
- [ ] 阅读：章节衔接、搜索定位、排版变更、阅读/听书进度一致性及生命周期。
- [ ] 音频：系统/离线切换、暂停恢复、取消与试听停止、焦点和媒体会话、无效语音包。
- [ ] 网络与账号：失败/重试/取消、登录初始化、退出及换号隔离、同步冲突；保持现有接口兼容。
- [ ] 隐私与体验：备份、错误和日志中的敏感信息、权限、无障碍、四种语言及空/错/加载状态。
- [ ] 用有意义的回归测试及尽可能实际运行的核心流程复核；记录未验证项与后端依赖。

每项以具体问题及证据关闭，不以“未看到报错”认定功能完整。

## 初始证据

- PATH 中没有 Java；Android Studio 自带 `C:\Program Files\Android\Android Studio\jbr` 可用于当前进程的 JAVA_HOME。
- 首次检查与新增测试发生源码快照交错，单元测试编译失败；完整重跑已完成 debug APK 与 4 个单元测试（0 失败）。Lint 失败：12 errors、129 warnings、1 hint，不认定最终验证通过。报告：`app/build/reports/lint-results-debug.html`；文本：`app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt`。
- Lint 错误涉及 3 处换行策略常量、3 处 Media3 opt-in、1 处 Context 强转 Activity、5 处 Compose 资源读取；另有应用内语言切换缺少 bundle 语言打包配置警告。需要修复实际原因，不建立隐藏问题的 baseline。
- 下载直接写最终文件，取消和截断可能留下半文件，待修复与测试。
- 书城重复下载构建新 BookEntity，可能覆盖阅读进度及用户封面，待修复与测试。
- 章节缓存分开发布 key/chapters，存在并发读取到其他书内容的窗口。已改为不可变快照一次发布；缓存失效/外部列表修改及四线程 200,000 次交错访问测试通过。
- 自动备份规则为模板，数据库使用破坏性迁移回退，需审查数据保留与隐私边界。
- 离线 TTS 回退到系统后，暂停/恢复仍按配置选择离线引擎，需跟踪实际活动引擎。
- 播放进入末章即保存 100%，并沿用上一章偏移，需修复实际播放进度保存。
- 书城搜索无请求版本或取消保护，旧响应可能覆盖新搜索；需回归乱序响应和分页重试。
- 四种语言资源键齐全，仍存在硬编码提示；不能以资源键齐全代替语言体验验证。
- ADB 当前无设备；已安装 Medium_Phone、Pixel_10_Pro、Pixel_Tablet、Small_Tablet AVD。

## 验证与跨项目依赖

尚未完成。网络验证必须使用本地/模拟服务，不对生产执行写入测试。

### 第二轮实现（进行中）

- 修正 12 项 Lint 错误对应代码，应用内语言切换保留完整语言资源；完整检查确认 0 errors、134 warnings、1 hint，警告仍需按实际影响处理。
- 所有 HearTextApi 请求通过可取消的响应消费封装，取消一直覆盖响应体读取阶段。
- 下载写入同目录随机 `.part`，拒绝空/截断内容，成功后原子替换；失败保留旧文件并清理暂存文件。
- HTTP 异常消息不再附带服务器响应正文，避免 UI/崩溃日志泄露后台诊断或用户内容。
- 为 API 注入 endpoint 和 SessionTokenProvider，使用本机 MockWebServer 验证，不依赖生产账号。
- 初始网络测试发现 MockWebServer 4.12 与现有 Clerk 实际解析的 OkHttp 5.3.2 不兼容；已按 dependencyInsight 证据对齐声明及测试依赖到现用 5.3.2，不改变实际运行时版本。
- 三项下载文件回归已通过；401 刷新一次及第二次 401 终止测试已通过。两项取消测试捕获 SocketException 语义问题，已修复并重跑通过：响应头等待与响应体卡住时均在 2 秒内完成取消，旧文件保留且暂存文件移除。
- `tools/verify-local.ps1` 已完整运行成功（2m19s）：隔离 Debug APK、11 个单元测试全部通过、Lint 0 errors。测试中含原有的一个占位算术测试，不将其计入功能验证证据。
- `-PheartextValidation=true`：仅 debug 添加 `.validation` 包名后缀、空 Clerk key、本机模拟器 host API 地址，跳过 Firebase 插件；正常生产配置、local.properties 和发布签名未修改。仅验证版允许明文 HTTP。README 记录命令与报告位置。
- Medium_Phone（API 26 x86）安装失败：NO_MATCHING_ABIS，与项目支持的 ABI 不匹配；已关闭本轮启动的该设备。改用现有 Small_Tablet（API 35 x86_64），本轮独立启动在 emulator-5582；不操作其他项目设备。
- 设备测试发现访客入口默认书城，导航图标缺少标签；已为三个导航项添加本地化名称、Tab 角色及选中状态，测试已验证切换到书库。
- 设备测试发现横屏书籍详情把“继续阅读”按钮压为零高度；已改为可滚动详情，并用测试检查按钮滚动后可见再点击。阅读、翻页及重建恢复尚待通过。
- Windows 共享 Gradle 进程持有旧编译 JAR 文件锁；不停止其他项目进程。验证版输出独立到 `build/validation-app`，脚本使用 `--no-daemon`，后续报告以该路径为准。
- 横屏详情修复后，Small_Tablet 的阅读设备测试通过：访客入口、书库 Tab 选中、Alice 详情滚动至可见按钮、打开阅读器、等待邻页预加载后翻页、Room 保存位置、Activity recreate 恢复相同位置。测试等待预加载完成，不把首屏就绪误当作邻页已加载。
- TTS 长句分段不超过 120 个 UTF-16 单元，不截断内容、不拆开代理对；3 项 JVM 回归通过，包含 521 字无标点正文和 emoji 边界/定位。
- TTS 跟踪实际活动引擎，离线失败后当前播放会话持续使用系统引擎；暂停、恢复、停止控制实际引擎。设备上的模拟引擎测试通过，离线失败只尝试一次，停止后无新语句。此测试不能替代真实系统/离线音频硬件与模型验证。
- 新增语音错误/回退提示已提供英、中、西、法四种语言，UI 不显示底层异常正文。
- `tools/verify-local.ps1 -Connected -Serial emulator-5582` 完整通过（2m31s）：Debug 构建、14 项 JVM 测试、4 项设备测试、Lint；测试均为 0 failures / 0 errors。报告路径 `build/validation-app/reports/`。后续修改后必须重跑适用验证。

下一批已确认的修复点：Profile 账号删除 UI 立即调用退出，可能在 DELETE 请求取得 token 前清除会话；TXT 分章丢弃首章前言；EPUB 空解析用错误字符串伪装正文；离线 TTS 直接截断超过 120 字的句子。均属于已有功能正确性，不新增产品范围。

只读后端检查（`app/api/v1/progress.py`、`annotations.py`、`app/schemas/__init__.py`）：进度及批注按 `client_updated_at` 合并，支持 `extras`；离线音色包含可选 `checksum_sha256`。客户端目前未使用 extras 保存定位器，也未校验 checksum。无需为这些能力修改后端接口。未对运行服务发请求。
