package org.pillarone.riskanalytics.domain.pc.cf.output;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pillarone.riskanalytics.core.output.SingleValueResultPOJO;
import org.pillarone.riskanalytics.core.packets.PacketList;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.utils.datetime.DateTimeUtilities;

import java.util.*;

/**
 * This collecting mode strategy splits claim and underwriting information up by inception date.
 *
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class SingleSplitByInceptionDateCollectionModeStrategy extends AggregateSplitByInceptionDateCollectionModeStrategy {

    protected static Log LOG = LogFactory.getLog(SingleSplitByInceptionDateCollectionModeStrategy.class);

    static final String IDENTIFIER = "SPLIT_BY_INCEPTION_DATE_WITH_SINGLE_AVAILABLE";

    public List<SingleValueResultPOJO> collect(PacketList packets, boolean crashSimOnError) throws IllegalAccessException {
        initSimulation();
        iteration = packetCollector.getSimulationScope().getIterationScope().getCurrentIteration();
        period = packetCollector.getSimulationScope().getIterationScope().getPeriodScope().getCurrentPeriod();

        List<ClaimCashflowPacket> cashflowPackets= (List<ClaimCashflowPacket>) packets;

        if (isCompatibleWith(packets.get(0).getClass())) {
            List<SingleValueResultPOJO> singleValueResults = createSingleValueResults(aggregate(packets), crashSimOnError);
            if (packets.get(0) instanceof ClaimCashflowPacket) {

                singleValueResults.addAll(createDateSplit(packets, crashSimOnError));
            }
            return singleValueResults;
        } else {
            String notImplemented = ResourceBundle.getBundle(RESOURCE_BUNDLE).getString("AggregateSplitByInceptionDateCollectingModeStrategy.notImplemented");
            throw new NotImplementedException(notImplemented + "\n(" + packetCollector.getPath() + ")");
        }
    }

    protected List<SingleValueResultPOJO> createDateSplit(List<ClaimCashflowPacket> claims, boolean crashSimOnError) {
        List<SingleValueResultPOJO> results = new ArrayList<SingleValueResultPOJO>();
        double totalReserveRisk = 0;
        double premiumRisk = 0;
        Map<String, Double> reserveRiskByPeriodPath = new HashMap<String, Double>();
        for (ClaimCashflowPacket claim : claims) {
            if (claim.getReserveRisk() != 0) {
                // belongs to reserve risk
                totalReserveRisk += claim.getReserveRisk();
                String datePath = DateTimeUtilities.formatDate.print(claim.getDate());
                String pathExtension = datePath.replace(" ", "_") + PATH_SEPARATOR ;

                String pathExtended = getExtendedPath(claim, pathExtension);
                Double reserveRisk = reserveRiskByPeriodPath.get(pathExtended);
                reserveRisk = reserveRisk == null ? claim.getReserveRisk() : reserveRisk + claim.getReserveRisk();
                reserveRiskByPeriodPath.put(pathExtended, reserveRisk);
            }
            else if (claim.getPremiumRisk() != 0) {
                // belongs to premium risk
                premiumRisk += claim.getPremiumRisk();
            }
        }
        for (Map.Entry<String, Double> reserveRisk : reserveRiskByPeriodPath.entrySet()) {
            results.add(createSingleValueResult(reserveRisk.getKey(), ClaimCashflowPacket.RESERVE_RISK_BASE, reserveRisk.getValue(), crashSimOnError));
        }
        if (premiumRisk != 0) {
            results.add(createSingleValueResult(packetCollector.getPath(), ClaimCashflowPacket.PREMIUM_RISK_BASE, premiumRisk, crashSimOnError) );
        }
        if (totalReserveRisk != 0) {
            results.add(createSingleValueResult(packetCollector.getPath(), ClaimCashflowPacket.RESERVE_RISK_BASE, totalReserveRisk, crashSimOnError));
        }
        if (premiumRisk + totalReserveRisk != 0) {
            results.add(createSingleValueResult(packetCollector.getPath(), ClaimCashflowPacket.PREMIUM_AND_RESERVE_RISK_BASE, premiumRisk + totalReserveRisk, crashSimOnError));
        }
        return results;
    }



    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public boolean isCompatibleWith(Class packetClass) {
        return ClaimCashflowPacket.class.isAssignableFrom(packetClass) ;
    }
}
