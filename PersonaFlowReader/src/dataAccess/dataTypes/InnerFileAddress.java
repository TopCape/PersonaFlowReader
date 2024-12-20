package dataAccess.dataTypes;

import dataAccess.FileReadWriteUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;

public class InnerFileAddress {
    public final int startAddr;
    public final int endAddr;
    public final long currAddr;

    public InnerFileAddress(int startAddr, int endAddr, long currAddr) {
        this.startAddr = startAddr;
        this.endAddr = endAddr;
        this.currAddr = currAddr;
    }

    public static InnerFileAddress getFileAddress(RandomAccessFile file, ByteOrder order) throws IOException {
        long currAddr = file.getFilePointer();
        int startAddr = FileReadWriteUtils.readInt(file, order);
        int endAddr = FileReadWriteUtils.readInt(file, order);
        return new InnerFileAddress(startAddr, endAddr, currAddr);
    }
}
