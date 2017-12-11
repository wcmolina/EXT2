package ext2;

import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

public class Shell {

    private FileSystem fileSystem;
    private String currentPath = "/";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_RESET = "\u001B[0m";

    public Shell(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public void start() throws IOException {
        Scanner scanner = new Scanner(System.in);
        String input, command;
        mainloop:
        for (; ; ) {
            System.out.printf("%n%s$ ", getCurrentPath());
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
                    String opts[] = input.split(" ", 2);
                    String path = (opts.length == 2) ? opts[1] : ".";
                    try {
                        cd(path);
                    } catch (IOException ioe) {
                        System.out.println("Unexpected IO Exception ocurred");
                    }
                    break;
                }
                case "cat": {
                    if (input.contains(" > ")) {
                        String opts[] = input.split(" > ");
                        String fileName = opts[1].trim();
                        if (Utils.containsIllegals(fileName)) {
                            System.out.println("Illegal character found in the file name");
                            break;
                        }
                        if (fileName.length() > 255) {
                            System.out.println("Error: File name too long (a maximum of 255 characters is allowed");
                            break;
                        }
                        String content = "";
                        String line;
                        while (!(line = scanner.nextLine()).equals("eof")) {
                            content += line + "\n";
                        }
                        fileSystem.writeFile(fileName, content);
                    } else if (input.contains(" >> ")) {
                        System.out.println("Append to file not supported (yet)");
                    } else {
                        String opts[] = input.split(" ", 2);
                        if (opts.length == 2) {
                            // cat file.txt
                            String fileName = opts[1];
                            cat(fileName);
                        } else {
                            System.out.println("Invalid 'cat' usage. Use 'cat [filename]' or 'cat > [filename]'");
                        }
                    }
                    break;
                }
                case "mkdir": {
                    String opts[] = input.split(" ", 2);
                    String dirName = opts[1];
                    if (Utils.containsIllegals(dirName)) {
                        System.out.println("Illegal character found in the file name");
                        break;
                    }
                    fileSystem.writeDirectory(dirName);
                    break;
                }

                case "rmdir": {
                    // . and .. can't be deleted
                    String opts[] = input.split(" ", 2);
                    if (opts.length == 2) {
                        String name = opts[1];
                        if (name.equals(".") || name.equals("..")) {
                            System.out.println("The system can't delete this directory");
                        } else {
                            try {
                                if (!fileSystem.removeEntry(name, DirectoryEntry.DIRECTORY)) {
                                    System.out.printf("The system could not find the directory '%s'%n", name);
                                }
                            } catch (IllegalArgumentException iae) {
                                System.out.println(iae.getMessage());
                            }
                        }
                    } else {
                        System.out.println("Invalid 'rmdir' usage. Use 'rmdir [directory name]'");
                    }
                    break;
                }

                case "rm": {
                    String opts[] = input.split(" ", 2);
                    if (opts.length == 2) {
                        String name = opts[1];
                        if (!fileSystem.removeEntry(name, DirectoryEntry.FILE)) {
                            System.out.printf("The system could not find the file '%s'%n", name);
                        }
                    } else {
                        System.out.println("Invalid 'rm' usage. Use 'rm [filename]'");
                    }
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
        for (int block = 0; block < directory.size(); block++) {
            DirectoryBlock dirBlock = directory.get(block);
            for (int entry = 0; entry < dirBlock.size(); entry++) {
                DirectoryEntry dirEntry = dirBlock.get(entry);
                if (dirEntry.getFilename().equals(".") || dirEntry.getFilename().equals("..")) continue;

                System.out.printf((block == directory.size() - 1) && (entry == dirBlock.size() - 1)
                        ? (dirEntry.getType() == DirectoryEntry.DIRECTORY) ? ANSI_BLUE + "%s%n" + ANSI_RESET : "%s%n"
                        : (dirEntry.getType() == DirectoryEntry.DIRECTORY) ? ANSI_BLUE + "%s  " + ANSI_RESET : "%s  ", dirEntry.getFilename());
            }
        }
    }

    public void lsExtended(Directory directory) {
        InodeTable inodeTable = fileSystem.getInodeTable();
        Inode inode;
        String creationDate, accessDate, modifiedDate, fileName, type, size;
        System.out.format("%22s  %22s  %22s  %6s %8s %s%n", "Created", "Last access", "Modified", "Type", "Size", "Name");

        for (DirectoryBlock block : directory) {
            for (DirectoryEntry dirEntry : block) {
                if (dirEntry.getFilename().equals(".") || dirEntry.getFilename().equals("..")) continue;

                inode = inodeTable.get(dirEntry.getInode());
                creationDate = Utils.epochTimeToDate(inode.getCreationTime());
                accessDate = (dirEntry.getType() == DirectoryEntry.DIRECTORY) ? "" : Utils.epochTimeToDate(inode.getLastAccessTime());
                modifiedDate = (dirEntry.getType() == DirectoryEntry.DIRECTORY) ? "" : Utils.epochTimeToDate(inode.getModifiedTime());
                size = (dirEntry.getType() == DirectoryEntry.DIRECTORY) ? "" : Integer.toString(inode.getSize());
                type = (dirEntry.getType() == DirectoryEntry.DIRECTORY) ? "<DIR>" : "";
                fileName = dirEntry.getFilename();

                System.out.format(
                        (dirEntry.getType() == DirectoryEntry.DIRECTORY)
                                ? "%22s  %22s  %22s  %6s %8s " + ANSI_BLUE + "%s" + ANSI_BLUE + "%n" + ANSI_RESET
                                : "%22s  %22s  %22s  %6s %8s %s%n",
                        creationDate, accessDate, modifiedDate, type, size, fileName);
            }
        }
    }

    public void cat(String fileName) {
        try {
            byte contentBytes[] = fileSystem.readFile(fileName);
            if (contentBytes == null) {
                System.out.println("The system could not find the file");
                return;
            }
            String content = new String(contentBytes);
            System.out.println(content);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void cd(String path) throws IOException {
        // Used to restore the path in case this method throws an exception while building the path
        String rollbackPath = getCurrentPath();
        Directory initialDir = (path.startsWith("/")) ? fileSystem.getRoot() : fileSystem.getCurrentDirectory();
        currentPath = (path.startsWith("/")) ? "/" : currentPath;

        ArrayList<String> directories = Utils.splitPath(path);
        for (String name : directories) {
            DirectoryEntry entry = initialDir.findEntry(name, DirectoryEntry.DIRECTORY);
            if (entry != null) {
                if (entry.getType() == DirectoryEntry.DIRECTORY) {
                    Directory directory = new Directory();
                    ArrayList<Integer> dirBlocks;
                    int inodeNumber = entry.getInode();
                    Inode inode = fileSystem.getInodeTable().get(inodeNumber);
                    dirBlocks = inode.getDirectBlocks();

                    // Go through each block and read their dir_entries
                    for (int block : dirBlocks)
                        directory.add(fileSystem.readDirectoryBlock(block));

                    initialDir = directory;
                    currentPath = FilenameUtils.concat(getCurrentPath(), name.concat("/"));
                } else {
                    // It is a file so it doesn't have directory entries
                    currentPath = rollbackPath;
                    System.out.println("The system could not find the path specified");
                    return;
                }
            } else {
                currentPath = rollbackPath;
                System.out.println("The system could not find the path specified");
                return;
            }
        }
        fileSystem.setCurrentDirectory(initialDir);
    }

    public String getCurrentPath() {
        return currentPath == null ? "/" : FilenameUtils.separatorsToUnix(currentPath);
    }
}
