package ext2;

import java.util.ArrayList;

public class Directory extends ArrayList<DirectoryBlock> {

    public Directory() {
        super();
    }

    public DirectoryEntry findEntry(String filename, int type) {
        for (DirectoryBlock block : this) {
            for (DirectoryEntry dirEntry : block) {
                if (dirEntry.getFilename().equals(filename) && dirEntry.getType() == type) {
                    return dirEntry;
                }
            }
        }
        return null;
    }

    public DirectoryBlock getBlockContaining(String name, int type) {
        for (DirectoryBlock block : this) {
            for (DirectoryEntry entry : block) {
                if (entry.getFilename().equals(name) && entry.getType() == type) {
                    return block;
                }
            }
        }
        return null;
    }

    // Returns the inode number of the "." dir_entry of this directory (self reference)
    public int getInode() {
        DirectoryBlock firstBlock = this.get(0);
        DirectoryEntry self = firstBlock.get(0);
        return self.getInode();
    }

    // Returns the inode number of the ".." dir_entry of this directory (parent reference)
    public int getParentInode() {
        DirectoryBlock firstBlock = this.get(0);
        DirectoryEntry parent = firstBlock.get(1);
        return parent.getInode();
    }

    public DirectoryBlock getLastBlock() {
        return get(size() - 1);
    }
}
