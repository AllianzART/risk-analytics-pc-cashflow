package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.caching;

import com.google.common.collect.SetMultimap;
import org.pillarone.riskanalytics.core.simulation.SimulationException;
import org.pillarone.riskanalytics.core.simulation.engine.PeriodScope;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.pc.cf.claim.IClaimRoot;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.ContractCoverBase;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.IncurredClaimBase;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.filterUtilities.GRIUtilities;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.filterUtilities.RIUtilities;

import java.util.*;

/**
 * author simon.parten @ art-allianz . com
 */
public class ListOnlyContractClaimStore implements IAllContractClaimCache {

    private final Collection<ClaimCashflowPacket> allCashflows = new ArrayList<ClaimCashflowPacket>();
    private final Map<Integer, Collection<ClaimCashflowPacket>> claimsBySimulationPeriod = new HashMap<Integer, Collection<ClaimCashflowPacket>>();


    public Collection<ClaimCashflowPacket> allClaimCashflowPackets() {
        return allCashflows;
    }

    public Set<IClaimRoot> allIncurredClaims() {
        return RIUtilities.incurredClaims(this.allCashflows, IncurredClaimBase.BASE);
    }

    public SetMultimap<IClaimRoot, IClaimRoot> incurredClaimsByKey() {
        return RIUtilities.incurredClaims(this.allCashflows);
    }

    public void cacheClaims(Collection<ClaimCashflowPacket> claims) {
        throw new SimulationException("Inappropriate caching mechanism used");
    }

    public Collection<ClaimCashflowPacket> allClaimCashflowPacketsInModelPeriod(Collection<ClaimCashflowPacket> allCashflows, PeriodScope periodScope, ContractCoverBase base, Integer anInt) {
        return GRIUtilities.cashflowsCoveredInModelPeriod(this.allCashflows, periodScope, base, anInt);
    }

    public Set<IClaimRoot> allIncurredClaimsInModelPeriod(Integer anInt, PeriodScope periodScope, ContractCoverBase coverBase) {
        return RIUtilities.incurredClaimsByPeriod(anInt, periodScope.getPeriodCounter() ,
                incurredClaimsByKey().values(), coverBase);
    }

    public Set<IClaimRoot> allIncurredClaimsCurrentModelPeriod(PeriodScope periodScope, ContractCoverBase coverBase) {
        return RIUtilities.incurredClaimsByPeriod(
                periodScope.getCurrentPeriod(), periodScope.getPeriodCounter(), allIncurredClaims(), coverBase);
    }

    public Collection<IClaimRoot> allIncurredClaimsUpToSimulationPeriod(Integer period, PeriodScope periodScope, ContractCoverBase coverBase) {
        Collection<ClaimCashflowPacket> cashflowsBeforeSimPeriod = allCashflowClaimsUpToSimulationPeriod(period, periodScope, coverBase);
        return RIUtilities.incurredClaims(cashflowsBeforeSimPeriod, IncurredClaimBase.BASE);
    }

    public Collection<ClaimCashflowPacket> allCashflowClaimsUpToSimulationPeriod(Integer period, PeriodScope periodScope, ContractCoverBase coverBase) {
        Collection<ClaimCashflowPacket> claimsBeforeSimPeriod = new ArrayList<ClaimCashflowPacket>();
        for (int i = 0; i <= period; i++) {
            claimsBeforeSimPeriod.addAll(claimsBySimulationPeriod.get(i));
        }
        return claimsBeforeSimPeriod;
    }

    public Collection<ClaimCashflowPacket> allClaimCashflowPacketsInSimulationPeriod(Collection<ClaimCashflowPacket> allCashflows, PeriodScope periodScope, ContractCoverBase base, Integer anInt) {
        return claimsBySimulationPeriod.get(anInt);
    }

    public Collection<IClaimRoot> allIncurredClaimsInSimulationPeriod(Integer period, PeriodScope periodScope, ContractCoverBase coverBase) {
        Collection<ClaimCashflowPacket> cashflowPackets = claimsBySimulationPeriod.get(period);
        return RIUtilities.incurredClaims(cashflowPackets, IncurredClaimBase.BASE);
    }

    public Collection<IClaimRoot> allIncurredClaimsCurrentSimulationPeriod(PeriodScope periodScope, ContractCoverBase coverBase) {
        return allIncurredClaimsInSimulationPeriod(periodScope.getCurrentPeriod(), periodScope, coverBase);
    }

    public void cacheClaims(Collection<ClaimCashflowPacket> claims, Integer simulationPeriod) {
        final Collection<ClaimCashflowPacket> cashflowPackets = new ArrayList<ClaimCashflowPacket>();
        cashflowPackets.addAll(claims);
        allCashflows.addAll(claims);
        if (claimsBySimulationPeriod.get(simulationPeriod) != null) {
            throw new SimulationException("Attempted to overwrite claimsBySimulationPeriod cache in claim store. Contact development");
        }
        claimsBySimulationPeriod.put(simulationPeriod, cashflowPackets );
    }
}
