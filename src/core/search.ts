import type { Kanji } from '../data';

export function searchKanji(items: Kanji[], query: string, favorites: string[] = [], savedOnly = false): Kanji[] {
  const needle = query.normalize('NFKC').trim().toLocaleLowerCase();
  return items.filter((item) => {
    if (savedOnly && !favorites.includes(item.character)) return false;
    if (!needle) return true;
    return [item.character, ...item.meanings, ...item.on, ...item.kun, ...item.nanori]
      .join(' ')
      .normalize('NFKC')
      .toLocaleLowerCase()
      .includes(needle);
  });
}
