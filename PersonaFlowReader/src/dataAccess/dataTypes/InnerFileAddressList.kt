package dataAccess.dataTypes

import dataAccess.writeInt
import dataAccess.writePadding
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.util.*

data class InnerFileAddressList(
    val list: List<InnerFileAddress>
) {
    @Throws(IOException::class)
    fun writeFileAddresses(file: RandomAccessFile?, order: ByteOrder?) {
        for ((startAddr, endAddr) in this.list) {
            writeInt(file!!, order!!, startAddr)
            writeInt(file, order, endAddr)
        }
        writePadding(file!!)
    }

    fun getListSize(): Int = list.size


    fun getStartAddress(idx: Int): Int {
        return try {
            list[idx].startAddr
        } catch (e: IndexOutOfBoundsException) {
            -1
        }
    }

    fun getFileSize(idx: Int): Int = list[idx].endAddr - list[idx].startAddr
}

@Throws(IOException::class)
fun makeList(file: RandomAccessFile, order: ByteOrder): InnerFileAddressList {
    var fileAddr = getFileAddress(file, order)
    val list = LinkedList<InnerFileAddress>()
    val limit = fileAddr.startAddr
    while (fileAddr.currAddr < limit && fileAddr.startAddr != 0 && fileAddr.endAddr != 0) {
        list.add(fileAddr)
        fileAddr = getFileAddress(file, order)
    }
    return InnerFileAddressList(list)
}

