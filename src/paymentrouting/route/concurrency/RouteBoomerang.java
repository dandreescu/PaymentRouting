package paymentrouting.route.concurrency;

import static paymentrouting.route.concurrency.RouteBoomerang.BoomType.REDUNDANT;
import static paymentrouting.route.concurrency.RouteBoomerang.BoomType.REDUNDANT_RETRY;
import static paymentrouting.route.concurrency.RouteBoomerang.BoomType.RETRY;
import static paymentrouting.route.concurrency.Status.DONE;
import static paymentrouting.route.concurrency.Status.READY;

import gtna.data.Single;
import gtna.graph.Edge;
import gtna.graph.Node;
import gtna.io.DataWriter;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.util.parameter.IntParameter;
import gtna.util.parameter.Parameter;
import gtna.util.parameter.StringParameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;

import gtna.graph.Graph;
import java.util.Random;
import paymentrouting.datasets.Paths;
import paymentrouting.datasets.TransactionList;
import treeembedding.credit.CreditLinks;
import treeembedding.credit.Transaction;

public class RouteBoomerang extends RoutePaymentConcurrent {
  PriorityQueue<BoomTr> trQueue;
  Queue[] backlog;
  Paths paths;
  int v = 2, u; //todo DON'T FORGET TO CHANGE to 25
  BoomType protocol;

  double ttc = 0;
  double volume = 0;
  double endTime = 0;

//  Map<BoomPayment, Map<BoomTr, List<String >>> paymentLog;

  public enum BoomType {
    RETRY, REDUNDANT, REDUNDANT_RETRY
  }

  public RouteBoomerang(BoomType protocol, int u) {
    super(new Parameter[]{
        new StringParameter("BOOM_TYPE", "BOOMERANG_" + protocol.toString()),
        new IntParameter("U", u),
        });
    this.protocol = protocol;
    this.u = u;
  }

//  public void logPayment(BoomTr p, String msg) {
//    Map<BoomTr, List<String >> myMap = paymentLog.get(p.parent);
//    List<String> myLog = myMap.get(p);
//    myLog.add(p.parent.succ + " " + p.parent.amt + ": " + msg + ": time = " + p.time + "; status = " + p.status + "; i = " + p.i + "; path =" +
//        Arrays.toString(p.path));
//  }

  public void preprocess(Graph g) {
//    paymentLog = new HashMap<>();
    rand = new Random();
    edgeweights = (CreditLinks) g.getProperty("CREDIT_LINKS");
    transactions = ((TransactionList)g.getProperty("TRANSACTION_LIST")).getTransactions();
    paths = (Paths) g.getProperty("EDGE_DISJOINT_PATHS");

    originalAll = new HashMap<>();
    locked = new HashMap<>();
    qLocks = new PriorityQueue<>();
    trQueue = new PriorityQueue<>();
    backlog = new Queue[g.getNodeCount()];

    ttc = 0;
    volume = 0;
    endTime = 0;
    success = 0;
  }

  public void postprocess() {
    unlockAllUntil(Double.MAX_VALUE);
    weightUpdate(edgeweights, originalAll);

    ttc /= success;
    // todo commented out to match the total volume / success count in the graphs in data/boomerang/original_plots
//    volume /= success;
//    success /= transactions.length;
  }

  /**
   * statistics for successful transactions
   * @param ttc
   * @param amt
   */
  public void incSucc(double ttc, double amt){
    success++;
    volume += amt;
    this.ttc += ttc;
  }

  public void run(Graph g) {
    for (Transaction tr : transactions) { // fill backlog of each node with transactions
      int src = tr.getSrc();
      if (backlog[src] == null)
        backlog[src] = new LinkedList<>();
      backlog[src].add(tr);
    }

    for (Node n: g.getNodes()) {  // each node starts its first transaction in the backlog
      int src = n.getIndex();
      if (backlog[src] != null)
        startBoomTr(src, 0d);
    }

    while (!trQueue.isEmpty()) {
      BoomTr btr = trQueue.poll();  // next event
      if (btr.status == DONE || btr.status == READY) // DONE means ignore, READY mean waiting at the dest for execute/rollback
          continue;
      unlockAllUntil(btr.time);     // unlock collateral
      endTime = btr.time;           // only for stats, endtime of last transaction
      btr.progress();               // tr makes a step
      trQueue.add(btr);
    }
  }

  public void startBoomTr(int src, double time) {
    Transaction tr = (Transaction) backlog[src].poll(); // next pending tr
    if (tr == null)
      return;   // this node is done

    int dst = tr.getDst();
    double val = tr.getVal();
    double valPerTr = val / v;              // split payment into v

    BoomTr[] peers = new BoomTr[u + v];     // tiny sibling transactions (even if some do not start yet, in case of RETRY)
    BoomPayment parent = new BoomPayment(v, peers, time, val, this); // parent coordinates all
    Map<BoomTr, List<String>> myMap = new HashMap<>();
//    paymentLog.put(parent, myMap);
    for (int j = 0; j < v + u; j++) {            // for all pieces
      int[] path = paths.get(src, dst, rand);    // random path out of k-edge-disjoint
      BoomTr btr = new BoomTr(valPerTr, path, parent);
      peers[j] = btr;
      myMap.put(btr, new ArrayList<>());
      if((protocol == REDUNDANT )     // redundant -> send all from the start
          || (protocol == RETRY && j < v)   // retry -> send first v
          || (protocol == REDUNDANT_RETRY && j < v + Math.min(10, u))) { // at most 10 redundant
        btr.start(time);
        this.trQueue.add(btr);
      }
    }
  }

  /**
   * remember the original weight of an edge, to revert after each run
   * @param src
   * @param dst
   */
  public void remember(int src, int dst){
    Edge e = edgeweights.makeEdge(src, dst);
    double w = edgeweights.getWeight(e);
    if (!originalAll.containsKey(e))
      originalAll.put(e, w);
  }

  @Override
  public boolean applicable(Graph g, Network n, HashMap<String, Metric> m) {
    return g.hasProperty("CREDIT_LINKS")
        && g.hasProperty("TRANSACTION_LIST")
        && g.hasProperty("EDGE_DISJOINT_PATHS");
  }

  @Override
  public Single[] getSingles() {
    Single s = new Single(this.key + "_SUCCESS", this.success);
    Single ttc = new Single(this.key + "_TTC", this.ttc / 1000); //msec -> sec
    Single vol = new Single(this.key + "_VOLUME", this.volume);
    return new Single[]{s, ttc, vol};
  }

  /**
   * Writes nothing.
   * Could not find a way to not write distributions without messing with other metrics.
   * @param folder
   * @return
   */
  @Override
  public boolean writeData(String folder) {
    double[] nothing = new double[0];
    boolean succ = true;
    succ &= DataWriter.writeWithIndex(nothing, this.key+"_MESSAGES", folder);
    succ &= DataWriter.writeWithIndex(nothing, this.key+"_MESSAGES_SUCC", folder);
    succ &= DataWriter.writeWithIndex(nothing, this.key+"_HOPS", folder);
    succ &= DataWriter.writeWithIndex(nothing, this.key+"_HOPS_SUCC", folder);
    succ &= DataWriter.writeWithIndex(nothing, this.key+"_TRYS", folder);
    succ &= DataWriter.writeWithIndex(nothing, this.key+"_SUCCESS_TEMPORAL", folder);
    return succ;
  }
}

