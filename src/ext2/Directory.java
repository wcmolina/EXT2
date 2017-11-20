package ext2;

/**
 *
 * @author Wilmer
 */
public class Directory {
    // A directory is just a list of inodes and filenames
    // The inode points to the rest of the file's metadata, including the pointers to the data blocks
    private final int I_NODE;
    private String filename;
    //private final int REC_LEN; record length. Needed?
    //private final int NAME_LEN; filename length. Needed? este creo que si

    public Directory(int inode, String name) {
        I_NODE = inode;
        filename = name;
    }
}
