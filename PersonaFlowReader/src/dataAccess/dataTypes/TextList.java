package dataAccess.dataTypes;

import dataAccess.FileReadWriteUtils;
import dataAccess.Library;
import dataAccess.LongCharRandomAccessFile;

import javax.naming.OperationNotSupportedException;
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

    public TextList() {
        this.textList = new LinkedList<>();
        this.refList = new LinkedList<>();
    }

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
            if (Library.getInstance().TEXT_INSTRUCTIONS.containsKey(data)) {
                Library.TextInstruction instr = Library.getInstance().TEXT_INSTRUCTIONS.get(data);
                switch(instr) {
                    case AWAITING_INPUT:
                    case PLAYER_FIRST_NAME:
                    case PLAYER_NICKNAME:
                    case PLAYER_LAST_NAME:
                    case COIN_NUMBER:
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
                    case PRINT_VALUE:
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
                            toRet.append(Library.getInstance().TEXT_COLORS_ANSI.get(data));
                        }
                        else {
                            toRet.append(START_SPECIAL)
                                    .append(instr.name()).append(PARAM_SEPARATOR).append(Library.getInstance().TEXT_COLORS.get(data))
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
                if (Library.getInstance().TEXT_CODES.containsKey(data)) {
                    toRet.append(Library.getInstance().TEXT_CODES.get(data));
                } else {
                    toRet.append(String.format("{%04X}", data));
                }
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

    public static void encodeText(RandomAccessFile outputFile, String text, RandomAccessFile inputFile, int textInputPointer) throws IOException, OperationNotSupportedException {
        long pointerBK = inputFile.getFilePointer();

        //int length = text.length();
        int length = text.length();
        boolean canBeSpecial = false;
        boolean isSpecial = false;
        boolean canBeClosing = false;

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
                data = Library.getInstance().TEXT_CODES_REVERSE.get("" + text.charAt(i-1));
                FileReadWriteUtils.writeShort(outputFile, ByteOrder.BIG_ENDIAN, data);

                // decrement i to force the loop to read the current char
                i--;
                canBeSpecial = false;
                continue;
            } else if (isSpecial) {
                if (currChar.charAt(0) == END_SPECIAL.charAt(0)) {
                    canBeClosing = true;
                    continue;
                } else if (canBeClosing && currChar.charAt(0) == END_SPECIAL.charAt(1)) {
                    // if a normal instruction
                    if (Library.getInstance().TEXT_INSTRUCTIONS_REVERSE.containsKey(special.toString())) {
                        data = Library.getInstance().TEXT_INSTRUCTIONS_REVERSE.get(special.toString());
                        FileReadWriteUtils.writeShort(outputFile, ByteOrder.BIG_ENDIAN, data);
                    } else {
                        // it is either the WAIT or SHOW_OPTIONS or SET_COLOR or PRINT_ICON.... instructions, which has a parameter
                        String[] split = special.toString().split(PARAM_SEPARATOR);
                        data = Library.getInstance().TEXT_INSTRUCTIONS_REVERSE.get(split[0]);
                        FileReadWriteUtils.writeShort(outputFile, ByteOrder.BIG_ENDIAN, data);
                        if (split[0].compareTo(Library.TextInstruction.WAIT.name()) == 0 ||
                                split[0].compareTo(Library.TextInstruction.SHOW_OPTIONS.name()) == 0 ||
                                split[0].compareTo(Library.TextInstruction.PRINT_VALUE.name()) == 0 ||
                                split[0].compareTo(Library.TextInstruction.PRINT_ICON.name()) == 0) {
                            data = Short.parseShort(split[1]); // the value
                            FileReadWriteUtils.writeShort(outputFile, ByteOrder.LITTLE_ENDIAN, data);
                        } else if (split[0].compareTo(Library.TextInstruction.SET_COLOR.name()) == 0 ||
                                split[0].compareTo(Library.TextInstruction.LEGACY_SET_COLOR.name()) == 0) {
                            data = Library.getInstance().TEXT_COLORS_REVERSE.get(split[1].charAt(0)); // the color
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

            // When RandomAccessFile reads more complex characters, it puts them
            if ((byte)currChar.charAt(0) < 0) {
                LongCharRandomAccessFile longCharFile = new LongCharRandomAccessFile(textInputPointer+i, inputFile);
                inputFile.seek(textInputPointer+i);
                currChar = "" + longCharFile.nextChar();
                i += longCharFile.numOfBytes-1;
            }
            if (!Library.getInstance().TEXT_CODES_REVERSE.containsKey(currChar)) {
                currChar = convertCharToUsable(currChar);
            }
            data = Library.getInstance().TEXT_CODES_REVERSE.get(currChar);

            // TODO: remove this if when accents are figured out
            if (data > 0x006C && data <0x0078 ||
            data > 0x007E && data < 0x0088 ||
            data > 0x08E && data < 0x009A ||
            data > 0x00A8 && data < 0x00B0 ||
            data > 0x00BC && data < 0x00CF) {
                currChar = convertCharToUsable(currChar);
                data = Library.getInstance().TEXT_CODES_REVERSE.get(currChar);
            }
            FileReadWriteUtils.writeShort(outputFile, ByteOrder.BIG_ENDIAN, data);

        }
        FileReadWriteUtils.writeShort(outputFile, ByteOrder.BIG_ENDIAN, Library.getInstance().TEXT_INSTRUCTIONS_REVERSE.get("END"));
        inputFile.seek(pointerBK);
    }

    private static String convertCharToUsable(String currChar) throws OperationNotSupportedException {
        switch (currChar) {
            case "'":
                return "’"; // this char is used for Apostrophe instead of the standard '
            case "\"":
                return "”"; // this char can be used for quotes instead of the standard "
            case "~":
                return "～"; // this char can be used for tilde instead of the standard ~
            case "〜": // full width version of ~
                return "～";
            case "*":
                return "＊"; // this char can be used for asterisk instead of the standard *
            case "！": // full width version of !
                return "!";
            case "？": // full width version of ?
                return "?";
            case "＞":
                return ">";
            case "ａ":
                return "a";
            case "ｂ":
                return "b";
            case "ｃ":
                return "c";
            case "ｄ":
                return "d";
            case "ｅ":
                return "e";
            case "ｆ":
                return "f";
            case "ｇ":
                return "g";
            case "ｈ":
                return "h";
            case "ｉ":
                return "i";
            case "ｊ":
                return "j";
            case "ｋ":
                return "k";
            case "ｌ":
                return "l";
            case "ｍ":
                return "m";
            case "ｎ":
                return "n";
            case "ｏ":
                return "o";
            case "ｐ":
                return "p";
            case "ｑ":
                return "q";
            case "ｒ":
                return "r";
            case "ｓ":
                return "s";
            case "ｔ":
                return "t";
            case "ｕ":
                return "u";
            case "ｖ":
                return "v";
            case "ｗ":
                return "w";
            case "ｘ":
                return "x";
            case "ｙ":
                return "y";
            case "ｚ":
                return "z";

            case "Ａ":
                return "A";
            case "Ｂ":
                return "B";
            case "Ｃ":
                return "C";
            case "Ｄ":
                return "D";
            case "Ｅ":
                return "E";
            case "Ｆ":
                return "F";
            case "Ｇ":
                return "G";
            case "Ｈ":
                return "H";
            case "Ｉ":
                return "I";
            case "Ｊ":
                return "J";
            case "Ｋ":
                return "K";
            case "Ｌ":
                return "L";
            case "Ｍ":
                return "M";
            case "Ｎ":
                return "N";
            case "Ｏ":
                return "O";
            case "Ｐ":
                return "P";
            case "Ｑ":
                return "Q";
            case "Ｒ":
                return "R";
            case "Ｓ":
                return "S";
            case "Ｔ":
                return "T";
            case "Ｕ":
                return "U";
            case "Ｖ":
                return "V";
            case "Ｗ":
                return "W";
            case "Ｘ":
                return "X";
            case "Ｙ":
                return "Y";
            case "Ｚ":
                return "Z";

                // TODO: Delete all cases below when accents are figured out
            case "Á":
                return "A";
            case "À":
                return "A";
            case "Ã":
                return "A";
            case "Â":
                return "A";
            case "Ä":
                return "A";
            case "É":
                return "E";
            case "È":
                return "E";
            case "Ê":
                return "E";
            case "Ë":
                return "E";
            case "Í":
                return "I";
            case "Ì":
                return "I";
            case "Î":
                return "I";
            case "Ï":
                return "I";
            case "Ó":
                return "O";
            case "Ò":
                return "O";
            case "Õ":
                return "O";
            case "Ô":
                return "O";
            case "Ö":
                return "O";
            case "Ú":
                return "U";
            case "Ù":
                return "U";
            case "Û":
                return "U";
            case "Ü":
                return "U";
            case "Ç":
                return "C";
            case "á":
                return "a";
            case "à":
                return "a";
            case "ã":
                return "a";
            case "â":
                return "a";
            case "ä":
                return "a";
            case "é":
                return "e";
            case "è":
                return "e";
            case "ê":
                return "e";
            case "ë":
                return "e";
            case "ç":
                return "c";
            case "í":
                return "i";
            case "ì":
                return "i";
            case "î":
                return "i";
            case "ï":
                return "i";
            case "ó":
                return "o";
            case "ò":
                return "o";
            case "õ":
                return "o";
            case "ô":
                return "o";
            case "ö":
                return "o";
            case "ú":
                return "u";
            case "ù":
                return "u";
            case "û":
                return "u";
            case "ü":
                return "u";

            default:
                throw new OperationNotSupportedException("Character " + currChar + " is not usable.");
        }
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
