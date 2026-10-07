package com.yishenghuang.heartext

import com.yishenghuang.heartext.tts.streamPcm
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PcmStreamTest {
    @Test fun partialWritesAndBackPressurePreserveEverySampleAndDrainTail() = runBlocking {
        val offsets = mutableListOf<Int>()
        var queued = 0
        var played = 0L
        var calls = 0
        streamPcm(7000, {}, { offset, count ->
            offsets += offset
            if (++calls == 2) 0 else minOf(count, 517).also { queued += it }
        }, {
            assertEquals(7000, queued)
            played = minOf(7000L, played + 1000)
            played
        })
        assertEquals(7000L, played)
        assertEquals(517, offsets[1])
        assertEquals(517, offsets[2])
        assertEquals(7000, queued)
    }

    @Test fun cancelledTailWaitDoesNotHang() = runBlocking {
        val draining = CompletableDeferred<Unit>()
        val job = launch {
            streamPcm(20, {}, { _, count -> count }, { draining.complete(Unit); 0 })
        }
        draining.await()
        withTimeout(1000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
    }

    @Test fun pausedStreamWritesNothingUntilResumed() = runBlocking {
        val resumed = CompletableDeferred<Unit>()
        var writes = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            streamPcm(50, { resumed.await() }, { _, count -> writes++; count }, { 50 })
        }
        assertEquals(0, writes)
        resumed.complete(Unit)
        job.join()
        assertEquals(1, writes)
    }

    @Test fun outputErrorIsNotMistakenForSuccessfulPlayback() = runBlocking {
        val failure = runCatching { streamPcm(50, {}, { _, _ -> -6 }, { 0 }) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }
}
