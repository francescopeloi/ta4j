package org.ta4j.core.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

public final class CumulativePnL implements Indicator<Num> {

    private final BarSeries barSeries;
    private final List<Num> values;

    public CumulativePnL(BarSeries barSeries, Position position) {
        if (position.isOpened()) {
            throw new IllegalArgumentException("Position is not closed. Provide a final index if open.");
        }
        this.barSeries = barSeries;
        this.values = new ArrayList<>(Collections.singletonList(barSeries.numFactory().zero()));
        calculate(position, position.getExit().getIndex());
        fillToTheEnd(barSeries.getEndIndex());
    }

    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord) {
        this(barSeries, tradingRecord, tradingRecord.getEndIndex(barSeries));
    }

    public CumulativePnL(BarSeries barSeries, TradingRecord tradingRecord, int finalIndex) {
        this.barSeries = barSeries;
        this.values = new ArrayList<>(Collections.singletonList(barSeries.numFactory().zero()));

        var positions = tradingRecord.getPositions();
        for (Position position : positions) {
            var endIndex = AnalysisUtils.determineEndIndex(position, finalIndex, barSeries.getEndIndex());
            calculate(position, endIndex);
        }
        if (tradingRecord.getCurrentPosition().isOpened()) {
            calculate(tradingRecord.getCurrentPosition(), finalIndex);
        }
        fillToTheEnd(finalIndex);
    }

    @Override
    public Num getValue(int index) {
        return values.get(index);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    @Override
    public BarSeries getBarSeries() {
        return barSeries;
    }

    public int getSize() {
        return barSeries.getBarCount();
    }

    private void calculate(Position position, int finalIndex) {
        var numberFactory = barSeries.numFactory();
        var isLong = position.getEntry().isBuy();
        var entryIndex = position.getEntry().getIndex();
        var endIndex = AnalysisUtils.determineEndIndex(position, finalIndex, barSeries.getEndIndex());
        var begin = entryIndex + 1;

        if (begin > values.size()) {
            var last = values.getLast();
            values.addAll(Collections.nCopies(begin - values.size(), last));
        }

        var periods = Math.max(0, endIndex - entryIndex);
        var holdingCost = position.getHoldingCost(endIndex);
        var averageCostPerPeriod = periods > 0 ? holdingCost.dividedBy(numberFactory.numOf(periods)) : numberFactory.zero();

        var netEntryPrice = position.getEntry().getNetPrice();
        var baseAtEntry = values.get(entryIndex);

        var startingIndex = Math.max(begin, 1);
        for (var i = startingIndex; i < endIndex; i++) {
            var close = barSeries.getBar(i).getClosePrice();
            var netIntermediate = AnalysisUtils.addCost(close, averageCostPerPeriod, isLong);
            var delta = isLong ? netIntermediate.minus(netEntryPrice) : netEntryPrice.minus(netIntermediate);
            values.add(baseAtEntry.plus(delta));
        }

        var exitRaw = position.getExit() != null
                ? position.getExit().getNetPrice()
                : barSeries.getBar(endIndex).getClosePrice();
        var netExit = AnalysisUtils.addCost(exitRaw, averageCostPerPeriod, isLong);
        var deltaExit = isLong ? netExit.minus(netEntryPrice) : netEntryPrice.minus(netExit);
        values.add(baseAtEntry.plus(deltaExit));
    }

    private void fillToTheEnd(int endIndex) {
        if (endIndex >= values.size()) {
            var last = values.getLast();
            values.addAll(Collections.nCopies(barSeries.getEndIndex() - values.size() + 1, last));
        }
    }

}
