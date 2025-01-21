package dataAccess

import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException

private const val RESET_COLOR = "\u001B[0m"
private const val BLUE_COLOR = "\u001B[34m"
private const val PINK_COLOR = "\u001B[35m"
private const val YELLOW_COLOR = "\u001B[33m"
private const val RED_COLOR = "\u001B[31m"

const val SECTION_KEYWORD = "section"
const val BGM_SECTION_KEYWORD = ".bgm"
const val TALK_SECTION_KEYWORD = ".talk"
const val TALK2_SECTION_KEYWORD = ".talk2"
const val POS_SECTION_KEYWORD = ".positions"
const val INTER_SECTION_KEYWORD = ".interactables"
const val CODE_AREA_KEYWORD = ".code"
const val TEXT_AREA_KEYWORD = ".text"
const val ADDR_KEYWORD = "addr"
const val SPACE_TAB_REGEX = "[ \t]"
const val NOT_FORMATTED_ERR_TXT = "File is not formatted correctly"
const val LABEL_TXT = "LABEL"
const val LABEL_SEPARATOR = "_"
const val UNKNOWN_INSTR_TEXT = "unknown"
const val CMD_START = 0xFF.toByte()
const val HEX_PREFIX = "0x"
const val BASE_DIR = "./"

const val STARTING_SONG_ADDRESS: Long = 0x02

const val ADDRESS_WITH_FLOW_SCRIPT_POINTER: Long = 0x64
const val ADDRESS_WITH_TEXT_TABLE_POINTER: Long = 0x34
const val ADDRESS_WITH_POSITION_DATA_SIZE_POINTER: Long = 0x38
const val ADDRESS_WITH_INTERACTABLE_DATA_SIZE_POINTER: Long = 0x48


const val ADDRESS_OF_CHARACTER_DATA = 0x1F4
const val CHARACTER_DATA_SIZE = 0x24
const val CHARACTER_DATA_NUM = 64
const val CHARACTER_DATA_EVENT_ADDRESS_1_OFFSET = 0x4
const val CHARACTER_DATA_EVENT_ADDRESS_2_OFFSET = 0xc // offset starting from the end of the above event address value
const val CHARACTER_DATA_EVENT_ADDRESS_2_ABSOLUTE_OFFSET = 0x14

const val ADDRESS_OF_SECONDARY_CHARACTER_DATA = 0xAF4
const val SECONDARY_CHARACTER_DATA_NUM = 8
const val SECONDARY_CHARACTER_DATA_SIZE = 0x1C
const val SECONDARY_CHARACTER_DATA_EVENT_ADDRESS_2_OFFSET = 0x8 // offset starting from the end of the previous event address
const val SECONDARY_CHARACTER_DATA_EVENT_ADDRESS_2_ABSOLUTE_OFFSET = 0x10

const val CHARACTER_NAME_BYTE: Byte = 0x1B
const val EMPTY_FILE_STRING = "EMPTY"

const val MINUS_1_INT = -0x1

const val COMMENT_SYMBOL = "//"
const val TABLE_COMMENT_SYMBOL = "#"
const val COMMENT_INDICATOR = "\u0001" // used to indicate a line was fully commented

const val TEMP_STRING = "_temp"

// For EBOOT modification
const val EBOOT_NAME = "EBOOT.BIN"

const val US_EBOOT_E0_FILELIST_ADDR = 0x1ED678


/*
    const val US_EBOOT_E1_FILELIST_ADDR = 0x1EC678;
    const val US_EBOOT_E2_FILELIST_ADDR = 0x1EB678;
    const val US_EBOOT_E3_FILELIST_ADDR = 0x1EA678;
    const val US_EBOOT_E4_FILELIST_ADDR = 0x1E9678;
    */
const val JP_EBOOT_E0_FILELIST_ADDR = 0x1EAA38


/*
    const val P_EBOOT_E1_FILELIST_ADDR = 0x1E9A38;
    const val JP_EBOOT_E2_FILELIST_ADDR = 0x1E8A38;
    const val JP_EBOOT_E3_FILELIST_ADDR = 0x1E7A38;
    const val JP_EBOOT_E4_FILELIST_ADDR = 0x1E6A38;
     */
// to obtain the filelist of the other files, E0_ADDR - OFFSET*file_num
const val EBOOT_FILELIST_OFFSET = 0x1000

enum class FlowInstruction {
    NOTHING,
    ret,
    jump,
    UNKNOWN_COMMAND_24,
    UNKNOWN_COMMAND_25,
    jump_if,
    UNKNOWN_COMMAND_27,
    battle,
    ld_world_map,
    open_shop_menu,
    ld_file,
    ld_3d_map,
    play_MV,
    unk_cmd_2F,
    unk_cmd_30,
    UNKNOWN_COMMAND_31,
    UNKNOWN_COMMAND_32,
    UNKNOWN_COMMAND_39,
    unk_cmd_3A,
    unk_cmd_3B,
    give_item,
    UNKNOWN_COMMAND_3D,
    money_check,
    money_transfer,
    unk_cmd_44,
    unk_cmd_45,
    unk_cmd_47,
    open_save_menu,
    UNKNOWN_COMMAND_4C,
    wait,
    UNKNOWN_COMMAND_4F,
    UNKNOWN_COMMAND_52,
    UNKNOWN_COMMAND_53,
    player_option,
    ld_text,
    UNKNOWN_COMMAND_56,
    UNKNOWN_COMMAND_57,
    unk_cmd_58,
    unk_cmd_59,
    unk_cmd_5A,
    open_dialog,
    close_dialog,
    pose,
    fx,
    clr_char,
    ld_portrait,
    close_portrait,
    emote,
    screen_fx,
    UNKNOWN_COMMAND_6D,
    plan_char_mov,
    UNKNOWN_COMMAND_74,
    UNKNOWN_COMMAND_73,
    fade_char,
    UNKNOWN_COMMAND_77,
    follow_char,
    UNKNOWN_COMMAND_79,
    clr_emote,
    do_planned_moves,
    tp_char,
    play_song,
    play_sfx,
    unk_cmd_87,
    UNKNOWN_COMMAND_88,
    UNKNOWN_COMMAND_89,
    UNKNOWN_COMMAND_8A
}

enum class TextInstruction {
    SHOW_OPTIONS, PLAYER_LAST_NAME, COIN_NUMBER, END, AWAITING_INPUT, LINE_BREAK, CONTINUE, WAIT, LEGACY_SET_COLOR, PLAYER_FIRST_NAME, PLAYER_NICKNAME, SET_COLOR, PRINT_ICON, PRINT_VALUE, CHARACTER_NAME
}

var FLOW_INSTRUCTIONS: MutableMap<Byte, FlowInstruction> = HashMap()
var TEXT_INSTRUCTIONS: MutableMap<Short, TextInstruction> = HashMap()
var TEXT_CODES: MutableMap<Short, String> = HashMap()
var TEXT_COLORS: MutableMap<Short, Char> = HashMap()
var TEXT_COLORS_ANSI: MutableMap<Short, String> = HashMap()

var FLOW_INSTRUCTIONS_REVERSE: MutableMap<String, Byte> = HashMap()
var TEXT_INSTRUCTIONS_REVERSE: MutableMap<String, Short> = HashMap()
var TEXT_CODES_REVERSE: MutableMap<String, Short> = HashMap()
var TEXT_COLORS_REVERSE: MutableMap<Char, Short> = HashMap()

var PARAMETERIZED_TEXT_INSTRUCTIONS: MutableList<String> = emptyList<String>().toMutableList()

// hashmap with the number of parameters (in extra ints) per instruction
var PARAM_NUM: MutableMap<FlowInstruction, Byte> = HashMap()

private var instance: Library? = null

fun getLibInstance(): Library? {
    if (instance == null) {
        try {
            instance = Library()
        } catch (e: IOException) {
            println("NO TABLE FILE FOUND!!!!!!")
        }
    }
    return instance
}

class Library// creating reverse maps
@Throws(IOException::class) constructor() {
    init {
        initLib()
        FLOW_INSTRUCTIONS_REVERSE = HashMap()
        for ((key, value) in FLOW_INSTRUCTIONS) {
            FLOW_INSTRUCTIONS_REVERSE[value.name]
            FLOW_INSTRUCTIONS_REVERSE[value.name] = key
        }
        val tempMapE = HashMap<String, Short>()
        for ((key, value) in TEXT_INSTRUCTIONS) {
            tempMapE[value.name] = key
        }
        TEXT_INSTRUCTIONS_REVERSE = tempMapE
        TEXT_CODES_REVERSE = HashMap()
        for ((key, value) in TEXT_CODES) {
            TEXT_CODES_REVERSE[value] = key
        }
        TEXT_COLORS_REVERSE = HashMap()
        for ((key, value) in TEXT_COLORS) {
            TEXT_COLORS_REVERSE[value] = key
        }
    }

    @Throws(IOException::class)
    private fun initLib() {
        FLOW_INSTRUCTIONS[0x00.toByte()] = FlowInstruction.NOTHING
        FLOW_INSTRUCTIONS[0x21.toByte()] = FlowInstruction.ret
        FLOW_INSTRUCTIONS[0x22.toByte()] = FlowInstruction.jump
        FLOW_INSTRUCTIONS[0x24.toByte()] = FlowInstruction.UNKNOWN_COMMAND_24
        FLOW_INSTRUCTIONS[0x25.toByte()] = FlowInstruction.UNKNOWN_COMMAND_25
        FLOW_INSTRUCTIONS[0x26.toByte()] = FlowInstruction.jump_if
        FLOW_INSTRUCTIONS[0x27.toByte()] = FlowInstruction.UNKNOWN_COMMAND_27
        FLOW_INSTRUCTIONS[0x28.toByte()] = FlowInstruction.battle
        FLOW_INSTRUCTIONS[0x29.toByte()] = FlowInstruction.ld_world_map
        FLOW_INSTRUCTIONS[0x2A.toByte()] = FlowInstruction.open_shop_menu
        FLOW_INSTRUCTIONS[0x2B.toByte()] = FlowInstruction.ld_file
        FLOW_INSTRUCTIONS[0x2C.toByte()] = FlowInstruction.ld_3d_map
        FLOW_INSTRUCTIONS[0x2D.toByte()] = FlowInstruction.play_MV
        FLOW_INSTRUCTIONS[0x2F.toByte()] = FlowInstruction.unk_cmd_2F
        FLOW_INSTRUCTIONS[0x30.toByte()] = FlowInstruction.unk_cmd_30
        FLOW_INSTRUCTIONS[0x31.toByte()] = FlowInstruction.UNKNOWN_COMMAND_31
        FLOW_INSTRUCTIONS[0x32.toByte()] = FlowInstruction.UNKNOWN_COMMAND_32
        FLOW_INSTRUCTIONS[0x39.toByte()] = FlowInstruction.UNKNOWN_COMMAND_39
        FLOW_INSTRUCTIONS[0x3A.toByte()] = FlowInstruction.unk_cmd_3A
        FLOW_INSTRUCTIONS[0x3B.toByte()] = FlowInstruction.unk_cmd_3B
        FLOW_INSTRUCTIONS[0x3C.toByte()] = FlowInstruction.give_item
        FLOW_INSTRUCTIONS[0x3D.toByte()] = FlowInstruction.UNKNOWN_COMMAND_3D
        FLOW_INSTRUCTIONS[0x3E.toByte()] = FlowInstruction.money_check
        FLOW_INSTRUCTIONS[0x3F.toByte()] = FlowInstruction.money_transfer
        FLOW_INSTRUCTIONS[0x44.toByte()] = FlowInstruction.unk_cmd_44
        FLOW_INSTRUCTIONS[0x45.toByte()] = FlowInstruction.unk_cmd_45
        FLOW_INSTRUCTIONS[0x47.toByte()] = FlowInstruction.unk_cmd_47
        FLOW_INSTRUCTIONS[0x4B.toByte()] = FlowInstruction.open_save_menu
        FLOW_INSTRUCTIONS[0x4C.toByte()] = FlowInstruction.UNKNOWN_COMMAND_4C
        FLOW_INSTRUCTIONS[0x4d.toByte()] = FlowInstruction.wait
        FLOW_INSTRUCTIONS[0x4f.toByte()] = FlowInstruction.UNKNOWN_COMMAND_4F
        FLOW_INSTRUCTIONS[0x52.toByte()] = FlowInstruction.UNKNOWN_COMMAND_52
        FLOW_INSTRUCTIONS[0x53.toByte()] = FlowInstruction.UNKNOWN_COMMAND_53
        FLOW_INSTRUCTIONS[0x54.toByte()] = FlowInstruction.player_option
        FLOW_INSTRUCTIONS[0x55.toByte()] = FlowInstruction.ld_text
        FLOW_INSTRUCTIONS[0x56.toByte()] = FlowInstruction.UNKNOWN_COMMAND_56
        FLOW_INSTRUCTIONS[0x57.toByte()] = FlowInstruction.UNKNOWN_COMMAND_57
        FLOW_INSTRUCTIONS[0x58.toByte()] = FlowInstruction.unk_cmd_58
        FLOW_INSTRUCTIONS[0x59.toByte()] = FlowInstruction.unk_cmd_59
        FLOW_INSTRUCTIONS[0x5A.toByte()] = FlowInstruction.unk_cmd_5A
        FLOW_INSTRUCTIONS[0x60.toByte()] = FlowInstruction.open_dialog
        FLOW_INSTRUCTIONS[0x61.toByte()] = FlowInstruction.close_dialog
        FLOW_INSTRUCTIONS[0x64.toByte()] = FlowInstruction.pose
        FLOW_INSTRUCTIONS[0x65.toByte()] = FlowInstruction.fx
        FLOW_INSTRUCTIONS[0x66.toByte()] = FlowInstruction.clr_char
        FLOW_INSTRUCTIONS[0x67.toByte()] = FlowInstruction.ld_portrait
        FLOW_INSTRUCTIONS[0x68.toByte()] = FlowInstruction.close_portrait
        FLOW_INSTRUCTIONS[0x69.toByte()] = FlowInstruction.emote
        FLOW_INSTRUCTIONS[0x6C.toByte()] = FlowInstruction.screen_fx
        FLOW_INSTRUCTIONS[0x6D.toByte()] = FlowInstruction.UNKNOWN_COMMAND_6D
        FLOW_INSTRUCTIONS[0x6E.toByte()] = FlowInstruction.plan_char_mov
        FLOW_INSTRUCTIONS[0x73.toByte()] = FlowInstruction.UNKNOWN_COMMAND_73
        FLOW_INSTRUCTIONS[0x74.toByte()] = FlowInstruction.UNKNOWN_COMMAND_74
        FLOW_INSTRUCTIONS[0x76.toByte()] = FlowInstruction.fade_char
        FLOW_INSTRUCTIONS[0x77.toByte()] = FlowInstruction.UNKNOWN_COMMAND_77
        FLOW_INSTRUCTIONS[0x78.toByte()] = FlowInstruction.follow_char
        FLOW_INSTRUCTIONS[0x79.toByte()] = FlowInstruction.UNKNOWN_COMMAND_79
        FLOW_INSTRUCTIONS[0x7a.toByte()] = FlowInstruction.clr_emote
        FLOW_INSTRUCTIONS[0x7B.toByte()] = FlowInstruction.do_planned_moves
        FLOW_INSTRUCTIONS[0x7c.toByte()] = FlowInstruction.tp_char
        FLOW_INSTRUCTIONS[0x80.toByte()] = FlowInstruction.play_song
        FLOW_INSTRUCTIONS[0x81.toByte()] = FlowInstruction.play_sfx
        FLOW_INSTRUCTIONS[0x87.toByte()] = FlowInstruction.unk_cmd_87
        FLOW_INSTRUCTIONS[0x88.toByte()] = FlowInstruction.UNKNOWN_COMMAND_88
        FLOW_INSTRUCTIONS[0x89.toByte()] = FlowInstruction.UNKNOWN_COMMAND_89
        FLOW_INSTRUCTIONS[0x8A.toByte()] = FlowInstruction.UNKNOWN_COMMAND_8A


        PARAM_NUM[FlowInstruction.NOTHING] = 0.toByte()
        PARAM_NUM[FlowInstruction.ret] = 0.toByte()
        PARAM_NUM[FlowInstruction.jump] = 1.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_24] = 0.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_25] = 0.toByte()
        PARAM_NUM[FlowInstruction.jump_if] = 1.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_27] = 0.toByte()
        PARAM_NUM[FlowInstruction.battle] = 0.toByte()
        PARAM_NUM[FlowInstruction.ld_world_map] = 1.toByte()
        PARAM_NUM[FlowInstruction.open_shop_menu] = 0.toByte()
        PARAM_NUM[FlowInstruction.ld_file] = 1.toByte()
        PARAM_NUM[FlowInstruction.ld_3d_map] = 1.toByte()
        PARAM_NUM[FlowInstruction.play_MV] = 0.toByte()
        PARAM_NUM[FlowInstruction.unk_cmd_2F] = 1.toByte()
        PARAM_NUM[FlowInstruction.unk_cmd_30] = 1.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_31] = 0.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_32] = 0.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_39] = 0.toByte()
        PARAM_NUM[FlowInstruction.unk_cmd_3A] = 1.toByte()
        PARAM_NUM[FlowInstruction.unk_cmd_3B] = 1.toByte()
        PARAM_NUM[FlowInstruction.give_item] = 1.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_3D] = 1.toByte()
        PARAM_NUM[FlowInstruction.money_check] = 2.toByte()
        PARAM_NUM[FlowInstruction.money_transfer] = 1.toByte()
        PARAM_NUM[FlowInstruction.unk_cmd_44] = 1.toByte()
        PARAM_NUM[FlowInstruction.unk_cmd_45] = 1.toByte()
        PARAM_NUM[FlowInstruction.unk_cmd_47] = 1.toByte()
        PARAM_NUM[FlowInstruction.open_save_menu] = 0.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_4C] = 0.toByte()
        PARAM_NUM[FlowInstruction.wait] = 0.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_4F] = 0.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_52] = 0.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_53] = 0.toByte()
        PARAM_NUM[FlowInstruction.player_option] = 1.toByte()
        PARAM_NUM[FlowInstruction.ld_text] = 1.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_56] = 0.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_57] = 0.toByte()
        PARAM_NUM[FlowInstruction.unk_cmd_58] = 1.toByte()
        PARAM_NUM[FlowInstruction.unk_cmd_59] = 1.toByte()
        PARAM_NUM[FlowInstruction.unk_cmd_5A] = 1.toByte()
        PARAM_NUM[FlowInstruction.open_dialog] = 0.toByte()
        PARAM_NUM[FlowInstruction.close_dialog] = 0.toByte()
        PARAM_NUM[FlowInstruction.pose] = 2.toByte()
        PARAM_NUM[FlowInstruction.fx] = 2.toByte()
        PARAM_NUM[FlowInstruction.clr_char] = 0.toByte()
        PARAM_NUM[FlowInstruction.ld_portrait] = 0.toByte()
        PARAM_NUM[FlowInstruction.close_portrait] = 0.toByte()
        PARAM_NUM[FlowInstruction.emote] = 0.toByte()
        PARAM_NUM[FlowInstruction.screen_fx] = 0.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_6D] = 0.toByte()
        PARAM_NUM[FlowInstruction.plan_char_mov] = 1.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_73] = 0.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_74] = 0.toByte()
        PARAM_NUM[FlowInstruction.fade_char] = 0.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_77] = 0.toByte()
        PARAM_NUM[FlowInstruction.follow_char] = 0.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_79] = 1.toByte()
        PARAM_NUM[FlowInstruction.clr_emote] = 0.toByte()
        PARAM_NUM[FlowInstruction.do_planned_moves] = 0.toByte()
        PARAM_NUM[FlowInstruction.tp_char] = 1.toByte()
        PARAM_NUM[FlowInstruction.play_song] = 0.toByte()
        PARAM_NUM[FlowInstruction.play_sfx] = 0.toByte()
        PARAM_NUM[FlowInstruction.unk_cmd_87] = 1.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_88] = 0.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_89] = 0.toByte()
        PARAM_NUM[FlowInstruction.UNKNOWN_COMMAND_8A] = 0.toByte()

        TEXT_INSTRUCTIONS[0xff01.toShort()] = TextInstruction.END
        TEXT_INSTRUCTIONS[0xff02.toShort()] = TextInstruction.AWAITING_INPUT
        TEXT_INSTRUCTIONS[0xff03.toShort()] = TextInstruction.LINE_BREAK
        TEXT_INSTRUCTIONS[0xff04.toShort()] = TextInstruction.CONTINUE
        TEXT_INSTRUCTIONS[0xff05.toShort()] = TextInstruction.WAIT
        TEXT_INSTRUCTIONS[0xff06.toShort()] = TextInstruction.LEGACY_SET_COLOR // might be legacy "SET_COLOR"
        TEXT_INSTRUCTIONS[0xff07.toShort()] = TextInstruction.PLAYER_FIRST_NAME
        TEXT_INSTRUCTIONS[0xff08.toShort()] = TextInstruction.PLAYER_NICKNAME
        TEXT_INSTRUCTIONS[0xff0E.toShort()] = TextInstruction.SHOW_OPTIONS
        TEXT_INSTRUCTIONS[0xff0F.toShort()] = TextInstruction.PLAYER_LAST_NAME
        TEXT_INSTRUCTIONS[0xff11.toShort()] = TextInstruction.COIN_NUMBER
        TEXT_INSTRUCTIONS[0xff18.toShort()] = TextInstruction.SET_COLOR
        TEXT_INSTRUCTIONS[0xff19.toShort()] = TextInstruction.PRINT_ICON
        TEXT_INSTRUCTIONS[0xff1a.toShort()] = TextInstruction.PRINT_VALUE
        TEXT_INSTRUCTIONS[(0xff00 or CHARACTER_NAME_BYTE.toInt()).toShort()] = TextInstruction.CHARACTER_NAME

        // read table file
        // It was too large to initialize in here, so it is in a file
        val reader = BufferedReader(FileReader("./table/p1p.tbl"))
        var line: String
        while ((reader.readLine().also { line = it }) != null) {
            line = removeCommentAndSpaces(line)
            if (line.isEmpty()) continue

            val split = line.split("=".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val code = split[0].toInt(16).toShort()

            var character = "NULL"
            if (split.size < 2) {
                if (code.toInt() == 0x41) character = "="
                if (code.toInt() == 0x54) character = "#"
            } else {
                character = split[1]
            }

            if (!character.isBlank()) {
                character = character.trim { it <= ' ' }
            }
            TEXT_CODES[code] = character
        }

        TEXT_COLORS[0x0000.toShort()] = '_'
        TEXT_COLORS[0x0001.toShort()] = 'W'
        TEXT_COLORS[0x0002.toShort()] = 'B'
        TEXT_COLORS[0x0003.toShort()] = 'P'
        TEXT_COLORS[0x0004.toShort()] = 'G'
        TEXT_COLORS[0x0005.toShort()] = 'Y'
        // 0006 also makes text blue, but it isn't used in P1 (at least in E0.BIN)
        TEXT_COLORS[0x0007.toShort()] = 'R'

        TEXT_COLORS_ANSI[0x0000.toShort()] = RESET_COLOR
        TEXT_COLORS_ANSI[0x0002.toShort()] = BLUE_COLOR
        TEXT_COLORS_ANSI[0x0003.toShort()] = PINK_COLOR
        TEXT_COLORS_ANSI[0x0005.toShort()] = YELLOW_COLOR
        TEXT_COLORS_ANSI[0x0007.toShort()] = RED_COLOR

        PARAMETERIZED_TEXT_INSTRUCTIONS.add(TextInstruction.WAIT.name)
        PARAMETERIZED_TEXT_INSTRUCTIONS.add(TextInstruction.SET_COLOR.name)
        PARAMETERIZED_TEXT_INSTRUCTIONS.add(TextInstruction.PRINT_ICON.name)
    }

}

enum class EMOTES {
    exclamation, question, heart, awkward, zzz
}

enum class POSES {
    still, idle, walk, pain, fight, crouched, depressed, victory, dead, collapse, stand_up
}

enum class EVENT_DIRS {
    NW, SE, SW, NE
}

enum class PORTRAIT_ORIENTATION {
    left, middle, right
}

enum class PORTRAIT_CHARS {
    MC, Maki, Mark, Nanjo, Yukino, Ayase, Brown, Elly, Reiji, Maki_sick, Maki_happy, Mai, Aki, Maki_masked, Maki_masked_SQQ, Setsuko,
    MC_alt1, Saeko, Saeko_Ice, Saeko_young, Nurse, Hanya, Ooishi, Yamaoka, Yosuke, Chisato, Chisato_corruptv1, Chisato_corruptv2, Chisato_corruptv3, MC_alt2, Tsutomu, Yuko,
    Yuko_smug, Toro, MC_alt3, Tadashi, Tamaki, Katsue_rich, Katsue_poor, Kandori, Kandori_mask, Takeda, MC_alt4, Nicholai, Tomomi, Tomomi_corrupt, MC_alt5, Kumi,
    Michiko, Yuriko, MC_alt6, MC_alt7, Night_Queen, Yin_Yang_clerk, Rosa_clerk, Weapon_clerk, Armor_clerk, Pharma_clerk, MC_alt8, Sweets_clerk, Turunkhamen, Club_coin_clerk, Diner_clerk, Doctor,
    Igor, Trish, Khamenturun, MC_alt9, Master, Club_yen_clurk, glitch
}

enum class MONEY_DIRECTION {
    ADD, REMOVE
}

val SCREEN_EFFECTS: Array<String> = arrayOf(
    "stop current effect (only works for earthquake)",
    "fades out of black (quick)",
    "fades out of black (mid speed)",
    "fades out of black (slow)",
    "fades into black (quick)",
    "fades into black (mid speed)",
    "fades into black (slow)",
    "smaller screen shake (earthquake)",
    "medium screen shake (earthquake)",
    "bigger screen shake (earthquake)",
    "fades out of white (quick)",
    "fades out of white (mid speed)",
    "fades out of white (slow)",
    "fades into white (quick)",
    "fades into white (mid speed)",
    "fades into white (slow)",
    "screen flashes black (mid speed)",
    "screen flashes black (quick)"
)

val BATTLES: Array<String> = arrayOf(
    "first awakening",
    "Elly's awakening",
    "Maki's awakening",
    "Brown's awakening",
    "Ayase's awakening",
    "Takeda battle",
    "Reiji's awakening",  // 06
    "Reiji's awakneing variation?",  // 07
    "Tesso battle",  // 08
    "Yog Sothoth Jr battle",  // 09
    "Harem Queen battle",  // 0A
    "Harem Queen variation battle ?",  // 0B
    "Mr. Bear battle",  // 0C
    "Saurva battle",  // 0D
    "Hariti battle",  // 0E
    "Kandori battle",  // 0F
    "Pandora fase 1",  // 10
    "Akuma monster battle",
    "Akuma monster battle variation?",
    "Hypnos 1 battle",
    "Hypnos 2 battle",
    "Hypnos 3 battle",
    "Hypnos 4 battle",
    "Nemesis 1 battle",
    "Nemesis 2 battle",
    "Nemesis 3 battle",
    "Nemesis 4 battle",
    "Nemesis 5 battle",
    "Nemesis 6 battle",
    "Thanatos 1 battle",
    "Thanatos 2 battle",
    "Snow Queen mask battle",  // 1F
    "Queen Asura battle",  // 20
    "bad ending last battle",  // 21
    "Pandora fase 2" // 22
)

val OPTIONS: List<List<String>> = listOf(
    listOf("Yes", "No"),  // 0000
    listOf("Sure.", "No way."),  // 0001
    listOf("Yeah,", "No, I don't"),  // 0002
    listOf("Start game", "Check coins", "See explanations", "Stop playing"),  // 0003
    listOf("No", "Yes"),  // 0004
    listOf("Game rules", "Controls", "Winning hands", "Go back"),  // 0005
    listOf("Game rules", "Controls", "Tips", "Go back"),  // 0006
    listOf("Let them join", "Don't let them join"),  // 0007
    listOf("Help her", "Don't help her"),  // 0008
    listOf("Don't leave", "Leave"),  // 0009
    listOf("Don't open it", "Open it"),  // 000A
    listOf("Don't listen", "Listen"),  // 000B
    listOf("Create Persona", "Take on Persona", "Talk", "Leave"),  // 000C
    listOf("Stop hiding.", "Yes, it's safe here.", "That's true, but...", "I don't really know."),  // 000D
    listOf("For myself.", "Just 'cause.", "For everyone's sake.", "That's how it went."),  // 000E
    listOf("I don't really know.", "To find my reason."),  // 000F
    listOf("Press the red button", "Press the blue button."),  // 0010
    listOf("Heal us, please.", "Just dropping by."),  // 0011
    listOf("Fight Hariti", "Lower your weapons"),  // 0012
    listOf("Don't hide like that!", "Maybe you are..."),  // 0013
    listOf("Stay here", "Go to 8F", "Go to 4F", "Go to 1F"),  // 0014
    listOf("Manual Fusion", "Guided Fusion", "View cards", "Cancel"),  // 0015
    listOf("The Queen's is better.", "Maki's is better."),  // 0016
    listOf("Beginner tips", "Regular tips", "About Personas", "Advanced tips"),  // 0017
    listOf("Start game", "Check cards", "See explanations", "Cancel"),  // 0018
    listOf("Bet on Mark", "Bet on Brown"),  // 0019
    listOf("That's the plan.", "Not really."),  // 001A
    listOf("Yeah.", "That's"),  // 001B (HUH?)
    listOf("Yeah, I do.", "No, no one."),  // 001C
    listOf("A few.", "Not a one."),  // 001D
    listOf("I like the old way.", "I like the new way."),  // 001E
    listOf("Sure, put me down.", "Don't you dare."),  // 001F
    listOf("Buy", "Sell", "Equip", "Cancel"),  // 0020
    listOf("Trade for items", "Trade for incense", "Equip", "Cancel"),  // 0021
    listOf("Normal", "Beginner", "Expert"),  // 0022
    listOf("Yes, it was.", "On second thought..."),  // 0023 (LAST ONE)

)

fun getShopDescription(value: Short): String {
    return when (value.toInt()) {
        0x1 -> "Yin & Yang, Maki's world v1"
        0x2 -> "Weapon shop, Aki's side v1"
        0x3 -> "Weapon shop, Aki's side v2"
        0x4 -> "Yin & Yang, Maki's world v2"
        0x6 -> "Rosa, Mai's side v1"
        0x7 -> "Armor shop, Aki's side v1"
        0x8 -> "Armor shop, Aki's side v2"
        0x9 -> "Rosa, Mai's side v2"
        0xA -> "Tadashi, Mai's side v1"
        0xB -> "Tadashi, Aki's side v1"
        0xC -> "Turunkhamen, Mai's side v1"
        0xD -> "Turunkhamen, Aki's side v1"
        0xe -> "Casino, money to coin"
        0xf -> "Casino, coin to item, Mai's side v1"
        0x10 -> "Yin & Yang real world"
        0x11 -> "Sennen"
        0x12 -> "Tadashi"
        0x14 -> "Casino, coin to item, Sun Mall v1?"
        0x16 -> "Casino, coin to item, unknown"
        0x18 -> "Casino, coin to item, Sun Mall v2?"
        0x1a -> "Casino, coin to item, Joy Street"
        0x1c -> "Casino, coin to item, Mai's side v2"
        0x1e -> "Casino, coin to item, Aki's side v1"
        0x1f -> "Khamenturun, Mai's side v1"
        0x20 -> "Velvet Room, talk menu"
        0x21 -> "Velvet Room, manual fusion menu"
        0x22 -> "Velvet Room, guided fusion menu"
        0x23 -> "Velvet Room, view cards menu"
        0x24 -> "Velvet Room, leave?!??!!"
        0x25 -> "Casino, poker help menu"
        0x26 -> "Casino, blackjack help menu"
        0x27 -> "Casino, slot machine help menu"
        0x28 -> "Casino, code breaker help menu"
        0x29 -> "Casino, dice game help menu"
        0x31 -> "Tadashi, Mai's side v2"
        0x32 -> "Turunkhamen, Mai's side v2"
        else -> "unknown"
    }
}

fun getSFXDescription(value: Short): String {
    return when (value.toInt()) {
        0x0, 0x1 -> "woosh"
        0x3 -> "quick lightning"
        0x4 -> "heal/reflect sound?"
        0x5 -> "holy voice"
        0x8 -> "something fell, a rock or smt"
        0x9 -> "something falling intensely? Sounds kinda like lightning"
        0xA -> "little noises followed by weird woosh"
        0xB -> "water flowing, a lil bubbling"
        0xC, 0xD -> "bird, followed by pecking"
        0xE -> "open door"
        0xF -> "unlock door"
        0x10 -> "open gate"
        0x11 -> "creaking"
        0x12 -> "some other opening sound, gate or something? Resident Evil like"
        0x13 -> "deep lightning, or something falling"
        0x14 -> "same as before but more intense, metallic scrapping as well"
        0x15 -> "quick opening of metal door"
        0x16 -> "weird woosh"
        0x17 -> "quick woosh"
        0x18 -> "machine hum"
        0x19 -> "heavy machine (moving?)"
        0x1A -> "quiet unlock"
        0x1B -> "ominous sound, like a debuff being cast (imagination)"
        0x1c -> "page turn?"
        0x1D -> "curtain pull?"
        0x1E -> "glass shatter"
        0x1F -> "ray gun"
        0x20 -> "lightning_1"
        0x21 -> "small crunch"
        0x22 -> "ghostly sound, deep"
        0x23 -> "mechanical door closing? or elevator stopping"
        0x24 -> "BAM (closed window quickly)"
        0x25 -> "light woosh, like page turning"
        0x26 -> "open heavier door"
        0x27 -> "heartbeat"
        0x28 -> "punch"
        0x29 -> "small ding"
        0x2A -> "window break"
        0x2B -> "minecraft cave noise"
        0x2C -> "Ice Queen music box"
        0x2D -> "teleporter sound"
        0x2E -> "healing like holy sound, revive vibes"
        0x2F -> "weird woosh, comes and goes"
        0x30 -> "electronic woosh"
        0x31 -> "deep sounding lightning?"
        0x32 -> "big metal gate quick open"
        0x4D -> "lightning_2"

        else -> "nothing"

    }
}

private fun removeCommentAndSpaces(line: String): String {
    if (line.isEmpty()) return line

    // checking if the whole line is a comment by first checking if it starts with the comment symbol
    if (line.substring(0, TABLE_COMMENT_SYMBOL.length).compareTo(TABLE_COMMENT_SYMBOL) == 0) {
        return ""
    }

    var index = 0

    index = line.indexOf(TABLE_COMMENT_SYMBOL[0], index)
    while (index > 0) {
        if (line.substring(index, index + TABLE_COMMENT_SYMBOL.length)
                .compareTo(TABLE_COMMENT_SYMBOL) == 0
        ) {
            break
        } else {
            index += TABLE_COMMENT_SYMBOL.length
        }
        index = line.indexOf(TABLE_COMMENT_SYMBOL[0], index)
    }

    if (index == -1) {
        index = line.length
    }
    return line.substring(0, index)
}