package dataAccess.dataTypes;

import dataAccess.FileReadWriteUtils;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.util.LinkedList;

public class InnerFileAddressList {

    private final LinkedList<InnerFileAddress> addressList;

    public InnerFileAddressList(LinkedList<InnerFileAddress> list){
        this.addressList = list;
    }

    public static InnerFileAddressList makeList(RandomAccessFile file, ByteOrder endianness) throws IOException {
        InnerFileAddress fileAddr = InnerFileAddress.getFileAddress(file, endianness);
        LinkedList<InnerFileAddress> list = new LinkedList<>();
        int limit = fileAddr.startAddr;
        while(fileAddr.currAddr < limit && fileAddr.startAddr != 0 && fileAddr.endAddr != 0) {
            list.add(fileAddr);
            fileAddr = InnerFileAddress.getFileAddress(file, endianness);
        }
        return new InnerFileAddressList(list);
    }

    public void writeFileAddresses(RandomAccessFile file, ByteOrder order) throws IOException {
        for(InnerFileAddress fileAddr : this.addressList) {
            FileReadWriteUtils.writeInt(file, order, fileAddr.startAddr);
            FileReadWriteUtils.writeInt(file, order, fileAddr.endAddr);
        }
        FileReadWriteUtils.writePadding(file);
    }

    public int getListSize() {
        return addressList.size();
    }

    public int getStartAddress(int idx) {
        try {
            return addressList.get(idx).startAddr;
        } catch (IndexOutOfBoundsException e) {
            return -1;
        }
    }

    public int getFileSize(int idx) {
        return addressList.get(idx).endAddr - addressList.get(idx).startAddr;
    }
}
