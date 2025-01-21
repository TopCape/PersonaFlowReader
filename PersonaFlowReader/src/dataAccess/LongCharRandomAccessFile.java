package dataAccess;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UTFDataFormatException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

// taken from: https://stackoverflow.com/questions/42309110/reading-a-single-utf-8-character-with-randomaccessfile
public class LongCharRandomAccessFile {

    public int seekPointer;
    public int numOfBytes;
    public RandomAccessFile source; // initialise in your own way

    public LongCharRandomAccessFile(int seekPointer, RandomAccessFile source) {
        this.seekPointer = seekPointer;
        this.source = source;
    }

    public void seek(int shift) {
        seekPointer += shift;
        if (seekPointer < 0) seekPointer = 0;
        try {
            source.seek(seekPointer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int byteCheck(int chr) {
        if (chr == -1) return 1; // eof
        int i = 1; // theres always atleast one byte
        if (chr >= 192) i++; // 2 bytes
        if (chr >= 224) i++; // 3 bytes
        if (chr >= 240) i++; // 4 bytes
        if (chr >= 128 && chr <= 191) i = -1; // woops, we're halfway through a char!
        return i;
    }

    public char nextChar() {
        try {
            seekPointer++;
            int i = source.read();

            numOfBytes = byteCheck(i);
            if (numOfBytes == -1) {
                boolean malformed = true;
                for (int k = 0; k < 4; k++) { // Iterate 3 times.
                    // we only iterate 3 times because the maximum size of a utf-8 char is 4 bytes.
                    // any further and we may possibly interrupt the other chars.
                    seek(-1);
                    i = source.read();
                    numOfBytes = byteCheck(i);
                    if (numOfBytes != -1) {
                        malformed = false;
                        break;
                    }
                }
                if (malformed) {
                    seek(3);
                    throw new UTFDataFormatException("Malformed UTF char at position: " + seekPointer);
                }
            }

            byte[] chrs = new byte[numOfBytes];
            chrs[0] = (byte) i;

            for (int j = 1; j < chrs.length; j++) {
                seekPointer++;
                chrs[j] = (byte) source.read();
            }

            return i > -1 ? new String(chrs, StandardCharsets.UTF_8).charAt(0) : '\0'; // EOF character is -1.
        } catch (IOException e) {
            e.printStackTrace();
        }
        return '\0';
    }

}
