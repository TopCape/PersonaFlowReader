package dataAccess.dataTypes;

import dataAccess.FileReadWriteUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;

public class InnerFileAddress {
    public final int startAddr;
    public final int endAddr;

    public InnerFileAddress(int startAddr, int endAddr) {
        this.startAddr = startAddr;
        this.endAddr = endAddr;
    }

    public static InnerFileAddress getFileAddress(RandomAccessFile file, ByteOrder order) throws IOException {
        int startAddr = FileReadWriteUtils.readInt(file, order);
        int endAddr = FileReadWriteUtils.readInt(file, order);
        return new InnerFileAddress(startAddr, endAddr);
    }
}
