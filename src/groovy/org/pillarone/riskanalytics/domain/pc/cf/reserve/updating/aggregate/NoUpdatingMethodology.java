package org.pillarone.riskanalytics.domain.pc.cf.reserve.updating.aggregate;

import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.parameterization.AbstractParameterObject;
import org.pillarone.riskanalytics.core.parameterization.IParameterObjectClassifier;
import org.pillarone.riskanalytics.core.simulation.IPeriodCounter;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimRoot;
import org.pillarone.riskanalytics.domain.pc.cf.pattern.PatternPacket;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class NoUpdatingMethodology extends AbstractParameterObject implements IAggregateUpdatingMethodologyStrategy {

    public Map getParameters() {
        return Collections.emptyMap();
    }

    public IParameterObjectClassifier getType() {
        return AggregateUpdatingMethodologyStrategyType.NONE;
    }


    public List<ClaimRoot> updatingUltimate(List<ClaimRoot> baseClaims, IAggregateActualClaimsStrategy actualClaims,
                                            IPeriodCounter periodCounter, DateTime updateDate, List<PatternPacket> patterns) {
        return baseClaims;
    }
}