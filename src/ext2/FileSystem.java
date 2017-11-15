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
        final byte ZEROS[] = new byte[DISK.getSizeBytes()];
        DISK.seek(0);
        DISK.write(ZEROS);
        formatDataBitmap();
        formatInodeBitmap();
    }

    private void formatDataBitmap() throws IOException {
        final int RESERVED_BLOCKS = DATA_BITMAP_BLOCKS + INODE_BITMAP_BLOCKS + INODE_TABLE_BLOCKS;
        final byte DATA_BITMAP[] = new byte[DATA_BITMAP_SIZE];
        final int BYTES_MARKED = (int) Math.ceil(RESERVED_BLOCKS / 8.0);
        int markedBits = 0;
        for (int i = 0; i < BYTES_MARKED; i++) {
            // Loop every bit of byte i
            for (int j = 7; j >= 0; j--) {
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
        DISK.seek(DATA_BITMAP_OFFSET);
        DISK.write(DATA_BITMAP);
    }

    private void formatInodeBitmap() throws IOException {
        final byte INODE_BITMAP[] = new byte[INODE_BITMAP_SIZE];
        DISK.seek(INODE_BITMAP_OFFSET);
        DISK.write(INODE_BITMAP);
    }
}
