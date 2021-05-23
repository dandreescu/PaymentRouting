package paymentrouting.route.costfunction;

import paymentrouting.datasets.LNParams;
import treeembedding.credit.CreditLinks;

public interface CostFunction {
  double compute(int src, int dst, double amt, CreditLinks edgeweights, LNParams params,
                 boolean direct);
}
