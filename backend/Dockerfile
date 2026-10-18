FROM python:3.12-slim
WORKDIR /opt/app
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY app/ ./app/
ENV CARGOSHARE_DEMO_MODE=false
EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
