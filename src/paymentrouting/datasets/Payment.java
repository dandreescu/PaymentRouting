package paymentrouting.datasets;

import gtna.graph.Edge;
import paymentrouting.route.Path;
import paymentrouting.route.RouteExamplePayment;
import treeembedding.credit.CreditLinks;

public class Payment {
  Path[] paths;
  int[] indices;
  RouteExamplePayment rPay;

  public Payment(Path[] paths, RouteExamplePayment rPay) {
    this.paths = paths;
    this.rPay = rPay;
    indices = new int[paths.length];
  }
//
//  public void step() {
//    for (int i = 0; i < indices.length; i++) {
//      indices[i]++;
//    }
//    boolean ok = rPay.edgeweights.setWeight(path[i], path[i + 1], val);
//    if (!ok) {
//      rPay.weightUpdate(edgeweights, original);
//      rPay.success--;
//      break;
//    }
//
//    Edge e = rPay.edgeweights.makeEdge(path[i], path[i + 1]);
////        System.out.println(i+": " + e.toString());
//    double w = rPay.edgeweights.getWeight(e);
//    if (!original.containsKey(e))
//      original.put(e, w);
//    if (!originalAll.containsKey(e))
//      originalAll.put(e, w);
//  }
}
