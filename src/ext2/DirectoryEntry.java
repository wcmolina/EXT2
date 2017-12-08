package ext2;

import com.google.common.primitives.Bytes;

public class DirectoryEntry {

    public static final byte DIRECTORY = 1;
    public static final byte FILE = 2;
    public static final byte SYM_LINK = 3;
    // Inode number (4 bytes)
    private int inode;
    // Record length (2 bytes)
    private short recLen;
    // Name length (1 byte)
    private byte nameLen;
    // File type (1 byte)
    private byte fileType;
    // File name (0 - 255 bytes)
    private String filename;

    public DirectoryEntry(int inode, byte type, String name) {
        this.inode = inode;
        fileType = type;
        filename = name;
        // As long as filename.length is not > 255 the byte can still be recovered using Byte.toUnsignedInt() method
        nameLen = (byte) filename.length();
        if (nameLen % 4 != 0) {
            // Not a multiple of 4, make it one by appending null terminators
            int nullsToAdd = 4 - (nameLen % 4);
            for (int i = 0; i < nullsToAdd; i++) {
                filename += '\0';
            }
        }
    }

    public DirectoryEntry(int inode, short recLen, byte type, String name) {
        this(inode, type, name);
        this.recLen = recLen;
    }

    // Byte array representation of a directory entry so we can write it back to disk
    public byte[] toByteArray() {
        final byte I_NODE[] = BitUtils.toByteArray(inode);
        final byte REC_LEN[] = BitUtils.toByteArray(recLen);
        final byte NAME_LEN[] = BitUtils.toByteArray(nameLen);
        final byte TYPE[] = BitUtils.toByteArray(fileType);
        final byte FILE_NAME[] = filename.getBytes();
        return Bytes.concat(I_NODE, REC_LEN, NAME_LEN, TYPE, FILE_NAME);
    }

    // Ideal length: every directory entry has an ideal length (multiple of 4) based on
    // how many characters its file name has
    public short getIdealLen() {
        return (short) (4 * ((8 + nameLen + 3) / 4));
    }

    public int getInode() {
        return inode;
    }

    public void setInode(int inode) {
        this.inode = inode;
    }

    public short getRecLen() {
        return recLen;
    }

    public void setRecLen(short recLen) {
        this.recLen = recLen;
    }

    public int getType() {
        return fileType;
    }

    public String getFilename() {
        return filename.trim();
    }
}
