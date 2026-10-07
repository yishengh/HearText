package com.yishenghuang.heartext.tts

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** A mono PCM stream: offsets and playback position are sample/frame counts. */
internal suspend fun streamPcm(
    sampleCount: Int,
    awaitPlayable: suspend () -> Unit,
    write: (offset: Int, count: Int) -> Int,
    playedFrames: () -> Long
) {
    var offset = 0
    var stalls = 0
    while (offset < sampleCount) {
        currentCoroutineContext().ensureActive()
        awaitPlayable()
        val written = write(offset, minOf(2048, sampleCount - offset))
        check(written >= 0 && written <= sampleCount - offset) { "PCM output failed" }
        if (written == 0) {
            check(++stalls < 1_000) { "PCM output stalled" }
            delay(10)
        } else {
            offset += written
            stalls = 0
        }
    }
    // Enqueuing the final buffer does not mean the speaker has consumed it.
    var previous = -1L
    stalls = 0
    while (true) {
        currentCoroutineContext().ensureActive()
        awaitPlayable()
        val played = playedFrames()
        if (played >= sampleCount) return
        stalls = if (played == previous) stalls + 1 else 0
        check(stalls < 1_000) { "PCM playback stalled" }
        previous = played
        delay(10)
    }
}
