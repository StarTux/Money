package com.cavetale.money;

import java.util.ArrayList;
import java.util.List;

/**
 * Store some cached data for online players.
 *
 * The money should always correspond to the current money in the bank
 * account.
 *
 * The other fields are used for the Sidebar:
 * - If showProgress is true, show displayMoney and inch it closer to
 *   the actual money, with a sound effect
 * - Else if showTimed is true, show money until showUntil expires
 * - In any case, log all the logs and remove them if too old
 */
public final class Cached {
    protected double money;
    protected double displayMoney;
    protected double distance;
    protected boolean showProgress;
    protected boolean showTimed;
    protected long showUntil;
    protected final List<SQLLog> logs = new ArrayList<>();
}
