package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.nonproportional;

import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.simulation.IPeriodCounter;
import org.pillarone.riskanalytics.domain.pc.cf.claim.*;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.CededUnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoUtils;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.AbstractReinsuranceContract;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.ClaimStorage;

import java.util.List;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class UnifiedAdcLptContract extends AbstractReinsuranceContract implements INonPropReinsuranceContract {

    private double cededShare;
    private double attachmentPoint;
    private double limit;
    private double premium;

    private boolean isStartCoverPeriod = true;

    /**
     * used to trigger return of a single ceded claim packet
     */
    private IClaimRoot firstClaimKey;

    private ClaimCashflowPacket aggregateIncrementalCededReserveClaim;
    private ClaimCashflowPacket aggregateCumulativeCededReserveClaim;

    // the following three properties are required as claim updates are not received after an individual claim pattern
    // has ended
    private double cumulativeUltimate;
    private double cumulativeReported;
    private double cumulativePaid;

    private double previousCumulativeUltimateCeded;
    private double previousCumulativeReportedCeded;
    private double previousCumulativePaidCeded;
    private double previousReservesCeded;
    private double previousIBNRCeded;

    /**
     * All provided values have to be absolute! Scaling is done within the parameter strategy.
     *
     * @param cededShare
     * @param attachmentPoint
     * @param limit
     */
    public UnifiedAdcLptContract(double cededShare, double attachmentPoint, double limit, double premium) {
        this.cededShare = cededShare;
        this.attachmentPoint = attachmentPoint;
        this.limit = limit;
        this.premium = premium;
    }


    @Override
    public void initBasedOnAggregateCalculations(List<ClaimCashflowPacket> grossClaims, List<UnderwritingInfoPacket> grossUnderwritingInfo) {
        if (grossClaims.size() > 0) {
            firstClaimKey = grossClaims.get(0).getKeyClaim();   // used to make sure that only for the first claim calculateClaimCeded() will return a reserve claim
            ClaimCashflowPacket aggregateGrossClaim = new ClaimPacketAggregator().aggregate(grossClaims);
            if (aggregateCumulativeCededReserveClaim == null) {
                cumulativeUltimate = aggregateGrossClaim.totalCumulatedIndexed();
                cumulativeReported = aggregateGrossClaim.getReportedCumulatedIndexed();
                cumulativePaid = aggregateGrossClaim.getPaidCumulatedIndexed();
            }
            else {
                cumulativeUltimate += aggregateGrossClaim.totalIncrementalIndexed();
                cumulativeReported += aggregateGrossClaim.getReportedIncrementalIndexed();
                cumulativePaid += aggregateGrossClaim.getPaidIncrementalIndexed();
            }
            double cumulativeUltimateCeded = cededCumulativeValue(cumulativeUltimate);
            double cumulativeReportedCeded = cededCumulativeValue(cumulativeReported);
            double cumulativePaidCeded = cededCumulativeValue(cumulativePaid);
            double reserves = cumulativeUltimateCeded - cumulativePaidCeded;
            double ibnr = cumulativeUltimateCeded - cumulativeReportedCeded;
            if (aggregateCumulativeCededReserveClaim == null) {
                DateTime occurrenceDate = grossClaims.get(0).getUpdateDate().withDayOfYear(1);
                IClaimRoot keyClaim = new ClaimRoot(cumulativeUltimateCeded, ClaimType.AGGREGATED_RESERVES, occurrenceDate, occurrenceDate);
                double changeInReserves = reserves;
                double changeInIBNR = cumulativeUltimateCeded - cumulativeReportedCeded;
                int updatePeriod = grossClaims.get(0).getUpdatePeriod();
                aggregateCumulativeCededReserveClaim = new ClaimCashflowPacket(keyClaim, keyClaim, cumulativeUltimateCeded,
                        cumulativePaidCeded, cumulativePaidCeded, cumulativeReportedCeded, cumulativeReportedCeded,
                        reserves, changeInReserves, changeInIBNR, null, occurrenceDate, updatePeriod);
                aggregateIncrementalCededReserveClaim = ClaimUtils.scale(aggregateCumulativeCededReserveClaim, 1);
            }
            else {
                aggregateIncrementalCededReserveClaim = new ClaimCashflowPacket(
                        aggregateCumulativeCededReserveClaim.getKeyClaim(), aggregateCumulativeCededReserveClaim.getKeyClaim(),
                        0, aggregateCumulativeCededReserveClaim.nominalUltimate(),
                        cumulativePaidCeded - previousCumulativePaidCeded, cumulativePaidCeded,
                        cumulativeReportedCeded - previousCumulativeReportedCeded, cumulativeReportedCeded,
                        reserves, reserves - previousReservesCeded,
                        ibnr - previousIBNRCeded, null,
                        aggregateCumulativeCededReserveClaim.getOccurrenceDate(), aggregateCumulativeCededReserveClaim.getUpdatePeriod()
                );
            }
            previousCumulativeUltimateCeded = cumulativeUltimateCeded;
            previousCumulativeReportedCeded = cumulativeReportedCeded;
            previousCumulativePaidCeded = cumulativePaidCeded;
            previousReservesCeded = reserves;
            previousIBNRCeded = ibnr;
        }
    }

    public void initCededPremiumAllocation(List<ClaimCashflowPacket> cededClaims, List<UnderwritingInfoPacket> grossUnderwritingInfos) {
    }

    public ClaimCashflowPacket calculateClaimCeded(ClaimCashflowPacket grossClaim, ClaimStorage storage, IPeriodCounter periodCounter) {
        if (grossClaim.getKeyClaim() == firstClaimKey) {
            return aggregateIncrementalCededReserveClaim;
        }
        return null;
    }

    private double cededCumulativeValue(double grossCumulativeValue) {
        return cededShare * Math.max(0, Math.min(limit, -grossCumulativeValue - attachmentPoint));
    }

    /**
     * Not implemented as not required PMO-2235
     *
     * @param cededUnderwritingInfos
     * @param netUnderwritingInfos
     * @param coveredByReinsurers
     * @param fillNet                if true the net list is filled too
     */
    public void calculateUnderwritingInfo(List<CededUnderwritingInfoPacket> cededUnderwritingInfos,
                                          List<UnderwritingInfoPacket> netUnderwritingInfos,
                                          double coveredByReinsurers, boolean fillNet) {
        if (isStartCoverPeriod) {
            for (UnderwritingInfoPacket grossUnderwritingInfo : grossUwInfos) {
                CededUnderwritingInfoPacket cededUnderwritingInfo = CededUnderwritingInfoPacket.deriveCededPacketForNonPropContract(
                        grossUnderwritingInfo, contractMarker, -premium, -premium, 0);
                UnderwritingInfoUtils.applyMarkers(grossUnderwritingInfo, cededUnderwritingInfo);
                cededUwInfos.add(cededUnderwritingInfo);
                cededUnderwritingInfos.add(cededUnderwritingInfo);
                if (fillNet && isStartCoverPeriod) {
                    netUnderwritingInfos.add(grossUnderwritingInfo.getNet(cededUnderwritingInfo, false));
                }
            }
        }
        isStartCoverPeriod = false;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ceded share: ");
        builder.append(cededShare);
        builder.append("attachment point: ");
        builder.append(attachmentPoint);
        builder.append(", limit: ");
        builder.append(limit);
        return builder.toString();
    }

}
