package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PivotLowRuleTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BarSeries series;
    private PivotLowRule rule;

    public PivotLowRuleTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(new MockBarBuilderFactory()).build();
        addBar(1, 1);
        addBar(2, 1.5);
        addBar(3, 0.7);
        addBar(2, 0.5);
        addBar(4, 0.8);
        addBar(2, 1.0);
        addBar(1, 0.9);

        rule = new PivotLowRule(new LowPriceIndicator(series), 1, 1);
    }

    private void addBar(double high, double low) {
        series.barBuilder().highPrice(high).lowPrice(low).closePrice(high).add();
    }

    @Test
    public void isSatisfied() {
        assertFalse(rule.isSatisfied(0));
        assertFalse(rule.isSatisfied(1));
        assertFalse(rule.isSatisfied(2));
        assertFalse(rule.isSatisfied(3));
        assertTrue(rule.isSatisfied(4));
        assertFalse(rule.isSatisfied(5));
        assertFalse(rule.isSatisfied(6));
    }
}
