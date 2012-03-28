package org.pillarone.riskanalytics.domain.pc.cf.output;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pillarone.riskanalytics.core.output.ICollectingModeStrategy;
import org.pillarone.riskanalytics.core.output.PathMapping;
import org.pillarone.riskanalytics.core.output.SingleValueResultPOJO;
import org.pillarone.riskanalytics.core.packets.Packet;
import org.pillarone.riskanalytics.core.packets.PacketList;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;

import java.util.*;

/**
 * This collecting mode strategy splits claim and underwriting information up by inception date.
 *
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class AggregateSplitByInceptionDateCollectingModeStrategy extends AbstractSplitCollectingModeStrategy implements ICollectingModeStrategy {

    protected static Log LOG = LogFactory.getLog(AggregateSplitByInceptionDateCollectingModeStrategy.class);

    static final String IDENTIFIER = "SPLIT_BY_INCEPTION_DATE";
    private static final String RESERVE_RISK = "reserveRisk";
    private static final String PREMIUM_RISK = "premiumRisk";
    private static final String PERIOD = "period";

    public List<SingleValueResultPOJO> collect(PacketList packets) {
        initSimulation();
        iteration = packetCollector.getSimulationScope().getIterationScope().getCurrentIteration();
        period = packetCollector.getSimulationScope().getIterationScope().getPeriodScope().getCurrentPeriod();

        if (isCompatibleWith(packets.get(0).getClass())) {
            try {
                List<SingleValueResultPOJO> singleValueResults = createSingleValueResults(aggregate(packets));
                if (packets.get(0) instanceof ClaimCashflowPacket) {
                    singleValueResults.addAll(createPremiumReserveRisk(packets));
                }
                return singleValueResults;
            }
            catch (IllegalAccessException ex) {
//                todo(sku): remove
            }
        } else {
            String notImplemented = ResourceBundle.getBundle(RESOURCE_BUNDLE).getString("AggregateSplitByInceptionDateCollectingModeStrategy.notImplemented");
            throw new NotImplementedException(notImplemented + "\n(" + packetCollector.getPath() + ")");
        }
        return null;
    }

    protected SingleValueResultPOJO createSingleValueResult(String path, String fieldName, Double value) {
        if (value == Double.NaN || value == Double.NEGATIVE_INFINITY || value == Double.POSITIVE_INFINITY) {
            if (LOG.isErrorEnabled()) {
                StringBuilder message = new StringBuilder();
                message.append(value).append(" collected at ").append(packetCollector.getPath());
                message.append(" (period ").append(period).append(") in iteration ");
                message.append(iteration).append(" - ignoring.");
                LOG.error(message);
            }
            return null;
        }
        else {
            SingleValueResultPOJO result = new SingleValueResultPOJO();
            result.setSimulationRun(simulationRun);
            result.setIteration(iteration);
            result.setPeriod(period);
            result.setPath(mappingCache.lookupPath(path));
            result.setCollector(mappingCache.lookupCollector(getIdentifier()));
            result.setField(mappingCache.lookupField(fieldName));
            result.setValueIndex(0);
            result.setValue(value);
            return result;
        }
    }

    /**
     * @param packets
     * @return a map with paths as key
     */
    private Map<PathMapping, Packet> aggregate(List<Packet> packets) {
        // has to be a LinkedHashMap to make sure the shortest path is the first in the map and gets AGGREGATED as collecting mode
        Map<PathMapping, Packet> resultMap = new LinkedHashMap<PathMapping, Packet>(packets.size());
        if (packets == null || packets.size() == 0) {
            return resultMap;
        }

        for (Packet packet : packets) {
            String originPath = packetCollector.getSimulationScope().getStructureInformation().getPath(packet);
            PathMapping path = mappingCache.lookupPath(originPath);
            addToMap(packet, path, resultMap);

            PathMapping periodPath = getPathMapping(packet);
            addToMap(packet, periodPath, resultMap);
        }
        return resultMap;
    }
    
    private String inceptionPeriod(Packet packet) {
        if (packet instanceof ClaimCashflowPacket) {
            return String.valueOf(((ClaimCashflowPacket) packet).getOccurrenceDate().getYear());
        }
        else if (packet instanceof UnderwritingInfoPacket) {
            return String.valueOf(((UnderwritingInfoPacket) packet).getExposure().getInceptionDate().getYear());
        }
        else {
            throw new IllegalArgumentException("Packet type " + packet.getClass() + " is not supported.");
        }
    }

    private void addToMap(Packet packet, PathMapping path, Map<PathMapping, Packet> resultMap) {
        if (packet instanceof ClaimCashflowPacket) {
            addToMap((ClaimCashflowPacket) packet, path, resultMap);
        }
        else if (packet instanceof UnderwritingInfoPacket) {
            addToMap((UnderwritingInfoPacket) packet, path, resultMap);
        }
        else {
            throw new IllegalArgumentException("Packet type " + packet.getClass() + " is not supported.");
        }
    }
    
    private List<SingleValueResultPOJO> createPremiumReserveRisk(List<ClaimCashflowPacket> claims) {
        List<SingleValueResultPOJO> results = new ArrayList<SingleValueResultPOJO>();
        double totalReserveRisk = 0;
        for (ClaimCashflowPacket claim : claims) {
            if (claim.reserveRisk() != 0) {
                // belongs to reserve risk
                totalReserveRisk += claim.reserveRisk();
                String periodLabel = inceptionPeriod(claim);
                String pathExtension = PERIOD + PATH_SEPARATOR + periodLabel;
                String pathExtended = getExtendedPath(claim, pathExtension);
                results.add(createSingleValueResult(pathExtended, RESERVE_RISK, claim.reserveRisk()));
            }
            else if (claim.premiumRisk() != 0) {
                // belongs to premium risk
                results.add(createSingleValueResult(packetCollector.getPath(), PREMIUM_RISK, claim.premiumRisk()));
            }
        }
        if (totalReserveRisk != 0) {
            results.add(createSingleValueResult(packetCollector.getPath(), RESERVE_RISK, totalReserveRisk));
        }
        return results;
    }

    private PathMapping getPathMapping(Packet packet) {
        String periodLabel = inceptionPeriod(packet);
        String pathExtension = "period" + PATH_SEPARATOR + periodLabel;
        String pathExtended = getExtendedPath(packet, pathExtension);
        return mappingCache.lookupPath(pathExtended);
    }
    
    public String getIdentifier() {
        return IDENTIFIER;
    }

}
