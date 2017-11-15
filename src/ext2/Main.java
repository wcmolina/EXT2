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
            final Disk DISK = new Disk(new File("disk.bin"));
            final FileSystem FILE_SYSTEM = new FileSystem(DISK);
            FILE_SYSTEM.format();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
