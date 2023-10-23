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
		byte[] msgLengthByte = new byte[4];
		for (int i = 0; i < 4; i++) {
            msgLengthByte[i] = (byte) (msgLength >> (i * 8));
        }

        // copies everything into r
        System.arraycopy(msgLengthByte, 0, r, 0, msgLengthByte.length);
        r[4] = msgType;
         System.arraycopy(payload, 0, r, 5, payload.length);

        return r;
    }

    // NOT TESTED YET
    // given a message in byte[] form, returns message length
    public static int decompMsgLength(byte[] msg){
        if(msg.length < 5){
            int r = 0;

            byte[] first4Bytes = new byte[4];
            System.arraycopy(msg, 0, first4Bytes, 0, 4);
            r = bytesToInt(first4Bytes);

            return r;
        }
        else{
            System.out.println("Error: msg legnth is less than 5");
            return -1;
        }
    }

    // NOT TESTED YET
    // given a message in byte[] form, returns message type
    public static byte decompMsgType(byte[] msg){
        if(msg.length < 5){
            byte r = msg[4];

            return r;
        }
        else{
            System.out.println("Error: msg legnth is less than 5");
            return -1;
        }
    }

    // NOT TESTED YET
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

    public static void main(String args[])
	{
		// just here for testing

        
        

	}
}
