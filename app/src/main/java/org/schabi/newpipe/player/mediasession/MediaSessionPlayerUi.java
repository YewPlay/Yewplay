package org.schabi.newpipe.player.mediasession;

import static android.support.v4.media.session.PlaybackStateCompat.ACTION_FAST_FORWARD;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_REWIND;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SEEK_TO;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SET_REPEAT_MODE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;
import static androidx.media3.common.Player.EVENT_IS_PLAYING_CHANGED;
import static androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION;
import static androidx.media3.common.Player.EVENT_PLAYBACK_PARAMETERS_CHANGED;
import static androidx.media3.common.Player.EVENT_PLAYBACK_STATE_CHANGED;
import static androidx.media3.common.Player.EVENT_PLAY_WHEN_READY_CHANGED;
import static androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY;
import static androidx.media3.common.Player.EVENT_REPEAT_MODE_CHANGED;
import static androidx.media3.common.Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED;
import static androidx.media3.common.Player.EVENT_TIMELINE_CHANGED;
import static org.schabi.newpipe.MainActivity.DEBUG;
import static org.schabi.newpipe.player.notification.NotificationConstants.ACTION_RECREATE_NOTIFICATION;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.session.MediaButtonReceiver;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.ExoPlayer;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlayerService;
import org.schabi.newpipe.player.mediabrowser.MediaBrowserPlaybackPreparer;
import org.schabi.newpipe.player.notification.NotificationActionData;
import org.schabi.newpipe.player.notification.NotificationConstants;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.ui.PlayerUi;
import org.schabi.newpipe.player.ui.VideoPlayerUi;
import org.schabi.newpipe.util.StreamTypeUtil;
import org.schabi.newpipe.util.image.ImageStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Bridges the current player to the service-lifetime {@link MediaSessionCompat}. The ExoPlayer2
 * {@code MediaSessionConnector} does not exist in Media3, and a Media3 session cannot replace it
 * here: {@code MediaBrowserServiceCompat.setSessionToken()} can only be called once per service,
 * while players (and any session bound to their Media3 player instance) are created and destroyed
 * on every playback. So the session lives in {@link PlayerService} (token set once) and this
 * class pushes playback state, metadata and queue to it, and routes controller commands back to
 * the current player — the same contract the connector provided, including the floating queue
 * window, the custom notification actions and the hardware-media-buttons setting.
 */
public class MediaSessionPlayerUi extends PlayerUi
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "MediaSessUi";
    private static final int MAX_QUEUE_SIZE = 10;

    private static final long BASE_ACTIONS = ACTION_PLAY_PAUSE | ACTION_PLAY | ACTION_PAUSE
            | ACTION_SEEK_TO | ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_PREVIOUS
            | ACTION_SKIP_TO_QUEUE_ITEM | ACTION_FAST_FORWARD | ACTION_REWIND
            | ACTION_SET_REPEAT_MODE | ACTION_SET_SHUFFLE_MODE
            | ACTION_PREPARE_FROM_MEDIA_ID | ACTION_PLAY_FROM_MEDIA_ID;

    @NonNull
    private final MediaSessionCompat mediaSession;
    @NonNull
    private final MediaBrowserPlaybackPreparer playbackPreparer;

    private final String ignoreHardwareMediaButtonsKey;
    private boolean shouldIgnoreHardwareMediaButtons = false;

    // used to check whether any notification action changed, before sending costly updates
    private List<NotificationActionData> prevNotificationActions = List.of();

    @Nullable
    private ExoPlayer attachedExoPlayer;

    private final androidx.media3.common.Player.Listener exoPlayerListener =
            new androidx.media3.common.Player.Listener() {
                @Override
                public void onEvents(@NonNull final androidx.media3.common.Player exoPlayer,
                                     @NonNull final androidx.media3.common.Player.Events events) {
                    if (events.containsAny(EVENT_TIMELINE_CHANGED,
                            EVENT_MEDIA_ITEM_TRANSITION)) {
                        pushMetadata();
                        pushQueueWindow();
                    }
                    if (events.containsAny(EVENT_PLAYBACK_STATE_CHANGED,
                            EVENT_PLAY_WHEN_READY_CHANGED, EVENT_IS_PLAYING_CHANGED,
                            EVENT_POSITION_DISCONTINUITY, EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
                        pushPlaybackState();
                    }
                    if (events.containsAny(EVENT_REPEAT_MODE_CHANGED,
                            EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                        pushRepeatShuffle();
                    }
                }
            };

    private final MediaSessionCompat.Callback sessionCallback = new MediaSessionCompat.Callback() {
        @Override
        public void onPlay() {
            player.play();
            // hide the player controls even if the play command came from the media session
            player.UIs().get(VideoPlayerUi.class).ifPresent(ui -> ui.hideControls(0, 0));
        }

        @Override
        public void onPause() {
            player.pause();
        }

        @Override
        public void onSkipToNext() {
            player.playNext();
        }

        @Override
        public void onSkipToPrevious() {
            player.playPrevious();
        }

        @Override
        public void onSeekTo(final long pos) {
            player.seekTo(pos);
        }

        @Override
        public void onFastForward() {
            player.fastForward();
        }

        @Override
        public void onRewind() {
            player.fastRewind();
        }

        @Override
        public void onSkipToQueueItem(final long id) {
            if (player.getPlayQueue() != null) {
                final PlayQueueItem item = player.getPlayQueue().getItem((int) id);
                if (item != null) {
                    player.selectQueueItem(item);
                }
            }
        }

        @Override
        public void onPrepare() {
            playbackPreparer.onPrepare(false);
        }

        @Override
        public void onPrepareFromMediaId(final String mediaId, final Bundle extras) {
            playbackPreparer.onPrepareFromMediaId(mediaId, false, extras);
        }

        @Override
        public void onPlayFromMediaId(final String mediaId, final Bundle extras) {
            playbackPreparer.onPrepareFromMediaId(mediaId, true, extras);
        }

        @Override
        public void onPrepareFromSearch(final String query, final Bundle extras) {
            playbackPreparer.onPrepareFromSearch(query, false, extras);
        }

        @Override
        public void onPlayFromSearch(final String query, final Bundle extras) {
            playbackPreparer.onPrepareFromSearch(query, true, extras);
        }

        @Override
        public void onPrepareFromUri(final android.net.Uri uri, final Bundle extras) {
            playbackPreparer.onPrepareFromUri(uri, false, extras);
        }

        @Override
        public void onPlayFromUri(final android.net.Uri uri, final Bundle extras) {
            playbackPreparer.onPrepareFromUri(uri, true, extras);
        }

        @Override
        public void onCustomAction(final String action, final Bundle extras) {
            // custom notification actions are delivered as broadcasts, as before (this is what
            // SessionConnectorActionProvider did through the ExoPlayer2 connector)
            if (action != null && !action.isEmpty()) {
                context.sendBroadcast(new Intent(action));
            }
        }

        @Override
        public void onSetRepeatMode(@PlaybackStateCompat.RepeatMode final int repeatMode) {
            final ExoPlayer exoPlayer = player.getExoPlayer();
            if (exoPlayer == null) {
                return;
            }
            final int exoRepeatMode;
            switch (repeatMode) {
                case PlaybackStateCompat.REPEAT_MODE_ONE:
                    exoRepeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE;
                    break;
                case PlaybackStateCompat.REPEAT_MODE_ALL:
                case PlaybackStateCompat.REPEAT_MODE_GROUP:
                    exoRepeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL;
                    break;
                case PlaybackStateCompat.REPEAT_MODE_NONE:
                case PlaybackStateCompat.REPEAT_MODE_INVALID:
                default:
                    exoRepeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF;
                    break;
            }
            exoPlayer.setRepeatMode(exoRepeatMode);
        }

        @Override
        public void onSetShuffleMode(@PlaybackStateCompat.ShuffleMode final int shuffleMode) {
            final ExoPlayer exoPlayer = player.getExoPlayer();
            if (exoPlayer != null) {
                exoPlayer.setShuffleModeEnabled(shuffleMode
                        == PlaybackStateCompat.SHUFFLE_MODE_ALL);
            }
        }

        @Override
        public boolean onMediaButtonEvent(@NonNull final Intent mediaButtonIntent) {
            // returning true stops all further event processing of the system, see the
            // ignore_hardware_media_buttons setting
            if (shouldIgnoreHardwareMediaButtons) {
                return true;
            }
            return super.onMediaButtonEvent(mediaButtonIntent);
        }
    };


    public MediaSessionPlayerUi(@NonNull final Player player,
                                @NonNull final PlayerService service,
                                @NonNull final MediaBrowserPlaybackPreparer playbackPreparer) {
        super(player);
        this.mediaSession = service.getMediaSession();
        this.playbackPreparer = playbackPreparer;
        this.ignoreHardwareMediaButtonsKey =
                context.getString(R.string.ignore_hardware_media_buttons_key);
    }

    @Override
    public void initPlayer() {
        super.initPlayer();
        destroyPlayer(); // release previously used resources

        mediaSession.setActive(true);
        mediaSession.setCallback(sessionCallback);

        final ExoPlayer exoPlayer = player.getExoPlayer();
        if (exoPlayer != null) {
            attachedExoPlayer = exoPlayer;
            exoPlayer.addListener(exoPlayerListener);
        }

        // listen to changes to ignore_hardware_media_buttons_key
        updateShouldIgnoreHardwareMediaButtons(player.getPrefs());
        player.getPrefs().registerOnSharedPreferenceChangeListener(this);

        // force updating media session actions by resetting the previous ones
        prevNotificationActions = List.of();
        updateMediaSessionActions();
        pushMetadata();
        pushQueueWindow();
        pushRepeatShuffle();
        pushPlaybackState();
    }

    @Override
    public void destroyPlayer() {
        super.destroyPlayer();
        player.getPrefs().unregisterOnSharedPreferenceChangeListener(this);
        if (attachedExoPlayer != null) {
            attachedExoPlayer.removeListener(exoPlayerListener);
            attachedExoPlayer = null;
        }
        mediaSession.setCallback(null);
        mediaSession.setActive(false);
        prevNotificationActions = List.of();
    }

    @Override
    public void onThumbnailLoaded(@Nullable final Bitmap bitmap) {
        super.onThumbnailLoaded(bitmap);
        // the thumbnail is now loaded: push the metadata again so it contains the album art
        pushMetadata();
    }


    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String key) {
        if (key == null || key.equals(ignoreHardwareMediaButtonsKey)) {
            updateShouldIgnoreHardwareMediaButtons(sharedPreferences);
        }
    }

    public void updateShouldIgnoreHardwareMediaButtons(final SharedPreferences sharedPreferences) {
        shouldIgnoreHardwareMediaButtons =
                sharedPreferences.getBoolean(ignoreHardwareMediaButtonsKey, false);
    }


    public void handleMediaButtonIntent(final Intent intent) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
    }

    public Optional<MediaSessionCompat.Token> getSessionToken() {
        return Optional.of(mediaSession.getSessionToken());
    }


    private void pushPlaybackState() {
        if (!mediaSession.isActive()) {
            return;
        }
        final ExoPlayer exoPlayer = player.getExoPlayer();
        if (exoPlayer == null) {
            return;
        }

        final int state;
        switch (exoPlayer.getPlaybackState()) {
            case ExoPlayer.STATE_BUFFERING:
                state = exoPlayer.getPlayWhenReady()
                        ? PlaybackStateCompat.STATE_BUFFERING : PlaybackStateCompat.STATE_PAUSED;
                break;
            case ExoPlayer.STATE_READY:
                state = exoPlayer.isPlaying()
                        ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
                break;
            case ExoPlayer.STATE_ENDED:
                state = PlaybackStateCompat.STATE_STOPPED;
                break;
            case ExoPlayer.STATE_IDLE:
            default:
                state = PlaybackStateCompat.STATE_NONE;
                break;
        }

        final PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder()
                .setActions(BASE_ACTIONS)
                .setState(state, exoPlayer.getCurrentPosition(),
                        exoPlayer.getPlaybackParameters().speed, SystemClock.elapsedRealtime())
                .setBufferedPosition(exoPlayer.getBufferedPosition())
                .setActiveQueueItemId(Optional.ofNullable(player.getPlayQueue())
                        .map(queue -> (long) queue.getIndex())
                        .orElse(-1L));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // on Android 13+ the system derives the notification's custom actions from the custom
            // playback state actions (this is what the connector's custom action providers did)
            for (final NotificationActionData data : prevNotificationActions) {
                builder.addCustomAction(new PlaybackStateCompat.CustomAction.Builder(
                        data.action(), data.name(), data.icon()).build());
            }
        }

        mediaSession.setPlaybackState(builder.build());
    }

    private void pushMetadata() {
        if (!mediaSession.isActive()) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "pushMetadata called");
        }

        // set title and artist
        final MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, player.getVideoTitle())
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, player.getUploaderName());

        // set duration (-1 for livestreams or if unknown, see the METADATA_KEY_DURATION docs)
        final long duration = player.getCurrentStreamInfo()
                .filter(info -> !StreamTypeUtil.isLiveStream(info.getStreamType()))
                .map(info -> info.getDuration() * 1000L)
                .orElse(-1L);
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration);

        // set album art, unless the user asked not to, or there is no thumbnail available
        final boolean showThumbnail = player.getPrefs().getBoolean(
                context.getString(R.string.show_thumbnail_key), true);
        Optional.ofNullable(player.getThumbnail())
                .filter(bitmap -> showThumbnail)
                .ifPresent(bitmap -> {
                    builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap);
                    builder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap);
                });

        mediaSession.setMetadata(builder.build());
    }

    private void pushRepeatShuffle() {
        if (!mediaSession.isActive()) {
            return;
        }
        final ExoPlayer exoPlayer = player.getExoPlayer();
        if (exoPlayer == null) {
            return;
        }
        final int sessionRepeatMode;
        switch (exoPlayer.getRepeatMode()) {
            case androidx.media3.common.Player.REPEAT_MODE_ONE:
                sessionRepeatMode = PlaybackStateCompat.REPEAT_MODE_ONE;
                break;
            case androidx.media3.common.Player.REPEAT_MODE_ALL:
                sessionRepeatMode = PlaybackStateCompat.REPEAT_MODE_ALL;
                break;
            case androidx.media3.common.Player.REPEAT_MODE_OFF:
            default:
                sessionRepeatMode = PlaybackStateCompat.REPEAT_MODE_NONE;
                break;
        }
        mediaSession.setRepeatMode(sessionRepeatMode);
        mediaSession.setShuffleMode(exoPlayer.getShuffleModeEnabled()
                ? PlaybackStateCompat.SHUFFLE_MODE_ALL : PlaybackStateCompat.SHUFFLE_MODE_NONE);
    }

    private void pushQueueWindow() {
        if (!mediaSession.isActive()) {
            return;
        }
        final int windowCount = Optional.ofNullable(player.getPlayQueue())
                .map(PlayQueue::size)
                .orElse(0);
        if (windowCount == 0) {
            mediaSession.setQueue(Collections.emptyList());
            return;
        }

        // Yes this is almost a copypasta, got a problem with that? =\
        final int currentWindowIndex = player.getPlayQueue().getIndex();
        final int queueSize = Math.min(MAX_QUEUE_SIZE, windowCount);
        final int startIndex = Util.constrainValue(currentWindowIndex - ((queueSize - 1) / 2), 0,
                windowCount - queueSize);

        final List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
        for (int i = startIndex; i < startIndex + queueSize; i++) {
            queue.add(new MediaSessionCompat.QueueItem(getQueueMetadata(i), i));
        }
        mediaSession.setQueue(queue);
    }

    @Nullable
    private MediaDescriptionCompat getQueueMetadata(final int index) {
        if (player.getPlayQueue() == null) {
            return null;
        }
        final PlayQueueItem item = player.getPlayQueue().getItem(index);
        if (item == null) {
            return null;
        }

        final MediaDescriptionCompat.Builder descBuilder = new MediaDescriptionCompat.Builder()
                .setMediaId(String.valueOf(index))
                .setTitle(item.getTitle())
                .setSubtitle(item.getUploader());

        // set additional metadata for A2DP/AVRCP (Audio/Video Bluetooth profiles)
        final Bundle additionalMetadata = new Bundle();
        additionalMetadata.putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getTitle());
        additionalMetadata.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, item.getUploader());
        additionalMetadata
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, item.getDuration() * 1000);
        additionalMetadata.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, index + 1L);
        additionalMetadata
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, player.getPlayQueue().size());
        descBuilder.setExtras(additionalMetadata);

        try {
            descBuilder.setIconUri(Uri.parse(
                    ImageStrategy.choosePreferredImage(item.getThumbnails())));
        } catch (final Throwable e) {
            // no thumbnail available at all, or the user disabled image loading,
            // or the obtained url is not a valid `Uri`
        }

        return descBuilder.build();
    }


    private void updateMediaSessionActions() {
        // On Android 13+ (or Android T or API 33+) the actions in the player notification can't be
        // controlled directly anymore, but are instead derived from custom media session actions.
        // However the system allows customizing only two of these actions, since the other three
        // are fixed to play-pause-buffering, previous, next.

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Although setting media session actions on older android versions doesn't seem to
            // cause any trouble, it also doesn't seem to do anything, so we don't do anything to
            // save battery. Check out NotificationUtil.updateActions() to see what happens on
            // older android versions.
            return;
        }

        if (!mediaSession.isActive()) {
            // mediaSession will be inactive after destroyPlayer is called
            return;
        }

        // only use the fourth and fifth actions (the settings page also shows only the last 2 on
        // Android 13+)
        final List<NotificationActionData> newNotificationActions = IntStream.of(3, 4)
                .map(i -> player.getPrefs().getInt(
                        player.getContext().getString(NotificationConstants.SLOT_PREF_KEYS[i]),
                        NotificationConstants.SLOT_DEFAULTS[i]))
                .mapToObj(action -> NotificationActionData
                        .fromNotificationActionEnum(player, action))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // avoid costly notification actions update, if nothing changed from last time
        if (!newNotificationActions.equals(prevNotificationActions)) {
            prevNotificationActions = newNotificationActions;
            // publish the new custom actions as part of the playback state
            pushPlaybackState();
        }
    }

    @Override
    public void onBlocked() {
        super.onBlocked();
        updateMediaSessionActions();
    }

    @Override
    public void onPlaying() {
        super.onPlaying();
        updateMediaSessionActions();
    }

    @Override
    public void onBuffering() {
        super.onBuffering();
        updateMediaSessionActions();
    }

    @Override
    public void onPaused() {
        super.onPaused();
        updateMediaSessionActions();
    }

    @Override
    public void onPausedSeek() {
        super.onPausedSeek();
        updateMediaSessionActions();
    }

    @Override
    public void onCompleted() {
        super.onCompleted();
        updateMediaSessionActions();
    }

    @Override
    public void onRepeatModeChanged(@RepeatMode final int repeatMode) {
        super.onRepeatModeChanged(repeatMode);
        updateMediaSessionActions();
    }

    @Override
    public void onShuffleModeEnabledChanged(final boolean shuffleModeEnabled) {
        super.onShuffleModeEnabledChanged(shuffleModeEnabled);
        updateMediaSessionActions();
    }

    @Override
    public void onBroadcastReceived(final Intent intent) {
        super.onBroadcastReceived(intent);
        if (ACTION_RECREATE_NOTIFICATION.equals(intent.getAction())) {
            // the notification actions changed
            updateMediaSessionActions();
        }
    }

    @Override
    public void onMetadataChanged(@NonNull final StreamInfo info) {
        super.onMetadataChanged(info);
        updateMediaSessionActions();
    }

    @Override
    public void onPlayQueueEdited() {
        super.onPlayQueueEdited();
        updateMediaSessionActions();
    }
}
