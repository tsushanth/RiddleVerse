# quiz-web-frontend
web frontend and backend for quiz
docker build --platform=linux/amd64 -t gcr.io/summarizerproxy/quiz-web-frontend .
docker push gcr.io/summarizerproxy/quiz-web-frontend
gcloud run deploy quiz-web-frontend \                                                    
  --memory=2Gi \                       
  --cpu=2 \
  --image=gcr.io/summarizerproxy/quiz-web-frontend \
  --region=us-central1 \
  --platform=managed

commands:
curl "https://puzzleverseai.com/generate-puzzle?puzzleType=mathestimation&modelName=gpt-3.5-turbo&difficulty=easy"

curl "https://puzzleverseai.com/api/debug-generation?puzzleType=mathestimation&difficulty=Easy"

GET /api/detect-ghost-puzzles - Detect ghost puzzles
POST /api/remove-ghost-puzzles - Remove ghost puzzles with safety checks
GET /api/validate-puzzle-integrity - Comprehensive integrity validation
GET /api/system-health-check - System-wide health monitoring
POST /api/quick-repair-puzzle-paths - All-in-one repair solution
POST /api/batch-ghost-cleanup - Batch processing for multiple puzzle types


Quick Start
1. Detect Ghost Puzzles
# Check specific puzzle type
GET /api/detect-ghost-puzzles?puzzleType=mathestimation&difficulty=Easy

# Check all puzzle types
GET /api/detect-ghost-puzzles
2. Remove Ghost Puzzles (Analysis Mode)
POST /api/remove-ghost-puzzles
{
  "puzzleType": "mathestimation",
  "difficulty": "Easy",
  "dryRun": true
}
3. Remove Ghost Puzzles (Actual Cleanup)
POST /api/remove-ghost-puzzles
{
  "puzzleType": "mathestimation", 
  "difficulty": "Easy",
  "dryRun": false
}
Advanced Usage
Comprehensive Integrity Check
GET /api/validate-puzzle-integrity?puzzleType=mathestimation&difficulty=Easy
System-Wide Health Check
GET /api/system-health-check?includePuzzleTypes=mathestimation,mathtipping&difficultiesPerType=Easy,Medium
Quick Repair (All Issues)
POST /api/quick-repair-puzzle-paths
{
  "puzzleType": "mathestimation",
  "difficulty": "Easy", 
  "dryRun": false,
  "repairGhosts": true,
  "repairBrokenPaths": true
}
Batch Ghost Cleanup
POST /api/batch-ghost-cleanup
{
  "targets": [
    {"puzzleType": "mathestimation", "difficulty": "Easy"},
    {"puzzleType": "mathestimation", "difficulty": "Medium"},
    {"puzzleType": "mathtipping", "difficulty": "Easy"}
  ],
  "dryRun": false,
  "maxConcurrent": 3
}

SELECT 
    p.type,
    p.difficulty,
    COUNT(p.puzzleid) as total_puzzles,
    COUNT(pp.puzzleid) as puzzles_in_path,
    COUNT(p.puzzleid) - COUNT(pp.puzzleid) as missing_from_path
FROM puzzles p
LEFT JOIN puzzle_path pp ON p.puzzleid::text = pp.puzzleid::text
WHERE p."parentSetId" IS NULL
GROUP BY p.type, p.difficulty
HAVING COUNT(p.puzzleid) - COUNT(pp.puzzleid) > 0
ORDER BY missing_from_path DESC;

BEGIN;

-- Delete and rebuild in one atomic transaction
DELETE FROM puzzle_path 
WHERE puzzleid IN (
    SELECT p.puzzleid::uuid
    FROM puzzles p
    WHERE p.type = 'crossword' 
    AND p.difficulty = 'easy' 
    AND p."parentSetId" IS NULL
);

-- Immediately rebuild in the same transaction
WITH numbered_puzzles AS (
    SELECT 
        puzzleid::uuid as puzzleid,
        type,
        difficulty,
        ROW_NUMBER() OVER (ORDER BY puzzleid) as rn
    FROM puzzles 
    WHERE type = 'crossword' 
    AND difficulty = 'easy' 
    AND "parentSetId" IS NULL
),
path_data AS (
    SELECT 
        p1.puzzleid as current_puzzle,
        p2.puzzleid as next_puzzle,
        p1.type,
        p1.difficulty
    FROM numbered_puzzles p1
    LEFT JOIN numbered_puzzles p2 ON p2.rn = p1.rn + 1
)
INSERT INTO puzzle_path (puzzleid, nextpuzzleid, type, difficulty)
SELECT 
    current_puzzle,
    next_puzzle,
    type,
    difficulty
FROM path_data;

COMMIT;

# Export each tier as CSV for Facebook/Google Ads
curl -X GET "https://puzzleverseai.com/api/ltv/export-audience/high_value" \
  -o high_value_audience.csv

curl -X GET "https://puzzleverseai.com/api/ltv/export-audience/whales" \
  -o whales_audience.csv

curl -X GET "https://puzzleverseai.com/api/ltv/export-audience/high_intent" \
  -o high_intent_audience.csv
```

**CSV format:**
```
user123
user456
user789
...
```

---

## 📱 **How to Use in Facebook Ads**

### **Option 1: Custom Audiences (Retargeting)**

1. Go to **Facebook Ads Manager** → **Audiences**
2. Click **Create Audience** → **Custom Audience**
3. Choose **Customer List**
4. Upload your CSV file (`whales_audience.csv`)
5. Map the column to **User ID** or **Email** (if you have emails)
6. Create audience named: "Puzzle App - Whales (High LTV)"

**Create separate audiences for each tier:**
- ✅ Whales - Max bid $25-50
- ✅ High Value - Max bid $10-15
- ✅ High Intent - Max bid $8-12
- ❌ Churned - Use as **EXCLUSION** audience

---

### **Option 2: Lookalike Audiences (Acquisition)**

**Best practice:** Use your highest-value users to find similar people

1. Go to **Facebook Ads Manager** → **Audiences**
2. Click **Create Audience** → **Lookalike Audience**
3. Choose source: **"Puzzle App - Whales (High LTV)"**
4. Select location: United States (or your target country)
5. Choose audience size: **1%** (most similar, highest quality)
6. Create audience

**Create multiple lookalike tiers:**
- 🥇 **1% Lookalike - Whales** (Bid: $15-20)
- 🥈 **1-3% Lookalike - High Value** (Bid: $8-12)
- 🥉 **3-5% Lookalike - Medium Value** (Bid: $4-8)

**Campaign structure:**
```
Campaign: User Acquisition - Lookalike
├── Ad Set 1: 1% LAL Whales (Budget: $100/day, Bid: $15)
├── Ad Set 2: 1% LAL High Value (Budget: $50/day, Bid: $10)
└── Ad Set 3: 1-3% LAL High Value (Budget: $30/day, Bid: $6)
```

---

## 🎯 **How to Use in Google Ads**

### **Customer Match Audiences**

1. Go to **Google Ads** → **Tools & Settings** → **Audience Manager**
2. Click **Customer List** (blue plus button)
3. Upload your CSV file
4. Name it: "PuzzleVerse - High Value Users"
5. Wait 24-48 hours for matching (Google needs time to match user IDs)

### **Similar Audiences**

1. After Customer Match audience is ready
2. Google automatically creates **Similar Audiences**
3. Use these for acquisition campaigns

**Campaign structure:**
```
Campaign: App Install - Similar Audiences
├── Ad Group 1: Similar to Whales (Bid: $12)
├── Ad Group 2: Similar to High Value (Bid: $7)
└── Ad Group 3: High Intent Converters (Bid: $9)
```

---

## 💰 **Bidding Strategy by Tier**

Based on the LTV calculations, here's how much you should bid:

| Tier | Avg LTV | Recommended Bid | Max Bid | Campaign Type |
|------|---------|-----------------|---------|---------------|
| 🐋 Whales | $60-100+ | $18-30 | $50 | Retention, Lookalike |
| 💎 High Value | $20-40 | $8-16 | $15 | Lookalike, Conversion |
| 💰 Medium Value | $10-20 | $5-10 | $8 | Broad Acquisition |
| 📊 Low Value | $5-10 | $2-5 | $4 | Only if profitable |
| 🎯 High Intent | $15-30 | $6-12 | $10 | Conversion focused |
| ⚠️ Churned | N/A | $0 | $0 | **EXCLUDE** |

**Formula used:** 
```
Recommended Bid = LTV × CPA Target %
- Whales: 30% of LTV
- High Value: 40% of LTV
- Medium Value: 50% of LTV
- Low Value: 60% of LTV