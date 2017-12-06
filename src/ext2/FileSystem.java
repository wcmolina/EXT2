package ext2;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.util.ArrayList;

public class FileSystem {

    private final Disk DISK;

    // Disk block size in KB
    public static final int BLOCK_SIZE = 4096;

    // Blocks per group
    private final int DATA_BITMAP_BLOCKS = 2;
    private final int INODE_BITMAP_BLOCKS = 1;
    private final int INODE_TABLE_BLOCKS = 16;

    // Size per group
    private final int DATA_BITMAP_SIZE = DATA_BITMAP_BLOCKS * BLOCK_SIZE; // 8192 bytes
    private final int INODE_BITMAP_SIZE = INODE_BITMAP_BLOCKS * BLOCK_SIZE; // 4096 bytes
    private final int INODE_TABLE_SIZE = INODE_TABLE_BLOCKS * BLOCK_SIZE; // 65536 bytes

    // Offset per group
    private final int DATA_BITMAP_OFFSET = 0;
    private final int INODE_BITMAP_OFFSET = DATA_BITMAP_SIZE; // byte 8192
    private final int INODE_TABLE_OFFSET = INODE_BITMAP_OFFSET + INODE_BITMAP_SIZE; // byte 12288
    private final int DATA_OFFSET = INODE_TABLE_OFFSET + INODE_TABLE_SIZE; // byte 77824

    // Bitmaps
    private final byte DATA_BITMAP[] = new byte[DATA_BITMAP_SIZE];
    private final byte INODE_BITMAP[] = new byte[INODE_BITMAP_SIZE];

    private Directory currentDir;
    private InodeTable inodeTable;

    // String currentPath (start at root dir)
    private String currentPath = "/";

    public FileSystem(Disk disk) {
        DISK = disk;
    }

    // Get the structures from disk and allocate them to memory
    public void load() throws IOException {
        if (currentDir == null) {
            // Load bitmaps and inode table to memory
            allocateBitmaps();
            allocateInodeTable();

            // Read the root directory
            currentDir = getRoot();
        }
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
        ArrayList<Integer> usedInodes = BitUtils.findAllSetBits(INODE_BITMAP);
        for (int index : usedInodes) {
            DISK.seek(getInodeOffset(index));
            DISK.read(inode);
            inodeTable.add(Inode.fromByteArray(inode, index));
        }
    }

    public void format() throws IOException {
        final byte ZEROS[] = new byte[DISK.getSizeBytes()];
        DISK.seek(0);
        DISK.write(ZEROS);

        // Create the first directory (root)
        int dirBlock = BitUtils.nextClearBitThenSet(DATA_BITMAP);
        int dirInode = BitUtils.nextClearBitThenSet(INODE_BITMAP);

        // Create an inode for root
        Inode inode = new Inode(dirInode, Inode.DIRECTORY);
        inode.addBlock(dirBlock);
        inodeTable = new InodeTable();
        inodeTable.add(inode);

        // Create . and .. directory entries
        DirectoryBlock block = new DirectoryBlock(dirBlock);
        DirectoryEntry self, parent;
        self = new DirectoryEntry(dirInode, ".", DirectoryEntry.DIRECTORY);
        parent = new DirectoryEntry(dirInode, "..", DirectoryEntry.DIRECTORY);
        block.addEntry(self);
        block.addEntry(parent);
        currentDir = new Directory();
        currentDir.add(block);

        // Write the directory inode and its entries to disk
        DISK.seek(getInodeOffset(dirInode));
        DISK.write(inode.toByteArray());
        DISK.seek(getDataBlockOffset(dirBlock));
        DISK.write(self.toByteArray());
        DISK.write(parent.toByteArray());

        // Save data and inode bitmaps to disk
        saveBitmaps();
    }

    private void saveBitmaps() throws IOException {
        DISK.seek(DATA_BITMAP_OFFSET);
        DISK.write(DATA_BITMAP);
        DISK.write(INODE_BITMAP);
    }

    public void writeDirectory(String name) throws IOException {
        int dirInode = BitUtils.nextClearBitThenSet(INODE_BITMAP);

        // Only the last block is writable, the previous ones should be full of dir_entries
        DirectoryBlock lastBlock = currentDir.getLastBlock();

        // Add the new directory as a dir_entry in the current one. Check if it fits in the last used block
        DirectoryEntry dirEntry = new DirectoryEntry(dirInode, name, DirectoryEntry.DIRECTORY);
        if (lastBlock.getRemainingLength() >= dirEntry.getIdealLen()) {
            DirectoryEntry prevEntry = lastBlock.getLastEntry();
            int lastEntryOffset = lastBlock.getLength() - prevEntry.getIdealLen();
            lastBlock.addEntry(dirEntry);

            // Write the previous dir_entry (because its rec_len was modified in addEntry()) and the new dir_entry to disk
            DISK.seek(getDataBlockOffset(lastBlock.getBlock()) + lastEntryOffset);
            DISK.write(prevEntry.toByteArray());
            DISK.write(dirEntry.toByteArray());
        } else {
            // The new dir_entry doesn't fit in the block, create a new one
            int newBlock = BitUtils.nextClearBitThenSet(DATA_BITMAP);
            Inode inode = inodeTable.findInode(currentDir.getInode());
            inode.addBlock(newBlock);

            DirectoryBlock block = new DirectoryBlock(newBlock);
            block.addEntry(dirEntry);
            currentDir.add(block);

            // Write the current directory inode to disk (to update it)
            int inodeOffset = getInodeOffset(inode.getInode());
            DISK.seek(inodeOffset);
            DISK.write(inode.toByteArray());

            // Write the new dir_entry to disk, in the newly assigned block
            DISK.seek(getDataBlockOffset(newBlock));
            DISK.write(dirEntry.toByteArray());
        }

        // Get the next available block for the new directory and create its inode
        int dirBlock = BitUtils.nextClearBitThenSet(DATA_BITMAP);
        Inode inode = new Inode(dirInode, Inode.DIRECTORY);
        inode.addBlock(dirBlock);
        inodeTable.add(inode);

        // The parent of the new directory is going to be the current directory
        int parentInode = currentDir.getInode();

        // Create . and .. directory entries for the new directory
        DirectoryEntry self, parent;
        self = new DirectoryEntry(dirInode, ".", DirectoryEntry.DIRECTORY);
        parent = new DirectoryEntry(parentInode, "..", DirectoryEntry.DIRECTORY);
        DirectoryBlock block = new DirectoryBlock(dirBlock);
        block.addEntry(self);
        block.addEntry(parent);

        // Write the directory inode and its entries to disk
        DISK.seek(getInodeOffset(dirInode));
        DISK.write(inode.toByteArray());
        DISK.seek(getDataBlockOffset(dirBlock));
        DISK.write(self.toByteArray());
        DISK.write(parent.toByteArray());

        // Save data and inode bitmaps to disk
        saveBitmaps();
    }

    // Given a block index, read the directory entries from that block
    public DirectoryBlock readDirectoryBlock(int blockIndex) throws IOException {
        DirectoryBlock block = new DirectoryBlock(blockIndex);
        int entryOffset = getDataBlockOffset(blockIndex);
        byte recLenBytes[] = new byte[2];
        byte nameLenBytes[] = new byte[1];
        short recLen;
        byte nameLen;
        int idealLen;

        // This will determine when to stop reading a block (when the sum of all the rec_len equals 4096)
        int bytesRead = 0;

        while (bytesRead != BLOCK_SIZE) {
            // Read de dir_entry's rec_len (skip the inode's 4 bytes)
            DISK.seek(entryOffset + 4);
            DISK.read(recLenBytes);
            recLen = Shorts.fromByteArray(recLenBytes);
            DISK.read(nameLenBytes);
            nameLen = nameLenBytes[0];
            idealLen = (4 * ((8 + nameLen + 3) / 4));

            // The number of bytes this dir_entry has is determined by its ideal_len
            byte dirEntry[] = new byte[idealLen];

            // Go back to the start of this dir_entry and read all its bytes
            DISK.seek(entryOffset);
            DISK.read(dirEntry);
            block.add(DirectoryEntry.fromByteArray(dirEntry));

            // Update the dir_entry offset for the next one (if any)
            entryOffset += recLen;
            bytesRead += recLen;
        }
        return block;
    }

    // Go through every directory in the path
    // Path may be in the form: /dir1/dir2/dir3/
    // This method would return the directory dir3 (if the path exists)
    // Used for cd <path>. For example: cd /usr/bin/ or cd home/documents/work
    public Directory readDirectoryBlock(String path) throws IOException {
        // Used to restore the path in case this method throws an exception while building the path
        String rollbackPath = getCurrentPath();
        Directory initialDir;

        // If path starts with "/", begin from the root directory
        if (path.startsWith("/")) {
            initialDir = getRoot();
            currentPath = "/";
        } else
            initialDir = currentDir;

        ArrayList<String> directories = Utils.splitPath(path);
        for (String name : directories) {
            // First we need to find the dir_entry to get the directory's inode (the one we are trying to find)
            // Once we get the inode, we can know where are the blocks of that directory
            DirectoryEntry entry = initialDir.findEntry(name);
            if (entry != null) {
                if (entry.getType() == DirectoryEntry.DIRECTORY) {
                    Directory directory = new Directory();
                    ArrayList<Integer> dirBlocks;
                    int inodeNumber = entry.getInode();
                    Inode inode = inodeTable.findInode(inodeNumber);
                    dirBlocks = inode.getBlocks();

                    // Go through each block and read their dir_entries
                    for (int block : dirBlocks) {
                        directory.add(readDirectoryBlock(block));
                    }

                    initialDir = directory;
                    currentPath = FilenameUtils.concat(getCurrentPath(), name.concat("/"));
                } else {
                    // It is a file so it doesn't have directory entries
                    currentPath = rollbackPath;
                    return null;
                }
            } else {
                currentPath = rollbackPath;
                return null;
            }
        }
        currentDir = initialDir;
        return initialDir;
    }

    // Saves the text into available data blocks, and then creates the dir_entry and the inode for the file
    public void writeFile(String fileName, String content) throws IOException {
        // Split file's bytes into groups of 4KB and write each one to disk (one block per group)
        byte contentBytes[][] = BitUtils.splitBytes(content.getBytes(), 4096);
        int fileBlocks[] = new int[contentBytes.length];
        for (int i = 0; i < contentBytes.length; i++) {
            byte[] group = contentBytes[i];
            int blockNumber = BitUtils.nextClearBitThenSet(DATA_BITMAP);
            fileBlocks[i] = blockNumber;
            DISK.seek(getDataBlockOffset(blockNumber));
            DISK.write(group);
        }

        // Create a new inode for this file and write it to disk
        int inodeNumber = BitUtils.nextClearBitThenSet(INODE_BITMAP);
        Inode inode = new Inode(inodeNumber, Inode.FILE, content.length());
        inode.addBlock(fileBlocks);
        inodeTable.add(inode);
        DISK.seek(getInodeOffset(inodeNumber));
        DISK.write(inode.toByteArray());

        // Create a dir_entry for this file and add it to the block
        DirectoryEntry dirEntry = new DirectoryEntry(inodeNumber, fileName, DirectoryEntry.FILE);

        // Only the last block is writable, the previous ones should be full of dir_entries
        DirectoryBlock lastBlock = currentDir.getLastBlock();

        // Check if the new dir_entry fits in the directory's last block
        if (lastBlock.getRemainingLength() >= dirEntry.getIdealLen()) {
            DirectoryEntry prevEntry = lastBlock.getLastEntry();
            int lastEntryOffset = lastBlock.getLength() - prevEntry.getIdealLen();
            lastBlock.addEntry(dirEntry);

            // Write the previous dir_entry (because its rec_len was modified in addEntry()) and the new dir_entry to disk
            DISK.seek(getDataBlockOffset(lastBlock.getBlock()) + lastEntryOffset);
            DISK.write(prevEntry.toByteArray());
            DISK.write(dirEntry.toByteArray());
        } else {
            // The new dir_entry doesn't fit in the block, create a new one
            int newBlock = BitUtils.nextClearBitThenSet(DATA_BITMAP);
            Inode currentDirInode = inodeTable.findInode(currentDir.getInode());
            currentDirInode.addBlock(newBlock);

            DirectoryBlock block = new DirectoryBlock(newBlock);
            block.addEntry(dirEntry);
            currentDir.add(block);

            // Write the current directory inode to disk (to update it)
            int inodeOffset = getInodeOffset(currentDirInode.getInode());
            DISK.seek(inodeOffset);
            DISK.write(currentDirInode.toByteArray());

            // Write the new dir_entry to disk, in the newly assigned block
            DISK.seek(getDataBlockOffset(newBlock));
            DISK.write(dirEntry.toByteArray());
        }

        saveBitmaps();
    }

    // Given a file name, searches for the file in the current directory, and returns the data in the data blocks
    public byte[] readFile(String fileName) throws IOException {
        int inode = 0;
        try {
            inode = currentDir.findEntry(fileName).getInode();
        } catch (NullPointerException npe) {
            // File not found
            return null;
        }

        ArrayList<Integer> fileBlocks = null;
        int fileSize = 0;
        Inode fileInode = inodeTable.findInode(inode);
        fileBlocks = fileInode.getBlocks();
        fileSize = fileInode.getSize();

        byte data[] = new byte[fileSize];
        if (fileBlocks.size() == 1) {
            DISK.seek(getDataBlockOffset(fileBlocks.get(0)));
            DISK.read(data);
            return data;
        }
        // Start writing at position 0 of the array, and read up to 4096 bytes per block
        int offset = 0;
        int len = 4096;
        for (int block : fileBlocks) {
            DISK.seek(getDataBlockOffset(block));
            DISK.read(data, offset, len);

            // Next block data
            offset += 4096;
            len = (len + 4096 > fileSize) ? fileSize - len : len + 4096;
        }
        return data;
    }

    // Calculate the data offset of the given data block number
    private int getDataBlockOffset(int blockNumber) {
        return DATA_OFFSET + (blockNumber - 1) * BLOCK_SIZE;
    }

    // Calculate the inode offset of the given inode index
    private int getInodeOffset(int inode) {
        return INODE_TABLE_OFFSET + (inode - 1) * 64;
    }

    public Directory getCurrentDirectory() {
        return currentDir;
    }

    public String getCurrentPath() {
        return currentPath == null ? "/" : FilenameUtils.separatorsToUnix(currentPath);
    }

    public InodeTable getInodeTable() {
        return inodeTable;
    }

    public Directory getRoot() throws IOException {
        Directory root = new Directory();
        Inode rootInode = inodeTable.findInode(1);
        for (int block : rootInode.getBlocks()) {
            root.add(readDirectoryBlock(block));
        }
        return root;
    }
}