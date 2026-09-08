FROM python:3.11-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py run.py ./
COPY models/bge-m3 ./models/bge-m3
EXPOSE 8001
CMD ["python", "run.py"]
