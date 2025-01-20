package dataAccess.dataTypes

import dataAccess.readInt
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteOrder

data class InnerFileAddress(
    val startAddr: Int,
    val endAddr: Int,
    val currAddr: Long
)

@Throws(IOException::class)
fun getFileAddress(file: RandomAccessFile, order: ByteOrder): InnerFileAddress {
    val currAddr = file.filePointer
    val startAddr = readInt(file, order)
    val endAddr = readInt(file, order)
    return InnerFileAddress(startAddr, endAddr, currAddr)
}