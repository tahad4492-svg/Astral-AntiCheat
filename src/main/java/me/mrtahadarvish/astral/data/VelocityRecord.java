package me.mrtahadarvish.astral.data;

/** Knockback / velocity the server sent to a player, waiting to be confirmed by movement. */
public final class VelocityRecord {

    public final double x;
    public final double y;
    public final double z;
    public final double horizontal;
    /** Client tick counter when the velocity was sent. */
    public final int clientTick;
    /** Amount of client ticks the player has to take the velocity. */
    public final int window;
    public final boolean checkVertical;
    public final boolean checkHorizontal;

    public boolean verticalTaken;
    public boolean horizontalTaken;
    public double maxDeltaY;
    public double maxDeltaXZ;
    public boolean invalid;
    public boolean horizontalInvalid;
    public boolean evaluated;

    public VelocityRecord(double x, double y, double z, int clientTick, int window, boolean checkVertical, boolean checkHorizontal) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.horizontal = Math.hypot(x, z);
        this.clientTick = clientTick;
        this.window = window;
        this.checkVertical = checkVertical;
        this.checkHorizontal = checkHorizontal;
    }
}
