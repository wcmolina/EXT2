package ext2;

import java.util.LinkedList;

public class InodeTable extends LinkedList<Inode> {

    public InodeTable() {
        super();
    }

    public Inode getByInodeNumber(int inodeNumber) {
        for (Inode inode : this) {
            if (inode.getInodeNumber() == inodeNumber) {
                return inode;
            }
        }
        return null;
    }
}
