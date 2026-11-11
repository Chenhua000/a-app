# -*- coding: utf-8 -*-
"""
把「阅读 / Legado」导出的书源 JSON 转成校园助手 App 能用的书源格式。

用法：
    python tools/convert_booksources.py <源文件.json> [输出目录]

产出：
    1) 书源文档.md          —— 人看的清单（名字 / 网址 / 分组 / 是否可用 / 不可用原因）
    2) book_sources_app.json —— 可直接粘进 App「书源 → 导入」的 JSON
    3) stats.txt             —— 统计摘要

设计原则：App 的规则引擎只有「CSS 选择器 + @属性 + || 备用 + 正则替换」，
不执行任何 JavaScript，所以凡是依赖 JS / 变量 / POST 的源一律剔除，宁缺毋滥。
"""
import json
import os
import re
import sys
from collections import Counter, OrderedDict

# 依赖 JS 或引擎无法执行的语法
JS_MARKERS = ("<js>", "@js:", "js:", "<JS>", "@JS:", "java.", "{{", "}}", "$.", "<%%>", "%%>", "source.")
# Legado 在 selector 里用到的、App 暂不支持的组合语法
UNSUPPORTED_SELECTOR = ("&&", "%%", "@css:", "children", "ownText")


def clean_base_url(raw: str) -> str:
    """bookSourceUrl 里可能带 #分组、##备注、多余的 '/' 或缺少协议"""
    url = (raw or "").strip()
    # Legado 允许在网址后面用 # 写分组/备注
    for sep in ("##", "#"):
        if sep in url:
            url = url.split(sep)[0]
    url = url.strip()
    if not url:
        return ""
    if not url.startswith(("http://", "https://")):
        url = "http://" + url
    return url.rstrip("/")


def uses_js(*rules) -> bool:
    blob = " ".join([r for r in rules if isinstance(r, str)])
    return any(m in blob for m in JS_MARKERS)


def uses_js_in_search_url(url: str) -> bool:
    """搜索地址里 {{key}} 是关键词占位符，先摘掉再判断有没有 JS"""
    stripped = re.sub(r"\{\{?\s*(key|searchKey)\s*\}?\}", "", url or "")
    return any(m in stripped for m in JS_MARKERS)


def uses_unsupported_selector(*rules) -> bool:
    blob = " ".join([r for r in rules if isinstance(r, str)])
    return any(m in blob for m in UNSUPPORTED_SELECTOR)


def has_index_syntax(rule: str) -> bool:
    """Legado 的 tag.2 / tag.-1 / tag.1:3 索引写法"""
    return bool(re.search(r"\.-?\d+(:-?\d+)?(?=@|$)", rule or ""))


def strip_inline_regex(rule: str):
    """
    Legado 的 `##` 后缀：selector##正则（删掉匹配内容）或 selector##正则##替换。
    返回 (纯选择器, [正则...])。正文规则把正则留下做替换，其他字段只要选择器。
    """
    if not rule or "##" not in rule:
        return (rule or "").strip(), []
    parts = rule.split("##")
    selector = parts[0].strip()
    patterns = [p.strip() for p in parts[1:] if p.strip()]
    return selector, patterns


def normalize_attr(rule: str) -> str:
    """@textNode（单数）是 @textNodes 的别名；还有常见的 @allText"""
    rule = re.sub(r"@textNode(?=[\s|]|$)", "@textNodes", rule)
    rule = re.sub(r"@allText(?=[\s|]|$)", "@textNodes", rule)
    return rule


# Legado 里单独写 `href` / `src` / `text` / `html` 表示「取当前元素的该属性」，
# 等价于 `@href` 等；App 引擎会把「@ 之前的部分」当 CSS 选择器，所以裸属性必须补 @。
BARE_ATTR = {"href", "src", "text", "html", "textnodes", "owntext", "alltext"}


def normalize_bare_attr(rule: str) -> str:
    text = rule.strip()
    if text.lower() in BARE_ATTR:
        return "@" + text.lower()
    return rule


XPATH_ID = re.compile(r"^//(?:\*|\w+)\[@id=[\"']([^\"']+)[\"']\](?:/+(.*))?$")
XPATH_CLASS = re.compile(r"^//(?:\*|\w+)\[(?:@class=[\"']([^\"']+)[\"']|contains\(@class,\s*[\"']([^\"']+)[\"']\))\](?:/+(.*))?$")
XPATH_TEXT_TAIL = re.compile(r"/text\(\)$")
XPATH_ATTR_TAIL = re.compile(r"/@([\w-]+)$")


def xpath_to_css(rule: str):
    """把常见 XPath 片段翻译成 CSS，翻不了的返回 None（这个源就淘汰）"""
    text = rule.strip()
    if not (text.startswith("//") or "/text()" in text):
        return text
    attr = ""
    m = XPATH_TEXT_TAIL.search(text)
    if m:
        attr = "@text"
        text = XPATH_TEXT_TAIL.sub("", text)
    else:
        m = XPATH_ATTR_TAIL.search(text)
        if m and "[" not in text:
            # //div/@href 这种整个就是取属性
            attr = "@" + m.group(1)
            text = XPATH_ATTR_TAIL.sub("", text)

    id_match = XPATH_ID.match(text)
    if id_match:
        tail = (id_match.group(2) or "").strip("/")
        css = "#" + id_match.group(1)
        if tail and re.fullmatch(r"\w+(/>?/?\w+)*", tail):
            css += " " + tail.replace("/", " ")
        return css + attr

    class_match = XPATH_CLASS.match(text)
    if class_match:
        cls = (class_match.group(1) or class_match.group(2) or "").split()[0]
        tail = (class_match.group(3) or "").strip("/")
        css = "." + cls
        if tail and re.fullmatch(r"\w+(/>?/?\w+)*", tail):
            css += " " + tail.replace("/", " ")
        return css + attr

    return None  # 位置型 XPath（//div[5]/div/...）没法可靠翻译


def clean_rule(rule: str, keep_regex: bool = False):
    """统一清洗一条规则：去内联正则、统一取值后缀、裸属性补 @、翻 XPath"""
    selector, patterns = strip_inline_regex(rule)
    selector = normalize_attr(selector)
    if selector and (selector.startswith("//") or "/text()" in selector):
        selector = xpath_to_css(selector) or ""
    if not selector:
        return "", []
    selector = normalize_bare_attr(selector)
    return selector, (patterns if keep_regex else [])


def parse_replace_regex(text: str):
    """Legado 的 replaceRegex：每行 `正则##替换值`，行之间用换行或 && 连接"""
    result = []
    if not text:
        return result
    normalized = (text or "").replace("&&", "\n")
    for line in normalized.split("\n"):
        line = line.strip()
        if not line or "##" not in line:
            continue
        pattern, _, replacement = line.partition("##")
        pattern = pattern.strip()
        if pattern:
            result.append({"from": pattern, "to": replacement})
    return result[:12]  # 正则替换很吃性能，最多留 12 条


def build_search_url(base: str, search_url: str):
    """
    把 Legado 的搜索地址整理成 App 能直接 GET 的绝对地址。
    返回 (url, charset)；url=None 表示这个源 App 用不了。
    Legado 允许 `地址,{ "charset": "gbk", "method": "post", ... }` 的配置后缀：
    纯 charset 的 GET 配置可以吃（App 支持按编码发关键词），POST 的一律剔除。
    """
    if not search_url:
        return None, "utf-8"
    url = search_url.strip()
    charset = "utf-8"

    config_match = re.search(r",\s*(\{.*\})\s*$", url, re.DOTALL)
    if config_match:
        try:
            config = json.loads(config_match.group(1))
        except ValueError:
            return None, charset
        if str(config.get("method", "get")).lower() != "get":
            return None, charset  # POST 搜索 App 引擎不支持
        charset = str(config.get("charset", "utf-8")).lower().replace("gb2312", "gbk")
        url = url[: config_match.start()].strip()

    # 只取 || 备用里的第一段
    url = url.split("||")[0].strip()
    if "{{key}}" not in url and "{key}" not in url:
        # 老版本 Legado 用 searchKey
        if "searchKey" in url or "{{" in url:
            url = url.replace("searchKey", "{{key}}")
        else:
            return None, charset
    if url.startswith("//"):
        url = "https:" + url
    elif url.startswith("/"):
        url = base + url
    elif not url.startswith(("http://", "https://")):
        url = base + "/" + url
    return url, charset


def convert(item: dict):
    """返回 (转换后的 App 书源, 不可用原因)"""
    name = (item.get("bookSourceName") or "").strip()
    raw_url = (item.get("bookSourceUrl") or "").strip()
    base = clean_base_url(raw_url)

    if not name:
        return None, "缺少书源名"
    if not re.match(r"^https?://[^\s]+$", base):
        return None, f"网址格式异常（{raw_url}）"
    if not item.get("enabled", True):
        return None, "源里已停用"

    rs = item.get("ruleSearch") or {}
    rt = item.get("ruleToc") or {}
    rc = item.get("ruleContent") or {}
    rbi = item.get("ruleBookInfo") or {}
    search_url = item.get("searchUrl") or ""

    critical = {
        "search.list": rs.get("bookList", ""),
        "search.name": rs.get("name", ""),
        "search.url": rs.get("bookUrl", ""),
        "toc.list": rt.get("chapterList", ""),
        "toc.name": rt.get("chapterName", ""),
        "toc.url": rt.get("chapterUrl", ""),
        "content": rc.get("content", ""),
        "searchUrl": search_url,
    }

    missing = [k for k, v in critical.items() if not str(v).strip()]
    if missing:
        return None, "缺少关键规则：" + "、".join(missing)

    selector_rules = [v for k, v in critical.items() if k != "searchUrl"]
    if uses_js(*selector_rules):
        return None, "依赖 JavaScript，App 引擎不支持"
    if uses_js_in_search_url(search_url):
        return None, "搜索地址依赖 JavaScript 拼装"
    if uses_unsupported_selector(*selector_rules):
        return None, "用了 App 不支持的组合选择器（&& / %% / @css 等）"

    url, charset = build_search_url(base, search_url)
    if url is None:
        return None, "搜索地址需要 POST 或不是关键词搜索"

    source = OrderedDict()
    source["name"] = name
    source["baseUrl"] = base
    source["searchUrl"] = url
    source["charset"] = charset
    # 统一清洗：去内联正则、统一取值后缀、翻 XPath。正文规则把 ##正则 留下来做替换。
    search_list, _ = clean_rule(rs.get("bookList", ""))
    search_name, _ = clean_rule(rs.get("name") or "@text")
    search_author, _ = clean_rule(rs.get("author") or "")
    search_cover, _ = clean_rule(rs.get("coverUrl") or "")
    search_summary, _ = clean_rule(rs.get("intro") or "")
    search_url_field, _ = clean_rule(rs.get("bookUrl") or "@href")
    toc_page, _ = clean_rule(rbi.get("tocUrl") or "")
    toc_list, _ = clean_rule(rt.get("chapterList", ""))
    toc_name, _ = clean_rule(rt.get("chapterName") or "@text")
    toc_url, _ = clean_rule(rt.get("chapterUrl") or "@href")
    content_selector, content_regex = clean_rule(rc.get("content", ""), keep_regex=True)

    # 清洗后关键规则没了（比如 XPath 翻不出来），这个源放弃
    if not all([search_list, search_name, search_url_field, toc_list, toc_name, toc_url, content_selector]):
        return None, "规则用了翻不了的 XPath 或清洗后为空"

    rules = OrderedDict()
    rules["search"] = OrderedDict(
        [
            ("list", search_list),
            ("name", search_name),
            ("author", search_author),
            ("cover", search_cover),
            ("summary", search_summary),
            ("url", search_url_field),
        ]
    )
    rules["toc"] = OrderedDict(
        [
            ("pageUrl", toc_page),
            ("list", toc_list),
            ("name", toc_name),
            ("url", toc_url),
            ("contains", ""),
            ("remove", []),
        ]
    )
    replace_pairs = [{"from": p, "to": ""} for p in content_regex[:12]]
    replace_pairs += parse_replace_regex(rc.get("replaceRegex"))
    rules["content"] = OrderedDict(
        [
            ("selector", content_selector),
            ("remove", []),
            ("replace", replace_pairs[:12]),
        ]
    )
    source["rules"] = rules
    source["group"] = (item.get("bookSourceGroup") or "").strip()
    source["_indexSyntax"] = any(has_index_syntax(v) for v in critical.values())
    return source, None


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    src = sys.argv[1]
    out_dir = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(src))
    os.makedirs(out_dir, exist_ok=True)

    with open(src, encoding="utf-8") as fp:
        data = json.load(fp)

    converted, rejected = [], []
    for item in data:
        source, reason = convert(item)
        if source:
            converted.append(source)
        else:
            rejected.append(
                {
                    "name": (item.get("bookSourceName") or "").strip() or "(无名)",
                    "url": (item.get("bookSourceUrl") or "").strip(),
                    "group": (item.get("bookSourceGroup") or "").strip(),
                    "reason": reason,
                }
            )

    # 去重：同名同站、或搜索地址完全相同的，只留第一条
    seen_name, seen_url, unique = set(), set(), []
    for s in converted:
        name_key = (s["name"], s["baseUrl"])
        if name_key in seen_name or s["searchUrl"] in seen_url:
            continue
        seen_name.add(name_key)
        seen_url.add(s["searchUrl"])
        unique.append(s)

    reasons = Counter(r["reason"].split("：")[0] for r in rejected)
    groups = Counter(s["group"] or "未分组" for s in unique)

    with open(os.path.join(out_dir, "book_sources_app.json"), "w", encoding="utf-8") as fp:
        json.dump(unique, fp, ensure_ascii=False, indent=2)

    with open(os.path.join(out_dir, "stats.txt"), "w", encoding="utf-8") as fp:
        fp.write(f"源文件：{os.path.basename(src)}\n")
        fp.write(f"总条数：{len(data)}\n")
        fp.write(f"可转换：{len(converted)}（去重后 {len(unique)}）\n")
        fp.write(f"剔除：{len(rejected)}\n\n剔除原因分布：\n")
        for reason, count in reasons.most_common():
            fp.write(f"  {reason}: {count}\n")
        fp.write("\n分组分布：\n")
        for group, count in groups.most_common(30):
            fp.write(f"  {group}: {count}\n")

    write_document(out_dir, data, unique, rejected, reasons)

    print(f"总条数 {len(data)} → 可用 {len(unique)}（去重前 {len(converted)}），剔除 {len(rejected)}")
    print("剔除原因：")
    for reason, count in reasons.most_common():
        print(f"  {reason}: {count}")
    return 0


def write_document(out_dir, data, unique, rejected, reasons):
    lines = []
    lines.append("# 小说书源文档")
    lines.append("")
    lines.append(
        f"由 `bookSource(1) (1).json`（共 {len(data)} 条 Legado 书源）自动转换生成。"
    )
    lines.append("")
    lines.append("## 转换说明")
    lines.append("")
    lines.append(
        "- 校园助手的规则引擎支持 **标准 CSS 选择器**，也支持「阅读 / Legado」的常见写法："
        "`id.xxx` / `class.xxx` / `tag.div` / 下标 `a.2`、`a.-1`、`a.1:3` / 排除 `tr!0` / "
        "链式查找 `a@b` / 取值 `@text`、`@html`、`@href`、`@textNodes`。"
    )
    lines.append(
        "- 但引擎**不执行 JavaScript**，所以依赖 JS 解密、变量运算、POST 请求登录的书源一律剔除。"
    )
    lines.append(
        "- `book_sources_app.json` 是可直接导入 App 的文件：**小说 → 书源 → 导入**，"
        "把文件内容整段粘进去即可（或用「一键导入内置书源包」按钮）。"
    )
    lines.append(
        "- 「网址」列已去掉 Legado 里 `##备注`、`#分组` 这类后缀，并补上缺失的协议头。"
    )
    lines.append("")
    lines.append("## 统计")
    lines.append("")
    lines.append("| 项目 | 数量 |")
    lines.append("|---|---|")
    lines.append(f"| 原始书源 | {len(data)} |")
    lines.append(f"| 可导入 App（去重后） | {len(unique)} |")
    lines.append(f"| 被剔除 | {len(rejected)} |")
    lines.append("")
    lines.append("剔除原因分布：")
    lines.append("")
    for reason, count in reasons.most_common():
        lines.append(f"- {reason}：{count} 条")
    lines.append("")

    lines.append("## 一、可导入的书源（按分组）")
    lines.append("")
    by_group = {}
    for s in unique:
        by_group.setdefault(s["group"] or "未分组", []).append(s)
    index = 0
    for group in sorted(by_group, key=lambda g: (-len(by_group[g]), g)):
        items = by_group[group]
        lines.append(f"### {group}（{len(items)} 条）")
        lines.append("")
        lines.append("| # | 书源名 | 网址 |")
        lines.append("|---|---|---|")
        for s in items:
            index += 1
            lines.append(f"| {index} | {s['name']} | {s['baseUrl']} |")
        lines.append("")

    lines.append("## 二、未收录的书源")
    lines.append("")
    lines.append("| # | 书源名 | 原网址 | 原因 |")
    lines.append("|---|---|---|---|")
    for i, r in enumerate(rejected, 1):
        lines.append(f"| {i} | {r['name']} | `{r['url']}` | {r['reason']} |")
    lines.append("")

    with open(os.path.join(out_dir, "书源文档.md"), "w", encoding="utf-8") as fp:
        fp.write("\n".join(lines))


if __name__ == "__main__":
    sys.exit(main())
