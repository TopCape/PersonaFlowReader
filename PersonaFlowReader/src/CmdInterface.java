import dataAccess.EventFileOps;
import dataAccess.FileReadWriteUtils;
import dataAccess.Library;

import javax.naming.OperationNotSupportedException;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class CmdInterface {

    private static final String OG_PATH = Library.BASE_DIR + "OG/";
    private static final String EXTRACTED_PATH = Library.BASE_DIR + EventFileOps.EXTRACTED_DIR_NAME + "/";
    private static final String CANCEL_STRING = "(Enter \"-1\" at any time to cancel the current operation and go back by one \"screen\")";

    /**
     * The main execution loop for this cmd interface
     */
    public static void run() {
        Scanner sc = new Scanner(System.in);

        boolean isLeaving = false;
        System.out.println("Before choosing any option, make sure the unedited Ex.BIN files are placed in a folder named \"OG\" in the directory of this program");
        while(!isLeaving) {
            printInstructions();
            System.out.println();
            System.out.println(CANCEL_STRING);

            int option = requestOption(sc);

            // try/catch to handle the errors sent by EventFileOps
            try {
                switch (option) {
                    case 0:
                        extractBIN(sc);
                        break;
                    case 1:
                        decodeEVS(sc);
                        break;
                    case 2:
                        encodeDEC(sc);
                        break;
                    case 3:
                        combineEVS(sc);
                        break;
                    case 99:
                    case -1:
                        isLeaving = true;
                        System.out.println("See ya next time!");
                        break;
                    default:
                        System.out.println("Not a valid option. Try again.\n");
                }
            } catch (IOException e) {
                System.out.println("There was a problem accessing the file");
            } catch (OperationNotSupportedException e) {
                System.out.println(e.getMessage());
                //throw e;
            }

        }

    }

    /**
     * Method for extracting .BIN
     * @param sc the Scanner object to read more user inputs
     * @throws OperationNotSupportedException custom exception messages here
     * @throws IOException file related exceptions
     */
    private static void extractBIN(Scanner sc) throws OperationNotSupportedException, IOException {
        System.out.println("Enter the filename (including the extension):");
        String filename = requestInput(sc);

        boolean cancel = checkIfCancel(filename);
        if (cancel) return;

        EventFileOps.extract(OG_PATH + filename);
        System.out.println("The files have been extracted");
    }

    /**
     * Method for decoding .EVS files
     * @param sc the Scanner object to read more user inputs
     * @throws OperationNotSupportedException custom exception messages here
     * @throws IOException file related exceptions
     */
    private static void decodeEVS(Scanner sc) throws OperationNotSupportedException, IOException {
        int j = isJpn(sc);
        if (j < 0) return;

        boolean isJ = j > 0;

        while (true) {
            System.out.println("Enter the filename (including the extension) or a directory (ending in \"/\") to decode all EVS files in it:");
            String filename = requestInput(sc);

            boolean cancel = checkIfCancel(filename);
            if (cancel) return;

            if (filename.charAt(filename.length() - 1) == '/') {
                decodeAll(filename, isJ);
                break;
            } else if (filename.endsWith(EventFileOps.EVENT_SCRIPT_EXTENSION_1) || filename.endsWith(EventFileOps.EVENT_SCRIPT_EXTENSION_2)) {
                String[] filenameSplit = filename.split("_");
                String folderName = filenameSplit[0];
                String path = EXTRACTED_PATH + folderName + "/" + filename;
                EventFileOps.decodeFlowScript(path, isJ);
                break;
            } else {
                System.out.println("Wrong file...");
            }
        }

    }

    /**
     * Method for encoding .DEC files
     * @param sc the Scanner object to read more user inputs
     * @throws OperationNotSupportedException custom exception messages here
     * @throws IOException file related exceptions
     */
    private static void encodeDEC(Scanner sc) throws OperationNotSupportedException, IOException {
        int j = isJpn(sc);
        if (j < 0) return;

        boolean isJ = j > 0;

        while (true) {
            System.out.println("Enter the filename (including the extension) or a directory (ending in \"/\") to encode all DEC files in it:");
            String filename = requestInput(sc);

            boolean cancel = checkIfCancel(filename);
            if (cancel) return;

            if (filename.charAt(filename.length() - 1) == '/') {
                encodeAll(filename, isJ);
                break;
            } else if (filename.endsWith(EventFileOps.DEC_SCRIPT_EXTENSION_1) || filename.endsWith(EventFileOps.DEC_SCRIPT_EXTENSION_2)) {
                String[] filenameSplit = filename.split("_");
                String folderName = filenameSplit[0];
                String path = EXTRACTED_PATH + folderName + "/" + filename;
                EventFileOps.encodeFlowScript(path, isJ);
                break;
            } else {
                System.out.println("Wrong file...");
            }
        }
    }

    /**
     * Method for combining .EVS files back into a .BIN
     * @param sc the Scanner object to read more user inputs
     * @throws OperationNotSupportedException custom exception messages here
     * @throws IOException file related exceptions
     */
    private static void combineEVS(Scanner sc) throws OperationNotSupportedException, IOException {
        int j = isJpn(sc);
        if (j < 0) return;

        boolean isJ = j > 0;

        String inFolder;
        while(true) {
            System.out.println("Enter the name of the folder that contains the extracted files (should be the same as the original file, \"Ex\"):");
            inFolder = requestInput(sc);

            boolean cancel = checkIfCancel(inFolder);
            if (cancel) return;

            if (inFolder.isEmpty()) {
                System.out.println("You must enter a folder name");
                continue;
            }
            break;
        }

        System.out.println("Enter the output path (or just press ENTER for the default path, \"/output/\" in the program's directory)");
        String outFolder = requestInput(sc);

        boolean cancel = checkIfCancel(outFolder);
        if (cancel) return;

        if (inFolder.charAt(inFolder.length()-1) == '/') inFolder = inFolder.substring(0, inFolder.length()-1);
        String[] seg = inFolder.split("/");
        String filename = seg[seg.length-1];

        String destinationDir = outFolder;
        if (outFolder.isEmpty()) {
            destinationDir = Library.BASE_DIR + EventFileOps.OUTPUT_DIR_NAME + "/";
        } else if (destinationDir.charAt(destinationDir.length()-1) != '/') {
            destinationDir += '/';
        }

        // create directory if it doesn't exist
        File dir = new File(destinationDir);
        if (!dir.exists()) {
            dir.mkdir();
        }

        cancel = checkIfCancel(outFolder);
        if (cancel) return;

        String actualPath = EXTRACTED_PATH + inFolder;
        EventFileOps.archive(OG_PATH, actualPath, destinationDir, filename, isJ);
    }

    /**
     * Auxiliary method for asking the user if the extracted files are from the japanese version of the game
     * @param sc the Scanner object to read more user inputs
     * @return {@code 1} for yes, {@code 0} for no or {@code -1} for none of the above
     */
    private static int isJpn(Scanner sc) {
        while(true) {
            System.out.println("Were the files extracted from a japanese version of the game? (y/n):");
            String jpnCheck = requestInput(sc);

            boolean cancel = checkIfCancel(jpnCheck);
            if (cancel) return -1;

            int yesOrNo = yesOrNo(jpnCheck);

            if (yesOrNo < 0) System.out.println("Not an option. Try again.");
            else return yesOrNo;
        }
    }

    /**
     * Auxiliary method for receiving yes or no answers
     * @param string the string input by the user
     * @return {@code 1} for yes, {@code 0} for no or {@code -1} for none of the above
     */
    private static int yesOrNo(String string) {
        if (string.compareToIgnoreCase("y") == 0) {
            return 1;
        } else if (string.compareToIgnoreCase("n") == 0) {
            return 0;
        } else {
            return -1;
        }
    }

    /**
     * Auxiliary method to check if the user entered the cancel input, which makes the user go back by 1 "page"
     * @param string the string input by the user
     * @return {@code true} if the received value was -1
     */
    private static boolean checkIfCancel(String string) {
        int value;
        try {
            value = Integer.parseInt(string);
            return value == -1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Auxiliary method for waiting for a number
     * @param sc the Scanner object to read more user inputs
     * @return the option number
     */
    private static int requestOption(Scanner sc) {
        String input = requestInput(sc);
        int inputInt = -1;
        try {
            inputInt = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.println("Not a number. Try again.\n");
        }
        return inputInt;
    }

    /**
     * Auxiliary method to print the > and receive a line from the user input
     * @param sc the Scanner object to read more user inputs
     * @return a string of what the user wrote
     */
    private static String requestInput(Scanner sc) {
        System.out.print("> ");
        return sc.nextLine();
    }

    /**
     * Prints the instructions of the interface
     */
    private static void printInstructions() {
        System.out.println("Enter the corresponding number to choose from the following options:");
        System.out.println("0 - extract an Ex.BIN file");
        System.out.println("1 - decode an EVS file");
        System.out.println("2 - encode a DEC file");
        System.out.println("3 - combine EVS files to form a new BIN file");
        System.out.println("99 - exit");
        System.out.println();
    }

    /**
     * Decodes all .EVS files in a directory
     * @param path the directory path
     * @param isJ {@code true} if the file was extracted from a japanese version of the game
     * @throws OperationNotSupportedException custom exception messages here
     * @throws IOException file related exceptions
     */
    private static void decodeAll(String path, boolean isJ) throws OperationNotSupportedException, IOException {
        File dir = new File(path);
        File[] directoryListing = dir.listFiles();

        if (directoryListing != null) {
            if (directoryListing.length == 0) {
                throw new OperationNotSupportedException("The directory is empty.");
            }
            for (File child : directoryListing) {
                if (FileReadWriteUtils.getExtension(child.getPath()).compareToIgnoreCase("evs") == 0) {
                    System.out.printf("%s\r",child.getName());
                    EventFileOps.decodeFlowScript(child.getPath(), isJ);
                }
            }
        } else {
            System.out.println("There is something wrong with the directory.");
        }
    }

    /**
     * Encodes all .DEC files in a directory
     * @param path the directory path
     * @param isJ {@code true} if the file was extracted from a japanese version of the game
     * @throws OperationNotSupportedException custom exception messages here
     * @throws IOException file related exceptions
     */
    private static void encodeAll(String path, boolean isJ) throws OperationNotSupportedException, IOException {
        File dir = new File(path);
        File[] directoryListing = dir.listFiles();

        if (directoryListing != null) {
            if (directoryListing.length == 0) {
                throw new OperationNotSupportedException("The directory is empty.");
            }
            for (File child : directoryListing) {
                if (FileReadWriteUtils.getExtension(child.getPath()).compareToIgnoreCase("dec") == 0) {
                    System.out.printf("%s\r",child.getName());
                    EventFileOps.encodeFlowScript(child.getPath(), isJ);
                }
            }
        } else {
            System.out.println("There is something wrong with the directory.");
        }
    }
}
