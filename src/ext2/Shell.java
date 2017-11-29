package ext2;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * @author Wilmer
 */
public class Shell {

    /**
     * @param args the command line arguments
     */

    private FileSystem fileSystem;
    private Directory currentDirectory;

    public Shell(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public void start() throws IOException {
        Scanner scanner = new Scanner(System.in);
        String input, command;
        try {
            mainloop:
            for (; ; ) {
                System.out.print("\n/: ");
                input = scanner.nextLine();
                command = input.split(" ")[0];
                switch (command) {
                    case "ls": {
                        if (currentDirectory == null) {
                            currentDirectory = fileSystem.getRootDirectory();
                        }
                        ls(currentDirectory);
                        break;
                    }
                    case "cd": {
                        break;
                    }
                    case "cat": {
                        String opts[] = input.split(" ");
                        if (opts.length == 2) {
                            // cat file.txt
                            String fileName = opts[1];
                            // Find and show file contents
                            cat(fileName);
                        } else if (opts.length == 3) {
                            // cat > file.txt
                            String fileName = opts[2];
                            String content = "";
                            String line;
                            // Type eof to end input stream
                            while (!(line = scanner.nextLine()).equals("eof")) {
                                content += line + "\n";
                            }
                            fileSystem.createFile(fileName, content);
                        } else {
                            System.out.println("Invalid 'cat' usage");
                        }
                        break;
                    }
                    case "exit":
                        break mainloop;
                }
            }
        } catch (NoSuchElementException nsee) {
            System.exit(0);
        }
    }

    public void ls(Directory currentDirectory) {
        for (DirectoryEntry dirEntry : currentDirectory) {
            System.out.println(dirEntry.getFilename());
        }
    }

    public void cat(String fileName) {
        try {
            byte contentBytes[] = fileSystem.retrieveFile(fileName);
            String content = new String(contentBytes);
            System.out.println(content);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
