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
  Map<Integer, Map<Integer, int[][]>> paths;

  public Paths(){}

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
    Filereader fr = new Filereader(filename);

    String key = this.readHeader(fr);
    this.paths = new HashMap<Integer, Map<Integer, int[][]>>();
    String line = null;
    List<int[]> ps = new ArrayList<>();
    int prev_src = -1, prev_dst = -1, src = -1, dst = -1;
    while ((line = fr.readLine()) != null) {
      if (prev_src != src && prev_dst != dst){
        Map<Integer, int[][]> this_map = paths.getOrDefault(src, new HashMap<>());
        int[][] ps_int = new int[ps.size()][];
        for (int i = 0; i < ps.size(); i++) {
          ps_int[i] = ps.get(i);
        }
        this_map.put(dst, ps_int);
        paths.put(src, this_map);
        ps.clear();
      }
      String[] parts = line.split(" ");
      if (parts.length < 2) continue;
      src = Integer.parseInt(parts[0]);
      dst = Integer.parseInt(parts[1]);
      int[] p = new int[parts.length-2];
      for (int i = 2; i < parts.length; i++) {
        p[i-2] = Integer.parseInt(parts[i]);
      }
      ps.add(p);
    }

    fr.close();

    return key;
  }
}
