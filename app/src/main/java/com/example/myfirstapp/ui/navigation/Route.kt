package com.example.myfirstapp.ui.navigation

sealed class AppRoute(val title: String) {
    data object Schedule : AppRoute("课表")
    data object Ledger : AppRoute("记账")
    data object Todo : AppRoute("待办")
    data object Novel : AppRoute("小说")
    data object Ideas : AppRoute("灵感")
    data object Board : AppRoute("任务栏")
    data object Shortcut : AppRoute("软链接")
    data object Browser : AppRoute("浏览器")
    data object Repo : AppRoute("仓库")
    data object Stats : AppRoute("收支统计")
    data object Slots : AppRoute("节次设置")
    data object Settings : AppRoute("设置")
    data object Sources : AppRoute("书源管理")
    data class Toc(val bookId: Long) : AppRoute("目录")
    data class Reader(val bookId: Long, val index: Int) : AppRoute("阅读")
    data class IdeaEditor(val ideaId: Long) : AppRoute("灵感笔记")
}

enum class AppTab(val label: String) {
    SCHEDULE("课表"),
    LEDGER("记账"),
    NOVEL("小说"),
    IDEAS("灵感"),
    BOARD("任务栏"),
    SHORTCUT("软链接"),
    BROWSER("浏览器"),
    REPO("仓库")
}

/** 底部模块的默认顺序（可在设置里隐藏/调序） */
val defaultModuleOrder: List<AppTab> = listOf(
    AppTab.SCHEDULE, AppTab.BOARD, AppTab.LEDGER, AppTab.NOVEL,
    AppTab.IDEAS, AppTab.SHORTCUT, AppTab.BROWSER, AppTab.REPO
)

fun tabOf(route: AppRoute): AppTab = when (route) {
    AppRoute.Board -> AppTab.BOARD
    AppRoute.Shortcut -> AppTab.SHORTCUT
    AppRoute.Browser -> AppTab.BROWSER
    AppRoute.Repo -> AppTab.REPO
    AppRoute.Ledger -> AppTab.LEDGER
    AppRoute.Novel -> AppTab.NOVEL
    AppRoute.Ideas -> AppTab.IDEAS
    else -> AppTab.SCHEDULE
}

fun routeOf(tab: AppTab): AppRoute = when (tab) {
    AppTab.SCHEDULE -> AppRoute.Schedule
    AppTab.LEDGER -> AppRoute.Ledger
    AppTab.NOVEL -> AppRoute.Novel
    AppTab.IDEAS -> AppRoute.Ideas
    AppTab.BOARD -> AppRoute.Board
    AppTab.SHORTCUT -> AppRoute.Shortcut
    AppTab.BROWSER -> AppRoute.Browser
    AppTab.REPO -> AppRoute.Repo
}

fun routeForNotificationTarget(target: String?): AppRoute =
    if (target != null && (target.startsWith("todo:") || target.startsWith("task:"))) AppRoute.Board
    else AppRoute.Schedule

/** 把路由压成一个可保存的字符串 */
fun routeName(route: AppRoute): String = when (route) {
    AppRoute.Schedule -> "schedule"
    AppRoute.Ledger -> "ledger"
    AppRoute.Todo -> "todo"
    AppRoute.Novel -> "novel"
    AppRoute.Stats -> "stats"
    AppRoute.Slots -> "slots"
    AppRoute.Settings -> "settings"
    AppRoute.Sources -> "sources"
    AppRoute.Ideas -> "ideas"
    AppRoute.Board -> "board"
    AppRoute.Shortcut -> "shortcut"
    AppRoute.Browser -> "browser"
    AppRoute.Repo -> "repo"
    is AppRoute.Toc -> "toc:${route.bookId}"
    is AppRoute.Reader -> "reader:${route.bookId}:${route.index}"
    is AppRoute.IdeaEditor -> "ideaEditor:${route.ideaId}"
}

fun routeFromName(name: String?): AppRoute? {
    if (name == null) return null
    val parts = name.split(":")
    return when (parts.first()) {
        "schedule" -> AppRoute.Schedule
        "ledger" -> AppRoute.Ledger
        "todo" -> AppRoute.Todo
        "novel" -> AppRoute.Novel
        "ideas" -> AppRoute.Ideas
        "board" -> AppRoute.Board
        "shortcut" -> AppRoute.Shortcut
        "browser" -> AppRoute.Browser
        "repo" -> AppRoute.Repo
        "stats" -> AppRoute.Stats
        "slots" -> AppRoute.Slots
        "settings" -> AppRoute.Settings
        "sources" -> AppRoute.Sources
        "toc" -> parts.getOrNull(1)?.toLongOrNull()?.let { AppRoute.Toc(it) }
        "reader" -> {
            val bookId = parts.getOrNull(1)?.toLongOrNull() ?: return null
            val index = parts.getOrNull(2)?.toIntOrNull() ?: 0
            AppRoute.Reader(bookId, index)
        }
        "ideaEditor" -> parts.getOrNull(1)?.toLongOrNull()?.let { AppRoute.IdeaEditor(it) }
        else -> null
    }
}
