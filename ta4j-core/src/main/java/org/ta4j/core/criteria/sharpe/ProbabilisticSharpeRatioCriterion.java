package org.ta4j.core.criteria.sharpe;

import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.Locale;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.sharpe.model.Annualization;
import org.ta4j.core.criteria.sharpe.model.Sampling;
import org.ta4j.core.num.Num;

public class ProbabilisticSharpeRatioCriterion extends AbstractAnalysisCriterion {

    private static final WeekFields ISO_WEEK_FIELDS = WeekFields.of(Locale.ROOT);

    private final Num annualRiskFreeRate;
    private final Sampling sampling;
    private final ZoneId groupingZoneId;
    private final Num sharpeThreshold;
    private final AnalysisCriterion sharpeRatio;

    public ProbabilisticSharpeRatioCriterion(
            Num annualRiskFreeRate,
            Sampling sampling,
            Num sharpeThreshold,
            ZoneId groupingZoneId
    ) {
        this.annualRiskFreeRate = annualRiskFreeRate;
        this.sampling = sampling;
        this.groupingZoneId = groupingZoneId;
        this.sharpeThreshold = sharpeThreshold;
        this.sharpeRatio = new SharpeRatioCriterion(annualRiskFreeRate, sampling, Annualization.PERIOD, groupingZoneId);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var zero = series.numFactory().zero();
        if (tradingRecord == null) {
            return zero;
        }
        var hasClosedPositions = tradingRecord.getPositions().stream().anyMatch(Position::isClosed);
        if (!hasClosedPositions) {
            return zero;
        }

        var sharpe = sharpeRatio.calculate(series, tradingRecord).doubleValue();

        var cashFlow = new CashFlow(series, tradingRecord);
        var start = series.getBeginIndex() + 1;
        var end = series.getEndIndex();
        var anchorIndex = series.getBeginIndex();

        var excessReturns = Sampling.indexPairs(sampling, groupingZoneId, series, anchorIndex, start, end)
                .mapToDouble(pair -> excessReturn(series, cashFlow, pair.previousIndex(), pair.currentIndex()).doubleValue())
                .toArray();

        if (excessReturns.length < 2) {
            return zero;
        }

        var moments = Moments.from(excessReturns); // TODO: implement (mean, skewness, kurtosis)
        var t = excessReturns.length;

        var numerator = (sharpe - sharpeThreshold.doubleValue()) * Math.sqrt(t - 1.0);
        var denominator = Math.sqrt(
                1.0 - moments.skewness() * sharpe + ((moments.kurtosis() - 1.0) / 4.0) * sharpe * sharpe
        );

        if (!(denominator > 0.0) || Double.isNaN(denominator) || Double.isInfinite(denominator)) {
            return zero;
        }

        var z = numerator / denominator;
        var psr = standardNormalCdf(z); // TODO: implement Φ(z)

        return series.numFactory().numOf(psr);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var zero = series.numFactory().zero();
        if (position == null || !position.isClosed()) {
            return zero;
        }

        var sharpe = sharpeRatio.calculate(series, position).doubleValue();
        var cashFlow = new CashFlow(series, position);
        var start = Math.max(position.getEntry().getIndex() + 1, series.getBeginIndex() + 1);
        var end = Math.min(position.getExit().getIndex(), series.getEndIndex());
        var anchorIndex = position.getEntry().getIndex();

        var excessReturns = Sampling.indexPairs(sampling, groupingZoneId, series, anchorIndex, start, end)
                .mapToDouble(pair -> excessReturn(series, cashFlow, pair.previousIndex(), pair.currentIndex()).doubleValue())
                .toArray();

        if (excessReturns.length < 2) {
            return zero;
        }

        var moments = Moments.from(excessReturns); // TODO
        var t = excessReturns.length;

        var numerator = (sharpe - sharpeThreshold.doubleValue()) * Math.sqrt(t - 1.0);
        var denominator = Math.sqrt(
                1.0 - moments.skewness() * sharpe + ((moments.kurtosis() - 1.0) / 4.0) * sharpe * sharpe
        );

        if (!(denominator > 0.0) || Double.isNaN(denominator) || Double.isInfinite(denominator)) {
            return zero;
        }

        var z = numerator / denominator;
        var psr = standardNormalCdf(z); // TODO

        return series.numFactory().numOf(psr);
    }

    private Num excessReturn(BarSeries series, CashFlow cashFlow, int previousIndex, int currentIndex) {
        var one = series.numFactory().one();
        var grossReturn = cashFlow.getValue(currentIndex).dividedBy(cashFlow.getValue(previousIndex)).minus(one);
        var riskFree = series.numFactory().zero(); // keep it 0 here unless you also want PSR to subtract RF at return level
        return grossReturn.minus(riskFree);
    }

    private double standardNormalCdf(double z) {
        throw new UnsupportedOperationException("TODO: implement Φ(z)");
    }

    private record Moments(double mean, double skewness, double kurtosis) {

        static Moments from(double[] values) {
            throw new UnsupportedOperationException("TODO: implement mean, skewness, kurtosis");
        }
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

}
