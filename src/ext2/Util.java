package ext2;

/**
 *
 * @author Wilmer
 */
public final class Util {
    // Contains useful utility methods for bit manipulation and stuff

    // Finds the first bit that is unset (0), and then sets (changes to 1) that same bit
    // Used for the file system bitmaps
    public static int firstBitUnset(byte... array) {
        int index = 0;
        for (int i = 0; i < array.length; i++) {
            // Loop through every byte
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                // Loop through every bit j in byte b
                if (!(b << ~j < 0)) {
                    // Bit j not set
                    b |= (1 << j); // set bit j to 1
                    array[i] = b;
                    return index;
                }
                index++;
            }
        }
        return 0;
    }

    // Useful later on when deleting is implemented
    // Finds the first bit that is set (1), and then unsets (changes to 0) that same bit
    // Used for the file system bitmaps
    public static int firstBitSet(byte... array) {
        int index = 0;
        for (int i = 0; i < array.length; i++) {
            // Loop through every byte
            byte b = array[i];
            for (int j = 7; j >= 0; j--) {
                // Loop through every bit j in byte b
                if (b << ~j < 0) {
                    // Bit j set
                    b &= ~(1 << j); // set bit j to 0
                    array[i] = b;
                    return index;
                }
                index++;
            }
        }
        return 0;
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
