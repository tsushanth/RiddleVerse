import { DIFFICULTY_LEVELS, PUZZLE_TYPES } from '../config/puzzleConfig.js';

// Base prompt templates for each puzzle type
export const PUZZLE_PROMPTS = {
  [PUZZLE_TYPES.MATH_ESTIMATION]: {
    base: `Generate a "Math Estimation Puzzle" in JSON format.

Instructions:
- Provide an array of numbers for estimation practice.
- Use numbers with appropriate decimal places for the difficulty level.
- Keep numbers reasonably sized to allow for visual range display.
- Numbers should make mental calculation challenging but manageable for estimation.
- Calculate the exact sum and provide it as the answer (round to appropriate decimal places).
- Provide a brief hint about estimation strategies.

JSON format:
{
  "numbers": [12.5, 23.75, 8.3, 15.2],
  "sum": 59.75,
  "difficulty": "{{difficulty}}",
  "hint": "Round each number to the nearest whole number first, then add"
}`,
    
    difficultySpecific: {
      [DIFFICULTY_LEVELS.EASY]: {
        requirements: "2-3 numbers between 1-50 with whole numbers or 1 decimal place",
        example: "[12, 25, 8] → sum: 45"
      },
      [DIFFICULTY_LEVELS.MEDIUM]: {
        requirements: "3-4 numbers between 10-100 with 1-2 decimal places",
        example: "[23.5, 67.25, 41.8] → sum: 132.55"
      },
      [DIFFICULTY_LEVELS.HARD]: {
        requirements: "4-5 numbers between 50-500 with 2 decimal places",
        example: "[156.75, 289.50, 94.25, 367.80] → sum: 908.30"
      },
      [DIFFICULTY_LEVELS.EXPERT]: {
        requirements: "5-6 numbers between 100-1000 with 2-3 decimal places",
        example: "[345.675, 567.125, 234.890, 678.345, 123.456] → sum: 1949.491"
      }
    }
  },

  [PUZZLE_TYPES.MATH_TIPPING]: {
    base: `Generate a "Math Tipping Puzzle" in JSON format.

Instructions:
- Provide a bill amount within the specified range.
- Use appropriate tip percentages for the difficulty level.
- Include the calculated tip amount.

JSON format:
{
  "billAmount": 47.50,
  "tipPercentage": 20,
  "tipAmount": 9.50,
  "difficulty": "{{difficulty}}",
  "hint": "Calculate {{tipPercentage}}% of the bill amount"
}`,

    difficultySpecific: {
      [DIFFICULTY_LEVELS.EASY]: {
        requirements: "Bill amount: $20-60, Tip percentages: 15%, 20%",
        example: "Bill: $40, Tip: 20% = $8.00"
      },
      [DIFFICULTY_LEVELS.MEDIUM]: {
        requirements: "Bill amount: $50-120, Tip percentages: 15%, 18%, 20%, 25%",
        example: "Bill: $78.50, Tip: 18% = $14.13"
      },
      [DIFFICULTY_LEVELS.HARD]: {
        requirements: "Bill amount: $100-250, Tip percentages: 12%, 15%, 18%, 20%, 22%, 25%",
        example: "Bill: $167.80, Tip: 22% = $36.92"
      },
      [DIFFICULTY_LEVELS.EXPERT]: {
        requirements: "Bill amount: $200-500, Tip percentages: 10%, 12%, 15%, 18%, 20%, 22%, 25%, 30%",
        example: "Bill: $347.65, Tip: 17% = $59.10"
      }
    }
  },

  [PUZZLE_TYPES.PERCENTAGES]: {
    base: `Generate a "Percentages Puzzle" in JSON format.

Instructions:
- Provide a total within the specified range.
- Use appropriate percentages for the difficulty level.
- Calculate the percentage amount accurately.

JSON format:
{
  "total": 400,
  "percentage": 25,
  "answer": 100,
  "difficulty": "{{difficulty}}",
  "hint": "What is {{percentage}}% of {{total}}?"
}`,

    difficultySpecific: {
      [DIFFICULTY_LEVELS.EASY]: {
        requirements: "Total: 100-500, Percentages: 10%, 20%, 25%, 50%",
        example: "25% of 200 = 50"
      },
      [DIFFICULTY_LEVELS.MEDIUM]: {
        requirements: "Total: 200-1000, Percentages: 15%, 30%, 35%, 40%, 60%, 75%",
        example: "35% of 480 = 168"
      },
      [DIFFICULTY_LEVELS.HARD]: {
        requirements: "Total: 500-2000, Percentages: 12%, 18%, 23%, 37%, 42%, 67%",
        example: "37% of 1240 = 458.8"
      },
      [DIFFICULTY_LEVELS.EXPERT]: {
        requirements: "Total: 1000-5000, Percentages: 13%, 17%, 19%, 23%, 29%, 31%",
        example: "23% of 2847 = 654.81"
      }
    }
  },

  [PUZZLE_TYPES.DIVISION]: {
    base: `Generate a "Division Puzzle" in JSON format.

Instructions:
- Provide division problems within the specified ranges.
- Each pair should be [dividend, divisor, quotient].
- Use numbers that divide evenly (no remainders).

JSON format:
{
  "problems": [
    [144, 12, 12],
    [225, 15, 15],
    [168, 14, 12]
  ],
  "difficulty": "{{difficulty}}",
  "hint": "Think about multiplication tables to help with division"
}`,

    difficultySpecific: {
      [DIFFICULTY_LEVELS.EASY]: {
        requirements: "2-3 problems, Dividend: 24-144, Divisor: 2-12",
        example: "[72, 8, 9] → 72÷8=9"
      },
      [DIFFICULTY_LEVELS.MEDIUM]: {
        requirements: "3-4 problems, Dividend: 100-500, Divisor: 5-25",
        example: "[315, 15, 21] → 315÷15=21"
      },
      [DIFFICULTY_LEVELS.HARD]: {
        requirements: "4-5 problems, Dividend: 400-1500, Divisor: 12-40",
        example: "[1248, 24, 52] → 1248÷24=52"
      },
      [DIFFICULTY_LEVELS.EXPERT]: {
        requirements: "5-6 problems, Dividend: 1000-5000, Divisor: 20-80",
        example: "[3584, 56, 64] → 3584÷56=64"
      }
    }
  },

  [PUZZLE_TYPES.AVERAGE]: {
    base: `Generate an "Average Puzzle" in JSON format.

Instructions:
- Provide an array of numbers within the specified range.
- Ensure the average is a whole number.
- Calculate the exact average.

JSON format:
{
  "numbers": [84, 92, 76],
  "average": 84,
  "difficulty": "{{difficulty}}",
  "hint": "Add all numbers together and divide by how many numbers there are"
}`,

    difficultySpecific: {
      [DIFFICULTY_LEVELS.EASY]: {
        requirements: "2-3 numbers between 10-50, average must be whole number",
        example: "[20, 30, 40] → average: 30"
      },
      [DIFFICULTY_LEVELS.MEDIUM]: {
        requirements: "3-4 numbers between 25-100, average must be whole number",
        example: "[65, 78, 89, 92] → average: 81"
      },
      [DIFFICULTY_LEVELS.HARD]: {
        requirements: "4-5 numbers between 50-200, average must be whole number",
        example: "[134, 156, 178, 162, 145] → average: 155"
      },
      [DIFFICULTY_LEVELS.EXPERT]: {
        requirements: "5-6 numbers between 100-500, average must be whole number",
        example: "[245, 367, 289, 334, 421, 298] → average: 326"
      }
    }
  },

  [PUZZLE_TYPES.DISCOUNTS]: {
    base: `Generate a "Price Ordering Puzzle" in JSON format.

Instructions:
- Provide items with different prices and discount scenarios for ordering practice.
- Each item should have: name, icon (emoji), originalPrice, and discountPercentage (or null for no discount).
- Use realistic prices and common discount percentages.
- Items should be varied so that after applying discounts, they have different final prices.
- Calculate the correct order from least to most expensive (ascending by final price).
- Include creative item names and appropriate emojis.
- Follow the specific difficulty requirements below.

JSON format:
{
  "items": [
    {"name": "T-Shirt", "icon": "👕", "originalPrice": 25.00, "discountPercentage": 20},
    {"name": "Book", "icon": "📚", "originalPrice": 18.00, "discountPercentage": null},
    {"name": "Coffee Mug", "icon": "☕", "originalPrice": 12.00, "discountPercentage": 15},
    {"name": "Phone Case", "icon": "📱", "originalPrice": 30.00, "discountPercentage": 40}
  ],
  "correctOrder": [3, 2, 1, 4],
  "difficulty": "{{difficulty}}",
  "hint": "Calculate the final price after applying discounts, then order from least to most expensive"
}`,

    difficultySpecific: {
      [DIFFICULTY_LEVELS.EASY]: {
        requirements: "3-4 items, $5-25 prices, simple discounts (10%, 20%, 25%, 50%), final prices should differ by at least $3-5, at least one item with no discount",
        example: "Book $20 (no discount) → $20, Mug $15 (20% off) → $12, Hat $10 (50% off) → $5"
      },
      [DIFFICULTY_LEVELS.MEDIUM]: {
        requirements: "4 items, $10-50 prices, standard discounts (15%, 20%, 25%, 30%, 35%), final prices should differ by $2-4, maximum one item with no discount",
        example: "Shirt $35 (25% off) → $26.25, Book $28 (15% off) → $23.80, Mug $20 (30% off) → $14.00"
      },
      [DIFFICULTY_LEVELS.HARD]: {
        requirements: "4-5 items, $20-80 prices, complex discounts (12%, 18%, 22%, 28%, 33%, 37%, 42%), final prices should differ by $1-3, all items must have discounts, include at least one discount that doesn't end in 0 or 5",
        example: "Jacket $65 (28% off) → $46.80, Shoes $55 (18% off) → $45.10, Watch $70 (33% off) → $46.90"
      },
      [DIFFICULTY_LEVELS.EXPERT]: {
        requirements: "5-6 items, $30-120 prices, challenging discounts (13%, 17%, 23%, 27%, 31%, 38%, 43%, 47%), final prices should differ by $0.50-2, all items have discounts, multiple discounts that create very close final prices, include prime number discounts",
        example: "Laptop $89 (23% off) → $68.53, Tablet $95 (27% off) → $69.35, Speaker $85 (19% off) → $68.85"
      }
    }
  },

  [PUZZLE_TYPES.ANTONYMS]: {
    base: `Generate multiple "Antonyms Puzzle" sets in JSON format to maximize LLM usage.
  
  Instructions:
  - Generate {{setCount}} different antonym puzzle sets
  - Each set should have unique word pairs (no repetition across sets)
  - Each pair should contain words that are direct opposites
  - Use words appropriate for the difficulty level
  - Ensure all pairs are legitimate antonyms
  
  JSON format:
  {
    "puzzleSets": [
      {
        "pairs": [["hot", "cold"], ["big", "small"], ["fast", "slow"]],
        "hint": "Match each word with its opposite meaning"
      },
      {
        "pairs": [["bright", "dark"], ["loud", "quiet"], ["rough", "smooth"]],
        "hint": "Find the word that means the opposite"
      }
    ],
    "difficulty": "{{difficulty}}",
    "totalSets": {{setCount}}
  }`,
  
    difficultySpecific: {
      [DIFFICULTY_LEVELS.EASY]: {
        requirements: "Generate 5 sets, each with 3-4 pairs, elementary level words, no word repetition",
        example: "Set 1: hot/cold, big/small, happy/sad | Set 2: up/down, fast/slow, old/new"
      },
      [DIFFICULTY_LEVELS.MEDIUM]: {
        requirements: "Generate 4 sets, each with 4-5 pairs, middle school level words, no word repetition",
        example: "Set 1: ancient/modern, generous/selfish | Set 2: brave/cowardly, wise/foolish"
      },
      [DIFFICULTY_LEVELS.HARD]: {
        requirements: "Generate 3 sets, each with 5-6 pairs, high school level words, no word repetition",
        example: "Set 1: meticulous/careless, abundant/scarce | Set 2: transparent/opaque, rigid/flexible"
      },
      [DIFFICULTY_LEVELS.EXPERT]: {
        requirements: "Generate 3 sets, each with 6-7 pairs, college level vocabulary, no word repetition",
        example: "Set 1: magnanimous/petty, ubiquitous/rare | Set 2: ephemeral/permanent, lucid/obscure"
      }
    }
  },

  [PUZZLE_TYPES.WORD_ASSOCIATION]: {
    base: `Generate a "Word Association Puzzle" in JSON format.

Instructions:
- Provide a list of 4 clue words that are all strongly associated with a hidden target word.
- The target word must be a common English word and logically connect to all the clue words.
- Do NOT include synonyms or direct variants of the target word.
- The goal is to guess the target word based on the clue words.
- Also provide a brief hint to help the solver.
- Ensure only one correct answer exists.

JSON format:
{
  "clueWords": ["clue1", "clue2", "clue3", "clue4"],
  "targetWord": "solution word",
  "difficulty": "{{difficulty}}",
  "hint": "A helpful nudge or subtle clue"
}`,

    difficultySpecific: {
      [DIFFICULTY_LEVELS.EASY]: {
        requirements: "Common nouns (animals, food, household items), direct associations",
        example: "clues: bark, tail, bone, fetch → target: dog"
      },
      [DIFFICULTY_LEVELS.MEDIUM]: {
        requirements: "General concepts (emotions, activities, places), clear but less direct associations",
        example: "clues: stage, audience, curtain, applause → target: theater"
      },
      [DIFFICULTY_LEVELS.HARD]: {
        requirements: "Abstract concepts (qualities, processes, ideas), indirect associations",
        example: "clues: scales, blindfold, gavel, verdict → target: justice"
      },
      [DIFFICULTY_LEVELS.EXPERT]: {
        requirements: "Complex or specialized concepts, subtle associations requiring knowledge",
        example: "clues: catalyst, reaction, bonds, electrons → target: chemistry"
      }
    }
  },
  [PUZZLE_TYPES.SENTENCE_TRANSITIONS]: {
    base: `Generate multiple "Sentence Transitions Puzzle" sets in JSON format to maximize LLM usage efficiency.
    
    Instructions:
    - Generate {{setCount}} different sentence transition puzzles
    - Each puzzle should have unique sentence pairs (no repetition across puzzles)
    - Each puzzle needs a transition word removed and provided as the correct answer
    - Generate 3-4 multiple choice options (1 correct, 2-3 plausible distractors)
    - Focus on common transition categories: contrast, cause-effect, addition, sequence, emphasis
    - Use natural, conversational language suitable for speaking practice
    - Ensure the context makes the correct transition obvious to native speakers
    - Make distractors plausible but clearly incorrect in context
    
    Common transition types:
    - Contrast: however, nevertheless, on the other hand, instead, yet, still
    - Cause-Effect: therefore, consequently, as a result, thus, hence
    - Addition: furthermore, moreover, additionally, also, besides
    - Sequence: meanwhile, subsequently, then, next, finally
    - Emphasis: indeed, in fact, certainly, obviously, clearly
    
    JSON format:
    {
      "puzzleSets": [
        {
          "sentence1": "Lake Hillier in Western Australia is a vivid pink color that has intrigued scientists for years.",
          "sentence2": "experts don't know for sure what produces the hue.",
          "correctTransition": "However",
          "options": ["However", "Therefore", "Furthermore", "Meanwhile"],
          "explanation": "The second sentence contrasts with the expectation that scientists would know the cause, making 'However' the correct choice.",
          "transitionType": "contrast",
          "hint": "Look for the logical relationship between the two sentences - does the second sentence contrast, continue, or explain the first?"
        },
        {
          "sentence1": "The new medication showed promising results in clinical trials.",
          "sentence2": "the FDA approved it for general use.",
          "correctTransition": "Therefore",
          "options": ["Therefore", "However", "Meanwhile", "Additionally"],
          "explanation": "The approval followed as a logical result of the promising trials, making 'Therefore' correct.",
          "transitionType": "cause-effect",
          "hint": "Consider whether the second sentence is a result of the first, contrasts with it, or adds new information."
        }
      ],
      "difficulty": "{{difficulty}}",
      "totalSets": {{setCount}}
    }`,
  
    difficultySpecific: {
      [DIFFICULTY_LEVELS.EASY]: {
        requirements: "Generate 6 sets, simple obvious transitions, common topics, clear contrast/addition/sequence relationships, basic vocabulary, obvious wrong answers",
        example: "Sentence 1: 'It was raining heavily.' Sentence 2: '_____ we decided to stay indoors.' → Answer: 'Therefore' (vs. However, Meanwhile, Furthermore)"
      },
      [DIFFICULTY_LEVELS.MEDIUM]: {
        requirements: "Generate 5 sets, moderate complexity, mixed transition types, some abstract concepts, intermediate vocabulary, plausible distractors",
        example: "Sentence 1: 'The new policy was intended to reduce costs.' Sentence 2: '_____ many employees were concerned about job security.' → Answer: 'Nevertheless' (vs. Therefore, Furthermore, Subsequently)"
      },
      [DIFFICULTY_LEVELS.HARD]: {
        requirements: "Generate 4 sets, complex relationships, subtle distinctions between transitions, advanced vocabulary, academic/professional contexts, close distractors",
        example: "Sentence 1: 'The research methodology was rigorous.' Sentence 2: '_____ the conclusions remained tentative due to limited sample size.' → Answer: 'Nonetheless' (vs. Consequently, Moreover, Subsequently)"
      },
      [DIFFICULTY_LEVELS.EXPERT]: {
        requirements: "Generate 4 sets, sophisticated language, nuanced transition meanings, professional/academic register, very subtle distinctions, multiple viable options with best choice",
        example: "Sentence 1: 'The economic indicators suggested recovery.' Sentence 2: '_____ analysts remained cautious given historical volatility patterns.' → Answer: 'Notwithstanding' (vs. Consequently, Furthermore, Thereafter)"
      }
    }
  },

[PUZZLE_TYPES.TRIVIA]: {
  base: `Generate a "Trivia Puzzle" in JSON format.

Instructions:
- Create a factual question with a clear, verifiable answer
- Use questions appropriate for the difficulty level
- Avoid overly specific dates, numbers, or obscure facts unless Expert level
- Include 4 multiple choice options with only one correct answer
- Make incorrect options plausible but clearly wrong
- Provide a helpful hint that guides without giving away the answer
- Cover various topics: history, science, geography, literature, general knowledge

JSON format:
{
  "question": "What is the largest planet in our solar system?",
  "answer": "Jupiter",
  "options": ["Jupiter", "Saturn", "Earth", "Neptune"],
  "correct_option": "Jupiter",
  "difficulty": "{{difficulty}}",
  "hint": "This planet is known for its Great Red Spot"
}`,

  difficultySpecific: {
    [DIFFICULTY_LEVELS.EASY]: {
      requirements: "Basic general knowledge, well-known facts, simple concepts",
      example: "What color do you get when you mix red and blue? → Purple"
    },
    [DIFFICULTY_LEVELS.MEDIUM]: {
      requirements: "Intermediate knowledge, historical events, scientific concepts",
      example: "Which scientist developed the theory of relativity? → Albert Einstein"
    },
    [DIFFICULTY_LEVELS.HARD]: {
      requirements: "Advanced knowledge, specific historical dates, complex concepts",
      example: "In which year did the Berlin Wall fall? → 1989"
    },
    [DIFFICULTY_LEVELS.EXPERT]: {
      requirements: "Specialized knowledge, obscure facts, academic-level concepts",
      example: "Which element has the chemical symbol 'Au'? → Gold"
    }
  }
},

// Add configuration for 'math' puzzle type (general math problems)
'math': {
  base: `Generate a "Math Puzzle" in JSON format.

Instructions:
- Create a mathematical problem appropriate for the difficulty level
- Include step-by-step calculation if needed
- Provide multiple choice options with one correct answer
- Make the problem clear and unambiguous
- Include a helpful hint for solving

JSON format:
{
  "question": "If a train travels 120 miles in 2 hours, what is its average speed?",
  "answer": "60 mph",
  "options": ["50 mph", "60 mph", "70 mph", "80 mph"],
  "correct_option": "60 mph",
  "difficulty": "{{difficulty}}",
  "hint": "Speed = Distance ÷ Time"
}`,

  difficultySpecific: {
    [DIFFICULTY_LEVELS.EASY]: {
      requirements: "Basic arithmetic, simple word problems, whole numbers",
      example: "What is 15 + 27? → 42"
    },
    [DIFFICULTY_LEVELS.MEDIUM]: {
      requirements: "Fractions, decimals, basic algebra, geometry",
      example: "What is 3/4 of 80? → 60"
    },
    [DIFFICULTY_LEVELS.HARD]: {
      requirements: "Complex equations, advanced geometry, statistics",
      example: "Solve for x: 2x + 5 = 17 → x = 6"
    },
    [DIFFICULTY_LEVELS.EXPERT]: {
      requirements: "Calculus concepts, advanced algebra, complex problem solving",
      example: "What is the derivative of x²? → 2x"
    }
  }
},

// Add configuration for 'storypuzzle' type
'storypuzzle': {
  base: `Generate a "Story Puzzle" in JSON format.

Instructions:
- Create a short narrative with a logical puzzle or mystery to solve
- Include all necessary information within the story
- The answer should be deducible from the given information
- Make the story engaging and age-appropriate for the difficulty level
- Provide multiple choice options if applicable
- Include a hint that guides logical thinking

JSON format:
{
  "question": "Sarah has 3 boxes. The red box contains twice as many marbles as the blue box. The green box has 5 more marbles than the blue box. If there are 23 marbles total, how many marbles are in the blue box?",
  "answer": "6",
  "options": ["4", "5", "6", "7"],
  "correct_option": "6",
  "difficulty": "{{difficulty}}",
  "hint": "Set up equations: Let blue = x, then red = 2x, green = x + 5"
}`,

  difficultySpecific: {
    [DIFFICULTY_LEVELS.EASY]: {
      requirements: "Simple logic puzzles, basic counting, straightforward scenarios",
      example: "Who ate the cookies? Use simple clues to deduce the answer"
    },
    [DIFFICULTY_LEVELS.MEDIUM]: {
      requirements: "Multi-step logic, basic deduction, moderate complexity scenarios",
      example: "Figure out the order of events using time clues and relationships"
    },
    [DIFFICULTY_LEVELS.HARD]: {
      requirements: "Complex logical reasoning, multiple variables, advanced scenarios",
      example: "Solve a mystery using multiple interconnected clues and constraints"
    },
    [DIFFICULTY_LEVELS.EXPERT]: {
      requirements: "Sophisticated logical puzzles, abstract reasoning, complex narratives",
      example: "Advanced logic problems requiring systematic elimination and deduction"
    }
  }
},

[PUZZLE_TYPES.ANAGRAM]: {
  base: `Generate multiple sets of words for anagram puzzles in JSON format to maximize LLM usage.

Instructions:
- Generate {{setCount}} different word sets for anagram creation
- Each word should be unique across all sets (no repetition)
- Words must be real English nouns, verbs, or adjectives (no proper nouns)
- Follow strict difficulty rules for word selection
- Each word should have a clear, helpful hint
- Focus on commonly used words that players will recognize

JSON format:
{
  "wordSets": [
    {
      "words": [
        {"word": "listen", "hint": "To hear carefully and pay attention", "frequency": "common"},
        {"word": "silent", "hint": "Making no sound or noise", "frequency": "common"},
        {"word": "earth", "hint": "The planet we live on", "frequency": "very_common"}
      ]
    },
    {
      "words": [
        {"word": "heart", "hint": "Organ that pumps blood", "frequency": "very_common"},
        {"word": "smart", "hint": "Having quick intelligence", "frequency": "common"},
        {"word": "start", "hint": "To begin something", "frequency": "very_common"}
      ]
    }
  ],
  "difficulty": "{{difficulty}}",
  "totalSets": {{setCount}}
}`,

  difficultySpecific: {
    [DIFFICULTY_LEVELS.EASY]: {
      requirements: "Generate 8 sets of 4-5 words each. Word length: 4-6 letters. Frequency: very_common or common. Elementary vocabulary only. Total: ~35 words",
      example: "house, plant, music, smile, clean, happy, water, light"
    },
    [DIFFICULTY_LEVELS.MEDIUM]: {
      requirements: "Generate 6 sets of 5-6 words each. Word length: 5-8 letters. Frequency: common or occasional. Middle school vocabulary. Total: ~32 words",
      example: "garden, picture, journey, kitchen, brother, simple, nature, friend"
    },
    [DIFFICULTY_LEVELS.HARD]: {
      requirements: "Generate 5 sets of 6-7 words each. Word length: 6-10 letters. Frequency: occasional or uncommon. High school vocabulary. Total: ~32 words",
      example: "elephant, mountain, treasure, beautiful, adventure, machine, evening, morning"
    },
    [DIFFICULTY_LEVELS.EXPERT]: {
      requirements: "Generate 4 sets of 7-8 words each. Word length: 7-12 letters. Frequency: uncommon or rare. College+ vocabulary. Total: ~30 words",
      example: "magnificent, extraordinary, pronunciation, encyclopedia, revolutionary, atmosphere, appreciate, government"
    }
  }
},

  'connotationwords': {
    base: `Generate a "Connotation Words Puzzle" in JSON format.

Instructions:
- Provide a set of positive and negative words.
- The words can be related to a given topic or general English.
- The words must be commonly used in English and understandable by the target difficulty level.
- The lists should NOT contain duplicates.
- The lists should have 4 words each.
- Provide a brief hint to help the solver guess the topic or the nature of the words.

JSON format:
{
  "topic": "The topic these words relate to. If general, use 'General English'.",
  "positiveWords": ["word1", "word2", "word3", "word4"],
  "negativeWords": ["word1", "word2", "word3", "word4"],
  "difficulty": "{{difficulty}}",
  "hint": "A subtle hint about the topic or nature of the words."
}`,

    difficultySpecific: {
      [DIFFICULTY_LEVELS.EASY]: {
        requirements: "Elementary level, 3 positive/3 negative, everyday concepts",
        example: "positive: happy, bright, good / negative: sad, dark, bad"
      },
      [DIFFICULTY_LEVELS.MEDIUM]: {
        requirements: "Middle school level, 4 positive/4 negative, general topics",
        example: "positive: excellent, wonderful, amazing / negative: terrible, awful, horrible"
      },
      [DIFFICULTY_LEVELS.HARD]: {
        requirements: "High school level, 4-5 positive/4-5 negative, specific themes",
        example: "positive: magnificent, splendid, superb / negative: dreadful, abysmal, atrocious"
      },
      [DIFFICULTY_LEVELS.EXPERT]: {
        requirements: "Advanced level, 5-6 positive/5-6 negative, complex themes",
        example: "positive: exemplary, paramount, sublime / negative: deplorable, heinous, egregious"
      }
    }
  },
  [PUZZLE_TYPES.SYNONYMS]: {
    base: `Generate multiple "Synonyms Puzzle" sets in JSON format to maximize LLM usage efficiency.
  
    Instructions:
    - Generate {{setCount}} different synonym puzzle sets
    - Each set should have unique word groups (no word repetition across all sets)
    - Each group should contain 3-4 words that are synonyms
    - Use words appropriate for the difficulty level
    - Ensure all words in each group truly have similar meanings
    - Distribute words evenly across semantic categories (emotions, actions, descriptions, etc.)
    
    JSON format:
    {
      "puzzleSets": [
        {
          "groups": [
            ["happy", "joyful", "cheerful"],
            ["big", "large", "huge"], 
            ["fast", "quick", "rapid"]
          ],
          "hint": "Group words that have similar meanings"
        },
        {
          "groups": [
            ["smart", "clever", "intelligent"],
            ["small", "tiny", "little"],
            ["cold", "freezing", "chilly"]
          ],
          "hint": "Find words that mean the same thing"
        },
        {
          "groups": [
            ["angry", "mad", "furious"],
            ["beautiful", "pretty", "lovely"],
            ["difficult", "hard", "challenging"]
          ],
          "hint": "Match words with identical meanings"
        }
      ],
      "difficulty": "{{difficulty}}",
      "totalSets": {{setCount}}
    }`,
  
    difficultySpecific: {
      [DIFFICULTY_LEVELS.EASY]: {
        requirements: "Generate 8 sets, each with 3 groups of 3 simple words, elementary vocabulary, no word repetition across all 8 sets",
        example: "Set 1: happy/glad/joyful, big/large/huge | Set 2: fast/quick/speedy, small/tiny/little | Set 3: cold/freezing/chilly"
      },
      [DIFFICULTY_LEVELS.MEDIUM]: {
        requirements: "Generate 6 sets, each with 3-4 groups of 3-4 words, middle school vocabulary, no word repetition across all 6 sets",
        example: "Set 1: beautiful/pretty/lovely/gorgeous | Set 2: angry/mad/furious/irritated | Set 3: difficult/hard/challenging/tough"
      },
      [DIFFICULTY_LEVELS.HARD]: {
        requirements: "Generate 5 sets, each with 4 groups of 3-4 words, high school vocabulary, no word repetition across all 5 sets",
        example: "Set 1: meticulous/careful/thorough/precise | Set 2: abundant/plentiful/copious/ample | Set 3: ancient/old/archaic/antiquated"
      },
      [DIFFICULTY_LEVELS.EXPERT]: {
        requirements: "Generate 4 sets, each with 4-5 groups of 3-4 words, college level vocabulary, no word repetition across all 4 sets",
        example: "Set 1: magnanimous/generous/benevolent/charitable | Set 2: ubiquitous/omnipresent/pervasive/widespread | Set 3: ephemeral/transient/fleeting/temporary"
      }
    }
  },
  [PUZZLE_TYPES.MEMORY_RETENTION]: {
    base: `Generate a "Memory Retention Puzzle" in JSON format.
  
  Instructions:
  - Create an engaging essay about 3 distinct subjects within a coherent topic
  - Write for audio narration with natural speaking pace (150 words per minute)
  - Weave between subjects naturally throughout the essay
  - Create specific, memorable facts that clearly belong to each subject
  - Facts should be distributed evenly across the audio timeline
  - Each fact should be 1-2 sentences and clearly attributable to one subject
  - Use educational topics that naturally have 3 related but distinct components
  
  CRITICAL REQUIREMENTS:
  - Essay must be written for natural speech (use contractions, conversational tone)
  - Facts must be explicitly stated in the essay text
  - Each fact needs a showTiming in milliseconds from essay start
  - Distribute facts evenly across the duration
  - Make facts specific enough to be memorable but not overwhelming
  - Ensure each subject gets equal representation
  
  JSON format:
  {
    "topic": "Ocean Ecosystems",
    "essay": "The world's oceans contain three major ecosystem zones, each with unique characteristics and wildlife. The sunlight zone, extending from the surface to 200 meters deep, is home to most marine life including dolphins, sea turtles, and colorful coral reefs. Dolphins use echolocation to navigate and hunt in these bright waters. Moving deeper, we enter the twilight zone, stretching from 200 to 1000 meters, where bioluminescent creatures create their own light. Giant squid, some reaching 40 feet in length, hunt in these dimly lit waters. The mysterious vampire squid actually feeds on marine snow, not blood as its name suggests. Finally, the midnight zone extends beyond 1000 meters into complete darkness. Here, the anglerfish uses a glowing lure to attract prey in the pitch-black environment. These deep-sea trenches can reach depths of over 36,000 feet. The pressure at these depths is more than 1000 times greater than at sea level, yet life still thrives in these extreme conditions.",
    "subjects": [
      {
        "id": "sunlight_zone",
        "name": "Sunlight Zone (0-200m)",
        "description": "The bright surface waters where most marine life exists"
      },
      {
        "id": "twilight_zone", 
        "name": "Twilight Zone (200-1000m)",
        "description": "The dimly lit middle waters with bioluminescent creatures"
      },
      {
        "id": "midnight_zone",
        "name": "Midnight Zone (1000m+)",
        "description": "The dark deep waters with extreme pressure"
      }
    ],
    "facts": [
      {
        "id": "fact1",
        "text": "Dolphins use echolocation to navigate and hunt",
        "correctSubject": "sunlight_zone",
        "showTiming": 15000
      },
      {
        "id": "fact2", 
        "text": "Giant squid can reach 40 feet in length",
        "correctSubject": "twilight_zone",
        "showTiming": 35000
      },
      {
        "id": "fact3",
        "text": "Deep-sea trenches can reach depths of over 36,000 feet",
        "correctSubject": "midnight_zone", 
        "showTiming": 55000
      }
    ],
    "difficulty": "{{difficulty}}",
    "hint": "Listen for specific facts about each ocean zone and remember which zone they belong to"
  }
  
  TOPIC GUIDELINES:
  - Choose educational topics with natural 3-part divisions
  - Examples: Ocean zones, Historical periods, Scientific fields, Ecosystems, etc.
  - Avoid topics that feel forced or artificial
  - Each subject should be distinct but related to the overall theme
  - Facts should feel natural within the essay flow, not forced
  
  TIMING GUIDELINES:
  - Calculate timing based on natural speech pace (150 words/minute)
  - Distribute facts evenly across the essay duration
  - First fact should appear after 10-15 seconds
  - Last fact should appear at least 10 seconds before essay ends
  - Facts should not cluster together - maintain even spacing`,
  
    difficultySpecific: {
      [DIFFICULTY_LEVELS.EASY]: {
        requirements: "9 total facts (3 per subject), 450-500 words essay (3-3.5 minutes), simple vocabulary, obvious subject distinctions, facts every 20-25 seconds, clear topic divisions",
        example: "Topic: 'Types of Weather' → Subjects: Sunny Weather, Rainy Weather, Snowy Weather | Facts: 'The sun provides vitamin D', 'Rainbows appear after rain storms', 'Snowflakes have six sides'"
      },
      [DIFFICULTY_LEVELS.MEDIUM]: {
        requirements: "12 total facts (4 per subject), 600-700 words essay (4-4.5 minutes), intermediate vocabulary, clear but nuanced distinctions, facts every 18-22 seconds, educational topics",
        example: "Topic: 'Renewable Energy Sources' → Subjects: Solar Power, Wind Energy, Hydroelectric Power | Facts: 'Solar panels convert 15-20% of sunlight to electricity', 'Wind turbines need 7+ mph winds to generate power'"
      },
      [DIFFICULTY_LEVELS.HARD]: {
        requirements: "15 total facts (5 per subject), 750-850 words essay (5-5.5 minutes), advanced vocabulary, subtle distinctions requiring attention, facts every 15-20 seconds, complex topics",
        example: "Topic: 'Branches of Psychology' → Subjects: Cognitive Psychology, Behavioral Psychology, Developmental Psychology | Facts: 'Working memory can hold 7±2 items', 'Classical conditioning was discovered by Pavlov'"
      },
      [DIFFICULTY_LEVELS.EXPERT]: {
        requirements: "18 total facts (6 per subject), 900-1000 words essay (6-6.5 minutes), specialized vocabulary, very subtle distinctions, overlapping concepts, facts every 12-18 seconds, academic topics",
        example: "Topic: 'Quantum Mechanics Principles' → Subjects: Wave-Particle Duality, Uncertainty Principle, Quantum Entanglement | Facts: 'The double-slit experiment demonstrates wave-particle duality', 'Heisenberg showed position and momentum cannot both be precisely measured'"
      }
    }
  },  
  [PUZZLE_TYPES.MEMORY_SEQUENCING]: {
    base: `Generate a "Memory Sequencing Puzzle" in JSON format.
  
  Instructions:
  - Create a two-part historical/chronological narrative about a specific topic
  - Part 1 should be a flowing narrative paragraph covering earlier events
  - Part 2 should be a flowing narrative paragraph covering later events  
  - Extract specific items (institutions, events, people, etc.) from BOTH parts
  - Each item should have a clear date or chronological position
  - Generate exactly {{totalItems}} items total ({{itemsPerPart}} from each part)
  - Items should be specific enough to sequence but general enough to be memorable
  
  Format Requirements:
  - partOneNarrative: A 2-3 sentence flowing paragraph about earlier events
  - partTwoNarrative: A 2-3 sentence flowing paragraph about later events
  - items: Array of specific things mentioned in both narratives with dates/order
  - Each item should be a noun phrase (institution, event, invention, etc.)
  
  JSON format:
  {
    "topic": "Famous Museums Opening Dates",
    "description": "Sequence the opening dates of world-famous museums",
    "partOneTitle": "18th and Early 19th Century Museums",
    "partTwoTitle": "Mid and Late 19th Century Museums", 
    "partOneNarrative": "Some of the world's most amazing museums were opened to the public in the 18th and 19th centuries. The British Museum in London opened in 1759, the Uffizi in Florence opened in 1769, the Louvre in Paris opened in 1793, and the Rijksmuseum in Amsterdam opened in 1815.",
    "partTwoNarrative": "The museum expansion continued throughout the 19th century. The Hermitage in St. Petersburg opened in 1852, the Smithsonian in Washington, D.C. opened in 1855, and the Acropolis Museum in Athens opened in 1876.",
    "partOneAudioScript": "Listen to this passage about early museums. The British Museum opened in 1759, the Uffizi in 1769, the Louvre in 1793, and the Rijksmuseum in 1815.",
    "partTwoAudioScript": "Now listen to this passage about later museums. The Hermitage opened in 1852, the Smithsonian in 1855, and the Acropolis Museum in 1876.",
    "items": [
      {
        "id": "british_museum",
        "name": "The British Museum",
        "year": 1759,
        "partNumber": 1,
        "correctOrder": 1
      },
      {
        "id": "uffizi",
        "name": "The Uffizi",
        "year": 1769,
        "partNumber": 1,
        "correctOrder": 2
      },
      {
        "id": "louvre",
        "name": "The Louvre",
        "year": 1793,
        "partNumber": 1,
        "correctOrder": 3
      },
      {
        "id": "rijksmuseum", 
        "name": "The Rijksmuseum",
        "year": 1815,
        "partNumber": 1,
        "correctOrder": 4
      },
      {
        "id": "hermitage",
        "name": "The Hermitage",
        "year": 1852,
        "partNumber": 2,
        "correctOrder": 5
      },
      {
        "id": "smithsonian",
        "name": "The Smithsonian",
        "year": 1855,
        "partNumber": 2,
        "correctOrder": 6
      },
      {
        "id": "acropolis_museum",
        "name": "The Acropolis Museum", 
        "year": 1876,
        "partNumber": 2,
        "correctOrder": 7
      }
    ],
    "difficulty": "{{difficulty}}",
    "hint": "Listen to both passages and arrange the items in chronological order"
  }
  
  CRITICAL REQUIREMENTS:
  - partOneNarrative and partTwoNarrative must be flowing, natural paragraphs
  - Items must be extracted FROM the narratives (mentioned explicitly)
  - Each item needs: id, name, year/date, partNumber, correctOrder
  - correctOrder should be 1-{{totalItems}} across BOTH parts combined
  - Topics should have clear chronological elements (dates, years, sequences)
  - Use educational topics: inventions, historical events, scientific discoveries, etc.`,
  
    difficultySpecific: {
      [DIFFICULTY_LEVELS.EASY]: {
        requirements: "6 total items (3 per part), simple historical topics with clear dates, obvious chronological order, elementary vocabulary. Topics: basic inventions, simple historical events, everyday discoveries",
        example: "Topic: 'Early Inventions' → Part 1: telephone (1876), light bulb (1879), automobile (1885) | Part 2: airplane (1903), radio (1920), television (1927)"
      },
      [DIFFICULTY_LEVELS.MEDIUM]: {
        requirements: "8 total items (4 per part), moderate historical complexity with specific dates, intermediate vocabulary. Topics: scientific discoveries, cultural institutions, technological advances",
        example: "Topic: 'Space Race Milestones' → Part 1: Sputnik (1957), first human in space (1961), first spacewalk (1965), first lunar orbit (1968) | Part 2: moon landing (1969), space station (1971), Mars rover (1997), ISS (1998)"
      },
      [DIFFICULTY_LEVELS.HARD]: {
        requirements: "10 total items (5 per part), complex historical sequences with nuanced timing, advanced vocabulary. Topics: wars, political movements, scientific revolutions",
        example: "Topic: 'World War II Pacific Theater' → Part 1: Pearl Harbor, Doolittle Raid, Coral Sea, Midway, Guadalcanal | Part 2: Marshall Islands, Philippines, Iwo Jima, Okinawa, atomic bombs"
      },
      [DIFFICULTY_LEVELS.EXPERT]: {
        requirements: "12 total items (6 per part), highly complex sequences requiring specialized knowledge, academic vocabulary. Topics: scientific theories, philosophical movements, detailed historical analysis",
        example: "Topic: 'Development of Modern Physics' → Part 1: Maxwell equations, photoelectric effect, special relativity, atomic model, quantum theory, wave-particle duality | Part 2: uncertainty principle, quantum mechanics, nuclear fission, transistor, laser, quantum computing"
      }
    }
  },
  [PUZZLE_TYPES.MEMORY_STORY]: {
    base: `Generate a memory story puzzle where users need to remember everyday items from a scenario.
  
  Create a story card (1-2 sentences) featuring a character in one of these scenarios:
  - Grocery shopping (fruits, vegetables, dairy, etc.)
  - Packing for travel (clothes, toiletries, electronics, etc.)  
  - Recipe ingredients (flour, eggs, spices, etc.)
  - Choosing outfit (shirts, pants, shoes, accessories, etc.)
  
  Requirements:
  - Include exactly {{itemCount}} specific items in the story
  - Use a random character name (Sarah, John, Emma, etc.)
  - Make the story natural and relatable
  - Items should be clearly mentioned in the story
  - For answer options, include relevant emojis with each item (e.g., "🥕 carrots", "🥚 eggs", "👕 shirt")
  - Provide correct items + 3-4 distractors as options
  - Choose emojis that clearly represent each item
  
  Format as JSON:
  {
    "storyCard": "One or two sentence story mentioning the items",
    "question": "What does [character] need to [action]?", 
    "correctItems": ["🥚 eggs", "🥕 carrots", "🥛 milk"],
    "options": ["🥚 eggs", "🥕 carrots", "🧈 butter", "🥛 milk", "🍯 honey", "🧄 garlic"],
    "hint": "Helpful memory tip",
    "scenario": "grocery|packing|recipe|outfit",
    "character": "Character name used",
    "difficulty": "{{difficulty}}",
    "itemCount": {{itemCount}}
  }`,
  
    difficultySpecific: {
      [DIFFICULTY_LEVELS.EASY]: {
        requirements: "3 items to remember, simple everyday scenarios, clear item mentions, obvious emoji choices",
        example: "Sarah needs 🥚 eggs, 🌾 flour, and 🥛 milk for pancakes"
      },
      [DIFFICULTY_LEVELS.MEDIUM]: {
        requirements: "5 items to remember, moderate scenarios with mixed item types, appropriate emojis for each category",
        example: "John is packing 👕 shirts, 👖 pants, 🦷 toothbrush, 🔌 phone charger, and 🕶️ sunglasses"
      },
      [DIFFICULTY_LEVELS.HARD]: {
        requirements: "7 items to remember, complex scenarios with similar item categories, precise emoji matching",
        example: "Emma needs 🥕 carrots, 🧅 onions, 🍅 tomatoes, 🧀 cheese, 🍝 pasta, 🫒 olive oil, and 🌿 basil for dinner"
      },
      [DIFFICULTY_LEVELS.EXPERT]: {
        requirements: "9 items to remember, challenging scenarios with easily confused items, detailed emoji representation",
        example: "Mike is packing 🧥 jacket, 🧶 sweater, 👢 boots, 🧤 gloves, 🧣 scarf, 👒 hat, 🩲 thermal underwear, 🧦 wool socks, and 🥽 ski goggles"
      }
    }
  }

};

function getSetCountForDifficulty(difficulty, puzzleType) {
  if (puzzleType === PUZZLE_TYPES.ANAGRAM) {
    switch (difficulty) {
      case DIFFICULTY_LEVELS.EASY: return 8;
      case DIFFICULTY_LEVELS.MEDIUM: return 6;
      case DIFFICULTY_LEVELS.HARD: return 5;
      case DIFFICULTY_LEVELS.EXPERT: return 4;
      default: return 6;
    }
  }
  
  if (puzzleType === PUZZLE_TYPES.SYNONYMS) {
    switch (difficulty) {
      case DIFFICULTY_LEVELS.EASY: return 8;      // Was 4, now 8 (2x improvement)
      case DIFFICULTY_LEVELS.MEDIUM: return 6;    // Was 3, now 6 (2x improvement)
      case DIFFICULTY_LEVELS.HARD: return 5;      // Was 3, now 5 (1.7x improvement)
      case DIFFICULTY_LEVELS.EXPERT: return 4;    // Was 2, now 4 (2x improvement)
      default: return 6;
    }
  }

  if (puzzleType === PUZZLE_TYPES.SENTENCE_TRANSITIONS) {
    switch (difficulty) {
      case DIFFICULTY_LEVELS.EASY: return 6;      // New: 6x improvement
      case DIFFICULTY_LEVELS.MEDIUM: return 5;    // New: 5x improvement
      case DIFFICULTY_LEVELS.HARD: return 4;      // New: 4x improvement
      case DIFFICULTY_LEVELS.EXPERT: return 4;    // New: 4x improvement
      default: return 5;
    }
  }
  
  // Existing logic for antonyms
  switch (difficulty) {
    case DIFFICULTY_LEVELS.EASY: return 5;
    case DIFFICULTY_LEVELS.MEDIUM: return 4;
    case DIFFICULTY_LEVELS.HARD: return 3;
    case DIFFICULTY_LEVELS.EXPERT: return 3;
    default: return 3;
  }
}

// Function to generate difficulty-specific prompts
export function generatePromptForPuzzle(puzzleType, difficulty) {
  console.log(`Looking for puzzleType: "${puzzleType}"`);
  console.log(`Available keys:`, Object.keys(PUZZLE_PROMPTS));
  const promptConfig = PUZZLE_PROMPTS[puzzleType];
  
  if (!promptConfig) {
    console.error(`No prompt configuration found for puzzle type: ${puzzleType}`);
    console.error(`Did you mean one of these?`, Object.keys(PUZZLE_PROMPTS).filter(key => 
      key.toLowerCase().includes('connotation')
    ));
    throw new Error(`No prompt configuration found for puzzle type: ${puzzleType}`);
  }

  let prompt = promptConfig.base;
  
  // Replace difficulty placeholder
  prompt = prompt.replace(/\{\{difficulty\}\}/g, difficulty);
  
  // Add set count for antonyms
  if (puzzleType === PUZZLE_TYPES.ANTONYMS || puzzleType === PUZZLE_TYPES.SYNONYMS || puzzleType === PUZZLE_TYPES.ANAGRAM) {
    const setCount = getSetCountForDifficulty(difficulty);
    prompt = prompt.replace(/\{\{setCount\}\}/g, setCount);
  }
  
  // Add difficulty-specific requirements if available
  const difficultyConfig = promptConfig.difficultySpecific?.[difficulty];
  if (difficultyConfig) {
    prompt += `\n\n${difficulty} Requirements:\n- ${difficultyConfig.requirements}\n- Example: ${difficultyConfig.example}`;
  }

  if (puzzleType === PUZZLE_TYPES.MEMORY_SEQUENCING) {
    const itemCounts = {
      [DIFFICULTY_LEVELS.EASY]: { total: 6, perPart: 3 },
      [DIFFICULTY_LEVELS.MEDIUM]: { total: 8, perPart: 4 },
      [DIFFICULTY_LEVELS.HARD]: { total: 10, perPart: 5 },
      [DIFFICULTY_LEVELS.EXPERT]: { total: 12, perPart: 6 }
    };
    
    const counts = itemCounts[difficulty] || { total: 8, perPart: 4 };
    prompt = prompt.replace(/\{\{totalItems\}\}/g, counts.total);
    prompt = prompt.replace(/\{\{itemsPerPart\}\}/g, counts.perPart);
  }
  
  return prompt;
}