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
            File binaryFile = new File("disk.bin");
            final Disk DISK;
            final FileSystem FILE_SYSTEM;
            if (binaryFile.exists() && !binaryFile.isDirectory()) {
                // disk.bin already exists. Perform operations in this file
                System.out.println("File disk.bin already exists");
                DISK = new Disk(binaryFile);
                FILE_SYSTEM = new FileSystem(DISK);
                // TODO: use this file as a disk and try to save a file using cat > a.txt
                // Algorithm for saving a file in EXT2? How are dir_entry, inodes table, and pointers used?
            } else {
                binaryFile.createNewFile();
                DISK = new Disk(binaryFile);
                FILE_SYSTEM = new FileSystem(DISK);
                FILE_SYSTEM.format();
                System.out.println("File disk.bin was created and formatted successfully");
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
