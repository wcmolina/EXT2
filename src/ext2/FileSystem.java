package ext2;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;

import java.io.IOException;
import java.util.ArrayList;

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
    // Root directory
    private Directory root;
    private InodeTable inodeTable;

    // Constructor
    public FileSystem(Disk disk) throws IOException {
        DISK = disk;
        initialize();
    }

    // Initialize structures stored in disk into memory
    private void initialize() throws IOException {
        root = readDirectory(1);
        // Load bitmaps to memory
        allocateBitmaps();
        // Load inode table
        allocateInodeTable();
    }

    // Should be called only once after formatting the disk
    public void writeRootDirectory() throws IOException {
        final int directoryBlock = Util.getThenToggleBit(false, DATA_BITMAP);
        final int inodeIndex = Util.getThenToggleBit(false, INODE_BITMAP);
        // Create . and .. directory entries
        Inode rootSelf = new Inode(inodeIndex, Inode.DIRECTORY);
        rootSelf.addBlockPointers(directoryBlock);
        root = new Directory(directoryBlock);
        root.add(new DirectoryEntry(inodeIndex, ".", DirectoryEntry.DIRECTORY));
        root.add(new DirectoryEntry(inodeIndex, "..", DirectoryEntry.DIRECTORY));
        // Write bitmaps to disk
        saveBitmaps();
        // Write root's inode and directory entries to disk
        DISK.write(rootSelf.toByteArray());
        DISK.seek(getDataBlockOffset(directoryBlock));
        for (DirectoryEntry dirEntry : root)
            DISK.write(dirEntry.toByteArray());
    }

    public void format() throws IOException {
        final byte ZEROS[] = new byte[DISK.getSizeBytes()];
        DISK.seek(0);
        DISK.write(ZEROS);
        writeRootDirectory();
    }

    private void allocateBitmaps() throws IOException {
        DISK.seek(DATA_BITMAP_OFFSET);
        DISK.read(DATA_BITMAP);
        DISK.read(INODE_BITMAP);
    }

    private void allocateInodeTable() throws IOException {
        byte inode[] = new byte[64];
        inodeTable = new InodeTable();
        // Get all indexes already taken in the inode bitmap
        ArrayList<Integer> list = Util.findAllBits(true, INODE_BITMAP);
        // Search these indexes in the disk
        for (int index : list) {
            DISK.seek(getInodeOffset(index));
            DISK.read(inode);
            inodeTable.add(Inode.fromByteArray(inode, index));
        }
    }

    private void saveBitmaps() throws IOException {
        DISK.seek(DATA_BITMAP_OFFSET);
        DISK.write(DATA_BITMAP);
        DISK.write(INODE_BITMAP);
    }

    // Given a block index, read the directory entries from that block
    public Directory readDirectory(int blockIndex) throws IOException {
        Directory directory = new Directory(blockIndex);
        // The offset where the list of directoy entries start
        int dirEntryOffset = getDataBlockOffset(blockIndex);
        byte inodeBytes[] = new byte[4];
        byte recLenBytes[] = new byte[2];
        int inode;
        short recLen;
        // Get the first directory entry in this block and read its inode
        DISK.seek(dirEntryOffset);
        DISK.read(inodeBytes);
        inode = Ints.fromByteArray(inodeBytes);
        while (inode != 0) {
            DISK.read(recLenBytes);
            recLen = Shorts.fromByteArray(recLenBytes);
            // The number of bytes this directory entry has is determined by recLen
            byte dirEntry[] = new byte[recLen];
            // Go back to the start of this directory entry and read all its bytes
            DISK.seek(dirEntryOffset);
            DISK.read(dirEntry);
            directory.add(DirectoryEntry.fromByteArray(dirEntry));
            // Read the next directory entry inode (if any)
            DISK.read(inodeBytes);
            inode = Ints.fromByteArray(inodeBytes);
            // Update the directory entry offset for the next one (if any)
            dirEntryOffset += recLen;
        }
        return directory;
    }

    public void createFile(String fileName, String content) throws IOException {
        // FIX ME: Check if path provided exists
        // Split file's bytes into groups of 4KB and write each one to disk (one block per group)
        byte contentBytes[][] = Util.splitBytes(content.getBytes(), 4096);
        int fileBlocks[] = new int[contentBytes.length];
        for (int i = 0; i < contentBytes.length; i++) {
            byte[] group = contentBytes[i];
            int blockNumber = Util.getThenToggleBit(false, DATA_BITMAP);
            fileBlocks[i] = blockNumber;
            DISK.seek(getDataBlockOffset(blockNumber));
            DISK.write(group);
        }
        // Create a new inode for this file and write it to disk
        int inodeNumber = Util.getThenToggleBit(false, INODE_BITMAP);
        Inode inode = new Inode(inodeNumber, Inode.FILE, content.length());
        inode.addBlockPointers(fileBlocks);
        inodeTable.add(inode);
        DISK.seek(getInodeOffset(inodeNumber));
        DISK.write(inode.toByteArray());
        // Create a directory entry for this file
        // FIX ME: it is hard coded to save it in root only
        DirectoryEntry dirEntry = new DirectoryEntry(inodeNumber, fileName, DirectoryEntry.FILE);
        // Write the directory entry at the end of the directory
        DISK.seek(getDataBlockOffset(root.getBlockNumber()) + root.getTotalLength());
        DISK.write(dirEntry.toByteArray());
        root.add(dirEntry);
        saveBitmaps();
    }

    public byte[] retrieveFile(String fileName) throws IOException {
        int inodeNumber = 0;
        for (DirectoryEntry dirEntry : root) {
            if (dirEntry.getFilename().equals(fileName)) {
                inodeNumber = dirEntry.getInode();
                break;
            }
        }
        if (inodeNumber == 0) {
            // File not found
            return null;
        }
        int fileBlocks[] = null;
        int fileSize = 0;
        for (Inode inode : inodeTable) {
            if (inode.getInode() == inodeNumber) {
                fileBlocks = inode.getUsedPointers();
                fileSize = inode.getSize();
                break;
            }
        }
        if (fileBlocks == null) {
            // ???
            return null;
        }
        byte data[] = new byte[fileSize];
        if (fileBlocks.length == 1) {
            DISK.seek(getDataBlockOffset(fileBlocks[0]));
            DISK.read(data);
            return data;
        }
        int offset = 0;
        // Read up to 4096 bytes per block. If file size is less than 4096, then read up to file size
        int len = (fileSize > 4096) ? 4096 : fileSize;
        for (int block : fileBlocks) {
            DISK.seek(getDataBlockOffset(block));
            DISK.read(data, offset, len);
            // Read next block data
            offset += 4096;
            len = (len + 4096 > fileSize) ? fileSize - len : len + 4096;
        }
        return data;
    }

    public int getBlockSizeBytes() {
        return BLOCK_SIZE_KB * 1024;
    }

    private int getDataBlockOffset(int blockNumber) {
        return DATA_OFFSET + (blockNumber - 1) * getBlockSizeBytes();
    }

    private int getInodeOffset(int inode) {
        return INODE_TABLE_OFFSET + (inode - 1) * 64;
    }

    public Directory getRootDirectory() {
        return root;
    }
}