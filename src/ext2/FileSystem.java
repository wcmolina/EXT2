package ext2;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static java.lang.Math.toIntExact;

public class FileSystem {

    private final Disk DISK;

    // Disk block size in KB
    public static final int BLOCK_SIZE = 4096;

    // Blocks per group
    private final int DATA_BITMAP_BLOCKS = 2;
    private final int INODE_BITMAP_BLOCKS = 1;
    private final int INODE_TABLE_BLOCKS = 20;

    // Size per group
    private final int DATA_BITMAP_SIZE = DATA_BITMAP_BLOCKS * BLOCK_SIZE; // 8192 bytes
    private final int INODE_BITMAP_SIZE = INODE_BITMAP_BLOCKS * BLOCK_SIZE; // 4096 bytes
    private final int INODE_TABLE_SIZE = INODE_TABLE_BLOCKS * BLOCK_SIZE; // 81920 bytes

    // Offset per group
    private final int DATA_BITMAP_OFFSET = 0;
    private final int INODE_BITMAP_OFFSET = DATA_BITMAP_SIZE; // byte 8192
    private final int INODE_TABLE_OFFSET = INODE_BITMAP_OFFSET + INODE_BITMAP_SIZE; // byte 12288
    private final int DATA_OFFSET = INODE_TABLE_OFFSET + INODE_TABLE_SIZE; // byte 94208

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
        byte inodeBytes[] = new byte[80];
        inodeTable = new InodeTable();
        Inode inode;

        // Get all indexes already taken in the inode bitmap
        // ArrayList<Integer> usedInodes = BitUtils.findAllSetBits(INODE_BITMAP);
        int totalInodes = 1024;
        for (int index = 1; index <= totalInodes; index++) {
            DISK.seek(getInodeOffset(index));
            DISK.read(inodeBytes);
            inode = Inode.fromByteArray(inodeBytes, index);
            if (inode != null)
                inodeTable.put(index, inode);
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
        inode.addBlocks(dirBlock);
        inodeTable = new InodeTable();
        inodeTable.put(dirInode, inode);

        // Create . and .. directory entries
        DirectoryBlock block = new DirectoryBlock(dirBlock);
        DirectoryEntry self, parent;
        self = new DirectoryEntry(dirInode, DirectoryEntry.DIRECTORY, ".");
        parent = new DirectoryEntry(dirInode, DirectoryEntry.DIRECTORY, "..");
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
        DirectoryEntry dirEntry = new DirectoryEntry(dirInode, DirectoryEntry.DIRECTORY, name);
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
            Inode inode = inodeTable.get(currentDir.getInode());
            inode.addBlocks(newBlock);

            DirectoryBlock block = new DirectoryBlock(newBlock);
            block.addEntry(dirEntry);
            currentDir.add(block);

            // Write the current directory inode to disk (to update it)
            DISK.seek(getInodeOffset(inode.getInode()));
            DISK.write(inode.toByteArray());

            // Write the new dir_entry to disk, in the newly assigned block
            DISK.seek(getDataBlockOffset(newBlock));
            DISK.write(dirEntry.toByteArray());
        }

        // Get the next available block for the new directory and create its inode
        int dirBlock = BitUtils.nextClearBitThenSet(DATA_BITMAP);
        Inode inode = new Inode(dirInode, Inode.DIRECTORY);
        inode.addBlocks(dirBlock);
        inodeTable.put(dirInode, inode);

        // The parent of the new directory is going to be the current directory
        int parentInode = currentDir.getInode();

        // Create . and .. directory entries for the new directory
        DirectoryEntry self, parent;
        self = new DirectoryEntry(dirInode, DirectoryEntry.DIRECTORY, ".");
        parent = new DirectoryEntry(parentInode, DirectoryEntry.DIRECTORY, "..");
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

        // Byte arrays
        byte inodeBytes[] = new byte[4];
        byte recLenBytes[] = new byte[2];
        byte filenameBytes[];

        int inode, idealLen;
        short recLen;
        byte nameLen, type;
        String name;

        // This will determine when to stop reading a block (when the sum of all the rec_len equals 4096)
        int recLenCount = 0;

        DISK.seek(getDataBlockOffset(blockIndex));
        while (recLenCount != BLOCK_SIZE) {
            // Read dir_entry attributes from disk
            DISK.read(inodeBytes);
            DISK.read(recLenBytes);
            nameLen = DISK.readByte();
            type = DISK.readByte();

            inode = Ints.fromByteArray(inodeBytes);
            recLen = Shorts.fromByteArray(recLenBytes);

            // Read the file name bytes
            idealLen = (4 * ((8 + nameLen + 3) / 4));
            filenameBytes = new byte[idealLen - 8];
            DISK.read(filenameBytes);
            name = new String(filenameBytes);

            // Check if the entry has been deleted (if the deletion time is set in its inode)
            Inode entryInode = inodeTable.get(inode);
            if (entryInode.getDeletionTime() == 0) {
                DirectoryEntry entry = new DirectoryEntry(inode, recLen, type, name);
                block.add(entry);
                recLenCount += recLen;
                DISK.seek(getDataBlockOffset(blockIndex) + recLenCount);
            }
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
            DirectoryEntry entry = initialDir.findEntry(name, DirectoryEntry.DIRECTORY);
            if (entry != null) {
                if (entry.getType() == DirectoryEntry.DIRECTORY) {
                    Directory directory = new Directory();
                    ArrayList<Integer> dirBlocks;
                    int inodeNumber = entry.getInode();
                    Inode inode = inodeTable.get(inodeNumber);
                    dirBlocks = inode.getUsedDirectBlocks();

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

    // Remove a dir_entry from the current directory
    // Returns true if the entry was 'deleted' successfully, false otherwise
    public boolean removeEntry(String name, int type) throws IOException, IllegalArgumentException {
        DirectoryBlock block;
        DirectoryEntry entry;
        Inode inode;
        if ((block = currentDir.getBlockContaining(name, type)) != null) {
            for (int i = 0; i < block.size(); i++) {
                entry = block.get(i);
                if (entry.getFilename().equals(name) && entry.getType() == type) {
                    // This entry's inode
                    inode = inodeTable.get(entry.getInode());

                    // If it is a directory, check if it is empty
                    if (type == DirectoryEntry.DIRECTORY) {
                        for (int index : inode.getUsedDirectBlocks()) {
                            if (readDirectoryBlock(index).hasEntries()) {
                                throw new IllegalArgumentException("Directory is not empty. Cannot delete it");
                            }
                        }
                    }

                    // Clear the bits used by the dir_entry in the data bitmap
                    for (int index : inode.getUsedDirectBlocks()) {
                        BitUtils.clearBit(index, DATA_BITMAP);
                    }

                    // Check if it has an indirect pointer
                    int indirectPointer = inode.getIndirectPointer();
                    ArrayList<Integer> references = null;
                    if (indirectPointer != 0) {
                        references = new ArrayList<>();
                        byte blockBytes[] = new byte[4];
                        int reference;
                        DISK.seek(getDataBlockOffset(indirectPointer));
                        DISK.read(blockBytes);
                        reference = Ints.fromByteArray(blockBytes);
                        while (reference != 0) {
                            references.add(reference);
                            DISK.read(blockBytes);
                            reference = Ints.fromByteArray(blockBytes);
                        }
                        BitUtils.clearBit(indirectPointer, DATA_BITMAP);
                    }

                    if (references != null) {
                        for (int index : references) {
                            BitUtils.clearBit(index, DATA_BITMAP);
                        }
                    }

                    // Clear the bit of this inode in the inode bitmap and set its deletion time, then write it to disk
                    BitUtils.clearBit(inode.getInode(), INODE_BITMAP);
                    inode.setDeletionTime(toIntExact(System.currentTimeMillis() / 1000));
                    DISK.seek(getInodeOffset(inode.getInode()));
                    DISK.write(inode.toByteArray());

                    if (i == 0) {
                        DISK.seek(getDataBlockOffset(block.getBlock()));
                    } else {
                        // Update and write to disk the rec_len of the previous entry so it can 'absorb' the 'deleted' entry
                        DirectoryEntry previous = block.get(i - 1);
                        int recLen = previous.getRecLen() + entry.getRecLen();
                        int prevOffset = block.getOffset(i - 1);
                        previous.setRecLen((short) recLen);
                        DISK.seek(getDataBlockOffset(block.getBlock()) + prevOffset);
                        DISK.write(previous.toByteArray());
                    }
                    block.remove(i);
                    saveBitmaps();
                    return true;
                }
            }
        }
        return false;
    }

    // Saves the text into available data blocks, and then creates the dir_entry and the inode for the file
    public void writeFile(String fileName, String content) throws IOException {
        // Split file's bytes into groups of 4KB and write each one to disk (one block per group)
        byte contentBytes[][] = BitUtils.splitBytes(content.getBytes(), BLOCK_SIZE);
        int blocksNeeded = contentBytes.length;
        byte directBlocks[][] = (blocksNeeded > 12) ? Arrays.copyOfRange(contentBytes, 0, 12) : contentBytes;
        byte indirectBlocks[][] = (blocksNeeded > 12) ? Arrays.copyOfRange(contentBytes, 12, blocksNeeded) : null;

        // Add the direct pointers first
        int directBlocksCount = directBlocks.length;
        int fileBlocks[] = new int[directBlocksCount];
        for (int i = 0; i < directBlocksCount; i++) {
            byte group[] = directBlocks[i];
            int blockNumber = BitUtils.nextClearBitThenSet(DATA_BITMAP);
            fileBlocks[i] = blockNumber;
            DISK.seek(getDataBlockOffset(blockNumber));
            DISK.write(group);
        }

        // Add the indirect pointers (if necessary)
        ArrayList<Integer> referenceList;
        int indirectPointer = 0;
        if (indirectBlocks != null) {
            // The block references will be saved at this block
            indirectPointer = BitUtils.nextClearBitThenSet(DATA_BITMAP);
            referenceList = new ArrayList<>();
            for (byte[] group : indirectBlocks) {
                int blockNumber = BitUtils.nextClearBitThenSet(DATA_BITMAP);
                referenceList.add(blockNumber);
                DISK.seek(getDataBlockOffset(blockNumber));
                DISK.write(group);
            }

            // Write the (indirect) block references to disk
            DISK.seek(getDataBlockOffset(indirectPointer));
            for (int reference : referenceList) {
                DISK.write(Ints.toByteArray(reference));
            }
        }

        // Create a new inode for this file and write it to disk
        int inodeNumber = BitUtils.nextClearBitThenSet(INODE_BITMAP);
        Inode inode = new Inode(inodeNumber, Inode.FILE, content.length());
        inode.addBlocks(fileBlocks);
        if (indirectPointer != 0) inode.setIndirectPointer(indirectPointer);
        inodeTable.put(inodeNumber, inode);
        DISK.seek(getInodeOffset(inodeNumber));
        DISK.write(inode.toByteArray());

        // Create a dir_entry for this file and add it to the block
        DirectoryEntry dirEntry = new DirectoryEntry(inodeNumber, DirectoryEntry.FILE, fileName);

        // Only the last block is writable, the previous ones should be full of dir_entries
        DirectoryBlock lastBlock = currentDir.getLastBlock();

        // Check if the new dir_entry fits in the directory's last block
        if (lastBlock.getRemainingLength() >= dirEntry.getIdealLen()) {
            DirectoryEntry prevEntry = lastBlock.getLastEntry();
            int prevEntryOffset = lastBlock.getLength() - prevEntry.getIdealLen();
            lastBlock.addEntry(dirEntry);

            // Write the previous dir_entry (because its rec_len was modified in addEntry()) and the new dir_entry to disk
            DISK.seek(getDataBlockOffset(lastBlock.getBlock()) + prevEntryOffset);
            DISK.write(prevEntry.toByteArray());
            DISK.write(dirEntry.toByteArray());
        } else {
            // The new dir_entry doesn't fit in the block, create a new one
            int newBlock = BitUtils.nextClearBitThenSet(DATA_BITMAP);
            Inode currentDirInode = inodeTable.get(currentDir.getInode());
            currentDirInode.addBlocks(newBlock);

            DirectoryBlock block = new DirectoryBlock(newBlock);
            block.addEntry(dirEntry);
            currentDir.add(block);

            // Write the current directory inode to disk (to update it)
            DISK.seek(getInodeOffset(currentDirInode.getInode()));
            DISK.write(currentDirInode.toByteArray());

            // Write the new dir_entry to disk, in the newly assigned block
            DISK.seek(getDataBlockOffset(newBlock));
            DISK.write(dirEntry.toByteArray());
        }

        saveBitmaps();
    }

    public void appendToFile() {
        // Aquí hay que ver si el ultimo bloque todavia tiene espacio para seguir metiendo data
        // Por ejemplo, filesize mod 4096 me va a dar el sobrante del ultimo bloque de datos
        // 12467 mod 4096 = 179. Eso quiere decir que en el ultimo bloque, solo se han usado 179 bytes.
        // Hay 3 bloques llenos, y el cuarto solo tiene 179 bytes.
    }

    // Given a file name, searches for the file in the current directory, and returns the data in the data blocks
    public byte[] readFile(String fileName) throws IOException {
        int inode;
        try {
            inode = currentDir.findEntry(fileName, DirectoryEntry.FILE).getInode();
        } catch (NullPointerException npe) {
            // File not found
            return null;
        }

        // Read direct blocks first
        ArrayList<Integer> directBlocks;
        int fileSize;
        Inode fileInode = inodeTable.get(inode);

        // Update the last access time and write it to disk
        fileInode.setLastAccessTime(toIntExact(System.currentTimeMillis() / 1000));
        DISK.seek(getInodeOffset(fileInode.getInode()));
        DISK.write(fileInode.toByteArray());

        directBlocks = fileInode.getUsedDirectBlocks();
        fileSize = fileInode.getSize();

        int maxDirectBytes = BLOCK_SIZE * 12;
        byte directData[] = new byte[(fileSize > maxDirectBytes) ? maxDirectBytes : fileSize];

        // The easiest case: the file only uses one block
        if (directBlocks.size() == 1) {
            DISK.seek(getDataBlockOffset(directBlocks.get(0)));
            DISK.read(directData);
            return directData;
        }

        // There is more than one block of data
        // Start writing at position 0 of the array, and read up to 4096 bytes per block
        int offset = 0;
        int len = BLOCK_SIZE;
        for (int block : directBlocks) {
            DISK.seek(getDataBlockOffset(block));
            DISK.read(directData, offset, len - offset);

            // Next block data
            offset += BLOCK_SIZE;
            len = (len + BLOCK_SIZE > directData.length) ? directData.length - len : len + BLOCK_SIZE;
        }

        // Read the indirect block pointer (if any)
        int indirectPointer;
        if ((indirectPointer = fileInode.getIndirectPointer()) != 0) {
            int remainingBytes = fileSize - maxDirectBytes;
            // This is where the direct data and the indirect data will be saved
            byte fullData[];

            // This is where the rest of the data in the indirect pointer will be saved
            byte indirectData[] = new byte[remainingBytes];

            ArrayList<Integer> references = new ArrayList<>();
            byte blockBytes[] = new byte[4];
            int block;
            DISK.seek(getDataBlockOffset(indirectPointer));
            DISK.read(blockBytes);
            block = Ints.fromByteArray(blockBytes);
            while (block != 0) {
                references.add(block);
                DISK.read(blockBytes);
                block = Ints.fromByteArray(blockBytes);
            }

            // Now references should have all the blocks where the rest of the data is
            // The easiest case: references only has one block
            if (references.size() == 1) {
                DISK.seek(getDataBlockOffset(references.get(0)));
                DISK.read(indirectData);
                fullData = Bytes.concat(directData, indirectData);
                return fullData;
            }

            // There is more than one block reference if it gets here
            // Start writing at position 0 of the array, and read up to 4096 bytes per block
            offset = 0;
            len = BLOCK_SIZE;
            for (int reference : references) {
                DISK.seek(getDataBlockOffset(reference));
                DISK.read(indirectData, offset, len - offset);

                // Next block data
                offset += BLOCK_SIZE;
                len = (len + BLOCK_SIZE > indirectData.length) ? indirectData.length - len : len + BLOCK_SIZE;
            }
            return Bytes.concat(directData, indirectData);
        }
        return directData;
    }

    // Calculate the data offset of the given data block number
    private int getDataBlockOffset(int blockNumber) {
        return DATA_OFFSET + (blockNumber - 1) * BLOCK_SIZE;
    }

    // Calculate the inode offset of the given inode index
    private int getInodeOffset(int inode) {
        return INODE_TABLE_OFFSET + (inode - 1) * 80;
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
        Inode rootInode = inodeTable.get(1);
        for (int block : rootInode.getUsedDirectBlocks()) {
            root.add(readDirectoryBlock(block));
        }
        return root;
    }
}