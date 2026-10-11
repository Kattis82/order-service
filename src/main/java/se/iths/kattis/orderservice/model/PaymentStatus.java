package se.iths.kattis.orderservice.model;

// hålla reda på om betalningen väntar eller är klar
// betalningsstatusen kan bara vara PENDING eller PAID
public enum PaymentStatus {
    PENDING,
    PAID
}
