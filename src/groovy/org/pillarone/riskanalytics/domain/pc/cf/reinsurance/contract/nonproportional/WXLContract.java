package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.nonproportional;

import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimType;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.ClaimStorage;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.allocation.IPremiumAllocationStrategy;

import java.util.List;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class WXLContract extends XLContract {

    /**
     * All provided values have to be absolute! Scaling is done within the parameter strategy.
     *
     * @param cededPremiumFixed
     * @param attachmentPoint
     * @param limit
     * @param aggregateDeductible
     * @param aggregateLimit
     * @param reinstatementPremiumFactors
     * @param premiumAllocation
     */
    public WXLContract(double cededPremiumFixed, double attachmentPoint, double limit, double aggregateDeductible,
                       double aggregateLimit, List<Double> reinstatementPremiumFactors,
                       IPremiumAllocationStrategy premiumAllocation) {
        super(cededPremiumFixed, attachmentPoint, limit, aggregateDeductible, aggregateLimit, reinstatementPremiumFactors,
            premiumAllocation);
    }

    public ClaimCashflowPacket calculateClaimCeded(ClaimCashflowPacket grossClaim, ClaimStorage storage) {
        if (grossClaim.getBaseClaim().getClaimType().equals(ClaimType.SINGLE)) {
            return super.calculateClaimCeded(grossClaim, storage);
        }
        return new ClaimCashflowPacket();
    }

}