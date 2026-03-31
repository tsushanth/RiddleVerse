# Critical Configurations Backup
# DO NOT DELETE - Contains essential configs for frontend rebuild

## 1. Firebase Config (Frontend - Public)
```javascript
const firebaseConfig = {
  apiKey: "AIzaSyCUdOy2BUynLmg3eSJQwRz2sNpIWOSbFq0",
  authDomain: "aipuzzle-3122c.firebaseapp.com",
  projectId: "aipuzzle-3122c",
  storageBucket: "aipuzzle-3122c.firebasestorage.app",
  messagingSenderId: "719851629523",
  appId: "1:719851629523:web:d1f7f9e20e3ef6df129d26",
  measurementId: "G-BJ4LKWX858"
};
```

## 2. API Base URL Configuration
```javascript
const API_BASE_URL = window.location.hostname.includes("localhost")
    ? "http://localhost:8080"
    : "https://puzzleverseai.com";
```

## 3. iOS Deep Linking (apple-app-site-association)
Location: `/web/backend/public/.well-known/apple-app-site-association`
```json
{
    "applinks": {
        "apps": [],
        "details": [
            {
                "appID": "7RS696YC75.KreativeKoala.riddlebot",
                "paths": [ "/share-riddle*", "/open-riddle*" ]
            }
        ]
    }
}
```

## 4. Android Deep Linking (assetlinks.json)
Location: `/web/backend/public/.well-known/assetlinks.json`
```json
[
  {
    "relation": [
      "delegate_permission/common.handle_all_urls",
      "delegate_permission/common.get_login_creds"
    ],
    "target": {
      "namespace": "android_app",
      "package_name": "com.kreativekoala.riddlebot",
      "sha256_cert_fingerprints": [
        "94:00:BC:49:6D:EE:33:3D:06:6E:69:76:E8:1B:A3:71:1D:93:07:F4:2D:D4:25:94:1B:77:A4:E9:12:E5:98:C4",
        "FE:A3:97:71:64:32:C8:B9:F1:6F:93:54:9A:18:D5:68:FB:8F:9D:B5:10:17:EE:1A:5F:BB:3E:6D:60:69:E6:7B"
      ]
    }
  }
]
```

## 5. Required Environment Variables (Backend)
```
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-supabase-anon-key
FIREBASE_SERVICE_ACCOUNT_KEY={"type":"service_account",...}
FIREBASE_DATABASE_URL=https://your-project.firebaseio.com
GCS_SERVICE_ACCOUNT_KEY={"type":"service_account",...}
OPENAI_API_KEY=sk-proj-...
ANTHROPIC_API_KEY=sk-ant-...
DEEPSEEK_API_KEY=sk-...
GOOGLE_API_KEY=AIza...
PIXABAY_API_KEY=...
EMAIL_USER=...
EMAIL_PASSWORD=...
SCHEDULER_SECRET=...
REDIS_URL=redis://localhost:6379
PORT=8080
NODE_ENV=production
```

## 6. Key API Endpoints (Backend serves these)
- POST `/auth/google` - Google OAuth authentication
- GET `/fetch-next-puzzle/:type` - Get next puzzle by type
- POST `/api/puzzles/check-answer` - Check user's answer
- POST `/api/puzzles/generate-answer` - AI generates answer
- GET `/leaderboard` - Get leaderboard data
- POST `/reset-puzzle-progress/:userId` - Reset user progress
- GET `/api/health` - Health check
- GET `/.well-known/assetlinks.json` - Android deep linking
- GET `/.well-known/apple-app-site-association` - iOS deep linking

## 7. LocalStorage Keys Used
- `token` - JWT auth token
- `email` - User email
- `userId` - User ID
- `userScore` - Local score tracking
- `deepSeekScore` - AI score tracking

## 8. Puzzle Types (from iOS app - need feature parity)
### Server-side generated:
- trivia, anagram, storypuzzle, math, riddle, qa
- synonym, antonym, wordprefix, letterset, wordsnake
- estimation, percentage, tipbubble, average, conversion, division
- geography_cities, geography_countries
- memoryretention, memorysequencing, memorystory
- crossword, wordsearch, crypto
- imagepuzzle, waldo

### Client-side generated:
- memoryprevioussingle, memorypreviouspair
- colorshapematching, colortextmatching
- imagevortex, mathcomparison
- numbersequence, numbersum
- symbolswipe, uniqueobject
- contextswitch, dualtask
- mathcrossword, mathexpression
- flow, symmetry, triangledot
- pinballdeflector, oddoneout
