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
    sudo \
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
COPY server.js statusCodes.js monitoring.js agentSandbox.js ./

# Secret isolation (see agentSandbox.js). Three unix identities:
#   root   : only the entrypoint, which holds the Fly secrets just long enough to hand them to the worker
#   node   : the worker (holds WORKER_SECRET + the Anthropic key in memory, runs the key proxy and chromium)
#   agent  : the Claude CLI, started by the worker through sudo. It runs shell commands for hostile prompts,
#            so it must not be able to read the worker's memory, files or /proc/<pid>/environ.
# node and agent share group `gw` so the worker can read and delete what the agent writes in /work.
# The CLI refuses --dangerously-skip-permissions as root, so it never runs as root.
RUN groupadd gw \
    && useradd -m -g gw -s /bin/bash agent \
    && usermod -aG gw node \
    && mkdir -p /work /run/gw /home/agent/.claude /home/node/.claude \
    && echo '{"hasCompletedOnboarding":true}' > /home/agent/.claude.json \
    && echo '{"hasCompletedOnboarding":true}' > /home/node/.claude.json \
    && chown root:gw /work && chmod 2770 /work \
    && chown -R agent:gw /home/agent && chmod 700 /home/agent \
    && chown -R node:node /home/node \
    && echo 'node ALL=(agent) NOPASSWD: ALL' > /etc/sudoers.d/gw-agent && chmod 0440 /etc/sudoers.d/gw-agent \
    && git config --system user.name "riddleverse-game-worker" \
    && git config --system user.email "noreply@invalid" \
    && chown -R node:node /app

COPY entrypoint.sh /usr/local/bin/gw-entrypoint.sh
RUN chmod 0755 /usr/local/bin/gw-entrypoint.sh

ENV HOME=/home/node \
    GW_AGENT_USER=agent \
    GW_AGENT_HOME=/home/agent \
    GW_PROJECTS_DIR=/work

EXPOSE 3456
# Starts as root on purpose: the entrypoint drops to `node` with the secret variables already unset.
ENTRYPOINT ["/usr/local/bin/gw-entrypoint.sh"]
CMD ["node", "server.js"]
