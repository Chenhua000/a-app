package com.example.myfirstapp.data.repo

/**
 * 内置实用网址。只收稳定、常见的站点，避免死链。
 * 分类体系：常用（动态）/ 实用工具 / 影音娱乐 / 游戏娱乐 / 学习资源 / 电子书 /
 * 博客论坛 / 编程开发 / 软件下载 / 生活服务 / 我的 APK。
 * 注：影音、电子书模块此前清空，现已分别重新开放收录；
 *     v3.47 按用户给的一批网址补齐 7 条，v3.48 新增「游戏娱乐」模块并收录 2 条。
 */
object RepoCatalog {

    private fun site(
        id: String,
        name: String,
        url: String,
        desc: String,
        category: RepoCategory,
        vararg tags: String
    ) = RepoSite(id, name, url, desc, category, tags.toList())

    /** 新收录：卡片上打「新」角标 */
    private fun fresh(
        id: String,
        name: String,
        url: String,
        desc: String,
        category: RepoCategory,
        vararg tags: String
    ) = RepoSite(id, name, url, desc, category, tags.toList(), builtIn = true, isNew = true)

    val sites: List<RepoSite> = listOf(
        // ---------- 实用工具（精简后 10 条 + 新收录 1 条） ----------
        site("ut_convertio", "Convertio", "https://convertio.co/zh", "在线格式转换，文档图片音频都能转", RepoCategory.UTILS, "转换", "工具"),
        site("ut_smallpdf", "Smallpdf", "https://smallpdf.com", "PDF 压缩、合并、转 Word", RepoCategory.UTILS, "PDF", "工具"),
        site("ut_qrcode", "草料二维码", "https://cli.im", "在线生成与解析二维码", RepoCategory.UTILS, "二维码", "工具"),
        site("ut_tool_lu", "在线工具箱", "https://tool.lu", "时间戳、编码、正则等开发小工具", RepoCategory.UTILS, "工具", "开发"),
        site("ut_temp_mail", "临时邮箱", "https://temp-mail.org", "注册时用的一次性邮箱", RepoCategory.UTILS, "邮箱", "隐私"),
        fresh("img_tinypng", "TinyPNG", "https://tinypng.com", "公认最好用的在线图片压缩", RepoCategory.UTILS, "压缩", "图片"),
        site("img_removephotos", "Remove.photos", "https://remove.photos/zh-cn", "在线抠图换底色", RepoCategory.UTILS, "抠图", "AI"),
        site("ut_convertworld", "单位换算", "https://www.convertworld.com", "各国单位在线换算", RepoCategory.UTILS, "换算", "工具"),
        site("ut_ezgif", "在线 GIF", "https://ezgif.com", "视频转 GIF 与动图编辑", RepoCategory.UTILS, "GIF", "图片"),
        fresh("ut_pdf24", "PDF24", "https://tools.pdf24.org", "全功能 PDF 在线工具箱", RepoCategory.UTILS, "PDF", "工具"),
        fresh("ut_sendwang", "私传网", "https://send.wang/", "免安装跨平台 P2P 文件互传", RepoCategory.UTILS, "传输", "文件"),

        // ---------- 影音娱乐（新收录：CLICLI 动漫） ----------
        fresh("media_clicli", "CLICLI动漫", "https://www.cliclidm.com/", "在线追番：国漫 / 日漫 / 漫剧", RepoCategory.MEDIA, "动漫", "追番"),

        // ---------- 游戏娱乐（v3.48 新增模块） ----------
        fresh("game_20find", "摸鱼解谜", "https://20find.com/", "每日推理谜题：用线索找坏蛋", RepoCategory.GAME, "解谜", "推理"),
        fresh("game_heeee", "资源网", "https://heeee.com/", "综合资源导航：游戏 / 工具 / 影音 / 学习", RepoCategory.GAME, "导航", "资源"),
        // id 保留 v3.47 的 life_baoku，只改分类，避免已收藏的用户丢失条目
        fresh("life_baoku", "Baoku One", "https://baoku.one/", "PC 游戏下载索引", RepoCategory.GAME, "游戏", "下载"),

        // ---------- 学习资源（仅保留：知网、谷歌学术、中科院资源） ----------
        site("study_cnki", "中国知网", "https://www.cnki.net", "学术文献检索与下载", RepoCategory.STUDY, "论文", "学术"),
        site("study_scholar", "谷歌学术", "https://scholar.google.com", "全球论文检索入口", RepoCategory.STUDY, "论文", "学术"),
        fresh("study_pubscholar", "PubScholar", "https://pubscholar.cn", "中科院公益学术资源平台", RepoCategory.STUDY, "论文", "学术"),

        // ---------- 电子书（Z-Library + 书源仓库 / 小说站） ----------
        fresh("book_zlibrary", "Z-Library", "https://zh.vbh101.ru/", "全球电子书图书馆 · 中文入口", RepoCategory.BOOKS, "电子书", "检索"),
        fresh("book_yuancang", "源仓", "https://yuancang.cc/", "开源阅读书源仓库，数千书源订阅", RepoCategory.BOOKS, "书源", "阅读"),
        fresh("book_ixdzs", "爱下电子书", "https://ixdzs8.com", "网络小说在线阅读与书单", RepoCategory.BOOKS, "小说", "阅读"),

        // ---------- 博客论坛（精简后仅保留 3 条） ----------
        site("blog_cnblogs", "博客园", "https://www.cnblogs.com", "开发者个人博客", RepoCategory.BLOG, "技术", "博客"),
        site("blog_segmentfault", "SegmentFault", "https://segmentfault.com", "技术问答与文章", RepoCategory.BLOG, "技术", "问答"),
        site("blog_ruanyifeng", "阮一峰的网络日志", "https://www.ruanyifeng.com/blog", "技术科普随笔", RepoCategory.BLOG, "技术", "教程"),

        // ---------- 编程开发（按需求仅保留 4 条） ----------
        site("dev_runoob", "菜鸟教程", "https://www.runoob.com", "入门教程速查", RepoCategory.DEV, "教程", "入门"),
        site("dev_leetcode", "力扣", "https://leetcode.cn", "算法刷题与面试", RepoCategory.DEV, "算法", "面试"),
        fresh("dev_hellogithub", "HelloGitHub", "https://hellogithub.com", "发现好玩的开源项目", RepoCategory.DEV, "开源", "月刊"),
        fresh("ext_crxsoso", "Crx 搜搜", "https://www.crxsoso.com", "扩展与应用离线包搜索", RepoCategory.DEV, "插件", "下载"),

        // ---------- 软件下载（精简后 3 条 + 新收录 2 条） ----------
        site("soft_github_releases", "GitHub Trending", "https://github.com/trending", "开源软件发布页入口", RepoCategory.SOFTWARE, "开源", "下载"),
        site("soft_52pojie", "吾爱破解", "https://www.52pojie.cn", "国内老牌软件交流论坛", RepoCategory.SOFTWARE, "论坛", "实用"),
        fresh("soft_ossnav", "开源精选", "https://www.ossnav.com", "开源软件替换方案导航", RepoCategory.SOFTWARE, "开源", "导航"),
        fresh("soft_plainapp", "PlainApp", "https://plainapp.app/zh", "开源手机管理：浏览器内传文件 / 短信 / 投屏", RepoCategory.SOFTWARE, "开源", "手机"),
        fresh("soft_webtorrent", "WebTorrent", "https://webtorrent.io/", "开源 BT 客户端，浏览器 / 桌面边下边播", RepoCategory.SOFTWARE, "开源", "下载"),

        // ---------- 生活服务（游戏相关内容已迁到「游戏娱乐」） ----------
        fresh("life_ip", "IP 查询", "https://ip.net.coffee", "查本机公网 IP 与归属地", RepoCategory.LIFE, "网络", "查询"),
        site("life_tracemoe", "trace.moe", "https://trace.moe", "截图搜番剧出处", RepoCategory.LIFE, "二次元", "搜索"),
        fresh("life_bird", "不死鸟资源", "https://b.oe.do/", "综合网址导航：影视 / 小说 / 工具 / 热榜", RepoCategory.LIFE, "导航", "资源")
    )
}
