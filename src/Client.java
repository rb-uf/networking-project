import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.*;
import java.util.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class Client {
	Socket requestSocket;           //socket connect to the server
	ObjectOutputStream out;         //stream write to the socket
 	ObjectInputStream in;          //stream read from the socket
	String message;                //message send to the server
	String MESSAGE;                //capitalized message read from the server

	int peerID = 1002; // figure out how to set this later
	int serverPeerID;

	

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

			sendPiece();
			
			while(true)
			{	
				
			}
		}
		catch (ConnectException e) {
    			System.err.println("Connection refused. You need to initiate a server first.");
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
			System.err.println("Class not found");
			closeConnection();
		}
		catch(IOException ioException){
			ioException.printStackTrace();
			closeConnection();
		}
	}

	// NOT TESTED YET
	public void sendBitField(BitField payload){
		// bitfield messages have a value of 5
		byte msgType = 5;

		// creates the msg object
		byte[] msg = utils.createMessage(1 + payload.getNumOfBytes(), msgType, payload.getBitField());

		try{
			out.writeObject(msg);
			out.flush(); 
		}
		catch(IOException ioException){
			System.out.println("Error in sendBitField()");
		}
	}

	public void sendChoke(){
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

	public void sendUnchoke(){
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

	// requestIndex represents the index in the bitfield that says that this client wants this piece.
	public void sendRequest(int requestIndex){
		byte msgType = 6;
		byte[] payload = utils.intToBytes(requestIndex);
		byte[] msg = utils.createMessage(5, msgType, payload);

		try{
			out.writeObject(msg);
			out.flush(); 
		}
		catch(IOException ioException){
			System.out.println("Error in sendRequest()");
		}
	}

	// sends over the piece that is randomly chosen
	// currently sends whoel image over
	public void sendPiece() throws IOException{
		String path = "./src/peer_1001/tree.jpg"; // set this up later to read from path based on peer number

		byte msgType = 7;

		// makes imageBYtes contain byte[] of image
		Path path2 = Paths.get(path);
		byte[] imageBytes = Files.readAllBytes(path2);

		int index = 0; // IMPLEMENT CHOOSe INDEX OF PIECE
		
		// creates payload, did this because payload contains index of piece, and then the piece bytes
		byte[] indexBytes = utils.intToBytes(index);
		byte[] payload = Arrays.copyOf(indexBytes, indexBytes.length + imageBytes.length);
        System.arraycopy(imageBytes, 0, payload, indexBytes.length, imageBytes.length);

		byte[] msg = utils.createMessage(1+4+imageBytes.length, msgType, payload);
		

		try{
			out.writeObject(msg);
			out.flush(); 
		}
		catch(IOException ioException){
			System.out.println("Error in sendRequest()");
		}
	}

	

	


	//main method
	public static void main(String args[])
	{
		Client client = new Client();
		client.run();
	}

}