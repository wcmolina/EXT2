package ext2;

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            Disk disk;
            FileSystem fileSystem;
            File binaryFile = new File("disk.bin");
            if (binaryFile.exists() && !binaryFile.isDirectory()) {
                disk = new Disk(binaryFile);
                fileSystem = new FileSystem(disk);
            } else {
                binaryFile.createNewFile();
                disk = new Disk(binaryFile);
                fileSystem = new FileSystem(disk);
                fileSystem.format();
            }
            Shell shell = new Shell(fileSystem);
            shell.start();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
