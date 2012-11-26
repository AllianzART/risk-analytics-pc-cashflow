package org.pillarone.riskanalytics.domain.pc.cf.claim.generator.attritional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pillarone.riskanalytics.core.parameterization.ConstrainedMultiDimensionalParameter;
import org.pillarone.riskanalytics.core.parameterization.ConstrainedString;
import org.pillarone.riskanalytics.core.parameterization.ConstraintsFactory;
import org.pillarone.riskanalytics.core.simulation.IPeriodCounter;
import org.pillarone.riskanalytics.core.simulation.SimulationException;
import org.pillarone.riskanalytics.core.util.GroovyUtils;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimRoot;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimType;
import org.pillarone.riskanalytics.domain.pc.cf.claim.GrossClaimRoot;
import org.pillarone.riskanalytics.domain.pc.cf.claim.generator.AbstractClaimsGenerator;
import org.pillarone.riskanalytics.domain.pc.cf.claim.generator.contractBase.IReinsuranceContractBaseStrategy;
import org.pillarone.riskanalytics.domain.pc.cf.claim.generator.contractBase.ReinsuranceContractBaseType;
import org.pillarone.riskanalytics.domain.pc.cf.indexing.BaseDateMode;
import org.pillarone.riskanalytics.domain.pc.cf.indexing.Factors;
import org.pillarone.riskanalytics.domain.pc.cf.indexing.IndexMode;
import org.pillarone.riskanalytics.domain.pc.cf.indexing.IndexUtils;
import org.pillarone.riskanalytics.domain.pc.cf.pattern.IPayoutPatternMarker;
import org.pillarone.riskanalytics.domain.pc.cf.reserve.updating.aggregate.*;
import org.pillarone.riskanalytics.domain.utils.constraint.DoubleConstraints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class AttritionalClaimsGenerator extends AbstractClaimsGenerator {

    static Log LOG = LogFactory.getLog(AttritionalClaimsGenerator.class);

    private AttritionalClaimsModel subClaimsModel = new AttritionalClaimsModel();
    private IReinsuranceContractBaseStrategy parmParameterizationBasis = ReinsuranceContractBaseType.getStrategy(
            ReinsuranceContractBaseType.PLEASESELECT, new HashMap());
    private ConstrainedString parmPayoutPattern = new ConstrainedString(IPayoutPatternMarker.class, "");
    private PayoutPatternBase parmPayoutPatternBase = PayoutPatternBase.PERIOD_START_DATE;
    private IAggregateActualClaimsStrategy parmActualClaims = AggregateActualClaimsStrategyType.getDefault();
    private IAggregateUpdatingMethodologyStrategy parmUpdatingMethodology = AggregateUpdatingMethodologyStrategyType.getDefault();
    private ConstrainedMultiDimensionalParameter parmDeterministicClaims = new ConstrainedMultiDimensionalParameter(
            GroovyUtils.convertToListOfList(new Object[]{0d, 0d}), Arrays.asList(REAL_PERIOD, CLAIM_VALUE),
            ConstraintsFactory.getConstraints(DoubleConstraints.IDENTIFIER));

    @Override
    protected void doCalculation(String phase) {
        try {
            if (provideClaims(phase)) {
                IPeriodCounter periodCounter = periodScope.getPeriodCounter();
                List<ClaimRoot> baseClaims = new ArrayList<ClaimRoot>();
                List<GrossClaimRoot> claimsAfterSplit = new ArrayList<GrossClaimRoot>();
                List<ClaimCashflowPacket> claims = new ArrayList<ClaimCashflowPacket>();

                List<Factors> runoffFactors = null;
                // Check that the period we are in covers new claims.
                if (periodScope.getCurrentPeriod() < globalLastCoveredPeriod) {
                    if (globalDeterministicMode) {
                        baseClaims = getDeterministicClaims(parmDeterministicClaims, periodScope, ClaimType.ATTRITIONAL);
                    } else {
                        List<Factors> severityFactors = IndexUtils.filterFactors(inFactors, subClaimsModel.getParmSeverityIndices(),
                                IndexMode.STEPWISE_PREVIOUS, BaseDateMode.START_OF_PROJECTION, null);
                        baseClaims = subClaimsModel.baseClaims(inUnderwritingInfo, inEventFrequencies, inEventSeverities,
                                severityFactors, parmParameterizationBasis, this, periodScope);
                    }
                    baseClaims = parmUpdatingMethodology.updatingUltimate(baseClaims, parmActualClaims, periodCounter,
                            globalUpdateDate, inPatterns, periodScope.getCurrentPeriod(), DAYS_360, parmPayoutPatternBase);
                    checkBaseClaims(baseClaims, globalSanityChecks, iterationScope);
                    runoffFactors = new ArrayList<Factors>();
                    List<GrossClaimRoot> grossClaimRoots = baseClaimsOfCurrentPeriodAdjustedPattern(baseClaims, parmPayoutPattern, parmActualClaims,
                            periodScope, parmPayoutPatternBase);
                    claimsAfterSplit = parmParameterizationBasis.splitClaims(grossClaimRoots, periodScope);
                    storeClaimsWhichOccurInFuturePeriods(claimsAfterSplit, periodStore);
                    claims = cashflowsInCurrentPeriod(claimsAfterSplit, runoffFactors, periodScope);
                }
                developClaimsOfFormerPeriods(claims, periodCounter, runoffFactors);
                checkCashflowClaims(claims, globalSanityChecks);
                doCashflowChecks(claims, claimsAfterSplit, baseClaims);
                setTechnicalProperties(claims);
                outClaims.addAll(claims);
            }
            else {
                prepareProvidingClaimsInNextPeriodOrNot(phase);
            }
        }
        catch (SimulationException e) {
            throw new SimulationException("Problem in claims generator in iteration : "
                    + iterationScope.getCurrentIteration() + ". Period :" + periodScope.getCurrentPeriod()
                    + " with seed : " + simulationScope.getSimulation().getRandomSeed().toString()
                    +  "\n \n " + e.getMessage(), e);
        }
    }

    public AttritionalClaimsModel getSubClaimsModel() {
        return subClaimsModel;
    }

    public void setSubClaimsModel(AttritionalClaimsModel subClaimsModel) {
        this.subClaimsModel = subClaimsModel;
    }

    public IReinsuranceContractBaseStrategy getParmParameterizationBasis() {
        return parmParameterizationBasis;
    }

    public void setParmParameterizationBasis(IReinsuranceContractBaseStrategy parmParameterizationBasis) {
        this.parmParameterizationBasis = parmParameterizationBasis;
    }

    public ConstrainedString getParmPayoutPattern() {
        return parmPayoutPattern;
    }

    public void setParmPayoutPattern(ConstrainedString parmPayoutPattern) {
        this.parmPayoutPattern = parmPayoutPattern;
    }

    public ConstrainedMultiDimensionalParameter getParmDeterministicClaims() {
        return parmDeterministicClaims;
    }

    public void setParmDeterministicClaims(ConstrainedMultiDimensionalParameter parmDeterministicClaims) {
        this.parmDeterministicClaims = parmDeterministicClaims;
    }

    public IAggregateActualClaimsStrategy getParmActualClaims() {
        return parmActualClaims;
    }

    public void setParmActualClaims(IAggregateActualClaimsStrategy parmActualClaims) {
        this.parmActualClaims = parmActualClaims;
    }

    public IAggregateUpdatingMethodologyStrategy getParmUpdatingMethodology() {
        return parmUpdatingMethodology;
    }

    public void setParmUpdatingMethodology(IAggregateUpdatingMethodologyStrategy parmUpdatingMethodology) {
        this.parmUpdatingMethodology = parmUpdatingMethodology;
    }

    public PayoutPatternBase getParmPayoutPatternBase() {
        return parmPayoutPatternBase;
    }

    public void setParmPayoutPatternBase(PayoutPatternBase parmPayoutPatternBase) {
        this.parmPayoutPatternBase = parmPayoutPatternBase;
    }
}
