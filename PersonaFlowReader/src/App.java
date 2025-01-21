import java.nio.ByteOrder;

public class App {

    public static final ByteOrder order = ByteOrder.LITTLE_ENDIAN;
    public static final ByteOrder instructionOrder = ByteOrder.BIG_ENDIAN;

    public static void main(String[] args) {
        CmdInterface.run();

    }
}
