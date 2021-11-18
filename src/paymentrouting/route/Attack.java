package paymentrouting.route;

import gtna.graph.Graph;
import gtna.graph.Node;
import gtna.graph.spanningTree.ParentChild;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class Attack {
  private List<Stats> statsNonObf;
  private List<Stats> statsObf;
  private ColluderGenerator cg;
//  private double fraction;
//  private boolean degree;


  private Graph g;
  private SpeedyMurmursMulti sm;
  private int[] colluders;

  private HopDistance hop;

  private int[][] lvl;
  private int[][] roots;
  private int[][][][] possParents;
  private int[][][][] possChildren;

//  private int[] size;

  private int[][] actualParents;

  public Attack(ColluderGenerator cg) {
    this.cg = cg;
  }

//  public Attack(double fraction, boolean degree) {
//    this.fraction = fraction;
//    this.degree = degree;
//  }
//
//  public Attack(double fraction, int[] colluders) {
//    this.fraction = fraction;
//    this.colluders = colluders;
//  }

//  private int[] generateColluders() {
//    Random rand = new Random();
//    Set<Integer> colluders = new HashSet<>();
//    Node[] nodes = g.getNodes().clone();
//    if (degree) {
//      Arrays.sort(nodes, Comparator.comparing(Node::getInDegree).reversed());
//    }
//    while (colluders.size() < nodes.length * fraction) {
//      int next = rand.nextInt(degree ? (int) (nodes.length * fraction + 1) : nodes.length);
//      colluders.add(nodes[next].getIndex());
//    }
//    return colluders.stream().mapToInt(Integer::intValue).toArray();
//  }

  public void init(Graph g, SpeedyMurmursMulti sm) {
    this.g = g;
    this.sm = sm;
    this.statsObf = new ArrayList<>();
    this.statsNonObf = new ArrayList<>();
    if (colluders == null)
      this.colluders = cg.generateColluders(g);

    this.hop = new HopDistance();
    this.hop.initRouteInfo(g, null);

    System.out.println(Arrays.toString(colluders));

    this.actualParents = sm.parents();
    this.lvl = sm.levels();

    this.roots = possibleRoots();
    System.out.println("#### poss roots: " + Arrays.stream(roots).map(r -> r.length).max(Integer::compareTo).get());
    this.possParents = new int[sm.realities][][][];
    this.possChildren = new int[sm.realities][][][];
    for (int k = 0; k < sm.realities; k++) {
      this.possParents[k] = new int[roots[k].length][][];
      this.possChildren[k] = new int[roots[k].length][][];
      for (int i = 0; i < roots[k].length; i++) {
        initParents(i, k);
        initChildren(i, k);
      }
    }

    System.out.println("roots: " + roots.length);
  }

  private int[][] possibleRoots() {
    int[][] res = new int[sm.realities][];
    for (int k = 0; k < sm.realities; k++) {
      Node[] nodes = g.getNodes();
      Set[] observations = new Set[colluders.length];

      System.out.println("#### TREE " + k);
      for (int i = 0; i < colluders.length; i++) {
        int rootDist = sm.rootDist(colluders[i], k);
//        int myRootDist = (int) hop.distance(colluders[i], sm.roots[k], 0);
//        if (rootDist != myRootDist) {
//          System.out.println("RD: " + rootDist + " != " + myRootDist);
//          System.out.println(sm.roots[k] + " -> " + colluders[i]);
//          System.out.println(Arrays.toString(sm.coords[k][colluders[i]]));
//          System.out.println(Arrays.toString(sm.coords[k][colluders[i]]));
//        }
        Set<Integer> possible = new HashSet<>();
        for (int poss = 0; poss < nodes.length; poss++) {
          int thisDist = (int) hop.distance(colluders[i], poss, 0);
//          if (poss == sm.roots[k]){
//            System.out.println("DIST: " + thisDist);
//          }
          if (rootDist == thisDist) {
            possible.add(poss);
          }
        }
        observations[i] = possible;
      }
      Set<Integer> result = new HashSet<Integer>(observations[0]);
      for (Set o : observations) {
        result.retainAll(o);
      }
      res[k] = result.stream().mapToInt(Integer::intValue).toArray();
    }
    return res;
  }


  private void initParents(int rootIndex, int k) {
    Node[] nodes = g.getNodes();

    int[] hopCount = new int[nodes.length];
    Arrays.fill(hopCount, -1);

    List<ParentChild>[] possibleParents = new List[nodes.length];
    for (int i = 0; i < possibleParents.length; i++) {
      possibleParents[i] = new ArrayList<>();
    }

    Queue<ParentChild> q = new LinkedList<>();
    q.add(new ParentChild(-1, roots[k][rootIndex], 0));

    while (!q.isEmpty()) {
      ParentChild currParentChild = q.poll();
      int curr = currParentChild.getChild();
      int depth = currParentChild.getDepth();

      if (hopCount[curr] == -1 || hopCount[curr] == depth) { // first or new path found
        possibleParents[curr].add(currParentChild);
      }

      if (hopCount[curr] == -1) { // only if first encounter of node
        for (int next : nodes[curr].getOutgoingEdges()) {
          q.add(new ParentChild(curr, next, depth + 1));
        }
      }
      hopCount[curr] = depth;
    }

    int[][] result = new int[nodes.length][];
    for (int i = 0; i < possibleParents.length; i++) {
      result[i] = possibleParents[i].stream().mapToInt(ParentChild::getParent).toArray();
    }

    for (int c : colluders) { // we know all neighbours of colluders
      for (int i = 0; i < result.length; i++) {
        if (i == c || actualParents[k][i] == c) { // if neighbour we know it
          result[i] = new int[] {actualParents[k][i]};
        } else { // else we know we're not it
          result[i] = Arrays.stream(result[i]).filter(p -> p != c).toArray();
        }
      }
    }
    possParents[k][rootIndex] = result;
  }

  private void initChildren(int rootIndex, int k) {
    int[][] parents = possParents[k][rootIndex];
    Set<Integer>[] children = new Set[parents.length];
    for (int i = 0; i < children.length; i++) {
      children[i] = new HashSet<>();
    }
    for (int i = 0; i < parents.length; i++) {
      for (int parent : parents[i]) {
        if (parent != -1) {
          children[parent].add(i);
        }
      }
    }
    possChildren[k][rootIndex] = new int[parents.length][];
    for (int i = 0; i < possChildren[k][rootIndex].length; i++) {
      possChildren[k][rootIndex][i] = children[i].stream().mapToInt(Integer::intValue).toArray();
    }
  }

  private boolean couldBeInSubtree(int node, int subRoot, int k) {
    return Arrays.stream(roots).anyMatch(r -> couldBeInThisSubtree(node, subRoot, r[k], k));
  }

  private boolean couldBeInThisSubtree(int node, int subRoot, int root, int k) {
    if (node == subRoot) {
      return true;
    }
    if (node == root) {
      return false;
    }
    return Arrays.stream(possParents[k][root][node])
        .anyMatch(p -> couldBeInSubtree(p, subRoot, root));
  }

  public void observe(int to, int dst, int cur) {
    if (isAttacker(cur)) {
      dstAnonymitySet(0, to, dst, cur, true);
      dstAnonymitySet(0, to, dst, cur, false);
    }
  }

  private int[] dstAnonymitySet(int from, int to, int dst, int attacker, boolean obfuscated) {
    Set<Integer> res = new HashSet<>();
//    Set<Integer> all = new HashSet<>();
    int[] sizePerTree = new int[sm.realities];
    for (int k = 0; k < sm.realities; k++) {
      Set<Integer> currSet = Arrays.stream(dstAnonymitySet(from, to, dst, attacker, obfuscated, k))
          .boxed().collect(Collectors.toSet());
      sizePerTree[k] = currSet.size();
      if (k == 0) {
        res.addAll(currSet);
      } else {
        res.retainAll(currSet);
      }
//      all.addAll(currSet);
    }
    int[] atLvls = Arrays.stream(lvl).mapToInt(l -> l[attacker]).toArray();
    int atDeg = g.getNode(attacker).getInDegree();
    int[] dstLvls = Arrays.stream(lvl).mapToInt(l -> l[dst]).toArray();
    int dstDeg = g.getNode(dst).getInDegree();
    int size = res.size();

    (obfuscated ? statsObf :statsNonObf)
        .add(new Stats(atLvls, atDeg, dstLvls, dstDeg, sizePerTree, size));

//    System.out.println("### att lvl: "
//        + Arrays.stream(lvl).map(l -> l[attacker] + "").collect(Collectors.joining(","))
//        + ", deg " + g.getNode(attacker).getInDegree());
//    System.out.println("### dst lvl: "
//        + Arrays.stream(lvl).map(l -> l[dst] + "").collect(Collectors.joining(","))
//        + ", deg " + g.getNode(dst).getInDegree());
//    System.out.println("### set size: " + res.size() + " (" + all.size() + ") " +
//        (obfuscated ? " (obfuscated)" : ""));
//    System.out.println();
//    size[to] = res.size();
    return res.stream().mapToInt(Integer::intValue).toArray();
  }

  private int[] dstAnonymitySet(int from, int to, int dst, int attacker, boolean obfuscated,
                                int k) {
    return maybeSubtree(to, k).stream()
        .filter(i -> obfuscated || hop.distance(attacker, dst, 0) == hop.distance(attacker, i, 0))
        .mapToInt(Integer::intValue).toArray();
  }

  private Set<Integer> maybeSubtree(int n, int k) {
    Set<Integer> subTree = new HashSet<>();
    for (int[][] st : possChildren[k]) {
      boolean[] visited = new boolean[g.getNodeCount()];
      Queue<Integer> q = new LinkedList<>();
      q.add(n);
      while (!q.isEmpty()) {
        int curr = q.poll();
        visited[curr] = true;
        subTree.add(curr);
        for (int c : st[curr]) {
          if (!visited[c]) {
            q.add(c);
          }
        }
      }
    }
    return subTree;
  }

  private boolean isAttacker(int n) {
    for (int c : colluders) {
      if (c == n) {
        return true;
      }
    }
    return false;
  }

  public void printStats(boolean obf) {
    Map<Integer, List<Integer>> sizePerDstDeg = new HashMap<>();
    Map<Integer, List<Integer>> sizePerAtkDeg = new HashMap<>();
    Map<Integer, Integer> sizeHist = new HashMap<>();
    Map<Integer, Double> dstHist = new HashMap<>();
    Map<Integer, Double> atkHist = new HashMap<>();

    for (Stats s: (obf ? statsObf : statsNonObf)) {
      sizePerDstDeg.putIfAbsent(s.dstDeg, new ArrayList<>());
      sizePerAtkDeg.putIfAbsent(s.atDeg,  new ArrayList<>());

      sizePerDstDeg.get(s.dstDeg).add(s.size);
      sizePerAtkDeg.get(s.atDeg).add(s.size);

      int logsize = (int) Math.log10(s.size);
      sizeHist.putIfAbsent(logsize, 0);
      sizeHist.put(logsize, sizeHist.get(logsize) + 1);
    }

    for (Map.Entry<Integer, List<Integer>> e: sizePerDstDeg.entrySet()) {
      dstHist.put(e.getKey(), e.getValue().stream().mapToDouble(Integer::doubleValue).average().orElse(-1));
    }
    for (Map.Entry<Integer, List<Integer>> e: sizePerAtkDeg.entrySet()) {
      atkHist.put(e.getKey(), e.getValue().stream().mapToDouble(Integer::doubleValue).average().orElse(-1));
    }

//    int maxDstDeg = sizePerDstDeg.keySet().stream().max(Integer::compareTo).orElse(0);
//    int maxAtkDeg = sizePerAtkDeg.keySet().stream().max(Integer::compareTo).orElse(0);
//    int maxSize = sizeHist.keySet().stream().max(Integer::compareTo).orElse(0);
//
//    double[] dst = new double[maxDstDeg];
//    double[] atk = new double[maxAtkDeg];
//    int[] siz = new int[maxSize];
//
//
//    for (int i = 0; i < dst.length; i++) {
//      dst[i] = sizePerDstDeg.getOrDefault(i, List.of(-1))
//          .stream().mapToDouble(Integer::doubleValue).average().orElse(-1);
//    }
//
//    for (int i = 0; i < atk.length; i++) {
//      atk[i] = sizePerAtkDeg.getOrDefault(i, List.of(-1))
//          .stream().mapToDouble(Integer::doubleValue).average().orElse(-1);
//    }
//
//    for (int i = 0; i < siz.length; i++) {
//      siz[i] = sizeHist.getOrDefault(i, -1);
//    }

    try {
      FileWriter myWriter = new FileWriter("THREE_new_" + obf + "_oneST_" + cg.toString() + ".txt");
      myWriter.write("observed=" + statsObf.size() + "\n");
      myWriter.write("size avg"+ (obf ? statsObf : statsNonObf).stream().mapToDouble(s -> s.size).average().orElse(-1) + "\n");
      myWriter.write("size histogram\n");
      for(Map.Entry<Integer, Integer> e: sizeHist.entrySet().stream()
          .sorted(Map.Entry.comparingByKey()).collect(Collectors.toList())) {
        myWriter.write(e.getKey() + ": " + e.getValue() + "\n");
      }
      myWriter.write("avg size dst deg\n");
      for(Map.Entry<Integer, Double> e: dstHist.entrySet().stream()
          .sorted(Map.Entry.comparingByKey()).collect(Collectors.toList())) {
        myWriter.write(e.getKey() + ": " + e.getValue() + "\n");
      }
      myWriter.write("avg size atk deg\n");
      for(Map.Entry<Integer, Double> e: atkHist.entrySet().stream()
          .sorted(Map.Entry.comparingByKey()).collect(Collectors.toList())) {
        myWriter.write(e.getKey() + ": " + e.getValue() + "\n");
      }

//      myWriter.write(Arrays.toString(dst) + "\n");
//      myWriter.write(Arrays.toString(atk) + "\n");
//      myWriter.write(Arrays.toString(siz) + "\n");
      myWriter.close();
    } catch (IOException e) {
      e.printStackTrace();
    }

//    System.out.println("#####  " + cg.toString() + ", tr count: " + statsObf.size());
//    System.out.println(obf ? "OBFUSCATED COORDINATES" : "NON-OBFUSCATED COORDINATES");
//    System.out.println((obf ? statsObf : statsNonObf).stream().mapToDouble(s -> s.size).average().orElse(-1));

  }

  class Stats {
    int[] atLvls;
    int atDeg;
    int[] dstLvls;
    int dstDeg;
    int[] sizePerTree;
    int size;

    public Stats(int[] atLvls, int atDeg, int[] dstLvls, int dstDeg, int[] sizePerTree, int size) {
      this.atLvls = atLvls;
      this.atDeg = atDeg;
      this.dstLvls = dstLvls;
      this.dstDeg = dstDeg;
      this.sizePerTree = sizePerTree;
      this.size = size;
    }


    @Override
    public String toString() {
      return
          "atLvls=" + Arrays.toString(atLvls) +
          ", atDeg=" + atDeg +
          ", dstLvls=" + Arrays.toString(dstLvls) +
          ", dstDeg=" + dstDeg +
          ", sizePerTree=" + Arrays.toString(sizePerTree) +
          ", size=" + size;
    }
  }

}
