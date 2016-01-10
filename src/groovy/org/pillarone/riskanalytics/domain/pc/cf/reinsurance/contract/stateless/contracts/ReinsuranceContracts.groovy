package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.contracts

import org.pillarone.riskanalytics.core.components.DynamicComposedComponent
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket
import org.pillarone.riskanalytics.core.packets.PacketList
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket
import org.pillarone.riskanalytics.domain.pc.cf.exposure.CededUnderwritingInfoPacket
import org.pillarone.riskanalytics.core.components.Component
import org.pillarone.riskanalytics.core.wiring.PortReplicatorCategory
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.cover.ReinsuranceContractAndBase
import org.pillarone.riskanalytics.domain.utils.constant.ReinsuranceContractBase
import org.pillarone.riskanalytics.core.wiring.WireCategory
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.ReinsuranceContract
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.cover.SelectedCoverStrategy
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.cover.CoverStrategyType

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
class ReinsuranceContracts extends DynamicComposedComponent {

    static Log LOG = LogFactory.getLog(ReinsuranceContracts);

    PacketList<UnderwritingInfoPacket> inUnderwritingInfo = new PacketList<UnderwritingInfoPacket>(UnderwritingInfoPacket)
    PacketList<ClaimCashflowPacket> inClaims= new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket)
    PacketList<ClaimCashflowPacket> outClaimsGross = new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket)
    PacketList<ClaimCashflowPacket> outClaimsNet = new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket)
    PacketList<ClaimCashflowPacket> outClaimsCeded = new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket)
    PacketList<UnderwritingInfoPacket> outUnderwritingInfoGross = new PacketList<UnderwritingInfoPacket>(UnderwritingInfoPacket)
    PacketList<UnderwritingInfoPacket> outUnderwritingInfoNet = new PacketList<UnderwritingInfoPacket>(UnderwritingInfoPacket)
    PacketList<UnderwritingInfoPacket> outUnderwritingInfoGNPI = new PacketList<UnderwritingInfoPacket>(UnderwritingInfoPacket)
    PacketList<CededUnderwritingInfoPacket> outUnderwritingInfoCeded = new PacketList<CededUnderwritingInfoPacket>(CededUnderwritingInfoPacket)

    @Override
    Component createDefaultSubComponent() {
        //FR Broken? subcomponents are ReinsuranceContract objs yet stored in a map of TermReinsuranceContract objs (*different* class)
        new ReinsuranceContract()
    }

    /**
     * key: normalized contract name, value: contract instance
     */
    private Map<String, TermReinsuranceContract> reinsuranceContracts = new HashMap<String, TermReinsuranceContract>()

    public void wire() {
        initContractMap()
        if (contractsWithCoverAvailable()) {
            wireUnderwritingInfo()
            wirePerils()
            wireContracts()
            wireReplicatingOutChannels()
        }
        else {
            wireNoActiveContracts()
        }
    }

    private boolean contractsWithCoverAvailable() {
        List<TermReinsuranceContract> contractsWithNoCover = new ArrayList<TermReinsuranceContract>()
        for (TermReinsuranceContract contract: componentList) {
            if (contract.parmCover.getType().equals(CoverStrategyType.SELECTED)) {
                SelectedCoverStrategy coverStrategy = (SelectedCoverStrategy) contract.parmCover
                coverStrategy.getCoveredReinsuranceContractsAndBase();
                if (coverStrategy.noCover()) {
                    contractsWithNoCover.add(contract)
                }
                return true
            }
        }
        LOG.debug("removed contracts: ${contractsWithNoCover.collectAll { it.normalizedName }}");
        componentList.size() > contractsWithNoCover.size()
    }

    /**
     * wire gross underwriting info to every contract, all contract get the same gross underwriting info
     */
    private void wireUnderwritingInfo() {
        for (ReinsuranceContract contract: componentList) {
            doWire PortReplicatorCategory, contract, 'inUnderwritingInfo', this, 'inUnderwritingInfo'
        }
    }

    /**
     *  wire gross peril information to all contracts covering perils
     */
    private void wirePerils() {
        LOG.debug 'wirePerils()'
        for (TermReinsuranceContract contract: componentList) {
            if (contract.parmCover.getType().equals(CoverStrategyType.ALLGROSSCLAIMS)
//                    || contract.parmCover.getType().equals(CoverStrategyType.ALLCLAIMFILTERS)
                    || !((SelectedCoverStrategy) contract.parmCover).contractBasedCover()) {
                doWire PortReplicatorCategory, contract, 'inClaims', this, 'inClaims'
            }
        }
    }

    /**
     * Wiring for the SELECTED strategy is done according to the structure parameter only. gross and filter claims are
     * applied as an additional and filter in the strategy itself. OR connections are not supported.
     */
    private void wireContracts() {
        LOG.debug 'wireContracts()'
        for (TermReinsuranceContract contract: componentList) {
            if (contract.parmCover.getType().equals(CoverStrategyType.SELECTED)
                    && ((SelectedCoverStrategy) contract.parmCover).contractBasedCover()) {
                List<ReinsuranceContractAndBase> contractsAndBase = ((SelectedCoverStrategy) contract.parmCover).getCoveredReinsuranceContractsAndBase();

                for (ReinsuranceContractAndBase contractAndBase : contractsAndBase) {
                    TermReinsuranceContract sourceContract = reinsuranceContracts.get(contractAndBase.reinsuranceContract.name)
                    boolean coverNet = contractAndBase.contractBase.equals(ReinsuranceContractBase.NET)
                    doWire WireCategory, contract, 'inClaims', sourceContract, coverNet ? 'outClaimsNet' : 'outClaimsCeded'
                }
            }
        }
    }

    private void wireReplicatingOutChannels() {
        LOG.debug 'wireReplicatingOutChannels()'
        for (ReinsuranceContract contract: componentList) {
            doWire PortReplicatorCategory, this, 'outClaimsGross', contract, 'outClaimsGross'
            doWire PortReplicatorCategory, this, 'outClaimsCeded', contract, 'outClaimsCeded'
            doWire PortReplicatorCategory, this, 'outClaimsNet', contract, 'outClaimsNet'
            doWire PortReplicatorCategory, this, 'outUnderwritingInfoGross', contract, 'outUnderwritingInfoGross'
            doWire PortReplicatorCategory, this, 'outUnderwritingInfoNet', contract, 'outUnderwritingInfoNet'
            doWire PortReplicatorCategory, this, 'outUnderwritingInfoGNPI', contract, 'outUnderwritingInfoGNPI'
            doWire PortReplicatorCategory, this, 'outUnderwritingInfoCeded', contract, 'outUnderwritingInfoCeded'
        }
    }

    private void wireNoActiveContracts() {
        LOG.debug 'wireNoActiveContracts()'
        wireUnderwritingInfo()
        doWire WireCategory, this, 'outClaimsGross', this, 'inClaims'
        doWire WireCategory, this, 'outClaimsNet', this, 'inClaims'
    }

    private void initContractMap() {
        for (TermReinsuranceContract contract: componentList) {
            reinsuranceContracts.put(contract.name, contract)
        }
    }

    /**
     * Helper method for wiring when sender or receiver are determined dynamically
     */
    public static void doWire(category, receiver, inChannelName, sender, outChannelName) {
        LOG.debug "$receiver.$inChannelName <- $sender.$outChannelName ($category)"
        category.doSetProperty(receiver, inChannelName, category.doGetProperty(sender, outChannelName))
    }

    public String getGenericSubComponentName() {
        return "contracts"
    }
}
