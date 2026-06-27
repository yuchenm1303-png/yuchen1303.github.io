from __future__ import annotations

from copy import deepcopy
from datetime import datetime, timezone
from time import monotonic
from typing import Any

import httpx
from fastapi import HTTPException, Query

import discussion_server as discussion


app = discussion.app
legacy = discussion.legacy

DISCUSSION_POST_PATH = "/api/stock/a-share/discussion/post"
DISCUSSION_POST_CACHE_VERSION = "v1-eastmoney-guba-post-body"
DISCUSSION_POST_FRESH_SECONDS = 120.0


def _build_discussion_post(
    security: dict[str, str],
    post_id: str,
) -> dict[str, Any]:
    started_at = monotonic()
    source_url = discussion._post_page_url(security["code"], post_id)
    source = discussion._get_html(source_url, timeout=9.0)
    title = discussion._extract_title(source)
    body = discussion._extract_body(source)
    if not title and not body:
        raise ValueError("股吧帖子正文未返回可识别内容")
    author_block = discussion._first_balanced_div(source, "author-info") or source
    return {
        "provider": "eastmoney_guba_html",
        "status": "ok",
        "code": security["code"],
        "name": security["name"],
        "market": security["market"],
        "postId": post_id,
        "post": {
            "postId": post_id,
            "title": title or "股吧讨论",
            "author": discussion._extract_post_author(source),
            "publishedAt": discussion._first_date_text(author_block),
            "content": body,
            "likeCount": discussion._extract_like_count(source),
            "sourceUrl": source_url,
        },
        "commentsDeferred": True,
        "sourcePageUrl": source_url,
        "dataSourceLabel": "东方财富股吧帖子正文 · 评论按需加载",
        "updatedAt": datetime.now(timezone.utc).isoformat(),
        "cacheHit": False,
        "cacheAgeMs": 0,
        "totalLatencyMs": int((monotonic() - started_at) * 1000),
        "warnings": [],
    }


def _post_cached(security: dict[str, str], post_id: str) -> dict[str, Any]:
    key = legacy._cache_key(
        "discussion-post",
        security["code"],
        f"{DISCUSSION_POST_CACHE_VERSION}:post={post_id}",
    )
    return discussion._cache_payload(
        key,
        DISCUSSION_POST_FRESH_SECONDS,
        lambda: _build_discussion_post(security, post_id),
    )


@app.get(DISCUSSION_POST_PATH)
def a_share_discussion_post(
    query: str = Query(..., min_length=1, max_length=32),
    postId: str = Query(..., min_length=5, max_length=24, pattern=r"^\d+$"),
) -> dict[str, Any]:
    security = discussion._resolve_security(query)
    try:
        return deepcopy(_post_cached(security, postId))
    except HTTPException:
        raise
    except (httpx.HTTPError, ValueError, TypeError) as exc:
        raise HTTPException(
            status_code=502,
            detail=f"股吧帖子正文暂不可用：{exc.__class__.__name__}: {exc}",
        ) from exc
