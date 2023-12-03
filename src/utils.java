import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

public class utils {

    // converts int to bytes
    public static byte[] intToBytes(int value) {
        byte[] bytes = new byte[4];
        bytes[0] = (byte) ((value >> 24) & 0xFF);
        bytes[1] = (byte) ((value >> 16) & 0xFF);
        bytes[2] = (byte) ((value >> 8) & 0xFF);
        bytes[3] = (byte) (value & 0xFF);
        return bytes;
    }

    // converts bytes to int
    public static int bytesToInt(byte[] byteArray) {
        if (byteArray.length != 4) {
            throw new IllegalArgumentException("Byte array must be exactly 4 bytes long to convert to an integer.");
        }
    
        int intValue = Integer.valueOf(byteArray[0] << 24 |
                                      (byteArray[1] & 0xFF) << 16 |
                                      (byteArray[2] & 0xFF) << 8 |
                                      (byteArray[3] & 0xFF));
    
        return intValue;
    }

    // creates handshake bytes based on peer ID
    public static byte[] createHandshake(int peerID){
        String s = "P2PFILESHARINGPROJ";
        byte[] bytes = s.getBytes();
        byte[] r = new byte[bytes.length + 14];
        System.arraycopy(bytes, 0, r, 0, bytes.length);
        byte[] ID = intToBytes(peerID);
        System.arraycopy(ID, 0, r, 28, 4);

        return r;
    }

    // given the parameters, returns the byte array of everything combined
    public static byte[] createMessage(int msgLength, byte msgType, byte[] payload){
        byte[] r = new byte[5 + payload.length];

		// create byte array out of the int of the message length
		byte[] msgLengthByte = intToBytes(msgLength);

        // copies everything into r
        System.arraycopy(msgLengthByte, 0, r, 0, msgLengthByte.length);
        r[4] = msgType;
         System.arraycopy(payload, 0, r, 5, payload.length);

        return r;
    }

    // given a message in byte[] form, returns message length
    public static int decompMsgLength(byte[] msg){
        if(msg.length < 5){
            System.out.println("Error: msg legnth is less than 5");
            return -1;
            
        }
        else{
            int r = 0;

            byte[] first4Bytes = new byte[4];
            System.arraycopy(msg, 0, first4Bytes, 0, 4);
            r = bytesToInt(first4Bytes);

            return r;
        }
    }

    // given a message in byte[] form, returns message type
    public static byte decompMsgType(byte[] msg){
        if(msg.length < 5){
            System.out.println("Error: msg legnth is less than 5");
            return -1;
        }
        else{
            byte r = msg[4];

            return r;
        }
    }

    // given a message in byte[] form, returns message payload
    public static byte[] decompMsgPayload(byte[] msg){
        if(msg.length > 5){
            int lengthOfNewArray = msg.length - 5;
            byte[] r = new byte[lengthOfNewArray];
            System.arraycopy(msg, 5, r, 0, lengthOfNewArray);

            return r;
        }
        else return new byte[0];
    }

    // given the map of chunks, return a single byte[] that can be turned into image or txt or whatever
    public static byte[] combineChunks(Map<Integer,byte[]> chunks, int fileSize){
        Map<Integer, byte[]> sortedChunks = new TreeMap<>(chunks);

        // Create the combined byte array
        byte[] combinedBytes = new byte[fileSize];
        int currentIndex = 0;

        // Concatenate the byte arrays in increasing order of keys
        for (byte[] chunk : sortedChunks.values()) {
            System.arraycopy(chunk, 0, combinedBytes, currentIndex, chunk.length);
            currentIndex += chunk.length;
        }

        return combinedBytes;
    }

    // given file path, index of piece, and piece size: returns the bytes of that piece
    public static byte[] readPieceBasedOnIndex(String path, int pieceIndex, int pieceSize){
        int startByte = pieceIndex * pieceSize;
        
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            // Set the file pointer to the starting position
            file.seek(startByte);
            
            // for when its the last piece in the file, so that i dont read more than the file
            int bytesToRead = (int)Math.min(pieceSize, file.length() - startByte); 

            // Read the specified range of bytes
            byte[] pieceData = new byte[bytesToRead];
            int bytesRead = file.read(pieceData);

            if (bytesRead != -1) {
                return pieceData;
            } else {
                System.out.println("End of file reached.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new byte[0];
    }

    // given 2 bitfields, returns index of a random 0 in selfBitField
    // where selfBitField doesnt have the piece but otherBitField does
    public static int getRandomZeroIndex(BitField selfBitField, BitField otherBitField){
        List<Integer> zeroIndices = new ArrayList<>();

        for (int i = 0; i < selfBitField.usedBits; i++) {
            if (!selfBitField.checkBit(i) && otherBitField.checkBit(i)) {
                zeroIndices.add(i);
            }
        }

        if (!zeroIndices.isEmpty()) {
            Random random = new Random();
            int randomIndex = zeroIndices.get(random.nextInt(zeroIndices.size()));
            return randomIndex;
        } else {
            // No '0' bits found
            return -1;
        }
    }


    // given two bitfield, decides if the second one is interesting to the first
    // criteria is if second BF has a 1 that first BF does not
    public static boolean isInterestingBF(BitField selfBitField, BitField otherBitField){
        for (int i = 0; i < selfBitField.usedBits; i++) {
            if(otherBitField.checkBit(i) == true && selfBitField.checkBit(i) == false) return true;
        }
        
        return false;
    }


    // does this after every redoing of the neighbors to a thread group
    public static void performTasksOnThreadsAfterTimeUp(ThreadGroup threadGroup){
        Thread[] threads = new Thread[threadGroup.activeCount()];
        int count = threadGroup.enumerate(threads, false);

        for (int i = 0; i < count; i++) {
            Thread thread = threads[i];
            
            // Instruct the thread to call a function
            if (thread instanceof PeerProcess.Handler) {
                ((PeerProcess.Handler) thread).performTasksAfterTimeUp();
            }
        }
    }

    public static void main(String args[])
	{
        
		
        

	}
}
