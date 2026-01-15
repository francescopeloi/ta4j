package org.ta4j.core.criteria.sharpe;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.stream.IntStream;
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
            ZoneId groupingZoneId,
            Num sharpeThreshold
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

        var moments = Moments.from(excessReturns);
        var sampleSize = excessReturns.length;
        var sharpeNull = sharpeThreshold.doubleValue();
        var autocorrelation = lag1Autocorrelation(excessReturns, moments.mean());
        var sigmaSharpeNull = sigmaSharpeNull(sampleSize, autocorrelation, moments.skewness(), moments.kurtosis(), sharpeNull);

        if (!(sigmaSharpeNull > 0.0) || Double.isInfinite(sigmaSharpeNull)) {
            return zero;
        }
        var z = (sharpe - sharpeNull) / sigmaSharpeNull;
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

        var moments = Moments.from(excessReturns);
        var sampleSize = excessReturns.length;
        var sharpeNull = sharpeThreshold.doubleValue();
        var autocorrelation = lag1Autocorrelation(excessReturns, moments.mean());
        var sigmaSharpeNull = sigmaSharpeNull(sampleSize, autocorrelation, moments.skewness(), moments.kurtosis(), sharpeNull);

        if (!(sigmaSharpeNull > 0.0) || Double.isInfinite(sigmaSharpeNull)) {
            return zero;
        }

        var z = (sharpe - sharpeNull) / sigmaSharpeNull;
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

    private static double lag1Autocorrelation(double[] values, double mean) {
        if (values.length < 2) {
            return 0.0;
        }

        var varianceDenominator = Arrays.stream(values)
                .map(value -> value - mean)
                .map(centered -> centered * centered)
                .sum();

        if (!(varianceDenominator > 0.0) || Double.isInfinite(varianceDenominator)) {
            return 0.0;
        }

        var covarianceNumerator = IntStream.range(1, values.length)
                .mapToDouble(index -> (values[index] - mean) * (values[index - 1] - mean))
                .sum();

        return clamp(covarianceNumerator / varianceDenominator, -0.999999, 0.999999);
    }

    private static double sigmaSharpeNull(
            int sampleSize,
            double autocorrelation,
            double skewness,
            double kurtosis,
            double sharpeNull
    ) {
        if (sampleSize <= 1) {
            return Double.NaN;
        }

        var rho = clamp(autocorrelation, -0.999999, 0.999999);
        var rhoSquared = rho * rho;

        var oneMinusRho = 1.0 - rho;
        var oneMinusRhoSquared = 1.0 - rhoSquared;

        var term1 = (1.0 + rho) / oneMinusRho;
        var term2 = (1.0 + rho + rhoSquared) / oneMinusRhoSquared;
        var term3 = (1.0 + rhoSquared) / oneMinusRhoSquared;

        var sigmaSquared = (1.0 / sampleSize) * (
                term1
                        - term2 * skewness * sharpeNull
                        + term3 * ((kurtosis - 1.0) / 4.0) * sharpeNull * sharpeNull
        );

        return sigmaSquared > 0.0 ? Math.sqrt(sigmaSquared) : Double.NaN;
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

}
