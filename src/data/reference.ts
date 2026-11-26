export type RuntimeRadicalLink = {
  code: number;
  character: string | null;
  meaning: string | null;
  reading: string | null;
  occurrences: number;
};

export type RuntimeKanji = {
  code: number;
  character: string;
  meaning: string | null;
  translation: string | null;
  on: string[];
  kun: string[];
  romanizedReading: string | null;
  nanori: string[];
  strokeCount: number | null;
  strokePaths: string[];
  radicals: RuntimeRadicalLink[];
  decomposition: string | null;
  classicalRadical: number | null;
  level: number | null;
  sequence: number | null;
  levels: Record<string, number>;
  sequences: Record<string, number>;
  notes: string | null;
  detailShard: number;
};

export type RuntimeWord = {
  id: number;
  forms: string[];
  readings: string[];
  romaji: string[];
  glosses: string[];
  tags: string[];
  jlpt: number | null;
  common: boolean;
  translation: string | null;
  notes: string | null;
  searchText: string;
};

export type RuntimeSentence = { id: number; text: string; translation: string | null };
export type RuntimeName = { id: number; name: string; reading: string; type: string | null };
export type KanjiDetail = { words: RuntimeWord[]; sentences: RuntimeSentence[]; names: RuntimeName[] };
export type RuntimeRadical = {
  code: number; character: string | null; meaning: string | null; reading: string | null;
  strokeCount: number | null; sequence: number | null; variantOf: number | null; strokePaths: string[];
};
export type RuntimeKana = {
  character: string | null; reading: string; example: string | null; meaning: string | null;
  translation: string | null; origin: string | null; katakana: boolean; archaic: boolean;
  diacritic: boolean; strokeCount: number | null; strokePaths: string[]; sequence: number;
};
export type RuntimeCatalog = {
  schemaVersion: number; source: string; kanji: RuntimeKanji[]; radicals: RuntimeRadical[];
  kana: RuntimeKana[]; detailShards: number;
};
export type OutlierReference = {
  id: number; name: string; text: string; keyConcept: boolean; ancientForm: string | null;
};
export type OutlierCharacter = {
  id: number; name: string; drawing: { viewBox: string; paths: string[] } | null;
};
export type RuntimeOutlier = { references: OutlierReference[]; characters: OutlierCharacter[] };

async function readGzipJson<T>(path: string): Promise<T> {
  const response = await fetch(path);
  if (!response.ok) throw new Error(`Could not load the local reference data (${response.status}).`);
  const compressed = await response.arrayBuffer();
  if (typeof DecompressionStream === 'undefined') throw new Error('This browser cannot open the compressed local data.');
  const stream = new Blob([compressed]).stream().pipeThrough(new DecompressionStream('gzip'));
  return new Response(stream).json() as Promise<T>;
}

const cached = new Map<string, Promise<unknown>>();
function cachedLoad<T>(path: string): Promise<T> {
  let request = cached.get(path) as Promise<T> | undefined;
  if (!request) {
    request = readGzipJson<T>(path).catch((error) => {
      cached.delete(path);
      throw error;
    });
    cached.set(path, request);
  }
  return request;
}

export const loadRuntimeCatalog = () => cachedLoad<RuntimeCatalog>('/data/reference/catalog.json.gzbin');
export const loadRuntimeWords = () => cachedLoad<RuntimeWord[]>('/data/reference/words.json.gzbin');
export const loadRuntimeSentences = () => cachedLoad<RuntimeSentence[]>('/data/reference/sentences.json.gzbin');
export const loadRuntimeNames = () => cachedLoad<RuntimeName[]>('/data/reference/names.json.gzbin');
export const loadRuntimeOutlier = () => cachedLoad<RuntimeOutlier>('/data/reference/outlier.json.gzbin');

export async function loadKanjiDetail(kanji: RuntimeKanji): Promise<KanjiDetail> {
  const shard = await cachedLoad<Record<string, KanjiDetail>>(
    `/data/reference/details-${String(kanji.detailShard).padStart(3, '0')}.json.gzbin`,
  );
  return shard[kanji.character] ?? { words: [], sentences: [], names: [] };
}

export function normalizeSearch(value: string): string {
  return value.normalize('NFKC').trim().toLocaleLowerCase();
}

export type KanjiFilters = {
  jlpt?: number; strokeCount?: number; radical?: number; level?: number; favorites?: Set<string>;
  savedOnly?: boolean; dueOnly?: boolean; dueCharacters?: Set<string>;
};

export function searchRuntimeKanji(items: RuntimeKanji[], query: string, filters: KanjiFilters = {}): RuntimeKanji[] {
  const needle = normalizeSearch(query);
  return items.filter((item) => {
    if (needle && ![item.character, item.meaning ?? '', item.translation ?? '', item.romanizedReading ?? '',
      ...item.on, ...item.kun, ...item.nanori, ...item.radicals.map((radical) => `${radical.character ?? ''} ${radical.meaning ?? ''} ${radical.reading ?? ''}`)]
      .join(' ').normalize('NFKC').toLocaleLowerCase().includes(needle)) return false;
    if (filters.jlpt && item.levels.jlpt !== filters.jlpt) return false;
    if (filters.strokeCount && item.strokeCount !== filters.strokeCount) return false;
    if (filters.radical && !item.radicals.some((radical) => radical.code === filters.radical)) return false;
    if (filters.level && item.levels.jouyou !== filters.level) return false;
    if (filters.savedOnly && !filters.favorites?.has(item.character)) return false;
    if (filters.dueOnly && !filters.dueCharacters?.has(item.character)) return false;
    return true;
  });
}

export function searchRuntimeWords(items: RuntimeWord[], query: string, filters: { jlpt?: number; commonOnly?: boolean } = {}): RuntimeWord[] {
  const needle = normalizeSearch(query);
  return items.filter((word) => {
    if (needle && !word.searchText.includes(needle)) return false;
    if (filters.jlpt && word.jlpt !== filters.jlpt) return false;
    if (filters.commonOnly && !word.common) return false;
    return true;
  });
}
