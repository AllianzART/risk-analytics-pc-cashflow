package org.pillarone.riskanalytics.domain.pc.cf.reinsurance

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.pillarone.riskanalytics.core.components.DynamicComposedComponent
import org.pillarone.riskanalytics.core.packets.PacketList
import org.pillarone.riskanalytics.core.parameterization.ConstrainedMultiDimensionalParameter
import org.pillarone.riskanalytics.core.parameterization.ConstraintsFactory
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket
import org.pillarone.riskanalytics.domain.pc.cf.creditrisk.LegalEntityDefault
import org.pillarone.riskanalytics.domain.pc.cf.exposure.CededUnderwritingInfoPacket
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket
import org.pillarone.riskanalytics.domain.pc.cf.legalentity.LegalEntityPortionConstraints
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.ReinsuranceContractType
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.RetroactiveReinsuranceContract
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.RetrospectiveReinsuranceContract
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.RetrospectiveReinsuranceContractType
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.cover.CoverAttributeStrategyType
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.cover.RetrospectiveCoverAttributeStrategyType
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.cover.period.PeriodStrategyType
import org.pillarone.riskanalytics.domain.utils.marker.ILegalEntityMarker
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

    private List contractsBasedOnGrossClaims = []
    /** contains the relations between reinsurance contracts covering legal entities */
    private ListMultimap<ILegalEntityMarker, IReinsuranceContractMarker> coverForLegalEntity = ArrayListMultimap.create()

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
            replicateInChannels this, inClaims
            replicateInChannels this, inUnderwritingInfo
            replicateOutChannels this, outClaimsInward
            replicateOutChannels this, outUnderwritingInfoInward
            replicateOutChannels this, outUnderwritingInfoCeded
            replicateOutChannels this, outClaimsCeded
        }
    }

    /**
     * @return true if at least one contract has a none trivial cover strategy
     */
    private boolean noneTrivialContracts() {
        List<RetrospectiveReinsuranceContract> contractsWithNoCover = new ArrayList<RetrospectiveReinsuranceContract>()
        for (RetrospectiveReinsuranceContract contract: componentList) {
            if (contract.parmCover.getType().equals(CoverAttributeStrategyType.NONE)) {
                contractsWithNoCover.add(contract)
            }
        }
        LOG.debug("removed contracts: ${contractsWithNoCover.collectAll { it.normalizedName }}");
        componentList.size() > contractsWithNoCover.size()
    }

    /**
     * Helper method for wiring when sender or receiver are determined dynamically
     */
    public static void doWire(category, receiver, inChannelName, sender, outChannelName) {
        LOG.debug "$receiver.$inChannelName <- $sender.$outChannelName ($category)"
        println "$receiver.$inChannelName <- $sender.$outChannelName ($category)"
        category.doSetProperty(receiver, inChannelName, category.doGetProperty(sender, outChannelName))
    }
}
