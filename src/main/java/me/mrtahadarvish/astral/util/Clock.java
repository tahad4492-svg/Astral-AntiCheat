package me.mrtahadarvish.astral.util;

import java.util.function.LongSupplier;

/** Time source used by timing based checks (replaceable in tests). */
public final class Clock {

    private static LongSupplier nanos = System::nanoTime;

    private Clock() {
    }

    public static long nanos() {
        return nanos.getAsLong();
    }

    public static long millis() {
        return nanos.getAsLong() / 1_000_000L;
    }

    /** Only for tests: replace the time source. */
    public static void use(LongSupplier source) {
        nanos = source;
    }
}
