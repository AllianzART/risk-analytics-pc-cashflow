package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless;

import org.pillarone.riskanalytics.core.simulation.IPeriodCounter;
import org.pillarone.riskanalytics.core.simulation.engine.PeriodScope;
import org.pillarone.riskanalytics.domain.pc.cf.claim.IClaimRoot;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.additionalPremium.AdditionalPremium;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.additionalPremium.LayerAndAP;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.additionalPremium.LossAndAP;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.caching.IAllContractClaimCache;

import java.util.Collection;

public interface IIncurredCalculation {

    double layerCededIncurred(Collection<IClaimRoot> incurredClaims, LayerParameters layerParameters);

    Collection<AdditionalPremium> additionalPremiumByLayer(Collection<IClaimRoot> incurredClaims, LayerParameters layerParameters, double layerPremium);

    LossAndAP cededIncurredRespectTerm(IAllContractClaimCache claimStore, ScaledPeriodLayerParameters scaledLayerParameters, PeriodScope periodScope, double termExcess, double termLimit, IPeriodCounter counter, ContractCoverBase coverageBase, double contractPeriodPremium);

    Collection<LayerAndAP> additionalPremiumAllLayers(Collection<IClaimRoot> incurredClaims, Collection<LayerParameters> layerParameters, double layerPremium);

}
