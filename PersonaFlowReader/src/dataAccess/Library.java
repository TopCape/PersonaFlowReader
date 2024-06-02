package dataAccess;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;


public class Library {

    private static final String RESET_COLOR = "\u001B[0m";
    private static final String BLUE_COLOR = "\u001B[34m";
    private static final String PINK_COLOR = "\u001B[35m";
    private static final String YELLOW_COLOR = "\u001B[33m";
    private static final String RED_COLOR = "\u001B[31m";

    public static final String SECTION_KEYWORD = "section";
    public static final String BGM_SECTION_KEYWORD = ".bgm";
    public static final String TALK_SECTION_KEYWORD = ".talk";
    public static final String TALK2_SECTION_KEYWORD = ".talk2";
    public static final String POS_SECTION_KEYWORD = ".positions";
    public static final String INTER_SECTION_KEYWORD = ".interactables";
    public static final String CODE_AREA_KEYWORD = ".code";
    public static final String TEXT_AREA_KEYWORD = ".text";
    public static final String ADDR_KEYWORD = "addr";
    public static final String SPACE_TAB_REGEX = "[ \t]";
    public static final String NOT_FORMATTED_ERR_TXT = "File is not formatted correctly";
    public static final String LABEL_TXT = "LABEL";
    public static final String LABEL_SEPARATOR = "_";
    public static final String UNKNOWN_INSTR_TEXT = "unknown";
    public static final byte CMD_START = (byte)0xFF;
    public static final String HEX_PREFIX = "0x";
    public static final String BASE_DIR = "./";

    public static final long STARTING_SONG_ADDRESS = 0x02;

    public static final long ADDRESS_WITH_FLOW_SCRIPT_POINTER = 0x64;
    public static final long ADDRESS_WITH_TEXT_TABLE_POINTER = 0x34;
    public static final long ADDRESS_WITH_POSITION_DATA_SIZE_POINTER = 0x38;
    public static final long ADDRESS_WITH_INTERACTABLE_DATA_SIZE_POINTER = 0x48;


    public static final int ADDRESS_OF_CHARACTER_DATA = 0x1F4;
    public static final byte CHARACTER_DATA_SIZE = 0x24;
    public static final int CHARACTER_DATA_NUM = 64;
    public static final byte CHARACTER_DATA_EVENT_ADDRESS_1_OFFSET = 0x4;
    public static final byte CHARACTER_DATA_EVENT_ADDRESS_2_OFFSET = 0xc; // offset starting from the end of the above event address value
    public static final byte CHARACTER_DATA_EVENT_ADDRESS_2_ABSOLUTE_OFFSET = 0x14;

    public static final int ADDRESS_OF_SECONDARY_CHARACTER_DATA = 0xAF4;
    public static final int SECONDARY_CHARACTER_DATA_NUM = 8;
    public static final byte SECONDARY_CHARACTER_DATA_SIZE = 0x1C;
    public static final byte SECONDARY_CHARACTER_DATA_EVENT_ADDRESS_2_OFFSET = 0x8; // offset starting from the end of the previous event address
    public static final byte SECONDARY_CHARACTER_DATA_EVENT_ADDRESS_2_ABSOLUTE_OFFSET = 0x10;

    public static final byte CHARACTER_NAME_BYTE = 0x1B;
    public static final String EMPTY_FILE_STRING = "EMPTY";

    public static final int MINUS_1_INT = 0xFFFFFFFF;

    public static final String COMMENT_SYMBOL = "//";
    public static final String TABLE_COMMENT_SYMBOL = "#";
    public static final String COMMENT_INDICATOR = "\1"; // used to indicate a line was fully commented

    public enum FlowInstruction {
        NOTHING,
        ret,
        jump,
        UNKNOWN_COMMAND_24,
        UNKNOWN_COMMAND_25,
        jump_if,
        UNKNOWN_COMMAND_27,
        battle,
        ld_world_map,
        UNKNOWN_COMMAND_2A,
        ld_file,
        ld_3d_map,
        play_MV,
        unk_cmd_2F,
        UNKNOWN_COMMAND_30,
        UNKNOWN_COMMAND_31,
        UNKNOWN_COMMAND_32,
        UNKNOWN_COMMAND_39,
        unk_cmd_3A,
        unk_cmd_3B,
        UNKNOWN_COMMAND_3C,
        UNKNOWN_COMMAND_3D,
        UNKNOWN_COMMAND_3E,
        UNKNOWN_COMMAND_3F,
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

    public enum TextInstruction {
        SHOW_OPTIONS, PLAYER_LAST_NAME, COIN_NUMBER, END, AWAITING_INPUT, LINE_BREAK, CONTINUE, WAIT, LEGACY_SET_COLOR, PLAYER_FIRST_NAME, PLAYER_NICKNAME, SET_COLOR, PRINT_ICON, PRINT_VALUE, CHARACTER_NAME
    }

    public final HashMap<Byte, FlowInstruction> FLOW_INSTRUCTIONS;
    public final HashMap<Short, TextInstruction> TEXT_INSTRUCTIONS;
    public final HashMap<Short, String> TEXT_CODES;
    public final HashMap<Short, Character> TEXT_COLORS;
    public final HashMap<Short, String> TEXT_COLORS_ANSI;

    public final HashMap<String, Byte> FLOW_INSTRUCTIONS_REVERSE;
    public final HashMap<String, Short> TEXT_INSTRUCTIONS_REVERSE;
    public final HashMap<String, Short> TEXT_CODES_REVERSE;
    public final HashMap<Character, Short> TEXT_COLORS_REVERSE;

    public final LinkedList<String> PARAMETERIZED_TEXT_INSTRUCTIONS;

    // hashmap with the number of parameters (in extra ints) per instruction
    public final HashMap<FlowInstruction, Byte> PARAM_NUM;

    private static Library instance = null;

    public static Library getInstance() {
        if (instance == null) {
            try {
                instance = new Library();
            } catch (IOException e) {
                System.out.println("NO TABLE FILE FOUND!!!!!!");
            }
        }
        return instance;
    }

    public Library() throws IOException {
        FLOW_INSTRUCTIONS = new HashMap<>();
        PARAM_NUM = new HashMap<>();
        TEXT_INSTRUCTIONS = new HashMap<>();
        TEXT_CODES = new HashMap<>();

        TEXT_COLORS = new HashMap<>();
        TEXT_COLORS_ANSI = new HashMap<>();
        PARAMETERIZED_TEXT_INSTRUCTIONS = new LinkedList<>();

        initLib();

        // creating reverse maps
        FLOW_INSTRUCTIONS_REVERSE = new HashMap<>();
        for (Map.Entry<Byte, FlowInstruction> entry : FLOW_INSTRUCTIONS.entrySet()) {
            FLOW_INSTRUCTIONS_REVERSE.put(entry.getValue().name(), entry.getKey());
        }

        HashMap<String, Short> tempMapE = new HashMap<>();
        for (Map.Entry<Short, TextInstruction> entry : TEXT_INSTRUCTIONS.entrySet()) {
            tempMapE.put(entry.getValue().name(), entry.getKey());
        }
        TEXT_INSTRUCTIONS_REVERSE = tempMapE;

        TEXT_CODES_REVERSE = new HashMap<>();
        for (Map.Entry<Short, String> entry : TEXT_CODES.entrySet()) {
            TEXT_CODES_REVERSE.put(entry.getValue(), entry.getKey());
        }

        TEXT_COLORS_REVERSE = new HashMap<>();
        for (Map.Entry<Short, Character> entry : TEXT_COLORS.entrySet()) {
            TEXT_COLORS_REVERSE.put(entry.getValue(), entry.getKey());
        }
    }

    private void initLib() throws IOException {
        FLOW_INSTRUCTIONS.put((byte) 0x00, FlowInstruction.NOTHING);
        FLOW_INSTRUCTIONS.put((byte) 0x21, FlowInstruction.ret);
        FLOW_INSTRUCTIONS.put((byte) 0x22, FlowInstruction.jump);
        FLOW_INSTRUCTIONS.put((byte) 0x24, FlowInstruction.UNKNOWN_COMMAND_24);
        FLOW_INSTRUCTIONS.put((byte) 0x25, FlowInstruction.UNKNOWN_COMMAND_25);
        FLOW_INSTRUCTIONS.put((byte) 0x26, FlowInstruction.jump_if);
        FLOW_INSTRUCTIONS.put((byte) 0x27, FlowInstruction.UNKNOWN_COMMAND_27);
        FLOW_INSTRUCTIONS.put((byte) 0x28, FlowInstruction.battle);
        FLOW_INSTRUCTIONS.put((byte) 0x29, FlowInstruction.ld_world_map);
        FLOW_INSTRUCTIONS.put((byte) 0x2A, FlowInstruction.UNKNOWN_COMMAND_2A);
        FLOW_INSTRUCTIONS.put((byte) 0x2B, FlowInstruction.ld_file);
        FLOW_INSTRUCTIONS.put((byte) 0x2C, FlowInstruction.ld_3d_map);
        FLOW_INSTRUCTIONS.put((byte) 0x2D, FlowInstruction.play_MV);
        FLOW_INSTRUCTIONS.put((byte) 0x2F, FlowInstruction.unk_cmd_2F);
        FLOW_INSTRUCTIONS.put((byte) 0x30, FlowInstruction.UNKNOWN_COMMAND_30);
        FLOW_INSTRUCTIONS.put((byte) 0x31, FlowInstruction.UNKNOWN_COMMAND_31);
        FLOW_INSTRUCTIONS.put((byte) 0x32, FlowInstruction.UNKNOWN_COMMAND_32);
        FLOW_INSTRUCTIONS.put((byte) 0x39, FlowInstruction.UNKNOWN_COMMAND_39);
        FLOW_INSTRUCTIONS.put((byte) 0x3A, FlowInstruction.unk_cmd_3A);
        FLOW_INSTRUCTIONS.put((byte) 0x3B, FlowInstruction.unk_cmd_3B);
        FLOW_INSTRUCTIONS.put((byte) 0x3C, FlowInstruction.UNKNOWN_COMMAND_3C);
        FLOW_INSTRUCTIONS.put((byte) 0x3D, FlowInstruction.UNKNOWN_COMMAND_3D);
        FLOW_INSTRUCTIONS.put((byte) 0x3E, FlowInstruction.UNKNOWN_COMMAND_3E);
        FLOW_INSTRUCTIONS.put((byte) 0x3F, FlowInstruction.UNKNOWN_COMMAND_3F);
        FLOW_INSTRUCTIONS.put((byte) 0x44, FlowInstruction.unk_cmd_44);
        FLOW_INSTRUCTIONS.put((byte) 0x45, FlowInstruction.unk_cmd_45);
        FLOW_INSTRUCTIONS.put((byte) 0x47, FlowInstruction.unk_cmd_47);
        FLOW_INSTRUCTIONS.put((byte) 0x4B, FlowInstruction.open_save_menu);
        FLOW_INSTRUCTIONS.put((byte) 0x4C, FlowInstruction.UNKNOWN_COMMAND_4C);
        FLOW_INSTRUCTIONS.put((byte) 0x4d, FlowInstruction.wait);
        FLOW_INSTRUCTIONS.put((byte) 0x4f, FlowInstruction.UNKNOWN_COMMAND_4F);
        FLOW_INSTRUCTIONS.put((byte) 0x52, FlowInstruction.UNKNOWN_COMMAND_52);
        FLOW_INSTRUCTIONS.put((byte) 0x53, FlowInstruction.UNKNOWN_COMMAND_53);
        FLOW_INSTRUCTIONS.put((byte) 0x54, FlowInstruction.player_option);
        FLOW_INSTRUCTIONS.put((byte) 0x55, FlowInstruction.ld_text);
        FLOW_INSTRUCTIONS.put((byte) 0x56, FlowInstruction.UNKNOWN_COMMAND_56);
        FLOW_INSTRUCTIONS.put((byte) 0x57, FlowInstruction.UNKNOWN_COMMAND_57);
        FLOW_INSTRUCTIONS.put((byte) 0x58, FlowInstruction.unk_cmd_58);
        FLOW_INSTRUCTIONS.put((byte) 0x59, FlowInstruction.unk_cmd_59);
        FLOW_INSTRUCTIONS.put((byte) 0x5A, FlowInstruction.unk_cmd_5A);
        FLOW_INSTRUCTIONS.put((byte) 0x60, FlowInstruction.open_dialog);
        FLOW_INSTRUCTIONS.put((byte) 0x61, FlowInstruction.close_dialog);
        FLOW_INSTRUCTIONS.put((byte) 0x64, FlowInstruction.pose);
        FLOW_INSTRUCTIONS.put((byte) 0x65, FlowInstruction.fx);
        FLOW_INSTRUCTIONS.put((byte) 0x66, FlowInstruction.clr_char);
        FLOW_INSTRUCTIONS.put((byte) 0x67, FlowInstruction.ld_portrait);
        FLOW_INSTRUCTIONS.put((byte) 0x68, FlowInstruction.close_portrait);
        FLOW_INSTRUCTIONS.put((byte) 0x69, FlowInstruction.emote);
        FLOW_INSTRUCTIONS.put((byte) 0x6C, FlowInstruction.screen_fx);
        FLOW_INSTRUCTIONS.put((byte) 0x6D, FlowInstruction.UNKNOWN_COMMAND_6D);
        FLOW_INSTRUCTIONS.put((byte) 0x6E, FlowInstruction.plan_char_mov);
        FLOW_INSTRUCTIONS.put((byte) 0x73, FlowInstruction.UNKNOWN_COMMAND_73);
        FLOW_INSTRUCTIONS.put((byte) 0x74, FlowInstruction.UNKNOWN_COMMAND_74);
        FLOW_INSTRUCTIONS.put((byte) 0x76, FlowInstruction.fade_char);
        FLOW_INSTRUCTIONS.put((byte) 0x77, FlowInstruction.UNKNOWN_COMMAND_77);
        FLOW_INSTRUCTIONS.put((byte) 0x78, FlowInstruction.follow_char);
        FLOW_INSTRUCTIONS.put((byte) 0x79, FlowInstruction.UNKNOWN_COMMAND_79);
        FLOW_INSTRUCTIONS.put((byte) 0x7a, FlowInstruction.clr_emote);
        FLOW_INSTRUCTIONS.put((byte) 0x7B, FlowInstruction.do_planned_moves);
        FLOW_INSTRUCTIONS.put((byte) 0x7c, FlowInstruction.tp_char);
        FLOW_INSTRUCTIONS.put((byte) 0x80, FlowInstruction.play_song);
        FLOW_INSTRUCTIONS.put((byte) 0x81, FlowInstruction.play_sfx);
        FLOW_INSTRUCTIONS.put((byte) 0x87, FlowInstruction.unk_cmd_87);
        FLOW_INSTRUCTIONS.put((byte) 0x88, FlowInstruction.UNKNOWN_COMMAND_88);
        FLOW_INSTRUCTIONS.put((byte) 0x89, FlowInstruction.UNKNOWN_COMMAND_89);
        FLOW_INSTRUCTIONS.put((byte) 0x8A, FlowInstruction.UNKNOWN_COMMAND_8A);


        PARAM_NUM.put(FlowInstruction.NOTHING, (byte)0);
        PARAM_NUM.put(FlowInstruction.ret, (byte)0);
        PARAM_NUM.put(FlowInstruction.jump, (byte)1);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_24, (byte)0);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_25, (byte)0);
        PARAM_NUM.put(FlowInstruction.jump_if, (byte)1);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_27, (byte)0);
        PARAM_NUM.put(FlowInstruction.battle, (byte)0);
        PARAM_NUM.put(FlowInstruction.ld_world_map, (byte)1);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_2A, (byte)0);
        PARAM_NUM.put(FlowInstruction.ld_file, (byte)1);
        PARAM_NUM.put(FlowInstruction.ld_3d_map, (byte)1);
        PARAM_NUM.put(FlowInstruction.play_MV, (byte)0);
        PARAM_NUM.put(FlowInstruction.unk_cmd_2F, (byte)1);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_30, (byte)1);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_31, (byte)0);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_32, (byte)0);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_39, (byte)0);
        PARAM_NUM.put(FlowInstruction.unk_cmd_3A, (byte)1);
        PARAM_NUM.put(FlowInstruction.unk_cmd_3B, (byte)1);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_3C, (byte)1);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_3D, (byte)1);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_3E, (byte)2);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_3F, (byte)1);
        PARAM_NUM.put(FlowInstruction.unk_cmd_44, (byte)1);
        PARAM_NUM.put(FlowInstruction.unk_cmd_45, (byte)1);
        PARAM_NUM.put(FlowInstruction.unk_cmd_47, (byte)1);
        PARAM_NUM.put(FlowInstruction.open_save_menu, (byte)0);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_4C, (byte)0);
        PARAM_NUM.put(FlowInstruction.wait, (byte)0);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_4F, (byte)0);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_52, (byte)0);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_53, (byte)0);
        PARAM_NUM.put(FlowInstruction.player_option, (byte)1);
        PARAM_NUM.put(FlowInstruction.ld_text, (byte)1);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_56, (byte)0);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_57, (byte)0);
        PARAM_NUM.put(FlowInstruction.unk_cmd_58, (byte)1);
        PARAM_NUM.put(FlowInstruction.unk_cmd_59, (byte)1);
        PARAM_NUM.put(FlowInstruction.unk_cmd_5A, (byte)1);
        PARAM_NUM.put(FlowInstruction.open_dialog, (byte)0);
        PARAM_NUM.put(FlowInstruction.close_dialog, (byte)0);
        PARAM_NUM.put(FlowInstruction.pose, (byte)2);
        PARAM_NUM.put(FlowInstruction.fx, (byte)2);
        PARAM_NUM.put(FlowInstruction.clr_char, (byte)0);
        PARAM_NUM.put(FlowInstruction.ld_portrait, (byte)0);
        PARAM_NUM.put(FlowInstruction.close_portrait, (byte)0);
        PARAM_NUM.put(FlowInstruction.emote, (byte)0);
        PARAM_NUM.put(FlowInstruction.screen_fx, (byte)0);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_6D, (byte)0);
        PARAM_NUM.put(FlowInstruction.plan_char_mov, (byte)1);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_73, (byte)0);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_74, (byte)0);
        PARAM_NUM.put(FlowInstruction.fade_char, (byte)0);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_77, (byte)0);
        PARAM_NUM.put(FlowInstruction.follow_char, (byte)0);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_79, (byte)1);
        PARAM_NUM.put(FlowInstruction.clr_emote, (byte)0);
        PARAM_NUM.put(FlowInstruction.do_planned_moves, (byte)0);
        PARAM_NUM.put(FlowInstruction.tp_char, (byte)1);
        PARAM_NUM.put(FlowInstruction.play_song, (byte)0);
        PARAM_NUM.put(FlowInstruction.play_sfx, (byte)0);
        PARAM_NUM.put(FlowInstruction.unk_cmd_87, (byte)1);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_88, (byte)0);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_89, (byte)0);
        PARAM_NUM.put(FlowInstruction.UNKNOWN_COMMAND_8A, (byte)0);

        TEXT_INSTRUCTIONS.put((short)0xff01, TextInstruction.END);
        TEXT_INSTRUCTIONS.put((short)0xff02, TextInstruction.AWAITING_INPUT);
        TEXT_INSTRUCTIONS.put((short)0xff03, TextInstruction.LINE_BREAK);
        TEXT_INSTRUCTIONS.put((short)0xff04, TextInstruction.CONTINUE);
        TEXT_INSTRUCTIONS.put((short)0xff05, TextInstruction.WAIT);
        TEXT_INSTRUCTIONS.put((short)0xff06, TextInstruction.LEGACY_SET_COLOR); // might be legacy "SET_COLOR"
        TEXT_INSTRUCTIONS.put((short)0xff07, TextInstruction.PLAYER_FIRST_NAME);
        TEXT_INSTRUCTIONS.put((short)0xff08, TextInstruction.PLAYER_NICKNAME);
        TEXT_INSTRUCTIONS.put((short)0xff0E, TextInstruction.SHOW_OPTIONS);
        TEXT_INSTRUCTIONS.put((short)0xff0F, TextInstruction.PLAYER_LAST_NAME);
        TEXT_INSTRUCTIONS.put((short)0xff11, TextInstruction.COIN_NUMBER);
        TEXT_INSTRUCTIONS.put((short)0xff18, TextInstruction.SET_COLOR);
        TEXT_INSTRUCTIONS.put((short)0xff19, TextInstruction.PRINT_ICON);
        TEXT_INSTRUCTIONS.put((short)0xff1a, TextInstruction.PRINT_VALUE);
        TEXT_INSTRUCTIONS.put((short)(0xff00 | CHARACTER_NAME_BYTE), TextInstruction.CHARACTER_NAME);

        // read table file
        // It was too large to initialize in here, so it is in a file
        BufferedReader reader = new BufferedReader(new FileReader("./table/p1p.tbl"));
        String line;
        while ((line = reader.readLine()) != null) {
            line = removeCommentAndSpaces(line);
            if (line.isEmpty()) continue;

            String[] split = line.split("=");
            short code = (short) Integer.parseInt(split[0], 16);

            String character;
            if (split.length < 2) {
                character = "=";
            } else {
                character = split[1];
            }

            if (!character.isBlank()) {
                character = character.trim();
            }
            TEXT_CODES.put(code, character);
        }

        TEXT_COLORS.put((short) 0x0000, '_');
        TEXT_COLORS.put((short) 0x0001, 'W');
        TEXT_COLORS.put((short) 0x0002, 'B');
        TEXT_COLORS.put((short) 0x0003, 'P');
        TEXT_COLORS.put((short) 0x0004, 'G');
        TEXT_COLORS.put((short) 0x0005, 'Y');
        // 0006 also makes text blue, but it isn't used in P1 (at least in E0.BIN)
        TEXT_COLORS.put((short) 0x0007, 'R');

        TEXT_COLORS_ANSI.put((short) 0x0000, RESET_COLOR);
        TEXT_COLORS_ANSI.put((short) 0x0002, BLUE_COLOR);
        TEXT_COLORS_ANSI.put((short) 0x0003, PINK_COLOR);
        TEXT_COLORS_ANSI.put((short) 0x0005, YELLOW_COLOR);
        TEXT_COLORS_ANSI.put((short) 0x0007, RED_COLOR);

        PARAMETERIZED_TEXT_INSTRUCTIONS.add(TextInstruction.WAIT.name());
        PARAMETERIZED_TEXT_INSTRUCTIONS.add(TextInstruction.SET_COLOR.name());
        PARAMETERIZED_TEXT_INSTRUCTIONS.add(TextInstruction.PRINT_ICON.name());

    }


    public enum EMOTES {
            exclamation, question, heart, awkward, zzz
    }

    public enum POSES {
        still, idle, walk, pain, fight, crouched, depressed, victory, dead, collapse, stand_up
    }

    public enum EVENT_DIRS {
            NW, SE, SW, NE
    }

    public enum PORTRAIT_ORIENTATION {
        left, middle, right
    }

    public enum PORTRAIT_CHARS {
        MC, Maki, Mark, Nanjo, Yukino, Ayase, Brown, Elly, Reiji, Maki_sick, Maki_happy, Mai, Aki, Maki_masked, Maki_masked_SQQ, Setsuko,
        MC_alt1, Saeko, Saeko_Ice, Saeko_young, Nurse, Hanya, Ooishi, Yamaoka, Yosuke, Chisato, Chisato_corruptv1, Chisato_corruptv2, Chisato_corruptv3, MC_alt2, Tsutomu, Yuko,
        Yuko_smug, Toro, MC_alt3, Tadashi, Tamaki, Katsue_rich, Katsue_poor, Kandori, Kandori_mask, Takeda, MC_alt4, Nicholai, Tomomi, Tomomi_corrupt, MC_alt5, Kumi,
        Michiko, Yuriko, MC_alt6, MC_alt7, Night_Queen, Yin_Yang_clerk, Rosa_clerk, Weapon_clerk, Armor_clerk, Pharma_clerk, MC_alt8, Sweets_clerk, Turunkhamen, Club_coin_clerk, Diner_clerk, Doctor,
        Igor, Trish, Khamenturun, MC_alt9, Master, Club_yen_clurk, glitch
    }

    public static final String[] SCREEN_EFFECTS = {
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
    };

    public static final String[] BATTLES = {
            "first awakening",
            "Elly's awakening",
            "Maki's awakening",
            "Brown's awakening",
            "Ayase's awakening",
            "Takeda battle",
            "Reiji's awakening", // 06
            "Reiji's awakneing variation?", // 07
            "Tesso battle", // 08
            "Yog Sothoth Jr battle", // 09
            "Harem Queen battle", // 0A
            "Harem Queen variation battle ?", // 0B
            "Mr. Bear battle", // 0C
            "Saurva battle", // 0D
            "Hariti battle", // 0E
            "Kandori battle", // 0F
            "Pandora fase 1", // 10
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
            "Snow Queen mask battle", // 1F
            "Queen Asura battle", // 20
            "bad ending last battle", // 21
            "Pandora fase 2" // 22
    };

    public static final String[][] OPTIONS = {
            {"Yes", "No"}, // 0000
            {"Sure.", "No way."}, // 0001
            {"Yeah,", "No, I don't"}, // 0002
            {"Start game", "Check coins", "See explanations", "Stop playing"}, // 0003
            {"No", "Yes"}, // 0004
            {"Game rules", "Controls", "Winning hands", "Go back"}, // 0005
            {"Game rules", "Controls", "Tips", "Go back"}, // 0006
            {"Let them join", "Don't let them join"}, // 0007
            {"Help her", "Don't help her"}, // 0008
            {"Don't leave", "Leave"}, // 0009
            {"Don't open it", "Open it"}, // 000A
            {"Don't listen", "Listen"}, // 000B
            {"Create Persona", "Take on Persona", "Talk", "Leave"}, // 000C
            {"Stop hiding.", "Yes, it's safe here.", "That's true, but...", "I don't really know."}, // 000D
            {"For myself.", "Just 'cause.", "For everyone's sake.", "That's how it went."}, // 000E
            {"I don't really know.", "To find my reason."}, // 000F
            {"Press the red button", "Press the blue button."}, // 0010
            {"Heal us, please.", "Just dropping by."}, // 0011
            {"Fight Hariti", "Lower your weapons"}, // 0012
            {"Don't hide like that!", "Maybe you are..."}, // 0013
            {"Stay here", "Go to 8F", "Go to 4F", "Go to 1F"}, // 0014
            {"Manual Fusion", "Guided Fusion", "View cards", "Cancel"}, // 0015
            {"The Queen's is better.", "Maki's is better."}, // 0016
            {"Beginner tips", "Regular tips", "About Personas", "Advanced tips"}, // 0017
            {"Start game", "Check cards", "See explanations", "Cancel"}, // 0018
            {"Bet on Mark", "Bet on Brown"}, // 0019
            {"That's the plan.", "Not really."}, // 001A
            {"Yeah.", "That's"}, // 001B (HUH?)
            {"Yeah, I do.", "No, no one."}, // 001C
            {"A few.", "Not a one."}, // 001D
            {"I like the old way.", "I like the new way."}, // 001E
            {"Sure, put me down.", "Don't you dare."}, // 001F
            {"Buy", "Sell", "Equip", "Cancel"}, // 0020
            {"Trade for items", "Trade for incense", "Equip", "Cancel"}, // 0021
            {"Normal", "Beginner", "Expert"}, // 0022
            {"Yes, it was.", "On second thought..."}, // 0023 (LAST ONE)

    };

    public static String getSFXDescription(short val) {
        switch (val) {
            case 0x0:
            case 0x1:
                return "woosh";
            case 0x3:
                return "quick lightning";
            case 0x4:
                return "heal/reflect sound?";
            case 0x5:
                return "holy voice";
            case 0x8:
                return "something fell, a rock or smt";
            case 0x9:
                return "something falling intensely? Sounds kinda like lightning";
            case 0xA:
                return "little noises followed by weird woosh";
            case 0xB:
                return "water flowing, a lil bubbling";
            case 0xC:
            case 0xD:
                return "bird, followed by pecking";
            case 0xE:
                return "open door";
            case 0xF:
                return "unlock door";
            case 0x10:
                return "open gate";
            case 0x11:
                return "creaking";
            case 0x12:
                return "some other opening sound, gate or something? Resident Evil like";
            case 0x13:
                return "deep lightning, or something falling";
            case 0x14:
                return "same as before but more intense, metallic scrapping as well";
            case 0x15:
                return "quick opening of metal door";
            case 0x16:
                return "weird woosh";
            case 0x17:
                return "quick woosh";
            case 0x18:
                return "machine hum";
            case 0x19:
                return "heavy machine (moving?)";
            case 0x1A:
                return "quiet unlock";
            case 0x1B:
                return "ominous sound, like a debuff being cast (imagination)";
            case 0x1c:
                return "page turn?";
            case 0x1D:
                return "curtain pull?";
            case 0x1E:
                return "glass shatter";
            case 0x1F:
                return "ray gun";
            case 0x20:
                return "lightning_1";
            case 0x21:
                return "small crunch";
            case 0x22:
                return "ghostly sound, deep";
            case 0x23:
                return "mechanical door closing? or elevator stopping";
            case 0x24:
                return "BAM (closed window quickly)";
            case 0x25:
                return "light woosh, like page turning";
            case 0x26:
                return "open heavier door";
            case 0x27:
                return "heartbeat";
            case 0x28:
                return "punch";
            case 0x29:
                return "small ding";
            case 0x2A:
                return "window break";
            case 0x2B:
                return "minecraft cave noise";
            case 0x2C:
                return "Ice Queen music box";
            case 0x2D:
                return "teleporter sound";
            case 0x2E:
                return "healing like holy sound, revive vibes";
            case 0x2F:
                return "weird woosh, comes and goes";
            case 0x30:
                return "electronic woosh";
            case 0x31:
                return "deep sounding lightning?";
            case 0x32:
                return "big metal gate quick open";
            case 0x4D: // checked in 0x088DEA60
                return "lightning_2";

                default:
                    return "nothing";

        }
    }

    private static String removeCommentAndSpaces(String line) {
        if (line.isEmpty()) return line;

        // checking if the whole line is a comment by first checking if it starts with the comment symbol
        if(line.substring(0, Library.TABLE_COMMENT_SYMBOL.length()).compareTo(Library.TABLE_COMMENT_SYMBOL) == 0) {
            return "";
        }

        int index = 0;

        index = line.indexOf(Library.TABLE_COMMENT_SYMBOL.charAt(0), index);
        while (index > 0) {
            if (line.substring(index, index + Library.TABLE_COMMENT_SYMBOL.length()).compareTo(Library.TABLE_COMMENT_SYMBOL) == 0) {
                break;
            } else {
                index += Library.TABLE_COMMENT_SYMBOL.length();
            }
            index = line.indexOf(Library.TABLE_COMMENT_SYMBOL.charAt(0), index);
        }

        if (index == -1) {
            index = line.length();
        }
        return line.substring(0, index);
    }

}
