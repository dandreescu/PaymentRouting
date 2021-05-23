package paymentrouting.route.costfunction;

import java.util.Random;
import paymentrouting.datasets.LNParams;
import treeembedding.credit.CreditLinks;

public class CLightning implements CostFunction {

  static double C_RISK_FACTOR = 10;
  static double RISK_BIAS = 1;
  static double DEFAULT_FUZZ = 0.05;

  static Random rand  = new Random();

  public double compute(int src, int dst, double amt, CreditLinks edgeweights, LNParams params, boolean direct) {
    double[] ps = params.getParams(src, dst);
    double base = ps[0];
    double rate = ps[1];
    double delay = ps[2];

    double fee = base + amt * rate;

    fee *= 1 + DEFAULT_FUZZ * rand.nextDouble();

    if (direct)
      fee = 0;

    return (amt + fee) * delay * C_RISK_FACTOR + RISK_BIAS;
  }

}
