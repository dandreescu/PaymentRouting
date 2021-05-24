package paymentrouting.route.costfunction;

import paymentrouting.datasets.LNParams;
import treeembedding.credit.CreditLinks;

public class Eclair implements CostFunction {

  static double CBR = 684609;

  static double MIN_DELAY = 9;
  static double MAX_DELAY = 2016;
  static double MIN_CAP = 0;
  static double MAX_CAP = 100000000;
  static double MIN_AGE = CBR - 8640;
  static double MAX_AGE = CBR;
  static double DELAY_RATIO = 0.15;
  static double CAPACITY_RATIO = 0.5;
  static double AGE_RATIO = 0.35;

  public double compute(int src, int dst, double amt, CreditLinks edgeweights, LNParams params, boolean direct) {
    if (direct) return 0d;
    double[] ps = params.getParams(src, dst);
    try {
      double base = ps[0];
      double rate = ps[1];
      double delay = ps[2];
      double age = ps[3];

    double cap = edgeweights.getTotalCapacity(src, dst);

    double fee = base + amt * rate;

    double ndelay = normalize(delay, MIN_DELAY, MAX_DELAY);
    double ncapacity = normalize(cap, MIN_CAP, MAX_CAP);
    double nage = normalize(age, MIN_AGE, MAX_AGE);

    return fee * (ndelay * DELAY_RATIO + ncapacity * CAPACITY_RATIO + nage * AGE_RATIO);
    } catch (NullPointerException e) {
      System.out.println();
      return 0;
    }
  }

  private double normalize(double val, double min, double max) {
    if (val <= min)
      return 0d;
    if (val > max)
      return 1d;
    return (val - min) / (max - min);
  }
}
