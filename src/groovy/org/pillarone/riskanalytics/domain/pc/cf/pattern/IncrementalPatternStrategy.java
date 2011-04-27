package org.pillarone.riskanalytics.domain.pc.cf.pattern;

import org.joda.time.Period;
import org.pillarone.riskanalytics.core.parameterization.ConstrainedMultiDimensionalParameter;
import org.pillarone.riskanalytics.core.parameterization.IParameterObjectClassifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class IncrementalPatternStrategy extends AbstractPatternStrategy {

    public static final String INCREMENTAL = "Incremental";
    public static final String INCREMENTAL_PATTERN = "incrementalPattern";

    private ConstrainedMultiDimensionalParameter incrementalPattern;
    private PatternPacket pattern;

    public IParameterObjectClassifier getType() {
        return PatternStrategyType.INCREMENTAL;
    }

    public Map getParameters() {
        Map params = new HashMap(1);
        params.put(INCREMENTAL_PATTERN, incrementalPattern);
        return params;
    }

    public PatternPacket getPattern() {
        if (pattern == null) {
            int columnMonthIndex = incrementalPattern.getColumnIndex(PatternTableConstraints.MONTHS);
            List<Double> incrementalValues = getPatternValues(incrementalPattern, columnMonthIndex,
                    incrementalPattern.getColumnIndex(INCREMENTAL));
            List<Double> cumulativeValues = getCumulativePatternValues(incrementalValues);
            List<Period> cumulativePeriods = getCumulativePeriods(incrementalPattern, columnMonthIndex);
            pattern = new  PatternPacket(cumulativeValues, cumulativePeriods);
        }
        return pattern;
    }

    private List<Double> getCumulativePatternValues(List<Double> incrementalValues) {
        List<Double> cumulativeValues = new ArrayList<Double>(incrementalValues.size());
        double cumulative = 0d;
        for (Double increment : incrementalValues) {
            cumulative += increment;
            cumulativeValues.add(cumulative);
        }
        return cumulativeValues;
    }
}
