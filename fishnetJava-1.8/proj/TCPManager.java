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
    private Node node;
    private int addr;
    private Manager manager;

    private static final byte dummy[] = new byte[0];

    HashMap<Integer, TCPSock> boundPorts = new HashMap<>();
    HashMap<Integer, TCPSock> listeners = new HashMap<>();
    HashMap<Integer, TCPSock> connections = new HashMap<>();

    public TCPManager(Node node, int addr, Manager manager) {
        this.node = node;
        this.addr = addr;
        this.manager = manager;
    }

    /**
     * Start this TCP manager
     */
    public void start() {

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

    public void sendSYN(TCPSock sock) {

    }

    public int getAddr() {
        return this.addr;
    }

    /*
     * End Socket API
     */
}
