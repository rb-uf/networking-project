import java.net.*;
import java.io.*;
import java.util.*;

public class Server {

	private static final int sPort = 8000;   //The server will be listening on this port number
	public static final int peerID = 1001; // figure out how to set this later

	// variables from common.cfg
	public static int NumberOfPreferredNeighbors;
    public static int UnchokingInterval;
    public static int OptimisticUnchokingInterval;
    public static String FileName;
    public static int FileSize;
    public static int PieceSize;

	public static BitField bf; // keep track of which chunk it has
	public static Map<Integer, byte[]> chunks = new HashMap<>(); // maps each chunk of data to piece index
	
	// goes to common.cfg and reads in its info
	// NOTE: does not check PeerInfo.cfg yet
	public Server(){
		Scanner f;
		try {
			f = new Scanner(new File("Common.cfg"));
		}
		catch (FileNotFoundException exc) {
			System.out.println("error: Common.cfg file not found");
			return;
		}

		f.next(); // skip over the string "NumberOfPreferredNeighbors"; should probably check this
		NumberOfPreferredNeighbors = f.nextInt();
		f.next();
		UnchokingInterval = f.nextInt();
		f.next();
		OptimisticUnchokingInterval = f.nextInt();
		f.next();
		FileName = f.next();
		f.next();
		FileSize = f.nextInt();
		f.next();
		PieceSize = f.nextInt();

		// initilizes bit field, all values to 0
		bf = new BitField(FileSize, PieceSize);
	}

	int getNumberOfPreferredNeighbors() { return NumberOfPreferredNeighbors; }
    int getUnchokingInterval() { return UnchokingInterval; }
    int getOptimisticUnchokingInterval() { return OptimisticUnchokingInterval; }
    String getFileName() { return FileName; }
    int getFileSize() { return FileSize; }
    int getPieceSize() { return PieceSize; }


	

	public static void main(String[] args) throws Exception {
		Server s = new Server(); // needs this to just initialize everything in server
		System.out.println("The server is running."); 
        ServerSocket listener = new ServerSocket(sPort);
		int clientNum = 1;
		
        try {
        	while(true) {
            	new Handler(listener.accept(),clientNum).start();
				System.out.println("Client "  + clientNum + " is connected!");
				clientNum++;
        	}
        } finally {
        		listener.close();
        } 
 
    }

	/**
     	* A handler thread class.  Handlers are spawned from the listening
     	* loop and are responsible for dealing with a single client's requests.
     	*/
	private static class Handler extends Thread {
		private Socket connection;
       	private ObjectInputStream in;	//stream read from the socket
       	private ObjectOutputStream out;    //stream write to the socket
		private int no;		//The index number of the client

		private int clientPeerID; 
		private BitField clientBitField; // bit field of the client

       	public Handler(Socket connection, int no) {
           	this.connection = connection;
	   		this.no = no;
       	}

       	public void run() {
			try{
				//initialize Input and Output streams
				out = new ObjectOutputStream(connection.getOutputStream());
				out.flush();
				in = new ObjectInputStream(connection.getInputStream());

				sendHandshake();
				verifyHandshake();

				
				while(true)
				{
					// this is the general function that will catch all of the messages
					receiveMessage();
				}
			}
			catch(IOException ioException){
				System.out.println("Disconnect with Client " + no);
			}
			finally{
				closeConnection();
			}
		}

		//send a message to the output stream
		public void sendMessage(String msg)
		{
			try{
				out.writeObject(msg);
				out.flush();
				System.out.println("Send message: " + msg + " to Client " + no);
			}
			catch(IOException ioException){
				ioException.printStackTrace();
			}
		}

		/*
		 * NOTE: closing connection on one end will not immediately close connection on other end.
		 * when the other end tries to use connection, it will throw error which is caught
		 * by catch block, then finally block closes the connection.
		 * So you can close connection whenever and you're still good.
		 */
		public void closeConnection(){
			System.out.println("(Server Side) Closing Connection with peer: " + clientPeerID);
			try{
				in.close();
				out.close();
				connection.close();
			}
			catch(IOException ioException){
				System.out.println("Disconnect with Client " + no);
			}
		}

		// sends a handshake to the client
		public void sendHandshake(){
			System.out.println("(Server Side) Sending handshake to peer");
			byte[] h = utils.createHandshake(peerID);
			try{
				out.writeObject(h);
				out.flush(); 
			}
			catch(IOException ioException){
				ioException.printStackTrace();
			}
		}

		// verifies the handshake
		// checks if the string is correct, has 10 zero bytes, and checks the peerID
		public void verifyHandshake(){
			try{
				byte[] handshake = (byte[])in.readObject();
				byte[] strBytes = Arrays.copyOfRange(handshake, 0, 18);
				byte[] zBytes = Arrays.copyOfRange(handshake, 18, 28);
				byte[] intBytes = Arrays.copyOfRange(handshake, 28, 32);
				
				// checks string
				String s = new String(strBytes);
				if (!s.equals("P2PFILESHARINGPROJ")) {
					System.out.println("(Server Side) Bad string in handshake");
					closeConnection();
				}

				// checks zero bytes
				boolean allZero = true;
				for (byte b : zBytes) {
					if (b != 0) {
						allZero = false;
						break; 
					}
				}
				if(!allZero){
					System.out.println("(Server Side) Bad zero bytes in handshake");
					closeConnection();
				}

				// checks the client side peer ID
				int handshakeID = utils.bytesToInt(intBytes);
				// should verify that it is correct peerID somehow?
				System.out.println("(Server Side) Connected to peer ID: " + handshakeID);
				clientPeerID = handshakeID;
			}
			catch(ClassNotFoundException classnot){
				System.err.println("Class not found");
				closeConnection();
			}
			catch(IOException ioException){
				ioException.printStackTrace();
				closeConnection();
			}
		}

		/*
		 * runs this after handshake.
		 * Reads any message, then switchs based on the message type
		 * NOT TESTED YET
		 * NOT DONE YET
		 */
		public void receiveMessage(){
			try{
				byte[] msg = (byte[])in.readObject();
				byte msgType = utils.decompMsgType(msg);

				switch(msgType){
					case 0:

						break;
					case 1:

						break;
					case 2:

						break;
					case 3:

						break;
					case 4:

						break;
					case 5: // bit field
						System.out.println("received bitfield message");
						receivedBitFieldMsg(msg);
						break;
					case 6:

						break;
					case 7:
						System.out.println("received peice message");
						receivedPieceMsg(msg);
						break;
					default:
						System.err.println("Closing connection. Error, Bad msg type: " + msgType);
						closeConnection();
						break;
				} // end of switch statement


			}
			catch(ClassNotFoundException classnot){
				System.err.println("Class not found");
				closeConnection();
			}
			catch(IOException ioException){
				ioException.printStackTrace();
				closeConnection();
			}
		}

		// what happens when the server receives a bit field message
		private void receivedBitFieldMsg(byte[] msg){
			clientBitField = new BitField(utils.decompMsgPayload(msg), utils.decompMsgLength(msg) - 1);
		}

		// what happens when the server receives a piece
		// NOTE: current saves to this directory: "./"
		private void receivedPieceMsg(byte[] msg){
			byte[] payload = utils.decompMsgPayload(msg);
			byte[] imageBytes = Arrays.copyOfRange(payload, 4, payload.length);
			int index = utils.bytesToInt(Arrays.copyOfRange(payload, 0, 4));
			
			chunks.put(index, imageBytes);
			
			// if all chunks have been received, then combine them and save it
			if(chunks.size() == Math.ceilDiv(FileSize, PieceSize)){
				String path = "./image.jpg"; // set this up dynamically later
				byte[] combinedData = utils.combineChunks(chunks, FileSize);
				try (FileOutputStream fos = new FileOutputStream(path)) {
					fos.write(combinedData);
				} catch (IOException e) {
					e.printStackTrace();
					System.err.println("Error saving the image to: " + path);
				}
			}
		}


	} // end of handler definition

	


} // end of server definition
