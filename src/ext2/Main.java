package ext2;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * @author Wilmer
 */
public class Main {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            File binaryFile = new File("disk.bin");
            final Disk DISK;
            final FileSystem FILE_SYSTEM;
            if (binaryFile.exists() && !binaryFile.isDirectory()) {
                // disk.bin already exists. Perform operations in this file
                System.out.println("File disk.bin already exists");
                DISK = new Disk(binaryFile);
                FILE_SYSTEM = new FileSystem(DISK);
                // TODO: use this file as a disk and try to save a file using cat > a.txt
                // Algorithm for saving a file in EXT2? How are dir_entry, inodes table, and pointers used?
                String catTest = "Hola mundo mi nombre es Wilmer";
                int fileSize = catTest.length() * 2;
                Inode node = new Inode(Inode.FILE, fileSize);
                System.out.println("inode entry example:");
                // Este monton de bits seria un inode en la inode table (son 64 bytes), y la inode table soporta hasta 1024 entradas
                Util.printBytes(node.toByteArray());
                // Inodo creado a partir del array de bytes del inodo anterior
                // Producen los mismos bytes
                Inode built = Inode.fromByteArray(node.toByteArray());
                Util.printBytes(built.toByteArray());
            } else {
                binaryFile.createNewFile();
                DISK = new Disk(binaryFile);
                FILE_SYSTEM = new FileSystem(DISK);
                FILE_SYSTEM.format();
                System.out.println("File disk.bin was created and formatted successfully");
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
