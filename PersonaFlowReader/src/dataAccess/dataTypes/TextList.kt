package dataAccess.dataTypes

import dataAccess.*
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.text.Normalizer
import java.util.*
import javax.naming.OperationNotSupportedException

private const val CHARACTER_NAME_COLOR = "\u001B[33m"
private const val RESET_COLOR = "\u001B[0m"

private const val START_SPECIAL = "(*"
private const val END_SPECIAL = "*)"

private const val PARAM_SEPARATOR = ","

data class TextList(
    val textList: MutableList<String> = emptyList<String>().toMutableList(),
    val refList: MutableList<Int> = emptyList<Int>().toMutableList(),
    var size: Int = 0
) {
    override fun toString(): String {
        val toRet = java.lang.StringBuilder()
        toRet.append("Number of dialogs: ").append(this.size).append("\n\n")
        for (i in 0..<this.size) {
            //String text = new String(this.textList[i], StandardCharsets.UTF_8);
            val text = textList[i]
            toRet.append(i).append("(").append(String.format("0x%08x", refList[i])).append(")").append(":\n")
                .append(text).append("\n\n")
        }
        return toRet.toString()
    }

    @Throws(IOException::class)
    fun writeText(outputFile: RandomAccessFile) {
        //outputFile.writeBytes(Library.SECTION_KEYWORD + "\t" + Library.TEXT_AREA_KEYWORD + "\t" + String.format("0x%08x", this.size) + "\n");
        for (i in 0..<this.size) {
            //outputFile.writeBytes(String.format("\t%03d:", i));
            outputFile.write(textList[i].toByteArray())
            outputFile.writeBytes("\n")
        }
        //return toRet.toString();
    }

    fun indexOfText(address: Int): Int {
        return refList.indexOf(address)
        /*
        for (i in 0..<size) {
            if (refList[i] == address) {
                return i
            }
        }
        return -1
        */
    }

    @Throws(IOException::class)
    fun addText(file: RandomAccessFile, address: Int, oneLine: Boolean) {
        val text: String = getText(file, address, oneLine)
        refList.add(address)
        textList.add(text)

        size++
    }

    fun getFirstAddr(): Int {
        return refList[0]
    }

    fun getRef(idx: Int): Int {
        return refList[idx]
    }
}

fun readEncodedTextList(file: RandomAccessFile, addr: Int, oneLine: Boolean): TextList {
    val pointerBK = file.filePointer.toInt()
    file.seek(addr.toLong())

    val size = readInt(file, ByteOrder.LITTLE_ENDIAN)

    val refList = LinkedList<Int>()
    val textList = LinkedList<String>()
    for (i in 0..<size) {
        val textRef = readInt(file, ByteOrder.LITTLE_ENDIAN)
        refList.add(textRef)
        textList.add(getText(file, textRef, oneLine))
    }
    file.seek(pointerBK.toLong())
    return TextList(textList, refList, size)
}

@Throws(IOException::class)
private fun getText(file: RandomAccessFile, address: Int, oneLine: Boolean): String {
    val pointerBK = file.filePointer.toInt()
    val toRet = StringBuilder("\"")
    file.seek(address.toLong())

    var optionsComment: StringBuilder? = null

    var data = readShort(file, ByteOrder.BIG_ENDIAN)
    var name_color_set = false
    while (data != 0xff01.toShort()) {
        getLibInstance()
        // it's an instruction
        if (TEXT_INSTRUCTIONS.containsKey(data)) {
            val instr = TEXT_INSTRUCTIONS[data]
            when (instr) {
                TextInstruction.AWAITING_INPUT,
                TextInstruction.PLAYER_FIRST_NAME,
                TextInstruction.PLAYER_NICKNAME,
                TextInstruction.PLAYER_LAST_NAME,
                TextInstruction.COIN_NUMBER -> toRet.append(
                    START_SPECIAL
                ).append(instr.name).append(END_SPECIAL)

                TextInstruction.LINE_BREAK -> {
                    if (name_color_set) {
                        if (!oneLine) toRet.append(RESET_COLOR)
                        name_color_set = false
                    }
                    if (!oneLine) toRet.append("\n")
                    else toRet.append(START_SPECIAL).append(instr.name).append(END_SPECIAL)
                }

                TextInstruction.CONTINUE -> if (!oneLine) toRet.append("\n\n")
                else toRet.append(START_SPECIAL).append(instr.name).append(END_SPECIAL)

                TextInstruction.WAIT,
                TextInstruction.PRINT_VALUE -> {
                    toRet.append(START_SPECIAL).append(instr.name).append(PARAM_SEPARATOR)
                    data = readShort(file, ByteOrder.LITTLE_ENDIAN)
                    toRet.append(data.toInt()).append(END_SPECIAL)
                }

                TextInstruction.SHOW_OPTIONS -> {
                    toRet.append(START_SPECIAL).append(instr.name).append(PARAM_SEPARATOR)
                    data = readShort(file, ByteOrder.LITTLE_ENDIAN)
                    toRet.append(data.toInt()).append(END_SPECIAL)

                    if (optionsComment == null) {
                        optionsComment = StringBuilder()
                    }
                    optionsComment.append("\t").append(COMMENT_SYMBOL).append(" Shows options: |")
                    val options = OPTIONS[data.toInt()]
                    for (s in options) {
                        optionsComment.append("\"").append(s).append("\"")
                        optionsComment.append("|")
                    }
                }

                TextInstruction.SET_COLOR,
                TextInstruction.LEGACY_SET_COLOR -> {
                    data = readShort(file, ByteOrder.LITTLE_ENDIAN)
                    if (!oneLine) {
                        toRet.append(TEXT_COLORS_ANSI[data])
                    } else {
                        toRet.append(START_SPECIAL)
                            .append(instr.name).append(PARAM_SEPARATOR)
                            .append(TEXT_COLORS[data])
                            .append(END_SPECIAL)
                    }
                }

                TextInstruction.PRINT_ICON -> {
                    data = readShort(file, ByteOrder.LITTLE_ENDIAN)
                    if (!oneLine) {
                        when (data.toInt()) {
                            0 -> toRet.append("\uD83E\uDDF4")
                            3 -> toRet.append("\uD83D\uDD11")
                            else -> toRet.append("❓")
                        }
                    } else {
                        toRet.append(START_SPECIAL)
                            .append(instr.name).append(PARAM_SEPARATOR).append(data.toInt())
                            .append(END_SPECIAL)
                    }
                }

                TextInstruction.CHARACTER_NAME -> {
                    if (!oneLine) toRet.append(CHARACTER_NAME_COLOR)
                    else toRet.append(START_SPECIAL).append(instr.name).append(END_SPECIAL)

                    name_color_set = true
                }

                TextInstruction.END -> println("END found... code shouldn't have reached this place")
                null -> println("NULL in TextList.getText")
            }
        } else { // it's text
            if (TEXT_CODES.containsKey(data)) {
                toRet.append(TEXT_CODES[data])
            } else {
                toRet.append(String.format("{%04X}", data))
            }
        }
        data = readShort(file, ByteOrder.BIG_ENDIAN)
    }

    toRet.append("\"")

    if (optionsComment != null) {
        toRet.append(optionsComment)
    }

    // restore pointer position
    file.seek(pointerBK.toLong())
    return toRet.toString()
}

@Throws(IOException::class, OperationNotSupportedException::class)
fun encodeText(outputFile: RandomAccessFile?, text: String, inputFile: RandomAccessFile, textInputPointer: Int) {
    val pointerBK = inputFile.filePointer

    //int length = text.length();
    val length = text.length
    var canBeSpecial = false
    var isSpecial = false
    var canBeClosing = false

    var data: Short
    var special = java.lang.StringBuilder()
    var i = 0
    while (i < length) {
        var currChar = text.substring(i, i + 1)
        //byte currChar = text[i];
        if (currChar[0] == START_SPECIAL[0]) {
            canBeSpecial = true
            i++
            continue
        } else if (canBeSpecial && currChar[0] == START_SPECIAL[1]) {
            isSpecial = true
            canBeSpecial = false
            i++
            continue
            // if it ended up not being a special char
        } else if (canBeSpecial && !isSpecial) {
            // write the old chars
            data = TEXT_CODES_REVERSE["" + text[i - 1]]!!
            writeShort(outputFile!!, ByteOrder.BIG_ENDIAN, data)

            // decrement i to force the loop to read the current char
            i--
            canBeSpecial = false
            i++
            continue
        } else if (isSpecial) {
            if (currChar[0] == END_SPECIAL[0]) {
                canBeClosing = true
                i++
                continue
            } else if (canBeClosing && currChar[0] == END_SPECIAL[1]) {
                // if a normal instruction
                if (TEXT_INSTRUCTIONS_REVERSE.containsKey(special.toString())) {
                    data = TEXT_INSTRUCTIONS_REVERSE[special.toString()]!!
                    writeShort(outputFile!!, ByteOrder.BIG_ENDIAN, data)
                } else {
                    // it is either the WAIT or SHOW_OPTIONS or SET_COLOR or PRINT_ICON.... instructions, which has a parameter
                    val split: List<String> = special.toString().split(PARAM_SEPARATOR)
                        //special.toString().split(PARAM_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
                    data = TEXT_INSTRUCTIONS_REVERSE[split[0]]!!
                    writeShort(outputFile!!, ByteOrder.BIG_ENDIAN, data)
                    if (split[0].compareTo(TextInstruction.WAIT.name) == 0 || split[0].compareTo(TextInstruction.SHOW_OPTIONS.name) == 0 || split[0].compareTo(
                            TextInstruction.PRINT_VALUE.name
                        ) == 0 || split[0].compareTo(TextInstruction.PRINT_ICON.name) == 0
                    ) {
                        data = split[1].toShort() // the value
                        writeShort(outputFile, ByteOrder.LITTLE_ENDIAN, data)
                    } else if (split[0].compareTo(TextInstruction.SET_COLOR.name) == 0 ||
                        split[0].compareTo(TextInstruction.LEGACY_SET_COLOR.name) == 0
                    ) {
                        data = TEXT_COLORS_REVERSE[split[1][0]]!! // the color
                        writeShort(outputFile, ByteOrder.LITTLE_ENDIAN, data)
                    } else {
                        println("EERRROOOOORRRR in special text code\n")
                    }
                }
                special = java.lang.StringBuilder()
                canBeSpecial = false
                isSpecial = false
                canBeClosing = false
                i++
                continue
            }
            special.append(currChar)
            i++
            continue
        }

        // When RandomAccessFile reads more complex characters, it puts them
        if (currChar[0].code.toByte() < 0) {
            val longCharFile = LongCharRandomAccessFile(textInputPointer + i, inputFile)
            inputFile.seek((textInputPointer + i).toLong())
            currChar = "" + longCharFile.nextChar()
            i += longCharFile.numOfBytes - 1
        }
        if (!TEXT_CODES_REVERSE.containsKey(currChar)) {
            currChar = convertCharToUsable(currChar)
        }
        data = TEXT_CODES_REVERSE[currChar]!!

        // TODO: remove this if when accents are figured out
        /*if (data > 0x006C && data <0x0078 ||
            data > 0x007E && data < 0x0088 ||
            data > 0x08E && data < 0x009A ||
            data > 0x00A8 && data < 0x00B0 ||
            data > 0x00BC && data < 0x00CF) {
                currChar = convertCharToUsable(currChar);
                data = Library.getInstance().TEXT_CODES_REVERSE.get(currChar);
            }*/
        writeShort(outputFile!!, ByteOrder.BIG_ENDIAN, data)


        i++
    }
    writeShort(outputFile!!, ByteOrder.BIG_ENDIAN, TEXT_INSTRUCTIONS_REVERSE["END"]!!)
    inputFile.seek(pointerBK)
}

@Throws(OperationNotSupportedException::class)
private fun convertCharToUsable(currChar: String): String {
    var normalizedChar = Normalizer.normalize(currChar, Normalizer.Form.NFKC)
    return if (TEXT_CODES_REVERSE.containsKey(normalizedChar)) {
        normalizedChar
    } else {
        throw OperationNotSupportedException("Character $currChar is not usable.")
    }
}