// Loads the shared public-play catalog (web/shared/playCatalog.json). Dependency-free so it can be used from tests and the bot.
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const here = path.dirname(fileURLToPath(import.meta.url));
// Local: web/backend/services -> web/shared. Docker: /backend/services -> /shared. Same relative path.
export const CATALOG_PATH = path.resolve(here, '..', '..', 'shared', 'playCatalog.json');

export function loadCatalog(file = CATALOG_PATH) {
    const raw = JSON.parse(fs.readFileSync(file, 'utf8'));
    return { categories: raw.categories, games: raw.games };
}
