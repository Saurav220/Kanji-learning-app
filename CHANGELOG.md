# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Quiz Focus picker on Multiple-Choice Quiz launch**: Added `QuizFocusPickerDialog` for 1-tap selection across all five focuses (*Meanings*, *Readings*, *Vocabulary*, *Sentences*, and *Mixed questions*) with a *Last used* badge when launching from Today or Study.
- **Inline Quiz Focus selector**: Added expandable *Quiz focus* filter chips inside `StudyModePickerDialog` when *Multiple choice* is chosen from Sequence Sets or Collections.
- **Session header focus indicator**: Displayed the active Quiz Focus in the study session header (for example, `Multiple choice · Meanings`).
- **Domain glossary**: Added [GLOSSARY.md](./GLOSSARY.md) defining *Multiple-Choice Quiz* and *Quiz Focus*.
- **Unit tests**: Added coverage for explicit `quizPrompt` route round-trips, session `quizFocus` preference overrides, and missing-data fallback prompt resolution.

### Changed
- **Default Quiz Focus**: Changed the fresh-install and backup-import fallback default from `MIXED` to `MEANING`.
- **Synced preference memory**: Persisted the selected Quiz Focus back to `StudyPreferences` on quiz launch and relabeled the Settings dropdown to *Default quiz focus*.
- **Consistent terminology**: Renamed *Quick quiz* to *Multiple choice* on the Today and Progress screens.

### Fixed
- **Missing-reading prompt mismatch**: Updated `StudySessionController.resolveQuizPrompt` so kanji without *on* or *kun* readings fall back cleanly to `MEANING` instead of showing *"Which reading is correct?"* with a meaning answer.

## [1.0.0]

### Added
- Initial KanjiForge Android and web Japanese kanji learning app with offline reference catalog, flashcards, multiple-choice quizzes, writing practice, collections, and v2 JSON backup/restore.
