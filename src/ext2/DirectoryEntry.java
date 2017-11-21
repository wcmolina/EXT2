package ext2;

/**
 *
 * @author Wilmer
 */
public class DirectoryEntry {
    // A directory is just a list of inodes and filenames (list of directory entries)
    // The inode points to the rest of the file's metadata, including the pointers to the data blocks
    // Inode number
    // 4 bytes
    private final int I_NODE;
    // Record length
    // 2 bytes
    private final int REC_LEN;
    // Name length
    // 1 byte
    private final int NAME_LEN;
    // File type (dir or file)
    // 1 byte
    private final int FILE_TYPE;
    // File name (Max 255 chars)
    // 0-255 bytes
    private String filename;

    public DirectoryEntry(int inode, String name, int type) {
        filename = name;
        I_NODE = inode;
        FILE_TYPE = type;
        NAME_LEN = filename.length();
        if (NAME_LEN % 4 != 0) {
            // Not a multiple of 4, make it one by appending null terminators
            int nullsToAdd = 4 - (NAME_LEN % 4);
            for (int i = 0; i < nullsToAdd; i++) {
                filename += '\0';
            }
        }
        REC_LEN = 8 + filename.length();
    }
}
