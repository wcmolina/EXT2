package ext2;

import java.util.LinkedList;

public class InodeTable extends LinkedList<Inode> {

    public InodeTable() {
        super();
    }

    public Inode findInode(int inodeNumber) {
        for (Inode inode : this) {
            if (inode.getInode() == inodeNumber) {
                return inode;
            }
        }
        return null;
    }
}
