FROM python:3.11.14-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    EMBEDDING_MODEL_PATH=/models/bge-m3 \
    EMBEDDING_DEVICE=cpu

RUN groupadd --system --gid 10001 sky \
    && useradd --system --uid 10001 --gid sky --home-dir /app --shell /usr/sbin/nologin sky

WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir --requirement requirements.txt

COPY --chown=sky:sky app.py run.py ./

#COPY --chown=sky:sky models/bge-m3 /models/bge-m3

USER 10001:10001
EXPOSE 8001
STOPSIGNAL SIGTERM

HEALTHCHECK --interval=20s --timeout=5s --start-period=180s --retries=5 \
  CMD ["python", "-c", "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8001/health/ready', timeout=3)"]

CMD ["python", "-m", "uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8001", "--workers", "1", "--timeout-graceful-shutdown", "30"]
