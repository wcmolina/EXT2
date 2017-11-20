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
    // Data bitmap
    private final int DATA_BITMAP[] = new int[2048];

    public FileSystem(Disk disk) {
        DISK = disk;
    }

    public void format() throws IOException {
        // Fills disk with zeros
        final byte ZEROS[] = new byte[DISK.getSizeBytes()];
        DISK.seek(0);
        DISK.write(ZEROS);

        /*
        Create root directory
        El primer bloque donde inician los datos es el numero 20.
        En el bloque numero 20, debe haber un Directory (estructura que contiene directory entries). Ese Directory debe ser root
        Cada directory entry tiene:
            inode: numero de inodo que indica donde se encuentra la metadata del archivo o directorio
            nombre: nombre del archivo o directorio (tamaño variable)
        Antes de crear el directory ya debe haber un inode creado
        Un inode contiene la metadata del archivo (size, timestamps, punteros a bloques, etc.)
        Antes de crear un inode, es necesario buscar que bloques estan libres en el segmento de datos (utlizando el data bitmap) y, ademas,
            que entrada esta disponible en el inode table (con el inode bitmap)
        Con el inode bitmap, buscamos en que indice hay un bit apagado (en 0), retornamos ese indice y luego
        Con el data bitmap, buscamos en que índice hay un bit apagado (en 0). Retornamos ese indice y luego buscamos ese indice en el segmento de datos.
        Antes de ello, es necesario saber cuantos bytes tomará el archivo.
        Para saber cuantos bytes toma un archivo, se multiplica el length del string * 2
        */
    }

    // Offsets
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
