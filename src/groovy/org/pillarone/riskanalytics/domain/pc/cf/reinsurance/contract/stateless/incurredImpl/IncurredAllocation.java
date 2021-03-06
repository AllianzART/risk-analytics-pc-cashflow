package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.incurredImpl;

import org.pillarone.riskanalytics.core.simulation.SimulationException;
import org.pillarone.riskanalytics.core.simulation.engine.PeriodScope;
import org.pillarone.riskanalytics.domain.pc.cf.claim.CededClaimRoot;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimType;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ICededRoot;
import org.pillarone.riskanalytics.domain.pc.cf.claim.IClaimRoot;
import org.pillarone.riskanalytics.domain.pc.cf.exceptionUtils.ExceptionUtils;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.AllClaimsRIOutcome;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.ContractCoverBase;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.IIncurredAllocation;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.IncurredClaimRIOutcome;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.caching.IAllContractClaimCache;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.filterUtilities.RIUtilities;

import java.util.Set;

/**
 * author simon.parten @ art-allianz . com
 */
public class IncurredAllocation implements IIncurredAllocation {

    public AllClaimsRIOutcome allocateClaims(double incurredInPeriod, IAllContractClaimCache claimStore, PeriodScope periodScope, ContractCoverBase base) {

        Set<IClaimRoot> grossClaimsThisPeriod = claimStore.allIncurredClaimsCurrentModelPeriodForAllocation(periodScope, base);
        double grossIncurred = RIUtilities.ultimateSum(grossClaimsThisPeriod);
        double checkValue = ExceptionUtils.getCheckValue(grossIncurred);
        if(  incurredInPeriod > grossIncurred + checkValue) {
            throw new SimulationException("Ceded amount in contract: " + incurredInPeriod + " is greater than the grossIncurred in the period : " + grossIncurred + ". " +
                    "This is non-sensical, please contact development");
        }

        /* If the incurred amount is zero we want to cede no claims ! Divide by a large number */
        if (grossClaimsThisPeriod.size() > 0) {
            if (Math.abs(grossIncurred) == 0) {
                grossIncurred = Double.POSITIVE_INFINITY;
            }
        }

        final AllClaimsRIOutcome allClaimsRIOutcome = new AllClaimsRIOutcome();
        for (IClaimRoot iClaimRoot : grossClaimsThisPeriod) {
            double cededRatio = iClaimRoot.getUltimate() / grossIncurred;
            double cededIncurred = cededRatio * incurredInPeriod;
            ICededRoot cededClaim = new CededClaimRoot( cededIncurred , iClaimRoot, ClaimType.CEDED);
            ICededRoot netClaim = new CededClaimRoot( iClaimRoot.getUltimate() - cededIncurred , iClaimRoot, ClaimType.NET);
            IncurredClaimRIOutcome incurredClaimRIOutcome = new IncurredClaimRIOutcome(netClaim, cededClaim, iClaimRoot);
            allClaimsRIOutcome.addClaim(incurredClaimRIOutcome);
        }

        return allClaimsRIOutcome;
    }


}
