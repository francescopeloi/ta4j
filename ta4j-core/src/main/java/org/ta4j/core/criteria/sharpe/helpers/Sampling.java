package org.ta4j.core.criteria.sharpe.helpers;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.Locale;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.ta4j.core.BarSeries;

public enum Sampling {

    PER_BAR, DAILY, WEEKLY, MONTHLY;

    private static final WeekFields ISO_WEEK_FIELDS = WeekFields.of(Locale.ROOT);

    public static Stream<IndexPair> indexPairs(Sampling sampling, ZoneId groupingZoneId, BarSeries series, int anchorIndex, int start, int end) {
        if (sampling == Sampling.PER_BAR) {
            return IntStream.rangeClosed(start, end).mapToObj(i -> new IndexPair(i - 1, i));
        }

        var periodEndIndices = IntStream.rangeClosed(start, end)
                .filter(i -> isPeriodEnd(sampling, groupingZoneId, series, i, end))
                .toArray();

        if (periodEndIndices.length == 0) {
            return Stream.empty();
        }

        var firstPair = Stream.of(new IndexPair(anchorIndex, periodEndIndices[0]));
        var consecutivePairs = IntStream.range(1, periodEndIndices.length)
                .mapToObj(k -> new IndexPair(periodEndIndices[k - 1], periodEndIndices[k]));

        return Stream.concat(firstPair, consecutivePairs);
    }

    private static boolean isPeriodEnd(Sampling sampling, ZoneId groupingZoneId, BarSeries series, int index, int endIndex) {
        if (index == endIndex) {
            return true;
        }

        var now = endTimeZoned(groupingZoneId, series, index);
        var next = endTimeZoned(groupingZoneId, series, index + 1);

        return switch (sampling) {
            case DAILY -> !now.toLocalDate().equals(next.toLocalDate());
            case WEEKLY -> !sameIsoWeek(now, next);
            case MONTHLY -> !YearMonth.from(now).equals(YearMonth.from(next));
            case PER_BAR -> true;
        };
    }

    private static boolean sameIsoWeek(ZonedDateTime a, ZonedDateTime b) {
        var weekA = a.get(ISO_WEEK_FIELDS.weekOfWeekBasedYear());
        var weekB = b.get(ISO_WEEK_FIELDS.weekOfWeekBasedYear());
        var yearA = a.get(ISO_WEEK_FIELDS.weekBasedYear());
        var yearB = b.get(ISO_WEEK_FIELDS.weekBasedYear());
        return weekA == weekB && yearA == yearB;
    }

    private static ZonedDateTime endTimeZoned(ZoneId groupingZoneId, BarSeries series, int index) {
        return series.getBar(index).getEndTime().atZone(groupingZoneId);
    }

}