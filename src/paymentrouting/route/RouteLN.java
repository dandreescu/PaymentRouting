
package paymentrouting.route;

import gtna.graph.Edge;
import gtna.graph.Node;
import gtna.util.parameter.BooleanParameter;
import gtna.util.parameter.StringParameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import gtna.data.Single;
import gtna.graph.Graph;
import gtna.io.DataWriter;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.util.Distribution;
import gtna.util.parameter.Parameter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import paymentrouting.datasets.LNParams;
import paymentrouting.datasets.TransactionList;
import paymentrouting.route.costfunction.CostFunction;
import paymentrouting.route.costfunction.Eclair;
import paymentrouting.route.costfunction.LND;
import treeembedding.credit.CreditLinks;
import treeembedding.credit.Transaction;

public class RouteLN extends Metric {

  public double success;
  public double fees;
  public Distribution hopDistribution;

  public CostFunction costFunction;

  public CreditLinks edgeweights; //the balances of the channels
  public LNParams params;
  public Transaction[] transactions; //list of transactions
  public Random generator;
  Map<Edge, Double> originalAll;
  Map<Edge, Double> original;
  Map[] lastFailure;
  double timeNow;
  boolean up;

  public RouteLN(CostFunction costFunction, boolean update) {
    super("ROUTE_LN", new Parameter[] {
        new StringParameter("PROTOCOL", costFunction.getClass().getSimpleName()),
        new BooleanParameter("UPDATE", update)});
    this.costFunction = costFunction;
    up = update;
  }

  @Override
  public void computeData(Graph g, Network n, HashMap<String, Metric> m) {
    this.hopDistribution = new Distribution(new long[] {1, 2, 3, 4}, 10);
    generator = new Random(12345);
    if (costFunction instanceof LND){
      lastFailure = new Map[g.getNodeCount()];
      ((LND) costFunction).init(lastFailure);
    }

    fees = 0;
    edgeweights = (CreditLinks) g.getProperty("CREDIT_LINKS");
    params = (LNParams) g.getProperty("LN_PARAMS");
    transactions = ((TransactionList)g.getProperty("TRANSACTION_LIST")).getTransactions();
    success = transactions.length;
    originalAll = new HashMap<>();

    for (int i = 0; i < transactions.length; i++) {
      Transaction tr = transactions[i];
      int src = tr.getSrc();
      int dst = tr.getDst();
      double val = tr.getVal();
      timeNow = tr.getTime();
      original = new HashMap<>();
      if (costFunction instanceof LND){
        ((LND) costFunction).setObserver(src, timeNow);
      }

      int k = (costFunction instanceof Eclair) ? 3 : 1;

      List<Path> paths = yensKShortestPaths(k, src, dst, val, g.getNodes(), false);
      if (paths == null) {
        success--;
        continue;
      }
//      appendExtra(paths, val, g.getNodes());

      int pid = generator.nextInt(paths.size());
      Path p = paths.get(pid);

      if (p == null) {
        success--;
        continue;
      }
      int[] path = p.p;

      boolean ok = false;
      for (int r = 0; r < 5; r++) { //retry 5 times
        ok = send(path, val) == -1;
        if(costFunction instanceof LND){
          Path res = dijkstra(src, dst, val, g.getNodes(), new HashSet<>(), new HashSet<>());
          if(res==null)
            break;
          path = res.p;
        }
        if (ok) {
          break;
        }
      }
      if (ok) {
        double fee = 0;
        for (int j = path.length-2; j > 0; j--){
          double[] par = params.getParams(path[j], path[j+1]);
          fee += //par[0];//
           val * par[1];
        }
//        System.out.println("added: " + fee + "for val: "+val);
        fees += fee;
      } else {
        success--;
      }

      if(!up)
        weightUpdate(edgeweights, originalAll);
    }
    weightUpdate(edgeweights, originalAll);
    fees /= success;
    success /= transactions.length;
  }

  public int send(int[] path, double val) {
    for (int i = 0; i < path.length - 1; i++) {

      Edge e = edgeweights.makeEdge(path[i], path[i + 1]);
      double w = edgeweights.getWeight(e);
      if (!original.containsKey(e))
        original.put(e, w);
      if (!originalAll.containsKey(e))
        originalAll.put(e, w);

      boolean ok = edgeweights.setWeight(path[i], path[i + 1], val);
      if (!ok) {
        if(costFunction instanceof LND) {
          if (lastFailure[path[0]] == null) {
            lastFailure[path[0]] = new HashMap();
          }
          lastFailure[path[0]].put(e, timeNow);
//          System.out.println("src: "+path[0]+"\ttime: "+timeNow);
        }
        weightUpdate(edgeweights, original);
        return i;
      }
    }
    return -1;
  }

//  private void appendExtra(List<Path> paths, double val, Node[] nodes) {
//    List<Path> newPaths = new ArrayList<>();
//    Set<Edge> excluded = new HashSet<>();
//    int[] p = paths.get(0).p;
//    for (int k = 0; k < 5; k++) {
//      double minCap = Double.MAX_VALUE / 2;
//      Edge e = null;
//      for (int i = 0; i < p.length - 1; i++) {    // for all edges on path
//        double cap = edgeweights.getTotalCapacity(p[i], p[i + 1]);
//        Edge edge = edgeweights.makeEdge(p[i], p[i + 1]);
//        if (!excluded.contains(edge) && cap < minCap) {
//          minCap = cap;
//          e = edge;
//        }
//      }
//      if (e != null) {
//        excluded.add(e);
//        Path newPath = dijkstra(p[0], p[p.length - 1], val / 2, nodes, new HashSet<>(), new HashSet<>());
//        newPaths.add(newPath);
//      }
//    }
//    paths.addAll(newPaths);
//  }

//  public boolean sendRec(List<Path> paths, int[] path, double val) {
//    for (int i = 0; i < path.length - 1; i++) {    // for all edges on path
//
//      Edge e = edgeweights.makeEdge(path[i], path[i + 1]);
//      double w = edgeweights.getWeight(e);
//      if (!original.containsKey(e))
//        original.put(e, w);
//      if (!originalAll.containsKey(e))
//        originalAll.put(e, w);
//
//      boolean ok = edgeweights.setWeight(path[i], path[i + 1], val);
//      if (!ok) {
////        weightUpdate(edgeweights, original);
//        int[] spurPath = findAlt(paths, path, i);
//        if (spurPath == null)
//          return false;
//        double pot = edgeweights.getPot(path[i], path[i + 1]);
//        boolean splitSaved = sendRec(paths, spurPath, val - pot)
//            && sendRec(paths, Arrays.copyOfRange(path, i, path.length), pot);
//        if (splitSaved) {
////          System.out.println("saved: "+paths.toString().split("@")[1]);
//        }
//        return splitSaved;
//      }
//    }
//    return true;
//  }

  private int[] findAlt(List<Path> paths, int[] path, int failPoint) {
    for (Path p : paths) {
      if (p != null
          && p.p != null
          && failPoint+1 < p.p.length
          && p.p[failPoint] == path[failPoint]
          && p.p[failPoint+1] != path[failPoint+1]
          && !Arrays.equals(path, p.p)) {
        return Arrays.copyOfRange(p.p, failPoint, p.p.length);
      }
    }
    return null;
  }


  private List<Path> yensKShortestPaths(int numPaths, int src, int dst, double val, Node[] nodes, boolean badEclair){
    if (!badEclair) {
      int aux = src;
      src = dst;
      dst = aux;
    }
    Queue<Path> B = new PriorityQueue<>();
    Path[] A = new Path[numPaths];
    A[0] = dijkstra(src, dst, val, nodes, new HashSet<>(), new HashSet<>());
    if (A[0] == null)
      return null;

    for (int k = 1; k < numPaths; k++) {
//      System.out.println(k);
      for (int i = 0; i < A[k-1].len() - 2; i++) {
        int spurNodeIndex = A[k-1].p[i];
        Node spurNode = nodes[spurNodeIndex];
        Path rootPath = A[k-1].slice(i, val, edgeweights, params, costFunction);

        Set<Edge> removedEdges = new HashSet<>();
        for (Path p : A) {
          if (p != null && i+1 < p.p.length && p.slice(i, val, edgeweights, params, costFunction).equals(rootPath)) {
            removedEdges.add(edgeweights.makeEdge(p.p[i], p.p[i+1]));
          }
        }
        Set<Integer> removedNodes = new HashSet<>();
        for (int rootPathNode : rootPath.p) {
          if (rootPathNode != spurNode.getIndex()) {
            removedNodes.add(rootPathNode);
          }
        }

        Path spurPath = dijkstra(spurNode.getIndex(), dst, val, nodes, removedNodes, removedEdges); //todo remove removed

        if (spurPath != null) {
          Path totalPath = Path.concat(rootPath, spurPath);
          if (!B.contains(totalPath))
            B.add(totalPath);
        }
      }
      if (B.isEmpty())
        break;

      A[k] = B.poll();
    }
    for (Path p : A) {
      if (p == null) {
//        System.out.println("null path");
      }
      else {
//        System.out.println(Arrays.toString(p.p));
        if (!badEclair) {
          p.reverse();
        }
      }
    }
    return Arrays.stream(A).filter(Objects::nonNull).collect(Collectors.toList());
  }

  private Path dijkstra(int src, int dst, double val, Node[] nodes, Set<Integer> excludedNodes, Set<Edge> excludedEdges) {

    double[] dist = new double[nodes.length];
    int[] prev = new int[nodes.length];
//    double[] cltv = new double[nodes.length];
    double[] amt = new double[nodes.length];
    boolean[] visited = new boolean[nodes.length];

    Queue<Node> pq = new LinkedList<Node>();
//        new PriorityQueue<>((x, y) -> (int) Math.signum(dist[y.getIndex()] - dist[x.getIndex()]));
    for (Node n : nodes) {
      if (!excludedNodes.contains(n.getIndex())) {
        pq.add(n);
      }
    }

    Arrays.fill(prev, -1);
    Arrays.fill(dist, Double.MAX_VALUE);

    for (int in : nodes[dst].getIncomingEdges()) {
      if (!excludedEdges.contains(edgeweights.makeEdge(in, dst))){
        if (edgeweights.getPot(in, dst) >= val) {
          amt[in] = val;
          prev[in] = dst;
//          cltv[in] = params.getDelay(new Edge(in, dst));
          dist[in] = costFunction.compute(in, dst, val, edgeweights, params, true);
          if (in == src)
            return new Path(new int[] {src, dst}, dist[in]);
        }
      }
    }
    visited[dst] = true;
    while (!visited[src]) {
//      Node curr = pq.poll();
      Node curr = null;
      Double min = Double.MAX_VALUE;
      for (Node n : pq){
        if (!visited[n.getIndex()]){
          if(min>dist[n.getIndex()]){
            min=dist[n.getIndex()];
            curr = n;
          }
        }
      }
      if (curr == null)
        break;
      int i = curr.getIndex();
      if (dist[i] == Integer.MAX_VALUE)
        break;
      visited[i] = true;
      for (int in : curr.getIncomingEdges()) {
        if (!excludedEdges.contains(edgeweights.makeEdge(in, i))) {
          if (edgeweights.getTotalCapacity(in, i) >= val && !visited[in]) {
            double candidate =
                dist[i] + costFunction.compute(in, i, amt[i], edgeweights, params, false);
            if (candidate < dist[in]) {
              prev[in] = i;
              dist[in] = candidate;
//              cltv[in] = cltv[i];// + delay
              Edge e = new Edge(in, i);
              amt[in] = amt[i] + params.getBase(e) + params.getRate(e) * amt[i];
            }
          }
        }
      }
    }
    ArrayList<Integer> result = new ArrayList<>();
    while (prev[src] != -1) {
      result.add(src);
      src = prev[src];
    }
    result.add(src);
    if (result.size() == 1)
      return null;
    int[] arr = result.stream().mapToInt(Integer::intValue).toArray();
    return new Path(arr, dist[src]);
  }

  public void weightUpdate(CreditLinks edgeweights, Map<Edge, Double> updateWeight){
    for (Map.Entry<Edge, Double> entry : updateWeight.entrySet()) {
      edgeweights.setWeight(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public boolean writeData(String folder) {
    return DataWriter.writeWithIndex(this.hopDistribution.getDistribution(),
        this.key + "_HOPS", folder);
  }

  @Override
  public Single[] getSingles() {
    Single s = new Single(this.key + "_SUCCESS", this.success);
    Single f = new Single(this.key + "_FEES", this.fees);
    return new Single[] {s, f};
  }


  @Override
  public boolean applicable(Graph g, Network n, HashMap<String, Metric> m) {
    return g.hasProperty("CREDIT_LINKS") && g.hasProperty("TRANSACTION_LIST");
  }
}



