package paymentrouting.route.costfunction;

import paymentrouting.datasets.LNParams;
import treeembedding.credit.CreditLinks;

public class LND implements CostFunction {
  static double LND_RISK_FACTOR = 0.000000015;
  static double A_PRIORI_PROB = 0.6;

  public double compute(int src, int dst, double amt, CreditLinks edgeweights, LNParams params, boolean direct) {
    double[] ps = params.getParams(src, dst);
    double base = ps[0];
    double rate = ps[1];
    double delay = ps[2];
    double lastFailure = ps[4];
    double fee = base + amt * rate;
    if (direct) fee = 0;
    return (amt + fee) * delay * LND_RISK_FACTOR + fee + probBias(lastFailure);
  }

  private static double probBias(double lastFailure) {//todo use last failure as delta?
    double deltaHours = lastFailure;//(System.currentTimeMillis() / 1000d - lastFailure) / 3600;
    if (deltaHours < 1)
      return Double.MAX_VALUE;
    return 100d / (A_PRIORI_PROB * (1 - 1 / (Math.pow(2, deltaHours))));
  }
}
