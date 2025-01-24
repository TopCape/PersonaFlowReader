import dataAccess.*
import dataAccess.dataTypes.*
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import javax.naming.OperationNotSupportedException
import kotlin.math.absoluteValue

private const val READ_MODE = "r"
private const val WRITE_MODE = "rw"
const val EXTENSION_1 = ".BIN"
const val EXTENSION_2 = ".bin"
const val EVENT_SCRIPT_EXTENSION_1 = ".EVS"
const val EVENT_SCRIPT_EXTENSION_2 = ".evs"
const val DEC_SCRIPT_EXTENSION_1 = ".DEC"
const val DEC_SCRIPT_EXTENSION_2 = ".dec"
const val TXT_EXTENSION_1 = ".TXT"
const val TXT_EXTENSION_2 = ".txt"
const val PO_EXTENSION_1 = ".PO"
const val PO_EXTENSION_2 = ".po"

const val EXTRACTED_DIR_NAME = "extracted"
const val OUTPUT_DIR_NAME = "output"
const val BASE_EVS_FILENAME_SIZE = 6

private var labelNum = 0
private var isLastInstruction = false
private var emptyLineHappened = false

val valOrder = ByteOrder.LITTLE_ENDIAN
val instructionOrder = ByteOrder.BIG_ENDIAN

private var textList: TextList = TextList()

// stores address values with their associated label name. Used when decoding an event file
// the boolean indicates if the label has been used yet or not
private val labels = LinkedHashMap<Int, Pair<String, Boolean>>()

// stores text IDs together with a list of addresses that need to be filled with the real address of the text position
private val textReferenceLocations = HashMap<Short, MutableList<Int>>()

// Stores label names together with a list of addresses that need to be filled with the real address of the label
private val labelReferenceLocations = HashMap<String, MutableList<Int>>()
// stores label names together with the real address they represent
private val labelReferenceRealVals = HashMap<String, Int>()

@Throws(IOException::class, OperationNotSupportedException::class)
fun extract(path: String) {
    if (!path.endsWith(EXTENSION_1) && !path.endsWith(EXTENSION_2)) {
        //System.out.println("PATH was: " + path);
        throw OperationNotSupportedException("Only .bin files are supported")
    }

    val addressList: InnerFileAddressList
    val basePath: String
    try {
        RandomAccessFile(path, READ_MODE).use { baseFile ->
            addressList = makeList(baseFile, valOrder)
            //val pathArray = path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val pathArray = path.split("/")
            val baseNameWExt = pathArray[pathArray.size - 1]
            val baseName = nameWoExtension(baseNameWExt)
            //basePath = path.substring(0, path.length()-baseNameWExt.length()) + baseName;
            val initialPath = pathArray[0] + "/" + EXTRACTED_DIR_NAME + "/"

            // create directory if it doesn't exist
            var dir = File(initialPath)
            if (!dir.exists()) {
                dir.mkdir()
            }

            basePath = initialPath + baseName

            // create directory if it doesn't exist
            dir = File(basePath)
            if (!dir.exists()) {
                dir.mkdir()
            }

            // getting each inner file
            for (i in 0..<addressList.getListSize()) {
                val nameAddition = String.format("_%03d", i)
                val newFileName = basePath + "/" + baseName + nameAddition + EVENT_SCRIPT_EXTENSION_1
                System.out.printf("%s\r", newFileName)

                val fileAddr = addressList.getStartAddress(i)
                if (fileAddr == -1) break

                try {
                    RandomAccessFile(newFileName, WRITE_MODE).use { newFile ->
                        var size = addressList.getFileSize(i)
                        size /= 4 // size in ints (4 bytes each)
                        baseFile.seek(fileAddr.toLong())
                        for (j in 0..<size) {
                            newFile.writeInt(baseFile.readInt())
                        }
                    }
                } catch (e: Exception) {
                    println(e.message)
                    throw e
                }
                print("DONE\r")
            }
        }
    } catch (e: Exception) {
        println(e.message)
        throw e
    }
}

/**
 * Groups up all the files back into an EX.BIN file
 * @param ebootPath the path where the original EBOOT.BIN is
 * @param dirPath the path to where all the sub-files are
 * @param destinationDir the path to save the new EX.BIN (and EBOOT.BIN) files
 * @param filename the name of the EX.BIN file, without the extension
 * @param isJ `true` if the event file was extracted from the japanese version of the game
 * @throws IOException file IO exceptions
 */
@Throws(IOException::class)
fun archive(ebootPath: String, dirPath: String, destinationDir: String, filename: String, isJ: Boolean) {
    //File dir = new File(dirPath);
    //File[] directoryListing = dir.listFiles();

    val fileAddrList = LinkedList<InnerFileAddress>()

    try {
        Files.list(Paths.get(dirPath)).use { pathStream ->
            val pathList = LinkedList<Path>()
            pathStream
                .filter { s: Path ->
                    s.toString().uppercase(Locale.getDefault()).endsWith(EVENT_SCRIPT_EXTENSION_1)
                }
                .sorted().forEach { e: Path -> pathList.add(e) }
            //LinkedList<Path> pathList = pathStream.collect(Collectors.toCollection(LinkedList::new));
            val headerSize = pathList.size * 4 * 2 // x 4 bytes per int x 2 ints (start and end)
            var startAddr = roundToLBA(headerSize)

            for (child in pathList) {
                val endAddr = ((child.toFile().length() + startAddr)).toInt()
                val fileAddr = InnerFileAddress(startAddr, endAddr, 0) // currAddr only used for reading
                fileAddrList.add(fileAddr)
                startAddr = endAddr
            }
            val fileList = InnerFileAddressList(fileAddrList)


            // Update the EBOOT.BIN
            val eventFileNum = ("" + filename[1]).toInt()
            val ebootOut = File(destinationDir, EBOOT_NAME)
            if (ebootOut.exists()) {
                updateEbootFileList(ebootOut, ebootOut, eventFileNum, fileList, isJ)
            } else {
                val ebootIn = File(ebootPath, EBOOT_NAME)
                updateEbootFileList(ebootIn, ebootOut, eventFileNum, fileList, isJ)
            }


            val destinationFile = destinationDir + filename + EXTENSION_1
            try {
                RandomAccessFile(destinationFile, WRITE_MODE).use { file ->
                    fileList.writeFileAddresses(file, valOrder)
                    for (child in pathList) {
                        System.out.printf("%s\r", child.toFile().path)
                        try {
                            RandomAccessFile(child.toFile(), READ_MODE).use { subFile ->
                                val sizeInInts = subFile.length().toInt() / 4
                                for (i in 0..<sizeInInts) {
                                    file.writeInt(subFile.readInt())
                                }
                            }
                        } catch (e: java.lang.Exception) {
                            println("ERR in reading subfile for archiving: " + e.message)
                            throw e
                        }
                        print("DONE\r")
                    }
                }
            } catch (e: java.lang.Exception) {
                println("ERR in archiving process: " + e.message)
                throw e
            }
        }
    } catch (e: java.lang.Exception) {
        throw e
    }
}

/**
 * Decodes an event file's flow script
 * @param path the path to the file to decode
 * @param isJ `true` if the event file was extracted from the japanese version of the game
 * @throws IOException file IO exceptions
 */
@Throws(IOException::class)
fun decodeFlowScript(path: String?, isJ: Boolean) {
    labelNum = 0
    isLastInstruction = false
    labels.clear()

    try {
        RandomAccessFile(path, READ_MODE).use { inputFile ->
            var outputPath = nameWoExtension(path!!) + DEC_SCRIPT_EXTENSION_1
            // delete file if it already exists
            val file = File(outputPath)
            file.delete()

            try {
                RandomAccessFile(outputPath, WRITE_MODE).use { outputFile ->
                    // Adding text to a string instead of printing directly so .addr part can be the first one in the file
                    val preCodePrint = StringBuilder()

                    // section .bgm
                    preCodePrint.append(SECTION_KEYWORD + "\t" + BGM_SECTION_KEYWORD + "\n")
                    //outputFile.writeBytes(Library.SECTION_KEYWORD + "\t" + Library.BGM_SECTION_KEYWORD + "\n");
                    inputFile.seek(STARTING_SONG_ADDRESS)
                    val firstSong = readShort(inputFile, valOrder)
                    val secondSong = readShort(inputFile, valOrder)
                    preCodePrint.append(String.format("\t0x%02x\n\t0x%02x\n\n", firstSong, secondSong))

                    //outputFile.writeBytes(String.format("\t0x%02x\n\t0x%02x\n\n", firstSong, secondSong));

                    // saving all text strings
                    // in the japanese version, there is no text list at the end of the file :(
                    if (!isJ) {
                        inputFile.seek(ADDRESS_WITH_TEXT_TABLE_POINTER)
                        val textTableAddr = readInt(inputFile, valOrder)
                        textList = readEncodedTextList(inputFile, textTableAddr, true)
                    } else {
                        textList = TextList()
                    }


                    // section .talk
                    preCodePrint.append(SECTION_KEYWORD + "\t" + TALK_SECTION_KEYWORD + "\n")
                    //outputFile.writeBytes(Library.SECTION_KEYWORD + "\t" + Library.TALK_SECTION_KEYWORD + "\n");
                    //decodeTalkAddresses(inputFile, outputFile, true);
                    decodeTalkAddresses(inputFile, preCodePrint, true, isJ)

                    // section .talk2
                    preCodePrint.append(SECTION_KEYWORD + "\t" + TALK2_SECTION_KEYWORD + "\n")
                    //outputFile.writeBytes(Library.SECTION_KEYWORD + "\t" + Library.TALK2_SECTION_KEYWORD + "\n");
                    //decodeTalkAddresses(inputFile, outputFile, false);
                    decodeTalkAddresses(inputFile, preCodePrint, false, isJ)

                    // section .positions
                    preCodePrint.append(SECTION_KEYWORD + "\t" + POS_SECTION_KEYWORD + "\n")
                    //outputFile.writeBytes(Library.SECTION_KEYWORD + "\t" + Library.POS_SECTION_KEYWORD + "\n");
                    //decodePositionsOrInteractables(inputFile, outputFile, false);
                    decodePositionsOrInteractables(inputFile, preCodePrint, false, isJ)

                    // section .interactables
                    preCodePrint.append(SECTION_KEYWORD + "\t" + INTER_SECTION_KEYWORD + "\n")
                    //outputFile.writeBytes(Library.SECTION_KEYWORD + "\t" + Library.INTER_SECTION_KEYWORD + "\n");
                    //decodePositionsOrInteractables(inputFile, outputFile, true);
                    decodePositionsOrInteractables(inputFile, preCodePrint, true, isJ)

                    // address value zone
                    inputFile.seek(ADDRESS_WITH_FLOW_SCRIPT_POINTER - (if (isJ) 4 else 0))
                    var flowStartAddr = readInt(inputFile, valOrder)

                    if (flowStartAddr == -1) {
                        // if the flow start addr was -1, use the earliest LABEL's address as the start address
                        // get the smallest known address
                        val pointers = ArrayList(labels.keys)
                        if (pointers.isEmpty()) {
                            outputFile.writeBytes(EMPTY_FILE_STRING)
                            System.out.println(EMPTY_FILE_STRING)
                            return
                        }
                        flowStartAddr = pointers.stream().min(Comparator.comparing(Int::absoluteValue)).get()
                    }

                    // go to start of code
                    inputFile.seek(flowStartAddr.toLong())

                    // an address is known. Print it!
                    outputFile.writeBytes(
                        java.lang.String.format(
                            "%s\t0x%08x\n\n",
                            ADDR_KEYWORD,
                            flowStartAddr
                        )
                    )

                    // print all that was just formed, so the code can be printed right after
                    outputFile.writeBytes(preCodePrint.toString())
                    outputFile.writeBytes(
                        java.lang.String.format(
                            "%s\t%s\n",
                            SECTION_KEYWORD,
                            CODE_AREA_KEYWORD
                        )
                    )

                    while (!isLastInstruction) {
                        val currPointer = inputFile.filePointer.toInt()
                        // DEBUG
                        //System.out.println("ADDRESS:" + String.format("0x%08x", currPointer));
                        if (labels.containsKey(currPointer)) {
                            //System.out.println(labels.get(currPointer) + ":");
                            outputFile.writeBytes("\n${labels[currPointer]!!.first}:\n")//.trimIndent())
                            labels[currPointer] = Pair(labels[currPointer]!!.first, true) // setting the label as having been printed
                            //labels[currPointer].second = true
                        }

                        val textInst = decodeInstruction(inputFile, false, isJ)
                        outputFile.writeBytes(textInst)
                    }

                    // Code can exist AFTER the text, so gotta check if there are labels that weren't achieved
                    var allDone = labels.isEmpty()
                    while (!allDone) {
                        val auxMap = labels.clone() as LinkedHashMap<Int, Pair<String, Boolean>>
                        var labelsSize = labels.entries.size
                        var secondNum = 0
                        for ((pointer, pair) in auxMap) {
                            if (pair.second) {
                                //labels.remove(pointer);
                                secondNum++
                                if (secondNum >= labelsSize) {
                                    allDone = true
                                    break
                                }
                                continue
                            }
                            inputFile.seek(pointer.toLong())
                            isLastInstruction = false



                            while (!isLastInstruction) {
                                val currPointer = inputFile.filePointer.toInt()

                                // Add label before instruction
                                if (labels.containsKey(currPointer)) {
                                    outputFile.writeBytes("\n${labels[currPointer]!!.first}:\n")//.trimIndent())
                                    labels[currPointer] = Pair(labels[currPointer]!!.first, true) // setting the label as having been printed

                                    //labels[currPointer].second = true // setting the label as having been printed
                                    labelsSize++
                                }

                                val textInst = decodeInstruction(inputFile, true, isJ)
                                outputFile.writeBytes(textInst)
                            }
                        }
                    }
                }
            } catch (e: java.lang.Exception) {
                //System.out.println("ERR opening file to write decoded to: " + e.getMessage());
                throw e
            }

            // WRITING SEPERATE TEXT FILE
            outputPath = nameWoExtension(path) + TXT_EXTENSION_1
            try {
                RandomAccessFile(outputPath, WRITE_MODE).use { outputFile ->
                    textList.writeText(outputFile)
                }
            } catch (e: java.lang.Exception) {
                //System.out.println("ERR opening file to write decoded to: " + e.getMessage());
                throw e
            }
        }
    } catch (e: java.lang.Exception) {
        //System.out.println("ERR opening file to decode: " + e.getMessage());
        throw e
    }
}

/**
 * Encodes an event file's flow script back to the original format
 * @param inputPath the path for the file to encode back
 * @param isJ `true` if the event file was extracted from the japanese version of the game
 * @throws IOException file IO exceptions
 * @throws OperationNotSupportedException thrown if some part of the file isn't formatted as expected
 */
@Throws(IOException::class, OperationNotSupportedException::class)
fun encodeFlowScript(inputPath: String?, isJ: Boolean) {
    isLastInstruction = false
    emptyLineHappened = false
    textReferenceLocations.clear()
    labelReferenceLocations.clear()
    labelReferenceRealVals.clear()
    val textListSize: Int
    val inputName = nameWoExtension(inputPath!!)
    val outputPath = inputName + TEMP_STRING + EVENT_SCRIPT_EXTENSION_1
    val inputTextPath = inputName + TXT_EXTENSION_1
    try {
        RandomAccessFile(inputPath, READ_MODE).use { inputFile ->
            // checking if the file is empty
            var line = inputFile.readLine()
            line = removeCommentAndSpaces(line)

            if (line.compareTo(EMPTY_FILE_STRING) == 0) {
                System.out.println(EMPTY_FILE_STRING)
                return
            }

            inputFile.seek(0)

            // delete file if it already exists
            val file = File(outputPath)
            file.delete()
            try {
                RandomAccessFile(outputPath, WRITE_MODE).use { outputFile ->
                    // first gonna get the data from the og file up until the event flow script
                    fillFileBeginning(inputPath, inputFile, outputFile, isJ)


                    // skip empty lines
                    while ((inputFile.readLine().also { line = it }).compareTo("") == 0);
                    line = removeCommentAndSpaces(line)


                    // skip spaces and tabs after "section"
                    //val bgmSplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val bgmSplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).toTypedArray()
                    var i = skipSpacesNTabs(bgmSplit, 1)

                    // BGM section check
                    if (bgmSplit[0].compareTo(SECTION_KEYWORD) != 0 || bgmSplit[i].compareTo(BGM_SECTION_KEYWORD) != 0) {
                        throw OperationNotSupportedException(NOT_FORMATTED_ERR_TXT)
                    }

                    val pointerBK = outputFile.filePointer
                    outputFile.seek(STARTING_SONG_ADDRESS)
                    while ((inputFile.readLine().also { line = it }).compareTo("") != 0) {
                        //val songEntrySplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        val songEntrySplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).toTypedArray()
                        i = skipSpacesNTabs(songEntrySplit, 1)
                        writeShort(outputFile, valOrder, extractShortFromString(songEntrySplit[i]))
                    }
                    outputFile.seek(pointerBK)

                    // skip empty lines
                    while ((inputFile.readLine().also { line = it }).compareTo("") == 0);
                    line = removeCommentAndSpaces(line)

                    // skip spaces and tabs after "section"
                    // val talkSplit: Array<String> = line.split(Library.SPACE_TAB_REGEX.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val talkSplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).toTypedArray()
                    i = skipSpacesNTabs(talkSplit, 1)

                    // Primary talk section check
                    if (talkSplit[0].compareTo(SECTION_KEYWORD) != 0 || talkSplit[i].compareTo(TALK_SECTION_KEYWORD) != 0) {
                        throw OperationNotSupportedException(NOT_FORMATTED_ERR_TXT)
                    }
                    // Primary talk section handling
                    registerTalkAddresses(inputFile, true, isJ)

                    // skip empty lines
                    while ((inputFile.readLine().also { line = it }).compareTo("") == 0);
                    line = removeCommentAndSpaces(line)

                    // skip spaces and tabs after "section"
                    // val talk2Split: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val talk2Split: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).toTypedArray()
                    i = skipSpacesNTabs(talk2Split, 1)

                    // Secondary talk section check
                    if (talk2Split[0].compareTo(SECTION_KEYWORD) != 0 || talk2Split[i].compareTo(TALK2_SECTION_KEYWORD) != 0) {
                        throw OperationNotSupportedException(NOT_FORMATTED_ERR_TXT)
                    }
                    // Secondary talk section handling
                    registerTalkAddresses(inputFile, false, isJ)


                    // skip empty lines
                    while ((inputFile.readLine().also { line = it }).compareTo("") == 0);
                    line = removeCommentAndSpaces(line)

                    // skip spaces and tabs after "section"
                    //val positionsSplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val positionsSplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).toTypedArray()
                    i = skipSpacesNTabs(positionsSplit, 1)

                    // positions section check
                    if (positionsSplit[0].compareTo(SECTION_KEYWORD) != 0 || positionsSplit[i].compareTo(POS_SECTION_KEYWORD) != 0) {
                        throw OperationNotSupportedException(NOT_FORMATTED_ERR_TXT)
                    }

                    encodePositionOrInteractableSection(inputFile, outputFile, false, isJ)


                    // skip empty lines
                    while ((inputFile.readLine().also { line = it }).compareTo("") == 0);
                    line = removeCommentAndSpaces(line)

                    // skip spaces and tabs after "section"
                    //val interSplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val interSplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).toTypedArray()
                    i = skipSpacesNTabs(interSplit, 1)

                    // interactables section check
                    if (interSplit[0].compareTo(SECTION_KEYWORD) != 0 || interSplit[i].compareTo(INTER_SECTION_KEYWORD) != 0) {
                        throw OperationNotSupportedException(NOT_FORMATTED_ERR_TXT)
                    }

                    encodePositionOrInteractableSection(inputFile, outputFile, true, isJ)

                    // skip empty lines
                    while ((inputFile.readLine().also { line = it }).compareTo("") == 0);
                    line = removeCommentAndSpaces(line)

                    // skip spaces and tabs after "section"
                    //val codeSplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val codeSplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).toTypedArray()
                    i = skipSpacesNTabs(codeSplit, 1)

                    // Code section check
                    if (codeSplit[0].compareTo(SECTION_KEYWORD) != 0 || codeSplit[i].compareTo(CODE_AREA_KEYWORD) != 0) {
                        throw OperationNotSupportedException(NOT_FORMATTED_ERR_TXT)
                    }

                    // Code section handling
                    while (true) {
                        if (!emptyLineHappened) {
                            encodeInstruction(inputFile, outputFile)
                        } else {
                            //... check if new section OR label
                            line = inputFile.readLine()
                            if (line == null) {
                                break
                            }
                            line = removeCommentAndSpaces(line)
                            //val sectionSplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                            val sectionSplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).toTypedArray()
                            //val labelSplit: Array<String> = sectionSplit[0].split(LABEL_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                            val labelSplit: Array<String> = sectionSplit[0].split(LABEL_SEPARATOR).toTypedArray()

                            // if it is a label
                            if (sectionSplit.size == 1 && labelSplit[0].compareTo(LABEL_TXT) == 0) {
                                //String labelNum = labelSplit[1];

                                // padding to make the label code multiple to 8

                                while (outputFile.filePointer % 8 != 0L) outputFile.writeByte(0)

                                fillInRef(outputFile, sectionSplit[0].substring(0, sectionSplit[0].length - 1), ((-1).toShort()))
                                emptyLineHappened = false
                            } else { // it must be a new section...
                                // readLine of an empty line returns an empty string
                                if (line.length == 0) {
                                    continue
                                }

                                // skipping tabs and spaces between "section" and ".text"
                                i = skipSpacesNTabs(sectionSplit, 1)

                                // if different from expected "section" and ".text"
                                if (sectionSplit[0].compareTo(SECTION_KEYWORD) != 0 || sectionSplit[i].compareTo(
                                        TEXT_AREA_KEYWORD
                                    ) != 0
                                ) {
                                    throw OperationNotSupportedException(NOT_FORMATTED_ERR_TXT)
                                }

                                // skipping to the next value on that line: the number of text strings
                                //i = skipSpacesNTabs(sectionSplit, i+1);
                                //textListSize = Integer.parseInt(sectionSplit[i].substring(2), 16);
                                break
                            }
                        }
                    }

                    val textPointers: IntArray
                    try {
                        RandomAccessFile(inputTextPath, READ_MODE).use { inputTxtFile ->
                            // Count number of non-empty lines (https://stackoverflow.com/questions/48485223/how-to-get-the-number-of-lines-from-a-text-file-in-java-ignoring-blank-lines)
                            textListSize = Files.lines(Paths.get(inputTextPath)).filter { l: String -> l.isNotEmpty() }.count().toInt()

                            // text section handling
                            var textInputPointer = inputTxtFile.filePointer.toInt()
                            line = inputTxtFile.readLine()
                            textPointers = IntArray(textListSize)
                            var textId: Short = 0
                            i = 0
                            while (i < textListSize) {
                                line = removeCommentAndSpaces(line)
                                //int indexOfColon = line.indexOf(":");
                                //String text = line.substring(indexOfColon + 1);
                                //textInputPointer += indexOfColon + 1; // to skip everything before colon
                                var text = line

                                // making sure the text is between quotes
                                if (text!![0] != '\"' || text[text.length - 1] != '\"') {
                                    throw OperationNotSupportedException("TEXT FORMATTED INCORRECTLY: $text")
                                }
                                // removing quotes
                                text = text.substring(1, text.length - 1)
                                textInputPointer++ // to skip the quote

                                // splitting spaces and tabs
                                //val textSplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                                val textSplit: Array<String> = line.split(SPACE_TAB_REGEX).toTypedArray()

                                // skip spaces and tabs at beginning
                                val textEntryIdx = skipSpacesNTabs(textSplit, 0)

                                // getting text index
                                //short textId = Short.parseShort(textSplit[textEntryIdx].substring(0, 3));

                                // text has to be aligned to multiples of 8
                                while (outputFile.filePointer % 8 != 0L) outputFile.writeByte(0)

                                // filling in a text address location we didn't know yet
                                // RIGHT NOW in the output file is where the text starts
                                fillInRef(outputFile, "", textId++)

                                textPointers[i] = outputFile.filePointer.toInt()
                                //TextList.encodeText(outputFile, text.getBytes(Charset.forName("windows-1252")));
                                //TextList.encodeText(outputFile, new String(text.getBytes(Charset.forName("Cp1252")), Charset.forName("Cp1252")));
                                //TextList.encodeText(outputFile, text.getBytes(StandardCharsets.UTF_8));
                                encodeText(outputFile, String(text.toByteArray(StandardCharsets.UTF_8), StandardCharsets.UTF_8), inputTxtFile, textInputPointer)

                                textInputPointer = inputTxtFile.filePointer.toInt()
                                line = inputTxtFile.readLine()
                                i++
                            }
                        }
                    } catch (e: java.lang.Exception) {
                        //System.out.println(e.getMessage());
                        throw e
                    }


                    // gotta go update the reference to the text table
                    if (!isJ) { // in the japanese version, there is no text table
                        val textTablePointer = outputFile.filePointer.toInt()
                        outputFile.seek(ADDRESS_WITH_TEXT_TABLE_POINTER)
                        writeInt(outputFile, valOrder, textTablePointer)
                        outputFile.seek(textTablePointer.toLong())

                        // writing the text table at the end
                        writeInt(outputFile, valOrder, textListSize)

                        i = 0
                        while (i < textListSize) {
                            writeInt(outputFile, valOrder, textPointers[i])
                            i++
                        }
                    }

                    // final padding
                    while (outputFile.filePointer % 0x800 != 0L) {
                        outputFile.writeByte(0)
                    }
                }
            } catch (e: java.lang.Exception) {
                //System.out.println(e.getMessage());
                throw e
            }
        }
    } catch (e: java.lang.Exception) {
        //System.out.println(e.getMessage());
        throw e
    }

    val ogPath = nameWoExtension(inputPath) + EVENT_SCRIPT_EXTENSION_1
    val ogFile = File(ogPath)
    val tempFile = File(outputPath)

    if (ogFile.exists()) {
        if (ogFile.delete()) {
            if (tempFile.renameTo(ogFile)) {
                print("DONE\r")
            }
        }
    }
}

/**
 * Fills start of output file based on original file
 * @param path input file's path
 * @param inputFile object used to read from input file
 * @param outputFile object used to write to output file
 * @param isJ `true` if the event file was extracted from the japanese version of the game
 */
@Throws(IOException::class, OperationNotSupportedException::class)
private fun fillFileBeginning(path: String, inputFile: RandomAccessFile, outputFile: RandomAccessFile, isJ: Boolean) {
    val ogPath = nameWoExtension(path) + EVENT_SCRIPT_EXTENSION_1
    try {
        RandomAccessFile(ogPath, READ_MODE).use { ogFile ->
            ogFile.seek(ADDRESS_WITH_FLOW_SCRIPT_POINTER - (if (isJ) 4 else 0))
            var startAddr = readInt(ogFile, valOrder)

            var addrLine = inputFile.readLine()
            // if the og file doesn't have a valid address, gotta get one from the input file's .addr zone
            if (startAddr == -1) {
                addrLine = removeCommentAndSpaces(addrLine)
                //val addrSplit: Array<String> = addrLine.split(SPACE_TAB_REGEX.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val addrSplit: Array<String> = addrLine.split(SPACE_TAB_REGEX).toTypedArray()
                if (addrSplit[0].compareTo(ADDR_KEYWORD) != 0) {
                    throw OperationNotSupportedException("addr not there?")
                }
                val i = skipSpacesNTabs(addrSplit, 1)
                startAddr = extractIntFromString(addrSplit[i])
            }

            // back to the beginning of the file
            ogFile.seek(0)

            // moving the address while copying the data, until the event flow script place
            var currAddr = outputFile.filePointer.toInt()
            while (currAddr < startAddr) {
                outputFile.writeInt(ogFile.readInt())
                currAddr = outputFile.filePointer.toInt()
            }
        }
    } catch (e: java.lang.Exception) {
        println("ERR opening original file to read initial data from: " + e.message)
        throw e
    }
}

@Throws(IOException::class)
private fun decodeInstruction(inputFile: RandomAccessFile, isPastText: Boolean, isJ: Boolean): String {
    var instr = inputFile.readByte() // the start of instructions is always FF

    // check if it is the last pointer
    val currPointer = inputFile.filePointer.toInt()

    if (!isJ && !isPastText && currPointer >= textList.getFirstAddr()) {
        isLastInstruction = true
        return ""
    }

    if (instr != CMD_START) {
        // advance through padding
        var t: Long = 0
        val tries = inputFile.length() - inputFile.filePointer
        while ((inputFile.readByte().also { instr = it }) == 0.toByte()) {
            t++
            if (t == tries - 1) {
                break
            }
        }

        // if, after the padding, the next byte still isn't an instruction, it must be text
        if (instr != CMD_START) {
            isLastInstruction = true
        }

        inputFile.seek(inputFile.filePointer - 1)
        return ""
    }

    instr = inputFile.readByte()

    // if the next instruction was actually a text instruction (text normally starts with FF1B)
    if (instr == CHARACTER_NAME_BYTE) {
        isLastInstruction = true
        return ""
    }

    var name: String
    val label: String
    val param: String
    val param2: String
    val addressStr: String
    val check: Short
    val address: Int
    val fee: Int
    val smolParam: Byte
    // UNCOMMENT FOR DEBUG HERE
    //System.out.printf("yep: 0x%02x\n", instr);
    val flowInstr: FlowInstruction = FLOW_INSTRUCTIONS[instr]!!
    name = flowInstr.name
    when (flowInstr) {
        FlowInstruction.ret -> {
            // advancing the 00s
            getShortString(inputFile)
            if (isPastText) isLastInstruction = true // this is used for situations where the labels have to be used to know about more code

            return "\t" + name + "\n"
        }

        FlowInstruction.jump -> {
            check = readShort(inputFile, instructionOrder)
            name = simpleInstructionCheck(check, name, instr)

            // get followup int, which corresponds to the address to jump to
            address = readInt(inputFile, valOrder)

            label = getLabel(address)

            return "\t" + name + "\t" + label + "\n"
        }

        FlowInstruction.jump_if -> {
            val condition = getShortString(inputFile)
            address = readInt(inputFile, valOrder)
            label = getLabel(address)

            return "\t$name\t$condition,$label\t$COMMENT_SYMBOL the parameter's exact meaning is unknown, but it might be related to game flags\n"
        }

        FlowInstruction.battle -> {
            val p = getShortVal(inputFile)
            param = String.format("$HEX_PREFIX%04x", p)
            val battleName = if (p > BATTLES.size - 1) {
                "unknown"
            } else {
                BATTLES[p.toInt()]
            }

            return "\t$name\t$param\t$COMMENT_SYMBOL $battleName\n"
        }

        FlowInstruction.ld_world_map -> {
            param = getShortString(inputFile)
            addressStr = getIntString(inputFile)
            return "\t$name\t$param,$addressStr\t$COMMENT_SYMBOL loads a world map\n"
        }

        FlowInstruction.open_shop_menu -> {
            check = getShortVal(inputFile)
            //return "\t" + name + "\t" + getShortString(check) + "\t" + COMMENT_SYMBOL + " opens shop menu: " + getShopDescription(check) + "\n"
            return "\t$name\t${getShortString(check)}\t$COMMENT_SYMBOL opens shop menu: ${getShopDescription(check)}\n"
        }

        FlowInstruction.ld_file -> {
            param = getShortString(inputFile)
            addressStr = getIntString(inputFile)
            return "\t$name\t$param,$addressStr\t$COMMENT_SYMBOL loads another event file\n"
        }

        FlowInstruction.ld_3d_map -> {
            val mapID = getByteString(inputFile)
            val unknown = getByteString(inputFile)
            val x = getByteString(inputFile)
            val y = getByteString(inputFile)
            val direction = getByteString(inputFile)
            val fourthParam = getByteString(inputFile)

            return "\t$name\t$mapID,$unknown,$x,$y,$direction,$fourthParam\t$COMMENT_SYMBOL ld_3d_map <map ID>,<unknown>,<X>,<Y>,<direction (0|1|2|3 -> E|W|S|N)>, <unknown>\n"
        }

        FlowInstruction.give_item -> {
            param = getShortString(inputFile)
            val quantity = getInt(inputFile)

            return "\t$name\t$param,$quantity\t$COMMENT_SYMBOL give_item <item_id>,<quantity>\n"
        }

        FlowInstruction.play_MV -> {
            param = String.format("MV%02x.pmf", inputFile.readByte())
            param2 = getByteString(inputFile)
            return "\t$name\t$param,$param2\t$COMMENT_SYMBOL second parameter is some kind of flag?\n"
        }

        FlowInstruction.money_check -> {
            check = readShort(inputFile, instructionOrder)
            name = simpleInstructionCheck(check, name, instr)
            fee = getInt(inputFile)
            address = getInt(inputFile)
            label = getLabel(address)

            return "\t$name\t$fee,$label\t$COMMENT_SYMBOL money_check <fee, label when not enough money>\n"
        }

        FlowInstruction.money_transfer -> {
            check = getShortVal(inputFile) // actually the direction of the transaction
            fee = getInt(inputFile)

            //return "\t" + name + "\t" + MONEY_DIRECTION.entries[check.toInt()] + "," + fee + "\t" + COMMENT_SYMBOL + " money_transfer <ADD or REMOVE>,<quantity>\n"
            return "\t$name\t${MONEY_DIRECTION.entries[check.toInt()]},$fee\t$COMMENT_SYMBOL money_transfer <ADD or REMOVE>,<quantity>\n"
        }

        FlowInstruction.open_save_menu -> return "\t" + name + "\n"
        FlowInstruction.wait -> {
            val ticks = getShortString(inputFile)
            return "\t$name\t$ticks\t$COMMENT_SYMBOL value in ticks \n"
        }

        FlowInstruction.player_option -> {
            param = getShortString(inputFile)
            address = getInt(inputFile)
            label = getLabel(address)

            return "\t$name\t$param,$label\t$COMMENT_SYMBOL $name <option num?>,<label>\n"
        }

        FlowInstruction.ld_text -> {
            check = readShort(inputFile, instructionOrder)
            name = simpleInstructionCheck(check, name, instr)
            address = getInt(inputFile)
            var textIdx = textList.indexOfText(address)

            // sometimes, text that ISN'T in the text table is used by the game (thanks developers)
            // in these cases, the string has to be obtained based on the address, and then added to the text list
            // this will always be used by the japanese version, since the text list doesn't exist
            if (textIdx == -1) {
                textList.addText(inputFile, address, true)
                textIdx = textList.indexOfText(address)
            }
            return "\t$name\t$textIdx\t$COMMENT_SYMBOL idx of text in .text section\n"
        }

        FlowInstruction.unk_cmd_2F,
        FlowInstruction.unk_cmd_30,
        FlowInstruction.unk_cmd_3A,
        FlowInstruction.unk_cmd_3B,
        FlowInstruction.unk_cmd_44,
        FlowInstruction.unk_cmd_45,
        FlowInstruction.unk_cmd_47,
        FlowInstruction.unk_cmd_58,
        FlowInstruction.unk_cmd_59,
        FlowInstruction.unk_cmd_5A,
        FlowInstruction.unk_cmd_87 -> {
            check = readShort(inputFile, valOrder)
            address = getInt(inputFile)
            label = getLabel(address)
            //return "\t" + name + "\t" + getShortString(check) + "," + label + "\t" + COMMENT_SYMBOL + " unknown, but uses a label\n"
            return "\t$name\t${getShortString(check)},$label\t$COMMENT_SYMBOL unknown, but uses a label\n"
        }

        FlowInstruction.open_dialog -> {
            check = readShort(inputFile, instructionOrder)
            name = simpleInstructionCheck(check, name, instr)
            return "\t$name\t$COMMENT_SYMBOL opens dialog box graphic\n"
        }

        FlowInstruction.close_dialog -> {
            check = readShort(inputFile, instructionOrder)
            name = simpleInstructionCheck(check, name, instr)
            return "\t$name\t$COMMENT_SYMBOL closes dialog box graphic\n"
        }

        FlowInstruction.pose -> {
            val character = inputFile.readByte()
            val pose = inputFile.readByte()
            val poseStr = if (pose < 0) {
                String.format("$HEX_PREFIX%02x", pose)
            } else POSES.entries[pose.toInt()].name
            return "\t$name\t" +
                    "${String.format("$HEX_PREFIX%02x", character)}," +
                    "$poseStr," +
                    "${getByteString(inputFile)},${getByteString(inputFile)}," +
                    "${EVENT_DIRS.entries[inputFile.readByte().toInt()]}," +
                    "${getByteString(inputFile)},${getIntString(inputFile)}\t" +
                    "$COMMENT_SYMBOL pose <character ID>,<pose>,<X>,<Y>,<direction>,<unknown>,<unknown>\n"

            //return ("\t" + name + "\t" + String.format("$HEX_PREFIX%02x", character) + "," + poseStr + ","
            //        + getByteString(inputFile) + "," + getByteString(inputFile) + "," + EVENT_DIRS.entries[inputFile.readByte().toInt()] + ","
            //        + getByteString(inputFile) + "," + getIntString(inputFile) + "\t"
            //        + COMMENT_SYMBOL + " pose <character ID>,<pose>,<X>,<Y>,<direction>,<unknown>,<unknown>\n")
        }

        FlowInstruction.fx ->
            return "\t$name\t${getByteString(inputFile)},${getByteString(inputFile)}," +
                    "${getIntString(inputFile)},${getIntString(inputFile)}\t$COMMENT_SYMBOL makes effect happen, like lightning. No idea of the specifics\n"
            //return ("\t" + name + "\t" + getByteString(inputFile) + "," + getByteString(inputFile ) + ","
                //+ getIntString(inputFile) + "," + getIntString(inputFile) + "\t" + COMMENT_SYMBOL + " makes effect happen, like lightning. No idea of the specifics\n")

        FlowInstruction.clr_char -> {
            param = getShortString(inputFile)
            return "\t$name\t$param\t$COMMENT_SYMBOL this clears the character numbered in the parameter\n"
        }

        FlowInstruction.ld_portrait -> {
            smolParam = inputFile.readByte() // character ID
            return "\t$name\t${PORTRAIT_CHARS.entries[smolParam.toInt()]},${PORTRAIT_ORIENTATION.entries[inputFile.readByte().toInt()]}\n"
            //return "\t" + name + "\t" + PORTRAIT_CHARS.entries[smolParam.toInt()] + "," + PORTRAIT_ORIENTATION.entries[inputFile.readByte().toInt()] +
            //        "\n"
        }

        FlowInstruction.close_portrait -> {
            check = readShort(inputFile, instructionOrder)
            name = simpleInstructionCheck(check, name, instr)
            return "\t$name\t$COMMENT_SYMBOL closes portrait graphic\n"
        }

        FlowInstruction.emote -> {
            smolParam = inputFile.readByte() // character ID
            return "\t$name\t$smolParam,${EMOTES.entries[inputFile.readByte().toInt()]}\t$COMMENT_SYMBOL first parameter = character ID (dependent on scene)\n"
        }

        FlowInstruction.screen_fx -> {
            check = getShortVal(inputFile)
            return "\t$name\t${getShortString(check)}\t$COMMENT_SYMBOL does an effect that fills the full screen. In this case, ${SCREEN_EFFECTS[check.toInt()]}\n"
            //return "\t" + name + "\t" + getShortString(check) + "\t" + COMMENT_SYMBOL + " does an effect that fills the full screen. In this case, " + SCREEN_EFFECTS[check.toInt()] + "\n"
        }

        FlowInstruction.fade_char -> return "\t$name\t${inputFile.readByte()},${inputFile.readByte()}\t$COMMENT_SYMBOL fades character with ID in first param with speed in second param\n"
        FlowInstruction.plan_char_mov ->
            return "\t$name\t" +
                    "${getByteString(inputFile)},${getByteString(inputFile)}," +
                    "${getByteString(inputFile)},${getByteString(inputFile)}," +
                    "${getByteString(inputFile)},${getByteString(inputFile)}\t" +
                    "$COMMENT_SYMBOL $name\t<character ID>,<trajectory idx>,<speed>,<direction_at_destination>,...\n"
            //return ("\t" + name + "\t" + getByteString(inputFile) + "," + getByteString(inputFile) + "," + getByteString(inputFile) + "," +
            //    getByteString(inputFile) + "," + getByteString(inputFile) + "," + getByteString(inputFile) + "\t" + COMMENT_SYMBOL + " " + name + "\t<character ID>,<trajectory idx>,<speed>,<direction_at_destination>,...\n")

        FlowInstruction.follow_char -> {
            check = getShortVal(inputFile)
            return "\t$name\t$check\t$COMMENT_SYMBOL sets camera to follow character. parameter = character ID (dependent on scene)\n"
        }

        FlowInstruction.clr_emote -> {
            check = getShortVal(inputFile)
            return "\t$name\t$check\t$COMMENT_SYMBOL clears the emote of the character in the parameter\n"
        }

        FlowInstruction.do_planned_moves -> {
            check = readShort(inputFile, instructionOrder)
            name = simpleInstructionCheck(check, name, instr) // HERE
            return "\t$name\t$COMMENT_SYMBOL executes the previously planned character movements\n"
        }

        FlowInstruction.tp_char -> {
            param = getShortString(inputFile)
            return "\t$name\t$param,${getIntString(inputFile)}\t$COMMENT_SYMBOL sets position/direction of a character, specifics of parameters are unknown\n"
            //return "\t" + name + "\t" + param + "," + getIntString(inputFile) + "\t" + COMMENT_SYMBOL + " sets position/direction of a character, specifics of parameters are unknown\n"
        }

        FlowInstruction.play_song -> {
            param = getShortString(inputFile)
            return "\t$name\t$param\t$COMMENT_SYMBOL plays the song whose ID is in the parameter\n"
        }

        FlowInstruction.play_sfx -> {
            check = getShortVal(inputFile)
            return "\t$name\t${getShortString(check)}\t$COMMENT_SYMBOL plays sfx: ${getSFXDescription(check)}\n"
            //return "\t" + name + "\t" + getShortString(check) + "\t" + COMMENT_SYMBOL + " plays sfx: " + getSFXDescription(check) + "\n"
        }

        else -> {
            val toRet = java.lang.StringBuilder()
            toRet.append("\t$UNKNOWN_INSTR_TEXT|FF").append(String.format("%02x", instr))
                .append(String.format("%02x", inputFile.readByte())).append(String.format("%02x", inputFile.readByte()))
            val params: Int = PARAM_NUM[flowInstr]!!.toInt()
            var i = 0
            while (i < params) {
                toRet.append(",").append(String.format("%08x", readInt(inputFile, instructionOrder)))
                i++
            }
            return toRet.append("\n").toString()
        }
    }
}

@Throws(IOException::class, OperationNotSupportedException::class)
private fun encodeInstruction(inputFile: RandomAccessFile, outputFile: RandomAccessFile) {
    var line = inputFile.readLine()
    if (line == null) {
        emptyLineHappened = true
        return
    }
    line = removeCommentAndSpaces(line)

    if (line.compareTo(COMMENT_INDICATOR) == 0) {
        // then the line is fully commented
        return
    }

    //val spaceSplit = line.split(SPACE_TAB_REGEX.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val spaceSplit = line.split(SPACE_TAB_REGEX.toRegex()).toTypedArray()

    if (spaceSplit.size == 1) {
        // if it is a new line, readLine returns an empty string
        if (spaceSplit[0].isEmpty()) {
            emptyLineHappened = true
            return
        }
    }

    // skipping first spaces/tabs
    var i = skipSpacesNTabs(spaceSplit, 0)

    val instr = spaceSplit[i]
    if (instr.contains("|")) {
        encodeUnknownInstruction(outputFile, instr)
        return
    }

    // no parameters
    // this includes ret, open_dialog, close_dialog, close_portrait, do_planned_moves
    if (i + 1 == spaceSplit.size) {
        writeIntInstruction(outputFile, instr, 0.toShort()) // 0 for padding

        // in case this is a return instruction, must add padding if the next instruction isn't aligned with multiples of 8
        if (instr.compareTo(FlowInstruction.ret.name) == 0) {
            while (outputFile.filePointer % 8 != 0L) outputFile.writeByte(0)
        }
    } else { // has parameters
        i = skipSpacesNTabs(spaceSplit, i + 1) // skips spaces and tabs until the first parameter
        val params = spaceSplit[i]
        //val paramSplit = params.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val paramSplit = params.split(",").toTypedArray()

        // jump instruction
        if (instr.compareTo(FlowInstruction.jump.name) == 0) {
            val label = paramSplit[0]

            // write first 4 bytes
            writeIntInstruction(outputFile, instr, 0.toShort()) // 0 for padding

            // register a required address
            val currAddr = outputFile.filePointer.toInt()
            addLabelRef(outputFile, label, currAddr)

            // jump if instruction
        } else if (
            instr.compareTo(FlowInstruction.jump_if.name) == 0 ||
            instr.compareTo(FlowInstruction.unk_cmd_2F.name) == 0 ||
            instr.compareTo(FlowInstruction.unk_cmd_30.name) == 0 ||
            instr.compareTo(FlowInstruction.unk_cmd_3A.name) == 0 ||
            instr.compareTo(FlowInstruction.unk_cmd_3B.name) == 0 ||
            instr.compareTo(FlowInstruction.unk_cmd_44.name) == 0 ||
            instr.compareTo(FlowInstruction.unk_cmd_45.name) == 0 ||
            instr.compareTo(FlowInstruction.unk_cmd_47.name) == 0 ||
            instr.compareTo(FlowInstruction.unk_cmd_58.name) == 0 ||
            instr.compareTo(FlowInstruction.unk_cmd_59.name) == 0 ||
            instr.compareTo(FlowInstruction.unk_cmd_5A.name) == 0 ||
            instr.compareTo(FlowInstruction.unk_cmd_87.name) == 0
        ) {
            val param = paramSplit[0]
            val label = paramSplit[1]

            // write first 4 bytes
            writeIntInstruction(outputFile, instr, extractShortFromString(param))

            // register a required address
            val currAddr = outputFile.filePointer.toInt()
            addLabelRef(outputFile, label, currAddr)

            // simple 1 short parameter instructions
        } else if (
            instr.compareTo(FlowInstruction.UNKNOWN_COMMAND_27.name) == 0 ||
            instr.compareTo(FlowInstruction.battle.name) == 0 ||
            instr.compareTo(FlowInstruction.open_shop_menu.name) == 0 ||
            instr.compareTo(FlowInstruction.wait.name) == 0 ||
            instr.compareTo(FlowInstruction.clr_char.name) == 0 ||
            instr.compareTo(FlowInstruction.screen_fx.name) == 0 ||
            instr.compareTo(FlowInstruction.play_song.name) == 0 ||
            instr.compareTo(FlowInstruction.play_sfx.name) == 0
        ) {
            val param = paramSplit[0]

            // write first 4 bytes
            writeIntInstruction(outputFile, instr, extractShortFromString(param))

            // simple 1 short parameter instructions (in decimal)
        } else if (
            instr.compareTo(FlowInstruction.clr_emote.name) == 0 ||
            instr.compareTo(FlowInstruction.follow_char.name) == 0
        ) {
            val param = paramSplit[0]
            writeIntInstruction(outputFile, instr, param.toShort())

            // instructions with 1 short param and 1 int
        } else if (
            instr.compareTo(FlowInstruction.ld_world_map.name) == 0 ||
            instr.compareTo(FlowInstruction.ld_file.name) == 0 ||
            instr.compareTo(FlowInstruction.tp_char.name) == 0
        ) {
            val shortParam = paramSplit[0]
            val intParam = paramSplit[1]

            // write first 4 bytes
            writeIntInstruction(outputFile, instr, extractShortFromString(shortParam))

            // write int in hexadecimal
            writeInt(outputFile, valOrder, extractIntFromString(intParam))
        } else if (instr.compareTo(FlowInstruction.ld_3d_map.name) == 0) {
            val mapID = paramSplit[0]
            val unknown = paramSplit[1]
            val x = paramSplit[2]
            val y = paramSplit[3]
            val dir = paramSplit[4]
            val param5 = paramSplit[5]

            // write first 4 bytes
            outputFile.writeByte(CMD_START.toInt())
            outputFile.writeByte(FLOW_INSTRUCTIONS_REVERSE[instr]!!.toInt())
            outputFile.writeByte(extractByteFromString(mapID).toInt())
            outputFile.writeByte(extractByteFromString(unknown).toInt())

            // write next parameters
            outputFile.writeByte(extractByteFromString(x).toInt())
            outputFile.writeByte(extractByteFromString(y).toInt())
            outputFile.writeByte(extractByteFromString(dir).toInt())
            outputFile.writeByte(extractByteFromString(param5).toInt())
        } else if (instr.compareTo(FlowInstruction.give_item.name) == 0) {
            val itemId = paramSplit[0]
            val quantity = paramSplit[1]

            // write first 4 bytes
            writeIntInstruction(outputFile, instr, extractShortFromString(itemId))

            // write quantity
            writeInt(outputFile, valOrder, quantity.toInt())
        } else if (instr.compareTo(FlowInstruction.play_MV.name) == 0) {
            val movieFileName = paramSplit[0] // format: MVXX.pmf, where XX is the number
            val param1 = movieFileName.substring(2, 4) // byte (hex without 0x)
            val param2 = paramSplit[1] // byte (hex)

            outputFile.writeByte(CMD_START.toInt())
            outputFile.writeByte(FLOW_INSTRUCTIONS_REVERSE[instr]!!.toInt())
            outputFile.writeByte(param1.toInt(16))
            outputFile.writeByte(param2.substring(2).toInt(16))
        } else if (instr.compareTo(FlowInstruction.money_check.name) == 0) {
            val fee = paramSplit[0]
            val label = paramSplit[1]

            //write first 4 bytes
            writeIntInstruction(outputFile, instr, 0.toShort())

            // write the fee
            writeInt(outputFile, valOrder, fee.toInt())

            // register a required address
            val currAddr = outputFile.filePointer.toInt()
            addLabelRef(outputFile, label, currAddr)
        } else if (instr.compareTo(FlowInstruction.money_transfer.name) == 0) {
            val direction = paramSplit[0]
            val quantity = paramSplit[1]

            //write first 4 bytes
            writeIntInstruction(
                outputFile,
                instr,
                MONEY_DIRECTION.valueOf(direction).ordinal.toShort()
            )

            // write the quantity
            writeInt(outputFile, valOrder, quantity.toInt())
        } else if (instr.compareTo(FlowInstruction.player_option.name) == 0) {
            val param = paramSplit[0]
            val label = paramSplit[1]

            // write first 4 bytes
            writeIntInstruction(outputFile, instr, extractShortFromString(param))

            // register a required address
            val currAddr = outputFile.filePointer.toInt()
            addLabelRef(outputFile, label, currAddr)
        } else if (instr.compareTo(FlowInstruction.ld_text.name) == 0) {
            val textID = paramSplit[0] // short

            // write first 4 bytes
            writeIntInstruction(outputFile, instr, 0.toShort()) // 0 for padding

            // register a required text address
            val currAddr = outputFile.filePointer.toInt()
            addTextRef(textID.toInt().toShort(), currAddr)
            writeInt(outputFile, valOrder, 0) // padding while no address
        } else if (instr.compareTo(FlowInstruction.fx.name) == 0) {
            val param1 = paramSplit[0] // byte (hex)
            val param2 = paramSplit[1] // byte (hex)
            val param3 = paramSplit[2] // int (hex)
            val param4 = paramSplit[3] // int (hex)

            // write first 4 bytes
            outputFile.writeByte(CMD_START.toInt())
            outputFile.writeByte(FLOW_INSTRUCTIONS_REVERSE[instr]!!.toInt())
            outputFile.writeByte(extractByteFromString(param1).toInt())
            outputFile.writeByte(extractByteFromString(param2).toInt())

            // write 2 ints
            writeInt(outputFile, valOrder, extractIntFromString(param3))
            writeInt(outputFile, valOrder, extractIntFromString(param4))
        } else if (instr.compareTo(FlowInstruction.pose.name) == 0) {
            val charID = paramSplit[0] // byte (hex)
            val pose = paramSplit[1] // pose text OR byte (hex)
            val x = paramSplit[2] // byte (hex)
            val y = paramSplit[3] // byte (hex)
            val direction = paramSplit[4] // direction text
            val unknown1 = paramSplit[5] // byte (hex)
            val unknown2 = paramSplit[6] // int (hex)

            // write first 4 bytes
            outputFile.writeByte(CMD_START.toInt())
            outputFile.writeByte(FLOW_INSTRUCTIONS_REVERSE[instr]!!.toInt())
            outputFile.writeByte(extractByteFromString(charID).toInt())

            // print pose or the byte if it isn't registered
            try {
                outputFile.writeByte(POSES.valueOf(pose).ordinal)
            } catch (e: IllegalArgumentException) {
                outputFile.writeByte(extractByteFromString(pose).toInt())
            }

            // write params
            outputFile.writeByte(extractByteFromString(x).toInt())
            outputFile.writeByte(extractByteFromString(y).toInt())
            outputFile.writeByte(EVENT_DIRS.valueOf(direction).ordinal)
            outputFile.writeByte(extractByteFromString(unknown1).toInt())

            // write final int
            writeInt(outputFile, valOrder, extractIntFromString(unknown2))
        } else if (instr.compareTo(FlowInstruction.ld_portrait.name) == 0) {
            val param1 = paramSplit[0] // text of the character
            val param2 = paramSplit[1] // text of the position

            // write first 4 bytes
            outputFile.writeByte(CMD_START.toInt())
            outputFile.writeByte(FLOW_INSTRUCTIONS_REVERSE[instr]!!.toInt())
            outputFile.writeByte(PORTRAIT_CHARS.valueOf(param1).ordinal)
            outputFile.writeByte(PORTRAIT_ORIENTATION.valueOf(param2).ordinal)
        } else if (instr.compareTo(FlowInstruction.emote.name) == 0) {
            val param1 = paramSplit[0] // byte
            val param2 = paramSplit[1] // text of the emote

            // write first 4 bytes
            outputFile.writeByte(CMD_START.toInt())
            outputFile.writeByte(FLOW_INSTRUCTIONS_REVERSE[instr]!!.toInt())
            outputFile.writeByte(param1.toInt())
            outputFile.writeByte(EMOTES.valueOf(param2).ordinal)
        } else if (instr.compareTo(FlowInstruction.fade_char.name) == 0) {
            val param1 = paramSplit[0] // byte
            val param2 = paramSplit[1] // byte

            // write first 4 bytes
            outputFile.writeByte(CMD_START.toInt())
            outputFile.writeByte(FLOW_INSTRUCTIONS_REVERSE[instr]!!.toInt())
            outputFile.writeByte(param1.toInt())
            outputFile.writeByte(param2.toInt())
        } else if (instr.compareTo(FlowInstruction.plan_char_mov.name) == 0) {
            val charID = paramSplit[0] // byte (hex)
            val dir = paramSplit[1] // byte (hex)
            val speed = paramSplit[2] // byte (hex)
            val dest_dir = paramSplit[3] // byte (hex)
            val param5 = paramSplit[4] // byte (hex)
            val param6 = paramSplit[5] // byte (hex)

            // write first 4 bytes
            outputFile.writeByte(CMD_START.toInt())
            outputFile.writeByte(FLOW_INSTRUCTIONS_REVERSE[instr]!!.toInt())
            outputFile.writeByte(charID.substring(2).toInt(16))
            outputFile.writeByte(dir.substring(2).toInt(16))

            // write other 4 params
            outputFile.writeByte(speed.substring(2).toInt(16))
            outputFile.writeByte(dest_dir.substring(2).toInt(16))
            outputFile.writeByte(param5.substring(2).toInt(16))
            outputFile.writeByte(param6.substring(2).toInt(16))
        } else {
            throw OperationNotSupportedException("Some instruction was named incorrectly\t\t$instr\n")
        }
    }
}

/**
 * Saves the addresses associated to characters in the scene (when player talks to them)
 * @param inputFile object used to read from input file
 * @param builder string builder used to add text later used on the output file
 * @param isJ `true` if the event file was extracted from the japanese version of the game
 * @throws IOException file stuff
 */
@Throws(IOException::class)
private fun decodeTalkAddresses(
    inputFile: RandomAccessFile,
    builder: java.lang.StringBuilder,
    isPrimaryTalk: Boolean,
    isJ: Boolean
) {
    val pointerBk = inputFile.filePointer

    var startAddress: Int =
        if (isPrimaryTalk) ADDRESS_OF_CHARACTER_DATA else ADDRESS_OF_SECONDARY_CHARACTER_DATA
    startAddress -= if (isJ) 4 else 0 // adjust for japanese version
    val numOfStructs: Int = if (isPrimaryTalk) CHARACTER_DATA_NUM else SECONDARY_CHARACTER_DATA_NUM
    val dataSize: Int = if (isPrimaryTalk) CHARACTER_DATA_SIZE else SECONDARY_CHARACTER_DATA_SIZE
    val secondAddrOffset: Int =
        if (isPrimaryTalk) CHARACTER_DATA_EVENT_ADDRESS_2_OFFSET else SECONDARY_CHARACTER_DATA_EVENT_ADDRESS_2_OFFSET

    for (i in 0..<numOfStructs) {
        inputFile.seek((startAddress + (i * dataSize)).toLong())

        //if (isNextCharacterDataEmpty(inputFile)) {
        //    continue;
        //}
        inputFile.seek(inputFile.filePointer + CHARACTER_DATA_EVENT_ADDRESS_1_OFFSET)
        val address1 = readInt(inputFile, valOrder)

        inputFile.seek(inputFile.filePointer + secondAddrOffset)
        val address2 = readInt(inputFile, valOrder)

        // If both addresses are -1, then there is nothing to write
        if ((address1 == MINUS_1_INT || address1 == 0) && (address2 == MINUS_1_INT || address2 == 0)) {
            continue
        }
        builder.append(String.format("\t%02d\t\t", i))

        // checking first address position
        if (address1 != MINUS_1_INT && address1 != 0) {
            val label = getLabel(address1)
            //outputFile.writeBytes(String.format("%02d\t\t%s\t\t%s <Character in scene>:  <Label to code that executes when spoken to>\n", i, label, Library.COMMENT_SYMBOL));
            builder.append(String.format("%s", label))
        } else {
            builder.append(LABEL_SEPARATOR)
        }

        builder.append(",")

        // checking second address position
        if (address2 != MINUS_1_INT && address2 != 0) {
            val label = getLabel(address2)

            builder.append(String.format("%s", label))
        } else {
            builder.append(LABEL_SEPARATOR)
        }

        builder.append("\n")
    }

    builder.append("\n")
    inputFile.seek(pointerBk)
}

/**
 * Saves addresses where pointers are required, used for when the event script is being encoded
 * @param inputFile the object used to read the decrypted event file
 * @param isPrimaryTalk `true` if this method is saving the addresses of primary character interactions
 * @param isJ `true` if the event file was extracted from the japanese version of the game
 * @throws IOException file related IO exceptions
 */
@Throws(IOException::class)
private fun registerTalkAddresses(inputFile: RandomAccessFile, isPrimaryTalk: Boolean, isJ: Boolean) {
    // TODO: write over all the references in these data structures, in case user wants to change what character has a reference
    // Right now, the program just writes the ones in the decoded file, it doesn't overwrite non-existing ones with FFFFFFFF
    // this would have to include moving all the data in the talk structures for things to work well

    var startAddress: Int =
        if (isPrimaryTalk) ADDRESS_OF_CHARACTER_DATA
        else ADDRESS_OF_SECONDARY_CHARACTER_DATA
    startAddress -= if (isJ) 4 else 0
    val dataSize: Int = if (isPrimaryTalk) CHARACTER_DATA_SIZE
    else SECONDARY_CHARACTER_DATA_SIZE
    val secondAddrOffset: Int =
        if (isPrimaryTalk) CHARACTER_DATA_EVENT_ADDRESS_2_ABSOLUTE_OFFSET
        else SECONDARY_CHARACTER_DATA_EVENT_ADDRESS_2_ABSOLUTE_OFFSET

    var line: String
    var i: Int
    while ((inputFile.readLine().also { line = it }).compareTo("") != 0) {
        line = removeCommentAndSpaces(line)
        //val talkLineSplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val talkLineSplit: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).toTypedArray()

        i = skipSpacesNTabs(talkLineSplit, 0)
        val characterId = talkLineSplit[i].toInt()
        i = skipSpacesNTabs(talkLineSplit, i + 1)

        val labels = talkLineSplit[i]
        val labelSplit = labels.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        // If the first LABEL space isn't empty
        if (labelSplit[0].compareTo(LABEL_SEPARATOR) != 0) {
            val address: Int = startAddress + (characterId * dataSize) + CHARACTER_DATA_EVENT_ADDRESS_1_OFFSET
            addLabelRef(null, labelSplit[0], address)
        }

        // If the second LABEL space isn't empty
        if (labelSplit[1].compareTo(LABEL_SEPARATOR) != 0) {
            val address = startAddress + (characterId * dataSize) + secondAddrOffset
            addLabelRef(null, labelSplit[1], address)
        }
    }
}

/**
 * Decodes position data or interactable data, based on `isInteractable` parameter
 * @param inputFile the object used to read from the .EVS file
 * @param builder the string builder to attach text to
 * @param isInteractable `true` to read interactable data, `false` to read position data
 * @param isJ `true` if the file was extracted from a japanese version of the game
 * @throws IOException file related exception
 */
@Throws(IOException::class)
private fun decodePositionsOrInteractables(
    inputFile: RandomAccessFile,
    builder: java.lang.StringBuilder,
    isInteractable: Boolean,
    isJ: Boolean
) {
    val pointerBk = inputFile.filePointer

    var effectivePointer: Long =
        if (isInteractable) ADDRESS_WITH_INTERACTABLE_DATA_SIZE_POINTER
        else ADDRESS_WITH_POSITION_DATA_SIZE_POINTER
    effectivePointer -= (if (isJ) 4 else 0).toLong() // adjust the pointer by 4 bytes, because JPN version has no text list

    // fetch the pointer to the list's size
    inputFile.seek(effectivePointer)
    val sizePointer = readInt(inputFile, valOrder)

    // go to where the size value is
    inputFile.seek(sizePointer.toLong())
    val size = readInt(inputFile, valOrder)

    // if the size is 0, there are no positions
    if (size == 0) {
        builder.append("\n")
        inputFile.seek(pointerBk)
        return
    }

    // go to the beginning of the list of positions
    inputFile.seek(effectivePointer + 4)
    val positionsPointer = readInt(inputFile, valOrder)
    inputFile.seek(positionsPointer.toLong())

    for (i in 0..<size) {
        val x = inputFile.readByte()
        val y = inputFile.readByte()
        val unknown = readShort(inputFile, valOrder)
        val address = readInt(inputFile, valOrder)

        val label = getLabel(address)

        builder.append(String.format("\t%03d\t%03d\t%s\n", x, y, label))
    }

    builder.append("\n")
    inputFile.seek(pointerBk)
}

/**
 * Updates the file lists of the EX.BIN file that is being archived
 * @param ebootIn the original EBOOT.BIN (or the one located in the output folder)
 * @param ebootOut the edited EBOOT.BIN (the same file if an EBOOT.BIN exists in the output folder)
 * @param eventFileNum the number of the EX.BIN file (the X, if you will)
 * @param fileList the file list to write to the EBOOT.BIN
 * @param isJ `true` if the event file was extracted from the japanese version of the game
 * @throws IOException I/O file stuff
 */
@Throws(IOException::class)
private fun updateEbootFileList(
    ebootIn: File,
    ebootOut: File,
    eventFileNum: Int,
    fileList: InnerFileAddressList,
    isJ: Boolean
) {
    val outputFilePath = ebootOut.toPath()

    // if the EBOOT.BIN doesn't exist in the output file, create a copy
    if (ebootIn.compareTo(ebootOut) != 0) {
        Files.copy(ebootIn.toPath(), outputFilePath, StandardCopyOption.REPLACE_EXISTING)
    }

    try {
        RandomAccessFile(outputFilePath.toString(), WRITE_MODE).use { file ->
            // calculate the start address of the edit
            var address: Int = if (isJ) JP_EBOOT_E0_FILELIST_ADDR else US_EBOOT_E0_FILELIST_ADDR
            address -= EBOOT_FILELIST_OFFSET * eventFileNum

            file.seek(address.toLong())
            fileList.writeFileAddresses(file, valOrder)
        }
    } catch (e: java.lang.Exception) {
        //System.out.println("ERROR reading EBOOT file to write to (in output folder)");
        throw e
    }
}

/**
 * Encodes the position trigger entries into the output file and saves addresses to pointers that must be filled in later
 * @param inputFile the object used to read from the .DEC file
 * @param outputFile the object used to write to the .EVS file
 * @param isInteractable if true, treats the data like it is an interactable, otherwise treats it like positions
 * @param isJ `true` if the event file was extracted from the japanese version of the game
 * @throws IOException I/O file stuff
 */
@Throws(IOException::class)
private fun encodePositionOrInteractableSection(
    inputFile: RandomAccessFile,
    outputFile: RandomAccessFile,
    isInteractable: Boolean,
    isJ: Boolean
) {
    // backing up the pointer to restore it after the positions
    val pointerBK = outputFile.filePointer

    var effectivePointer: Long =
        if (isInteractable) ADDRESS_WITH_INTERACTABLE_DATA_SIZE_POINTER
        else ADDRESS_WITH_POSITION_DATA_SIZE_POINTER
    effectivePointer -= (if (isJ) 4 else 0).toLong()

    // getting the size of the positions list
    outputFile.seek(effectivePointer)
    outputFile.seek(readInt(outputFile, valOrder).toLong())
    val positionsSize = readInt(outputFile, valOrder)

    // getting pointer to the first position data
    outputFile.seek(effectivePointer + 4)
    outputFile.seek(readInt(outputFile, valOrder).toLong())

    // interpreting the positions section
    for (positionEntryIdx in 0..<positionsSize) {
        var line = inputFile.readLine()
        line = removeCommentAndSpaces(line)
        //val positionData: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val positionData: Array<String> = line.split(SPACE_TAB_REGEX.toRegex()).toTypedArray()

        var i = skipSpacesNTabs(positionData, 0)
        val x = positionData[i].toShort().toByte()
        i = skipSpacesNTabs(positionData, i + 1)
        val y = positionData[i].toShort().toByte()
        i = skipSpacesNTabs(positionData, i + 1)

        val label = positionData[i]

        // writing the coordinates
        outputFile.writeByte(x.toInt())
        outputFile.writeByte(y.toInt())

        // skipping the unknown short to get to where the address is
        val address = outputFile.filePointer.toInt() + 2

        // add the address to the addresses that need to be filled
        addLabelRef(null, label, address)

        // skip the short and the address to reach the next entry
        outputFile.seek(outputFile.filePointer + 2 + 4)
    }

    outputFile.seek(pointerBK)
}

/**
 * Converts a TXT file with character dialogue and system messages into a PO file format.
 *
 * The function processes the input TXT file, extracting character names and dialogue lines,
 * and formats them as `msgctxt` (character name) and `msgid` (dialogue text) entries in a PO file.
 * It also handles specific tags:
 * - Replaces `(*LINE_BREAK*)` with `\\n` to format line breaks correctly.
 * - Converts `(*AWAITING_INPUT*)` to `(*INPUT*)\\n`.
 * - Converts `(*CONTINUE*)` to `(*CLEAR*)`.
 * - Trims extra text after `(*SHOW_OPTIONS,...)` to leave only the main tag (e.g., `(*SHOW_OPTIONS,9*)`).
 *
 * @param path the path to the input TXT file.
 * @param j a boolean indicating the language of the output PO file:
 *          - `true` for Japanese (sets the `Language` field to `ja`).
 *          - `false` for English (sets the `Language` field to `en`).
 */
fun convertTXTToPO(path: String, j: Boolean) {
    val inputLines = File(path).readLines()

    val poBuilder = StringBuilder()
    poBuilder.append("msgid \"\"\n")
    poBuilder.append("msgstr \"\"\n")
    poBuilder.append("\"Content-Type: text/plain; charset=UTF-8\\n\"\n")
    poBuilder.append("\"Language: ${if (j) "ja" else "en"}\\n\"\n\n")

    inputLines.forEach { line ->
        val characterRegex = "\\(\\*CHARACTER_NAME\\*\\)(.*?)\\(\\*LINE_BREAK\\*\\)".toRegex()
        val match = characterRegex.find(line)

        if (match != null) {
            val characterName = match.groupValues[1].trim()
            val text = line.replace(characterRegex, "")
                .replace("(*LINE_BREAK*)", "\\n")
                .replace("(*AWAITING_INPUT*)", "(*INPUT*)\\n")
                .replace("(*CONTINUE*)", "(*CLEAR*)")
                .replace("\\(\\*SHOW_OPTIONS,.*?\\*\\).*".toRegex()) { matchResult ->
                    matchResult.value.substringBefore("*)") + "*)"
                }.trim().trim('"')

            poBuilder.append("msgctxt \"$characterName\"\n")
            poBuilder.append("msgid \"$text\"\n")
            poBuilder.append("msgstr \"\"\n\n")
        } else {
            val text = line.replace("(*LINE_BREAK*)", "\\n")
                .replace("(*AWAITING_INPUT*)", "(*INPUT*)\\n")
                .replace("(*CONTINUE*)", "(*CLEAR*)")
                .replace("\\(\\*SHOW_OPTIONS,.*?\\*\\).*".toRegex()) { matchResult ->
                    matchResult.value.substringBefore("*)") + "*)"
                }.trim().trim('"')

            poBuilder.append("msgctxt \"System Message\"\n")
            poBuilder.append("msgid \"$text\"\n")
            poBuilder.append("msgstr \"\"\n\n")
        }
    }

    val outputFilePath = path.replaceAfterLast(".", "PO")
    File(outputFilePath).writeText(poBuilder.toString())
    println("PO file generated in: $outputFilePath")
}

/**
 * Converts a PO file with character dialogue and system messages into a TXT format.
 *
 * The function processes the input PO file, extracting `msgctxt` (character name) and `msgid`
 * (dialogue text), and formats them back into the original TXT format. It also reverses specific
 * tag conversions:
 * - Replaces `\\n` with `(*LINE_BREAK*)`.
 * - Converts `(*INPUT*)` to `(*AWAITING_INPUT*)`.
 * - Converts `(*CLEAR*)` to `(*CONTINUE*)`.
 *
 * @param path the path to the input PO file.
 * @throws IOException if an error occurs while reading or writing the file.
 */
fun convertPOToTXT(path: String) {
    val inputLines = File(path).readLines()

    val txtBuilder = StringBuilder()
    var characterName: String? = null

    inputLines.forEach { line ->
        when {
            line.startsWith("msgctxt") -> {
                if (!line.contains("System Message"))
                    characterName = line.removePrefix("msgctxt").trim().removeSurrounding("\"")
                else
                    characterName = null
            }
            line.startsWith("msgid") -> {
                var text = line.removePrefix("msgid").trim().removeSurrounding("\"")
                    .replace("(*INPUT*)\\n", "(*AWAITING_INPUT*)")
                    .replace("(*CLEAR*)", "(*CONTINUE*)")
                text = text.replace("\\n", "(*LINE_BREAK*)")
                if (!text.isBlank()) {
                    if (characterName != null) {
                        txtBuilder.append("\"(*CHARACTER_NAME*)$characterName(*LINE_BREAK*)$text\"\n")
                    } else {
                        txtBuilder.append("\"$text\"\n")
                    }
                }
            }
        }
    }

    val outputFilePath = path.replaceAfterLast(".", "TXT")
    File(outputFilePath).writeText(txtBuilder.toString().trim())
    println("TXT file generated in: $outputFilePath")
}

/**
 * Checks if the next character data entry is "empty"
 * @param inputFile the object used to access the input file
 * @return `true` if it is empty, `false` otherwise
 * @throws IOException reading the file
 */
@Throws(IOException::class)
private fun isNextCharacterDataEmpty(inputFile: RandomAccessFile): Boolean {
    val pointerBk = inputFile.filePointer

    val short1 = (inputFile.readShort() == 0xFFFF.toShort())
    val short2 = (inputFile.readShort() == 0.toShort())
    val address = (inputFile.readInt() == -0x1)
    val int1 = (inputFile.readInt() == 0)
    val int2 = (readInt(inputFile, ByteOrder.LITTLE_ENDIAN) == 0xFF)
    val int3 = (inputFile.readInt() == 0)

    val int4 = (inputFile.readInt() == -0x1)
    val int5 = (inputFile.readInt() == 0)
    val int6 = (readInt(inputFile, ByteOrder.LITTLE_ENDIAN) == 0xFF)
    val int7 = (inputFile.readInt() == 0)

    inputFile.seek(pointerBk)

    return short1 && short2 && address && int1 && int2 && int3 && int4 && int5 && int6 && int7
}

/**
 * Adds a label to the labels map. Used for decoding file
 * @param address the address the label points to
 * @return the label's name
 */
private fun getLabel(address: Int): String {
    val label: String
    if (!labels.containsKey(address)) {
        label = LABEL_TXT + LABEL_SEPARATOR + labelNum++
        val pair = Pair(label, false)
        labels[address] = pair
    } else {
        label = labels[address]!!.first
    }
    return label
}

@Throws(IOException::class)
private fun addLabelRef(outputFile: RandomAccessFile?, labelName: String, currAddr: Int) {
    if (labelReferenceLocations.containsKey(labelName)) {
        labelReferenceLocations[labelName]!!.add(currAddr)
        if (outputFile != null) writeInt(outputFile, valOrder, 0) // padding while no address
    } else if (labelReferenceRealVals.containsKey(labelName)) {
        writeInt(outputFile!!, valOrder, labelReferenceRealVals[labelName]!!)
    } else {
        val toAdd = LinkedList<Int>()
        toAdd.add(currAddr)
        labelReferenceLocations[labelName] = toAdd
        if (outputFile != null) writeInt(outputFile, valOrder, 0) // padding while no address
    }
}

private fun addTextRef(textID: Short, currAddr: Int) {
    if (textReferenceLocations.containsKey(textID)) {
        textReferenceLocations[textID]!!.add(currAddr)
    } else {
        val toAdd = LinkedList<Int>()
        toAdd.add(currAddr)
        textReferenceLocations[textID] = toAdd
    }
}

@Throws(IOException::class)
private fun writeIntInstruction(outputFile: RandomAccessFile, instr: String, `val`: Short) {
    outputFile.writeByte(CMD_START.toInt())
    outputFile.writeByte(FLOW_INSTRUCTIONS_REVERSE[instr]!!.toInt())
    writeShort(outputFile, valOrder, `val`)
}

@Throws(OperationNotSupportedException::class, IOException::class)
private fun encodeUnknownInstruction(outFile: RandomAccessFile, instr: String) {
    //val splitDeeper = instr.split("[|]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val splitDeeper = instr.split("[|]".toRegex()).toTypedArray()
    if (splitDeeper[0].compareTo(UNKNOWN_INSTR_TEXT) != 0) {
        throw OperationNotSupportedException("unknown instruction is not formatted correctly")
    }
    //val splitCommas = splitDeeper[1].split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val splitCommas = splitDeeper[1].split(",").toTypedArray()
    for (intValue in splitCommas) {
        writeInt(outFile, instructionOrder, intValue.toLong(16).toInt())
    }
}

fun skipSpacesNTabs(split: Array<String>, idx: Int): Int {
    var i = idx
    while (i < split.size) {
        if (split[i].isNotEmpty()) break
        i++
    }
    return i
}

/**
 * Fills in previous references that couldn't be filled at the time
 * @param outFile the object that allows writing to the output file
 * @param labelName the name of the label. Enter a random string if not in use
 * @param textId the id of the text string. Enter a -1 if not in use
 * @throws IOException file stuff
 */
@Throws(IOException::class)
private fun fillInRef(outFile: RandomAccessFile, labelName: String, textId: Short) {
    val currAddr = outFile.filePointer.toInt()
    val addrs = if (textId.toInt() != -1) {
        textReferenceLocations[textId]
    } else {
        labelReferenceLocations[labelName]
    }

    // addrs is null when, for instance, the file has unused text
    if (addrs == null) {
        println("Unused text")
        return
    }
    for (addr in addrs) {
        outFile.seek(addr.toLong())
        writeInt(outFile, valOrder, currAddr)
    }
    addrs.clear()

    if (textId.toInt() == -1) {
        labelReferenceLocations.remove(labelName)
        labelReferenceRealVals[labelName] = currAddr
    }

    outFile.seek(currAddr.toLong())
}

fun removeCommentAndSpaces(line: String): String {
    if (line.isEmpty()) return line

    if (line.length < COMMENT_SYMBOL.length) return COMMENT_INDICATOR

    // checking if the whole line is a comment by first checking if it starts with the comment symbol
    if (line.substring(0, COMMENT_SYMBOL.length).compareTo(COMMENT_SYMBOL) == 0) {
        return COMMENT_INDICATOR
    }

    var index = 0

    index = line.indexOf(COMMENT_SYMBOL[0], index)
    while (index > 0) {
        if (line.substring(index, index + COMMENT_SYMBOL.length).compareTo(COMMENT_SYMBOL) == 0) {
            break
        } else {
            index += COMMENT_SYMBOL.length
        }
        index = line.indexOf(COMMENT_SYMBOL[0], index)
    }

    if (index == -1) {
        index = line.length
    }
    index--
    while (line[index] == '\t' || line[index] == ' ') {
        index--
        // if index gets negative, then the line is FULLY commented
        if (index < 0) {
            return COMMENT_INDICATOR
        }
    }
    return line.substring(0, index + 1)
}

private fun simpleInstructionCheck(check: Short, name: String, code: Byte): String {
    if (check.toInt() != 0) throw RuntimeException("NOT ACTUAL $name INSTRUCTION ($code)")
    return name
}

@Throws(OperationNotSupportedException::class)
private fun extractByteFromString(value: String): Byte {
    try {
        if (value.substring(0, 2).compareTo(HEX_PREFIX) != 0) {
            throw OperationNotSupportedException("byte value is not formatted correctly")
        }
        val prefixRemoved = value.substring(2)
        return prefixRemoved.toShort(16).toByte()
    } catch (e: StringIndexOutOfBoundsException) {
        throw OperationNotSupportedException("byte value is not formatted correctly")
    }
}

@Throws(OperationNotSupportedException::class)
private fun extractShortFromString(value: String): Short {
    try {
        if (value.substring(0, 2).compareTo(HEX_PREFIX) != 0) {
            throw OperationNotSupportedException("short value is not formatted correctly")
        }
        val prefixRemoved = value.substring(2)
        return prefixRemoved.toInt(16).toShort()
    } catch (e: StringIndexOutOfBoundsException) {
        throw OperationNotSupportedException("short value is not formatted correctly")
    }
}

@Throws(OperationNotSupportedException::class)
private fun extractIntFromString(value: String): Int {
    try {
        if (value.substring(0, 2).compareTo(HEX_PREFIX) != 0) {
            throw OperationNotSupportedException("int value is not formatted correctly")
        }
        val prefixRemoved = value.substring(2)
        return prefixRemoved.toLong(16).toInt()
    } catch (e: StringIndexOutOfBoundsException) {
        throw OperationNotSupportedException("int value is not formatted correctly")
    }
}

@Throws(IOException::class)
private fun getByteString(file: RandomAccessFile): String {
    return String.format("$HEX_PREFIX%02x", file.readByte())
}

@Throws(IOException::class)
private fun getShortString(file: RandomAccessFile): String {
    return String.format("$HEX_PREFIX%04x", readShort(file, valOrder))
}

@Throws(IOException::class)
private fun getShortString(`val`: Short): String {
    return String.format("$HEX_PREFIX%04x", `val`)
}

@Throws(IOException::class)
private fun getShortVal(file: RandomAccessFile): Short {
    return readShort(file, valOrder)
}

@Throws(IOException::class)
private fun getIntString(file: RandomAccessFile): String {
    return String.format("$HEX_PREFIX%08x", readInt(file, valOrder))
}

@Throws(IOException::class)
private fun getInt(file: RandomAccessFile): Int {
    return readInt(file, valOrder)
}

@Throws(OperationNotSupportedException::class)
fun readHexIntString(number: String): Int {
    if (number.substring(0, 2).compareTo(HEX_PREFIX) != 0) {
        throw OperationNotSupportedException("A hex value was not formatted correctly (0x...)")
    }
    return number.substring(2).toInt(16)
}

@Throws(IOException::class)
fun testText(file: RandomAccessFile, isJ: Boolean) {
    if (isJ) {
        println("can't do this in japanese version")
        return
    }
    file.seek(ADDRESS_WITH_TEXT_TABLE_POINTER)
    val address = readInt(file, valOrder)
    val textList: TextList = readEncodedTextList(file, address, false) // TO CHANGE TO FUNCTIONAL, CHANGE THIS TO TRUE
    println(textList)
    //System.out.println(textList.writeText());
}