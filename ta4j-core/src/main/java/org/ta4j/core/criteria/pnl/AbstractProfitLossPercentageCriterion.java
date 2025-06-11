package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Base class for profit/loss percentage criteria.
 * <p>
 * Calculates the aggregated profit or loss in percent relative to the entry
 * price of each position.
 */
public abstract class AbstractProfitLossPercentageCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        var numFactory = series.numFactory();
        if (position.isClosed()) {
            var entryValue = position.getEntry().getValue();
            if (entryValue.isZero()) {
                return numFactory.zero();
            }
            return profit(position).dividedBy(entryValue).multipliedBy(numFactory.hundred());
        }
        return numFactory.zero();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();

        var totalProfit = tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(this::profit)
                .reduce(zero, Num::plus);

        var totalEntry = tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(Position::getEntry)
                .map(Trade::getValue)
                .reduce(zero, Num::plus);

        if (totalEntry.isZero()) {
            return zero;
        }
        return totalProfit.dividedBy(totalEntry).multipliedBy(numFactory.hundred());
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    /**
     * Returns the profit or loss for the given position.
     *
     * @param position the position
     * @return the profit or loss
     */
    protected abstract Num profit(Position position);
}
