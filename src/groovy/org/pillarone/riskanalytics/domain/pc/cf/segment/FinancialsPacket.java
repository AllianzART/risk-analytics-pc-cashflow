package org.pillarone.riskanalytics.domain.pc.cf.segment;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.packets.MultiValuePacket;
import org.pillarone.riskanalytics.core.packets.Packet;
import org.pillarone.riskanalytics.core.simulation.IPeriodCounter;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimType;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimUtils;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ReserveRoot;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.CededUnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoUtils;

import java.util.*;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class FinancialsPacket extends MultiValuePacket {

    private DateTime inceptionDate;
    private double commission;

    private Financials gross = new Financials();
    private Financials ceded = new Financials();
    private Financials net = new Financials();

    public FinancialsPacket() {
    }

    public FinancialsPacket(List<UnderwritingInfoPacket> grossUwInfos, List<UnderwritingInfoPacket> netUwInfos,
                            List<CededUnderwritingInfoPacket> cededUwInfos, List<ClaimCashflowPacket> grossClaims,
                            List<ClaimCashflowPacket> netClaims, List<ClaimCashflowPacket> cededClaims,
                            boolean singlePeriod, Boolean occurrenceInCurrentPeriod) {
        if (singlePeriod) {
            if (!grossClaims.isEmpty()) {
                inceptionDate = grossClaims.get(0).getBaseClaim().getExposureStartDate();
            }
            else if (!netClaims.isEmpty()) {
                inceptionDate = netClaims.get(0).getBaseClaim().getExposureStartDate();
            }
            else if (!cededClaims.isEmpty()) {
                inceptionDate = cededClaims.get(0).getBaseClaim().getExposureStartDate();
            }
            else if (!grossUwInfos.isEmpty()) {
                inceptionDate = grossUwInfos.get(0).getExposure().getInceptionDate();
            }
            else if (!cededUwInfos.isEmpty()) {
                inceptionDate = cededUwInfos.get(0).getExposure().getInceptionDate();
            }
            else if (!netUwInfos.isEmpty()) {
                inceptionDate = netUwInfos.get(0).getExposure().getInceptionDate();
            }
        }

        gross = new Financials(grossUwInfos, grossClaims, occurrenceInCurrentPeriod, Boolean.FALSE);
        ceded = new Financials();
        if (!cededUwInfos.isEmpty()) {
            CededUnderwritingInfoPacket aggregateCededUwInfoPacket = UnderwritingInfoUtils.aggregateCeded(cededUwInfos);
            ceded.premiumWritten = aggregateCededUwInfoPacket.getPremiumWritten();
            ceded.premiumPaid = aggregateCededUwInfoPacket.getPremiumPaid();
            commission = aggregateCededUwInfoPacket.getCommission();
        }
        net = new Financials(netUwInfos, netClaims, occurrenceInCurrentPeriod, Boolean.TRUE);
        ceded.initClaimRelatedFigures(cededClaims, occurrenceInCurrentPeriod, Boolean.TRUE);
    }

    public static List<FinancialsPacket> getFinancialsPacketsByInceptionPeriod(
            List<UnderwritingInfoPacket> grossUwInformation, List<UnderwritingInfoPacket> netUwInformation,
            List<CededUnderwritingInfoPacket> cededUwInformation, List<ClaimCashflowPacket> grossClaims,
            List<ClaimCashflowPacket> netClaims, List<ClaimCashflowPacket> cededClaims, IPeriodCounter periodCounter) {
        ListMultimap<Integer, ClaimCashflowPacket> grossClaimsByPeriod = ArrayListMultimap.create();
        ListMultimap<Integer, ClaimCashflowPacket> netClaimsByPeriod = ArrayListMultimap.create();
        ListMultimap<Integer, ClaimCashflowPacket> cededClaimsByPeriod = ArrayListMultimap.create();
        ListMultimap<Integer, UnderwritingInfoPacket> grossUwInfoByPeriod = ArrayListMultimap.create();
        ListMultimap<Integer, CededUnderwritingInfoPacket> cededUwInfoByPeriod = ArrayListMultimap.create();
        ListMultimap<Integer, UnderwritingInfoPacket> netUwInfoByPeriod = ArrayListMultimap.create();
        Set<Integer> periods = new HashSet<Integer>();
        for (ClaimCashflowPacket claim : grossClaims) {
            if (hasReserveGeneratorOrigin(claim, periodCounter)) continue;
            int period = claim.getBaseClaim().getInceptionPeriod(periodCounter);
            periods.add(period);
            grossClaimsByPeriod.put(period, claim);
        }
        for (ClaimCashflowPacket claim : netClaims) {
            if (hasReserveGeneratorOrigin(claim, periodCounter)) continue;
            int period = claim.getBaseClaim().getInceptionPeriod(periodCounter);
            periods.add(period);
            netClaimsByPeriod.put(period, claim);
        }
        for (ClaimCashflowPacket claim : cededClaims) {
            if (hasReserveGeneratorOrigin(claim, periodCounter)) continue;
            int period = claim.getBaseClaim().getInceptionPeriod(periodCounter);
            periods.add(period);
            cededClaimsByPeriod.put(period, claim);
        }
        for (UnderwritingInfoPacket uwInfo : grossUwInformation) {
            periods.add(uwInfo.getExposure().getInceptionPeriod());
            grossUwInfoByPeriod.put(uwInfo.getExposure().getInceptionPeriod(), uwInfo);
        }
        for (CededUnderwritingInfoPacket uwInfo : cededUwInformation) {
            periods.add(uwInfo.getExposure().getInceptionPeriod());
            cededUwInfoByPeriod.put(uwInfo.getExposure().getInceptionPeriod(), uwInfo);
        }
        for (UnderwritingInfoPacket uwInfo : netUwInformation) {
            periods.add(uwInfo.getExposure().getInceptionPeriod());
            netUwInfoByPeriod.put(uwInfo.getExposure().getInceptionPeriod(), uwInfo);
        }
        List<FinancialsPacket> packets = new ArrayList<FinancialsPacket>();
        for (Integer period : periods) {
            FinancialsPacket packet = new FinancialsPacket(grossUwInfoByPeriod.get(period), netUwInfoByPeriod.get(period),
                    cededUwInfoByPeriod.get(period), grossClaimsByPeriod.get(period), netClaimsByPeriod.get(period),
                    cededClaimsByPeriod.get(period), true, period == periodCounter.currentPeriodIndex());
            packets.add(packet);
        }
        return packets;
    }

    private static boolean hasReserveGeneratorOrigin(ClaimCashflowPacket claim, IPeriodCounter periodCounter) {
        if (!claim.getClaimType().equals(ClaimType.AGGREGATED_RESERVES)) return false;
        return periodCounter.startOfFirstPeriod().isAfter(claim.getKeyClaim().getOccurrenceDate());
    }

    public FinancialsPacket plus(FinancialsPacket other) {
        if (other == null) return this;
        commission += other.commission;
        gross = gross.plus(other.gross);
        net = net.plus(other.net);
        ceded = ceded.plus(other.ceded);
        return this;
    }

    @Override
    public Packet copy() {
        FinancialsPacket copy = (FinancialsPacket) super.copy();
        copy.gross = gross.copy();
        copy.net = net.copy();
        copy.ceded = ceded.copy();
        copy.inceptionDate = new DateTime(inceptionDate);
        return copy;
    }

    public final static String COMMISSION = "commission";

    public final static String GROSS_CLAIM_PAID = "grossClaimPaid";
    public final static String GROSS_CLAIM_ULTIMATE = "grossClaimUltimate";
    public final static String GROSS_PREMIUM_PAID = "grossPremiumPaid";
    public final static String GROSS_PREMIUM_WRITTEN = "grossPremiumWritten";
    public final static String GROSS_CASHFLOW = "grossCashflow";
    public final static String GROSS_BEST_ESTIMATE = "grossBestEstimate";
    public final static String GROSS_PREMIUM_RISK = "grossPremiumRisk";
    public final static String GROSS_RESERVE_RISK = "grossReserveRisk";
    public final static String GROSS_PREMIUM_RESERVE_RISK = "grossPremiumReserveRisk";
    public final static String GROSS_LOSS_RATIO_WRITTEN_ULTIMATE = "grossLossRatioWrittenUltimate";
    public final static String GROSS_LOSS_RATIO_PAID_PAID = "grossLossRatioPaidPaid";

    public final static String NET_CLAIM_PAID = "netClaimPaid";
    public final static String NET_CLAIM_ULTIMATE = "netClaimUltimate";
    public final static String NET_PREMIUM_PAID = "netPremiumPaid";
    public final static String NET_PREMIUM_WRITTEN = "netPremiumWritten";
    public final static String NET_CASHFLOW = "netCashflow";
    public final static String NET_BEST_ESTIMATE = "netBestEstimate";
    public final static String NET_PREMIUM_RISK = "netPremiumRisk";
    public final static String NET_RESERVE_RISK = "netReserveRisk";
    public final static String NET_PREMIUM_RESERVE_RISK = "netPremiumReserveRisk";
    public final static String NET_LOSS_RATIO_WRITTEN_ULTIMATE = "netLossRatioWrittenUltimate";
    public final static String NET_LOSS_RATIO_PAID_PAID = "netLossRatioPaidPaid";

    public final static String CEDED_CLAIM_PAID = "cededClaimPaid";
    public final static String CEDED_CLAIM_ULTIMATE = "cededClaimUltimate";
    public final static String CEDED_PREMIUM_PAID = "cededPremiumPaid";
    public final static String CEDED_PREMIUM_WRITTEN = "cededPremiumWritten";
    public final static String CEDED_CASHFLOW = "cededCashflow";
    public final static String CEDED_BEST_ESTIMATE = "cededBestEstimate";
    public final static String CEDED_PREMIUM_RISK = "cededPremiumRisk";
    public final static String CEDED_RESERVE_RISK = "cededReserveRisk";
    public final static String CEDED_PREMIUM_RESERVE_RISK = "cededPremiumReserveRisk";
    public final static String CEDED_LOSS_RATIO_WRITTEN_ULTIMATE = "cededLossRatioWrittenUltimate";
    public final static String CEDED_LOSS_RATIO_PAID_PAID = "cededLossRatioPaidPaid";


    @Override
    public Map<String, Number> getValuesToSave() throws IllegalAccessException {
        Map<String, Number> valuesToSave = new HashMap<String, Number>();
        valuesToSave.put(COMMISSION, commission);    // this and missing default c'tor (final!) leads to failure during result tree building

        valuesToSave.put(GROSS_CLAIM_PAID, gross.claimPaid);
        valuesToSave.put(GROSS_CLAIM_ULTIMATE, gross.claimUltimate);
        valuesToSave.put(GROSS_PREMIUM_PAID, gross.premiumPaid);
        valuesToSave.put(GROSS_PREMIUM_WRITTEN, gross.premiumWritten);
        valuesToSave.put(GROSS_CASHFLOW, gross.cashflow);
        valuesToSave.put(GROSS_BEST_ESTIMATE, gross.bestEstimate);
        valuesToSave.put(GROSS_PREMIUM_RISK, gross.premiumRisk);
        valuesToSave.put(GROSS_RESERVE_RISK, gross.reserveRisk);
        valuesToSave.put(GROSS_PREMIUM_RESERVE_RISK, gross.premiumReserveRisk);
        valuesToSave.put(GROSS_LOSS_RATIO_WRITTEN_ULTIMATE, gross.lossRatioWrittenUltimate);
        valuesToSave.put(GROSS_LOSS_RATIO_PAID_PAID, gross.lossRatioPaidPaid);

        valuesToSave.put(NET_CLAIM_PAID, net.claimPaid);
        valuesToSave.put(NET_CLAIM_ULTIMATE, net.claimUltimate);
        valuesToSave.put(NET_PREMIUM_PAID, net.premiumPaid);
        valuesToSave.put(NET_PREMIUM_WRITTEN, net.premiumWritten);
        valuesToSave.put(NET_CASHFLOW, net.cashflow);
        valuesToSave.put(NET_BEST_ESTIMATE, net.bestEstimate);
        valuesToSave.put(NET_PREMIUM_RISK, net.premiumRisk);
        valuesToSave.put(NET_RESERVE_RISK, net.reserveRisk);
        valuesToSave.put(NET_PREMIUM_RESERVE_RISK, net.premiumReserveRisk);
        valuesToSave.put(NET_LOSS_RATIO_WRITTEN_ULTIMATE, net.lossRatioWrittenUltimate);
        valuesToSave.put(NET_LOSS_RATIO_PAID_PAID, net.lossRatioPaidPaid);

        valuesToSave.put(CEDED_CLAIM_PAID, ceded.claimPaid);
        valuesToSave.put(CEDED_CLAIM_ULTIMATE, ceded.claimUltimate);
        valuesToSave.put(CEDED_PREMIUM_PAID, ceded.premiumPaid);
        valuesToSave.put(CEDED_PREMIUM_WRITTEN, ceded.premiumWritten);
        valuesToSave.put(CEDED_CASHFLOW, ceded.cashflow);
        valuesToSave.put(CEDED_BEST_ESTIMATE, ceded.bestEstimate);
        valuesToSave.put(CEDED_PREMIUM_RISK, ceded.premiumRisk);
        valuesToSave.put(CEDED_RESERVE_RISK, ceded.reserveRisk);
        valuesToSave.put(CEDED_PREMIUM_RESERVE_RISK, ceded.premiumReserveRisk);
        valuesToSave.put(CEDED_LOSS_RATIO_WRITTEN_ULTIMATE, ceded.lossRatioWrittenUltimate);
        valuesToSave.put(CEDED_LOSS_RATIO_PAID_PAID, ceded.lossRatioPaidPaid);

        return valuesToSave;
    }

    public double getNetCashflow() {
        return net.cashflow;
    }

    public void setNetCashflow(double netCashflow) {
        net.cashflow = netCashflow;
    }

    public double getNetPremiumPaid() {
        return net.premiumPaid;
    }

    public void setNetPremiumPaid(double netPremiumPaid) {
        net.premiumPaid = netPremiumPaid;
    }

    public double getNetClaimPaid() {
        return net.claimPaid;
    }

    public void setNetClaimPaid(double netClaimPaid) {
        net.claimPaid = netClaimPaid;
    }

    public double getCommission() {
        return commission;
    }

    public void setCommission(double commission) {
        this.commission = commission;
    }

    public double getNetLossRatioWrittenUltimate() {
        return net.lossRatioWrittenUltimate;
    }

    public void setNetLossRatioWrittenUltimate(double netLossRatioWrittenUltimate) {
        net.lossRatioWrittenUltimate = netLossRatioWrittenUltimate;
    }

    public void setNetPremiumRisk(double netPremiumRisk) {
        net.premiumRisk = netPremiumRisk;
    }

    public double netPremiumRisk() {
        return net.premiumRisk;
    }

    public void setNetReserveRisk(double netReserveRisk) {
        net.reserveRisk = netReserveRisk;
    }

    public double getNetReserveRisk() {
        return net.reserveRisk;
    }

    public DateTime getInceptionDate() {
        return inceptionDate;
    }

    public void setInceptionDate(DateTime inceptionDate) {
        this.inceptionDate = inceptionDate;
    }

    public double getNetPremiumWritten() {
        return net.premiumWritten;
    }

    public void setNetPremiumWritten(double netPremiumWritten) {
        net.premiumWritten = netPremiumWritten;
    }

    public double getNetClaimUltimate() {
        return net.claimUltimate;
    }

    public void setNetClaimUltimate(double netClaimUltimate) {
        net.claimUltimate = netClaimUltimate;
    }

    public double getNetLossRatioPaidPaid() {
        return net.lossRatioPaidPaid;
    }

    public void setNetLossRatioPaidPaid(double netLossRatioPaidPaid) {
        net.lossRatioPaidPaid = netLossRatioPaidPaid;
    }

    public void setNetPremiumReserveRisk(double netPremiumReserveRsik) {
        net.premiumReserveRisk = netPremiumReserveRsik;
    }

    public double getNetPremiumReserveRisk() {
        return net.premiumReserveRisk;
    }

    public double getGrossCashflow() {
        return gross.cashflow;
    }

    public void setGrossCashflow(double grossCashflow) {
        gross.cashflow = grossCashflow;
    }

    public double getGrossPremiumPaid() {
        return gross.premiumPaid;
    }

    public void setGrossPremiumPaid(double grossPremiumPaid) {
        gross.premiumPaid = grossPremiumPaid;
    }

    public double getGrossClaimPaid() {
        return gross.claimPaid;
    }

    public void setGrossClaimPaid(double grossClaimPaid) {
        gross.claimPaid = grossClaimPaid;
    }

    public double getGrossLossRatioPaidPaid() {
        return gross.lossRatioPaidPaid;
    }

    public void setGrossLossRatioPaidPaid(double grossLossRatioPaidPaid) {
        gross.lossRatioPaidPaid = grossLossRatioPaidPaid;
    }

    public double getGrossPremiumRisk() {
        return gross.premiumRisk;
    }

    public void setGrossPremiumRisk(double grossPremiumRisk) {
        gross.premiumRisk = grossPremiumRisk;
    }

    public double getGrossReserveRisk() {
        return gross.reserveRisk;
    }

    public void setGrossReserveRisk(double grossReserveRisk) {
        gross.reserveRisk = grossReserveRisk;
    }

    public double getGrossPremiumReserveRisk() {
        return gross.premiumReserveRisk;
    }

    public void setGrossPremiumReserveRisk(double grossPremiumReserveRisk) {
        gross.premiumReserveRisk = grossPremiumReserveRisk;
    }

    public double getGrossBestEstimate() {
        return gross.bestEstimate;
    }

    public void setGrossBestEstimate(double grossBestEstimate) {
        gross.bestEstimate = grossBestEstimate;
    }

    public double getGrossPremiumWritten() {
        return gross.premiumWritten;
    }

    public void setGrossPremiumWritten(double grossPremiumWritten) {
        gross.premiumWritten = grossPremiumWritten;
    }

    public double getGrossClaimUltimate() {
        return gross.claimUltimate;
    }

    public void setGrossClaimUltimate(double grossClaimUltimate) {
        gross.claimUltimate = grossClaimUltimate;
    }

    public double getGrossLossRatioWrittenUltimate() {
        return gross.lossRatioWrittenUltimate;
    }

    public void setGrossLossRatioWrittenUltimate(double grossLossRatioWrittenUltimate) {
        gross.lossRatioWrittenUltimate = grossLossRatioWrittenUltimate;
    }

    public double getNetBestEstimate() {
        return net.bestEstimate;
    }

    public void setNetBestEstimate(double netBestEstimate) {
        net.bestEstimate = netBestEstimate;
    }

    public double getCededCashflow() {
        return ceded.cashflow;
    }

    public void setCededCashflow(double cededCashflow) {
        ceded.cashflow = cededCashflow;
    }

    public double getCededPremiumPaid() {
        return ceded.premiumPaid;
    }

    public void setCededPremiumPaid(double cededPremiumPaid) {
        ceded.premiumPaid = cededPremiumPaid;
    }

    public double getCededClaimPaid() {
        return ceded.claimPaid;
    }

    public void setCededClaimPaid(double cededClaimPaid) {
        ceded.claimPaid = cededClaimPaid;
    }

    public double getCededLossRatioPaidPaid() {
        return ceded.lossRatioPaidPaid;
    }

    public void setCededLossRatioPaidPaid(double cededLossRatioPaidPaid) {
        ceded.lossRatioPaidPaid = cededLossRatioPaidPaid;
    }

    public void setCededPremiumRisk(double cededPremiumRisk) {
        ceded.premiumRisk = cededPremiumRisk;
    }

    public double getCededPremiumRisk() {
        return ceded.premiumRisk;
    }

    public void setCededReserveRisk(double cededReserveRisk) {
        ceded.reserveRisk = cededReserveRisk;
    }

    public double getCededReserveRisk() {
        return ceded.reserveRisk;
    }

    public void setCededPremiumReserveRisk(double premiumReserveRisk) {
        ceded.premiumReserveRisk = premiumReserveRisk;
    }

    public double getCededPremiumReserveRisk() {
        return ceded.premiumReserveRisk;
    }

    public double getCededBestEstimate() {
        return ceded.bestEstimate;
    }

    public void setCededBestEstimate(double cededBestEstimate) {
        ceded.bestEstimate = cededBestEstimate;
    }

    public double getCededPremiumWritten() {
        return ceded.premiumWritten;
    }

    public void setCededPremiumWritten(double cededPremiumWritten) {
        ceded.premiumWritten = cededPremiumWritten;
    }

    public double getCededClaimUltimate() {
        return ceded.claimUltimate;
    }

    public void setCededClaimUltimate(double cededClaimUltimate) {
        ceded.claimUltimate = cededClaimUltimate;
    }

    public double getCededLossRatioWrittenUltimate() {
        return ceded.lossRatioWrittenUltimate;
    }

    public void setCededLossRatioWrittenUltimate(double cededLossRatioWrittenUltimate) {
        ceded.lossRatioWrittenUltimate = cededLossRatioWrittenUltimate;
    }
    
    private class Financials {
        double cashflow;
        double premiumPaid;
        double claimTotal;
        double claimPaid;
        double claimOutstanding;
        double lossRatioPaidPaid;

        double premiumRisk;
        double reserveRisk;
        double premiumReserveRisk;

        double bestEstimate;
        double premiumWritten;
        double claimUltimate;
        double lossRatioWrittenUltimate;

        public Financials() {
        }

        public Financials(List<UnderwritingInfoPacket> uwInfos, List<ClaimCashflowPacket> claims,
                          Boolean occurrenceInCurrentPeriod, Boolean cededOrNetFigures) {
            if (!uwInfos.isEmpty()) {
                premiumWritten = UnderwritingInfoUtils.aggregate(uwInfos).getPremiumWritten();
                premiumPaid = UnderwritingInfoUtils.aggregate(uwInfos).getPremiumPaid();
            }
            initClaimRelatedFigures(claims, occurrenceInCurrentPeriod, cededOrNetFigures);
        }

        public void initClaimRelatedFigures(List<ClaimCashflowPacket> claims, Boolean occurrenceInCurrentPeriod, Boolean cededOrNetFigures) {
            if (!claims.isEmpty()) {
                ClaimCashflowPacket sum = ClaimUtils.sum(ClaimUtils.castCCPListToPacketList(claims), true);
                claimUltimate = sum.ultimate();
                claimTotal = sum.totalIncrementalIndexed();
                claimPaid = sum.getPaidIncrementalIndexed();
                claimOutstanding = sum.outstandingIndexed();
            }
            cashflow = premiumPaid + claimPaid;

            bestEstimate = premiumWritten + claimUltimate;
            double financialRisk = premiumWritten + claimTotal;
            if (cededOrNetFigures == Boolean.TRUE) {
                cashflow += commission;
                financialRisk += commission;
            }
            if (occurrenceInCurrentPeriod == Boolean.TRUE) {
                premiumRisk = financialRisk;
            }
            else if (occurrenceInCurrentPeriod == Boolean.FALSE) {
                reserveRisk = financialRisk;
            }
            premiumReserveRisk = premiumRisk + reserveRisk;
            lossRatioWrittenUltimate = premiumWritten == 0 ? 0 : -claimUltimate / premiumWritten;
            lossRatioPaidPaid = premiumPaid == 0 ? 0 : -claimPaid / premiumPaid;
        }

        public Financials plus(Financials other) {
            if (other == null) return this;
            claimPaid += other.claimPaid;
            claimUltimate += other.claimUltimate;
            premiumPaid += other.premiumPaid;
            premiumWritten += other.premiumWritten;
            cashflow = premiumPaid + claimPaid + commission;
            premiumRisk += other.premiumRisk;
            reserveRisk += other.reserveRisk;
            premiumReserveRisk += other.premiumReserveRisk;
            lossRatioWrittenUltimate = premiumWritten == 0 ? 0 : -claimUltimate / premiumWritten;
            lossRatioPaidPaid = premiumPaid == 0 ? 0 : -claimPaid / premiumPaid;
            bestEstimate = premiumWritten + claimUltimate;
            return this;
        }

        public Financials copy() {
            Financials copy = new Financials();
            copy.claimPaid = claimPaid;
            copy.claimUltimate = claimUltimate;
            copy.premiumPaid = premiumPaid;
            copy.premiumWritten = premiumWritten;
            copy.cashflow = cashflow;
            copy.premiumRisk = premiumRisk;
            copy.reserveRisk = reserveRisk;
            copy.premiumReserveRisk = premiumReserveRisk;
            copy.lossRatioWrittenUltimate = lossRatioWrittenUltimate;
            copy.lossRatioPaidPaid = lossRatioPaidPaid;
            copy.bestEstimate = bestEstimate;
            return copy;
        }
    }
}
