package ext2;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;

public final class BitUtils {

    // Returns the index of the first cleared bit that is found
    public static int nextClearBit(byte[] array) {
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            // Loop through every byte
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                if (((b >>> j) & 1) == 0) return index;
                index++;
            }
        }
        return 0;
    }

    // Returns the index of the first set bit that is found
    public static int nextSetBit(byte[] array) {
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            // Loop through every byte
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                if (((b >>> j) & 1) != 0) return index;
                index++;
            }
        }
        return 0;
    }

    // Returns the index of the first cleared bit that is found and sets that same bit to 1
    public static int nextClearBitThenSet(byte[] array) {
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            // Loop through every byte
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                if (((b >>> j) & 1) == 0) {
                    b |= (1 << j); // set bit j to 1
                    array[i] = b;
                    return index;
                }
                index++;
            }
        }
        return 0;
    }

    // Returns the index of the first set bit that is found and clears that same bit to 0
    public static int nextSetBitThenClear(byte[] array) {
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            // Loop through every byte
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                if (((b >>> j) & 1) != 0) {
                    b &= ~(1 << j); // set bit j to 0
                    array[i] = b;
                    return index;
                }
                index++;
            }
        }
        return 0;
    }

    // Sets bit at index 'bitIndex' to 1
    public static void setBit(int bitIndex, byte[] array) {
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                // Set bit
                if (index == bitIndex) {
                    b |= (1 << j); // set bit j to 1
                    array[i] = b;
                    return;
                }
                index++;
            }
        }
    }

    // Clears bit at index 'bitIndex' to 0
    public static void clearBit(int bitIndex, byte[] array) {
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                // Set bit
                if (index == bitIndex) {
                    b &= ~(1 << j); // set bit j to 0
                    array[i] = b;
                    return;
                }
                index++;
            }
        }
    }

    // Toggles bit at index 'bitIndex'
    public static void toggleBit(int bitIndex, byte[] array) {
        int index = 1;
        for (int i = 0; i < array.length; i++) {
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                if (index == bitIndex) {
                    // Toggle bit
                    b ^= (1 << j);
                    array[i] = b;
                    return;
                }
                index++;
            }
        }
    }

    // Returns a list containing every index that corresponds to a bit set or unset depending on the value of 'set'
    public static ArrayList<Integer> findAllSetBits(byte[] array) {
        ArrayList<Integer> list = new ArrayList<>();
        int index = 1;
        for (byte b : array) {
            for (int bit = 7; bit >= 0; bit--) {
                if (((b >>> bit) & 1) != 0) list.add(index);
                index++;
            }
        }
        return list;
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
