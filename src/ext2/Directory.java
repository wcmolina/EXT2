package ext2;

import java.io.IOException;
import java.util.ArrayList;

public class Directory extends ArrayList<DirectoryEntry> {

    // Adds the record length of each dir_entry contained in this directory
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

    public boolean contains(String fileName) {
        for (DirectoryEntry dirEntry : this) {
            if (dirEntry.getFilename().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    public DirectoryEntry getEntryByName(String fileName) {
        for (DirectoryEntry dirEntry : this) {
            if (dirEntry.getFilename().equals(fileName)) {
                return dirEntry;
            }
        }
        return null;
    }

    // Returns the inode number of the "." dir_entry of this directory (self reference)
    public int getInode() {
        int inodeNumber = 0;
        for (DirectoryEntry dirEntry : this) {
            if (dirEntry.getFilename().equals(".")) {
                return dirEntry.getInodeNumber();
            }
        }
        return inodeNumber;
    }

    // Returns the inode number of the ".." dir_entry of this directory (parent reference)
    public int getParentInode() throws IOException {
        int inodeNumber = 0;
        for (DirectoryEntry dirEntry : this) {
            if (dirEntry.getFilename().equals("..")) {
                return dirEntry.getInodeNumber();
            }
        }
        return inodeNumber;
    }
}
