FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py ./
COPY realtime_runtime.py ./
COPY market_home_server.py ./
COPY market_breadth_server.py ./
COPY market_stage_server.py ./
COPY stock_detail_server.py ./
COPY fast_stock_server.py ./
COPY index_detail_server.py ./
COPY index_compact_server.py ./
COPY market_kline_server.py ./
COPY sector_detail_server.py ./
COPY hot_rank_server.py ./
COPY discussion_server.py ./
COPY discussion_post_server.py ./
COPY production_server.py ./
COPY test_market_home_server.py ./
COPY test_market_stage_server.py ./
RUN python -m py_compile main.py realtime_runtime.py market_home_server.py market_breadth_server.py market_stage_server.py stock_detail_server.py fast_stock_server.py index_detail_server.py index_compact_server.py market_kline_server.py sector_detail_server.py hot_rank_server.py discussion_server.py discussion_post_server.py production_server.py test_market_home_server.py test_market_stage_server.py \
    && python -m unittest test_market_home_server.py test_market_stage_server.py \
    && python -c "import fast_stock_server, index_compact_server, market_breadth_server, market_stage_server, production_server; paths = {getattr(route, 'path', '') for route in production_server.app.router.routes}; required = {'/health', '/api/stock/a-share/realtime', '/api/stock/a-share/market/home', '/api/stock/a-share/market/indices', '/api/stock/a-share/market/breadth', '/api/stock/a-share/market/discovery', '/api/stock/a-share/index/compact', '/api/stock/a-share/index/compact/batch', '/api/stock/a-share/index/compact/quotes', '/api/stock/a-share/index/compact/trend'}; missing = sorted(required - paths); assert not missing, f'missing production routes: {missing}'; warm = getattr(index_compact_server, '_warm_tools_index_hero', None); assert warm is None or warm not in production_server.app.router.on_startup, 'index compact startup warmup must be disabled in production'; assert market_stage_server.STAGE_VERSION == 'v6-full-universe-breadth'; assert market_breadth_server.MARKET_BREADTH_MIN_COVERAGE >= 0.95; assert market_breadth_server.MARKET_BREADTH_MIN_ROWS >= 3000; assert fast_stock_server.REALTIME_TICK_WINDOW <= 40"

EXPOSE 8000

CMD ["uvicorn", "production_server:app", "--host", "0.0.0.0", "--port", "8000", "--loop", "uvloop", "--http", "httptools", "--no-access-log"]
