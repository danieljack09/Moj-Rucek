'use strict';

const path = require('node:path');
const fs = require('node:fs');
const { DatabaseSync } = require('node:sqlite');

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

CREATE INDEX IF NOT EXISTS idx_ingredients_updated ON ingredients(updated_at);
CREATE INDEX IF NOT EXISTS idx_recipes_updated ON recipes(updated_at);
CREATE INDEX IF NOT EXISTS idx_recipe_ingredients_recipe ON recipe_ingredients(recipe_id);
`);

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

function listIngredients() {
  return db
    .prepare('SELECT * FROM ingredients WHERE deleted = 0')
    .all()
    .map(ingredientOut)
    .sort(byName);
}

function saveIngredient(id, { name, category, defaultUnit }) {
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
      ins.run(s.name, keyOf(s.name), s.category, s.defaultUnit, now());
      added++;
    }
    return added;
  });
}
const seeded = seedIngredients();
if (seeded) console.log(`Seeded ${seeded} built-in ingredients.`);

// ---------- recipes ----------

const parseJsonList = (s) => {
  try {
    const v = JSON.parse(s);
    return Array.isArray(v) ? v : [];
  } catch {
    return [];
  }
};

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
}

// ---------- public catalog for the app ----------

/** Everything changed at or after `since` (epoch ms), including deletions. since=0 → full catalog. */
function catalogSince(since) {
  const serverTime = now();
  const ingredients = db
    .prepare('SELECT * FROM ingredients WHERE updated_at >= ? AND updated_at < ? ORDER BY id')
    .all(since, serverTime)
    .map(ingredientOut);
  const recipes = db
    .prepare('SELECT * FROM recipes WHERE updated_at >= ? AND updated_at < ? ORDER BY id')
    .all(since, serverTime)
    .map(recipeOut);
  // Clients store `serverTime` and send it back as `since`; anything stamped
  // before it is already included, anything later is picked up next time.
  return { serverTime, ingredients, recipes };
}

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

module.exports = {
  HttpError,
  listIngredients, saveIngredient, deleteIngredient,
  listRecipes, getRecipe, saveRecipe, deleteRecipe,
  catalogSince,
};
