package paymentrouting.route;

import java.util.Arrays;
import paymentrouting.datasets.LNParams;
import paymentrouting.route.costfunction.CostFunction;
import paymentrouting.route.costfunction.LND;
import treeembedding.credit.CreditLinks;

public class Path implements Comparable<Path> {
  int[] p;
  double cost;

  public Path(int[] p, double cost) {
    this.p = p;
    this.cost = cost;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Path path = (Path) o;
    return Double.compare(path.cost, cost) == 0 &&
        Arrays.equals(p, path.p);
  }

  public int len() {
    return p.length;
  }

  public void reverse() {
    int aux;
    for (int i = 0; i < p.length / 2; i++) {
      aux = p[i];
      p[i] = p[p.length - i - 1];
      p[p.length - i - 1] = aux;
    }
  }

  public Path slice(int end, double amt, CreditLinks edgeweights, LNParams params, CostFunction costFunction) {
    int[] slice = Arrays.copyOfRange(this.p, 0, Math.min(end, p.length));
    double newCost = 0;
    if (slice.length > 1)
      newCost += costFunction.compute(slice[0], slice[1], amt, edgeweights, params, true);
    for (int i = 1; i < slice.length - 1; i++) {
      newCost += costFunction.compute(slice[i], slice[i+1], amt, edgeweights, params, false);
    }
    return new Path(slice, newCost);
  }

  public static Path concat(Path rootPath, Path spurPath) {
    int[] totalPath = new int[rootPath.p.length + spurPath.p.length];
    System.arraycopy(rootPath.p, 0, totalPath, 0, rootPath.p.length);
    System.arraycopy(spurPath.p, 0, totalPath, rootPath.p.length, spurPath.p.length);
    return new Path(totalPath, rootPath.cost + spurPath.cost);
  }

  @Override
  public int compareTo(Path o) {
    return Double.compare(this.cost, o.cost);
  }
}