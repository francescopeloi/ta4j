package org.ta4j.core.criteria.sharpe;

import java.time.ZoneId;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.sharpe.helpers.Annualization;
import org.ta4j.core.criteria.sharpe.helpers.Moments;
import org.ta4j.core.criteria.sharpe.helpers.Sampling;
import org.ta4j.core.criteria.sharpe.helpers.SharpeRatioReturnSeries;
import org.ta4j.core.num.Num;

public class ProbabilisticSharpeRatioCriterion extends AbstractAnalysisCriterion {

    private static final NormalDistribution STANDARD_NORMAL_DISTRIBUTION = new NormalDistribution(null, 0.0, 1.0);

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

        var excessReturns = SharpeRatioReturnSeries.samples(
                        series,
                        cashFlow,
                        sampling,
                        groupingZoneId,
                        anchorIndex,
                        start,
                        end,
                        annualRiskFreeRate)
                .mapToDouble(sample -> sample.excessReturn().doubleValue())
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

        if (!(denominator > 0.0) || Double.isInfinite(denominator)) {
            return zero;
        }

        var z = numerator / denominator;
        var psr = standardNormalCdf(z);

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

        var excessReturns = SharpeRatioReturnSeries.samples(
                        series,
                        cashFlow,
                        sampling,
                        groupingZoneId,
                        anchorIndex,
                        start,
                        end,
                        annualRiskFreeRate)
                .mapToDouble(sample -> sample.excessReturn().doubleValue())
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

        if (!(denominator > 0.0) || Double.isInfinite(denominator)) {
            return zero;
        }

        var z = numerator / denominator;
        var psr = standardNormalCdf(z);

        return series.numFactory().numOf(psr);
    }

    private double standardNormalCdf(double z) {
        if (Double.isNaN(z)) {
            return 0.0;
        }
        if (!Double.isFinite(z)) {
            return z > 0.0 ? 1.0 : 0.0;
        }
        if (z <= -8.0) {
            return 0.0;
        }
        if (z >= 8.0) {
            return 1.0;
        }
        return STANDARD_NORMAL_DISTRIBUTION.cumulativeProbability(z);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

}
