package paymentrouting.route.concurrency;

import static paymentrouting.route.concurrency.RouteBoomerang.BoomType.REDUNDANT;
import static paymentrouting.route.concurrency.RouteBoomerang.BoomType.REDUNDANT_RETRY;
import static paymentrouting.route.concurrency.RouteBoomerang.BoomType.RETRY;
import static paymentrouting.route.concurrency.Status.ABORTED;
import static paymentrouting.route.concurrency.Status.DONE_NEG;
import static paymentrouting.route.concurrency.Status.DONE_POS;
import static paymentrouting.route.concurrency.Status.ONGOING;
import static paymentrouting.route.concurrency.Status.READY_NEG;
import static paymentrouting.route.concurrency.Status.READY_POS;

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
  int v, u; //todo DON'T FORGET TO CHANGE to 25
  BoomType protocol;

  double ttc;
  double volume;
//  List[] times;
//  Map<BoomPayment, Map<BoomTr, List<String >>> paymentLog;

  public enum BoomType {
    RETRY, REDUNDANT, REDUNDANT_RETRY
  }

  public RouteBoomerang(BoomType protocol, int u, double latency) {
    super(new Parameter[]{
        new StringParameter("BOOM_TYPE", "BOOMERANG_" + protocol.toString()),
        new IntParameter("U", u),
        });
    this.linklatency = latency;
    this.protocol = protocol;
    this.u = u;
    this.v = 25; //todo DON'T forget to change!
  }

//  public void logTime(int src, String msg) {
//    if (times[src] == null) times[src] = new ArrayList();
//    times[src].add(msg);
//  }

//  public void logPayment(BoomTr p, String msg) {
//    Map<BoomTr, List<String >> myMap = paymentLog.get(p.parent);
//    List<String> myLog = myMap.get(p);
//    myLog.add(msg);
//  }

  public void preprocess(Graph g) {
//    paymentLog = new HashMap<>();
//    times = new List[g.getNodeCount()];
    rand = new Random(123456);
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
    success = 0;
  }

  public void postprocess() {
    unlockAllUntil(Double.MAX_VALUE);
    weightUpdate(edgeweights, originalAll);

    ttc /= success;
    volume /= success;
    success /= transactions.length;
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
    for (Transaction tr : transactions) { // start transactions
      int src = tr.getSrc();
      int dst = tr.getDst();
      double time = tr.getTime();

      double val = tr.getVal();
      double valPerTr = val / v;              // split payment into v

      BoomTr[] peers = new BoomTr[u + v];     // tiny sibling transactions (even if some do not start yet, in case of RETRY)
      BoomPayment parent = new BoomPayment(v, peers, time, val, this); // parent coordinates all

      for (int j = 0; j < v + u; j++) {            // for all pieces
        int[] path = paths.get(src, dst, rand);    // random path out of k-edge-disjoint
        BoomTr btr = new BoomTr(valPerTr, path, parent);
        peers[j] = btr;

        if ((protocol == REDUNDANT)     // redundant -> send all from the start
            || (protocol == RETRY && j < v)   // retry -> send first v
            || (protocol == REDUNDANT_RETRY && j < v + Math.min(10, u))) { // at most 10 redundant
          btr.start(time);
          this.trQueue.add(btr);
        }
      }
    }
    while (!trQueue.isEmpty()) {    // event loop
      BoomTr btr = trQueue.poll();  // next event
      unlockAllUntil(btr.time);     // unlock collateral
      btr.progress();               // tr makes a step
      if (btr.status == ONGOING || btr.status == ABORTED)
        trQueue.add(btr);
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

