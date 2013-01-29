package org.pillarone.riskanalytics.domain.pc.cf.output;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pillarone.riskanalytics.core.output.ICollectingModeStrategy;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;

/**
 * This collecting mode strategy calculates the premium and reserve risk
 *
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 * @deprecated Use general {@link SplitAndFilterCollectionModeStrategy} class for collecting results.
 */
public class AggregateIncludingPremiumReserveRiskCollectingModeStrategy extends AggregateSplitByInceptionDateCollectingModeStrategy {

    protected static Log LOG = LogFactory.getLog(AggregateIncludingPremiumReserveRiskCollectingModeStrategy.class);

    static final String IDENTIFIER = "INCLUDING_PREMIUM_RESERVE_RISK";

    protected boolean splitByInceptionPeriod() {
        return false;
    }

    public String getIdentifier() {
        return IDENTIFIER;
    }

    public boolean isCompatibleWith(Class packetClass) {
        return ClaimCashflowPacket.class.isAssignableFrom(packetClass);
    }
}
