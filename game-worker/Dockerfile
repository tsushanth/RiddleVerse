# RiddleVerse game worker: its own Fly service (riddleverse-game-worker).
# Authenticates to the model with ANTHROPIC_API_KEY (a Fly secret) in non-interactive -p mode.
# No subscription/OAuth login and no credential is ever baked into the image.
FROM node:20-slim

# Chromium + libs for puppeteer (runtime validation), git (project folders / optional repo features)
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    chromium \
    fonts-liberation \
    fonts-noto-color-emoji \
    libasound2 \
    libatk-bridge2.0-0 \
    libatk1.0-0 \
    libcups2 \
    libdbus-1-3 \
    libdrm2 \
    libgbm1 \
    libgtk-3-0 \
    libnspr4 \
    libnss3 \
    libxcomposite1 \
    libxdamage1 \
    libxfixes3 \
    libxrandr2 \
    xdg-utils \
    git \
    && rm -rf /var/lib/apt/lists/*

ENV PUPPETEER_SKIP_DOWNLOAD=true \
    PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true \
    PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium \
    DISABLE_AUTOUPDATER=1 \
    CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1 \
    NODE_ENV=production \
    WORKER_PORT=3456

# Claude Code CLI (server.js finds it with `which claude`); fail the build if it does not run
RUN npm install -g @anthropic-ai/claude-code && claude --version

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY server.js statusCodes.js monitoring.js ./

# The CLI refuses --dangerously-skip-permissions as root, so run as the unprivileged `node` user.
# ~/.game-worker/projects lives on the machine's ephemeral disk (projects are throwaway).
# ~/.claude.json holds only the onboarding flag, no credential.
RUN mkdir -p /home/node/.game-worker/projects /home/node/.claude \
    && echo '{"hasCompletedOnboarding":true}' > /home/node/.claude.json \
    && git config --system user.name "riddleverse-game-worker" \
    && git config --system user.email "noreply@invalid" \
    && chown -R node:node /home/node /app
USER node
ENV HOME=/home/node

EXPOSE 3456
CMD ["node", "server.js"]
