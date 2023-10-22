import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

public class Client {
	Socket requestSocket;           //socket connect to the server
	ObjectOutputStream out;         //stream write to the socket
 	ObjectInputStream in;          //stream read from the socket
	String message;                //message send to the server
	String MESSAGE;                //capitalized message read from the server

	int peerID = 1002; // figure out how to set this later
	int serverPeerID;

	public void Client() {}

	void run()
	{
		try{
			//create a socket to connect to the server
			requestSocket = new Socket("localhost", 8000);
			System.out.println("Connected to localhost in port 8000");
			//initialize inputStream and outputStream
			out = new ObjectOutputStream(requestSocket.getOutputStream());
			out.flush();
			in = new ObjectInputStream(requestSocket.getInputStream());

			sendHandshake();
			verifyHandshake();
			
			//get Input from standard input
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
			while(true)
			{
				System.out.print("Hello, please input a sentence: ");
				//read a sentence from the standard input
				message = bufferedReader.readLine();
				//Send the sentence to the server
				sendMessage(message);
				//Receive the upperCase sentence from the server
				MESSAGE = (String)in.readObject();
				//show the message to the user
				System.out.println("Receive message: " + MESSAGE);
			}
		}
		catch (ConnectException e) {
    			System.err.println("Connection refused. You need to initiate a server first.");
		} 
		catch ( ClassNotFoundException e ) {
            		System.err.println("Class not found");
        	} 
		catch(UnknownHostException unknownHost){
			System.err.println("You are trying to connect to an unknown host!");
		}
		catch(IOException ioException){
			ioException.printStackTrace();
		}
		finally{
			//Close connections
			closeConnection();
		}
	}

	public void closeConnection(){
		System.out.println("(Client Side) Closing Connection with peer: " + serverPeerID);
		try{
			in.close();
			out.close();
			requestSocket.close();
		}
		catch(IOException ioException){
			ioException.printStackTrace();
		}
	}

	//send a message to the output stream
	void sendMessage(String msg)
	{	
		try{
			//stream write the message
			out.writeObject(msg);
			out.flush();
		}
		catch(IOException ioException){
			ioException.printStackTrace();
		}
	}

	// sends a handshake to the server
	public void sendHandshake(){
		System.out.println("(Client Side) Sending handshake to peer");
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
			// should verify that it is correct peerID somehow?
			System.out.println("(Client Side) Connected to peer ID: " + handshakeID);
			serverPeerID = handshakeID;
		}
		catch(ClassNotFoundException classnot){
			// idk
		}
		catch(IOException ioException){
			// idk
		}
	}


	//main method
	public static void main(String args[])
	{
		Client client = new Client();
		client.run();
	}

}