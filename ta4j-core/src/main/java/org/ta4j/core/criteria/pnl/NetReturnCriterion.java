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
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.num.Num;

/**
 * Return (in percentage) criterion, returned in decimal format.
 *
 * <p>
 * This criterion uses the net return of the positions; trading costs are
 * deducted from the calculation. It represents the percentage change including
 * the base value.
 *
 * <p>
 * The return of the provided {@link Position position(s)} over the provided
 * {@link BarSeries series}.
 */
public class NetReturnCriterion extends AbstractReturnCriterion {
    public NetReturnCriterion() {
        super();
    }

    public NetReturnCriterion(boolean addBase) {
        super(addBase);
    }

    @Override
    protected Num calculateReturn(BarSeries series, Position position) {
        var one = series.numFactory().one();

        // open positions are ignored and return the base value
        if (!position.isClosed()) {
            return one;
        }

        var entry = position.getEntry();
        var exit = position.getExit();

        // default to an amount of one if none was specified
        var amount = entry.getAmount();
        if (amount.isNaN()) {
            amount = one;
        }

        // use bar close prices if the trade price is NaN
        var entryPrice = entry.getPricePerAsset(series);
        var exitPrice = exit.getPricePerAsset(series);

        var entryValue = entryPrice.multipliedBy(amount);
        if (entryValue.isZero()) {
            return one;
        }

        Num grossProfit;
        if (entry.isBuy()) {
            grossProfit = exitPrice.minus(entryPrice).multipliedBy(amount);
        } else {
            grossProfit = entryPrice.minus(exitPrice).multipliedBy(amount);
        }

        var txCost = entry.getCostModel()
                .calculate(entryPrice, amount)
                .plus(exit.getCostModel().calculate(exitPrice, amount));

        var profit = grossProfit.minus(txCost);

        return profit.dividedBy(entryValue).plus(one);
    }

}
