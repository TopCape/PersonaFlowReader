package dataAccess.dataTypes;

import dataAccess.FileReadWriteUtils;
import dataAccess.Library;
import dataAccess.LongCharRandomAccessFile;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;

public class TextList {

    private static final String CHARACTER_NAME_COLOR = "\u001B[33m";
    private static final String RESET_COLOR = "\u001B[0m";

    private static final String START_SPECIAL = "(*";
    private static final String END_SPECIAL = "*)";

    private static final String PARAM_SEPARATOR = ",";

    private int size;
    //private final String[] textList;
    private final LinkedList<String> textList;
    private final LinkedList<Integer> refList;

    public TextList(int size, LinkedList<String> textList, LinkedList<Integer> refList) {
        this.size = size;
        this.textList = textList;
        this.refList = refList;
    }

    public static TextList readEncodedTextList(RandomAccessFile file, int addr, boolean oneLine) throws IOException {
        int pointerBK = (int) file.getFilePointer();
        file.seek(addr);

        int size = FileReadWriteUtils.readInt(file, ByteOrder.LITTLE_ENDIAN);

        LinkedList<Integer> refList = new LinkedList<>();
        LinkedList<String> textList = new LinkedList<>();
        for (int i = 0; i < size; i++) {
            int textRef = FileReadWriteUtils.readInt(file, ByteOrder.LITTLE_ENDIAN);
            refList.add(textRef);
            textList.add(getText(file, textRef, oneLine));
        }
        file.seek(pointerBK);
        return new TextList(size, textList, refList);
    }

    private static String getText(RandomAccessFile file, int address, boolean oneLine) throws IOException {
        int pointerBK = (int) file.getFilePointer();
        StringBuilder toRet = new StringBuilder("\"");
        file.seek(address);

        StringBuilder optionsComment = null;

        short data = FileReadWriteUtils.readShort(file, ByteOrder.BIG_ENDIAN);
        boolean name_color_set = false;
        while (data != (short)0xff01) {
            // it's an instruction
            if (Library.TEXT_INSTRUCTIONS.containsKey(data)) {
                Library.TextInstruction instr = Library.TEXT_INSTRUCTIONS.get(data);
                switch(instr) {
                    case AWAITING_INPUT:
                    case PLAYER_FIRST_NAME:
                    case PLAYER_NICKNAME:
                    case PLAYER_LAST_NAME:
                        toRet.append(START_SPECIAL).append(instr.name()).append(END_SPECIAL);
                        break;
                    case LINE_BREAK:
                        if (name_color_set) {
                            if (!oneLine) toRet.append(RESET_COLOR);
                            name_color_set = false;
                        }
                        if (!oneLine) toRet.append("\n");
                        else toRet.append(START_SPECIAL).append(instr.name()).append(END_SPECIAL);
                        break;
                    case CONTINUE:
                        if (!oneLine) toRet.append("\n\n");
                        else toRet.append(START_SPECIAL).append(instr.name()).append(END_SPECIAL);
                        break;
                    case WAIT:
                        toRet.append(START_SPECIAL).append(instr.name()).append(PARAM_SEPARATOR);
                        data = FileReadWriteUtils.readShort(file, ByteOrder.LITTLE_ENDIAN);
                        toRet.append(data).append(END_SPECIAL);
                        break;
                    case SHOW_OPTIONS:
                        toRet.append(START_SPECIAL).append(instr.name()).append(PARAM_SEPARATOR);
                        data = FileReadWriteUtils.readShort(file, ByteOrder.LITTLE_ENDIAN);
                        toRet.append(data).append(END_SPECIAL);

                        if (optionsComment == null) {
                            optionsComment = new StringBuilder();
                        }
                        optionsComment.append("\t").append(Library.COMMENT_SYMBOL).append(" Shows options: |");
                        String[] options = Library.OPTIONS[data];
                        for (String s : options) {
                            optionsComment.append("\"").append(s).append("\"");
                            optionsComment.append("|");
                        }

                        break;
                    case SET_COLOR: // sets a color
                    case LEGACY_SET_COLOR:
                        data = FileReadWriteUtils.readShort(file, ByteOrder.LITTLE_ENDIAN);
                        if (!oneLine) {
                            toRet.append(Library.TEXT_COLORS_ANSI.get(data));
                        }
                        else {
                            toRet.append(START_SPECIAL)
                                    .append(instr.name()).append(PARAM_SEPARATOR).append(Library.TEXT_COLORS.get(data))
                                    .append(END_SPECIAL);
                        }
                        break;
                    case PRINT_ICON:
                        data = FileReadWriteUtils.readShort(file, ByteOrder.LITTLE_ENDIAN);
                        if (!oneLine) {
                            switch(data) {
                                case 0:
                                    toRet.append("\uD83E\uDDF4");
                                    break;
                                case 3:
                                    toRet.append("\uD83D\uDD11");
                                    break;
                                default:
                                    toRet.append("❓");
                            }
                        } else {
                            toRet.append(START_SPECIAL)
                                    .append(instr.name()).append(PARAM_SEPARATOR).append(data)
                                    .append(END_SPECIAL);
                        }
                        break;

                    case CHARACTER_NAME: // yellow
                        if (!oneLine) toRet.append(CHARACTER_NAME_COLOR);
                        else toRet.append(START_SPECIAL).append(instr.name()).append(END_SPECIAL);

                        name_color_set = true;
                        break;
                }
            } else { // it's text
                toRet.append(Library.TEXT_CODES.get(data));
            }
            data = FileReadWriteUtils.readShort(file, ByteOrder.BIG_ENDIAN);
        }

        toRet.append("\"");

        if (optionsComment != null) {
            toRet.append(optionsComment);
        }

        // restore pointer position
        file.seek(pointerBK);
        return toRet.toString();
    }

    public static void encodeText(RandomAccessFile outputFile, String text, RandomAccessFile inputFile, int textInputPointer) throws IOException {
        long pointerBK = inputFile.getFilePointer();

        //int length = text.length();
        int length = text.length();
        boolean canBeSpecial = false;
        boolean isSpecial = false;
        boolean canBeClosing = false;
        boolean canBeLineBreak = false;

        byte longerCharByteAmount = 0;
        byte totalLongerCharByteAmount = 0;
        byte[] longerChar = new byte[4];

        short data;
        StringBuilder special = new StringBuilder();
        for (int i = 0; i < length; i++) {
            String currChar = text.substring(i, i+1);
            //byte currChar = text[i];
            if (currChar.charAt(0) == START_SPECIAL.charAt(0)) {
                canBeSpecial = true;
                continue;
            } else if (canBeSpecial && currChar.charAt(0) == START_SPECIAL.charAt(1)) {
                isSpecial = true;
                canBeSpecial = false;
                continue;
                // if it ended up not being a special char
            } else if (canBeSpecial && !isSpecial) {
                // write the old chars
                data = Library.TEXT_CODES_REVERSE.get("" + text.charAt(i-1));
                //data = Library.TEXT_CODES_REVERSE.get(new String(new byte[]{text[i-1]}, StandardCharsets.UTF_8));
                FileReadWriteUtils.writeShort(outputFile, ByteOrder.BIG_ENDIAN, data);
                //data = Library.TEXT_CODES_REVERSE.get(new String(new byte[]{currChar}, StandardCharsets.UTF_8));
                data = Library.TEXT_CODES_REVERSE.get(currChar);
                FileReadWriteUtils.writeShort(outputFile, ByteOrder.BIG_ENDIAN, data);
                canBeSpecial = false;
                continue;
            } else if (isSpecial) {
                if (currChar.charAt(0) == END_SPECIAL.charAt(0)) {
                    canBeClosing = true;
                    continue;
                } else if (canBeClosing && currChar.charAt(0) == END_SPECIAL.charAt(1)) {
                    // if a normal instruction
                    if (Library.TEXT_INSTRUCTIONS_REVERSE.containsKey(special.toString())) {
                        data = Library.TEXT_INSTRUCTIONS_REVERSE.get(special.toString());
                        FileReadWriteUtils.writeShort(outputFile, ByteOrder.BIG_ENDIAN, data);
                    } else {
                        // it is either the WAIT or SHOW_OPTIONS or SET_COLOR or PRINT_ICON instructions, which has a parameter
                        String[] split = special.toString().split(PARAM_SEPARATOR);
                        data = Library.TEXT_INSTRUCTIONS_REVERSE.get(split[0]);
                        FileReadWriteUtils.writeShort(outputFile, ByteOrder.BIG_ENDIAN, data);
                        if (split[0].compareTo(Library.TextInstruction.WAIT.name()) == 0 ||
                                split[0].compareTo(Library.TextInstruction.SHOW_OPTIONS.name()) == 0 ||
                                split[0].compareTo(Library.TextInstruction.PRINT_ICON.name()) == 0) {
                            data = Short.parseShort(split[1]); // the value
                            FileReadWriteUtils.writeShort(outputFile, ByteOrder.LITTLE_ENDIAN, data);
                        } else if (split[0].compareTo(Library.TextInstruction.SET_COLOR.name()) == 0 ||
                                split[0].compareTo(Library.TextInstruction.LEGACY_SET_COLOR.name()) == 0) {
                            data = Library.TEXT_COLORS_REVERSE.get(split[1].charAt(0)); // the color
                            FileReadWriteUtils.writeShort(outputFile, ByteOrder.LITTLE_ENDIAN, data);
                        } else {
                            System.out.println("EERRROOOOORRRR in special text code\n");
                        }
                    }
                    special = new StringBuilder();
                    canBeSpecial = false;
                    isSpecial = false;
                    canBeClosing = false;
                    continue;
                }
                special.append(currChar);
                continue;
            }
            if (currChar.compareTo("(") == 0) {
                System.out.println("a");
            }

            // When RandomAccessFile reads more complex characters, it puts them
            if ((byte)currChar.charAt(0) < 0) {
                LongCharRandomAccessFile longCharFile = new LongCharRandomAccessFile(textInputPointer+i, inputFile);
                inputFile.seek(textInputPointer+i);
                currChar = "" + longCharFile.nextChar();
                i += longCharFile.numOfBytes-1;
            }
            data = Library.TEXT_CODES_REVERSE.get(currChar);
            FileReadWriteUtils.writeShort(outputFile, ByteOrder.BIG_ENDIAN, data);

        }
        FileReadWriteUtils.writeShort(outputFile, ByteOrder.BIG_ENDIAN, Library.TEXT_INSTRUCTIONS_REVERSE.get("END"));
        inputFile.seek(pointerBK);
    }

    @Override
    public String toString() {
        StringBuilder toRet = new StringBuilder();
        toRet.append("Number of dialogs: ").append(this.size).append("\n\n");
        for (int i = 0; i < this.size; i++) {
            //String text = new String(this.textList[i], StandardCharsets.UTF_8);
            String text = this.textList.get(i);
            toRet.append(i).append("(").append(String.format("0x%08x", refList.get(i))).append(")").append(":\n")
                    .append(text).append("\n\n");
        }
        return toRet.toString();
    }

    public void writeText(RandomAccessFile outputFile) throws IOException {
        //toRet.append("section\t.text\n");
        outputFile.writeBytes(Library.SECTION_KEYWORD + "\t" + Library.TEXT_AREA_KEYWORD + "\t" + String.format("0x%08x", this.size) + "\n");
        for (int i = 0; i < this.size; i++) {
            //toRet.append("\t").append(String.format("%03d", i)).append("\"").append((this.textList[i])).append("\"\n");
            outputFile.writeBytes(String.format("\t%03d:", i));
            outputFile.write(textList.get(i).getBytes());
            outputFile.writeBytes("\n");
        }
        //return toRet.toString();
    }

    public int indexOfText(int address) {
        for (int i = 0; i < size; i++) {
            if (this.refList.get(i) == address) {
                return i;
            }
        }
        return -1;
    }

    public void addText(RandomAccessFile file, int address, boolean oneLine) throws IOException {
        String text = getText(file, address, oneLine);
        this.refList.add(address);
        this.textList.add(text);

        this.size++;
    }

    public int getFirstAddr() {
        return this.refList.get(0);
    }

    public int getRef(int idx) {
        return this.refList.get(idx);
    }
}
