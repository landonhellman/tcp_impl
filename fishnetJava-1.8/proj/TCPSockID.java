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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TCPSockID)) return false;
        TCPSockID other = (TCPSockID) o;
        return this.sourceFishnetAddress == other.sourceFishnetAddress &&
                this.destinationFishnetAddress == other.destinationFishnetAddress &&
                this.sourcePort == other.sourcePort &&
                this.destinationPort == other.destinationPort;
    }

    @Override
    public int hashCode() {
        int result = sourceFishnetAddress;
        result = 31 * result + destinationFishnetAddress;
        result = 31 * result + sourcePort;
        result = 31 * result + destinationPort;
        return result;
    }

    @Override
    public String toString() {
        return "(" + sourceFishnetAddress + "," + destinationFishnetAddress + "," + sourcePort + "," + destinationPort + ")";
    }

}
