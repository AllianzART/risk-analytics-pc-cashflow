package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract;

import org.pillarone.riskanalytics.core.simulation.IPeriodCounter;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.CededUnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.indexing.FactorsPacket;

import java.util.List;

/**
 *  Common methods to calculate the effects of a reinsurance contract
 *  implemented by all reinsurance contract strategies.
 *
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public interface IReinsuranceContract {

    /** used to reset deductibles, limits if required
     * @param period
     * @param inFactors
     */
    void initPeriod(int period, List<FactorsPacket> inFactors);


    void add(UnderwritingInfoPacket grossUnderwritingInfo);

    /** This function is used if some preprocessing steps are required before calculating ceded claims on an individual
     *  level, ie CXL, SL. Default implementation is void.
     * @param grossClaim
     */
    void initPeriodClaims(List<ClaimCashflowPacket> grossClaim);

    /**
     *  Calculates the claim covered of the loss net after contracts with
     *  a smaller inuring priority or preceding contracts in the net.
     *  @param grossClaim
     *  @param storage
     *  @return
     */
    ClaimCashflowPacket calculateClaimCeded(ClaimCashflowPacket grossClaim, ClaimStorage storage, IPeriodCounter periodCounter);


    /**
     * @param cededUnderwritingInfos
     * @param netUnderwritingInfos
     * @param coveredByReinsurers
     * @param fillNet if true the second list is filled too
     */
    void calculateUnderwritingInfo(List<CededUnderwritingInfoPacket> cededUnderwritingInfos,
                                   List<UnderwritingInfoPacket> netUnderwritingInfos, double coveredByReinsurers,
                                   boolean fillNet);

}
