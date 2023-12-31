import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.Thread;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class PeerProcess {

	private static int sPort;   //The server will be listening on this port number
    private static final int BOUND = 100; // bound on BlockingQueue

    // variables from common.cfg
	public static int NumberOfPreferredNeighbors; // k
    public static int UnchokingInterval; // p
    public static int OptimisticUnchokingInterval; // m
    public static String FileName;
    public static int FileSize;
    public static int PieceSize;

	public static BitField bf; // keep track of which chunk it has
    public volatile static int serverPeerID; // visible to each thread
    public static boolean hasFile;
    public static Vector<Peer> peers;
	public static ConcurrentMap<Integer, Peer> peerMap = new ConcurrentHashMap<Integer, Peer>();
	public static Map<Integer, byte[]> chunks = new HashMap<>(); // maps each chunk of data to piece index
    // maps peerID to Peer info
    public static Boolean didChange; // used in timers, needs to be global

	public static ThreadGroup connections;

	// iterates everytime unchokingTask() is ran, each thread checks this to see if they send choke/unchoke message
	public static int globalIntervalCount = 0; 

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
			f = new Scanner(new File("PeerInfo.cfg"));
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
        
        connections = new ThreadGroup ("connections");
        ThreadGroup writer = new ThreadGroup ("writer");
        BlockingQueue<String> queue = new LinkedBlockingQueue<>(BOUND); // Queue for writer/producer log messages

        new LogWriter(serverPeerID, queue, writer).start();
		// deletes the previous log file
		String filePath = "log_peer_" + serverPeerID;
		File file = new File(filePath);
		file.delete();

        /* Borrowed parts from Don's Server.java */

        // contact all peers already running, thread the connection
        for (int i = 0; i < contactNum; i++)    {
            //create a socket to connect to the peer
			// think should replace "localhost" with peers.get(i).address when run on seperate machines
			new Handler(new Socket(peers.get(i).address, peers.get(i).port), peers.get(i).peerID, connections, queue).start();
			System.out.println("Connected to "+ peers.get(i).address + " in port " + peers.get(i).port);
        }

		// now listens for incoming connections
        ServerSocket listener = new ServerSocket(sPort);
		contactNum++;
        	try {
            	while(contactNum < contactCounter) { // once peer has contected/been contacted by all other peers, move on
                	new Handler(listener.accept(), connections, queue).start();
				    System.out.println("Client "  + contactNum + " is connected!");
                    contactNum++;
            		}
        	} finally {
            	listener.close(); // only closes ServerSocket, not spawned client sockets
        	} 


            
            // now initialize a timer for unchokingInterval + optimisticChockingInterval

        Timer t = new Timer ();
        t.scheduleAtFixedRate (new UnchokingTask(queue), UnchokingInterval*1000 ,UnchokingInterval*1000);
        t.scheduleAtFixedRate (new OptimisticTask(queue), OptimisticUnchokingInterval*1000,OptimisticUnchokingInterval*1000);
        
		

        while(!peersFinished())
        {
            
        }
        // maybe should sleep to make sure writer writes out to the logs
        connections.interrupt();
        writer.interrupt();
        t.cancel();
    }

    private static class UnchokingTask extends TimerTask   {
        private BlockingQueue<String> queue;

        public UnchokingTask(BlockingQueue<String> queue)   {
            this.queue = queue;
        }
        public void run ()
        {
			// Don's addition to this. used to help threads keep track of when this goes off
			globalIntervalCount++;

			//System.out.println("Printing out globalIntervalCount: " + globalIntervalCount);

            Vector<Integer> neighborsIDs = new Vector<Integer>();
            didChange = false;

            Iterator<ConcurrentMap.Entry<Integer, Peer> > itr = peerMap.entrySet().iterator(); 
            
            SortedMap<Long, Vector<ConcurrentMap.Entry<Integer, Peer>>> sm = new TreeMap<Long, Vector<ConcurrentMap.Entry<Integer, Peer>>>();
            while (itr.hasNext()) { 
                ConcurrentMap.Entry<Integer, Peer> entry = itr.next(); 
                Long key = entry.getValue().bytesDownloadAmount;
                if (sm.get(key) == null)
                    sm.put(key, new Vector<ConcurrentMap.Entry<Integer, Peer>>());
                
                sm.get(key).add(entry);
            }

            // now iterate through sm, chaning k entries to prefered neighbors and the rest to choked neighbors.
            int numberChosen = 0;
            Iterator<SortedMap.Entry<Long, Vector<ConcurrentMap.Entry<Integer, Peer>>>> smItr = sm.entrySet().iterator(); 
            while (numberChosen != NumberOfPreferredNeighbors && smItr.hasNext())   {
                SortedMap.Entry<Long, Vector<ConcurrentMap.Entry<Integer, Peer>>> entry = smItr.next();
                Vector<ConcurrentMap.Entry<Integer, Peer>> candidates = entry.getValue();
                if (candidates.size() >= 3) {
                    numberChosen += randomNeighbors(candidates, NumberOfPreferredNeighbors-numberChosen, neighborsIDs);

                }
                else    {
                    numberChosen += makeNeighbors(candidates, NumberOfPreferredNeighbors-numberChosen, neighborsIDs);
                }

            }
            while (smItr.hasNext()) { // set rest's isChoked = true
                SortedMap.Entry<Long, Vector<ConcurrentMap.Entry<Integer, Peer>>> entry = smItr.next();
                Vector<ConcurrentMap.Entry<Integer, Peer>> candidates = entry.getValue();
                for (int i = 0; i < candidates.size(); i++) {
                    if (candidates.get(i).getValue().isChoked == false) // change detected
                        didChange = true;
                    candidates.get(i).getValue().isChoked = true; // choked
                    candidates.get(i).getValue().bytesDownloadAmount = 0; // reset for next interval
                }
            }

            if (didChange)  {
                // create log message and add to queue
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now(); 
                String time = dtf.format(now);
                String log = time + ": Peer " + serverPeerID + " has the preferred neighbors " + neighborsIDs.get(0);
                for (int i = 1; i < neighborsIDs.size(); i++)   {
                    log += "," + neighborsIDs.get(i);
                }
                log += ".";
                try{
                    queue.put(log);
                }
                catch (InterruptedException e)    {
                    Thread.currentThread().interrupt();
                }
            }
            
            
        }   
        private int randomNeighbors(Vector<ConcurrentMap.Entry<Integer, Peer>> list, int maxToSelect, Vector<Integer> neighborsIDs) { // randomly sets Peer's in the entries unchoked, rest to choked, returns number chosen
            Collections.shuffle(list); // randomly orders the elements
            int numSelected = 0;
            int i = 0;
            for (; i < list.size() && numSelected < maxToSelect; i++)   {
                if (list.get(i).getValue().isChoked == true) // change detected
                    didChange = true;
                list.get(i).getValue().isChoked = false; // unchoked
                list.get(i).getValue().bytesDownloadAmount = 0; // reset for next interval
                neighborsIDs.add(list.get(i).getValue().peerID);
                numSelected++;

            }
            for (;i <list.size(); i++)  { // if there are more to check, set to choked
                if (list.get(i).getValue().isChoked == false) // change detected
                    didChange = true;
                list.get(i).getValue().isChoked = true; // choke
                list.get(i).getValue().bytesDownloadAmount = 0; // reset for next interval
            } 
            return numSelected;
        }
        private int makeNeighbors(Vector<ConcurrentMap.Entry<Integer, Peer>> list, int maxToSelect, Vector<Integer> neighborsIDs){ // choses elements off the top of the vector as neighbors
            int numSelected = 0;
            int i = 0;
            for (; i < list.size() && numSelected < maxToSelect; i++)   {
                if (list.get(i).getValue().isChoked == true) // change detected
                    didChange = true;
                list.get(i).getValue().isChoked = false; // unchoked
                list.get(i).getValue().bytesDownloadAmount = 0; // reset for next interval
                neighborsIDs.add(list.get(i).getValue().peerID);
                numSelected++;
            }
            for (;i <list.size(); i++)  { // if there are more to check, set to choked
                if (list.get(i).getValue().isChoked == false) // change detected
                    didChange = true;
                list.get(i).getValue().isChoked = true; // choke
                list.get(i).getValue().bytesDownloadAmount = 0; // reset for next interval
            } 
            return numSelected;
        }
    }

    private static class OptimisticTask extends TimerTask   {
        private BlockingQueue<String> queue;

        public OptimisticTask(BlockingQueue<String> queue)   {
            this.queue = queue;
        }
        public void run ()
        {
            didChange = false;
            Iterator<ConcurrentMap.Entry<Integer, Peer>> itr = peerMap.entrySet().iterator(); 
            Vector<ConcurrentMap.Entry<Integer, Peer>> entries = new Vector<ConcurrentMap.Entry<Integer, Peer>>();
            while (itr.hasNext()) { 
                ConcurrentMap.Entry<Integer, Peer> entry = itr.next(); 
                if (entry.getValue().isInterestedInMe && entry.getValue().isChoking)
                    entries.add(entry);
            }
            
            int index = getRandomValue(0, entries.size()-1);
            Integer changedPeerID = -1;
            if (index != -1)    {
                for (int i = 0; i < entries.size(); i++)    {
                    if (i == index) {
                        if (entries.get(i).getValue().isOptUnchoked == false)   {
                            didChange = true;
                            changedPeerID = entries.get(i).getValue().peerID;
                        }
                        entries.get(i).getValue().isOptUnchoked = true;
                    }
                    else {
                        if (entries.get(i).getValue().isOptUnchoked == true)   {
                            didChange = true;
                        }
                        entries.get(i).getValue().isOptUnchoked = false;
                    }
                }
            } 
            else { // no peers are interested and choking, set all peer's variable to false
                itr = peerMap.entrySet().iterator();
                while (itr.hasNext())   {
                    ConcurrentMap.Entry<Integer, Peer> entry = itr.next(); 
                    if (entry.getValue().isOptUnchoked == true)   {
                            didChange = true;
                        }
                    entry.getValue().isOptUnchoked = false; // set each peer to not first
                }
            }
            
            if (didChange && changedPeerID != -1)  {
                // create log message and add to queue
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
                LocalDateTime now = LocalDateTime.now(); 
                String time = dtf.format(now);
                String log = time + ": Peer " + serverPeerID + " has the optimistically unchoked neighbor " + changedPeerID + ".";
                try{
                    queue.put(log);
                }
                catch (InterruptedException e)    {
                    Thread.currentThread().interrupt();
                }
            }
            
        }

        public static int getRandomValue(int Min, int Max) 
        { 
            if (Max == -1)
                return -1;
            return ThreadLocalRandom.current().nextInt(Min, Max + 1); 
        } 
    }
	
    private static boolean peersFinished()  {
        return false; // need to implement
    }

    private static class LogWriter extends Thread   {
        private BlockingQueue<String> queue;
        private FileWriter fWriter;

        public LogWriter(int ID, BlockingQueue<String> queue, ThreadGroup tg)    {
            super(tg, String.valueOf(ID));
            this.queue = queue;
            try{
                fWriter = new FileWriter("log_peer_" + ID);
            }
            catch (IOException e)   {
                System.out.println(e.getMessage());
            }

        }
        public void run()   {

            // then read elements from blocking queue

            try {
                while(true) { // reads from buffer until interupted
                    String log = queue.take(); // get message from queue

					//System.out.println("printing out whatever gets to log: "+ log);
					
                    fWriter.write(log);
                    fWriter.write(System.getProperty( "line.separator" ));
                }
            }
            catch (IOException exception)   {
                System.out.println(exception.getMessage());
            }
            catch (InterruptedException e)    {
                Thread.currentThread().interrupt();
            }
            finally {
                try {
                    fWriter.close();
                }
                catch (IOException exception)   {
                    System.out.println(exception.getMessage());
                }
                
            }

        }
    }
    
	public static class Handler extends Thread {
		private Socket connection;
        private ObjectInputStream in;	//stream read from the socket
        private ObjectOutputStream out;    //stream write to the socket
		private boolean initiated;		// did this thread initiated the connection
        private int ID;                 // own id
        private int partnerID;          // determined on handshake or construction
        private BlockingQueue<String> logQueue;
		private ThreadGroup threadGroup;

        public Handler(Socket connection, ThreadGroup tg, BlockingQueue<String> queue) { // received connection, awaiting handshake
            super(tg, String.valueOf(serverPeerID)); // adds thread to thread group, under name of peer id
            this.connection = connection;
	    	this.initiated = false;
            this.partnerID = 0;
            this.ID = serverPeerID;
            this.logQueue = queue;
			threadGroup = tg;
        }
        public Handler(Socket connection, int partnerID, ThreadGroup tg, BlockingQueue<String> queue) { // started connection, sends first handshake
            super(tg, String.valueOf(serverPeerID)); // adds thread to thread group, under name of peer id
        	this.connection = connection;
	    	this.initiated = true;
            this.partnerID = partnerID;
            this.ID = serverPeerID;
            this.logQueue = queue;
			threadGroup = tg;
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
            // send the bitmap
			sendBitField();
			while(true){
				receiveMessage();

				if(utils.isInterestingBF(bf, peerMap.get(partnerID).bf) && partnerID == 1001){
					sendRequest();
				}
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
			System.exit(1);
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
			System.exit(1);
		}
	}


	// what happens when the server receives a bit field message
	// sends interested if bitfield is interesting
	private void receivedBitFieldMsg(byte[] msg){
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now(); 
        String time = dtf.format(now);
        String log = time + ": Peer " + serverPeerID + " received the 'bitfield' message from " + partnerID + ".";
		try{
            logQueue.put(log);
        }
        catch (InterruptedException e)    {
            Thread.currentThread().interrupt();
        }

		System.out.println("recevied bitfield from peer " + partnerID);

		peerMap.get(partnerID).bf = new BitField(utils.decompMsgPayload(msg), utils.decompMsgLength(msg) - 1);
		
		// checks if bitfield is interesting or not, then sends interested message
		if(utils.isInterestingBF(bf, peerMap.get(partnerID).bf)){
			sendInterested();
			peerMap.get(partnerID).isInteresting = true;
		}
		else{
			sendUninterested();
			peerMap.get(partnerID).isInteresting = false;
		}
	}

	// what happens when the server receives a piece
	private void receivedPieceMsg(byte[] msg){
		byte[] payload = utils.decompMsgPayload(msg);
		byte[] imageBytes = Arrays.copyOfRange(payload, 4, payload.length);
		int index = utils.bytesToInt(Arrays.copyOfRange(payload, 0, 4));
		
		chunks.put(index, imageBytes);
		
		// if all chunks have been received, then combine them and save it
		checkGotAllChunks();

		// updates the server side bitfield
		bf.setBit(index);

		// logging the action
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now(); 
        String time = dtf.format(now);
        String log = time + ": Peer " + serverPeerID + " has downloaded the piece " + index + " from " + partnerID + ". Now the number of pieces it has is " + chunks.size() + ".";
		
        try {
			logQueue.put(log);
		} catch (InterruptedException e) {
			System.err.println("error with logging");
		}

		System.out.println("Receiving piece from " + partnerID + "for piece "+index);

		// checks if the partnerPeer has anymore interesting pieces, if not, send uninterested
		if(!utils.isInterestingBF(bf, peerMap.get(partnerID).bf)){
			sendUninterested();
			peerMap.get(partnerID).isInteresting = false;
		}
		else sendRequest();

		// send have messages to all connected clients 
		utils.sendHaveMsgToAll(threadGroup, index);


	}

	public void sendBitField(){
		System.out.println("sent bitfield to peer "+ partnerID);

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
		if(chunks.size() == (int)Math.ceil((double) FileSize/PieceSize)){
			String dirPath = "./peer_"+serverPeerID;
			String filePath = "./peer_"+serverPeerID + "/" + FileName;

			// checks if directory exists, creates directory
			File directory = new File(dirPath);
			if (!directory.exists()) directory.mkdirs();

			byte[] combinedData = utils.combineChunks(chunks, FileSize);
			try (FileOutputStream fos = new FileOutputStream(filePath)) {
				fos.write(combinedData);
			} catch (IOException e) {
				System.err.println("Error saving the image to: " + filePath);
			}

			// logging the action
			DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        	LocalDateTime now = LocalDateTime.now(); 
        	String time = dtf.format(now);
        	String log = time + ": Peer " + serverPeerID + " has downloaded the complete file.";
			try{
        	    logQueue.put(log);
        	}
        	catch (InterruptedException e)    {
        	    Thread.currentThread().interrupt();
        	}
		}
	}


	// sends over the piece based on the index provided
	public void sendPiece(int index) throws IOException{
		

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
			System.out.println("Error in sendPiece()");
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

		System.out.println("Sending request to peer " + partnerID + " for " + requestedIndex);

		// if sendRequest got called but there are no pieces to request
		if(requestedIndex == -1){
			System.out.println("sendRequest() called, but no pieces to grab");
			return;
		}

		bf.setBit(requestedIndex); // sets the requested index so that other calls cannot request the same index


		byte msgType = 6;
		byte[] payload = utils.intToBytes(requestedIndex);
		byte[] msg = utils.createMessage(5, msgType, payload);

		try{
			out.writeObject(msg);
			out.flush(); 
		}
		catch(IOException ioException){
			System.out.println("Error in sendRequest()");
			System.exit(1);
		}
	}

	// receives the request for a certain piece, then sends over that piece
	public void receivedRequestMsg(byte[] msg) throws IOException{
		byte[] payload = utils.decompMsgPayload(msg);
		int requestedIndex = utils.bytesToInt(payload);


		sendPiece(requestedIndex);
	}

	// send interested message
	public void sendInterested(){
		

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
		// logging the action
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now(); 
        String time = dtf.format(now);
        String log = time + ": Peer " + serverPeerID + " received the 'interested' message from " + partnerID + ".";
		try{
            logQueue.put(log);
        }
        catch (InterruptedException e)    {
            Thread.currentThread().interrupt();
        }

		peerMap.get(partnerID).isInterestedInMe = true;
	}

	// send uninterested message
	public void sendUninterested(){

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
		// logging the action
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now(); 
        String time = dtf.format(now);
        String log = time + ": Peer " + serverPeerID + " received the 'not interested' message from " + partnerID + ".";
		try{
            logQueue.put(log);
        }
        catch (InterruptedException e)    {
            Thread.currentThread().interrupt();
        }
		peerMap.get(partnerID).isInterestedInMe = false;
	}

	public void sendChoke(){
		System.out.println("Sent choke to peer " + partnerID);
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
		// logging the action
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now(); 
        String time = dtf.format(now);
        String log = time + ": Peer " + serverPeerID + " is choked by " + partnerID + ".";
		try{
            logQueue.put(log);
        }
        catch (InterruptedException e)    {
            Thread.currentThread().interrupt();
        }
		System.err.println("Received choke from " + partnerID);
		peerMap.get(partnerID).chokedMe = true;
	}

	public void sendUnchoke(){
		System.out.println("Sent unchoke to peer " + partnerID);
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
		// logging the action
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now(); 
        String time = dtf.format(now);
        String log = time + ": Peer " + serverPeerID + " is unchoked by " + partnerID + ".";
		try{
            logQueue.put(log);
        }
        catch (InterruptedException e)    {
            Thread.currentThread().interrupt();
        }
		System.out.println("Received unchoke from "+partnerID);
		peerMap.get(partnerID).chokedMe = false;
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

		// if this bit is interesting, and then send interested if not already
		if(!bf.checkBit(haveIndex) && !peerMap.get(partnerID).isInteresting){
			sendInterested();
			peerMap.get(partnerID).isInteresting = true;
		}

		// logging the action
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now(); 
        String time = dtf.format(now);
        String log = time + ": Peer " + serverPeerID + " received the 'have' message from " + partnerID + " for the piece "+haveIndex+".";
		try{
            logQueue.put(log);
        }
        catch (InterruptedException e)    {
            Thread.currentThread().interrupt();
        }
	}

	// perform this everytime time interval is up
	public void performTasksAfterTimeUp(){
		if((!peerMap.get(partnerID).isChoked || peerMap.get(partnerID).isOptUnchoked) && peerMap.get(partnerID).isInterestedInMe){
			sendUnchoke();
		}
		else{
			sendChoke();
		}
		

		// only request again if not already requesting
		if(!peerMap.get(partnerID).chokedMe && peerMap.get(partnerID).isInteresting){
			sendRequest();
		}
	}

    }

}
