import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

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
                peers.add(new Peer(currPeerID, currPeerPort, currPeerAddress));
                f.next();
            }
            contactCounter++;
        }

		// initilizes bit field, all values to 0
        bf = new BitField(FileSize, PieceSize);
        if (hasFile) // if server has the file, set all bits to 1
		    bf.setAllBits();
        

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
        	private String message;    //message received from the client
		    private String MESSAGE;    //uppercase message send to the client
		    private Socket connection;
        	private ObjectInputStream in;	//stream read from the socket
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
                while(true)
                {

                }
            }
            catch(IOException ioException){
                System.out.println("Disconnect with Peer " + partnerID);
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

    }

}
