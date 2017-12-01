package ext2;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;

import java.util.Arrays;

public class DirectoryEntry {

    public static final byte DIRECTORY = 1;
    public static final byte FILE = 2;
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

    public DirectoryEntry(int inode, String name, byte type) {
        this.inode = inode;
        fileType = type;
        filename = name;
        // FIX ME: If filename.length > 127 the byte will overflow
        // As long as filename.length is not > 255 the byte can still be recovered using Byte.toUnsignedInt() method
        nameLen = (byte) filename.length();
        if (nameLen % 4 != 0) {
            // Not a multiple of 4, make it one by appending null terminators
            int nullsToAdd = 4 - (nameLen % 4);
            for (int i = 0; i < nullsToAdd; i++) {
                filename += '\0';
            }
        }
        // 8 bytes are needed for: inode, rec_len, name_len, and type. File name length varies
        recLen = (short) (8 + filename.length());
    }

    // Create a directory entry from an array containing all its bytes
    public static DirectoryEntry fromByteArray(byte[] array) {
        // The first 8 bytes are fixed (inode, rec_len, name_len, type)
        final byte I_NODE[] = Arrays.copyOfRange(array, 0, 4);
        final byte REC_LEN[] = Arrays.copyOfRange(array, 4, 6);
        final byte FILE_NAME[] = Arrays.copyOfRange(array, 8, array.length);
        int inode = Ints.fromByteArray(I_NODE);
        short recLen = Shorts.fromByteArray(REC_LEN);
        byte nameLen = array[6];
        byte type = array[7];
        String fileName = new String(FILE_NAME);

        // Create a new directory entry instance
        DirectoryEntry dirEntry = new DirectoryEntry(inode, fileName, type);
        dirEntry.setNameLen(nameLen);
        dirEntry.setRecLen(recLen);
        return dirEntry;
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

    // Getters and setters
    public int getInodeNumber() {
        return inode;
    }

    public void setInodeNumber(int iNode) {
        this.inode = iNode;
    }

    public int getRecLen() {
        return recLen;
    }

    public void setRecLen(short recLen) {
        this.recLen = recLen;
    }

    public int getNameLen() {
        return nameLen;
    }

    public void setNameLen(byte nameLen) {
        this.nameLen = nameLen;
    }

    public int getType() {
        return fileType;
    }

    public void setType(byte fileType) {
        this.fileType = fileType;
    }

    public String getFilename() {
        return filename.trim();
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }
}
