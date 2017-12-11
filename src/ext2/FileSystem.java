package ext2;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;

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
        writeBitmaps();
    }

    private void writeBitmaps() throws IOException {
        DISK.seek(DATA_BITMAP_OFFSET);
        DISK.write(DATA_BITMAP);
        DISK.write(INODE_BITMAP);
    }

    public void writeDirectory(String name) throws IOException {
        int dirInode = BitUtils.nextClearBitThenSet(INODE_BITMAP);

        addDirectoryEntry(dirInode, DirectoryEntry.DIRECTORY, name);

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
        writeBitmaps();
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

    public DirectoryEntry findEntry(String path, byte type) throws IOException {
        Directory initialDir = (path.startsWith("/")) ? getRoot() : currentDir;

        ArrayList<String> entries = Utils.splitPath(path);
        for (int i = 0; i < entries.size(); i++) {
            String name = entries.get(i);
            DirectoryEntry entry = initialDir.findEntry(name, type);
            if (entry != null) {
                if (entry.getType() == DirectoryEntry.DIRECTORY) {
                    Directory directory = new Directory();
                    ArrayList<Integer> dirBlocks;
                    int inodeNumber = entry.getInode();
                    Inode inode = inodeTable.get(inodeNumber);
                    dirBlocks = inode.getDirectBlocks();

                    // Go through each block and read their dir_entries
                    for (int block : dirBlocks) {
                        directory.add(readDirectoryBlock(block));
                    }

                    initialDir = directory;
                } else {
                    // It is a file so it doesn't have directory entries. Check if it is the last element in the path
                    return (i == entries.size() - 1) ? entry : null;
                }
            } else return null;
        }
        return null;
    }

    public void createLink(String source, String destination, byte type) throws IOException {
        DirectoryEntry sourceEntry = findEntry(source, DirectoryEntry.FILE);
        Inode sourceInode = inodeTable.get(sourceEntry.getInode());

        if (type == DirectoryEntry.SYM_LINK) {
            // ...
        } else if (type == DirectoryEntry.HARD_LINK) {
            // ...
        }
    }

    // Remove a dir_entry from the current directory
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
                        for (int index : inode.getDirectBlocks()) {
                            if (readDirectoryBlock(index).hasEntries()) {
                                throw new IllegalArgumentException("Directory is not empty. Cannot delete it");
                            }
                        }
                    }

                    // Clear the bits used by the dir_entry in the data bitmap
                    for (int index : inode.getDirectBlocks()) {
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
                    writeBitmaps();
                    return true;
                }
            }
        }
        return false;
    }

    // Saves the text into available data blocks, and then creates the dir_entry and the inode for the file
    public void writeFile(String fileName, String text) throws IOException {
        // Split file's bytes into groups of 4KB and write each one to disk (one block per group)
        byte content[][] = BitUtils.splitBytes(text.getBytes(), BLOCK_SIZE);
        int blocksNeeded = content.length;
        // Bytes that will go in the direct pointers
        byte direct[][] = (blocksNeeded > 12) ? Arrays.copyOfRange(content, 0, 12) : content;
        // Bytes that will go in the indirect pointer's block references
        byte indirect[][] = (blocksNeeded > 12) ? Arrays.copyOfRange(content, 12, blocksNeeded) : null;

        // Add the direct pointers first
        int directBlocks[] = new int[direct.length];
        for (int i = 0; i < direct.length; i++) {
            byte group[] = direct[i];
            int blockNumber = BitUtils.nextClearBitThenSet(DATA_BITMAP);
            directBlocks[i] = blockNumber;
            DISK.seek(getDataBlockOffset(blockNumber));
            DISK.write(group);
        }

        // Add the indirect pointers (if necessary)
        ArrayList<Integer> references;
        int indirectPointer = 0;
        if (indirect != null) {
            // The block references will be saved at this block
            indirectPointer = BitUtils.nextClearBitThenSet(DATA_BITMAP);
            references = new ArrayList<>();
            for (byte[] group : indirect) {
                int block = BitUtils.nextClearBitThenSet(DATA_BITMAP);
                references.add(block);
                DISK.seek(getDataBlockOffset(block));
                DISK.write(group);
            }

            // Write the (indirect) block references to disk
            DISK.seek(getDataBlockOffset(indirectPointer));
            for (int reference : references) {
                DISK.write(Ints.toByteArray(reference));
            }
        }

        // Create a new inode for this file and write it to disk
        int inodeNumber = BitUtils.nextClearBitThenSet(INODE_BITMAP);
        Inode inode = new Inode(inodeNumber, Inode.FILE, text.length());
        inode.addBlocks(directBlocks);
        if (indirectPointer != 0) inode.setIndirectPointer(indirectPointer);
        inodeTable.put(inodeNumber, inode);
        DISK.seek(getInodeOffset(inodeNumber));
        DISK.write(inode.toByteArray());

        addDirectoryEntry(inodeNumber, DirectoryEntry.FILE, fileName);
        writeBitmaps();
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

        Inode fileInode = inodeTable.get(inode);
        fileInode.setLastAccessTime(toIntExact(System.currentTimeMillis() / 1000));
        DISK.seek(getInodeOffset(fileInode.getInode()));
        DISK.write(fileInode.toByteArray());

        ArrayList<Integer> directBlocks = fileInode.getDirectBlocks();
        final int fileSize = fileInode.getSize();
        final int maxDirectBytes = BLOCK_SIZE * 12;
        byte directData[] = new byte[(fileSize > maxDirectBytes) ? maxDirectBytes : fileSize];

        int offset = 0;
        int len = (directData.length < BLOCK_SIZE) ? directData.length : BLOCK_SIZE;
        for (int block : directBlocks) {
            DISK.seek(getDataBlockOffset(block));
            DISK.read(directData, offset, len);

            // Next block data
            offset += BLOCK_SIZE;
            len = (directData.length - offset >= BLOCK_SIZE) ? BLOCK_SIZE : directData.length - offset;
        }

        // Read the indirect block pointer (if any)
        int indirectPointer;
        if ((indirectPointer = fileInode.getIndirectPointer()) != 0) {
            int remainingBytes = fileSize - maxDirectBytes;
            byte indirectData[] = new byte[remainingBytes];

            int referenceCount = (int) Math.ceil(fileSize / (double) BLOCK_SIZE) - 12;
            ArrayList<Integer> references = readIndirectPointer(indirectPointer, referenceCount);

            // Start writing at position 0 of the array, and read up to 4096 bytes per block
            offset = 0;
            len = (remainingBytes > BLOCK_SIZE) ? BLOCK_SIZE : remainingBytes;
            for (int reference : references) {
                DISK.seek(getDataBlockOffset(reference));
                DISK.read(indirectData, offset, len);

                // Next block data
                offset += BLOCK_SIZE;
                len = (indirectData.length - offset >= BLOCK_SIZE) ? BLOCK_SIZE : indirectData.length - offset;
            }
            return Bytes.concat(directData, indirectData);
        }
        return directData;
    }

    public boolean append(String fileName, String text) throws IOException {
        byte content[] = text.getBytes();
        int appendLength = content.length;
        int inodeNumber;

        try {
            inodeNumber = currentDir.findEntry(fileName, DirectoryEntry.FILE).getInode();
        } catch (NullPointerException npe) {
            return false;
        }

        Inode inode = inodeTable.get(inodeNumber);
        final ArrayList<Integer> directBlocks = inode.getDirectBlocks();
        final int fileSize = inode.getSize();
        int freeBlocks = 12 - directBlocks.size();

        int remainder = fileSize % BLOCK_SIZE;
        int lastBlockFreeBytes = (remainder == 0) ? 0 : BLOCK_SIZE - remainder;
        int freeDirectBytes = freeBlocks * BLOCK_SIZE + lastBlockFreeBytes;

        byte direct[];
        byte indirect[];
        if (appendLength < freeDirectBytes) {
            // I don't need the indirect pointer
            direct = content;
            indirect = null;
        } else {
            direct = (freeDirectBytes > 0) ? Arrays.copyOfRange(content, 0, freeDirectBytes) : null;
            indirect = (freeDirectBytes > 0) ? Arrays.copyOfRange(content, freeDirectBytes, appendLength) : content;
        }

        if (direct != null) {
            if (lastBlockFreeBytes > 0) {
                int lastBlock = directBlocks.get(directBlocks.size() - 1);
                if (direct.length < lastBlockFreeBytes) {
                    DISK.seek(getDataBlockOffset(lastBlock) + remainder);
                    DISK.write(direct);
                    writeAppendModifiedDate(inode, appendLength);
                    return true;
                } else {
                    byte blockFill[] = Arrays.copyOfRange(direct, 0, lastBlockFreeBytes);
                    direct = Arrays.copyOfRange(direct, lastBlockFreeBytes, direct.length);
                    DISK.seek(getDataBlockOffset(lastBlock) + remainder);
                    DISK.write(blockFill);
                }
                remainder = 0;
            }

            byte directBlockGroups[][] = BitUtils.splitBytes(direct, BLOCK_SIZE);
            int blocks[] = new int[directBlockGroups.length];
            for (int i = 0; i < directBlockGroups.length; i++) {
                byte[] group = directBlockGroups[i];
                int block = BitUtils.nextClearBitThenSet(DATA_BITMAP);
                blocks[i] = block;
                DISK.seek(getDataBlockOffset(block));
                DISK.write(group);
            }
            inode.addBlocks(blocks);
        }

        if (indirect != null) {
            ArrayList<Integer> references = new ArrayList<>();
            int indirectPointer = inode.getIndirectPointer();
            if (indirectPointer == 0) {
                // If it gets here is because the direct blocks have exactly 49152 bytes. Remainder should be 0
                indirectPointer = BitUtils.nextClearBitThenSet(DATA_BITMAP);
            }
            if (remainder > 0) {
                int referenceCount = (int) Math.ceil(fileSize / (double) BLOCK_SIZE) - 12;
                references = readIndirectPointer(indirectPointer, referenceCount);
                int lastBlock = references.get(references.size() - 1);
                if (indirect.length < lastBlockFreeBytes) {
                    DISK.seek(getDataBlockOffset(lastBlock) + remainder);
                    DISK.write(direct);
                    writeAppendModifiedDate(inode, appendLength);
                    return true;
                } else {
                    byte blockFill[] = Arrays.copyOfRange(indirect, 0, lastBlockFreeBytes);
                    indirect = Arrays.copyOfRange(indirect, lastBlockFreeBytes, appendLength);
                    DISK.seek(getDataBlockOffset(lastBlock));
                    DISK.write(blockFill);
                }
            }

            byte indirectBlockGroups[][] = BitUtils.splitBytes(indirect, BLOCK_SIZE);
            for (byte[] group : indirectBlockGroups) {
                int block = BitUtils.nextClearBitThenSet(DATA_BITMAP);
                references.add(block);
                DISK.seek(getDataBlockOffset(block));
                DISK.write(group);
            }

            // Write the (indirect) block references to disk
            DISK.seek(getDataBlockOffset(indirectPointer));
            for (int reference : references) {
                DISK.write(Ints.toByteArray(reference));
            }
        }
        writeAppendModifiedDate(inode, appendLength);
        return true;
    }

    private void writeAppendModifiedDate(Inode inode, int appendLength) throws IOException {
        inode.setSize(inode.getSize() + appendLength);
        inode.setModifiedTime(toIntExact(System.currentTimeMillis() / 1000));
        DISK.seek(getInodeOffset(inode.getInode()));
        DISK.write(inode.toByteArray());
        writeBitmaps();
    }

    public ArrayList<Integer> readIndirectPointer(int pointer, int referenceCount) throws IOException {
        ArrayList<Integer> references = new ArrayList<>();
        byte blockBytes[] = new byte[4];
        DISK.seek(getDataBlockOffset(pointer));
        while (referenceCount != 0) {
            DISK.read(blockBytes);
            int block = Ints.fromByteArray(blockBytes);
            references.add(block);
            referenceCount--;
        }
        return references;
    }

    public Directory getCurrentDirectory() {
        return currentDir;
    }

    public void setCurrentDirectory(Directory directory) {
        this.currentDir = directory;
    }

    public InodeTable getInodeTable() {
        return inodeTable;
    }

    public Directory getRoot() throws IOException {
        Directory root = new Directory();
        Inode rootInode = inodeTable.get(1);
        for (int block : rootInode.getDirectBlocks()) {
            root.add(readDirectoryBlock(block));
        }
        return root;
    }

    // Add a new directory entry to the current directory
    private void addDirectoryEntry(int inodeNumber, byte type, String name) throws IOException {
        // Only the last block is writable, the previous ones should be full of dir_entries
        DirectoryBlock lastBlock = currentDir.getLastBlock();

        // Add the new directory as a dir_entry in the current one. Check if it fits in the last used block
        DirectoryEntry entry = new DirectoryEntry(inodeNumber, type, name);
        if (lastBlock.getRemainingLength() >= entry.getIdealLen()) {
            DirectoryEntry prevEntry = lastBlock.getLastEntry();
            int prevEntryOffset = lastBlock.getLength() - prevEntry.getIdealLen();
            lastBlock.addEntry(entry);

            // Write the previous dir_entry (because its rec_len was modified in addEntry()) and the new dir_entry to disk
            DISK.seek(getDataBlockOffset(lastBlock.getBlock()) + prevEntryOffset);
            DISK.write(prevEntry.toByteArray());
            DISK.write(entry.toByteArray());
        } else {
            // The new dir_entry doesn't fit in the block, create a new one
            int newBlock = BitUtils.nextClearBitThenSet(DATA_BITMAP);
            Inode inode = inodeTable.get(currentDir.getInode());
            inode.addBlocks(newBlock);

            DirectoryBlock block = new DirectoryBlock(newBlock);
            block.addEntry(entry);
            currentDir.add(block);

            // Write the current directory inode to disk (to update it)
            DISK.seek(getInodeOffset(inode.getInode()));
            DISK.write(inode.toByteArray());

            // Write the new dir_entry to disk, in the newly assigned block
            DISK.seek(getDataBlockOffset(newBlock));
            DISK.write(entry.toByteArray());
        }
    }

    // Calculate the data offset of the given data block number
    private int getDataBlockOffset(int blockNumber) {
        return DATA_OFFSET + (blockNumber - 1) * BLOCK_SIZE;
    }

    // Calculate the inode offset of the given inode index
    private int getInodeOffset(int inode) {
        return INODE_TABLE_OFFSET + (inode - 1) * 80;
    }
}