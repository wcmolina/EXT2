package ext2;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.util.ArrayList;

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

    // Root directory and current directory
    // Current directory only changes with the cd command (which uses the readDirectoryFromPath(path) method)
    private Directory root;
    private Directory currentDirectory;

    // Inode table
    private InodeTable inodeTable;

    // String currentPath
    private String currentPath;

    public FileSystem(Disk disk) throws IOException {
        DISK = disk;
        initialize();
    }

    public void format() throws IOException {
        final byte ZEROS[] = new byte[DISK.getSizeBytes()];
        DISK.seek(0);
        DISK.write(ZEROS);

        // Create the first directory (root)
        createDirectory(null);
        initialize();
    }

    // Get the structures from disk and allocate them to memory
    private void initialize() throws IOException {
        root = readDirectory(1);
        currentDirectory = root;
        currentPath = "/";

        // Load bitmaps and inode table to memory
        allocateBitmaps();
        allocateInodeTable();
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

    private void saveBitmaps() throws IOException {
        DISK.seek(DATA_BITMAP_OFFSET);
        DISK.write(DATA_BITMAP);
        DISK.write(INODE_BITMAP);
    }

    // Creates a directory (in the current directory) with the given name. It also adds the . and .. directory entries
    public void createDirectory(String name) throws IOException {
        int dirBlock = BitUtils.nextClearBitThenSet(DATA_BITMAP);
        int dirInode = BitUtils.nextClearBitThenSet(INODE_BITMAP);
        boolean isRoot = dirBlock == 1;

        // This part assumes root directory was already created. If that's not the case then skip this part
        if (!isRoot) {
            // Add the new directory to the current directory as a dir_entry and write it to disk
            DirectoryEntry dirEntry = new DirectoryEntry(dirInode, name, DirectoryEntry.DIRECTORY);
            DISK.seek(getDataBlockOffset(currentDirectory.getBlockNumber()) + currentDirectory.getTotalLength());
            DISK.write(dirEntry.toByteArray());
            currentDirectory.add(dirEntry);
        }

        // Create . and .. directory entries
        Inode inode = new Inode(dirInode, Inode.DIRECTORY);
        int parentInode = (isRoot) ? dirInode : currentDirectory.getInode();
        inode.addBlockPointers(dirBlock);
        inodeTable.add(inode);
        DirectoryEntry self, parent;
        self = new DirectoryEntry(dirInode, ".", DirectoryEntry.DIRECTORY);
        parent = new DirectoryEntry(parentInode, "..", DirectoryEntry.DIRECTORY);

        // Write the directory inode and its entries to disk
        DISK.seek(getInodeOffset(dirInode));
        DISK.write(inode.toByteArray());
        DISK.seek(getDataBlockOffset(dirBlock));
        DISK.write(self.toByteArray());
        DISK.write(parent.toByteArray());

        saveBitmaps();
    }

    // Given a block index, read the directory entries from that block
    public Directory readDirectory(int blockIndex) throws IOException {
        Directory directory = new Directory(blockIndex);
        int dirEntryOffset = getDataBlockOffset(blockIndex);
        byte inodeBytes[] = new byte[4];
        byte recLenBytes[] = new byte[2];
        int dirEntryInode;
        short dirEntryRecLen;

        // Get the first dir_entry in this block and read its inode
        DISK.seek(dirEntryOffset);
        DISK.read(inodeBytes);
        dirEntryInode = Ints.fromByteArray(inodeBytes);

        while (dirEntryInode != 0) {
            DISK.read(recLenBytes);
            dirEntryRecLen = Shorts.fromByteArray(recLenBytes);

            // The number of bytes this dir_entry has is determined by recLen
            byte dirEntry[] = new byte[dirEntryRecLen];

            // Go back to the start of this dir_entry and read all its bytes
            DISK.seek(dirEntryOffset);
            DISK.read(dirEntry);
            directory.add(DirectoryEntry.fromByteArray(dirEntry));

            // Read the next dir_entry inode (if any)
            DISK.read(inodeBytes);
            dirEntryInode = Ints.fromByteArray(inodeBytes);

            // Update the dir_entry offset for the next one (if any)
            dirEntryOffset += dirEntryRecLen;
        }
        return directory;
    }

    // Go through every directory in the path
    // Path may be in the form: /dir1/dir2/dir3/
    // This method would return the directory dir3 (if the path exists)
    // Used for cd <path>. For example: cd /usr/bin/ or cd home/documents/work
    public Directory readDirectoryFromPath(String path) throws IOException {
        // Used to restore the path in case this method throws an exception while building the path
        String rollbackPath = getCurrentPath();
        Directory initialDir;

        // If path starts with "/", begin from the root directory
        if (path.startsWith("/")) {
            initialDir = root;
            currentPath = "/";
        } else
            initialDir = currentDirectory;

        ArrayList<String> splitPath = Utils.splitPath(path);
        for (String dirName : splitPath) {
            DirectoryEntry dirEntry = initialDir.getEntryByName(dirName);
            if (dirEntry != null) {
                if (dirEntry.getType() == DirectoryEntry.DIRECTORY) {

                    // Get the blocks of the directory using the inode of the dirEntry
                    int dirBlock;
                    int inodeNumber = dirEntry.getInodeNumber();
                    Inode inode = inodeTable.getByInodeNumber(inodeNumber);

                    // FIX ME: a directory can have more than one block containing its directory entries
                    dirBlock = inode.getUsedPointers()[0];

                    // Get the directory at this block and update the initial directory with the same one
                    initialDir = readDirectory(dirBlock);
                    currentPath = FilenameUtils.concat(getCurrentPath(), dirName.concat("/"));
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
        currentDirectory = initialDir;
        return initialDir;
    }

    // Read and write files

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
        inode.addBlockPointers(fileBlocks);
        inodeTable.add(inode);
        DISK.seek(getInodeOffset(inodeNumber));
        DISK.write(inode.toByteArray());

        // Create a dir_entry for this file and write it at the end of the directory
        DirectoryEntry dirEntry = new DirectoryEntry(inodeNumber, fileName, DirectoryEntry.FILE);
        DISK.seek(getDataBlockOffset(currentDirectory.getBlockNumber()) + currentDirectory.getTotalLength());
        DISK.write(dirEntry.toByteArray());
        currentDirectory.add(dirEntry);

        saveBitmaps();
    }

    // Given a file name, searches for the file in the current directory, and returns the data in the data blocks
    public byte[] readFile(String fileName) throws IOException {
        int fileInode = 0;
        for (DirectoryEntry dirEntry : currentDirectory) {
            if (dirEntry.getFilename().equals(fileName)) {
                fileInode = dirEntry.getInodeNumber();
                break;
            }
        }
        if (fileInode == 0) {
            // File not found
            return null;
        }
        int fileBlocks[] = null;
        int fileSize = 0;
        for (Inode inode : inodeTable) {
            if (inode.getInodeNumber() == fileInode) {
                fileBlocks = inode.getUsedPointers();
                fileSize = inode.getSize();
                break;
            }
        }
        if (fileBlocks == null) {
            // ??? :(
            return null;
        }
        byte data[] = new byte[fileSize];
        if (fileBlocks.length == 1) {
            DISK.seek(getDataBlockOffset(fileBlocks[0]));
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

    // Getters and setters

    public int getBlockSizeBytes() {
        return BLOCK_SIZE_KB * 1024;
    }

    // Calculate the data offset of the given data block number
    private int getDataBlockOffset(int blockNumber) {
        return DATA_OFFSET + (blockNumber - 1) * getBlockSizeBytes();
    }

    // Calculate the inode offset of the given inode index
    private int getInodeOffset(int inode) {
        return INODE_TABLE_OFFSET + (inode - 1) * 64;
    }

    public Directory getCurrentDirectory() {
        return currentDirectory;
    }

    public String getCurrentPath() {
        return currentPath == null ? "/" : FilenameUtils.separatorsToUnix(currentPath);
    }
}