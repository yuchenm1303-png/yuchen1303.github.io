FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py ./
COPY realtime_runtime.py ./
COPY market_home_server.py ./
COPY stock_detail_server.py ./
COPY fast_stock_server.py ./
RUN python -m py_compile main.py realtime_runtime.py market_home_server.py stock_detail_server.py fast_stock_server.py

EXPOSE 8000

CMD ["uvicorn", "fast_stock_server:app", "--host", "0.0.0.0", "--port", "8000", "--loop", "uvloop", "--http", "httptools", "--no-access-log"]
