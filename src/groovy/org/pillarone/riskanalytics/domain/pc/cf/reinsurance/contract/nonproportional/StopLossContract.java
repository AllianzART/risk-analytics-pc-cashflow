package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.nonproportional;

import org.pillarone.riskanalytics.core.simulation.IPeriodCounter;
import org.pillarone.riskanalytics.domain.pc.cf.claim.BasedOnClaimProperty;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimUtils;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.CededUnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoUtils;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.AbstractReinsuranceContract;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.AggregateEventClaimsStorage;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.ClaimStorage;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.allocation.IRIPremiumSplitStrategy;

import java.util.List;

/**
 *
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class StopLossContract extends AbstractReinsuranceContract implements INonPropReinsuranceContract {

    private double cededPremiumFixed;
    /** used to make sure that fixed premium is paid only in first period */
    private boolean isStartCoverPeriod = true;

    /** Strategy to allocate the ceded premium to the different lines of business  */
    // todo: business object instead of parameter
    private IRIPremiumSplitStrategy riPremiumSplit;

    private ThresholdStore periodAttachmentPoint;
    private ThresholdStore periodLimit;

    // todo(sku): check if reset is required!
    private AggregateEventClaimsStorage aggregateClaimStorage;

    /**
     * All provided values have to be absolute! Scaling is done within the parameter strategy.
     * @param cededPremiumFixed
     * @param attachmentPoint
     * @param limit
     * @param riPremiumSplit
     */
    public StopLossContract(double cededPremiumFixed, double attachmentPoint, double limit,
                            IRIPremiumSplitStrategy riPremiumSplit) {
        this.cededPremiumFixed = cededPremiumFixed;
        this.riPremiumSplit = riPremiumSplit;
        periodAttachmentPoint = new ThresholdStore(attachmentPoint);
        periodLimit = new ThresholdStore(limit);
    }

    @Override
    public void initBasedOnAggregateCalculations(List<ClaimCashflowPacket> grossClaims, List<UnderwritingInfoPacket> grossUnderwritingInfo) {
        if (aggregateClaimStorage != null) {
            aggregateClaimStorage.resetIncrementsAndFactors();
        }
        if (aggregateClaimStorage == null) {
            aggregateClaimStorage = new AggregateEventClaimsStorage();
        }
        aggregateClaimStorage.add(ClaimUtils.sum(ClaimUtils.castCCPListToPacketList(grossClaims) , true));

        cededFactor(BasedOnClaimProperty.ULTIMATE_UNINDEXED, aggregateClaimStorage);
        cededFactor(BasedOnClaimProperty.ULTIMATE_INDEXED, aggregateClaimStorage);
        cededFactor(BasedOnClaimProperty.REPORTED, aggregateClaimStorage);
        cededFactor(BasedOnClaimProperty.PAID, aggregateClaimStorage);
    }

    // todo(sku): try to call this function only if isStartCoverPeriod
    public void initCededPremiumAllocation(List<ClaimCashflowPacket> cededClaims, List<UnderwritingInfoPacket> grossUnderwritingInfos) {
        riPremiumSplit.initSegmentShares(cededClaims, grossUnderwritingInfos);
    }

    public ClaimCashflowPacket calculateClaimCeded(ClaimCashflowPacket grossClaim, ClaimStorage storage, IPeriodCounter periodCounter) {
        ClaimCashflowPacket cededClaim = ClaimUtils.getCededClaim(grossClaim, storage,
                aggregateClaimStorage.getCededFactor(BasedOnClaimProperty.ULTIMATE_UNINDEXED),
                aggregateClaimStorage.getCededFactor(BasedOnClaimProperty.ULTIMATE_INDEXED),
                aggregateClaimStorage.getCededFactor(BasedOnClaimProperty.REPORTED),
                aggregateClaimStorage.getCededFactor(BasedOnClaimProperty.PAID), false);
        add(grossClaim, cededClaim);
        return cededClaim;
    }

    private void cededFactor(BasedOnClaimProperty claimPropertyBase, AggregateEventClaimsStorage storage) {
        double claimPropertyCumulated = storage.getCumulated(claimPropertyBase);
        double claimPropertyIncremental = storage.getIncremental(claimPropertyBase);
        double attachmentPoint = periodAttachmentPoint.get(claimPropertyBase);
        double limit = periodLimit.get(claimPropertyBase);
        double cededCumulated = Math.min(Math.max(-claimPropertyCumulated - attachmentPoint, 0), limit);
        double cededIncremental = cededCumulated - storage.getCumulatedCeded(claimPropertyBase);
        double factor = claimPropertyIncremental == 0 ? 0 : cededIncremental / claimPropertyIncremental;
        storage.setCededFactor(claimPropertyBase, factor);
        storage.update(claimPropertyBase, cededIncremental);
    }

    public void calculateUnderwritingInfo(List<CededUnderwritingInfoPacket> cededUnderwritingInfos,
                                          List<UnderwritingInfoPacket> netUnderwritingInfos,
                                          double coveredByReinsurers, boolean fillNet) {
        if (isStartCoverPeriod) {
            initCededPremiumAllocation(cededClaims, grossUwInfos);
        }
        for (UnderwritingInfoPacket grossUnderwritingInfo : grossUwInfos) {
            double cededPremiumFixedShare = cededPremiumFixed * riPremiumSplit.getShare(grossUnderwritingInfo);
            cededPremiumFixedShare *= coveredByReinsurers;
            double cededPremiumVariable = 0;
            cededPremiumVariable *= coveredByReinsurers;
            double cededPremium = isStartCoverPeriod ? cededPremiumFixedShare + cededPremiumVariable : cededPremiumVariable;

            CededUnderwritingInfoPacket cededUnderwritingInfo = CededUnderwritingInfoPacket.deriveCededPacketForNonPropContract(
                    grossUnderwritingInfo, contractMarker, -cededPremium, isStartCoverPeriod ? -cededPremiumFixedShare : 0,
                    -cededPremiumVariable);
            UnderwritingInfoUtils.applyMarkers(grossUnderwritingInfo, cededUnderwritingInfo);
            cededUwInfos.add(cededUnderwritingInfo);
            cededUnderwritingInfos.add(cededUnderwritingInfo);
            if (fillNet && isStartCoverPeriod) {
                netUnderwritingInfos.add(grossUnderwritingInfo.getNet(cededUnderwritingInfo, false));
            }
        }
        isStartCoverPeriod = false;
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("attachment point: ");
        buffer.append(periodAttachmentPoint);
        buffer.append(", limit: ");
        buffer.append(periodLimit);
        return buffer.toString();
    }

}
