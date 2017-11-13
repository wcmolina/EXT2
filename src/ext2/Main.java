package ext2;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author Wilmer
 */
public class Main {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            Disk disk = new Disk(new File("disk.bin"));
            disk.format();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
