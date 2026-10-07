package com.yishenghuang.heartext.data

import java.io.File
import java.io.RandomAccessFile

/** Reads ModelProto metadata without loading its potentially large graph into memory. */
internal object OnnxMetadata {
    fun read(file: File): Map<String, String> = RandomAccessFile(file, "r").use { input ->
        require(input.length() in 1..(1024L * 1024 * 1024)) { "Invalid ONNX size" }
        val metadata = linkedMapOf<String, String>()
        var irVersion = 0L
        var graph = false
        var opset = false
        while (input.filePointer < input.length()) {
            val tag = readVarint(input)
            require(tag > 0) { "Invalid ONNX tag" }
            val field = tag ushr 3
            when ((tag and 7).toInt()) {
                0 -> { val value = readVarint(input); if (field == 1L) irVersion = value }
                1 -> skip(input, 8)
                5 -> skip(input, 4)
                2 -> {
                    val length = readVarint(input)
                    require(length >= 0 && length <= input.length() - input.filePointer) { "Truncated ONNX field" }
                    if (field == 14L) {
                        require(length <= 65_536 && metadata.size < 256) { "ONNX metadata is too large" }
                        val end = input.filePointer + length
                        var key: String? = null
                        var value: String? = null
                        while (input.filePointer < end) {
                            val entryTag = readVarint(input)
                            require(entryTag == 10L || entryTag == 18L) { "Invalid metadata entry" }
                            val size = readVarint(input)
                            require(size in 0..(end - input.filePointer)) { "Truncated metadata entry" }
                            val bytes = ByteArray(size.toInt())
                            input.readFully(bytes)
                            if (entryTag == 10L) key = bytes.toString(Charsets.UTF_8) else value = bytes.toString(Charsets.UTF_8)
                        }
                        require(!key.isNullOrBlank() && value != null && !metadata.containsKey(key)) { "Invalid metadata key" }
                        metadata[key] = value
                    } else {
                        if (field == 7L) graph = length > 0
                        if (field == 8L) opset = length > 0
                        skip(input, length)
                    }
                }
                else -> error("Unsupported ONNX wire type")
            }
        }
        require(irVersion > 0 && graph && opset) { "Missing ONNX model structure" }
        metadata
    }

    fun supportsVits(file: File): Boolean = runCatching {
        val data = read(file)
        require(data["sample_rate"]?.toIntOrNull() in 4_000..192_000)
        require(data["n_speakers"]?.toIntOrNull() in 1..10_000)
        require(!data["language"].isNullOrBlank() && !data["comment"].isNullOrBlank())
        if (data.getValue("comment").contains("melo")) require((data["version"]?.toIntOrNull() ?: 0) >= 2)
        for (key in listOf("add_blank", "speaker_id", "version", "jieba", "blank_id", "bos_id", "eos_id", "use_eos_bos", "pad_id", "has_g2pw")) {
            if (key in data) require(data[key]?.toIntOrNull() != null)
        }
        true
    }.getOrDefault(false)

    private fun skip(input: RandomAccessFile, length: Long) {
        require(length >= 0 && length <= input.length() - input.filePointer) { "Truncated ONNX" }
        input.seek(input.filePointer + length)
    }

    private fun readVarint(input: RandomAccessFile): Long {
        var result = 0L
        for (shift in 0..63 step 7) {
            val byte = input.readUnsignedByte()
            require(shift < 63 || byte <= 1) { "Invalid protobuf integer" }
            result = result or ((byte and 127).toLong() shl shift)
            if (byte and 128 == 0) return result
        }
        error("Invalid protobuf integer")
    }
}
