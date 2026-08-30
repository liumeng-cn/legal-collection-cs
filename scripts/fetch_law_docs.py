#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""抓取金融/信贷相关法律法规，生成知识库 document 表幂等 INSERT SQL。

用法:
    python scripts/fetch_law_docs.py                 # 抓取并输出 SQL + 原始文本
    python scripts/fetch_law_docs.py --out-dir out   # 指定输出目录

输出:
    <out>/law_docs.sql   幂等 SQL（\\c knowledge / DELETE / INSERT / setval）
    <out>/raw/*.txt      各法规清洗后的纯文本，便于人工核对

依赖: lxml（标准库 urllib / ssl，无需 requests）。
文档 ID 区间 100-199 预留给法律法规，避免与种子数据(1-5)/测试数据(6-17)冲突。
"""

import argparse
import re
import ssl
import sys
import urllib.error
import urllib.request
from pathlib import Path

from lxml import html as lxml_html

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"
)

# 法律法规 ID 起始值，区间 [LAW_ID_BASE, LAW_ID_BASE + 99]
LAW_ID_BASE = 100

# (标题, URL, 正文 XPath 表达式(可为 None 走启发式), 说明)
SOURCES = [
    (
        "最高人民法院关于审理民间借贷案件适用法律若干问题的规定",
        "https://www.hncourt.gov.cn/public/detail.php?id=182105",
        "//*[contains(@class,'detail_content')]",
        "法释〔2015〕18号，经2020年修正，现行有效（LPR 四倍规则）",
    ),
    (
        "征信业管理条例",
        "https://www.gov.cn/zhengce/2013-01/29/content_2602614.htm",
        None,
        "国务院令第631号，自2013年3月15日起施行",
    ),
    (
        "中华人民共和国个人信息保护法",
        "http://www.npc.gov.cn/npc/c2/c30834/202108/t20210820_313088.html",
        None,
        "主席令第九十一号，自2021年11月1日起施行",
    ),
    (
        "最高人民法院关于审理银行卡民事纠纷案件若干问题的规定",
        "https://www.court.gov.cn/zixun/xiangqing/304771.html",
        None,
        "法释〔2021〕10号，自2021年5月25日起施行",
    ),
]


def fetch(url: str) -> str:
    """抓取网页源码，优先默认 SSL 校验，失败降级为不校验并告警。"""
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.read().decode("utf-8", errors="replace")
    except (ssl.SSLError, urllib.error.URLError) as first_err:
        ctx = ssl._create_unverified_context()
        print(f"  [warn] {url} 默认 SSL 失败，降级为不校验重试: {first_err}", file=sys.stderr)
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=30, context=ctx) as resp:
            return resp.read().decode("utf-8", errors="replace")


# 文末署名/来源等噪声标签（冒号结尾避免误伤正文，如“款项来源”）
_BYLINE_RE = re.compile(
    r"(责任\s*编辑|责\s*编|文章\s*出处|来源|编\s*辑|记者|作者|发布时间|发布日期|作者\s*单位)"
    r"[：:][^\n。；！？]*"
)
_NAV_LINE_RE = re.compile(
    r"^(<<|>>|返回|打印|关闭|分享|收藏|上一篇|下一篇|相关文章|相关链接|相关新闻)"
)


_JUNK_LINE_RE = re.compile(r"^(新华微博|人民微博|新浪微博|腾讯微博|微博)$")


def _normalize(text: str) -> str:
    text = text.replace("　", " ").replace("\xa0", " ").replace(" ", " ")
    text = re.sub(r"\.[a-zA-Z][\w-]*\s*\{[^{}]*\}", " ", text)  # 残留 CSS 规则
    text = re.sub(r"【字体[：:][^】]*】", " ", text)  # gov.cn 字号切换控件
    text = _BYLINE_RE.sub(" ", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r" *\n *", "\n", text)
    out = []
    for ln in text.split("\n"):
        ln = ln.strip()
        if not ln:
            continue
        if re.fullmatch(r"[#.][\w\-.#{}\s:;,]*", ln):  # 残留 CSS 行
            continue
        if _NAV_LINE_RE.match(ln) or _JUNK_LINE_RE.match(ln):  # 页面导航/分享按钮残留
            continue
        out.append(ln)
    return "\n".join(out)


def extract_text(html_doc: str, selector: str | None) -> str:
    """从 HTML 提取正文纯文本。

    优先精确 XPath 选择器；否则移除噪声标签后，在 div/article/td 中取文本最长的块。
    """
    tree = lxml_html.fromstring(html_doc)
    for bad in tree.xpath(
        "//script | //style | //noscript | //iframe | //head | //nav | //footer | //header"
    ):
        bad.getparent().remove(bad)

    if selector:
        nodes = tree.xpath(selector)
        if nodes:
            return _normalize(nodes[0].text_content())

    bodies = tree.xpath("//body")
    if bodies:
        candidates = bodies[0].xpath(".//div | .//article | .//td")
        if candidates:
            best = max(candidates, key=lambda e: len(e.text_content()))
            return _normalize(best.text_content())
        return _normalize(bodies[0].text_content())
    return _normalize(tree.text_content())


def _sql_literal(text: str) -> str:
    return "'" + text.replace("'", "''") + "'"


def build_sql(rows: list[dict]) -> str:
    parts = [
        "-- 自动生成：法律法规知识库文档（幂等，可重复执行）",
        "-- 来源：scripts/fetch_law_docs.py",
        "\\c knowledge",
        "",
        f"-- 刷新法律法规文档（id {LAW_ID_BASE}-{LAW_ID_BASE + 99}），保留种子/测试数据",
        f"DELETE FROM document WHERE id >= {LAW_ID_BASE} AND id < {LAW_ID_BASE + 100};",
        "",
    ]
    if rows:
        parts.append("INSERT INTO document (id, title, content, allowed_roles, case_id) VALUES")
        values = []
        for r in rows:
            values.append(
                f"  ({r['id']}, {_sql_literal(r['title'])}, {_sql_literal(r['content'])}, NULL, NULL)"
            )
        parts.append(",\n".join(values))
        parts.append("ON CONFLICT (id) DO NOTHING;")
    parts.append("")
    parts.append(
        "SELECT setval(pg_get_serial_sequence('document', 'id'), (SELECT max(id) FROM document));"
    )
    parts.append("")
    return "\n".join(parts)


def main() -> int:
    parser = argparse.ArgumentParser(description="抓取金融法律法规生成知识库 SQL")
    parser.add_argument("--out-dir", default="out", help="输出目录（默认 out）")
    args = parser.parse_args()

    out_dir = Path(args.out_dir)
    raw_dir = out_dir / "raw"
    raw_dir.mkdir(parents=True, exist_ok=True)

    rows = []
    for idx, (title, url, selector, note) in enumerate(SOURCES, start=1):
        doc_id = LAW_ID_BASE + idx
        print(f"[{idx}/{len(SOURCES)}] 抓取: {title}")
        try:
            html_doc = fetch(url)
            content = extract_text(html_doc, selector)
        except Exception as exc:  # noqa: BLE001 —— 单条失败不阻断整体
            print(f"  [fail] 抓取/解析失败，已跳过: {exc}", file=sys.stderr)
            continue

        if not content:
            print(f"  [fail] 未提取到正文，已跳过: {title}", file=sys.stderr)
            continue

        # 保存原始文本供人工核对
        safe_name = re.sub(r"[\\/:*?\"<>|]", "_", title)[:60]
        (raw_dir / f"{doc_id:03d}_{safe_name}.txt").write_text(content, encoding="utf-8")

        rows.append({"id": doc_id, "title": title, "content": content})
        print(f"  [ok] 正文 {len(content)} 字 -> {raw_dir / f'{doc_id:03d}_{safe_name}.txt'}")

    sql_path = out_dir / "law_docs.sql"
    sql_path.write_text(build_sql(rows), encoding="utf-8")
    print(f"\n已生成 SQL: {sql_path}（共 {len(rows)} 条）")

    if len(rows) < len(SOURCES):
        print(f"警告: {len(SOURCES) - len(rows)} 条抓取失败，请检查网络或手动补录。", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
