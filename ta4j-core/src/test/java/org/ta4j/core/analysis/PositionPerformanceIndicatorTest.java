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
package org.ta4j.core.analysis;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PositionPerformanceIndicatorTest extends AbstractIndicatorTest<PositionPerformanceIndicator, Num> {

    public PositionPerformanceIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void recalculationWithEmptyRecordKeepsValuesStable() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d, 2d).build();
        var tradingRecord = new BaseTradingRecord();

        var cashFlow = new CashFlow(series, tradingRecord);
        assertUnchanged(cashFlow, tradingRecord, series.getEndIndex(), numFactory.one());

        var cumulativePnL = new CumulativePnL(series, tradingRecord);
        assertUnchanged(cumulativePnL, tradingRecord, series.getEndIndex(), numFactory.zero());

        var returnsIndicator = new Returns(series, tradingRecord);
        assertUnchanged(returnsIndicator, tradingRecord, series.getEndIndex(), numFactory.zero());
    }

    private void assertUnchanged(PositionPerformanceIndicator indicator, TradingRecord tradingRecord, int finalIndex,
            Num expectedValue) {
        indicator.calculate(tradingRecord, finalIndex, OpenPositionHandling.MARK_TO_MARKET);
        assertNumEquals(expectedValue, indicator.getValue(finalIndex));
    }
}
