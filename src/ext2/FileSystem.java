package ext2;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;

import java.io.IOException;

/**
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
    // Bitmaps
    private final byte DATA_BITMAP[] = new byte[DATA_BITMAP_SIZE];
    private final byte INODE_BITMAP[] = new byte[INODE_BITMAP_SIZE];
    // Current directory
    private Directory currentDirectory;

    // Constructor
    public FileSystem(Disk disk) {
        DISK = disk;
    }

    // Should be called once after the binary file is created and formatted
    private void initialize() throws IOException {
        System.out.println("Intitializing file system");
        Directory root = new Directory();
        currentDirectory = root;
        // Get first unset bit in bitmaps
        final int dataBlockIndex = Util.getThenToggleBit(false, DATA_BITMAP);
        final int inodeIndex = Util.getThenToggleBit(false, INODE_BITMAP);
        // Create . and .. directory entries
        Inode rootSelf = new Inode(Inode.DIRECTORY);
        rootSelf.addBlocks(dataBlockIndex);
        root.add(new DirectoryEntry(inodeIndex, ".", DirectoryEntry.DIRECTORY));
        root.add(new DirectoryEntry(inodeIndex, "..", DirectoryEntry.DIRECTORY));
        // Write bitmaps
        DISK.seek(DATA_BITMAP_OFFSET);
        DISK.write(DATA_BITMAP);
        DISK.write(INODE_BITMAP);
        // Write inode
        DISK.write(rootSelf.toByteArray());
        // Write root's directory entries (at data block 1)
        DISK.seek(DATA_OFFSET + (dataBlockIndex - 1) * BLOCK_SIZE_KB);
        for (DirectoryEntry dirEntry : root) DISK.write(dirEntry.toByteArray());
    }

    public void format() throws IOException {
        // Fills disk with zeros
        final byte ZEROS[] = new byte[DISK.getSizeBytes()];
        DISK.seek(0);
        DISK.write(ZEROS);
        initialize();
    }

    private void allocateBitmaps() throws IOException {
        DISK.seek(DATA_BITMAP_OFFSET);
        DISK.read(DATA_BITMAP);
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

    public Directory getCurrentDirectory() throws IOException {
        if (currentDirectory == null) {
            // Make root the current directory
            // Root is at data block 1
            currentDirectory = readDirectory(1);
        }
        return currentDirectory;
    }

    public void setCurrentDirectory(Directory directory) {
        currentDirectory = directory;
    }

    // Given a block index, read the directory entries from that block
    private Directory readDirectory(int blockIndex) throws IOException {
        Directory directory = new Directory();
        int dirEntryOffset = DATA_OFFSET + (blockIndex - 1) * BLOCK_SIZE_KB;
        byte inodeBytes[] = new byte[4];
        byte recLenBytes[] = new byte[2];
        int inode;
        short recLen;
        // Read inode
        DISK.seek(dirEntryOffset);
        DISK.read(inodeBytes);
        inode = Ints.fromByteArray(inodeBytes);
        while (inode != 0) {
            DISK.read(recLenBytes);
            recLen = Shorts.fromByteArray(recLenBytes);
            byte dirEntry[] = new byte[recLen];
            // Go back to the start of this directory entry
            DISK.seek(dirEntryOffset);
            // Read the whole recLen bytes
            DISK.read(dirEntry);
            directory.add(DirectoryEntry.fromByteArray(dirEntry));
            // Read next inode if any
            DISK.read(inodeBytes);
            inode = Ints.fromByteArray(inodeBytes);
            // Next directory entry offset
            dirEntryOffset += recLen;
        }
        return directory;
    }
}
