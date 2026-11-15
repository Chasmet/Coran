#!/usr/bin/env python3
"""Smoke-test of Quran API source availability and verse/audio alignment."""
import json
import time
import urllib.request


def fetch(url):
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={
                "Accept": "application/json", "User-Agent": "Coran-Android-Check/1.1"
            })
            with urllib.request.urlopen(req, timeout=20) as response:
                return json.load(response)
        except Exception:
            if attempt == 2:
                raise
            time.sleep(attempt + 1)


for source in ("quran-uthmani", "fr.hamidullah", "en.sahih",
               "fr.leclerc", "en.walk", "ar.alafasy"):
    data = fetch("https://api.alquran.cloud/v1/surah/1/" + source)
    assert data["code"] == 200, (source, data.get("code"))
    section = data["data"]
    assert section["number"] == 1, source
    assert section["edition"]["identifier"] == source, source
    verses = section["ayahs"]
    assert len(verses) == 7, (source, len(verses))
    assert verses[0]["numberInSurah"] == 1, source
    if source in ("fr.leclerc", "en.walk", "ar.alafasy"):
        assert verses[0]["audio"].startswith("https://"), (source, verses[0])
    else:
        assert verses[0]["text"].strip(), source
    print("OK", source, "7 versets")
