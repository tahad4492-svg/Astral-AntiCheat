package me.mrtahadarvish.astral.data;

/**
 * A small "suspicion" counter. Checks add to it when something looks wrong and remove from it
 * when everything is fine, so one strange tick never flags a player on its own.
 */
public final class Buffer {

    private double value;

    public double increase(double amount) {
        value += amount;
        return value;
    }

    public double increase() {
        return increase(1.0);
    }

    public double decrease(double amount) {
        value = Math.max(0.0, value - amount);
        return value;
    }

    public double get() {
        return value;
    }

    public void set(double value) {
        this.value = value;
    }

    public void reset() {
        value = 0.0;
    }
}
