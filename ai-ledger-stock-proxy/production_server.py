from __future__ import annotations

import fast_stock_server as stock_server
import hot_rank_server  # noqa: F401  注册实时热点榜路由
import index_detail_server  # noqa: F401  注册指数详情路由
import market_kline_server  # noqa: F401  注册扩展历史K线路由
import sector_detail_server  # noqa: F401  注册板块详情与成分股路由


app = stock_server.app
