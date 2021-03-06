package org.pillarone.riskanalytics.domain.pc.cf.reinsurance

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.pillarone.riskanalytics.core.components.Component
import org.pillarone.riskanalytics.core.components.DynamicComposedComponent
import org.pillarone.riskanalytics.core.packets.PacketList
import org.pillarone.riskanalytics.core.parameterization.ConstrainedMultiDimensionalParameter
import org.pillarone.riskanalytics.core.parameterization.ConstraintsFactory
import org.pillarone.riskanalytics.core.wiring.PortReplicatorCategory
import org.pillarone.riskanalytics.core.wiring.WireCategory
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket
import org.pillarone.riskanalytics.domain.pc.cf.creditrisk.LegalEntityDefault
import org.pillarone.riskanalytics.domain.pc.cf.exposure.CededUnderwritingInfoPacket
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket
import org.pillarone.riskanalytics.domain.pc.cf.legalentity.LegalEntityPortionConstraints
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.ReinsuranceContractType
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.RetroactiveReinsuranceContract
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.cover.CoverAttributeStrategyType
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.cover.MatrixCoverAttributeStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.cover.NoneCoverAttributeStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.cover.period.PeriodStrategyType
import org.pillarone.riskanalytics.domain.utils.marker.IReinsuranceContractMarker

/**
 * The current implementation does not allow any contract chains and does not check double cover.
 *
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
class RetroactiveReinsuranceContracts extends DynamicComposedComponent {

    static Log LOG = LogFactory.getLog(RetroactiveReinsuranceContracts)

    PacketList<ClaimCashflowPacket> inClaims = new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket)
    PacketList<UnderwritingInfoPacket> inUnderwritingInfo = new PacketList<UnderwritingInfoPacket>(UnderwritingInfoPacket)
    PacketList<LegalEntityDefault> inLegalEntityDefault = new PacketList<LegalEntityDefault>(LegalEntityDefault)

    PacketList<ClaimCashflowPacket> outClaimsCeded = new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket)
    PacketList<ClaimCashflowPacket> outClaimsInward = new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket)
    PacketList<CededUnderwritingInfoPacket> outUnderwritingInfoCeded = new PacketList<CededUnderwritingInfoPacket>(CededUnderwritingInfoPacket)
    PacketList<UnderwritingInfoPacket> outUnderwritingInfoInward = new PacketList<UnderwritingInfoPacket>(UnderwritingInfoPacket)
    List<ClaimMerger> claimMergers = []
    List<UnderwritingInfoMerger> uwInfoMergers = []

    public RetroactiveReinsuranceContract createDefaultSubComponent(){
        return new RetroactiveReinsuranceContract(
                parmReinsurers : new ConstrainedMultiDimensionalParameter(
                    Collections.emptyList(), LegalEntityPortionConstraints.COLUMN_TITLES,
                    ConstraintsFactory.getConstraints(LegalEntityPortionConstraints.IDENTIFIER)),
                parmCover : CoverAttributeStrategyType.getDefault(),
                parmCoveredPeriod : PeriodStrategyType.getRetroActiveDefault(),
                parmContractStrategy : ReinsuranceContractType.getDefault());
    }

    @Override
    void wire() {
        if (noneTrivialContracts()) {
            replicateInChannels this, inLegalEntityDefault
            replicateOutChannels this, outClaimsInward
            replicateOutChannels this, outUnderwritingInfoInward
            wireContractsBasedOnGross()
            wireContractsBaseOnNetContracts()
            wireContractsBaseOnCededContracts()
            wireWithMerger()
            wireProgramIndependentReplications()
        }
    }

/**
 * in channels of contracts based on original (gross) claims can be wired directly with replicating in channels
 */
    private void wireContractsBasedOnGross() {
        for (RetroactiveReinsuranceContract contract : componentList) {
            MatrixCoverAttributeStrategy strategy = getCoverStrategy(contract)
            if (strategy?.coverGrossClaimsOnly()) {
                doWire PortReplicatorCategory, contract, 'inClaims', this, 'inClaims'
                doWire PortReplicatorCategory, contract, 'inUnderwritingInfo', this, 'inUnderwritingInfo'
            }
        }
    }

    private void wireWithMerger() {
        for (RetroactiveReinsuranceContract contract : componentList) {
            MatrixCoverAttributeStrategy strategy = getCoverStrategy(contract)
            if (strategy?.mergerRequired()) {
                ClaimMerger claimMerger = new ClaimMerger(coverAttributeStrategy: strategy, name: "${contract.name}Preceeding")
                UnderwritingInfoMerger uwInfoMerger = new UnderwritingInfoMerger(coverAttributeStrategy: strategy, name: "${contract.name}Preceeding")
                claimMergers << claimMerger
                uwInfoMergers << uwInfoMerger
                List<IReinsuranceContractMarker> benefitContracts = strategy.benefitContracts
                List<IReinsuranceContractMarker> coveredNetOfContracts = strategy.coveredNetOfContracts()
                List<IReinsuranceContractMarker> coveredCededOfContracts = strategy.coveredCededOfContracts()
                for (IReinsuranceContractMarker coveredContract : coveredCededOfContracts) {
                    doWire WireCategory, claimMerger, 'inClaimsCeded', coveredContract, 'outClaimsCeded'
                    doWire WireCategory, uwInfoMerger, 'inUnderwritingInfoCeded', coveredContract, 'outUnderwritingInfoCeded'
                }
                if (coveredNetOfContracts.size() > 0 || benefitContracts.size() > 0) {
                    for (IReinsuranceContractMarker coveredContract : coveredNetOfContracts) {
                        doWire WireCategory, claimMerger, 'inClaimsNet', coveredContract, 'outClaimsNet'
                        doWire WireCategory, claimMerger, 'inClaimsCededForNet', coveredContract, 'outClaimsCeded'
                        doWire WireCategory, uwInfoMerger, 'inUnderwritingInfoNet', coveredContract, 'outUnderwritingInfoGNPI'
                    }
                    for (IReinsuranceContractMarker benefitContract : benefitContracts) {
                        doWire WireCategory, claimMerger, 'inClaimsBenefit', benefitContract, 'outClaimsCeded'
                        doWire WireCategory, uwInfoMerger, 'inUnderwritingInfoBenefit', benefitContract, 'outUnderwritingInfoCeded'
                    }
                    doWire PortReplicatorCategory, claimMerger, 'inClaimsGross', this, 'inClaims'
                    doWire PortReplicatorCategory, uwInfoMerger, 'inUnderwritingInfoGross', this, 'inUnderwritingInfo'
                }
                else if (strategy.hasGrossFilters()) {
                    doWire PortReplicatorCategory, claimMerger, 'inClaimsGross', this, 'inClaims'
                    doWire PortReplicatorCategory, uwInfoMerger, 'inUnderwritingInfoGross', this, 'inUnderwritingInfo'
                }
                doWire WireCategory, contract, 'inClaims', claimMerger, 'outClaims'
                doWire WireCategory, contract, 'inUnderwritingInfo', uwInfoMerger, 'outUnderwritingInfo'
            }
        }
    }

    private MatrixCoverAttributeStrategy getCoverStrategy(RetroactiveReinsuranceContract contract) {
        if (contract.parmCover instanceof MatrixCoverAttributeStrategy) {
            return (MatrixCoverAttributeStrategy) contract.parmCover
        } else if (contract.parmCover instanceof NoneCoverAttributeStrategy) {
            return null
        } else {
            throw new IllegalArgumentException('This Reinsurance Program allows only Matrix Cover. Developer has to restrict available cover types on model level.')
        }
    }

    private void wireContractsBaseOnNetContracts() {
        for (RetroactiveReinsuranceContract contract : componentList) {
            MatrixCoverAttributeStrategy strategy = getCoverStrategy(contract)
            if (strategy && !strategy?.mergerRequired()) {
                for (IReinsuranceContractMarker coveredContract : strategy.coveredNetOfContracts()) {
                    doWire WireCategory, contract, 'inClaims', coveredContract, 'outClaimsNet'
                    doWire WireCategory, contract, 'inUnderwritingInfo', coveredContract, 'outUnderwritingInfoGNPI'
                }
            }
        }
    }

    private void wireContractsBaseOnCededContracts() {
        for (RetroactiveReinsuranceContract contract : componentList) {
            MatrixCoverAttributeStrategy strategy = getCoverStrategy(contract)
            if (strategy && !strategy?.mergerRequired()) {
                for (IReinsuranceContractMarker coveredContract : strategy.coveredCededOfContracts()) {
                    doWire WireCategory, contract, 'inClaims', coveredContract, 'outClaimsCeded'
                    doWire WireCategory, contract, 'inUnderwritingInfo', coveredContract, 'outUnderwritingInfoCeded'
                }
            }
        }
    }

    /**
     * All ceded information is wired directly to a replicating channel independently of specific reinsurance program.
     * Includes all replicating wiring independent of a p14n.
     */
    private void wireProgramIndependentReplications() {
        for (RetroactiveReinsuranceContract contract : componentList) {
            doWire PortReplicatorCategory, this, 'outUnderwritingInfoCeded', contract, 'outUnderwritingInfoCeded'
            doWire PortReplicatorCategory, this, 'outClaimsCeded', contract, 'outClaimsCeded'
        }
    }

    /**
     * @return true if at least one contract has a none trivial cover strategy
     */
    private boolean noneTrivialContracts() {
        List<RetroactiveReinsuranceContract> contractsWithNoCover = new ArrayList<RetroactiveReinsuranceContract>()
        for (RetroactiveReinsuranceContract contract : componentList) {
            if (contract.parmCover.getType().equals(CoverAttributeStrategyType.NONE)) {
                contractsWithNoCover.add(contract)
            }
        }
        LOG.debug("removed contracts: ${contractsWithNoCover.collectAll { it.normalizedName }}");
        componentList.size() > contractsWithNoCover.size()
    }

    /**
     *  Sub components are either properties on the component or in case
     *  of dynamically composed components stored in its componentList.
     *  @return all sub components
     */
    public List<Component> allSubComponents() {
        List<Component> subComponents = super.allSubComponents()
        subComponents.addAll(claimMergers)
        subComponents.addAll(uwInfoMergers)
        return subComponents
    }
}
