package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.paidImpl;

import org.joda.time.DateTime;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.core.simulation.engine.PeriodScope;
import org.pillarone.riskanalytics.core.simulation.SimulationException;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.LayerParameters;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.AdditionalPremiumPerLayer;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.PeriodLayerParameters;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.ContractCoverBase;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.IPaidCalculation;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.filterUtilities.RIUtilities;

import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.incurredImpl.TermIncurredCalculation;
import org.pillarone.riskanalytics.domain.pc.cf.claim.IClaimRoot;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.IncurredClaimBase;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.filterUtilities.GRIUtilities;

import java.util.*;

/**
 * author simon.parten @ art-allianz . com
 */
public class TermPaidRespectIncurredByClaim implements IPaidCalculation {

    public double layerCededPaid(Collection<ClaimCashflowPacket> layerCashflows, LayerParameters layerParameters) {
        double lossAfterAnnualStructure = lossAfterAnnualStructure(layerCashflows, layerParameters);

        double lossAfterShareAndProRata = lossAfterAnnualStructure * layerParameters.getShare();

        return lossAfterShareAndProRata;
    }

    public double cumulativePaidForPeriodIgnoreTermStructure(Collection<ClaimCashflowPacket> allPaidClaims, PeriodLayerParameters layerParameters, PeriodScope periodScope, ContractCoverBase coverageBase, double termLimit, double termExcess, int period) {
        return 0d;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Map<Integer, Double> cededIncrementalPaidRespectTerm(List<ClaimCashflowPacket> allPaidClaims, PeriodLayerParameters layerParameters,
                                                                PeriodScope periodScope, ContractCoverBase coverageBase, double termLimit, double termExcess, DateTime fromDate, DateTime toDate) {

        List<ClaimCashflowPacket> fromDateFilteredClaims = RIUtilities.cashflowClaimsByOccurenceDate(periodScope.getPeriodCounter().startOfFirstPeriod(), fromDate, allPaidClaims);
        List<ClaimCashflowPacket> toDateFilteredClaims = RIUtilities.cashflowClaimsByOccurenceDate(periodScope.getPeriodCounter().startOfFirstPeriod(), toDate, allPaidClaims);
        Map<Integer, Double> paidByPeriodUpToFilterFromDate = cededCumulativePaidRespectTerm(fromDateFilteredClaims, layerParameters, periodScope, coverageBase, termLimit, termExcess);
        Map<Integer, Double> cumulativePaidToDate = cededCumulativePaidRespectTerm(toDateFilteredClaims, layerParameters, periodScope, coverageBase, termLimit, termExcess);

        Map<Integer, Double> paidByPeriod = new TreeMap<Integer, Double>();

        for (int modelPeriod = 0; modelPeriod < cumulativePaidToDate.size(); modelPeriod++) {
//          It is possible nothing is entered for the map, which may only run to the end of a prior period.
            if (paidByPeriodUpToFilterFromDate.get(modelPeriod) == null) {
                paidByPeriod.put(modelPeriod, cumulativePaidToDate.get(modelPeriod));
            } else {

                double paidPriorSimPeriod = paidByPeriodUpToFilterFromDate.get(modelPeriod);
                double paidToCurrentSimPoint = cumulativePaidToDate.get(modelPeriod);
                double cumPaid = paidToCurrentSimPoint - paidPriorSimPeriod;
                if (cumPaid < -0.000000005) {
                    throw new SimulationException("Insanity detected: incremental paid amount in model period : " + modelPeriod + " is calculated as negative. " + cumPaid +
                            "Contact support");
                }
                paidByPeriod.put(modelPeriod, cumPaid);
            }
        }

        return paidByPeriod;
    }

    public Map<Integer, Double> cededCumulativePaidRespectTerm(List<ClaimCashflowPacket> allPaidClaims, PeriodLayerParameters layerParameters, PeriodScope periodScope, ContractCoverBase coverageBase, double termLimit, double termExcess) {

        TermIncurredCalculation incCalc = new TermIncurredCalculation();
        List<IClaimRoot> allIncurredClaims = new ArrayList<IClaimRoot>(RIUtilities.incurredClaims(allPaidClaims, IncurredClaimBase.BASE));
        Map<Integer, Double> cededIncurredByPeriod = incCalc.cededIncurredsByPeriods(allIncurredClaims, periodScope, termExcess, termLimit, layerParameters, coverageBase);

        Map<Integer, Double> allPaidIncludingThisPeriod = cededPaidByModelPeriod(periodScope, allPaidClaims, layerParameters, coverageBase, periodScope.getCurrentPeriod(), termExcess, termLimit);
        Map<Integer, Double> allPaidToDateRespectIncurredTerm = imposeIncurredLimits(cededIncurredByPeriod, allPaidIncludingThisPeriod);

        return allPaidToDateRespectIncurredTerm;
    }

    /**
     * This method calculates the cumulative ceded amounts (by model period). It inspects the total amount ceded across the entire
     * simulation to determine if the term excess is breached, and then begins allocating paid amounts to periods.
     *
     * @param periodScope
     * @param allPaidClaims
     * @param layerParameters
     * @param base
     * @param periodTo
     * @param termExcess
     * @param termLimit
     * @return
     */
    public Map<Integer, Double> cededPaidByModelPeriod(PeriodScope periodScope, List<ClaimCashflowPacket> allPaidClaims, PeriodLayerParameters layerParameters, ContractCoverBase base, int periodTo, double termExcess, double termLimit) {
        Map<Integer, Double> period_paid = new HashMap<Integer, Double>();

        /* As it stands, the spec takes no notice of the term excess when calculating payments. For the moment, ignore it here too. Set to falase to enable functionality.  */
        boolean termExcessExceeded = true;
        double cumulativePaidInSimulation = 0d;
        for (int period = 0; period <= periodTo; period++) {
            if (termExcessExceeded) {
                List<ClaimCashflowPacket> cashflowsPaidAgainsThisModelPeriod = GRIUtilities.cashflowsCoveredInModelPeriod(allPaidClaims, periodScope, base, period);
                List<ClaimCashflowPacket> latestCashflowsInPeriod = RIUtilities.latestCashflowByIncurredClaim(cashflowsPaidAgainsThisModelPeriod);
                List<LayerParameters> layers = layerParameters.getLayers(period);
                double paidLossToModelPeriod = paidLossAllLayers(latestCashflowsInPeriod, layers);
                period_paid.put(period, paidLossToModelPeriod);
                continue;
            }

            double incrementalPaidSimPeriod = cededPaidUpToSimulationPeriod(new ArrayList<ClaimCashflowPacket>(allPaidClaims), layerParameters, periodScope, termExcess, termLimit, base, period);
            cumulativePaidInSimulation += incrementalPaidSimPeriod;

            if (cumulativePaidInSimulation >= termExcess) {
                termExcessExceeded = true;
                period_paid.put(period, incrementalPaidSimPeriod);

            } else {
                period_paid.put(period, 0d);
            }
        }
        return period_paid;
    }

    /**
     * This method calculated the incremental paid amount (respecting the term limit) across the entire simulation.
     *
     * @param allCashflows
     * @param layerParameters
     * @param periodScope
     * @param termExcess
     * @param termLimit
     * @param coverageBase
     * @param periodTo
     * @return
     */
    public double cededPaidUpToSimulationPeriod(List<ClaimCashflowPacket> allCashflows, PeriodLayerParameters layerParameters, PeriodScope periodScope, double termExcess, double termLimit, ContractCoverBase coverageBase, int periodTo) {

        double termPaidPriorPeriod = 0;
        for (int period = 0; period < periodTo; period++) {
            List<ClaimCashflowPacket> cashflowsPaidAgainsThisModelPeriod = GRIUtilities.cashflowsCoveredInModelPeriod(allCashflows, periodScope, coverageBase, period);
            List<ClaimCashflowPacket> latestCashflowsInPeriod = RIUtilities.latestCashflowByIncurredClaim(cashflowsPaidAgainsThisModelPeriod);
            List<LayerParameters> layers = layerParameters.getLayers(period);
            termPaidPriorPeriod += paidLossAllLayers(latestCashflowsInPeriod, layers);
        }

        List<ClaimCashflowPacket> cashflowsPaidAgainsThisModelPeriod = GRIUtilities.cashflowsCoveredInModelPeriod(allCashflows, periodScope, coverageBase, periodTo);
        List<ClaimCashflowPacket> latestCashflowsInPeriod = RIUtilities.latestCashflowByIncurredClaim(cashflowsPaidAgainsThisModelPeriod);
        List<LayerParameters> layers = layerParameters.getLayers(periodTo);
        double paidLossThisPeriod = paidLossAllLayers(latestCashflowsInPeriod, layers);

        double lossAfterTermStructure = Math.min(Math.max(termPaidPriorPeriod + paidLossThisPeriod - termExcess, 0), termLimit);
        double lossAfterTermStructurePriorPeriods = Math.min(Math.max(termPaidPriorPeriod - termExcess, 0), termLimit);
        return lossAfterTermStructure - lossAfterTermStructurePriorPeriods;

    }

    /**
     * This method accepts all cashflows in all layers and calculates the amount ceded by contract.
     *
     * @param allLayerCashflows
     * @param layerParameters
     * @return
     */
    public double paidLossAllLayers(Collection<ClaimCashflowPacket> allLayerCashflows, Collection<LayerParameters> layerParameters) {
        double paidLoss = 0;
        for (LayerParameters layerParameter : layerParameters) {
            paidLoss += layerCededPaid(allLayerCashflows, layerParameter);
        }
        return paidLoss;
    }

    /**
     * This calculates the amount ceded respecting the annual structure.
     *
     * @param layerCashflows
     * @param layerParameters
     * @return
     */
    public double lossAfterAnnualStructure(Collection<ClaimCashflowPacket> layerCashflows, LayerParameters layerParameters) {
        double lossAfterClaimStructure = 0;
        for (ClaimCashflowPacket aClaim : layerCashflows) {
            lossAfterClaimStructure += Math.min(Math.max(-aClaim.getPaidCumulatedIndexed() - layerParameters.getClaimExcess(), 0), layerParameters.getClaimLimit());
        }
        return Math.min(Math.max(lossAfterClaimStructure - layerParameters.getLayerPeriodExcess(), 0), layerParameters.getLayerPeriodLimit());
    }

    public Map<Integer, Double> imposeIncurredLimits(Map<Integer, Double> incurredLimits, Map<Integer, Double> paidAmounts) {
        Map<Integer, Double> paidAmountRespectingIncurred = new TreeMap<Integer, Double>();

        for (Map.Entry<Integer, Double> entry : paidAmounts.entrySet()) {
            double incurredLimitInPeriod = incurredLimits.get(entry.getKey());
            double paidAmountInPeriod = entry.getValue();
            paidAmountRespectingIncurred.put(entry.getKey(), Math.min(paidAmountInPeriod, incurredLimitInPeriod));
        }
        return paidAmountRespectingIncurred;
    }


    public double additionalPremiumByLayer(Collection<ClaimCashflowPacket> cashflowsByLayer, LayerParameters layerParameters, double layerPremium) {
        double additionalPremium = 0;

        for (AdditionalPremiumPerLayer additionalPremiumPerLayer : layerParameters.getAdditionalPremiums()) {
            double tempAdditionalPremium = 0;
            LayerParameters tempLayer = new LayerParameters(layerParameters.getShare(), layerParameters.getClaimExcess(), layerParameters.getClaimLimit());
            tempLayer.addAdditionalPremium(additionalPremiumPerLayer.getPeriodExcess(), additionalPremiumPerLayer.getPeriodLimit(), additionalPremiumPerLayer.getAdditionalPremium(), additionalPremiumPerLayer.getBasis());
            switch (additionalPremiumPerLayer.getBasis()) {
                case PREMIUM:
                    double loss = lossAfterAnnualStructure(cashflowsByLayer, tempLayer);
                    tempAdditionalPremium = (loss * layerPremium * layerParameters.getShare() * additionalPremiumPerLayer.getAdditionalPremium()) / tempLayer.getLayerPeriodLimit();
                    break;
                case LOSS:
                    tempAdditionalPremium = layerCededPaid(cashflowsByLayer, tempLayer) * additionalPremiumPerLayer.getAdditionalPremium();
                    break;
                case NCB:
                    if (lossAfterAnnualStructure(cashflowsByLayer, tempLayer) == 0) {
                        tempAdditionalPremium = layerParameters.getShare() * additionalPremiumPerLayer.getAdditionalPremium() * layerPremium;
                    }
                    break;
                default:
                    throw new SimulationException("Unknown additional premium basis :" + additionalPremiumPerLayer.getBasis());
            }
            additionalPremium += tempAdditionalPremium;
        }

        return additionalPremium;
    }
}