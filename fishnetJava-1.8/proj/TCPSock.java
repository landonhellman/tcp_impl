/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet socket implementation</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */

public class TCPSock {
    // TCP socket states
    enum State {
        // protocol states
        CLOSED,
        LISTEN,
        SYN_SENT,
        ESTABLISHED,
        SHUTDOWN // close requested, FIN not sent (due to unsent data in queue)
    }
    private State state;

    private int sourceFishnetAddress;
    private int destinationFishnetAddress;
    private int sourcePort;
    private int destinationPort;
    private TCPSockID id;
    private TCPManager manager;

    private int backlog;

    public TCPSock(TCPManager manager) {
        this.manager = manager;
    }

//    public TCPSock(int sourceFishnetAddress, int destinationFishnetAddress,
//                   int sourcePort, int destinationPort) {
//        this.id = new TCPSockID(sourceFishnetAddress, destinationFishnetAddress, sourcePort, destinationPort);
//    }

    /*
     * The following are the socket APIs of TCP transport service.
     * All APIs are NON-BLOCKING.
     */

    /**
     * Bind a socket to a local port
     *
     * @param localPort int local port number to bind the socket to
     * @return int 0 on success, -1 otherwise
     */
    public int bind(int sourcePort) {

//        System.out.println("BOUND");
        if (!isClosed()) {
            return -1;
        }

        if (!manager.bindPort(sourcePort, this)) {
            return -1;
        }

        this.sourcePort = sourcePort;
        this.sourceFishnetAddress = manager.getAddr();
        return 0;

    }

    /**
     * Listen for connections on a socket
     * @param backlog int Maximum number of pending connections
     * @return int 0 on success, -1 otherwise
     */
    public int listen(int backlog) {
        if (!isClosed() || this.sourcePort == 0) {
            return -1;
        }

        this.backlog = backlog;
        this.state = State.LISTEN;
        this.manager.createListener(this.sourcePort, this);

        return 0;

    }

    /**
     * Accept a connection on a socket
     *
     * @return TCPSock The first established connection on the request queue
     */
    public TCPSock accept() {
        return null;
    }

    public boolean isConnectionPending() {
        return (state == State.SYN_SENT);
    }

    public boolean isClosed() {
        return (state == State.CLOSED);
    }

    public boolean isConnected() {
        return (state == State.ESTABLISHED);
    }

    public boolean isClosurePending() {
        return (state == State.SHUTDOWN);
    }

    /**
     * Initiate connection to a remote socket
     *
     * @param destAddr int Destination node address
     * @param destPort int Destination port
     * @return int 0 on success, -1 otherwise
     */
    public int connect(int destAddr, int destPort) {
        if (!isClosed()) {
            return -1;
        }

        this.destinationFishnetAddress = destAddr;
        this.destinationPort = destPort;

        this.id = new TCPSockID(sourceFishnetAddress, destAddr, sourcePort, destPort);

        manager.sendSYN(this);

        this.state = State.SYN_SENT;

        return 0;
    }

    /**
     * Initiate closure of a connection (graceful shutdown)
     */
    public void close() {
    }

    /**
     * Release a connection immediately (abortive shutdown)
     */
    public void release() {
        this.state = State.CLOSED;
    }

    /**
     * Write to the socket up to len bytes from the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer to write from
     * @param pos int starting position in buffer
     * @param len int number of bytes to write
     * @return int on success, the number of bytes written, which may be smaller
     *             than len; on failure, -1
     */
    public int write(byte[] buf, int pos, int len) {
        return -1;
    }

    /**
     * Read from the socket up to len bytes into the buffer buf starting at
     * position pos.
     *
     * @param buf byte[] the buffer
     * @param pos int starting position in buffer
     * @param len int number of bytes to read
     * @return int on success, the number of bytes read, which may be smaller
     *             than len; on failure, -1
     */
    public int read(byte[] buf, int pos, int len) {
        return -1;
    }

    /*
     * End of socket API
     */
}
