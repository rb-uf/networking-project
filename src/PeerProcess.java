import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.*;

public class PeerProcess {

	private static int sPort;   //The server will be listening on this port number
    
    // variables from common.cfg
	public static int NumberOfPreferredNeighbors;
    public static int UnchokingInterval;
    public static int OptimisticUnchokingInterval;
    public static String FileName;
    public static int FileSize;
    public static int PieceSize;

	public static BitField bf; // keep track of which chunk it has
    public volatile static int serverPeerID; // visible to each thread
    public static boolean hasFile;
    public static Vector<Peer> peers;
	public static ConcurrentMap<Integer, Peer> peerMap = new ConcurrentHashMap<Integer, Peer>();
	public static Map<Integer, byte[]> chunks = new HashMap<>(); // maps each chunk of data to piece index

	public static void main(String[] args) throws Exception {

        if (args.length != 1)   {
            System.out.println("Incorrect number of command line arguments.");
            System.out.println("Example Command:  java peerProcess 1001");
        }

        serverPeerID = Integer.parseInt(args[0]); // get ID from command line

        /* Borrowed parts from Don's Server.java */
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

        f.close();

        // read PeerInfo.cfg
        try {
			f = new Scanner(new File("PeerInfo_edit.cfg"));
		}
		catch (FileNotFoundException exc) {
			System.out.println("error: PeerInfo.cfg file not found");
			return;
		}

        int contactCounter = 0;
        int contactNum = 0;
        int currPeerID;
        String currPeerAddress;
        int currPeerPort;
        peers = new Vector<Peer>();
        
        while(f.hasNextLine())
        {
            currPeerID = f.nextInt();
            currPeerAddress = f.next();
            currPeerPort = f.nextInt();

            if (currPeerID == serverPeerID) {
                hasFile = (1 == f.nextInt()); // set has file when
                contactNum = contactCounter;
                sPort = currPeerPort;
            }
            else    {
                Peer currPeer = new Peer(currPeerID, currPeerPort, currPeerAddress);
                peers.add(currPeer);
                peerMap.put(currPeerID, currPeer);
                f.next();
            }
            contactCounter++;
        }

		// initilizes bit field, all values to 0
        bf = new BitField(FileSize, PieceSize);
        if (hasFile){ // if server has the file, set all bits to 1
		    bf.setAllBits();
		}
        

        /* Borrowed parts from Don's Server.java */

        // contact all peers already running, thread the connection
        for (int i = 0; i < contactNum; i++)    {
            //create a socket to connect to the peer
			new Handler(new Socket("localhost", peers.get(i).port), peers.get(i).peerID).start(); // think should replace "localhost" with peers.get(i).address when run on seperate machines
			System.out.println("Connected to "+ peers.get(i).address + " in port " + peers.get(i).port);
        }

		// now listens for incoming connections
        ServerSocket listener = new ServerSocket(sPort);
		contactNum++;
        	try {
            	while(true) {
                	new Handler(listener.accept()).start();
				    System.out.println("Client "  + contactNum + " is connected!");
                    contactNum++;
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
        	private ObjectInputStream 	in;	//stream read from the socket
        	private ObjectOutputStream out;    //stream write to the socket
		    private boolean initiated;		// did this thread initiated the connection
            private int ID;                 // own id
            private int partnerID;          // determined on handshake or construction
			
        	public Handler(Socket connection) { // received connection, awaiting handshake
            		this.connection = connection;
	    		    this.initiated = false;
                    this.partnerID = 0;
                    this.ID = serverPeerID; 
        	}

            public Handler(Socket connection, int partnerID) { // started connection, sends first handshake
            		this.connection = connection;
	    		    this.initiated = true;
                    this.partnerID = partnerID;
                    this.ID = serverPeerID;
        	}

            public void run() {
 		    try{
			    //initialize Input and Output streams
			    out = new ObjectOutputStream(connection.getOutputStream());
			    out.flush();
			    in = new ObjectInputStream(connection.getInputStream());
				

                if (initiated)  { // send handshake
                    sendHandshake();
                    verifyHandshake();
                }
                else {
                    receiveHandShake();
                    sendHandshake();
                }

				// sends bitfield
				sendBitField();


				// handles recieving any message
                while(true)
                {
					receiveMessage();

					// NOTE: temporary line of code, the sendRequest() should be invoked after a bunch of other checks
					if(utils.getRandomZeroIndex(bf,peerMap.get(partnerID).bf) != -1) sendRequest();

					
                }
            }
            catch(IOException ioException){
                System.out.println("Disconnect with Peer " + partnerID);
				closeConnection();
            }
            finally{
                //Close connections
                closeConnection();
            }
	}

	// //send a message to the output stream
	// public void sendMessage(String msg)
	// {
	// 	try{
	// 		out.writeObject(msg);
	// 		out.flush();
	// 		System.out.println("Send message: " + msg + " to Peer " + partnerID);
	// 	}
	// 	catch(IOException ioException){
	// 		ioException.printStackTrace();
	// 	}
	// }

    /* Borrowed parts from Don's Client.java */

    public void closeConnection(){
		System.out.println("(Client Side) Closing Connection with peer: " + partnerID);
		try{
			in.close();
			out.close();
			connection.close();
		}
		catch(IOException ioException){
			ioException.printStackTrace();
		}
	}

    // sends a handshake to the server
	public void sendHandshake(){
		System.out.println("(Client Side) Sending handshake to peer " + partnerID);
		byte[] h = utils.createHandshake(ID);
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
				System.out.println("(Client Side) Bad string in handshake");
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
				System.out.println("(Client Side) Bad zero bytes in handshake");
				closeConnection();
			}
			
			// checks the server side peer ID
			int handshakeID = utils.bytesToInt(intBytes);
			if (handshakeID != partnerID)   {
                System.out.println("(Client Side) Incorrect ID");
				closeConnection();
            }
			System.out.println("(Client Side) Connected to peer ID: " + handshakeID);
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
    // used by receiver to interpret and configure itself
    public void receiveHandShake(){
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
			
			// sets partnerID to handshakeID
			int handshakeID = utils.bytesToInt(intBytes);
			partnerID = handshakeID; // maybe should verify that this is one of the peers
			System.out.println("(Server Side) Connected to peer ID: " + handshakeID);
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

    /* Borrowed parts from Don's Client.java */


	/*
	 * runs this after handshake.
	 * Reads any message, then switchs based on the message type
	 * NOT DONE YET
	 */
	public void receiveMessage(){
		try{
			byte[] msg = (byte[])in.readObject();
			byte msgType = utils.decompMsgType(msg);
			switch(msgType){
				case 0:
					receivedChokeMsg(msg);
					break;
				case 1:
					receivedUnchokeMsg(msg);
					break;
				case 2:
					receivedInterestedMsg(msg);
					break;
				case 3:
					receivedUninterestedMsg(msg);
					break;
				case 4:
					receivedHaveMsg(msg);
					break;
				case 5: // bit field
					receivedBitFieldMsg(msg);
					break;
				case 6:
					receivedRequestMsg(msg);
					break;
				case 7:
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
		System.out.println("Recieving bitfield from peer " + partnerID);
		peerMap.get(partnerID).bf = new BitField(utils.decompMsgPayload(msg), utils.decompMsgLength(msg) - 1);
	}

	// what happens when the server receives a piece
	private void receivedPieceMsg(byte[] msg){
		byte[] payload = utils.decompMsgPayload(msg);
		byte[] imageBytes = Arrays.copyOfRange(payload, 4, payload.length);
		int index = utils.bytesToInt(Arrays.copyOfRange(payload, 0, 4));

		System.out.println("Recieving piece ("+index+")from peer " + partnerID);
		
		chunks.put(index, imageBytes);
		
		// if all chunks have been received, then combine them and save it
		checkGotAllChunks();

		// updates the server side bitfield
		bf.setBit(index);

		// checks if the partnerPeer has anymore interesting pieces, if not, send uninterested
		if(!utils.isInterestingBF(bf, peerMap.get(partnerID).bf)){
			sendUninterested();
		}
	}

	public void sendBitField(){
		System.out.println("Sending bitfield to peer " + partnerID);

		// bitfield messages have a value of 5
		byte msgType = 5;

		// creates the msg object
		byte[] msg = utils.createMessage(1 + bf.getNumOfBytes(), msgType, bf.getBitField());

		try{
			out.writeObject(msg);
			out.flush(); 
		}
		catch(IOException ioException){
			System.out.println("Error in sendBitField()");
		}
	}

	// checks if it has all the chunks
	// creates the directory and file
	public void checkGotAllChunks(){
		if(chunks.size() == Math.ceilDiv(FileSize, PieceSize)){
			String dirPath = "./peer_"+serverPeerID;
			String filePath = "./peer_"+serverPeerID + "/" + FileName;

			// checks if directory exists, creates directory
			File directory = new File(dirPath);
			if (!directory.exists()) directory.mkdirs();

			byte[] combinedData = utils.combineChunks(chunks, FileSize);
			try (FileOutputStream fos = new FileOutputStream(filePath)) {
				fos.write(combinedData);
			} catch (IOException e) {
				e.printStackTrace();
				System.err.println("Error saving the image to: " + filePath);
			}
		}
	}


	// sends over the piece based on the index provided
	public void sendPiece(int index) throws IOException{
		System.out.println("Sending piece ("+ index +") to peer " + partnerID);

		String path = "peer_"+serverPeerID+"/"+FileName;

		byte msgType = 7;
		
		byte[] indexInBytes = utils.intToBytes(index);
		byte[] pieceData = utils.readPieceBasedOnIndex(path, index, PieceSize);
		byte[] payload = Arrays.copyOf(indexInBytes, indexInBytes.length + pieceData.length);
        System.arraycopy(pieceData, 0, payload, 4, pieceData.length);

		byte[] msg = utils.createMessage(1+4+pieceData.length, msgType, payload);

		try{
			out.writeObject(msg);
			out.flush(); 
		}
		catch(IOException ioException){
			System.out.println("Error in sendRequest()");
		}
	}

	// sends request message
	// the requested index is randomly chosen based on pieces that have not been requested yet and do not have
	/*
	 * NOTE: currently, updates bf upon request. Don't know if this is bad idea or not
	 * This function updates its own bitfield before it received the piece from the request. 
	 * This means that if a new PeerProcess is initiated while other peers are transferring, then it will assume 
	 * that this peer has a piece that it has not received yet, so the image will look wonky.
	 * BUT, this is not an issue because no PeerProcess will be initiated while peers are transferring in our experiment setup.
	 */
	public void sendRequest(){
		int requestedIndex = utils.getRandomZeroIndex(bf,peerMap.get(partnerID).bf);

		// if sendRequest got called but there are no pieces to request
		if(requestedIndex == -1){
			System.out.println("sendRequest() called, but no pieces to grab");
			return;
		}

		bf.setBit(requestedIndex); // sets the requested index so that other calls cannot request the same index

		System.out.println("Sending request ("+ requestedIndex +") to peer " + partnerID);

		byte msgType = 6;
		byte[] payload = utils.intToBytes(requestedIndex);
		byte[] msg = utils.createMessage(5, msgType, payload);

		try{
			out.writeObject(msg);
			out.flush(); 
		}
		catch(IOException ioException){
			System.out.println("Error in sendRequest()");
		}
	}

	// receives the request for a certain piece, then sends over that piece
	public void receivedRequestMsg(byte[] msg) throws IOException{
		byte[] payload = utils.decompMsgPayload(msg);
		int requestedIndex = utils.bytesToInt(payload);

		System.out.println("Recieving request ("+ requestedIndex +") from peer " + partnerID);

		sendPiece(requestedIndex);
	}

	// send interested message
	public void sendInterested(){
		System.out.println("Sending interested to peer " + partnerID);

		byte msgType = 2;
		byte[] emptyPayload = new byte[0];
		byte[] msg = utils.createMessage(1, msgType, emptyPayload);

		try{
			out.writeObject(msg);
			out.flush(); 
		}
		catch(IOException ioException){
			System.out.println("Error in sendInterested()");
		}
	}

	// just updates isInterestedInMe in peerMap
	public void receivedInterestedMsg(byte[] msg){
		System.out.println("Receiving interested from peer " + partnerID);
		peerMap.get(partnerID).isInterestedInMe = true;
	}

	// send uninterested message
	public void sendUninterested(){
		System.out.println("Sending uninterested to peer " + partnerID);

		byte msgType = 3;
		byte[] emptyPayload = new byte[0];
		byte[] msg = utils.createMessage(1, msgType, emptyPayload);

		try{
			out.writeObject(msg);
			out.flush(); 
		}
		catch(IOException ioException){
			System.out.println("Error in sendUninterested()");
		}
	}

	// just updates isInterestedInMe in peerMap
	public void receivedUninterestedMsg(byte[] msg){
		System.out.println("Receiving interested from peer " + partnerID);
		peerMap.get(partnerID).isInterestedInMe = false;
	}

	public void sendChoke(){
		System.out.println("Sending choke to peer " + partnerID);
		byte msgType = 0;
		byte[] emptyPayload = new byte[0];
		byte[] msg = utils.createMessage(1, msgType, emptyPayload);

		try{
			out.writeObject(msg);
			out.flush(); 
		}
		catch(IOException ioException){
			System.out.println("Error in sendChoke()");
		}
	}

	// just updates isChoked in peerMap
	public void receivedChokeMsg(byte[] msg){
		System.out.println("Receiving choke from peer " + partnerID);
		peerMap.get(partnerID).isChoked = true;
	}

	public void sendUnchoke(){
		System.out.println("Sending unchoke to peer " + partnerID);
		byte msgType = 1;
		byte[] emptyPayload = new byte[0];
		byte[] msg = utils.createMessage(1, msgType, emptyPayload);

		try{
			out.writeObject(msg);
			out.flush(); 
		}
		catch(IOException ioException){
			System.out.println("Error in sendunChoke()");
		}
	}

	// just updates isChoked in peerMap
	public void receivedUnchokeMsg(byte[] msg){
		System.out.println("Receiving unchoke from peer " + partnerID);
		peerMap.get(partnerID).isChoked = false;
	}


	// haveIndex represents the index in the bitfield that says that this client has this piece.
	public void sendHave(int haveIndex){
		byte msgType = 4;
		byte[] payload = utils.intToBytes(haveIndex);
		byte[] msg = utils.createMessage(5, msgType, payload);

		try{
			out.writeObject(msg);
			out.flush(); 
		}
		catch(IOException ioException){
			System.out.println("Error in sendHave()");
		}
	}

	// updates the BF in peerMap to reflect that the partnerPeer has this piece
	public void receivedHaveMsg(byte[] msg){
		byte[] payload = utils.decompMsgPayload(msg);
		int haveIndex = utils.bytesToInt(payload);

		peerMap.get(partnerID).bf.setBit(haveIndex);
	}


    }

}
