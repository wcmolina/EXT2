package ext2;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * @author Wilmer
 */
public class Shell {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            File binaryFile = new File("disk.bin");
            final Disk DISK;
            final FileSystem FILE_SYSTEM;
            if (binaryFile.exists() && !binaryFile.isDirectory()) {
                System.out.println("File disk.bin already exists");
                DISK = new Disk(binaryFile);
                FILE_SYSTEM = new FileSystem(DISK);
                Scanner scanner = new Scanner(System.in);
                String cmd;
                mainloop:
                for (; ; ) {
                    System.out.print("\n/: ");
                    cmd = scanner.nextLine();
                    switch (cmd) {
                        case "ls": {
                            // ls current directory. For now it only ls inside the root dir
                            ls(FILE_SYSTEM.getCurrentDirectory());
                            break;
                        }
                        case "cd": {
                            break;
                        }
                        case "exit":
                            break mainloop;
                    }
                }
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

    public static void ls(Directory currentDirectory) {
        for (DirectoryEntry dirEntry : currentDirectory) {
            System.out.println(dirEntry.getFilename());
        }
    }
}
