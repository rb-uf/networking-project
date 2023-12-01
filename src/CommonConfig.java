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
	    System.out.println("error: config file not found");
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
    }

    int getNumberOfPreferredNeighbors() { return NumberOfPreferredNeighbors; }
    int getUnchokingInterval() { return UnchokingInterval; }
    int getOptimisticUnchokingInterval() { return OptimisticUnchokingInterval; }
    String getFileName() { return FileName; }
    int getFileSize() { return FileSize; }
    int getPieceSize() { return PieceSize; }
}
