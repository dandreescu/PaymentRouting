package paymentrouting.datasets;

import gtna.graph.GraphProperty;
import java.util.Map;
import java.util.Random;

public class Paths extends GraphProperty {
  Map<Integer, Map<Integer, int[][]>> paths;

  public Paths(Map<Integer, Map<Integer, int[][]>> paths) {
    this.paths = paths;
  }

  public int[] get(int src, int dst, Random rand) {
    int[][] ps = paths.get(src).get(dst);
    return ps[rand.nextInt(ps.length)];
  }

  @Override
  public boolean write(String filename, String key) {
    return false;
  }

  @Override
  public String read(String filename) {
    return null;
  }
}
