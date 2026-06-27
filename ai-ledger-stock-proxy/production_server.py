from __future__ import annotations

import fast_stock_server as stock_server
import index_detail_server  # noqa: F401  注册指数详情路由


app = stock_server.app
