package me.mrtahadarvish.astral.check;

import me.mrtahadarvish.astral.data.PlayerData;

/**
 * A check that runs once every client tick (Paper's ClientTickEndEvent), after Astral updated
 * the movement data of the player.
 */
public interface TickCheck {

    void onTick(PlayerData data);
}
