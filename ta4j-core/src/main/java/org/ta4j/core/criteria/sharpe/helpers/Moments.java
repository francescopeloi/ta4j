package org.ta4j.core.criteria.sharpe.helpers;

public record Moments(double mean, double skewness, double kurtosis) {

    public static Moments from(double[] values) {
        if (values == null || values.length == 0) {
            return new Moments(Double.NaN, 0.0, 3.0);
        }

        var finiteValues = java.util.Arrays.stream(values)
                .filter(Double::isFinite)
                .toArray();

        if (finiteValues.length == 0) {
            return new Moments(Double.NaN, 0.0, 3.0);
        }

        var mean = org.apache.commons.math3.stat.StatUtils.mean(finiteValues);

        var skewness = new org.apache.commons.math3.stat.descriptive.moment.Skewness().evaluate(finiteValues);
        var excessKurtosis = new org.apache.commons.math3.stat.descriptive.moment.Kurtosis().evaluate(finiteValues);
        var pearsonKurtosis = excessKurtosis + 3.0;

        var safeSkewness = Double.isFinite(skewness) ? skewness : 0.0;
        var safeKurtosis = Double.isFinite(pearsonKurtosis) ? pearsonKurtosis : 3.0;

        return new Moments(mean, safeSkewness, safeKurtosis);
    }
}