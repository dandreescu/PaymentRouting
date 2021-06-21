package paymentrouting.route.concurrency;

import static paymentrouting.route.concurrency.RouteBoomerang.BoomType.REDUNDANT_RETRY;
import static paymentrouting.route.concurrency.Status.ABORTED;
import static paymentrouting.route.concurrency.Status.DONE_NEG;
import static paymentrouting.route.concurrency.Status.DONE_POS;
import static paymentrouting.route.concurrency.Status.NOT_STARTED;
import static paymentrouting.route.concurrency.Status.ONGOING;
import static paymentrouting.route.concurrency.Status.READY_NEG;
import static paymentrouting.route.concurrency.Status.READY_POS;

import java.util.Arrays;
import java.util.stream.Stream;

public class BoomPayment {
  double sendingTime;
  BoomTr[] peers;
  int necessary;
  double amt;
  RouteBoomerang rPay;
  boolean done;

  public BoomPayment(int v, BoomTr[] peers, double sendingTime, double amt, RouteBoomerang rPay) {
    this.necessary = v;                       // number of transactions that add up to the total (v)
    this.peers = peers;                       // array of the tiny transactions after splitting
    this.sendingTime = sendingTime;           // needed for ttc
    this.amt = amt;
    this.rPay = rPay;
  }

  public void check(double time) {
    if (done) return;
    assert (filter(READY_NEG).count() < 2);
    filter(READY_NEG).forEach(tr -> {
      tr.rollback(time);
      maybeRetry(time);
    });

    if (!(filter(READY_POS).count() < necessary
        && filter(ONGOING).count() > 0
        && filter(READY_POS, ONGOING, NOT_STARTED).count() >= necessary)) {
      finalizeAMP(time);
    }
  }

  private void finalizeAMP(double time) {
    done = true;
//    System.out.println("Finalize: " + time + "\tsucc: " + (filter(READY_POS).count() == necessary));
    filter(ONGOING).forEach(BoomTr::abort);

    if (filter(READY_POS).count() == necessary) {   //success
      filter(READY_POS).forEach(tr -> tr.execute(time));
      rPay.incSucc(time - sendingTime, amt);     // whole payment successful
//      Arrays.stream(peers).forEach(tr -> rPay.logTime(tr.getSrc(), "SUCC: "+time + " "+tr.status+" "+this.toString().split("@")[1]));
    } else {
      filter(READY_POS).forEach(tr -> tr.rollback(time));
//      Arrays.stream(peers).forEach(tr -> rPay.logTime(tr.getSrc(), "FAIL: "+time + " "+tr.status+" "+this.toString().split("@")[1]));
    }
  }

  private void maybeRetry(double timeNow) {
    if (filter(NOT_STARTED).count() > 0) {
//      System.out.println("Retry: " + timeNow);                   // if we can still retry any
      BoomTr newTr = filter(NOT_STARTED).findFirst().get();      // next available tiny transaction
      newTr.start(timeNow);                           // starts now
      rPay.trQueue.add(newTr);
    }
  }

  private Stream<BoomTr> filter (Status... statuses) {
    return Arrays.stream(peers).filter(bTr -> Arrays.asList(statuses).contains(bTr.status));
  }

//  public void print(double time) {
//    System.out.println(amt + ": " + time + ": "
//            + "O=" + filter(ONGOING).count()
//            + "; R=" + filter(READY).count()
//            + "; A=" + filter(ABORTED).count()
//            + "; N=" + filter(NOT_STARTED).count()
//            + "; DN=" + filter(DONE_NEG).count()
//            + "; DP=" + filter(DONE_POS).count()
//        );
//    System.out.println();
//  }
}
