#!/usr/bin/env python3
"""Build deterministic, source-traceable KanjiForge language data."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import re
import tempfile
import unicodedata
import zipfile
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"
KVG_NS = "{http://kanjivg.tagaini.net}"
SVG_NS = "{http://www.w3.org/2000/svg}"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def nfc(value: str | None) -> str:
    return unicodedata.normalize("NFC", value or "").strip()


def raw_text(element: ET.Element | None) -> str:
    return (element.text if element is not None and element.text else "").strip()


def children(element: ET.Element, name: str) -> list[ET.Element]:
    return [child for child in element if child.tag == name]


def text(element: ET.Element | None) -> str:
    return nfc(element.text if element is not None else None)


def unique(values: list[str]) -> list[str]:
    return list(dict.fromkeys(value for value in values if value))


def number(element: ET.Element | None, context: str) -> int | None:
    value = text(element)
    if not value:
        return None
    if not value.isdecimal():
        raise ValueError(f"Invalid integer {value!r} in {context}")
    return int(value)


def parse_kanjidic(path: Path) -> tuple[list[dict], dict]:
    records: list[dict] = []
    seen: set[str] = set()
    header: dict[str, str] = {}
    for event, element in ET.iterparse(gzip.open(path, "rb"), events=("start", "end")):
        if event == "start" and element.tag == "kanjidic2":
            continue
        if event != "end":
            continue
        if element.tag == "header":
            header = {node.tag: text(node) for node in element}
            element.clear()
        elif element.tag == "character":
            character = raw_text(element.find("literal"))
            if len(character) != 1 or 0xD800 <= ord(character) <= 0xDFFF:
                raise ValueError(f"Invalid KANJIDIC2 literal {character!r}")
            if character in seen:
                raise ValueError(f"Duplicate KANJIDIC2 character {character!r}")
            seen.add(character)
            codepoint = raw_text(next((node for node in element.findall("./codepoint/cp_value") if node.get("cp_type") == "ucs"), None))
            if not codepoint or int(codepoint, 16) != ord(character):
                raise ValueError(f"KANJIDIC2 codepoint mismatch for {character!r}: {codepoint!r}")
            misc = element.find("misc")
            radical = next((node for node in element.findall("./radical/rad_value") if node.get("rad_type") == "classical"), None)
            rm = element.find("reading_meaning")
            readings = rm.findall("./rmgroup/reading") if rm is not None else []
            meanings = rm.findall("./rmgroup/meaning") if rm is not None else []
            record = {
                "character": character,
                "codepoint": codepoint.lower(),
                "meanings": unique([text(node) for node in meanings if node.get("m_lang") in (None, "en")]),
                "on": unique([text(node) for node in readings if node.get("r_type") == "ja_on"]),
                "kun": unique([text(node) for node in readings if node.get("r_type") == "ja_kun"]),
                "nanori": unique([text(node) for node in readings if node.get("r_type") == "ja_nanori"]),
                "wordIds": [],
            }
            strokes = number(misc.find("stroke_count") if misc is not None else None, f"{character} stroke_count")
            grade = number(misc.find("grade") if misc is not None else None, f"{character} grade")
            jlpt = number(misc.find("jlpt") if misc is not None else None, f"{character} jlpt")
            frequency = number(misc.find("freq") if misc is not None else None, f"{character} frequency")
            radical_number = number(radical, f"{character} classical radical")
            for key, value in (("strokes", strokes), ("grade", grade), ("jlpt", jlpt), ("frequency", frequency), ("radical", radical_number)):
                if value is not None:
                    record[key] = value
            records.append(record)
            element.clear()
    if not header.get("database_version") or not header.get("date_of_creation"):
        raise ValueError("KANJIDIC2 header is missing its version/date")
    return records, header


def priority_key(priorities: list[str], sequence: int) -> tuple[int, int, int]:
    ranks = {"news1": 0, "ichi1": 1, "spec1": 2, "gai1": 3, "news2": 4, "ichi2": 5, "spec2": 6, "gai2": 7}
    candidates = [ranks[tag] for tag in priorities if tag in ranks]
    frequency = [int(match.group(1)) for tag in priorities if (match := re.fullmatch(r"nf(\d{2})", tag))]
    rank = min(candidates) if candidates else (8 + min(frequency) if frequency else 100)
    return rank, min(frequency) if frequency else 100, sequence


def parse_jmdict(path: Path) -> tuple[list[dict], dict, dict[str, list[int]]]:
    records: list[dict] = []
    seen: set[int] = set()
    by_character: dict[str, list[int]] = defaultdict(list)
    root_attributes: dict[str, str] = {}
    rejected = {"without_english_gloss": 0, "without_written_kanji_form": 0}
    for event, element in ET.iterparse(gzip.open(path, "rb"), events=("start", "end")):
        if event == "start" and element.tag == "JMdict":
            root_attributes = dict(element.attrib)
            continue
        if event != "end" or element.tag != "entry":
            continue
        sequence_text = text(element.find("ent_seq"))
        if not sequence_text.isdecimal():
            raise ValueError(f"Malformed JMdict sequence ID {sequence_text!r}")
        sequence = int(sequence_text)
        if sequence in seen:
            raise ValueError(f"Duplicate JMdict sequence ID {sequence}")
        seen.add(sequence)
        written = unique([raw_text(node.find("keb")) for node in children(element, "k_ele")])
        readings = []
        for reading_element in children(element, "r_ele"):
            reading = raw_text(reading_element.find("reb"))
            restricted = unique([raw_text(node) for node in children(reading_element, "re_restr")])
            if any(form not in written for form in restricted):
                raise ValueError(f"JMdict {sequence} reading restriction references an unknown written form")
            no_kanji = reading_element.find("re_nokanji") is not None
            applies_to = [] if no_kanji else (restricted or None)
            if reading:
                readings.append({"text": reading, "appliesTo": applies_to})
        senses = children(element, "sense")
        glosses = unique([
            text(gloss)
            for sense in senses
            for gloss in children(sense, "gloss")
            if gloss.get(XML_LANG) in (None, "en", "eng")
        ])
        if not glosses:
            rejected["without_english_gloss"] += 1
            element.clear()
            continue
        part_of_speech = unique([text(node) for sense in senses for node in children(sense, "pos")])
        priorities = unique([
            text(node)
            for form in children(element, "k_ele")
            for node in children(form, "ke_pri")
        ] + [
            text(node)
            for form in children(element, "r_ele")
            for node in children(form, "re_pri")
        ])
        record = {
            "id": sequence,
            "written": written,
            "readings": readings,
            "glosses": glosses,
            "partOfSpeech": part_of_speech,
            "priorities": priorities,
        }
        records.append(record)
        linked_characters = set(character for form in written for character in form)
        if not linked_characters:
            rejected["without_written_kanji_form"] += 1
        for character in sorted(linked_characters):
            by_character[character].append(sequence)
        element.clear()
    if root_attributes.get("version") != "1.10" or not root_attributes.get("created"):
        raise ValueError(f"Unsupported or unversioned JMdict NG root: {root_attributes}")
    records.sort(key=lambda record: priority_key(record["priorities"], record["id"]))
    return records, root_attributes, {"byCharacter": by_character, "rejected": rejected}


def parse_kanjivg(path: Path) -> tuple[dict[str, list[str]], dict]:
    strokes: dict[str, list[str]] = {}
    rejected = {"filename_codepoint_mismatch": 0, "missing_or_noncontiguous_strokes": 0}
    mismatch_examples: list[dict[str, str | None]] = []
    with zipfile.ZipFile(path) as archive:
        names = sorted(name for name in archive.namelist() if name.startswith("kanji/") and name.endswith(".svg"))
        for name in names:
            filename = Path(name).stem
            if not re.fullmatch(r"[0-9a-fA-F]{4,6}", filename):
                continue
            character = chr(int(filename, 16))
            root = ET.fromstring(archive.read(name))
            stroke_group = root.find(f".//{SVG_NS}g[@id='kvg:StrokePaths_{filename}']")
            primary = stroke_group[0] if stroke_group is not None and len(stroke_group) else None
            if primary is None or primary.get(KVG_NS + "element") != character:
                rejected["filename_codepoint_mismatch"] += 1
                mismatch_examples.append({"path": name, "expectedCharacter": character, "embeddedCharacter": primary.get(KVG_NS + "element") if primary is not None else None})
                continue
            indexed: dict[int, str] = {}
            for node in root.iter(SVG_NS + "path"):
                match = re.search(r"-s(\d+)$", node.get("id", ""))
                path_data = nfc(node.get("d"))
                if match and path_data:
                    index = int(match.group(1))
                    if index in indexed:
                        raise ValueError(f"Duplicate KanjiVG stroke {index} for {character!r}")
                    indexed[index] = path_data
            if not indexed or sorted(indexed) != list(range(1, len(indexed) + 1)):
                rejected["missing_or_noncontiguous_strokes"] += 1
                continue
            strokes[character] = [indexed[index] for index in sorted(indexed)]
    return strokes, {"svgFiles": len(names), "charactersWithPaths": len(strokes), "rejected": rejected, "mismatchExamples": mismatch_examples}


def write_json(path: Path, value: object) -> None:
    path.write_text(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8", newline="\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sources", type=Path, default=ROOT / "data/sources")
    parser.add_argument("--output", type=Path, default=ROOT / "src/data/generated")
    args = parser.parse_args()
    sources_dir = args.sources.resolve()
    output_dir = args.output.resolve()
    metadata = json.loads((sources_dir / "metadata.json").read_text(encoding="utf-8"))
    source_facts = {}
    for key, source in metadata["sources"].items():
        file = sources_dir / source["file"]
        if not file.is_file():
            raise FileNotFoundError(file)
        digest = sha256(file)
        if source.get("sha256") and digest != source["sha256"]:
            raise ValueError(f"Pinned checksum mismatch for {file.name}: {digest}")
        source_facts[key] = {**source, "retrievedAt": metadata["retrievedAt"], "sha256": digest, "sizeBytes": file.stat().st_size}

    kanji, kanji_header = parse_kanjidic(sources_dir / metadata["sources"]["kanjidic2"]["file"])
    vocabulary, jmdict_header, jmdict_links = parse_jmdict(sources_dir / metadata["sources"]["jmdict"]["file"])
    stroke_paths, stroke_facts = parse_kanjivg(sources_dir / metadata["sources"]["kanjivg"]["file"])
    words_by_character = jmdict_links["byCharacter"]
    vocabulary_by_id = {word["id"]: word for word in vocabulary}
    example_ids: set[int] = set()
    for record in kanji:
        record["wordIds"] = sorted(words_by_character.get(record["character"], []), key=lambda sequence: priority_key(vocabulary_by_id[sequence]["priorities"], sequence))
        record["exampleIds"] = record["wordIds"][:6]
        example_ids.update(record["exampleIds"])
    all_word_ids = {word["id"] for word in vocabulary}
    invalid_links = [(record["character"], sequence) for record in kanji for sequence in record["wordIds"] if sequence not in all_word_ids]
    if invalid_links:
        raise ValueError(f"Invalid KANJIDIC2-to-JMdict references: {invalid_links[:5]}")

    output_dir.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="kanjiforge-data-", dir=output_dir.parent) as temp:
        stage = Path(temp)
        outputs = {
            "kanji.json": kanji,
            "vocabulary.json": vocabulary,
            "example-vocabulary.json": {str(sequence): vocabulary_by_id[sequence] for sequence in sorted(example_ids)},
            "strokes.json": {character: paths for character, paths in sorted(stroke_paths.items())},
        }
        output_facts = {}
        for filename, value in outputs.items():
            file = stage / filename
            write_json(file, value)
            output_facts[filename] = {"sha256": sha256(file), "sizeBytes": file.stat().st_size}
        manifest = {
            "schemaVersion": 1,
            "sources": source_facts,
            "sourceVersions": {
                "kanjidic2": {"databaseVersion": kanji_header["database_version"], "created": kanji_header["date_of_creation"], "fileVersion": kanji_header["file_version"]},
                "jmdict": jmdict_header,
                "kanjivg": metadata["sources"]["kanjivg"]["release"],
            },
            "fields": {
                "kanji": ["literal", "UCS codepoint", "English meanings", "Japanese on/kun/nanori readings", "stroke count", "grade", "pre-2010 JLPT level", "frequency", "classical radical", "all linked JMdict sequence IDs", "six highest-priority example IDs"],
                "vocabulary": ["JMdict sequence ID", "written forms", "reading forms with written-form restrictions", "English glosses", "part-of-speech tags", "priority tags"],
                "strokes": ["KanjiVG exact-character SVG stroke path data in source order"],
            },
            "transformations": ["Unicode NFC normalization for descriptive text", "preserve source codepoints in kanji literals, written forms, readings, and restriction identifiers because canonical normalization can merge distinct CJK compatibility characters", "deduplicate identical forms/readings/glosses/tags preserving first appearance", "English glosses only", "omit absent optional fields", "sort words by JMdict priority class, frequency rank, then numeric sequence ID", "retain reading restrictions and re_nokanji as appliesTo", "link words to kanji by exact written-form codepoints", "select at most six highest-priority words per character for the compact UI example index", "include stroke geometry only when filename codepoint and embedded element agree and stroke numbering is contiguous"],
            "counts": {
                "kanji": len(kanji),
                "kanjiWithoutEnglishMeanings": sum(not record["meanings"] for record in kanji),
                "vocabulary": len(vocabulary),
                "kanjiWithWords": sum(bool(record["wordIds"]) for record in kanji),
                "kanjiWordReferences": sum(len(record["wordIds"]) for record in kanji),
                "uniqueExampleWords": len(example_ids),
                "kanjiWithStrokePaths": len(stroke_paths),
                "strokes": sum(map(len, stroke_paths.values())),
                "unmatchedVocabulary": jmdict_links["rejected"]["without_written_kanji_form"],
            },
            "rejections": {
                "kanjidic2": 0,
                "jmdict": jmdict_links["rejected"],
                "kanjivg": stroke_facts["rejected"],
                "kanjivgMismatchExamples": stroke_facts["mismatchExamples"],
                "invalidWordReferences": 0,
            },
            "generatedFiles": output_facts,
        }
        write_json(stage / "manifest.json", manifest)
        output_dir.mkdir(parents=True, exist_ok=True)
        for filename in (*outputs, "manifest.json"):
            os.replace(stage / filename, output_dir / filename)
    print(json.dumps({"counts": manifest["counts"], "rejections": manifest["rejections"], "generatedFiles": manifest["generatedFiles"]}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
