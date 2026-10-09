package me.mrtahadarvish.astral.data;

import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Ring buffer with the bounding box of a player for the last server ticks. Used for lag
 * compensation: an attacker sees the target where it was "ping" milliseconds ago.
 */
public final class PositionHistory {

    private final int size;
    private final int[] ticks;
    private final double[][] boxes;
    private int head;
    private int count;

    public PositionHistory(int size) {
        this.size = size;
        this.ticks = new int[size];
        this.boxes = new double[size][6];
    }

    public void add(int tick, BoundingBox box) {
        double[] b = boxes[head];
        b[0] = box.getMinX();
        b[1] = box.getMinY();
        b[2] = box.getMinZ();
        b[3] = box.getMaxX();
        b[4] = box.getMaxY();
        b[5] = box.getMaxZ();
        ticks[head] = tick;
        head = (head + 1) % size;
        if (count < size) {
            count++;
        }
    }

    /** Boxes recorded between {@code fromTick} and {@code toTick} (inclusive). */
    public List<BoundingBox> between(int fromTick, int toTick) {
        List<BoundingBox> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int index = (head - 1 - i + size) % size;
            int tick = ticks[index];
            if (tick < fromTick || tick > toTick) {
                continue;
            }
            double[] b = boxes[index];
            out.add(new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]));
        }
        return out;
    }

    public void clear() {
        count = 0;
        head = 0;
    }
}
