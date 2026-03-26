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
import java.util.Queue;
import java.util.LinkedList;

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
    private final long AckTimeout = 500;
    private State state;

    public int sourceFishnetAddress;
    public int destinationFishnetAddress;
    public int sourcePort;
    public int destinationPort;
    public TCPSockID id;
    private TCPManager manager;


    // for retransmission
    public boolean waitingForAck = false;
    int transType;
    byte[] transData;
    int transSeqNum;
    int transLen;
    public long timeoutTime;

    // QUESTION: can we just use the Java.util queue?
    Queue<TCPSock> pendingConnections = new LinkedList<>();

    private int backlog;
    public int expectedSeqNum;

    public int nextSeqNum = 1;

    LinkedList<Byte> buffer = new LinkedList<>();

    public TCPSock(TCPManager manager) {
        this.manager = manager;
        this.state = State.CLOSED;
    }

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

    public void queueSock(TCPSock sock) {
        if (pendingConnections.size() < backlog) {
            pendingConnections.add(sock);
        }
    }

    /**
     * Accept a connection on a socket
     *
     * @return TCPSock The first established connection on the request queue
     */
    public TCPSock accept() {
        if (!pendingConnections.isEmpty()) {
            return pendingConnections.poll();
        }
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

    public void setEstablished() {
        state = State.ESTABLISHED;
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

        this.state = State.SYN_SENT;
        manager.connections.put(this.id, this);
        sendSYN();

        return 0;
    }

    public void sendSYN() {
        Transport aPacket = new Transport(
                this.sourcePort,
                this.destinationPort,
                Transport.SYN,
                0,
                0,
                new byte[0]
        );

        manager.node.sendSegment(this.sourceFishnetAddress, this.destinationFishnetAddress,
                Protocol.TRANSPORT_PKT, aPacket.pack());

        this.waitingForAck = true;
        this.transType = Transport.SYN;
        this.transSeqNum = 0;
        this.transLen = 0;
        this.transData = new byte[0];
        this.timeoutTime = manager.manager.now() + AckTimeout;

        System.out.print("S");
    }

    public void sendACK() {
        Transport aPacket = new Transport(
                this.sourcePort,
                this.destinationPort,
                Transport.ACK,
                0,
                this.expectedSeqNum,
                new byte[0]
        );

        manager.node.sendSegment(this.sourceFishnetAddress, this.destinationFishnetAddress,
                Protocol.TRANSPORT_PKT, aPacket.pack());
    }

    public void sendDATA(byte[] data, int pos, int len) {
        byte[] payload = new byte[len];
        System.arraycopy(data, pos, payload, 0, len);

        Transport pkt = new Transport(
                this.sourcePort,
                this.destinationPort,
                Transport.DATA,
                0,
                this.nextSeqNum,
                payload
        );

        manager.node.sendSegment(this.sourceFishnetAddress, this.destinationFishnetAddress,
                Protocol.TRANSPORT_PKT, pkt.pack()
        );

        this.waitingForAck = true;
        this.transType = Transport.DATA;
        this.transData = new byte[len];
        System.arraycopy(payload, 0, this.transData, 0, len);
        this.transSeqNum = this.nextSeqNum;
        this.transLen = len;
        this.timeoutTime = manager.manager.now() + AckTimeout;

        System.out.print(".");
    }

    public void sendFIN() {
        Transport aPacket = new Transport(
                this.sourcePort,
                this.destinationPort,
                Transport.FIN,
                0,
                this.nextSeqNum,
                new byte[0]
        );

        manager.node.sendSegment(this.sourceFishnetAddress, this.destinationFishnetAddress,
                Protocol.TRANSPORT_PKT, aPacket.pack());

        this.waitingForAck = true;
        this.transType = Transport.FIN;
        this.transSeqNum = this.nextSeqNum;
        this.transLen = 0;
        this.transData = new byte[0];
        this.timeoutTime = manager.manager.now() + AckTimeout;

        System.out.print("F");
    }

    /**
     * Initiate closure of a connection (graceful shutdown)
     */
    public void close() {
        if (isConnected()) {
            state = State.SHUTDOWN;
            sendFIN();
        }
    }

    /**
     * Release a connection immediately (abortive shutdown)
     */
    public void release() {
        this.state = State.CLOSED;
        sendFIN();
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
        if (!isConnected()) {
            return -1;
        }

        if (len <= 0 || this.waitingForAck) {
            return 0;
        }

        sendDATA(buf, pos, Math.min(len, Transport.MAX_PAYLOAD_SIZE));
        nextSeqNum += Math.min(len, Transport.MAX_PAYLOAD_SIZE);
        return Math.min(len, Transport.MAX_PAYLOAD_SIZE);
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
        if (buffer.isEmpty()) {
            return 0;
        }
        int count = 0;

        while (count < len && !buffer.isEmpty()) {
            buf[pos + count] = buffer.removeFirst();
            count++;
        }

        return count;
    }

    public void handleSYN(Packet p) {
        Transport t = Transport.unpack(p.getPayload());

        TCPSock listener = manager.listeners.get(t.getDestPort());

        if (listener == null) {
            return;
        }

        TCPSock sock = manager.socket();
        sock.sourcePort = t.getDestPort();
        sock.destinationPort = t.getSrcPort();
        sock.sourceFishnetAddress = manager.getAddr();
        sock.destinationFishnetAddress = p.getSrc();

        sock.id = new TCPSockID(
                sock.sourceFishnetAddress,
                sock.destinationFishnetAddress,
                sock.sourcePort,
                sock.destinationPort
        );

        sock.expectedSeqNum = t.getSeqNum() + 1;

        listener.queueSock(sock);

        manager.connections.put(sock.id, sock);
        sock.setEstablished();

        sock.sendACK();
    }

    public void handleACK(Packet p) {
        Transport t = Transport.unpack(p.getPayload());

        TCPSockID id = new TCPSockID(
                p.getDest(),
                p.getSrc(),
                t.getDestPort(),
                t.getSrcPort()
        );

        if (this.isConnectionPending()) {
            this.setEstablished();
            this.waitingForAck = false;
            System.out.print(":");
            return;
        }

        if (this.isClosurePending()) {
            this.waitingForAck = false;
            this.state = State.CLOSED;
            manager.connections.remove(id);
            System.out.print(":");
            return;
        }

        if (this.waitingForAck && (t.getSeqNum() == this.transSeqNum + this.transLen)) {
            this.waitingForAck = false;
            this.transData = null;
            this.transLen = 0;
            System.out.print(":");
        } else {
            System.out.print("?");
        }
    }

    public void handleDATA(Packet p) {
        Transport t = Transport.unpack(p.getPayload());
        byte[] data = t.getPayload();

        if (t.getSeqNum() == this.expectedSeqNum) {
            for (byte b : data) {
                this.buffer.add(b);
            }
            this.expectedSeqNum += data.length;
            System.out.print(".");
        } else {
            System.out.print("!");
        }

        this.sendACK();
    }

    public void handleFIN(Packet p) {
        this.sendACK();
        this.state = State.CLOSED;
        manager.connections.remove(id);
    }

    /*
     * End of socket API
     */
}
