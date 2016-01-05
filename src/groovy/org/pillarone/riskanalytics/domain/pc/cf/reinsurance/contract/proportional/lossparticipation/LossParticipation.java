package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.proportional.lossparticipation;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimUtils;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoUtils;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.ClaimStorage;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.limit.ILimitStrategy;

import java.util.List;
import java.util.SortedMap;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class LossParticipation implements ILossParticipation {

    private static Log LOG = LogFactory.getLog(LossParticipation.class);

    private SortedMap<Double, Double> table;
    private AggregateValues cumulatedGross;
    private AggregateValues cumulatedGrossPreviousPeriod;
    private AggregateValues cumulatedCeded;
    private AggregateValues cumulatedCededPreviousPeriod;
    private ILimitStrategy limit;

    public LossParticipation(SortedMap<Double, Double> table) {
        this.table = table;
        cumulatedGross = new AggregateValues();
    }

    public boolean noLossParticipation() {
        return table.isEmpty();
    }

    SortedMap<Double, Double> getLossRatios() {
        return table;
    }

    public void initPeriod(List<ClaimCashflowPacket> claims, List<UnderwritingInfoPacket> underwritingInfos, ILimitStrategy limit) {
        cumulatedGrossPreviousPeriod = new AggregateValues(cumulatedGross);
        cumulatedGross.update(claims, underwritingInfos);
        cumulatedCededPreviousPeriod = new AggregateValues();
        cumulatedCeded = null;
        this.limit = limit;
    }

    private class AggregateValues {

        double ultimate;
        double reported;
        double paid;

        double premiumWritten;
        double premiumPaid;

        private AggregateValues() {
        }

        private AggregateValues(AggregateValues copy) {
            this.ultimate = copy.ultimate;
            this.reported = copy.reported;
            this.paid = copy.paid;
            this.premiumWritten = copy.premiumWritten;
            this.premiumPaid = copy.premiumPaid;
        }

        private AggregateValues(double ultimate, double reported, double paid) {
            this.ultimate = ultimate;
            this.reported = reported;
            this.paid = paid;
        }

        private void update(List<ClaimCashflowPacket> claims, List<UnderwritingInfoPacket> underwritingInfos) {
            ClaimCashflowPacket totalClaim =  ClaimUtils.sum(ClaimUtils.castCCPListToPacketList(claims), true);

            if (totalClaim != null) {
                ultimate += totalClaim.ultimate() + totalClaim.developmentResultCumulative();
                reported += totalClaim.getReportedIncrementalIndexed();
                paid += totalClaim.getPaidIncrementalIndexed();
            }
            // add up previous totalUwInfo as UnderwritingInfoPacket does not contain cummulated values
            UnderwritingInfoPacket updateUwInfo = UnderwritingInfoUtils.aggregate(underwritingInfos);
            if (updateUwInfo != null) {
                premiumWritten += updateUwInfo.getPremiumWritten();
                premiumPaid += updateUwInfo.getPremiumPaid();
            }
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("ultimate: ");
            builder.append(ultimate);
            builder.append(", reported: ");
            builder.append(reported);
            builder.append(", paid: ");
            builder.append(paid);
            return builder.toString();
        }
    }

    public ClaimCashflowPacket cededClaim(double quotaShare, ClaimCashflowPacket grossClaim, ClaimStorage storage, boolean adjustExposureInfo) {
        lazyInitCumulatedCeded(quotaShare);
        double cededUltimate = cumulatedGross.ultimate == 0 ? 0 : grossClaim.developedUltimate() / cumulatedGross.ultimate * cumulatedCeded.ultimate;
        double cededReported = cumulatedGross.reported == 0 ? 0 : grossClaim.getReportedCumulatedIndexed() / cumulatedGross.reported * cumulatedCeded.reported;
        double cededPaid = cumulatedGross.paid == 0 ? 0 : grossClaim.getPaidCumulatedIndexed() / cumulatedGross.paid * cumulatedCeded.paid;
        ClaimCashflowPacket packet = ClaimUtils.cededClaim(grossClaim, storage, cededUltimate, cededUltimate, cededReported, cededPaid, adjustExposureInfo);
        return packet;
    }

    private void lazyInitCumulatedCeded(double quotaShare) {
        if (cumulatedCeded == null) {
            double cumulatedCededUltimate = 0;
            double cumulatedCededReported = 0;
            double cumulatedCededPaid = 0;
            if (cumulatedGross.premiumWritten != 0) {
                double adjustedLossRatio = lossRatioAdjustedByLossParticipation(limit.appliedLimit(cumulatedGross.ultimate) / cumulatedGross.premiumWritten);
                cumulatedCededUltimate = cumulatedGross.premiumWritten * quotaShare * adjustedLossRatio;
                adjustedLossRatio = lossRatioAdjustedByLossParticipation(limit.appliedLimit(cumulatedGross.reported) / cumulatedGross.premiumWritten);
                cumulatedCededReported = cumulatedGross.premiumWritten * quotaShare * adjustedLossRatio;
            }
            else {
                LOG.warn("trivial results for loss participation as cumulated premium written gross is 0");
            }
            if (cumulatedGross.premiumPaid != 0) {
                double adjustedLossRatio = lossRatioAdjustedByLossParticipation(limit.appliedLimit(cumulatedGross.paid) / cumulatedGross.premiumPaid);
                cumulatedCededPaid = cumulatedGross.premiumPaid * quotaShare * adjustedLossRatio;
            }
            else {
                LOG.warn("trivial results for loss participation as cumulated premium paid gross is 0");
            }
            cumulatedCeded = new AggregateValues(cumulatedCededUltimate, cumulatedCededReported, cumulatedCededPaid);
        }
    }

    public double lossParticipation(double lossRatio) {
        double previousLossRatio = 0d;
        double previousParticipation = 0d;
        double summedParticipation = 0d;
        for (SortedMap.Entry<Double, Double> item : table.entrySet()) {
            double incrementalLossRatio = item.getKey() - previousLossRatio;
            if (lossRatio > item.getKey()) {
                summedParticipation += incrementalLossRatio * previousParticipation;
                previousLossRatio = item.getKey();
                previousParticipation = item.getValue();
            }
        }
        if (lossRatio > previousLossRatio) {
            summedParticipation += (lossRatio - previousLossRatio) * previousParticipation;
        }
        return summedParticipation;
    }

    public double lossRatioAdjustedByLossParticipation(double lossRatio) {
        double lossParticipation = lossParticipation(Math.abs(lossRatio));
        return Math.abs(lossRatio) - lossParticipation;
    }
}
