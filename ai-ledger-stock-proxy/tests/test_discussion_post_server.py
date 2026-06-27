from __future__ import annotations

import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import discussion_post_server as post_server


POST_HTML = """
<html><body>
  <div class="newstitle">独立帖子详情测试</div>
  <div class="newsauthor">
    <div class="author-info cl">
      <a href="https://i.eastmoney.com/test">测试作者</a>
      <div class="time">2026-06-27 15:20:00</div>
    </div>
  </div>
  <span class="likemodule">9</span>
  <div class="newstext"><p>正文第一段。</p><p>正文第二段。</p></div>
  <div class="reply_item cl"><div class="reply_title">这条评论不应由正文接口返回。</div></div>
</body></html>
"""


class DiscussionPostServerTest(unittest.TestCase):
    def test_body_endpoint_does_not_parse_or_return_comments(self) -> None:
        original_get_html = post_server.discussion._get_html
        post_server.discussion._get_html = lambda url, timeout=9.0: POST_HTML
        try:
            payload = post_server._build_discussion_post(
                {"code": "600584", "name": "长电科技", "market": "沪A"},
                "1732115383",
            )
        finally:
            post_server.discussion._get_html = original_get_html

        self.assertEqual(payload["post"]["title"], "独立帖子详情测试")
        self.assertIn("正文第一段", payload["post"]["content"])
        self.assertEqual(payload["post"]["author"], "测试作者")
        self.assertTrue(payload["commentsDeferred"])
        self.assertNotIn("comments", payload)

    def test_post_body_route_registered_once(self) -> None:
        routes = [
            route
            for route in post_server.app.router.routes
            if getattr(route, "path", None) == post_server.DISCUSSION_POST_PATH
            and "GET" in (getattr(route, "methods", None) or set())
        ]
        self.assertEqual(len(routes), 1)


if __name__ == "__main__":
    unittest.main()
