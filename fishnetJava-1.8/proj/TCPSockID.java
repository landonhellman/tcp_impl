/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: TCP Socket Connection Tuple</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Landon Hellman
 * @version 1.0
 */

public class TCPSockID {
    private int sourceFishnetAddress;
    private int destinationFishnetAddress;
    private int sourcePort;
    private int destinationPort;

    public TCPSockID(int sourceFishnetAddress, int destinationFishnetAddress, int sourcePort, int destinationPort) {
        this.sourceFishnetAddress = sourceFishnetAddress;
        this.destinationFishnetAddress = destinationFishnetAddress;
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
    }


}
