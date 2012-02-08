package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.nonproportional;

import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.IReinsuranceContract;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.IReinsuranceContractStrategy;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.ReinsuranceContractType;

import java.util.List;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class WCXLConstractStrategy extends XLConstractStrategy implements IReinsuranceContractStrategy {

    public ReinsuranceContractType getType() {
        return ReinsuranceContractType.WCXL;
    }

    public IReinsuranceContract getContract(List<UnderwritingInfoPacket> underwritingInfoPackets,
                                            ThresholdStore termDeductible, EqualUsagePerPeriodThresholdStore termLimit) {
        double cededPremiumFixed = getCededPremiumFixed(underwritingInfoPackets);
        List<Double> reinstatementPremiumFactors = (List<Double>) reinstatementPremiums.getValues().get(0);
        return new WCXLContract(cededPremiumFixed, attachmentPoint, limit, aggregateDeductible, aggregateLimit,
                stabilization, reinstatementPremiumFactors, riPremiumSplit);
    }
}
