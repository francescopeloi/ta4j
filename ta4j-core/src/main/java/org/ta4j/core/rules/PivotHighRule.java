package org.ta4j.core.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.RecentSwingHighIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Rule which is satisfied when a pivot high is formed.
 *
 * <p>
 * A pivot high is defined as a bar whose high price is greater than the
 * highs of {@code leftBars} bars to its left and {@code rightBars} bars to
 * its right. Because the confirmation requires future data, the rule becomes
 * satisfied {@code rightBars} bars after the pivot bar itself.
 */
public class PivotHighRule extends AbstractRule {

    private final Indicator<Num> highPrice;
    private final RecentSwingHighIndicator swingHigh;
    private final PreviousValueIndicator previousSwingHigh;
    private final int leftBars;
    private final int rightBars;

    /**
     * Constructor using the bar series high price.
     *
     * @param series   the bar series
     * @param leftBars number of bars to the left of the pivot
     * @param rightBars number of bars to the right of the pivot
     */
    public PivotHighRule(BarSeries series, int leftBars, int rightBars) {
        this(new HighPriceIndicator(series), leftBars, rightBars);
    }

    /**
     * Constructor with custom indicator.
     *
     * @param highPrice the high price indicator
     * @param leftBars  number of bars to the left of the pivot
     * @param rightBars number of bars to the right of the pivot
     */
    public PivotHighRule(Indicator<Num> highPrice, int leftBars, int rightBars) {
        this.highPrice = highPrice;
        this.leftBars = leftBars;
        this.rightBars = rightBars;
        this.swingHigh = new RecentSwingHighIndicator(highPrice, leftBars, rightBars, 0);
        this.previousSwingHigh = new PreviousValueIndicator(swingHigh);
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        int pivotIndex = index - rightBars;
        if (pivotIndex < 0 || index >= highPrice.getBarSeries().getBarCount()) {
            traceIsSatisfied(index, false);
            return false;
        }

        Num pivotValue = highPrice.getValue(pivotIndex);
        Num currentSwing = swingHigh.getValue(index);
        Num previousSwing = previousSwingHigh.getValue(index);

        boolean satisfied = !currentSwing.isNaN()
                && !pivotValue.isNaN()
                && currentSwing.isEqual(pivotValue)
                && (previousSwing.isNaN() || !previousSwing.isEqual(pivotValue));

        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
