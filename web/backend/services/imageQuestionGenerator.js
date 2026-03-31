// services/imageQuestionGenerator.js
// Enhanced accuracy-first service: DALL·E image → gpt-3.5-turboo analysis with consensus
// refinement → unambiguous questions with VERIFIED location option distribution.
//
// Requires: npm i axios
// Env: OPENAI_API_KEY must be set

import axios from 'axios';

// ⚙️ Tunables (defaults prioritize accuracy > volume)
const DEFAULT_OPTIONS = {
  askColor: true,
  askLocation: true,
  allowCounting: false,     // counting OFF by default
  includePlurals: false,    // only generate Qs for singletons
  useConsensus: true,       // run grid + container sweeps and dedupe
  lenient: false,           // slightly lower thresholds if true
  debug: false
};

// VERIFIED HARDCODED LOCATION OPTIONS - Manually tested and strategically optimized!
// 
// STRATEGIES USED:
// 🔺 Corners: Diagonal opposite + same row far + center position
// 📏 Edge Centers: Both opposite corners + one middle side  
// ◀️ Middle Sides: ALL opposite side positions (very logical!)
// 🎯 Center: All 4 corners for maximum separation
//
const LOCATION_OPTIONS = {
  // === CORNER POSITIONS === 
  'Top Left': ['Top Left', 'Bottom Right', 'Top Right', 'Bottom Center'],
  'Top Right': ['Top Right', 'Bottom Left', 'Top Left', 'Bottom Center'],
  'Bottom Left': ['Bottom Left', 'Top Right', 'Bottom Right', 'Top Center'],
  'Bottom Right': ['Bottom Right', 'Top Left', 'Bottom Left', 'Top Center'],
  
  // === EDGE CENTERS ===
  'Top Center': ['Top Center', 'Bottom Left', 'Bottom Right', 'Middle Left'],
  'Bottom Center': ['Bottom Center', 'Top Left', 'Top Right', 'Middle Right'],
  
  // === MIDDLE SIDES === 
  'Middle Left': ['Middle Left', 'Top Right', 'Middle Right', 'Bottom Right'],
  'Middle Right': ['Middle Right', 'Top Left', 'Middle Left', 'Bottom Left'],
  
  // === CENTER POSITION ===
  'Middle Center': ['Middle Center', 'Top Left', 'Top Right', 'Bottom Left'],
  
  // === ALTERNATIVE NAMING VARIATIONS ===
  'Center': ['Middle Center', 'Top Left', 'Top Right', 'Bottom Left'],
  'Middle': ['Middle Center', 'Top Left', 'Top Right', 'Bottom Left'],
};

// Position aliases for flexible matching
const POSITION_ALIASES = {
  'top-left': 'Top Left',
  'topleft': 'Top Left',
  'top-center': 'Top Center', 
  'topcenter': 'Top Center',
  'top-middle': 'Top Center',
  'topmiddle': 'Top Center',
  'top-right': 'Top Right',
  'topright': 'Top Right',
  
  'middle-left': 'Middle Left',
  'middleleft': 'Middle Left', 
  'center-left': 'Middle Left',
  'centerleft': 'Middle Left',
  'middle-center': 'Middle Center',
  'middlecenter': 'Middle Center',
  'center': 'Middle Center',
  'middle': 'Middle Center',
  'middle-right': 'Middle Right',
  'middleright': 'Middle Right',
  'center-right': 'Middle Right', 
  'centerright': 'Middle Right',
  
  'bottom-left': 'Bottom Left',
  'bottomleft': 'Bottom Left',
  'bottom-center': 'Bottom Center',
  'bottomcenter': 'Bottom Center',
  'bottom-middle': 'Bottom Center',
  'bottommiddle': 'Bottom Center', 
  'bottom-right': 'Bottom Right',
  'bottomright': 'Bottom Right'
};

// Normalize position name to standard format
function normalizePositionName(position) {
  const cleaned = position.trim();
  const lowercase = cleaned.toLowerCase();
  
  // Try exact match first
  if (LOCATION_OPTIONS[cleaned]) {
    return cleaned;
  }
  
  // Try alias lookup
  if (POSITION_ALIASES[lowercase]) {
    return POSITION_ALIASES[lowercase];
  }
  
  // Try case-insensitive match in options keys
  const exactMatch = Object.keys(LOCATION_OPTIONS).find(key => 
    key.toLowerCase() === lowercase
  );
  
  return exactMatch || null;
}

// Generate smart, well-distributed location answer options - SIMPLE LOOKUP!
function generateLocationOptions(correctAnswer, numOptions = 4) {
  const normalizedAnswer = normalizePositionName(correctAnswer);
  
  if (!normalizedAnswer || !LOCATION_OPTIONS[normalizedAnswer]) {
    console.warn(`⚠️  No hardcoded options found for position: "${correctAnswer}"`);
    // Fallback to basic options if position not recognized
    return [correctAnswer, 'Top Right', 'Bottom Left', 'Middle Center']
      .slice(0, numOptions)
      .sort(() => Math.random() - 0.5);
  }

  const options = LOCATION_OPTIONS[normalizedAnswer];
  
  // Return shuffled options (correct answer is always first in our arrays)
  const shuffledOptions = options.slice(0, numOptions).sort(() => Math.random() - 0.5);
  
  console.log(`🎯 Generated verified options for ${normalizedAnswer}: [${shuffledOptions.join(', ')}]`);
  
  return shuffledOptions;
}

// Enhanced position detection with standardized naming
function detectGridPosition(bbox) {
  const [x = 0, y = 0, w = 0, h = 0] = bbox || [];
  const centerX = x + w / 2;
  const centerY = y + h / 2;

  // More precise grid detection with standardized names
  let position;
  
  // Determine row
  const row = centerY < 0.33 ? 0 : centerY < 0.67 ? 1 : 2;
  // Determine column  
  const col = centerX < 0.33 ? 0 : centerX < 0.67 ? 1 : 2;

  // Map to standardized position names
  const positionMap = [
    ['Top Left', 'Top Center', 'Top Right'],        // row 0
    ['Middle Left', 'Middle Center', 'Middle Right'], // row 1
    ['Bottom Left', 'Bottom Center', 'Bottom Right']  // row 2
  ];
  
  position = positionMap[row][col];
  
  console.log(`📍 Detected position: ${position} (${row},${col}) from bbox center (${centerX.toFixed(2)}, ${centerY.toFixed(2)})`);
  
  return position;
}

const STRICT = { VISIBILITY:0.80, SAT_MIN:0.22, SEP_MIN:0.08, AREA_MIN:0.015, COLOR_UNIFORMITY:0.92 };
const RELAXED = { VISIBILITY:0.65, SAT_MIN:0.18, SEP_MIN:0.05, AREA_MIN:0.010, COLOR_UNIFORMITY:0.85 };

// Enhanced scene templates
const SCENES = [
  {
    theme: "Kitchen Counter",
    description: "A clean kitchen counter with specific cooking items arranged neatly",
    requiredObjects: [
      { name: "red apple", count: 2, color: "red", position: "left side of counter" },
      { name: "white coffee mug", count: 1, color: "white", position: "center of counter" },
      { name: "wooden cutting board", count: 1, color: "brown", position: "right side of counter" },
      { name: "silver knife", count: 1, color: "silver", position: "on cutting board" },
      { name: "yellow banana", count: 3, color: "yellow", position: "left of mug" }
    ],
    background: "clean white marble counter",
    lighting: "bright natural light from above",
    complexity: "medium"
  },
  {
    theme: "Study Desk",
    description: "An organized study desk with school supplies neatly arranged",
    requiredObjects: [
      { name: "blue notebook", count: 1, color: "blue", position: "center left of desk" },
      { name: "red pen", count: 2, color: "red", position: "next to notebook" },
      { name: "yellow highlighter", count: 1, color: "yellow", position: "top right corner" },
      { name: "black calculator", count: 1, color: "black", position: "bottom right" },
      { name: "green eraser", count: 1, color: "green", position: "center of desk" }
    ],
    background: "clean wooden desk surface",
    lighting: "bright desk lamp lighting",
    complexity: "medium"
  },
  {
    theme: "Breakfast Table",
    description: "A simple breakfast setting on a wooden table with morning items",
    requiredObjects: [
      { name: "white plate", count: 2, color: "white", position: "center of table" },
      { name: "silver fork", count: 2, color: "silver", position: "left of plates" },
      { name: "blue juice glass", count: 1, color: "blue", position: "top right" },
      { name: "yellow orange", count: 1, color: "orange", position: "on left plate" },
      { name: "brown toast", count: 2, color: "brown", position: "on right plate" }
    ],
    background: "clean wooden table surface",
    lighting: "natural morning light",
    complexity: "easy"
  },
  {
    theme: "Craft Table",
    description: "A simple craft workspace with art supplies laid out",
    requiredObjects: [
      { name: "red scissors", count: 1, color: "red", position: "left side of table" },
      { name: "yellow pencil", count: 3, color: "yellow", position: "center of table" },
      { name: "blue ruler", count: 1, color: "blue", position: "top of table" },
      { name: "green marker", count: 2, color: "green", position: "right side" },
      { name: "purple eraser", count: 1, color: "purple", position: "bottom center" }
    ],
    background: "clean white table surface",
    lighting: "bright overhead lighting",
    complexity: "easy"
  },
  {
    theme: "Garden Potting Bench",
    description: "A simple outdoor potting bench with gardening tools",
    requiredObjects: [
      { name: "green watering can", count: 1, color: "green", position: "left side of bench" },
      { name: "brown pot", count: 3, color: "brown", position: "center of bench" },
      { name: "silver trowel", count: 1, color: "silver", position: "right side" },
      { name: "yellow gloves", count: 1, color: "yellow", position: "on bench" },
      { name: "red seed packet", count: 2, color: "red", position: "near pots" }
    ],
    background: "wooden potting bench outdoors",
    lighting: "natural outdoor daylight",
    complexity: "medium"
  },
  {
    theme: "Office Workspace",
    description: "A neat office desk with essential work items",
    requiredObjects: [
      { name: "black laptop", count: 1, color: "black", position: "center of desk" },
      { name: "blue pen", count: 2, color: "blue", position: "right of laptop" },
      { name: "white coffee mug", count: 1, color: "white", position: "top right corner" },
      { name: "yellow sticky notes", count: 1, color: "yellow", position: "left of laptop" },
      { name: "red stapler", count: 1, color: "red", position: "bottom left" }
    ],
    background: "clean office desk surface",
    lighting: "bright office lighting",
    complexity: "hard"
  },
  {
    theme: "Picnic Setup",
    description: "A simple outdoor picnic spread on a checkered blanket",
    requiredObjects: [
      { name: "white plate", count: 2, color: "white", position: "center of blanket" },
      { name: "red apple", count: 1, color: "red", position: "on left plate" },
      { name: "blue water bottle", count: 1, color: "blue", position: "top right corner" },
      { name: "brown basket", count: 1, color: "brown", position: "left side" },
      { name: "yellow sandwich", count: 1, color: "yellow", position: "on right plate" }
    ],
    background: "red and white checkered picnic blanket on grass",
    lighting: "outdoor daylight",
    complexity: "medium"
  },
  {
    theme: "Living Room Coffee Table",
    description: "A cozy living room coffee table with a few items",
    requiredObjects: [
      { name: "green book", count: 2, color: "green", position: "left side of table" },
      { name: "white candle", count: 1, color: "white", position: "center of table" },
      { name: "black remote control", count: 1, color: "black", position: "right side" },
      { name: "blue throw pillow", count: 1, color: "blue", position: "on sofa behind table" },
      { name: "brown picture frame", count: 1, color: "brown", position: "back of table" }
    ],
    background: "wooden coffee table in living room",
    lighting: "warm indoor lighting",
    complexity: "easy"
  }
];

function sceneByDifficulty(difficulty) {
  const d = (difficulty || 'medium').toLowerCase();
  const filtered = SCENES.filter(s =>
    d === 'easy'   ? ['easy','medium'].includes(s.complexity) :
    d === 'hard'   ? ['hard','medium'].includes(s.complexity) :
                     s.complexity === 'medium'
  );
  return filtered[Math.floor(Math.random()*filtered.length)] || SCENES[0];
}

function preciseImagePrompt(tpl) {
  const lines = tpl.requiredObjects.map(o =>
    `- Exactly ${o.count} ${o.color} ${o.name}${o.count>1?'s':''} positioned ${o.position}`
  ).join('\n');
  const colors = [...new Set(tpl.requiredObjects.map(o => o.color))].join(', ');
  const noDupes = tpl.requiredObjects.every(o => o.count === 1)
    ? '\n- Do NOT include more than one instance of any object type.'
    : '';

  return `Create a detailed, clear illustration showing: ${tpl.description}

EXACT OBJECT REQUIREMENTS (include ALL of these precisely):
${lines}

SCENE SPECIFICATIONS:
- Background: ${tpl.background}
- Lighting: ${tpl.lighting}
- Style: Clean, bright cartoon illustration with excellent clarity
- Object sizing: large enough and well-separated with clear boundaries
- Colors: Use exactly (${colors})${noDupes}

CRITICAL ACCURACY REQUIREMENTS:
- Exact counts/colors/positions
- NO extra objects beyond those listed
- High contrast; avoid overlaps and tiny duplicates
- Educational; each object easily identifiable.`;
}

function hexToHsl(hex) {
  const m=(hex||'#777777').replace('#',''), n=parseInt(m||'777777',16);
  const r=(n>>16)&255, g=(n>>8)&255, b=n&255;
  const R=r/255, G=g/255, B=b/255, M=Math.max(R,G,B), m2=Math.min(R,G,B);
  let h,s,l=(M+m2)/2;
  if (M===m2){h=s=0;} else { const d=M-m2; s=l>0.5? d/(2-M-m2): d/(M+m2);
    switch(M){case R:h=(G-B)/d + (G<B?6:0);break;case G:h=(B-R)/d + 2;break;default:h=(R-G)/d + 4;}
    h/=6;
  }
  return {h,s,l};
}
function bboxArea(inst){ const [,,w=0,h=0]=inst?.bbox||[]; return w*h; }
const VIS_OK = new Set(['excellent','good']);

function iou(a, b) {
  const [ax, ay, aw, ah] = a || [0,0,0,0];
  const [bx, by, bw, bh] = b || [0,0,0,0];
  const x1 = Math.max(ax, bx), y1 = Math.max(ay, by);
  const x2 = Math.min(ax+aw, bx+bw), y2 = Math.min(ay+ah, by+bh);
  const w = Math.max(0, x2 - x1), h = Math.max(0, y2 - y1);
  const inter = w*h, ua = aw*ah + bw*bh - inter;
  return ua > 0 ? inter/ua : 0;
}
function dedupeInstances(instances, iouThresh = 0.5) {
  const out = [];
  for (const inst of instances || []) {
    if (!out.some(o => iou(o.bbox, inst.bbox) >= iouThresh)) out.push(inst);
  }
  return out;
}

export class ImageQuestionGenerator {
  constructor(options = {}) {
    this.options = { ...DEFAULT_OPTIONS, ...options };
    this.debugMode = !!this.options.debug;
    this.apiKey = process.env.OPENAI_API_KEY;
    if (!this.apiKey) throw new Error('OPENAI_API_KEY not set');
    this.http = axios.create({
      baseURL: 'https://api.openai.com/v1',
      headers: { Authorization: `Bearer ${this.apiKey}`, 'Content-Type': 'application/json' },
      timeout: 120000
    });
  }

  setDebugMode(enabled) { this.debugMode = !!enabled; }
  
  debugLog(msg, type='info') {
    if (!this.debugMode) return;
    const t = new Date().toISOString();
    const e = type==='error'?'❌':type==='success'?'✅':type==='warning'?'⚠️':'🔍';
    console.log(`${e} [${t}] IMAGE_QUESTION: ${msg}`);
  }

  selectSceneByDifficulty(difficulty) {
    return sceneByDifficulty(difficulty);
  }

  async generateImageQuestionPuzzle(difficulty = 'medium', maxRetries = 2, opts = {}) {
    const options = { ...this.options, ...opts };
    const TH = options.lenient ? RELAXED : STRICT;

    for (let attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        const template = this.selectSceneByDifficulty(difficulty);
        this.debugLog(`🎨 Selected scene: ${template.theme} (${template.complexity})`);

        // 1) Generate image
        const imagePrompt = preciseImagePrompt(template);
        const imgBuf = await this.generateImage(imagePrompt);
        this.debugLog(`📦 Image generated (${imgBuf.length} bytes)`);

        // 2) Analyze (+ consensus refinement)
        const analysis = await this.analyzeWithConsensus(imgBuf, template, options);
        if (!analysis.objects_analysis?.length) throw new Error('Empty analysis');

        // 3) Build unambiguous questions WITH VERIFIED LOCATION OPTIONS
        const questions = this.buildQuestions(analysis, template, difficulty, TH, options);
        if (!questions.length) {
            throw new Error(`No valid questions generated for ${template.theme}`);
        }

        // 4) Upload image (Supabase)
        const imageUrl = await this.uploadImage(`img_qa_${Date.now()}_${performance.now().toString().replace('.', '')}`, imgBuf);
        this.debugLog(`☁️ Uploaded to ${imageUrl}`);

        // 5) Bundle puzzle
        const puzzleData = {
          puzzleId: `img_qa_${Date.now()}`,
          type: 'image_question',
          theme: template.theme,
          description: `Answer questions about this ${template.theme.toLowerCase()}`,
          imageUrl,
          imageWidth: 1024,
          imageHeight: 1024,
          imagePrompt,
          questions,
          totalQuestions: questions.length,
          difficulty,
          timeLimit: this.getTimeLimitForDifficulty(difficulty, questions.length),
          gameSettings: { questionsPerRound: questions.length, allowReview: true, shuffleQuestions: true, showProgress: true },
          imageStatus: 'uploaded',
          imageStorageProvider: 'supabase',
          imageGeneratedAt: new Date().toISOString(),
          questionsGeneratedAt: new Date().toISOString(),
          generationMethod: 'dalle_with_vision_analysis_consensus',
          sceneComplexity: template.complexity,
          imageAnalysis: analysis,
          accuracyMethod: 'vision_verified_consensus'
        };

        return {
          success: true,
          puzzleData: this.formatForPuzzleSystem(puzzleData),
          attempts: attempt
        };
      } catch (e) {
        this.debugLog(`Attempt ${attempt} failed: ${e.message}`, 'error');
        if (attempt === maxRetries) return { success: false, message: e.message, attempts: attempt };
        await new Promise(r => setTimeout(r, 1500));
      }
    }
  }

  async generateImage(prompt) {
    const res = await this.http.post('/images/generations', {
      model: 'dall-e-3',
      prompt,
      n: 1,
      size: '1024x1024',
      response_format: 'b64_json'
    }, { timeout: 60000 });
    const b64 = res.data?.data?.[0]?.b64_json;
    if (!b64) throw new Error('No image data from DALL·E');
    return Buffer.from(b64, 'base64');
  }

  // ── Analysis (strict → soft fallback → grid sweep → container sweep → consensus) ──
  async analyzeWithConsensus(imageBuffer, template, options) {
    const strict = await this.analyzeStrict(imageBuffer, template).catch(()=>null);
    let analysis = strict;

    if (!analysis || !analysis.objects_analysis?.some(o => (o.actual_count||0) > 0)) {
      this.debugLog('Strict analysis found nothing; using soft analyzer', 'warning');
      analysis = await this.analyzeSoft(imageBuffer, template);
    }

    if (options.useConsensus) {
      this.debugLog('🔍 Running consensus refinement (grid + containers + dedupe)…');
      analysis = await this.refineConsensus(imageBuffer, template, analysis);
    }
    return analysis;
  }

  async analyzeStrict(imageBuffer, template) {
    const base64 = imageBuffer.toString('base64');
    const expectedTypes = template.requiredObjects.map(o => o.name.split(' ').pop());
    const skeleton = {
      objects_analysis: expectedTypes.map(t => ({
        object_type: t, instances: [],
        actual_count: 0, count_certainty: 0, color_uniformity: 0, matches_expectation: false
      })),
      scene_assessment: { overall_clarity: "fair", object_separation: 0.5, total_objects_visible: 0 },
      discrepancies: []
    };

    const prompt = `
Return ONLY valid JSON and keep the EXACT same array length & order as in the skeleton below.
- Fill each "objects_analysis[i]" using the same object_type.
- For each instance include: bbox [x,y,w,h] in 0..1, dominant_hex "#RRGGBB",
  color_label in {"red","blue","green","yellow","purple","orange","pink","brown","black","white","gray"},
  visibility in {"excellent","good","fair","poor"}.
- actual_count = instances.length; provide count_certainty [0..1], color_uniformity [0..1].
- Fill scene_assessment.

Skeleton:
${JSON.stringify(skeleton, null, 2)}`;

    const res = await this.http.post('/chat/completions', {
      model: 'gpt-4o',
      response_format: { type: 'json_object' },
      temperature: 0,
      max_tokens: 1800,
      messages: [{
        role: 'user',
        content: [
          { type: 'text', text: prompt },
          { type: 'image_url', image_url: { url: `data:image/png;base64,${base64}`, detail: 'high' } }
        ]
      }]
    });

    const json = JSON.parse(res.data.choices[0].message.content || '{}');
    let list = Array.isArray(json.objects_analysis) ? json.objects_analysis : [];
    if (list.length !== skeleton.objects_analysis.length) list = skeleton.objects_analysis;
    else list = list.map((o,i)=>({ ...o, object_type: skeleton.objects_analysis[i].object_type }));

    return {
      objects_analysis: list,
      scene_assessment: json.scene_assessment || skeleton.scene_assessment,
      discrepancies: json.discrepancies || []
    };
  }

  async analyzeSoft(imageBuffer, template) {
    const base64 = imageBuffer.toString('base64');
    const expectedTypes = template.requiredObjects.map(o => o.name.split(' ').pop());
    const prompt = `
Return ONLY JSON. Identify items that best match: ${expectedTypes.join(', ')}.
For each type, estimate count and instances (bbox, dominant_hex, color_label, visibility).
Include scene_assessment (overall_clarity, object_separation, total_objects_visible).`;

    const res = await this.http.post('/chat/completions', {
      model: 'gpt-4o',
      response_format: { type: 'json_object' },
      temperature: 0.2,
      max_tokens: 1800,
      messages: [{
        role: 'user',
        content: [
          { type: 'text', text: prompt },
          { type: 'image_url', image_url: { url: `data:image/png;base64,${base64}`, detail: 'high' } }
        ]
      }]
    });

    const json = JSON.parse(res.data.choices[0].message.content || '{}');

    const grouped = new Map(expectedTypes.map(t=>[t.toLowerCase(), {
      object_type:t, instances:[], actual_count:0, count_certainty:0, color_uniformity:0, matches_expectation:false
    }]));
    for (const o of (json.objects_analysis||[])) {
      const k = String(o.object_type||'').toLowerCase();
      if (!grouped.has(k)) continue;
      const g = grouped.get(k);
      g.instances.push(...(o.instances||[]));
      g.actual_count += (o.actual_count||0);
      g.count_certainty = Math.max(g.count_certainty, o.count_certainty ?? 0);
      g.color_uniformity = Math.max(g.color_uniformity, o.color_uniformity ?? 0);
    }

    return {
      objects_analysis: expectedTypes.map(t=>grouped.get(t.toLowerCase())),
      scene_assessment: json.scene_assessment || { overall_clarity:'fair', object_separation:0.6, total_objects_visible:0 },
      discrepancies: json.discrepancies || []
    };
  }

  async analyzeGrid(imageBuffer, template) {
    const base64 = imageBuffer.toString('base64');
    const expectedTypes = template.requiredObjects.map(o => o.name.split(' ').pop());
    const prompt = `
Scan the image in a 3x3 grid (cells: TL, TC, TR, ML, MC, MR, BL, BC, BR).
For EACH expected type (${expectedTypes.join(', ')}):
- List EVERY instance with bbox [x,y,w,h] (0..1), color_label, visibility.
- Assign the instance to the grid cell where its center falls.
Return ONLY JSON:
{
  "per_type": {
    "<type>": {
      "instances": [ { "bbox":[...], "color_label":"...", "visibility":"..." } ],
      "by_cell": { "TL": n, "TC": n, "TR": n, "ML": n, "MC": n, "MR": n, "BL": n, "BC": n, "BR": n },
      "count_certainty": 0..1
    }
  }
}`;
    const res = await this.http.post('/chat/completions', {
      model: 'gpt-4o',
      response_format: { type: 'json_object' },
      temperature: 0,
      max_tokens: 1600,
      messages: [{
        role: 'user',
        content: [
          { type: 'text', text: prompt },
          { type: 'image_url', image_url: { url: `data:image/png;base64,${base64}`, detail: 'high' } }
        ]
      }]
    });
    const j = JSON.parse(res.data.choices[0].message.content || '{}');
    return j?.per_type || {};
  }

  async analyzeContainers(imageBuffer, template) {
    const base64 = imageBuffer.toString('base64');
    const expectedTypes = template.requiredObjects.map(o => o.name.split(' ').pop());
    const clustered = expectedTypes.filter(t => /pencil|pen|marker|crayon/i.test(t)).join(', ') || '(none)';

    const prompt = `
Focus on potential containers (cups/holders on left and right).
For EACH of: ${clustered}
- Count items "in left cup", "in right cup", and "loose on desk".
- List instances with bbox, color_label, visibility.
Return ONLY JSON:
{
  "containers": {
    "left_cup": { "<type>": { "count": n } },
    "right_cup": { "<type>": { "count": n } },
    "desk": { "<type>": { "count": n } }
  },
  "instances": { "<type>": [ { "bbox":[...], "color_label":"...", "visibility":"..." } ] }
}`;
    const res = await this.http.post('/chat/completions', {
      model: 'gpt-4o',
      response_format: { type: 'json_object' },
      temperature: 0,
      max_tokens: 1600,
      messages: [{
        role: 'user',
        content: [
          { type: 'text', text: prompt },
          { type: 'image_url', image_url: { url: `data:image/png;base64,${base64}`, detail: 'high' } }
        ]
      }]
    });
    const j = JSON.parse(res.data.choices[0].message.content || '{}');
    return j;
  }

  async refineConsensus(imageBuffer, template, analysis) {
    try {
      const grid = await this.analyzeGrid(imageBuffer, template).catch(()=> ({}));
      const cont = await this.analyzeContainers(imageBuffer, template).catch(()=> ({}));
      const byTypeGrid = grid;                       // { type: { instances: [...] } }
      const byTypeContInst = cont?.instances || {};  // { type: [instances...] }

      for (const row of analysis.objects_analysis || []) {
        const t = row.object_type;
        const allInst = [
          ...(row.instances || []),
          ...((byTypeGrid[t]?.instances) || []),
          ...((byTypeContInst[t]) || []),
        ];
        const deduped = dedupeInstances(allInst, 0.5);
        row.instances = deduped;
        row.actual_count = deduped.length;

        const gridCount = (byTypeGrid[t]?.instances || []).length;
        const contCount = (byTypeContInst[t] || []).length;
        if ((gridCount > 1 && contCount > 1) || (gridCount > 1 && row.actual_count > 1)) {
          row.count_certainty = Math.max(0.85, row.count_certainty || 0.8);
        }
      }
    } catch (e) {
      this.debugLog(`Consensus failed: ${e.message}`, 'warning');
    }
    return analysis;
  }

  // ── Question generation with VERIFIED LOCATION OPTIONS ──
  buildQuestions(analysis, template, difficulty, TH, options) {
    const questions = [];
    const clarity = analysis.scene_assessment?.overall_clarity || 'fair';
    const sceneOK = VIS_OK.has(clarity);

    let objs = (analysis.objects_analysis || []).filter(o => (o.actual_count || 0) > 0);
    if (!options.includePlurals) objs = objs.filter(o => o.actual_count === 1);

    if (!objs.length) return questions;

    this.debugLog(`🎯 Building questions for ${objs.length} objects`);

    for (const o of objs) {
      const inst = o.instances?.[0];
      const visScore = (() => {
        const s = { excellent: 1, good: 0.8, fair: 0.5, poor: 0.2 };
        return (o.instances || []).length ? 
          (o.instances || []).reduce((a, i) => a + (s[i.visibility] || 0), 0) / (o.instances || []).length : 0;
      })();
      const sat = hexToHsl(inst?.dominant_hex).s;

      // Color questions (unchanged)
      if (options.askColor && sceneOK && visScore >= TH.VISIBILITY && sat >= TH.SAT_MIN) {
        const color = (inst?.color_label || '').toLowerCase();
        if (color) {
          const all = ['red', 'blue', 'green', 'yellow', 'purple', 'orange', 'pink', 'brown', 'black', 'white', 'gray'];
          const optionsList = [color, ...all.filter(c => c !== color).sort(() => Math.random() - 0.5).slice(0, 3)]
            .sort(() => Math.random() - 0.5);
          
          questions.push({
            id: questions.length + 1,
            question: `What color is the ${o.object_type}?`,
            answer: color,
            type: 'color',
            difficulty: 'easy',
            options: optionsList,
            hint: `Look at the ${o.object_type}'s color`,
            confidence: 'high'
          });
        }
      }

      // ENHANCED: Location questions with verified hardcoded option distribution
      if (options.askLocation && sceneOK && visScore >= TH.VISIBILITY && inst && bboxArea(inst) >= TH.AREA_MIN) {
        const detectedPosition = detectGridPosition(inst.bbox);
        
        // Generate well-distributed options using verified hardcoded lookup
        const smartOptions = generateLocationOptions(detectedPosition, 4);
        
        this.debugLog(`📍 Location question for ${o.object_type}: ${detectedPosition}`);
        this.debugLog(`🎲 Generated verified options: ${smartOptions.join(', ')}`);

        questions.push({
          id: questions.length + 1,
          question: `Where is the ${o.object_type} located?`,
          answer: detectedPosition,
          type: 'location',
          difficulty: 'easy',
          options: smartOptions,
          hint: `Look at the ${o.object_type}'s position in the image`,
          confidence: 'high'
        });
      }

      // Counting questions (unchanged)
      if (options.allowCounting) {
        questions.push({
          id: questions.length + 1,
          question: `How many ${o.object_type} are in the image?`,
          answer: '1',
          type: 'counting',
          difficulty: 'easy',
          options: ['1', '2', '3', '4'].sort(() => Math.random() - 0.5),
          hint: `Count the ${o.object_type}`,
          confidence: 'high'
        });
      }
    }

    this.debugLog(`✅ Generated ${questions.length} questions total`);
    return questions;
  }

  // ── Upload + packing ──
  async uploadImage(puzzleId, imageBuffer) {
    try {
      const { supabase } = await import('../config/database.js');
      const fileName = `image-questions/${puzzleId}.jpg`;
      const { error } = await supabase.storage
        .from('puzzle-images')
        .upload(fileName, imageBuffer, { contentType: 'image/jpeg', upsert: true, cacheControl: '3600' });
      if (error) throw new Error(error.message);
      const { data: { publicUrl } } = supabase.storage.from('puzzle-images').getPublicUrl(fileName);
      if (!publicUrl) throw new Error('No public URL from Supabase');
      return publicUrl;
    } catch (e) {
      throw new Error(`Image upload error: ${e.message}`);
    }
  }

  getTimeLimitForDifficulty(difficulty, questionCount) {
    const base = { easy:20000, medium:25000, hard:30000 }[difficulty] ?? 25000;
    return base * Math.max(1, questionCount);
  }

  formatForPuzzleSystem(puzzleData) {
    const questionData = {
      puzzleId: puzzleData.puzzleId,
      theme: puzzleData.theme,
      description: puzzleData.description,
      imageUrl: puzzleData.imageUrl,
      imageWidth: puzzleData.imageWidth,
      imageHeight: puzzleData.imageHeight,
      questions: puzzleData.questions,
      totalQuestions: puzzleData.totalQuestions,
      timeLimit: puzzleData.timeLimit,
      gameSettings: puzzleData.gameSettings,
      imageStatus: puzzleData.imageStatus,
      generationMethod: puzzleData.generationMethod,
      accuracyMethod: puzzleData.accuracyMethod
    };
    const answerData = {
      questions: puzzleData.questions,
      correctAnswers: puzzleData.questions.map(q => q.answer),
      totalQuestions: puzzleData.totalQuestions,
      maxScore: puzzleData.totalQuestions * 10,
      passingScore: Math.ceil(puzzleData.totalQuestions * 0.6) * 10,
      imageAnalysis: puzzleData.imageAnalysis
    };
    return {
      question: JSON.stringify(questionData),
      answer: JSON.stringify(answerData),
      hint: `Answer ${puzzleData.totalQuestions} questions about this ${puzzleData.theme.toLowerCase()}`,
      difficulty: puzzleData.difficulty,
      metadata: {
        theme: puzzleData.theme,
        questionCount: puzzleData.totalQuestions,
        imageGenerated: true,
        generatedAt: puzzleData.imageGeneratedAt,
        puzzleType: 'image_question',
        requiresImageGeneration: true,
        hasMultipleChoice: true,
        imageUrl: puzzleData.imageUrl,
        generationMethod: puzzleData.generationMethod,
        sceneComplexity: puzzleData.sceneComplexity,
        timeLimit: puzzleData.timeLimit,
        accuracyMethod: puzzleData.accuracyMethod,
        visionVerified: true
      }
    };
  }
}

// Export for use in puzzle generation system
export const imageQuestionGenerator = new ImageQuestionGenerator();