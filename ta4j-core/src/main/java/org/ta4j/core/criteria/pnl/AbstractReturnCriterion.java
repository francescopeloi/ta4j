package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Base class for return based criteria.
 * <p>
 * Handles calculation of the aggregated return across positions and the
 * optional inclusion of the base percentage.
 */
public abstract class AbstractReturnCriterion extends AbstractAnalysisCriterion {

    /**
     * If {@code true} the base percentage of {@code 1} (equivalent to 100%) is
     * included in the returned value.
     */
    protected final boolean addBase;

    /**
     * Constructor with {@link #addBase} set to {@code true}.
     */
    protected AbstractReturnCriterion() {
        this(true);
    }

    /**
     * Constructor.
     *
     * @param addBase whether to include the base percentage
     */
    protected AbstractReturnCriterion(boolean addBase) {
        this.addBase = addBase;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position.isClosed()) {
            return calculateReturn(series, position);
        }
        if (addBase) {
            return series.numFactory().one();
        }
        return series.numFactory().zero();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var one = series.numFactory().one();
        var result = tradingRecord.getPositions()
                .stream()
                .map(p -> calculate(series, p))
                .reduce(one, Num::multipliedBy);
        if (addBase) {
            return result;
        }
        return result.minus(one);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    /**
     * Calculates the return of the given closed position including the base.
     *
     * @param series   the bar series
     * @param position the closed position
     * @return the return of the position including the base
     */
    protected abstract Num calculateReturn(BarSeries series, Position position);
}
