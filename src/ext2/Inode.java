package ext2;

import com.google.common.primitives.Bytes;
import java.nio.ByteBuffer;
import static java.lang.Math.toIntExact;

/**
 *
 * @author Wilmer
 */
public class Inode {
    public static final int DIRECTORY = 0;
    public static final int FILE = 1;
    // 4 bytes
    private int type;
    // 4 bytes
    private int size;
    // 4 bytes
    private final int creationTime;
    // 4 bytes
    private int deletionTime;
    // 48 bytes
    private final int[] directPointers = new int[12];

    // type: directory or file
    public Inode(int type) {
        this.type = type;
        creationTime = toIntExact(System.currentTimeMillis() / 1000);
        // Set all pointers to -1, to tag them as unused
        for (int i = 0; i < directPointers.length; i++) {
            directPointers[i] = -1;
        }
    }

    // File size
    public void setSize(int size) {
        this.size = size;
    }

    // Save the references of the blocks passed to this method in the pointers
    // Fix: saved in memory only, must be written to disk later
    public void addBlocks(int... blocks) {
        if (blocks.length > directPointers.length) {
            System.out.println("Too many blocks to allocate them all in 12 pointers");
            return;
        }
        for (int block : blocks) {
            for (int i = 0; i < directPointers.length; i++) {
                if (directPointers[i] == -1) {
                    // Pointer unused, assign block here
                    directPointers[i] = block;
                }
            }
        }
    }

    public int getType() {
        return type;
    }

    /*  To array of 64 bytes
        Every inode takes 64 bytes (fixed size)
        The first 4 bytes: mode or type (dir, regular file, etc.)
        The next 4 bytes: file size in bytes
        The next 8 bytes: creation time and deletion time (4 bytes each)
        The remaining 48 bytes are used for the 12 direct pointers (4 bytes each)
    */
    public byte[] toByteArray() {
        final byte TYPE[] = ByteBuffer.allocate(4).putInt(type).array();
        final byte SIZE[] = ByteBuffer.allocate(4).putInt(size).array();
        final byte CR_TIME[] = ByteBuffer.allocate(4).putInt(creationTime).array();
        final byte DEL_TIME[] = ByteBuffer.allocate(4).putInt(deletionTime).array();
        // Fix: missing 12 pointers
        // Merge all arrays
        // The resulting array is used to write this inode instance back to disk
        return Bytes.concat(TYPE, SIZE, CR_TIME, DEL_TIME);
    }
}
