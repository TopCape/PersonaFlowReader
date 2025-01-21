package dataAccess

import java.io.IOException
import java.io.RandomAccessFile
import java.io.UTFDataFormatException
import java.nio.charset.StandardCharsets

// taken from: https://stackoverflow.com/questions/42309110/reading-a-single-utf-8-character-with-randomaccessfile
data class LongCharRandomAccessFile(
    var seekPointer: Int,
    val source: RandomAccessFile
) {
    var numOfBytes: Int = 0

    fun seek(shift: Int) {
        seekPointer += shift
        if (seekPointer < 0) seekPointer = 0
        try {
            source.seek(seekPointer.toLong())
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun byteCheck(chr: Int): Int {
        if (chr == -1) return 1 // eof

        var i = 1 // theres always atleast one byte
        if (chr >= 192) i++ // 2 bytes

        if (chr >= 224) i++ // 3 bytes

        if (chr >= 240) i++ // 4 bytes

        if (chr >= 128 && chr <= 191) i = -1 // woops, we're halfway through a char!

        return i
    }

    fun nextChar(): Char {
        try {
            seekPointer++
            var i = source.read()

            numOfBytes = byteCheck(i)
            if (numOfBytes == -1) {
                var malformed = true
                for (k in 0..3) { // Iterate 3 times.
                    // we only iterate 3 times because the maximum size of a utf-8 char is 4 bytes.
                    // any further and we may possibly interrupt the other chars.
                    seek(-1)
                    i = source.read()
                    numOfBytes = byteCheck(i)
                    if (numOfBytes != -1) {
                        malformed = false
                        break
                    }
                }
                if (malformed) {
                    seek(3)
                    throw UTFDataFormatException("Malformed UTF char at position: $seekPointer")
                }
            }

            val chrs = ByteArray(numOfBytes)
            chrs[0] = i.toByte()

            for (j in 1..<chrs.size) {
                seekPointer++
                chrs[j] = source.read().toByte()
            }

            return if (i > -1) String(chrs, StandardCharsets.UTF_8)[0] else '\u0000' // EOF character is -1.
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return '\u0000'
    }
}