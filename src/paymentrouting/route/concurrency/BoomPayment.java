package paymentrouting.route.concurrency;

import static paymentrouting.route.concurrency.RouteBoomerang.BoomType.REDUNDANT;
import static paymentrouting.route.concurrency.RouteBoomerang.BoomType.REDUNDANT_RETRY;
import static paymentrouting.route.concurrency.RouteBoomerang.BoomType.RETRY;

import java.util.Random;
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

  public BoomPayment(int necessary, BoomTr[] peers, BoomType type, double sendingTime, double amt, RouteBoomerang rPay) {
    this.necessary = necessary; // number of transactions that add up to the total (v)
    this.peers = peers;         // array of the tiny transactions after splitting
    this.sendingTime = sendingTime; // needed for ttc
    if(type == REDUNDANT_RETRY)
      this.retried = 10;            // v + 10 already sent (consider 10 as already retried)
    else if(type == RETRY)
      this.retried = 0;             // v already sent
    else this.retried = peers.length - necessary; // v + u already sent (consider u as already retried)
    this.amt = amt;
    this.rPay = rPay;
  }

  public void anotherSuccess(double timeNow) {
    successful++;   // number of successful tiny payments
    if (successful == necessary) {
      rPay.incSucc(timeNow - sendingTime, amt);  // whole payment successful
      for (BoomTr p : peers)
        p.cancel(true);         // stop all (outstanding)
      rPay.startBoomTr(peers[0].getSrc(), timeNow+150); // and tell the node to start a new transaction from the backlog
    }
  }

  public void anotherFail(double timeNow) {
    failed++;
    if (retried + necessary < peers.length) {   // if we can still retry some
      BoomTr newTr = peers[necessary + retried];  // next available tiny transaction
      newTr.time = timeNow;   // starts now
      rPay.trQueue.add(newTr);
      retried++;    // use another retry
    }
    if (failed + necessary > peers.length) {  // if there is no chance of success
      for (BoomTr p : peers) {
        p.cancel(false);         // stop all (outstanding)
      }
      rPay.startBoomTr(peers[0].getSrc(), timeNow +150); // and tell the node to start a new transaction from the backlog
    }
  }
  private double randLat() { // todo graph transformation maybe? (boomerang does have a different lat each time though)
    return 50d + new Random().nextInt(101);
  }

}
