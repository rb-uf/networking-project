import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

public class Server {

	private static final int sPort = 8000;   //The server will be listening on this port number
	private static final int peerID = 1001; // figure out how to set this later

	public static void main(String[] args) throws Exception {
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
       	private String message;    //message received from the client
		private String MESSAGE;    //uppercase message send to the client
		private Socket connection;
       	private ObjectInputStream in;	//stream read from the socket
       	private ObjectOutputStream out;    //stream write to the socket
		private int no;		//The index number of the client

		private int clientPeerID;

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

				try{
					while(true)
					{
						//receive the message sent from the client
						message = (String)in.readObject();
						//show the message to the user
						System.out.println("Receive message: " + message + " from client " + no);
						//Capitalize all letters in the message
						MESSAGE = message.toUpperCase();
						//send MESSAGE back to the client
						sendMessage(MESSAGE);
					}
				}
				catch(ClassNotFoundException classnot){
					System.err.println("Data received in unknown format");
				}
			}
			catch(IOException ioException){
				System.out.println("Disconnect with Client " + no);
			}
			finally{
				closeConnection();
			}
		}

        //send bytes to client
        //just using this for testing purposes
        public void sendBytes(Object o){
            try{
				out.writeObject(o);
				out.flush();
				System.out.println("Sent some bytes to Client " + no);
			}
			catch(IOException ioException){
				ioException.printStackTrace();
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
				// idk
			}
			catch(IOException ioException){
				// idk
			}
		}

	} // end of handler definition

	


} // end of server definition
