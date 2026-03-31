# Stage 1: Build Frontend with Vite/React
FROM node:18-slim AS frontend-build
WORKDIR /frontend

# Copy frontend dependencies
COPY frontend/package*.json ./
RUN npm install

# Copy all frontend files
COPY frontend ./

# Build the frontend
RUN npm run build

# Stage 2: Backend + Redis + Serve Frontend
FROM node:18-slim AS backend
WORKDIR /backend

# Install system dependencies including Redis
RUN apt-get update && apt-get install -y \
    libc6 \
    libstdc++6 \
    redis-server \
    supervisor \
    && rm -rf /var/lib/apt/lists/*

# Copy backend dependencies & install
COPY backend/package*.json ./
RUN npm install

# Copy backend source code
COPY backend ./

# Create public folder and copy the frontend build
RUN mkdir -p public
COPY --from=frontend-build /frontend/dist public/

# Copy .well-known files for deep linking
COPY --from=frontend-build /frontend/public/.well-known public/.well-known/
COPY --from=frontend-build /frontend/public/app-ads.txt public/app-ads.txt

# Copy local model into container
COPY local_all-MiniLM-L6-v2 /backend/model-cache/Xenova/all-MiniLM-L6-v2
COPY local_all-MiniLM-L6-v2 /backend/node_modules/@xenova/transformers/models/Xenova/all-MiniLM-L6-v2

# Set environment variables
ENV OPENAI_API_KEY="REDACTED_SECRET"
ENV DEEPSEEK_API_KEY="REDACTED_SECRET"
ENV PIXABAY_API_KEY="REDACTED_SECRET"
ENV UNSPLASH_ACCESS_KEY="REDACTED_SECRET"
ENV REPLICATE_API_TOKEN="REDACTED_SECRET"
ENV ALERT_EMAIL=puzzleverseai@gmail.com
ENV PORT=8080
ENV DOCKER_BUILD=true
ENV REDIS_URL=redis://localhost:6379
ENV ADMIN_KEY=REDACTED_SECRET
ENV STRIPE_SECRET_KEY=REDACTED_SECRET
ENV STRIPE_WEBHOOK_SECRET=REDACTED_SECRET
# FORGE_WORKER_URL should be set via GCP Cloud Run environment variables
# ENV FORGE_WORKER_URL=https://claude-forge-worker.fly.dev

# Create supervisor configuration for running Redis + Node.js
RUN echo '[supervisord]' > /etc/supervisor/conf.d/supervisord.conf && \
    echo 'nodaemon=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'user=root' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo '' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo '[program:redis]' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'command=redis-server --bind 0.0.0.0 --port 6379' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'autostart=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'autorestart=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stdout_logfile=/dev/stdout' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stdout_logfile_maxbytes=0' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stderr_logfile=/dev/stderr' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stderr_logfile_maxbytes=0' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo '' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo '[program:nodejs]' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'command=node server.js' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'directory=/backend' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'autostart=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'autorestart=true' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stdout_logfile=/dev/stdout' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stdout_logfile_maxbytes=0' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stderr_logfile=/dev/stderr' >> /etc/supervisor/conf.d/supervisord.conf && \
    echo 'stderr_logfile_maxbytes=0' >> /etc/supervisor/conf.d/supervisord.conf

# Run smoke tests before deployment
RUN echo "Running pre-deployment smoke tests..." \
    && node smoke-tests.js \
    && echo "Smoke tests passed - proceeding with deployment"

# Expose backend port
EXPOSE 8080

# Start both Redis and Node.js using supervisor
CMD ["/usr/bin/supervisord", "-c", "/etc/supervisor/conf.d/supervisord.conf"]
