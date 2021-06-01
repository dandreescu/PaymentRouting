package paymentrouting.route.concurrency;

import static paymentrouting.route.concurrency.RouteBoomerang.BoomType.REDUNDANT_RETRY;
import static paymentrouting.route.concurrency.RouteBoomerang.BoomType.RETRY;

import paymentrouting.route.concurrency.RouteBoomerang.BoomType;

public class BoomPayment {
  double sendingTime;
  int retried;
  BoomTr[] peers;
  int failed;
  int successful;
  int necessary;
  double amt;
  RouteBoomerang rPay;
  private double lastOngoingTime;
  int ongoing;

  public BoomPayment(int necessary, BoomTr[] peers, BoomType type, double sendingTime, double amt, RouteBoomerang rPay) {
    this.necessary = necessary;                                 // number of transactions that add up to the total (v)
    this.peers = peers;                                         // array of the tiny transactions after splitting
    this.sendingTime = sendingTime;                             // needed for ttc

    if(type == REDUNDANT_RETRY)
      this.retried = Math.min(10, peers.length - necessary);    // v + 10 already sent (consider 10 as already retried)
    else if(type == RETRY)
      this.retried = 0;                                 // v already sent
    else
      this.retried = peers.length - necessary;       // v + u already sent (consider u as already retried)

    this.amt = amt;
    this.rPay = rPay;
    this.ongoing = necessary + retried;
  }

  public void anotherSuccess(double timeNow) {
    successful++;                                         // number of successful tiny payments
    if (successful == necessary) {
      rPay.incSucc(timeNow - sendingTime, amt);       // whole payment successful
      for (int i = 0; i < necessary + retried; i++) {
        BoomTr p = peers[i];
        p.sendExecute();
      }
    }
  }

  public void anotherFail(double timeNow) {
    failed++;
    if (failed + necessary > peers.length) {            // if there is no chance of success
      for (int i = 0; i < necessary + retried; i++) {
        BoomTr p = peers[i];
        p.sendAbort();
      }
    } else if (retried + necessary < peers.length) {    // if we can still retry some
        BoomTr newTr = peers[necessary + retried];      // next available tiny transaction
        newTr.time = timeNow;                           // starts now
        rPay.trQueue.add(newTr);
        retried++;                                      // use another retry
        ongoing++;
      }
    }


  public void decrementOngoing(double time) {
    this.lastOngoingTime = Math.max(lastOngoingTime, time);    // time when the last ongoing tr was cancelled
    ongoing--;
    if (ongoing == 0)
      rPay.startBoomTr(peers[0].getSrc(), lastOngoingTime);     // start new payment when no more ongoing
  }
}
