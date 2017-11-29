package ext2;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

/**
 * @author Wilmer
 */

public final class Util {

    // Finds the first bit that is unset (0) or set (1) depending on the value of 'set',
    // and then toggles that same bit
    public static int getThenToggleBit(boolean set, byte... array) {
        // Important: All block and inode addresses start at 1. 0 is used as a flag to indicate null or no inode
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            byte b = array[i];
            for (int bit = 7; bit >= 0; bit--) {
                if (((b >>> bit) & 1) != 0 && set) {
                    // Toggle bit and replace byte
                    b ^= (1 << bit);
                    array[i] = b;
                    return index;
                } else if (((b >>> bit) & 1) == 0 && !set) {
                    // Toggle bit and replace byte
                    b ^= (1 << bit);
                    array[i] = b;
                    return index;
                }
                index++;
            }
        }
        return 0;
    }

    // Finds the first bit that is unset (0) or set (1) depending on the value of 'set'
    public static int findFirstBit(boolean set, byte... array) {
        // All block and inode addresses start at 1. 0 is used as a flag to indicate null or no inode
        int index = 1;
        for (byte b : array) {
            for (int bit = 7; bit >= 0; bit--) {
                if (((b >>> bit) & 1) != 0 && set) return index;
                else if (((b >>> bit) & 1) == 0 && !set) return index;
                index++;
            }
        }
        return 0;
    }

    // Returns a list containing every index that corresponds to a bit set or unset depending on the value of 'set'
    // Used for data and inode bitmaps, so index starts at 1
    public static ArrayList<Integer> findAllBits(boolean set, byte...array) {
        ArrayList<Integer> list = new ArrayList<>();
        int index = 1;
        for (byte b : array) {
            for (int bit = 7; bit >= 0; bit--) {
                if (((b >>> bit) & 1) != 0 && set) list.add(index);
                else if (((b >>> bit) & 1) == 0 && !set) list.add(index);
                index++;
            }
        }
        return list;
    }

    // Toggles (changes from 1 to 0 or viceversa) one bit at the index specified by 'bitIndex'
    // For example: bitIndex of 12, toggles the fourth bit of the second byte of the array
    public static void toggleBit(int bitIndex, byte[] array) {
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            byte b = array[i];
            for (int bit = 7; bit >= 0; bit--) {
                // Toggle bit
                if (index == bitIndex) {
                    b ^= (1 << bit);
                    array[i] = b;
                }
                index++;
            }
        }
    }

    // Split an array of bytes into equal parts of size 'chunkSize'
    public static byte[][] splitBytes(byte[] data, int chunkSize) {
        final int length = data.length;
        final byte[][] result = new byte[(length + chunkSize - 1) / chunkSize][];
        int resultIndex = 0;
        int stopIndex = 0;

        for (int startIndex = 0; startIndex + chunkSize <= length; startIndex += chunkSize) {
            stopIndex += chunkSize;
            result[resultIndex++] = Arrays.copyOfRange(data, startIndex, stopIndex);
        }

        if (stopIndex < length)
            result[resultIndex] = Arrays.copyOfRange(data, stopIndex, length);
        return result;
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
