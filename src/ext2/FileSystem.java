package ext2;

import java.io.IOException;
import java.nio.ByteBuffer;

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
    // Data bitmap
    private final byte DATA_BITMAP[] = new byte[DATA_BITMAP_SIZE];
    private final byte INODE_BITMAP[] = new byte[INODE_BITMAP_SIZE];

    public FileSystem(Disk disk) {
        DISK = disk;
    }

    // Should be called once after the binary file is created and formatted
    public void initialize() throws IOException {
        // Create the root directory in the first block after the data offset
        Directory root = new Directory();
        // Next unused data block
        final int NEXT_DATA_BLOCK = Util.firstBitUnset(DATA_BITMAP);
        // Next unused inode index (from the inode table)
        final int NEXT_INODE = Util.firstBitUnset(INODE_BITMAP);

        Inode rootSelf = new Inode(Inode.DIRECTORY);
        rootSelf.addBlocks(NEXT_DATA_BLOCK);
        // ..
        Inode rootParent = new Inode(Inode.DIRECTORY);
        rootParent.addBlocks(0);
        root.add(new DirectoryEntry(0, ".", rootSelf.getType()));
        root.add(new DirectoryEntry(1, "..", rootParent.getType()));

        // Write root
        DISK.seek(DATA_OFFSET);
        //DISK.write(root.toByteArray());

        // Memory allocation
        // Allocate bitmaps to memory
        allocateBitmaps();

        // Allocate inode table to memory
    }

    public void format() throws IOException {
        // Fills disk with zeros
        final byte ZEROS[] = new byte[DISK.getSizeBytes()];
        DISK.seek(0);
        DISK.write(ZEROS);
        //initalize();
    }

    private void allocateBitmaps() throws IOException {
        // Data bitmap: desde byte 0 hasta el 8192
        DISK.seek(DATA_BITMAP_OFFSET);
        DISK.read(DATA_BITMAP);
        // Inode bitmap: desde byte 8192 hasta el 12288
        DISK.seek(INODE_BITMAP_OFFSET);
        DISK.read(INODE_BITMAP);
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
