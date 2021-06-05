package paymentrouting.route.concurrency;

public enum Status {
  ONGOING,
  DONE_NEG, DONE_POS,
  READY_POS, READY_NEG,
  ABORTED,
  NOT_STARTED
}
