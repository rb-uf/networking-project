public class BitField {
    private byte[] data; // The array to store the bits
    public int usedBits; // the number of bits being used (to account for padding of zeros at the end)

    // initializes everything to 0
    public BitField(int fileSize, int pieceSize) {
        usedBits = (fileSize+pieceSize-1)/pieceSize;
        data = new byte[(usedBits + 7) / 8]; // Calculate the array size needed for the specified number of bits
    }

    // pass in payload bitfield and the length of the payload
    public BitField(byte[] bf, int length){
        data = bf;
        usedBits = length;
    }

    // set bit to 1
    public void setBit(int position) {
        if (position >= 0 && position < data.length * 8) {
            int arrayIndex = position / 8;
            int bitIndex = position % 8;
            data[arrayIndex] |= (1 << bitIndex);
        }
    }

    // set bit to 0
    public void clearBit(int position) {
        if (position >= 0 && position < data.length * 8) {
            int arrayIndex = position / 8;
            int bitIndex = position % 8;
            data[arrayIndex] &= ~(1 << bitIndex);
        }
    }

    // returns true if bit is 1, false if bit is 0
    public boolean checkBit(int position) {
        if (position >= 0 && position < data.length * 8) {
            int arrayIndex = position / 8;
            int bitIndex = position % 8;
            return ((data[arrayIndex] & (1 << bitIndex)) != 0);
        }
        return false;
    }

    // set all bits to 1
    public void setAllBits(){
        for(int i=0; i<usedBits; i++){
            setBit(i);
        }
    }

    // returns the data used to represent the bit field
    public byte[] getBitField(){
        return data;
    }

    // returns the number of bytes being used
    public int getNumOfBytes(){
        return data.length;
    }

    // returns string representation of bitfield
    public String toString() {
        String r = "";
        for (int i = 0; i < data.length*8; i++) {
            if(checkBit(i)) r = r + "1";
            else r = r + "0";
        }
        return r;
    }

    // for testing/demonstration purposes
    public static void main(String[] args) {
        // creates a new bit field that only uses 10 pieces: 20/2 = 10
        BitField bitField = new BitField(20, 2);
        bitField.setBit(1); // set second bit to 1
        bitField.setBit(2); // set thrid bit to 1
        System.out.println("After setting index 1 and 2 to true: " + bitField.toString());
        bitField.clearBit(1); // set all bits to 0
        System.out.println("After clearing index 1: " + bitField.toString());

        // set all bits to 1
        // notice how there are 0s at the end since only 10 bits are used but there are 16 bits in 2 bytes
        bitField.setAllBits(); 
        System.out.println("After setting all bits: " + bitField.toString());
    }
}
