export type Rating = 'again' | 'hard' | 'good' | 'easy';
export type ReviewRecord = {
  type: 'kanji' | 'word'; id: string; repetitions: number; intervalDays: number;
  dueAt: number; lastReviewedAt: number; firstReviewedAt: number; lastRating: Rating;
};
export type StudySet = {
  id: string; name: string; kanji: string[]; words: number[]; createdAt: number;
};
export type SessionRecord = {
  id: string; mode: 'cards' | 'quiz' | 'writing'; startedAt: number; finishedAt: number;
  total: number; correct: number;
};
export type Preferences = { dailyNew: number; cardDirection: 'meaning' | 'reading' | 'kanji' };
export type StudyState = {
  version: 2; favorites: string[]; favoriteWords: number[]; sets: StudySet[];
  reviews: Record<string, ReviewRecord>; sessions: SessionRecord[]; preferences: Preferences;
};

const STORAGE_KEY = 'kf:state:v2';
const DEFAULT_PREFERENCES: Preferences = { dailyNew: 10, cardDirection: 'meaning' };

export function createInitialState(): StudyState {
  return { version: 2, favorites: [], favoriteWords: [], sets: [], reviews: {}, sessions: [], preferences: DEFAULT_PREFERENCES };
}

function savedArray<T>(key: string): T[] {
  try {
    const value = JSON.parse(localStorage.getItem(key) ?? '[]');
    return Array.isArray(value) ? value : [];
  } catch { return []; }
}

export function loadStudyState(): StudyState {
  try {
    const value = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null') as Partial<StudyState> | null;
    if (value?.version === 2 && Array.isArray(value.favorites) && value.reviews) {
      return { ...createInitialState(), ...value, preferences: { ...DEFAULT_PREFERENCES, ...value.preferences } };
    }
  } catch { /* migrate from the prototype keys below */ }
  const migrated = createInitialState();
  migrated.favorites = savedArray<string>('kf:favorites');
  try {
    const oldRatings = JSON.parse(localStorage.getItem('kf:ratings') ?? '{}') as Record<string, string>;
    migrated.reviews = Object.fromEntries(Object.entries(oldRatings).map(([id, rating]) => [
      `kanji:${id}`, { type: 'kanji' as const, id, repetitions: rating === 'good' ? 1 : 0,
        intervalDays: rating === 'good' ? 1 : 0, dueAt: Date.now(), lastReviewedAt: Date.now(), firstReviewedAt: Date.now(),
        lastRating: rating === 'good' ? 'good' as const : 'again' as const },
    ]));
  } catch { /* ignore malformed prototype ratings */ }
  persistStudyState(migrated);
  return migrated;
}

export function persistStudyState(state: StudyState): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

export function reviewKey(type: 'kanji' | 'word', id: string | number): string {
  return `${type}:${id}`;
}

export function recordReview(state: StudyState, type: 'kanji' | 'word', id: string | number, rating: Rating, now = Date.now()): StudyState {
  const key = reviewKey(type, id);
  const previous = state.reviews[key];
  let repetitions = previous?.repetitions ?? 0;
  let intervalDays = previous?.intervalDays ?? 0;
  if (rating === 'again') { repetitions = 0; intervalDays = 10 / 1440; }
  if (rating === 'hard') { repetitions += 1; intervalDays = Math.max(1, intervalDays * 1.2); }
  if (rating === 'good') { repetitions += 1; intervalDays = intervalDays < 1 ? 1 : intervalDays * 2; }
  if (rating === 'easy') { repetitions += 1; intervalDays = Math.max(4, intervalDays * 2.5); }
  const reviews = { ...state.reviews, [key]: { type, id: String(id), repetitions, intervalDays,
    dueAt: now + intervalDays * 86_400_000, lastReviewedAt: now,
    firstReviewedAt: previous?.firstReviewedAt ?? previous?.lastReviewedAt ?? now, lastRating: rating } };
  return { ...state, reviews };
}

export function dueKanji(state: StudyState, now = Date.now()): Set<string> {
  return new Set(Object.values(state.reviews)
    .filter((item) => item.type === 'kanji' && item.dueAt <= now)
    .map((item) => item.id));
}

export function todayNewKanji(state: StudyState, now = Date.now()): Set<string> {
  const start = new Date(now); start.setHours(0, 0, 0, 0);
  return new Set(Object.values(state.reviews)
    .filter((item) => item.type === 'kanji' && (item.firstReviewedAt ?? item.lastReviewedAt) >= start.getTime())
    .map((item) => item.id));
}
