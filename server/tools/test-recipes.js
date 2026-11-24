'use strict';

// Adds (or removes) four clearly marked test recipes on the server, through the admin API.
//
//   node server/tools/test-recipes.js add    <file-with-the-admin-password>
//   node server/tools/test-recipes.js remove <file-with-the-admin-password>
//
// The password is read from a file you create yourself (and delete afterwards), so it never has to be typed into a
// chat or a command line. Test recipes all start with "ТЕСТ – ", which is how `remove` finds them.

const fs = require('node:fs');

const BASE = process.env.RUCEK_URL || 'https://rucek.display.mk';
const PREFIX = 'ТЕСТ – ';

const RECIPES = [
  {
    name: PREFIX + 'Пилешко со ориз', category: 'PILESKO', servings: 3, prepTimeMinutes: 15, cookTimeMinutes: 40,
    description: 'Едноставно јадење за секој ден.', tags: ['брзо'], notes: 'Може да се додаде и зеленчук.',
    instructionSteps: ['Исечи го пилешкото на коцки.', 'Запржи го во малку масло.', 'Додај ориз и вода и вари 25 минути.'],
    ingredients: [{ name: 'Пилешко филе', quantity: 500, unit: 'GRAM' }, { name: 'Ориз', quantity: 300, unit: 'GRAM' }, { name: 'Масло', quantity: 50, unit: 'MILLILITER' }],
  },
  {
    name: PREFIX + 'Спагети болоњезе', category: 'PASTA', servings: 4, prepTimeMinutes: 10, cookTimeMinutes: 30,
    description: 'Класичен сос со мелено месо.', tags: ['паста'], notes: null,
    instructionSteps: ['Запржи го меленото месо.', 'Додај домати и зачини и крчкај 20 минути.', 'Свари ги спагетите и сервирај.'],
    ingredients: [{ name: 'Спагети', quantity: 400, unit: 'GRAM' }, { name: 'Мелено месо', quantity: 400, unit: 'GRAM' }, { name: 'Конзерва домати', quantity: 1, unit: 'CAN' }],
  },
  {
    name: PREFIX + 'Тавче гравче', category: 'GRAV_I_LEKJA', servings: 4, prepTimeMinutes: 20, cookTimeMinutes: 90,
    description: 'Традиционално јадење.', tags: ['традиционално'], notes: 'Најдобро е ако грав се натопи преку ноќ.',
    instructionSteps: ['Натопи го гравот преку ноќ.', 'Свари го додека не омекне.', 'Додај кромид и пиперка и испечи во рерна.'],
    ingredients: [{ name: 'Грав', quantity: 500, unit: 'GRAM' }, { name: 'Кромид', quantity: 2, unit: 'PIECE' }, { name: 'Сува пиперка', quantity: 1, unit: 'PIECE' }],
  },
  {
    name: PREFIX + 'Компири во рерна', category: 'KOMPIR', servings: 3, prepTimeMinutes: 10, cookTimeMinutes: 45,
    description: 'Хрскави компири со зачини.', tags: ['едноставно'], notes: null,
    instructionSteps: ['Исечи ги компирите на кришки.', 'Измешај ги со масло и зачини.', 'Пеки 45 минути на 200 степени.'],
    ingredients: [{ name: 'Компири', quantity: 1, unit: 'KILOGRAM' }, { name: 'Масло', quantity: 40, unit: 'MILLILITER' }, { name: 'Зачини', quantity: 1, unit: 'PACKAGE' }],
  },
];

async function main() {
  const [mode, passwordFile] = process.argv.slice(2);
  if (!['add', 'remove'].includes(mode) || !passwordFile) {
    console.error('Usage: node server/tools/test-recipes.js add|remove <password-file>');
    process.exit(2);
  }
  const password = fs.readFileSync(passwordFile, 'utf8').trim();
  const login = await fetch(BASE + '/admin/api/login', {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ password }),
  });
  if (!login.ok) { console.error('Login failed (' + login.status + '). Check the password file.'); process.exit(1); }
  const cookie = login.headers.get('set-cookie').split(';')[0];
  const call = (method, path, body) => fetch(BASE + '/admin/api' + path, {
    method, headers: { 'content-type': 'application/json', cookie }, body: body ? JSON.stringify(body) : undefined,
  });

  const existing = await (await call('GET', '/recipes')).json();
  const mine = existing.filter((r) => r.name.startsWith(PREFIX));
  if (mode === 'remove') {
    for (const r of mine) await call('DELETE', '/recipes/' + r.id);
    console.log('Removed ' + mine.length + ' test recipe(s).');
    return;
  }
  const have = new Set(mine.map((r) => r.name));
  let added = 0;
  for (const r of RECIPES) {
    if (have.has(r.name)) continue;
    const res = await call('POST', '/recipes', r);
    if (!res.ok) { console.error('Could not add "' + r.name + '" (' + res.status + '): ' + (await res.text())); process.exit(1); }
    added++;
  }
  console.log('Added ' + added + ' test recipe(s); ' + (RECIPES.length - added) + ' were already there.');
}

main().catch((e) => { console.error(e.message); process.exit(1); });
