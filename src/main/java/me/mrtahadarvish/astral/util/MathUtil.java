package me.mrtahadarvish.astral.util;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Locale;

public final class MathUtil {

    /** Smallest possible rotation step of a real mouse (0% sensitivity), in degrees. */
    public static final double MIN_SENSITIVITY_STEP = 0.0096;

    private MathUtil() {
    }

    public static float wrapDegrees(float value) {
        value %= 360.0F;
        if (value >= 180.0F) {
            value -= 360.0F;
        }
        if (value < -180.0F) {
            value += 360.0F;
        }
        return value;
    }

    /**
     * Greatest common divisor of two rotation deltas with a small tolerance, so float rounding
     * errors do not break it. Legit mouse rotations always share a divisor that comes from
     * the mouse sensitivity.
     */
    public static double gcd(double a, double b) {
        a = Math.abs(a);
        b = Math.abs(b);
        if (a < b) {
            double t = a;
            a = b;
            b = t;
        }
        int guard = 0;
        while (b > 0.0015 && guard++ < 64) {
            double t = a % b;
            a = b;
            b = t;
        }
        return a;
    }

    public static double mean(Collection<? extends Number> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (Number n : values) {
            sum += n.doubleValue();
        }
        return sum / values.size();
    }

    public static double deviation(Collection<? extends Number> values) {
        if (values.size() < 2) {
            return 0.0;
        }
        double mean = mean(values);
        double sum = 0.0;
        for (Number n : values) {
            double d = n.doubleValue() - mean;
            sum += d * d;
        }
        return Math.sqrt(sum / (values.size() - 1));
    }

    /** Distance from a point to the closest point of a box (0 when inside). */
    public static double distanceToBox(Vector point, BoundingBox box) {
        double dx = Math.max(Math.max(box.getMinX() - point.getX(), 0.0), point.getX() - box.getMaxX());
        double dy = Math.max(Math.max(box.getMinY() - point.getY(), 0.0), point.getY() - box.getMaxY());
        double dz = Math.max(Math.max(box.getMinZ() - point.getZ(), 0.0), point.getZ() - box.getMaxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static double horizontalDistanceToBox(Vector point, BoundingBox box) {
        double dx = Math.max(Math.max(box.getMinX() - point.getX(), 0.0), point.getX() - box.getMaxX());
        double dz = Math.max(Math.max(box.getMinZ() - point.getZ(), 0.0), point.getZ() - box.getMaxZ());
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Look direction for a yaw / pitch pair (same math as the vanilla client). */
    public static Vector direction(float yaw, float pitch) {
        double rotX = Math.toRadians(yaw);
        double rotY = Math.toRadians(pitch);
        double xz = Math.cos(rotY);
        return new Vector(-xz * Math.sin(rotX), -Math.sin(rotY), xz * Math.cos(rotX));
    }

    /** Angle in degrees between two vectors. */
    public static double angle(Vector a, Vector b) {
        double la = a.length();
        double lb = b.length();
        if (la == 0.0 || lb == 0.0) {
            return 0.0;
        }
        double cos = a.dot(b) / (la * lb);
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return Math.toDegrees(Math.acos(cos));
    }

    public static float lerpAngle(float from, float to, float t) {
        return from + wrapDegrees(to - from) * t;
    }

    public static String format(double value) {
        return format(value, 3);
    }

    public static String format(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
