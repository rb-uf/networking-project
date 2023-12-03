public class Peer { // class which holds information about other peers for a single
                    // server
    public int peerID;
    public int port;
    public String address;
    public boolean isInterestedInMe;
    public boolean isInteresting;
    public boolean isChoking;
    public boolean isChoked;
    public boolean isOptUnchoked; // might want an isOptChoking
    public BitField bf; // keep track of which chunks they have
    public long bytesDownloadAmount; // used to unchoke peers

    public Peer(int peerID, int port, String address)   {
        this.peerID = peerID;
        this.port = port;
        this.address = address;
        isInterestedInMe = false;
        isInteresting = false;
        isChoked = true;
        isChoking = true;
        isOptChoked = false;
        bf = new BitField(PeerProcess.FileSize, PeerProcess.PieceSize);
        bytesDownloadAmount = 0;
    }
}
