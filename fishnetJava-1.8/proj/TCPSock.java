/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet socket implementation</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang / Landon Hellman
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
    private State state;


    // use EstRTT + 4 * DevRTT from the slides
    private static final long INITIAL_TIMEOUT = 5000;
    private static final long MIN_TIMEOUT = 100;
    private static final long MAX_TIMEOUT = 500000;
    public long controlSendTime;
    public boolean controlRetransmitted = false;

    private double estRTT = -1;
    private double devRTT = -1;
    private long TIMEOUT = INITIAL_TIMEOUT;


    // Congestion Control
    private int cwnd = Transport.MAX_PAYLOAD_SIZE;


    // Used for TCPSockID. Basically stated in the spec.
    public int sourceFishnetAddress;
    public int destinationFishnetAddress;
    public int sourcePort;
    public int destinationPort;

    public TCPSockID id;
    private TCPManager manager;


    // For Retransmission
    public boolean waitingForAck = false;
    public long timeoutTime;


    // Queue for pending connections
    Queue<TCPSock> pendingConnections = new LinkedList<>();


    // Backlog
    private int backlog;
    public int expectedSeqNum;


    // Ring Buffer used for reading and writing
    // Recommended by Professor. Thank you!
    private static final int WINDOW_SLOTS = 4;
    public static final int windowSize = WINDOW_SLOTS * Transport.MAX_PAYLOAD_SIZE;
    public int remoteRequestedWindow = windowSize;

    private static class ringBuffer {
        boolean use;
        int seqNum;
        int len;
        byte[] data;
        long sendTime;
        long timeoutTime;
    }

    public int ringHead = 0;
    public int ringTail = 0;
    public int ringCount = 0;

    public int sendBase = 1;
    public ringBuffer[] buffer = new ringBuffer[WINDOW_SLOTS];
    public int nextSeqNum = 1;
    public long oldestTimeout;


    // Receiver Buffer so not completely overwhelmed
    private LinkedList<Byte> recvBuffer = new LinkedList<>();
    public int RECEIVER_BUFFER_SIZE = 8 * Transport.MAX_PAYLOAD_SIZE;


    // Constructor
    public TCPSock(TCPManager manager) {
        this.manager = manager;
        this.state = State.CLOSED;

        // Initialize the Ring Buffer
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
    public boolean queueSock(TCPSock sock) {
        if (pendingConnections.size() >= backlog) {
            return false;
        }
        pendingConnections.add(sock);
        return true;
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

        this.sendBase = 2;
        this.nextSeqNum = 2;

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
                1, // I just decided to do 1 because that's a valid option. Technically not super safe though.
                new byte[0]
        );

        manager.node.sendSegment(this.sourceFishnetAddress, this.destinationFishnetAddress,
                Protocol.TRANSPORT_PKT, aPacket.pack());

        this.waitingForAck = true;
        this.controlRetransmitted = false;
        this.controlSendTime = manager.manager.now();
        this.timeoutTime = this.controlSendTime + this.TIMEOUT;

        System.out.print("S");
    }

    public void sendACK() {

//        System.out.println("TIMEOUT IS NOW: " +  timeoutTime);

        int requestedWindow = RECEIVER_BUFFER_SIZE - recvBuffer.size();

        Transport aPacket = new Transport(
                this.sourcePort,
                this.destinationPort,
                Transport.ACK,
                requestedWindow,
                this.expectedSeqNum,
                new byte[0]
        );

        manager.node.sendSegment(this.sourceFishnetAddress, this.destinationFishnetAddress,
                Protocol.TRANSPORT_PKT, aPacket.pack());
    }

    public void sendDATA(byte[] data, int seqNum) {

        int requestedWindow = RECEIVER_BUFFER_SIZE - recvBuffer.size();

        Transport pkt = new Transport(
                this.sourcePort,
                this.destinationPort,
                Transport.DATA,
                requestedWindow,
                seqNum,
                data
        );

        manager.node.sendSegment(this.sourceFishnetAddress, this.destinationFishnetAddress,
                Protocol.TRANSPORT_PKT, pkt.pack()
        );

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
        this.controlRetransmitted = false;
        this.controlSendTime = manager.manager.now();
        this.timeoutTime = this.controlSendTime + this.TIMEOUT;

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
        this.waitingForAck = false;
        this.controlRetransmitted = false;
        this.timeoutTime = 0;
        this.oldestTimeout = 0;

        this.ringHead = 0;
        this.ringTail = 0;
        this.ringCount = 0;

        for (int i = 0; i < buffer.length; i++) {
            buffer[i].use = false;
            buffer[i].data = null;
            buffer[i].len = 0;
            buffer[i].seqNum = 0;
            buffer[i].sendTime = 0;
            buffer[i].timeoutTime = 0;
        }

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
        int usableWindow = Math.min(remoteRequestedWindow, cwnd);

        while (writtenBit < len && (nextSeqNum < usableWindow + sendBase) &&
                ringCount < WINDOW_SLOTS) {

            // Make a good packet size
            int available = usableWindow + sendBase - nextSeqNum;
            int chunk = Math.min(Transport.MAX_PAYLOAD_SIZE, len - writtenBit);
            chunk = Math.min(chunk, available);

            byte[] payload = new byte[chunk];
            System.arraycopy(buf, pos + writtenBit, payload, 0, chunk);

            // Send packet ( aka udt_send )
            sendDATA(payload, nextSeqNum);

            long now = manager.manager.now();

            ringBuffer s = buffer[ringTail];
            s.use = true;
            s.seqNum = nextSeqNum;
            s.len = chunk;
            s.data = payload;
            s.sendTime = now;
            s.timeoutTime = now + this.TIMEOUT;

            ringTail = (ringTail + 1) % buffer.length;
            ringCount++;

            if (sendBase == nextSeqNum) {
                oldestTimeout = s.timeoutTime;
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
        if (this.state != State.ESTABLISHED) {
            return -1;
        }

        if (len <= 0 || recvBuffer.isEmpty()) {
            return 0;
        }

        int count = 0;

        // Read from the receiver buffer array!
        while (count < len && !recvBuffer.isEmpty()) {
            buf[pos + count] = recvBuffer.removeFirst();
            count++;
        }

        return count;
    }

    // Important handlers.
    public void handleSYN(Packet p) {
        // Get listener
        Transport t = Transport.unpack(p.getPayload());
        TCPSock listener = manager.listeners.get(t.getDestPort());

        if (listener == null) {
            return;
        }

        // maybe the listener should turn into a regular sock? I didn't really understand how to do this part.

        // Create a new socket to send an ack back
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

        // In this case, it will always be 2
        sock.expectedSeqNum = t.getSeqNum() + 1;

        sock.sendBase = 2;
        sock.nextSeqNum = 2;

        if (!listener.queueSock(sock)) {
            Transport pk = new Transport(
                    sock.sourcePort,
                    sock.destinationPort,
                    Transport.FIN,
                    0,
                    0,
                    new byte[0]
            );

            manager.node.sendSegment(
                    sock.sourceFishnetAddress,
                    sock.destinationFishnetAddress,
                    Protocol.TRANSPORT_PKT,
                    pk.pack()
            );
            System.out.print("F");
            return;
        }

        // This might need to be put later?
        manager.connections.put(sock.id, sock);
        sock.setEstablished();

        sock.sendACK();
    }

    public void handleACK(Packet p) {
        Transport t = Transport.unpack(p.getPayload());
        this.remoteRequestedWindow = t.getWindow();

        long now = manager.manager.now();

        if (this.isConnectionPending()) {
            if (t.getSeqNum() != this.sendBase) {
                System.out.print("?");
                return;
            }

            if (!controlRetransmitted) {
                long sampleRTT = now - controlSendTime;
                updateRTT(sampleRTT);
            }

            this.setEstablished();
            this.waitingForAck = false;
            System.out.print(":");
            return;
        }

        if (t.getSeqNum() > sendBase) {

            this.cwnd += Transport.MAX_PAYLOAD_SIZE;

            sendBase = t.getSeqNum();

            while (ringCount > 0) {
                ringBuffer s = buffer[ringHead];
                if (!s.use) break;

                if (s.seqNum + s.len <= t.getSeqNum()) {
                    s.use = false;
                    s.data = null;
                    ringHead = (ringHead + 1) % buffer.length;
                    ringCount--;
                    long sampleRTT = now - s.sendTime;
                    updateRTT(sampleRTT);
                } else {
                    break;
                }
            }

            oldestTimeout = (ringCount > 0) ? buffer[ringHead].timeoutTime : -1;
            System.out.print(":");
        } else {
            System.out.print("?");
        }

        if (this.isClosurePending() && sendBase == nextSeqNum) {
            this.waitingForAck = false;
            this.controlRetransmitted = false;
            this.release();
//            sendFIN();
        }

    }

    public void handleDATA(Packet p) {
        Transport t = Transport.unpack(p.getPayload());
        if (t == null) return;

        byte[] data = t.getPayload();

        int space = RECEIVER_BUFFER_SIZE - recvBuffer.size();

        if (t.getSeqNum() == this.expectedSeqNum) {
            if (data.length <= space) {
                for (byte b : data) {
                    this.recvBuffer.addLast(b);
                }
                this.expectedSeqNum += data.length;
                System.out.print(".");
            }
            else {
                System.out.print("!");
            }
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
        long now = manager.manager.now();

        // Congestion Control
        this.cwnd /= 2;

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

            s.timeoutTime = now + this.TIMEOUT;

            System.out.print("!");
        }
        oldestTimeout = (ringCount > 0) ? buffer[ringHead].timeoutTime : -1;
    }

    public void retransmit_control() {

        int transportType = (this.state == State.SYN_SENT) ? Transport.SYN : (isClosurePending()) ? Transport.FIN : -1;

        int seqNumber = (this.state == State.SYN_SENT) ? 1 : this.nextSeqNum;

        Transport pkt = new Transport(
                sourcePort,
                destinationPort,
                transportType,
                0,
                seqNumber,
                new byte[0]
        );

        manager.node.sendSegment(
                sourceFishnetAddress,
                destinationFishnetAddress,
                Protocol.TRANSPORT_PKT,
                pkt.pack()
        );

        System.out.print("!");

        controlRetransmitted = true;
        timeoutTime = manager.manager.now() + this.TIMEOUT;
    }

    /*
     * RTT LOGIC
     */

    // to make the timeout not too big or small where it's impossible to run anymore
    private long limitTIMEOUT(long value) {
        if (value < MIN_TIMEOUT) return MIN_TIMEOUT;
        if (value > MAX_TIMEOUT) return MAX_TIMEOUT;
        return value;
    }

    // from slides
    private void updateRTT(long sampleRTT) {
        if (sampleRTT <= 0) {
            return;
        }

        double beta = 0.25;
        double alpha = 0.125;

        if (estRTT < 0) {
            // first sample
            estRTT = sampleRTT;
            devRTT = sampleRTT / 2.0;
        } else {
            // From the slides
            estRTT = (1-alpha) * estRTT + alpha * sampleRTT;
            devRTT = (1-beta) * devRTT + beta * Math.abs(sampleRTT - estRTT);
        }

//        System.out.println("TIMEOUT IS NOW: " + TIMEOUT);

        TIMEOUT = limitTIMEOUT((long) Math.ceil(estRTT + 4.0 * devRTT));
    }

    /*
     * End of socket API
     */
}
