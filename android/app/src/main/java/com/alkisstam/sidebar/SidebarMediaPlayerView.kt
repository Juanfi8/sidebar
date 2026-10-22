package com.alkisstam.sidebar

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils

/** A media-session remote, not an audio player. It only observes user-selected apps. */
internal class SidebarMediaPlayerView(
    context: Context,
    private val accentColor: Int,
    private val textColor: Int,
    cardColor: Int,
    private val iconTypeface: Typeface,
    private val onOpenApp: (String) -> Unit,
    private val onConfigure: () -> Unit,
    private val onGrantAccess: () -> Unit
) : LinearLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
    private val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val watched = linkedMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val destroyed = mutableSetOf<MediaSession.Token>()
    private var listening = false
    private var current: MediaController? = null
    private var session: MediaSession.Token? = null
    private enum class Command { PREVIOUS, TOGGLE, NEXT }

    private val status = TextView(context).apply {
        textSize = 12f
        setTextColor(textColor)
        setPadding(dp(4), dp(8), dp(4), dp(8))
    }
    private val artwork = object : ImageView(context) {
        override fun getAccessibilityClassName(): CharSequence = "android.widget.Button"
    }.apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = shape(ColorUtils.setAlphaComponent(textColor, 24), 8)
        clipToOutline = true
        isFocusable = true
        setOnClickListener { openPlayingApp() }
    }
    private val title = TextView(context).apply {
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(textColor)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val artist = TextView(context).apply {
        textSize = 11f
        setTextColor(textColor)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    private val track = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(artwork, LayoutParams(dp(40), dp(40)))
        addView(LinearLayout(context).apply {
            orientation = VERTICAL
            addView(title)
            addView(artist)
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) })
    }
    private fun button(icon: String, label: String, command: Command) = object : TextView(context) {
        override fun getAccessibilityClassName(): CharSequence = "android.widget.Button"
    }.apply {
        text = icon
        typeface = iconTypeface
        textSize = 24f
        gravity = Gravity.CENTER
        includeFontPadding = false
        contentDescription = label
        setTextColor(textColor)
        background = shape(Color.TRANSPARENT, 22)
        isFocusable = true
        setOnClickListener { perform(command) }
    }
    private val previous = button("\uE045", "Previous track", Command.PREVIOUS)
    private val toggle = button("\uE037", "Play", Command.TOGGLE).apply {
        background = shape(accentColor, 22)
        setTextColor(
            if (ColorUtils.calculateLuminance(accentColor) > 0.179) Color.BLACK else Color.WHITE)
    }
    private val next = button("\uE044", "Next track", Command.NEXT)
    private val controls = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        for (button in listOf(previous, toggle, next)) {
            addView(button, LayoutParams(0, dp(44), 1f).apply {
                marginStart = dp(3); marginEnd = dp(3)
            })
        }
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        if (isAttachedToWindow) replaceSessions(sessions.orEmpty())
    }
    private val accessChanged: () -> Unit = { if (isAttachedToWindow) startListening() }
    private val settingsChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.MEDIA_APPS || key == Prefs.SHOW_MEDIA_PLAYER) {
            handler.post { if (isAttachedToWindow) startListening() }
        }
    }

    init {
        orientation = VERTICAL
        background = shape(cardColor, 16)
        setPadding(dp(10), dp(10), dp(10), dp(6))
        addView(status)
        addView(track)
        addView(controls, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(4) })
        track.visibility = GONE
        controls.visibility = GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(settingsChanged)
        SidebarMediaAccess.addObserver(accessChanged)
        startListening()
    }

    override fun onDetachedFromWindow() {
        stopListening()
        prefs.unregisterOnSharedPreferenceChangeListener(settingsChanged)
        SidebarMediaAccess.removeObserver(accessChanged)
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    private fun startListening() {
        stopListening()
        visibility = if (prefs.getBoolean(Prefs.SHOW_MEDIA_PLAYER, false)) VISIBLE else GONE
        if (visibility != VISIBLE) return
        if (Prefs.mediaApps(prefs).isEmpty()) {
            showStatus("Choose media apps in Sidebar", onConfigure)
            return
        }
        if (!SidebarMediaAccess.isGranted(context)) {
            showStatus("Grant notification access to use media controls", onGrantAccess)
            return
        }
        try {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, SidebarMediaAccess.component(context), handler)
            listening = true
            refreshSessions()
        } catch (_: SecurityException) {
            stopListening()
            showStatus("Grant notification access to use media controls", onGrantAccess)
        }
    }

    private fun stopListening() {
        if (listening) runCatching { manager.removeOnActiveSessionsChangedListener(sessionsChanged) }
        listening = false
        watched.values.forEach { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        watched.clear()
        destroyed.clear()
        current = null
        session = null
        artwork.setImageDrawable(null)
        title.text = ""
        artist.text = ""
    }

    private fun refreshSessions() {
        if (!isAttachedToWindow || !listening) return
        if (!SidebarMediaAccess.isGranted(context)) {
            stopListening()
            showStatus("Grant notification access to use media controls", onGrantAccess)
            return
        }
        try {
            replaceSessions(manager.getActiveSessions(SidebarMediaAccess.component(context)))
        } catch (_: SecurityException) {
            stopListening()
            showStatus("Grant notification access to use media controls", onGrantAccess)
        }
    }

    private fun replaceSessions(controllers: List<MediaController>) {
        if (!listening) return
        val allowed = Prefs.mediaApps(prefs)
        val selected = controllers.filter { it.packageName in allowed && it.sessionToken !in destroyed }
        val tokens = selected.map { it.sessionToken }.toSet()
        watched.keys.filter { it !in tokens }.forEach { token ->
            watched.remove(token)?.let { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        }
        for (controller in selected) {
            val token = controller.sessionToken
            if (token in watched) continue
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) { if (isAttachedToWindow) render() }
                override fun onPlaybackStateChanged(state: PlaybackState?) { if (isAttachedToWindow) render() }
                override fun onSessionDestroyed() {
                    destroyed.add(token)
                    refreshSessions()
                }
            }
            try {
                controller.registerCallback(callback, handler)
                watched[token] = controller to callback
            } catch (_: SecurityException) { }
        }
        render()
    }

    private fun activity(state: PlaybackState?): MediaActivity = when (state?.state) {
        PlaybackState.STATE_PLAYING, PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING -> MediaActivity.PLAYING
        PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING,
        PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> MediaActivity.TRANSITIONING
        PlaybackState.STATE_PAUSED -> MediaActivity.PAUSED
        else -> MediaActivity.INACTIVE
    }

    private fun render() {
        if (!listening || !isAttachedToWindow) return
        if (!SidebarMediaAccess.isGranted(context)) { startListening(); return }
        val allowed = Prefs.mediaApps(prefs)
        val candidates = watched.values.filter { it.first.packageName in allowed }.map { (controller, _) ->
            val state = controller.playbackState
            MediaCandidate(controller, controller.packageName, activity(state), state?.lastPositionUpdateTime ?: 0L)
        }
        val controller = selectMediaSession(candidates, allowed)
        current = controller
        if (controller == null) {
            session = null
            artwork.setImageDrawable(null)
            title.text = ""
            artist.text = ""
            showStatus("Play music in a selected app")
            return
        }
        status.visibility = GONE
        track.visibility = VISIBLE
        controls.visibility = VISIBLE
        val metadata = controller.metadata
        fun text(key: String) = metadata?.getString(key)?.takeIf { it.isNotBlank() }
        val trackTitle = text(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.description?.title?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown track"
        val trackArtist = text(MediaMetadata.METADATA_KEY_ARTIST)
            ?: text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.description?.subtitle?.toString()?.takeIf { it.isNotBlank() } ?: "Unknown artist"
        title.text = trackTitle
        artist.text = trackArtist
        track.contentDescription = "$trackTitle, $trackArtist"
        val bitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bitmap != null && !bitmap.isRecycled) {
            artwork.setImageBitmap(bitmap)
        } else {
            artwork.setImageDrawable(runCatching { context.packageManager.getApplicationIcon(controller.packageName) }.getOrNull())
        }
        val appName = runCatching {
            val info = context.packageManager.getApplicationInfo(controller.packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(controller.packageName)
        artwork.contentDescription = "Open $appName"
        session = controller.sessionToken
        val state = controller.playbackState
        val playing = activity(state) in setOf(MediaActivity.PLAYING, MediaActivity.TRANSITIONING)
        toggle.text = if (playing) "\uE034" else "\uE037"
        toggle.contentDescription = if (playing) "Pause" else "Play"
        setEnabled(previous, supports(state, Command.PREVIOUS))
        setEnabled(toggle, supports(state, Command.TOGGLE))
        setEnabled(next, supports(state, Command.NEXT))
    }

    private fun openPlayingApp() {
        val controller = current ?: return
        if (!prefs.getBoolean(Prefs.SHOW_MEDIA_PLAYER, false) ||
            !SidebarMediaAccess.isGranted(context) || controller.packageName !in Prefs.mediaApps(prefs)) {
            startListening()
            return
        }
        if (session != controller.sessionToken || controller.sessionToken in destroyed) return
        onOpenApp(controller.packageName)
    }

    private fun supports(state: PlaybackState?, command: Command): Boolean {
        val actions = state?.actions ?: return false
        val required = when (command) {
            Command.PREVIOUS -> PlaybackState.ACTION_SKIP_TO_PREVIOUS
            Command.NEXT -> PlaybackState.ACTION_SKIP_TO_NEXT
            Command.TOGGLE -> {
                val playing = activity(state) in setOf(MediaActivity.PLAYING, MediaActivity.TRANSITIONING)
                return selectMediaToggleAction(actions, playing) != MediaToggleAction.NONE
            }
        }
        return actions and required != 0L
    }

    private fun perform(command: Command) {
        val controller = current ?: return
        // Recheck on every tap, including changes made while the panel was open.
        if (!SidebarMediaAccess.isGranted(context) || controller.packageName !in Prefs.mediaApps(prefs)) {
            startListening()
            return
        }
        val state = controller.playbackState
        if (session != controller.sessionToken || !supports(state, command)) return
        try {
            val transport = controller.transportControls
            when (command) {
                Command.PREVIOUS -> transport.skipToPrevious()
                Command.NEXT -> transport.skipToNext()
                Command.TOGGLE -> {
                    val playing = activity(state) in setOf(MediaActivity.PLAYING, MediaActivity.TRANSITIONING)
                    when (selectMediaToggleAction(state?.actions ?: 0L, playing)) {
                        MediaToggleAction.PLAY -> transport.play()
                        MediaToggleAction.PAUSE -> transport.pause()
                        MediaToggleAction.TOGGLE -> {
                            // Address only this selected session, never the device's global media target.
                            controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                            controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                        }
                        MediaToggleAction.NONE -> return
                    }
                }
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Media controls are unavailable", Toast.LENGTH_SHORT).show()
            refreshSessions()
        }
    }

    private fun showStatus(message: String, onClick: (() -> Unit)? = null) {
        current = null
        track.visibility = GONE
        controls.visibility = GONE
        status.visibility = VISIBLE
        status.text = message
        status.setOnClickListener(if (onClick == null) null else View.OnClickListener { onClick() })
        status.isClickable = onClick != null
        status.isFocusable = onClick != null
    }

    private fun setEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.35f
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
}
