package ext2;

import java.io.IOException;
import java.util.Scanner;

public class Shell {

    private FileSystem fileSystem;

    public Shell(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public void start() throws IOException {
        Scanner scanner = new Scanner(System.in);
        String input, command;
        mainloop:
        for (; ; ) {
            System.out.printf("%n%s: ", fileSystem.getCurrentPath());
            input = scanner.nextLine();
            command = input.split(" ")[0];
            switch (command) {
                case "ls": {
                    ls(fileSystem.getCurrentDirectory());
                    break;
                }
                case "cd": {
                    String opts[] = input.split(" ");
                    String path = (opts.length == 2) ? opts[1] : ".";
                    try {
                        cd(path);
                    } catch (IOException ioe) {
                        System.err.println("Unexpected IO Exception ocurred");
                    }
                    break;
                }
                case "cat": {
                    String opts[] = input.split(" ");
                    if (opts.length == 2) {
                        // cat file.txt
                        String fileName = opts[1];
                        cat(fileName);
                    } else if (opts.length == 3) {
                        // cat > file.txt
                        String fileName = opts[2];
                        if (fileName.length() > 255) {
                            System.err.println("Error: File name too long (a maximum of 255 characters are allowed");
                            break;
                        }
                        String content = "";
                        String line;
                        while (!(line = scanner.nextLine()).equals("eof")) {
                            content += line + "\n";
                        }
                        fileSystem.writeFile(fileName, content);
                    } else {
                        System.out.println("Invalid 'cat' usage");
                    }
                    break;
                }

                case "mkdir": {
                    String opts[] = input.split(" ");
                    String dirName = opts[1];
                    fileSystem.createDirectory(dirName);
                    break;
                }
                case "exit":
                    break mainloop;
            }
        }
    }

    public void ls(Directory currentDirectory) {
        for (DirectoryEntry dirEntry : currentDirectory) {
            System.out.println(dirEntry.getFilename());
        }
    }

    public void cat(String fileName) {
        try {
            byte contentBytes[] = fileSystem.readFile(fileName);
            if (contentBytes == null) {
                System.err.println("File not found");
                return;
            }
            String content = new String(contentBytes);
            System.out.println(content);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void cd(String path) throws IOException {
        if (fileSystem.readDirectoryFromPath(path) == null)
            System.err.println("The system could not find the path specified");
    }
}
