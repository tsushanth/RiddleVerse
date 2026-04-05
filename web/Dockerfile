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
ENV OPENAI_API_KEY="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"
ENV DEEPSEEK_API_KEY="sk-a55f9f944f82446984142489e47d3e17"
ENV PIXABAY_API_KEY="51870401-10c08576f17e1cb89793c39c7"
ENV UNSPLASH_ACCESS_KEY="sfI8La5IhBt5IfV5y6HQrRI47JnmrLPwlunHrYofd2Y"
ENV REPLICATE_API_TOKEN="r8_XcMu4geDBPvqEEGYJ9H4efqM674WX2V0ccnvx"
ENV ALERT_EMAIL=puzzleverseai@gmail.com
ENV PORT=8080
ENV DOCKER_BUILD=true
ENV REDIS_URL=redis://localhost:6379
ENV ADMIN_KEY=e665497ea03f74641aedcf66473454c9a041e9bc399ab1136ebafbba3561f3a4
ENV STRIPE_SECRET_KEY=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
ENV STRIPE_WEBHOOK_SECRET=whsec_DWlr328iFRourWiEc6Fu7XvZCwDJ7zmQ
# FORGE_WORKER_URL should be set via GCP Cloud Run environment variables
# ENV FORGE_WORKER_URL=https://claude-forge-worker.fly.dev
ENV SCREENSHOT_SERVICE_URL=http://178.156.231.255:3465
ENV PLAY_BASE_URL=https://quiz-web-frontend-917362189743.us-central1.run.app

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
