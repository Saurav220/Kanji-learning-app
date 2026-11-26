#!/usr/bin/env python3
"""Package the readable local KanjiForge exports as a compact Android catalog."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import sqlite3
import unicodedata
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "public/data/reference"
OUTPUT_DIR = ROOT / "app/src/main/assets/reference"

SEQUENCE_COLUMNS = {
    "jlpt": ("jlpt_level", "jlpt_sequence"),
    "jlptRevised": ("jlpt_revised_level", "jlpt_revised_sequence"),
    "jouyou": ("jouyou_level", "jouyou_sequence"),
    "jouyouRevised": ("jouyou_revised_level", "jouyou_revised_sequence"),
    "heisig": ("heisig_level", "heisig_sequence"),
    "heisigRevised": ("heisig_revised_level", "heisig_revised_sequence"),
    "kanken": ("kanken_level", "kanken_sequence"),
    "kankenRevised": ("kanken_revised_level", "kanken_revised_sequence"),
    "kklc": ("kklc_level", "kklc_sequence"),
    "frequency": ("freq_level", "freq_sequence"),
    "frequencyRevised": ("freq_revised_level", "freq_revised_sequence"),
    "hadamitzky": ("hadamitzky_level", "hadamitzky_sequence"),
    "kic": ("kic_level", "kic_sequence"),
}


def read_json_gzip(path: Path) -> Any:
    with gzip.open(path, "rt", encoding="utf-8") as stream:
        return json.load(stream)


def json_text(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def grams(text: str) -> list[str]:
    points = [f"{ord(char):06X}" for char in text]
    width = 1 if len(points) == 1 else 2
    return sorted({"g" + "x".join(points[index:index + width])
                   for index in range(len(points) - width + 1)})


def normalized(text: str) -> str:
    return unicodedata.normalize("NFKC", text).lower()


SCHEMA = """
PRAGMA page_size=4096;
PRAGMA journal_mode=OFF;
PRAGMA synchronous=OFF;
PRAGMA foreign_keys=ON;
PRAGMA user_version=2;
CREATE TABLE kanji (
    code INTEGER PRIMARY KEY,
    character TEXT NOT NULL UNIQUE,
    meaning TEXT,
    translation TEXT,
    on_json TEXT NOT NULL,
    kun_json TEXT NOT NULL,
    romanized_reading TEXT,
    nanori_json TEXT NOT NULL,
    stroke_count INTEGER,
    stroke_paths_json TEXT NOT NULL,
    radicals_json TEXT NOT NULL,
    decomposition TEXT,
    classical_radical INTEGER,
    level INTEGER,
    sequence INTEGER,
    jlpt INTEGER,
    grade INTEGER,
    frequency INTEGER,
    notes TEXT
);
CREATE TABLE radicals (
    code INTEGER PRIMARY KEY,
    character TEXT,
    meaning TEXT,
    reading TEXT,
    stroke_count INTEGER,
    sequence INTEGER,
    variant_of INTEGER,
    stroke_paths_json TEXT NOT NULL
);
CREATE TABLE kana (
    character TEXT PRIMARY KEY,
    reading TEXT,
    example TEXT,
    meaning TEXT,
    translation TEXT,
    origin TEXT,
    katakana INTEGER NOT NULL,
    archaic INTEGER NOT NULL,
    diacritic INTEGER NOT NULL,
    stroke_count INTEGER,
    stroke_paths_json TEXT NOT NULL,
    sequence INTEGER
);
CREATE TABLE words (
    id INTEGER PRIMARY KEY,
    forms_json TEXT NOT NULL,
    readings_json TEXT NOT NULL,
    romaji_json TEXT NOT NULL,
    glosses_json TEXT NOT NULL,
    tags_json TEXT NOT NULL,
    jlpt INTEGER,
    common INTEGER NOT NULL,
    translation TEXT,
    notes TEXT,
    search_text TEXT NOT NULL
);
CREATE TABLE sentences (id INTEGER PRIMARY KEY, text TEXT, translation TEXT);
CREATE TABLE names (id INTEGER PRIMARY KEY, name TEXT, reading TEXT, type TEXT);
CREATE TABLE outlier_references (
    id INTEGER PRIMARY KEY,
    name TEXT,
    text TEXT,
    key_concept INTEGER NOT NULL,
    ancient_form TEXT
);
CREATE TABLE outlier_characters (
    id INTEGER PRIMARY KEY,
    name TEXT,
    view_box TEXT,
    paths_json TEXT NOT NULL
);
CREATE TABLE kanji_word (
    kanji_code INTEGER NOT NULL REFERENCES kanji(code),
    word_id INTEGER NOT NULL REFERENCES words(id),
    PRIMARY KEY(kanji_code, word_id)
);
CREATE TABLE kanji_sentence (
    kanji_code INTEGER NOT NULL REFERENCES kanji(code),
    sentence_id INTEGER NOT NULL REFERENCES sentences(id),
    recommended INTEGER NOT NULL,
    PRIMARY KEY(kanji_code, sentence_id)
);
CREATE TABLE kanji_name (
    kanji_code INTEGER NOT NULL REFERENCES kanji(code),
    name_id INTEGER NOT NULL REFERENCES names(id),
    PRIMARY KEY(kanji_code, name_id)
);
CREATE INDEX kanji_jlpt_idx ON kanji(jlpt, code);
CREATE INDEX kanji_grade_idx ON kanji(grade, code);
CREATE INDEX kanji_strokes_idx ON kanji(stroke_count, code);
CREATE INDEX kanji_radical_idx ON kanji(classical_radical, code);
CREATE INDEX words_jlpt_idx ON words(jlpt, id);
CREATE INDEX words_common_idx ON words(common, id);
CREATE INDEX kanji_word_word_idx ON kanji_word(word_id, kanji_code);
CREATE INDEX kanji_sentence_sentence_idx ON kanji_sentence(sentence_id, kanji_code);
CREATE INDEX kanji_name_name_idx ON kanji_name(name_id, kanji_code);
CREATE VIRTUAL TABLE word_search_fts USING fts4(word_id UNINDEXED, grams, tokenize=unicode61);
"""


def insert_many(connection: sqlite3.Connection, table: str, columns: str,
                rows: Iterable[tuple[Any, ...]]) -> None:
    connection.executemany(f"INSERT INTO {table}({columns}) VALUES ({','.join('?' for _ in columns.split(','))})", rows)


def build_catalog(source_dir: Path, output_dir: Path) -> dict[str, Any]:
    source_manifest_path = source_dir / "manifest.json"
    source_manifest = json.loads(source_manifest_path.read_text(encoding="utf-8"))
    catalog = read_json_gzip(source_dir / "catalog.json.gzbin")
    words = read_json_gzip(source_dir / "words.json.gzbin")
    sentences = read_json_gzip(source_dir / "sentences.json.gzbin")
    names = read_json_gzip(source_dir / "names.json.gzbin")
    outlier = read_json_gzip(source_dir / "outlier.json.gzbin")
    word_links = read_json_gzip(source_dir / "kanji-word-links.json.gzbin")
    sentence_links = read_json_gzip(source_dir / "kanji-sentence-links.json.gzbin")
    name_links = read_json_gzip(source_dir / "kanji-name-links.json.gzbin")

    output_dir.mkdir(parents=True, exist_ok=True)
    database_path = output_dir / "catalog.sqlite"
    # Android's asset packager treats a `.gz` suffix as a precompressed asset,
    # strips the suffix, and stores the decompressed bytes. The installer needs
    # the gzip stream so it can verify both compressed and SQLite checksums.
    compressed_path = output_dir / "catalog.sqlite.gzbin"
    manifest_path = output_dir / "manifest.json"
    database_path.unlink(missing_ok=True)

    connection = sqlite3.connect(database_path)
    try:
        connection.executescript(SCHEMA)
        sequence_columns = ["code INTEGER PRIMARY KEY"]
        for level_column, rank_column in SEQUENCE_COLUMNS.values():
            sequence_columns.extend((f"{level_column} INTEGER", f"{rank_column} INTEGER"))
        connection.execute(f"CREATE TABLE kanji_sequence ({', '.join(sequence_columns)})")
        for family, (level_column, rank_column) in SEQUENCE_COLUMNS.items():
            connection.execute(
                f"CREATE INDEX kanji_sequence_{family}_idx ON kanji_sequence ({level_column}, {rank_column})"
            )

        kanji_rows = []
        for item in catalog["kanji"]:
            levels = item.get("levels") or {}
            kanji_rows.append((
                item["code"], item["character"], item.get("meaning"), item.get("translation"),
                json_text(item.get("on", [])), json_text(item.get("kun", [])), item.get("romanizedReading"),
                json_text(item.get("nanori", [])), item.get("strokeCount"), json_text(item.get("strokePaths", [])),
                json_text(item.get("radicals", [])), item.get("decomposition"), item.get("classicalRadical"),
                item.get("level"), item.get("sequence"), levels.get("jlpt"), levels.get("jouyou"),
                levels.get("frequency"), item.get("notes"),
            ))
        insert_many(connection, "kanji", "code,character,meaning,translation,on_json,kun_json,romanized_reading,nanori_json,stroke_count,stroke_paths_json,radicals_json,decomposition,classical_radical,level,sequence,jlpt,grade,frequency,notes", kanji_rows)

        sequence_fields = [column for pair in SEQUENCE_COLUMNS.values() for column in pair]
        sequence_rows = []
        for item in catalog["kanji"]:
            levels = item.get("levels") or {}
            ranks = item.get("sequences") or {}
            values = [item["code"]]
            for family in SEQUENCE_COLUMNS:
                values.extend((levels.get(family), ranks.get(family)))
            sequence_rows.append(tuple(values))
        insert_many(connection, "kanji_sequence", ",".join(["code", *sequence_fields]), sequence_rows)

        insert_many(connection, "radicals", "code,character,meaning,reading,stroke_count,sequence,variant_of,stroke_paths_json", (
            (item["code"], item.get("character"), item.get("meaning"), item.get("reading"),
             item.get("strokeCount"), item.get("sequence"), item.get("variantOf"), json_text(item.get("strokePaths", [])))
            for item in catalog.get("radicals", [])
        ))
        insert_many(connection, "kana", "character,reading,example,meaning,translation,origin,katakana,archaic,diacritic,stroke_count,stroke_paths_json,sequence", (
            (item["character"], item.get("reading"), item.get("example"), item.get("meaning"), item.get("translation"),
             item.get("origin"), int(bool(item.get("katakana"))), int(bool(item.get("archaic"))),
             int(bool(item.get("diacritic"))), item.get("strokeCount"), json_text(item.get("strokePaths", [])),
             item.get("sequence"))
            for item in catalog.get("kana", []) if item.get("character")
        ))

        word_rows = []
        fts_rows = []
        for item in words:
            forms = item.get("forms", [])
            readings = item.get("readings", [])
            romaji = item.get("romaji", [])
            glosses = item.get("glosses", [])
            search_text = normalized(item.get("searchText") or " ".join([*forms, *readings, *romaji, *glosses]))
            word_rows.append((item["id"], json_text(forms), json_text(readings), json_text(romaji),
                              json_text(glosses), json_text(item.get("tags", [])), item.get("jlpt"),
                              int(bool(item.get("common"))), item.get("translation"), item.get("notes"), search_text))
            word_grams = grams(search_text)
            if word_grams:
                fts_rows.append((item["id"], " ".join(word_grams)))
        insert_many(connection, "words", "id,forms_json,readings_json,romaji_json,glosses_json,tags_json,jlpt,common,translation,notes,search_text", word_rows)
        insert_many(connection, "word_search_fts", "word_id,grams", fts_rows)

        insert_many(connection, "sentences", "id,text,translation", (
            (item["id"], item.get("text"), item.get("translation")) for item in sentences
        ))
        insert_many(connection, "names", "id,name,reading,type", (
            (item["id"], item.get("name"), item.get("reading"), item.get("type")) for item in names
        ))
        insert_many(connection, "outlier_references", "id,name,text,key_concept,ancient_form", (
            (item["id"], item.get("name"), item.get("text"), int(bool(item.get("keyConcept"))), item.get("ancientForm"))
            for item in outlier.get("references", [])
        ))
        insert_many(connection, "outlier_characters", "id,name,view_box,paths_json", (
            (item["id"], item.get("name"), (item.get("drawing") or {}).get("viewBox"),
             json_text((item.get("drawing") or {}).get("paths", [])))
            for item in outlier.get("characters", [])
        ))
        insert_many(connection, "kanji_word", "kanji_code,word_id", ((code, word_id) for code, word_id in word_links))
        insert_many(connection, "kanji_sentence", "kanji_code,sentence_id,recommended", (
            (code, sentence_id, int(bool(recommended))) for code, sentence_id, recommended in sentence_links
        ))
        insert_many(connection, "kanji_name", "kanji_code,name_id", ((code, name_id) for code, name_id in name_links))
        connection.commit()
        connection.execute("PRAGMA optimize")
        connection.execute("VACUUM")
        integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
        if integrity != "ok":
            raise RuntimeError(f"SQLite integrity check failed: {integrity}")
        connection.close()
    except BaseException:
        connection.close()
        raise

    with database_path.open("rb") as source, compressed_path.open("wb") as raw:
        with gzip.GzipFile(fileobj=raw, mode="wb", compresslevel=9, mtime=0) as compressed:
            for chunk in iter(lambda: source.read(1024 * 1024), b""):
                compressed.write(chunk)

    counts = {
        "kanji": len(catalog["kanji"]), "words": len(words), "sentences": len(sentences),
        "names": len(names), "radicals": len(catalog.get("radicals", [])),
        "kanjiSequenceRows": len(catalog["kanji"]), "sequenceFamilies": len(SEQUENCE_COLUMNS),
        "kana": len(catalog.get("kana", [])), "outlierReferences": len(outlier.get("references", [])),
        "outlierCharacters": len(outlier.get("characters", [])), "wordLinks": len(word_links),
        "sentenceLinks": len(sentence_links), "nameLinks": len(name_links),
    }
    output_manifest = {
        "schemaVersion": 2,
        "source": source_manifest["source"],
        "sourceManifestSha256": sha256(source_manifest_path),
        "counts": counts,
        "excluded": source_manifest.get("excluded", {}),
        "catalog": {
            "file": compressed_path.name,
            "sizeBytes": compressed_path.stat().st_size,
            "sha256": sha256(compressed_path),
            "uncompressedSizeBytes": database_path.stat().st_size,
            "uncompressedSha256": sha256(database_path),
        },
    }
    manifest_path.write_text(json.dumps(output_manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    database_path.unlink()
    for extra in output_dir.glob("catalog.sqlite*"):
        if extra != compressed_path:
            extra.unlink(missing_ok=True)
    return output_manifest


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=SOURCE_DIR)
    parser.add_argument("--output", type=Path, default=OUTPUT_DIR)
    args = parser.parse_args()
    result = build_catalog(args.source, args.output)
    print(json.dumps({"counts": result["counts"], "catalog": result["catalog"], "output": str(args.output)}, indent=2))


if __name__ == "__main__":
    main()
