package org.ta4j.core.criteria.pnl;

import org.ta4j.core.Position;
import org.ta4j.core.num.Num;

/** Gross profit and loss percentage criterion. */
public class GrossProfitLossPercentageCriterion extends AbstractProfitLossPercentageCriterion {

    @Override
    protected Num profit(Position position) {
        return position.getGrossProfit();
    }
}
