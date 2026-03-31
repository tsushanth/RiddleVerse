// musicPuzzleGenerator.js - Clean Deezer + iTunes Implementation (Minimal Changes)

import axios from 'axios';
import { callAI, callAIWithRetry } from '../utils/aiClient.js';
import { USAGE_CATEGORIES } from '../utils/usageTracker.js';
import { supabase } from "../config/database.js";

export class MusicPuzzleGenerator {
    constructor() {
        this.debugMode = false;
        this.supabaseUrl = 'https://uujjodxicvifmiwlimob.supabase.co';
        this.apis = {
            deezer: {
                baseUrl: 'https://api.deezer.com',
                active: true,
                rateLimitPerMinute: 50
            },
            itunes: {
                baseUrl: 'https://itunes.apple.com',
                active: true,
                rateLimitPerMinute: 100
            }
        };
        
        this.recentlyUsedSongs = new Map();
        this.maxRecentSongs = 100;
        this.qualityThresholds = {
            minimumPopularityScore: 50000, // Fixed: Higher numbers = more popular in Deezer
            minimumTrackDuration: 60000,
            maximumTrackDuration: 600000,
            requirePreview: true,
            requireArtwork: false
        };
        
        this.initializeGenreMapping();
    }

    setDebugMode(enabled) {
        this.debugMode = enabled;
    }

    debugLog(message, type = 'info') {
        if (this.debugMode) {
            const timestamp = new Date().toISOString();
            const emoji = type === 'error' ? '❌' : type === 'success' ? '✅' : type === 'warning' ? '⚠️' : '🔍';
            console.log(`${emoji} [${timestamp}] MUSIC_PUZZLE: ${message}`);
        }
    }

    initializeGenreMapping() {
        this.genreMapping = {
            'bollywood': ['A.R. Rahman', 'Lata Mangeshkar', 'Kishore Kumar', 'Asha Bhosle', 'Mohammed Rafi', 'Shreya Ghoshal', 'Arijit Singh'],
            'k-pop': ['BTS', 'BLACKPINK', 'EXO', 'TWICE', 'Red Velvet', 'ITZY', 'aespa', 'NewJeans', 'Stray Kids'],
            'j-pop': ['Hikaru Utada', 'Ayumi Hamasaki', 'Arashi', 'AKB48', 'Perfume', 'One OK Rock'],
            'latin': ['Bad Bunny', 'Shakira', 'J Balvin', 'Daddy Yankee', 'Ozuna', 'Maluma'],
            'afrobeats': ['Wizkid', 'Burna Boy', 'Davido', 'Tiwa Savage', 'Yemi Alade'],
            'arabic': ['Fairuz', 'Umm Kulthum', 'Mohamed Abdel Wahab', 'Amr Diab', 'Nancy Ajram', 'Elissa'],
            'french chanson': ['Édith Piaf', 'Charles Aznavour', 'Jacques Brel', 'Serge Gainsbourg'],
            'rock': ['Led Zeppelin', 'The Beatles', 'Queen', 'Pink Floyd', 'The Rolling Stones', 'AC/DC'],
            'pop': ['Michael Jackson', 'Madonna', 'Taylor Swift', 'Ariana Grande', 'Ed Sheeran', 'Billie Eilish'],
            'jazz': ['Miles Davis', 'John Coltrane', 'Duke Ellington', 'Ella Fitzgerald', 'Louis Armstrong']
        };
    }

    // MAIN METHOD - UNCHANGED
    async generateMusicPuzzle(difficulty = 'medium', customQuery = null) {
        this.debugLog(`Starting music puzzle generation with audio upload (${difficulty})`);
        
        try {
            const puzzleTimestamp = Date.now();
            const puzzleRandomId = Math.random().toString(36).substr(2, 6);
            const puzzleId = `music_puzzle_${puzzleTimestamp}_${puzzleRandomId}`;
            this.currentPuzzleId = puzzleId;
            
            const searchQuery = customQuery || this.generateRandomMusicTheme(difficulty);
            this.debugLog(`Using search query: "${searchQuery}" with puzzleId: ${puzzleId}`);
            
            const searchQueries = await this.expandUserQuery(searchQuery);
            const searchResults = await this.executeSearchQueries(searchQueries, this.getSongCountForDifficulty(difficulty) * 5);
            
            if (searchResults.length === 0) {
                throw new Error('No search results found from Deezer or iTunes API');
            }
            
            const songsWithPreviews = searchResults.filter(song => song.previewUrl && song.previewUrl.length > 0);
            this.debugLog(`Filtered to ${songsWithPreviews.length} songs with previews from ${searchResults.length} total`);
            
            if (songsWithPreviews.length < 3) {
                throw new Error(`Only ${songsWithPreviews.length} songs with previews found, need at least 3`);
            }
            
            const scoredResults = await this.scoreAndRankSongs(songsWithPreviews);
            const selection = await this.selectBestResults(searchQuery, scoredResults, this.getSongCountForDifficulty(difficulty));
            
            if (selection.songs.length < 3) {
                throw new Error(`Only ${selection.songs.length} quality songs with previews found, need at least 3`);
            }
            
            this.debugLog(`Downloading and uploading audio for ${selection.songs.length} songs with puzzleId: ${puzzleId}...`);
            const songsWithUploadedAudio = await this.enhanceSongsWithCrossApiData(selection.songs, puzzleId);
            
            if (songsWithUploadedAudio.length < 3) {
                throw new Error(`Only ${songsWithUploadedAudio.length} songs successfully uploaded, need at least 3`);
            }
            
            const puzzleData = await this.buildCompletePuzzle(songsWithUploadedAudio, puzzleId, difficulty, searchQuery);
            
            this.debugLog(`Music puzzle generation completed: ${songsWithUploadedAudio.length} songs with uploaded audio`);
            
            return {
                success: true,
                data: puzzleData,
                message: `Generated music puzzle with ${songsWithUploadedAudio.length} songs (audio uploaded to Supabase)`,
                audioUploadStats: {
                    songsProcessed: selection.songs.length,
                    songsUploaded: songsWithUploadedAudio.filter(s => s.audioUploaded).length,
                    uploadSuccessRate: Math.round((songsWithUploadedAudio.filter(s => s.audioUploaded).length / selection.songs.length) * 100)
                }
            };
            
        } catch (error) {
            this.debugLog(`Music puzzle generation failed: ${error.message}`, 'error');
            return {
                success: false,
                error: error.message,
                message: `Music puzzle generation failed: ${error.message}`
            };
        }
    }

    // EXISTING DEEZER SEARCH - UNCHANGED
    async searchDeezer(query, maxResults = 10) {
        try {
            this.debugLog(`Searching Deezer for: "${query}"`);
            
            const response = await axios.get(`${this.apis.deezer.baseUrl}/search`, {
                params: {
                    q: query,
                    limit: maxResults * 2,
                    order: 'RANKING'
                },
                timeout: 10000,
                headers: {
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
                    'Accept': 'application/json'
                }
            });
            
            const tracks = response.data.data || [];
            this.debugLog(`Deezer returned ${tracks.length} tracks for "${query}"`);
            
            const validTracks = [];
            const seen = new Set();
            
            for (const track of tracks) {
                const key = `${track.title}_${track.artist?.name}`;
                if (seen.has(key)) continue;
                seen.add(key);
                
                // Fixed validation logic
                const hasValidPreview = track.preview && track.preview.length > 0 && track.preview.startsWith('https://');
                const hasValidMetadata = track.title && track.artist?.name && track.artist.name.length > 0;
                const hasReasonablePopularity = !track.rank || track.rank >= 50000; // Higher rank = more popular
                const hasValidDuration = !track.duration || track.duration >= 30; // At least 30 seconds
                
                if (hasValidPreview && hasValidMetadata && hasReasonablePopularity && hasValidDuration) {
                    validTracks.push({
                        id: `deezer_${track.id}`,
                        title: track.title,
                        artist: track.artist.name,
                        album: track.album?.title || 'Unknown Album',
                        previewUrl: track.preview,
                        duration: track.duration ? track.duration * 1000 : 180000,
                        popularity: track.rank || 50000,
                        explicit: track.explicit_lyrics || false,
                        source: 'deezer',
                        previewValidated: true,
                        albumImageUrl: track.album?.cover_medium || track.album?.cover_small
                    });
                    
                    if (validTracks.length >= maxResults) break;
                }
            }
            
            validTracks.sort((a, b) => (b.popularity || 0) - (a.popularity || 0));
            this.debugLog(`Found ${validTracks.length} valid tracks from ${tracks.length} total`);
            
            return validTracks.slice(0, maxResults);
            
        } catch (error) {
            this.debugLog(`Deezer search failed: ${error.message}`, 'error');
            return [];
        }
    }

    // NEW: ITUNES API SEARCH - ADDED ONLY
    async searchiTunes(query, maxResults = 10) {
        try {
            this.debugLog(`Searching iTunes for: "${query}"`);
            
            const response = await axios.get(`${this.apis.itunes.baseUrl}/search`, {
                params: {
                    term: query,
                    media: 'music',
                    entity: 'song',
                    limit: maxResults * 2
                },
                timeout: 10000,
                headers: {
                    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
                    'Accept': 'application/json'
                }
            });
            
            const tracks = response.data.results || [];
            this.debugLog(`iTunes returned ${tracks.length} tracks for "${query}"`);
            
            const validTracks = [];
            const seen = new Set();
            
            for (const track of tracks) {
                const key = `${track.trackName}_${track.artistName}`;
                if (seen.has(key)) continue;
                seen.add(key);
                
                const hasValidPreview = track.previewUrl && track.previewUrl.length > 0 && track.previewUrl.startsWith('https://');
                const hasValidMetadata = track.trackName && track.artistName && track.artistName.length > 0;
                const hasValidDuration = !track.trackTimeMillis || track.trackTimeMillis >= 60000; // At least 60 seconds
                
                if (hasValidPreview && hasValidMetadata && hasValidDuration) {
                    validTracks.push({
                        id: `itunes_${track.trackId}`,
                        title: track.trackName,
                        artist: track.artistName,
                        album: track.collectionName || 'Unknown Album',
                        previewUrl: track.previewUrl,
                        duration: track.trackTimeMillis || 180000,
                        popularity: 70000, // iTunes doesn't provide popularity, use default
                        explicit: track.trackExplicitness === 'explicit',
                        source: 'itunes',
                        previewValidated: true,
                        albumImageUrl: track.artworkUrl100 || track.artworkUrl60,
                        genre: track.primaryGenreName,
                        releaseDate: track.releaseDate
                    });
                    
                    if (validTracks.length >= maxResults) break;
                }
            }
            
            this.debugLog(`Found ${validTracks.length} valid tracks from ${tracks.length} total`);
            
            return validTracks.slice(0, maxResults);
            
        } catch (error) {
            this.debugLog(`iTunes search failed: ${error.message}`, 'error');
            return [];
        }
    }

    // MODIFIED: Search both APIs now
    async executeSearchQueries(searchQueries, maxResults = 15) {
        this.debugLog(`Executing ${searchQueries.length} search queries`);
        
        const allResults = [];
        
        for (const searchQuery of searchQueries) {
            try {
                this.debugLog(`Searching for: "${searchQuery.query}" (${searchQuery.type})`);
                
                // Search both APIs in parallel
                const [deezerResults, itunesResults] = await Promise.all([
                    this.searchDeezer(searchQuery.query, Math.ceil(maxResults / searchQueries.length / 2)),
                    this.searchiTunes(searchQuery.query, Math.ceil(maxResults / searchQueries.length / 2))
                ]);
                
                [...deezerResults, ...itunesResults].forEach(result => {
                    result.searchContext = searchQuery;
                    result.relevanceBoost = this.calculateRelevanceBoost(searchQuery.type);
                });
                
                allResults.push(...deezerResults, ...itunesResults);
                await new Promise(resolve => setTimeout(resolve, 300));
                
            } catch (error) {
                this.debugLog(`Search failed for "${searchQuery.query}": ${error.message}`, 'warning');
                continue;
            }
        }
        
        const deduped = this.deduplicateSongsByKey(allResults);
        this.debugLog(`Found ${deduped.length} unique songs from all searches`);
        
        return deduped;
    }

    // EXISTING ENHANCEMENT METHOD - UNCHANGED (just handles iTunes songs that might need Deezer fallback)
    async enhanceSongsWithCrossApiData(songs, puzzleId) {
        this.debugLog(`Enhancing ${songs.length} songs with cross-API data and audio upload using puzzleId: ${puzzleId}...`);
        
        const enhanced = [];
        
        for (let i = 0; i < songs.length; i++) {
            const song = songs[i];
            try {
                let enhancedSong = { ...song };
                
                // Step 1: For iTunes songs without previews, try to find Deezer match
                if (!enhancedSong.previewUrl && enhancedSong.source === 'itunes') {
                    this.debugLog(`Finding Deezer match for iTunes song: ${enhancedSong.title} by ${enhancedSong.artist}`);
                    const deezerMatch = await this.findDeezerMatch(enhancedSong.title, enhancedSong.artist);
                    if (deezerMatch && deezerMatch.previewUrl) {
                        enhancedSong.previewUrl = deezerMatch.previewUrl;
                        enhancedSong.crossApiEnhanced = true;
                        enhancedSong.deezerMatch = deezerMatch;
                        this.debugLog(`Found Deezer preview for ${enhancedSong.title}`);
                    } else {
                        this.debugLog(`No Deezer preview found for ${enhancedSong.title}, skipping...`, 'warning');
                        continue;
                    }
                }
                
                if (enhancedSong.previewUrl) {
                    this.debugLog(`Downloading and uploading audio for: ${enhancedSong.title} (track ${i + 1})`);
                    
                    // Step 2: Download audio preview using your working method
                    const audioBuffer = await this.downloadAudioPreview(enhancedSong.previewUrl);
                    
                    // Step 3: Upload to Supabase using your working method
                    const audioUrl = await this.uploadAudio(puzzleId, i + 1, audioBuffer);
                    
                    // Update song with uploaded audio URL
                    enhancedSong.previewUrl = audioUrl;
                    enhancedSong.originalPreviewUrl = song.previewUrl || enhancedSong.deezerMatch?.previewUrl;
                    enhancedSong.audioUploaded = true;
                    enhancedSong.audioFileName = `music-puzzles/${puzzleId}/track_${i + 1}.mp3`;
                    
                    this.debugLog(`Audio uploaded successfully: ${enhancedSong.audioFileName}`);
                } else {
                    this.debugLog(`No preview URL available for ${enhancedSong.title}, skipping...`, 'warning');
                    continue;
                }
                
                enhancedSong.title = enhancedSong.title || 'Unknown Title';
                enhancedSong.artist = enhancedSong.artist || 'Unknown Artist';
                enhancedSong.album = enhancedSong.album || 'Unknown Album';
                enhancedSong.duration = enhancedSong.duration || 180000;
                enhancedSong.popularity = enhancedSong.popularity || 50000;
                
                enhanced.push(enhancedSong);
                
                // Small delay to be respectful to APIs
                await new Promise(resolve => setTimeout(resolve, 500));
                
            } catch (error) {
                this.debugLog(`Failed to enhance song ${song.title}: ${error.message}`, 'warning');
                continue;
            }
        }
        
        this.debugLog(`Enhanced ${enhanced.length} songs with uploaded audio using puzzleId: ${puzzleId}`);
        return enhanced;
    }

    // NEW: Helper to find Deezer match for iTunes songs
    async findDeezerMatch(title, artist) {
        try {
            const searchQuery = `${artist} ${title}`;
            const deezerResults = await this.searchDeezer(searchQuery, 3);
            
            // Find the best match based on title and artist similarity
            for (const result of deezerResults) {
                const titleMatch = this.normalizeString(result.title) === this.normalizeString(title);
                const artistMatch = this.normalizeString(result.artist) === this.normalizeString(artist);
                
                if (titleMatch && artistMatch && result.previewUrl) {
                    return result;
                }
            }
            
            // Fallback to first result with preview if exact match not found
            const firstWithPreview = deezerResults.find(r => r.previewUrl);
            return firstWithPreview || null;
            
        } catch (error) {
            this.debugLog(`Failed to find Deezer match: ${error.message}`, 'warning');
            return null;
        }
    }

    // EXISTING DOWNLOAD METHOD - UNCHANGED
    async downloadAudioPreview(previewUrl) {
        try {
            this.debugLog(`⬇️ Downloading audio preview...`);
            const response = await axios.get(previewUrl, {
                responseType: 'arraybuffer',
                timeout: 30000
            });
            const audioBuffer = Buffer.from(response.data);
            this.debugLog(`📦 Downloaded audio: ${audioBuffer.length} bytes`);
            return audioBuffer;
        } catch (error) {
            this.debugLog(`❌ Audio download failed: ${error.message}`, 'error');
            throw new Error(`Audio download error: ${error.message}`);
        }
    }

    // EXISTING UPLOAD METHOD - UNCHANGED
    async uploadAudio(puzzleId, trackIndex, audioBuffer) {
        try {
            this.debugLog('☁️ Importing Supabase client...');
            const { supabase } = await import('../config/database.js');
            
            // Create organized path for music puzzles
            const fileName = `music-puzzles/${puzzleId}/track_${trackIndex}.mp3`;
            this.debugLog(`📁 Uploading to: ${fileName}`);
            
            // Upload the audio buffer to Supabase storage
            const { data, error } = await supabase.storage
                .from('puzzle-audio')
                .upload(fileName, audioBuffer, { 
                    contentType: 'audio/mpeg',
                    upsert: true
                });
                
            if (error) {
                this.debugLog(`❌ Supabase upload error: ${error.message}`, 'error');
                throw new Error(`Supabase upload failed: ${error.message}`);
            }
            
            this.debugLog(`✅ Upload successful: ${data.path}`);
            
            // Get the public URL for the uploaded audio
            const { data: { publicUrl } } = supabase.storage
                .from('puzzle-audio')
                .getPublicUrl(fileName);
                
            if (!publicUrl) {
                throw new Error('Failed to get public URL from Supabase');
            }
            
            this.debugLog(`🔗 Public URL generated: ${publicUrl}`);
            return publicUrl;
            
        } catch (error) {
            this.debugLog(`❌ Audio upload failed: ${error.message}`, 'error');
            throw new Error(`Audio upload error: ${error.message}`);
        }
    }

    // EXISTING BUILD PUZZLE METHOD - UNCHANGED
    async buildCompletePuzzle(songs, puzzleId, difficulty, searchQuery) {
        this.debugLog(`Building complete music puzzle with ${songs.length} uploaded audio files...`);
        
        const songsWithAudio = songs.filter(song => song.audioUploaded && song.previewUrl);
        if (songsWithAudio.length < 3) {
            throw new Error(`Only ${songsWithAudio.length} songs have uploaded audio, need at least 3`);
        }
        
        const songsWithOptions = await Promise.all(songsWithAudio.map(async song => {
            const options = this.generateMusicOptions(song, songsWithAudio, difficulty);
            return {
                ...song,
                options: options,
                audioMetadata: {
                    fileName: song.audioFileName,
                    uploadedAt: new Date().toISOString(),
                    originalSource: song.source,
                    fileSize: song.fileSize || null
                }
            };
        }));
        
        const difficultySettings = {
            easy: { timeLimit: 240000, songsCount: 3 },
            medium: { timeLimit: 180000, songsCount: 6 },
            hard: { timeLimit: 120000, songsCount: 9 }
        };
        
        const settings = difficultySettings[difficulty] || difficultySettings.medium;
        const finalSongs = songsWithOptions.slice(0, Math.min(settings.songsCount, songsWithOptions.length));
        
        return {
            puzzleId: puzzleId,
            title: `Music Quiz: ${this.formatSearchTheme(searchQuery)}`,
            description: `Identify ${finalSongs.length} songs from their audio previews`,
            songs: finalSongs,
            totalSongs: finalSongs.length,
            timeLimit: settings.timeLimit,
            difficulty: difficulty,
            theme: this.formatSearchTheme(searchQuery),
            searchQuery: searchQuery,
            generatedAt: new Date().toISOString(),
            audioSource: 'supabase_uploaded',
            apiStats: {
                totalSongsFound: songs.length,
                songsWithValidPreviews: songs.filter(s => s.previewValidated).length,
                songsWithUploadedAudio: finalSongs.filter(s => s.audioUploaded).length,
                sources: [...new Set(songs.map(s => s.source || 'deezer'))],
                uploadSuccessRate: Math.round((finalSongs.filter(s => s.audioUploaded).length / songs.length) * 100)
            }
        };
    }

    // All remaining methods are UNCHANGED from your original code
    generateMusicOptions(targetSong, allSongs, difficulty) {
        const optionCount = difficulty === 'easy' ? 3 : difficulty === 'medium' ? 4 : 5;
        const correctAnswer = `${targetSong.title} - ${targetSong.artist}`;
        const options = [correctAnswer];
        
        const otherSongs = allSongs.filter(song => 
            song.id !== targetSong.id && song.title !== targetSong.title
        );
        
        const shuffledOthers = this.shuffleArray([...otherSongs]);
        for (let i = 0; i < Math.min(optionCount - 1, shuffledOthers.length); i++) {
            const distractor = `${shuffledOthers[i].title} - ${shuffledOthers[i].artist}`;
            if (!options.includes(distractor)) {
                options.push(distractor);
            }
        }
        
        while (options.length < optionCount) {
            const distractor = this.generateMusicDistractor(targetSong.title, targetSong.artist, options);
            if (distractor && !options.includes(distractor)) {
                options.push(distractor);
            } else {
                break;
            }
        }
        
        return this.shuffleArray(options);
    }

    generateMusicDistractor(originalTitle, originalArtist, existingOptions) {
        const variations = [
            `${originalTitle} (Remix) - ${originalArtist}`,
            `${originalTitle} - Radio Edit - ${originalArtist}`,
            `${originalTitle} (Acoustic) - ${originalArtist}`,
            `${originalTitle} 2.0 - ${originalArtist}`,
            `${originalTitle} (Live) - ${originalArtist}`
        ];
        
        const available = variations.filter(v => !existingOptions.includes(v));
        return available.length > 0 ? available[Math.floor(Math.random() * available.length)] : null;
    }

    formatForPuzzleSystem(puzzleData) {
        this.debugLog('Formatting music puzzle for storage...');
        
        if (!puzzleData.songs || !Array.isArray(puzzleData.songs)) {
            throw new Error('Missing or invalid songs array in puzzle data');
        }
        
        if (!puzzleData.puzzleId || !puzzleData.title) {
            throw new Error('Missing puzzleId or title in puzzle data');
        }
        
        const questions = puzzleData.songs.map((song, index) => ({
            id: index + 1,
            audioUrl: song.previewUrl,
            question: "What song is this?",
            answer: `${song.title} - ${song.artist}`,
            options: song.options || this.generateMusicOptions(song, puzzleData.songs, puzzleData.difficulty || 'medium'),
            type: "song_identification",
            difficulty: puzzleData.difficulty || 'medium',
            hint: `This song was performed by ${song.artist}`,
            metadata: {
                sourceId: song.id.replace(/^(deezer_|itunes_)/, ''),
                source: song.source,
                rank: song.popularity || 0,
                explicit: song.explicit || false,
                releaseDate: song.releaseDate || "Unknown",
                duration: Math.floor((song.duration || 180000) / 1000),
                albumImageUrl: song.albumImageUrl || null,
                requestedArtist: song.artist,
                requestedTitle: song.title,
                discovered: song.discovered || false,
                artistFocused: false,
                searchBased: false,
                modernHit: false
            }
        }));
        
        const questionData = {
            questions: questions,
            correctAnswers: questions.map(q => q.answer),
            totalTracks: questions.length,
            maxScore: questions.length * 10,
            passingScore: Math.ceil(questions.length * 0.6) * 10
        };
        
        const answerData = {
            correctAnswers: questions.map(q => q.answer),
            totalTracks: questions.length,
            maxScore: questions.length * 10,
            passingScore: Math.ceil(questions.length * 0.6) * 10
        };
        
        return {
            question: JSON.stringify(questionData),
            answer: JSON.stringify(answerData),
            hint: `Identify ${questions.length} songs from ${puzzleData.theme || 'various artists'}`,
            difficulty: puzzleData.difficulty || 'medium',
            options: [],
            metadata: {
                trackCount: questions.length,
                theme: puzzleData.theme,
                timeLimit: puzzleData.timeLimit || 180000,
                generatedAt: new Date().toISOString(),
                puzzleType: 'music_identification_interactive',
                hasMultipleChoice: true,
                requiresAudioGeneration: false,
                storagePattern: 'question_contains_all_data',
                apiSources: [...new Set(puzzleData.songs.map(s => s.source || 'deezer'))],
                previewsAvailable: questions.length,
                searchQuery: puzzleData.searchQuery
            }
        };
    }

    // ALL REMAINING METHODS UNCHANGED
    generateRandomMusicTheme(difficulty) {
        const themes = {
            easy: ['pop hits', 'rock classics', 'Taylor Swift', 'Ed Sheeran'],
            medium: ['bollywood', 'jazz standards', 'Beatles', '80s hits'],
            hard: ['french chanson', 'k-pop', 'arabic music', 'jazz fusion']
        };
        
        const availableThemes = themes[difficulty] || themes.medium;
        const randomTheme = availableThemes[Math.floor(Math.random() * availableThemes.length)];
        
        this.debugLog(`Generated random theme: "${randomTheme}" for ${difficulty} difficulty`);
        return randomTheme;
    }

    getSongCountForDifficulty(difficulty) {
        const counts = { easy: 4, medium: 6, hard: 9 };
        return counts[difficulty] || 6;
    }

    async expandUserQuery(userQuery) {
        this.debugLog(`Expanding user query: "${userQuery}"`);
        
        const prompt = `
You are a music search expert. Given a user's music query, generate 3-5 specific search queries that will help find relevant songs from the Deezer and iTunes APIs.

User Query: "${userQuery}"

Consider:
- If it's a genre (like "bollywood", "k-pop", "jazz"), suggest specific popular artists from that genre
- If it's an artist name, suggest variations and popular songs
- If it's a mood/theme, suggest artists and songs that match
- If it's a decade/era, suggest representative artists and hits
- Include both artist-specific and genre-specific searches

Return a JSON array of search queries in this format:
{
  "queries": [
    {
      "query": "search term for API",
      "type": "artist|genre|song|mood",
      "description": "brief explanation of why this search is relevant"
    }
  ]
}

Generate 3-5 diverse search queries that will maximize finding quality, relevant music from Deezer and iTunes APIs.
`;

        try {
            const response = await callAIWithRetry(prompt, 'gpt-3.5-turbo', 2, {
                category: USAGE_CATEGORIES.PUZZLE_GENERATION,
                puzzleType: 'music_query_expansion'
            });

            const parsed = JSON.parse(response);
            if (parsed.queries && Array.isArray(parsed.queries)) {
                this.debugLog(`Generated ${parsed.queries.length} search queries`);
                return parsed.queries;
            }
            
            throw new Error('Invalid query expansion response format');
            
        } catch (error) {
            this.debugLog(`Query expansion failed: ${error.message}`, 'error');
            return this.fallbackQueryExpansion(userQuery);
        }
    }

    fallbackQueryExpansion(userQuery) {
        const queries = [];
        const lowerQuery = userQuery.toLowerCase().trim();
        
        const recognizedGenre = this.identifyGenreFromQuery(lowerQuery);
        if (recognizedGenre && this.genreMapping[recognizedGenre]) {
            const artists = this.genreMapping[recognizedGenre].slice(0, 4);
            artists.forEach(artist => {
                queries.push({
                    query: artist,
                    type: 'artist',
                    description: `Popular ${recognizedGenre} artist`
                });
            });
        } else {
            queries.push({
                query: userQuery,
                type: 'general',
                description: 'Direct search'
            });
            
            const simplified = userQuery.replace(/[^\w\s]/g, '').trim();
            if (simplified !== userQuery) {
                queries.push({
                    query: simplified,
                    type: 'general',
                    description: 'Simplified search'
                });
            }
            
            if (this.looksLikeArtistQuery(userQuery)) {
                const artistName = this.extractArtistName(userQuery);
                queries.push({
                    query: `${artistName} hits`,
                    type: 'artist',
                    description: 'Artist popular songs'
                });
            }
        }
        
        this.debugLog(`Fallback: Generated ${queries.length} search queries`);
        return queries;
    }

    calculateRelevanceBoost(queryType) {
        const boosts = {
            'artist': 1.2,
            'song': 1.3,
            'genre': 1.0,
            'mood': 0.9,
            'general': 0.8
        };
        return boosts[queryType] || 1.0;
    }

    async selectBestResults(userQuery, searchResults, maxResults = 9) {
        this.debugLog(`Selecting best results from ${searchResults.length} candidates`);
        
        const resultsSummary = searchResults.slice(0, 25).map((song, index) => ({
            index: index,
            title: song.title,
            artist: song.artist,
            album: song.album || 'Unknown',
            popularity: song.popularity || 0,
            source: song.source,
            hasPreview: !!song.previewUrl,
            searchContext: song.searchContext?.description || 'unknown',
            relevanceBoost: song.relevanceBoost || 1.0
        }));
        
        const prompt = `
You are a music curator selecting the best songs for a music identification puzzle from Deezer and iTunes API results.

Original User Query: "${userQuery}"

Available Songs (${resultsSummary.length} candidates):
${JSON.stringify(resultsSummary, null, 2)}

Select the ${maxResults} BEST songs that:
1. Are most relevant to the user's query "${userQuery}"
2. Have audio previews (hasPreview: true is required)
3. Are well-known/popular songs (higher popularity scores preferred)
4. Provide good variety (mix of artists, avoid too many from same artist)
5. Are appropriate for a music quiz

Return a JSON object with selected song indices:
{
  "selectedIndices": [0, 3, 7, 12, ...],
  "reasoning": "Brief explanation of selection criteria used"
}

Select exactly ${maxResults} songs by their index numbers from the list above.
`;

        try {
            const response = await callAIWithRetry(prompt, 'gpt-3.5-turbo', 2, {
                category: USAGE_CATEGORIES.PUZZLE_GENERATION,
                puzzleType: 'music_result_selection'
            });

            const parsed = JSON.parse(response);
            
            if (parsed.selectedIndices && Array.isArray(parsed.selectedIndices)) {
                const selectedSongs = parsed.selectedIndices
                    .filter(index => index >= 0 && index < searchResults.length)
                    .map(index => searchResults[index]);
                
                this.debugLog(`LLM selected ${selectedSongs.length} songs: ${parsed.reasoning}`);
                return {
                    songs: selectedSongs,
                    reasoning: parsed.reasoning
                };
            }
            
            throw new Error('Invalid result selection response format');
            
        } catch (error) {
            this.debugLog(`Result selection failed: ${error.message}`, 'error');
            return this.fallbackResultSelection(searchResults, maxResults);
        }
    }

    fallbackResultSelection(searchResults, maxResults) {
        this.debugLog(`Using fallback result selection for ${maxResults} songs`);
        
        const scoredResults = searchResults.map(song => {
            let score = 0;
            
            if (song.previewUrl) score += 30;
            if (song.popularity > 0) score += Math.min(25, song.popularity / 10000); // Adjusted for Deezer's scale
            score *= (song.relevanceBoost || 1.0);
            if (song.duration >= 120000 && song.duration <= 360000) score += 10;
            if (song.album) score += 3;
            if (song.explicit) score -= 5;
            
            return { ...song, selectionScore: score };
        });
        
        scoredResults.sort((a, b) => b.selectionScore - a.selectionScore);
        
        const selectedSongs = [];
        const artistCount = new Map();
        
        for (const song of scoredResults) {
            if (selectedSongs.length >= maxResults) break;
            
            const artistSongCount = artistCount.get(song.artist) || 0;
            if (artistSongCount < 2) {
                selectedSongs.push(song);
                artistCount.set(song.artist, artistSongCount + 1);
            }
        }
        
        this.debugLog(`Fallback selected ${selectedSongs.length} songs`);
        
        return {
            songs: selectedSongs,
            reasoning: 'Algorithmic selection based on preview availability, popularity, and relevance'
        };
    }

    async scoreAndRankSongs(songs) {
        return songs.map(song => {
            let qualityScore = 0;
            
            if (song.previewUrl) qualityScore += 30;
            if (song.popularity > 0) {
                qualityScore += Math.min(25, song.popularity / 10000); // Adjusted for Deezer's scale
            }
            if (song.duration >= 120000 && song.duration <= 360000) {
                qualityScore += 10;
            }
            if (song.album) qualityScore += 5;
            if (song.explicit) qualityScore -= 10;
            
            qualityScore *= (song.relevanceBoost || 1.0);
            
            song.qualityScore = Math.max(0, qualityScore);
            return song;
        }).sort((a, b) => b.qualityScore - a.qualityScore);
    }

    // Helper methods
    identifyGenreFromQuery(query) {
        if (this.genreMapping[query]) {
            return query;
        }
        
        const genreSynonyms = {
            'bollywood music': 'bollywood',
            'bollywood songs': 'bollywood',
            'hindi songs': 'bollywood',
            'indian music': 'bollywood',
            'korean pop': 'k-pop',
            'korean music': 'k-pop',
            'kpop': 'k-pop',
            'japanese pop': 'j-pop',
            'japanese music': 'j-pop',
            'jpop': 'j-pop',
            'spanish music': 'latin',
            'latin music': 'latin'
        };
        
        return genreSynonyms[query] || null;
    }

    looksLikeArtistQuery(query) {
        const artistIndicators = ['songs by', 'music by', 'artist', 'singer', 'band', 'group'];
        const lowerQuery = query.toLowerCase();
        
        if (artistIndicators.some(indicator => lowerQuery.includes(indicator))) {
            return true;
        }
        
        const words = query.trim().split(/\s+/);
        const isProperNoun = words.every(word => 
            word.length > 0 && 
            word[0] === word[0].toUpperCase() &&
            !['music', 'songs', 'hits', 'popular', 'best', 'top'].includes(word.toLowerCase())
        );
        
        return isProperNoun && words.length >= 1 && words.length <= 4;
    }

    extractArtistName(query) {
        const lowerQuery = query.toLowerCase();
        let artistName = query;
        
        const prefixesToRemove = ['songs by ', 'music by ', 'hits by ', 'best of '];
        const suffixesToRemove = [' songs', ' music', ' hits', ' tracks', ' artist', ' singer'];
        
        for (const prefix of prefixesToRemove) {
            if (lowerQuery.startsWith(prefix)) {
                artistName = query.substring(prefix.length);
                break;
            }
        }
        
        const lowerArtistName = artistName.toLowerCase();
        for (const suffix of suffixesToRemove) {
            if (lowerArtistName.endsWith(suffix)) {
                artistName = artistName.substring(0, artistName.length - suffix.length);
                break;
            }
        }
        
        return artistName.trim();
    }

    deduplicateSongsByKey(songs) {
        const seen = new Set();
        return songs.filter(song => {
            const key = this.generateSongKey(song.title, song.artist);
            if (seen.has(key)) {
                return false;
            }
            seen.add(key);
            return true;
        });
    }

    generateSongKey(title, artist) {
        return `${this.normalizeString(title)}_${this.normalizeString(artist)}`;
    }

    normalizeString(str) {
        return str.toLowerCase()
            .replace(/[^\w\s]/g, '')
            .replace(/\s+/g, ' ')
            .trim();
    }

    formatSearchTheme(searchQuery) {
        const genreFormatting = {
            'bollywood': 'Bollywood',
            'k-pop': 'K-Pop',
            'j-pop': 'J-Pop',
            'latin': 'Latin Music',
            'afrobeats': 'Afrobeats',
            'arabic': 'Arabic Music',
            'french chanson': 'French Chanson'
        };
        
        const identifiedGenre = this.identifyGenreFromQuery(searchQuery.toLowerCase());
        if (identifiedGenre && genreFormatting[identifiedGenre]) {
            return genreFormatting[identifiedGenre];
        }
        
        if (this.looksLikeArtistQuery(searchQuery)) {
            const artistName = this.extractArtistName(searchQuery);
            return `${artistName} Music Quiz`;
        }
        
        return searchQuery.split(' ')
            .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
            .join(' ');
    }

    shuffleArray(array) {
        const shuffled = [...array];
        for (let i = shuffled.length - 1; i > 0; i--) {
            const j = Math.floor(Math.random() * (i + 1));
            [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
        }
        return shuffled;
    }

    // Compatibility methods for existing integrations
    async generateCustomPuzzle(searchQuery, options = {}) {
        const { difficulty = 'medium' } = options;
        return await this.generateMusicPuzzle(difficulty, searchQuery);
    }

    async generateMusicPuzzleWithOptions(difficulty, options = {}) {
        const { language, country, maxSongs = 6 } = options;
        
        try {
            let searchQuery = '';
            if (language && country) {
                searchQuery = `${language} music ${country}`;
            } else if (language) {
                searchQuery = `${language} music`;
            } else {
                searchQuery = 'popular hits';
            }
            
            return await this.generateMusicPuzzle(difficulty, searchQuery);
        } catch (error) {
            return {
                success: false,
                error: error.message,
                message: `Music puzzle with options failed: ${error.message}`
            };
        }
    }

    async generateArtistFocusedPuzzle(artistName, difficulty, options = {}) {
        try {
            return await this.generateMusicPuzzle(difficulty, artistName);
        } catch (error) {
            return {
                success: false,
                error: error.message,
                message: `Artist-focused puzzle failed: ${error.message}`
            };
        }
    }

    async generateModernHitsPuzzle(difficulty, options = {}) {
        const { originalTopic, fallbackMode } = options;
        
        try {
            const searchQuery = fallbackMode ? 'top hits popular' : 'modern hits';
            const result = await this.generateMusicPuzzle(difficulty, searchQuery);
            
            if (result.success && fallbackMode) {
                result.data.fallbackInfo = {
                    originalTopic,
                    fallbackReason: `Topic "${originalTopic}" mapped to Modern Hits`,
                    fallbackMode: true
                };
            }
            
            return result;
        } catch (error) {
            return {
                success: false,
                error: error.message,
                message: `Modern hits puzzle failed: ${error.message}`
            };
        }
    }

    async generateSearchBasedPuzzle(searchQuery, difficulty, options = {}) {
        try {
            return await this.generateMusicPuzzle(difficulty, searchQuery);
        } catch (error) {
            return {
                success: false,
                error: error.message,
                message: `Search-based puzzle failed: ${error.message}`
            };
        }
    }

    formatForPuzzleSystemWithFallback(puzzleData) {
        try {
            const formatted = this.formatForPuzzleSystem(puzzleData);
            
            if (puzzleData.fallbackInfo) {
                const questionData = JSON.parse(formatted.question);
                questionData.fallbackInfo = puzzleData.fallbackInfo;
                formatted.question = JSON.stringify(questionData);
                
                if (puzzleData.fallbackInfo.fallbackMode) {
                    formatted.hint = `Music Quiz (${puzzleData.fallbackInfo.originalTopic} → Modern Hits)`;
                }
            }
            
            return formatted;
        } catch (error) {
            this.debugLog(`Fallback formatting failed: ${error.message}`, 'error');
            throw error;
        }
    }
}

export const musicPuzzleGenerator = new MusicPuzzleGenerator();