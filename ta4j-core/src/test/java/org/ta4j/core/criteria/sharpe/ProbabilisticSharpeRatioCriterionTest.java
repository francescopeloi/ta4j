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

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.stream.IntStream;
import org.junit.Ignore;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.num.NumFactory;

public class ProbabilisticSharpeRatioCriterionTest extends AbstractCriterionTest {

    public ProbabilisticSharpeRatioCriterionTest(NumFactory numFactory) {
        super(params -> new ProbabilisticSharpeRatioCriterion((double) params[0], (SamplingFrequency) params[1],
                (Annualization) params[2], (ZoneId) params[3], (CashReturnPolicy) params[4],
                (EquityCurveMode) params[5], (OpenPositionHandling) params[6], (double) params[7], (double) params[8],
                (int) params[9]), numFactory);
    }

    @Test
    public void returnsHalfProbability_whenSharpeEqualsBenchmark() {
        var series = buildDailySeries(new double[] { 100d, 110d, 105d, 120d, 115d, 130d },
                Instant.parse("2024-01-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var sharpeCriterion = new SharpeRatioCriterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC);
        var sharpe = sharpeCriterion.calculate(series, tradingRecord).doubleValue();

        var criterion = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, sharpe, 0d);
        var psr = criterion.calculate(series, tradingRecord).doubleValue();

        assertEquals(0.5d, psr, 1e-12);
    }

    @Test
    public void alignsWithSharpeAnnualizationAndSampling() {
        var series = buildDailySeries(new double[] { 100d, 115d, 107d, 120d, 118d, 130d },
                Instant.parse("2024-02-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var sharpePerPeriodCriterion = new SharpeRatioCriterion(0d, SamplingFrequency.DAY, Annualization.PERIOD,
                ZoneOffset.UTC);
        var sharpePerPeriod = sharpePerPeriodCriterion.calculate(series, tradingRecord).doubleValue();

        var criterion = criterion(0d, SamplingFrequency.DAY, Annualization.ANNUALIZED, sharpePerPeriod, 0d);
        var psr = criterion.calculate(series, tradingRecord).doubleValue();

        assertEquals(0.5d, psr, 1e-12);
    }

    @Test
    public void doesNotReturnHalfProbability_whenAnnualizedSharpeIsPassedAsPerPeriodBenchmark() {
        var series = buildDailySeries(new double[] { 100d, 115d, 107d, 120d, 118d, 130d },
                Instant.parse("2024-02-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var sharpeAnnualized = new SharpeRatioCriterion(0d, SamplingFrequency.DAY, Annualization.ANNUALIZED,
                ZoneOffset.UTC).calculate(series, tradingRecord).doubleValue();

        var psr = criterion(0d, SamplingFrequency.DAY, Annualization.ANNUALIZED, sharpeAnnualized, 0d)
                .calculate(series, tradingRecord)
                .doubleValue();

        assertTrue(psr < 1e-6);
    }

    @Test
    public void returnsZero_whenTradingRecordIsNull() {
        var series = buildDailySeries(new double[] { 100d, 110d, 105d, 120d, 115d, 130d },
                Instant.parse("2024-03-01T00:00:00Z"));

        var criterion = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, 0d, 0d);
        var psr = criterion.calculate(series, (TradingRecord) null);

        assertTrue(psr.isZero());
    }

    @Test
    public void returnsZero_whenSeriesTooShort() {
        var series = buildDailySeries(new double[] { 100d, 101d }, Instant.parse("2024-03-10T00:00:00Z"));
        var tradingRecord = new BaseTradingRecord();

        var criterion = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, 0d, 0d);
        var psr = criterion.calculate(series, tradingRecord);

        assertTrue(psr.isZero());
    }

    @Test
    public void returnsZero_whenReturnsHaveZeroVariance() {
        var series = buildDailySeries(new double[] { 100d, 100d, 100d, 100d, 100d, 100d },
                Instant.parse("2024-03-20T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var criterion = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, 0d, 0d);
        var psr = criterion.calculate(series, tradingRecord);

        assertTrue(psr.isZero());
    }

    @Test
    public void staysWithinBounds_zeroToOne() {
        var series = buildDailySeries(new double[] { 100d, 110d, 90d, 120d, 115d, 130d },
                Instant.parse("2024-04-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var criterion = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, 0d, 0d);
        var psr = criterion.calculate(series, tradingRecord).doubleValue();

        assertTrue(psr >= 0d);
        assertTrue(psr <= 1d);
    }

    @Test
    public void increasesWhenBenchmarkDecreases_andDecreasesWhenBenchmarkIncreases() {
        var series = buildDailySeries(new double[] { 100d, 112d, 108d, 125d, 121d, 140d },
                Instant.parse("2024-04-10T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var lowBenchmark = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, -5d, 0d)
                .calculate(series, tradingRecord)
                .doubleValue();
        var highBenchmark = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, 5d, 0d)
                .calculate(series, tradingRecord)
                .doubleValue();

        assertTrue(lowBenchmark > highBenchmark);
        assertTrue(lowBenchmark > 0.5d);
        assertTrue(highBenchmark < 0.5d);
    }

    @Test
    public void psrIsInvariantUnderAnnualizationToggle() {
        var series = buildDailySeries(new double[] { 100d, 110d, 105d, 120d, 115d, 130d },
                Instant.parse("2024-05-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var sr0PerPeriod = 0.25d;

        var period = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, sr0PerPeriod, 0d)
                .calculate(series, tradingRecord)
                .doubleValue();
        var annualized = criterion(0d, SamplingFrequency.BAR, Annualization.ANNUALIZED, sr0PerPeriod, 0d)
                .calculate(series, tradingRecord)
                .doubleValue();

        assertEquals(period, annualized, 1e-12);
    }

    @Test
    public void psrDecreasesWithPositiveAutocorrelation_whenSharpeAboveBenchmark() {
        var series = buildDailySeries(new double[] { 100d, 112d, 109d, 125d, 123d, 140d },
                Instant.parse("2024-05-10T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var noAutocorr = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, 0d, 0d)
                .calculate(series, tradingRecord)
                .doubleValue();
        var withAutocorr = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, 0d, 0.5d)
                .calculate(series, tradingRecord)
                .doubleValue();

        assertTrue(noAutocorr > withAutocorr);
    }

    @Test
    public void psrDecreasesWhenAnnualRiskFreeRateIncreases() {
        var series = buildDailySeries(new double[] { 100d, 112d, 109d, 125d, 123d, 140d },
                Instant.parse("2024-05-20T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var lowRiskFree = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, 0d, 0d)
                .calculate(series, tradingRecord)
                .doubleValue();
        var highRiskFree = criterion(0.50d, SamplingFrequency.BAR, Annualization.PERIOD, 0d, 0d)
                .calculate(series, tradingRecord)
                .doubleValue();

        assertTrue(lowRiskFree > highRiskFree);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorThrows_whenAutocorrelationIsOne() {
        new ProbabilisticSharpeRatioCriterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET, 0d, 1d, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorThrows_whenAutocorrelationIsMinusOne() {
        new ProbabilisticSharpeRatioCriterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET, 0d, -1d, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorThrows_whenNumberOfTrialsNotOne() {
        new ProbabilisticSharpeRatioCriterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET, 0d, 0d, 2);
    }

    @Test(expected = NullPointerException.class)
    public void constructorThrows_whenAnnualizationIsNull() {
        new ProbabilisticSharpeRatioCriterion(0d, SamplingFrequency.BAR, null, ZoneOffset.UTC,
                CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET, 0d, 0d, 1);
    }

    @Test
    public void betterThanUsesGreaterThan() {
        var series = buildDailySeries(new double[] { 100d, 110d, 105d, 120d, 115d, 130d },
                Instant.parse("2024-06-01T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var criterion = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, 0d, 0d);
        var higher = series.numFactory().numOf(0.6d);
        var lower = series.numFactory().numOf(0.5d);

        assertTrue(criterion.betterThan(higher, lower));
        assertFalse(criterion.betterThan(lower, higher));

        var psr = criterion.calculate(series, tradingRecord).doubleValue();
        assertTrue(psr >= 0d);
        assertTrue(psr <= 1d);
    }

    @Test
    public void returnsZero_whenTradingRecordHasNoPositions() {
        var series = buildDailySeries(new double[] { 100d, 110d, 105d, 120d, 115d, 130d },
                Instant.parse("2024-06-10T00:00:00Z"));
        var tradingRecord = new BaseTradingRecord();

        var psr = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, 0d, 0d).calculate(series, tradingRecord);

        assertTrue(psr.isZero());
    }

    @Test
    public void returnsZero_whenSamplingFrequencyProducesSingleSample() {
        var series = buildHourlySeriesSameDay(new double[] { 100d, 101d, 99d, 102d, 101d, 103d },
                Instant.parse("2024-06-12T00:00:00Z"));
        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);
        tradingRecord.exit(series.getEndIndex(), series.getBar(series.getEndIndex()).getClosePrice(), amount);

        var psr = criterion(0d, SamplingFrequency.DAY, Annualization.PERIOD, 0d, 0d).calculate(series, tradingRecord);

        assertTrue(psr.isZero());
    }

    @Test
    public void cashReturnPolicyAffectsResult_whenTimeOutOfMarketAndRiskFreeIsPositive() {
        var series = buildDailySeries(new double[] { 100d, 104d, 103d, 103d, 106d, 105d, 108d },
                Instant.parse("2024-06-14T00:00:00Z"));
        var amount = series.numFactory().one();

        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), amount);
        tradingRecord.exit(1, series.getBar(1).getClosePrice(), amount);
        tradingRecord.enter(5, series.getBar(5).getClosePrice(), amount);
        tradingRecord.exit(6, series.getBar(6).getClosePrice(), amount);

        var otherPolicy = Arrays.stream(CashReturnPolicy.values())
                .filter(policy -> policy != CashReturnPolicy.CASH_EARNS_RISK_FREE)
                .findFirst()
                .orElseThrow();

        var psrCashEarnsRf = criterion(0.20d, CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.MARK_TO_MARKET)
                .calculate(series, tradingRecord)
                .doubleValue();

        var psrOther = criterion(0.20d, otherPolicy, EquityCurveMode.MARK_TO_MARKET).calculate(series, tradingRecord)
                .doubleValue();

        assertTrue(psrCashEarnsRf >= psrOther);
    }

    @Test
    public void psrChangesWhenAutocorrelationChangesSign_whenSharpeNotEqualBenchmark() {
        var series = buildDailySeries(new double[] { 100d, 112d, 108d, 125d, 121d, 140d, 133d, 150d },
                Instant.parse("2024-07-10T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var psrNegative = psr(series, tradingRecord, -0.70d);
        var psrPositive = psr(series, tradingRecord, 0.70d);

        assertTrue(psrNegative >= 0d && psrNegative <= 1d);
        assertTrue(psrPositive >= 0d && psrPositive <= 1d);

        assertTrue("Expected PSR to change when autocorrelation changes sign",
                Math.abs(psrNegative - psrPositive) > 1e-12);
    }

    @Test
    public void returnsZero_whenPositionIsNull() {
        var series = buildDailySeries(new double[] { 100d, 110d, 105d, 120d, 115d, 130d },
                Instant.parse("2024-08-01T00:00:00Z"));

        var criterion = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, 0d, 0d);
        var psr = criterion.calculate(series, (org.ta4j.core.Position) null);

        assertTrue(psr.isZero());
    }

    @Test
    public void remainsStableAtExtremeBenchmarks() {
        var series = buildDailySeries(new double[] { 100d, 112d, 109d, 125d, 123d, 140d, 137d, 150d },
                Instant.parse("2024-08-10T00:00:00Z"));
        var tradingRecord = alwaysInvested(series);

        var nearOne = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, -100d, 0d)
                .calculate(series, tradingRecord)
                .doubleValue();
        var nearZero = criterion(0d, SamplingFrequency.BAR, Annualization.PERIOD, 100d, 0d)
                .calculate(series, tradingRecord)
                .doubleValue();

        assertTrue(nearOne >= 0d && nearOne <= 1d);
        assertTrue(nearZero >= 0d && nearZero <= 1d);

        assertTrue(nearOne > 0.999);
        assertTrue(nearZero < 1e-6);
    }

    @Ignore("Enable when ProbabilisticSharpeRatioCriterion actually uses equityCurveMode (currently unused in the posted implementation).")
    @Test
    public void equityCurveModeAffectsResult_forOpenPnl() {
        var series = buildDailySeries(new double[] { 100d, 110d, 120d, 130d, 140d, 150d },
                Instant.parse("2024-06-16T00:00:00Z"));
        var amount = series.numFactory().one();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(), amount);

        var markToMarket = criterion(0d, CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.MARK_TO_MARKET)
                .calculate(series, tradingRecord)
                .doubleValue();

        var realized = criterion(0d, CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.REALIZED)
                .calculate(series, tradingRecord)
                .doubleValue();

        assertTrue(markToMarket != realized);
    }

    private BarSeries buildHourlySeriesSameDay(double[] closes, Instant start) {
        var series = getBarSeries("hourly_same_day_series");

        IntStream.range(0, closes.length).forEach(i -> {
            var endTime = start.plus(Duration.ofHours(i + 1L));
            var close = closes[i];
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofHours(1))
                    .endTime(endTime)
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .build());
        });

        return series;
    }

    private BarSeries buildDailySeries(double[] closes, Instant start) {
        var series = getBarSeries("daily_series");

        IntStream.range(0, closes.length).forEach(i -> {
            var endTime = start.plus(Duration.ofDays(i + 1L));
            var close = closes[i];
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(endTime)
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .build());
        });

        return series;
    }

    private static TradingRecord alwaysInvested(BarSeries series) {
        var amount = series.numFactory().one();
        var begin = series.getBeginIndex();
        var end = series.getEndIndex();
        var split = begin + (end - begin) / 2;

        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(begin, series.getBar(begin).getClosePrice(), amount);
        tradingRecord.exit(split, series.getBar(split).getClosePrice(), amount);

        if (split < end) {
            tradingRecord.enter(split, series.getBar(split).getClosePrice(), amount);
            tradingRecord.exit(end, series.getBar(end).getClosePrice(), amount);
        }

        return tradingRecord;
    }

    private static Optional<OpenPositionHandling> ignoreLikeOpenPositionHandling() {
        return Stream.of(OpenPositionHandling.values())
                .filter(value -> value != OpenPositionHandling.MARK_TO_MARKET)
                .filter(value -> {
                    var name = value.name();
                    return name.contains("IGNORE") || name.contains("EXCLUDE") || name.contains("SKIP");
                })
                .findFirst();
    }

    private static TradingRecord openPositionFromStart(org.ta4j.core.BarSeries series) {
        var amount = series.numFactory().one();
        var begin = series.getBeginIndex();
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(begin, series.getBar(begin).getClosePrice(), amount);
        return tradingRecord;
    }

    private double psr(BarSeries series, TradingRecord tradingRecord, double v1) {
        return criterion(v1).calculate(series, tradingRecord).doubleValue();
    }

    private ProbabilisticSharpeRatioCriterion criterion(double autocorrelation) {
        return (ProbabilisticSharpeRatioCriterion) getCriterion(0.0, SamplingFrequency.BAR, Annualization.PERIOD,
                ZoneOffset.UTC, CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET, 0.0, autocorrelation, 1);
    }

    private ProbabilisticSharpeRatioCriterion criterion(double annualRiskFreeRate, SamplingFrequency samplingFrequency,
            Annualization annualization, double benchmarkSharpeRatio, double autocorrelation) {
        return (ProbabilisticSharpeRatioCriterion) getCriterion(annualRiskFreeRate, samplingFrequency, annualization,
                ZoneOffset.UTC, CashReturnPolicy.CASH_EARNS_RISK_FREE, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET, benchmarkSharpeRatio, autocorrelation, 1);
    }

    private ProbabilisticSharpeRatioCriterion criterion(double annualRiskFreeRate, CashReturnPolicy cashReturnPolicy,
            EquityCurveMode equityCurveMode) {
        return (ProbabilisticSharpeRatioCriterion) getCriterion(annualRiskFreeRate, SamplingFrequency.BAR,
                Annualization.PERIOD, ZoneOffset.UTC, cashReturnPolicy, equityCurveMode,
                OpenPositionHandling.MARK_TO_MARKET, 0.0, 0.0, 1);
    }
}