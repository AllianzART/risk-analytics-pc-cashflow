package org.pillarone.riskanalytics.domain.pc.cf.claim.generator;

import org.pillarone.riskanalytics.core.parameterization.IParameterObject;
import org.pillarone.riskanalytics.core.simulation.engine.PeriodScope;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimRoot;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimType;
import org.pillarone.riskanalytics.domain.pc.cf.dependency.EventDependenceStream;
import org.pillarone.riskanalytics.domain.pc.cf.dependency.SystematicFrequencyPacket;
import org.pillarone.riskanalytics.domain.pc.cf.event.EventPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.indexing.FactorsPacket;
import org.pillarone.riskanalytics.domain.utils.marker.IPerilMarker;

import java.util.List;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public interface IClaimsGeneratorStrategy extends IParameterObject {

    /**
     *
     * @param baseClaims this list is needed in order to calculate the number of required claims after calculateClaims has been executed
     *                   i.e. if external severity information is provided and only the missing claim number needs to be generated.
     * @param uwInfos
     * @param uwInfosFilterCriteria
     * @param factorsPackets is used only for frequency based strategies in order to apply indices on frequency
     * @param periodScope
     * @param systematicFrequencies
     * @param filterCriteria
     * @return
     */
    List<ClaimRoot> generateClaims(List<ClaimRoot> baseClaims, List<UnderwritingInfoPacket> uwInfos, List uwInfosFilterCriteria,
                                   List<FactorsPacket> factorsPackets, PeriodScope periodScope,
                                   List<SystematicFrequencyPacket> systematicFrequencies,
                                   IPerilMarker filterCriteria);

    List<ClaimRoot> calculateClaims(List<UnderwritingInfoPacket> uwInfos, List uwInfosFilterCriteria,
                                    List<EventDependenceStream> eventStreams,
                                    IPerilMarker filterCriteria, PeriodScope periodScope);

    List<ClaimRoot> calculateClaims(double scaleFactor, PeriodScope periodScope, List<Double> severities, List<EventPacket> events);

    List<ClaimRoot> generateClaims(double scaleFactor, int claimNumber, PeriodScope periodScope);

    ClaimType claimType();
}