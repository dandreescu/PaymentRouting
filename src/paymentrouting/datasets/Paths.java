package paymentrouting.datasets;

import gtna.graph.Edge;
import gtna.graph.GraphProperty;
import gtna.io.Filereader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import treeembedding.credit.Transaction;

public class Paths extends GraphProperty {
  Map<Integer, Map<Integer, List<int[]>>> paths;

  public Paths(){}

  public Paths(Map<Integer, Map<Integer, List<int[]>>> paths) {
    this.paths = paths;
  }

  public int[] get(int src, int dst, Random rand) {
    List<int[]> ps = paths.get(src).get(dst);
    return ps.get(rand.nextInt(ps.size()));
  }

  @Override
  public boolean write(String filename, String key) {
    return false;
  }

  @Override
  public String read(String filename) {
    Filereader fr = new Filereader(filename);
    String key = this.readHeader(fr);

    this.paths = new HashMap<>();
    String line;

    while ((line = fr.readLine()) != null) {

      String[] parts = line.split(" ");
      if (parts.length < 2) continue;

      int src = Integer.parseInt(parts[0]);
      int dst = Integer.parseInt(parts[1]);
      int[] p = new int[parts.length-2];

      for (int i = 2; i < parts.length; i++) {
        p[i-2] = Integer.parseInt(parts[i]);
      }

      Map<Integer, List<int[]>> this_map = paths.getOrDefault(src, new HashMap<>());
      List<int[]> this_list = this_map.getOrDefault(dst, new ArrayList<>());
      this_list.add(p);
      this_map.put(dst, this_list);
      paths.put(src, this_map);
    }

    fr.close();

    return key;
  }
}
