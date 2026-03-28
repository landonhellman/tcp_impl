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

    // add timer for closing too!!!!!
    // add timer for opening!!!

    public void check_retransmission() {
        long now = manager.now();

        for (TCPSock sock : this.connections.values()) {
//            if (sock.waitingForAck && now >= sock.timeoutTime) {
            if (sock.sendBase != sock.nextSeqNum && now >= sock.oldestTimeout) {
                sock.retransmit_window();
            }

            if (sock.waitingForAck && now >= sock.timeoutTime) {
                sock.retransmit_control();
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
                else {
                    Transport t1 = new Transport(
                            t.getDestPort(),
                            t.getSrcPort(),
                            Transport.FIN,
                            0,
                            0,
                            new byte[0]
                    );

                    node.sendSegment(
                            p.getDest(),
                            p.getSrc(),
                            Protocol.TRANSPORT_PKT,
                            t1.pack()
                    );
                    System.out.print("F");
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

            // give an error
        }
    }

    public int getAddr() {
        return this.addr;
    }

    /*
     * End Socket API
     */
}
