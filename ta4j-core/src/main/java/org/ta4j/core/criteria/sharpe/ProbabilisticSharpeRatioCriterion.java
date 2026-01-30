/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.criteria.sharpe;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.ExcessReturns;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.frequency.IndexPair;
import org.ta4j.core.analysis.frequency.Sample;
import org.ta4j.core.analysis.frequency.SampleSummary;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.analysis.frequency.SamplingFrequencyIndexes;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.utils.BarSeriesUtils;

/**
 * Computes the Probabilistic Sharpe Ratio (PSR).
 *
 * <p>
 * <b>Definition.</b> The PSR is defined as the probability that the observed
 * Sharpe ratio exceeds a benchmark Sharpe ratio {@code SR0} under a normal
 * approximation of Sharpe ratio estimation error.
 *
 * <p>
 * <b>Sampling and returns.</b> This implementation uses the same excess-return
 * sampling pipeline as {@link SharpeRatioCriterion}, aggregating returns using
 * {@link SamplingFrequency} and {@link CashReturnPolicy} over the equity curve.
 *
 * <p>
 * <b>Variance adjustment.</b> The Sharpe ratio variance estimate follows the
 * formulation described by Bailey & López de Prado, incorporating skewness and
 * (non-excess) kurtosis of excess returns. An optional AR(1) autocorrelation
 * adjustment is applied to approximate serial dependence.
 *
 * <p>
 * <b>Benchmark scale.</b> {@code benchmarkSharpeRatioPerPeriod} is always
 * interpreted as a <b>per-period</b> Sharpe ratio (non-annualized, on the same
 * sampling periods as the underlying samples). If
 * {@link Annualization#ANNUALIZED} is selected, the implementation scales both
 * the observed Sharpe and the benchmark by the same annualization factor (and
 * scales the variance by factor²), keeping PSR invariant.
 *
 * <p>
 * <b>Multiple testing.</b> Not supported yet. {@code numberOfTrials} must be
 * {@code 1}.
 *
 * @since 0.22.2
 */
public class ProbabilisticSharpeRatioCriterion extends AbstractAnalysisCriterion {

    private static final int DEFAULT_NUMBER_OF_TRIALS = 1;
    private static final NormalDistribution STANDARD_NORMAL = new NormalDistribution();

    private final SamplingFrequencyIndexes samplingFrequencyIndexes;
    private final Annualization annualization;
    private final CashReturnPolicy cashReturnPolicy;
    private final double annualRiskFreeRate;
    private final EquityCurveMode equityCurveMode;
    private final OpenPositionHandling openPositionHandling;
    private final double benchmarkSharpeRatioPerPeriod;
    private final double autocorrelation;

    public ProbabilisticSharpeRatioCriterion() {
        this(0d, SamplingFrequency.BAR, Annualization.ANNUALIZED, ZoneOffset.UTC, CashReturnPolicy.CASH_EARNS_RISK_FREE,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET, 0d, 0d, DEFAULT_NUMBER_OF_TRIALS);
    }

    public ProbabilisticSharpeRatioCriterion(double annualRiskFreeRate) {
        this(annualRiskFreeRate, SamplingFrequency.BAR, Annualization.ANNUALIZED, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET, 0d, 0d, DEFAULT_NUMBER_OF_TRIALS);
    }

    public ProbabilisticSharpeRatioCriterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
            Annualization annualization, ZoneId groupingZoneId) {
        this(annualRiskFreeRate, samplingFrequency, annualization, groupingZoneId,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET, 0d, 0d, DEFAULT_NUMBER_OF_TRIALS);
    }

    public ProbabilisticSharpeRatioCriterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
            Annualization annualization, ZoneId groupingZoneId, CashReturnPolicy cashReturnPolicy) {
        this(annualRiskFreeRate, samplingFrequency, annualization, groupingZoneId, cashReturnPolicy,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET, 0d, 0d, DEFAULT_NUMBER_OF_TRIALS);
    }

    public ProbabilisticSharpeRatioCriterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
            Annualization annualization, ZoneId groupingZoneId, CashReturnPolicy cashReturnPolicy,
            OpenPositionHandling openPositionHandling) {
        this(annualRiskFreeRate, samplingFrequency, annualization, groupingZoneId, cashReturnPolicy,
                EquityCurveMode.MARK_TO_MARKET, openPositionHandling, 0d, 0d, DEFAULT_NUMBER_OF_TRIALS);
    }

    public ProbabilisticSharpeRatioCriterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
            Annualization annualization, ZoneId groupingZoneId, CashReturnPolicy cashReturnPolicy,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this(annualRiskFreeRate, samplingFrequency, annualization, groupingZoneId, cashReturnPolicy, equityCurveMode,
                openPositionHandling, 0d, 0d, DEFAULT_NUMBER_OF_TRIALS);
    }

    public ProbabilisticSharpeRatioCriterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
            Annualization annualization, ZoneId groupingZoneId, CashReturnPolicy cashReturnPolicy,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling,
            double benchmarkSharpeRatioPerPeriod, double autocorrelation, int numberOfTrials) {
        this.annualRiskFreeRate = annualRiskFreeRate;
        this.annualization = Objects.requireNonNull(annualization, "annualization must not be null");
        this.cashReturnPolicy = Objects.requireNonNull(cashReturnPolicy, "cashReturnPolicy must not be null");
        this.equityCurveMode = Objects.requireNonNull(equityCurveMode, "equityCurveMode must not be null");
        this.openPositionHandling = Objects.requireNonNull(openPositionHandling,
                "openPositionHandling must not be null");
        this.benchmarkSharpeRatioPerPeriod = benchmarkSharpeRatioPerPeriod;

        if (autocorrelation <= -1d || autocorrelation >= 1d) {
            throw new IllegalArgumentException("autocorrelation must be between -1 and 1");
        }
        this.autocorrelation = autocorrelation;
        if (numberOfTrials != 1) {
            throw new IllegalArgumentException(
                    "numberOfTrials must be 1 until multiple-testing adjustment is implemented");
        }

        Objects.requireNonNull(samplingFrequency, "samplingFrequency must not be null");
        Objects.requireNonNull(groupingZoneId, "groupingZoneId must not be null");
        this.samplingFrequencyIndexes = new SamplingFrequencyIndexes(samplingFrequency, groupingZoneId);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position == null || position.getEntry() == null) {
            return series.numFactory().zero();
        }
        return calculate(series, new BaseTradingRecord(position));
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();

        if (tradingRecord == null) {
            return zero;
        }

        var beginIndex = series.getBeginIndex();
        var start = beginIndex + 1;
        var end = series.getEndIndex();
        if (end - start + 1 < 2) {
            return zero;
        }

        var annualRiskFreeRateNum = numFactory.numOf(annualRiskFreeRate);
        var excessReturns = new ExcessReturns(series, annualRiskFreeRateNum, cashReturnPolicy, tradingRecord,
                openPositionHandling);

        var samples = samplingFrequencyIndexes.sample(series, beginIndex, start, end)
                .map(pair -> getSample(series, pair, excessReturns));
        var summary = SampleSummary.fromSamples(samples, numFactory);

        if (summary.count() < 2) {
            return zero;
        }

        var sampleVariance = summary.sampleVariance(numFactory);
        if (sampleVariance.isZero()) {
            return zero;
        }

        var stdev = sampleVariance.sqrt();
        if (stdev.isZero()) {
            return zero;
        }

        var sharpePerPeriod = summary.mean().dividedBy(stdev);
        var annualizationFactor = summary.annualizationFactor(numFactory);

        var sharpe = annualization == Annualization.PERIOD ? sharpePerPeriod
                : annualizationFactor.map(sharpePerPeriod::multipliedBy).orElse(sharpePerPeriod);

        var sr0PerPeriod = numFactory.numOf(benchmarkSharpeRatioPerPeriod);
        var sr0 = annualization == Annualization.PERIOD ? sr0PerPeriod
                : annualizationFactor.map(sr0PerPeriod::multipliedBy).orElse(sr0PerPeriod);

        var gamma3 = summary.sampleSkewness(numFactory);
        var gamma4 = summary.sampleKurtosis(numFactory).plus(numFactory.three());

        var variancePerPeriod = sharpeRatioVariance(gamma3, gamma4, sharpePerPeriod, summary.count(), numFactory);
        var variance = annualization == Annualization.PERIOD ? variancePerPeriod
                : annualizationFactor.map(factor -> variancePerPeriod.multipliedBy(factor.multipliedBy(factor)))
                        .orElse(variancePerPeriod);

        if (variance.isLessThanOrEqual(zero)) {
            return zero;
        }

        var denominator = variance.sqrt();
        if (denominator.isZero()) {
            return zero;
        }

        var zScore = sharpe.minus(sr0).dividedBy(denominator).doubleValue();
        return numFactory.numOf(STANDARD_NORMAL.cumulativeProbability(zScore));
    }

    private Sample getSample(BarSeries series, IndexPair pair, ExcessReturns excessReturns) {
        var previousIndex = pair.previousIndex();
        var excessReturn = excessReturns.excessReturn(previousIndex, pair.currentIndex());
        var deltaYears = BarSeriesUtils.deltaYears(series, previousIndex, pair.currentIndex());
        return new Sample(excessReturn, deltaYears);
    }

    private Num sharpeRatioVariance(Num gamma3, Num gamma4, Num sharpe, int sampleCount, NumFactory numFactory) {
        var zero = numFactory.zero();
        if (sampleCount < 2) {
            return zero;
        }

        var rho = autocorrelation;
        var b = rho / (1d - rho);
        var c = (rho * rho) / (1d - (rho * rho));

        var aNum = numFactory.numOf(1d + 2d * b);
        var bNum = numFactory.numOf(1d + b + c);
        var cNum = numFactory.numOf(1d + 2d * c);

        var sharpeSquared = sharpe.multipliedBy(sharpe);
        var term = aNum.minus(bNum.multipliedBy(gamma3).multipliedBy(sharpe))
                .plus(cNum.multipliedBy(gamma4.minus(numFactory.one()))
                        .multipliedBy(sharpeSquared)
                        .dividedBy(numFactory.numOf(4)));

        return term.dividedBy(numFactory.numOf(sampleCount - 1));
    }

    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }
}