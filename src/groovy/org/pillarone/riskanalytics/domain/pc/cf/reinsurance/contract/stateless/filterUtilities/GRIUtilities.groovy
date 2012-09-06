package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.filterUtilities

import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket
import org.pillarone.riskanalytics.domain.pc.cf.claim.IClaimRoot
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.ContractCoverBase
import org.joda.time.DateTime
import org.pillarone.riskanalytics.core.simulation.engine.PeriodScope
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.IncurredClaimBase
import org.pillarone.riskanalytics.domain.pc.cf.claim.ICededRoot
import org.pillarone.riskanalytics.core.simulation.SimulationException

/**
 *   author simon.parten @ art-allianz . com
 */
class GRIUtilities {

    public static List<ClaimCashflowPacket> cashflowsRelatedToRoots(Set<IClaimRoot> rootClaims, List<ClaimCashflowPacket> cashflows) {
        new ArrayList<ClaimCashflowPacket>(cashflows.findAll {it -> rootClaims.contains(it.baseClaim) })
    }

    public static boolean hasGrossCashflow(List<ClaimCashflowPacket> cashflowPacketList, ICededRoot root ) {
        return cashflowPacketList*.getBaseClaim().contains(root)
    }


    public static Double ultimateSumFromCashflows(List<ClaimCashflowPacket> cashflows) {
        Set<IClaimRoot> incurredClaims = RIUtilities.incurredClaims(cashflows, IncurredClaimBase.BASE)
        ArrayList<IClaimRoot> claims = new ArrayList<IClaimRoot>()
        claims.addAll(incurredClaims)
        return ultimateSum(claims)

    }

    public static ICededRoot findCededClaimRelatedToGrossClaim(IClaimRoot grossClaim, List<ICededRoot> allCededClaims ) {
        ICededRoot claim = allCededClaims.find {grossClaim.equals(it.getGrossClaim())}
        if(claim == null) {
            throw new SimulationException("Failed to match a gross claim to the list of ceded claims; " + claim.toString())
        }
        return claim
    }

    public static ClaimCashflowPacket findCashflowToGrossClaim(IClaimRoot cededKeyClaim, List<ClaimCashflowPacket> allCashflows ) {
        ClaimCashflowPacket claim = allCashflows.find {cededKeyClaim.equals(it.getKeyClaim())}
//        There ought to be no prior ceded claims. Pump out a dummy claim.
        if(claim == null) {
            claim = new ClaimCashflowPacket()
        }
        return claim
    }

    public static Double ultimateSum(List<IClaimRoot> incurredClaims) {
        if(incurredClaims.size() > 0) {
            return (Double) incurredClaims*.getUltimate().sum()
        }
        return 0d
    }

    public static Double incrementalCashflowSum(List<ClaimCashflowPacket> incurredClaims) {
        if(incurredClaims.size() > 0) {
            return (Double) incurredClaims*.getPaidIncrementalIndexed().sum()
        }
        return 0d
    }

    public static List<ClaimCashflowPacket> uncoveredClaims(ContractCoverBase coverageBase, DateTime coverStart, DateTime coverEnd, List<ClaimCashflowPacket> incomingClaims) {

        final List<ClaimCashflowPacket> uncoveredClaims = incomingClaims.findAll {ClaimCashflowPacket it ->
            DateTime claimCoverDate = coverageBase.claimCoverDate(it);
            boolean inCoverPeriod = (coverStart.isEqual(claimCoverDate) || coverStart.isBefore(claimCoverDate)) && coverEnd.isAfter(claimCoverDate)
            return !inCoverPeriod
        }
        return uncoveredClaims;
    }

    public static ArrayList<IClaimRoot> claimsCoveredInPeriod(List<IClaimRoot> incurredClaims, PeriodScope periodScope, ContractCoverBase base, int period ) {

        ArrayList<IClaimRoot> claims  = incurredClaims.findAll {
            it ->
            DateTime coverDate = base.claimCoverDate(it)
            return periodScope.getPeriodCounter().belongsToPeriod(coverDate) == period
        }

        return claims
    }

    public static ArrayList<ClaimCashflowPacket> cashflowsCoveredInModelPeriod(List<ClaimCashflowPacket> cashflows, PeriodScope periodScope, ContractCoverBase base, int period ) {

        return cashflows.findAll {
            it ->
            DateTime coverDate = base.claimCoverDate(it)
            return periodScope.getPeriodCounter().belongsToPeriod(coverDate) == period
        }
    }


}