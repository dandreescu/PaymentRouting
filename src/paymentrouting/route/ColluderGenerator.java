package paymentrouting.route;

import gtna.graph.Graph;

public interface ColluderGenerator {
  int[] generateColluders(Graph g);
}
