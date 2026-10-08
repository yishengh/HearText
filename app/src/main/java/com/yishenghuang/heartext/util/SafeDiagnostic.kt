package com.yishenghuang.heartext.util

/** Engine exceptions can contain spoken text or file paths, including in nested causes. */
internal fun diagnosticException(original: Throwable): Throwable =
    RuntimeException("Playback failure: ${original.javaClass.name}").apply {
        stackTrace = original.stackTrace.copyOf()
    }

/** Only fixed diagnostic categories cross the reporting boundary; no voice IDs or free text. */
internal fun diagnosticKeys(values: Map<String, String>): Map<String, String> = buildMap {
    values["tts_engine"]?.takeIf { it == "system" || it == "offline" }?.let { put("tts_engine", it) }
    put("tts_fallback", if (values["tts_fallback"] == "system") "system" else "none")
}
