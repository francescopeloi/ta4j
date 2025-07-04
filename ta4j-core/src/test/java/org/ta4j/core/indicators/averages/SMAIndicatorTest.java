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
package org.ta4j.core.indicators.averages;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.XLSIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private final ExternalIndicatorTest xls;

    public SMAIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new SMAIndicator(data, (int) params[0]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "SMA.xls", 6, numFactory);
    }

    private BarSeries data;

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2)
                .build();
    }

    public Instant getNextEndTime() {
        var lastBar = data.getLastBar();
        return lastBar == null ? null : lastBar.getEndTime().plus(lastBar.getTimePeriod());
    }

    @Test
    public void usingBarCount3UsingClosePrice() {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 3);

        assertNumEquals(1, indicator.getValue(0));
        assertNumEquals(1.5, indicator.getValue(1));
        assertNumEquals(2, indicator.getValue(2));
        assertNumEquals(3, indicator.getValue(3));
        assertNumEquals(10d / 3, indicator.getValue(4));
        assertNumEquals(11d / 3, indicator.getValue(5));
        assertNumEquals(4, indicator.getValue(6));
        assertNumEquals(13d / 3, indicator.getValue(7));
        assertNumEquals(4, indicator.getValue(8));
        assertNumEquals(10d / 3, indicator.getValue(9));
        assertNumEquals(10d / 3, indicator.getValue(10));
        assertNumEquals(10d / 3, indicator.getValue(11));
        assertNumEquals(3, indicator.getValue(12));
    }

    @Test
    public void usingBarCount3UsingClosePriceMovingSerie() {
        data.setMaximumBarCount(13);
        data.barBuilder().closePrice(5.).endTime(getNextEndTime()).add();

        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 3);

        // unstable bars skipped, unpredictable results
        assertNumEquals((3d + 4d + 3d) / 3, indicator.getValue(data.getBeginIndex() + 3));
        assertNumEquals((4d + 3d + 4d) / 3, indicator.getValue(data.getBeginIndex() + 4));
        assertNumEquals((3d + 4d + 5d) / 3, indicator.getValue(data.getBeginIndex() + 5));
        assertNumEquals((4d + 5d + 4d) / 3, indicator.getValue(data.getBeginIndex() + 6));
        assertNumEquals((3d + 2d + 5d) / 3, indicator.getValue(data.getBeginIndex() + 12));
    }

    @Test
    public void whenBarCountIs1ResultShouldBeIndicatorValue() {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        for (int i = 0; i < data.getBarCount(); i++) {
            assertEquals(data.getBar(i).getClosePrice(), indicator.getValue(i));
        }
    }

    @Test
    public void externalData() throws Exception {
        Indicator<Num> xlsClose = new ClosePriceIndicator(xls.getSeries());
        Indicator<Num> actualIndicator;

        actualIndicator = getIndicator(xlsClose, 1);
        assertIndicatorEquals(xls.getIndicator(1), actualIndicator);
        assertEquals(329.0, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(xlsClose, 3);
        assertIndicatorEquals(xls.getIndicator(3), actualIndicator);
        assertEquals(326.6333, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(xlsClose, 13);
        assertIndicatorEquals(xls.getIndicator(13), actualIndicator);
        assertEquals(327.7846, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);
    }

}
