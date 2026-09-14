FROM python:3.11.14-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    HOST=0.0.0.0 \
    PORT=8000 \
    TASK_STATE_DB=/app/data/agent_tasks.sqlite3 \
    KNOWLEDGE_STATE_DB=/app/data/knowledge_tasks.sqlite3 \
    KNOWLEDGE_SOURCE_PATH=/app/data/knowledge_sources

RUN groupadd --system --gid 10001 sky \
    && useradd --system --uid 10001 --gid sky --home-dir /app --shell /usr/sbin/nologin sky

WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir --requirement requirements.txt

COPY --chown=sky:sky app ./app
COPY --chown=sky:sky run.py ./run.py
RUN mkdir -p /app/data/knowledge_sources && chown -R sky:sky /app/data

USER 10001:10001
VOLUME ["/app/data"]
EXPOSE 8000
STOPSIGNAL SIGTERM

HEALTHCHECK --interval=15s --timeout=5s --start-period=15s --retries=5 \
  CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health/ready', timeout=3)"]

CMD ["python", "-m", "uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1", "--timeout-graceful-shutdown", "30"]
