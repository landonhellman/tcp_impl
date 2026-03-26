import java.util.HashMap;

/**
 * <p>Title: CPSC 433/533 Programming Assignment</p>
 *
 * <p>Description: Fishnet TCP manager</p>
 *
 * <p>Copyright: Copyright (c) 2006</p>
 *
 * <p>Company: Yale University</p>
 *
 * @author Hao Wang
 * @version 1.0
 */
public class TCPManager {
    public Node node;
    private int addr;
    public Manager manager;

    private static final byte dummy[] = new byte[0];

    HashMap<Integer, TCPSock> boundPorts = new HashMap<>();
    HashMap<Integer, TCPSock> listeners = new HashMap<>();
    HashMap<TCPSockID, TCPSock> connections = new HashMap<>();

    private final long AckTimeout = 500;

    public TCPManager(Node node, int addr, Manager manager) {
        this.node = node;
        this.addr = addr;
        this.manager = manager;
    }

    /**
     * Start this TCP manager
     */
    public void start() {
        start_timer();
    }

    public void start_timer() {
        this.node.addTimer(AckTimeout, "tcpTimedOut");
    }

    public void check_retransmission() {
        long now = manager.now();

        for (TCPSock sock : this.connections.values()) {
            if (sock.waitingForAck && now >= sock.timeoutTime) {

                Transport pkt = new Transport(
                        sock.sourcePort,
                        sock.destinationPort,
                        sock.transType,
                        0,
                        sock.transSeqNum,
                        sock.transData
                );

                node.sendSegment(
                        sock.sourceFishnetAddress,
                        sock.destinationFishnetAddress,
                        Protocol.TRANSPORT_PKT,
                        pkt.pack()
                );

                System.out.print("!");

                sock.timeoutTime = now + AckTimeout;
            }
        }
    }

    /*
     * Begin socket API
     */

    /**
     * Create a socket
     *
     * @return TCPSock the newly created socket, which is not yet bound to
     *                 a local port
     */
    public TCPSock socket() {
        return new TCPSock(this);
    }

    public boolean bindPort(int sourcePort, TCPSock sock) {
        if (boundPorts.containsKey(sourcePort)) {
            return false;
        }
        boundPorts.put(sourcePort, sock);
        return true;
    }

    public void createListener(int sourcePort, TCPSock sock) {
        listeners.put(sourcePort, sock);
    }

    public void receiveTransport(Packet p) {
        Transport t = Transport.unpack(p.getPayload());

        if (t == null) {
            return;
        }

        TCPSockID id = new TCPSockID(
                p.getDest(),
                p.getSrc(),
                t.getDestPort(),
                t.getSrcPort()
        );

        TCPSock sock = connections.get(id);

        switch(t.getType()) {
            case Transport.SYN:
                TCPSock listener = listeners.get(t.getDestPort());
                if (listener != null) {
                    listener.handleSYN(p);
                }
                break;
            case Transport.ACK:
                if (sock == null) {
                    System.out.print("?");
                    return;
                }
                sock.handleACK(p);
                break;
            case Transport.DATA:
                if (sock == null) {
                    System.out.print("!");
                    return;
                }
                sock.handleDATA(p);
                break;
            case Transport.FIN:
                if (sock == null) {
                    return;
                }
                sock.handleFIN(p);
                break;
        }
    }

    public int getAddr() {
        return this.addr;
    }

    /*
     * End Socket API
     */
}
