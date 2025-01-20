package dataAccess

import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val LBA = 0x800;

/**
 * Reads a short from the file object and applies the correct endianness to it.
 * @param file object used to read file
 * @param byteOrder endianness of data
 * @return short read from the file object, in the correct endianness
 * @throws IOException random access file read io exception
 */
@Throws(IOException::class)
fun readShort(file: RandomAccessFile, byteOrder: ByteOrder): Short {
    val buffer = ByteArray(2)
    file.read(buffer)
    val byteBuffer = ByteBuffer.wrap(buffer)

    return byteBuffer.order(byteOrder).getShort()
}

/**
 * Reads an integer from the file object and applies the correct endianness to it.
 * @param file object used to read file
 * @param byteOrder endianness of data
 * @return integer read from the file object, in the correct endianness
 * @throws IOException random access file read io exception
 */
@Throws(IOException::class)
fun readInt(file: RandomAccessFile, byteOrder: ByteOrder): Int {
    val buffer = ByteArray(4)
    file.read(buffer)
    val byteBuffer = ByteBuffer.wrap(buffer)

    return byteBuffer.order(byteOrder).getInt()
}

/**
 * Writes a short to the file using the file object, applying the correct endianness to the value.
 * @param file object used to write to file
 * @param byteOrder endianness of data
 * @param value the short to write
 * @throws IOException random access file write io exception
 */
@Throws(IOException::class)
fun writeShort(file: RandomAccessFile, byteOrder: ByteOrder, value: Short) {
    if (byteOrder == ByteOrder.BIG_ENDIAN) {
        file.writeShort(value.toInt())
        return
    }
    val ogValue = ByteBuffer.allocate(2).order(byteOrder).putShort(value).array()
    file.writeShort(ByteBuffer.wrap(ogValue).getShort().toInt())
}

/**
 * Writes an int to the file using the file object, applying the correct endianness to the value.
 * @param file object used to write to file
 * @param byteOrder endianness of data
 * @param value the int to write
 * @throws IOException random access file write io exception
 */
@Throws(IOException::class)
fun writeInt(file: RandomAccessFile, byteOrder: ByteOrder, value: Int) {
    if (byteOrder == ByteOrder.BIG_ENDIAN) {
        file.writeInt(value)
        return
    }
    val ogValue = ByteBuffer.allocate(4).order(byteOrder).putInt(value).array()
    file.writeInt(ByteBuffer.wrap(ogValue).getInt())
}

/**
 * Writes 0s until the next multiple of LBA
 * @param file object used to write to file
 */
@Throws(IOException::class)
fun writePadding(file: RandomAccessFile) {
    while (file.filePointer % LBA != 0L) {
        file.writeInt(0)
    }
}

/**
 * Returns the extension of the file in path
 * @param path file path
 * @return the extension of the file
 */
fun getExtension(path: String): String? {
    if (path[path.length - 1] == '/') {
        println("This is a directory, no extension...")
        return null
    }
    val pathSplit = path.split("[.]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    return pathSplit[pathSplit.size - 1]
}

fun nameWoExtension(name: String): String {
    return name.substring(0, name.length - 4)
}

fun roundToLBA(number: Int): Int {
    var rest: Int = number % LBA
    if (rest < 0) {
        rest += LBA
    }
    return if (rest == 0) 0
        else number + LBA - rest
}