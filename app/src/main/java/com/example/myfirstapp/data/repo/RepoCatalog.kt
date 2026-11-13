package com.example.myfirstapp.data.repo

/**
 * 内置实用网址。只收稳定、常见的站点，避免死链。
 * 分类体系：常用（动态）/ 实用工具 / 影音娱乐 / 游戏娱乐 / 学习资源 / 电子书 /
 * 博客论坛 / 论坛社区 / 编程开发 / 软件下载 / 网盘资源 / 生活服务 / 我的 APK。
 * 注：影音、电子书模块此前清空，现已分别重新开放收录；
 *     v3.47 批量补齐 7 条，v3.48 新增「游戏娱乐」模块；
 *     v3.49 按两份实测去重清单新增「网盘资源」14 条与「论坛社区」29 条。
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

        // ---------- 博客论坛（技术博客，SegmentFault 已迁到「论坛社区」） ----------
        site("blog_cnblogs", "博客园", "https://www.cnblogs.com", "开发者个人博客", RepoCategory.BLOG, "技术", "博客"),
        site("blog_ruanyifeng", "阮一峰的网络日志", "https://www.ruanyifeng.com/blog", "技术科普随笔", RepoCategory.BLOG, "技术", "教程"),

        // ---------- 论坛社区（v3.49 新增模块，实测去重后 29 个） ----------
        // 软件、脚本与开发
        fresh("forum_appinn", "小众软件论坛", "https://meta.appinn.net/", "实用软件、工具用法与开发者介绍", RepoCategory.FORUM, "软件", "讨论"),
        fresh("forum_tampermonkey", "油猴中文网", "https://bbs.tampermonkey.net.cn/", "用户脚本、ScriptCat 安装调试交流", RepoCategory.FORUM, "脚本", "讨论"),
        fresh("forum_github", "GitHub Discussions", "https://github.com/orgs/community/discussions", "Actions、仓库功能与开源项目交流", RepoCategory.FORUM, "开源", "讨论"),
        // 读书、综合论坛和生活交流
        fresh("forum_shuge", "书格交流区", "https://www.shuge.org/meet/", "古籍版本、书影与图书资料求询", RepoCategory.FORUM, "古籍", "读书"),
        fresh("forum_tianya_im", "第三方天涯", "https://tianya.im/", "读书、历史、生活、职场长帖", RepoCategory.FORUM, "综合", "长帖"),
        fresh("forum_douban", "豆瓣小组", "https://www.douban.com/group/explore", "读书、影视、生活兴趣小组", RepoCategory.FORUM, "兴趣", "小组"),
        fresh("forum_tianya_net", "天涯恢复版", "https://www.tianya.net/index.html", "天涯旧帖与历史精华帖", RepoCategory.FORUM, "旧帖", "历史"),
        fresh("forum_essay", "Essay 时光广场", "https://www.essay.ink/essays", "随笔、短文与主题写作", RepoCategory.FORUM, "写作", "随笔"),
        fresh("forum_dashudong", "树洞网", "https://dashudong.com/", "匿名倾诉、生活求助与情感话题", RepoCategory.FORUM, "匿名", "生活"),
        fresh("forum_ebihu", "逼乎", "https://www.ebihu.com/", "小型问答、玩梗与兴趣交流", RepoCategory.FORUM, "问答", "社区"),
        fresh("forum_autohome", "汽车之家论坛", "https://club.autohome.com.cn/", "车型、保养、维修与用车经验", RepoCategory.FORUM, "汽车", "用车"),
        // 动漫、游戏、影视和推理
        fresh("forum_bangumi", "Bangumi 小组", "https://bgm.tv/group", "动画、漫画、轻小说评分与话题", RepoCategory.FORUM, "动漫", "评分"),
        fresh("forum_zmtu", "ZMTU", "https://zmtu.com/", "ACG、推番、游戏与轻量技术讨论", RepoCategory.FORUM, "ACG", "讨论"),
        fresh("forum_gugu", "咕咕番社区", "https://gugu.fan/feed", "追番动态、漫评与同好频道", RepoCategory.FORUM, "追番", "动态"),
        fresh("forum_tanghenre", "汤很热社区", "https://tanghenre.com/community", "海龟汤、规则怪谈与推理题", RepoCategory.FORUM, "推理", "社区"),
        fresh("forum_ezdmw", "E 站弹幕网社区", "https://www.ezdmw.org/Index/contribution/contribution.html?type=community&order=new", "ACG 图文投稿与动漫讨论", RepoCategory.FORUM, "ACG", "投稿"),
        fresh("forum_akongwu", "A 控屋论坛", "https://akongwu.com/ltwdsqht/", "恐怖影视求片、补档与资源交流", RepoCategory.FORUM, "影视", "论坛"),
        // 有内容但访问或读帖受限（游客可能遇到验证/登录）
        fresh("forum_v2ex", "V2EX", "https://www.v2ex.com/", "开发、软件、数码与分享创造", RepoCategory.FORUM, "技术", "社区"),
        fresh("forum_linuxdo", "LINUX DO", "https://linux.do/", "AI 应用、开发工具、自托管部署", RepoCategory.FORUM, "AI", "开发"),
        fresh("forum_stackoverflow", "Stack Overflow", "https://stackoverflow.com/questions", "英文编程问答、报错与 API 用法", RepoCategory.FORUM, "编程", "问答"),
        fresh("forum_kanxue", "看雪论坛", "https://bbs.kanxue.com/", "安全研究、逆向工程与 CTF", RepoCategory.FORUM, "安全", "逆向"),
        fresh("forum_right", "恩山无线论坛", "https://www.right.com.cn/forum/", "OpenWrt、软路由、光猫与 NAS", RepoCategory.FORUM, "路由", "NAS"),
        fresh("forum_eleduck", "电鸭社区", "https://eleduck.com/", "远程工作、自由职业与职场讨论", RepoCategory.FORUM, "远程", "职场"),
        fresh("forum_tieba", "百度贴吧", "https://tieba.baidu.com/", "兴趣、游戏、器材与地区主题吧", RepoCategory.FORUM, "兴趣", "贴吧"),
        fresh("forum_reddit", "Reddit", "https://www.reddit.com/", "英文兴趣社区、读书与技术讨论", RepoCategory.FORUM, "综合", "英文"),
        fresh("forum_gcores", "机核讨论", "https://www.gcores.com/discussions", "游戏文化、作品讨论与机组", RepoCategory.FORUM, "游戏", "文化"),
        fresh("forum_1000qm", "阡陌居", "https://www.1000qm.vip/", "电子书与书友交流", RepoCategory.FORUM, "电子书", "书友"),
        // 清单里与已有条目重复的两个站点：迁移而非新增，id 保持不变以免收藏丢失
        site("soft_52pojie", "吾爱破解", "https://www.52pojie.cn", "国内老牌软件交流论坛", RepoCategory.FORUM, "论坛", "实用"),
        site("blog_segmentfault", "SegmentFault", "https://segmentfault.com", "技术问答与文章", RepoCategory.FORUM, "技术", "问答"),

        // ---------- 编程开发（按需求仅保留 4 条） ----------
        site("dev_runoob", "菜鸟教程", "https://www.runoob.com", "入门教程速查", RepoCategory.DEV, "教程", "入门"),
        site("dev_leetcode", "力扣", "https://leetcode.cn", "算法刷题与面试", RepoCategory.DEV, "算法", "面试"),
        fresh("dev_hellogithub", "HelloGitHub", "https://hellogithub.com", "发现好玩的开源项目", RepoCategory.DEV, "开源", "月刊"),
        fresh("ext_crxsoso", "Crx 搜搜", "https://www.crxsoso.com", "扩展与应用离线包搜索", RepoCategory.DEV, "插件", "下载"),

        // ---------- 软件下载（吾爱破解已迁到「论坛社区」） ----------
        site("soft_github_releases", "GitHub Trending", "https://github.com/trending", "开源软件发布页入口", RepoCategory.SOFTWARE, "开源", "下载"),
        fresh("soft_ossnav", "开源精选", "https://www.ossnav.com", "开源软件替换方案导航", RepoCategory.SOFTWARE, "开源", "导航"),
        fresh("soft_plainapp", "PlainApp", "https://plainapp.app/zh", "开源手机管理：浏览器内传文件 / 短信 / 投屏", RepoCategory.SOFTWARE, "开源", "手机"),
        fresh("soft_webtorrent", "WebTorrent", "https://webtorrent.io/", "开源 BT 客户端，浏览器 / 桌面边下边播", RepoCategory.SOFTWARE, "开源", "下载"),

        // ---------- 网盘资源（v3.49 新增模块，实测保留 14 个入口） ----------
        fresh("disk_panhunt", "盘搜 PanHunt", "https://s.panhunt.com/", "多网盘聚合搜索，也返回磁力", RepoCategory.NETDISK, "网盘", "聚合"),
        fresh("disk_quanpan", "全盘搜索", "https://quanpan.xyz/", "多网盘聚合，可按来源查看", RepoCategory.NETDISK, "网盘", "聚合"),
        fresh("disk_haisou", "海搜", "https://haisou.cc/", "分享标题、文件与文件夹搜索", RepoCategory.NETDISK, "网盘", "搜索"),
        fresh("disk_kuake", "谷哥网盘搜索", "https://kuake.so/", "夸克为主，也补搜阿里 / 百度 / 迅雷", RepoCategory.NETDISK, "夸克", "网盘"),
        fresh("disk_panso", "聚盘搜", "https://www.panso567.com/", "按网盘来源、类型和时间筛选", RepoCategory.NETDISK, "网盘", "筛选"),
        // 原清单为 https://www.so1st.com/#/ ，hash 路由已规范为根路径
        fresh("disk_so1st", "又一个资源搜索", "https://www.so1st.com/", "多网盘补搜，尤其阿里云盘", RepoCategory.NETDISK, "网盘", "补搜"),
        fresh("disk_l9", "老九资源", "https://pan.l9.lc/", "带独立详情页的网盘资源库", RepoCategory.NETDISK, "网盘", "详情"),
        fresh("disk_kkso", "KK 网盘搜", "https://kkso.net/", "夸克、百度网盘补搜", RepoCategory.NETDISK, "夸克", "网盘"),
        fresh("disk_aliso", "阿里搜", "https://aliso.cc/", "阿里云盘资源，可看文件名和大小", RepoCategory.NETDISK, "阿里云盘", "搜索"),
        fresh("disk_mizixing", "米字型资源网", "https://mizixing.com/", "影视类网盘分享，站内文章检索", RepoCategory.NETDISK, "影视", "网盘"),
        fresh("disk_yyets", "人人影视分享站", "https://yyets.click/search", "影视资源、字幕与评论分享", RepoCategory.NETDISK, "影视", "字幕"),
        fresh("disk_knaben", "Knaben", "https://knaben.org/", "综合 BT 磁力搜索，按做种数排序", RepoCategory.NETDISK, "磁力", "种子"),
        fresh("disk_linuxtracker", "LinuxTracker", "https://linuxtracker.org/", "Linux 发行版 ISO 与开源软件种子", RepoCategory.NETDISK, "种子", "Linux"),
        fresh("disk_cilixiong", "磁力熊", "https://www.cilixiong.uk/", "按影视片名检索磁力链接", RepoCategory.NETDISK, "磁力", "影视"),

        // ---------- 生活服务（游戏相关内容已迁到「游戏娱乐」） ----------
        fresh("life_ip", "IP 查询", "https://ip.net.coffee", "查本机公网 IP 与归属地", RepoCategory.LIFE, "网络", "查询"),
        site("life_tracemoe", "trace.moe", "https://trace.moe", "截图搜番剧出处", RepoCategory.LIFE, "二次元", "搜索"),
        fresh("life_bird", "不死鸟资源", "https://b.oe.do/", "综合网址导航：影视 / 小说 / 工具 / 热榜", RepoCategory.LIFE, "导航", "资源")
    )
}
