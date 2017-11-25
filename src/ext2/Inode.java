package ext2;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static java.lang.Math.toIntExact;

/**
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
    private int creationTime;
    // 4 bytes
    private int deletionTime;
    // 48 bytes
    private final int[] directPointers = new int[12];

    // type: directory or file
    public Inode(int type) {
        this.type = type;
        creationTime = toIntExact(System.currentTimeMillis() / 1000);
    }

    public Inode(int type, int size) {
        this(type);
        this.size = size;
    }

    // File size
    public void setSize(int size) {
        this.size = size;
    }

    public void setCreationTime(int time) {
        creationTime = time;
    }

    public void setDeletionTime(int time) {
        deletionTime = time;
    }

    // Save the references of the blocks passed to this method in the pointers
    public void addBlocks(int... blocks) {
        if (blocks.length > directPointers.length) {
            System.out.println("Too many blocks to allocate them all in 12 pointers");
            return;
        }
        for (int block : blocks) {
            for (int i = 0; i < directPointers.length; i++) {
                directPointers[i] = block;
            }
        }
    }

    public int getType() {
        return type;
    }

    public int getSize() {
        return size;
    }

    // Return epoch time
    public int getCreationTime() {
        return creationTime;
    }

    // Reads 64 bytes from the byte array[] and creates a new instance of Inode from it
    public static Inode fromByteArray(byte array[]) {
        // Split 64 byte array into subarrays
        final byte TYPE[] = Arrays.copyOfRange(array, 0, 4);
        final byte SIZE[] = Arrays.copyOfRange(array, 4, 8);
        final byte CR_TIME[] = Arrays.copyOfRange(array, 8, 12);
        final byte DEL_TIME[] = Arrays.copyOfRange(array, 12, 16);
        final byte POINTERS[] = Arrays.copyOfRange(array, 16, 64);

        // Build new inode instance from previous arrays
        int type = Ints.fromByteArray(TYPE);
        int size = Ints.fromByteArray(SIZE);
        int crTime = Ints.fromByteArray(CR_TIME);
        int delTime = Ints.fromByteArray(DEL_TIME);

        // Create pointers array
        IntBuffer intBuffer = ByteBuffer.wrap(POINTERS).asIntBuffer();
        int[] pointers = new int[intBuffer.remaining()];
        intBuffer.get(pointers);

        // Create instance and return it
        Inode inode = new Inode(type, size);
        inode.setCreationTime(crTime);
        inode.setDeletionTime(delTime);
        inode.addBlocks(pointers);
        return inode;
    }

    /*  To array of 64 bytes
        Every inode takes 64 bytes (fixed size)
        The first 4 bytes: mode or type (dir, regular file, etc.)
        The next 4 bytes: file size in bytes
        The next 8 bytes: creation time and deletion time (4 bytes each)
        The remaining 48 bytes are used for the 12 direct pointers (4 bytes each)
    */
    public byte[] toByteArray() {
        final byte TYPE[] = Util.toByteArray(type);
        final byte SIZE[] = Util.toByteArray(size);
        final byte CR_TIME[] = Util.toByteArray(creationTime);
        final byte DEL_TIME[] = Util.toByteArray(deletionTime);
        final byte POINTERS[] = Util.toByteArray(directPointers);
        // Merge all arrays
        // The resulting array is used to write this inode instance back to disk
        return Bytes.concat(TYPE, SIZE, CR_TIME, DEL_TIME, POINTERS);
    }
}
