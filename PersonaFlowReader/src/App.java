import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.util.ArrayList;

import dataAccess.EventFileOps;
import dataAccess.FileReadWriteUtils;

import javax.naming.OperationNotSupportedException;

public class App {

    public static final ByteOrder order = ByteOrder.LITTLE_ENDIAN;
    public static final ByteOrder instructionOrder = ByteOrder.BIG_ENDIAN;

    public static void main(String[] args) {
        CmdInterface.run();

        // outside of ICU
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_021.BIN");
        //EventFileOps.decodeFlowScript("../Efiles_removal_cutscenes/E0/E0_021.BIN");
        //EventFileOps.encodeFlowScript("../Efiles_removal_cutscenes/E0/E0_021.DEC");

        // yamaoka dead
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_023.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_023.DEC");

        // shrine with Maki's mom waking up
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_029.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_029.DEC");

        //EventFileOps.decodeFlowScript("../Efiles_OG/E0/E0_029.BIN");
        //EventFileOps.encodeFlowScript("../Efiles_OG/E0/E0_029.DEC");

        // room where you get the snow queen mask
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_040.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_040.DEC");

        // Behind gym, after having SQ mask (SQ story telling)
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_041.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_041.DEC");

        // police station, finding key
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_048.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_048.DEC");

        // police station, saving Mark
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_049.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_049.DEC");

        // Mark's mom rosa candida stuff
        //EventFileOps.decodeFlowScript("../Efiles/E2/E2_040.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E2/E2_040.DEC");
        //EventFileOps.decodeFlowScript("../Efiles/E2/E2_040_og.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E2/E2_040_og.DEC");

        // police station after Brown's awakening
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_050.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_050.DEC");

        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_051.BIN");
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_052.BIN");
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_053.BIN");
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_054.BIN");

        // warehouse, first time
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_055.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_055.DEC");

        // warehouse, second time
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_056.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_056.DEC");

        // Kandori's office
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_057.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_057.DEC");

        // Injured boy met, right before mazification of school
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_065.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_065.DEC");

        // Reiji recruit room, before awakening battle
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_066.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_066.DEC");

        // Reiji recruit room, after awakening battle
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_067.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_067.DEC");

        // Shrine another world, before movie
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_085.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_085.DEC");

        // Shrine another world, after movie
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_087.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_087.DEC");


        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_088.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_088.DEC");

        // subway, after boss
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_093.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_093.DEC");

        // Kama palace boss room
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_100.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_100.DEC");

        // Kama palace boss room AFTER BOSS
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_101.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_101.DEC");

        // Mana Castle entrance first time
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_116.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_116.DEC");

        // Mr. Bear boss room, before boss
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_118.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_118.DEC");

        // Mr. Bear boss room, after boss
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_119.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_119.DEC");

        // Kandori confrontation in Mana Castle, before mirror cutscene
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_133.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_133.DEC");

        // Kandori confrontation in Mana Castle, after mirror cutscene
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_134.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_134.DEC");

        // after Kandori boss in Mana Castle
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_135.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_135.DEC");

        // Before Hariti boss (Haunted House boss room)
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_137.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_137.DEC");

        // After Hariti boss (Haunted House boss room)
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_138.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_138.DEC");

        // back to the D.V.A system chamber
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_144.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_144.DEC");

        // Before Kandori boss fight
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_147.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_147.DEC");

        // SEBEC Bad ending scene
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_149.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_149.DEC");

        // Maki with mask in forest room
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_162.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_162.DEC");

        // Alaya Caverns other MC room, before flashbacks
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_197.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_197.DEC");

        // Hospital Flashback scene
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_179.BIN");

        // D.V.A system flashback
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_180.BIN");

        // Harem Queen flashback
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_181.BIN");

        // Hariti flashback
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_182.BIN");

        // Kandori flashback
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_183.BIN");

        // Masked Maki in forest flashback
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_184.BIN");

        // some Agastya tree
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_198.BIN");

        // Alaya Caverns other MC room, after flashbacks
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_199.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_199.DEC");

        // After final boss (Pandora)
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_215.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_215.DEC");

        // First SQQ cutscene (after freezing school)
        //EventFileOps.decodeFlowScript("../Efiles/E1/E1_001.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E1/E1_001.DEC");

        //EventFileOps.decodeFlowScript("../Efiles/E1/E1_004.BIN");

        // Before Toro battle
        //EventFileOps.decodeFlowScript("../Efiles/E1/E1_006.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E1/E1_006.DEC");

        // After Toro battle
        //EventFileOps.decodeFlowScript("../Efiles/E1/E1_009.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E1/E1_009.DEC");

        // hypnos tower door room
        //EventFileOps.decodeFlowScript("../Efiles/E1/E1_011.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E1/E1_011.DEC");

        // hypnos tower sleeping lovebirds room
        //EventFileOps.decodeFlowScript("../Efiles/E1/E1_033.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E1/E1_033.DEC");

        // hypnos tower room with rainbow door
        //EventFileOps.decodeFlowScript("../Efiles/E1/E1_040.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E1/E1_040.DEC");

        // Principal sleep room, now awake
        //EventFileOps.decodeFlowScript("../Efiles/E1/E1_066.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E1/E1_066.DEC");

        // boss room, first time in
        //EventFileOps.decodeFlowScript("../Efiles/E1/E1_073.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E1/E1_073.DEC");

        // boss room, after time in
        //EventFileOps.decodeFlowScript("../Efiles/E1/E1_074.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E1/E1_074.DEC");

        // decode ALL E1
        //decodeAll("../Efiles/E1/");
        //decodeAll("../Efiles/E2/");
        //decodeAll("../Efiles/E3/");
        //decodeAll("../Efiles/E4/");


        //decodeAll("../Efiles_OG/E0/", false);
        //decodeAll("../Efiles_OG_JPN/E0/", true);
        //EventFileOps.decodeFlowScript("../Efiles_OG_JPN/E0/E0_000.BIN", true);
        //EventFileOps.encodeFlowScript("../Efiles_OG_JPN/E0/E0_000.DEC", true);
        //EventFileOps.decodeFlowScript("../Efiles_OG/E0/E0_065.BIN", false);
        //EventFileOps.encodeFlowScript("../Efiles_OG/E0/E0_065.DEC", false);


        //EventFileOps.decodeFlowScript("../Efiles_OG/E3/E3_012.BIN");
        //EventFileOps.encodeFlowScript("../Efiles_OG/E3/E3_012.DEC");

        // CASINO ROOM OR SOMETHIN. BAAAAD AT CONVERTING NOW
        //EventFileOps.decodeFlowScript("../Efiles/E2/E2_048.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E2/E2_048.DEC");

        /*
        String line = "\t\t\tret\t\t; a comment";
        line = EventFileOps.removeCommentAndSpaces(line);
        System.out.println(line);

        String[] split = line.split("[ \t]");
        System.out.println("length: " + split.length);

        int i = EventFileOps.skipSpacesNTabs(split, 0);

        String instr = split[i];
        if (i+1 == split.length) {
            System.out.println("no params");
            System.out.println(instr);
        } else {
            System.out.println("more params");

            System.out.println("i: " + i);
            i = EventFileOps.skipSpacesNTabs(split, i+1);
            System.out.println(split[i]);
        }
        System.out.println("i: " + i);

        String movieFileName = "MV0B.pmf"; // format: MVXX.pmf, where XX is the number
        String param1 = "0x" + movieFileName.substring(2,4);
        System.out.println(param1);

        System.out.println("---------------------------");

        line = "\t\t\t000:\"(*CHARACTER_NAME*)Hat-wearing student(*LINE_BREAK*)“(*SET_COLOR,R*)Persona(*SET_COLOR,W*)“?(*AWAITING_INPUT*)(*CONTINUE*)(*CHARACTER_NAME*)Hat-wearing student(*LINE_BREAK*)Dude, if that really worked and I could(*LINE_BREAK*)see my future, I'd be on easy street.(*AWAITING_INPUT*)(*CONTINUE*)(*CHARACTER_NAME*)Hat-wearing student(*LINE_BREAK*)You sure you ain't got the brain rot,(*LINE_BREAK*)Hidehiko?(*AWAITING_INPUT*)\"\t ";
        line = EventFileOps.removeCommentAndSpaces(line);

        String[] splitText = line.split("[ \t]");
        i = EventFileOps.skipSpacesNTabs(splitText, 0);
        short textId = Short.parseShort(splitText[i].substring(0, 3));
        System.out.println("text ID = " + textId);

        i = line.indexOf(":") + 1;
        String text = line.substring(i);
        System.out.println(text);

        System.out.println(text.charAt(0));
        System.out.println(text.charAt(text.length()-1));
         */


        /*String line = "\t\t\t"+ Library.UNKNOWN_INSTR_TEXT + "|FF247201,88130000";
        String[] split = line.split("[ \t]");
        System.out.println("length: " + split.length);
        int i;
        for (i = 0; i < split.length; i++) {
            if (split[i].length() > 0) break;
        }
        String instr = split[i];
        if (instr.contains("|")) {
            String[] splitDeeper = instr.split("[|]");
            if (splitDeeper[0].compareTo(Library.UNKNOWN_INSTR_TEXT) != 0) {
                System.out.println("BAD\n");
                return;
            }
            String[] splitCommas = splitDeeper[1].split(",");
            for (String value : splitCommas) {
                System.out.println(String.format("%08x", (int)Long.parseLong(value, 16)));
            }
        }*/


        /*
        String line = "\t\t050:\"eat dicks yep\"";
        String[] split = line.split("[ \t]");
        System.out.println("length: " + split.length);
        int i;
        for (i = 0; i < split.length; i++) {
            if (split[i].length() > 0) break;
        }

        System.out.println(": idx: " + line.indexOf(":"));

        short textId = Short.parseShort(split[i].substring(0, 3));
        System.out.println(textId);
        */

        /*
        String line = "LABEL_35:\n";

        String[] sectionSplit = line.split(Library.SPACE_TAB_REGEX);
        String label = sectionSplit[0];
        String labelNum =label.split(Library.LABEL_SEPARATOR)[1];
        System.out.println(Short.parseShort(labelNum.substring(0, labelNum.length()-2)));
        */

        /*
        String line = "\tld_portrait\t4,left\t\t\t\t\t; value in ticks\n";
        System.out.println(line);
        line = EventFileOps.removeComment(line);
        System.out.println(line);

        String[] split = line.split("[ \t]");
        System.out.println(split.length);
        int i;
        for (i = 0; i < split.length; i++) {
            if (split[i].length() > 0) break;
        }
        System.out.println(split[i]);
        System.out.println(split[i+1]);
        */

        /*
        RandomAccessFile file = new RandomAccessFile("./Efiles/E0/E0_022.BIN", "r");
        EventFileOps.testText(file, 0x13A8);
        System.out.println("\n(Next dialg)\n");
        EventFileOps.testText(file, 0x1410);
        System.out.println("\n(Next dialg)\n");
        EventFileOps.testText(file, 0x1458);

         */

        /*File f = new File(PATH);
        try (RandomAccessFile file = new RandomAccessFile(f, "rw")){
            Integer[] pointers = getFilePointers(file);
            //testPrintFilePointers(file, pointers);
            for (Integer pointer: pointers) {
                //seek here and do things
                //since idk yet, seeking directly to pointer to flow instructions
                file.seek(pointer+Library.FLOW_OFFSET);
                int flowInstrPointer = FileReadWriteUtils.readInt(file, order);
                file.seek(pointer+flowInstrPointer);

                int nothin = 0;
                while(true) {
                    short instruction = FileReadWriteUtils.readShort(file, instructionOrder);
                    if (instruction < (short) 0xff00) break;
                    String interpreted = interpretInstruction(file, pointer, instruction);
                    if (interpreted == null || interpreted.compareTo("nothin") == 0) {
                        nothin++;
                    } else {
                        nothin = 0;
                    }
                    if (nothin == LIMIT) break;
                    System.out.println(interpreted + "\n");
                }
                System.out.println("---------------------------------------------------------------------------");
                break;
            }

        } catch (FileNotFoundException e) {
            System.out.println("File was not found");
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }*/
    }

    private static void decodeAllJP() throws OperationNotSupportedException, IOException {
        decodeAll("../Efiles_OG_JPN/E0/", true);
        System.out.println("E0 done");
        //decodeAll("../Efiles_OG/E1/");
        decodeAll("../Efiles_OG_JPN/E1/", true);
        System.out.println("E1 done");
        //decodeAll("../Efiles_OG/E2/");
        decodeAll("../Efiles_OG_JPN/E2/", true);
        System.out.println("E2 done");
        //decodeAll("../Efiles_OG/E3/");
        decodeAll("../Efiles_OG_JPN/E3/", true);
        System.out.println("E3 done");
        //decodeAll("../Efiles_OG/E4/");
        decodeAll("../Efiles_OG_JPN/E4/", true);
    }

    private static void decodeAll(String path, boolean isJ) throws OperationNotSupportedException, IOException {
        File dir = new File(path);
        File[] directoryListing = dir.listFiles();

        if (directoryListing != null) {
            if (directoryListing.length == 0) {
                throw new OperationNotSupportedException("The directory is empty");
            }
            for (File child : directoryListing) {
                System.out.println(child.getName());
                if (FileReadWriteUtils.getExtension(child.getPath()).compareToIgnoreCase("evs") == 0) {
                    EventFileOps.decodeFlowScript(child.getPath(), isJ);
                }
            }
        }
    }

    private static void testText() throws IOException {
        RandomAccessFile file = new RandomAccessFile("./Efiles/E0/E0_022.BIN", "r");
        EventFileOps.testText(file, false);

        System.out.println("\n-------------------------\n");

        file = new RandomAccessFile("./Efiles/E0/E0_000.BIN", "r");
        EventFileOps.testText(file, false);

        System.out.println("\n-------------------------\n");

        file = new RandomAccessFile("./Efiles/E0/E0_018.BIN", "r");
        EventFileOps.testText(file, false);

        System.out.println("\n-------------------------\n");

        file = new RandomAccessFile("./Efiles/E0/E0_027.BIN", "r");
        EventFileOps.testText(file, false);

        System.out.println("\n-------------------------\n");

        file = new RandomAccessFile("./Efiles/E0/E0_075.BIN", "r");
        EventFileOps.testText(file, false);
    }

    public static Integer[] getFilePointers(RandomAccessFile file) throws IOException {
        ArrayList<Integer> l = new ArrayList<>();
        int ref = -1;
        while(true) {
            ref = FileReadWriteUtils.readInt(file, order);
            if (ref == 0) break;
            l.add(ref);
        }
        Integer[] a = new Integer[0];
        return l.toArray(a);
    }

    private static void testPrintFilePointers(RandomAccessFile file, Integer[] pointers) throws IOException {
        for (Integer i : pointers) {
            System.out.printf("0x%08X%n", i);
            file.seek(i.longValue());
            System.out.printf("0x%08X%n",FileReadWriteUtils.readInt(file, instructionOrder));
        }
        System.out.println("LENGTH: " + pointers.length);
    }
}
