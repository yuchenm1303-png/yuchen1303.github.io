from __future__ import annotations

import html
import json
import re
from copy import deepcopy
from datetime import datetime, timezone
from threading import Lock
from time import monotonic
from typing import Any, Callable
from urllib.parse import urljoin

import httpx
from fastapi import HTTPException, Query

import market_home_server as market_home


app = market_home.app
legacy = market_home.legacy

DISCUSSION_LIST_PATH = "/api/stock/a-share/discussions"
DISCUSSION_DETAIL_PATH = "/api/stock/a-share/discussion/detail"
DISCUSSION_CACHE_VERSION = "v2-eastmoney-guba-comment-compat"
DISCUSSION_LIST_FRESH_SECONDS = 45.0
DISCUSSION_DETAIL_FRESH_SECONDS = 120.0
DISCUSSION_STALE_SECONDS = 6 * 60 * 60.0
DISCUSSION_MAX_PAGE = 20
DISCUSSION_MAX_PAGE_SIZE = 30
GUBA_BASE_URL = "https://guba.eastmoney.com/"
GUBA_HEADERS = {
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.7",
    "Referer": GUBA_BASE_URL,
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/124.0.0.0 Safari/537.36"
    ),
}

_LOCKS_GUARD = Lock()
_DISCUSSION_LOCKS: dict[str, Lock] = {}

_TAG_RE = re.compile(r"<[^>]+>", re.S)
_SCRIPT_RE = re.compile(r"<(script|style)\b[^>]*>.*?</\1\s*>", re.I | re.S)
_SPACE_RE = re.compile(r"[\t\r\f\v ]+")
_BLANK_RE = re.compile(r"\n{3,}")
_DIV_TOKEN_RE = re.compile(r"<div\b[^>]*>|</div\s*>", re.I)
_COMMENT_CONTAINER_KEYS = {
    "comments",
    "commentlist",
    "comment_list",
    "replylist",
    "reply_list",
    "replydata",
    "reply_data",
    "replies",
    "replys",
}
_COMMENT_CONTENT_KEYS = (
    "content",
    "commentContent",
    "comment_content",
    "replyContent",
    "reply_content",
    "replyText",
    "reply_text",
    "replyTitle",
    "reply_title",
    "text",
)
_COMMENT_AUTHOR_KEYS = (
    "author",
    "userName",
    "user_name",
    "nickname",
    "nickName",
    "replyUser",
    "reply_user",
    "reuserName",
    "reuser_name",
)
_COMMENT_ID_KEYS = (
    "commentId",
    "comment_id",
    "replyId",
    "reply_id",
    "id",
)
_COMMENT_TIME_KEYS = (
    "publishedAt",
    "published_at",
    "publishTime",
    "publish_time",
    "replyTime",
    "reply_time",
    "createTime",
    "create_time",
    "time",
)
_COMMENT_LIKE_KEYS = (
    "likeCount",
    "like_count",
    "likeNum",
    "like_num",
    "agreeCount",
    "agree_count",
)


def _lock_for(key: str) -> Lock:
    with _LOCKS_GUARD:
        return _DISCUSSION_LOCKS.setdefault(key, Lock())


def _clean_text(fragment: Any, limit: int = 20000) -> str:
    raw = str(fragment or "")
    raw = _SCRIPT_RE.sub("", raw)
    raw = re.sub(r"<(br|hr)\b[^>]*>", "\n", raw, flags=re.I)
    raw = re.sub(r"</(p|div|li|h1|h2|h3|tr)\s*>", "\n", raw, flags=re.I)
    raw = _TAG_RE.sub("", raw)
    raw = html.unescape(raw).replace("\u200b", "").replace("\xa0", " ")
    lines = [_SPACE_RE.sub(" ", line).strip() for line in raw.splitlines()]
    cleaned = "\n".join(line for line in lines if line)
    cleaned = _BLANK_RE.sub("\n\n", cleaned).strip()
    return cleaned[:limit]


def _count_value(value: Any) -> int:
    text = _clean_text(value, 32).replace(",", "").replace("，", "")
    if not text or text in {"-", "--"}:
        return 0
    match = re.search(r"([0-9]+(?:\.[0-9]+)?)\s*([万亿]?)", text)
    if not match:
        return 0
    amount = float(match.group(1))
    unit = match.group(2)
    if unit == "万":
        amount *= 10000
    elif unit == "亿":
        amount *= 100000000
    return max(int(amount), 0)


def _class_tokens(opening_tag: str) -> set[str]:
    match = re.search(r'class\s*=\s*["\']([^"\']*)["\']', opening_tag, re.I)
    return set(match.group(1).split()) if match else set()


def _balanced_tag_blocks(source: str, tag_name: str, class_token: str) -> list[str]:
    tag = re.escape(tag_name)
    token_re = re.compile(rf"<{tag}\b[^>]*>|</{tag}\s*>", re.I)
    blocks: list[str] = []
    for opening in re.finditer(rf"<{tag}\b[^>]*>", source, re.I):
        opening_tag = opening.group(0)
        if class_token not in _class_tokens(opening_tag):
            continue
        depth = 1
        end = opening.end()
        for token in token_re.finditer(source, opening.end()):
            if not token.group(0).lower().startswith("</"):
                depth += 1
            else:
                depth -= 1
            if depth == 0:
                end = token.start()
                break
        blocks.append(source[opening.end():end])
    return blocks


def _balanced_div_blocks(source: str, class_token: str) -> list[str]:
    blocks: list[str] = []
    for opening in re.finditer(r"<div\b[^>]*>", source, re.I):
        opening_tag = opening.group(0)
        if class_token not in _class_tokens(opening_tag):
            continue
        depth = 1
        end = opening.end()
        for token in _DIV_TOKEN_RE.finditer(source, opening.end()):
            if token.group(0).lower().startswith("<div"):
                depth += 1
            else:
                depth -= 1
            if depth == 0:
                end = token.start()
                break
        blocks.append(source[opening.end():end])
    return blocks


def _first_balanced_div(source: str, class_token: str) -> str:
    blocks = _balanced_div_blocks(source, class_token)
    return blocks[0] if blocks else ""


def _first_class_text(source: str, class_tokens: tuple[str, ...], limit: int = 500) -> str:
    for token in class_tokens:
        div = _first_balanced_div(source, token)
        if div:
            text = _clean_text(div, limit)
            if text:
                return text
        pattern = re.compile(
            rf'<(?:span|a|p|li)\b[^>]*class=["\'][^"\']*\b{re.escape(token)}\b[^"\']*["\'][^>]*>(.*?)</(?:span|a|p|li)\s*>',
            re.I | re.S,
        )
        match = pattern.search(source)
        if match:
            text = _clean_text(match.group(1), limit)
            if text:
                return text
    return ""


def _first_date_text(source: str) -> str:
    preferred = _first_class_text(source, ("pubtime", "publishtime", "time", "update"), 80)
    target = preferred or _clean_text(source, 1000)
    match = re.search(
        r"(?:20\d{2}[-/.]\d{1,2}[-/.]\d{1,2}\s+)?\d{1,2}:\d{2}(?::\d{2})?|20\d{2}[-/.]\d{1,2}[-/.]\d{1,2}",
        target,
    )
    return match.group(0) if match else preferred


def _absolute_guba_url(value: str) -> str:
    raw = html.unescape(str(value or "").strip())
    if raw.startswith("//"):
        return f"https:{raw}"
    return urljoin(GUBA_BASE_URL, raw)


def _resolve_security(query: str) -> dict[str, str]:
    keyword = query.strip()
    if not keyword:
        raise HTTPException(status_code=400, detail="股票代码或名称不能为空")
    try:
        security = legacy._resolve_security(market_home._get_shared_client(), keyword)
    except Exception as exc:
        raise HTTPException(status_code=404, detail=f"未找到股票：{query}") from exc
    code = str(security.get("code") or "").strip()
    if not (len(code) == 6 and code.isdigit()):
        raise HTTPException(status_code=400, detail=f"股吧暂不支持该证券：{query}")
    return {
        "code": code,
        "name": str(security.get("name") or code).strip(),
        "market": str(security.get("market") or "A股").strip(),
    }


def _list_page_url(code: str, page: int) -> str:
    return f"{GUBA_BASE_URL}list,{code},f_{page}.html"


def _post_page_url(code: str, post_id: str) -> str:
    return f"{GUBA_BASE_URL}news,{code},{post_id}.html"


def _get_html(url: str, timeout: float = 7.5) -> str:
    response = market_home._get_shared_client().get(
        url,
        headers=GUBA_HEADERS,
        timeout=timeout,
    )
    response.raise_for_status()
    text = response.text
    if not text or "访问过于频繁" in text or "请输入验证码" in text:
        raise ValueError("东方财富股吧触发访问限制")
    return text


def _post_kind(fragment: str) -> str:
    plain = _clean_text(fragment, 160)
    for label, value in (("公告", "announcement"), ("研报", "research"), ("资讯", "news"), ("问董秘", "qa")):
        if label in plain:
            return value
    return "discussion"


def _parse_list_rows(source: str, code: str) -> list[dict[str, Any]]:
    rows = re.findall(
        r'<tr\b[^>]*class=["\'][^"\']*\blistitem\b[^"\']*["\'][^>]*>(.*?)</tr\s*>',
        source,
        re.I | re.S,
    )
    if not rows:
        rows = _balanced_div_blocks(source, "listitem")

    posts: list[dict[str, Any]] = []
    seen: set[str] = set()
    for row in rows:
        cells = re.findall(r"<td\b[^>]*>(.*?)</td\s*>", row, re.I | re.S)
        if len(cells) < 3:
            continue
        title_cell = cells[2]
        link_matches = re.findall(
            r'<a\b[^>]*href=["\']([^"\']+)["\'][^>]*>(.*?)</a\s*>',
            title_cell,
            re.I | re.S,
        )
        link = next(
            ((href, label) for href, label in link_matches if "news," in href and ".html" in href),
            None,
        )
        if link is None:
            continue
        href, title_html = link
        post_match = re.search(r"news,[^,]+,(\d+)\.html", href, re.I)
        if not post_match:
            continue
        post_id = post_match.group(1)
        if post_id in seen:
            continue
        seen.add(post_id)
        title = _clean_text(title_html, 300)
        if not title:
            continue
        read_count = _count_value(cells[0]) if cells else 0
        comment_count = _count_value(cells[1]) if len(cells) > 1 else 0
        author = _clean_text(cells[3], 80) if len(cells) > 3 else ""
        updated_at = _first_class_text(title_cell, ("pub_time", "update"), 80)
        if not updated_at:
            updated_at = _first_date_text(title_cell)
        posts.append(
            {
                "postId": post_id,
                "stockCode": code,
                "title": title,
                "author": author or "股吧用户",
                "updatedAt": updated_at,
                "readCount": read_count,
                "commentCount": comment_count,
                "kind": _post_kind(title_cell),
                "hasImage": "[图片]" in title or "<img" in title_cell.lower(),
                "sourceUrl": _absolute_guba_url(href),
            }
        )
    return posts


def _extract_title(source: str) -> str:
    for token in ("newstitle", "article-title", "news_title"):
        value = _first_class_text(source, (token,), 500)
        if value:
            return value
    match = re.search(r"<h1\b[^>]*>(.*?)</h1\s*>", source, re.I | re.S)
    if match:
        return _clean_text(match.group(1), 500)
    match = re.search(r'<meta\b[^>]*property=["\']og:title["\'][^>]*content=["\']([^"\']*)', source, re.I)
    return html.unescape(match.group(1)).strip()[:500] if match else ""


def _extract_post_author(source: str) -> str:
    author_block = _first_balanced_div(source, "author-info") or _first_balanced_div(source, "newsauthor")
    if not author_block:
        return "股吧用户"
    for pattern in (
        r'<a\b[^>]*href=["\'][^"\']*i\.eastmoney\.com[^"\']*["\'][^>]*>(.*?)</a\s*>',
        r'<span\b[^>]*class=["\'][^"\']*(?:author|nickname)[^"\']*["\'][^>]*>(.*?)</span\s*>',
    ):
        match = re.search(pattern, author_block, re.I | re.S)
        if match:
            value = _clean_text(match.group(1), 80)
            if value:
                return value
    text = _clean_text(author_block, 160)
    text = re.sub(r"20\d{2}[-/.].*$", "", text).strip()
    return text[:80] or "股吧用户"


def _extract_body(source: str) -> str:
    body = _first_balanced_div(source, "newstext")
    if not body:
        body = _first_balanced_div(source, "article-body")
    return _clean_text(body, 30000)


def _extract_like_count(source: str) -> int:
    value = _first_class_text(source, ("likemodule", "like_count"), 80)
    return _count_value(value)


def _parse_nested_replies(block: str) -> list[dict[str, Any]]:
    replies: list[dict[str, Any]] = []
    reply_blocks = _balanced_tag_blocks(block, "li", "reply_item_l2")
    if not reply_blocks:
        reply_blocks = _balanced_tag_blocks(block, "div", "reply_item_l2")
    for item in reply_blocks:
        content = _first_class_text(item, ("reply_title_span", "reply_title"), 2000)
        if not content:
            continue
        replies.append(
            {
                "author": _first_class_text(item, ("reuser_l2_nick", "reuser_name", "reuser"), 80) or "股吧用户",
                "content": content,
                "publishedAt": _first_date_text(item),
                "likeCount": _extract_like_count(item),
            }
        )
    return replies[:20]


def _comment_html_blocks(source: str) -> list[str]:
    blocks: list[str] = []
    seen: set[str] = set()
    for token in ("reply_item", "reply-item", "comment_item", "comment-item", "reply_item_l1"):
        for tag_name in ("div", "li"):
            for block in _balanced_tag_blocks(source, tag_name, token):
                signature = block[:240]
                if signature in seen:
                    continue
                seen.add(signature)
                blocks.append(block)
    return blocks


def _parse_html_comments(source: str) -> list[dict[str, Any]]:
    comments: list[dict[str, Any]] = []
    for index, block in enumerate(_comment_html_blocks(source), start=1):
        title_block = _first_balanced_div(block, "reply_title")
        content = _clean_text(title_block, 4000)
        if not content:
            content = _first_class_text(
                block,
                ("reply_title_span", "reply_title", "comment-content", "comment_content"),
                4000,
            )
        if not content:
            continue
        author = _first_class_text(
            block,
            ("reuser_name", "reuser_nick", "reply_user", "reuser", "user_name", "nickname"),
            100,
        ) or "股吧用户"
        id_match = re.search(
            r'(?:data-(?:id|replyid|commentid)|id)\s*=\s*["\']([^"\']+)["\']',
            block,
            re.I,
        )
        replies = _parse_nested_replies(block)
        comments.append(
            {
                "commentId": id_match.group(1) if id_match else f"comment-{index}",
                "author": author,
                "content": content,
                "publishedAt": _first_date_text(block),
                "likeCount": _extract_like_count(block),
                "replyCount": len(replies),
                "replies": replies,
            }
        )
    return comments


def _first_mapping_value(item: dict[str, Any], keys: tuple[str, ...]) -> Any:
    for key in keys:
        value = item.get(key)
        if value is not None and str(value).strip() not in {"", "null", "None"}:
            return value
    return None


def _json_reply(item: dict[str, Any], fallback_id: str) -> dict[str, Any] | None:
    content_value = _first_mapping_value(item, _COMMENT_CONTENT_KEYS)
    content = _clean_text(content_value, 4000)
    if not content:
        return None
    author = _clean_text(_first_mapping_value(item, _COMMENT_AUTHOR_KEYS), 100) or "股吧用户"
    published_at = _clean_text(_first_mapping_value(item, _COMMENT_TIME_KEYS), 80)
    like_count = _count_value(_first_mapping_value(item, _COMMENT_LIKE_KEYS))
    comment_id = _clean_text(_first_mapping_value(item, _COMMENT_ID_KEYS), 100) or fallback_id
    nested: list[dict[str, Any]] = []
    for key, value in item.items():
        if key.lower() not in _COMMENT_CONTAINER_KEYS or not isinstance(value, list):
            continue
        for nested_index, nested_item in enumerate(value, start=1):
            if not isinstance(nested_item, dict):
                continue
            reply = _json_reply(nested_item, f"{comment_id}-reply-{nested_index}")
            if reply is not None:
                nested.append(
                    {
                        "author": reply["author"],
                        "content": reply["content"],
                        "publishedAt": reply["publishedAt"],
                        "likeCount": reply["likeCount"],
                    }
                )
    return {
        "commentId": comment_id,
        "author": author,
        "content": content,
        "publishedAt": published_at,
        "likeCount": like_count,
        "replyCount": len(nested),
        "replies": nested[:20],
    }


def _collect_json_comments(value: Any) -> list[dict[str, Any]]:
    comments: list[dict[str, Any]] = []

    def walk(node: Any, container_hint: bool = False) -> None:
        if isinstance(node, list):
            for item in node:
                if container_hint and isinstance(item, dict):
                    comment = _json_reply(item, f"json-comment-{len(comments) + 1}")
                    if comment is not None:
                        comments.append(comment)
                        continue
                walk(item, container_hint)
            return
        if not isinstance(node, dict):
            return
        for key, child in node.items():
            normalized = key.lower()
            walk(child, normalized in _COMMENT_CONTAINER_KEYS)

    walk(value)
    deduplicated: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in comments:
        signature = f"{item['commentId']}|{item['author']}|{item['content']}"
        if signature in seen:
            continue
        seen.add(signature)
        deduplicated.append(item)
    return deduplicated


def _balanced_json_fragment(source: str, start: int) -> str:
    if start < 0 or start >= len(source) or source[start] not in "[{":
        return ""
    opening = source[start]
    closing = "]" if opening == "[" else "}"
    depth = 0
    in_string = False
    escaped = False
    for index in range(start, len(source)):
        char = source[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == opening:
            depth += 1
        elif char == closing:
            depth -= 1
            if depth == 0:
                return source[start:index + 1]
    return ""


def _embedded_json_values(source: str) -> list[Any]:
    values: list[Any] = []
    scripts = re.findall(r"<script\b[^>]*>(.*?)</script\s*>", source, re.I | re.S)
    for script in scripts:
        decoded = html.unescape(script).strip()
        candidates: list[str] = []
        if decoded.startswith(("{", "[")):
            candidates.append(decoded.rstrip(";"))
        assignment = re.search(
            r"(?:window\.)?(?:__INITIAL_STATE__|__NEXT_DATA__|pageData|replyData|commentData)\s*=\s*",
            decoded,
            re.I,
        )
        if assignment:
            start = next(
                (position for position in range(assignment.end(), len(decoded)) if decoded[position] in "[{"),
                -1,
            )
            fragment = _balanced_json_fragment(decoded, start)
            if fragment:
                candidates.append(fragment)
        for candidate in candidates:
            try:
                values.append(json.loads(candidate))
            except (json.JSONDecodeError, TypeError, ValueError):
                continue

    for key in _COMMENT_CONTAINER_KEYS:
        pattern = re.compile(rf'["\']{re.escape(key)}["\']\s*:\s*', re.I)
        for match in pattern.finditer(source):
            start = next(
                (position for position in range(match.end(), min(len(source), match.end() + 32)) if source[position] in "[{"),
                -1,
            )
            fragment = _balanced_json_fragment(source, start)
            if not fragment:
                continue
            try:
                values.append({key: json.loads(fragment)})
            except (json.JSONDecodeError, TypeError, ValueError):
                continue
    return values


def _parse_embedded_json_comments(source: str) -> list[dict[str, Any]]:
    for value in _embedded_json_values(source):
        comments = _collect_json_comments(value)
        if comments:
            return comments
    return []


def _parse_comments(source: str) -> tuple[list[dict[str, Any]], str]:
    html_comments = _parse_html_comments(source)
    if html_comments:
        return html_comments, "server_html"
    json_comments = _parse_embedded_json_comments(source)
    if json_comments:
        return json_comments, "embedded_json"
    return [], "unavailable"


def _cache_payload(
    key: str,
    fresh_seconds: float,
    loader: Callable[[], dict[str, Any]],
) -> dict[str, Any]:
    fresh = legacy._cache_get_seconds(key, fresh_seconds)
    if fresh is not None:
        payload, age = fresh
        result = deepcopy(payload)
        result["cacheHit"] = True
        result["cacheAgeMs"] = max(int(age * 1000), 0)
        return result
    with _lock_for(key):
        fresh = legacy._cache_get_seconds(key, fresh_seconds)
        if fresh is not None:
            payload, age = fresh
            result = deepcopy(payload)
            result["cacheHit"] = True
            result["cacheAgeMs"] = max(int(age * 1000), 0)
            return result
        try:
            payload = loader()
        except Exception:
            stale = legacy._cache_get_seconds(key, DISCUSSION_STALE_SECONDS)
            if stale is not None:
                payload, age = stale
                result = deepcopy(payload)
                result["status"] = "stale"
                result["cacheHit"] = True
                result["cacheAgeMs"] = max(int(age * 1000), 0)
                result["warnings"] = list(result.get("warnings") or []) + [
                    f"discussion_cache: stale age={age:.2f}s"
                ]
                return result
            raise
        legacy._cache_put(key, payload)
        return payload


def _build_discussion_list(security: dict[str, str], page: int) -> dict[str, Any]:
    started_at = monotonic()
    source_url = _list_page_url(security["code"], page)
    source = _get_html(source_url)
    posts = _parse_list_rows(source, security["code"])
    if not posts:
        raise ValueError("股吧列表未返回可识别的真实帖子")
    return {
        "provider": "eastmoney_guba_html",
        "status": "ok",
        "code": security["code"],
        "name": security["name"],
        "market": security["market"],
        "page": page,
        "sourcePageUrl": source_url,
        "dataSourceLabel": f"东方财富 {security['name']} 股吧 · 只读讨论",
        "totalOnPage": len(posts),
        "posts": posts,
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "cacheHit": False,
        "cacheAgeMs": 0,
        "totalLatencyMs": int((monotonic() - started_at) * 1000),
        "warnings": [],
    }


def _build_discussion_detail(
    security: dict[str, str],
    post_id: str,
) -> dict[str, Any]:
    started_at = monotonic()
    source_url = _post_page_url(security["code"], post_id)
    source = _get_html(source_url, timeout=9.0)
    title = _extract_title(source)
    body = _extract_body(source)
    comments, comment_parser = _parse_comments(source)
    if not title and not body:
        raise ValueError("股吧帖子正文未返回可识别内容")
    author_block = _first_balanced_div(source, "author-info") or source
    return {
        "provider": "eastmoney_guba_html",
        "status": "ok" if comments else "partial",
        "code": security["code"],
        "name": security["name"],
        "postId": post_id,
        "post": {
            "postId": post_id,
            "title": title or "股吧讨论",
            "author": _extract_post_author(source),
            "publishedAt": _first_date_text(author_block),
            "content": body,
            "likeCount": _extract_like_count(source),
            "sourceUrl": source_url,
        },
        "comments": comments,
        "commentCountParsed": len(comments),
        "commentParser": comment_parser,
        "sourcePageUrl": source_url,
        "dataSourceLabel": "东方财富股吧帖子与公开评论 · 只读",
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "cacheHit": False,
        "cacheAgeMs": 0,
        "totalLatencyMs": int((monotonic() - started_at) * 1000),
        "warnings": [] if comments else ["帖子正文可用，但公开评论未出现在服务端HTML或内嵌JSON中"],
    }


def _list_cached(security: dict[str, str], page: int) -> dict[str, Any]:
    key = legacy._cache_key(
        "discussion-list",
        security["code"],
        f"{DISCUSSION_CACHE_VERSION}:page={page}",
    )
    return _cache_payload(
        key,
        DISCUSSION_LIST_FRESH_SECONDS,
        lambda: _build_discussion_list(security, page),
    )


def _detail_cached(security: dict[str, str], post_id: str) -> dict[str, Any]:
    key = legacy._cache_key(
        "discussion-detail",
        security["code"],
        f"{DISCUSSION_CACHE_VERSION}:post={post_id}",
    )
    return _cache_payload(
        key,
        DISCUSSION_DETAIL_FRESH_SECONDS,
        lambda: _build_discussion_detail(security, post_id),
    )


@app.get(DISCUSSION_LIST_PATH)
def a_share_discussions(
    query: str = Query(..., min_length=1, max_length=32),
    page: int = Query(1, ge=1, le=DISCUSSION_MAX_PAGE),
    pageSize: int = Query(20, ge=1, le=DISCUSSION_MAX_PAGE_SIZE),
) -> dict[str, Any]:
    security = _resolve_security(query)
    try:
        payload = _list_cached(security, page)
        result = deepcopy(payload)
        posts = list(result.get("posts") or [])
        result["fullCount"] = len(posts)
        result["posts"] = posts[:pageSize]
        result["count"] = len(result["posts"])
        result["hasMore"] = len(posts) > pageSize or len(posts) >= DISCUSSION_MAX_PAGE_SIZE
        return result
    except HTTPException:
        raise
    except (httpx.HTTPError, ValueError, TypeError) as exc:
        raise HTTPException(
            status_code=502,
            detail=f"个股讨论暂不可用：{exc.__class__.__name__}: {exc}",
        ) from exc


@app.get(DISCUSSION_DETAIL_PATH)
def a_share_discussion_detail(
    query: str = Query(..., min_length=1, max_length=32),
    postId: str = Query(..., min_length=5, max_length=24, pattern=r"^\d+$"),
    page: int = Query(1, ge=1, le=100),
    pageSize: int = Query(20, ge=1, le=DISCUSSION_MAX_PAGE_SIZE),
) -> dict[str, Any]:
    security = _resolve_security(query)
    try:
        payload = _detail_cached(security, postId)
        result = deepcopy(payload)
        comments = list(result.get("comments") or [])
        start = (page - 1) * pageSize
        result["commentPage"] = page
        result["commentPageSize"] = pageSize
        result["commentTotalParsed"] = len(comments)
        result["comments"] = comments[start:start + pageSize]
        result["hasMoreComments"] = start + pageSize < len(comments)
        return result
    except HTTPException:
        raise
    except (httpx.HTTPError, ValueError, TypeError, json.JSONDecodeError) as exc:
        raise HTTPException(
            status_code=502,
            detail=f"股吧帖子暂不可用：{exc.__class__.__name__}: {exc}",
        ) from exc
