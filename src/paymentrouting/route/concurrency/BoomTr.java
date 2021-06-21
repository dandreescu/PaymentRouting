package paymentrouting.route.concurrency;


import static paymentrouting.route.concurrency.Status.DONE_NEG;
import static paymentrouting.route.concurrency.Status.DONE_POS;
import static paymentrouting.route.concurrency.Status.NOT_STARTED;
import static paymentrouting.route.concurrency.Status.ONGOING;
import static paymentrouting.route.concurrency.Status.ABORTED;
import static paymentrouting.route.concurrency.Status.READY_NEG;
import static paymentrouting.route.concurrency.Status.READY_POS;

import gtna.graph.Edge;

public class BoomTr implements Comparable<BoomTr> {

  Status status;
  double time;
  double val;
  int i;
  int[] path;
  BoomPayment parent;
//  double lastNegUnlockTime;

  /**
   * this is one of the tiny transactions, after splitting
   * @param val
   * @param path
   * @param parent
   */
  public BoomTr(double val, int[] path, BoomPayment parent) {
    this.time = -1;
    this.val = val;
    this.path = path;
    this.i = 0;
    this.parent = parent;
    this.status = NOT_STARTED;
  }

  public void start(double time) {
    assert (status == NOT_STARTED);
    this.time = time;
    this.status = ONGOING;
  }

  public void abort() {
    assert (status == ONGOING);
    status = ABORTED;
  }

  public void progress() {
    Status prev = status;
//    parent.rPay.logTime(getSrc(), "prog: "+time + " "+status+" "+parent.toString().split("@")[1]);
//    print("prog...");
//    parent.rPay.logPayment(this, "prog: "+time + "\t"+status);
    switch (status) {
      case ONGOING:
        route();
        break;
      case ABORTED:
        unlock(false);     // was aborted sometime between prev hop and now
        break;
      case NOT_STARTED:
        assert false;
        break;
      case DONE_NEG:        // DONE means just ignore
      case DONE_POS:        // DONE means just ignore
      case READY_POS:
      case READY_NEG:
//        assert false;   // shouldn't happen
        break;
    }
//    print("...prog");
    if (status != prev)
      parent.check(time);
//    print("...chck");
  }

  public void execute(double timeNow) {
//    print("exec...");
    assert (status == READY_POS);
    this.time = timeNow;
    unlock(true);
//    print("...exec");
  }

  public void rollback(double timeNow) {
//    print("roll...");
    assert (status == READY_POS || status == READY_NEG);
    this.time = timeNow;
    unlock(false);
//    print("...roll");
  }

  private void route() {
    if (isDestination()) {
      status = READY_POS;
      return;
    }

    parent.rPay.remember(path[i], path[i + 1]);                // remember initial weight (originalAll)
    boolean ok = parent.rPay.lock(path[i], path[i + 1], val);  // lock collateral

    if(ok) {
      i++;                            // proceed to next node on path
      time += parent.rPay.linklatency;              // time passes (after everything else)
    } else {
      status = READY_NEG;
    }
  }

  private void unlock(boolean successful) {
//    print("unlk...");
    status = successful ? DONE_POS : DONE_NEG;
    double time = this.time;
    for (int j = i; j > 0; j--) {     // from current to src
      time += parent.rPay.linklatency;              // even the first unlock after latency (boomerang does it)
      ScheduledUnlock lock = new ScheduledUnlock(
          new Edge(path[j - 1], path[j]), time, successful, val);
      parent.rPay.qLocks.add(lock);
    }
//    print("...unlk");
  }

  public int getSrc(){
    return path[0];
  }

  private boolean isDestination(){
    return i + 1 == path.length;
  }


//  public void print(String msg){
//    System.out.println(time + "\t" + "child: " + Arrays.asList(parent.peers).indexOf(this) + "\t" + status);
//    System.out.println(msg + "\tamt: " + parent.amt + "\n");
//  }

  @Override
  public int compareTo(BoomTr o) {
    return (int) Math.signum(this.time - o.time);
  }
}

