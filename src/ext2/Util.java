package ext2;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author Wilmer
 */

// Contains useful utility methods for bit manipulation and other stuff
public final class Util {

    // Finds the first bit that is unset (0) or set (1) depending on the value of set,
    // and then toggles that same bit
    public static int getThenToggleBit(boolean set, byte... array) {
        // All block and inode addresses start at 1. 0 is used as a flag to indicate null or no inode
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                if (((b >>> j) & 1) != 0 && set) {
                    // Toggle bit and replace byte
                    b ^= (1 << j);
                    array[i] = b;
                    return index;
                } else if (((b >>> j) & 1) == 0 && !set) {
                    // Toggle bit and replace byte
                    b ^= (1 << j);
                    array[i] = b;
                    return index;
                }
                index++;
            }
        }
        return 0;
    }

    // Finds the first bit that is unset (0) or set (1) depending on the value of set
    public static int findFirstBit(boolean set, byte... array) {
        // All block and inode addresses start at 1. 0 is used as a flag to indicate null or no inode
        int index = 1;
        for (byte b : array) {
            for (int j = 7; j >= 0; j--) {
                if (((b >>> j) & 1) != 0 && set) return index;
                else if (((b >>> j) & 1) == 0 && !set) return index;
                index++;
            }
        }
        return 0;
    }

    public static void toggleBit(int bitIndex, byte[] array) {
        int index = 0;
        for (int i = 0; i < array.length; i++) {
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                // Toggle bit
                if (index == bitIndex) {
                    b ^= (1 << j);
                    array[i] = b;
                }
                index++;
            }
        }
    }

    public static String epochTimeToDate(int time) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss a");
        return sdf.format(new Date(time * 1000L));
    }

    // Converts an int array to a byte array
    public static byte[] toByteArray(int... array) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(array.length * 4);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(array);
        return byteBuffer.array();
    }

    public static byte[] toByteArray(short... array) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(array.length * 2);
        ShortBuffer shortBuffer = byteBuffer.asShortBuffer();
        shortBuffer.put(array);
        return byteBuffer.array();
    }

    public static byte[] toByteArray(byte... array) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(array.length);
        byteBuffer.put(array);
        return byteBuffer.array();
    }

    // Prints every byte in binary of a byte array
    public static void printBytes(byte... array) {
        String bin = "";
        for (byte b : array) {
            bin += String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0') + " ";
        }
        System.out.println(bin);
    }
}
