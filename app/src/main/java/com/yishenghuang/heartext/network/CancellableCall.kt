package com.yishenghuang.heartext.network

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Response
import java.io.IOException

/** Keep cancellation attached until the body has been consumed, not just until headers arrive. */
internal suspend fun <T> Call.consumeCancellable(consume: (Response) -> T): T = coroutineScope {
    val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            this@consumeCancellable.cancel()
        }
    }
    try {
        withContext(Dispatchers.IO) {
            ensureActive()
            try {
                execute().use(consume).also { ensureActive() }
            } catch (error: IOException) {
                // Call.cancel() closes the socket. Preserve coroutine cancellation semantics
                // instead of reporting the resulting SocketException as a network failure.
                ensureActive()
                throw error
            }
        }
    } finally {
        cancellation.cancel()
    }
}
