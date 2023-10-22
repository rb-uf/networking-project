
class TestCommonConfig {
    public static void main(String args[]) {
	CommonConfig cfg = new CommonConfig("Common.cfg");
	
	System.out.println("NumberOfPreferredNeighbors " + cfg.getNumberOfPreferredNeighbors());
	System.out.println("UnchokingInterval " + cfg.getUnchokingInterval());
	System.out.println("OptimisticUnchokingInterval " + cfg.getOptimisticUnchokingInterval());
	System.out.println("FileName " + cfg.getFileName());
	System.out.println("FileSize " + cfg.getFileSize());
	System.out.println("PieceSize " + cfg.getPieceSize());
    }
}
