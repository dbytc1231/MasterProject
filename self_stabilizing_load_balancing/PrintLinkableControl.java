package self_stabilizing_load_balancing;

import peersim.config.Configuration;
import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;
import peersim.core.Linkable;

public class PrintLinkableControl implements Control {

    private final String prefix;
    private final int linkablePid;

    public PrintLinkableControl(String prefix) {
        this.prefix = prefix;
        linkablePid = Configuration.getPid(prefix + ".linkable");
    }

    public boolean execute() {
        // Output all node IDs
        for (int i = 0; i < Network.size(); i++) {
            System.out.println(i);
        }
        // Traverse each node and output neighbor edges
        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            Linkable linkable = (Linkable) node.getProtocol(linkablePid);
            for (int j = 0; j < linkable.degree(); j++) {
                Node neighbor = linkable.getNeighbor(j);
                long neighborId = neighbor.getID();
                System.out.println(i + " " + neighborId);
            }
        }
        return false;
    }
}