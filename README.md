# 尘剑 · 校园生活助手（ChenJian）

一个用 Kotlin + Jetpack Compose 写的 Android 校园生活工具 App，把课表、待办、记账、小说阅读、灵感笔记、应用快捷方式、内置浏览器和网址仓库放进同一个应用。

> 当前版本 **v3.48**（versionCode 45） · minSdk 24 / targetSdk 35 / compileSdk 35 · 仅分发 debug APK，仓库内不含安装包。

## 功能模块

底部导航共 8 个模块，全部常驻显示：

| 模块 | 代码目录 | 主要能力 |
| --- | --- | --- |
| 课表 | `ui/schedule/` | 周课表网格、课程颜色、节次设置 |
| 任务栏 | `ui/task/`、`ui/todo/` | 待办清单、月历视图、任务状态 |
| 记账 | `ui/ledger/` | 记一笔、收支统计、分类图表、长按编辑/删除 |
| 小说 | `ui/novel/` | 书库首页（分类卡 / 排行榜 / 男女频道）、书源搜索、书架筛选排序、目录缓存、翻页阅读器 |
| 灵感 | `ui/idea/` | 便签瀑布流、颜色标记、自动保存 |
| 软链接 | `ui/shortcuts/` | 自定义图标，一键启动手机里的第三方 App |
| 浏览器 | `ui/browser/` | 20 个搜索引擎、内嵌 WebView、主页快捷入口 |
| 仓库 | `ui/repo/` | 内置网址目录（11 个分类）、收藏/最近使用、本地 APK 面板 |

## 技术栈

- **语言 / UI**：Kotlin 2.0.21、Jetpack Compose（BOM 2025.05.01）、Material 3
- **构建**：Android Gradle Plugin 8.10.0、KSP 2.0.21、JDK 17、Gradle Wrapper
- **数据**：Room 2.6.1（账目、章节缓存）、DataStore Preferences（设置、收藏）
- **网络 / 解析**：Jsoup、HttpURLConnection（书源规则、目录与正文抓取）
- **其它**：AlarmManager + BroadcastReceiver（提醒，已移除 WorkManager 主链路）、FileProvider（安装本地 APK）、WebView、core library desugaring

## 快速开始

```bash
git clone https://github.com/Chenhua000/A-app.git
cd A-app
```

启动图标 PNG、`gradle-wrapper.jar` 等二进制资源都已随仓库提交，clone 后直接可用。用 **Android Studio** 打开项目根目录，等 Gradle Sync 完成即可运行 `:app`。
本机 SDK 路径写在 `local.properties`（该文件不入库）：

```properties
sdk.dir=/path/to/Android/sdk
```

### 命令行构建

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Windows + Git Bash 下 `./gradlew` 可能因 MSYS 路径转换报 `ClassNotFound(GradleWrapperMain)`，可绕过脚本直接调用 wrapper：

```bash
java -classpath gradle/wrapper/gradle-wrapper.jar \
  org.gradle.wrapper.GradleWrapperMain :app:assembleDebug
```

## 目录结构

```
app/src/main/java/com/example/myfirstapp/
├── ui/                 # 各模块 Compose 页面
│   ├── components/     # 共享组件（卡片、空态、消息条…）
│   ├── theme/          # 颜色、排版、尺寸令牌
│   ├── navigation/     # 底部 Tab 与模块顺序
│   └── AppRoot.kt      # 全局壳：顶栏 / 抽屉 / 底部导航
├── data/               # Room、DataStore、书库数据(bookstore/)、网络抓取、内置仓库目录
├── domain/             # 书源规则、结果清洗、金额解析等纯逻辑
├── reminder/           # 闹钟与提醒协调
└── MyFirstApp.kt       # Application 初始化
app/src/test/           # 本地单元测试
app/src/main/assets/
├── book_sources.json   # 推荐书源包
├── bookstore_data.json # 书库分类 / 榜单 / 热词数据
└── legado_sources.json # 1744 条笔趣阁书源（首次启动自动导入，默认停用）
gradle/                 # version catalog + wrapper
tools/                  # 书源转换辅助脚本
```

## 设计约定

维护 UI 时建议先了解这几条既有约定：

- **设计系统**：`ui/theme/Dimens.kt`（间距 / 触摸目标 / `onColorFor()`）、`Type.kt`、`Theme.kt`（`dynamicColor` 默认关闭，避免系统动态色绕过自定义配色）、`ui/components/Components.kt`。
- **顶栏**：不显示页面标题，只在首页显示菜单、二级页显示返回箭头；阅读器为沉浸模式。
- **长按交互**：卡片不常驻按钮，统一用 `combinedClickable` 长按弹 `DropdownMenu`（编辑/删除或收藏/取消收藏），删除必须走 `AlertDialog` 确认。仓库站点、软链接、记账记录都遵循这套范式。
- **仓库网格**：`RepoGridBuilder` 生成全局唯一 key，因为同一站点会同时出现在收藏、最近使用和分类里；直接用 `site.id` 当 key 会触发 `Key was already used` 崩溃。新增分类只需在 `RepoCategory` 枚举加一项，同时补 `categoryIcon` 的分支与对应图标 import。

## 测试

```bash
./gradlew :app:testDebugUnitTest
```

`BundledSourcesLiveTest.builtInSourcesCanSearch` 依赖外网 gutenberg.org，离线环境会失败，属于既有的环境性失败，不是回归。其余为纯逻辑本地测试。

## 小说书源

- 首次启动会从 `assets/legado_sources.json` 自动导入 **1744 条书源**，全部**默认停用**；在「小说 → 书源管理」按分组批量启用或跑自检。
- 分组按可用性标记：`笔趣阁·可用`（1117）、`JS规则`（481）、`POST搜索`（121）、`需登录`（10）、`不完整`（15）。本项目规则引擎只跑 CSS 选择器与正则，**刻意不执行网页 JS**，后四类大概率搜不出结果。
- 书源来自 Legado（开源阅读）系的两代格式，已转换为本项目 `BookRules` 结构；`LegadoSourcesTest` 校验全量导入的字段与规则可解析性。
- 书库首页的分类卡、榜单、热词是静态内置数据；点书名先进「书籍详情」页（大封面 + 简介），再由「搜索并阅读」走书源搜索。封面来自内置数据，会回填到搜索结果、书架与目录页；加载失败自动回退到书名首字色块。只作展示，不托管任何第三方内容。
- 书源管理页使用 `LazyColumn` 虚拟化渲染，1805 条书源也不会卡顿。

## 版本与发布

每个版本递增 `app/build.gradle.kts` 里的 `versionCode` / `versionName`；debug APK 打成 ZIP 后单独分发，不进入本仓库。近期节点：

- v3.45 影音分类重新开放
- v3.46 电子书分类重新开放
- v3.47 批量新增 7 个网址
- v3.48 新增「游戏娱乐」模块，收录 2 个站点
- v3.49 小说模块复刻书库首页、内置 1744 条书源，并新增「网盘资源」「论坛社区」两个仓库分类

## 不包含的内容

以下文件由 `.gitignore` 排除，clone 后不会出现：

- 所有 APK / ZIP 发布包、`build/`、`dist/`、UI 调试截图
- `local.properties`、IDE 配置、`.workbuddy/` 本地工作记忆
- `书源/`：第三方书源规则与网址索引，涉及版权与合规，需要时自行导入
- 旧版《零基础操作指南》（v3.8 时期，已由本文件取代）

## 说明

仓库模块里收录的是第三方网站的**网址书签**，站点内容与其版权归各原作者所有，本项目不托管、不分发任何资源文件，请遵守相关法律法规与站点条款。

本项目未指定开源许可证，默认保留所有权利。
