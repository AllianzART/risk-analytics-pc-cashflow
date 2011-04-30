package org.pillarone.riskanalytics.domain.pc.cf.claim.generator;

import org.pillarone.riskanalytics.core.parameterization.IParameterObjectClassifier;
import org.pillarone.riskanalytics.core.simulation.engine.PeriodScope;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimRoot;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimType;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.FrequencyBase;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoUtils;
import org.pillarone.riskanalytics.domain.utils.math.distribution.DistributionModified;
import org.pillarone.riskanalytics.domain.utils.math.distribution.RandomDistribution;
import org.pillarone.riskanalytics.domain.utils.math.generator.IRandomNumberGenerator;
import org.pillarone.riskanalytics.domain.utils.math.generator.RandomNumberGeneratorFactory;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class FrequencyAverageAttritionalClaimsGeneratorStrategy extends AttritionalClaimsGeneratorStrategy  {

    private FrequencyBase frequencyBase;
    private RandomDistribution frequencyDistribution;
    private DistributionModified frequencyModification;

    public IParameterObjectClassifier getType() {
        return ClaimsGeneratorType.FREQUENCY_AVERAGE_ATTRITIONAL;
    }

    public Map getParameters() {
        Map<String, Object> parameters = super.getParameters();
        parameters.put(FREQUENCY_BASE, frequencyBase);
        parameters.put(FREQUENCY_DISTRIBUTION, frequencyDistribution);
        parameters.put(FREQUENCY_MODIFICATION, frequencyModification);
        return parameters;
    }


    public List<ClaimRoot> generateClaims(List<UnderwritingInfoPacket> uwInfos, List uwInfosFilterCriteria, PeriodScope periodScope) {
        double severityScalingFactor = UnderwritingInfoUtils.scalingFactor(uwInfos, claimsSizeBase, uwInfosFilterCriteria);
        double frequencyFactor = UnderwritingInfoUtils.scalingFactor(uwInfos, frequencyBase, uwInfosFilterCriteria);
        IRandomNumberGenerator frequencyGenerator = RandomNumberGeneratorFactory.getGenerator(frequencyDistribution, frequencyModification);
        IRandomNumberGenerator claimsSizeGenerator = RandomNumberGeneratorFactory.getGenerator(claimsSizeDistribution, claimsSizeModification);
        int numberOfClaims = (int) (frequencyGenerator.nextValue().intValue() * frequencyFactor);
        double claimValue = 0;
        for (int i = 0; i < numberOfClaims; i++) {
            claimValue += claimsSizeGenerator.nextValue().doubleValue();
        }
        List<Double> claimValues = new ArrayList<Double>();
        claimValues.add(claimValue * severityScalingFactor);
        return getClaims(claimValues, ClaimType.ATTRITIONAL, periodScope);
    }

}