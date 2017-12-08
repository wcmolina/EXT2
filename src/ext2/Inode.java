package ext2;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import static java.lang.Math.toIntExact;

public class Inode {

    public static final int DIRECTORY = 1;
    public static final int FILE = 2;
    public static final int SYM_LINK = 3;
    // 4 bytes
    private int type;
    // 4 bytes
    private int size;
    // 4 bytes
    private int creationTime;
    // 4 bytes
    private int modifiedTime;
    // 4 bytes
    private int lastAccessTime;
    // 4 bytes
    private int deletionTime;
    // 4 bytes
    private int linkCount;
    // 48 bytes
    private final int[] pointers = new int[12];
    // 4 bytes
    private ArrayList<Integer> indirectPointers;
    // Inode number
    private int inode;

    public Inode(int inode, int type) {
        this.inode = inode;
        this.type = type;
        creationTime = modifiedTime = lastAccessTime = toIntExact(System.currentTimeMillis() / 1000);
        linkCount = 1;
        indirectPointers = new ArrayList<>();
    }

    public Inode(int inode, int type, int size) {
        this(inode, type);
        this.size = size;
    }

    // Save the references of the blocks passed to this method in the pointers
    // FIX ME? Return true if the blocks where added succesfully, false otherwise
    public void addBlocks(int... blocks) {
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

    // Reads 80 bytes from the byte array[] and creates a new instance of Inode from it
    public static Inode fromByteArray(byte array[], int inodeNumber) {
        // Split 64 byte array into subarrays
        final byte TYPE[] = Arrays.copyOfRange(array, 0, 4);

        // Before we continue, check if the type is 0 (no inode uses type 0. If it is 0 it means there is no inode)
        int type = Ints.fromByteArray(TYPE);
        if (type == 0) return null;

        final byte SIZE[] = Arrays.copyOfRange(array, 4, 8);
        final byte CR_TIME[] = Arrays.copyOfRange(array, 8, 12);
        final byte M_TIME[] = Arrays.copyOfRange(array, 12, 16);
        final byte A_TIME[] = Arrays.copyOfRange(array, 16, 20);
        final byte DEL_TIME[] = Arrays.copyOfRange(array, 20, 24);
        final byte LINKS[] = Arrays.copyOfRange(array, 24, 28);
        final byte POINTERS[] = Arrays.copyOfRange(array, 28, 76);
        final byte IND_POINTERS[] = Arrays.copyOfRange(array, 76, 80);

        // Build new inode instance from previous arrays
        int size = Ints.fromByteArray(SIZE);
        int crTime = Ints.fromByteArray(CR_TIME);
        int modTime = Ints.fromByteArray(M_TIME);
        int accTime = Ints.fromByteArray(A_TIME);
        int delTime = Ints.fromByteArray(DEL_TIME);
        int links = Ints.fromByteArray(LINKS);

        // Create pointers array
        IntBuffer intBuffer = ByteBuffer.wrap(POINTERS).asIntBuffer();
        int pointers[] = new int[intBuffer.remaining()];
        intBuffer.get(pointers);

        // Create indirect pointers array
        intBuffer = ByteBuffer.wrap(IND_POINTERS).asIntBuffer();
        int indPointers[] = new int[intBuffer.remaining()];
        intBuffer.get(indPointers);

        // Create array list
        ArrayList<Integer> list = Utils.intsToList(indPointers);

        // Create instance and return it
        Inode inode = new Inode(inodeNumber, type, size);
        inode.setCreationTime(crTime);
        inode.setModifiedTime(modTime);
        inode.setLastAccessTime(accTime);
        inode.setDeletionTime(delTime);
        inode.setLinkCount(links);
        inode.addBlocks(pointers);
        inode.setIndirectPointers(list);
        return inode;
    }

    public byte[] toByteArray() {
        final byte TYPE[] = BitUtils.toByteArray(type);
        final byte SIZE[] = BitUtils.toByteArray(size);
        final byte CR_TIME[] = BitUtils.toByteArray(creationTime);
        final byte M_TIME[] = BitUtils.toByteArray(modifiedTime);
        final byte A_TIME[] = BitUtils.toByteArray(lastAccessTime);
        final byte DEL_TIME[] = BitUtils.toByteArray(deletionTime);
        final byte LINKS[] = BitUtils.toByteArray(linkCount);
        final byte POINTERS[] = BitUtils.toByteArray(pointers);
        final byte IND_POINTERS[] = BitUtils.toByteArray(Ints.toArray(indirectPointers));
        return Bytes.concat(TYPE, SIZE, CR_TIME, M_TIME, A_TIME, DEL_TIME, LINKS, POINTERS, IND_POINTERS);
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

    public int getModifiedTime() {
        return modifiedTime;
    }

    public void setModifiedTime(int modifiedTime) {
        this.modifiedTime = modifiedTime;
    }

    public int getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(int lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public int getDeletionTime() {
        return deletionTime;
    }

    public void setDeletionTime(int time) {
        deletionTime = time;
    }

    public int getLinkCount() {
        return linkCount;
    }

    public void setLinkCount(int linkCount) {
        this.linkCount = linkCount;
    }

    public ArrayList<Integer> getIndirectPointers() {
        return indirectPointers;
    }

    public void setIndirectPointers(ArrayList<Integer> indirectPointers) {
        this.indirectPointers = indirectPointers;
    }

    public int getInode() {
        return inode;
    }

    public ArrayList<Integer> getBlocks() {
        ArrayList<Integer> blocks = new ArrayList<>();
        for (int i : pointers) {
            if (i == 0) continue;
            blocks.add(i);
        }
        return blocks;
    }
}
