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
    public static final int LIMIT = 3;
    public static final String PATH = "../Efiles/E0.BIN";

    public static void main(String[] args) throws OperationNotSupportedException, IOException {
        //EventFileOps.extract(PATH);
        //EventFileOps.extract("../Efiles_removal_cutscenes/E0.BIN");
        //EventFileOps.extract("../Efiles/E2.BIN");
        //EventFileOps.archive("../Efiles/E4/");


        //testText();

        //EventFileOps.decodeFlowScript("../Efiles/E4/E4_000.BIN");
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_075.BIN");
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_022.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_022.DEC");

        //System.out.println("NeXT");
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_075.BIN");

        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_001.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_001.DEC");


        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_000_CUSTOM.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_000_CUSTOM.DEC");

        //EventFileOps.decodeFlowScript("../Efiles_removal_cutscenes/E0/E0_000.BIN");

        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_000.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_000.DEC");
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_001.BIN");

        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_012.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_012.DEC");
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_014.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_014.DEC");

        // outside of ICU
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_021.BIN");
        //EventFileOps.decodeFlowScript("../Efiles_removal_cutscenes/E0/E0_021.BIN");
        //EventFileOps.encodeFlowScript("../Efiles_removal_cutscenes/E0/E0_021.DEC");

        // yamaoka dead
        EventFileOps.decodeFlowScript("../Efiles/E0/E0_023.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E0/E0_023.DEC");

        // room where you get the snow queen mask
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_040.BIN");

        // police station, finding key
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_048.BIN");

        // police station, saving Mark
        //EventFileOps.decodeFlowScript("../Efiles/E0/E0_049.BIN");

        // Mark's mom rosa candida stuff
        //EventFileOps.decodeFlowScript("../Efiles/E2/E2_040.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E2/E2_040.DEC");
        //EventFileOps.decodeFlowScript("../Efiles/E2/E2_040_og.BIN");
        //EventFileOps.encodeFlowScript("../Efiles/E2/E2_040_og.DEC");

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



    private static void testText() throws IOException {
        RandomAccessFile file = new RandomAccessFile("./Efiles/E0/E0_022.BIN", "r");
        EventFileOps.testText(file);

        System.out.println("\n-------------------------\n");

        file = new RandomAccessFile("./Efiles/E0/E0_000.BIN", "r");
        EventFileOps.testText(file);

        System.out.println("\n-------------------------\n");

        file = new RandomAccessFile("./Efiles/E0/E0_018.BIN", "r");
        EventFileOps.testText(file);

        System.out.println("\n-------------------------\n");

        file = new RandomAccessFile("./Efiles/E0/E0_027.BIN", "r");
        EventFileOps.testText(file);

        System.out.println("\n-------------------------\n");

        file = new RandomAccessFile("./Efiles/E0/E0_075.BIN", "r");
        EventFileOps.testText(file);
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
