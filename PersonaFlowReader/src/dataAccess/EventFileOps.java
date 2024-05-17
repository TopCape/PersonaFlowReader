package dataAccess;

import dataAccess.dataTypes.InnerFileAddress;
import dataAccess.dataTypes.InnerFileAddressList;
import dataAccess.dataTypes.TextList;

import javax.naming.OperationNotSupportedException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class EventFileOps {

    private static final String READ_MODE = "r";
    private static final String WRITE_MODE = "rw";
    private static final String EXTENSION_1 = ".BIN";
    private static final String EXTENSION_2 = ".bin";

    private static final HashMap<Integer, String> labels = new HashMap<>();
    private static int labelNum;
    private static boolean isLastInstruction;
    private static boolean emptyLineHappened;

    public static final ByteOrder valOrder = ByteOrder.LITTLE_ENDIAN;
    public static final ByteOrder instructionOrder = ByteOrder.BIG_ENDIAN;

    private static TextList textList;

    private static final HashMap<Short, LinkedList<Integer>> textReferenceLocations = new HashMap<>();
    private static final HashMap<Short, LinkedList<Integer>> labelReferenceLocations = new HashMap<>();


    public static void extract(String path) throws IOException, OperationNotSupportedException {
        if (!path.endsWith(EXTENSION_1) && !path.endsWith(EXTENSION_2)) {
            System.out.println("PATH was: " + path);
            throw new OperationNotSupportedException("Only .bin files are supported");
        }

        InnerFileAddressList addressList;
        String basePath;
        try (RandomAccessFile baseFile = new RandomAccessFile(path, READ_MODE)) {
            addressList = InnerFileAddressList.makeList(baseFile, valOrder);

            String[] pathArray = path.split("/");
            String baseNameWExt = pathArray[pathArray.length-1];
            String baseName = baseNameWExt.substring(0, baseNameWExt.length()-4);
            basePath = path.substring(0, path.length()-baseNameWExt.length()) + baseName;

            // create directory if it doesn't exist
            File dir = new File(basePath);
            if (!dir.exists()) {
                dir.mkdir();
            }

            // getting each inner file
            for (int i = 0; i < addressList.getListSize(); i++) {
                String nameAddition = String.format("_%03d", i);
                String newFileName = basePath + "/" + baseName + nameAddition + ".BIN";

                int fileAddr = addressList.getStartAddress(i);
                if (fileAddr == -1) break;

                try (RandomAccessFile newFile = new RandomAccessFile(newFileName, WRITE_MODE)) {
                    int size = addressList.getFileSize(i);
                    size /= 4; // size in ints (4 bytes each)
                    baseFile.seek(fileAddr);
                    for(int j = 0; j < size; j++) {
                        newFile.writeInt(baseFile.readInt());
                    }
                } catch (Exception e) {
                    System.out.println("ERR Making new file: " + e.getMessage());
                    throw e;
                }
            }
        } catch (Exception e) {
            System.out.println("ERR Opening file for extraction: " + e.getMessage());
            throw e;
        }
    }

    public static void archive(String dirPath) throws OperationNotSupportedException, IOException {
        File dir = new File(dirPath);
        File[] directoryListing = dir.listFiles();

        int startAddr = 0x800;
        LinkedList<InnerFileAddress> fileAddrList = new LinkedList<>();
        if (directoryListing != null) {
            if (directoryListing.length == 0) {
                throw new OperationNotSupportedException("The directory is empty");
            }
            for (File child : directoryListing) {
                int endAddr = (int) (child.length() + startAddr);
                InnerFileAddress fileAddr = new InnerFileAddress(startAddr, endAddr);
                fileAddrList.add(fileAddr);
                startAddr = endAddr;
            }
            InnerFileAddressList fileList = new InnerFileAddressList(fileAddrList);

            if (dirPath.charAt(dirPath.length()-1) == '/') dirPath = dirPath.substring(0, dirPath.length()-1);
            String[] seg = dirPath.split("/");
            String fileName = seg[seg.length-1];
            String destinationFile = dirPath + "/" + fileName + "_NEW.BIN";

            try (RandomAccessFile file = new RandomAccessFile(destinationFile, WRITE_MODE)) {
                fileList.writeFileAddresses(file, valOrder);

                for (File child : directoryListing) {
                    try (RandomAccessFile subFile = new RandomAccessFile(child, READ_MODE)) {
                        int sizeInInts = (int) subFile.length() / 4;
                        for (int i = 0; i < sizeInInts; i++) {
                            file.writeInt(subFile.readInt());
                        }
                    } catch (Exception e) {
                        System.out.println("ERR in reading subfile for archiving: " + e.getMessage());
                        throw e;
                    }
                }

            } catch (Exception e) {
                System.out.println("ERR in archiving process: " + e.getMessage());
                throw e;
            }


        } else {
            throw new OperationNotSupportedException("It must be a directory path");
        }
    }

    public static void decodeFlowScript(String path) throws IOException {
        labelNum = 0;
        isLastInstruction = false;
        labels.clear();

        try (RandomAccessFile inputFile = new RandomAccessFile(path, READ_MODE)) {
            String outputPath = path.substring(0, path.length()-3) + "DEC";

            // delete file if it already exists
            File file = new File(outputPath);
            file.delete();

            try (RandomAccessFile outputFile = new RandomAccessFile(outputPath, WRITE_MODE)) {

                inputFile.seek(Library.ADDRESS_WITH_TEXT_TABLE_POINTER);
                int textTableAddr = FileReadWriteUtils.readInt(inputFile, valOrder);
                textList = TextList.readEncodedTextList(inputFile, textTableAddr, true);

                inputFile.seek(Library.ADDRESS_WITH_FLOW_SCRIPT_POINTER);
                int flowStartAddr = FileReadWriteUtils.readInt(inputFile, valOrder);
                inputFile.seek(flowStartAddr);

                //outputFile.writeBytes(Library.ADDR_KEYWORD + "\t" + String.format(Library.HEX_PREFIX + "%04x", flowStartAddr) + Library.BIG_LINE_BREAK);
                outputFile.writeBytes(String.format("%s\t%s%04x%s", Library.ADDR_KEYWORD, Library.HEX_PREFIX, flowStartAddr, Library.BIG_LINE_BREAK));

                outputFile.writeBytes(Library.SECTION_KEYWORD + "\t" + Library.TALK_AREA_KEYWORD + "\n");
                populateTalkAddresses(inputFile, outputFile);


                outputFile.writeBytes(Library.SECTION_KEYWORD + "\t" + Library.CODE_AREA_KEYWORD + "\n");
                // TODO redefine last instruction check
                // File E2_040 has MORE event script after the first "wave" of text
                // so... check the labels for unused ones or ones that are higher than the first text address?
                // if they exist, jump there and write them before writing the text

                // THOUGHT: Labels are always AFTER the jump instructions to them
                // SOOOO: when a label is finally written, delete it from the map
                // any labels that haven't been deleted can be "explored" until the end (FF21)
                // therefore, all code is reached
                while (!isLastInstruction) {
                    String textInst = decodeInstruction(inputFile, false);
                    outputFile.writeBytes(textInst);

                    int currPointer = (int) inputFile.getFilePointer();
                    // UNCOMMENT FOR DEBUG HERE
                    //System.out.println("ADDRESS:" + String.format("0x%08x", currPointer));
                    if (labels.containsKey(currPointer)) {
                        //System.out.println(labels.get(currPointer) + ":");
                        outputFile.writeBytes("\n" + labels.get(currPointer) + ":\n");
                        labels.remove(currPointer); // the label has been used, so no need to do anything else
                    }

                }

                // HERE
                //long pointerBk = inputFile.getFilePointer();

                // Code can exist AFTER the text, so gotta check if there are labels that weren't achieved
                while (!labels.isEmpty()) {
                    int pointer = new ArrayList<>(labels.keySet()).get(0);
                    inputFile.seek(pointer);
                    isLastInstruction = false;

                    // write the label before the instructions
                    outputFile.writeBytes("\n" + labels.get(pointer) + ":\n");
                    labels.remove(pointer);

                    while(!isLastInstruction) {
                        String textInst = decodeInstruction(inputFile, true);
                        outputFile.writeBytes(textInst);

                        /*int currPointer = (int) inputFile.getFilePointer();
                        if (labels.containsKey(currPointer)) {
                            //System.out.println(labels.get(currPointer) + ":");
                            outputFile.writeBytes("\n" + labels.get(currPointer) + ":\n");
                            labels.remove(currPointer); // the label has been used, so no need to do anything else
                        }*/
                    }
                    labels.remove(pointer);
                }


                outputFile.writeBytes("\n");
                textList.writeText(outputFile);

            } catch (Exception e) {
                System.out.println("ERR opening file to write decoded to: " + e.getMessage());
                throw e;
            }

        } catch (Exception e) {
            System.out.println("ERR opening file to decode: " + e.getMessage());
            throw e;
        }
    }

    public static void encodeFlowScript(String inputPath) throws IOException, OperationNotSupportedException {
        isLastInstruction = false;
        emptyLineHappened = false;
        textReferenceLocations.clear();
        labelReferenceLocations.clear();
        int textListSize = 0;
        try (RandomAccessFile inputFile = new RandomAccessFile(inputPath, READ_MODE)) {
            String line = inputFile.readLine();
            String[] split = line.split(Library.SPACE_TAB_REGEX);

            if (split[0].compareTo(Library.ADDR_KEYWORD) != 0) {
                throw new OperationNotSupportedException(Library.NOT_FORMATTED_ERR_TXT);
            }

            int startAddr = readHexIntString(split[1]);

            String outputPath = inputPath.substring(0, inputPath.length()-4) + "_ENCODED.BIN";
            try (RandomAccessFile outputFile = new RandomAccessFile(outputPath, WRITE_MODE)) {
                // first gonna get the data from the og file up until the event flow script
                fillFileBeginning(inputPath, outputFile, startAddr);

                // MAKE SURE THIS WORK
                while ((line = inputFile.readLine()).compareTo("") == 0);
                line = removeCommentAndSpaces(line);

                // skip spaces and tabs after "section"
                String[] talkSplit = line.split(Library.SPACE_TAB_REGEX);
                int i = skipSpacesNTabs(talkSplit, 1);

                if (talkSplit[0].compareTo(Library.SECTION_KEYWORD) != 0 || talkSplit[i].compareTo(Library.TALK_AREA_KEYWORD) != 0) {
                    throw new OperationNotSupportedException(Library.NOT_FORMATTED_ERR_TXT);
                }

                while ((line = inputFile.readLine()).compareTo("") != 0) {
                    line = removeCommentAndSpaces(line);
                    String[] talkLineSplit = line.split(Library.SPACE_TAB_REGEX);
                    int characterId = Integer.parseInt(talkLineSplit[0]);
                    i = skipSpacesNTabs(talkLineSplit, 1);

                    String label = talkLineSplit[i];
                    short labelNum = (short) Integer.parseInt(label.split(Library.LABEL_SEPARATOR)[1]);
                    int address = Library.ADDRESS_OF_CHARACTER_DATA + (characterId * Library.CHARACTER_DATA_SIZE) + Library.CHARACTER_DATA_EVENT_ADDRESS_OFFSET;

                    if (labelReferenceLocations.containsKey(labelNum)) {
                        labelReferenceLocations.get(labelNum).add(address);
                    } else {
                        LinkedList<Integer> toAdd = new LinkedList<>();
                        toAdd.add(address);
                        labelReferenceLocations.put(labelNum, toAdd);
                    }

                }


                // MAKE SURE THIS WORK
                while ((line = inputFile.readLine()).compareTo("") == 0);
                line = removeCommentAndSpaces(line);

                // skip spaces and tabs after "section"
                String[] codeSplit = line.split(Library.SPACE_TAB_REGEX);
                i = skipSpacesNTabs(codeSplit, 1);

                if (codeSplit[0].compareTo(Library.SECTION_KEYWORD) != 0 || codeSplit[i].compareTo(Library.CODE_AREA_KEYWORD) != 0) {
                    throw new OperationNotSupportedException(Library.NOT_FORMATTED_ERR_TXT);
                }

                while (true) {
                    if (!emptyLineHappened) {
                        encodeInstruction(inputFile, outputFile);
                    } else {
                        //... check if new section OR label
                        line = inputFile.readLine();
                        line = removeCommentAndSpaces(line);
                        String[] sectionSplit = line.split(Library.SPACE_TAB_REGEX);
                        String[] labelSplit = sectionSplit[0].split(Library.LABEL_SEPARATOR);

                        // if it is a label
                        if (sectionSplit.length == 1 && labelSplit[0].compareTo(Library.LABEL_TXT) == 0) {
                            String labelNum = labelSplit[1];
                            fillInRef(outputFile, Short.parseShort(labelNum.substring(0, labelNum.length()-1)), false);
                            emptyLineHappened = false;
                        } else { // it must be a new section...
                            // readLine of an empty line returns an empty string
                            if (line.length() == 0) {
                                continue;
                            }

                            // skipping tabs and spaces between "section" and ".text"
                            i = skipSpacesNTabs(sectionSplit, 1);

                            // if different from expected "section" and ".text"
                            if (sectionSplit[0].compareTo(Library.SECTION_KEYWORD) != 0 || sectionSplit[i].compareTo(Library.TEXT_AREA_KEYWORD) != 0) {
                                throw new OperationNotSupportedException(Library.NOT_FORMATTED_ERR_TXT);
                            }

                            // skipping to the next value on that line: the number of text strings
                            i = skipSpacesNTabs(sectionSplit, i+1);
                            textListSize = Integer.parseInt(sectionSplit[i].substring(2), 16);
                            break;
                        }
                    }
                }

                // text section handling
                int textInputPointer = (int) inputFile.getFilePointer();
                line = inputFile.readLine();
                int[] textPointers = new int[textListSize];
                for (i = 0; i < textListSize; i++) {
                    line = EventFileOps.removeCommentAndSpaces(line);
                    int indexOfColon = line.indexOf(":");
                    String text = line.substring(indexOfColon + 1);
                    textInputPointer += indexOfColon + 1; // to skip everything before colon

                    // making sure the text is between quotes
                    if (text.charAt(0) != '\"' || text.charAt(text.length()-1) != '\"') {
                        throw new OperationNotSupportedException("TEXT FORMATTED INCORRECTLY: " + text);
                    }
                    // removing quotes
                    text = text.substring(1, text.length() - 1);
                    textInputPointer ++; // to skip the quote

                    // splitting spaces and tabs
                    String[] textSplit = line.split(Library.SPACE_TAB_REGEX);

                    // skip spaces and tabs at beginning
                    int textEntryIdx = skipSpacesNTabs(textSplit, 0);

                    // getting text index
                    short textId = Short.parseShort(textSplit[textEntryIdx].substring(0, 3));

                    // text has to be aligned to multiples of 8
                    while (outputFile.getFilePointer() % 8 != 0) outputFile.writeByte(0);

                    // filling in a text address location we didn't know yet
                    // RIGHT NOW in the output file is where the text starts
                    fillInRef(outputFile, textId, true);

                    textPointers[i] = (int) outputFile.getFilePointer();
                    //TextList.encodeText(outputFile, text.getBytes(Charset.forName("windows-1252")));
                    //TextList.encodeText(outputFile, new String(text.getBytes(Charset.forName("Cp1252")), Charset.forName("Cp1252")));
                    //TextList.encodeText(outputFile, text.getBytes(StandardCharsets.UTF_8));
                    TextList.encodeText(outputFile, new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8), inputFile, textInputPointer);

                    textInputPointer = (int) inputFile.getFilePointer();
                    line = inputFile.readLine();
                }

                // gotta go update the reference to the text table
                int textTablePointer = (int) outputFile.getFilePointer();
                outputFile.seek(Library.ADDRESS_WITH_TEXT_TABLE_POINTER);
                FileReadWriteUtils.writeInt(outputFile, valOrder, textTablePointer);
                outputFile.seek(textTablePointer);

                // writing the text table at the end
                FileReadWriteUtils.writeInt(outputFile, valOrder, textListSize);

                for (i = 0; i < textListSize; i++) {
                    FileReadWriteUtils.writeInt(outputFile, valOrder, textPointers[i]);
                }

                // final padding
                while(outputFile.getFilePointer() % 0x800 != 0) {
                    outputFile.writeByte(0);
                }

                /*line = inputFile.readLine();
                while(line != null) {
                    String[] textSplit = line.split(Library.SPACE_TAB_REGEX);

                    // skip spaces and tabs at beginning
                    int i = skipSpacesNTabs(textSplit, 0);

                    short textId = Short.parseShort(textSplit[i].substring(0, 3));
                    fillInRef(outputFile, textId, true);

                    String text = line.substring(line.indexOf(":") + 1);
                    textList = TextList.readDecodedTextList(text);
                    TextList.encodeTextAndList(outputFile, textList);

                    line = inputFile.readLine();
                }
                */


            } catch (Exception e) {
                System.out.println("ERR opening file to write encoded to: " + e.getMessage());
                throw e;
            }
        } catch (Exception e) {
            System.out.println("ERR opening file to encode: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Fills start of output file based on original file
     * @param path input file's path
     * @param outputFile object used to write to output file
     * @param startAddr address where event flow script starts
     */
    private static void fillFileBeginning(String path, RandomAccessFile outputFile, int startAddr) throws IOException {
        String ogPath = path.substring(0, path.length()-3) + "BIN";
        try (RandomAccessFile ogFile = new RandomAccessFile(ogPath, READ_MODE)) {
            // moving the address while copying the data, until the event flow script place
            int currAddr = (int) outputFile.getFilePointer();
            while (currAddr < startAddr) {
                outputFile.writeInt(ogFile.readInt());
                currAddr = (int) outputFile.getFilePointer();
            }
        } catch (Exception e) {
            System.out.println("ERR opening original file to read initial data from: " + e.getMessage());
            throw e;
        }
    }


    private static String decodeInstruction(RandomAccessFile inputFile, boolean isPastText) throws IOException {
        int currPointer;
        byte instr = inputFile.readByte(); // the start of instructions is always FF

        // check if it is the last pointer
        currPointer = (int) inputFile.getFilePointer();
        if (!isPastText && currPointer >= textList.getFirstAddr()) {
            isLastInstruction = true;
            return "";
        }

        if (instr != Library.CMD_START) {
            // advance through padding
            while (instr == (byte)0) {
                instr = inputFile.readByte();
            }
            inputFile.seek(inputFile.getFilePointer()-1);
            return "";
        }

        instr = inputFile.readByte();

        String name, label, param, param2, addressStr;
        short check;
        int address;
        byte smolParam;
        //System.out.printf("yep: 0x%02x\n", instr); DEBUG
        Library.FlowInstruction flowInstr = Library.FLOW_INSTRUCTIONS.get(instr);
        switch(flowInstr) {
            case ret:
                name = flowInstr.name();
                // advancing the 00s
                getShortString(inputFile);
                if (isPastText) isLastInstruction = true; // this is used for situations where the labels have to be used to know about more code
                return "\t" + name + "\n";
            case jump:
                check = FileReadWriteUtils.readShort(inputFile, instructionOrder);
                name = simpleInstructionCheck(check, Library.FlowInstruction.jump.name(), instr);

                // get followup int, which corresponds to the address to jump to
                address = FileReadWriteUtils.readInt(inputFile, valOrder);

                if (!labels.containsKey(address)) {
                    label = Library.LABEL_TXT + Library.LABEL_SEPARATOR + labelNum++;
                    labels.put(address, label);
                } else {
                    label = labels.get(address);
                }
                return "\t" + name + "\t" + label + "\n";
            case jump_if:
                name = flowInstr.name();
                String condition = getShortString(inputFile);
                address = FileReadWriteUtils.readInt(inputFile, valOrder);
                if (!labels.containsKey(address)) {
                    label = Library.LABEL_TXT + Library.LABEL_SEPARATOR + labelNum++;
                    labels.put(address, label);
                } else {
                    label = labels.get(address);
                }
                return "\t" + name + "\t" + condition + "," + label + "\t" + Library.COMMENT_SYMBOL + " the parameter's exact meaning is unknown, but it might be related to game flags\n";
            //case UNKNOWN_COMMAND_27:
            //    name = flowInstr.name(); // TEST
            //    param = getShortString(inputFile);
            //    return "\t" + name + "\t" + param + "\n";
            case battle:
                name = flowInstr.name();
                short p = getShortVal(inputFile);
                param = String.format(Library.HEX_PREFIX + "%04x", p);
                return "\t" + name + "\t" + param + "\t"+ Library.COMMENT_SYMBOL + " " + Library.BATTLES[p] + "\n";
            case ld_world_map:
                name = flowInstr.name();
                param = getShortString(inputFile);
                addressStr = getIntString(inputFile);
                return "\t" + name + "\t" + param + "," + addressStr + "\t"+ Library.COMMENT_SYMBOL + " loads a world map\n";
            case ld_file:
                name = flowInstr.name();
                param = getShortString(inputFile);
                addressStr = getIntString(inputFile);
                return "\t" + name + "\t" + param + "," + addressStr + "\t"+ Library.COMMENT_SYMBOL + " loads another event file\n";
            case ld_3d_map:
                name = flowInstr.name();
                param = getShortString(inputFile);
                String x = getByteString(inputFile);
                String y = getByteString(inputFile);
                String direction = getByteString(inputFile);
                String fourthParam = getByteString(inputFile);

                return "\t" + name + "\t" + param + "," + x + "," + y + "," + direction + "," + fourthParam +
                        "\t"+ Library.COMMENT_SYMBOL + " ld_3d_map <map ID>,<X>,<Y>,<direction (0|1|2|3 -> E|W|S|N)>, <unknown>\n";
            case play_MV:
                name = flowInstr.name();
                param = String.format("MV%02x.pmf", inputFile.readByte());
                param2 = getByteString(inputFile);
                return "\t" + name + "\t" + param + "," + param2 + "\t"+ Library.COMMENT_SYMBOL + " second parameter is some kind of flag?\n";
            case open_save_menu:
                name = flowInstr.name();
                return "\t" + name + "\n";
            case wait:
                name = flowInstr.name();
                String ticks = getShortString(inputFile);
                return "\t" + name + "\t" + ticks + "\t"+ Library.COMMENT_SYMBOL + " value in ticks \n";
            case player_option:
                name = flowInstr.name();
                param = getShortString(inputFile);
                address = getInt(inputFile);
                if (!labels.containsKey(address)) {
                    label = Library.LABEL_TXT + Library.LABEL_SEPARATOR + labelNum++;
                    labels.put(address, label);
                } else {
                    label = labels.get(address);
                }
                return "\t" + name + "\t" + param + "," + label + "\t"+ Library.COMMENT_SYMBOL + " " + name + " <option num?>,<label>\n";
            case ld_text:
                check = FileReadWriteUtils.readShort(inputFile, instructionOrder);
                name = simpleInstructionCheck(check, flowInstr.name(), instr);
                address = getInt(inputFile);
                return "\t" + name + "\t" + textList.indexOfText(address) + "\t"+ Library.COMMENT_SYMBOL + " idx of text in .text section\n";
            case open_dialog:
                check = FileReadWriteUtils.readShort(inputFile, instructionOrder);
                name = simpleInstructionCheck(check, flowInstr.name(), instr);
                return "\t" + name + "\t"+ Library.COMMENT_SYMBOL + " opens dialog box graphic\n";
            case close_dialog:
                check = FileReadWriteUtils.readShort(inputFile, instructionOrder);
                name = simpleInstructionCheck(check, flowInstr.name(), instr);
                return "\t" + name + "\t"+ Library.COMMENT_SYMBOL + " closes dialog box graphic\n";
            case pose:
                name = flowInstr.name();
                byte character = inputFile.readByte();
                byte pose = inputFile.readByte();
                String poseStr;
                if (pose < 0) {
                    poseStr =  String.format(Library.HEX_PREFIX + "%02x", pose);
                } else poseStr = Library.POSES.values()[pose].name();
                return "\t" + name + "\t" + String.format(Library.HEX_PREFIX + "%02x", character) + "," + poseStr + ","
                        + getByteString(inputFile) + "," + getByteString(inputFile) + "," + Library.EVENT_DIRS.values()[inputFile.readByte()] + ","
                        + getByteString(inputFile) + "," + getIntString(inputFile) + "\t"
                        + Library.COMMENT_SYMBOL + " pose <character ID>,<pose>,<X>,<Y>,<direction>,<unknown>,<unknown>\n";
            case fx:
                name = flowInstr.name();
                return "\t" + name + "\t" + getByteString(inputFile) + "," + getByteString(inputFile) + ","
                        + getIntString(inputFile) + "," + getIntString(inputFile) + "\t"+ Library.COMMENT_SYMBOL + " makes effect happen, like lightning. No idea of the specifics\n";
            case clr_char:
                name = flowInstr.name();
                param = getShortString(inputFile);
                return "\t" + name + "\t" + param + "\t"+ Library.COMMENT_SYMBOL + " this clears the character numbered in the parameter\n";
            case ld_portrait:
                name = flowInstr.name();
                smolParam = inputFile.readByte(); // character ID
                return "\t" + name + "\t" + Library.PORTRAIT_CHARS.values()[smolParam] + "," + Library.PORTRAIT_ORIENTATION.values()[inputFile.readByte()] +
                        "\t"+ Library.COMMENT_SYMBOL + "\n";
            case close_portrait:
                check = FileReadWriteUtils.readShort(inputFile, instructionOrder);
                name = simpleInstructionCheck(check, flowInstr.name(), instr);
                return "\t" + name + "\t"+ Library.COMMENT_SYMBOL + " closes portrait graphic\n";
            case emote:
                name = flowInstr.name();
                smolParam = inputFile.readByte(); // character ID
                return "\t" + name + "\t" + smolParam + "," + Library.EMOTES.values()[inputFile.readByte()] + "\t"+ Library.COMMENT_SYMBOL + " first parameter = character ID (dependent on scene)\n";
            case screen_fx:
                name = flowInstr.name();
                check = getShortVal(inputFile);
                return "\t" + name + "\t" + getShortString(check) + "\t"+ Library.COMMENT_SYMBOL + " does an effect that fills the full screen. In this case, " + Library.SCREEN_EFFECTS[check] + "\n";
            case fade_char:
                name = flowInstr.name();
                return "\t" + name + "\t" + inputFile.readByte() + "," + inputFile.readByte() + "\t"+ Library.COMMENT_SYMBOL + " fades character with ID in first param with speed in second param\n";
            case plan_char_mov:
                name = flowInstr.name();
                return "\t" + name + "\t"
                        + getByteString(inputFile) + "," + getByteString(inputFile) + "," + getByteString(inputFile) + "," +
                        getByteString(inputFile) + "," + getByteString(inputFile) + "," + getByteString(inputFile)
                        + "\t"+ Library.COMMENT_SYMBOL + " " + name + "\t<character ID>,<trajectory idx>,<speed>,<direction_at_destination>,...\n";

            case follow_char:
                name = flowInstr.name();
                check = getShortVal(inputFile);
                return "\t" + name + "\t" + check + "\t"+ Library.COMMENT_SYMBOL + " sets camera to follow character. parameter = character ID (dependent on scene)\n";
            case clr_emote:
                name = flowInstr.name();
                check = getShortVal(inputFile);
                return "\t" + name + "\t" + check + "\t"+ Library.COMMENT_SYMBOL + " clears the emote of the character in the parameter\n";
            case do_planned_moves:
                check = FileReadWriteUtils.readShort(inputFile, instructionOrder);
                name = simpleInstructionCheck(check, flowInstr.name(), instr); // HERE
                return "\t" + name + "\t"+ Library.COMMENT_SYMBOL + " executes the previously planned character movements\n";

            case tp_char:
                name = flowInstr.name();
                param = getShortString(inputFile);
                return "\t" + name + "\t" + param + "," + getIntString(inputFile) + "\t"+ Library.COMMENT_SYMBOL + " sets position/direction of a character, specifics of parameters are unknown\n";
            case play_song:
                name = flowInstr.name();
                param = getShortString(inputFile);
                return "\t" + name + "\t" + param + "\t"+ Library.COMMENT_SYMBOL + " plays the song whose ID is in the parameter\n";
            case play_sfx:
                name = flowInstr.name();
                check = getShortVal(inputFile);
                return "\t" + name + "\t" + getShortString(check) + "\t"+ Library.COMMENT_SYMBOL + " plays sfx: " + Library.getSFXDescription(check) + "\n";
            default:
                StringBuilder toRet = new StringBuilder();
                toRet.append("\t" + Library.UNKNOWN_INSTR_TEXT + "|FF").append(String.format("%02x", instr))
                        .append(String.format("%02x", inputFile.readByte())).append(String.format("%02x", inputFile.readByte()));
                int params = Library.PARAM_NUM.get(flowInstr);
                for (int i = 0; i < params; i++) {
                    toRet.append(",").append(String.format("%08x", FileReadWriteUtils.readInt(inputFile, instructionOrder)));
                }
                return toRet.append("\n").toString();
        }
    }
    private static void encodeInstruction(RandomAccessFile inputFile, RandomAccessFile outputFile) throws IOException, OperationNotSupportedException {
        String line = inputFile.readLine();
        line = EventFileOps.removeCommentAndSpaces(line);

        if (line.compareTo(Library.COMMENT_INDICATOR) == 0) {
            // then the line is fully commented
            return;
        }

        String[] spaceSplit = line.split("[ \t]");

        if (spaceSplit.length == 1) {
            // if it is a new line, readLine returns an empty string
            if (spaceSplit[0].length() == 0) {
                emptyLineHappened = true;
                return;
            }
        }

        // skipping first spaces/tabs
        int i = skipSpacesNTabs(spaceSplit, 0);

        String instr = spaceSplit[i];
        if (instr.contains("|")) {
            encodeUnknownInstruction(outputFile, instr);
            return;
        }

        // no parameters
        // this includes ret, open_dialog, close_dialog, close_portrait, do_planned_moves
        if (i+1 == spaceSplit.length) {
            writeIntInstruction(outputFile, instr, (short)0); // 0 for padding

            // in case this is a return instruction, must add padding if the next instruction isn't aligned with multiples of 8
            if (instr.compareTo(Library.FlowInstruction.ret.name()) == 0) {
                while(outputFile.getFilePointer() % 8 != 0) outputFile.writeByte(0);
            }
        } else { // has parameters
            i = skipSpacesNTabs(spaceSplit, i+1); // skips spaces and tabs until the first parameter
            String params = spaceSplit[i];
            String[] paramSplit = params.split(",");

            // jump instruction
            if (instr.compareTo(Library.FlowInstruction.jump.name()) == 0) {
                String label = paramSplit[0];
                String labelNum = label.split(Library.LABEL_SEPARATOR)[1];

                // write first 4 bytes
                writeIntInstruction(outputFile, instr, (short)0); // 0 for padding

                // register a required address
                int currAddr = (int) outputFile.getFilePointer();
                addLabelRef((short)Integer.parseInt(labelNum), currAddr);
                FileReadWriteUtils.writeInt(outputFile, valOrder, 0); // padding while no address

                // if not aligned to multiple of 8, padding
                while(outputFile.getFilePointer() % 8 != 0) outputFile.writeByte(0);

                // jump if instruction
            } else if (instr.compareTo(Library.FlowInstruction.jump_if.name()) == 0) {
                String param = paramSplit[0];
                String label = paramSplit[1];
                String labelNum = label.split(Library.LABEL_SEPARATOR)[1];

                // write first 4 bytes
                writeIntInstruction(outputFile, instr, extractShortFromString(param));

                // register a required address
                int currAddr = (int) outputFile.getFilePointer();
                addLabelRef((short)Integer.parseInt(labelNum), currAddr);
                FileReadWriteUtils.writeInt(outputFile, valOrder, 0); // padding while no address

                // simple 1 short parameter instructions
            } else if (instr.compareTo(Library.FlowInstruction.UNKNOWN_COMMAND_27.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.battle.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.wait.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.clr_char.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.screen_fx.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.play_song.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.play_sfx.name()) == 0) {
                String param = paramSplit[0];

                // write first 4 bytes
                writeIntInstruction(outputFile, instr, extractShortFromString(param));

                // simple 1 short parameter instructions (in decimal)
            } else if (instr.compareTo(Library.FlowInstruction.clr_emote.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.follow_char.name()) == 0) {
                String param = paramSplit[0];
                writeIntInstruction(outputFile, instr, Short.parseShort(param));

                // instructions with 1 short param and 1 int
            } else if (instr.compareTo(Library.FlowInstruction.ld_world_map.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.ld_file.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.tp_char.name()) == 0)  {
                String shortParam = paramSplit[0];
                String intParam = paramSplit[1];

                // write first 4 bytes
                writeIntInstruction(outputFile, instr, extractShortFromString(shortParam));

                // write address
                FileReadWriteUtils.writeInt(outputFile, valOrder, extractIntFromString(intParam));

            } else if (instr.compareTo(Library.FlowInstruction.ld_3d_map.name()) == 0) {
                String mapID = paramSplit[0];
                String x = paramSplit[1];
                String y = paramSplit[2];
                String dir = paramSplit[3];
                String param5 = paramSplit[4];

                // write first 4 bytes
                writeIntInstruction(outputFile, instr, extractShortFromString(mapID));

                // write next parameters
                outputFile.writeByte(extractByteFromString(x));
                outputFile.writeByte(extractByteFromString(y));
                outputFile.writeByte(extractByteFromString(dir));
                outputFile.writeByte(extractByteFromString(param5));
            } else if (instr.compareTo(Library.FlowInstruction.play_MV.name()) == 0) {
                String movieFileName = paramSplit[0]; // format: MVXX.pmf, where XX is the number
                String param1 = movieFileName.substring(2,4); // byte (hex without 0x)
                String param2 = paramSplit[1]; // byte (hex)

                outputFile.writeByte(Library.CMD_START);
                outputFile.writeByte(Library.FLOW_INSTRUCTIONS_REVERSE.get(instr));
                outputFile.writeByte((byte)Short.parseShort(param1, 16));
                outputFile.writeByte((byte)Short.parseShort(param2.substring(2), 16));
            } else if (instr.compareTo(Library.FlowInstruction.player_option.name()) == 0) {
                String param = paramSplit[0];
                String label = paramSplit[1];
                String labelNum = label.split(Library.LABEL_SEPARATOR)[1];

                // write first 4 bytes
                writeIntInstruction(outputFile, instr, extractShortFromString(param));

                // register a required address
                int currAddr = (int) outputFile.getFilePointer();
                addLabelRef((short)Integer.parseInt(labelNum), currAddr);
                FileReadWriteUtils.writeInt(outputFile, valOrder, 0); // padding while no address
            } else if (instr.compareTo(Library.FlowInstruction.ld_text.name()) == 0) {
                String textID = paramSplit[0]; // short

                // write first 4 bytes
                writeIntInstruction(outputFile, instr, (short) 0); // 0 for padding

                // register a required text address
                int currAddr = (int) outputFile.getFilePointer();
                addTextRef((short) Integer.parseInt(textID), currAddr);
                FileReadWriteUtils.writeInt(outputFile, valOrder, 0); // padding while no address

            } else if (instr.compareTo(Library.FlowInstruction.fx.name()) == 0) {
                String param1 = paramSplit[0]; // byte (hex)
                String param2 = paramSplit[1]; // byte (hex)
                String param3 = paramSplit[2]; // int (hex)
                String param4 = paramSplit[3]; // int (hex)

                // write first 4 bytes
                outputFile.writeByte(Library.CMD_START);
                outputFile.writeByte(Library.FLOW_INSTRUCTIONS_REVERSE.get(instr));
                outputFile.writeByte(extractByteFromString(param1));
                outputFile.writeByte(extractByteFromString(param2));

                // write 2 ints
                FileReadWriteUtils.writeInt(outputFile, valOrder, extractIntFromString(param3));
                FileReadWriteUtils.writeInt(outputFile, valOrder, extractIntFromString(param4));

            } else if (instr.compareTo(Library.FlowInstruction.pose.name()) == 0) {
                String charID = paramSplit[0]; // byte (hex)
                String pose = paramSplit[1]; // pose text OR byte (hex)
                String x = paramSplit[2]; // byte (hex)
                String y = paramSplit[3]; // byte (hex)
                String direction = paramSplit[4]; // direction text
                String unknown1 = paramSplit[5]; // byte (hex)
                String unknown2 = paramSplit[6]; // int (hex)

                // write first 4 bytes
                outputFile.writeByte(Library.CMD_START);
                outputFile.writeByte(Library.FLOW_INSTRUCTIONS_REVERSE.get(instr));
                outputFile.writeByte(extractByteFromString(charID));

                // print pose or the byte if it isn't registered
                try {
                    outputFile.writeByte(Library.POSES.valueOf(pose).ordinal());
                } catch (IllegalArgumentException e) {
                    outputFile.writeByte(extractByteFromString(pose));
                }

                // write params
                outputFile.writeByte(extractByteFromString(x));
                outputFile.writeByte(extractByteFromString(y));
                outputFile.writeByte(Library.EVENT_DIRS.valueOf(direction).ordinal());
                outputFile.writeByte(extractByteFromString(unknown1));

                // write final int
                FileReadWriteUtils.writeInt(outputFile, valOrder, extractIntFromString(unknown2));
            } else if (instr.compareTo(Library.FlowInstruction.ld_portrait.name()) == 0) {
                String param1 = paramSplit[0]; // text of the character
                String param2 = paramSplit[1]; // text of the position

                // write first 4 bytes
                outputFile.writeByte(Library.CMD_START);
                outputFile.writeByte(Library.FLOW_INSTRUCTIONS_REVERSE.get(instr));
                outputFile.writeByte(Library.PORTRAIT_CHARS.valueOf(param1).ordinal());
                outputFile.writeByte(Library.PORTRAIT_ORIENTATION.valueOf(param2).ordinal());

            } else if (instr.compareTo(Library.FlowInstruction.emote.name()) == 0) {
                String param1 = paramSplit[0]; // byte
                String param2 = paramSplit[1]; // text of the emote

                // write first 4 bytes
                outputFile.writeByte(Library.CMD_START);
                outputFile.writeByte(Library.FLOW_INSTRUCTIONS_REVERSE.get(instr));
                outputFile.writeByte((byte)Short.parseShort(param1));
                outputFile.writeByte(Library.EMOTES.valueOf(param2).ordinal());
            } else if (instr.compareTo(Library.FlowInstruction.fade_char.name()) == 0) {
                String param1 = paramSplit[0]; // byte
                String param2 = paramSplit[1]; // byte

                // write first 4 bytes
                outputFile.writeByte(Library.CMD_START);
                outputFile.writeByte(Library.FLOW_INSTRUCTIONS_REVERSE.get(instr));
                outputFile.writeByte((byte)Short.parseShort(param1));
                outputFile.writeByte((byte)Short.parseShort(param2));
            } else if (instr.compareTo(Library.FlowInstruction.plan_char_mov.name()) == 0) {
                String charID = paramSplit[0]; // byte (hex)
                String dir = paramSplit[1]; // byte (hex)
                String speed = paramSplit[2]; // byte (hex)
                String dest_dir = paramSplit[3]; // byte (hex)
                String param5 = paramSplit[4]; // byte (hex)
                String param6 = paramSplit[5]; // byte (hex)

                // write first 4 bytes
                outputFile.writeByte(Library.CMD_START);
                outputFile.writeByte(Library.FLOW_INSTRUCTIONS_REVERSE.get(instr));
                outputFile.writeByte((byte)Short.parseShort(charID.substring(2), 16));
                outputFile.writeByte((byte)Short.parseShort(dir.substring(2), 16));

                // write other 4 params
                outputFile.writeByte((byte)Short.parseShort(speed.substring(2), 16));
                outputFile.writeByte((byte)Short.parseShort(dest_dir.substring(2), 16));
                outputFile.writeByte((byte)Short.parseShort(param5.substring(2), 16));
                outputFile.writeByte((byte)Short.parseShort(param6.substring(2), 16));
            } else {
                throw new OperationNotSupportedException("Some instruction was named incorrectly\t\t" + instr + "\n");
            }
        }
    }

    /**
     * Saves the addresses associated to characters in the scene (when player talks to them)
     * @param inputFile object used to read from input file
     * @param outputFile object used to write to output file
     * @throws IOException file stuff
     */
    private static void populateTalkAddresses(RandomAccessFile inputFile, RandomAccessFile outputFile) throws IOException {
        long pointerBk = inputFile.getFilePointer();
        //boolean oneEmpty = false; // because I've seen a file where it skips a character

        //inputFile.seek(Library.ADDRESS_OF_CHARACTER_DATA);
        for (int i = 0; i < 64; i++) {
            inputFile.seek(Library.ADDRESS_OF_CHARACTER_DATA + (i * Library.CHARACTER_DATA_SIZE));

            //if (isNextCharacterDataEmpty(inputFile)) {
            //    continue;
            //}
            inputFile.seek(inputFile.getFilePointer() + Library.CHARACTER_DATA_EVENT_ADDRESS_OFFSET);
            int address = FileReadWriteUtils.readInt(inputFile, valOrder);
            if (address != 0xFFFFFFFF) {
                // TODO also check address at offset 0x14, Elly in E0_023 has one for some reason....
                // this means we gotta redefine how to write the .talk section...
                // perhaps, for every FFFFFFF, we just put an _ so we know where the pointer goes later
                String label = Library.LABEL_TXT + Library.LABEL_SEPARATOR + labelNum++;
                labels.put(address, label);
                outputFile.writeBytes(String.format("%02d\t\t%s\t\t%s <Character in scene>:  <Label to code that executes when spoken to>\n", i, label, Library.COMMENT_SYMBOL));
            }
        }

        outputFile.writeBytes("\n");
        inputFile.seek(pointerBk);
    }

    /**
     * Checks if the next character data entry is "empty"
     * @param inputFile the object used to access the input file
     * @return {@code true} if it is empty, {@code false} otherwise
     * @throws IOException reading the file
     */
    private static boolean isNextCharacterDataEmpty(RandomAccessFile inputFile) throws IOException {
        long pointerBk = inputFile.getFilePointer();

        boolean short1 = (inputFile.readShort() == (short) 0xFFFF);
        boolean short2 = (inputFile.readShort() == (short) 0);
        boolean address = (inputFile.readInt() == 0xFFFFFFFF);
        boolean int1 = (inputFile.readInt() == 0);
        boolean int2 = (FileReadWriteUtils.readInt(inputFile, ByteOrder.LITTLE_ENDIAN) == 0xFF);
        boolean int3 = (inputFile.readInt() == 0);

        boolean int4 = (inputFile.readInt() == 0xFFFFFFFF);
        boolean int5 = (inputFile.readInt() == 0);
        boolean int6 = (FileReadWriteUtils.readInt(inputFile, ByteOrder.LITTLE_ENDIAN) == 0xFF);
        boolean int7 = (inputFile.readInt() == 0);

        inputFile.seek(pointerBk);

        return short1 && short2 && address && int1 && int2 && int3 && int4 && int5 && int6 && int7;
    }

    private static void addLabelRef(short lNum, int currAddr) {
        if (labelReferenceLocations.containsKey(lNum)) {
            labelReferenceLocations.get(lNum).add(currAddr);
        } else {
            LinkedList<Integer> toAdd = new LinkedList<>();
            toAdd.add(currAddr);
            labelReferenceLocations.put(lNum, toAdd);
        }
    }

    private static void addTextRef(short textId, int currAddr) {
        if (textReferenceLocations.containsKey(textId)) {
            textReferenceLocations.get(textId).add(currAddr);
        } else {
            LinkedList<Integer> toAdd = new LinkedList<>();
            toAdd.add(currAddr);
            textReferenceLocations.put(textId, toAdd);
        }
    }

    private static void writeIntInstruction(RandomAccessFile outputFile, String instr, short val) throws IOException {
        outputFile.writeByte(Library.CMD_START);
        outputFile.writeByte(Library.FLOW_INSTRUCTIONS_REVERSE.get(instr));
        FileReadWriteUtils.writeShort(outputFile, valOrder, val);
    }

    private static void encodeUnknownInstruction(RandomAccessFile outFile, String instr) throws OperationNotSupportedException, IOException {
        String[] splitDeeper = instr.split("[|]");
        if (splitDeeper[0].compareTo(Library.UNKNOWN_INSTR_TEXT) != 0) {
            throw new OperationNotSupportedException("unknown instruction is not formatted correctly");
        }
        String[] splitCommas = splitDeeper[1].split(",");
        for (String intValue : splitCommas) {
            FileReadWriteUtils.writeInt(outFile, instructionOrder, (int)Long.parseLong(intValue, 16));
        }
    }

    public static int skipSpacesNTabs(String[] split, int idx) {
        int i;
        for (i = idx; i < split.length; i++) {
            if (split[i].length() > 0) break;
        }
        return i;
    }

    /**
     * Fills in previous references that couldn't be filled at the time
     * @param outFile the object that allows writing to the output file
     * @param id the number of the text or label
     * @param isText @true if filling in a text reference, @false if it is a reference to more event flow code
     * @throws IOException file stuff
     */
    private static void fillInRef(RandomAccessFile outFile, short id, boolean isText) throws IOException {
        int currAddr = (int) outFile.getFilePointer();
        LinkedList<Integer> addrs;
        if (isText) {
            addrs = textReferenceLocations.get(id);
        } else {
            addrs = labelReferenceLocations.get(id);
        }

        // addrs is null when, for instance, the file has unused text
        if (addrs == null) {
            System.out.println("Unused text");
            return;
        }
        for (Integer addr : addrs) {
            outFile.seek(addr);
            FileReadWriteUtils.writeInt(outFile, valOrder, currAddr);
        }
        outFile.seek(currAddr);
    }

    public static String removeCommentAndSpaces(String line) {
        if (line.length() == 0) return line;

        // checking if the whole line is a comment by first checking if it starts with the comment symbol
        if(line.substring(0, Library.COMMENT_SYMBOL.length()).compareTo(Library.COMMENT_SYMBOL) == 0) {
            return Library.COMMENT_INDICATOR;
        }

        int index = 0;

        index = line.indexOf(Library.COMMENT_SYMBOL.charAt(0), index);
        while (index > 0) {
            if (line.substring(index, index + Library.COMMENT_SYMBOL.length()).compareTo(Library.COMMENT_SYMBOL) == 0) {
                break;
            } else {
                index += 2;
            }
            index = line.indexOf(Library.COMMENT_SYMBOL.charAt(0), index);
        }

        if (index == -1) {
            index = line.length();
        }
        index--;
        while (line.charAt(index) == '\t' || line.charAt(index) == ' ') {
            index--;
            // if index gets negative, then the line is FULLY commented
            if (index < 0) {
                return Library.COMMENT_INDICATOR;
            }
        }
        return line.substring(0, index+1);
    }

    private static String simpleInstructionCheck(short check, String name, byte code) {
        if (check != 0) throw new RuntimeException("NOT ACTUAL " + name + " INSTRUCTION ("+code+")");
        return name;
    }

    private static byte extractByteFromString(String value) throws OperationNotSupportedException {
        if (value.substring(0, 2).compareTo(Library.HEX_PREFIX) != 0) {
            throw new OperationNotSupportedException("byte value is not formatted correctly");
        }
        String prefixRemoved = value.substring(2);
        return (byte) Short.parseShort(prefixRemoved, 16);
    }

    private static short extractShortFromString(String value) throws OperationNotSupportedException {
        if (value.substring(0, 2).compareTo(Library.HEX_PREFIX) != 0) {
            throw new OperationNotSupportedException("short value is not formatted correctly");
        }
        String prefixRemoved = value.substring(2);
        return (short) Integer.parseInt(prefixRemoved, 16);
    }

    private static int extractIntFromString(String value) throws OperationNotSupportedException {
        if (value.substring(0, 2).compareTo(Library.HEX_PREFIX) != 0) {
            throw new OperationNotSupportedException("int value is not formatted correctly");
        }
        String prefixRemoved = value.substring(2);
        return (int) Long.parseLong(prefixRemoved, 16);
    }

    private static String getByteString(RandomAccessFile file) throws IOException {
        return String.format(Library.HEX_PREFIX + "%02x", file.readByte());
    }

    private static String getShortString(RandomAccessFile file) throws IOException {
        return String.format(Library.HEX_PREFIX + "%04x", FileReadWriteUtils.readShort(file, valOrder));
    }

    private static String getShortString(short val) throws IOException {
        return String.format(Library.HEX_PREFIX + "%04x", val);
    }

    private static short getShortVal(RandomAccessFile file) throws IOException {
        return FileReadWriteUtils.readShort(file, valOrder);
    }

    private static String getIntString(RandomAccessFile file) throws IOException {
        return String.format(Library.HEX_PREFIX + "%08x", FileReadWriteUtils.readInt(file, valOrder));
    }

    private static int getInt(RandomAccessFile file) throws IOException {
        return FileReadWriteUtils.readInt(file, valOrder);
    }

    public static int readHexIntString(String number) throws OperationNotSupportedException {
        if (number.substring(0, 2).compareTo(Library.HEX_PREFIX) != 0) {
            throw new OperationNotSupportedException("A hex value was not formatted correctly (0x...)");
        }
        return Integer.parseInt(number.substring(2), 16);
    }

    public static void testText(RandomAccessFile file) throws IOException {
        file.seek(Library.ADDRESS_WITH_TEXT_TABLE_POINTER);
        int address = FileReadWriteUtils.readInt(file, valOrder);
        TextList textList = TextList.readEncodedTextList(file, address, false);// TO CHANGE TO FUNCTIONAL, CHANGE THIS TO TRUE
        System.out.println(textList);
        //System.out.println(textList.writeText());
    }
}
