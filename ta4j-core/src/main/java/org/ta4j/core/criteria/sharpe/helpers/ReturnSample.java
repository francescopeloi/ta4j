package org.ta4j.core.criteria.sharpe.helpers;

import org.ta4j.core.num.Num;

public record ReturnSample(Num excessReturn, double deltaYears) {
}