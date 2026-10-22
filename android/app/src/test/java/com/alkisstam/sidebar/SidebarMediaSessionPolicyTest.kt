package com.alkisstam.sidebar

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SidebarMediaSessionPolicyTest {
    private fun candidate(id: String, pkg: String, activity: MediaActivity, update: Long = 0) =
        MediaCandidate(id, pkg, activity, update)

    @Test fun unselectedPlayingAppCannotDisplaceSelectedPausedApp() {
        val candidates = listOf(
            candidate("other", "other.app", MediaActivity.PLAYING, 100),
            candidate("selected", "music.app", MediaActivity.PAUSED, 10)
        )
        assertEquals("selected", selectMediaSession(candidates, setOf("music.app")))
    }

    @Test fun noSelectionNeverChoosesAnApp() {
        assertNull(selectMediaSession(listOf(candidate("a", "music.app", MediaActivity.PLAYING)), emptySet()))
    }

    @Test fun playingSessionWinsOverMoreRecentlyPausedSession() {
        val candidates = listOf(
            candidate("paused", "podcast.app", MediaActivity.PAUSED, 100),
            candidate("playing", "music.app", MediaActivity.PLAYING, 10)
        )
        assertEquals("playing", selectMediaSession(candidates, setOf("music.app", "podcast.app")))
    }

    @Test fun bufferingSessionWinsOverPausedSession() {
        val candidates = listOf(
            candidate("paused", "music.app", MediaActivity.PAUSED, 100),
            candidate("buffering", "music.app", MediaActivity.TRANSITIONING, 10)
        )
        assertEquals("buffering", selectMediaSession(candidates, setOf("music.app")))
    }

    @Test fun newestSessionWinsAmongEquallyActiveApps() {
        val candidates = listOf(
            candidate("old", "music.app", MediaActivity.PLAYING, 10),
            candidate("new", "podcast.app", MediaActivity.PLAYING, 100)
        )
        assertEquals("new", selectMediaSession(candidates, setOf("music.app", "podcast.app")))
    }

    @Test fun stoppedOrDestroyedSessionsAreNotShown() {
        assertNull(selectMediaSession(listOf(candidate("stopped", "music.app", MediaActivity.INACTIVE)), setOf("music.app")))
        assertNull(selectMediaSession(emptyList<MediaCandidate<String>>(), setOf("music.app")))
    }

    @Test fun removingAnAppFromSelectionImmediatelyExcludesIt() {
        val candidates = listOf(candidate("playing", "music.app", MediaActivity.PLAYING))
        assertEquals("playing", selectMediaSession(candidates, setOf("music.app")))
        assertNull(selectMediaSession(candidates, setOf("podcast.app")))
    }

    @Test fun directPlayAndPauseArePreferredWhenSupported() {
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE
        assertEquals(MediaToggleAction.PLAY, selectMediaToggleAction(actions, false))
        assertEquals(MediaToggleAction.PAUSE, selectMediaToggleAction(actions, true))
    }

    @Test fun toggleOnlyAppsReceiveATargetedToggle() {
        assertEquals(MediaToggleAction.TOGGLE, selectMediaToggleAction(PlaybackState.ACTION_PLAY_PAUSE, false))
        assertEquals(MediaToggleAction.TOGGLE, selectMediaToggleAction(PlaybackState.ACTION_PLAY_PAUSE, true))
    }

    @Test fun unsupportedPlayOrPauseCannotBeSent() {
        assertEquals(MediaToggleAction.NONE, selectMediaToggleAction(PlaybackState.ACTION_PLAY, true))
        assertEquals(MediaToggleAction.NONE, selectMediaToggleAction(PlaybackState.ACTION_PAUSE, false))
        assertEquals(MediaToggleAction.NONE, selectMediaToggleAction(0L, false))
        assertEquals(MediaToggleAction.NONE, selectMediaToggleAction(0L, true))
    }
}
