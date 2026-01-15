package org.ta4j.core.criteria.sharpe.helpers;

import java.util.stream.Stream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

public final class SharpeRatioReturnSeries {

    private static final double SECONDS_PER_YEAR = 365.2425d * 24 * 3600;

    private SharpeRatioReturnSeries() {
    }

    public static Stream<ReturnSample> samples(
            BarSeries series,
            CashFlow cashFlow,
            Sampling sampling,
            ZoneId groupingZoneId,
            int anchorIndex,
            int start,
            int end,
            Num annualRiskFreeRate
    ) {
        return Sampling.indexPairs(sampling, groupingZoneId, series, anchorIndex, start, end)
                .map(pair -> new ReturnSample(
                        excessReturn(series, cashFlow, pair.previousIndex(), pair.currentIndex(), annualRiskFreeRate),
                        deltaYears(series, pair.previousIndex(), pair.currentIndex())));
    }

    private static Num excessReturn(
            BarSeries series,
            CashFlow cashFlow,
            int previousIndex,
            int currentIndex,
            Num annualRiskFreeRate
    ) {
        var numFactory = series.numFactory();
        var one = numFactory.one();
        var eReturn = cashFlow.getValue(currentIndex).dividedBy(cashFlow.getValue(previousIndex)).minus(one);
        return eReturn.minus(periodRiskFree(series, previousIndex, currentIndex, annualRiskFreeRate));
    }

    private static Num periodRiskFree(
            BarSeries series,
            int previousIndex,
            int currentIndex,
            Num annualRiskFreeRate
    ) {
        var numFactory = series.numFactory();
        var deltaYears = deltaYears(series, previousIndex, currentIndex);
        if (deltaYears <= 0.0) {
            return numFactory.zero();
        }

        var annual = (annualRiskFreeRate == null) ? 0.0 : annualRiskFreeRate.doubleValue();
        var per = Math.pow(1.0 + annual, deltaYears) - 1.0;
        return numFactory.numOf(per);
    }

    private static double deltaYears(BarSeries series, int previousIndex, int currentIndex) {
        var endPrev = endTimeInstant(series, previousIndex);
        var endNow = endTimeInstant(series, currentIndex);
        var seconds = Math.max(0, Duration.between(endPrev, endNow).getSeconds());
        return seconds <= 0 ? 0.0 : seconds / SECONDS_PER_YEAR;
    }

    private static Instant endTimeInstant(BarSeries series, int index) {
        return series.getBar(index).getEndTime();
    }

}