#!/usr/bin/env python3
"""Contrôles statiques du module de lecture audio écran verrouillé."""
import pathlib
import xml.etree.ElementTree as ET

base = pathlib.Path('app/src/main')
android = '{http://schemas.android.com/apk/res/android}'
root = ET.parse(base / 'AndroidManifest.xml').getroot()
permissions = {p.get(android + 'name') for p in root.findall('uses-permission')}
required = {'android.permission.INTERNET', 'android.permission.WAKE_LOCK',
            'android.permission.FOREGROUND_SERVICE',
            'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK',
            'android.permission.FOREGROUND_SERVICE_DATA_SYNC'}
missing = required - permissions
assert not missing, f'Autorisations absentes : {missing}'
services = {s.get(android + 'name'): s.get(android + 'foregroundServiceType')
            for s in root.find('application').findall('service')}
assert services.get('.SurahPlaybackService') == 'mediaPlayback', services
assert services.get('androidx.work.impl.foreground.SystemForegroundService') == 'dataSync', services
assert any(a.get(android + 'name') == '.AudioLibraryActivity'
           for a in root.find('application').findall('activity'))
for layout, ids in {
    'activity_main.xml': ['listenAllTab', 'readingModeTab'],
    'activity_audio_library.xml': ['audioSurahList', 'fullPlayPause', 'fullStop'],
    'item_audio_surah.xml': ['audioSurahPlay', 'audioSurahDownload'],
}.items():
    xml = ET.parse(base / 'res/layout' / layout)
    available = {e.get(android + 'id', '').removeprefix('@+id/')
                 for e in xml.iter()}
    assert set(ids) <= available, (layout, ids, available)
src = (base/'java/com/chk/coran/SurahPlaybackService.java').read_text()
for expected in ['ACTION_NEXT', 'ACTION_PAUSE', 'ACTION_RESUME',
                 'MediaSession', 'startForeground', 'setWakeMode',
                 'AUDIO_BECOMING_NOISY', 'ACTION_STOP']:
    assert expected in src, expected
source = (base/'java/com/chk/coran/SurahDownloadWorker.java').read_text()
for expected in ['setForegroundAsync', 'FOREGROUND_SERVICE_TYPE_DATA_SYNC',
                 'AudioRepository.ensureAudio', 'getRunAttemptCount']:
    assert expected in source, expected
print('OK : onglets, FGS média, audio en arrière-plan et téléchargements complets.')
