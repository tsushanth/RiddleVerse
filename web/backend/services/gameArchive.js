/**
 * Copy a game (and its difficulty variants) into deleted_games. Throws when anything cannot be read or the copy cannot be
 * written, so the caller does not delete. Restore: see supabase/migrations/*_deleted_games.sql.
 */
export async function archiveGame(db, gameId, { verified = false } = {}) {
    const { data: game, error: gameError } = await db.from('custom_games').select('*').eq('id', gameId).single();
    if (gameError || !game) throw new Error(`archive: game unreadable (${gameError?.code || 'no row'})`);

    const { data: variants, error: variantError } = await db.from('game_difficulty_variants').select('*').eq('parent_game_id', gameId);
    if (variantError) throw new Error(`archive: variants unreadable (${variantError.code || 'error'})`);

    const { error } = await db.from('deleted_games').insert({
        game_id: gameId, creator_id: game.creator_id, verified, game, variants: variants || [],
    });
    if (error) throw new Error(`archive: copy not stored (${error.code || 'error'})`);
}
