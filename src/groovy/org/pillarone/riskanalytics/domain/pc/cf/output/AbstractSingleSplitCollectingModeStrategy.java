package org.pillarone.riskanalytics.domain.pc.cf.output;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pillarone.riskanalytics.core.components.IComponentMarker;
import org.pillarone.riskanalytics.core.output.ICollectingModeStrategy;
import org.pillarone.riskanalytics.core.output.PacketCollector;
import org.pillarone.riskanalytics.core.output.PathMapping;
import org.pillarone.riskanalytics.core.output.SingleValueResultPOJO;
import org.pillarone.riskanalytics.core.packets.Packet;
import org.pillarone.riskanalytics.core.packets.PacketList;
import org.pillarone.riskanalytics.core.simulation.SimulationException;
import org.pillarone.riskanalytics.core.simulation.engine.MappingCache;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.reserves.cashflow.ClaimDevelopmentPacket;
import org.pillarone.riskanalytics.domain.utils.marker.ComposedMarkerKey;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: palbini
 * Date: 26/05/15
 * Time: 14:36
 * To change this template use File | Settings | File Templates.
 */
abstract public class AbstractSingleSplitCollectingModeStrategy implements ICollectingModeStrategy {

        protected static Log LOG = LogFactory.getLog(AbstractSplitCollectingModeStrategy.class);

        protected static final String RESOURCE_BUNDLE = "org.pillarone.riskanalytics.domain.pc.cf.output.CollectingModeStrategyResources";
        protected static final String PATH_SEPARATOR = ":";
        protected String displayName;

        protected PacketCollector packetCollector;

        // the following variables are used for caching purposes
        protected String componentPath;
        protected Map<IComponentMarker, PathMapping> markerPaths;
        protected Map<ComposedMarkerKey, PathMapping> markerComposedPaths;
        protected MappingCache mappingCache;
        protected int iteration = 0;
        protected int period = 0;

        protected void initSimulation() {
            if (componentPath != null) return;
            componentPath = getComponentPath();
            markerPaths = new HashMap<IComponentMarker, PathMapping>();
            markerComposedPaths = new HashMap<ComposedMarkerKey, PathMapping>();
            mappingCache = packetCollector.getSimulationScope().getMappingCache();
        }

        abstract public List<SingleValueResultPOJO> collect(PacketList packets, boolean crashSimulationOnError) throws IllegalAccessException;

        /**
         * Create a SingleValueResult object for each packetValue.
         * Information about current simulation is gathered from the scopes.
         * The key of the value map is the path.
         *
         * @param packets
         * @return
         * @throws IllegalAccessException
         */
        protected List<SingleValueResultPOJO> createSingleValueResults(Map<PathMapping, List<Packet>> packets, boolean crashSimulationOnError) throws IllegalAccessException {
            List<SingleValueResultPOJO> singleValueResults = new ArrayList<SingleValueResultPOJO>(packets.size());
            boolean firstPath = true;
            for (Map.Entry<PathMapping, List<Packet>> packetEntry : packets.entrySet()) {
                PathMapping path = packetEntry.getKey();
                for (Packet packet: packetEntry.getValue()) {
                    for (Map.Entry<String, Number> field : filter(packet.getValuesToSave()).entrySet()) {
                        String fieldName = field.getKey();
                        Double value = (Double) field.getValue();
                        if (checkInvalidValues(fieldName, value, period, iteration, crashSimulationOnError)) continue;
                        SingleValueResultPOJO result = new SingleValueResultPOJO();
                        result.setIteration(iteration);
                        result.setPeriod(period);
                        result.setDate(packet.getDate());
                        result.setPath(path);
                        if (firstPath) {    // todo(sku): might be completely removed
                            result.setCollector(mappingCache.lookupCollector("AGGREGATED"));
                        }
                        else {
                            result.setCollector(mappingCache.lookupCollector(getIdentifier()));
                        }
                        result.setField(mappingCache.lookupField(fieldName));
                        result.setValueIndex(0);
                        result.setValue(value);
                        singleValueResults.add(result);
                    }
                }
                firstPath = false;
            }
            return singleValueResults;
        }

        public boolean checkInvalidValues(String name, Double value, int period, int iteration, boolean crashSimulationOnError) {
            if (value.isInfinite() || value.isNaN()) {

                StringBuilder message = new StringBuilder();
                message.append(value).append(" collected at ").append(packetCollector.getPath()).append(":").append(name);
                message.append(" (period ").append(period).append(") in iteration ");
                message.append(iteration).append(" - ignoring.");
                if (LOG.isErrorEnabled()) {
                    LOG.info(message);
                }
                if(crashSimulationOnError) {
                    throw new SimulationException(message.toString());
                }
                return true;
            }
            return false;
        }

        /**
         * @param valuesToSave
         * @return valuesToSave itself if filter() returns an empty list or all fields and number with matching field names
         */
        public Map<String, Number> filter(Map<String, Number> valuesToSave) {
            if (filter().isEmpty()) return valuesToSave;
            Map<String, Number> filteredValuesToSave = new HashMap<String, Number>(filter().size());
            for (String filterItem : filter()) {
                filteredValuesToSave.put(filterItem, valuesToSave.get(filterItem));
            }
            return filteredValuesToSave;
        }

        abstract public List<String> filter();

        protected PathMapping getPathMapping(Packet packet, IComponentMarker marker, String pathExtensionPrefix) {
            PathMapping path = markerPaths.get(marker);
            if (marker != null && path == null) {
                String pathExtension = pathExtensionPrefix + PATH_SEPARATOR + marker.getName();
                String pathExtended = getExtendedPath(packet, pathExtension);
                path = mappingCache.lookupPath(pathExtended);
                markerPaths.put(marker, path);
            }
            return path;
        }

        protected PathMapping getPathMapping(Packet packet,
                                             IComponentMarker firstMarker, String firstPathExtensionPrefix,
                                             IComponentMarker secondMarker, String secondPathExtensionPrefix) {
            ComposedMarkerKey pair = new ComposedMarkerKey(firstMarker, secondMarker);
            PathMapping path = markerComposedPaths.get(pair);
            if (firstMarker != null && path == null) {
                String pathExtension = firstPathExtensionPrefix + PATH_SEPARATOR + firstMarker.getName()
                        + PATH_SEPARATOR + secondPathExtensionPrefix + PATH_SEPARATOR + secondMarker.getName();
                String pathExtended = getExtendedPath(packet, pathExtension);
                path = mappingCache.lookupPath(pathExtended);
                markerComposedPaths.put(pair, path);
            }
            return path;
        }

        protected PathMapping getPathMapping(Packet packet,
                                             IComponentMarker firstMarker, String firstPathExtensionPrefix,
                                             IComponentMarker secondMarker, String secondPathExtensionPrefix,
                                             IComponentMarker thirdMarker, String thirdPathExtensionPrefix) {
            ComposedMarkerKey pair = new ComposedMarkerKey(firstMarker, secondMarker, thirdMarker);
            PathMapping path = markerComposedPaths.get(pair);
            if (firstMarker != null && path == null) {
                String pathExtension = firstPathExtensionPrefix + PATH_SEPARATOR + firstMarker.getName()
                        + PATH_SEPARATOR + secondPathExtensionPrefix + PATH_SEPARATOR + secondMarker.getName()
                        + PATH_SEPARATOR + thirdPathExtensionPrefix + PATH_SEPARATOR + thirdMarker.getName();
                String pathExtended = getExtendedPath(packet, pathExtension);
                path = mappingCache.lookupPath(pathExtended);
                markerComposedPaths.put(pair, path);
            }
            return path;
        }

        protected String getComponentPath() {
            int separatorPositionBeforeChannel = packetCollector.getPath().lastIndexOf(":");
            return packetCollector.getPath().substring(0, separatorPositionBeforeChannel);
        }

        protected void addToMap(ClaimCashflowPacket claim, PathMapping path, Map<PathMapping, List<Packet>> resultMap) {
            if (path == null) return;
            List<Packet> packetList;
            if (resultMap.containsKey(path)) {
                packetList =  resultMap.get(path);
            } else {
                packetList = new PacketList<Packet>();
            }
            packetList.add(claim);
            resultMap.put(path, packetList);
        }


        protected String getExtendedPath(Packet packet, String pathExtension) {
            if (pathExtension == null) return null;
            StringBuilder composedPath = new StringBuilder(componentPath);
            composedPath.append(PATH_SEPARATOR);
            composedPath.append(pathExtension);
            composedPath.append(PATH_SEPARATOR);
            composedPath.append(packet.senderChannelName);
            return composedPath.toString();
        }
    //
        public String getDisplayName(Locale locale) {
            if (displayName == null) {
                displayName = ResourceBundle.getBundle(RESOURCE_BUNDLE, locale).getString("ICollectingModeStrategy." + getIdentifier());
            }
            return displayName;
        }

        abstract public String getIdentifier();

        public PacketCollector getPacketCollector() {
            return packetCollector;
        }

        public void setPacketCollector(PacketCollector packetCollector) {
            this.packetCollector = packetCollector;
        }

        public boolean isCompatibleWith(Class packetClass) {
            return ClaimCashflowPacket.class.isAssignableFrom(packetClass) || UnderwritingInfoPacket.class.isAssignableFrom(packetClass);
        }

        public Object[] getArguments() {
            return new Object[0];
        }
    }


