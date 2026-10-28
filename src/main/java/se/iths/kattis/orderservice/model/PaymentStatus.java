package se.iths.kattis.orderservice.model;

/**
 * Orderns betalningsstatus
 */
public enum PaymentStatus {
    /**
     * Ordern är skapad och väntar på betalning
     */
    PENDING,
    /**
     * Stripe har bekräftat betalningen
     */
    PAID,
    /**
     * Checkout-sessionen gick ut utan betalning och lagret har återställts
     */
    EXPIRED
}
