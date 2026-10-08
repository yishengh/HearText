# HearText 1.0.1 正式发布验证

2026-10-08。用户在原本地开发目标完成后另行授权推送主分支、生成发布包并发布 Google Play，随后要求先完成正式 Clerk 配置。

## 当前候选包

- 主分支 `master` 已推送至 `76cb9f0`；版本 `1.0.1` / code `2`。
- 正式 Clerk instance：`ins_3KOmdZVQQLQPE1RvjEdE7ynFQGa`。
- Issuer：`https://clerk.heartext.677000.xyz`；API：`https://heartext.677000.xyz`。
- Publishable Key 通过已认证 Clerk CLI 只读取得，仅更新被 Git 忽略的 `local.properties`；没有取得 Secret Key。已解码检查生成的 Release BuildConfig 指向正式 Clerk 域名。
- 上传签名 SHA256 保持 `96c67beb178fd3ce23e5fedd88e49aeedcd0c2ea1a70261053d6a7c1ec95c7ae`。它与 Google Play 应用签名不同，不能相互代替。
- 正式包在 `build/releases/1.0.1/`，其 `release-metadata.json` 记录当前哈希。先前开发 Clerk 候选已由正式候选替换。

## 已执行

- `:app:bundleRelease :app:assembleRelease :app:testDebugUnitTest :app:lintRelease` 成功，1m11s；日志 `build/local-validation/production-clerk-build.log`。
- 54 项 JVM 测试，0 failures/errors；其中包含原有占位测试，不视为功能覆盖。
- Release Lint：0 errors、117 warnings、1 hint。
- `apksigner verify --print-certs` 成功，签名指纹与既有上传签名一致。
- 新建独立 AVD `build/release-avd/HearText_Release.avd`，API 35 / x86_64，序列号 `emulator-5584`。正式签名 APK 安装成功并进入登录界面。
- 原 `Small_Tablet` 安装签名不同，未覆盖或卸载，原数据保留。旧任务模拟器已正常关闭。

## 真实正式会话验证

用户完成登录后，正式签名 APK 显示书城分类、精选、排行榜，书籍详情和目录可正常加载；设置页显示 `Last sync completed`。证据 `build/local-validation/production-signed-in-sync.jpg`。

后端聊天只读核实容器日志，2026-10-08 01:48:29–01:49:08 EDT：`GET /v1/me`、`/v1/books`、`/v1/catalog`、`/v1/catalog/categories`、`/v1/catalog/featured`、`/v1/catalog/rankings`、`/v1/progress`、`/v1/annotations`、`/v1/offline-voices/featured` 均为 200；自动同步产生一次 `POST /v1/books` 201。日志时间与客户端操作一致，但日志本身不独立证明客户端版本。没有额外构造业务写入、删除账号或付费请求。

正式 AAB 已上传至 Play 并通过包解析，版本 code 2、min API 26、target API 36。阻断项“未选国家地区”已处理；保留未上传反混淆文件与原生符号两条非阻断警告。当前未启用 R8 混淆。

## Google Play 提交

生产版本 `1.0.1`、新增 177 个国家/地区及 Rest of World 共 3 项变更已执行 `Send changes for review`。页面随后显示 `Changes in review`，并说明快速检查成功后会送审；记录时快速检查仍在运行。Managed publishing off，审核通过后自动全量发布。尚未证明已对公众上线。

提交证据：`build/local-validation/play-production-submitted.png`。正式 AAB SHA256：`cc30a8b886d3ab163f04b914c0679627b34e3907eaaf9c9b8f5d956bbafd41f9`；APK SHA256：`0194c3fc18dd4e0e1124d15361ea5ea2f8fc10ac27255e4f0d9a98af0509ea86`。

## 待完成

- 用户真实正式登录及上述业务读取已验证；完整注册/邮件投递过程未由代理观察，退出后再次登录、双账号隔离和生产 CRUD 全流程未重新执行。
- Google Play 分发后的安装包尚未验证；平台快速检查、审核及实际上线状态待完成，不得把提交成功描述为已上线。
- Android 现有登录界面仅提供邮箱/密码；后端配置 Google OAuth 不等于客户端已有 Google 登录入口。

后端配置与服务端验证以其 `docs/android-handoff.md` 为依据。此前本地功能验证见 `LOCAL_DELIVERY.md`，该文的“未发布”描述属于原阶段历史，不应改写为正式发布验收结论。
