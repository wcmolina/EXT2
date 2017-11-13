package ext2;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 *
 * @author Wilmer
 */
public class Disk {
    // Disk volume size in KB
    // 256 MB = 262,144 KB = 268,435,456 bytes
    private final int SIZE_KB = 262144;
    // Disk block size in KB
    private final int BLOCK_SIZE_KB = 4;
    private final File BINARY_FILE;
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

    public Disk(File file) {
        BINARY_FILE = file;
    }

    public void format() throws IOException {
        // The first 8 KB (first two blocks) are for the data bitmap
        // The inode bitmap comes next, and it only needs 128 bytes (not even 1 KB, but still uses a whole 4 KB block)
        // After that comes the inode table that needs 64 KB

        // Data bitmap block group
        formatDataBitmap();
        // inode bitmap block group
        formatInodeBitmap();
    }

    private void formatDataBitmap() throws IOException {
        final int RESERVED_BLOCKS = DATA_BITMAP_BLOCKS + INODE_BITMAP_BLOCKS + INODE_TABLE_BLOCKS;
        final byte DATA_BITMAP[] = new byte[DATA_BITMAP_SIZE];
        int markedBits = 0;
        final int BYTES_MARKED = (int) Math.ceil(RESERVED_BLOCKS / 8.0);
        for (int i = 0; i < BYTES_MARKED; i++) {
            // Loop every bit of byte i
            for (int j = 0; j < 8; j++) {
                if (markedBits < RESERVED_BLOCKS) {
                    // b |= (1 << bitIndex) // set a bit to 1
                    // b &= ~(1 << bitIndex) // set a bit to 0
                    DATA_BITMAP[i] |= (1 << j);
                    markedBits++;
                } else {
                    break;
                }
            }
        }
        try (RandomAccessFile accessFile = new RandomAccessFile(BINARY_FILE, "rw")) {
            accessFile.seek(DATA_BITMAP_OFFSET);
            accessFile.write(DATA_BITMAP);
        }
    }

    private void formatInodeBitmap() throws IOException {
        final byte INODE_BITMAP[] = new byte[INODE_BITMAP_SIZE];
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(BINARY_FILE, "rw")) {
            randomAccessFile.seek(INODE_BITMAP_OFFSET);
            randomAccessFile.write(INODE_BITMAP);
        }
    }
}
