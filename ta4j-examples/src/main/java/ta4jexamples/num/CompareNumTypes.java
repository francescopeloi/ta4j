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
package ta4jexamples.num;

import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.CombineIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.IsEqualRule;
import org.ta4j.core.rules.UnderIndicatorRule;

public class CompareNumTypes {

    private static final int NUMBARS = 10000;

    public static void main(String args[]) {
        BaseBarSeriesBuilder barSeriesBuilder = new BaseBarSeriesBuilder();
        BarSeries seriesD = barSeriesBuilder.withName("Sample Series Double    ")
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();
        BarSeries seriesP = barSeriesBuilder.withName("Sample Series DecimalNum 32")
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();
        BarSeries seriesPH = barSeriesBuilder.withName("Sample Series DecimalNum 256")
                .withNumFactory(DecimalNumFactory.getInstance(256))
                .build();

        var now = Instant.now();
        int[] randoms = new Random().ints(NUMBARS, 80, 100).toArray();
        for (int i = 0; i < randoms.length; i++) {
            Instant date = now.minusSeconds(NUMBARS - i);
            seriesD.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(date)
                    .openPrice(randoms[i])
                    .closePrice(randoms[i] + 21)
                    .highPrice(randoms[i] - 21)
                    .lowPrice(randoms[i] - 5)
                    .add();
            seriesP.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(date)
                    .openPrice(randoms[i])
                    .closePrice(randoms[i] + 21)
                    .highPrice(randoms[i] - 21)
                    .lowPrice(randoms[i] - 5)
                    .add();
            seriesPH.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(date)
                    .openPrice(randoms[i])
                    .closePrice(randoms[i] + 21)
                    .highPrice(randoms[i] - 21)
                    .lowPrice(randoms[i] - 5)
                    .add();
        }
        Num D = DecimalNum.valueOf(test(seriesD).toString(), new MathContext(256));
        Num P = DecimalNum.valueOf(test(seriesP).toString(), new MathContext(256));
        Num standard = DecimalNum.valueOf(test(seriesPH).toString(), new MathContext(256));
        System.out.println(seriesD.getName() + " error: "
                + D.minus(standard).dividedBy(standard).multipliedBy(DecimalNum.valueOf(100)));
        System.out.println(seriesP.getName() + " error: "
                + P.minus(standard).dividedBy(standard).multipliedBy(DecimalNum.valueOf(100)));
    }

    public static Num test(BarSeries series) {
        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePriceIndicator, 100);
        MACDIndicator macdIndicator = new MACDIndicator(rsi);
        EMAIndicator ema = new EMAIndicator(rsi, 12);
        EMAIndicator emaLong = new EMAIndicator(rsi, 26);
        CombineIndicator macdIndicator2 = CombineIndicator.minus(ema, emaLong);

        Rule entry = new IsEqualRule(macdIndicator, macdIndicator2);
        Rule exit = new UnderIndicatorRule(new LowPriceIndicator(series), new HighPriceIndicator(series));
        Strategy strategy1 = new BaseStrategy(entry, exit); // enter/exit every tick

        long start = System.currentTimeMillis();
        BarSeriesManager manager = new BarSeriesManager(series);
        TradingRecord record1 = manager.run(strategy1);
        GrossReturnCriterion totalReturn1 = new GrossReturnCriterion();
        Num returnResult1 = totalReturn1.calculate(series, record1);
        long end = System.currentTimeMillis();

        System.out.printf("[%s]\n" + "    -Time:   %s ms.\n" + "    -Profit: %s \n" + "    -Bars:   %s\n \n",
                series.getName(), (end - start), returnResult1, series.getBarCount());
        return returnResult1;
    }
}
