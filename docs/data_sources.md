# Data sources and provenance

The production UI uses independently licensed public data, not the four opaque `.db.zip` payloads from Kanji Study 7.8.3. Source snapshots and all generated hashes are recorded in [`src/data/generated/manifest.json`](../src/data/generated/manifest.json). Original archives and retrieval metadata are in [`data/sources`](../data/sources/README.md).

## Pinned snapshot

Retrieved 2026-10-18. The generated manifest is authoritative for exact source SHA-256, output hashes, counts, transformations, and rejection diagnostics.

| Dataset | Snapshot and imported fields | License and attribution | Counts |
| --- | --- | --- | --- |
| KANJIDIC2 database 2026-291 | Unicode character and codepoint, English meanings, Japanese on/kun/nanori readings, stroke count, grade, pre-2010 JLPT level, frequency, classical radical | EDRDG CC BY-SA 4.0 with additional EDRDG attribution conditions; see [license](https://www.edrdg.org/edrdg/newlic.html) and [project](https://www.edrdg.org/wiki/KANJIDIC_Project.html) | 13,108 kanji; 2,724 without English meanings in source |
| JMdict New Generation 1.10, English | Sequence ID, written forms, readings and their written-form restrictions, English glosses, part of speech, priority tags | EDRDG CC BY-SA 4.0 with additional EDRDG attribution conditions; see [license](https://www.edrdg.org/edrdg/newlic.html), [project](https://www.edrdg.org/wiki/JMdict-EDICT_Dictionary_Project.html), and [NG format notes](https://www.edrdg.org/jmdemo/web/doc/2026-03-xmlng.html) | 218,927 entries; 504,881 kanji-word links |
| KanjiVG main release 2026-07-14 | Ordered SVG stroke path data for exact matching source characters | © Ulrich Apel, CC BY-SA 3.0; see [license](https://creativecommons.org/licenses/by-sa/3.0/) and [project/release format](https://kanjivg.tagaini.net/kanjivg/index.html) | 6,702 characters; 79,905 paths |

## Transformations and boundaries

- Descriptive text is normalized to Unicode NFC. Kanji literals, vocabulary written forms/readings, and reading-restriction identifiers preserve their original codepoints: canonical normalization can merge distinct CJK compatibility characters and break source identity.
- Only English KANJIDIC2 meanings and JMdict glosses are imported. Restricted KANJIDIC2 dictionary descriptors and query/search codes are excluded.
- KANJIDIC2 records without an English meaning are retained with an empty meanings array; no meaning is inferred from vocabulary.
- KANJIDIC2's JLPT field is the pre-2010 1–4 level, not the current N1–N5 system; the UI labels it accordingly. EDRDG says no official character lists are available for the new levels.
- Vocabulary links are built from exact codepoints in each JMdict written form. Reading restrictions and `re_nokanji` are retained; unmatched vocabulary entries remain in `vocabulary.json`.
- KanjiVG paths are emitted only when the filename codepoint agrees with the primary embedded character and stroke numbering is contiguous. `kanji/06a45.svg` names U+6A45 (橅) but embeds 機; it is rejected and recorded in the manifest rather than attached to either character.
- The app loads compact prioritized examples; the full 53 MB JMdict JSON source-derived index is retained locally for reproducible data use and is not part of the initial browser bundle.
- No KLC course text, Outlier Dictionary data, or reference-app SRS records are included. The app makes no claim of compatibility with the reference app's scheduler.

## Bundled documentation and license copies

Script-free copies of the upstream project documentation and license terms are included in `public/licenses/` and linked from the app Sources view. They were retrieved on 2026-10-18; scripts, stylesheets, and remote assets were removed so these local reference pages do not execute source-site code or fetch external resources.

| Bundled file | Official source | SHA-256 |
| --- | --- | --- |
| `EDRDG-General-Dictionary-License.html` | [EDRDG terms](https://www.edrdg.org/edrdg/newlic.html) | `a119f24dcd5f85ce8d05d49f4c0a0b71d1d604e5d914c6f8ac46078284ce3f99` |
| `KANJIDIC2-Project.html` | [KANJIDIC2 project](https://www.edrdg.org/wiki/KANJIDIC_Project.html) | `f90f5214b1f89cd4d87a373d8aa709fa6de8a7b94fd06d3b37daa6c237ab5cef` |
| `JMdict-Project.html` | [JMdict project](https://www.edrdg.org/wiki/JMdict-EDICT_Dictionary_Project.html) | `2bde28cf3db27ea33811baf03ea7df0cc8dd22ee8912dc4afe626fe70990b72e` |
| `JMdict-NG-Format.html` | [JMdict NG format](https://www.edrdg.org/jmdemo/web/doc/2026-03-xmlng.html) | `a1a63f6c56ef383248d32d461649e496af49c3da43c83c81c48d0b94e6b122c3` |
| `KanjiVG-Project-and-License.html` | [KanjiVG project](https://kanjivg.tagaini.net/kanjivg/index.html) | `db149c6b95b4b4c8c9767da0a45c4e3d0c8f8c02ce0e63d84b2c060cc56e4d55` |
| `CC-BY-SA-4.0.html` | [CC BY-SA 4.0 legal code](https://creativecommons.org/licenses/by-sa/4.0/legalcode.en) | `0b67381b3376a44bf0e646f08c9cf9c76b453c42eb318c951d1706595cd01953` |
| `CC-BY-SA-3.0.html` | [CC BY-SA 3.0 legal code](https://creativecommons.org/licenses/by-sa/3.0/legalcode) | `501793af3ab1511acee76e54ac11b01cc1dbc0be2a18ab686ef2be327e1e3a87` |

## Rebuild and updates

Run `python scripts/ingest_data.py` to validate the pinned input hashes and deterministically rebuild `src/data/generated`. The importer stops on malformed or duplicate IDs, bad references/restrictions, corrupt source files, and output validation failures. It records a rejected source mismatch without emitting that path.

Source SHA-256: KANJIDIC2 `6182a700e08a8f6a6646529b77f84f0d963ed33cd5c7516ff19e4ee642345446`; JMdict NG `ca7086849b084fa4d8fb799e0ce0ee90c108ee5612243deb2c4d19e1117a16d1`; KanjiVG `b5df6cd2bc249dd49b8041eb99e38ba9f8dc6b9ea57962a80084b4a315d1a7fc`.

Generated SHA-256: `kanji.json` `8e4cdc58ec43d08007e26ec4cabf233ebdee211d97ea243c76b9492d55b5d44a`; `vocabulary.json` `3fc2e5b7bddcfd30f2854e5c5e3ea4c0aaabd7484f7bda0d304c5df473871639`; `example-vocabulary.json` `28ca61dfad692ebba1c9200bc353497cde56020e7ff99063e1b7e12cdf49eb26`; `strokes.json` `da83593dff02889fff81cf5c19f7be16959e210a7029730f94a561d8c28fa00b`.

`npm run build` succeeded on 2026-10-18 with TypeScript and Vite 8.3.4. The Vite bundle keeps the language JSON as separate same-origin assets; only the small application JS/CSS is compiled into the main bundle. Build output includes two non-fatal `use client` directive notices from `lucide-react`.

EDRDG requires a procedure for updating the dictionaries at least monthly. Refresh the EDRDG snapshots from their official feeds and the KanjiVG archive from its official releases; update `data/sources/metadata.json` with the retrieval date, release, URL, and SHA-256, regenerate the data, review all diagnostics and licenses, and ship the updated source manifest. The ordinary app build never downloads sources.
