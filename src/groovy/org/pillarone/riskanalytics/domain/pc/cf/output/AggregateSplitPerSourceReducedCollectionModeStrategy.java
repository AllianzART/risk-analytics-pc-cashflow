package org.pillarone.riskanalytics.domain.pc.cf.output;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;

import java.util.Arrays;
import java.util.List;

/**
 * Component has the same purpose as AggregateDrillDownCollectingModeStrategy in the property casualty plugin and a similar implementation
 *
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class AggregateSplitPerSourceReducedCollectionModeStrategy extends AggregateSplitPerSourceCollectionModeStrategy {

    protected static Log LOG = LogFactory.getLog(AggregateSplitPerSourceReducedCollectionModeStrategy.class);

    static final String IDENTIFIER = "SPLIT_PER_SOURCE_REDUCED";

    List<String> collectedFieldList = Arrays.asList("ultimate", "reportedIncrementalIndexed");

    @Override
    public List<String> filter() {
        return collectedFieldList;
    }

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    public boolean isCompatibleWith(Class packetClass) {
        return ClaimCashflowPacket.class.isAssignableFrom(packetClass);
    }
}
