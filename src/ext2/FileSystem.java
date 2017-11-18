package ext2;

import java.io.IOException;

/**
 *
 * @author Wilmer
 */
public class FileSystem {

    private final Disk DISK;
    // Disk block size in KB
    private final int BLOCK_SIZE_KB = 4;
    // Blocks per group
    private final int DATA_BITMAP_BLOCKS = 2;
    private final int INODE_BITMAP_BLOCKS = 1;
    private final int INODE_TABLE_BLOCKS = 16;
    // Size per group
    private final int DATA_BITMAP_SIZE = DATA_BITMAP_BLOCKS * BLOCK_SIZE_KB * 1024; // 8192 bytes
    private final int INODE_BITMAP_SIZE = INODE_BITMAP_BLOCKS * BLOCK_SIZE_KB * 1024; // 4096 bytes
    private final int INODE_TABLE_SIZE = INODE_TABLE_BLOCKS * BLOCK_SIZE_KB * 1024; // 65536 bytes
    // Offset per group
    private final int DATA_BITMAP_OFFSET = 0;
    private final int INODE_BITMAP_OFFSET = DATA_BITMAP_SIZE; // byte 8192
    private final int INODE_TABLE_OFFSET = INODE_BITMAP_OFFSET + INODE_BITMAP_SIZE; // byte 12288
    private final int DATA_OFFSET = INODE_TABLE_OFFSET + INODE_TABLE_SIZE; // byte 77824

    public FileSystem(Disk disk) {
        DISK = disk;
    }

    public void format() throws IOException {
        // Fills disk with zeros
        final byte ZEROS[] = new byte[DISK.getSizeBytes()];
        DISK.seek(0);
        DISK.write(ZEROS);
    }

    public int getDataBitmapOffset() {
        return DATA_BITMAP_OFFSET;
    }

    public int getInodeBitmapOffset() {
        return INODE_BITMAP_OFFSET;
    }

    public int getInodeTableOffset() {
        return INODE_TABLE_OFFSET;
    }

    public int getDataOffset() {
        return DATA_OFFSET;
    }
}
