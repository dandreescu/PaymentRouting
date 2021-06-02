package paymentrouting.route.concurrency;

import static paymentrouting.route.concurrency.Status.NOT_STARTED;
import static paymentrouting.route.concurrency.Status.ONGOING;
import static paymentrouting.route.concurrency.Status.READY;

import java.util.Arrays;
import java.util.stream.Stream;
import paymentrouting.route.concurrency.RouteBoomerang.BoomType;

public class BoomPayment {
  double sendingTime;
  BoomTr[] peers;
  int necessary;
  double amt;
  RouteBoomerang rPay;
  double lastUnlockStartedTime;
   boolean succ;

  public BoomPayment(int v, BoomTr[] peers, double sendingTime, double amt, RouteBoomerang rPay) {
    this.necessary = v;                       // number of transactions that add up to the total (v)
    this.peers = peers;                       // array of the tiny transactions after splitting
    this.sendingTime = sendingTime;           // needed for ttc
    this.amt = amt;
    this.rPay = rPay;
  }

  public void anotherSuccess(double timeNow) {
    // check success
    if (filter(READY).count() == necessary) {
      succ = true;
      rPay.incSucc(timeNow - sendingTime, amt);     // whole payment successful
      filter(READY).forEach(BoomTr::execute);
      filter(ONGOING).forEach(BoomTr::abort);
    }
  }

  public void anotherFail(double timeNow) {
    // check fail
    if (filter(ONGOING, READY, NOT_STARTED).count() < necessary) {    // if there is no chance of success
      filter(READY).forEach(BoomTr::rollback);
      filter(ONGOING).forEach(BoomTr::abort);
      return;
    }
    // maybe retry
    if (filter(NOT_STARTED).count() > 0) {                      // if we can still retry any
      BoomTr newTr = filter(NOT_STARTED).findFirst().get();      // next available tiny transaction
      newTr.start(timeNow);                           // starts now
      rPay.trQueue.add(newTr);
    }
  }

  public void updateLastUnlockStartTime(double timeNow) {
    this.lastUnlockStartedTime = Math.max(lastUnlockStartedTime, timeNow);    // time when the last ongoing tr was cancelled
      // maybe start next
    if (filter(ONGOING).count() == 0)
      rPay.startBoomTr(peers[0].getSrc(), lastUnlockStartedTime);     // start new payment when no more ongoing
  }

    private Stream<BoomTr> filter (Status... statuses) {
      return Arrays.stream(peers).filter(bTr -> Arrays.asList(statuses).contains(bTr.status));
    }
}
