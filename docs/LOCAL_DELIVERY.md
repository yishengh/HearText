# HearText Android 本地交付

2026-10-08。接手基线 `ba7d491`，最初工作区干净；本阶段限定清单已完成。产品仍是 EPUB/TXT/PDF 本地阅读、系统/离线听书及现有账号同步/书城，PDF 不提供听书。未恢复已取消的云端付费 TTS、录音、克隆或滚动阅读方向。

这份结论只覆盖本地开发与下述验证，不代表线上业务、所有设备或发布验收完成。全过程没有发布、部署、推送、生产写入、付费调用或更换签名身份；后端只读，跨项目依赖在本仓库记录。

## 完成清单与证据

| 原定范围 | 已落实的行为 | 当前验证证据 |
| --- | --- | --- |
| 导入、保存、删除 | 三格式验证、输入大小/压缩资源限制、取消和失败不留半文件、重复下载保留进度/用户封面、迁移保留数据、删除意图持久化 | DocumentParsingTest、BookStorageTest、CatalogDownloadTest、DatabaseMigrationTest、CloudSyncTest；实际系统文件选择器导入 PDF |
| 阅读及生命周期 | 字符进度/书签、排版后定位、章节/搜索跳转、PDF locator、阅读会话释放、文字和 PDF 进程恢复 | LocalReaderSmokeTest、ReaderRelayoutTest、ReaderLifecycleTest、ReadiumLocatorTest、PdfReaderRecreationTest；下述真实 am kill 证据 |
| 系统与离线音频 | 活动引擎回退、句子定位、取消旧回调、合成/PCM 写入终止、音频焦点及媒体会话、试听停止、语音包原子安装和恢复 | SystemTtsEngineTest、TtsControllerTest、PcmStreamTest、NativeAudioTest、PlaybackPositionTest、OfflineVoiceRepositoryTest、VoicePackageTest；实际系统引擎和官方 Amy 模型运行 |
| 网络与账号 | 请求取消覆盖响应体、401 仅刷新一次、429/503 冷却、不自动重放写入、换号隔离、同步冲突、待删除队列、同步失败状态与重试、初始化/退出失败可恢复 | HearTextApiTest、DownloadTest、RetryAfterGateTest、AuthViewModelsTest、CloudSyncTest、AnnotationSyncTest、CatalogSearchTest、SyncStatusTest；只用本地 MockWebServer/模拟会话 |
| 隐私、权限与异常界面 | 禁用自动备份/迁移数据备份、避免原始异常/正文进入反馈日志、限定文件访问、导入使用 SAF、封面/字体安全保存、缓存清理只触及图片缓存、详情缺失/失败/重试 | BackupPolicyTest、SafeDiagnosticTest、CoverStoreTest、FontStoreTest、StorageRepositoryTest、OverviewStateTest；清单声明无麦克风或广泛存储权限；实际 OpenDocument 流程通过 |
| 无障碍和多语言 | 导航标签/Tab 状态、阅读控件触摸区域、当前正文辅助功能动作、排除预加载页、英中法西资源及长期 Context 语言一致性 | ReaderAccessibilityTest、LocalReaderSmokeTest、LocaleConsistencyTest；不是人工 TalkBack 或逐屏翻译验收 |

具体问题、失败复现、修复依据及每批检查点见 [LOCAL_HARDENING.md](LOCAL_HARDENING.md)。测试源码位于 `app/src/test` 和 `app/src/androidTest`，不是仅凭数量认定完成。

## 最终执行结果

- `tools/verify-local.ps1 -Connected -Serial emulator-5582 -OfflineFixture`：成功，1m58s。54 项 JVM、59 项设备测试，均 0 failures/errors/skipped。54 项中包含原有 1 项占位算术测试，不视为功能覆盖。
- Android Lint：0 errors、117 warnings、1 hint；未创建隐藏问题的 baseline。剩余包含依赖更新提示、内联 API 常量、过时 API、布局/资源建议，未宣称静态检查零警告。
- 正常配置 `gradlew.bat :app:assembleDebug --no-daemon --console=plain`：最终源码成功，42s；事先检查任务图，无上传/发布任务。没有安装或运行正常生产配置 APK。
- 设备：本任务 Small_Tablet / `emulator-5582`，Android 15 / API 35 / x86_64。Gradle 使用 Android Studio 自带 JBR。
- 官方 Amy 离线模型通过本机回环 HTTP 服务供应给模拟器；不调用在线付费语音。脚本会停止自己启动的服务。

本地证据（构建输出不进入 Git）：

- `build/local-validation/final-local-verification.log`、`final-normal-debug-build.log`、`final-test-summary.json`。
- `build/validation-app/reports/`：JVM、设备、Lint HTML；对应测试 XML 在 test-results 和 outputs/androidTest-results。
- 隔离验证 APK 另存 `build/local-validation/heartext-validation-debug.apk`，SHA-256 `86b38636268a60d6f2864d2e1c68416a6f6da452435444cf06cd789bc36d9768`。包名 `com.yishenghuang.heartext.validation`，没有 Clerk key，API 指向模拟器本机端口，不适合作为上线包。

## 实际进程恢复

文字：搜索修复后的文字阅读代码中，打开 Alice EPUB、翻到正文页，Home → `am kill` → 确认 PID 消失 → 从原任务启动。PID 7027→7303，可见正文 299 字符完全一致，哈希 `2434e36eec9912d5fb76868ac3bcc381345c7d7cc1e4001041351cab986ed092`。可复用 `python tools/verify-process-restore.py --serial emulator-5582`；先安装隔离 APK、打开一页至少 150 字符的正文并停止播放。

PDF：用本地三页 PDF 经系统 Downloads 导入，滑至明确标有 `LOCAL PDF PAGE 3` 的第三页。首次完整进程恢复出现“67% 但正文空白”，已修复 Compose 容器与恢复 Fragment 的挂载时序。最终 PID 7995→8230，旧进程确认消失；前后实际截图相同并检查可见第三页，PNG 哈希 `32e36724a06ffc164712c4afd99f11827c93a4ffcc729cc3f6ec857798e3c8fc`。证据 `pdf-fixed-before.png`、`pdf-fixed-after.png`、`pdf-fixed-process-evidence.json`。修复采用 [AndroidX FragmentManager.onContainerAvailable](https://developer.android.com/reference/androidx/fragment/app/FragmentManager)，随后完整回归通过。

上述是后台进程终止，不是 force-stop，也不将 Activity recreate 当作进程恢复。PDF 验证是本次实际操作证据，现有文字脚本不自动覆盖 PDF。

## 剩余问题及未验证边界

1. **真实账号与后端业务未联调。** 目前没有专用测试账号/可写非生产环境；需要两个测试账号验证登录、隔离、进度/批注、反馈及明确指定账号的删除。生产禁止写入边界继续有效。后端健康、文档和匿名认证拦截通过不等于这些流程通过。约定及 extras 扩展见 [ANDROID_BACKEND_INTEGRATION.md](ANDROID_BACKEND_INTEGRATION.md)，本阶段不要求后端代码改动。
2. **真机与辅助功能人工验收未执行。** 模拟器使用 no-audio；真实引擎及 PCM 行为通过不等于人耳音质通过。硬件蓝牙/车载/锁屏、完整 TalkBack、通知权限拒绝后的 OEM 行为、厂商强制后台清理、真实设备备份/迁移、四语言逐屏校对均不能标为完成。API 26 现有 x86 AVD 与应用 ABI 不匹配，未运行；其他系统版本/ARM 真机兼容性未证明。
3. **宿主模拟器稳定性仍有限制。** 曾有 qemu headless 的 Windows 0xc0000005 崩溃；根因未解决。只重启本任务设备并以软件 GPU 后完成回归，不据此宣称宿主问题已修复。
4. **同步仍受服务端最终状态影响。** 登记请求结果不明且暂不可按 clientBookId 查到时，删除意图保留；恢复登录/手动同步/删除可重试，没有新增常驻同步任务。旧的无引用字体/封面文件保留，不进行不确定的自动删除。既有字符串状态不会全部即时重新翻译，新生成反馈按当前语言解析。
5. **本次没有发布验收。** 未构建或上传正式发布产物、未验证 Google Play/线上状态、未调整签名和生产配置。后续发布不在本阶段授权范围内。

以上限制已独立保留，不用本地绿色测试代替。原定本地修复和可执行验证清单至此收口；后续只需在取得对应环境/设备后执行列明的联调与验收，不新增产品方向。
