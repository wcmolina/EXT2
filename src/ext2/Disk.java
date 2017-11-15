package ext2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;

/**
 *
 * @author Wilmer
 */
public class Disk extends RandomAccessFile {

    // Disk volume size in KB
    // 256 MB = 262,144 KB = 268,435,456 bytes
    // With int max volume size = 2TB (2,147,483,647 bytes)
    private final int SIZE_MB = 256;
    private final int SIZE_KB = SIZE_MB * 1024;
    private final int SIZE_B = SIZE_KB * 1024;

    public Disk(File file) throws FileNotFoundException {
        super(file, "rw");
    }

    public int getSizeMB() {
        return SIZE_MB;
    }

    public int getSizeKB() {
        return SIZE_KB;
    }

    public int getSizeBytes() {
        return SIZE_B;
    }
}