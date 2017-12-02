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
            System.out.printf("%n%s$ ", fileSystem.getCurrentPath());
            input = scanner.nextLine();
            command = input.split(" ")[0];
            switch (command) {
                case "ls": {
                    String opts[] = input.split(" ");
                    if (opts.length == 2) {
                        // ls -l
                        if (opts[1].equals("-l"))
                            lsExtended(fileSystem.getCurrentDirectory());
                        else
                            System.out.printf("Unknown option '%s'%n", opts[1]);
                    } else if (opts.length == 1) {
                        // ls
                        ls(fileSystem.getCurrentDirectory());
                    } else {
                        System.out.println("Invalid 'ls' usage. Use 'ls' or 'ls -l'");
                    }
                    break;
                }
                case "cd": {
                    String opts[] = input.split(" ");
                    String path = (opts.length == 2) ? opts[1] : ".";
                    try {
                        cd(path);
                    } catch (IOException ioe) {
                        System.out.println("Unexpected IO Exception ocurred");
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
                            System.out.println("Error: File name too long (a maximum of 255 characters are allowed");
                            break;
                        }
                        String content = "";
                        String line;
                        while (!(line = scanner.nextLine()).equals("eof")) {
                            content += line + "\n";
                        }
                        fileSystem.writeFile(fileName, content);
                    } else {
                        System.out.println("Invalid 'cat' usage. Use 'cat [filename]' or 'cat > [filename]'");
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
                default:
                    System.out.printf("Unknown command '%s'%n", input.trim());
                    break;
            }
        }
    }

    public void ls(Directory directory) {
        for (int i = 0; i < directory.size(); i++) {
            DirectoryEntry dirEntry = directory.get(i);
            if (dirEntry.getFilename().equals(".") || dirEntry.getFilename().equals(".."))
                continue;

            System.out.printf((i == directory.size() - 1)
                    ? "%s%n"
                    : "%s  ", dirEntry.getFilename());
        }
    }

    public void lsExtended(Directory directory) {
        InodeTable inodeTable = fileSystem.getInodeTable();
        Inode inode;
        String creationDate, fileName, type, size;

        for (DirectoryEntry dirEntry : directory) {
            if (dirEntry.getFilename().equals(".") || dirEntry.getFilename().equals(".."))
                continue;

            inode = inodeTable.getByInodeNumber(dirEntry.getInodeNumber());
            creationDate = Utils.epochTimeToDate(inode.getCreationTime());
            size = (dirEntry.getType() == DirectoryEntry.DIRECTORY) ? "" : Integer.toString(inode.getSize());
            type = (dirEntry.getType() == DirectoryEntry.DIRECTORY) ? "<DIR>" : "";
            fileName = dirEntry.getFilename();
            System.out.format("%22s %6s %6s %s%n", creationDate, type, size, fileName);
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
