package paymentrouting.route.concurrency;

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
  boolean startedNext;
  double sendingTime;
  BoomTr[] peers;
  int necessary;
  double amt;
  RouteBoomerang rPay;
  double lastUnlockStartedTime;
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
    filter(READY_NEG).forEach(tr -> {
      tr.rollback(time);
//      maybeRetry(time);
    });
    maybeRetry(time);

    if (!(filter(READY_POS).count() < necessary
        && filter(ONGOING).count() > 0
        && filter(READY_POS, ONGOING, NOT_STARTED).count() >= necessary))

      finalizeAMP(time);
  }

  public void finalizeAMP(double time) {
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
    done = true;
    updateLastUnlockStartTime(time);
  }

  private void maybeRetry(double timeNow) {
    while (filter(NOT_STARTED).count() > 0
        && filter(ONGOING, DONE_POS, READY_POS//, NOT_STARTED
        , ABORTED, READY_NEG      //todo WHY?
    ).count() < necessary) {
//      System.out.println("Retry: " + timeNow);                   // if we can still retry any
      BoomTr newTr = filter(NOT_STARTED).findFirst().get();      // next available tiny transaction
      newTr.start(timeNow);                           // starts now
      rPay.trQueue.add(newTr);
    }
  }

  public void updateLastUnlockStartTime(double timeNow) {
    if(startedNext) return;
//    System.out.println("Last Update: " + timeNow);
    this.lastUnlockStartedTime =
        Math.max(lastUnlockStartedTime, timeNow);    // time when the last ongoing tr was cancelled
    // maybe start next
    if (filter(ONGOING, ABORTED, READY_POS, READY_NEG).count() == 0) {
//      Arrays.stream(peers).forEach(tr -> rPay.logTime(tr.getSrc(), "START_NEXT: "+timeNow + " "+tr.status+" "+this.toString().split("@")[1]));
      rPay.startBoomTr(peers[0].getSrc(),
          lastUnlockStartedTime);     // start new payment when no more ongoing
      startedNext = true;
    }
  }

  private Stream<BoomTr> filter (Status... statuses) {
    return Arrays.stream(peers).filter(bTr -> Arrays.asList(statuses).contains(bTr.status));
  }

//
//  public void anotherSuccess(double timeNow) {
//    // check success
//    maybeRetry(timeNow);
//    if (filter(READY).count() == necessary) {
////      System.out.println(amt+": big succ " + timeNow);
//      succ = true;
//      rPay.incSucc(timeNow - sendingTime, amt);     // whole payment successful
//      filter(READY).forEach(boomTr -> boomTr.execute(timeNow));
//      filter(ONGOING).forEach(BoomTr::abort);
//      return;
//    }
//  }
//
//  public void anotherFail(double timeNow) {
//
//    maybeRetry(timeNow);
//    // check fail
//    if (filter(ONGOING, READY, NOT_STARTED).count() < necessary) {    // if there is no chance of success
//      //abort ABORTED??
////      System.out.println(amt+": big fail " + timeNow);
//      filter(READY).forEach(boomTr -> boomTr.rollback(timeNow));
//      filter(ONGOING).forEach(BoomTr::abort);
//      fail = true;
//      return;
//    }
//  }


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
