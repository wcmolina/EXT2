package ext2;

import java.util.ArrayList;

public class DirectoryBlock extends ArrayList<DirectoryEntry> {

    private final int BLOCK;

    public DirectoryBlock(int block) {
        this.BLOCK = block;
    }

    public void addEntry(DirectoryEntry dirEntry) {
        if (this.isEmpty()) {
            // First directory entry: its rec_len is equal to the block size
            dirEntry.setRecLen((short) (FileSystem.BLOCK_SIZE));
            this.add(dirEntry);
        } else {
            int remaining = getRemainingLength();

            // Change the previous dir_entry's rec_len to its ideal_len
            DirectoryEntry lastDirEntry = this.getLastEntry();
            lastDirEntry.setRecLen(lastDirEntry.getIdealLen());

            // The new dir_entry's rec_len is equal to the remaining bytes before the block fills up
            dirEntry.setRecLen((short) remaining);
            this.add(dirEntry);
        }
    }

    // Returns the sum of all the rec_len from 0 to 'index'
    public int getOffset(int index) {
        if (index == 0 || index >= this.size()) {
            return 0;
        }
        int recLen = 0;
        for (int i = 0; i < index; i++) {
            recLen += this.get(i).getRecLen();
        }
        return recLen;
    }

    // Returns how many bytes are left before the block fills up (gets to 4KB)
    public int getRemainingLength() {
        DirectoryEntry lastEntry = this.getLastEntry();
        int lastRecLen = lastEntry.getRecLen();
        int lastIdealLen = lastEntry.getIdealLen();
        return lastRecLen - lastIdealLen;
    }

    public int getLength() {
        return (FileSystem.BLOCK_SIZE) - getRemainingLength();
    }

    public DirectoryEntry getLastEntry() {
        return get(size() - 1);
    }

    public int getBlock() {
        return BLOCK;
    }

    // Returns true if this block contains entries other than the default . and ..
    public boolean hasEntries() {
        for (DirectoryEntry entry : this) {
            if (entry.getFilename().equals(".") || entry.getFilename().equals("..")) continue;
            return true;
        }
        return false;
    }
}
