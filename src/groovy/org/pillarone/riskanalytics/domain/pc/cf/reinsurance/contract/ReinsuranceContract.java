package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract;

import org.apache.commons.lang.ArrayUtils;
import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.components.Component;
import org.pillarone.riskanalytics.core.components.PeriodStore;
import org.pillarone.riskanalytics.core.packets.PacketList;
import org.pillarone.riskanalytics.core.parameterization.ConstrainedMultiDimensionalParameter;
import org.pillarone.riskanalytics.core.parameterization.ConstraintsFactory;
import org.pillarone.riskanalytics.core.simulation.IPeriodCounter;
import org.pillarone.riskanalytics.core.simulation.engine.IterationScope;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.pc.cf.claim.IClaimRoot;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.legalentity.LegalEntityDefaultPacket;
import org.pillarone.riskanalytics.domain.pc.cf.legalentity.LegalEntityPortionConstraints;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.ContractFinancialsPacket;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.proportional.commission.CommissionPacket;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.cover.CoverAttributeStrategyType;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.cover.ICoverAttributeStrategy;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.cover.IncludeType;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.cover.period.IPeriodStrategy;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.cover.period.PeriodStrategyType;

import java.util.*;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class ReinsuranceContract extends Component implements IReinsuranceContractMarker {

    private IterationScope iterationScope;
    private PeriodStore periodStore;

    private PacketList<ClaimCashflowPacket> inClaims = new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket.class);
    private PacketList<UnderwritingInfoPacket> inUnderwritingInfo = new PacketList<UnderwritingInfoPacket>(UnderwritingInfoPacket.class);
    private PacketList<LegalEntityDefaultPacket> inReinsurersDefault = new PacketList<LegalEntityDefaultPacket>(LegalEntityDefaultPacket.class);

    private PacketList<ClaimCashflowPacket> outClaimsNet = new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket.class);
    private PacketList<ClaimCashflowPacket> outClaimsCeded = new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket.class);
    private PacketList<UnderwritingInfoPacket> outUnderwritingInfoNet = new PacketList<UnderwritingInfoPacket>(UnderwritingInfoPacket.class);
    private PacketList<UnderwritingInfoPacket> outUnderwritingInfoCeded = new PacketList<UnderwritingInfoPacket>(UnderwritingInfoPacket.class);
    private PacketList<ContractFinancialsPacket> outContractFinancials = new PacketList<ContractFinancialsPacket>(ContractFinancialsPacket.class);
    private PacketList<CommissionPacket> outCommission = new PacketList<CommissionPacket>(CommissionPacket.class);


    private ConstrainedMultiDimensionalParameter parmReinsurers = new ConstrainedMultiDimensionalParameter(
            Collections.emptyList(), LegalEntityPortionConstraints.COLUMN_TITLES,
            ConstraintsFactory.getConstraints(LegalEntityPortionConstraints.IDENTIFIER));
    private ICoverAttributeStrategy parmCover = CoverAttributeStrategyType.getStrategy(
            CoverAttributeStrategyType.ALL, ArrayUtils.toMap(new Object[][]{{"reserves", IncludeType.NOTINCLUDED}}));
    private IPeriodStrategy parmCoveredPeriod = PeriodStrategyType.getDefault();

    private IReinsuranceContractStrategy parmContractStrategy = ReinsuranceContractType.getDefault();


    @Override
    protected void doCalculation() {
        // todo(sku): reinsurer default and recovery
        // check cover
        filterInChannels();
        updateContractParameters();
        fillGrossClaims();
        calculateCededClaims();
        calculateCededPremium();
        calculateCommission();
        discountClaims();
    }

    /**
     * filter according to covered period and covered claims generators, segments and companies
     */
    private void filterInChannels() {
        timeFilter();
        coverFilter();
    }

    /**
     * filter according to covered period and occurrence date of claim
     */
    private void timeFilter() {
        List<ClaimCashflowPacket> uncoveredClaims = new ArrayList<ClaimCashflowPacket>();
        for (ClaimCashflowPacket grossClaim : inClaims) {
            if (!parmCoveredPeriod.isCovered(grossClaim.getOccurrenceDate())) {
                uncoveredClaims.add(grossClaim);
            }
        }
        inClaims.removeAll(uncoveredClaims);
    }

    /**
     * filter according to covered claims generators, segments and companies (parmCover)
     */
    private void coverFilter() {

    }

    /**
     * add in every covered period a new contract to the periodStore
     */
    private void updateContractParameters() {
        if (isCurrentPeriodCovered()) {
            periodStore.put(REINSURANCE_CONTRACT, parmContractStrategy.getContract(inUnderwritingInfo));
        }
    }

    /**
     * Make sure a ClaimStorage object is created for every new CashflowClaimPacket and put in the first time slot of
     * the periodStore with key CLAIM_HISTORY. This objects contain the incremental history for paid and reported.
     * Put a list of claims sorted by update date with an update in this period containing a reference to their history to the
     * current periodStore time slot using key GROSS_CLAIMS.
     *
     * @return all contracts with new claim updates in current period
     */
    private Set<IReinsuranceContract> fillGrossClaims() {
        Set<IReinsuranceContract> contracts = new HashSet<IReinsuranceContract>();
        IPeriodCounter periodCounter = iterationScope.getPeriodScope().getPeriodCounter();
        Map<IClaimRoot, ClaimStorage> claimsHistories =
                (HashMap<IClaimRoot, ClaimStorage>) periodStore.getFirstPeriod(CLAIM_HISTORY);
        List<ClaimHistoryAndApplicableContract> currentPeriodGrossClaims = new ArrayList<ClaimHistoryAndApplicableContract>();
        if (claimsHistories == null) {
            claimsHistories = new HashMap<IClaimRoot, ClaimStorage>();
            periodStore.put(CLAIM_HISTORY, claimsHistories);
            for (ClaimCashflowPacket claim : inClaims) {
                int occurrencePeriod = claim.occurrencePeriod(periodCounter);
                contracts.add(newClaimOccurredInCurrentPeriod(claim, occurrencePeriod, claimsHistories, currentPeriodGrossClaims));
            }
        }
        else {
            for (ClaimCashflowPacket claim : inClaims) {
                int occurrencePeriod = claim.occurrencePeriod(periodCounter);
                int currentPeriod = iterationScope.getPeriodScope().getCurrentPeriod();
                if (currentPeriod == occurrencePeriod) {
                    contracts.add(newClaimOccurredInCurrentPeriod(claim, occurrencePeriod, claimsHistories, currentPeriodGrossClaims));
                }
                else {
                    ClaimStorage claimStorage = claimsHistories.get(claim.getBaseClaim());
                    IReinsuranceContract contract = (IReinsuranceContract) periodStore.get(REINSURANCE_CONTRACT, occurrencePeriod - currentPeriod);
                    contracts.add(contract);
                    ClaimHistoryAndApplicableContract claimWithHistory = new ClaimHistoryAndApplicableContract(claim, claimStorage, contract);
                    currentPeriodGrossClaims.add(claimWithHistory);
                }
            }
        }
        Collections.sort(currentPeriodGrossClaims, SortClaimHistoryAndApplicableContract.getInstance());
        periodStore.put(GROSS_CLAIMS, currentPeriodGrossClaims);
        return contracts;
    }

    private IReinsuranceContract newClaimOccurredInCurrentPeriod(ClaimCashflowPacket claim, int occurrencePeriod,
                            Map<IClaimRoot, ClaimStorage> claimsHistories,
                            List<ClaimHistoryAndApplicableContract> currentPeriodGrossClaims) {
        IReinsuranceContract contract = (IReinsuranceContract) periodStore.get(REINSURANCE_CONTRACT, occurrencePeriod);
        ClaimStorage claimStorage = new ClaimStorage(claim);
        claimsHistories.put(claim.getBaseClaim(), claimStorage);
        ClaimHistoryAndApplicableContract claimWithHistory = new ClaimHistoryAndApplicableContract(claim, claimStorage, contract);
        currentPeriodGrossClaims.add(claimWithHistory);
        return contract;
    }

    private void calculateCededClaims() {
        List<ClaimHistoryAndApplicableContract> currentPeriodGrossClaims = (List<ClaimHistoryAndApplicableContract>) periodStore.get(GROSS_CLAIMS);
        for (ClaimHistoryAndApplicableContract grossClaim : currentPeriodGrossClaims) {
            ClaimCashflowPacket cededClaim = grossClaim.getCededClaim();
//            cededClaim.scale(coveredByReinsurer);
            outClaimsCeded.add(cededClaim);
        }
    }

    private void calculateCededPremium() {

    }

    private void calculateCommission() {

    }

    private void discountClaims() {
        // use utility method as discounting is required in several places
    }

    private boolean isCurrentPeriodCovered() {
        DateTime periodStart = iterationScope.getPeriodScope().getCurrentPeriodStartDate();
        DateTime periodEnd = iterationScope.getPeriodScope().getNextPeriodStartDate();
        return parmCoveredPeriod.isCovered(periodStart) || parmCoveredPeriod.isCovered(periodEnd);
    }

// periodStore keys
    private static final String REINSURANCE_CONTRACT = "reinsurance contract";
    private static final String GROSS_CLAIMS = "gross claims";
    private static final String CLAIM_HISTORY = "claim history";

    public IterationScope getIterationScope() {
        return iterationScope;
    }

    public void setIterationScope(IterationScope iterationScope) {
        this.iterationScope = iterationScope;
    }

    public PeriodStore getPeriodStore() {
        return periodStore;
    }

    public void setPeriodStore(PeriodStore periodStore) {
        this.periodStore = periodStore;
    }

    public PacketList<ClaimCashflowPacket> getInClaims() {
        return inClaims;
    }

    public void setInClaims(PacketList<ClaimCashflowPacket> inClaims) {
        this.inClaims = inClaims;
    }

    public PacketList<UnderwritingInfoPacket> getInUnderwritingInfo() {
        return inUnderwritingInfo;
    }

    public void setInUnderwritingInfo(PacketList<UnderwritingInfoPacket> inUnderwritingInfo) {
        this.inUnderwritingInfo = inUnderwritingInfo;
    }

    public PacketList<LegalEntityDefaultPacket> getInReinsurersDefault() {
        return inReinsurersDefault;
    }

    public void setInReinsurersDefault(PacketList<LegalEntityDefaultPacket> inReinsurersDefault) {
        this.inReinsurersDefault = inReinsurersDefault;
    }

    public PacketList<ClaimCashflowPacket> getOutClaimsNet() {
        return outClaimsNet;
    }

    public void setOutClaimsNet(PacketList<ClaimCashflowPacket> outClaimsNet) {
        this.outClaimsNet = outClaimsNet;
    }

    public PacketList<ClaimCashflowPacket> getOutClaimsCeded() {
        return outClaimsCeded;
    }

    public void setOutClaimsCeded(PacketList<ClaimCashflowPacket> outClaimsCeded) {
        this.outClaimsCeded = outClaimsCeded;
    }

    public PacketList<UnderwritingInfoPacket> getOutUnderwritingInfoNet() {
        return outUnderwritingInfoNet;
    }

    public void setOutUnderwritingInfoNet(PacketList<UnderwritingInfoPacket> outUnderwritingInfoNet) {
        this.outUnderwritingInfoNet = outUnderwritingInfoNet;
    }

    public PacketList<UnderwritingInfoPacket> getOutUnderwritingInfoCeded() {
        return outUnderwritingInfoCeded;
    }

    public void setOutUnderwritingInfoCeded(PacketList<UnderwritingInfoPacket> outUnderwritingInfoCeded) {
        this.outUnderwritingInfoCeded = outUnderwritingInfoCeded;
    }

    public PacketList<ContractFinancialsPacket> getOutContractFinancials() {
        return outContractFinancials;
    }

    public void setOutContractFinancials(PacketList<ContractFinancialsPacket> outContractFinancials) {
        this.outContractFinancials = outContractFinancials;
    }

    public PacketList<CommissionPacket> getOutCommission() {
        return outCommission;
    }

    public void setOutCommission(PacketList<CommissionPacket> outCommission) {
        this.outCommission = outCommission;
    }

    public ConstrainedMultiDimensionalParameter getParmReinsurers() {
        return parmReinsurers;
    }

    public void setParmReinsurers(ConstrainedMultiDimensionalParameter parmReinsurers) {
        this.parmReinsurers = parmReinsurers;
    }

    public ICoverAttributeStrategy getParmCover() {
        return parmCover;
    }

    public void setParmCover(ICoverAttributeStrategy parmCover) {
        this.parmCover = parmCover;
    }

    /**
     * Defines the kind of contract and parametrization
     */
    public IReinsuranceContractStrategy getParmContractStrategy() {
        return parmContractStrategy;
    }

    public void setParmContractStrategy(IReinsuranceContractStrategy parmContractStrategy) {
        this.parmContractStrategy = parmContractStrategy;
    }

    public IPeriodStrategy getParmCoveredPeriod() {
        return parmCoveredPeriod;
    }

    public void setParmCoveredPeriod(IPeriodStrategy parmCoveredPeriod) {
        this.parmCoveredPeriod = parmCoveredPeriod;
    }
}