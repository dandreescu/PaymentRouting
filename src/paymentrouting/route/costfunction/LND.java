package paymentrouting.route.costfunction;

import gtna.graph.Edge;
import java.util.Map;
import paymentrouting.datasets.LNParams;
import treeembedding.credit.CreditLinks;

public class LND implements CostFunction {

  Map[] lastFailure;
  int observer;
  double time;

  static double LND_RISK_FACTOR = 0.000000015;
  static double A_PRIORI_PROB = 0.6;

  public double compute(int src, int dst, double amt, CreditLinks edgeweights, LNParams params, boolean direct) {
    double[] ps = params.getParams(src, dst);
    double base = ps[0];
    double rate = ps[1];
    double delay = ps[2];
    double fee = base + amt * rate;
    if (direct) fee = 0;
    return (amt + fee) * delay * LND_RISK_FACTOR + fee + probBias(src, dst);
  }

  private double probBias(int src, int dst) {
    double lastFailure;
    try {
      lastFailure = (Double) this.lastFailure[observer].get(new Edge(src, dst));
    } catch (NullPointerException npe) {
      return 100d / A_PRIORI_PROB;
    }
//    System.out.println("src: "+observer+"\ttime: "+time+"********");
    double deltaHours = (time - lastFailure);//todo
//    System.out.println(deltaHours);
    if (deltaHours < 1)
      return Double.MAX_VALUE;
    return 100d / (A_PRIORI_PROB * (1 - 1 / (Math.pow(2, deltaHours))));
  }


  public void init(Map[] lastFailure) {
    this.lastFailure = lastFailure;
  }

  public void setObserver(int observer, double time) {
    this.observer = observer;
    this.time = time;
  }
}
