package dataAccess;

import javax.naming.OperationNotSupportedException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FileReadWriteUtils {

    public static int LBA = 0x800;

    /**
     * Reads a short from the file object and applies the correct endianness to it.
     * @param file object used to read file
     * @param byteOrder endianness of data
     * @return short read from the file object, in the correct endianness
     * @throws IOException random access file read io exception
     */
    public static short readShort(RandomAccessFile file, ByteOrder byteOrder) throws IOException {
        byte[] buffer = new byte[2];
        file.read(buffer);
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

        return byteBuffer.order(byteOrder).getShort();
    }

    /**
     * Reads an integer from the file object and applies the correct endianness to it.
     * @param file object used to read file
     * @param byteOrder endianness of data
     * @return integer read from the file object, in the correct endianness
     * @throws IOException random access file read io exception
     */
    public static int readInt(RandomAccessFile file, ByteOrder byteOrder) throws IOException {
        byte[] buffer = new byte[4];
        file.read(buffer);
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

        return byteBuffer.order(byteOrder).getInt();
    }

    /**
     * Writes a short to the file using the file object, applying the correct endianness to the value.
     * @param file object used to write to file
     * @param byteOrder endianness of data
     * @param val the short to write
     * @throws IOException random access file write io exception
     */
    public static void writeShort(RandomAccessFile file, ByteOrder byteOrder, short val) throws IOException {
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            file.writeShort(val);
            return;
        }
        byte[] ogValue = ByteBuffer.allocate(2).order(byteOrder).putShort(val).array();
        file.writeShort(ByteBuffer.wrap(ogValue).getShort());
    }

    /**
     * Writes an int to the file using the file object, applying the correct endianness to the value.
     * @param file object used to write to file
     * @param byteOrder endianness of data
     * @param val the int to write
     * @throws IOException random access file write io exception
     */
    public static void writeInt(RandomAccessFile file, ByteOrder byteOrder, int val) throws IOException {
        if (byteOrder == ByteOrder.BIG_ENDIAN) {
            file.writeInt(val);
            return;
        }
        byte[] ogValue = ByteBuffer.allocate(4).order(byteOrder).putInt(val).array();
        file.writeInt(ByteBuffer.wrap(ogValue).getInt());
    }

    /**
     * Writes 0s until the next multiple of LBA
     * @param file object used to write to file
     */
    public static void writePadding(RandomAccessFile file) throws IOException {
        while(file.getFilePointer() % LBA != 0) {
            file.writeInt(0);
        }
    }

    /**
     * Returns the extension of the file in path
     * @param path file path
     * @return the extension of the file
     */
    public static String getExtension(String path) {
        if (path.charAt(path.length()-1) == '/') {
            System.out.println("This is a directory, no extension...");
            return null;
        }
        String[] pathSplit = path.split("[.]");
        return pathSplit[pathSplit.length-1];
    }

    public static int roundToLBA(int number) {
        int rest = number % LBA;
        if (rest < 0) {
            rest = LBA + rest;
        }
        if (rest == 0) {
            return rest;
        }
        return number + LBA - rest;
    }
}
// number = 0x700
// 0x700 % 0x800 -> 1792
