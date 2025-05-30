package self_stabilizing_load_balancing;

import peersim.cdsim.CDProtocol;
import peersim.config.FastConfig;
import peersim.core.CommonState;
import peersim.core.Linkable;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import peersim.transport.Transport;
import peersim.vector.SingleValueHolder;

import java.util.*;

/*
 * Copyright (c) 2003 The BISON Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 */

/**
 * Event driven version of epidemic averaging.
 */
public class SelfED extends SingleValueHolder
        implements CDProtocol, EDProtocol {

    //--------------------------------------------------------------------------
// Initialization
//--------------------------------------------------------------------------
    // parameters
    private int lastReceivedLoad = 0;
    private int lastGaveLoad = 0;
    private double tLoad; // temporal load
    private Map<Node, Boolean> pendingAcks = new HashMap<>(); // pending Ack
    private Map<Node, Integer> expectedTransfers = new HashMap<>(); // expectedTransfers for each node

    /**
     * @param prefix string prefix for config properties
     */
    public SelfED(String prefix) {
        super(prefix);
    } //initialize the neighbor


//--------------------------------------------------------------------------
// methods
//--------------------------------------------------------------------------

    /**
     * This is the standard method the define periodic activity.
     * The frequency of execution of this method is defined by a
     * {@link peersim.edsim.CDScheduler} component in the configuration.
     */
    public void nextCycle(Node node, int pid) {
        // l5-l8
        this.value = this.value +lastReceivedLoad-lastGaveLoad;
        lastReceivedLoad = 0;
        lastGaveLoad = 0;
        tLoad = this.value; // initialize t-load to value

        Linkable linkable = (Linkable) node.getProtocol(FastConfig.getLinkable(pid));
        if (linkable.degree() == 0) return;

        // print all nodes' load
//        System.out.printf("[Cycle %d] Node %d current load: %.2f%n", CommonState.getTime(), node.getID(), this.value);
//        Linkable allNodesLinkable = (Linkable) node.getProtocol(FastConfig.getLinkable(pid));
//        for (int i = 0; i < allNodesLinkable.degree(); i++) {
//            Node q = allNodesLinkable.getNeighbor(i);
//            SelfED qProtocol = (SelfED) q.getProtocol(pid);
//            System.out.printf("[Cycle %d] Node %d current load: %.2f%n", CommonState.getTime(), q.getID(), qProtocol.value);
//        }
        // l9-l11, compute V_less：neighbor who load is lower than the node's load
        List<Node> vLess = new ArrayList<>();
        for (int i = 0; i < linkable.degree(); i++) {
            Node q = linkable.getNeighbor(i);
            SelfED qProtocol = (SelfED) q.getProtocol(pid);
            if (qProtocol.value < this.value) {
                vLess.add(q);
            }
        }

        if (vLess.isEmpty()) return;

        // l13: find MinLoad with the smallest load neighbor
        Node minNode = Collections.min(vLess, Comparator.comparingDouble(
                q -> ((SelfED) q.getProtocol(pid)).value));
        double minLoad = ((SelfED) minNode.getProtocol(pid)).value;
//        System.out.printf("[Cycle %d] Node %d has the smallest load: %.2f%n", CommonState.getTime(), minNode.getID(), minLoad);
        // l14-15: compute loadToTransfer, tentativeLoad
        int loadToTransfer = Math.max(0, (int) Math.ceil((tLoad - minLoad) / 2)); //not be <0
        System.out.print(loadToTransfer);
        double tentativeLoad = tLoad - loadToTransfer;

        // l16-l18：select PV_less（load(q) < TentativeLoad）
        List<Node> pvLess = new ArrayList<>();
        for (Node q : vLess) {
            double qLoad = ((SelfED) q.getProtocol(pid)).value;
            if (qLoad < tentativeLoad) {
                pvLess.add(q);
            }
        }
        // l19: Define proposals to RRProposal with pvLess
        Map<Node, Integer> proposals = RRProposal(loadToTransfer, pvLess, tentativeLoad, pid);

        System.out.printf("[Cycle %d] Node %d current load: %.2f, min neighbor (Node %d) load: %.2f%n",
                CommonState.getTime(), node.getID(), this.value, minNode.getID(), minLoad);

        // l20-l23: send proposal to all nodes in V_less
        for (Map.Entry<Node, Integer> entry : proposals.entrySet()) {
            Node q = entry.getKey();
            //  int transfer = Math.max(0, entry.getValue()); // avoid <0
            int transfer = entry.getValue();
            expectedTransfers.put(q, transfer); // record expected transfers
            Transport tr = (Transport) node.getProtocol(FastConfig.getTransport(pid));
            tr.send(node, q, new ProposalMsg(transfer, tentativeLoad, node), pid);
            pendingAcks.put(q, false); // mark waiting Ack as false

            System.out.printf("[Cycle %d] Node %d sends proposal to Node %d: transfer %d, tentative load %.2f%n",
                    CommonState.getTime(), node.getID(), q.getID(), transfer, tentativeLoad);
        }
        // l23: setting ack as true in ProcessEvent()


    }


//--------------------------------------------------------------------------

    /**
     * This is the standard method to define to process incoming messages.
     */
    public void processEvent(Node node, int pid, Object event) {
        double oldLoad = this.value;
        // l24: proposal processing
        if (event instanceof ProposalMsg proposal) {
            SelfED senderProtocol = (SelfED) proposal.sender.getProtocol(pid);
            // l25-l29:receive and respond to proposals
            if (proposal.tentativeLoad > this.value) {
                int deal = Math.min(
                        (int) (proposal.tentativeLoad - this.value), //  the actual transferable load
                        proposal.loadToTransfer
                );
                // l27: sending Ack
                Transport tr = (Transport) node.getProtocol(FastConfig.getTransport(pid));
                tr.send(node, proposal.sender, new AckMsg(deal, node), pid);
                // l28-l29: updating lastReceivedLoad and tload(p)
                lastReceivedLoad += deal; // received loads
                //        this.value += deal;
                this.tLoad += deal; // update tentativeLoad**

                System.out.printf("[Cycle %d] Node %d accepted proposal from Node %d: load %.2f -> %.2f (received %d)%n",
                        CommonState.getTime(), node.getID(), proposal.sender.getID(),
                        oldLoad, this.value, deal);

            } else {
                // l30-31: refuse the proposal
                System.out.printf("[Cycle %d] Node %d rejected proposal from Node %d (tentative load %.2f <= current load %.2f)%n",
                        CommonState.getTime(), node.getID(), proposal.sender.getID(),
                        proposal.tentativeLoad, this.tLoad);

                Transport tr = (Transport) node.getProtocol(FastConfig.getTransport(pid));
                tr.send(node, proposal.sender, new AckMsg(0, node), pid);
            }
        }
        // handle Ack, l32-34
        if (event instanceof AckMsg) {
            AckMsg ack = (AckMsg) event;
//            int expected = expectedTransfers.getOrDefault(ack.sender, 0);
//            int actualDeal = Math.min(ack.deal, expected); // avoid over-accumulation
//            this.lastGaveLoad += actualDeal;
            this.lastGaveLoad +=ack.deal;
            pendingAcks.put(ack.sender, true); // mark Acks as true
            this.tLoad -= ack.deal;

            System.out.printf("[Cycle %d] Node %d received Ack from Node %d: gave %d, current tentative load: %.2f%n",
                    CommonState.getTime(), node.getID(), ack.sender.getID(),
                    ack.deal, this.tLoad);

            // Adding part: if all Acks arrive, update the final value and load.
            if (!pendingAcks.containsValue(false)) {
                System.out.printf("[Cycle %d] lastGaveLoad=%d%n", CommonState.getTime(),lastGaveLoad);
                //         this.value = this.value - lastGaveLoad; // update the final load
                //         this.tLoad = this.value; // and update tload

                pendingAcks.clear();
                expectedTransfers.clear();
                //     lastGaveLoad = 0; // empty for the use in next round
                System.out.printf("[Cycle %d] Node %d finalized load update: %.2f -> %.2f%n",
                        CommonState.getTime(), node.getID(), oldLoad, this.tLoad);

            }
        }
    }

    private Map<Node, Integer> RRProposal(int loadToTransfer, List<Node> pvLess, double tentativeLoad, int pid) {
        Map<Node, Integer> proposals = new HashMap<>();
        // l36-l37: setting tvLess and leftLoad_to_transfer
        List<Node> tvLess = new ArrayList<>(pvLess);
        int leftLoad = loadToTransfer;

//        if (loadToTransfer < 0) {
//            return proposals; // empty proposal for nothing to transfer
//        }
        // l38: while loop when  |TVless| > 0 ∧ LeftLoadToTransfer > 0
        while (!tvLess.isEmpty() && leftLoad > 0) {
            // l39: set m as max of tvless
            Node maxNode = Collections.max(tvLess, Comparator.comparingDouble(
                    q -> ((SelfED) q.getProtocol(pid)).tLoad));
            double m = ((SelfED) maxNode.getProtocol(pid)).tLoad;

            // set transferPerNode as tentativeLoad - m, the biggest load that every node can afford
            int transferPerNode = (int) (tentativeLoad - m);
            if (transferPerNode <= 0) break;

            // l40: check can the node transfer to all nodes in tvless
            if ( transferPerNode * tvLess.size() <= leftLoad) {
                // l41.a: proposal all load to tvless and update leftLoad
                for (Node q : tvLess) {
                    proposals.put(q, transferPerNode);
                    leftLoad -= transferPerNode;

                    // update the load after transferring
//                    double newLoad = ((SelfED) q.getProtocol(pid)).tLoad + transferPerNode;
//                    ((SelfED) q.getProtocol(pid)).tLoad = newLoad;
                }
                // l41.b: remove the nodes that equals TentativeLoad from tvless
                List<Node> toRemove = new ArrayList<>();
                for (Node q : tvLess) {
                    if (((SelfED) q.getProtocol(pid)).tLoad == tentativeLoad) {
                        toRemove.add(q);
                    }
                }
                tvLess.removeAll(toRemove);
            } else {
                // l42-43: using Round-Robin for residual load
                int currentIndex = 0;  // using index to control the cycle
                while (leftLoad > 0 && !tvLess.isEmpty()) {
                    Node q = tvLess.get(currentIndex);
                    double qLoad = ((SelfED) q.getProtocol(pid)).tLoad;
                    int maxTransfer = (int) (tentativeLoad - qLoad);
                    if (maxTransfer <= 0) {
                        tvLess.remove(currentIndex);
                        continue;
                    }
                    // allocate 1 in each round and update the receiver node
                    int transfer = Math.min(1, Math.min(maxTransfer, leftLoad));
                    //    int transfer = Math.min(maxTransfer, leftLoad);
                    proposals.merge(q, transfer, Integer::sum);
                    leftLoad -= transfer;
                    ((SelfED) q.getProtocol(pid)).tLoad += transfer;
                    if (((SelfED) q.getProtocol(pid)).tLoad >= tentativeLoad) {
                        tvLess.remove(currentIndex);
                    } else {
                        currentIndex++;
                    }
                    // reset index if next round is needed
                    if (currentIndex >= tvLess.size()) {
                        currentIndex = 0;
                    }
                }
            }
        }
        assert !pendingAcks.containsValue(false) : "Some Acks are missing!";
        return proposals;
    }
    public List<Node> getNeighbors(Node node, int pid) {
        Linkable linkable = (Linkable) node.getProtocol(FastConfig.getLinkable(pid));
        List<Node> neighbors = new ArrayList<>();

        for (int i = 0; i < linkable.degree(); i++) {
            neighbors.add(linkable.getNeighbor(i));
        }
        return neighbors;
    }

}


//--------------------------------------------------------------------------
//--------------------------------------------------------------------------

/**
 * The type of a message. It contains a value of type double and the
 * sender node of type {@link peersim.core.Node}.
 */
class ProposalMsg {
    final int loadToTransfer; // load that the proposal transfers
    final double tentativeLoad; // The tentative load of the sender
    final Node sender;

    public ProposalMsg(int loadToTransfer, double tentativeLoad, Node sender) {
        this.loadToTransfer = loadToTransfer;
        this.tentativeLoad = tentativeLoad;
        this.sender = sender;
    }
}

class AckMsg {
    final int deal; // Actual agreed load
    final Node sender;

    public AckMsg(int deal, Node sender) {
        this.deal = deal;
        this.sender = sender;
    }
}



