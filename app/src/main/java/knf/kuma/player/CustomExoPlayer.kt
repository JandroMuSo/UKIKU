package knf.kuma.player

import android.annotation.TargetApi
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import knf.kuma.R
import knf.kuma.commons.BypassUtil
import knf.kuma.commons.EAHelper
import knf.kuma.commons.doOnUI
import knf.kuma.commons.noCrash
import knf.kuma.custom.GenericActivity
import knf.kuma.database.CacheDB
import knf.kuma.pojos.QueueObject
import kotlinx.android.synthetic.main.exo_playback_control_view.*
import kotlinx.android.synthetic.main.exo_player.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.anko.doAsync
import xdroid.toaster.Toaster
import java.util.concurrent.TimeUnit

class CustomExoPlayer : GenericActivity(), Player.EventListener {
    private var exoPlayer: SimpleExoPlayer? = null
    private var currentPosition = C.TIME_UNSET
    private var resumeWindow = C.INDEX_UNSET

    private var isEnding = false
    private var listPosition = 0
    private var playList: List<QueueObject> = ArrayList()

    private var disposable: Disposable? = null
    private var lastPosition = 0L

    private val resizeMode: Int
        get() {
            return when (PreferenceManager.getDefaultSharedPreferences(this).getString("player_resize", "0")) {
                "0" -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                "1" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                "2" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                "3" -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(EAHelper.getTheme())
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        setContentView(R.layout.exo_player)
        window.decorView.setBackgroundColor(Color.BLACK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
            pip.visibility = View.VISIBLE
        pip.setOnClickListener { onPip() }
        skip.setOnClickListener { onSkip() }
        hideUI()
        player.resizeMode = resizeMode
        player.requestFocus()
        lifecycleScope.launch(Dispatchers.Main){
            if (savedInstanceState != null) {
                currentPosition = savedInstanceState.getLong("position", C.TIME_UNSET)
                listPosition = savedInstanceState.getInt("listPosition", 0)
            }else{
                intent.getStringExtra("title")?.let {
                    withContext(Dispatchers.IO){
                        CacheDB.INSTANCE.playerStateDAO().find(it)?.let {
                            currentPosition = it.position
                        }
                    }
                }
            }
            checkPlaylist(intent)
            initPlayer(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("position", currentPosition)
        if (listPosition != 0)
            outState.putInt("listPosition", listPosition)
    }

    private fun hideUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun initPlayer(intent: Intent) {
        if (exoPlayer == null) {
            lifecycleScope.launch(Dispatchers.Main) {
                video_title.text = intent.getStringExtra("title")
                val videoTrackSelectionFactory = AdaptiveTrackSelection.Factory()
                val trackSelector = DefaultTrackSelector(videoTrackSelectionFactory)
                val source: MediaSource
                if (intent.getBooleanExtra("isPlayList", false)) {
                    val sourceList = ArrayList<MediaSource>()
                    playList = withContext(Dispatchers.IO) { CacheDB.INSTANCE.queueDAO().getAllByAid(intent.getStringExtra("playlist")?:"empty") }
                    noCrash { video_title.text = playList[0].title() }
                    for (queueObject in playList) {
                        sourceList.add(ProgressiveMediaSource.Factory(DefaultDataSourceFactory(this@CustomExoPlayer, BypassUtil.userAgent, null)).createMediaSource(queueObject.createUri()))
                    }
                    source = ConcatenatingMediaSource(*sourceList.toTypedArray())
                } else
                    source = ProgressiveMediaSource.Factory(DefaultDataSourceFactory(this@CustomExoPlayer, BypassUtil.userAgent, null)).createMediaSource(intent.data)
                exoPlayer = ExoPlayerFactory.newSimpleInstance(this@CustomExoPlayer, DefaultRenderersFactory(this@CustomExoPlayer), trackSelector, DefaultLoadControl(), null, DefaultBandwidthMeter.Builder(this@CustomExoPlayer).build())
                player.player = exoPlayer
                exoPlayer?.addListener(this@CustomExoPlayer)
                val canResume = currentPosition != C.TIME_UNSET
                if (canResume)
                    exoPlayer?.seekTo(listPosition, currentPosition)
                exoPlayer?.prepare(source, !canResume, false)
                exoPlayer?.playWhenReady = true
                disposable = Observable.interval(1, TimeUnit.SECONDS).map { exoPlayer?.currentPosition?:0 }
                        .subscribeOn(AndroidSchedulers.from(exoPlayer?.applicationLooper, false))
                        .subscribe {
                            if (it > 0)
                                lastPosition = it
                        }
            }
        }
    }

    private fun releasePlayer() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
    }

    @TargetApi(Build.VERSION_CODES.N)
    internal fun onPip() {
        try {
            if (!isInPictureInPictureMode) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    currentPosition = exoPlayer?.currentPosition ?: 0
                    val params = PictureInPictureParams.Builder()
                            //.setAspectRatio(Rational(player.width, player.height))
                            .build()
                    enterPictureInPictureMode(params)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun onSkip() {
        exoPlayer?.seekTo(exoPlayer?.currentWindowIndex ?: 0, (exoPlayer?.currentPosition
                ?: 0) + 85000)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration?) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode) {
            runOnUiThread {
                lay_top.visibility = View.VISIBLE
                lay_bottom.visibility = View.VISIBLE
                player.useController = true
            }
            /*getApplication().startActivity(new Intent(this, getClass())
                    .putExtra("isReorder", true)
                    .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));*/
        } else {
            runOnUiThread {
                lay_top.visibility = View.GONE
                lay_bottom.visibility = View.GONE
                progress.visibility = View.GONE
                player.useController = false
            }
        }
    }

    private fun checkPlaylist(intent: Intent) {
        if (!intent.getBooleanExtra("isPlayList", false)) {
            exo_next.visibility = View.GONE
            exo_prev.visibility = View.GONE
        } else {
            exo_next.visibility = View.VISIBLE
            exo_prev.visibility = View.VISIBLE
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        exoPlayer?.playWhenReady = false
    }

    override fun onNewIntent(intent: Intent) {
        setIntent(intent)
        releasePlayer()
        currentPosition = 0
        resumeWindow = C.INDEX_UNSET
        checkPlaylist(intent)
        initPlayer(intent)
        super.onNewIntent(intent)
    }

    override fun onResume() {
        doOnUI { hideUI() }
        exoPlayer?.playWhenReady = true
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)
            return
        exoPlayer?.let {
            resumeWindow = it.currentWindowIndex
            currentPosition = it.currentPosition
            exoPlayer?.playWhenReady = false
        }
    }

    override fun onStop() {
        val state = PlayerState().apply {
            title = video_title.text.toString()
            position = if (!isEnding) {
                exoPlayer?.currentPosition?:0
            }else
                0
        }
        doAsync {
            CacheDB.INSTANCE.playerStateDAO().set(state)
        }
        super.onStop()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {

    }

    override fun onTracksChanged(trackGroups: TrackGroupArray, trackSelections: TrackSelectionArray) {

    }

    override fun onLoadingChanged(isLoading: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)
            return
        progress.post { progress.visibility = if (isLoading) View.VISIBLE else View.GONE }
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        if (playbackState == Player.STATE_READY)
            doOnUI { hideUI() }
        if (playbackState == Player.STATE_ENDED) {
            isEnding = true
            finish()
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {

    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {

    }

    override fun onPlayerError(error: ExoPlaybackException) {
        val exception: Exception? =
                when (error.type) {
                    ExoPlaybackException.TYPE_RENDERER -> error.rendererException
                    ExoPlaybackException.TYPE_UNEXPECTED -> error.unexpectedException
                    ExoPlaybackException.TYPE_SOURCE -> error.sourceException
                    else -> null
                }
        if (exception != null) {
            Toaster.toast("Error al reproducir: " + exception.message?.replace("%", "%%"), emptyArray<Any>())
            FirebaseCrashlytics.getInstance().recordException(exception)
        } else
            Toaster.toast("Error desconocido al reproducir")
        finish()
    }

    override fun onPositionDiscontinuity(reason: Int) {
        try {
            val latestPosition = exoPlayer?.currentWindowIndex ?: 0
            if (latestPosition != listPosition) {
                val state = PlayerState().apply {
                    title = playList[listPosition].title()
                    if (reason == 0) {
                        position = 0
                    } else if (reason in 1..2) {
                        position = lastPosition
                    }
                }
                doAsync {
                    CacheDB.INSTANCE.playerStateDAO().set(state)
                }
                listPosition = latestPosition
                video_title.text = playList[listPosition].title()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {

    }

    override fun onSeekProcessed() {

    }
}
