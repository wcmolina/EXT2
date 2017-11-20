package ext2;

import com.google.common.primitives.Bytes;
import java.nio.ByteBuffer;
import static java.lang.Math.toIntExact;

/**
 *
 * @author Wilmer
 */
public class Inode {
    public static final int DIRECTORY = 0;
    public static final int REGULAR = 1;
    // 4 bytes
    private final int TYPE;
    // 4 bytes
    private int size;
    // 4 bytes
    private int creationDate;
    // 4 bytes
    private int deletionDate;
    // 48 bytes
    private final int DIRECT_POINTERS[] = new int[12];

    // t: directory, regular
    // s: size of file in bytes
    public Inode(int t, int s) {
        TYPE = t;
        size = s;
        creationDate = toIntExact(System.currentTimeMillis() / 1000);
        deletionDate = 0;
    }

    // To 64 bytes array
    // Cada entry de la tabla de inodos (o sea cada inodo) debe abarcar 64 bytes
    // Los primeros 4 bytes son para el tipo del archivo (dir o file)
    // Los siguientes 4 bytes son para el size del archivo
    // Los 8 bytes que siguen son para el creation time y el deletion time (4 bytes c/u)
    // El resto de los 48 bytes son para los punteros a los bloques de datos
    public byte[] toByteArray() {
        final byte TYP[] = ByteBuffer.allocate(4).putInt(TYPE).array();
        final byte SZ[] = ByteBuffer.allocate(4).putInt(size).array();
        final byte C_TIME[] = ByteBuffer.allocate(4).putInt(creationDate).array();
        // 0 for now...
        final byte D_TIME[] = ByteBuffer.allocate(4).putInt(deletionDate).array();
        // faltan los 48 bytes a los punteros de los datos
        return Bytes.concat(TYP, SZ, C_TIME, D_TIME); // Thank God for Google Guava... Merges byte arrays
    }
}
