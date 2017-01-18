package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.paidImpl;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Sets;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pillarone.riskanalytics.core.simulation.SimulationException;
import org.pillarone.riskanalytics.core.simulation.engine.PeriodScope;
import org.pillarone.riskanalytics.core.util.MathUtils;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimUtils;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ICededRoot;
import org.pillarone.riskanalytics.domain.pc.cf.claim.IClaimRoot;
import org.pillarone.riskanalytics.domain.pc.cf.exceptionUtils.ExceptionUtils;
import org.pillarone.riskanalytics.domain.pc.cf.global.SimulationConstants;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.*;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.caching.ICededClaimStore;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.filterUtilities.RIUtilities;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * author simon.parten @ art-allianz . com
 */
public class ProportionalToGrossPaidAllocation implements IPaidAllocation {

    private static Log LOG = LogFactory.getLog(ProportionalToGrossPaidAllocation.class);
    private static DecimalFormat df = new DecimalFormat("#,###.##");

    public AllCashflowClaimsRIOutcome allocatePaid(Map<Integer, Double> incrementalPaidByPeriod, List<ClaimCashflowPacket> grossCashflowsThisPeriod,
                                                   ICededClaimStore claimStore,
                                                   PeriodScope periodScope,
                                                   ContractCoverBase coverageBase, AllClaimsRIOutcome incurredCededClaims, boolean sanityChecks) {


        IncurredClaimBase base = IncurredClaimBase.BASE;
        AllCashflowClaimsRIOutcome claimsOfInterest = new AllCashflowClaimsRIOutcome();
        Collection<ClaimCashflowPacket> latestCededCashflowsByIncurredClaim = RIUtilities.latestCashflowByIncurredClaim(claimStore.allCededCashlowsToDate(), base);
        Collection<IncurredClaimRIOutcome> allIncurredOutcomesToDate = Sets.newHashSet();
        allIncurredOutcomesToDate.addAll(incurredCededClaims.getAllIncurredOutcomes());
        allIncurredOutcomesToDate.addAll(claimStore.allIncurredRIOutcomesToDate());

//        For each model period
        for (Map.Entry<Integer, Double> entry : incrementalPaidByPeriod.entrySet()) {
            List<ClaimCashflowPacket> cashflowClaimsForPeriodCheck = new ArrayList<ClaimCashflowPacket>();
            List<ClaimCashflowPacket> cashflowsRelatedToModelPeriod = RIUtilities.cashflowsClaimsByPeriod(entry.getKey(), periodScope.getPeriodCounter(), grossCashflowsThisPeriod, coverageBase);
            double grossIncurredInPeriod = RIUtilities.ultimateSumFromCashflows(cashflowsRelatedToModelPeriod);
            double cededPaidAmountInModelPeriodThisSimPeriod = (Double) entry.getValue();

            ArrayListMultimap<IClaimRoot, ClaimCashflowPacket> cashflowsByKey = RIUtilities.cashflowsByRoot(cashflowsRelatedToModelPeriod, base);
            Map<IClaimRoot, Collection<ClaimCashflowPacket>> cashflows = cashflowsByKey.asMap();

            for (Map.Entry<IClaimRoot, Collection<ClaimCashflowPacket>> packetEntrys : cashflows.entrySet()) {
//                Make this return an RIOutcome
                IncurredClaimRIOutcome cededRoot = RIUtilities.findCededClaimRelatedToGrossClaim(packetEntrys.getKey(), allIncurredOutcomesToDate);
                List<ClaimCashflowPacket> cashflowPackets = new ArrayList<ClaimCashflowPacket>(packetEntrys.getValue());
                double grossIncurredByClaimRatio;
                if (Math.abs(grossIncurredInPeriod) == 0) {
                    grossIncurredByClaimRatio = 0d;
                } else {
                    grossIncurredByClaimRatio = packetEntrys.getKey().getUltimate() / grossIncurredInPeriod;
                }
                double claimPaidInContractYear = grossIncurredByClaimRatio * cededPaidAmountInModelPeriodThisSimPeriod;

                double sumIncrementsOfThisClaim = RIUtilities.incrementalCashflowSum(cashflowPackets);

                IClaimRoot keyClaim = base.parentClaim(cashflowPackets.get(0));

                ClaimCashflowPacket latestCededCashflow = RIUtilities.findCashflowToGrossClaim(keyClaim, latestCededCashflowsByIncurredClaim, IncurredClaimBase.KEY);

                boolean setUltimate = false;
                if (base.parentClaim(latestCededCashflow).getExposureStartDate() == null && latestCededCashflow.ultimate() == 0) {
//                    We know we have a dummy claim - this is the first time this incurred claim has ceded something.
                    setUltimate = true;
                }

                double cumulatedCededForThisClaim = latestCededCashflow.getPaidCumulatedIndexed();
                for (ClaimCashflowPacket cashflowPacket : cashflowPackets) {

                    if(setUltimate) {
                        ClaimCashflowPacket cededClaim = new ClaimCashflowPacket(cededRoot.getCededClaim(), cashflowPacket, 0d, 0d, setUltimate, cededRoot.getCededClaim().getUltimate());
                        ClaimCashflowPacket netClaim = new ClaimCashflowPacket(cededRoot.getNetClaim(), cashflowPacket, 0d, 0d, setUltimate, cededRoot.getNetClaim().getUltimate() );
                        ClaimUtils.applyMarkers(cashflowPacket, cededClaim);
                        ClaimUtils.applyMarkers(cashflowPacket, netClaim);
                        final ClaimRIOutcome claimRIOutcome = new ClaimRIOutcome(netClaim, cededClaim, cashflowPacket);
                        claimsOfInterest.addClaim(claimRIOutcome);
                        setUltimate = false;
                    }
                    double paidAgainstThisPacket = 0;
                    double netPaid = 0d;

                    if (sumIncrementsOfThisClaim == 0) {
                        paidAgainstThisPacket = claimPaidInContractYear;
                        paidAgainstThisPacket = checkPaidAgainstThisClaim(cededRoot.getCededClaim(), cumulatedCededForThisClaim, paidAgainstThisPacket);
                        netPaid = cashflowPacket.getPaidIncrementalIndexed() - paidAgainstThisPacket;
                        ClaimCashflowPacket cededClaim = new ClaimCashflowPacket(cededRoot.getCededClaim(), cashflowPacket, paidAgainstThisPacket, cumulatedCededForThisClaim + paidAgainstThisPacket, false, cededRoot.getCededClaim().getUltimate());
                        ClaimCashflowPacket netClaim = new ClaimCashflowPacket(cededRoot.getNetClaim(), cashflowPacket, netPaid, cumulatedCededForThisClaim + netPaid , false, cededRoot.getNetClaim().getUltimate() );
                        cashflowClaimsForPeriodCheck.add(cededClaim);
                        doPaidLessThanIncurredCheck(sanityChecks, cededRoot.getCededClaim(), cumulatedCededForThisClaim, cededClaim);
                        ClaimUtils.applyMarkers(cashflowPacket, cededClaim);
                        ClaimUtils.applyMarkers(cashflowPacket, netClaim);
                        final ClaimRIOutcome claimRIOutcome = new ClaimRIOutcome(netClaim, cededClaim, cashflowPacket);
                        claimsOfInterest.addClaim(claimRIOutcome);
                        break;
                    } else {
                        paidAgainstThisPacket = claimPaidInContractYear * cashflowPacket.getPaidIncrementalIndexed() / sumIncrementsOfThisClaim;
                        paidAgainstThisPacket = checkPaidAgainstThisClaim(cededRoot.getCededClaim(), cumulatedCededForThisClaim, paidAgainstThisPacket);
                        netPaid = cashflowPacket.getPaidIncrementalIndexed() - paidAgainstThisPacket;
                    }

                    cumulatedCededForThisClaim += paidAgainstThisPacket;
                    ClaimCashflowPacket cededClaim = new ClaimCashflowPacket(cededRoot.getCededClaim(), cashflowPacket, paidAgainstThisPacket, cumulatedCededForThisClaim, false, cededRoot.getCededClaim().getUltimate());
                    ClaimCashflowPacket netClaim = new ClaimCashflowPacket(cededRoot.getNetClaim(), cashflowPacket, netPaid, cumulatedCededForThisClaim, false, cededRoot.getNetClaim().getUltimate() );
                    cashflowClaimsForPeriodCheck.add(cededClaim);
                    doPaidLessThanIncurredCheck(sanityChecks, cededRoot.getCededClaim(), cumulatedCededForThisClaim, cededClaim);
                    setUltimate = false;
                    ClaimUtils.applyMarkers(cashflowPacket, cededClaim);
                    ClaimUtils.applyMarkers(cashflowPacket, netClaim);
                    final ClaimRIOutcome claimRIOutcome = new ClaimRIOutcome(netClaim, cededClaim, cashflowPacket);
                    claimsOfInterest.addClaim(claimRIOutcome);
                }
            }
            double checkCededPaidInModelPeriod = RIUtilities.incrementalCashflowSum(cashflowClaimsForPeriodCheck);
//            double checkValue = ExceptionUtils.getCheckValue(checkCededPaidInModelPeriod);
            double epsilon = MathUtils.getRelativeEpsilon(checkCededPaidInModelPeriod,entry.getValue());
//            if(!(
//                    (checkCededPaidInModelPeriod - entry.getValue() > - checkValue) && (checkCededPaidInModelPeriod - entry.getValue() < checkValue ))
//                    ) {
//                throw new SimulationException("Claims in model period; " + entry.getKey()  + " allocated incremental paid " + df.format(checkCededPaidInModelPeriod) + " do not match "
//                        + "the calculated paid amount in the period = " + df.format(entry.getValue()) + ". In simulation period periodScope " + periodScope.getCurrentPeriod() +
//                        " There must be an error in the claim allocation routine. Please forward to development"
//                );
//            }
            if( Math.abs(checkCededPaidInModelPeriod - entry.getValue()) > epsilon )
            {
                throw new SimulationException("Claims in model period; " + entry.getKey()  + " allocated incremental paid " + df.format(checkCededPaidInModelPeriod) + " do not match "
                        + "the calculated paid amount in the period = " + df.format(entry.getValue()) + ". In simulation period periodScope " + periodScope.getCurrentPeriod() +
                        " There must be an error in the claim allocation routine. Tolerance used:" + epsilon
                );
            }
        }

        return claimsOfInterest;
    }

    private double checkPaidAgainstThisClaim(ICededRoot cededRoot, double cumulatedCededForThisClaim, double paidAgainstThisPacket) {
        if(cumulatedCededForThisClaim + paidAgainstThisPacket > cededRoot.getUltimate()) {
            paidAgainstThisPacket = cededRoot.getUltimate() - cumulatedCededForThisClaim;
        }
        return paidAgainstThisPacket;
    }

    private void doPaidLessThanIncurredCheck(boolean sanityChecks, ICededRoot cededRoot, double cumulatedCededForThisClaim, ClaimCashflowPacket claimCashflowPacket) {
        if (Math.abs(cumulatedCededForThisClaim) > Math.abs(cededRoot.getUltimate()) + SimulationConstants.EPSILON) {
            String message = "Insanity detected : " + df.format(cededRoot.getUltimate()) + " has an ultimate of smaller magnitude " +
                    "than the paid amount " + df.format(claimCashflowPacket.getPaidCumulatedIndexed()) + ". " +
                    "This will create inconsistencies in higher structures. Contact development";
            LOG.error(message);
            if (sanityChecks) {
                throw new SimulationException(message);
            }
        }
    }
}
