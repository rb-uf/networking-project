import java.util.Scanner;
import java.io.File;
import java.io.FileNotFoundException;

class CommonConfig {
    int NumberOfPreferredNeighbors;
    int UnchokingInterval;
    int OptimisticUnchokingInterval;
    String FileName;
    int FileSize;
    int PieceSize;
    
    CommonConfig(String configFileName) {
	Scanner f;
	try {
	    f = new Scanner(new File(configFileName));
	}
	catch (FileNotFoundException exc) {
	    return;
	}
	
	NumberOfPreferredNeighbors = f.skip("NumberOfPreferredNeighbors").nextInt();
	UnchokingInterval = f.skip("UnchokingInterval").nextInt();
	OptimisticUnchokingInterval = f.skip("OptimisticUnchokingInterval").nextInt();
	FileName = f.skip("FileName").next();
	FileSize = f.skip("FileSize").nextInt();
	PieceSize = f.skip("PieceSize").nextInt();
    }

    int getNumberOfPreferredNeighbors() { return NumberOfPreferredNeighbors; }
    int getUnchokingInterval() { return UnchokingInterval; }
    int getOptimisticUnchokingInterval() { return OptimisticUnchokingInterval; }
    String getFileName() { return FileName; }
    int getFileSize() { return FileSize; }
    int getPieceSize() { return PieceSize; }
}
