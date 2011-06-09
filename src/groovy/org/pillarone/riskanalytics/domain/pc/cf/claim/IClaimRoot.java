package org.pillarone.riskanalytics.domain.pc.cf.claim;

import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.simulation.IPeriodCounter;
import org.pillarone.riskanalytics.domain.pc.cf.event.EventPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.ExposureInfo;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.IReinsuranceContractMarker;
import org.pillarone.riskanalytics.domain.pc.cf.segment.ISegmentMarker;
import org.pillarone.riskanalytics.domain.utils.math.copula.IPerilMarker;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public interface IClaimRoot {

    public double getUltimate();
    public boolean hasEvent();
    public EventPacket getEvent();
    public ClaimType getClaimType();
    public ExposureInfo getExposure();
    public DateTime getExposureStartDate();
    public DateTime getOccurrenceDate();
    public Integer getOccurrencePeriod(IPeriodCounter periodCounter);
      /**
     * @return payout and reported pattern have the same period entries. True even if one of them is null
     */
    public boolean hasSynchronizedPatterns();
    public boolean hasTrivialPayout();
    public boolean hasIBNR();

    public IPerilMarker peril();
    public ISegmentMarker segment();
    public IReinsuranceContractMarker reinsuranceContract();

    public ClaimRoot withScale(double scaleFactor, IReinsuranceContractMarker reinsuranceContract);


    public ClaimRoot withScale(double scaleFactor);
}
