package com.yishenghuang.heartext

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.yishenghuang.heartext.data.TextPosition
import com.yishenghuang.heartext.tts.TtsController
import com.yishenghuang.heartext.tts.TtsMode
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PlaybackPositionTest {
    @Test fun pauseAndStopKeepSentencePositionAndDefaultRestartResumesIt() = runBlocking<Unit> {
        check(BuildConfig.APPLICATION_ID.endsWith(".validation"))
        val app = ApplicationProvider.getApplicationContext<HearTextApp>()
        val repository = app.container.bookRepository
        val playback = app.container.playbackCoordinator
        val file = File(app.cacheDir, "listen-position-${UUID.randomUUID()}.txt").apply {
            writeText("First sentence for this local listening check. Second sentence should be skipped. " +
                "Third sentence is the saved position that should be heard again after restarting. " +
                "The final sentence lets this test pause before the chapter completes.")
        }
        val book = repository.importFromUri(Uri.fromFile(file))
        val text = repository.loadChapterTexts(book).first().plainText
        val target = TtsController.splitSentencesWithRanges(text)[2].start
        ActivityScenario.launch(MainActivity::class.java).use {
            try {
                withContext(Dispatchers.Main) { playback.start(book.id, 0, TtsMode.SYSTEM, null, startSentenceIndex = 2) }
                withTimeout(15000) {
                    while (!playback.session.value.active || playback.session.value.spokenStart != target) delay(10)
                }
                withContext(Dispatchers.Main) { playback.pause() }
                withTimeout(5000) {
                    while (TextPosition.decode(repository.getBook(book.id)!!.locatorJson)?.character != target) delay(10)
                }
                withContext(Dispatchers.Main) { playback.stop() }
                withContext(Dispatchers.Main) { playback.start(book.id, 0, TtsMode.SYSTEM, null) }
                withTimeout(15000) {
                    while (!playback.session.value.active || playback.session.value.spokenStart != target) delay(10)
                }
                withContext(Dispatchers.Main) { playback.pause() }
                assertEquals(2, playback.session.value.sentenceIndex)
                assertEquals(target, playback.session.value.spokenStart)
            } finally {
                withContext(Dispatchers.Main) { playback.stop() }
                repository.deleteBook(book.id)
                file.delete()
            }
        }
    }
}
