'use strict';

// Must stay in sync with the Android enums:
//   IngredientCategory, RecipeCategory, MeasurementUnit.
// Values are the enum *names* (what the app stores); labels are for the admin UI.

const INGREDIENT_CATEGORIES = {
  ZELENCUK: 'Зеленчук',
  OVOSJE: 'Овошје',
  MESO: 'Месо',
  MLECNI_PROIZVODI: 'Млечни производи',
  ZITARICI_I_TESTENINI: 'Житарици и тестенини',
  LEB_I_PEKARSKI_PROIZVODI: 'Леб и пекарски производи',
  KONZERVIRANA_HRANA: 'Конзервирана храна',
  ZACINI: 'Зачини',
  MASLA_I_DODATOCI: 'Масла и сосови',
  ZAMRZNATA_HRANA: 'Замрзната храна',
  JAJCA: 'Јајца',
  PIJALOCI: 'Пијалаци',
  DRUGO: 'Друго',
};

const RECIPE_CATEGORIES = {
  PILESKO: 'Пилешко',
  SVINSKO: 'Свинско',
  TELESKO: 'Телешко',
  MELENO_MESO: 'Мелено месо',
  RIBA: 'Риба',
  PASTA: 'Паста',
  ORIZ: 'Ориз',
  KOMPIR: 'Компир',
  GRAV_I_LEKJA: 'Грав и леќа',
  BEZ_MESO: 'Без месо',
  DRUGI_JADENJA: 'Други јадења',
};

const UNITS = {
  GRAM: 'g',
  KILOGRAM: 'kg',
  MILLILITER: 'ml',
  LITER: 'l',
  PIECE: 'парче',
  PACKAGE: 'пакување',
  CAN: 'конзерва',
};

module.exports = { INGREDIENT_CATEGORIES, RECIPE_CATEGORIES, UNITS };
