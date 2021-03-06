package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.nonproportional;

import org.pillarone.riskanalytics.core.simulation.IPeriodCounter;
import org.pillarone.riskanalytics.domain.pc.cf.claim.*;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.AggregateEventClaimStorageContainer;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.AggregateEventClaimsStorage;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.ClaimStorage;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.allocation.IRIPremiumSplitStrategy;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stabilization.IStabilizationStrategy;

import java.util.List;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
// todo(sku): get rid off code duplication
public class TermCXLContract extends TermXLContract {

    protected AggregateEventClaimStorageContainer cededShareByEvent = new AggregateEventClaimStorageContainer();

    /**
     * All provided values have to be absolute! Scaling is done within the parameter strategy.
     *
     * @param cededPremiumFixed
     * @param attachmentPoint
     * @param limit
     * @param aggregateDeductible
     * @param aggregateLimit
     * @param stabilization
     * @param reinstatementPremiumFactors
     * @param riPremiumSplit
     * @param termDeductible
     * @param termLimit
     */
    public TermCXLContract(double cededPremiumFixed, double attachmentPoint, double limit, double aggregateDeductible,
                           double aggregateLimit, IStabilizationStrategy stabilization,
                           List<Double> reinstatementPremiumFactors, IRIPremiumSplitStrategy riPremiumSplit,
                           IPeriodDependingThresholdStore termDeductible, IPeriodDependingThresholdStore termLimit) {
        super(cededPremiumFixed, attachmentPoint, limit, aggregateDeductible, aggregateLimit, stabilization,
                reinstatementPremiumFactors, riPremiumSplit, termDeductible, termLimit);
    }

    /**
     * reset cededShareByEvent
     * @param grossClaims
     * @param grossUnderwritingInfo
     */
    public void initBasedOnAggregateCalculations(List<ClaimCashflowPacket> grossClaims, List<UnderwritingInfoPacket> grossUnderwritingInfo) {
        cededShareByEvent.reset();
    }

    protected AggregateEventClaimsStorage getClaimsStorage(ClaimCashflowPacket grossClaim) {
        return cededShareByEvent.get(grossClaim, this);
    }

    protected void cededFactor(BasedOnClaimProperty claimPropertyBase, AggregateEventClaimsStorage storage, double stabilizationFactor) {
        double aggregateLimitValue = periodLimit.get(claimPropertyBase, stabilizationFactor);
        if (aggregateLimitValue > 0) {
            double claimPropertyCumulated = storage.getCumulated(claimPropertyBase);
            // if the following two properties are of different values there are several updates for an event in the same period
            double claimPropertyIncremental = storage.getIncremental(claimPropertyBase);
            double claimPropertyIncrementalLast = storage.getIncrementalLast(claimPropertyBase);
            double ceded = Math.min(Math.max(-claimPropertyCumulated - attachmentPoint * stabilizationFactor, 0), limit * stabilizationFactor);
            double cededAfterAAD = Math.max(0, ceded - periodDeductible.get(claimPropertyBase, stabilizationFactor));
            double reduceAAD = ceded - cededAfterAAD;
            cededAfterAAD = Math.max(0, cededAfterAAD - storage.getAadReductionInPeriod(claimPropertyBase));
            storage.addAadReductionInPeriod(claimPropertyBase, reduceAAD);
            periodDeductible.set(Math.max(0, periodDeductible.get(claimPropertyBase) - reduceAAD), claimPropertyBase);
            double incrementalCeded = Math.max(0, cededAfterAAD - storage.getCumulatedCeded(claimPropertyBase));
            double cededAfterAAL = aggregateLimitValue > incrementalCeded ? incrementalCeded : aggregateLimitValue;
            periodLimit.plus(-cededAfterAAL, claimPropertyBase);
            double factor = claimPropertyIncrementalLast == 0 ? 0 : cededAfterAAL / claimPropertyIncrementalLast;
            storage.setCededFactor(claimPropertyBase, factor);
            storage.update(claimPropertyBase, cededAfterAAL);
        }
    }

    @Override
    public ClaimCashflowPacket calculateClaimCeded(ClaimCashflowPacket grossClaim, ClaimStorage storage, IPeriodCounter periodCounter) {
        if (isClaimTypeCovered(grossClaim)) {
            double stabilizationFactor = storage.stabilizationFactor(grossClaim, stabilization, periodCounter);
            AggregateEventClaimsStorage eventStorage = updateCededFactor(grossClaim, stabilizationFactor);
            if (eventStorage != null) {
                double cededFactorUltimate = 0;
                IClaimRoot cededBaseClaim = storage.getCededClaimRoot();

                if (cededBaseClaim == null) {
                    // first time this gross claim is treated by this contract
                    cededFactorUltimate = eventStorage.getCededFactor(BasedOnClaimProperty.ULTIMATE_UNINDEXED);
                    cededBaseClaim = storage.lazyInitCededClaimRoot(cededFactorUltimate);
                }
                double cededFactorUltimateIndexed = eventStorage.getCededFactor(BasedOnClaimProperty.ULTIMATE_INDEXED);
                double cededFactorReported = eventStorage.getCededFactor(BasedOnClaimProperty.REPORTED);
                double cededFactorPaid = eventStorage.getCededFactor(BasedOnClaimProperty.PAID);

                ClaimCashflowPacket cededClaim = cededClaimWithAdjustedReported(grossClaim, storage, cededFactorUltimate,
                        cededFactorUltimateIndexed, stabilizationFactor, cededFactorReported, cededFactorPaid);
                add(grossClaim, cededClaim);
                return cededClaim;
            }
        }
        return ClaimUtils.getCededClaim(grossClaim, storage, 0, 0, 0, 0, false);
    }

    private AggregateEventClaimsStorage updateCededFactor(ClaimCashflowPacket grossClaim, double stabilizationFactor) {
            AggregateEventClaimsStorage storage = getClaimsStorage(grossClaim);
            if (storage == null) {
                storage = new AggregateEventClaimsStorage();
                cededShareByEvent.add(grossClaim, this, storage);
            }
            storage.add(grossClaim);
            cededFactor(BasedOnClaimProperty.ULTIMATE_UNINDEXED, storage, stabilizationFactor);
            cededFactor(BasedOnClaimProperty.REPORTED, storage, stabilizationFactor);
            cededFactor(BasedOnClaimProperty.PAID, storage, stabilizationFactor);
            return storage;
    }

    /**
     * This contract covers only event and aggregate event claims.
     * @param grossClaim
     * @return
     */
    protected boolean isClaimTypeCovered(ClaimCashflowPacket grossClaim) {
        return grossClaim.getBaseClaim().getClaimType().equals(ClaimType.EVENT)
                || grossClaim.getBaseClaim().getClaimType().equals(ClaimType.AGGREGATED_EVENT);
    }

}
