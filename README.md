# Persona Flow Reader

This tool allows the "decoding" and "encoding" of Persona 1 event files. This doesn't actually do the whole file, but only the section that are strictly related to the flow script inside the files. This includes:
* A list of characters in the event with the pointers to the flow script which are triggered by talking to them
* A list of positions in the scene that trigger flow script code with pointers to them
   * Some of these are triggered by just standing in the saved coordinates
   * Some of these are triggered by interacting with the object in the saved coordinates
* The flow script itself
* The text it contains

Most of the file is still unknown (to me), so it is copied from the base file and edited to fit new code that a user may have written.

## Requirements

This program assumes you have an extracted version of Persona 1 for the PSP OR an extracted version of Persona 1 with the Persona PSX Music Restoration mod.

### Original Persona 1 for PSP
In addition to these files, you should apply a patch to your EBOOT depending on the version of the game:
- **US**: https://gamebanana.com/mods/383097
- **JP**: https://gamebanana.com/mods/382682
- **PAL**: https://gamebanana.com/mods/399278

### PSX Music Restoration Mod
If you have this version of the game, you already have a valid EBOOT.

## How to use

### Setup
Paste EX.BIN files into the OG folder, where X is a number from 0 to 4. These are event files that are present in PSP_GAME/USRDIR/pack/, in Persona 1's files. Also paste the DECRYPTED EBOOT.BIN of the persona game into the same folder. The EBOOT.BIN contains data that must be updated if the EX.BIN file's size changes.

To run the program, you must have Java installed. If you do, simply execute "run.bat" and it will start the program in a command line window.

There are many options when you start the program. Enter the corresponding number and it will ask you for the specifics of each option.

At any time during the process, entering a "-1" will exit the current menu.

**IMPORTANT**: The program depends on the names of the files and the specific paths within the program's directory. **DO NOT CHANGE THEM**. The only exception is for operations that also accept directories, as indicated in the instructions as the parameters are being asked.

### 0: Extract Event Files

Each of these files have many event files inside of them, relating to types of event scenes in the game. E0 has SEBEC related event files and E1 has SQQ related event files, for instance.

When this option is used, the internal event files are all extracted to a folder called "extracted" in the same directory as the program and with the same name as the extracted event file. All internal files will be labeled with the name of the extracted event file + a suffix number based on their order in the file (ex. for E0.BIN, the first file inside it will be extracted to E0/E0_000.EVS). The .EVS extension is used for the extracted event files and stands for EVent Script. This operation requires the user to write the name of the original EX.BIN file when prompted.


### 1: Decode Event File

This option outputs a .DEC file and a .TXT file with the same name as the input file (ex. E0_000.EVS -> E0_000.DEC and E0_000.TXT). The outputed files are in a text format that are readable by any text editor software. The operation requests the name of the file to decode (including the extension, so E0_000.EVS for example) or a folder path (ending in "/") with the files to decode inside.

If you are only interested in editing dialog and text in events, the .TXT file is all you need to edit. It contains the lines of text used in the decoded event. Each line must be between quotes.

If you want to edit actual event file code or add more dialog lines (which implies adding at least more `ld_text` instructions), you must edit the .DEC file. This file contains the code inside of the event in an assembly-like format. Not every instruction is known, so those are written as "unknown", followed by the bytes it is comprised of. Some instructions have parameters that are also unknown.

I recommend using Notepad++ as I created a language style file for it. This has custom colors for different instructions and situations, which make the files easier to read. The file is present in the "Notepadpp_style" folder. In Notepad++, go to the "Language" tab > "User Defined Language" > "Define your language..." and in here, click "Import". Navigate to the "Notepaddpp_style" folder and import the .xml inside. Now, this style should automatically be selected for every .DEC file you open.


### 2: Encode Event File

This option encodes a .DEC (and .TXT file) specified by the user (the user must specify only the DEC file, so E0_000.DEC for example), replacing the original .EVS of the same name. Similarly to the decode instruction, this one can also receive a directory path, provided it ends in "/".

### 3: Archive Event Files

This option reads all the files in the specified folder and creats an EX.BIN type file from them. The resulting EX.BIN file is the file that the user should paste back into the game to apply their changes. The same output folder holds the edited EBOOT.BIN that should also be replaced in the game. If you want multiple EX.BIN files' changes to be reflected in the final EBOOT.BIN, don't remove the EBOOT.BIN in the output folder, as other archive operations will edit that file so the changes can stack.


# The Decoded Event File Format

The format that the event files decode to has been thought out for ease of encoding it back, as well as the user's easy of usage. All instructions that aren't self explanatory have a comment generated for them to explain their basics. 

Labels, as their names suggest, act like assembly labels. When the file is decoded, the pointers are interpreted as LABELs so that when the file is edited and encoded back, these can be changed to reflect the edits. 

The program assumes that all instructions that have more than 1 parameter have them seperated by a "," with no spaced to the left or right.

UNLESS SPECIFIED, ALL PARAMETER VALUES THAT ARE NUMERICAL ARE REPRESENTED IN HEXADECIMAL AND SHOULD BE WRITTEN WITH THE PREFIX "0x".

The following sections detail each part of these files.

## Sections

The file posesses different type of sections in it. They are expected to be in the order they are generated in (the order that they will be explained in below).

### .addr
Not really a section. It has an integer value, in hexadecimal, after it. This is the address where the event script should start when the file is encoded back.


### .bgm

This section contains 2 values related to the first song that plays when you enter the scene the event file describes. The value 0xFFFF (-1) typically means no song change. There are 2 song values because some rooms use the song in the second slot under certian circumstances. These values are in hexadecimal and represent the song IDs.

### .talk

This section contains a table of character IDs (scene dependant) along with LABELs that are jumped to when they are talked with in the scene. There can be up to 2 different LABELS per character, but it is unknown in which situation each is used. A situation has been observed where a character doesn't appear the first time you go to a room but later in the story they appear and can be talked to. In that case, the character has no first label, but has a second one.

Sometimes there is only 1 LABEL in the character and the position where another label would be is instead replaced by an *underscore*.

This data is saved to the file not only to make sure the encoded event file has the pointers corrected, but also to aid the user in knowing what parts of the event flow are triggered by what action. An example of a .talk section displayed as a table:

| Character ID | Label 1 | Label 2 |
| ------------ | ------- | ------- |
| 01           | LABEL_0 | _       |
| 02           | _       | LABEL_1 |
| 03           | LABEL_2 | LABEL_3 |

In this example, character 01 has 1 label on the first position, meaning that talking to the character executes the code pointed to by that label in a certain situation (first time in that room?). Character 02 has a label on the second position, meaning that in certain circumstances (second time in that room?), talking to the character will execute the code pointed to by that label. Character 03 has 2 different codes it executes when talked to, depending on the situation (one thing is said when entering the room for the first time, whilst the other is said when you return to that room later in the story)

### .talk2

This section contains the same type of data as the previous one but for secondary characters in the scene. These are taken from structs in the file that are smaller than the primary ones.

### .positions

This section contains a table of position triggers. They include coordinates and the LABELs that are jumped to when the playable character is in one of these positions. Each entry has X and Y values, followed by the label. Example:

    section .positions
        01  02  LABEL_0
        02  10  LABEL_1
    
This example displays 2 different positions that trigger different event script bits. The first entry says that when the player is in X=1 and Y=2, LABEL_0 is jumped to. The second entry says that when the player is in X=2 and Y=10, LABEL_1 is jumped to.
    
### .interactables

This section contains a table of interactables found around that map. Example: The box with the Snow Queen mask in the gym closet. These are saved in the same format as the positions in the section described above. The difference is that the coordinates specify the position in which the character should be facing. For instance, if the character is at X=1 and Y=1, but is looking towards the direction where the Y coordinate goes up (SE), and the interactable is in front of them, then the interactable's coordinates are X=1 and Y=2.

### .code

This is the main section of the file. It stores the event script itself. The known commands are as follows:
* **ret** - finishes the execution of the code
* **jump** - jumps to a specific LABEL that points to more code to execute
    ```
    jump <LABEL_NAME>
    ```
* **jump_if** - jumps to a specific LABEL under certain conditions. It is unknown what the paramter means, but it is probably related to the game's flags
    ```
    jump_if  <CONDITION>,<LABEL_NAME>
    ```
* **battle** - throws the player into an important battle. This includes persona awakening battles and boss battles
    ```
    battle  <BATTLE_NUM>
    ```
    The BATTLE_NUM values and what battles they represent:

    | Value  | Description |
    | ------ | ----------- |
    | 0x0000 | The first awakening |
    | 0x0001 | Elly's awakening |
    | 0x0002 | Maki's awakening |
    | 0x0003 | Brown's awakening |
    | 0x0004 | Ayase's awakening |
    | 0x0005 | Takeda battle |
    | 0x0006 | Reiji's awakening |
    | 0x0007 | unknown battle |
    | 0x0008 | Tesso battle |
    | 0x0009 | Yog Sothoth Jr battle |
    | 0x000A | Harem Queen battle |
    | 0x000B | Mr. Bear battle |
    | 0x000C | Saurva battle |
    | 0x000D | Hariti battle |
    | 0x000E | Kandori battle |
    | 0x000F | Pandora fase 1 |
    | 0x0010 | Akuma monster battle |
    | 0x0011 | Akuma monster battle variation? |
    | 0x0012 | Hypnos 1 battle |
    | 0x0013 | Hypnos 2 battle |
    | 0x0014 | Hypnos 3 battle |
    | 0x0015 | Hypnos 4 battle |
    | 0x0016 | Nemesis 1 battle |
    | 0x0017 | Nemesis 2 battle |
    | 0x0018 | Nemesis 3 battle |
    | 0x0019 | Nemesis 4 battle |
    | 0x001A | Nemesis 5 battle |
    | 0x001B | Nemesis 6 battle |
    | 0x001C | Thanatos 1 battle |
    | 0x001D | Thanatos 2 battle |
    | 0x001E | Snow Queen mask battle |
    | 0x001F | Queen Asura battle |
    | 0x0020 | Bad ending last battle |
    | 0x0021 | Pandora Fase 2 |

    The repeating "Hypnos", "Nemesis" and "Thanatos" entries speak to different stats on the same boss based on the order the player conquered the towers and other conditions.

* **ld_world_map** - Loads the player into a world map TODO: figure this out
    ```
    TODO 
    ```
* **open_shop_menu** - Opens a shop menu depending on the parameters.
    ```
    open_shop_menu  <SHOP_ID>
    ```

    The SHOP_ID can have the following values:

    | SHOP_ID | Shop |
    | ---------- | ----- |
    | 0x01 | Yin & Yang, Maki's world v1 |
    | 0x02 | Weapon shop, Aki's side v1 |
    | 0x03 | Weapon shop, Aki's side v2 |
    | 0x04 | Yin & Yang, Maki's world v2 |
    | 0x06 | Rosa, Mai's side v1 |
    | 0x07 | Armor shop, Aki's side v1 |
    | 0x08 | Armor shop, Aki's side v2 |
    | 0x09 | Rosa, Mai's side v2 |
    | 0x0A | Tadashi, Mai's side v1 |
    | 0x0B | Tadashi, Aki's side v1 |
    | 0x0C | Turunkhamen, Mai's side v1 |
    | 0x0D | Turunkhamen, Aki's side v1 |
    | 0x0E | Casino, money to coin |
    | 0x0F | Casino, coin to item, Mai's side v1 |
    | 0x10 | Yin & Yang real world |
    | 0x11 | Sennen |
    | 0x12 | Tadashi |
    | 0x14 | Casino, coin to item, Sun Mall v1? |
    | 0x16 | Casino, coin to item, unknown |
    | 0x18 | Casino, coin to item, Sun Mall v2? |
    | 0x1A | Casino, coin to item, Joy Street |
    | 0x1C | Casino, coin to item, Mai's side v2 |
    | 0x1E | Casino, coin to item, Aki's side v1 |
    | 0x1F | Khamenturun, Mai's side v1 |
    | 0x20 |Velvet Room, talk menu|
    | 0x21 |Velvet Room, manual fusion menu|
    | 0x22 |Velvet Room, guided fusion menu|
    | 0x23 |Velvet Room, view cards menu|
    | 0x24 |Velvet Room, leave?!??!!|
    | 0x25 |Casino, poker help menu|
    | 0x26 |Casino, blackjack help menu|
    | 0x27 |Casino, slot machine help menu|
    | 0x28 |Casino, code breaker help menu|
    | 0x29 |Casino, dice game help menu|
    | 0x31 |Tadashi, Mai's side v2|
    | 0x32 |Turunkhamen, Mai's side v2|

* **ld_file** - Loads another event file, numbered in the first parameter (specifics are unknown). For instance, when the first parameter is 0x0016, it loads the file E0_022.BIN (0x16 = 22), but unsure how other files in other EX.BIN files are referenced. 0x0316 loads E3_022.BIN.
     ```
    ld_file  <FILE_NUM>,<UNKNOWN>
    ```

* **ld_3d_map** - This loads the player into a 3D map (those first person maze sections of the game). The first parameter is the map's ID. The second is unknown. The third and fourth are the X and Y coordinates the player will be loaded into within that map. Parameter 5 holds the direction, which is represented by a value from 0 to 3, representing E, W, S and N respectively. The sixth parameter is unknown.
    ```
    ld_3d_map  <MAP_ID>,<UNKNOWN>,<X>,<Y>,<DIRECTION>,<UNKNOWN>
    ```

* **give_item** - Puts an item into the player's inventory. These include normal items, equipment and key items, depending on the first parameter. QUANITY is in decimal base.
    ```
    give_item  <ITEM_ID>,<QUANTITY>
    ```

* **play_MV** - plays a video file. The first parameter is the name of the file (TODO: change this. Just realized that there are movies that don't have a MVXX.pmf type name). The second parameter is unknown.
    ```
    play_MV  <MVXX.pmf>,<UNKNOWN>
    ```

* **money_check** - Checks if the player has at least FEE money. If the player doesn't have enough, jumps to the LABEL. The FEE is in decimal case.
    ```
    money_check  <FEE>,<LABEL>
    ```

* **money_transfer** - Adds/removes money to/from the player's wallet. The direction is specified in the first parameter as ADD or REMOVE. The QUANTITY is in decimal case.
    ```
    money_transfer  <DIRECITON>,<QUANTITY>
    ```

* **wait** - stops the execution of the event script for a certain amount of ticks. The number of ticks is in the first and only parameter.
    ```
    wait  <NUMBER_OF_TICKS>
    ```

* **player_option** - jumps to event script code pointed to by a label IF the option at parameter 0 is selected when the player selects an option from a pop-up menu. For instance, the first option in the menu is option 0, so the first parameter in that case would be 0x0000. The second parameter would be the LABEL that is jumped to if that was the selected option. These tend to show up back to back, since they need to be used for each option in the menu.
    ```
    player_option  0x0000,<LABEL_NAME>
    player_option  0x0001,<LABEL_NAME>
    player_option  <OPTION_NUM>,<LABEL_NAME>
    ```

* **ld_text** - displays the text that the index in the first parameter refers to. That index is written in decimal (as opposed to hexadecimal) and refers to one of the text strings at the end of the file.
    ```
    ld_text  <TEXT_IDX (in decimal)>
    ```

* **unk_cmd_58** - it is unknown what the command does, but it probably has something to do with jumping to other parts of the event flow script, seeing as one of its parameters is an address or, in the case of this program, a label. It also has a short as the first parameter.
    ```
    unk_cmd_58  <UNKNOWN>,<LABEL_NAME>
    ```

* **unk_cmd_44**, **unk_cmd_45** and **unk_cmd_47** - these, similarly to the previous command, are unknown in function but they posess a short parameter and an address, so they must be treated differently from the generic unknown instructions. These have been found in the file related to the shrine scene, triggered when you talk to the butterfly and it heals you. These commands could be related to healing your party members and removing status effects.
    ```
    unk_cmd_44  <UNKNOWN>,<LABEL_NAME>
    unk_cmd_45  <UNKNOWN>,<LABEL_NAME>
    unk_cmd_47  <UNKNOWN>,<LABEL_NAME>
    ```

* **open_dialog** - displays the dialog box at the bottom part of the screen. It has no parameters.
    ```
    open_dialog
    ```

* **close_dialog** - clears the dialog box at the bottom part of the screen. It has no parameters.
    ```
    close_dialog
    ```

* **pose** - poses a character in the scene. This includes the pose itself, the X and Y coordinates of where to teleport the character, the direction they must face (SW, NW, SE or NE) and two unknown parameters.
    ```
    pose <CHARACTER_ID>,<POSE>,<X>,<Y>,<DIRECTION>,<UNKNOWN>,<UNKNOWN>
    ```
    The possible pose values are:
    * "**still**" puts the character in a default pose that doesn't have an animation when the character moves
    * "**idle**" puts the character in an idle pose that has an animation when the character moves
    * "**walk**" puts the character in a walking pose, indifferent to whether they are walking.
    * "**pain**" puts the character in a pained pose, like when they are hit in battle
    * "**fight**" puts the character in a fighting stance, like when they are in battle
    * "**crouched**" puts the character in a crouched position
    * "**depressed**" puts the character in a sad/melancholic looking pose
    * "**victory**" puts the character in a victorious pose, like when a battle is won (?)
    * "**dead**" puts the character in a dead/lying down pose, like when they fainted in battle
    
    These values must be written exactly as they are here in the file, or the program will bug out.

* **fx** - plays effects on the scene, such as lightning. Specifics are unknown.
    ```
    fx <UNKNOWN>,<UNKNOWN>,<UNKNOWN>,<UNKNOWN>
    ```

* **clr_char** - clears the character OR EFFECT whose ID is in the parameter
    ```
    clr_char <CHARACTER_ID>
    ```

* **ld_portrait** - displays the portrait of the character in the first paramater in the position specified by the second parameter
    ```
    ld_portrait <CHARACTER>,<ORIENTATION>
    ```

    Possible orientation values:
    * left
    * middle
    * right

    Possible character values:
    * MC
    * Maki
    * Mark
    * Nanjo
    * Yukino
    * Ayase
    * Brown
    * Elly
    * Reiji
    * Maki_sick
    * Maki_happy
    * Mai
    * Aki
    * Maki_masked
    * Maki_masked_SQQ
    * Setsuko
    * MC_alt1
    * Saeko
    * Saeko_Ice
    * Saeko_young
    * Nurse
    * Hanya
    * Ooishi
    * Yamaoka
    * Yosuke
    * Chisato
    * Chisato_corruptv1
    * Chisato_corruptv2
    * Chisato_corruptv3
    * MC_alt2
    * Tsutomu
    * Yuko
    * Yuko_smug
    * Toro
    * MC_alt3
    * Tadashi
    * Tamaki
    * Katsue_rich
    * Katsue_poor
    * Kandori
    * Kandori_mask
    * Takeda
    * MC_alt4
    * Nicholai
    * Tomomi
    * Tomomi_corrupt
    * MC_alt5
    * Kumi
    * Michiko
    * Yuriko
    * MC_alt6
    * MC_alt7
    * Night_Queen
    * Yin_Yang_clerk
    * Rosa_clerk
    * Weapon_clerk
    * Armor_clerk
    * Pharma_clerk
    * MC_alt8
    * Sweets_clerk
    * Turunkhamen
    * Club_coin_clerk
    * Diner_clerk
    * Doctor
    * Igor
    * Trish
    * Khamenturun
    * MC_alt9
    * Master
    * Club_yen_clurk
    * glitch
    
    **WARNING**: These character values only work for this instruciton and NOT for others that use <CHARACTER_ID>. This is because portraits are global, whilst CHARACTER_ID values are different depending on the scene. You can understand what character is what looking through the code and seeing what text is printed, as it will have the name of the character speaking.

* **close_portrait** - clears the previously displayed portrait from the screen. It takes no parameters.
    ```
    close_portrait
    ```

* **emote** - makes the character in parameter 1 (VALUE IN DECIMAL) do the emote in parameter 2.
    ```
    emote <CHARACTER_ID>,<EMOTE>
    ```

    Possible EMOTE values:
    * exclamation
    * question
    * heart
    * awkward
    * zzz

* **screen_fx** - makes an effect happen on the whole screen based on the first parameter.
    ```
    screen_fx <SCREEN_EFFECT_ID>
    ```

    Possible SCREEN_EFFECT_IDs:
    * stop current effect (only works for earthquake)
    * fades out of black (quick)
    * fades out of black (mid speed)
    * fades out of black (slow)
    * fades into black (quick)
    * fades into black (mid speed)
    * fades into black (slow)
    * smaller screen shake (earthquake)
    * medium screen shake (earthquake)
    * bigger screen shake (earthquake)
    * fades out of white (quick)
    * fades out of white (mid speed)
    * fades out of white (slow)
    * fades into white (quick)
    * fades into white (mid speed)
    * fades into white (slow)
    * screen flashes black (mid speed)
    * screen flashes black (quick)

* **plan_char_mov** - plans out a character's movement. The first parameter specifies the character's ID. The second one is the idx of the "trajectory list" present in the base EX_XXX.BIN file to use for the character's trajectory. The third parameter is the speed at which to execute the trajectory. The fourth parameter is the direction the character should face at the testination. The rest are unknown.
    ```
    plan_char_mov <CHARACTER_ID>,<TRAJECTORY_IDX>,<SPEED>,<DIRECTION_AT_DESTINATION>,<UNKNOWN>,<UNKNOWN>
    ```

* **fade_char** - makes the character whose ID is in the first parameter fade into the scene in the speed specified in the second parameter. Both of these are deicmal values.
    ```
    fade_char <CHARACTER_ID>,<SPEED>
    ```

* **follow_char** - makes the camera follow the character specified by the first parameter (IN DECIMAL).
    ```
    follow_char <CHARACTER_ID>
    ```

* **clr_emote** - clears the emote the character whose ID is in the first parameter (IN DECIMAL) is currently doing.
    ```
    clr_emote <CHARACTER_ID>
    ```

* **do_planned_moves** - executes the moves previously planned by using the "plan_char_mov" instruction. Receives no parameter.
    ```
    do_planned_moves
    ```

* **tp_char** - teleports a character to a specific part of the scene. Parameter specifics are unknown.
    ```
    tp_char <UNKNOWN>,<UNKNOWN>
    ```
* **play_song** - plays the song specified in the parameter.
    ```
    play_song <SONG_ID>
    ```

* **play_sfx** - plays the sound effect specified by the parameter
    ```
    play_sfx <SFX_ID>
    ```

### .text

This section contains a collection of text strings of the dialog found inside the event file. The hexadecimal number to the right of the section signature is the size of the collection. Each text string is preceded by it's number, which is used in "ld_text" instructions. This number is in decimal.

In order to build the file back again successfully, special "tags" are used to determine special characters such as (\*LINE_BREAK\*), which traditionally is displayed as a \n. All of these special tags are between parentheses and asteriscs, like the previous example. Other tags include:
* **AWAITING_INPUT** - The text stops displaying on screen until the player presses a button to continue.
    ```
    (*AWAITING_INPUT*)
    ```
* **CONTINUE** - This is found between different text strings that continue into eachother after the input.
    ```
    (*CONTINUE*)
    ```
* **WAIT** - This tag delays the display of the following text by a certain amount of ticks specified in the parameter (IN DECIMAL).
    ```
    (*WAIT,<NUMBER_OF_TICKS>*)
    ```

* **PLAYER_FIRST_NAME** - This tag is replace by the player's first name when the game writes it.
    ```
    (*PLAYER_FIRST_NAME*)
    ```

* **PLAYER_NICKNAME** - This tag is replace by the player's nickname when the game writes it.
    ```
    (*PLAYER_NICKNAME*)
    ```

* **SHOW_OPTIONS** - When the game reaches this point when printing the text, it displays a menu with options specified by the parameter (IN DECIMAL).
    ```
    (*SHOW_OPTIONS,<OPTIONS_ID>*)
    ```

    The possible OPTIONS_ID values and the options they represent is displayed below:
    
    | OPTIONS_ID | Options |
    | ---------- | ----- |
    | 0 | "Yes", "No" |
    | 1 | "Sure.", "No way." |
    | 2 | "Yeah,", "No, I don't" |
    | 3 | "Start game", "Check coins", "See explanations", "Stop playing" |
    | 4 | "No", "Yes" |
    | 5 | "Game rules", "Controls", "Winning hands", "Go back" |
    | 6 | "Game rules", "Controls", "Tips", "Go back" |
    | 7 | "Let them join", "Don't let them join" |
    | 8 | "Help her", "Don't help her" |
    | 9 | "Don't leave", "Leave" |
    | 10 | "Don't open it", "Open it" |
    | 11 | "Don't listen", "Listen" |
    | 12 | "Create Persona", "Take on Persona", "Talk", "Leave" |
    | 13 | "Stop hiding.", "Yes, it's safe here.", "That's true, but...", "I don't really know." |
    | 14 | "For myself.", "Just 'cause.", "For everyone's sake.", "That's how it went." |
    | 15 | "I don't really know.", "To find my reason." |
    | 16 | "Press the red button", "Press the blue button." |
    | 17 | "Heal us, please.", "Just dropping by." |
    | 18 | "Fight Hariti", "Lower your weapons" |
    | 19 | "Don't hide like that!", "Maybe you are..." |
    | 20 | "Stay here", "Go to 8F", "Go to 4F", "Go to 1F" |
    | 21 | "Manual Fusion", "Guided Fusion", "View cards", "Cancel" |
    | 22 | "The Queen's is better.", "Maki's is better." |
    | 23 | "Beginner tips", "Regular tips", "About Personas", "Advanced tips" |
    | 24 | "Start game", "Check cards", "See explanations", "Cancel" |
    | 25 | "Bet on Mark", "Bet on Brown" |
    | 26 | "That's the plan.", "Not really." |
    | 27 | "Yeah.", "That's" |
    | 28 | "Yeah, I do.", "No, no one." |
    | 29 | "A few.", "Not a one." |
    | 30 | "I like the old way.", "I like the new way." |
    | 31 | "Sure, put me down.", "Don't you dare." |
    | 32 | "Buy", "Sell", "Equip", "Cancel" |
    | 33 | "Trade for items", "Trade for incense", "Equip", "Cancel" |
    | 34 | "Normal", "Beginner", "Expert" |
    | 35 | "Yes, it was.", "On second thought..." |

* **SET_COLOR** - sets the following text to the color specified by the parameter.
    ```
    (*SET_COLOR,<COLOR_ID>*)
    ```

    Possible COLOR_ID values:
    * **_** - Clear color
    * **W** - White
    * **B** - Blue
    * **P** - Pink
    * **G** - Green
    * **Y** - Yellow
    * **R** - Red

* **PRINT_ICON** - prints the icon specified by the parameter in that part of the text.
    ```
    (*PRINT_ICON,<ICON_ID>*)
    ```

    TODO check what ICON_IDs are

* **CHARACTER_NAME** - used before the speaking character's name to make it yellow.
    ```
    (*CHARACTER_NAME*)
    ```
