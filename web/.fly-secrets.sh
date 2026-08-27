#!/bin/bash
set -e
flyctl secrets set \
  SCREENSHOT_SERVICE_URL='http://178.156.231.255:3465' \\
  PLAY_BASE_URL='https://quiz-web-frontend-917362189743.us-central1.run.app' \\
  SUPABASE_URL='https://uujjodxicvifmiwlimob.supabase.co' \\
  SUPABASE_ANON_KEY='eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InV1ampvZHhpY3ZpZm1pd2xpbW9iIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDkyNjYyNjQsImV4cCI6MjA2NDg0MjI2NH0.btZJ8_oKTVrjTq5juWl-uJwZm-KaMQU_bo2dUSpumbs' \\
  GAME_WORKER_URL='http://178.156.192.31:3456' \\
  GAME_WORKER_SECRET='vibecoder-worker-secret-2024' \\
  GITHUB_PAT='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' \\
  GITHUB_ORG='Kreative-Koala-LLC' \\
  PIXABAY_API_KEY='51870401-10c08576f17e1cb89793c39c7' \\
  FORGE_WORKER_URL='https://claude-forge-worker.fly.dev' \\
  RIDDLEVERSE_BOT_TOKEN='8690783467:AAE4qF2W8SHME0P5QUZW2zm6sKhXuXf-wZM' \\
  SUPABASE_SERVICE_KEY='eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InV1ampvZHhpY3ZpZm1pd2xpbW9iIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc0OTI2NjI2NCwiZXhwIjoyMDY0ODQyMjY0fQ.vlWy6BWUxEWUlaaCtepPTvu2h_cU9YfrfWnSVwf2Jic' \\
  FIREBASE_SERVICE_ACCOUNT_KEY='{}' \
  GCS_SERVICE_ACCOUNT_KEY='{}'
