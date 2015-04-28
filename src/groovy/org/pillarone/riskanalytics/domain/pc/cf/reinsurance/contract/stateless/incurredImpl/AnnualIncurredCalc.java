package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.incurredImpl;

import org.pillarone.riskanalytics.domain.pc.cf.claim.IClaimRoot;
import org.pillarone.riskanalytics.core.simulation.SimulationException;
import org.pillarone.riskanalytics.core.simulation.engine.PeriodScope;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.LayerParameters;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.AdditionalPremiumPerLayer;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.ContractCoverBase;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.IIncurredCalculation;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.ScaledPeriodLayerParameters;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.additionalPremium.*;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.caching.IAllContractClaimCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * author simon.parten @ art-allianz . com
 */
public class AnnualIncurredCalc implements IIncurredCalculation {

    public double layerCededIncurred(Collection<IClaimRoot> incurredClaims, LayerParameters layerParameters) {

        double lossAfterAnnualStructure = lossAfterAnnualStructure(incurredClaims, layerParameters);

        double lossAfterShareAndProRata = lossAfterAnnualStructure * layerParameters.getShare();

        return lossAfterShareAndProRata;

    }

    private double lossAfterAnnualStructure(Collection<IClaimRoot> incurredClaims, LayerParameters layerParameters) {
        double lossAfterClaimStructure = 0;
        for (IClaimRoot incurredClaim : incurredClaims) {
            lossAfterClaimStructure += Math.min(Math.max(incurredClaim.getUltimate() - layerParameters.getClaimExcess(), 0), layerParameters.getClaimLimit());
        }
        return Math.min(Math.max(lossAfterClaimStructure - layerParameters.getLayerPeriodExcess(), 0), layerParameters.getLayerPeriodLimit());
    }

    public Collection<AdditionalPremium> additionalPremiumByLayer(Collection<IClaimRoot> incurredClaims, LayerParameters layerParameters, double layerPremium) {
        Collection<AdditionalPremium> additionalPremiums = new ArrayList<AdditionalPremium>();

        for (AdditionalPremiumPerLayer additionalPremiumPerLayer : layerParameters.getAdditionalPremiums()) {
            LayerParameters tempLayer = new LayerParameters(layerParameters.getShare(), layerParameters.getClaimExcess(), layerParameters.getClaimLimit());
            tempLayer.addAdditionalPremium(additionalPremiumPerLayer.getPeriodExcess(), additionalPremiumPerLayer.getPeriodLimit(), additionalPremiumPerLayer.getAdditionalPremium(), additionalPremiumPerLayer.getBasis().uiAPBasis());
            switch (additionalPremiumPerLayer.getBasis()) {
                case PREMIUM:
                    double loss = lossAfterAnnualStructure(incurredClaims, tempLayer);
                    double premAP = (loss * layerPremium * layerParameters.getShare() * additionalPremiumPerLayer.getAdditionalPremium()) / tempLayer.getClaimLimit();
                    if (premAP != 0) {
                        AdditionalPremium lossAPo = new AdditionalPremium(premAP, CalcAPBasis.PREMIUM);
                        additionalPremiums.add(lossAPo);
                    }
                    break;
                case LOSS:
                    double lossAP = layerCededIncurred(incurredClaims, tempLayer) * additionalPremiumPerLayer.getAdditionalPremium();
                    if (lossAP != 0) {
                        AdditionalPremium additionalPremium = new AdditionalPremium(lossAP, CalcAPBasis.LOSS);
                        additionalPremiums.add(additionalPremium);
                    }
                    break;
                case NCB:
                    double ncbAP = 0d;
                    if (layerCededIncurred(incurredClaims, tempLayer) == 0) {
                        ncbAP = layerParameters.getShare() * additionalPremiumPerLayer.getAdditionalPremium() * layerPremium;
                    }
                    if (ncbAP != 0) {
                        additionalPremiums.add(new AdditionalPremium(ncbAP, CalcAPBasis.NCB));
                    }
                    break;
                case NONE:
                    break;
                default:
                    throw new SimulationException("Unknown additional premium basis :" + additionalPremiumPerLayer.getBasis());
            }
        }

        return additionalPremiums;
    }

    public Collection<LayerAndAP> additionalPremiumAllLayers(Collection<IClaimRoot> incurredClaims, Collection<LayerParameters> layerParameters, double layerPremium) {
        Collection<LayerAndAP> additionalPremiumAllLayers = new ArrayList<LayerAndAP>();
        for (LayerParameters layerParameter : layerParameters) {
            LayerAndAP layerAndAP = new LayerAndAP(layerParameter, additionalPremiumByLayer(incurredClaims, layerParameter, layerPremium));
            additionalPremiumAllLayers.add(layerAndAP);
        }
        return additionalPremiumAllLayers;
    }

    public LossAfterTermStructure cededIncurredRespectTerm(IAllContractClaimCache claimStore, ScaledPeriodLayerParameters scaledLayerParameters, PeriodScope periodScope, double termExcess, double termLimit, ContractCoverBase coverageBase, Map<Integer, Double> premiumPerPeriod) {
        throw new SimulationException("Annual calculation was asked for a term calculation. It has no knowledge of this");
    }
}

