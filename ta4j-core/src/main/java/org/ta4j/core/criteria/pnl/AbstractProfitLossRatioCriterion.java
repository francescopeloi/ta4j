package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Base class for profit/loss ratio criteria.
 * <p>
 * Calculates the ratio of the average profit over the average loss.
 */
public abstract class AbstractProfitLossRatioCriterion extends AbstractAnalysisCriterion {

    private final AnalysisCriterion averageProfitCriterion;
    private final AnalysisCriterion averageLossCriterion;

    protected AbstractProfitLossRatioCriterion(AnalysisCriterion averageProfitCriterion,
            AnalysisCriterion averageLossCriterion) {
        this.averageProfitCriterion = averageProfitCriterion;
        this.averageLossCriterion = averageLossCriterion;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var avgProfit = averageProfitCriterion.calculate(series, position);
        var avgLoss = averageLossCriterion.calculate(series, position);
        return calculateRatio(series, avgProfit, avgLoss);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var avgProfit = averageProfitCriterion.calculate(series, tradingRecord);
        var avgLoss = averageLossCriterion.calculate(series, tradingRecord);
        return calculateRatio(series, avgProfit, avgLoss);
    }

    private Num calculateRatio(BarSeries series, Num avgProfit, Num avgLoss) {
        var numFactory = series.numFactory();
        if (avgProfit.isZero()) {
            return numFactory.zero();
        }
        if (avgLoss.isZero()) {
            return numFactory.one();
        }
        return avgProfit.dividedBy(avgLoss).abs();
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
