package peersim.dynamics;

import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.graph.Graph;

import java.util.Random;

public class WireER extends WireGraph {

    private double p;

    public WireER(String prefix) {
        super(prefix);
        p = Configuration.getDouble(prefix + ".p");
    }

    public void wire(Graph g) {
        int n = g.size();
        Random r = CommonState.r;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (r.nextDouble() < p) {
                    g.setEdge(i, j);
                    g.setEdge(j, i); // undirected edge
                }
            }
        }
    }
}
