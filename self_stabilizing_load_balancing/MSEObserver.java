package self_stabilizing_load_balancing;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Control;
import peersim.core.Network;
import peersim.core.Node;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class MSEObserver implements Control {

    private final int pid;
    private final String prefix;
    private final boolean debug;
    private BufferedWriter writer;
    private final String outputFilename;

    public MSEObserver(String prefix) {
        this.prefix = prefix;
        this.pid = Configuration.getPid(prefix + ".protocol");
        this.debug = Configuration.getBoolean(prefix + ".debug", false);
        this.outputFilename = Configuration.getString(prefix + ".output_filename", "mse_data.csv");

        try {
            writer = new BufferedWriter(new FileWriter(outputFilename, false));
            // 修改CSV表頭
            if (debug) {
                writer.write("Cycle,MSE,Avg,Min,Max,Discrepancy\n");
            } else {
                writer.write("Cycle,MSE,Discrepancy\n");
            }
        } catch (IOException e) {
            System.err.println("Error initializing CSV writer: " + e.getMessage());
        }
    }

    public boolean execute() {
        double totalLoad = 0;
        int nodeCount = Network.size();
        double maxLoad = Double.MIN_VALUE;
        double minLoad = Double.MAX_VALUE;

        // Max and min
        for (int i = 0; i < nodeCount; i++) {
            Node node = Network.get(i);
            SelfED protocol = (SelfED) node.getProtocol(pid);
            double value = protocol.getValue();

            totalLoad += value;
            if (value > maxLoad) maxLoad = value;
            if (value < minLoad) minLoad = value;
        }

        double globalMaxNeighborDiff = 0;

        for (int i = 0; i < nodeCount; i++) {
            Node node = Network.get(i);
            SelfED protocol = (SelfED) node.getProtocol(pid);
            double nodeLoad = protocol.getValue();

            double maxNeighborDiff = 0;
            List<Node> neighbors = protocol.getNeighbors(node, pid); // get neighbors

            for (Node neighbor : neighbors) {
                SelfED neighborProtocol = (SelfED) neighbor.getProtocol(pid);
                double neighborLoad = neighborProtocol.getValue();
                double diff = Math.abs(nodeLoad - neighborLoad);

                if (diff > maxNeighborDiff) {
                    maxNeighborDiff = diff;
                }
            }

            // Record the maximum neighbor load difference in the entire network
            if (maxNeighborDiff > globalMaxNeighborDiff) {
                globalMaxNeighborDiff = maxNeighborDiff;
            }
        }

        double discrepancy = globalMaxNeighborDiff; // Set discrepancy


        double avgLoad = totalLoad / nodeCount;
    //    double discrepancy = maxLoad - minLoad;

        double mse = 0;
        for (int i = 0; i < nodeCount; i++) {
            Node node = Network.get(i);
            SelfED protocol = (SelfED) node.getProtocol(pid);
            double diff = protocol.getValue() - avgLoad;
            mse += diff * diff;
        }
        mse /= nodeCount;

        if (debug) {
            System.out.printf("[MSE][Cycle %d] MSE: %.4f | Avg: %.2f | Discrepancy: %.2f%n",
                    CommonState.getTime(), mse, avgLoad, discrepancy);
        } else {
            System.out.printf("%d\t%.6f\t%.2f%n",
                    CommonState.getTime(), mse, discrepancy);
        }

        try {
            if (debug) {
                writer.write(String.format("%d,%.6f,%.2f,%.2f,%.2f,%.2f%n",
                        CommonState.getTime(), mse, avgLoad,
                        minLoad, maxLoad, discrepancy));
            } else {
                writer.write(String.format("%d,%.6f,%.2f%n",
                        CommonState.getTime(), mse, discrepancy));
            }
            writer.flush();
        } catch (IOException e) {
            System.err.println("Error writing to CSV: " + e.getMessage());
        }

        return false;
    }

    public void close() {
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing CSV writer: " + e.getMessage());
        }
    }
}