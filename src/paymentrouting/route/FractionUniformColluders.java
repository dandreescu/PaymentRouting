package paymentrouting.route;

import gtna.graph.Graph;
import gtna.graph.Node;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import treeembedding.credit.CreditLinks;

public class FractionUniformColluders implements ColluderGenerator {
  private double fraction;
  private NodeMetric preferenceMetric;
  private Random rand;
  private NodeMetric fractionMetric;

  public FractionUniformColluders(double fraction, NodeMetric fractionMetric, NodeMetric preferenceMetric) {
    this.fraction = fraction;
    this.fractionMetric = fractionMetric;
    this.preferenceMetric = preferenceMetric;
    this.rand = new Random();
  }

  private double[] nodeCDF(Graph g, NodeMetric metric) {
    double[] result = null;
    switch (metric) {
      case COUNT:
        result = IntStream.range(0, g.getNodeCount()).mapToDouble(i -> 1).toArray();
        break;
      case DEGREE:
        result = IntStream.range(0, g.getNodeCount())
            .mapToDouble(i -> g.getNode(i).getDegree()).toArray();
        break;
      case BALANCE:
        CreditLinks edgeweights = (CreditLinks) g.getProperty("CREDIT_LINKS");
        result = IntStream.range(0, g.getNodeCount())
            .mapToDouble(i -> Arrays.stream(g.getNode(i).getIncomingEdges())
                .mapToDouble(j -> edgeweights.getPot(i, j)).sum()).toArray();
        break;
    }
    double sum = Arrays.stream(result).sum();
    for (int i = 0; i < result.length; i++) {
      result[i] /= sum;
    }
    for (int i = 1; i < result.length; i++) {
      result[i] += result[i - 1];
    }
    return result;
  }

  private int sample(double[] CDF) {
    int i = Arrays.binarySearch(CDF, rand.nextDouble());
    if (i < 0)
      return (- i - 1); // check binary search javadoc: result should be insertion point
    return i;
  }

  @Override
  public int[] generateColluders(Graph g) {
    Set<Integer> colluders = new HashSet<>();
    double[] fractionCDF = nodeCDF(g, fractionMetric);
    double[] preferenceCDF = nodeCDF(g, preferenceMetric);

    double currFraction = 0;
    while (currFraction < fraction) {
      int node = sample(preferenceCDF);
      if (colluders.contains(node))
        continue;
      currFraction += fractionCDF[node] - (node > 0 ? fractionCDF[node - 1] : 0); // fraction of node is kernel not cdf
      colluders.add(node);
    }
    System.out.println("### FRACTION: " + currFraction);
    return colluders.stream().mapToInt(Integer::intValue).toArray();
  }

  @Override
  public String toString() {
    return
        "fraction=" + fraction +
        "_prefM=" + preferenceMetric +
        "_fracM=" + fractionMetric;
  }

  enum NodeMetric {
    COUNT, DEGREE, BALANCE
  }
}
