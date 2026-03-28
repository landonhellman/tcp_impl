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

    // use EstRTT + 4 * DevRTT
    private final long AckTimeout = 500;
    private State state;

    // basically what was stated in the spec
    public int sourceFishnetAddress;
    public int destinationFishnetAddress;
    public int sourcePort;
    public int destinationPort;

    //
    public TCPSockID id;
    private TCPManager manager;


    // for retransmission COMMENT OUT EVERNTUALLY
    public boolean waitingForAck = false;
//    int transType;
//    byte[] transData;
//    int transSeqNum;
//    int transLen;
    public long timeoutTime;

    // QUESTION: can we just use the Java.util queue?
    Queue<TCPSock> pendingConnections = new LinkedList<>();

    // backlog
    private int backlog;
    public int expectedSeqNum;

    // buffer for reading and writing
    // ring buffer
    private static final int WINDOW_SLOTS = 4;
    public static final int windowSize = WINDOW_SLOTS * Transport.MAX_PAYLOAD_SIZE;

    private static class ringBuffer {
        boolean use;
        int seqNum;
        int len;
        byte[] data;
        long timeoutTime;
    }

    public int ringHead = 0;
    public int ringTail = 0;
    public int ringCount = 0;

    public int sendBase = 1;
    public ringBuffer[] buffer = new ringBuffer[WINDOW_SLOTS];
    public int nextSeqNum = 1;
    public long oldestTimeout;

    private LinkedList<Byte> recvBuffer = new LinkedList<>();

    public TCPSock(TCPManager manager) {
        this.manager = manager;
        this.state = State.CLOSED;

        for (int i = 0; i < WINDOW_SLOTS; i++) {
            buffer[i] = new ringBuffer();
        }
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

    // queue a socket connection
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
        if (!isClosed() || this.sourcePort == 0) {
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
                0, // I just decided to do 0 because that's a valid option. Technically not super safe though.
                new byte[0]
        );

        manager.node.sendSegment(this.sourceFishnetAddress, this.destinationFishnetAddress,
                Protocol.TRANSPORT_PKT, aPacket.pack());

        this.waitingForAck = true;
//        this.transType = Transport.SYN;
//        this.transSeqNum = 0;
//        this.transLen = 0;
//        this.transData = new byte[0];
        this.timeoutTime = manager.manager.now() + AckTimeout;

        System.out.print("S");
    }

    public void sendACK() {
        Transport aPacket = new Transport(
                this.sourcePort,
                this.destinationPort,
                Transport.ACK,
                windowSize,
                this.expectedSeqNum,
                new byte[0]
        );

        manager.node.sendSegment(this.sourceFishnetAddress, this.destinationFishnetAddress,
                Protocol.TRANSPORT_PKT, aPacket.pack());
    }

//    public void sendDATA(byte[] data, int pos, int len) {
    public void sendDATA(byte[] data, int seqNum) {
//        byte[] payload = new byte[len];
//        System.arraycopy(data, pos, payload, 0, len);

        Transport pkt = new Transport(
                this.sourcePort,
                this.destinationPort,
                Transport.DATA,
                windowSize,
                seqNum,
                data
        );

        manager.node.sendSegment(this.sourceFishnetAddress, this.destinationFishnetAddress,
                Protocol.TRANSPORT_PKT, pkt.pack()
        );

//        this.waitingForAck = true;
//        this.transType = Transport.DATA;
//        this.transData = new byte[len];
//        System.arraycopy(payload, 0, this.transData, 0, len);
//        this.transSeqNum = this.nextSeqNum;
//        this.transLen = len;
//        this.timeoutTime = manager.manager.now() + AckTimeout;

        System.out.print(".");
    }

    public void sendFIN() {
        Transport aPacket = new Transport(
                this.sourcePort,
                this.destinationPort,
                Transport.FIN,
                windowSize,
                this.nextSeqNum,
                new byte[0]
        );

        manager.node.sendSegment(this.sourceFishnetAddress, this.destinationFishnetAddress,
                Protocol.TRANSPORT_PKT, aPacket.pack());

        this.waitingForAck = true;
//        this.transType = Transport.FIN;
//        this.transSeqNum = this.nextSeqNum;
//        this.transLen = 0;
//        this.transData = new byte[0];
        this.timeoutTime = manager.manager.now() + AckTimeout;

        System.out.print("F");
    }

    /**
     * Initiate closure of a connection (graceful shutdown)
     */
    //
    public void close() {
        // DONT GIVE ME NEW DATA
        if (isConnected()) {
            state = State.SHUTDOWN;
            if (sendBase == nextSeqNum) {
                sendFIN();
            }
        }
    }

    /**
     * Release a connection immediately (abortive shutdown)
     */
    // flash everything
    public void release() {
//        this.waitingForAck = false;
//        this.transData = null;
//        this.transLen = 0;
        this.state = State.CLOSED;
        if (this.id != null) {
            manager.connections.remove(this.id);
        }
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

        if (len <= 0) {
            return 0;
        }

        int writtenBit = 0;

        while (writtenBit < len && (nextSeqNum < windowSize + sendBase) &&
                ringCount < WINDOW_SLOTS) {

            // making a packet
            int available = windowSize + sendBase - nextSeqNum;
            int chunk = Math.min(Transport.MAX_PAYLOAD_SIZE, len - writtenBit);
            chunk = Math.min(chunk, available);

            byte[] payload = new byte[chunk];
            System.arraycopy(buf, pos + writtenBit, payload, 0, chunk);

            //sending packet (udt_send)
            sendDATA(payload, nextSeqNum);

            ringBuffer s = buffer[ringTail];
            s.use = true;
            s.seqNum = nextSeqNum;
            s.len = chunk;
            s.data = payload;
            s.timeoutTime = manager.manager.now();

            ringTail = (ringTail + 1) % buffer.length;
            ringCount++;

            if (sendBase == nextSeqNum) {
                oldestTimeout = manager.manager.now() + AckTimeout;
            }

            nextSeqNum += chunk;
            writtenBit += chunk;
        }
//        except {
//            // BLOCK SENDER
//        }

        return writtenBit;
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
        if (!isConnected() && !isClosurePending() && !isClosed()) {
            return -1;
        }

        if (len <= 0 || recvBuffer.isEmpty()) {
            return 0;
        }

        int count = 0;

        // read from the buffer array!
        while (count < len && !recvBuffer.isEmpty()) {
            buf[pos + count] = recvBuffer.removeFirst();
            count++;
        }

        return count;
    }

    // important handlers.
    public void handleSYN(Packet p) {
        //get listener
        Transport t = Transport.unpack(p.getPayload());
        TCPSock listener = manager.listeners.get(t.getDestPort());

        if (listener == null) {
            return;
        }

        // maybe the listener should turn into a regular sock? I didn't really understand how to do this part.

        // create a new socket to send an ack back
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

        // in this case, it will always be 1
        sock.expectedSeqNum = t.getSeqNum() + 1;

        // we queue the socket because we await the ack
        listener.queueSock(sock);

        // this might need to be put later?
        manager.connections.put(sock.id, sock);
        sock.setEstablished();

        sock.sendACK();
    }

    public void handleACK(Packet p) {
        Transport t = Transport.unpack(p.getPayload());

        if (this.isConnectionPending()) {
            this.setEstablished();
            this.waitingForAck = false;
            System.out.print(":");
            return;
        }

        if (t.getSeqNum() > sendBase) {

            sendBase = t.getSeqNum();

            while (ringCount > 0) {
                ringBuffer s = buffer[ringHead];
                if (!s.use) break;

                if (s.seqNum + s.len <= t.getSeqNum()) {
                    s.use = false;
                    s.data = null;
                    ringHead = (ringHead + 1) % buffer.length;
                    ringCount--;
                } else {
                    break;
                }
            }

            oldestTimeout = (ringCount > 0) ? manager.manager.now() + AckTimeout : -1;
            System.out.print(":");
        } else {
            System.out.print("?");
        }

        if (this.isClosurePending() && sendBase == nextSeqNum) {
            this.waitingForAck = false;
            this.release();
            sendFIN();
        }

    }

    // this makes it nonblocking
    public void handleDATA(Packet p) {
        Transport t = Transport.unpack(p.getPayload());
        if (t == null) return;

        byte[] data = t.getPayload();

        if (t.getSeqNum() == this.expectedSeqNum) {
            for (byte b : data) {
                this.recvBuffer.addLast(b);
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

    public void retransmit_window() {
        for (int i = 0; i < ringCount; i++) {
            int idx = (ringHead + i) % buffer.length;
            ringBuffer s = buffer[idx];

            Transport pkt = new Transport(
                    sourcePort,
                    destinationPort,
                    Transport.DATA,
                    windowSize,
                    s.seqNum,
                    s.data
            );

            manager.node.sendSegment(
                    sourceFishnetAddress,
                    destinationFishnetAddress,
                    Protocol.TRANSPORT_PKT,
                    pkt.pack()
            );

            System.out.print("!");
        }
        oldestTimeout = manager.manager.now() + AckTimeout;
    }

    public void retransmit_control() {

        int transportType = (this.state == State.SYN_SENT) ? Transport.SYN : (isClosurePending()) ? Transport.FIN : -1;

        Transport pkt = new Transport(
                sourcePort,
                destinationPort,
                transportType,
                0,
                0,
                new byte[0]
        );

        manager.node.sendSegment(
                sourceFishnetAddress,
                destinationFishnetAddress,
                Protocol.TRANSPORT_PKT,
                pkt.pack()
        );

        System.out.print("!");

        timeoutTime = manager.manager.now() + AckTimeout;
    }

    /*
     * End of socket API
     */
}
