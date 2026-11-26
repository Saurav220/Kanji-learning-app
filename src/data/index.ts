import kanjiUrl from './generated/kanji.json?url';
import exampleVocabularyUrl from './generated/example-vocabulary.json?url';
import strokeUrl from './generated/strokes.json?url';

export type Kanji = {
  character: string;
  codepoint: string;
  meanings: string[];
  on: string[];
  kun: string[];
  nanori: string[];
  strokes?: number;
  grade?: number;
  jlpt?: number;
  frequency?: number;
  radical?: number;
  wordIds: number[];
  exampleIds: number[];
};

export type Vocabulary = {
  id: number;
  written: string[];
  readings: { text: string; appliesTo: string[] | null }[];
  glosses: string[];
  partOfSpeech: string[];
  priorities: string[];
};

export type KanjiExample = {
  id: number;
  written: string[];
  readings: string[];
  glosses: string[];
  partOfSpeech: string[];
};

export type Catalog = {
  kanji: Kanji[];
  examples: Record<string, Vocabulary>;
  strokes: Record<string, string[]>;
};

async function readJson<T>(url: string): Promise<T> {
  const response = await fetch(url);
  if (!response.ok) throw new Error(`Could not load local language data (${response.status}).`);
  return response.json() as Promise<T>;
}

export async function loadCatalog(): Promise<Catalog> {
  const [kanji, examples, strokes] = await Promise.all([
    readJson<Kanji[]>(kanjiUrl),
    readJson<Record<string, Vocabulary>>(exampleVocabularyUrl),
    readJson<Record<string, string[]>>(strokeUrl),
  ]);
  return { kanji, examples, strokes };
}

export function wordsForCharacter(character: string, catalog: Catalog, limit = 6): KanjiExample[] {
  const entry = catalog.kanji.find((item) => item.character === character);
  if (!entry) return [];
  return entry.exampleIds.slice(0, limit).flatMap((id) => {
    const word = catalog.examples[String(id)];
    if (!word) return [];
    const written = word.written.filter((form) => [...form].includes(character));
    const readings = word.readings
      .filter(({ appliesTo }) => appliesTo === null || written.some((form) => appliesTo.includes(form)))
      .map(({ text }) => text);
    return [{ id, written, readings, glosses: word.glosses, partOfSpeech: word.partOfSpeech }];
  });
}

export function strokePathsForCharacter(character: string, catalog: Catalog): string[] {
  return catalog.strokes[character] ?? [];
}
