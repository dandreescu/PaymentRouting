package paymentrouting.datasets;

import gtna.transformation.Transformation;
import gtna.util.parameter.DoubleParameter;
import gtna.util.parameter.IntParameter;
import gtna.util.parameter.Parameter;
import gtna.util.parameter.StringParameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import gtna.graph.Edge;
import gtna.graph.Graph;
import gtna.graph.Node;

public class InitPaths extends Transformation {
  int k;

  public InitPaths(int k) {
    super("COMPUTE_PATHS", new Parameter[] {new IntParameter("K", k)});
    this.k = k;
  }

  public static List<int[]> getEdgeDisjointPaths(Graph g, int src, int dst, int k) {
    if(src==dst)return List.of(new int[]{src});

    Map<Edge, Integer> flow = new HashMap<>();
    Map<Edge, Integer> capacity = new HashMap<>();
    for (Edge e : g.getEdges().getEdges()){
      flow.put(e, 0);
      capacity.put(e, 1);
      Edge r = new Edge(e.getDst(), e.getSrc());
      flow.put(r, 0);
      capacity.put(r, 1);
    }
    double totalflow = 0;
    int[] respath;
    while (totalflow < k && (respath = findResidualFlow(flow, capacity, g.getNodes(), src, dst)) != null){
      //pot flow along this path
      int min = Integer.MAX_VALUE;
      for (int i = 0; i < respath.length-1; i++){
        Edge e = new Edge(respath[i], respath[i+1]);
        int a = capacity.get(e) - flow.get(e);
        if (a < min){
          min = a;
        }
      }
      //update flows
      totalflow = totalflow + min;
      for (int i = 0; i < respath.length-1; i++){
        int n1 = respath[i];
        int n2 = respath[i+1];
        Edge e = new Edge(n1, n2);
        Edge r = new Edge(n2, n1);
        flow.put(e, flow.get(e) + min);   // push flow
        flow.put(r, capacity.get(e) - flow.get(e));   // un-push residual
      }
    }

    List<int[]> paths = new ArrayList<>();
    for (int i = 0; i < k; i++) {
      List<Integer> p = new ArrayList<>();
      p.add(src);
      int curr = src;

      while (curr != dst){
        boolean stuck = true;
        for (int next : g.getNodes()[curr].getOutgoingEdges()){
          Edge e = new Edge(curr, next);
          if (flow.get(e) > 0){
            flow.put(e, 0);   // do not reuse edge
            curr = next;
            stuck = false;
            p.add(curr);
            break;
          }
        }
        if (stuck) break;
      }
      if (curr == dst){   // move path from list to array
        int[] arrpath = new int[p.size()];
        int j = 0;
        for (int n : p)
          arrpath[j++] = n;
        paths.add(arrpath);
        p.clear();
      } else break;
    }
    return paths;
  }


  private static int[] findResidualFlow(
      Map<Edge, Integer> flow,
      Map<Edge, Integer> capacity,
      Node[] nodes, int src, int dst){

    int[][] pre = new int[nodes.length][2];
    for (int i = 0; i < pre.length; i++){
      pre[i][0] = -1;
    }
    Queue<Integer> q = new LinkedList<Integer>();
    q.add(src);
    pre[src][0] = -2;
    while (!q.isEmpty()){
      int n1 = q.poll();
      int[] out = nodes[n1].getOutgoingEdges();
      for (int n: out){
        Edge e = new Edge(n1, n);
        if (pre[n][0] == -1 && capacity.get(e) > flow.get(e)){
          pre[n][0] = n1;
          pre[n][1] = pre[n1][1]+1;
          if (n == dst){
            int[] respath = new int[pre[n][1]+1];
            while (n != -2){
              respath[pre[n][1]] = n;
              n = pre[n][0];
            }
            return respath;
          }
          q.add(n);
        }

      }
    }
    return null;
  }

  @Override
  public Graph transform(Graph g) {
    Map<Integer, Map<Integer, List<int[]>>> map = new HashMap<>();
    Node[] nodes = g.getNodes();
    for (Node n : nodes) {
      int src = n.getIndex();
      Map<Integer, List<int[]>> pathsMap = new HashMap<>();
      map.put(src, pathsMap);
      for (Node m : nodes) {
        int dst = m.getIndex();
        pathsMap.put(dst, getEdgeDisjointPaths(g, src, dst, k));
      }
    }
    g.addProperty("EDGE_DISJOINT_PATHS", new Paths(map));
    return g;
  }

  @Override
  public boolean applicable(Graph g) {
    return true;
  }
}
