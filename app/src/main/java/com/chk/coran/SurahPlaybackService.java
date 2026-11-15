package com.chk.coran;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lecteur du Coran par sourates entières.
 * ForegroundService mediaPlayback + notification MediaStyle + MediaSession :
 * l'audio continue lorsque l'écran est éteint ou qu'une autre application est ouverte.
 * Les MP3 existants restent stockés localement par langue/verset.
 */
public final class SurahPlaybackService extends Service {
    public static final String ACTION_PLAY = "com.chk.coran.audio.PLAY_SURAH";
    public static final String ACTION_PAUSE = "com.chk.coran.audio.PAUSE";
    public static final String ACTION_RESUME = "com.chk.coran.audio.RESUME";
    public static final String ACTION_NEXT = "com.chk.coran.audio.NEXT";
    public static final String ACTION_PREVIOUS = "com.chk.coran.audio.PREVIOUS";
    public static final String ACTION_STOP = "com.chk.coran.audio.STOP";
    public static final String ACTION_STATE_CHANGED = "com.chk.coran.audio.STATE_CHANGED";
    public static final String EXTRA_SURAH = "surah";
    public static final String EXTRA_LANGUAGE = "language";
    public static final String PREFS = "coran_surah_playback";
    private static final String CHANNEL = "coran_full_surah_playback";
    private static final int NOTIFICATION_ID = 6720;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService audioTasks = Executors.newSingleThreadExecutor();
    private final ArrayList<QuranRepository.Verse> verses = new ArrayList<>();
    private MediaPlayer mediaPlayer;
    private MediaSession mediaSession;
    private AudioManager audioManager;
    private boolean prepared, playing, loading, active, resumeOnFocus, userWantsPlayback = true;
    private int surah = 1, verse = 1, token = 0;
    private String language = Languages.FRENCH;
    private String status = "";
    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            resumeOnFocus = playing && change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
            pause();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            if (mediaPlayer != null) mediaPlayer.setVolume(0.20f, 0.20f);
        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
            if (mediaPlayer != null) mediaPlayer.setVolume(1f, 1f);
            if (resumeOnFocus) {
                resumeOnFocus = false;
                resume();
            }
        }
    };
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) pause();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        createChannel();
        mediaSession = new MediaSession(this, "CoranSourates");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
            | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resume(); }
            @Override public void onPause() { pause(); }
            @Override public void onStop() { stopPlayback(); }
            @Override public void onSkipToNext() { next(); }
            @Override public void onSkipToPrevious() { previous(); }
        });
        mediaSession.setActive(true);
        registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        String action = intent.getAction();
        if (ACTION_PLAY.equals(action)) {
            int requested = intent.getIntExtra(EXTRA_SURAH, -1);
            if (requested < 1 || requested > 114) { stopSelf(); return START_NOT_STICKY; }
            surah = requested;
            language = Languages.normalize(intent.getStringExtra(EXTRA_LANGUAGE));
            verse = 1;
            userWantsPlayback = true;
            active = true;
            loading = true;
            status = "Préparation de la sourate…";
            // Android 8+ exige l'affichage immédiat de la notification d'un FGS démarré par l'UI.
            startForeground(NOTIFICATION_ID, makeNotification());
            beginSurah();
        } else if (ACTION_PAUSE.equals(action)) {
            pause();
        } else if (ACTION_RESUME.equals(action)) {
            resume();
        } else if (ACTION_NEXT.equals(action)) {
            next();
        } else if (ACTION_PREVIOUS.equals(action)) {
            previous();
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    private void beginSurah() {
        final int generation = ++token;
        releaseMedia();
        verses.clear();
        publish();
        final int requestedSurah = surah;
        audioTasks.execute(() -> {
            try {
                QuranRepository.Surah result = QuranRepository.load(getApplicationContext(), requestedSurah);
                handler.post(() -> {
                    if (generation != token || !active) return;
                    verses.addAll(result.verses);
                    prepareVerse(generation);
                });
            } catch (Exception e) {
                handler.post(() -> fail(generation, "Sourate indisponible : " + e.getMessage()));
            }
        });
    }

    private void prepareVerse(int generation) {
        if (generation != token || !active) return;
        if (verse < 1 || verse > verses.size()) {
            status = "Sourate terminée";
            stopPlayback();
            return;
        }
        releaseMedia();
        loading = true;
        playing = false;
        status = "Préparation du verset " + verse + "…";
        publish();
        int index = verse;
        int globalNumber = verses.get(index - 1).globalNumber;
        int chosenSurah = surah;
        String chosenLanguage = language;
        audioTasks.execute(() -> {
            PowerManager.WakeLock lock = null;
            try {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null) {
                    lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Coran:AudioVerseLoad");
                    lock.acquire(90000);
                }
                File audio = AudioRepository.ensureAudio(getApplicationContext(),
                    chosenLanguage, chosenSurah, index, globalNumber);
                handler.post(() -> startVerse(generation, index, audio));
            } catch (Exception e) {
                handler.post(() -> fail(generation, "Audio indisponible : " + e.getMessage()));
            } finally {
                if (lock != null && lock.isHeld()) lock.release();
            }
        });
    }

    private void startVerse(int generation, int index, File audio) {
        if (generation != token || !active || verse != index) return;
        try {
            MediaPlayer player = new MediaPlayer();
            mediaPlayer = player;
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            player.setDataSource(audio.getAbsolutePath());
            player.setOnPreparedListener(mp -> {
                if (generation != token || !active || player != mediaPlayer) return;
                int focus = audioManager == null ? AudioManager.AUDIOFOCUS_REQUEST_GRANTED :
                    audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
                if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    fail(generation, "Le son est utilisé par une autre application");
                    return;
                }
                prepared = true;
                loading = false;
                status = "";
                if (userWantsPlayback) {
                    mp.start();
                    playing = true;
                } else playing = false;
                publish();
                // Préchargement du prochain verset, sans attendre la fin de celui en cours.
                if (index < verses.size()) prefetchNext(generation, index + 1);
            });
            player.setOnCompletionListener(mp -> {
                if (generation != token || player != mediaPlayer || !active) return;
                if (verse < verses.size()) {
                    verse++;
                    prepareVerse(generation);
                } else {
                    loading = false;
                    playing = false;
                    status = "Sourate terminée";
                    releaseMedia();
                    stopForeground(true);
                    active = false;
                    publish();
                    stopSelf();
                }
            });
            player.setOnErrorListener((mp, what, extra) -> {
                fail(generation, "Erreur de décodage audio");
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            fail(generation, "Erreur audio : " + e.getMessage());
        }
    }

    private void prefetchNext(int generation, int next) {
        int global = verses.get(next - 1).globalNumber;
        int chosenSurah = surah;
        String chosenLanguage = language;
        audioTasks.execute(() -> {
            if (generation != token || !active) return;
            try {
                AudioRepository.ensureAudio(getApplicationContext(),
                    chosenLanguage, chosenSurah, next, global);
            } catch (Exception ignored) {
                // Un échec du préchargement sera retenté lors de la lecture de ce verset.
            }
        });
    }

    private void pause() {
        userWantsPlayback = false;
        if (mediaPlayer != null && prepared) {
            try { if (mediaPlayer.isPlaying()) mediaPlayer.pause(); }
            catch (Exception ignored) {}
        }
        playing = false;
        publish();
    }

    private void resume() {
        if (!active) return;
        userWantsPlayback = true;
        if (loading) { publish(); return; }
        if (mediaPlayer != null && prepared) {
            int focus = audioManager == null ? AudioManager.AUDIOFOCUS_REQUEST_GRANTED :
                audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return;
            try {
                mediaPlayer.start();
                playing = true;
                publish();
                return;
            } catch (Exception ignored) {}
        }
        final int generation = ++token;
        prepareVerse(generation);
    }

    private void next() {
        if (!active || verses.isEmpty()) return;
        userWantsPlayback = true;
        token++;
        if (verse < verses.size()) verse++;
        prepareVerse(token);
    }

    private void previous() {
        if (!active || verses.isEmpty()) return;
        userWantsPlayback = true;
        token++;
        if (verse > 1) verse--;
        prepareVerse(token);
    }

    private void fail(int generation, String message) {
        if (generation != token || !active) return;
        releaseMedia();
        loading = false;
        playing = false;
        status = message;
        publish();
    }

    private void stopPlayback() {
        token++;
        active = false;
        playing = false;
        loading = false;
        userWantsPlayback = false;
        status = "";
        releaseMedia();
        verses.clear();
        if (audioManager != null) audioManager.abandonAudioFocus(focusListener);
        stopForeground(true);
        publish();
        stopSelf();
    }

    private void releaseMedia() {
        prepared = false;
        playing = false;
        if (mediaPlayer != null) {
            try { mediaPlayer.reset(); } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private PendingIntent serviceAction(String action, int code) {
        Intent intent = new Intent(this, SurahPlaybackService.class).setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT |
            (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getService(this, code, intent, flags);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(
                new NotificationChannel(CHANNEL, "Lecture du Coran en arrière-plan",
                    NotificationManager.IMPORTANCE_LOW));
        }
    }

    private Notification makeNotification() {
        Intent open = new Intent(this, AudioLibraryActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT |
            (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent content = PendingIntent.getActivity(this, 6730, open, flags);
        String name = SurahCatalog.name(surah);
        String description = loading ? status :
            (status.isEmpty() ? Languages.narrator(language)
                + " · verset " + verse + (verses.isEmpty() ? "" : "/" + verses.size()) : status);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(Languages.flag(language) + " " + name)
            .setContentText(description)
            .setContentIntent(content)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(active)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_previous, "Précédent",
                serviceAction(ACTION_PREVIOUS, 1))
            .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                playing ? "Pause" : "Lecture",
                serviceAction(playing ? ACTION_PAUSE : ACTION_RESUME, 2))
            .addAction(android.R.drawable.ic_media_next, "Suivant",
                serviceAction(ACTION_NEXT, 3))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Arrêter",
                serviceAction(ACTION_STOP, 4));
        builder.setStyle(new Notification.MediaStyle()
            .setMediaSession(mediaSession.getSessionToken())
            .setShowActionsInCompactView(0, 1, 2));
        return builder.build();
    }

    private void publish() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean("active", active)
            .putBoolean("playing", playing)
            .putBoolean("loading", loading)
            .putInt("surah", surah)
            .putInt("verse", verse)
            .putInt("total", verses.size())
            .putString("language", language)
            .putString("error", status)
            .apply();
        if (mediaSession != null) {
            long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS;
            int state = loading ? PlaybackState.STATE_BUFFERING :
                (playing ? PlaybackState.STATE_PLAYING : (active ?
                    PlaybackState.STATE_PAUSED : PlaybackState.STATE_STOPPED));
            mediaSession.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    playing ? 1f : 0f).build());
            mediaSession.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, SurahCatalog.name(surah))
                .putString(MediaMetadata.METADATA_KEY_ARTIST, Languages.narrator(language))
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Coran · sourate complète").build());
        }
        sendBroadcast(new Intent(ACTION_STATE_CHANGED).setPackage(getPackageName()));
        if (active) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, makeNotification());
        }
    }

    @Override public void onDestroy() {
        token++;
        releaseMedia();
        audioTasks.shutdownNow();
        if (audioManager != null) audioManager.abandonAudioFocus(focusListener);
        try { unregisterReceiver(noisyReceiver); } catch (Exception ignored) {}
        if (mediaSession != null) { mediaSession.setActive(false); mediaSession.release(); }
        active = false;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putBoolean("active", false).putBoolean("playing", false)
            .putBoolean("loading", false).apply();
        super.onDestroy();
    }
}
