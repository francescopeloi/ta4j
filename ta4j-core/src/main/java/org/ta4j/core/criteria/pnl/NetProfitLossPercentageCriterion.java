package org.ta4j.core.criteria.pnl;

import org.ta4j.core.Position;
import org.ta4j.core.num.Num;

/** Net profit and loss percentage criterion. */
public class NetProfitLossPercentageCriterion extends AbstractProfitLossPercentageCriterion {

    @Override
    protected Num profit(Position position) {
        return position.getProfit();
    }
}
