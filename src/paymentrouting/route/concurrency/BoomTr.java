package paymentrouting.route.concurrency;


import static paymentrouting.route.concurrency.Status.READY;
import static paymentrouting.route.concurrency.Status.DONE;
import static paymentrouting.route.concurrency.Status.ONGOING;
import static paymentrouting.route.concurrency.Status.ABORTED;

import gtna.graph.Edge;
import java.util.Random;

public class BoomTr implements Comparable<BoomTr> {

  Status status;
  double time;
  double val;
  int i;
  int[] path;
  BoomPayment parent;

  /**
   * this is one of the tiny transactions, after splitting
   * @param time
   * @param val
   * @param path
   * @param parent
   */
  public BoomTr(double time, double val, int[] path, BoomPayment parent) {
    this.time = time;
    this.val = val;
    this.path = path;
    this.i = 0;
    this.parent = parent;
    this.status = ONGOING;
  }

  public void progress() {
    switch (status) {
      case ONGOING:
        route();
        break;
      case ABORTED:
        rollback();     // was aborted sometime between prev hop and now
        break;
      case DONE:        // DONE means just ignore
      case READY:
        assert false;   // shouldn't happen
        break;
    }
  }

  public void sendAbort() {
    switch (status) {
      case ONGOING:
        status = ABORTED;   // tr in on its way, will be rolled back when it reaches its next hop
        break;
      case ABORTED:
        assert false;       // shouldn't happen
      case DONE:
        break;
      case READY:
        status = ABORTED;
        rollback();         // tr is at destination already, rollback immediately
        break;
    }
  }

  public void sendExecute() {
    switch (status) {
      case ONGOING:
        status = ABORTED;     // if ongoing, will be rolled back when it reached the next hop
        break;
      case ABORTED:
        assert false;         // shouldn't happen
      case DONE:
        break;
      case READY:
        execute();
        break;
    }
  }

  private void route() {
    if (isDestination()) {
      status = READY;
      parent.anotherSuccess(time);
      return;
    }

    parent.rPay.remember(path[i], path[i + 1]);                // remember initial weight (originalAll)
    boolean ok = parent.rPay.lock(path[i], path[i + 1], val);  // lock collateral
    if(ok) {
      i++;                            // proceed to next node on path
      time += randLat();              // time passes (after everything else)
    } else {
      parent.anotherFail(time);       // let parent know
      status = ABORTED;
      rollback();                     // rollback immediately
    }
  }

  public static double randLat() {
    return 50d + new Random().nextInt(101);
  }

  private void execute() {
    assert (status == READY);
    assert (isDestination());
    unlock(true);
    status = DONE;
  }

  private void rollback() {
    assert (status == ABORTED);
    unlock(false);
    status = DONE;
  }

  private void unlock(boolean successful) {
    parent.decrementOngoing(time);    // one less ongoing tr, record current time, NOT unlock time
    double time = this.time;
    for (int j = i; j > 0; j--) {     // from current to src
      time += randLat();              // even the first unlock after latency (boomerang does it)
      ScheduledUnlock lock = new ScheduledUnlock(
          new Edge(path[j - 1], path[j]), time, successful, val);
      parent.rPay.qLocks.add(lock);
    }
  }

  public int getSrc(){
    return path[0];
  }

  private boolean isDestination(){
    return i + 1 == path.length;
  }

  @Override
  public int compareTo(BoomTr o) {
    return (int) Math.signum(this.time - o.time);
  }
}

