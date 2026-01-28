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

import java.util.Objects;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Shared contract for performance indicators derived from trading records.
 *
 * @since 0.22.2
 */
public interface PerformanceIndicator extends Indicator<Num> {

    /**
     * Calculates indicator values based on the provided trading record.
     *
     * @param tradingRecord        the trading record
     * @param finalIndex           index up until values of open positions are
     *                             considered
     * @param openPositionHandling how to handle the last open position
     *
     * @since 0.22.2
     */
    default void calculate(TradingRecord tradingRecord, int finalIndex, OpenPositionHandling openPositionHandling) {
        Objects.requireNonNull(tradingRecord);
        Objects.requireNonNull(openPositionHandling);
        tradingRecord.getPositions().forEach(position -> calculatePosition(position, finalIndex));
        handleLastPosition(tradingRecord, finalIndex, openPositionHandling);
    }

    /**
     * Returns the equity curve mode that influences open position handling.
     *
     * @return the equity curve mode
     *
     * @since 0.22.2
     */
    EquityCurveMode getEquityCurveMode();

    /**
     * Calculates indicator values for a single position.
     *
     * @param position   the position
     * @param finalIndex index up until values of open positions are considered
     *
     * @since 0.22.2
     */
    void calculatePosition(Position position, int finalIndex);

    private void handleLastPosition(TradingRecord tradingRecord, int finalIndex,
            OpenPositionHandling openPositionHandling) {
        var effectiveOpenPositionHandling = getEffectiveOpenPositionHandling(openPositionHandling);
        var currentPosition = tradingRecord.getCurrentPosition();
        if (effectiveOpenPositionHandling == OpenPositionHandling.MARK_TO_MARKET && currentPosition != null
                && currentPosition.isOpened()) {
            calculatePosition(currentPosition, finalIndex);
        }
    }

    /**
     * Derives the open-position handling from the equity curve mode to keep
     * realized-only curves from leaking unrealized P&amp;L into the calculation.
     *
     * <p>
     * When the equity curve is realized-only, we force
     * {@link OpenPositionHandling#IGNORE} regardless of the caller preference. For
     * all other modes we defer to the requested handling so callers can opt into
     * mark-to-market behavior.
     * </p>
     *
     * @param openPositionHandling the requested handling for open positions
     * @return the effective handling aligned with the equity curve mode
     */
    private OpenPositionHandling getEffectiveOpenPositionHandling(OpenPositionHandling openPositionHandling) {
        return getEquityCurveMode() == EquityCurveMode.REALIZED ? OpenPositionHandling.IGNORE : openPositionHandling;
    }

}
