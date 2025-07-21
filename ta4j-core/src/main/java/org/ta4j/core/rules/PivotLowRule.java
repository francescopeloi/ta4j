package org.ta4j.core.rules;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.RecentSwingLowIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Rule which is satisfied when a pivot low is formed.
 *
 * <p>
 * A pivot low is defined as a bar whose low price is lower than the lows
 * of {@code leftBars} bars to its left and {@code rightBars} bars to its
 * right. The rule becomes satisfied {@code rightBars} bars after the pivot
 * bar itself.
 */
public class PivotLowRule extends AbstractRule {

    private final Indicator<Num> lowPrice;
    private final RecentSwingLowIndicator swingLow;
    private final PreviousValueIndicator previousSwingLow;
    private final int leftBars;
    private final int rightBars;

    /**
     * Constructor using the bar series low price.
     *
     * @param series   the bar series
     * @param leftBars number of bars to the left of the pivot
     * @param rightBars number of bars to the right of the pivot
     */
    public PivotLowRule(BarSeries series, int leftBars, int rightBars) {
        this(new LowPriceIndicator(series), leftBars, rightBars);
    }

    /**
     * Constructor with custom indicator.
     *
     * @param lowPrice  the low price indicator
     * @param leftBars  number of bars to the left of the pivot
     * @param rightBars number of bars to the right of the pivot
     */
    public PivotLowRule(Indicator<Num> lowPrice, int leftBars, int rightBars) {
        this.lowPrice = lowPrice;
        this.leftBars = leftBars;
        this.rightBars = rightBars;
        this.swingLow = new RecentSwingLowIndicator(lowPrice, leftBars, rightBars, 0);
        this.previousSwingLow = new PreviousValueIndicator(swingLow);
    }

    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {
        int pivotIndex = index - rightBars;
        if (pivotIndex < 0 || index >= lowPrice.getBarSeries().getBarCount()) {
            traceIsSatisfied(index, false);
            return false;
        }

        Num pivotValue = lowPrice.getValue(pivotIndex);
        Num currentSwing = swingLow.getValue(index);
        Num previousSwing = previousSwingLow.getValue(index);

        boolean satisfied = !currentSwing.isNaN()
                && !pivotValue.isNaN()
                && currentSwing.isEqual(pivotValue)
                && (previousSwing.isNaN() || !previousSwing.isEqual(pivotValue));

        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
