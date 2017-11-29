package ext2;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;

import java.util.Arrays;

/**
 * @author Wilmer
 */
public class DirectoryEntry {
    // A directory is just a list of inodes and filenames (list of directory entries)
    // The inode points to the rest of the file's metadata, including the pointers to the data blocks
    public static final byte DIRECTORY = 1;
    public static final byte FILE = 2;
    // Inode number
    // 4 bytes
    private int iNode;
    // Record length
    // 2 bytes
    private short recLen;
    // Name length
    // 1 byte
    private byte nameLen;
    // File type (dir or file)
    // 1 byte
    private byte fileType;
    // File name (Max 255 chars)
    // 0-255 bytes
    private String filename;

    public DirectoryEntry(int inode, String name, byte type) {
        iNode = inode;
        fileType = type;
        filename = name;
        // Fix me? If filename.length > 127 the byte will overflow...
        // As long as filename.length is not > 255 the byte can still be recovered using Byte.toUnsignedInt() method
        // That's why file names can't be > 255 characters. That validation should be done in the console
        nameLen = (byte) filename.length();
        if (nameLen % 4 != 0) {
            // Not a multiple of 4, make it one by appending null terminators
            int nullsToAdd = 4 - (nameLen % 4);
            for (int i = 0; i < nullsToAdd; i++) {
                filename += '\0';
            }
        }
        // 8 bytes are needed for: inode, rec_len, name_len, and type
        // File name length varies
        recLen = (short) (8 + filename.length());
    }

    public int getInode() {
        return iNode;
    }

    public int getRecLen() {
        return recLen;
    }

    public int getNameLen() {
        return nameLen;
    }

    public int getType() {
        return fileType;
    }

    public String getFilename() {
        return filename.trim();
    }

    public void setiNode(int iNode) {
        this.iNode = iNode;
    }

    public void setRecLen(short recLen) {
        this.recLen = recLen;
    }

    public void setNameLen(byte nameLen) {
        this.nameLen = nameLen;
    }

    public void setFileType(byte fileType) {
        this.fileType = fileType;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    // This array should already contain the exact number of bytes this directory entry takes
    // Use rec_len to determine where each directory entry ends
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
        // Create a DirectoryEntry instance and return it
        DirectoryEntry dirEntry = new DirectoryEntry(inode, fileName, type);
        dirEntry.setNameLen(nameLen);
        dirEntry.setRecLen(recLen);
        return dirEntry;
    }

    // Byte array representation of a directory entry so we can write it back to disk
    public byte[] toByteArray() {
        final byte I_NODE[] = Util.toByteArray(iNode);
        final byte REC_LEN[] = Util.toByteArray(recLen);
        final byte NAME_LEN[] = Util.toByteArray(nameLen);
        final byte TYPE[] = Util.toByteArray(fileType);
        final byte FILE_NAME[] = filename.getBytes();
        return Bytes.concat(I_NODE, REC_LEN, NAME_LEN, TYPE, FILE_NAME);
    }
}
