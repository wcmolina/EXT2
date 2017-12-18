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
    // 48 bytes (12 x 4 bytes)
    private final int[] directPointers = new int[12];
    // 4 bytes
    private int indirectPointer;
    // Inode number
    private int inode;
    // Sym link url
    private String url = "";

    public Inode(int inode, int type) {
        this.inode = inode;
        this.type = type;
        creationTime = modifiedTime = lastAccessTime = toIntExact(System.currentTimeMillis() / 1000);
        linkCount = 1;
    }

    public Inode(int inode, int type, int size) {
        this(inode, type);
        this.size = size;
    }

    // Save the references of the blocks passed to this method in the pointers
    // FIX ME? Return true if the blocks where added succesfully, false otherwise
    public void addBlocks(int... blocks) {
        int pointersLeft = 12 - getDirectBlocks().size();
        if (blocks.length > pointersLeft) {
            throw new IllegalArgumentException(String.format("There are only %d direct pointers left and %d blocks were sent",
                    pointersLeft,
                    blocks.length));
        }
        for (int block : blocks) {
            for (int i = 0; i < 12; i++) {
                if (directPointers[i] == 0) {
                    directPointers[i] = block;
                    break;
                }
            }
        }
    }

    // Reads 80 bytes from the byte array[] and creates a new instance of Inode from it
    public static Inode fromByteArray(byte array[], int inodeNumber) {
        // Split 80 byte array into subarrays
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
        final byte IND_POINTER[] = Arrays.copyOfRange(array, 76, 80);

        // Build new inode instance from previous arrays
        int size = Ints.fromByteArray(SIZE);
        int crTime = Ints.fromByteArray(CR_TIME);
        int modTime = Ints.fromByteArray(M_TIME);
        int accTime = Ints.fromByteArray(A_TIME);
        int delTime = Ints.fromByteArray(DEL_TIME);
        int links = Ints.fromByteArray(LINKS);
        int indPointer = Ints.fromByteArray(IND_POINTER);
        String url = "";
        int pointers[] = null;

        if (type == SYM_LINK) {
            url = new String(POINTERS);
        } else {
            // Create pointers array
            IntBuffer intBuffer = ByteBuffer.wrap(POINTERS).asIntBuffer();
            pointers = new int[intBuffer.remaining()];
            intBuffer.get(pointers);
        }

        // Create instance and return it
        Inode inode = new Inode(inodeNumber, type, size);
        inode.setCreationTime(crTime);
        inode.setModifiedTime(modTime);
        inode.setLastAccessTime(accTime);
        inode.setDeletionTime(delTime);
        inode.setLinkCount(links);
        if (type == SYM_LINK) {
            inode.setSymLinkUrl(url);
        } else {
            inode.addBlocks(pointers);
        }
        inode.setIndirectPointer(indPointer);
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

        byte urlBytes[] = new byte[48];
        if (type == SYM_LINK) {
            byte[] bytes = url.getBytes();
            for (int i = 0; i < urlBytes.length; i++) {
                if (i > bytes.length - 1) {
                    urlBytes[i] = '\0';
                } else {
                    urlBytes[i] = bytes[i];
                }
            }
        }

        final byte POINTERS[] = (type == SYM_LINK) ? urlBytes : BitUtils.toByteArray(directPointers);
        final byte IND_POINTERS[] = BitUtils.toByteArray(indirectPointer);
        return Bytes.concat(TYPE, SIZE, CR_TIME, M_TIME, A_TIME, DEL_TIME, LINKS, POINTERS, IND_POINTERS);
    }

    public ArrayList<Integer> getDirectBlocks() {
        ArrayList<Integer> blocks = new ArrayList<>();
        for (int i : directPointers) {
            if (i == 0) continue;
            blocks.add(i);
        }
        return blocks;
    }

    public void setSymLinkUrl(String url) {
        this.url = url.trim();
    }

    public String getSymLinkUrl() {
        return url;
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

    public int getIndirectPointer() {
        return indirectPointer;
    }

    public void setIndirectPointer(int indirectPointer) {
        this.indirectPointer = indirectPointer;
    }

    public int getInode() {
        return inode;
    }

    public int getType() {
        return type;
    }
}
