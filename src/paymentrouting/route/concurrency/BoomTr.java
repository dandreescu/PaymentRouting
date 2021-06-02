package paymentrouting.route.concurrency;


import static paymentrouting.route.concurrency.Status.NOT_STARTED;
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

  public void setReady() {
    assert (status == ONGOING);
    this.status = READY;
  }

  public void setDone() {
    assert (status == ABORTED || status == READY);
    this.status = DONE;
  }

  public void progress() {
    parent.rPay.logPayment(this, "B_prog");
    switch (status) {
      case ONGOING:
        route();
        break;
      case ABORTED:
        rollback();     // was aborted sometime between prev hop and now
        break;
      case NOT_STARTED:
      case DONE:        // DONE means just ignore
      case READY:
        assert false;   // shouldn't happen
        break;
    }

    parent.rPay.logPayment(this, "A_prog");
  }

  public void execute() {
    parent.rPay.logPayment(this, "B_exec");
    assert (status == READY && isDestination());
    unlock(true);
    parent.rPay.logPayment(this, "A_exec");
  }

  public void rollback() {
    parent.rPay.logPayment(this, "B_roll");
    assert (status == ABORTED || status == READY);
    unlock(false);
    parent.rPay.logPayment(this, "A_roll");
  }

  private void route() {
    if (isDestination()) {
      setReady();
      parent.anotherSuccess(time);
      return;
    }

    parent.rPay.remember(path[i], path[i + 1]);                // remember initial weight (originalAll)
    boolean ok = parent.rPay.lock(path[i], path[i + 1], val);  // lock collateral
    if(ok) {
      i++;                            // proceed to next node on path
      time += randLat();              // time passes (after everything else)
    } else {
      abort();
      parent.anotherFail(time);
    }
  }

  private void unlock(boolean successful) {
    parent.updateLastUnlockStartTime(time);    // one less ongoing tr, record current time, NOT unlock time
    double time = this.time;
    for (int j = i; j > 0; j--) {     // from current to src
      time += randLat();              // even the first unlock after latency (boomerang does it)
      ScheduledUnlock lock = new ScheduledUnlock(
          new Edge(path[j - 1], path[j]), time, successful, val);
      parent.rPay.qLocks.add(lock);
    }
    setDone();
  }

  public int getSrc(){
    return path[0];
  }

  private boolean isDestination(){
    return i + 1 == path.length;
  }

  public static double randLat() {
    return 50d + new Random().nextInt(101);
  }

  @Override
  public int compareTo(BoomTr o) {
    return (int) Math.signum(this.time - o.time);
  }
}

