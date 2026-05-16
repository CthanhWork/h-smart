FROM python:3.9-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    curl \
    g++ \
    git \
    libgl1 \
    libglib2.0-0 \
    ninja-build \
    libsm6 \
    libxext6 \
    libxrender1 \
    libxrender-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade "pip<26" "setuptools<81" wheel packaging \
    && pip install --no-cache-dir "numpy<2" \
    && pip install --no-cache-dir --extra-index-url https://download.pytorch.org/whl/cpu torch==2.1.2+cpu torchvision==0.16.2+cpu \
    && pip install --no-cache-dir --no-build-isolation -r requirements.txt

COPY app ./app

EXPOSE 8000

CMD ["sh", "-c", "gunicorn app.main:app --worker-class uvicorn.workers.UvicornWorker --bind 0.0.0.0:8000 --workers ${WEB_CONCURRENCY:-1} --threads ${GUNICORN_THREADS:-2} --timeout ${GUNICORN_TIMEOUT:-120}"]
