package dataAccess;

import dataAccess.dataTypes.InnerFileAddress;
import dataAccess.dataTypes.InnerFileAddressList;
import dataAccess.dataTypes.Pair;
import dataAccess.dataTypes.TextList;

import javax.naming.OperationNotSupportedException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;

import static dataAccess.FileReadWriteUtils.roundToLBA;

public class EventFileOps {

    private static final String READ_MODE = "r";
    private static final String WRITE_MODE = "rw";
    public static final String EXTENSION_1 = ".BIN";
    public static final String EXTENSION_2 = ".bin";
    public static final String EVENT_SCRIPT_EXTENSION_1 = ".EVS";
    public static final String EVENT_SCRIPT_EXTENSION_2 = ".evs";
    public static final String DEC_SCRIPT_EXTENSION_1 = ".DEC";
    public static final String DEC_SCRIPT_EXTENSION_2 = ".dec";
    public static final String EXTRACTED_DIR_NAME = "extracted";
    public static final String OUTPUT_DIR_NAME = "output";
    public static final int BASE_EVS_FILENAME_SIZE = 6;

    private static int labelNum;
    private static boolean isLastInstruction;
    private static boolean emptyLineHappened;

    public static final ByteOrder valOrder = ByteOrder.LITTLE_ENDIAN;
    public static final ByteOrder instructionOrder = ByteOrder.BIG_ENDIAN;

    private static TextList textList;

    // stores address values with their associated label name. Used when decoding an event file
    // the boolean indicates if the label has been used yet or not
    private static final LinkedHashMap<Integer, Pair<String, Boolean>> labels = new LinkedHashMap<>();

    // stores text IDs together with a list of addresses that need to be filled with the real address of the text position
    private static final HashMap<Short, LinkedList<Integer>> textReferenceLocations = new HashMap<>();

    // Stores label names together with a list of addresses that need to be filled with the real address of the label
    private static final HashMap<String, LinkedList<Integer>> labelReferenceLocations = new HashMap<>();
    // stores label names together with the real address they represent
    private static final HashMap<String, Integer> labelReferenceRealVals = new HashMap<>();


    public static void extract(String path) throws IOException, OperationNotSupportedException {
        if (!path.endsWith(EXTENSION_1) && !path.endsWith(EXTENSION_2)) {
            //System.out.println("PATH was: " + path);
            throw new OperationNotSupportedException("Only .bin files are supported");
        }

        InnerFileAddressList addressList;
        String basePath;
        try (RandomAccessFile baseFile = new RandomAccessFile(path, READ_MODE)) {
            addressList = InnerFileAddressList.makeList(baseFile, valOrder);

            String[] pathArray = path.split("/");
            String baseNameWExt = pathArray[pathArray.length-1];
            String baseName = baseNameWExt.substring(0, baseNameWExt.length()-4);
            //basePath = path.substring(0, path.length()-baseNameWExt.length()) + baseName;
            String initialPath = pathArray[0] + "/" + EXTRACTED_DIR_NAME + "/";

            // create directory if it doesn't exist
            File dir = new File(initialPath);
            if (!dir.exists()) {
                dir.mkdir();
            }

            basePath = initialPath + baseName;

            // create directory if it doesn't exist
            dir = new File(basePath);
            if (!dir.exists()) {
                dir.mkdir();
            }

            // getting each inner file
            for (int i = 0; i < addressList.getListSize(); i++) {
                String nameAddition = String.format("_%03d", i);
                String newFileName = basePath + "/" + baseName + nameAddition + EVENT_SCRIPT_EXTENSION_1;
                System.out.printf("%s\r", newFileName);

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
                    System.out.println(e.getMessage());
                    throw e;
                }
                System.out.print("DONE\r");
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw e;
        }
    }

    /**
     * Groups up all the files back into an EX.BIN file
     * @param ebootPath the path where the original EBOOT.BIN is
     * @param dirPath the path to where all the sub-files are
     * @param destinationDir the path to save the new EX.BIN (and EBOOT.BIN) files
     * @param filename the name of the EX.BIN file, without the extension
     * @param isJ {@code true} if the event file was extracted from the japanese version of the game
     * @throws IOException file IO exceptions
     */
    public static void archive(String ebootPath, String dirPath, String destinationDir, String filename, boolean isJ) throws IOException {
        //File dir = new File(dirPath);
        //File[] directoryListing = dir.listFiles();

        LinkedList<InnerFileAddress> fileAddrList = new LinkedList<>();

        try (Stream<Path> pathStream = Files.list(Paths.get(dirPath))){

            LinkedList<Path> pathList = new LinkedList<>();
            pathStream
                    .filter(s -> s.toString().toUpperCase().endsWith(EVENT_SCRIPT_EXTENSION_1))
                    //.sorted()
                    .forEach(pathList::add);
            //LinkedList<Path> pathList = pathStream.collect(Collectors.toCollection(LinkedList::new));
            int headerSize = pathList.size() * 4 * 2; // x 4 bytes per int x 2 ints (start and end)
            int startAddr = roundToLBA(headerSize);

            for(Path child : pathList) {
                int endAddr = (int) ((child.toFile().length() + startAddr));
                InnerFileAddress fileAddr = new InnerFileAddress(startAddr, endAddr, 0); // currAddr only used for reading
                fileAddrList.add(fileAddr);
                startAddr = endAddr;
            }
            InnerFileAddressList fileList = new InnerFileAddressList(fileAddrList);


            // Update the EBOOT.BIN
            int eventFileNum = Integer.parseInt("" + filename.charAt(1));
            File ebootOut = new File(destinationDir, Library.EBOOT_NAME);
            if (ebootOut.exists()) {
                updateEbootFileList(ebootOut, ebootOut, eventFileNum, fileList, isJ);
            } else {
                File ebootIn = new File(ebootPath, Library.EBOOT_NAME);
                updateEbootFileList(ebootIn, ebootOut, eventFileNum, fileList, isJ);
            }


            String destinationFile = destinationDir + filename + EventFileOps.EXTENSION_1;

            try (RandomAccessFile file = new RandomAccessFile(destinationFile, WRITE_MODE)) {
                fileList.writeFileAddresses(file, valOrder);

                for (Path child : pathList) {
                    System.out.printf("%s\r", child.toFile().getPath());
                    try (RandomAccessFile subFile = new RandomAccessFile(child.toFile(), READ_MODE)) {
                        int sizeInInts = (int) subFile.length() / 4;
                        for (int i = 0; i < sizeInInts; i++) {
                            file.writeInt(subFile.readInt());
                        }

                    } catch (Exception e) {
                        System.out.println("ERR in reading subfile for archiving: " + e.getMessage());
                        throw e;
                    }
                    System.out.print("DONE\r");
                }

                /*String prevFilename = null;
                for (Object child : pathStream.toArray()) {
                    if (prevFilename == null) {
                        prevFilename = ((File)child).getName();
                        continue;
                    }
                    String currFilename = ((File)child).getName();
                    String prevFilenameComp = prevFilename.substring(0, BASE_EVS_FILENAME_SIZE);
                    String currFilenameComp = currFilename.substring(0, BASE_EVS_FILENAME_SIZE);

                    String fileToUse;
                    // checking if the files have the same base name (E0_000.EVS vs E0_000_ENCRYPTED.EVS)
                    if (prevFilenameComp.compareTo(currFilenameComp) == 0) {
                        fileToUse = prevFilename.length() < currFilename.length() ? currFilename : prevFilename;
                    } else { // gotta write the previous file

                    }

                    try (RandomAccessFile subFile = new RandomAccessFile((File)child, READ_MODE)) {
                        int sizeInInts = (int) subFile.length() / 4;
                        for (int i = 0; i < sizeInInts; i++) {
                            file.writeInt(subFile.readInt());
                        }
                    } catch (Exception e) {
                        System.out.println("ERR in reading subfile for archiving: " + e.getMessage());
                        throw e;
                    }
                }*/

            } catch (Exception e) {
                System.out.println("ERR in archiving process: " + e.getMessage());
                throw e;
            }

        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * Decodes an event file's flow script
     * @param path the path to the file to decode
     * @param isJ {@code true} if the event file was extracted from the japanese version of the game
     * @throws IOException file IO exceptions
     */
    public static void decodeFlowScript(String path, boolean isJ) throws IOException {
        labelNum = 0;
        isLastInstruction = false;
        labels.clear();

        try (RandomAccessFile inputFile = new RandomAccessFile(path, READ_MODE)) {
            String outputPath = path.substring(0, path.length()-3) + "DEC";

            // delete file if it already exists
            File file = new File(outputPath);
            file.delete();

            try (RandomAccessFile outputFile = new RandomAccessFile(outputPath, WRITE_MODE)) {
                // Adding text to a string instead of printing directly so .addr part can be the first one in the file
                StringBuilder preCodePrint = new StringBuilder();

                // section .bgm
                preCodePrint.append(Library.SECTION_KEYWORD + "\t" + Library.BGM_SECTION_KEYWORD + "\n");
                //outputFile.writeBytes(Library.SECTION_KEYWORD + "\t" + Library.BGM_SECTION_KEYWORD + "\n");
                inputFile.seek(Library.STARTING_SONG_ADDRESS);
                short firstSong = FileReadWriteUtils.readShort(inputFile, valOrder);
                short secondSong = FileReadWriteUtils.readShort(inputFile, valOrder);
                preCodePrint.append(String.format("\t0x%02x\n\t0x%02x\n\n", firstSong, secondSong));
                //outputFile.writeBytes(String.format("\t0x%02x\n\t0x%02x\n\n", firstSong, secondSong));

                // saving all text strings
                // in the japanese version, there is no text list at the end of the file :(
                if (!isJ) {
                    inputFile.seek(Library.ADDRESS_WITH_TEXT_TABLE_POINTER);
                    int textTableAddr = FileReadWriteUtils.readInt(inputFile, valOrder);
                    textList = TextList.readEncodedTextList(inputFile, textTableAddr, true);
                } else {
                    textList = new TextList();
                }


                // section .talk
                preCodePrint.append(Library.SECTION_KEYWORD + "\t" + Library.TALK_SECTION_KEYWORD + "\n");
                //outputFile.writeBytes(Library.SECTION_KEYWORD + "\t" + Library.TALK_SECTION_KEYWORD + "\n");
                //decodeTalkAddresses(inputFile, outputFile, true);
                decodeTalkAddresses(inputFile, preCodePrint, true, isJ);

                // section .talk2
                preCodePrint.append(Library.SECTION_KEYWORD + "\t" + Library.TALK2_SECTION_KEYWORD + "\n");
                //outputFile.writeBytes(Library.SECTION_KEYWORD + "\t" + Library.TALK2_SECTION_KEYWORD + "\n");
                //decodeTalkAddresses(inputFile, outputFile, false);
                decodeTalkAddresses(inputFile, preCodePrint, false, isJ);

                // section .positions
                preCodePrint.append(Library.SECTION_KEYWORD + "\t" + Library.POS_SECTION_KEYWORD + "\n");
                //outputFile.writeBytes(Library.SECTION_KEYWORD + "\t" + Library.POS_SECTION_KEYWORD + "\n");
                //decodePositionsOrInteractables(inputFile, outputFile, false);
                decodePositionsOrInteractables(inputFile, preCodePrint, false, isJ);

                // section .interactables
                preCodePrint.append(Library.SECTION_KEYWORD + "\t" + Library.INTER_SECTION_KEYWORD + "\n");
                //outputFile.writeBytes(Library.SECTION_KEYWORD + "\t" + Library.INTER_SECTION_KEYWORD + "\n");
                //decodePositionsOrInteractables(inputFile, outputFile, true);
                decodePositionsOrInteractables(inputFile, preCodePrint, true, isJ);

                // address value zone
                inputFile.seek(Library.ADDRESS_WITH_FLOW_SCRIPT_POINTER - (isJ ? 4 : 0));
                int flowStartAddr = FileReadWriteUtils.readInt(inputFile, valOrder);

                if (flowStartAddr == -1) {
                // if the flow start addr was -1, use the earliest LABEL's address as the start address
                    // get the smallest known address
                    ArrayList<Integer> pointers = new ArrayList<>(labels.keySet());
                    if (pointers.isEmpty()) {
                        outputFile.writeBytes(Library.EMPTY_FILE_STRING);
                        System.out.println(Library.EMPTY_FILE_STRING);
                        return;
                    }
                    flowStartAddr = pointers.stream().min(Comparator.comparing(Integer::valueOf)).get();
                }

                // go to start of code
                inputFile.seek(flowStartAddr);

                // an address is known. Print it!
                outputFile.writeBytes(String.format("%s\t0x%08x\n\n", Library.ADDR_KEYWORD, flowStartAddr));

                // print all that was just formed, so the code can be printed right after
                outputFile.writeBytes(preCodePrint.toString());
                outputFile.writeBytes(String.format("%s\t%s\n", Library.SECTION_KEYWORD, Library.CODE_AREA_KEYWORD));

                while (!isLastInstruction) {
                    int currPointer = (int) inputFile.getFilePointer();
                    // DEBUG
                    //System.out.println("ADDRESS:" + String.format("0x%08x", currPointer));
                    if (labels.containsKey(currPointer)) {
                        //System.out.println(labels.get(currPointer) + ":");
                        outputFile.writeBytes("\n" + labels.get(currPointer).first + ":\n");
                        labels.get(currPointer).second = true; // setting the label as having been printed
                    }

                    String textInst = decodeInstruction(inputFile, false, isJ);
                    outputFile.writeBytes(textInst);

                }

                // Code can exist AFTER the text, so gotta check if there are labels that weren't achieved
                boolean allDone = labels.isEmpty();
                while (!allDone) {
                    HashMap<Integer, Pair<String, Boolean>> auxMap = (HashMap<Integer, Pair<String, Boolean>>) labels.clone();
                    int labelsSize = labels.entrySet().size();
                    int secondNum = 0;
                    for (Map.Entry<Integer, Pair<String, Boolean>> entry: auxMap.entrySet()) {
                        int pointer = entry.getKey();

                        Pair<String, Boolean> pair = entry.getValue();
                        if (pair.second) {
                            //labels.remove(pointer);
                            secondNum++;
                            if (secondNum >= labelsSize) {
                                allDone = true;
                                break;
                            }
                            continue;
                        }
                        inputFile.seek(pointer);
                        isLastInstruction = false;



                        while(!isLastInstruction) {
                            int currPointer = (int) inputFile.getFilePointer();

                            // Add label before instruction
                            if (labels.containsKey(currPointer)) {
                                outputFile.writeBytes("\n" + labels.get(currPointer).first + ":\n");
                                labels.get(currPointer).second = true; // setting the label as having been printed
                                labelsSize++;
                            }

                            String textInst = decodeInstruction(inputFile, true, isJ);
                            outputFile.writeBytes(textInst);
                        }
                    }
                }


                outputFile.writeBytes("\n");
                textList.writeText(outputFile);

            } catch (Exception e) {
                //System.out.println("ERR opening file to write decoded to: " + e.getMessage());
                throw e;
            }

        } catch (Exception e) {
            //System.out.println("ERR opening file to decode: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Encodes an event file's flow script back to the original format
     * @param inputPath the path for the file to encode back
     * @param isJ {@code true} if the event file was extracted from the japanese version of the game
     * @throws IOException file IO exceptions
     * @throws OperationNotSupportedException thrown if some part of the file isn't formatted as expected
     */
    public static void encodeFlowScript(String inputPath, boolean isJ) throws IOException, OperationNotSupportedException {
        isLastInstruction = false;
        emptyLineHappened = false;
        textReferenceLocations.clear();
        labelReferenceLocations.clear();
        labelReferenceRealVals.clear();
        int textListSize;
        String outputPath = inputPath.substring(0, inputPath.length()-4) + Library.TEMP_STRING + EVENT_SCRIPT_EXTENSION_1;
        try (RandomAccessFile inputFile = new RandomAccessFile(inputPath, READ_MODE)) {
            //String outputPath = inputPath.substring(0, inputPath.length()-4) + EVENT_SCRIPT_EXTENSION_1;

            // checking if the file is empty
            String line = inputFile.readLine();
            line = removeCommentAndSpaces(line);

            if (line.compareTo(Library.EMPTY_FILE_STRING) == 0) {
                System.out.println(Library.EMPTY_FILE_STRING);
                return;
            }

            inputFile.seek(0);

            // delete file if it already exists
            File file = new File(outputPath);
            file.delete();

            try (RandomAccessFile outputFile = new RandomAccessFile(outputPath, WRITE_MODE)) {
                // first gonna get the data from the og file up until the event flow script
                fillFileBeginning(inputPath, inputFile, outputFile, isJ);


                // skip empty lines
                while ((line = inputFile.readLine()).compareTo("") == 0);
                line = removeCommentAndSpaces(line);


                // skip spaces and tabs after "section"
                String[] bgmSplit = line.split(Library.SPACE_TAB_REGEX);
                int i = skipSpacesNTabs(bgmSplit, 1);

                // BGM section check
                if (bgmSplit[0].compareTo(Library.SECTION_KEYWORD) != 0 || bgmSplit[i].compareTo(Library.BGM_SECTION_KEYWORD) != 0) {
                    throw new OperationNotSupportedException(Library.NOT_FORMATTED_ERR_TXT);
                }

                long pointerBK = outputFile.getFilePointer();
                outputFile.seek(Library.STARTING_SONG_ADDRESS);
                while ((line = inputFile.readLine()).compareTo("") != 0) {
                    String[] songEntrySplit = line.split(Library.SPACE_TAB_REGEX);
                    i = skipSpacesNTabs(songEntrySplit, 1);
                    FileReadWriteUtils.writeShort(outputFile, valOrder, extractShortFromString(songEntrySplit[i]));
                }
                outputFile.seek(pointerBK);

                // skip empty lines
                while ((line = inputFile.readLine()).compareTo("") == 0);
                line = removeCommentAndSpaces(line);

                // skip spaces and tabs after "section"
                String[] talkSplit = line.split(Library.SPACE_TAB_REGEX);
                i = skipSpacesNTabs(talkSplit, 1);

                // Primary talk section check
                if (talkSplit[0].compareTo(Library.SECTION_KEYWORD) != 0 || talkSplit[i].compareTo(Library.TALK_SECTION_KEYWORD) != 0) {
                    throw new OperationNotSupportedException(Library.NOT_FORMATTED_ERR_TXT);
                }
                // Primary talk section handling
                registerTalkAddresses(inputFile, true, isJ);

                // skip empty lines
                while ((line = inputFile.readLine()).compareTo("") == 0);
                line = removeCommentAndSpaces(line);

                // skip spaces and tabs after "section"
                String[] talk2Split = line.split(Library.SPACE_TAB_REGEX);
                i = skipSpacesNTabs(talk2Split, 1);

                // Secondary talk section check
                if (talk2Split[0].compareTo(Library.SECTION_KEYWORD) != 0 || talk2Split[i].compareTo(Library.TALK2_SECTION_KEYWORD) != 0) {
                    throw new OperationNotSupportedException(Library.NOT_FORMATTED_ERR_TXT);
                }
                // Secondary talk section handling
                registerTalkAddresses(inputFile, false, isJ);


                // skip empty lines
                while ((line = inputFile.readLine()).compareTo("") == 0);
                line = removeCommentAndSpaces(line);

                // skip spaces and tabs after "section"
                String[] positionsSplit = line.split(Library.SPACE_TAB_REGEX);
                i = skipSpacesNTabs(positionsSplit, 1);

                // positions section check
                if (positionsSplit[0].compareTo(Library.SECTION_KEYWORD) != 0 || positionsSplit[i].compareTo(Library.POS_SECTION_KEYWORD) != 0) {
                    throw new OperationNotSupportedException(Library.NOT_FORMATTED_ERR_TXT);
                }

                encodePositionOrInteractableSection(inputFile, outputFile, false, isJ);


                // skip empty lines
                while ((line = inputFile.readLine()).compareTo("") == 0);
                line = removeCommentAndSpaces(line);

                // skip spaces and tabs after "section"
                String[] interSplit = line.split(Library.SPACE_TAB_REGEX);
                i = skipSpacesNTabs(interSplit, 1);

                // interactables section check
                if (interSplit[0].compareTo(Library.SECTION_KEYWORD) != 0 || interSplit[i].compareTo(Library.INTER_SECTION_KEYWORD) != 0) {
                    throw new OperationNotSupportedException(Library.NOT_FORMATTED_ERR_TXT);
                }

                encodePositionOrInteractableSection(inputFile, outputFile, true, isJ);

                // skip empty lines
                while ((line = inputFile.readLine()).compareTo("") == 0);
                line = removeCommentAndSpaces(line);

                // skip spaces and tabs after "section"
                String[] codeSplit = line.split(Library.SPACE_TAB_REGEX);
                i = skipSpacesNTabs(codeSplit, 1);

                // Code section check
                if (codeSplit[0].compareTo(Library.SECTION_KEYWORD) != 0 || codeSplit[i].compareTo(Library.CODE_AREA_KEYWORD) != 0) {
                    throw new OperationNotSupportedException(Library.NOT_FORMATTED_ERR_TXT);
                }

                // Code section handling
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
                            //String labelNum = labelSplit[1];

                            // padding to make the label code multiple to 8
                            while(outputFile.getFilePointer() % 8 != 0) outputFile.writeByte(0);

                            fillInRef(outputFile, sectionSplit[0].substring(0, sectionSplit[0].length() - 1), (short)-1);
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
                    fillInRef(outputFile, "", textId);

                    textPointers[i] = (int) outputFile.getFilePointer();
                    //TextList.encodeText(outputFile, text.getBytes(Charset.forName("windows-1252")));
                    //TextList.encodeText(outputFile, new String(text.getBytes(Charset.forName("Cp1252")), Charset.forName("Cp1252")));
                    //TextList.encodeText(outputFile, text.getBytes(StandardCharsets.UTF_8));
                    TextList.encodeText(outputFile, new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8), inputFile, textInputPointer);

                    textInputPointer = (int) inputFile.getFilePointer();
                    line = inputFile.readLine();
                }

                // gotta go update the reference to the text table
                if (!isJ) { // in the japanese version, there is no text table
                    int textTablePointer = (int) outputFile.getFilePointer();
                    outputFile.seek(Library.ADDRESS_WITH_TEXT_TABLE_POINTER);
                    FileReadWriteUtils.writeInt(outputFile, valOrder, textTablePointer);
                    outputFile.seek(textTablePointer);

                    // writing the text table at the end
                    FileReadWriteUtils.writeInt(outputFile, valOrder, textListSize);

                    for (i = 0; i < textListSize; i++) {
                        FileReadWriteUtils.writeInt(outputFile, valOrder, textPointers[i]);
                    }
                }

                // final padding
                while(outputFile.getFilePointer() % 0x800 != 0) {
                    outputFile.writeByte(0);
                }


            } catch (Exception e) {
                //System.out.println(e.getMessage());
                throw e;
            }
        } catch (Exception e) {
            //System.out.println(e.getMessage());
            throw e;
        }

        String ogPath = inputPath.substring(0, inputPath.length()-4) + EVENT_SCRIPT_EXTENSION_1;
        File ogFile = new File(ogPath);
        File tempFile = new File(outputPath);

        if (ogFile.exists()) {
            if (ogFile.delete()) {
                if (tempFile.renameTo(ogFile)) {
                    System.out.print("DONE\r");
                }
            }
        }
    }

    /**
     * Fills start of output file based on original file
     * @param path input file's path
     * @param inputFile object used to read from input file
     * @param outputFile object used to write to output file
     * @param isJ {@code true} if the event file was extracted from the japanese version of the game
     */
    private static void fillFileBeginning(String path, RandomAccessFile inputFile, RandomAccessFile outputFile, boolean isJ) throws IOException, OperationNotSupportedException {
        String ogPath = path.substring(0, path.length()-4) + EVENT_SCRIPT_EXTENSION_1;
        try (RandomAccessFile ogFile = new RandomAccessFile(ogPath, READ_MODE)) {
            ogFile.seek(Library.ADDRESS_WITH_FLOW_SCRIPT_POINTER - (isJ ? 4 : 0));
            int startAddr = FileReadWriteUtils.readInt(ogFile, valOrder);

            String addrLine = inputFile.readLine();
            // if the og file doesn't have a valid address, gotta get one from the input file's .addr zone
            if (startAddr == -1) {
                addrLine = removeCommentAndSpaces(addrLine);
                String[] addrSplit = addrLine.split(Library.SPACE_TAB_REGEX);
                if (addrSplit[0].compareTo(Library.ADDR_KEYWORD) != 0) {
                    throw new OperationNotSupportedException("addr not there?");
                }
                int i = skipSpacesNTabs(addrSplit, 1);
                startAddr = extractIntFromString(addrSplit[i]);
            }

            // back to the beginning of the file
            ogFile.seek(0);

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


    private static String decodeInstruction(RandomAccessFile inputFile, boolean isPastText, boolean isJ) throws IOException {
        int currPointer;
        byte instr = inputFile.readByte(); // the start of instructions is always FF

        // check if it is the last pointer
        currPointer = (int) inputFile.getFilePointer();

        if (!isJ && !isPastText && currPointer >= textList.getFirstAddr()) {
            isLastInstruction = true;
            return "";
        }

        if (instr != Library.CMD_START) {
            // advance through padding
            long t = 0;
            long tries = inputFile.length() - inputFile.getFilePointer();
            while ((instr = inputFile.readByte()) == (byte)0) {
                t++;
                if (t == tries-1) {
                    break;
                }
            }

            // if, after the padding, the next byte still isn't an instruction, it must be text
            if (instr != Library.CMD_START) {
                isLastInstruction = true;
            }

            inputFile.seek(inputFile.getFilePointer()-1);
            return "";
        }

        instr = inputFile.readByte();

        // if the next instruction was actually a text instruction (text normally starts with FF1B)
        if (instr == Library.CHARACTER_NAME_BYTE) {
            isLastInstruction = true;
            return "";
        }

        String name, label, param, param2, addressStr;
        short check;
        int address, fee;
        byte smolParam;
        // UNCOMMENT FOR DEBUG HERE
        //System.out.printf("yep: 0x%02x\n", instr);
        Library.FlowInstruction flowInstr = Library.getInstance().FLOW_INSTRUCTIONS.get(instr);
        name = flowInstr.name();
        switch(flowInstr) {
            case ret:
                // advancing the 00s
                getShortString(inputFile);
                if (isPastText) isLastInstruction = true; // this is used for situations where the labels have to be used to know about more code
                return "\t" + name + "\n";
            case jump:
                check = FileReadWriteUtils.readShort(inputFile, instructionOrder);
                name = simpleInstructionCheck(check, name, instr);

                // get followup int, which corresponds to the address to jump to
                address = FileReadWriteUtils.readInt(inputFile, valOrder);

                label = getLabel(address);

                return "\t" + name + "\t" + label + "\n";
            case jump_if:
                String condition = getShortString(inputFile);
                address = FileReadWriteUtils.readInt(inputFile, valOrder);
                label = getLabel(address);

                return "\t" + name + "\t" + condition + "," + label + "\t" + Library.COMMENT_SYMBOL + " the parameter's exact meaning is unknown, but it might be related to game flags\n";
            //case UNKNOWN_COMMAND_27:
            //    name = flowInstr.name(); // TEST
            //    param = getShortString(inputFile);
            //    return "\t" + name + "\t" + param + "\n";
            case battle:
                short p = getShortVal(inputFile);
                param = String.format(Library.HEX_PREFIX + "%04x", p);

                String battleName;
                if (p > Library.BATTLES.length-1) {
                    battleName = "unknown";
                } else {
                    battleName = Library.BATTLES[p];
                }

                return "\t" + name + "\t" + param + "\t"+ Library.COMMENT_SYMBOL + " " + battleName + "\n";
            case ld_world_map:
                param = getShortString(inputFile);
                addressStr = getIntString(inputFile);
                return "\t" + name + "\t" + param + "," + addressStr + "\t"+ Library.COMMENT_SYMBOL + " loads a world map\n";
            case open_shop_menu:
                check = getShortVal(inputFile);
                return "\t" + name + "\t" + getShortString(check) + "\t" + Library.COMMENT_SYMBOL + " opens shop menu: " + Library.getShopDescription(check) +"\n";

            case ld_file:
                param = getShortString(inputFile);
                addressStr = getIntString(inputFile);
                return "\t" + name + "\t" + param + "," + addressStr + "\t"+ Library.COMMENT_SYMBOL + " loads another event file\n";
            case ld_3d_map:
                String mapID = getByteString(inputFile);
                String unknown = getByteString(inputFile);
                String x = getByteString(inputFile);
                String y = getByteString(inputFile);
                String direction = getByteString(inputFile);
                String fourthParam = getByteString(inputFile);

                return "\t" + name + "\t" + mapID + "," + unknown + "," + x + "," + y + "," + direction + "," + fourthParam +
                        "\t"+ Library.COMMENT_SYMBOL + " ld_3d_map <map ID>,<unknown>,<X>,<Y>,<direction (0|1|2|3 -> E|W|S|N)>, <unknown>\n";
            case give_item:
                param = getShortString(inputFile);
                int quantity = getInt(inputFile);

                return "\t" + name + "\t" + param + "," + quantity + "\t" + Library.COMMENT_SYMBOL + " give_item <item_id>,<quantity>\n";
            case play_MV:
                param = String.format("MV%02x.pmf", inputFile.readByte());
                param2 = getByteString(inputFile);
                return "\t" + name + "\t" + param + "," + param2 + "\t"+ Library.COMMENT_SYMBOL + " second parameter is some kind of flag?\n";
            case money_check:
                check = FileReadWriteUtils.readShort(inputFile, instructionOrder);
                name = simpleInstructionCheck(check, name, instr);
                fee = getInt(inputFile);
                address = getInt(inputFile);
                label = getLabel(address);

                return "\t" + name + "\t" + fee + "," + label + "\t" + Library.COMMENT_SYMBOL + " money_check <fee, label when not enough money>\n";
            case money_transfer:
                check = getShortVal(inputFile); // actually the direction of the transaction
                fee = getInt(inputFile);

                return "\t" + name + "\t" + Library.MONEY_DIRECTION.values()[check] + "," + fee + "\t" + Library.COMMENT_SYMBOL + " money_transfer <ADD or REMOVE>,<quantity>\n";
            case open_save_menu:
                return "\t" + name + "\n";
            case wait:
                String ticks = getShortString(inputFile);
                return "\t" + name + "\t" + ticks + "\t"+ Library.COMMENT_SYMBOL + " value in ticks \n";
            case player_option:
                param = getShortString(inputFile);
                address = getInt(inputFile);
                label = getLabel(address);

                return "\t" + name + "\t" + param + "," + label + "\t"+ Library.COMMENT_SYMBOL + " " + name + " <option num?>,<label>\n";
            case ld_text:
                check = FileReadWriteUtils.readShort(inputFile, instructionOrder);
                name = simpleInstructionCheck(check, name, instr);
                address = getInt(inputFile);
                int textIdx = textList.indexOfText(address);

                // sometimes, text that ISN'T in the text table is used by the game (thanks developers)
                // in these cases, the string has to be obtained based on the address, and then added to the text list
                // this will always be used by the japanese version, since the text list doesn't exist
                if (textIdx == -1) {
                    textList.addText(inputFile, address, true);
                    textIdx = textList.indexOfText(address);
                }
                return "\t" + name + "\t" + textIdx + "\t"+ Library.COMMENT_SYMBOL + " idx of text in .text section\n";

                // the three below are related to healing the party?
            case unk_cmd_2F:
            case unk_cmd_30:
            case unk_cmd_3A:
            case unk_cmd_3B:
            case unk_cmd_44:
            case unk_cmd_45:
            case unk_cmd_47:
            case unk_cmd_58:
            case unk_cmd_59:
            case unk_cmd_5A:
            case unk_cmd_87:
                check = FileReadWriteUtils.readShort(inputFile, valOrder);
                address = getInt(inputFile);
                label = getLabel(address);
                return "\t" + name + "\t" + getShortString(check) + "," +  label + "\t" + Library.COMMENT_SYMBOL + " unknown, but uses a label\n";
            case open_dialog:
                check = FileReadWriteUtils.readShort(inputFile, instructionOrder);
                name = simpleInstructionCheck(check, name, instr);
                return "\t" + name + "\t"+ Library.COMMENT_SYMBOL + " opens dialog box graphic\n";
            case close_dialog:
                check = FileReadWriteUtils.readShort(inputFile, instructionOrder);
                name = simpleInstructionCheck(check, name, instr);
                return "\t" + name + "\t"+ Library.COMMENT_SYMBOL + " closes dialog box graphic\n";
            case pose:
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
                return "\t" + name + "\t" + getByteString(inputFile) + "," + getByteString(inputFile) + ","
                        + getIntString(inputFile) + "," + getIntString(inputFile) + "\t"+ Library.COMMENT_SYMBOL + " makes effect happen, like lightning. No idea of the specifics\n";
            case clr_char:
                param = getShortString(inputFile);
                return "\t" + name + "\t" + param + "\t"+ Library.COMMENT_SYMBOL + " this clears the character numbered in the parameter\n";
            case ld_portrait:
                smolParam = inputFile.readByte(); // character ID
                return "\t" + name + "\t" + Library.PORTRAIT_CHARS.values()[smolParam] + "," + Library.PORTRAIT_ORIENTATION.values()[inputFile.readByte()] +
                        "\n";
            case close_portrait:
                check = FileReadWriteUtils.readShort(inputFile, instructionOrder);
                name = simpleInstructionCheck(check, name, instr);
                return "\t" + name + "\t"+ Library.COMMENT_SYMBOL + " closes portrait graphic\n";
            case emote:
                smolParam = inputFile.readByte(); // character ID
                return "\t" + name + "\t" + smolParam + "," + Library.EMOTES.values()[inputFile.readByte()] + "\t"+ Library.COMMENT_SYMBOL + " first parameter = character ID (dependent on scene)\n";
            case screen_fx:
                check = getShortVal(inputFile);
                return "\t" + name + "\t" + getShortString(check) + "\t"+ Library.COMMENT_SYMBOL + " does an effect that fills the full screen. In this case, " + Library.SCREEN_EFFECTS[check] + "\n";
            case fade_char:
                return "\t" + name + "\t" + inputFile.readByte() + "," + inputFile.readByte() + "\t"+ Library.COMMENT_SYMBOL + " fades character with ID in first param with speed in second param\n";
            case plan_char_mov:
                return "\t" + name + "\t"
                        + getByteString(inputFile) + "," + getByteString(inputFile) + "," + getByteString(inputFile) + "," +
                        getByteString(inputFile) + "," + getByteString(inputFile) + "," + getByteString(inputFile)
                        + "\t"+ Library.COMMENT_SYMBOL + " " + name + "\t<character ID>,<trajectory idx>,<speed>,<direction_at_destination>,...\n";

            case follow_char:
                check = getShortVal(inputFile);
                return "\t" + name + "\t" + check + "\t"+ Library.COMMENT_SYMBOL + " sets camera to follow character. parameter = character ID (dependent on scene)\n";
            case clr_emote:
                check = getShortVal(inputFile);
                return "\t" + name + "\t" + check + "\t"+ Library.COMMENT_SYMBOL + " clears the emote of the character in the parameter\n";
            case do_planned_moves:
                check = FileReadWriteUtils.readShort(inputFile, instructionOrder);
                name = simpleInstructionCheck(check, name, instr); // HERE
                return "\t" + name + "\t"+ Library.COMMENT_SYMBOL + " executes the previously planned character movements\n";

            case tp_char:
                param = getShortString(inputFile);
                return "\t" + name + "\t" + param + "," + getIntString(inputFile) + "\t"+ Library.COMMENT_SYMBOL + " sets position/direction of a character, specifics of parameters are unknown\n";
            case play_song:
                param = getShortString(inputFile);
                return "\t" + name + "\t" + param + "\t"+ Library.COMMENT_SYMBOL + " plays the song whose ID is in the parameter\n";
            case play_sfx:
                check = getShortVal(inputFile);
                return "\t" + name + "\t" + getShortString(check) + "\t"+ Library.COMMENT_SYMBOL + " plays sfx: " + Library.getSFXDescription(check) + "\n";
            default:
                StringBuilder toRet = new StringBuilder();
                toRet.append("\t" + Library.UNKNOWN_INSTR_TEXT + "|FF").append(String.format("%02x", instr))
                        .append(String.format("%02x", inputFile.readByte())).append(String.format("%02x", inputFile.readByte()));
                int params = Library.getInstance().PARAM_NUM.get(flowInstr);
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

                // write first 4 bytes
                writeIntInstruction(outputFile, instr, (short)0); // 0 for padding

                // register a required address
                int currAddr = (int) outputFile.getFilePointer();
                addLabelRef(outputFile, label, currAddr);

                // jump if instruction
            } else if (instr.compareTo(Library.FlowInstruction.jump_if.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.unk_cmd_2F.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.unk_cmd_30.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.unk_cmd_3A.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.unk_cmd_3B.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.unk_cmd_44.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.unk_cmd_45.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.unk_cmd_47.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.unk_cmd_58.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.unk_cmd_59.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.unk_cmd_5A.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.unk_cmd_87.name()) == 0) {
                String param = paramSplit[0];
                String label = paramSplit[1];

                // write first 4 bytes
                writeIntInstruction(outputFile, instr, extractShortFromString(param));

                // register a required address
                int currAddr = (int) outputFile.getFilePointer();
                addLabelRef(outputFile, label, currAddr);

                // simple 1 short parameter instructions
            } else if (instr.compareTo(Library.FlowInstruction.UNKNOWN_COMMAND_27.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.battle.name()) == 0 ||
                    instr.compareTo(Library.FlowInstruction.open_shop_menu.name()) == 0 ||
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

                // write int in hexadecimal
                FileReadWriteUtils.writeInt(outputFile, valOrder, extractIntFromString(intParam));

            } else if (instr.compareTo(Library.FlowInstruction.ld_3d_map.name()) == 0) {
                String mapID = paramSplit[0];
                String unknown = paramSplit[1];
                String x = paramSplit[2];
                String y = paramSplit[3];
                String dir = paramSplit[4];
                String param5 = paramSplit[5];

                // write first 4 bytes
                outputFile.writeByte(Library.CMD_START);
                outputFile.writeByte(Library.getInstance().FLOW_INSTRUCTIONS_REVERSE.get(instr));
                outputFile.writeByte(extractByteFromString(mapID));
                outputFile.writeByte(extractByteFromString(unknown));

                // write next parameters
                outputFile.writeByte(extractByteFromString(x));
                outputFile.writeByte(extractByteFromString(y));
                outputFile.writeByte(extractByteFromString(dir));
                outputFile.writeByte(extractByteFromString(param5));
            } else if (instr.compareTo(Library.FlowInstruction.give_item.name()) == 0) {
                String itemId = paramSplit[0];
                String quantity = paramSplit[1];

                // write first 4 bytes
                writeIntInstruction(outputFile, instr, extractShortFromString(itemId));

                // write quantity
                FileReadWriteUtils.writeInt(outputFile, valOrder, Integer.parseInt(quantity));
            } else if (instr.compareTo(Library.FlowInstruction.play_MV.name()) == 0) {
                String movieFileName = paramSplit[0]; // format: MVXX.pmf, where XX is the number
                String param1 = movieFileName.substring(2, 4); // byte (hex without 0x)
                String param2 = paramSplit[1]; // byte (hex)

                outputFile.writeByte(Library.CMD_START);
                outputFile.writeByte(Library.getInstance().FLOW_INSTRUCTIONS_REVERSE.get(instr));
                outputFile.writeByte((byte) Short.parseShort(param1, 16));
                outputFile.writeByte((byte) Short.parseShort(param2.substring(2), 16));
            } else if (instr.compareTo(Library.FlowInstruction.money_check.name()) == 0) {
                String fee = paramSplit[0];
                String label = paramSplit[1];

                //write first 4 bytes
                writeIntInstruction(outputFile, instr, (short) 0);

                // write the fee
                FileReadWriteUtils.writeInt(outputFile, valOrder, Integer.parseInt(fee));

                // register a required address
                int currAddr = (int) outputFile.getFilePointer();
                addLabelRef(outputFile, label, currAddr);
            } else if (instr.compareTo(Library.FlowInstruction.money_transfer.name()) == 0) {
                String direction = paramSplit[0];
                String quantity = paramSplit[1];

                //write first 4 bytes
                writeIntInstruction(outputFile, instr, (short) Library.MONEY_DIRECTION.valueOf(direction).ordinal());

                // write the quantity
                FileReadWriteUtils.writeInt(outputFile, valOrder, Integer.parseInt(quantity));
            } else if (instr.compareTo(Library.FlowInstruction.player_option.name()) == 0) {
                String param = paramSplit[0];
                String label = paramSplit[1];

                // write first 4 bytes
                writeIntInstruction(outputFile, instr, extractShortFromString(param));

                // register a required address
                int currAddr = (int) outputFile.getFilePointer();
                addLabelRef(outputFile, label, currAddr);
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
                outputFile.writeByte(Library.getInstance().FLOW_INSTRUCTIONS_REVERSE.get(instr));
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
                outputFile.writeByte(Library.getInstance().FLOW_INSTRUCTIONS_REVERSE.get(instr));
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
                outputFile.writeByte(Library.getInstance().FLOW_INSTRUCTIONS_REVERSE.get(instr));
                outputFile.writeByte(Library.PORTRAIT_CHARS.valueOf(param1).ordinal());
                outputFile.writeByte(Library.PORTRAIT_ORIENTATION.valueOf(param2).ordinal());

            } else if (instr.compareTo(Library.FlowInstruction.emote.name()) == 0) {
                String param1 = paramSplit[0]; // byte
                String param2 = paramSplit[1]; // text of the emote

                // write first 4 bytes
                outputFile.writeByte(Library.CMD_START);
                outputFile.writeByte(Library.getInstance().FLOW_INSTRUCTIONS_REVERSE.get(instr));
                outputFile.writeByte((byte)Short.parseShort(param1));
                outputFile.writeByte(Library.EMOTES.valueOf(param2).ordinal());
            } else if (instr.compareTo(Library.FlowInstruction.fade_char.name()) == 0) {
                String param1 = paramSplit[0]; // byte
                String param2 = paramSplit[1]; // byte

                // write first 4 bytes
                outputFile.writeByte(Library.CMD_START);
                outputFile.writeByte(Library.getInstance().FLOW_INSTRUCTIONS_REVERSE.get(instr));
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
                outputFile.writeByte(Library.getInstance().FLOW_INSTRUCTIONS_REVERSE.get(instr));
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
     * @param isJ {@code true} if the event file was extracted from the japanese version of the game
     * @throws IOException file stuff
     */
    private static void decodeTalkAddresses(RandomAccessFile inputFile, StringBuilder outputFile, boolean isPrimaryTalk, boolean isJ) throws IOException {
        long pointerBk = inputFile.getFilePointer();

        int startAddress = isPrimaryTalk ? Library.ADDRESS_OF_CHARACTER_DATA : Library.ADDRESS_OF_SECONDARY_CHARACTER_DATA;
        startAddress -= isJ ? 4 : 0; // adjust for japanese version
        int numOfStructs = isPrimaryTalk ? Library.CHARACTER_DATA_NUM : Library.SECONDARY_CHARACTER_DATA_NUM;
        int dataSize = isPrimaryTalk ? Library.CHARACTER_DATA_SIZE : Library.SECONDARY_CHARACTER_DATA_SIZE;
        int secondAddrOffset = isPrimaryTalk ? Library.CHARACTER_DATA_EVENT_ADDRESS_2_OFFSET : Library.SECONDARY_CHARACTER_DATA_EVENT_ADDRESS_2_OFFSET;

        for (int i = 0; i < numOfStructs; i++) {
            inputFile.seek(startAddress + (i * dataSize));

            //if (isNextCharacterDataEmpty(inputFile)) {
            //    continue;
            //}
            inputFile.seek(inputFile.getFilePointer() + Library.CHARACTER_DATA_EVENT_ADDRESS_1_OFFSET);
            int address1 = FileReadWriteUtils.readInt(inputFile, valOrder);

            inputFile.seek(inputFile.getFilePointer() + secondAddrOffset);
            int address2 = FileReadWriteUtils.readInt(inputFile, valOrder);

            // If both addresses are -1, then there is nothing to write
            if ((address1 == Library.MINUS_1_INT || address1 == 0) && (address2 == Library.MINUS_1_INT || address2 == 0)) {
                continue;
            }
            outputFile.append(String.format("\t%02d\t\t", i));

            // checking first address position
            if (address1 != Library.MINUS_1_INT && address1 != 0) {
                String label = getLabel(address1);
                //outputFile.writeBytes(String.format("%02d\t\t%s\t\t%s <Character in scene>:  <Label to code that executes when spoken to>\n", i, label, Library.COMMENT_SYMBOL));
                outputFile.append(String.format("%s", label));
            } else {
                outputFile.append(Library.LABEL_SEPARATOR);
            }

            outputFile.append(",");

            // checking second address position
            if (address2 != Library.MINUS_1_INT  && address2 != 0) {
                String label = getLabel(address2);

                outputFile.append(String.format("%s", label));
            } else {
                outputFile.append(Library.LABEL_SEPARATOR);
            }

            outputFile.append("\n");
        }

        outputFile.append("\n");
        inputFile.seek(pointerBk);
    }

    /**
     * Saves addresses where pointers are required, used for when the event script is being encoded
     * @param inputFile the object used to read the decrypted event file
     * @param isPrimaryTalk {@code true} if this method is saving the addresses of primary character interactions
     * @param isJ {@code true} if the event file was extracted from the japanese version of the game
     * @throws IOException file related IO exceptions
     */
    private static void registerTalkAddresses(RandomAccessFile inputFile, boolean isPrimaryTalk, boolean isJ) throws IOException {
        // TODO: write over all the references in these data structures, in case user wants to change what character has a reference
        // Right now, the program just writes the ones in the decoded file, it doesn't overwrite non-existing ones with FFFFFFFF
        // this would have to include moving all the data in the talk structures for things to work well

        int startAddress = isPrimaryTalk ? Library.ADDRESS_OF_CHARACTER_DATA : Library.ADDRESS_OF_SECONDARY_CHARACTER_DATA;
        startAddress -= isJ ? 4 : 0;
        int dataSize = isPrimaryTalk ? Library.CHARACTER_DATA_SIZE : Library.SECONDARY_CHARACTER_DATA_SIZE;
        int secondAddrOffset = isPrimaryTalk ? Library.CHARACTER_DATA_EVENT_ADDRESS_2_ABSOLUTE_OFFSET : Library.SECONDARY_CHARACTER_DATA_EVENT_ADDRESS_2_ABSOLUTE_OFFSET;

        String line;
        int i;
        while ((line = inputFile.readLine()).compareTo("") != 0) {
            line = removeCommentAndSpaces(line);
            String[] talkLineSplit = line.split(Library.SPACE_TAB_REGEX);

            i = skipSpacesNTabs(talkLineSplit, 0);
            int characterId = Integer.parseInt(talkLineSplit[i]);
            i = skipSpacesNTabs(talkLineSplit, i + 1);

            String labels = talkLineSplit[i];
            String[] labelSplit = labels.split(",");

            // If the first LABEL space isn't empty
            if (labelSplit[0].compareTo(Library.LABEL_SEPARATOR) != 0) {
                int address = startAddress + (characterId * dataSize) + Library.CHARACTER_DATA_EVENT_ADDRESS_1_OFFSET;
                addLabelRef(null , labelSplit[0], address);
            }

            // If the second LABEL space isn't empty
            if (labelSplit[1].compareTo(Library.LABEL_SEPARATOR) != 0) {
                int address = startAddress + (characterId * dataSize) + secondAddrOffset;
                addLabelRef(null , labelSplit[1], address);
            }
        }
    }

    /**
     * Decodes position data or interactable data, based on {@code isInteractable} parameter
     * @param inputFile the object used to read from the .EVS file
     * @param outputFile the object used to write to the .DEC file
     * @param isInteractable {@code true} to read interactable data, {@code false} to read position data
     * @param isJ {@code true} if the file was extracted from a japanese version of the game
     * @throws IOException file related exception
     */
    private static void decodePositionsOrInteractables(RandomAccessFile inputFile, StringBuilder outputFile, boolean isInteractable, boolean isJ) throws IOException {
        long pointerBk = inputFile.getFilePointer();

        long effectivePointer = isInteractable ? Library.ADDRESS_WITH_INTERACTABLE_DATA_SIZE_POINTER : Library.ADDRESS_WITH_POSITION_DATA_SIZE_POINTER;
        effectivePointer -= isJ ? 4 : 0; // adjust the pointer by 4 bytes, because JPN version has no text list

        // fetch the pointer to the list's size
        inputFile.seek(effectivePointer);
        int sizePointer = FileReadWriteUtils.readInt(inputFile, valOrder);

        // go to where the size value is
        inputFile.seek(sizePointer);
        int size = FileReadWriteUtils.readInt(inputFile, valOrder);

        // if the size is 0, there are no positions
        if (size == 0) {
            outputFile.append("\n");
            inputFile.seek(pointerBk);
            return;
        }

        // go to the beginning of the list of positions
        inputFile.seek(effectivePointer + 4);
        int positionsPointer = FileReadWriteUtils.readInt(inputFile, valOrder);
        inputFile.seek(positionsPointer);

        for (int i = 0; i < size; i ++) {
            byte x = inputFile.readByte();
            byte y = inputFile.readByte();
            short unknown = FileReadWriteUtils.readShort(inputFile, valOrder);
            int address = FileReadWriteUtils.readInt(inputFile, valOrder);

            String label = getLabel(address);

            outputFile.append(String.format("\t%03d\t%03d\t%s\n", x, y, label));
        }

        outputFile.append("\n");
        inputFile.seek(pointerBk);
    }

    /**
     * Updates the file lists of the EX.BIN file that is being archived
     * @param ebootIn the original EBOOT.BIN (or the one located in the output folder)
     * @param ebootOut the edited EBOOT.BIN (the same file if an EBOOT.BIN exists in the output folder)
     * @param eventFileNum the number of the EX.BIN file (the X, if you will)
     * @param fileList the file list to write to the EBOOT.BIN
     * @param isJ {@code true} if the event file was extracted from the japanese version of the game
     * @throws IOException I/O file stuff
     */
    private static void updateEbootFileList(File ebootIn, File ebootOut, int eventFileNum, InnerFileAddressList fileList, boolean isJ) throws IOException {
        Path outputFilePath = ebootOut.toPath();

        // if the EBOOT.BIN doesn't exist in the output file, create a copy
        if (ebootIn.compareTo(ebootOut) != 0) {
            Files.copy(ebootIn.toPath(), outputFilePath, StandardCopyOption.REPLACE_EXISTING);
        }

        try (RandomAccessFile file = new RandomAccessFile(outputFilePath.toString(), WRITE_MODE)) {
            // calculate the start address of the edit
            int address = isJ ? Library.JP_EBOOT_E0_FILELIST_ADDR : Library.US_EBOOT_E0_FILELIST_ADDR;
            address -= Library.EBOOT_FILELIST_OFFSET*eventFileNum;

            file.seek(address);
            fileList.writeFileAddresses(file, valOrder);

        } catch (Exception e) {
            //System.out.println("ERROR reading EBOOT file to write to (in output folder)");
            throw e;
        }
    }

    /**
     * Encodes the position trigger entries into the output file and saves addresses to pointers that must be filled in later
     * @param inputFile the object used to read from the .DEC file
     * @param outputFile the object used to write to the .EVS file
     * @param isInteractable if true, treats the data like it is an interactable, otherwise treats it like positions
     * @param isJ {@code true} if the event file was extracted from the japanese version of the game
     * @throws IOException I/O file stuff
     */
    private static void encodePositionOrInteractableSection(RandomAccessFile inputFile, RandomAccessFile outputFile, boolean isInteractable, boolean isJ) throws IOException {
        // backing up the pointer to restore it after the positions
        long pointerBK = outputFile.getFilePointer();

        long effectivePointer = isInteractable ? Library.ADDRESS_WITH_INTERACTABLE_DATA_SIZE_POINTER : Library.ADDRESS_WITH_POSITION_DATA_SIZE_POINTER;
        effectivePointer -= isJ ? 4 : 0;

        // getting the size of the positions list
        outputFile.seek(effectivePointer);
        outputFile.seek(FileReadWriteUtils.readInt(outputFile, valOrder));
        int positionsSize = FileReadWriteUtils.readInt(outputFile, valOrder);

        // getting pointer to the first position data
        outputFile.seek(effectivePointer + 4);
        outputFile.seek(FileReadWriteUtils.readInt(outputFile, valOrder));

        // interpreting the positions section
        for (int positionEntryIdx = 0; positionEntryIdx < positionsSize; positionEntryIdx++) {
            String line = inputFile.readLine();
            line = removeCommentAndSpaces(line);
            String[] positionData = line.split(Library.SPACE_TAB_REGEX);

            int i = skipSpacesNTabs(positionData, 0);
            byte x = (byte) Short.parseShort(positionData[i]);
            i = skipSpacesNTabs(positionData, i + 1);
            byte y = (byte) Short.parseShort(positionData[i]);
            i = skipSpacesNTabs(positionData, i + 1);

            String label = positionData[i];

            // writing the coordinates
            outputFile.writeByte(x);
            outputFile.writeByte(y);

            // skipping the unknown short to get to where the address is
            int address = (int) outputFile.getFilePointer() + 2;

            // add the address to the addresses that need to be filled
            addLabelRef(null, label, address);

            // skip the short and the address to reach the next entry
            outputFile.seek(outputFile.getFilePointer() + 2 + 4);
        }

        outputFile.seek(pointerBK);
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

    /**
     * Adds a label to the labels map. Used for decoding file
     * @param address the address the label points to
     * @return the label's name
     */
    private static String getLabel(int address) {
        String label;
        if (!labels.containsKey(address)) {
            label = Library.LABEL_TXT + Library.LABEL_SEPARATOR + labelNum++;
            Pair<String, Boolean> pair = new Pair<>(label, false);
            labels.put(address, pair);
        } else {
            label = labels.get(address).first;
        }
        return label;
    }

    private static void addLabelRef(RandomAccessFile outputFile, String labelName, int currAddr) throws IOException {
        if (labelReferenceLocations.containsKey(labelName)) {
            labelReferenceLocations.get(labelName).add(currAddr);
            if (outputFile != null) FileReadWriteUtils.writeInt(outputFile, valOrder, 0); // padding while no address
        } else if (labelReferenceRealVals.containsKey(labelName)) {
            FileReadWriteUtils.writeInt(outputFile, valOrder, labelReferenceRealVals.get(labelName));
        } else {
            LinkedList<Integer> toAdd = new LinkedList<>();
            toAdd.add(currAddr);
            labelReferenceLocations.put(labelName, toAdd);
            if (outputFile != null) FileReadWriteUtils.writeInt(outputFile, valOrder, 0); // padding while no address
        }
    }

    private static void addTextRef(short textID, int currAddr) {
        if (textReferenceLocations.containsKey(textID)) {
            textReferenceLocations.get(textID).add(currAddr);
        } else {
            LinkedList<Integer> toAdd = new LinkedList<>();
            toAdd.add(currAddr);
            textReferenceLocations.put(textID, toAdd);
        }
    }

    private static void writeIntInstruction(RandomAccessFile outputFile, String instr, short val) throws IOException {
        outputFile.writeByte(Library.CMD_START);
        outputFile.writeByte(Library.getInstance().FLOW_INSTRUCTIONS_REVERSE.get(instr));
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
     * @param labelName the name of the label. Enter a random string if not in use
     * @param textId the id of the text string. Enter a -1 if not in use
     * @throws IOException file stuff
     */
    private static void fillInRef(RandomAccessFile outFile, String labelName, short textId) throws IOException {
        int currAddr = (int) outFile.getFilePointer();
        LinkedList<Integer> addrs;
        if (textId != -1) {
            addrs = textReferenceLocations.get(textId);
        } else {
            addrs = labelReferenceLocations.get(labelName);
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
        addrs.clear();

        if (textId == -1) {
            labelReferenceLocations.remove(labelName);
            labelReferenceRealVals.put(labelName, currAddr);
        }

        outFile.seek(currAddr);
    }

    public static String removeCommentAndSpaces(String line) {
        if (line.isEmpty()) return line;

        if (line.length() < Library.COMMENT_SYMBOL.length())
            return Library.COMMENT_INDICATOR;

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
                index += Library.COMMENT_SYMBOL.length();
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
        try {
            if (value.substring(0, 2).compareTo(Library.HEX_PREFIX) != 0) {
                throw new OperationNotSupportedException("byte value is not formatted correctly");
            }
            String prefixRemoved = value.substring(2);
            return (byte) Short.parseShort(prefixRemoved, 16);
        } catch (StringIndexOutOfBoundsException e) {
            throw new OperationNotSupportedException("byte value is not formatted correctly");
        }
    }

    private static short extractShortFromString(String value) throws OperationNotSupportedException {
        try {
            if (value.substring(0, 2).compareTo(Library.HEX_PREFIX) != 0) {
                throw new OperationNotSupportedException("short value is not formatted correctly");
            }
            String prefixRemoved = value.substring(2);
            return (short) Integer.parseInt(prefixRemoved, 16);
        } catch (StringIndexOutOfBoundsException e) {
            throw new OperationNotSupportedException("short value is not formatted correctly");
        }
    }

    private static int extractIntFromString(String value) throws OperationNotSupportedException {
        try {
            if (value.substring(0, 2).compareTo(Library.HEX_PREFIX) != 0) {
                throw new OperationNotSupportedException("int value is not formatted correctly");
            }
            String prefixRemoved = value.substring(2);
            return (int) Long.parseLong(prefixRemoved, 16);
        } catch (StringIndexOutOfBoundsException e) {
            throw new OperationNotSupportedException("int value is not formatted correctly");
        }
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

    public static void testText(RandomAccessFile file, boolean isJ) throws IOException {
        if (isJ) {
            System.out.println("can't do this in japanese version");
            return;
        }
        file.seek(Library.ADDRESS_WITH_TEXT_TABLE_POINTER);
        int address = FileReadWriteUtils.readInt(file, valOrder);
        TextList textList = TextList.readEncodedTextList(file, address, false);// TO CHANGE TO FUNCTIONAL, CHANGE THIS TO TRUE
        System.out.println(textList);
        //System.out.println(textList.writeText());
    }
}
