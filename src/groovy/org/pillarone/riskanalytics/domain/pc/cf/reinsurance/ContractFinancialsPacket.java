package org.pillarone.riskanalytics.domain.pc.cf.reinsurance;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.packets.MultiValuePacket;
import org.pillarone.riskanalytics.core.simulation.IPeriodCounter;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimType;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimUtils;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.CededUnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoUtils;

import java.util.*;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class ContractFinancialsPacket extends MultiValuePacket {
    private double contractResult;
    private double primaryResult;
    private double cededPremium;
    private double netPremium;
    private double cededClaim;
    private double netClaim;
    private double cededCommission;
    private double cededLossRatio;
    private DateTime inceptionDate;

    /**
     * Default c´tor required by SumAggregator
     */
    public ContractFinancialsPacket() {
    }

    /**
     * cededClaim and netClaim are filled with the ultimate value, cededPremium and netPremium are filled with the premium written
     * @param cededClaims
     * @param netClaims
     * @param cededUwInformation
     * @param netUwInformation
     */
    public ContractFinancialsPacket(List<ClaimCashflowPacket> cededClaims, List<ClaimCashflowPacket> netClaims,
                                    List<CededUnderwritingInfoPacket> cededUwInformation,
                                    List<UnderwritingInfoPacket> netUwInformation, boolean singlePeriod) {
        if (!cededClaims.isEmpty()) {
            cededClaim = ClaimUtils.sum(ClaimUtils.castCCPListToPacketList(cededClaims) , true).ultimate();
        }
        if (!netClaims.isEmpty()) {
            netClaim = ClaimUtils.sum(ClaimUtils.castCCPListToPacketList(netClaims), true).ultimate();
        }
        if (!cededUwInformation.isEmpty()) {
            CededUnderwritingInfoPacket aggregateCededUwInfoPacket = UnderwritingInfoUtils.aggregateCeded(cededUwInformation);
            cededCommission = aggregateCededUwInfoPacket.getCommission();
            cededPremium = aggregateCededUwInfoPacket.getPremiumWritten();
        }
        if (!netUwInformation.isEmpty()) {
            netPremium = UnderwritingInfoUtils.aggregate(netUwInformation).getPremiumWritten();
        }
        if (singlePeriod) {
            if (!cededClaims.isEmpty()) {
                inceptionDate = cededClaims.get(0).getBaseClaim().getExposureStartDate();
            }
            else if (!netClaims.isEmpty()) {
                inceptionDate = netClaims.get(0).getBaseClaim().getExposureStartDate();
            }
            else if (!cededUwInformation.isEmpty()) {
                inceptionDate = cededUwInformation.get(0).getExposure().getInceptionDate();
            }
            else if (!netUwInformation.isEmpty()) {
                inceptionDate = netUwInformation.get(0).getExposure().getInceptionDate();
            }
        }
        updateDependingProperties();
    }

    public static List<ContractFinancialsPacket> getContractFinancialsPacketsByInceptionPeriod(
                        List<ClaimCashflowPacket> cededClaims, List<ClaimCashflowPacket> netClaims,
                        List<CededUnderwritingInfoPacket> cededUwInformation, List<UnderwritingInfoPacket> netUwInformation,
                        IPeriodCounter periodCounter) {
        ListMultimap<Integer, ClaimCashflowPacket> cededClaimsByPeriod = ArrayListMultimap.create();
        ListMultimap<Integer, ClaimCashflowPacket> netClaimsByPeriod = ArrayListMultimap.create();
        ListMultimap<Integer, CededUnderwritingInfoPacket> cededUwInfoByPeriod = ArrayListMultimap.create();
        ListMultimap<Integer, UnderwritingInfoPacket> netUwInfoByPeriod = ArrayListMultimap.create();
        Set<Integer> periods = new HashSet<Integer>();
        for (ClaimCashflowPacket claim : cededClaims) {
            if (claim.getClaimType().equals(ClaimType.AGGREGATED_RESERVES)) continue;
            int period = claim.getBaseClaim().getInceptionPeriod(periodCounter);
            periods.add(period);
            cededClaimsByPeriod.put(period, claim);
        }
        for (ClaimCashflowPacket claim : netClaims) {
            if (claim.getClaimType().equals(ClaimType.AGGREGATED_RESERVES)) continue;
            int period = claim.getBaseClaim().getInceptionPeriod(periodCounter);
            periods.add(period);
            netClaimsByPeriod.put(period, claim);
        }
        for (CededUnderwritingInfoPacket uwInfo : cededUwInformation) {
            periods.add(uwInfo.getExposure().getInceptionPeriod());
            cededUwInfoByPeriod.put(uwInfo.getExposure().getInceptionPeriod(), uwInfo);
        }
        for (UnderwritingInfoPacket uwInfo : netUwInformation) {
            periods.add(uwInfo.getExposure().getInceptionPeriod());
            netUwInfoByPeriod.put(uwInfo.getExposure().getInceptionPeriod(), uwInfo);
        }
        List<ContractFinancialsPacket> packets = new ArrayList<ContractFinancialsPacket>();
        for (Integer period : periods) {
            ContractFinancialsPacket packet = new ContractFinancialsPacket(cededClaimsByPeriod.get(period), netClaimsByPeriod.get(period),
                    cededUwInfoByPeriod.get(period), netUwInfoByPeriod.get(period), true);
            packets.add(packet);
        }
        return packets;
    }

    public ContractFinancialsPacket plus(ContractFinancialsPacket other) {
        if (other == null) return this;
        cededClaim += other.cededClaim;
        cededCommission += other.cededCommission;
        cededPremium += other.cededPremium;
        netClaim += other.netClaim;
        netPremium += other.netPremium;
        updateDependingProperties();
        return this;
    }

    public void updateDependingProperties() {
        updateContractResult();
        updatePrimaryResult();
        updateCededLossRatio();
    }
    public final static String CEDED_CLAIM = "cededClaim";
    public final static String CEDED_COMMISSION = "cededCommission";
    public final static String CEDED_PREMIUM = "cededPremium";
    public final static String NET_CLAIM = "netClaim";
    public final static String NET_PREMIUM = "netPremium";
    public final static String PRIMARY_RESULT = "primaryResult";
    public final static String CONTRACT_RESULT = "contractResult";
    public final static String CEDED_LOSS_RATIO = "cededLossRatio";

    @Override
    public Map<String, Number> getValuesToSave() throws IllegalAccessException {
        Map<String, Number> valuesToSave = new HashMap<String, Number>();
        valuesToSave.put(CEDED_CLAIM, cededClaim);    // this and missing default c'tor (final!) leads to failure during result tree building
        valuesToSave.put(CEDED_COMMISSION, cededCommission);
        valuesToSave.put(CEDED_PREMIUM, cededPremium);
        valuesToSave.put(NET_CLAIM, netClaim);
        valuesToSave.put(NET_PREMIUM, netPremium);
        valuesToSave.put(PRIMARY_RESULT, primaryResult);
        valuesToSave.put(CONTRACT_RESULT, contractResult);
        valuesToSave.put(CEDED_LOSS_RATIO, cededLossRatio);
        return valuesToSave;
    }

    public double getCededPremium() {
        return cededPremium;
    }

    public void setCededPremium(double cededPremium) {
        this.cededPremium = cededPremium;
        updateContractResult();
    }

    public double getCededClaim() {
        return cededClaim;
    }

    public void setCededClaim(double cededClaim) {
        this.cededClaim = cededClaim;
        updateContractResult();
    }

    public double getContractResult() {
        updateContractResult();
        return contractResult;
    }

    private void updateContractResult() {
        contractResult = cededPremium + cededClaim + cededCommission;
    }

    public void setContractResult(double contractResult) {
        this.contractResult = contractResult;
    }

    public double getCededCommission() {
        return cededCommission;
    }

    public void setCededCommission(double cededCommission) {
        this.cededCommission = cededCommission;
        updateContractResult();
        updatePrimaryResult();
    }

    public double getCededLossRatio() {
        updateCededLossRatio();
        return cededLossRatio;
    }

    private void updateCededLossRatio() {
        this.cededLossRatio = cededPremium == 0d ? 0d : cededClaim / cededPremium;
    }

    public double getPrimaryResult() {
        return primaryResult;
    }

    private void updatePrimaryResult() {
        this.primaryResult = netPremium + netClaim + cededCommission;
    }

    public double getNetPremium() {
        return netPremium;
    }

    public void setNetPremium(double netPremium) {
        this.netPremium = netPremium;
        updatePrimaryResult();
    }

    public double getNetClaim() {
        return netClaim;
    }

    public void setNetClaim(double netClaim) {
        this.netClaim = netClaim;
        updatePrimaryResult();
    }

    public void setPrimaryResult(double primaryResult) {
        this.primaryResult = primaryResult;
    }

    public void setCededLossRatio(double cededLossRatio) {
        this.cededLossRatio = cededLossRatio;
    }

    public DateTime getInceptionDate() {
        return inceptionDate;
    }

    public void setInceptionDate(DateTime inceptionDate) {
        this.inceptionDate = inceptionDate;
    }
}