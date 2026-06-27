from __future__ import annotations

import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

import discussion_server as discussion


LIST_HTML = """
<table>
  <tr class="listitem">
    <td><div>1.2万</div></td>
    <td><div>36</div></td>
    <td>
      <div><a href="/news,600519,1732115383.html">茅台今天怎么看</a></div>
      <div class="update pub_time">06-27 10:18</div>
    </td>
    <td><div>测试股友</div></td>
  </tr>
  <tr class="listitem">
    <td><div>88</div></td>
    <td><div>2</div></td>
    <td>
      <div><span>资讯</span><a href="https://guba.eastmoney.com/news,600519,1732115384.html">公司公告解读</a></div>
      <div class="update pub_time">06-27 09:30</div>
    </td>
    <td><div>贵州茅台资讯</div></td>
  </tr>
</table>
"""

POST_HTML = """
<html><body>
  <div class="newstitle">茅台今天怎么看</div>
  <div class="newsauthor">
    <div class="author-info cl">
      <a href="https://i.eastmoney.com/test">测试股友</a>
      <div class="time">2026-06-27 10:18:22</div>
    </div>
  </div>
  <span class="likemodule">12</span>
  <div class="newstext"><p>这是主贴第一段。</p><p>这是主贴第二段。</p></div>
  <div class="reply_item cl" data-id="comment-1">
    <span class="reuser_name">评论用户A</span>
    <div class="reply_title"><span>这是一级评论。</span></div>
    <div class="publishtime"><span class="pubtime">2026-06-27 10:20</span></div>
    <span class="likemodule">5</span>
    <ul class="replyListL2">
      <li class="reply_item_l2">
        <span class="reuser_l2_nick"><a>回复用户B</a></span>
        <span class="reply_title_span">这是二级回复。</span>
        <span class="pubtime">2026-06-27 10:21</span>
        <span class="likemodule">2</span>
      </li>
    </ul>
  </div>
</body></html>
"""


class DiscussionServerTest(unittest.TestCase):
    def test_parse_list_rows_reads_real_post_fields(self) -> None:
        posts = discussion._parse_list_rows(LIST_HTML, "600519")
        self.assertEqual(len(posts), 2)
        self.assertEqual(posts[0]["postId"], "1732115383")
        self.assertEqual(posts[0]["readCount"], 12000)
        self.assertEqual(posts[0]["commentCount"], 36)
        self.assertEqual(posts[0]["author"], "测试股友")
        self.assertEqual(posts[1]["kind"], "news")
        self.assertTrue(posts[0]["sourceUrl"].startswith("https://guba.eastmoney.com/"))

    def test_parse_post_body_author_and_comments(self) -> None:
        self.assertEqual(discussion._extract_title(POST_HTML), "茅台今天怎么看")
        self.assertEqual(discussion._extract_post_author(POST_HTML), "测试股友")
        self.assertIn("主贴第一段", discussion._extract_body(POST_HTML))
        comments = discussion._parse_comments(POST_HTML)
        self.assertEqual(len(comments), 1)
        self.assertEqual(comments[0]["author"], "评论用户A")
        self.assertEqual(comments[0]["content"], "这是一级评论。")
        self.assertEqual(comments[0]["likeCount"], 5)
        self.assertEqual(comments[0]["replyCount"], 1)
        self.assertEqual(comments[0]["replies"][0]["author"], "回复用户B")
        self.assertEqual(comments[0]["replies"][0]["content"], "这是二级回复。")

    def test_clean_text_removes_scripts_and_keeps_paragraphs(self) -> None:
        value = discussion._clean_text(
            "<script>alert(1)</script><p>第一段</p><p>第二段&nbsp;内容</p>"
        )
        self.assertNotIn("alert", value)
        self.assertEqual(value, "第一段\n第二段 内容")

    def test_discussion_routes_registered_once(self) -> None:
        for path in (
            discussion.DISCUSSION_LIST_PATH,
            discussion.DISCUSSION_DETAIL_PATH,
        ):
            routes = [
                route
                for route in discussion.app.router.routes
                if getattr(route, "path", None) == path
                and "GET" in (getattr(route, "methods", None) or set())
            ]
            self.assertEqual(len(routes), 1, path)


if __name__ == "__main__":
    unittest.main()
