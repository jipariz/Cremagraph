package app.onlyshots.home

/** A wide picker's machines column at most: a phone column. */
const val MACHINES_WIDTH_DP = 480f

/** The share of a medium window's width the machines take; the shots the rest. */
const val MACHINES_SHARE = 0.45f

/**
 * How wide a wide picker's machines column is, out of the [rowWidthDp] the machines and the shots share: a phone
 * column, or on a medium window its share.
 */
fun machinesWidthDp(rowWidthDp: Float): Float = minOf(MACHINES_WIDTH_DP, rowWidthDp * MACHINES_SHARE)
