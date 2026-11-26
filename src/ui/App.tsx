import { useEffect, useMemo, useRef, useState, type PointerEvent, type ReactNode } from 'react';
import {
  ArrowLeft, ArrowRight, BarChart3, BookOpen, Bookmark, CalendarDays, Check, ChevronLeft,
  ChevronRight, CircleHelp, Clock3, Compass, Download, Eye, Feather, Home, Layers3,
  Pause, Play, Plus, Puzzle, RotateCcw, Search, Settings, Sparkles, Type, Upload, X,
} from 'lucide-react';
import {
  loadKanjiDetail, loadRuntimeCatalog, loadRuntimeOutlier,
  loadRuntimeWords, normalizeSearch, searchRuntimeKanji, searchRuntimeWords,
  type KanjiDetail, type OutlierReference, type RuntimeCatalog, type RuntimeKanji,
  type RuntimeOutlier, type RuntimeWord,
} from '../data/reference';
import {
  dueKanji, loadStudyState, persistStudyState, recordReview, todayNewKanji,
  type Rating, type SessionRecord, type StudySet, type StudyState,
} from '../core/state';

type Screen = 'home' | 'kanji' | 'words' | 'study' | 'sets' | 'kana' | 'radicals' | 'progress' | 'outlier' | 'preferences';
type StudyMode = 'cards' | 'quiz' | 'writing';
type QuizQuestion = { character: string; prompt: string; correct: string; options: string[]; type: 'meaning' | 'reading' };

const navItems: { id: Screen; title: string; icon: typeof Home }[] = [
  { id: 'home', title: 'Today', icon: Home }, { id: 'kanji', title: 'Kanji', icon: Compass },
  { id: 'words', title: 'Words', icon: Search }, { id: 'study', title: 'Study', icon: BookOpen },
  { id: 'sets', title: 'Collections', icon: Layers3 }, { id: 'kana', title: 'Kana', icon: Type },
  { id: 'radicals', title: 'Radicals', icon: Puzzle }, { id: 'progress', title: 'Progress', icon: BarChart3 },
  { id: 'outlier', title: 'Outlier', icon: Sparkles }, { id: 'preferences', title: 'Settings', icon: Settings },
];

const short = (value: string | null | undefined, max = 48) => value && value.length > max ? `${value.slice(0, max - 1)}…` : value || '—';

function japaneseParts(input: string): ReactNode[] {
  const result: ReactNode[] = [];
  const pattern = /\{([^}]+)\}([^{}\s　]+)/g;
  let start = 0;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(input))) {
    if (match.index > start) result.push(input.slice(start, match.index));
    const reading = match[1].replace(/^\d+/, '');
    result.push(<ruby key={`${match.index}-${reading}`}>{match[2]}<rt>{reading}</rt></ruby>);
    start = pattern.lastIndex;
  }
  if (start < input.length) result.push(input.slice(start));
  return result;
}

function pathsToSvg(paths: string[], className = '') {
  if (!paths.length) return null;
  return <svg className={className} viewBox="0 0 109 109" role="img" aria-label={`${paths.length} stroke paths`}>
    <path className="guide-grid" d="M54.5 4v101M4 54.5h101M18.8 18.8l71.4 71.4m0-71.4L18.8 90.2" />
    {paths.map((path, index) => <path className="stroke-path" key={`${index}-${path.length}`} d={path} pathLength="1" style={{ animationDelay: `${index * 520}ms` }} />)}
  </svg>;
}

function newSet(name: string, now: number): StudySet {
  return { id: `set-${now}-${Math.random().toString(36).slice(2, 7)}`, name, kanji: [], words: [], createdAt: now };
}

function makeQuiz(items: RuntimeKanji[], count: number): QuizQuestion[] {
  const available = items.filter((item) => item.meaning && (item.on.length || item.kun.length));
  const mixed = [...available].sort((a, b) => ((a.code * 2654435761) >>> 0) - ((b.code * 2654435761) >>> 0));
  return mixed.slice(0, Math.min(count, mixed.length)).map((item, index) => {
    const type: QuizQuestion['type'] = index % 2 ? 'reading' : 'meaning';
    const correct = type === 'meaning' ? item.meaning! : [...item.on, ...item.kun][0] ?? '';
    const options = [correct];
    for (const candidate of mixed.slice(index + 1).concat(mixed.slice(0, index))) {
      const option = type === 'meaning' ? candidate.meaning : [...candidate.on, ...candidate.kun][0];
      if (option && !options.includes(option)) options.push(option);
      if (options.length === 4) break;
    }
    return { character: item.character, prompt: type === 'meaning' ? item.character : item.meaning!, correct,
      options: options.sort((a, b) => a.localeCompare(b)), type };
  });
}

export default function App() {
  const [screen, setScreen] = useState<Screen>('home');
  const [catalog, setCatalog] = useState<RuntimeCatalog | null>(null);
  const [loadError, setLoadError] = useState('');
  const [appState, setAppState] = useState<StudyState>(() => loadStudyState());
  const [selectedCharacter, setSelectedCharacter] = useState('日');
  const [selectedWordId, setSelectedWordId] = useState<number | null>(null);
  const [detail, setDetail] = useState<KanjiDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [kanjiQuery, setKanjiQuery] = useState('');
  const [wordQuery, setWordQuery] = useState('');
  const [jlptFilter, setJlptFilter] = useState(0);
  const [gradeFilter, setGradeFilter] = useState(0);
  const [strokeFilter, setStrokeFilter] = useState(0);
  const [radicalFilter, setRadicalFilter] = useState(0);
  const [savedOnly, setSavedOnly] = useState(false);
  const [commonWordsOnly, setCommonWordsOnly] = useState(false);
  const [visibleCount, setVisibleCount] = useState(72);
  const [words, setWords] = useState<RuntimeWord[] | null>(null);
  const [wordLoading, setWordLoading] = useState(false);
  const [dataError, setDataError] = useState('');
  const [outlier, setOutlier] = useState<RuntimeOutlier | null>(null);
  const [libraryQuery, setLibraryQuery] = useState('');
  const [libraryVisible, setLibraryVisible] = useState(100);
  const [wordVisible, setWordVisible] = useState(80);
  const [pickedOutlier, setPickedOutlier] = useState<OutlierReference | null>(null);
  const [cardMode, setCardMode] = useState<StudyMode>('cards');
  const [queue, setQueue] = useState<string[]>([]);
  const [queueIndex, setQueueIndex] = useState(0);
  const [revealed, setRevealed] = useState(false);
  const [sessionCorrect, setSessionCorrect] = useState(0);
  const [quizQuestions, setQuizQuestions] = useState<QuizQuestion[]>([]);
  const [quizAnswer, setQuizAnswer] = useState<string | null>(null);
  const [drawnPaths, setDrawnPaths] = useState<string[]>([]);
  const [writingHint, setWritingHint] = useState(false);
  const [strokePlaying, setStrokePlaying] = useState(false);
  const [setName, setSetName] = useState('');
  const [activeSetId, setActiveSetId] = useState<string | null>(null);
  const [kanaQuery, setKanaQuery] = useState('');
  const [radicalQuery, setRadicalQuery] = useState('');
  const [radicalStrokeFilter, setRadicalStrokeFilter] = useState(0);
  const [sessionStart, setSessionStart] = useState(0);
  const [sessionMessage, setSessionMessage] = useState('');
  const [topSearch, setTopSearch] = useState('');
  const fileRef = useRef<HTMLInputElement>(null);
  const kanjiSearchRef = useRef<HTMLInputElement>(null);

  useEffect(() => { loadRuntimeCatalog().then(setCatalog).catch((error: Error) => setLoadError(error.message)); }, []);
  useEffect(() => { persistStudyState(appState); }, [appState]);
  useEffect(() => {
    const shortcut = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault(); openScreen('kanji'); window.setTimeout(() => kanjiSearchRef.current?.focus(), 0);
      }
    };
    window.addEventListener('keydown', shortcut);
    return () => window.removeEventListener('keydown', shortcut);
  }, []);

  const entries = catalog?.kanji ?? [];
  const selected = entries.find((item) => item.character === selectedCharacter) ?? entries[0];
  const currentCard = entries.find((item) => item.character === queue[queueIndex]);
  const due = useMemo(() => dueKanji(appState), [appState]);
  const doneToday = useMemo(() => todayNewKanji(appState), [appState]);
  const favorites = useMemo(() => new Set(appState.favorites), [appState.favorites]);
  const filteredKanji = useMemo(() => {
    if (!catalog) return [];
    const result = searchRuntimeKanji(entries, kanjiQuery, {
      jlpt: jlptFilter || undefined, level: gradeFilter || undefined, strokeCount: strokeFilter || undefined,
      radical: radicalFilter || undefined, favorites, savedOnly, dueOnly: screen === 'study' && queue.length === 0,
      dueCharacters: due,
    });
    return result.sort((a, b) => (a.sequence ?? 99999) - (b.sequence ?? 99999));
  }, [catalog, entries, kanjiQuery, jlptFilter, gradeFilter, strokeFilter, radicalFilter, favorites, savedOnly, screen, queue.length, due]);
  const selectedWord = useMemo(() => words?.find((word) => word.id === selectedWordId) ?? null, [words, selectedWordId]);
  const wordResults = useMemo(() => words ? searchRuntimeWords(words, wordQuery, {
    jlpt: jlptFilter || undefined, commonOnly: commonWordsOnly,
  }) : [], [words, wordQuery, jlptFilter, commonWordsOnly]);
  const activeSet = appState.sets.find((set) => set.id === activeSetId) ?? null;
  const currentQuestion = quizQuestions[queueIndex];
  const outlierResults = useMemo(() => (outlier?.references ?? []).filter((item) =>
    normalizeSearch(`${item.name} ${item.text}`).includes(normalizeSearch(libraryQuery))), [outlier, libraryQuery]);

  useEffect(() => {
    if (!selected) return;
    let current = true;
    setDetail(null); setDetailLoading(true);
    loadKanjiDetail(selected).then((value) => { if (current) setDetail(value); })
      .catch((error: Error) => { if (current) setDataError(error.message); })
      .finally(() => { if (current) setDetailLoading(false); });
    return () => { current = false; };
  }, [selected?.character]);

  const ensureWords = async () => {
    if (words) return;
    setWordLoading(true); setDataError('');
    try { setWords(await loadRuntimeWords()); }
    catch (error) { setDataError((error as Error).message); }
    finally { setWordLoading(false); }
  };

  const openScreen = (next: Screen) => {
    setScreen(next); setVisibleCount(72); setSessionMessage(''); setDataError('');
    if (next === 'words') void ensureWords();
    if (next === 'sets' && (appState.favoriteWords.length || appState.sets.some((set) => set.words.length))) void ensureWords();
    if (next === 'outlier' && !outlier) loadRuntimeOutlier().then(setOutlier).catch((error: Error) => setDataError(error.message));
  };

  const toggleFavorite = (character: string) => setAppState((current) => ({ ...current,
    favorites: current.favorites.includes(character) ? current.favorites.filter((item) => item !== character) : [...current.favorites, character],
  }));
  const toggleWordFavorite = (id: number) => setAppState((current) => ({ ...current,
    favoriteWords: current.favoriteWords.includes(id) ? current.favoriteWords.filter((item) => item !== id) : [...current.favoriteWords, id],
  }));
  const addToSet = (type: 'kanji' | 'word', id: string | number) => {
    if (appState.sets.length === 0) {
      const created = newSet('My first set', Date.now());
      setAppState((state) => ({ ...state, sets: [{ ...created,
        kanji: type === 'kanji' ? [String(id)] : [], words: type === 'word' ? [Number(id)] : [],
      }, ...state.sets] }));
      setActiveSetId(created.id); setScreen('sets'); return;
    }
    const target = activeSetId ? appState.sets.find((item) => item.id === activeSetId) : appState.sets[0];
    if (!target) return;
    setAppState((state) => ({ ...state, sets: state.sets.map((item) => item.id !== target.id ? item : {
      ...item, kanji: type === 'kanji' && !item.kanji.includes(String(id)) ? [...item.kanji, String(id)] : item.kanji,
      words: type === 'word' && !item.words.includes(Number(id)) ? [...item.words, Number(id)] : item.words,
    }) }));
    setSessionMessage(`Added to ${target.name}`); window.setTimeout(() => setSessionMessage(''), 1800);
  };

  const createSet = () => {
    const name = setName.trim(); if (!name) return;
    const created = newSet(name, Date.now());
    setAppState((state) => ({ ...state, sets: [created, ...state.sets] }));
    setSetName(''); setActiveSetId(created.id);
  };

  const finishSession = (correct: number, total: number, mode: StudyMode) => {
    const now = Date.now();
    const record: SessionRecord = { id: `${now}`, mode, startedAt: sessionStart || now, finishedAt: now, total, correct };
    setAppState((state) => ({ ...state, sessions: [record, ...state.sessions].slice(0, 200) }));
    setSessionMessage(`${correct} of ${total} complete`);
    setScreen('home'); setQueue([]); setQuizQuestions([]); setQuizAnswer(null); setRevealed(false);
  };

  const startCards = (mode: StudyMode, requested?: string[]) => {
    if (!catalog) return;
    let next: string[];
    if (requested) {
      if (!requested.length) { setSessionMessage('No reviews are due yet'); return; }
      next = requested;
    }
    else {
      const reviewed = new Set(Object.values(appState.reviews).filter((item) => item.type === 'kanji').map((item) => item.id));
      const fresh = [...entries].filter((item) => !reviewed.has(item.character) && !doneToday.has(item.character))
        .sort((a, b) => (b.levels.jlpt ?? 0) - (a.levels.jlpt ?? 0) || (a.sequences.jlpt ?? a.sequence ?? 99999) - (b.sequences.jlpt ?? b.sequence ?? 99999))
        .slice(0, appState.preferences.dailyNew).map((item) => item.character);
      next = [...due, ...fresh];
      if (!next.length) next = entries.slice(0, 20).map((item) => item.character);
    }
    setQueue(next); setQueueIndex(0); setCardMode(mode); setSessionCorrect(0); setRevealed(false);
    setQuizAnswer(null); setSessionStart(Date.now()); setScreen('study'); setSessionMessage('');
    if (mode === 'quiz') setQuizQuestions(makeQuiz(next.flatMap((character) => { const found = entries.find((item) => item.character === character); return found ? [found] : []; }), 10));
  };

  const answerCard = (rating: Rating) => {
    if (!currentCard) return;
    setAppState((state) => recordReview(state, 'kanji', currentCard.character, rating));
    const correct = rating === 'good' || rating === 'easy';
    if (correct) setSessionCorrect((count) => count + 1);
    const next = queueIndex + 1;
    if (next >= queue.length) finishSession(sessionCorrect + (correct ? 1 : 0), queue.length, cardMode);
    else { setQueueIndex(next); setRevealed(false); setQuizAnswer(null); setDrawnPaths([]); }
  };

  const answerQuiz = (answer: string) => {
    if (quizAnswer || !currentQuestion) return;
    setQuizAnswer(answer);
    const isCorrect = answer === currentQuestion.correct;
    if (isCorrect) setSessionCorrect((count) => count + 1);
    setAppState((state) => recordReview(state, 'kanji', currentQuestion.character, isCorrect ? 'good' : 'again'));
  };

  const continueQuiz = () => {
    const next = queueIndex + 1;
    if (next >= quizQuestions.length) finishSession(sessionCorrect, quizQuestions.length, 'quiz');
    else { setQueueIndex(next); setQuizAnswer(null); }
  };

  const exportState = () => {
    const blob = new Blob([JSON.stringify(appState, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob); const anchor = document.createElement('a');
    anchor.href = url; anchor.download = `kanjiforge-backup-${new Date().toISOString().slice(0, 10)}.json`; anchor.click();
    URL.revokeObjectURL(url);
  };

  const importState = async (file?: File) => {
    if (!file) return;
    try {
      const imported = JSON.parse(await file.text()) as Partial<StudyState>;
      if (imported.version !== 2 || !Array.isArray(imported.favorites) || !Array.isArray(imported.sets) || !imported.reviews) {
        throw new Error('This file is not a KanjiForge backup.');
      }
      setAppState((current) => ({ ...current,
        favorites: [...new Set([...current.favorites, ...(imported.favorites as string[])])],
        favoriteWords: [...new Set([...current.favoriteWords, ...((imported.favoriteWords as number[]) ?? [])])],
        preferences: { ...current.preferences, ...imported.preferences },
        sets: [...current.sets, ...((imported.sets as StudySet[]) ?? []).filter((candidate) => !current.sets.some((item) => item.id === candidate.id))],
        reviews: { ...current.reviews, ...(imported.reviews as StudyState['reviews']) },
        sessions: [...current.sessions, ...((imported.sessions as SessionRecord[]) ?? [])].slice(0, 200),
      }));
      setSessionMessage('Backup merged');
    } catch (error) { setSessionMessage((error as Error).message); }
  };

  if (loadError) return <main className="load-screen" role="alert"><div className="load-card"><span className="monogram">漢</span><h1>KanjiForge</h1><p>{loadError}</p><button className="button primary" onClick={() => location.reload()}>Reload library</button></div></main>;
  if (!catalog || !selected) return <main className="load-screen" role="status"><div className="load-card"><span className="monogram">漢</span><h1>KanjiForge</h1><p>Opening the local kanji library…</p><div className="loading-line" /></div></main>;

  const dueCount = due.size;
  const reviewedToday = doneToday.size;

  return <div className="app-shell">
    <header className="topbar">
      <button className="wordmark" onClick={() => openScreen('home')}><span className="mark">漢</span><span>kanji<span className="wordmark-light">forge</span></span></button>
      <div className="topbar-center"><span className="status-dot" />LOCAL STUDY LIBRARY <i /> 7,071 CHARACTERS</div>
      <label className="top-search"><Search size={16} /><input aria-label="Search kanji" placeholder="Find a kanji" value={topSearch} onChange={(event) => setTopSearch(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') { setKanjiQuery(topSearch); openScreen('kanji'); } }} /><kbd>↵</kbd></label>
      <button className="avatar-button" onClick={() => openScreen('preferences')} aria-label="Open settings">日</button>
    </header>
    <div className="app-layout">
      <aside className="sidebar" aria-label="Main navigation">
        <span className="side-label">LIBRARY</span>
        {navItems.slice(0, 5).map(({ id, title, icon: Icon }) => <button key={id} className={`nav-item ${screen === id ? 'active' : ''}`} onClick={() => openScreen(id)}><Icon size={17} /><span>{title}</span>{id === 'study' && dueCount > 0 && <b className="nav-badge">{dueCount}</b>}</button>)}
        <span className="side-label secondary-label">REFERENCE</span>
        {navItems.slice(5).map(({ id, title, icon: Icon }) => <button key={id} className={`nav-item ${screen === id ? 'active' : ''}`} onClick={() => openScreen(id)}><Icon size={17} /><span>{title}</span></button>)}
        <div className="sidebar-footer"><span className="mini-seal">一</span><p>Small steps,<br />lasting memory.</p><small>漢字は、一歩ずつ。</small></div>
      </aside>

      <main className="main-content">
        {screen === 'home' && <>
          <PageHeading eyebrow="YOUR STUDY DESK" title={<>Make today<br /><em>count.</em></>} copy="A little practice, a little progress. Pick up where you left off." />
          {sessionMessage && <div className="toast" role="status"><Check size={15} />{sessionMessage}<button onClick={() => setSessionMessage('')} aria-label="Dismiss"><X size={14} /></button></div>}
          <section className="today-grid">
            <div className="today-card featured-card"><div className="card-kicker"><CalendarDays size={14} /> TODAY'S PRACTICE</div><div className="today-numbers"><strong>{dueCount}</strong><span>due for review</span></div><div className="progress-track"><span style={{ width: `${Math.min(100, reviewedToday / Math.max(1, appState.preferences.dailyNew) * 100)}%` }} /></div><div className="progress-copy">{reviewedToday} / {appState.preferences.dailyNew} new characters today</div><button className="button primary" onClick={() => startCards('cards')}><Play size={15} fill="currentColor" /> {dueCount ? 'Continue study' : 'Start a study session'} <ArrowRight size={15} /></button><div className="featured-foot"><Clock3 size={13} /> About 5 minutes · self-paced</div></div>
            <div className="today-card quick-card"><div className="card-kicker">QUICK PRACTICE</div><button className="quick-row" onClick={() => startCards('quiz')}><span className="quick-icon"><CircleHelp size={18} /></span><span><b>Multiple choice</b><small>Readings and meanings</small></span><ChevronRight size={17} /></button><button className="quick-row" onClick={() => startCards('writing')}><span className="quick-icon"><Feather size={18} /></span><span><b>Practice writing</b><small>Draw kanji strokes</small></span><ChevronRight size={17} /></button><button className="quick-row" onClick={() => openScreen('kanji')}><span className="quick-icon"><Search size={18} /></span><span><b>Explore kanji</b><small>Browse the full library</small></span><ChevronRight size={17} /></button></div>
            <div className="today-card stats-card"><div className="card-kicker">YOUR PROGRESS</div><div className="stat-row"><strong>{Object.keys(appState.reviews).length.toLocaleString()}</strong><span>characters practiced</span><span className="stat-glyph">字</span></div><div className="stat-divider" /><div className="stat-bottom"><div><strong>{appState.favorites.length}</strong><small>saved</small></div><div><strong>{appState.sessions.length}</strong><small>sessions</small></div><div><strong>{appState.sets.length}</strong><small>sets</small></div></div><button className="text-link" onClick={() => openScreen('progress')}>View your progress <ArrowRight size={14} /></button></div>
          </section>
          <section className="home-lower"><div><div className="section-heading"><div><span className="eyebrow">A CHARACTER TO EXPLORE</span><h2>Start with something familiar.</h2></div><button className="text-link" onClick={() => openScreen('kanji')}>Browse all <ArrowRight size={14} /></button></div><div className="home-kanji-row">{entries.filter((item) => ['日', '人', '食', '学', '水', '時'].includes(item.character)).map((item) => <button key={item.character} className="home-kanji" onClick={() => { setSelectedCharacter(item.character); openScreen('kanji'); }}><span>{item.character}</span><small>{short(item.meaning, 22)}</small></button>)}</div></div><div className="quote-card"><span>今日</span><p>Little by little,<br />one learns the way.</p><small>一歩ずつ</small></div></section>
        </>}

        {screen === 'kanji' && <>
          <PageHeading eyebrow="CHARACTER LIBRARY" title={<>Find your next<br /><em>character.</em></>} copy="Search by kanji, reading, meaning, radical, or number of strokes." />
          <section className="browse-workspace">
            <div className="catalog-panel">
              <div className="catalog-heading"><div><h2>Kanji index</h2><span>{filteredKanji.length.toLocaleString()} characters</span></div><button className={`filter-toggle ${savedOnly ? 'is-on' : ''}`} onClick={() => setSavedOnly(!savedOnly)}><Bookmark size={14} fill={savedOnly ? 'currentColor' : 'none'} /> Saved</button></div>
              <label className="searchbox"><Search size={16} /><input ref={kanjiSearchRef} value={kanjiQuery} onChange={(event) => { setKanjiQuery(event.target.value); setVisibleCount(72); }} placeholder="Character, reading, meaning…" aria-label="Search kanji by character, reading, or meaning" />{kanjiQuery && <button onClick={() => setKanjiQuery('')} aria-label="Clear search"><X size={14} /></button>}<kbd>⌘ K</kbd></label>
              <div className="filter-row"><label><span>JLPT</span><select value={jlptFilter} onChange={(event) => setJlptFilter(Number(event.target.value))}><option value="0">All levels</option>{[1, 2, 3, 4, 5].map((level) => <option value={level} key={level}>N{level}</option>)}</select></label><label><span>Grade</span><select value={gradeFilter} onChange={(event) => setGradeFilter(Number(event.target.value))}><option value="0">All grades</option>{[1, 2, 3, 4, 5, 6, 8].map((grade) => <option value={grade} key={grade}>{grade === 8 ? 'Secondary' : `Grade ${grade}`}</option>)}</select></label><label><span>Strokes</span><select value={strokeFilter} onChange={(event) => setStrokeFilter(Number(event.target.value))}><option value="0">Any</option>{Array.from({ length: 30 }, (_, i) => i + 1).map((number) => <option value={number} key={number}>{number}</option>)}</select></label><label><span>Radical</span><select value={radicalFilter} onChange={(event) => setRadicalFilter(Number(event.target.value))}><option value="0">Any radical</option>{catalog.radicals.map((radical) => <option value={radical.code} key={radical.code}>{radical.character} · {radical.meaning}</option>)}</select></label></div>
              <div className="kanji-grid" aria-live="polite">{filteredKanji.slice(0, visibleCount).map((item, index) => <button key={item.character} className={`kanji-tile ${selected.character === item.character ? 'chosen' : ''}`} onClick={() => setSelectedCharacter(item.character)}><span className="tile-index">{String(index + 1).padStart(2, '0')}</span><span className="tile-char">{item.character}</span><span className="tile-meaning">{short(item.meaning, 24)}</span>{favorites.has(item.character) && <Bookmark className="tile-save" size={11} fill="currentColor" />}</button>)}{filteredKanji.length === 0 && <EmptyState title="No characters found" copy="Try a different reading, level, or meaning." />}</div>
              {filteredKanji.length > visibleCount && <button className="show-more" onClick={() => setVisibleCount((count) => count + 72)}>Show more characters <ChevronRight size={14} /> <small>{Math.min(72, filteredKanji.length - visibleCount)} more</small></button>}
              <div className="data-note"><Sparkles size={13} /> {catalog.kanji.length.toLocaleString()} records from the recovered 7.8.3 content database</div>
            </div>
            <KanjiDetailPanel item={selected} detail={detail} loading={detailLoading} favorite={favorites.has(selected.character)} onFavorite={() => toggleFavorite(selected.character)} onStudy={() => startCards('cards', [selected.character])} onWriting={() => startCards('writing', [selected.character])} onRadical={(code) => { setRadicalFilter(code); setKanjiQuery(''); }} onNext={(delta) => { const index = filteredKanji.findIndex((item) => item.character === selected.character); const next = filteredKanji[(index + delta + filteredKanji.length) % filteredKanji.length]; if (next) setSelectedCharacter(next.character); }} onWord={(id) => { void ensureWords().then(() => { setSelectedWordId(id); setScreen('words'); }); }} onSet={() => addToSet('kanji', selected.character)} playing={strokePlaying} onPlay={() => setStrokePlaying((playing) => !playing)} />
          </section>
        </>}

        {screen === 'words' && <>
          <PageHeading eyebrow="DICTIONARY" title={<>Words in<br /><em>context.</em></>} copy="Browse dictionary entries linked to the recovered content catalog." />
          {sessionMessage && <div className="toast" role="status"><Check size={15} />{sessionMessage}<button onClick={() => setSessionMessage('')} aria-label="Dismiss"><X size={14} /></button></div>}
          <section className="word-browser"><div className="word-browser-top"><div><h2>Word search</h2><span>{words ? `${words.length.toLocaleString()} dictionary entries` : 'Loading the local dictionary'}</span></div><label className="word-search"><Search size={16} /><input value={wordQuery} onChange={(event) => setWordQuery(event.target.value)} placeholder="Written form, reading, romaji, or translation" aria-label="Search words" /></label></div>
            <div className="word-filters"><label>JLPT <select value={jlptFilter} onChange={(event) => setJlptFilter(Number(event.target.value))}><option value="0">Any level</option>{[1, 2, 3, 4, 5].map((level) => <option value={level} key={level}>N{level}</option>)}</select></label><label className="check-filter"><input type="checkbox" checked={commonWordsOnly} onChange={(event) => setCommonWordsOnly(event.target.checked)} /> Common words</label><span className="muted-inline">{wordLoading ? 'Preparing searchable words…' : `${wordResults.length.toLocaleString()} matches`}</span></div>
            {dataError && <p className="inline-error">{dataError}</p>}
            <div className="word-layout"><div className="word-list">{wordResults.slice(0, wordVisible).map((word) => <button key={word.id} className={`word-result ${selectedWordId === word.id ? 'selected' : ''}`} onClick={() => setSelectedWordId(word.id)}><span className="word-result-head"><b>{word.forms[0] || word.readings[0] || '—'}</b><small>{word.readings[0]}</small></span><span className="word-result-gloss">{short(word.glosses.join(' · '), 100)}</span><span className="word-result-tags">{word.common && <i>COMMON</i>}{word.jlpt ? <i>N{word.jlpt}</i> : null}</span></button>)}{!words && !wordLoading && <button className="button primary" onClick={() => void ensureWords()}>Load dictionary</button>}{wordLoading && <EmptyState title="Opening dictionary" copy="The local compressed index is loading." />}{words && wordResults.length === 0 && <EmptyState title="No matching words" copy="Try a kana reading or a shorter English phrase." />}{words && wordResults.length > wordVisible && <button className="show-more" onClick={() => setWordVisible((count) => count + 80)}>Show more words <ChevronRight size={14} /></button>}</div>
              <aside className="word-detail">{selectedWord ? <><div className="detail-top"><span>DICTIONARY ENTRY · {selectedWord.id}</span><button className={`save-button ${appState.favoriteWords.includes(selectedWord.id) ? 'saved' : ''}`} onClick={() => toggleWordFavorite(selectedWord.id)} aria-label="Save word"><Bookmark size={17} fill={appState.favoriteWords.includes(selectedWord.id) ? 'currentColor' : 'none'} /></button></div><h2 className="word-detail-title">{selectedWord.forms[0] || selectedWord.readings[0]}</h2><div className="word-detail-readings">{selectedWord.readings.join(' · ')}{selectedWord.romaji.length > 0 && <span>{selectedWord.romaji.slice(0, 2).join(' · ')}</span>}</div><ol className="gloss-list">{selectedWord.glosses.map((gloss, index) => <li key={`${index}-${gloss}`}>{gloss}</li>)}</ol><div className="tag-list">{selectedWord.tags.slice(0, 9).map((tag) => <span key={tag}>{tag}</span>)}</div><button className="button secondary full-width" onClick={() => addToSet('word', selectedWord.id)}><Plus size={15} /> Add to collection</button><button className="text-link" onClick={() => { const found = entries.find((item) => selectedWord.forms.some((form) => form.includes(item.character))); if (found) { setSelectedCharacter(found.character); openScreen('kanji'); } }}>Find linked kanji <ArrowRight size={14} /></button></> : <EmptyState title="Choose a word" copy="Select an entry to see its readings and meanings." />}</aside>
            </div>
            {wordResults.length > wordVisible && <p className="muted-inline center">Showing {wordVisible} of {wordResults.length.toLocaleString()} matches.</p>}
          </section>
        </>}

        {screen === 'study' && !currentCard && <>
          <PageHeading eyebrow="STUDY MODES" title={<>Practice with<br /><em>purpose.</em></>} copy="Choose what to practice and how you want to recall it." />
          {sessionMessage && <div className="toast" role="status"><Check size={15} />{sessionMessage}<button onClick={() => setSessionMessage('')} aria-label="Dismiss"><X size={14} /></button></div>}
          <div className="study-launch-grid"><StudyLaunch icon={<BookOpen />} title="Flashcards" copy="Reveal the character, then rate how well you remembered." count={`${dueCount} due · ${appState.preferences.dailyNew} new per day`} action="Start cards" onClick={() => startCards('cards')} /><StudyLaunch icon={<CircleHelp />} title="Multiple choice" copy="Check readings and meanings with a short quiz." count="10 questions · local scoring" action="Start quiz" onClick={() => startCards('quiz')} /><StudyLaunch icon={<Feather />} title="Practice writing" copy="Draw each kanji stroke, then compare it with the reference." count="Mouse · touch · pen" action="Draw kanji" onClick={() => startCards('writing')} /><StudyLaunch icon={<Clock3 />} title="Review queue" copy="Practice characters whose local review date has arrived." count={`${dueCount} characters ready`} action="Review due" onClick={() => startCards('cards', [...due])} disabled={!dueCount} /></div>
          <div className="settings-inline"><label>Card prompt<select value={appState.preferences.cardDirection} onChange={(event) => setAppState((state) => ({ ...state, preferences: { ...state.preferences, cardDirection: event.target.value as StudyState['preferences']['cardDirection'] } }))}><option value="meaning">Meaning → kanji</option><option value="reading">Reading → kanji</option><option value="kanji">Kanji → meaning</option></select></label><button className="text-link" onClick={() => openScreen('preferences')}>More study settings <ArrowRight size={14} /></button></div>
        </>}

        {screen === 'study' && currentCard && <StudySession mode={cardMode} item={currentCard} question={currentQuestion} index={queueIndex} total={cardMode === 'quiz' ? quizQuestions.length : queue.length} revealed={revealed} answer={quizAnswer} correctCount={sessionCorrect} cardDirection={appState.preferences.cardDirection} onReveal={() => setRevealed(true)} onRate={answerCard} onQuizAnswer={answerQuiz} onQuizContinue={continueQuiz} onExit={() => { setScreen('study'); setQueue([]); setQuizQuestions([]); }} onDraw={(path) => setDrawnPaths((paths) => [...paths, path])} drawnPaths={drawnPaths} writingHint={writingHint} setWritingHint={setWritingHint} onUndo={() => setDrawnPaths((paths) => paths.slice(0, -1))} onClear={() => setDrawnPaths([])} onWritingCheck={(rating) => answerCard(rating)} />}

        {screen === 'sets' && <>
          <PageHeading eyebrow="YOUR LIBRARY" title={<>Keep your<br /><em>favorites close.</em></>} copy="Save characters and words, then arrange them into study collections." />
          <div className="sets-create"><label><span>NEW COLLECTION</span><input value={setName} onChange={(event) => setSetName(event.target.value)} onKeyDown={(event) => event.key === 'Enter' && createSet()} placeholder="Name this collection" /></label><button className="button primary" onClick={createSet} disabled={!setName.trim()}><Plus size={15} /> Create collection</button></div>
          {appState.sets.length > 0 && <div className="set-tabs">{appState.sets.map((set) => <button key={set.id} className={activeSetId === set.id ? 'selected' : ''} onClick={() => setActiveSetId(set.id)}>{set.name}<small>{set.kanji.length + set.words.length}</small></button>)}</div>}
          {activeSet ? <div className="collection-content"><div className="collection-heading"><div><span className="eyebrow">COLLECTION</span><h2>{activeSet.name}</h2><p>{activeSet.kanji.length} kanji · {activeSet.words.length} words</p></div><div><button className="button secondary" onClick={() => startCards('cards', activeSet.kanji)} disabled={!activeSet.kanji.length}><Play size={14} /> Study set</button><button className="icon-button danger" onClick={() => { setAppState((state) => ({ ...state, sets: state.sets.filter((item) => item.id !== activeSet.id) })); setActiveSetId(null); }} aria-label="Delete collection"><X size={16} /></button></div></div><div className="collection-grid">{activeSet.kanji.map((character) => { const item = entries.find((entry) => entry.character === character); return item && <div className="collection-item" key={character}><button onClick={() => { setSelectedCharacter(character); openScreen('kanji'); }}><b>{character}</b><small>{short(item.meaning, 28)}</small></button><button className="remove-mini" onClick={() => setAppState((state) => ({ ...state, sets: state.sets.map((set) => set.id === activeSet.id ? { ...set, kanji: set.kanji.filter((value) => value !== character) } : set) }))} aria-label={`Remove ${character}`}>×</button></div>; })}{activeSet.words.map((id) => { const word = words?.find((entry) => entry.id === id); return <div className="collection-item" key={id}><button onClick={() => { void ensureWords().then(() => { setSelectedWordId(id); openScreen('words'); }); }}><b>{word?.forms[0] ?? `Word ${id}`}</b><small>{short(word?.glosses.join(' · '), 28)}</small></button></div>; })}{!activeSet.kanji.length && !activeSet.words.length && <EmptyState title="This collection is empty" copy="Use the bookmark or add button on a kanji or dictionary entry." />}</div></div> : <div className="collection-content"><div className="collection-heading"><div><span className="eyebrow">SAVED ITEMS</span><h2>Quick access</h2><p>{appState.favorites.length} kanji · {appState.favoriteWords.length} words</p></div><button className="button secondary" onClick={() => { setSavedOnly(true); openScreen('kanji'); }}>Browse saved kanji</button></div><div className="collection-grid">{appState.favorites.map((character) => { const item = entries.find((entry) => entry.character === character); return item && <button key={character} onClick={() => { setSelectedCharacter(character); openScreen('kanji'); }}><b>{character}</b><small>{short(item.meaning, 28)}</small></button>; })}{appState.favoriteWords.map((id) => { const word = words?.find((entry) => entry.id === id); return <button key={`word-${id}`} onClick={() => { void ensureWords().then(() => { setSelectedWordId(id); openScreen('words'); }); }}><b>{word?.forms[0] ?? `Word ${id}`}</b><small>{short(word?.glosses.join(' · ') ?? 'Saved word', 28)}</small></button>; })}{!appState.favorites.length && !appState.favoriteWords.length && <EmptyState title="Nothing saved yet" copy="Save a kanji or word to keep it handy." />}</div></div>}
        </>}

        {screen === 'kana' && <>
          <PageHeading eyebrow="HIRAGANA & KATAKANA" title={<>The sounds of<br /><em>Japanese.</em></>} copy="Explore the 148 kana entries with readings, origins, and stroke order." />
          <div className="kana-toolbar"><label className="searchbox"><Search size={16} /><input value={kanaQuery} onChange={(event) => setKanaQuery(event.target.value)} placeholder="Find a kana or reading" /></label><div className="kana-switch"><button className="selected">Hiragana & katakana</button></div></div>
          <div className="kana-grid">{catalog.kana.filter((item) => normalizeSearch(`${item.character ?? ''} ${item.reading} ${item.meaning ?? ''} ${item.example ?? ''}`).includes(normalizeSearch(kanaQuery))).map((item) => <article key={`${item.character}-${item.reading}`} className="kana-card"><div className="kana-glyph">{item.character}</div><div className="kana-name"><b>{item.reading}</b><small>{item.katakana ? 'Katakana' : 'Hiragana'}{item.archaic ? ' · archaic' : ''}</small></div><div className="kana-origin">{item.origin ? `from ${item.origin}` : item.meaning || '—'}</div><div className="kana-strokes">{pathsToSvg(item.strokePaths, 'kana-paths')}<span>{item.strokeCount} strokes</span></div><small className="kana-example">{item.example}{item.meaning ? ` · ${item.meaning}` : ''}</small></article>)}</div>
        </>}

        {screen === 'radicals' && <>
          <PageHeading eyebrow="COMPONENT LIBRARY" title={<>See how kanji<br /><em>fit together.</em></>} copy="Browse the recovered radical set and explore characters that share a component." />
          <div className="radical-toolbar"><label className="searchbox"><Search size={16} /><input value={radicalQuery} onChange={(event) => setRadicalQuery(event.target.value)} placeholder="Radical, reading, or meaning" /></label><label className="word-filters">Strokes <select value={radicalStrokeFilter} onChange={(event) => setRadicalStrokeFilter(Number(event.target.value))}><option value="0">Any</option>{Array.from({ length: 20 }, (_, i) => i + 1).map((item) => <option key={item} value={item}>{item}</option>)}</select></label></div>
          <div className="radical-grid">{catalog.radicals.filter((item) => (!radicalStrokeFilter || item.strokeCount === radicalStrokeFilter) && normalizeSearch(`${item.character ?? ''} ${item.meaning ?? ''} ${item.reading ?? ''}`).includes(normalizeSearch(radicalQuery))).map((item) => <button key={item.code} className="radical-card" onClick={() => { setRadicalFilter(item.code); openScreen('kanji'); }}><span>{item.character}</span><b>{item.meaning || '—'}</b><small>{item.reading || '—'} · {item.strokeCount ?? '—'} strokes</small></button>)}</div>
        </>}

        {screen === 'progress' && <>
          <PageHeading eyebrow="YOUR LEARNING HISTORY" title={<>Progress, one<br /><em>day at a time.</em></>} copy="Your review history stays on this device. These stats come from sessions in KanjiForge." />
          <div className="progress-overview"><Metric label="Practiced" value={Object.keys(appState.reviews).length} /><Metric label="Due now" value={dueCount} /><Metric label="Sessions" value={appState.sessions.length} /><Metric label="Saved kanji" value={appState.favorites.length} /></div>
          <section className="history-panel"><div className="panel-heading"><div><span className="eyebrow">RECENT SESSIONS</span><h2>Your study history</h2></div><span className="muted-inline">Stored locally</span></div>{appState.sessions.length ? appState.sessions.slice(0, 30).map((session) => <div className="history-row" key={session.id}><span className="history-icon">{session.mode === 'quiz' ? <CircleHelp size={16} /> : session.mode === 'writing' ? <Feather size={16} /> : <BookOpen size={16} />}</span><div><b>{session.mode === 'quiz' ? 'Multiple choice' : session.mode === 'writing' ? 'Writing challenge' : 'Flashcards'}</b><small>{new Date(session.finishedAt).toLocaleString()}</small></div><strong>{session.correct}/{session.total}</strong></div>) : <EmptyState title="No sessions yet" copy="Start a card session or quiz to begin your progress history." />}</section>
          <section className="history-panel source-breakdown"><div className="panel-heading"><div><span className="eyebrow">LIBRARY CONTENT</span><h2>What is in this build</h2></div><span className="muted-inline">Recovered 7.8.3 data</span></div><div className="source-counts"><span><b>{catalog.kanji.length.toLocaleString()}</b> kanji</span><span><b>{catalog.radicals.length}</b> radicals</span><span><b>{catalog.kana.length}</b> kana</span><span><b>218,764</b> dictionary entries</span><span><b>16,277</b> sentences</span><span><b>87,950</b> example names</span></div><p className="source-note">Database tables are used as recovered. KLC text and SRS difficulty fields remain encoded; the Outlier binary tables are excluded. The local scheduler is independent of the original app.</p></section>
        </>}

        {screen === 'outlier' && <>
          <PageHeading eyebrow="REFERENCE DICTIONARY" title={<>Kanji structure,<br /><em>in detail.</em></>} copy="Outlier reference articles recovered as readable text. Code-keyed binary entries remain unavailable." />
          <label className="searchbox outlier-search"><Search size={16} /><input value={libraryQuery} onChange={(event) => { setLibraryQuery(event.target.value); setLibraryVisible(100); }} placeholder="Search concepts or character decomposition" aria-label="Search Outlier references" /></label>
          {dataError && <p className="inline-error">{dataError}</p>}
          {!outlier ? <div className="history-panel"><EmptyState title="Loading reference articles" copy="Reading locally bundled reference text." /></div> : <div className="outlier-layout"><div className="outlier-list"><div className="panel-heading"><div><span className="eyebrow">KEY CONCEPTS & ARTICLES</span><h2>{outlierResults.length.toLocaleString()} references</h2></div></div>{outlierResults.slice(0, libraryVisible).map((item) => <button className={pickedOutlier?.id === item.id ? 'selected' : ''} key={item.id} onClick={() => setPickedOutlier(item)}><b>{item.name.replace(/\.html?$/i, '').replace(/[-_]/g, ' ')}</b><small>{short(item.text, 120)}</small></button>)}{outlierResults.length > libraryVisible && <button className="show-more" onClick={() => setLibraryVisible((count) => count + 100)}>Show more articles <ChevronRight size={14} /></button>}</div><article className="outlier-article">{pickedOutlier ? <><span className="eyebrow">{pickedOutlier.keyConcept ? 'KEY CONCEPT' : 'REFERENCE'}</span><h2>{pickedOutlier.name.replace(/\.html?$/i, '').replace(/[-_]/g, ' ')}</h2><p>{pickedOutlier.text}</p>{pickedOutlier.ancientForm && <div className="ancient-form">Ancient form <b>{pickedOutlier.ancientForm}</b></div>}</> : <EmptyState title="Choose an article" copy="Select a concept to read its recovered reference text." />}</article></div>}
          {outlier && outlier.characters.some((item) => item.drawing) && <details className="outlier-drawings"><summary>Recovered component diagrams <span>{outlier.characters.filter((item) => item.drawing).length}</span></summary><div className="outlier-drawing-grid">{outlier.characters.filter((item) => item.drawing).slice(0, 36).map((item) => <article key={item.id}><b>{item.name}</b><svg viewBox={item.drawing!.viewBox} aria-label={item.name}>{item.drawing!.paths.map((path, index) => <path key={index} d={path} />)}</svg></article>)}</div></details>}
        </>}

        {screen === 'preferences' && <>
          <PageHeading eyebrow="YOUR SETTINGS" title={<>Make it work<br /><em>for you.</em></>} copy="Set a comfortable pace and keep a portable copy of your learning state." />
          <div className="preferences-layout"><section className="settings-panel"><div className="panel-heading"><div><span className="eyebrow">STUDY RHYTHM</span><h2>Practice preferences</h2></div></div><label className="setting-row"><span><b>New characters each day</b><small>How many unseen characters to add to a session.</small></span><select value={appState.preferences.dailyNew} onChange={(event) => setAppState((state) => ({ ...state, preferences: { ...state.preferences, dailyNew: Number(event.target.value) } }))}>{[5, 10, 15, 20, 30].map((item) => <option key={item} value={item}>{item}</option>)}</select></label><label className="setting-row"><span><b>Card prompt</b><small>Choose what appears before you reveal a character.</small></span><select value={appState.preferences.cardDirection} onChange={(event) => setAppState((state) => ({ ...state, preferences: { ...state.preferences, cardDirection: event.target.value as StudyState['preferences']['cardDirection'] } }))}><option value="meaning">Meaning → kanji</option><option value="reading">Reading → kanji</option><option value="kanji">Kanji → meaning</option></select></label><div className="setting-row"><span><b>Audio</b><small>Reference audio files were not recovered for this browser build.</small></span><span className="muted-inline">Unavailable</span></div></section><section className="settings-panel"><div className="panel-heading"><div><span className="eyebrow">BACKUP & PROVENANCE</span><h2>Your data stays yours</h2></div></div><p className="setting-intro">Export your favorites, collections, preferences, and local review history to a versioned JSON file. Import merges it with this browser's current state.</p><div className="backup-actions"><button className="button primary" onClick={exportState}><Download size={15} /> Export backup</button><button className="button secondary" onClick={() => fileRef.current?.click()}><Upload size={15} /> Import and merge</button><input ref={fileRef} type="file" accept="application/json,.json" hidden onChange={(event) => { void importState(event.target.files?.[0]); event.currentTarget.value = ''; }} /></div><h3>Content sources</h3><p className="setting-intro">Readable records come from the supplied Kanji Study 7.8.3 runtime database snapshot. The captured databases and transformed local data remain on this machine. The KLC text, SRS binary fields, and sparse Outlier BLOBs are not decoded.</p><p className="setting-intro">KANJIDIC2 and JMdict New Generation are available as separate EDRDG-licensed fallback sources. KanjiVG stroke data is separately licensed. Review state is generated by this browser app and does not reproduce the reference app's scheduler.</p><a className="text-link" href="licenses/EDRDG-General-Dictionary-License.html" target="_blank" rel="noreferrer">Dictionary licenses and acknowledgements <ArrowRight size={14} /></a></section></div>
        </>}

        <footer className="footer"><span>漢字は、一歩ずつ。</span><span>RECOVERED CONTENT DATABASE · LOCAL STUDY STATE <i>·</i> <button onClick={() => openScreen('preferences')}>SOURCES & BACKUP</button></span></footer>
      </main>
    </div>
  </div>;
}

function PageHeading({ eyebrow, title, copy }: { eyebrow: string; title: ReactNode; copy: string }) {
  return <section className="intro-row"><div><div className="eyebrow">{eyebrow}<span className="eyebrow-line" /></div><h1>{title}</h1><p className="intro-copy">{copy}</p></div><div className="daily-mark"><span>今日</span><small>one day<br />at a time</small></div></section>;
}

function EmptyState({ title, copy }: { title: string; copy: string }) {
  return <div className="empty-state"><CircleHelp size={20} /><b>{title}</b><span>{copy}</span></div>;
}

function KanjiDetailPanel({ item, detail, loading, favorite, onFavorite, onStudy, onWriting, onRadical, onNext, onWord, onSet, playing, onPlay }: {
  item: RuntimeKanji; detail: KanjiDetail | null; loading: boolean; favorite: boolean; onFavorite: () => void;
  onStudy: () => void; onWriting: () => void; onRadical: (code: number) => void; onNext: (delta: number) => void;
  onWord: (id: number) => void; onSet: () => void; playing: boolean; onPlay: () => void;
}) {
  const [tab, setTab] = useState<'overview' | 'examples' | 'sentences' | 'names'>('overview');
  useEffect(() => setTab('overview'), [item.character]);
  return <aside className="detail-card">
    <div className="detail-top"><span>CHARACTER STUDY · {item.sequence ?? item.code}</span><div className="detail-actions"><button className={`save-button ${favorite ? 'saved' : ''}`} onClick={onFavorite} aria-label={favorite ? 'Remove from saved' : 'Save character'}><Bookmark size={17} fill={favorite ? 'currentColor' : 'none'} /></button><button className="save-button" onClick={onSet} aria-label="Add to collection"><Plus size={17} /></button><button className="save-button" onClick={() => onNext(-1)} aria-label="Previous character"><ChevronLeft size={16} /></button><button className="save-button" onClick={() => onNext(1)} aria-label="Next character"><ChevronRight size={16} /></button></div></div>
    <div className="specimen"><span className="specimen-side">STROKE STUDY</span>{pathsToSvg(item.strokePaths, `specimen-svg ${playing ? 'playing' : ''}`) ?? <span className="specimen-char">{item.character}</span>}<span className="specimen-count">{item.strokeCount ?? '—'} strokes</span><span className="specimen-stamp">漢字</span></div>
    <div className="specimen-controls"><button onClick={onPlay}>{playing ? <Pause size={13} /> : <Play size={13} />} {playing ? 'Stop strokes' : 'Play stroke order'}</button><button className="practice-writing" onClick={onWriting} aria-label={`Practice drawing ${item.character}`}><Feather size={14} /> Practice strokes</button></div>
    <h2 className="detail-meaning">{item.meaning || item.translation || 'Meaning unavailable in source'}</h2>
    {item.translation && item.meaning && <p className="detail-translation">{item.translation}</p>}
    <div className="reading-block"><div><span className="reading-label">ON READING</span><p>{item.on.length ? item.on.join(' · ') : '—'}</p></div><div><span className="reading-label">KUN READING</span><p>{item.kun.length ? item.kun.join(' · ') : '—'}</p></div></div>
    {!!item.nanori.length && <div className="detail-extra"><span className="reading-label">NAME READINGS</span><p>{item.nanori.join(' · ')}</p></div>}
    <div className="source-facts">{item.levels.jlpt && <span>JLPT N{item.levels.jlpt}</span>}{item.levels.jouyou && <span>GRADE {item.levels.jouyou}</span>}{item.levels.frequency && <span>FREQ {item.sequences.frequency}</span>}{item.radicals.slice(0, 3).map((radical) => <button key={radical.code} onClick={() => onRadical(radical.code)}>{radical.character} · {radical.meaning}</button>)}</div>
    {item.decomposition && <div className="detail-extra"><span className="reading-label">DECOMPOSITION</span><p>{item.decomposition}</p></div>}
    <div className="detail-tabs" role="tablist" aria-label="Kanji details">{(['overview', 'examples', 'sentences', 'names'] as const).map((value) => <button role="tab" aria-selected={tab === value} className={tab === value ? 'active' : ''} onClick={() => setTab(value)} key={value}>{value === 'overview' ? 'Overview' : value === 'examples' ? `Words ${detail?.words.length ? `· ${detail.words.length}` : ''}` : value === 'sentences' ? 'Sentences' : 'Names'}</button>)}</div>
    <div className="detail-tab-content">{loading ? <span className="muted-inline">Loading linked records…</span> : tab === 'overview' ? <><div className="overview-examples"><span className="section-title">EXAMPLE WORDS</span>{detail?.words.slice(0, 4).map((word) => <button className="word-row" key={word.id} onClick={() => onWord(word.id)}><span className="word-kanji">{word.forms[0] || word.readings[0]}</span><span className="word-reading">{word.readings[0]}</span><span className="word-meaning">{short(word.glosses[0], 44)}</span></button>)}</div><div className="overview-actions"><button onClick={onStudy}>Study this character <ChevronRight size={15} /></button></div></> : tab === 'examples' ? detail?.words.map((word) => <button className="word-row" key={word.id} onClick={() => onWord(word.id)}><span className="word-kanji">{word.forms[0] || word.readings[0]}</span><span className="word-reading">{word.readings[0]}</span><span className="word-meaning">{short(word.glosses[0], 44)}</span></button>) : tab === 'sentences' ? detail?.sentences.map((sentence) => <article className="sentence-row" key={sentence.id}><p lang="ja">{japaneseParts(sentence.text)}</p><small>{sentence.translation}</small></article>) : detail?.names.map((name) => <div className="name-row" key={name.id}><b>{name.name}</b><span>{name.reading}</span></div>)}
      {tab === 'sentences' && !detail?.sentences.length && <EmptyState title="No linked sentences" copy="No example sentence was linked to this character." />}
      {tab === 'names' && !detail?.names.length && <EmptyState title="No name examples" copy="No example names were linked to this character." />}
      {tab === 'examples' && !detail?.words.length && <EmptyState title="No linked words" copy="No dictionary entry was linked to this character." />}</div>
    <button className="detail-cta" onClick={onStudy}>Start a flashcard <ChevronRight size={16} /></button>
  </aside>;
}

function StudyLaunch({ icon, title, copy, count, action, onClick, disabled = false }: { icon: ReactNode; title: string; copy: string; count: string; action: string; onClick: () => void; disabled?: boolean }) {
  return <article className="study-launch"><span className="launch-icon">{icon}</span><span className="eyebrow">{count}</span><h2>{title}</h2><p>{copy}</p><button className="button secondary" onClick={onClick} disabled={disabled}>{action} <ArrowRight size={14} /></button></article>;
}

function StudySession({ mode, item, question, index, total, revealed, answer, correctCount, cardDirection, onReveal, onRate, onQuizAnswer, onQuizContinue, onExit, onDraw, drawnPaths, writingHint, setWritingHint, onUndo, onClear, onWritingCheck }: {
  mode: StudyMode; item: RuntimeKanji; question?: QuizQuestion; index: number; total: number; revealed: boolean;
  answer: string | null; correctCount: number; cardDirection: StudyState['preferences']['cardDirection']; onReveal: () => void; onRate: (rating: Rating) => void;
  onQuizAnswer: (answer: string) => void; onQuizContinue: () => void; onExit: () => void;
  onDraw: (path: string) => void; drawnPaths: string[]; writingHint: boolean; setWritingHint: (value: boolean) => void;
  onUndo: () => void; onClear: () => void; onWritingCheck: (rating: Rating) => void;
}) {
  const drawing = useRef<{ pointer: number; points: string[] } | null>(null);
  const [livePath, setLivePath] = useState('');
  const beginDraw = (event: PointerEvent<SVGSVGElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const point = `${(((event.clientX - rect.left) / rect.width) * 100).toFixed(1)},${(((event.clientY - rect.top) / rect.height) * 100).toFixed(1)}`;
    drawing.current = { pointer: event.pointerId, points: [point] }; event.currentTarget.setPointerCapture(event.pointerId); setLivePath(`M${point}`);
  };
  const moveDraw = (event: PointerEvent<SVGSVGElement>) => {
    if (!drawing.current || drawing.current.pointer !== event.pointerId) return;
    const rect = event.currentTarget.getBoundingClientRect();
    drawing.current.points.push(`${(((event.clientX - rect.left) / rect.width) * 100).toFixed(1)},${(((event.clientY - rect.top) / rect.height) * 100).toFixed(1)}`);
    setLivePath(`M${drawing.current.points.join(' L')}`);
  };
  const endDraw = (event: PointerEvent<SVGSVGElement>) => {
    if (!drawing.current || drawing.current.pointer !== event.pointerId) return;
    if (drawing.current.points.length > 1) onDraw(`M${drawing.current.points.join(' L')}`);
    drawing.current = null; setLivePath('');
  };
  const progress = Math.min(100, ((index + 1) / Math.max(1, total)) * 100);
  return <section className="session-view">
    <div className="session-top"><button className="back-link" onClick={onExit}><ArrowLeft size={15} /> Leave session</button><span>{mode === 'cards' ? 'FLASHCARDS' : mode === 'quiz' ? 'MULTIPLE CHOICE' : 'PRACTICE STROKES'}</span><span>{index + 1} / {total}</span></div><div className="session-progress"><span style={{ width: `${progress}%` }} /></div>
    <div className="session-title"><div className="eyebrow">A MOMENT OF PRACTICE <span className="eyebrow-line" /></div><h1>{mode === 'writing' ? <>Practice the strokes.<br /><em>Draw from memory.</em></> : mode === 'quiz' ? <>Trust what you<br /><em>have learned.</em></> : <>Meet the character.<br /><em>Recall what you know.</em></>}</h1></div>
    {mode === 'cards' && <div className="study-card"><div className="study-meta"><span>RECOGNITION · {String(index + 1).padStart(2, '0')}</span><button onClick={onExit} aria-label="Close study"><X size={18} /></button></div><div className="study-prompt"><span className="prompt-label">{cardDirection === 'meaning' ? 'MEANING' : cardDirection === 'reading' ? 'READING' : 'CHARACTER'}</span><strong>{cardDirection === 'meaning' ? item.meaning || item.translation || item.character : cardDirection === 'reading' ? [...item.on, ...item.kun][0] || item.meaning || item.character : item.character}</strong>{revealed && <><span className="answer-char">{item.character}</span><span className="answer-reading">{item.meaning}　·　{item.on.join(' · ')}　/　{item.kun.join(' · ')}</span>{item.strokePaths.length > 0 && pathsToSvg(item.strokePaths, 'answer-strokes')}</>}</div>{!revealed ? <button className="detail-cta reveal-button" onClick={onReveal}>Reveal answer <ChevronRight size={17} /></button> : <div className="rating-row"><span>How well did you recall it?</span><div>{(['again', 'hard', 'good', 'easy'] as Rating[]).map((rating) => <button key={rating} onClick={() => onRate(rating)}>{rating === 'again' ? 'Again' : rating === 'hard' ? 'Hard' : rating === 'good' ? 'Got it' : 'Easy'}</button>)}</div></div>}</div>}
    {mode === 'quiz' && question && <div className="quiz-card"><div className="quiz-prompt"><span className="prompt-label">{question.type === 'meaning' ? 'CHOOSE THE MEANING' : 'CHOOSE A READING'}</span><strong>{question.prompt}</strong></div><div className="quiz-options">{question.options.map((option) => <button key={option} className={answer === option ? option === question.correct ? 'correct' : 'incorrect' : ''} onClick={() => onQuizAnswer(option)} disabled={!!answer}><span>{option}</span>{answer === option && (option === question.correct ? <Check size={16} /> : <X size={16} />)}</button>)}</div>{answer && <div className="quiz-feedback"><span>{answer === question.correct ? 'Correct' : `The answer is ${question.correct}`}</span><button className="button primary" onClick={onQuizContinue}>Continue <ChevronRight size={14} /></button></div>}<div className="quiz-score">{correctCount} correct so far</div></div>}
    {mode === 'writing' && <div className="writing-card"><div className="writing-workspace"><div className="writing-head"><span className="prompt-label">DRAW EACH STROKE</span><span>{item.strokeCount ?? '—'} strokes</span></div><p className="writing-clue">{item.meaning || item.translation || 'Character'} · {item.on.slice(0, 2).join(' / ')}</p><div className="writing-canvas-wrap"><svg className="writing-canvas" viewBox="0 0 100 100" onPointerDown={beginDraw} onPointerMove={moveDraw} onPointerUp={endDraw} onPointerCancel={endDraw} aria-label="Drawing canvas"><path className="writing-grid" d="M50 2v96M2 50h96M16 16l68 68m0-68L16 84" />{writingHint && item.strokePaths.map((path, index) => <path className="writing-hint" d={path} key={`${index}-${path.length}`} />)}{drawnPaths.map((path, index) => <path className="drawing-line" d={path} key={`done-${index}`} />)}{livePath && <path className="drawing-line" d={livePath} />}</svg><span className="canvas-caption">Draw with mouse, touch, or pen</span></div><div className="writing-tools"><button onClick={onUndo} disabled={!drawnPaths.length}><RotateCcw size={14} /> Undo</button><button onClick={onClear} disabled={!drawnPaths.length}>Clear</button><button onClick={() => setWritingHint(!writingHint)} disabled={!item.strokePaths.length}><Eye size={14} /> {writingHint ? 'Hide stroke guide' : 'Show stroke guide'}</button>{!item.strokePaths.length && <span className="muted-inline">Stroke guide unavailable for this character</span>}</div></div><div className="writing-assessment"><span className="prompt-label">SELF-CHECK</span><h2>How did it feel?</h2><p>Compare your drawing with the sourced stroke paths. Your response sets the next review time.</p><div className="writing-reference">{pathsToSvg(item.strokePaths, 'writing-reference') ?? <span>Stroke guide unavailable</span>}<span>STROKE REFERENCE</span></div><div className="writing-ratings"><button onClick={() => onWritingCheck('again')}>Try again</button><button onClick={() => onWritingCheck('good')}>Got it</button></div></div></div>}
  </section>;
}

function Metric({ label, value }: { label: string; value: number }) {
  return <article className="metric-card"><span>{label}</span><strong>{value.toLocaleString()}</strong></article>;
}
