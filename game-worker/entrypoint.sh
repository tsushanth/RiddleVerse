#!/bin/sh
# Runs as root. Fly injects the secrets as environment variables of this process. A process's
# /proc/<pid>/environ keeps the original block even after the app unsets them, and a same-user child
# (the agent) could read it, so the worker must never be started with them in its environment.
# Instead: write them to a node-only file in /run/gw, start the worker as `node` without them, and the
# worker reads the file once and deletes it.
set -eu
umask 077
node -e '
const fs = require("fs");
const out = {};
for (const n of ["WORKER_SECRET", "ANTHROPIC_API_KEY", "GITHUB_PAT"]) if (process.env[n]) out[n] = process.env[n];
fs.writeFileSync("/run/gw/secrets.json", JSON.stringify(out), { mode: 0o400 });
'
chown node:node /run/gw /run/gw/secrets.json
chmod 700 /run/gw

exec setpriv --reuid=node --regid=node --init-groups \
    env -u WORKER_SECRET -u ANTHROPIC_API_KEY -u GITHUB_PAT \
        HOME=/home/node GW_SECRETS_FILE=/run/gw/secrets.json \
    "$@"
