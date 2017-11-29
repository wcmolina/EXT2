package ext2;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import static java.lang.Math.toIntExact;

/**
 * @author Wilmer
 */
public class Inode {
    public static final int DIRECTORY = 1;
    public static final int FILE = 2;
    // 4 bytes
    private int type;
    // 4 bytes
    private int size;
    // 4 bytes
    private int creationTime;
    // 4 bytes
    private int deletionTime;
    // 48 bytes
    // FIX ME? Change this to ArrayList<Integer>?
    private final int[] pointers = new int[12];
    // Inode number
    private int inode;

    public Inode(int inode, int type) {
        this.inode = inode;
        this.type = type;
        creationTime = toIntExact(System.currentTimeMillis() / 1000);
    }

    public Inode(int inode, int type, int size) {
        this(inode, type);
        this.size = size;
        this.inode = inode;
    }

    // Save the references of the blocks passed to this method in the pointers
    // FIX ME: return true if the blocks where added succesfully, false otherwise
    public void addBlockPointers(int... blocks) {
        if (blocks.length > 12) {
            System.out.println("Too many blocks to allocate them all in 12 pointers");
            return;
        }
        for (int block : blocks) {
            for (int i = 0; i < 12; i++) {
                if (pointers[i] == 0) {
                    pointers[i] = block;
                    break;
                }
            }
        }
    }

    // Reads 64 bytes from the byte array[] and creates a new instance of Inode from it
    public static Inode fromByteArray(byte array[], int inodeNumber) {
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
        Inode inode = new Inode(inodeNumber, type, size);
        inode.setCreationTime(crTime);
        inode.setDeletionTime(delTime);
        inode.addBlockPointers(pointers);
        return inode;
    }

    public byte[] toByteArray() {
        final byte TYPE[] = Util.toByteArray(type);
        final byte SIZE[] = Util.toByteArray(size);
        final byte CR_TIME[] = Util.toByteArray(creationTime);
        final byte DEL_TIME[] = Util.toByteArray(deletionTime);
        final byte POINTERS[] = Util.toByteArray(pointers);
        return Bytes.concat(TYPE, SIZE, CR_TIME, DEL_TIME, POINTERS);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(int time) {
        creationTime = time;
    }

    public int getDeletionTime() {
        return deletionTime;
    }

    public void setDeletionTime(int time) {
        deletionTime = time;
    }

    public int getType() {
        return type;
    }

    public int getInode() {
        return inode;
    }

    public void setInode(int inodeNumber) {
        inode = inodeNumber;
    }

    public int[] getPointers() {
        return pointers;
    }

    public int[] getUsedPointers() {
        ArrayList<Integer> blocks = new ArrayList<>();
        for (int i : pointers) {
            if (i == 0) continue;
            blocks.add(i);
        }
        return Ints.toArray(blocks);
    }
}
