# Pinned source snapshots

Retrieved 2026-10-18. These original compressed archives are kept byte-for-byte and are the only inputs to `scripts/ingest_data.py`; the app build does not download data.

| Source | Snapshot | Upstream date/version | SHA-256 | License |
| --- | --- | --- | --- | --- |
| KANJIDIC2 | `kanjidic2.xml.gz` | database 2026-291, created 2026-10-18 | `6182a700e08a8f6a6646529b77f84f0d963ed33cd5c7516ff19e4ee642345446` | [EDRDG CC BY-SA 4.0 terms](https://www.edrdg.org/edrdg/newlic.html) |
| JMdict New Generation, English | `JMdict_e_NG.gz` | created 2026-10-18, XML version 1.10 | `ca7086849b084fa4d8fb799e0ce0ee90c108ee5612243deb2c4d19e1117a16d1` | [EDRDG CC BY-SA 4.0 terms](https://www.edrdg.org/edrdg/newlic.html) |
| KanjiVG main (non-variant) | `kanjivg-20260714-main.zip` | release 2026-07-14 | `b5df6cd2bc249dd49b8041eb99e38ba9f8dc6b9ea57962a80084b4a315d1a7fc` | [CC BY-SA 3.0](https://creativecommons.org/licenses/by-sa/3.0/) |

The machine-readable URLs and license fields are in [`metadata.json`](metadata.json). Refresh EDRDG snapshots at least monthly as required by its licence. For an update, replace the source archives from their official URLs, update `metadata.json` with the retrieval date and new KanjiVG release, then run `python scripts/ingest_data.py`. Review generated counts, rejections, checksums, and attribution before shipping the new snapshot. The importer refuses a source whose checksum differs from `metadata.json`.

EDRDG's JMdict New Generation feed began distribution in May 2026 and supersedes the legacy XML feed. The importer uses the NG version and records its creation date/version from the document root.
