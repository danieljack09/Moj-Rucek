'use strict';

const path = require('node:path');
const fs = require('node:fs');
const { DatabaseSync } = require('node:sqlite');
const { HttpError } = require('./lib/http-error');
const { lockedView } = require('./limits');
const { detectImage } = require('./lib/image-type');
const crypto = require('node:crypto');

const DB_PATH = process.env.DB_PATH || path.join(__dirname, 'data', 'catalog.db');
fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });

const db = new DatabaseSync(DB_PATH);
db.exec('PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON;');

db.exec(`
CREATE TABLE IF NOT EXISTS ingredients (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  name         TEXT NOT NULL,
  name_key     TEXT NOT NULL UNIQUE,   -- lowercased in JS: SQLite NOCASE doesn't fold Cyrillic
  category     TEXT NOT NULL,
  default_unit TEXT NOT NULL,
  deleted      INTEGER NOT NULL DEFAULT 0,
  updated_at   INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS recipes (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  name         TEXT NOT NULL,
  description  TEXT,
  servings     INTEGER NOT NULL DEFAULT 3,
  prep_minutes INTEGER NOT NULL DEFAULT 0,
  cook_minutes INTEGER NOT NULL DEFAULT 0,
  category     TEXT,
  tags         TEXT NOT NULL DEFAULT '[]',
  steps        TEXT NOT NULL DEFAULT '[]',
  notes        TEXT,
  deleted      INTEGER NOT NULL DEFAULT 0,
  updated_at   INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS recipe_ingredients (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  recipe_id  INTEGER NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
  name       TEXT NOT NULL,
  quantity   REAL NOT NULL,
  unit       TEXT NOT NULL,
  notes      TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0
);

-- One picture per recipe, stored in the database itself so the nightly backup and a restore always carry it.
CREATE TABLE IF NOT EXISTS recipe_images (
  recipe_id  INTEGER PRIMARY KEY REFERENCES recipes(id) ON DELETE CASCADE,
  mime       TEXT NOT NULL,
  sha        TEXT NOT NULL,        -- first 16 hex characters of the SHA-256 of the bytes; part of the picture's address
  size       INTEGER NOT NULL,
  bytes      BLOB NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ingredients_updated ON ingredients(updated_at);
CREATE INDEX IF NOT EXISTS idx_recipes_updated ON recipes(updated_at);
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_recipe ON recipe_ingredients(recipe_id);
`);

// Upgrade of a database that already has recipe_images without the framing columns (the point of the picture that stays in view when
// the app crops it to a circle: 0 = left/top edge, 0.5 = middle, 1 = right/bottom edge).
{
  const columns = db.prepare('PRAGMA table_info(recipe_images)').all().map((c) => c.name);
  if (!columns.includes('focus_x')) db.exec('ALTER TABLE recipe_images ADD COLUMN focus_x REAL NOT NULL DEFAULT 0.5');
  if (!columns.includes('focus_y')) db.exec('ALTER TABLE recipe_images ADD COLUMN focus_y REAL NOT NULL DEFAULT 0.5');
}

// Strictly increasing timestamps, so "updated since X" never misses a change
// made in the same millisecond as a previous one.
let lastTs = 0;
function now() {
  lastTs = Math.max(Date.now(), lastTs + 1);
  return lastTs;
}

function transaction(fn) {
  db.exec('BEGIN IMMEDIATE');
  try {
    const result = fn();
    db.exec('COMMIT');
    return result;
  } catch (err) {
    db.exec('ROLLBACK');
    throw err;
  }
}

// ---------- ingredients ----------

const ingredientOut = (r) => ({
  id: r.id,
  name: r.name,
  category: r.category,
  defaultUnit: r.default_unit,
  deleted: !!r.deleted,
  updatedAt: r.updated_at,
});

const byName = (a, b) => a.name.localeCompare(b.name, 'mk');
const keyOf = (name) => name.trim().toLowerCase();
/** Ingredient names are stored with a capital first letter: "домати" → "Домати". */
const capitalize = (name) => {
  const n = name.trim();
  return n.charAt(0).toLocaleUpperCase('mk') + n.slice(1);
};

function listIngredients() {
  return db
    .prepare('SELECT * FROM ingredients WHERE deleted = 0')
    .all()
    .map(ingredientOut)
    .sort(byName);
}

function saveIngredient(id, { name, category, defaultUnit }) {
  name = capitalize(name);
  return transaction(() => {
    const ts = now();
    const key = keyOf(name);
    if (id == null) {
      // Re-adding a previously deleted name revives that row instead of failing UNIQUE.
      const existing = db.prepare('SELECT id, deleted FROM ingredients WHERE name_key = ?').get(key);
      if (existing) {
        if (!existing.deleted) throw new HttpError(409, 'Намирницата веќе постои.');
        db.prepare(
          'UPDATE ingredients SET name=?, name_key=?, category=?, default_unit=?, deleted=0, updated_at=? WHERE id=?'
        ).run(name, key, category, defaultUnit, ts, existing.id);
        return ingredientOut(db.prepare('SELECT * FROM ingredients WHERE id=?').get(existing.id));
      }
      const res = db
        .prepare('INSERT INTO ingredients (name, name_key, category, default_unit, updated_at) VALUES (?,?,?,?,?)')
        .run(name, key, category, defaultUnit, ts);
      return ingredientOut(db.prepare('SELECT * FROM ingredients WHERE id=?').get(res.lastInsertRowid));
    }
    // A soft-deleted row may still hold this key; only a live one counts as a clash.
    const clash = db.prepare('SELECT id, deleted FROM ingredients WHERE name_key = ? AND id != ?').get(key, id);
    if (clash && !clash.deleted) throw new HttpError(409, 'Друга намирница со ова име веќе постои.');
    if (clash) db.prepare('UPDATE ingredients SET name_key = ? WHERE id = ?').run(`${key}#deleted-${clash.id}`, clash.id);
    const res = db
      .prepare('UPDATE ingredients SET name=?, name_key=?, category=?, default_unit=?, updated_at=? WHERE id=? AND deleted=0')
      .run(name, key, category, defaultUnit, ts, id);
    if (res.changes === 0) throw new HttpError(404, 'Не е пронајдено.');
    return ingredientOut(db.prepare('SELECT * FROM ingredients WHERE id=?').get(id));
  });
}

function deleteIngredient(id) {
  const res = db.prepare('UPDATE ingredients SET deleted=1, updated_at=? WHERE id=? AND deleted=0').run(now(), id);
  if (res.changes === 0) throw new HttpError(404, 'Не е пронајдено.');
}

/**
 * Imports the app's built-in ingredient list (seed-ingredients.json) so the admin
 * panel shows everything the app already suggests. Insert-only: a name that already
 * has a row (live or deleted) is left alone, so edits and deletions are never undone.
 */
function seedIngredients() {
  let seed;
  try {
    seed = JSON.parse(fs.readFileSync(path.join(__dirname, 'seed-ingredients.json'), 'utf8'));
  } catch {
    return 0;
  }
  return transaction(() => {
    const exists = db.prepare('SELECT 1 FROM ingredients WHERE name_key = ?');
    const ins = db.prepare('INSERT INTO ingredients (name, name_key, category, default_unit, updated_at) VALUES (?,?,?,?,?)');
    let added = 0;
    for (const s of seed) {
      if (exists.get(keyOf(s.name))) continue;
      ins.run(capitalize(s.name), keyOf(s.name), s.category, s.defaultUnit, now());
      added++;
    }
    return added;
  });
}
const seeded = seedIngredients();
if (seeded) console.log(`Seeded ${seeded} built-in ingredients.`);

/** Capitalizes names stored before capitalization was enforced; bumps updated_at so apps re-sync them. */
function capitalizeExistingIngredients() {
  return transaction(() => {
    const upd = db.prepare('UPDATE ingredients SET name = ?, updated_at = ? WHERE id = ?');
    let changed = 0;
    for (const r of db.prepare('SELECT id, name FROM ingredients').all()) {
      const fixed = capitalize(r.name);
      if (fixed !== r.name) { upd.run(fixed, now(), r.id); changed++; }
    }
    return changed;
  });
}
const capitalized = capitalizeExistingIngredients();
if (capitalized) console.log(`Capitalized ${capitalized} ingredient names.`);

// ---------- recipes ----------

const parseJsonList = (s) => {
  try {
    const v = JSON.parse(s);
    return Array.isArray(v) ? v : [];
  } catch {
    return [];
  }
};

const EXT_BY_MIME = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp' };
const MAX_IMAGE_BYTES = 2 * 1024 * 1024;

/** The address of a recipe's picture ("/img/12-0123456789abcdef.jpg"), or null. The address changes whenever the picture does. */
function imageUrlOf(recipeId) {
  const row = db.prepare('SELECT sha, mime FROM recipe_images WHERE recipe_id = ?').get(recipeId);
  return row ? `/img/${recipeId}-${row.sha}.${EXT_BY_MIME[row.mime]}` : null;
}

/** A position inside the picture, 0 to 1 (rounded to 3 decimals); anything else is refused. Missing means the middle. */
function parseFocusValue(v) {
  if (v === undefined || v === null || v === '') return 0.5;
  if (typeof v !== 'number' && typeof v !== 'string') throw new HttpError(400, 'Невалидна положба на сликата.');   // no arrays, objects, booleans
  const n = Number(v);
  if (!Number.isFinite(n) || n < 0 || n > 1) throw new HttpError(400, 'Невалидна положба на сликата.');
  return Math.round(n * 1000) / 1000;
}

/** The framing point of a recipe's picture, or null when it has no picture. */
function imageFocusOf(recipeId) {
  const row = db.prepare('SELECT focus_x AS x, focus_y AS y FROM recipe_images WHERE recipe_id = ?').get(recipeId);
  return row ? { x: row.x, y: row.y } : null;
}

/** Stores (or replaces) the picture of a recipe. Only real JPEG, PNG or WebP files up to 2 MB are accepted. [focus] = { x, y } (default: the middle). */
function setRecipeImage(recipeId, buffer, focus = {}) {
  if (!Buffer.isBuffer(buffer) || buffer.length === 0) throw new HttpError(400, 'Нема слика.');
  if (buffer.length > MAX_IMAGE_BYTES) throw new HttpError(413, 'Сликата е преголема (најмногу 2 MB).');
  const kind = detectImage(buffer);
  if (!kind) throw new HttpError(400, 'Сликата мора да биде JPEG, PNG или WebP.');
  const sha = crypto.createHash('sha256').update(buffer).digest('hex').slice(0, 16);
  const fx = parseFocusValue(focus.x);
  const fy = parseFocusValue(focus.y);
  return transaction(() => {
    const exists = db.prepare('SELECT id FROM recipes WHERE id = ? AND deleted = 0').get(recipeId);
    if (!exists) throw new HttpError(404, 'Не е пронајдено.');
    const ts = now();
    db.prepare(
      `INSERT INTO recipe_images (recipe_id, mime, sha, size, bytes, updated_at, focus_x, focus_y) VALUES (?,?,?,?,?,?,?,?)
       ON CONFLICT(recipe_id) DO UPDATE SET mime=excluded.mime, sha=excluded.sha, size=excluded.size, bytes=excluded.bytes,
         updated_at=excluded.updated_at, focus_x=excluded.focus_x, focus_y=excluded.focus_y`
    ).run(recipeId, kind.mime, sha, buffer.length, buffer, ts, fx, fy);
    db.prepare('UPDATE recipes SET updated_at = ? WHERE id = ?').run(ts, recipeId);   // the app picks the change up at its next sync
    return { imageUrl: imageUrlOf(recipeId), imageFocusX: fx, imageFocusY: fy };
  });
}

/** Moves the framing point of an existing picture without sending it again (the address stays, the recipe counts as changed). */
function setRecipeImageFocus(recipeId, focus) {
  const fx = parseFocusValue(focus.x);
  const fy = parseFocusValue(focus.y);
  return transaction(() => {
    const exists = db.prepare('SELECT id FROM recipes WHERE id = ? AND deleted = 0').get(recipeId);
    if (!exists) throw new HttpError(404, 'Не е пронајдено.');
    const res = db.prepare('UPDATE recipe_images SET focus_x = ?, focus_y = ?, updated_at = ? WHERE recipe_id = ?').run(fx, fy, now(), recipeId);
    if (res.changes === 0) throw new HttpError(404, 'Рецептот нема слика.');
    db.prepare('UPDATE recipes SET updated_at = ? WHERE id = ?').run(now(), recipeId);
    return { imageUrl: imageUrlOf(recipeId), imageFocusX: fx, imageFocusY: fy };
  });
}

/** Removes the picture of a recipe (nothing happens when it has none). */
function removeRecipeImage(recipeId) {
  return transaction(() => {
    const exists = db.prepare('SELECT id FROM recipes WHERE id = ? AND deleted = 0').get(recipeId);
    if (!exists) throw new HttpError(404, 'Не е пронајдено.');
    const res = db.prepare('DELETE FROM recipe_images WHERE recipe_id = ?').run(recipeId);
    if (res.changes > 0) db.prepare('UPDATE recipes SET updated_at = ? WHERE id = ?').run(now(), recipeId);
    return { imageUrl: null, imageFocusX: null, imageFocusY: null };
  });
}

/** The picture served at an address, only while that address is still the current one for that recipe. */
function getRecipeImage(recipeId, sha) {
  const row = db.prepare(
    `SELECT i.mime, i.sha, i.bytes FROM recipe_images i JOIN recipes r ON r.id = i.recipe_id WHERE i.recipe_id = ? AND r.deleted = 0`
  ).get(recipeId);
  if (!row || row.sha !== sha) return null;
  return { mime: row.mime, sha: row.sha, bytes: Buffer.from(row.bytes) };
}

function recipeOut(r) {
  const lines = db
    .prepare('SELECT * FROM recipe_ingredients WHERE recipe_id = ? ORDER BY sort_order, id')
    .all(r.id)
    .map((l) => ({ name: l.name, quantity: l.quantity, unit: l.unit, notes: l.notes, sortOrder: l.sort_order }));
  return {
    id: r.id,
    name: r.name,
    description: r.description,
    servings: r.servings,
    prepTimeMinutes: r.prep_minutes,
    cookTimeMinutes: r.cook_minutes,
    category: r.category,
    tags: parseJsonList(r.tags),
    instructionSteps: parseJsonList(r.steps),
    notes: r.notes,
    ingredients: lines,
    imageUrl: r.deleted ? null : imageUrlOf(r.id),
    imageFocusX: r.deleted ? null : (imageFocusOf(r.id)?.x ?? null),
    imageFocusY: r.deleted ? null : (imageFocusOf(r.id)?.y ?? null),
    deleted: !!r.deleted,
    updatedAt: r.updated_at,
  };
}

function listRecipes() {
  return db
    .prepare('SELECT * FROM recipes WHERE deleted = 0')
    .all()
    .map(recipeOut)
    .sort(byName);
}

function getRecipe(id) {
  const r = db.prepare('SELECT * FROM recipes WHERE id = ? AND deleted = 0').get(id);
  if (!r) throw new HttpError(404, 'Не е пронајдено.');
  return recipeOut(r);
}

/** Full details of the recipes that exist and are not deleted, in the order of [ids]. */
function getRecipesByIds(ids) {
  const out = [];
  for (const id of ids) {
    const r = db.prepare('SELECT * FROM recipes WHERE id = ? AND deleted = 0').get(id);
    if (r) out.push(recipeOut(r));
  }
  return out;
}

function allRecipesFull() {
  return listRecipes();
}

function writeRecipeLines(recipeId, ingredients) {
  db.prepare('DELETE FROM recipe_ingredients WHERE recipe_id = ?').run(recipeId);
  const ins = db.prepare(
    'INSERT INTO recipe_ingredients (recipe_id, name, quantity, unit, notes, sort_order) VALUES (?,?,?,?,?,?)'
  );
  ingredients.forEach((l, i) => ins.run(recipeId, l.name, l.quantity, l.unit, l.notes, i));
}

function saveRecipe(id, d) {
  return transaction(() => {
    const ts = now();
    const cols = [d.name, d.description, d.servings, d.prepTimeMinutes, d.cookTimeMinutes, d.category,
      JSON.stringify(d.tags), JSON.stringify(d.instructionSteps), d.notes, ts];
    let recipeId = id;
    if (id == null) {
      const res = db
        .prepare(
          `INSERT INTO recipes (name, description, servings, prep_minutes, cook_minutes, category, tags, steps, notes, updated_at)
           VALUES (?,?,?,?,?,?,?,?,?,?)`
        )
        .run(...cols);
      recipeId = Number(res.lastInsertRowid);
    } else {
      const res = db
        .prepare(
          `UPDATE recipes SET name=?, description=?, servings=?, prep_minutes=?, cook_minutes=?, category=?,
             tags=?, steps=?, notes=?, updated_at=? WHERE id=? AND deleted=0`
        )
        .run(...cols, id);
      if (res.changes === 0) throw new HttpError(404, 'Не е пронајдено.');
    }
    writeRecipeLines(recipeId, d.ingredients);
    return recipeOut(db.prepare('SELECT * FROM recipes WHERE id=?').get(recipeId));
  });
}

function deleteRecipe(id) {
  const res = db.prepare('UPDATE recipes SET deleted=1, updated_at=? WHERE id=? AND deleted=0').run(now(), id);
  if (res.changes === 0) throw new HttpError(404, 'Не е пронајдено.');
  db.prepare('DELETE FROM recipe_images WHERE recipe_id = ?').run(id);   // a deleted recipe keeps no picture
}

// ---------- public catalog for the app ----------

/** Everything changed at or after `since` (epoch ms), including deletions. since=0 → full catalog. */
function catalogSince(since, { locked = false } = {}) {
  const serverTime = now();
  const ingredients = db
    .prepare('SELECT * FROM ingredients WHERE updated_at >= ? AND updated_at < ? ORDER BY id')
    .all(since, serverTime)
    .map(ingredientOut);
  const recipes = db
    .prepare('SELECT * FROM recipes WHERE updated_at >= ? AND updated_at < ? ORDER BY id')
    .all(since, serverTime)
    .map(recipeOut)
    .map((r) => (locked ? lockedView(r) : { ...r, locked: false }));
  // Clients store `serverTime` and send it back as `since`; anything stamped
  // before it is already included, anything later is picked up next time.
  return { serverTime, ingredients, recipes };
}


module.exports = {
  db,
  HttpError,
  listIngredients, saveIngredient, deleteIngredient,
  listRecipes, getRecipe, saveRecipe, deleteRecipe,
  catalogSince,
  getRecipesByIds, allRecipesFull,
  setRecipeImage, setRecipeImageFocus, removeRecipeImage, getRecipeImage, MAX_IMAGE_BYTES,
};
