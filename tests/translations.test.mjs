import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { english, translate } from '../lib/translations.ts';

test('every dictionary entry translates to English and preserves Russian', () => {
  for (const [ru, en] of Object.entries(english)) {
    assert.equal(translate(ru, 'en'), en);
    assert.equal(translate(ru, 'ru'), ru);
    assert.doesNotMatch(en, /[А-Яа-яЁё]/);
  }
});

test('all API errors have English translations', () => {
  const routes = readdirSync('app/api', { recursive: true }).filter(p => p.endsWith('route.ts')).map(p => `app/api/${p}`);
  for (const file of ['lib/analyzer.ts', 'lib/downloads.ts', ...routes]) {
    const source = readFileSync(file, 'utf8');
    for (const match of source.matchAll(/'([^'\n]*[А-Яа-яЁё][^'\n]*)'/g)) {
      const text = match[1];
      assert.doesNotMatch(translate(text, 'en'), /[А-Яа-яЁё]/, `${file}: ${text}`);
    }
  }
});

test('dynamic status text, sizes and original audio quality are translated', () => {
  for (const text of ['Размер файла: ≈28.4 МБ', ' · максимум 2160p', 'Дорожка 1 из 2. ', '≈ 3 мин.', '2.5 МБ/с', ' сек. · Обработка: ', ' · исходное качество', 'Загружено примерно 40 процентов', 'Выбрано: M4A, Исходное качество. Это пример. Вставьте свою ссылку выше, чтобы скачать настоящее видео.']) {
    assert.doesNotMatch(translate(text, 'en'), /[А-Яа-яЁё]/, text);
    assert.equal(translate(text, 'ru'), text);
  }
});
