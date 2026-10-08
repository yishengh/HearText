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
后端 2026-10-07 交接及安卓侧待验证约定见 [ANDROID_BACKEND_INTEGRATION.md](ANDROID_BACKEND_INTEGRATION.md)。公网契约已只读核对，真实账号业务联调未完成。

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

### 第三轮：文档与本地数据（进行中）

- 已保存检查点 `051d906`：隔离验证入口、可取消原子下载、缓存并发修复、横屏详情/导航、TTS 分段/回退；完整脚本通过，Lint 0 errors、130 warnings、1 hint。
- TXT 保留首章前言并支持 UTF-8/UTF-16 BOM，拒绝非法编码/含 NUL 的二进制输入。前言并入第一章，避免增加章节导致旧索引整体移动。
- EPUB 支持 manifest/container/spine 单双引号及属性顺序，保留短段落正文、正确解码十六进制及补充平面数字实体，移除 HTML head 元数据。保留历史主要章节编号，把旧版丢弃的短段落并入相邻章节；仅短章节构成的书正常读取。正文变化仍可能改变当前章节分页，不能把页码当作排版无关的位置。
- EPUB 拒绝无正文文档，限制单 entry 8 MiB、正文合计 64 MiB、spine 10,000 条，避免压缩输入造成无界内存分配。导入总文件限制 64 MiB。
- 导入通过同目录暂存后原子写入；解析/验证失败清理未入库文件和封面；PDF 用 PdfRenderer 校验有效页，TXT 入库前解码并统计章节。导入完成事件先交付本地结果，再尝试云同步。
- 删除本地书籍同时清理批注；删除示例书后记录状态避免重启复活。后者尚需专项设备测试覆盖。
- 本地进度、封面、简介、章节数改为按列更新，进度更新有时间戳条件，避免这些本地操作互相覆盖。云同步仍有整行更新路径，尚不能认定所有并发覆盖风险已关闭。
- 删除账号仅在 DELETE 成功后调用退出；失败保留会话，错误提示四语言一致。未执行真实账号删除；需要后续模拟服务覆盖时序。
- 新增 5 项解析 JVM 测试和 3 项 Room/导入设备测试。首轮设备测试编译发现 PdfDocument 不实现 Closeable，测试改为显式 finally.close。
- 完整重跑 `tools/verify-local.ps1 -Connected -Serial emulator-5582` 通过（1m17s）：Debug 构建、19 项 JVM 测试、7 项设备测试、Lint。设备报告验证三格式导入、无效输入无残留、删除书籍/批注、按列更新和旧时间戳拒绝；阅读重建及语音回退回归也再次通过。账号删除未用真实凭据验证。

### 既定范围内后续批次

1. 实际 TTS 生命周期：系统初始化取消/回调串扰/音量、离线合成取消和写入尾音、音频焦点、试听停止；用可用系统引擎及模拟引擎验证，真实模型缺失时明确限制。
2. 云同步与书城：整行覆盖、账号切换隔离、旧请求覆盖新搜索、重复下载保留用户数据、书签删除与冲突；以本地服务/模拟响应验证。
3. 生命周期/隐私/体验：阅读清理和排版后定位、Room 迁移保留、备份排除账号敏感数据、剩余无障碍及四语言错误状态。每项只围绕已存在功能，不扩大产品方向。

### 第四轮：音频生命周期

- 系统语音使用可取消的初始化等待与请求代次；stop、取消和 shutdown 会使等待请求失效，播放 ID 隔离旧回调。初始化期间暂停也不会偷偷开始播放。异步错误及入队拒绝会进入错误状态；恢复失败不再从 UI 调用栈抛出崩溃。
- 添加 Android 11+ 的 TTS service 查询声明；优先同语言的本地系统音色。验证版拒绝仅网络音色，设备测试不会把朗读文字发送给网络音色。系统音量作为每次朗读的参数；语音焦点 duck 采用暂停/恢复，避免回退后按配置引擎控制错误对象。
- 音频焦点回调更新实际持有状态，回调固定主线程；不留下无法恢复的 delayed focus 请求，失焦后也可正确 abandon。
- 离线合成/模型加载/PCM 写入转到后台；按请求代次丢弃已停止的合成结果，释放模型等待正在运行的 native 调用退出。原生 generate 无安全中断 API，停止不会强行释放正在使用的模型，返回后不播放旧结果。
- PCM 使用非阻塞写入、按实际写入样本数推进、可取消暂停等待，最后等待 playback head 消费完缓冲区再释放。写入错误/长时间停滞明确失败，不再当作成功读完。
- 播放不再自动下载共享 espeak 资源；安装阶段负责准备。App 播放协调改为主线程状态更新，重工作留在后台。
- 自然完成用独立事件传递；普通 stop/跳转产生的 Idle 不再推进下一章。只有末章真正完成才保存 100%；切换章节清零旧章页偏移。句内进度与排版无关定位仍待下一批完善。
- 新增系统引擎 5 项 JVM 竞态/失败测试和 PCM 4 项测试；首轮发现 JUnit 测试返回类型错误，已修复并重跑。
- `tools/verify-local.ps1 -Connected -Serial emulator-5582` 完整通过（1m22s）：28 项 JVM 测试、10 项设备测试，均 0 failures / 0 skipped。设备新增验证：本地系统音色朗读自然完成、暂停/恢复/停止；真实 AudioTrack 的 250ms PCM 全部消费后返回；stop 不发章节完成事件。模拟器以 no-audio 启动，此证据证明原生播放路径和状态，不代表人耳音质验收。
- 尚未验证真实 sherpa 离线模型合成、实际电话/其他 App 抢焦点、锁屏媒体按钮。音色安装的校验/事务替换、试听取消和未经请求的 Profile 资源下载仍需修复。

### 第五轮：音色包与试听

- 安装先暂存、校验、受限解压、验证模型与资源后再替换目录；替换有回滚副本和重启恢复入口。失败/取消清理 staging、zip、part，保留旧音色。按包串行安装，ID 拒绝路径字符/保留目录形式。
- 读取 API 已有的可选 `checksum_sha256` 并校验；兼容未返回该字段的旧响应。ZIP/TAR 拒绝路径逃逸，TAR 拒绝链接和特殊文件，限制文件数与解压大小。共享 espeak 下载也可取消并采用暂存替换。
- ONNX 预检改为读取 protobuf ModelProto 结构和 metadata，不再对整个文件搜索单个字符串；检查 sherpa VITS 所需的 sample_rate、n_speakers、language、comment 和数值字段。依据：[sherpa-onnx v1.13.4 源码](https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.4/sherpa-onnx/csrc/offline-tts-vits-model.cc)、[ONNX ModelProto](https://github.com/onnx/onnx/blob/main/onnx/onnx.proto)。这不是完整 ONNX 图验证，后端仍应只供应经过 ONNX 校验及实际合成验证的模型。
- 停止试听取消 ViewModel 加载协程并使 repository 请求代次失效，下载/prepare 迟到结果不再启动 MediaPlayer；旧播放器回调不能清除新播放器。页面原有 ON_STOP/onDispose 均调用此停止路径。
- Profile 初始化/刷新/选择音色不再自动联网下载 espeak；安装才准备资源。已安装音色扫描移至 IO。下载有可见取消按钮和安装阶段状态，失败提示本地化。
- 已安装同 ID 音色替换后，离线引擎比较模型文件长度/修改时间，下一次播放重新加载模型。
- 新增 `tools/prepare-offline-fixture.py` 与 `verify-local.ps1 -OfflineFixture`：从官方获取 Amy 模型，仅本地 loopback 服务提供测试包，校验后装入独立测试私有目录。固定测试文本不涉及用户文档，未对生产服务发写请求。
- 官方压缩包 SHA-256：`c70f5284a09a7fd4ed203b39b2ff51cac1432b422b852eb647b481dade3cf639`；本次测试 ZIP：`d4887d3ce241089598891e0cd3d0525ab2bb485f50a346dbd52db78826cd9e83`，67,304,989 bytes。生成信息存 `build/local-validation/voice-fixtures/provenance.json`，大文件不入 Git。
- 完整命令 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（1m3s）：33 项 JVM、14 项设备测试全部通过，无跳过；Lint 0 errors、133 warnings、1 hint。涵盖真实离线模型安装、合成自然完成、暂停/恢复/停止，以及坏包保留旧文件、取消清理、试听停止后无迟到播放。未加该开关时真实模型测试明确 skipped，不能当作模型验证通过。
- 跨项目要求：后端 `checksum_sha256` 应对应实际下载 ZIP 的精确字节；应校验原始 ONNX 图和 sherpa 必需元数据并做合成 smoke test，避免 native 对不合法图的不可恢复错误。客户端本轮已验证官方兼容模型，没有修改后端。

### 第六轮：请求会话与书城搜索

- API 请求捕获用户/会话标识，在取得 token 前后、读取响应后、401 重试前校验；下载每个写入检查点及原子替换前也校验。换号后的旧响应被取消，不能使用新会话重试旧请求或覆盖下载文件。此检查不能撤回服务器已经处理的请求；数据库行的账号归属及多请求同步事务仍待下一批处理，不能据此认定换号隔离全部完成。
- 书城搜索提取可测试的分页状态，提交新搜索立即取消旧任务并使用请求代次阻止迟到结果/清理覆盖新状态。分页固定已提交的查询和分类，不读取尚未提交的输入；失败保留已加载项目、允许原页重试，重复点击不重复请求。
- 首页分区刷新与搜索独立，分区失败不阻止搜索；搜索失败显示已有四语言资源，避免直接显示服务器/异常细节。
- 新增 4 项 JVM 回归：下载中换号保留原文件；401 返回前换号不重试；不可取消来源的迟到结果不覆盖新搜索；分页参数稳定、失败重试和连续点击去重。
- `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 完整通过（1m2s）：Debug 构建、37 项 JVM、14 项设备测试全部通过，0 failures / 0 skipped；Lint 完成。真实系统/离线语音、阅读重建与导入回归再次通过。未调用生产写接口，未发布或推送。

### 第七轮：书籍云同步与迁移保留

- 数据库升级到 v8，为远端书籍记录已验证的账号归属。新本地书籍可按原有行为同步；已有远端 ID 的旧书必须在当前账号的服务器列表中得到确认，才赋予归属。其他账号不上传其阅读进度，也不执行其远端删除。本地书籍仍保留并可离线阅读，未改变产品为账号隔离的本地书库。
- 注册按互斥锁串行，并在锁内重新读库；远端列表失败直接失败，不再误判为空列表后创建。云同步仅更新远端关联和封面字段，进度用数据库内的账号与严格较新时间戳条件更新；不再把网络等待之前的整行快照写回。无有效时间戳的未读书不制造“当前时间”的进度，远端新进度到来会清除旧 Readium locator。
- 会话期望通过协程上下文传递至 API，避免同步中下一次网络调用误用新账号。远端封面使用独立临时文件身份，再按原路径与非 USER 来源条件提交，避免同步下载直接覆盖用户封面文件。
- 移除 Room 的破坏性迁移回退；保留 v1 至 v8 的完整迁移链。新增真实 SQLite/Room 测试，从 v1 和 v7 升级并通过 Room schema 校验，验证进度、路径、远端关联、locator 和批注保留。未把旧库未经确认的远端书归给当前账号。
- 新增 4 项模拟服务/Room 集成测试，覆盖延迟注册期间本地进度/简介/封面修改、503 不创建、并发只创建一次、其他账号不上传或删除、旧时间戳不能覆盖，以及旧 ID 需服务端证据。
- `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 完整通过（1m13s）：Debug 构建、37 项 JVM、19 项设备测试全部通过，0 failures / 0 skipped；Lint 完成。测试仅访问本地模拟响应及本地模型服务。
- 仍待完成：书城重复下载的数据保留与账号冲突、批注归属/删除重试、书籍离线删除的持久重试、登录时同步任务切换；上述书籍同步测试不代表这些路径已经完成。

### 第八轮：书城下载与取消

- 下载使用 UUID 文件名，服务器书籍 ID 不再作为文件路径。最大文件与本地导入一致为 64 MiB；完整下载和 EPUB 解析成功后才调用上架接口，损坏文件不会变成可打开的本地书籍。
- 下载成功后以 Room 事务重新读取当前书籍，再替换内容文件和远端关联；保留进度、locator、添加时间、用户封面、简介和批注。不同账号的相同 client_book_id 选择独立本地 ID，不覆盖原书籍或其文件。
- 取消、解析失败、会话切换清理未提交的内容与封面；一旦开始本地事务则完成入库及保留标记，避免已入库文件被取消清理误删。只清理 catalog 目录中无数据库引用的旧内容文件，清理失败不把已提交下载报告为失败。
- 详情页阻止重复点击创建并行任务，增加已有四语言“取消”按钮；失败使用本地化提示，取消不显示下载失败。取消不能撤回服务器已经处理的上架请求，重试沿用现有上架接口。
- 新增模拟服务/Room 测试覆盖重复下载保留用户数据、账号同 ID 冲突、损坏输入在上架前拒绝、下载中取消清理，以及等待上架响应时换号不入库。首轮完整验证已通过（37 JVM / 22 设备），补充换号测试后再次执行完整验证。
- 补充测试后的第一次复跑因模拟器进程退出而中断：ADB 报 device offline/not found，预期 23 项设备测试只收集到 14 项完整结果。已确认 ADB 无设备且无 qemu 进程，重新启动本任务 Small_Tablet（5582）并等待开机后重跑；未将中断轮次算作通过。
- 恢复后阅读 smoke test 在打开详情前超时（LocalReaderSmokeTest:37），其按“Alice”文字选首个节点的定位不区分切页中的首页/书库。增加书库卡片专用 testTag，测试明确等待并点击书库节点；保留失败时语义树诊断，未放宽等待条件或跳过阅读验证。
- 最终完整命令 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（1m2s）：Debug、Lint、37 项 JVM、23 项设备测试，0 failures / 0 skipped。阅读打开/翻页/重建、真实系统及离线语音均再次通过。

### 第九轮：批注同步与删除重试

- 数据库 v9 增加批注远端归属、删除及删除确认标记。删除立即从用户列表移除并清除正文/选中文字，保留必要的同步标识；失败在后续同步中重试，404 视作已删除。迟到的创建响应只绑定远端 ID，不能恢复旧内容或已删除批注。
- 打开书籍与登录同步会拉取批注并补发尚未成功创建的本地批注；重试复用 client_annotation_id。互斥锁串行网络同步，本地删除仍可立即生效。书籍账号归属与期望会话校验共同限制远端操作，未经确认的旧归属不盲目执行删除。
- 合并使用 Instant 完整时间戳（包括毫秒与时区）；数据库事务按较新版本更新，保留本地删除标记，并验证父书籍仍存在且归属匹配，避免网络返回后复活孤立批注。远端完整列表中消失的已绑定批注会在本地隐藏，不重新上传。
- 只读核对关联后端 `app/api/v1/annotations.py`：GET 返回书籍完整列表、POST 按账号/client_annotation_id 幂等 LWW、DELETE 缺失项返回 404。未修改或调用生产后端。若以后接口改为分页，客户端“远端缺失即删除”的处理必须同步改为完整分页快照。
- 新增设备测试覆盖删除失败重试、创建中删除、毫秒冲突、其他账号不得远端操作、远端删除传播、创建失败使用同一客户端 ID 重试。迁移验证扩展为 v1/v7/v8 → v9，保留书籍与批注。
- 首轮完整构建/Lint/37 JVM/28 设备测试通过；追加删除内容清理和创建重试测试后重跑最终验证。
- 最终 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（1m3s）：37 项 JVM、29 项设备测试全部通过，0 failures / 0 skipped，Debug 构建与 Lint 通过。书籍离线删除重试和登录会话任务管理仍属于下一批，未把本轮批注测试当作这些路径的验证。

### 第十轮：登录状态与会话任务

- 登录监听使用已初始化状态、用户、会话 ID 和初始化错误；已有会话启动会执行同步，用户资料变化不重复启动。换号、退出或进入离线模式会取消旧同步任务。离线模式抑制自动登录同步；示例书初始化也不再自行注册远端书籍，统一由登录同步处理。
- SDK 初始化失败提供重试与离线入口，正在连接时也可继续离线阅读。获取 token 的初始化等待在错误或 15 秒超时后结束，不无限悬挂。这里不声称完全禁用 SDK 自身网络或所有封面加载；本轮针对自动书籍同步和授权请求等待。
- 抽出 Clerk SDK 边界及可模拟的表单状态处理。网络异常、SDK 失败和取消都释放 busy；连续点击不会创建重复请求。只有验证完成且 setActive 成功才进入完成状态，不再把注册的未完成响应或激活失败当作完成。增加注册验证码重发。
- 错误使用 en/zh/fr/es 资源，未显示或记录 SDK 原始异常；退出失败保留会话并显示提示。首页使用用户名/姓名，移除内部 Clerk 用户 ID 的展示。登录表单可滚动并适应键盘与底部系统栏。
- 依据本地已安装 Clerk Android 1.0.10 AAR 的公开签名核对 sessionFlow、initializationError、reinitialize 和注册邮箱字段；不升级 SDK，不执行真实注册、登录或删除账号写入测试。
- 新增模拟 ViewModel/协程测试覆盖已有会话启动、换号/退出取消、资料变更不重复同步、退出失败、初始化错误、表单异常恢复、重复提交、取消、未完成验证、离线模式抑制同步及 token 等待失败/超时。真实 Clerk 测试实例凭据尚未提供，SDK 与服务端完整认证链路仍未实测。
- 最终完整命令 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（59s）：Debug 构建、Lint、45 项 JVM、29 项设备测试，0 failures / 0 skipped。新增 coroutines-test 使用项目现有协程版本 1.10.2，仅测试依赖。

### 第十一轮：系统备份边界

- 默认备份模板没有排除私有数据。现将 allowBackup 关闭，并在旧版 full-backup-content、Android 12+ 的 cloud-backup/device-transfer 中显式排除全部凭据保护及设备保护的 root/file/database/sharedpref 域与 external 域，覆盖账号状态、正文、批注、离线模型和待同步状态。
- 依据 [Android Auto Backup 官方文档](https://developer.android.com/identity/data/autobackup)：默认包含多数应用文件，部分设备的 D2D 不完全依赖 allowBackup，因此同时配置设备迁移排除项。应用已有的账号云同步不受这些系统规则影响；本地导入文件不会通过系统备份恢复，卸载/清除应用数据前需要保留原文件。
- 设备测试检查实际安装包的 ApplicationInfo 标志和编译后的两套 XML 规则。未触发真实云备份，未执行跨设备迁移；不同厂商迁移实现的实际遵守情况未验证，不能把规则检查当作所有 OEM 的端到端迁移测试。
- 完整命令 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（52s）：Debug、Lint、45 项 JVM、30 项设备测试，0 failures / 0 skipped。

### 第十二轮：阅读页面退出与资源释放

- 删除 onCleared 中向已取消 viewModelScope 提交保存/关闭任务的路径。阅读事件立即截取章节、页码和单调递增的本地时间戳，交给应用作用域完成保存；页面销毁不取消已经发生的事件，迟到的旧写入不能凭执行时间覆盖新位置。
- 保存默认页码使用内存中的最后已知位置，不依赖可能尚未更新的 Room Flow。读取失败不会标记 sessionReady，也不重置旧进度。PDF 仅由 locator 事件保存位置，通用返回按钮的文本页码保存不会覆盖 PDF 进度。保存失败有四语言提示。
- PDF publication 按具体 session 实例释放，共享使用者计数归零才关闭；旧实例的迟到关闭不能关闭同 ID 的新实例。应用作用域负责页面销毁后的关闭，后台听书继续保持原有生命周期。
- 新增设备测试用阻塞写入队列制造“发出翻页事件后立即销毁 ViewModel”的时序，验证最后位置保存和旧时间戳拒绝；使用真实 PdfDocument/Readium/Pdfium 验证退出释放、共享引用和旧实例关闭，并验证损坏输入与 PDF 返回不会重置位置。
- 首轮因测试 tearDown 返回类型不是 Unit 被 JUnit 拒绝，修复后 33 项设备测试通过；补充损坏输入及 PDF 位置断言后再次完整验证。尚未以本轮测试证明字体排版后的字符锚点、句内听书位置或 PDF 导航器重建定位，这些仍待专项处理。
- 最终 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（55s）：Debug、Lint、45 项 JVM、34 项设备测试，0 failures / 0 skipped。

### 第十三轮：分页重排保持文字位置

- 原来仅字号变化捕获字符位置，现对行距、字距、字体、段间距、边距和窗口尺寸等所有重排保留当前页文字锚点；显式章节/页码跳转不沿用上一页锚点。窗口 onLayout 使用当前槽位章节/页码，避免返回首次打开位置。
- 强制重排会真正失效并重新加载三个槽位，修复 loadSlot 遇到已加载页直接返回的问题。待用字符锚点携带章节归属，新的显式跳转清除旧锚点；超出当前正文长度的字符定位落在末尾，避免回到第一页。
- 新增真实 Android 分页测试覆盖无字号变化的字体/行距重排、跳转其他章节后缩小窗口、强制重排、越界字符和连续跳转。断言原文字仍位于当前页，未只检查页码相同。
- 完整命令 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（57s）：Debug、Lint、45 项 JVM、35 项设备测试，0 failures / 0 skipped。未发布或访问生产写接口。
- 本轮针对打开页面期间的重排。退出重开后的字符定位、书签跨排版定位及 PDF 导航器重建仍需单独完成，未由本轮测试推定通过。

### 第十四轮：PDF 位置监听、页码修复与旧位置迁移

- 移除等待 300 ms 后只查找一次 Fragment 的监听方式，改为注册 Fragment 生命周期回调，连接现有及随后创建的阅读器；离开页面时取消订阅并注销回调。位置流保留最近一项，晚订阅也能得到当前页。Compose 使用最新回调，容器 ID 可随保存状态恢复。
- Host 保存当前 locator 到 Fragment 状态，重建时优先恢复；销毁 view 时清除 navigator 与方向导航安装标记。真实三页 PDF 测试覆盖延迟 500 ms 创建、导航、晚订阅和保存/重建，同时核对底层渲染器实际页码。
- 真实测试发现 Readium 3.3.0 Pdfium 页码转换错误；本地 AAR 字节码及设备 locator 日志与上游 [问题 #811](https://github.com/readium/kotlin-toolkit/issues/811) 一致。升级到包含修复的 [3.4.0](https://github.com/readium/kotlin-toolkit/releases/tag/3.4.0)，明确使用水平分页。未在应用中加入临时页码加减补偿。
- 依照 [上游迁移说明](https://github.com/readium/kotlin-toolkit/blob/3.4.0/docs/migration-guide.md)，旧 PDF locator 经 Publication.migrateLegacyPdfiumLocator 校正；新保存值携带 heartextPdfiumLocatorVersion=1，重复打开不再次迁移。测试从旧值通过真实仓库打开，验证实际恢复第二页、新编码重复恢复不变，再导航到第三页。旧版未曾记录的最后一页不能凭现有数据重建；迁移只校正已有记录。
- 新依赖的 Kotlin 2.4 元数据要求升级 Kotlin/Compose 编译插件至 2.4.20、KSP 至 2.3.10；按 [AGP 官方方式](https://developer.android.com/build/releases/agp-9-0-0-release-notes#upgrade-to-a-higher-kgp-version) 显式对齐内置 Kotlin。SDK 目标、minSdk、签名身份及发布配置未修改。迁移或构造被取消/失败时关闭 publication。
- 验证过程中修复了测试资源关闭/Unit 返回类型、locator 中不一致的页码片段，以及渲染尚未就绪时过早导航/断言的问题。最终等待条件同时检查 locator 与渲染器页码，不放宽位置要求。先前失败轮次不计作通过。
- 最终 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（51s）：Debug、Lint、45 项 JVM、36 项设备测试，0 failures / 0 skipped；真实系统/离线语音及数据库迁移回归通过。完整日志位于 build/local-validation/full-pdf-verification.log。
- 尚未证明系统杀进程后的完整页面恢复；无 session 时的已恢复 Fragment、共享 session 的最新初始位置仍需处理。正文字符锚点、书签跨排版定位及书籍离线删除重试也仍在既定清单中。没有发布、推送或生产写入。

### 第十五轮：publication 尚未打开时的 PDF 恢复

- 恢复 Host 时若仓库尚无 publication，仅反序列化无行为的子 Fragment 占位，随后清除；不创建需要真实文档的 Pdfium 导航器。真实恢复时序测试证明原 PdfNavigatorFragment dummy factory 会在 onViewCreated 中因缺少文档子 Fragment 而空指针，单纯在创建后移除不足以防止崩溃，现已移除这条 dummy 导航器路径。
- Host 增加幂等的 bindAvailableSession；Compose 已有 Host 在 session 就绪后重新绑定真实 navigator、方向输入和位置流。等待期间再次保存状态仍保留之前的 locator，避免尚未得到 navigator 时丢掉恢复点。
- 仓库复用 publication 时重新解析本次打开的 locator，校验取消后才更新初始位置并增加引用；已有导航器不被强行跳转，新打开者不再沿用首次打开时的旧位置。引用计数与实例身份关闭规则保持不变。
- 增强真实三页 PDF 测试：保存第三页，释放 publication，先恢复 Host 并验证尚不可绑定，再保存/恢复一次等待状态，然后用较旧的第二页持久位置重开 publication；最终仍恢复 Fragment 快照中的第三页，同时验证渲染器实际页码。重复绑定不重建有效导航器。共享 publication 测试验证新的初始位置进入复用实例。
- 原占位恢复崩溃已由专项测试复现；修复后的专项通过。最终 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（1m7s）：Debug、Lint、45 项 JVM、36 项设备测试，0 failures / 0 skipped。日志：build/local-validation/full-recovery-verification.log。
- 本轮通过真实 Fragment 保存状态和主动释放 publication 制造恢复顺序，不等同于操作系统实际杀进程的完整 UI 验证。仍需检查 Compose 阅读容器恢复后的绑定：程序创建的 FragmentContainerView 不经过 XML 构造中的 onContainerAvailable，现有 Fragment 视图重新挂载需进一步实测。正文字符锚点、书签定位及书籍删除重试仍属既定待办。未发布或生产写入。

### 第十六轮：正文日志与听书非致命异常诊断

- 删除 ReadView、PageContentView 中直接输出选中文字的三处日志，以及书内链接日志。页面加载和离线语音 token 生成失败只记录异常类型，不再输出原始异常消息/堆栈中的文件路径或解析内容。保留不含正文的分页与音频状态信息。
- 听书非致命异常报告使用新的安全副本：仅保留异常类型和代码调用栈，丢弃原消息、cause 与 suppressed 异常。附加字段只允许固定 system/offline 引擎及 system/none 回退状态；调用方也不再传入 voice ID。移除未使用的任意日志/任意键包装接口，debug 构建不执行这条非致命上报路径；手动测试崩溃方法增加 debug 检查，本阶段未调用它。
- 新增 JVM 测试把私人段落和文件路径放入消息、嵌套原因、suppressed 异常及任意字段，验证安全副本中不含这些内容，调用栈仍保留，非法引擎值被拒绝。没有向 Crashlytics 发送报告；这不是对第三方 SDK 全部自动日志或自动致命崩溃报告的无敏感数据保证。
- `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（1m24s）：Debug、Lint、46 项 JVM、36 项设备测试，0 failures / 0 skipped。随后仅清理日志删除后遗留的无用解构变量和调用方 voice ID 字段，再执行 `tools/verify-local.ps1`（1m45s），Debug/JVM/Lint 通过；这两处清理后没有重复设备测试。日志分别为 build/local-validation/privacy-verification.log 与 privacy-final-build.log。
- 项目源码复查已无选中文字/书内链接日志和直接 recordException(throwable) 调用。尚未完成的阅读容器恢复、字符锚点、书签和离线删除重试保持原范围；未发布、推送或执行生产写入。

### 第十七轮：共享离线语音资源备份恢复

- 共享 espeak-ng-data 路径在检查可用性前执行与语音包相同的同步备份恢复。此前只有单个语音目录恢复，若共享目录替换中断而只留下 .espeak-ng-data.backup，解析语音会误判不可用，ensureSharedEspeakNgData 还会尝试下载。
- 扩展现有真实官方模型测试：将有效数据目录改名为共享备份，验证 resolvePack 恢复共享目录、识别模型可播放；再次制造备份状态，验证 ensureSharedEspeakNgData 在本地恢复，然后使用恢复后的真实资源合成并执行暂停/恢复/停止。没有用空模型或仅检查目录存在来代替合成验证。
- 完整命令 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（1m9s）：Debug、Lint、46 项 JVM、36 项设备测试，0 failures / 0 skipped。日志：build/local-validation/shared-voice-recovery.log。共享资源恢复遗漏已关闭；既定其他待办保持不变。未发布、推送或生产写入。

### 第十八轮：字体导入与测试隔离

- 字体先写入限量 64 MiB 暂存文件，检查取消、空文件及 Android Typeface 能否加载，再原子提交。失败不替换现有字体设置，显示已有四语言导入失败提示；目录创建失败也返回可处理的失败结果。
- 文件路径使用内容 SHA-256，相同字体复用路径，不同字体使 Typeface/分页缓存失效。保留原有字体文件与旧路径兼容，尚未自动清理历史字体文件。
- 新设备测试加载两种真实字体，覆盖路径变化、重复导入、无效/空输入、取消、目录不可写及旧文件保持完整。
- 回归首轮发现 PDF 生命周期测试错误地把 publication 关闭当作独立 Room 保存完成；改为同时等待持久化任务结束后断言。下一轮模拟器退出导致测试中断，重启本任务模拟器后发现存储测试共用 library_state 偏好设置，污染示例初始化标记；现隔离并清理测试偏好设置。没有放宽功能断言；失败轮次不计作通过。
- 最终 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（49s）：Debug、Lint、46 项 JVM、37 项设备测试，0 failures / 0 skipped。日志：build/local-validation/font-isolated-verification.log。字体导入及阅读重建回归通过。
- 只读接收后端部署交接并读取公网 OpenAPI；记录 Retry-After 缺失和真实账号验证边界。其他既定待办仍未关闭；没有生产写入、发布或推送。


### 第十九轮：后端 Retry-After 冷却

- 认证 JSON、可选认证 JSON 与流式下载统一读取 429/503 的 Retry-After。冷却期间新调用立即返回带剩余毫秒数的 ApiHttpException，不保存服务器正文、不自动排队重试写操作。429 缺失或无效头默认冷却 1 秒；503 无头仍按原行为报告暂时不可用。
- 按 [RFC 9110](https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3) 支持秒数和 RFC 1123 日期；期限使用单调时钟，较短的后续响应不能提前解除已有冷却。状态共享于同一 API 实例，重启后不保留，不声称覆盖进程重启或已经发出的并发请求。
- 新增 3 项 JVM 测试覆盖时间格式/溢出/无效值、系统时钟变化与较短冷却，以及本地 429 后连续下载只发出一次请求、不刷新认证、不改变旧文件。已有取消与账号切换测试继续通过。
- `tools/verify-local.ps1` 通过（1m9s）：Debug、Lint、49 项 JVM 测试。该轮未重跑设备测试；上一轮 37 项设备结果不能当作本次请求层修改后的设备验证。日志：build/local-validation/retry-after-verification.log。
- 后续仍需完成既定同步删除、阅读定位及体验清单，并检查底层 HTTP 自动重试语义与完整设备回归。没有生产写入或发布。


### 第二十轮：阻止底层 HTTP 自动重发写请求

- 核对 [OkHttp 5.3.2 官方实现](https://raw.githubusercontent.com/square/okhttp/parent-5.3.2/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/http/RetryAndFollowUpInterceptor.kt)：503 携带 Retry-After: 0 会自动重发，且这一分支不检查 retryOnConnectionFailure；超大数字还可能在其整数转换处抛异常。
- 将 429/503 处理移至 network interceptor，在 OkHttp 自动 follow-up 前关闭响应并返回安全异常，同时关闭连接失败/408 自动恢复。应用显式的一次 401 刷新保留，正常下载重定向保留。网络失败直接交给现有本地保留与用户重试流程，不隐式重放可能已经提交的写操作。
- 新增本地 MockWebServer 测试：503/0 和 408 的 PATCH 都只收到一次请求，超大 Retry-After 仍返回受控异常且保留旧下载。原有 401、取消、换号与限流回归通过。
- 完整 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（1m5s）：Debug、Lint、52 项 JVM、37 项设备测试，0 failures / 0 skipped。真实系统/离线语音、阅读重建及同步回归均包含在本轮设备运行中。日志：build/local-validation/http-replay-verification.log。
- 此次结果关闭底层 HTTP 自动重试检查项，不代表既定书籍删除重试、持久字符位置和全局体验审查已完成。未发布、推送或访问生产写接口。


### 第二十一轮：本地文字位置持久化

- TXT/EPUB 翻页保存独立版本标记的章节字符偏移到现有 locatorJson，同时保留旧页码字段。新会话从数据库恢复字符位置；无标记、未知版本或无效字符值仍走原页码回退，不将 PDF locator 当成文字位置。章节/页码显式改变时丢弃不匹配的旧字符位置。
- ReadView 首次排版前接收字符定位，窗口尚无尺寸时保留待定位状态。Compose 不再在当前页尚未加载时强行用页码跳转，避免清掉初始字符定位。正常重排继续保留当前文字。
- 真实阅读冒烟测试在保存位置后修改字号至 1.8 倍并重建 Activity，断言原字符仍在当前页；生命周期测试销毁旧 ViewModel 后从 Room 创建新会话，验证恢复字符位置及旧时间戳不能覆盖新位置，并检查旧/未知版本回退。
- 实现后的首轮完整验证通过；补充新会话断言后发生模拟器进程退出，重启后一次示例书可见性超时。该超时发生在阅读器打开前，尚未确认根因，不能归因于字符定位；已加入只记录数据库条目存在与初始化标记的失败诊断。最终重跑通过，不将前两次失败记作通过。
- 最终 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（50s）：Debug、Lint、52 项 JVM、37 项设备测试，0 failures / 0 skipped。日志：build/local-validation/text-position-confirm.log。模拟器本轮恢复采用软件渲染；没有证据断言此前进程退出的原因。
- 本轮覆盖本地文字进度、数据库新会话恢复和 Activity 重建。尚未实测完整 OS 杀进程恢复；书签跨排版定位、听书句内位置、云端字符同步和示例初始化间歇问题仍待完成。没有生产写入、发布或推送。


### 第二十二轮：示例书初始化并发与残缺文件

- 同一仓库的示例初始化使用 Mutex 串行处理；复制内置 EPUB 使用原子暂存，取消向上传递，不再将“文件存在”当作复制完整的证据。尚无数据库记录时重新从内置资源复制，并要求实际解析成功才创建记录。
- 示例记录改用 INSERT IGNORE，避免初始化与其他入口交错时覆盖已存在的阅读进度。已有记录直接保留，用户删除后的初始化标记仍阻止自动重新添加。
- 新设备测试先写入残缺 EPUB，再启动八个并发初始化入口，验证只有一本可实际解析的示例、已有进度不被重置，以及删除后重复初始化不会恢复示例。
- 完整 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（1m8s）：Debug、Lint、52 项 JVM、38 项设备测试，0 failures / 0 skipped。日志：build/local-validation/sample-initialization.log。
- 本轮关闭明确的半文件与初始化并发风险；此前示例可见性超时没有取得失败时数据库/初始化标记证据，尚不能证明就是由这两项造成。保留失败诊断并在后续完整回归继续观察；不以本次通过宣称已找到那次超时的根因。


### 第二十三轮：封面输入与取消安全

- 网络、用户选择和 EPUB 图片统一写入随机暂存文件，限制输入 16 MiB、单边 16384 像素及 4000 万总像素，检查可实际解码后才原子替换。验证按采样缩小解码，不为验证分配完整大图；失败和取消保留旧封面并清理暂存。
- 网络封面通过既有 consumeCancellable 处理，取消覆盖等待响应头及读取响应体。取消异常继续向上传递，不再被 runCatching 转成普通失败。生成封面也使用原子提交并确保 Bitmap 回收。
- 新文件名取书籍 ID 的 SHA-256，不将外部 ID 当作路径；旧数据库保存的封面路径仍可读取。修复标题哈希为 Int.MIN_VALUE 时生成封面颜色索引可能为负的问题。
- 新设备测试用真实生成图片验证有效替换、无效/超限输入、目录穿越形式 ID、响应头及慢响应体取消，检查旧图字节不变且无暂存残留。
- 完整 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（1m8s）：Debug、Lint、52 项 JVM、39 项设备测试，0 failures / 0 skipped。日志：build/local-validation/cover-verification.log。
- 本轮针对输入、文件与网络安全；用户换图后的图片缓存失效、保存中删除书籍的时序及失败提示仍需结合现有封面 UI 完成，不由存储层测试推定通过。未发布或执行生产写入。


### 第二十四轮：用户换图提交与错误状态

- 用户每次选择封面使用独立路径，使数据库观察者和图片加载器得到新地址，避免原路径缓存显示旧图。准备图片后以书籍文件、创建时间及旧封面路径做条件更新，保存中删除或改变原记录时拒绝迟到提交并清理本次新图，不重建已删除书籍。
- 提交和结果标记在不可取消的短数据库阶段完成，避免取消后把已经引用的新图作为失败文件删除。旧封面暂不自动清理，避免影响仍使用旧路径的界面；历史无引用封面清理仍需配合存储清理审查。
- 详情页换图时禁用重复选择并显示进度；失败显示英、中、法、西四语言提示，不展示底层异常。封面点击目标增加按钮角色和换图动作标签。
- 新设备测试覆盖两次换图地址变化、损坏图片不改变数据库，以及图片准备后数据库删除书籍时拒绝保存且无新增文件残留。尚未通过系统图片选择器进行人工操作，UI 文案/状态的编译和 Lint 不等同于完整人工体验验证。
- 完整 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（1m24s）：Debug、Lint、52 项 JVM、40 项设备测试，0 failures / 0 skipped。日志：build/local-validation/user-cover-verification.log。未发布、推送或执行生产写入。


### 第二十五轮：书签跨排版定位与兼容同步

- 数据库升级 v10，annotations 增加可空 locatorJson，并提供 9→10 迁移；迁移设备测试覆盖 1、7、8、9 版本保留数据。新文字书签同时保留原页码字段与版本化字符位置，旧书签继续按页码，不臆测其原字符位置。
- 通过现有 extras.heartext_text_position 同步字符位置，后端只读检查确认支持 extras，无后端改动。模拟服务验证新建请求字段、GET 恢复及删除本地记录后重新合并。字符位置匹配当前页范围，去重键区分字符书签和旧页码书签，避免相同历史页码误删不同文字位置。
- 书签列表跳转使用字符位置，跳转过程中抑制旧页码重新配置；新书签显示四语言“已保存的文字位置”，旧书签仍显示页码。未知/无效版本回退到旧行为。
- 实际阅读页面测试保存书签后把字号改为 1.8 倍、重建 Activity、跳到其他章节，再点击书签列表，断言原字符回到当前页。该链路通过；并未以仓库单测代替 UI 跳转验证。
- 最终 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（53s）：Debug、Lint、52 项 JVM、41 项设备测试，0 failures / 0 skipped。日志：build/local-validation/bookmark-ui-verification.log。此前不含 UI 扩展断言的两轮也通过，最终以这轮为准。
- 新书签本地跨排版及模拟同步已覆盖；历史页码本身不能恢复此前未记录的字符，真实生产同步没有执行。其他既定听书位置、PDF 容器恢复、删除重试与体验审查仍待完成。


### 第二十六轮：进度 locator 同步与并发合并

- 进度 PUT/GET 通过现有 extras.heartext_locator 传递 locator；拉取文字进度只接受章节匹配的版本化字符位置，PDF 只接受 PDF 类型 locator。较新的旧客户端进度没有 locator 时清除已有 locator，保留原页码回退及严格时间戳比较。
- 同一仓库的进度发送串行处理，300 ms 合并窗口后重新读取数据库最新快照；以账号、本地书籍、远程书籍及成功时间戳去重。等待中仍检查原会话，失败不记作成功，下一次同步可重试；本地位置先存 Room，不依赖网络成功。
- 新设备测试让 12 个并发调用携带旧快照，验证实际只发送一次数据库中的最新页码和字符位置；清除本地 locator 后模拟 GET 验证恢复。另测 503 后再次提交成功、本地位置不丢失。
- 完整 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（1m9s）：Debug、Lint、52 项 JVM、43 项设备测试，0 failures / 0 skipped。日志：build/local-validation/progress-sync-verification.log。
- 合并与成功去重状态仅在当前进程内；持久数据仍由 Room 保留并通过已有登录同步重试，不新增后台常驻任务。PDF locator 传输已实现，但本轮新增往返测试针对文字；真实后端写入未执行。既定 PDF 容器恢复、听书句内位置、书籍删除重试和体验审查仍待完成。


### 第二十七轮：听书语句位置与前台服务启动

- TtsController 原子发布携带播放代次的语句字符范围；协调器只接受当前代次，为当前语句保存文字 locator。暂停和停止前保存位置，进度事件在异步数据库操作前捕获单调时间戳，避免迟到保存覆盖后来位置。
- 播放器默认启动从数据库字符位置映射到语句；阅读器明确“从当前页开始”仍传入指定语句。阅读跟随朗读翻页时，如语句位于当前页则保留语句偏移而非页首。这里恢复到当前语句/长句片段开头，不声称逐音素或字内无缝续播。
- 新设备测试使用应用的实际协调器、系统语音和媒体服务，指定第三句开始，暂停后检查 Room locator，停止并使用默认启动，验证回到第三句。首轮复现 ForegroundServiceDidNotStartInTimeException：显式 startForegroundService 后立即暂停，Media3 未需要前台执行，系统启动要求未及时满足。
- 移除额外的显式前台服务启动，保留 MediaController 绑定，让 Media3 按实际播放状态管理前台生命周期，符合 [MediaSessionService 后台播放文档](https://developer.android.com/media/media3/session/background-playback)。原失败测试随后通过。该结果不是对所有系统后台限制、通知操作或锁屏硬件按钮的完整验证。
- 最终 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（1m4s）：Debug、Lint、52 项 JVM、44 项设备测试，0 failures / 0 skipped。日志：build/local-validation/spoken-position-service.log。首轮崩溃记录保留在 spoken-position-verification.log，不计作通过。
- 仍需验证完整后台/锁屏媒体流程及系统杀进程恢复；既定 PDF 容器、书籍删除重试和体验审查继续推进。未发布、推送或执行生产写入。


### 第二十八轮：后台媒体控制与可信连接

- 扩展真实协调器设备测试：建立独立 MediaController，恢复播放后检查系统报告的服务 foreground 状态，再把 Activity 移到 CREATED（已停止），通过媒体会话执行暂停、恢复、停止，并核对协调器状态。证明上一轮移除显式 FGS 启动后，Media3 仍能把实际播放提升为前台服务并处理后台控制。
- onGetSession 仅向 isTrusted 控制器提供会话，避免任意外部控制器取得播放控制和元数据；根据 [Media3 ControllerInfo 官方定义](https://developer.android.com/reference/androidx/media3/session/MediaSession.ControllerInfo)，同应用、系统及用户授予媒体控制权限的控制器仍可信，不以可伪造的包名字符串放行。
- 加入信任检查前的完整后台测试通过（1m45s）；最终检查后再次完整 `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture` 通过（1m58s）：Debug、Lint、52 项 JVM、44 项设备测试，0 failures / 0 skipped。日志：build/local-validation/trusted-media-verification.log。
- 本轮实际验证同应用媒体控制器及前台服务状态。未安装独立不可信测试应用，未实测蓝牙耳机、车载或不同厂商锁屏，不能将程序化会话控制当作这些物理设备的验证。没有新增权限、发布或生产写入。

### 第二十九轮：实际 PDF 阅读页重建

- 新设备测试通过实际书库和详情页打开生成的三页 PDF，跳转第三页并等待 Room 保存，然后重建 MainActivity，检查真实 PDFView 当前页仍为第三页且宿主视图已经挂载。
- 该测试未复现此前担心的 Fragment 容器重新挂载问题，因此没有添加不必要的生产修补。Activity 重建不等同于系统杀进程恢复；后者仍未验证。
- 专项测试通过；最终 tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture 通过（1m44s）：Debug、Lint、52 项 JVM、45 项设备测试，0 failures / 0 skipped。日志：build/local-validation/pdf-ui-full-verification.log。未发布、推送或执行生产写入。
