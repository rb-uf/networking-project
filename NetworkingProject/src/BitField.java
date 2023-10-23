public class BitField {
    private int[] data; // The array to store the bits
    private int size; // how many pieces are being used and represented in the bit field

    public BitField(int fileSize, int pieceSize) {
        size = (fileSize+pieceSize-1)/pieceSize; // rounds up the int division
        data = new int[(size + 31) / 32]; // Calculate the array size needed for the specified number of bits
    }

    // set bit to 1
    public void setBit(int position) {
        if (position >= 0 && position < data.length * 32) {
            int arrayIndex = position / 32;
            int bitIndex = position % 32;
            data[arrayIndex] |= (1 << bitIndex);
        }
    }

    // set bit to 0
    public void clearBit(int position) {
        if (position >= 0 && position < data.length * 32) {
            int arrayIndex = position / 32;
            int bitIndex = position % 32;
            data[arrayIndex] &= ~(1 << bitIndex);
        }
    }

    // returns true if bit is 1, false if bit is 0
    public boolean checkBit(int position) {
        if (position >= 0 && position < data.length * 32) {
            int arrayIndex = position / 32;
            int bitIndex = position % 32;
            return ((data[arrayIndex] & (1 << bitIndex)) != 0);
        }
        return false;
    }

    // set all bits to 1
    public void setAllBits(){
        for(int i=0; i<size; i++){
            setBit(i);
        }
    }

    // returns string representation of bitfield
    public String toString() {
        String r = "";
        for (int i = 0; i < size; i++) {
            if(checkBit(i)) r = r + "1";
            else r = r + "0";
        }
        return r;
    }

    // for testing/demonstration purposes
    public static void main(String[] args) {
        BitField bitField = new BitField(11, 2);
        bitField.setBit(1);
        bitField.setBit(2);
        System.out.println("After setting index 1 and 2 to true: " + bitField.toString());
        bitField.clearBit(1);
        System.out.println("After clearing index 1: " + bitField.toString());
        bitField.setAllBits();
        System.out.println("After setting all bits: " + bitField.toString());
    }
}
