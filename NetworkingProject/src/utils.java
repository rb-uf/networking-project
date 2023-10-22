import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Arrays;

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


    public static void main(String args[])
	{
		// just here for testing

        
        

	}
}
