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
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.sharpe.helpers.Annualization;
import org.ta4j.core.criteria.sharpe.helpers.Sampling;
import org.ta4j.core.criteria.sharpe.helpers.SharpeRatioReturnSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Computes the Sharpe Ratio.
 *
 * <p>
 * <b>Definition.</b> The Sharpe Ratio is defined as {@code SR = μ / σ}, where
 * {@code μ} is the expected value of excess returns and {@code σ} is the
 * standard deviation of excess returns.
 *
 * <p>
 * <b>What this criterion measures.</b> This implementation builds a time series
 * of <em>excess returns</em> from the {@link CashFlow} equity curve: for each
 * sampled pair {@code (previousIndex, currentIndex)} it computes:
 * <ul>
 * <li>{@code return = equity(currentIndex) / equity(previousIndex) - 1}</li>
 * <li>{@code excessReturn = return - riskFreeReturn(previousIndex, currentIndex)}</li>
 * </ul>
 * It then returns {@code mean(excessReturn) / stdev(excessReturn)} using the
 * sample standard deviation.
 *
 * <p>
 * <b>Sampling (aggregation) of returns.</b> The {@link Sampling} parameter
 * controls how the return series is formed:
 * <ul>
 * <li>{@link Sampling#PER_BAR}: one return per bar, using consecutive bar
 * indices.</li>
 * <li>{@link Sampling#DAILY}/{@link Sampling#WEEKLY}/{@link Sampling#MONTHLY}:
 * returns are computed between period endpoints detected from bar
 * {@code endTime} after converting it to {@link #groupingZoneId}. Period
 * boundaries follow ISO week semantics for {@code WEEKLY}.</li>
 * </ul>
 * The first sampled return is anchored at the series begin index (for
 * {@link TradingRecord}) or the entry index (for {@link Position}), so the
 * first period return spans from the anchor to the first period end.
 *
 * <p>
 * <b>Risk-free rate.</b> {@link #annualRiskFreeRate} is interpreted as an
 * annualized rate (e.g., 0.05 = 5% per year) and converted into a per-period
 * compounded return using the elapsed time between the two bar end times. If
 * {@code annualRiskFreeRate} is {@code null}, it is treated as zero.
 *
 * <p>
 * <b>Annualization.</b> When {@link Annualization#PERIOD}, the returned Sharpe
 * is per sampling period (no scaling). When {@link Annualization#ANNUALIZED},
 * the per-period Sharpe is multiplied by {@code sqrt(periodsPerYear)} where
 * {@code periodsPerYear} is estimated from observed time deltas (count of
 * positive deltas divided by the sum of deltas in years).
 *
 * @since 0.22.1
 *
 */
public class SharpeRatioCriterion extends AbstractAnalysisCriterion {

    private final Num annualRiskFreeRate;
    private final Sampling sampling;
    private final Annualization annualization;
    private final ZoneId groupingZoneId;

    public SharpeRatioCriterion(
            Num annualRiskFreeRate,
            Sampling sampling,
            Annualization annualization,
            ZoneId groupingZoneId
    ) {
        this.annualRiskFreeRate = annualRiskFreeRate;
        this.sampling = sampling;
        this.annualization = annualization;
        this.groupingZoneId = groupingZoneId;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var zero = series.numFactory().zero();
        if (position == null) {
            return zero;
        }
        if (!position.isClosed()) {
            // Open positions do not have a complete return distribution (exit not fixed),
            // so Sharpe would depend on an arbitrary cutoff; returning 0 avoids misleading
            // ranking.
            return zero;
        }

        var cashFlow = new CashFlow(series, position);
        var start = Math.max(position.getEntry().getIndex() + 1, series.getBeginIndex() + 1);
        var end = Math.min(position.getExit().getIndex(), series.getEndIndex());
        var anchorIndex = position.getEntry().getIndex();

        return calculateSharpe(series, cashFlow, anchorIndex, start, end);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();
        if (tradingRecord == null) {
            return zero;
        }

        var hasClosedPositions = tradingRecord.getPositions().stream().anyMatch(Position::isClosed);
        if (!hasClosedPositions) {
            return zero;
        }

        var cashFlow = new CashFlow(series, tradingRecord);
        var start = series.getBeginIndex() + 1;
        var end = series.getEndIndex();
        var anchorIndex = series.getBeginIndex();

        return calculateSharpe(series, cashFlow, anchorIndex, start, end);
    }

    private Num calculateSharpe(BarSeries series, CashFlow cashFlow, int anchorIndex, int start, int end) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();
        if (end - start + 1 < 2) {
            return zero;
        }

        var samples = SharpeRatioReturnSeries.samples(
                series,
                cashFlow,
                sampling,
                groupingZoneId,
                anchorIndex,
                start,
                end,
                annualRiskFreeRate);

        var acc = samples.reduce(Acc.empty(zero),
                (a, s) -> a.add(s.excessReturn(), s.deltaYears(), numFactory),
                (a, b) -> a.merge(b, numFactory));

        if (acc.stats().count() < 2) {
            return zero;
        }

        var stdev = acc.stats().sampleVariance(numFactory).sqrt();
        if (stdev.isZero()) {
            return zero;
        }

        var sharpePerPeriod = acc.stats().mean().dividedBy(stdev);

        if (annualization == Annualization.PERIOD) {
            return sharpePerPeriod;
        }

        var annualizationFactor = acc.annualizationFactor();
        if (annualizationFactor <= 0.0) {
            return sharpePerPeriod;
        }

        return sharpePerPeriod.multipliedBy(numFactory.numOf(annualizationFactor));
    }

    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }

    private record Acc(Stats stats, double deltaYearsSum, int deltaCount) {

        static Acc empty(Num zero) {
            return new Acc(Stats.empty(zero), 0.0, 0);
        }

        Acc add(Num excessReturn, double deltaYears, NumFactory numFactory) {
            var nextStats = stats.add(excessReturn, numFactory);
            if (deltaYears <= 0.0) {
                return new Acc(nextStats, deltaYearsSum, deltaCount);
            }
            return new Acc(nextStats, deltaYearsSum + deltaYears, deltaCount + 1);
        }

        Acc merge(Acc other, NumFactory numFactory) {
            var mergedStats = stats.merge(other.stats, numFactory);
            return new Acc(mergedStats, deltaYearsSum + other.deltaYearsSum, deltaCount + other.deltaCount);
        }

        double annualizationFactor() {
            if (deltaCount <= 0 || deltaYearsSum <= 0.0) {
                return 0.0;
            }
            var periodsPerYear = deltaCount / deltaYearsSum;
            return Math.sqrt(periodsPerYear);
        }
    }

    record Stats(Num mean, Num m2, int count) {

        static Stats empty(Num zero) {
            return new Stats(zero, zero, 0);
        }

        Stats add(Num x, NumFactory f) {
            if (count == 0) {
                return new Stats(x, f.zero(), 1);
            }
            var n = count + 1;
            var nNum = f.numOf(n);
            var delta = x.minus(mean);
            var meanNext = mean.plus(delta.dividedBy(nNum));
            var delta2 = x.minus(meanNext);
            var m2Next = m2.plus(delta.multipliedBy(delta2));
            return new Stats(meanNext, m2Next, n);
        }

        Stats merge(Stats o, NumFactory f) {
            if (o.count == 0) {
                return this;
            }
            if (count == 0) {
                return o;
            }
            var n1 = count;
            var n2 = o.count;
            var n = n1 + n2;
            var n1Num = f.numOf(n1);
            var n2Num = f.numOf(n2);
            var nNum = f.numOf(n);
            var delta = o.mean.minus(mean);
            var meanNext = mean.plus(delta.multipliedBy(n2Num).dividedBy(nNum));
            var m2Next = m2.plus(o.m2)
                    .plus(delta.multipliedBy(delta).multipliedBy(n1Num).multipliedBy(n2Num).dividedBy(nNum));
            return new Stats(meanNext, m2Next, n);
        }

        Num sampleVariance(NumFactory f) {
            if (count < 2) {
                return f.zero();
            }
            return m2.dividedBy(f.numOf(count - 1));
        }
    }

}
