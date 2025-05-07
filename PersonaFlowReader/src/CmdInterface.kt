import dataAccess.BASE_DIR
import dataAccess.EBOOT_NAME
import dataAccess.getExtension
import dataAccess.getLibInstance
import java.io.File
import java.io.IOException
import java.util.*
import javax.naming.OperationNotSupportedException
import kotlin.math.max
import kotlin.math.min

private val OG_PATH: String = BASE_DIR + "OG/"
private val EXTRACTED_PATH: String = (BASE_DIR + EXTRACTED_DIR_NAME) + "/"
private const val CANCEL_STRING = "(Enter \"-1\" at any time to cancel the current operation and go back by one \"screen\")"

/**
 * The main execution loop for this cmd interface
 */
fun runCmdInterface() {
    val sc = Scanner(System.`in`)

    getLibInstance() // initializing Library (dont need instance, its a leftover)

    var isLeaving = false
    println("Before choosing any option, make sure the unedited Ex.BIN files are placed in a folder named \"OG\" in the directory of this program")
    while (!isLeaving) {
        printInstructions()
        println()
        println(CANCEL_STRING)

        val option = requestOption(sc)

        // try/catch to handle the errors sent by EventFileOps
        try {
            when (option) {
                0 -> extractBIN(sc)
                1 -> decodeEVS(sc)
                2 -> encodeDEC(sc)
                3 -> combineEVS(sc)
                4 -> txtToPO(sc)
                5 -> poToTXT(sc)
                99, -1 -> {
                    isLeaving = true
                    println("See ya next time!")
                }

                else -> println("Not a valid option. Try again.\n")
            }
        } catch (e: IOException) {
            println("There was a problem accessing the file")
            throw e
        } catch (e: OperationNotSupportedException) {
            println(e.message)
            //throw e
        } catch (e: NumberFormatException) {
            println("Try again.")
        }
    }
}

/**
 * Method for extracting .BIN
 * @param sc the Scanner object to read more user inputs
 * @throws OperationNotSupportedException custom exception messages here
 * @throws IOException file related exceptions
 */
@Throws(OperationNotSupportedException::class, IOException::class)
private fun extractBIN(sc: Scanner) {
    println("Enter the absolute path of the file, or an interval separated by \"-\" (example: 0-4 extracts E0.BIN to E4.BIN):")
    val input = requestInput(sc)

    if (checkIfCancel(input)) return

    if (File(input).exists()) {
        // Absolute path mode
        val file = File(input)

        println("Enter the absolute path for the extraction destination:")
        val output = requestInput(sc)

        if (checkIfCancel(input)) return

        println("Extracting: ${file.name}")
        extract(input, output)
    } else {
        // Interval mode
        for (fileNum in getNumberInterval(input)) {
            val filename = String.format("E%d.BIN", fileNum)
            val filePath = OG_PATH + filename

            val folder = File(filePath)
            if (!folder.exists()) {
                println("Skipping: $filename")
                continue
            }

            println("Extracting: $filename")
            extract(filePath)
        }
    }
    println("The files have been extracted")
}

/**
 * Method for decoding .EVS files
 * @param sc the Scanner object to read more user inputs
 * @throws OperationNotSupportedException custom exception messages here
 * @throws IOException file related exceptions
 */
@Throws(OperationNotSupportedException::class, IOException::class)
private fun decodeEVS(sc: Scanner) {
    val j = isJpn(sc)
    if (j < 0) return

    val isJ = j > 0

    while (true) {
        println("Enter the absolute path of the file (including the extension) or a directory (ending in \"/\") to decode all EVS files in it:")
        val input = requestInput(sc)

        if (checkIfCancel(input)) return

        val file = File(input)

        if (file.isDirectory) {
            // Decode all EVS files in the directory
            decodeAll(input, isJ)
            break
        } else if (file.isFile && (input.endsWith(EVENT_SCRIPT_EXTENSION_1) || input.endsWith(EVENT_SCRIPT_EXTENSION_2))) {
            // Decode a single EVS file
            decodeFlowScript(input, isJ)
            break
        } else {
            println("Invalid input. Please enter a valid directory or file with the correct extension.")
        }
    }
}

/**
 * Method for encoding .DEC files
 * @param sc the Scanner object to read more user inputs
 * @throws OperationNotSupportedException custom exception messages here
 * @throws IOException file related exceptions
 */
@Throws(OperationNotSupportedException::class, IOException::class)
private fun encodeDEC(sc: Scanner) {
    val j = isJpn(sc)
    if (j < 0) return

    val isJ = j > 0

    while (true) {
        println("Enter the absolute path of the file (including the extension) or a directory (ending in \"/\") to encode all DEC files in it:")
        val input = requestInput(sc)

        if (checkIfCancel(input)) return

        val file = File(input)

        if (file.isDirectory) {
            // Encode all DEC files in the directory
            encodeAll(input, isJ)
            break
        } else if (file.isFile && (input.endsWith(DEC_SCRIPT_EXTENSION_1) || input.endsWith(DEC_SCRIPT_EXTENSION_2))) {
            // Encode a single DEC file
            encodeFlowScript(input, isJ)
            break
        } else {
            println("Invalid input. Please enter a valid directory or file with the correct extension.")
        }
    }
}

/**
 * Method for combining .EVS files back into a .BIN
 * @param sc the Scanner object to read more user inputs
 * @throws OperationNotSupportedException custom exception messages here
 * @throws IOException file related exceptions
 */
@Throws(OperationNotSupportedException::class, IOException::class)
private fun combineEVS(sc: Scanner) {
    val j = isJpn(sc)
    if (j < 0) return

    val isJ = j > 0

    println("Enter the output path (or just press ENTER for the default path, \"/output/\" in the program's directory):")
    val outFolder = requestInput(sc)

    val cancel = checkIfCancel(outFolder)
    if (cancel) return

    var destinationDir = outFolder
    if (outFolder.isEmpty()) {
        destinationDir = "$BASE_DIR$OUTPUT_DIR_NAME/"
    } else if (!destinationDir.endsWith('/')) {
        destinationDir += '/'
    }

    // Create the directory if it doesn't exist
    val dir = File(destinationDir)
    if (!dir.exists()) {
        dir.mkdirs()
    }

    println("Enter the number of the file to combine back, or a list of absolute paths to combine (example: /path/to/E0,/path/to/E1) or an interval 0-4:")
    val input = requestInput(sc)

    if (checkIfCancel(input)) return

    val pathsToCombine = mutableListOf<String>()

    lateinit var eboot_path: String

    if (input.contains("/")) {
        // User provided a list of absolute paths separated by commas
        pathsToCombine.addAll(input.split(",").map { it.trim() })

        println("Enter the absolute path to the EBOOT.BIN:")
        eboot_path = requestInput(sc)

        if (checkIfCancel(eboot_path)) return

    } else if (input.contains("-")) {
        // User provided an interval
        eboot_path = OG_PATH + EBOOT_NAME
        for (fileNum in getNumberInterval(input)) {
            val fileName = String.format("E%d", fileNum)
            val inFolder = "$EXTRACTED_PATH$fileName"
            pathsToCombine.add(inFolder)
        }

    } else if (input.toIntOrNull() != null) {
        eboot_path = OG_PATH + EBOOT_NAME
        val fileNum = input.toInt()
        val fileName = String.format("E%d", fileNum)
        val inFolder = "$EXTRACTED_PATH$fileName"
        pathsToCombine.add(inFolder)
    } else {
        println("Invalid input. Please specify an interval or a list of absolute paths.")
        return
    }

    for (path in pathsToCombine) {
        val folder = File(path)
        if (!folder.exists() || !folder.isDirectory) {
            println("Skipping: $path (does not exist or is not a directory)")
            continue
        }

        println("Combining: $path")
        archive(eboot_path, folder.absolutePath, destinationDir, folder.name, isJ)
    }
}

/**
 * Method for converting .TXT files to .PO
 * @param sc the Scanner object to read more user inputs
 * @throws OperationNotSupportedException custom exception messages here
 * @throws IOException file related exceptions
 */
@Throws(OperationNotSupportedException::class, IOException::class)
private fun txtToPO(sc: Scanner) {
    val j = isJpn(sc)
    if (j < 0) return

    val isJ = j > 0

    while (true) {
        println("Enter the filename (including the extension) or a directory to convert all TXT files to PO in it:")
        val filePath = requestInput(sc).trim()

        if (checkIfCancel(filePath)) return

        val file = File(filePath)

        if (file.isDirectory) {
            convertAllTXTToPO(filePath, isJ)
            break
        } else if (file.isFile && (filePath.endsWith(TXT_EXTENSION_1) || filePath.endsWith(TXT_EXTENSION_2))) {
            convertTXTToPO(filePath, isJ)
            break
        } else {
            println("Invalid file or directory. Please try again...")
        }
    }
}

/**
 * Method for converting .PO files to .TXT
 * @param sc the Scanner object to read more user inputs
 * @throws OperationNotSupportedException custom exception messages here
 * @throws IOException file related exceptions
 */
@Throws(OperationNotSupportedException::class, IOException::class)
private fun poToTXT(sc: Scanner) {
    val j = isJpn(sc)
    if (j < 0) return

    val isJ = j > 0

    while (true) {
        println("Enter the filename (including the extension) or a directory to convert all PO files to TXT in it:")
        val filePath = requestInput(sc).trim()

        if (checkIfCancel(filePath)) return

        val file = File(filePath)

        if (file.isDirectory) {
            convertAllPOToTXT(filePath)
            break
        } else if (file.isFile && (filePath.endsWith(PO_EXTENSION_1) || filePath.endsWith(PO_EXTENSION_2))) {
            convertPOToTXT(filePath)
            break
        } else {
            println("Invalid file or directory. Please try again...")
        }
    }
}

/**
 * Auxiliary method for asking the user if the extracted files are from the japanese version of the game
 * @param sc the Scanner object to read more user inputs
 * @return `1` for yes, `0` for no or `-1` for none of the above
 */
private fun isJpn(sc: Scanner): Int {
    while (true) {
        println("Were the files extracted from a japanese version of the game? (y/n):")
        val jpnCheck = requestInput(sc)

        if (checkIfCancel(jpnCheck)) return -1

        val yesOrNo = yesOrNo(jpnCheck)

        if (yesOrNo < 0) println("Not an option. Try again.")
        else return yesOrNo
    }
}

/**
 * Auxiliary method for receiving yes or no answers
 * @param string the string input by the user
 * @return `1` for yes, `0` for no or `-1` for none of the above
 */
private fun yesOrNo(string: String): Int {
    return if (string.compareTo("y", ignoreCase = true) == 0) {
        1
    } else if (string.compareTo("n", ignoreCase = true) == 0) {
        0
    } else {
        -1
    }
}

/**
 * Auxiliary method to check if the user entered the cancel input, which makes the user go back by 1 "page"
 * @param string the string input by the user
 * @return `true` if the received value was -1
 */
private fun checkIfCancel(string: String): Boolean {
    val value: Int
    try {
        value = string.toInt()
        return value == -1
    } catch (e: java.lang.NumberFormatException) {
        return false
    }
}

/**
 * Auxiliary method for waiting for a number
 * @param sc the Scanner object to read more user inputs
 * @return the option number
 */
private fun requestOption(sc: Scanner): Int {
    var input: String
    var inputInt = -1
    while (true) {
        input = requestInput(sc)
        try {
            inputInt = input.toInt()
            break
        } catch (e: java.lang.NumberFormatException) {
            println("Not a number. Try again.\n")
        }
    }
    return inputInt
}

/**
 * Auxiliary method to print the > and receive a line from the user input
 * @param sc the Scanner object to read more user inputs
 * @return a string of what the user wrote
 */
private fun requestInput(sc: Scanner): String {
    print("> ")
    return sc.nextLine()
}

/**
 * Prints the instructions of the interface
 */
private fun printInstructions() {
    println("\n\n\nEnter the corresponding number to choose from the following options:")
    println("0 - extract an Ex.BIN file")
    println("1 - decode an EVS file")
    println("2 - encode a DEC file")
    println("3 - combine EVS files to form a new BIN file")
    println("4 - convert a TXT file to GNU GetText PO")
    println("5 - convert a GNU GetText PO to TXT file")
    println("99 - exit")
    println()
}

/**
 * Decodes all .EVS files in a directory
 * @param path the directory path
 * @param isJ `true` if the file was extracted from a japanese version of the game
 * @throws OperationNotSupportedException custom exception messages here
 * @throws IOException file related exceptions
 */
@Throws(OperationNotSupportedException::class, IOException::class)
private fun decodeAll(path: String, isJ: Boolean) {
    val dir = File(path)
    val directoryListing = dir.listFiles()

    if (directoryListing != null) {
        if (directoryListing.isEmpty()) {
            throw OperationNotSupportedException("The directory is empty.")
        }
        for (child in directoryListing) {
            if (getExtension(child.path)!!.compareTo("evs", ignoreCase = true) == 0) {
                System.out.printf("%s\r", child.name)
                decodeFlowScript(child.path, isJ)
            }
        }
    } else {
        println("There is something wrong with the directory.")
    }
}

/**
 * Encodes all .DEC files in a directory
 * @param path the directory path
 * @param isJ `true` if the file was extracted from a japanese version of the game
 * @throws OperationNotSupportedException custom exception messages here
 * @throws IOException file related exceptions
 */
@Throws(OperationNotSupportedException::class, IOException::class)
private fun encodeAll(path: String, isJ: Boolean) {
    val dir = File(path)
    val directoryListing = dir.listFiles()

    if (directoryListing != null) {
        if (directoryListing.isEmpty()) {
            throw OperationNotSupportedException("The directory is empty.")
        }
        for (child in directoryListing) {
            if (getExtension(child.path)!!.compareTo("dec", ignoreCase = true) == 0) {
                System.out.printf("%s\r", child.name)
                encodeFlowScript(child.path, isJ)
            }
        }
    } else {
        println("There is something wrong with the directory.")
    }
}

/**
 * Converts all .TXT files in a directory to PO
 * @param path the directory path
 * @param isJ `true` if the file was extracted from a japanese version of the game
 * @throws OperationNotSupportedException custom exception messages here
 * @throws IOException file related exceptions
 */
@Throws(OperationNotSupportedException::class, IOException::class)
private fun convertAllTXTToPO(path: String, isJ: Boolean) {
    val dir = File(path)
    val directoryListing = dir.listFiles()

    if (directoryListing != null) {
        if (directoryListing.isEmpty()) {
            throw OperationNotSupportedException("The directory is empty.")
        }
        for (child in directoryListing) {
            if (getExtension(child.path)!!.compareTo("txt", ignoreCase = true) == 0) {
                System.out.printf("%s\r", child.name)
                convertTXTToPO(child.path, isJ)
            }
        }
    } else {
        println("There is something wrong with the directory.")
    }
}

/**
 * Converts all .PO files in a directory to TXT
 *
 * This function scans a directory for all `.PO` files and converts each one to a `.TXT` file
 * using the `convertPOToTXT` function. The output files are saved in the same directory
 * with the `.TXT` extension replacing `.PO`.
 *
 * @param path the directory path containing `.PO` files
 * @throws OperationNotSupportedException if the directory is empty or no `.PO` files are found
 * @throws IOException for file-related exceptions
 */
@Throws(OperationNotSupportedException::class, IOException::class)
private fun convertAllPOToTXT(path: String) {
    val dir = File(path)
    val directoryListing = dir.listFiles()

    if (directoryListing != null) {
        val poFiles = directoryListing.filter { getExtension(it.path)?.equals("po", ignoreCase = true) == true }

        if (poFiles.isEmpty()) {
            throw OperationNotSupportedException("No .PO files found in the directory.")
        }

        for (poFile in poFiles) {
            System.out.printf("Converting %s...\n", poFile.name)
            convertPOToTXT(poFile.path)
        }
    } else {
        throw IOException("Failed to access the directory or it does not exist.")
    }
}


private fun getNumberInterval(input: String): LinkedList<Int> {
    val fileNums = LinkedList<Int>()
    // It's an interval if...
    if (input.indexOf('-') != -1) {
        //val split = input.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val split = input.split("-").toTypedArray()
        try {
            val num1 = split[0].toInt()
            val num2 = split[1].toInt()
            val first = min(num1.toDouble(), num2.toDouble()).toInt()
            val last = max(num1.toDouble(), num2.toDouble()).toInt()

            for (i in first..last) {
                fileNums.add(i)
            }
        } catch (e: java.lang.NumberFormatException) {
            println("Not a number...")
            throw e
        }
    } else {
        try {
            val fileNum = input.toInt()
            fileNums.add(fileNum)
        } catch (e: java.lang.NumberFormatException) {
            println("Not a number...")
            throw e
        }
    }
    return fileNums
}