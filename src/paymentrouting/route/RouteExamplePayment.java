
package paymentrouting.route;

import gtna.graph.Edge;
import gtna.graph.Node;
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
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import paymentrouting.datasets.LNParams;
import paymentrouting.datasets.TransactionList;
import paymentrouting.route.costfunction.CostFunction;
import paymentrouting.route.costfunction.Eclair;
import paymentrouting.route.costfunction.LND;
import treeembedding.credit.CreditLinks;
import treeembedding.credit.Transaction;

public class RouteExamplePayment extends Metric {

  protected double success;
  protected Distribution hopDistribution;

  protected CostFunction costFunction;

  protected CreditLinks edgeweights; //the balances of the channels
  protected LNParams params;
  protected Transaction[] transactions; //list of transactions

  public RouteExamplePayment(CostFunction costFunction) {
    super("EXAMPLE_ROUTE_PAYMENT", new Parameter[] {new StringParameter("PROTOCOL", costFunction.getClass().getSimpleName())});
    this.costFunction = costFunction;
  }

  @Override
  public void computeData(Graph g, Network n, HashMap<String, Metric> m) {
    this.hopDistribution = new Distribution(new long[] {1, 2, 3, 4}, 10);

    edgeweights = (CreditLinks) g.getProperty("CREDIT_LINKS");
    params = (LNParams) g.getProperty("LN_PARAMS");
    transactions = ((TransactionList)g.getProperty("TRANSACTION_LIST")).getTransactions();
    success = transactions.length;
    Map<Edge, Double> originalAll = new HashMap<>();

    for (Edge e : params.getParams().keySet()){
//      System.out.println(Arrays.toString(params.getParams().get(e)));
    }

    for (Transaction tr : transactions) {
      int src = tr.getSrc();
      int dst = tr.getDst();
      double val = tr.getVal();
//      System.out.println();
//      System.out.println(src + " -> " + dst + " : " + val);

      Map<Edge, Double> original = new HashMap<>();

      int k = 1;
      if (costFunction instanceof Eclair)
        k = 3;

      Path p = yensKShortestPaths(k, src, dst, val, g.getNodes(), false);

      if(p == null){
//        System.out.println("no path found");
        success--;
        continue;
      }
      int[] path = p.p;

//      System.out.println("path:");
//      System.out.println(Arrays.toString(path));


      for (int i = 0; i < path.length - 1; i++) {    // for all edges on path
        boolean ok = edgeweights.setWeight(path[i], path[i + 1], val);
        if (!ok) {
          weightUpdate(edgeweights, original);
          success--;
          break;
        }

        Edge e = edgeweights.makeEdge(path[i], path[i + 1]);
//        System.out.println(i+": " + e.toString());
        double w = edgeweights.getWeight(e);
        original.put(e, w);

        if (!originalAll.containsKey(e))
          originalAll.put(e, w);
      }
      weightUpdate(edgeweights, originalAll);//todo dyn?
    }
    weightUpdate(edgeweights, originalAll);
    success /= transactions.length;
  }

  private Path yensKShortestPaths(int numPaths, int src, int dst, double val, Node[] nodes, boolean badEclair){
    if (!badEclair) {
      int aux = src;
      src = dst;
      dst = aux;
    }
    Queue<Path> B = new PriorityQueue<>();
    Path[] A = new Path[numPaths];
    A[0] = dijkstra(src, dst, val, nodes, Set.of(), Set.of());
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
          if (p != null && p.slice(i, val, edgeweights, params, costFunction).equals(rootPath)) {
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
    Random generator = new Random();
    int randomIndex = generator.nextInt(A.length);
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
    return A[randomIndex];
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

  protected void weightUpdate(CreditLinks edgeweights, Map<Edge, Double> updateWeight){
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
    return new Single[] {s};
  }


  @Override
  public boolean applicable(Graph g, Network n, HashMap<String, Metric> m) {
    return g.hasProperty("CREDIT_LINKS") && g.hasProperty("TRANSACTION_LIST");
  }
}



