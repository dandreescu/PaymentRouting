package paymentrouting.route.concurrency;


import gtna.graph.Edge;
import java.util.Random;

public class BoomTr implements Comparable<BoomTr> {

  double time;
  double val;
  int i;
  int[] path;
  boolean cancelled;
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
      this.cancelled = false;
  }

  public void progress() {
    double lat = randLat();
    time += lat;                  // time passes
    if (i + 1 == path.length) {    // i is current node along path
      parent.anotherSuccess(time);  // parent controls sibling transactions
      return;
    }

    parent.rPay.remember(path[i], path[i + 1]);                // remember initial weight (originalAll)
    boolean ok = parent.rPay.lock(path[i], path[i + 1], val);  // lock collateral
    if (ok)
      i++;  // proceed to next node on path
    else
      parent.anotherFail(time);
  }

  private double randLat() { // todo graph transformation maybe? (boomerang does have a different lat each time though)
    return 50d + new Random().nextInt(101);
  }

  public void cancel(boolean successful) {
    cancelled = true;   // will be ignored when taken out of the queue
    double time = this.time;
    for (int j = i; j > 0; j--) { // from current to src
      time += randLat();
      ScheduledUnlock lock = new ScheduledUnlock(
          new Edge(path[j - 1], path[j]), time,
          successful && i + 1 == path.length, val);
      parent.rPay.qLocks.add(lock);
    }
  }

  public int getSrc(){
    return path[0];
  }

  @Override
  public int compareTo(BoomTr o) {
    return (int) Math.signum(this.time - o.time);
  }
}

