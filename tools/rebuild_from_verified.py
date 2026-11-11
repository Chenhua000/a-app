# -*- coding: utf-8 -*-
"""
根据 VerifyBundledSourcesTest 的体检结果，重建内置书源包和文档。

输入（由 gradle 体检测试写出，位于 app/build/）：
    verified_search.json  —— 能搜到书的源（保留集合）
    verified_full.json   —— 搜索+目录+正文都跑通的源（最稳）

产物：
    1) app/src/main/assets/book_sources.json  —— 重新录入 App 的内置包（只保留能用的）
    2) 书源/书源文档.md                       —— 人看的清单（名字/网址/状态/分组）
    3) 控制台打印条数，用于同步 DefaultBookSources.bundledCountHint
"""
import json
import os
import sys
from collections import Counter, OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APP_BUILD = os.path.join(ROOT, "app", "build")
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "book_sources.json")
DOC_DIR = os.path.join(ROOT, "书源")
DOC = os.path.join(DOC_DIR, "书源文档.md")


def load(name):
    path = os.path.join(APP_BUILD, name)
    if not os.path.exists(path):
        return []
    with open(path, encoding="utf-8") as fp:
        return json.load(fp)


def main():
    search_ok = load("verified_search.json")
    full_ok = load("verified_full.json")
    if not search_ok:
        print("没有找到 verified_search.json，先跑 VerifyBundledSourcesTest(VERIFY_SOURCES=1)")
        return 1

    full_keys = {(s.get("name"), s.get("baseUrl")) for s in full_ok}
    # 保留能搜到书的源；标注是否全链路可读 + 失败原因
    kept = []
    for s in search_ok:
        key = (s.get("name"), s.get("baseUrl"))
        entry = OrderedDict()
        for k in ("name", "baseUrl", "searchUrl", "charset", "rules", "group"):
            if k in s:
                entry[k] = s[k]
        entry["_readable"] = bool(s.get("_readable", key in full_keys))
        entry["_reason"] = s.get("_reason", "")
        kept.append(entry)

    os.makedirs(os.path.dirname(ASSETS), exist_ok=True)
    os.makedirs(DOC_DIR, exist_ok=True)

    # 1) 资产文件：去掉内部标注字段
    assets_list = []
    for s in kept:
        a = OrderedDict((k, v) for k, v in s.items() if not k.startswith("_"))
        assets_list.append(a)
    with open(ASSETS, "w", encoding="utf-8") as fp:
        json.dump(assets_list, fp, ensure_ascii=False, separators=(",", ":"))

    # 2) 文档
    readable = sum(1 for s in kept if s["_readable"])
    groups = Counter((s.get("group") or "未分组") for s in kept)
    by_group = {}
    for s in kept:
        by_group.setdefault(s.get("group") or "未分组", []).append(s)

    lines = []
    lines.append("# 小说书源文档（已联网验证）")
    lines.append("")
    lines.append("本文件由真机联网体检生成：**只保留能搜到书的书源**，"
                 "失效/改版/需要 JS 的源已全部剔除。")
    lines.append("")
    lines.append("## 统计")
    lines.append("")
    lines.append("| 项目 | 数量 |")
    lines.append("|---|---|")
    lines.append(f"| 已验证可用（能搜到书） | {len(kept)} |")
    lines.append(f"| 其中全链路可读（搜+目录+正文） | {readable} |")
    lines.append("")
    lines.append("分组分布：")
    lines.append("")
    for g, c in groups.most_common():
        lines.append(f"- {g}：{c} 个")
    lines.append("")
    lines.append("> 状态说明：**✓ 可读** = 搜索/目录/正文全链路验证通过；"
                 "**仅能搜** = 能返回书籍列表，但读正文时失败（站点改版/较慢，点击书籍可能打不开，可在书源里停用）。")
    lines.append("")
    lines.append("## 已验证可用的书源（按分组）")
    lines.append("")
    idx = 0
    for g in sorted(by_group, key=lambda x: (-len(by_group[x]), x)):
        items = by_group[g]
        lines.append(f"### {g}（{len(items)} 个）")
        lines.append("")
        lines.append("| # | 书源名 | 网址 | 状态 |")
        lines.append("|---|---|---|---|")
        for s in items:
            idx += 1
            status = "✓ 可读" if s["_readable"] else "仅能搜"
            lines.append(f"| {idx} | {s['name']} | {s['baseUrl']} | {status} |")
        lines.append("")

    with open(DOC, "w", encoding="utf-8") as fp:
        fp.write("\n".join(lines))

    print(f"重建完成：保留 {len(kept)} 个（其中全链路可读 {readable} 个）")
    print(f"资产文件：{ASSETS}")
    print(f"文档：{DOC}")
    print(f"→ 请把 DefaultBookSources.bundledCountHint 改成 {len(kept)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
