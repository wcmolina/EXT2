package ext2;

import java.util.LinkedList;

/**
 *
 * @author Wilmer
 */
public class Directory extends LinkedList<DirectoryEntry> {

    // Adds the record length of each directory entry contained in this directory
    private int totalLength;
    // Block number where the directory is saved along with its directory entries
    private int blockNumber;

    public Directory(int blockNumber) {
        super();
        this.blockNumber = blockNumber;
    }

    public int getTotalLength() {
        int total = 0;
        for (DirectoryEntry directoryEntry : this) {
            total += directoryEntry.getRecLen();
        }
        return total;
    }

    public int getBlockNumber() {
        return blockNumber;
    }
}
