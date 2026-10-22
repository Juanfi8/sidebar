package com.alkisstam.sidebar

import android.media.session.PlaybackState

internal enum class MediaActivity(val priority: Int) {
    INACTIVE(0), PAUSED(1), TRANSITIONING(2), PLAYING(3)
}

internal data class MediaCandidate<T>(
    val session: T,
    val packageName: String,
    val activity: MediaActivity,
    val lastUpdate: Long
)

internal fun <T> selectMediaSession(candidates: List<MediaCandidate<T>>, selectedPackages: Set<String>): T? =
    candidates.asSequence()
        .filter { it.packageName in selectedPackages && it.activity != MediaActivity.INACTIVE }
        .sortedWith(compareByDescending<MediaCandidate<T>> { it.activity.priority }.thenByDescending { it.lastUpdate })
        .firstOrNull()?.session

internal enum class MediaToggleAction { NONE, PLAY, PAUSE, TOGGLE }

internal fun selectMediaToggleAction(actions: Long, playing: Boolean): MediaToggleAction = when {
    playing && actions and PlaybackState.ACTION_PAUSE != 0L -> MediaToggleAction.PAUSE
    !playing && actions and PlaybackState.ACTION_PLAY != 0L -> MediaToggleAction.PLAY
    actions and PlaybackState.ACTION_PLAY_PAUSE != 0L -> MediaToggleAction.TOGGLE
    else -> MediaToggleAction.NONE
}
