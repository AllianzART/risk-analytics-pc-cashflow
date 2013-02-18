package org.pillarone.riskanalytics.domain.pc.cf.output;

import com.google.common.collect.Maps;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.pillarone.riskanalytics.core.components.Component;
import org.pillarone.riskanalytics.core.components.DynamicComposedComponent;
import org.pillarone.riskanalytics.core.output.DrillDownMode;
import org.pillarone.riskanalytics.core.output.PathMapping;
import org.pillarone.riskanalytics.core.output.SingleValueResultPOJO;
import org.pillarone.riskanalytics.core.packets.Packet;
import org.pillarone.riskanalytics.core.packets.PacketList;
import org.pillarone.riskanalytics.core.simulation.IPeriodCounter;
import org.pillarone.riskanalytics.core.util.PeriodLabelsUtil;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.ContractFinancialsPacket;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.additionalPremium.AdditionalPremium;
import org.pillarone.riskanalytics.domain.pc.cf.segment.FinancialsPacket;
import org.pillarone.riskanalytics.domain.utils.marker.ILegalEntityMarker;
import org.pillarone.riskanalytics.domain.utils.marker.IReinsuranceContractMarker;
import org.pillarone.riskanalytics.domain.utils.marker.ISegmentMarker;
import org.pillarone.riskanalytics.domain.utils.marker.IStructureMarker;

import java.text.MessageFormat;
import java.util.*;

/**
 * Generic way of collection information.
 *
 * Note that packets must extend from either single value packet or multi value packet in order to be collectable.
 *
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class SplitAndFilterCollectionModeStrategy extends AbstractSplitCollectingModeStrategy {

    private static final String PERILS = "claimsGenerators";
    private static final String CONTRACTS = "reinsuranceContracts";
    private static final String SEGMENTS = "segments";

    private final Map<Component, Class> componentsExtensibleBy = new HashMap<Component, Class>();

    private final List<DrillDownMode> drillDownModes;
    private final List<String> fieldFilter;
    private boolean displayUnderwritingYearOnly = true;
    private static final DateTimeFormatter formatter = DateTimeFormat.forPattern(PeriodLabelsUtil.PARAMETER_DISPLAY_FORMAT);
    private final List<Class<Packet>> compatibleClasses;
    private final String identifier_prefix;


    public SplitAndFilterCollectionModeStrategy(List<DrillDownMode> drillDownModes, List<String> fieldFilter, List<Class<Packet>> compatibleClasses, String identifier_prefix) {
        this.drillDownModes = drillDownModes;
        this.fieldFilter = fieldFilter;
        this.compatibleClasses = compatibleClasses;
        this.identifier_prefix = identifier_prefix;
    }

    public SplitAndFilterCollectionModeStrategy(List<DrillDownMode> drillDownModes, List<String> fieldFilter, List<Class<Packet>> compatibleClasses) {
        this(drillDownModes, fieldFilter, compatibleClasses, null);
    }

    public SplitAndFilterCollectionModeStrategy(List<DrillDownMode> drillDownModes, List<String> fieldFilter) {
        this(drillDownModes, fieldFilter, new ArrayList<Class<Packet>>());
    }

    // required for serialization by gridgain
    public SplitAndFilterCollectionModeStrategy() {
        this(new ArrayList<DrillDownMode>(), new ArrayList<String>(), new ArrayList<Class<Packet>>());
    }

    @Override
    public List<SingleValueResultPOJO> collect(PacketList packets, boolean crashSimulationOnError) throws IllegalAccessException {
        initSimulation();
        iteration = packetCollector.getSimulationScope().getIterationScope().getCurrentIteration();
        period = packetCollector.getSimulationScope().getIterationScope().getPeriodScope().getCurrentPeriod();

        if (isCompatibleWith(packets.get(0).getClass())) {
            Map<PathMapping, Packet> resultMap = allPathMappingsIncludingSplit(packets);
            return createSingleValueResults(resultMap, crashSimulationOnError);
        } else {
            String incompatibleMessage = ResourceBundle.getBundle(RESOURCE_BUNDLE).getString("SplitAndFilterCollectionModeStrategy.incompatibleCollector");
            throw new NotImplementedException(MessageFormat.format(incompatibleMessage, getIdentifier(), drillDownModes, fieldFilter, compatibleClasses, packets.get(0).getClass().getSimpleName()));
        }
    }

    private Map<PathMapping, Packet> allPathMappingsIncludingSplit(PacketList<Packet> packets) throws IllegalAccessException {
        Map<PathMapping, Packet> resultMap = new LinkedHashMap<PathMapping, Packet>(packets.size());
        for (Packet packet : packets) {
            String originPath = packetCollector.getSimulationScope().getStructureInformation().getPath(packet);
            PathMapping path = mappingCache.lookupPath(originPath);
            addToMap(packet, path, resultMap);
        }

        if (drillDownModes.contains(DrillDownMode.BY_SOURCE)) {
            if (packets.get(0) instanceof ClaimCashflowPacket) {
                resultMap.putAll(splitBySourePathsForClaims(packets));
            } else if (packets.get(0) instanceof UnderwritingInfoPacket) {
                resultMap.putAll(splitBySourePathsForUwInfos(packets));
            }
        }
        if (drillDownModes.contains(DrillDownMode.BY_PERIOD)) {
            resultMap.putAll(splitByInceptionPeriodPaths(packets));
        }
        if (drillDownModes.contains(DrillDownMode.BY_TYPE)) {
            Map<PathMapping, Packet> tempMap = Maps.newHashMap();
            for (Packet aPacket : packets) {
                String aPath = getExtendedPath(aPacket, ((AdditionalPremium) aPacket).typeDrillDownName());
                PathMapping pathMapping = mappingCache.lookupPath(aPath);
                if (resultMap.get(pathMapping) == null) {
                    tempMap.put(pathMapping, aPacket);
                } else {
                    AdditionalPremium aggregateMe = ((AdditionalPremium) tempMap.get(pathMapping));
                    tempMap.put(pathMapping, aggregateMe.plusForAggregateCollection(((AdditionalPremium) aPacket)));
                }
            }
            resultMap.putAll(tempMap);
        }

        return resultMap;
    }

    /**
     * @param claims
     * @return a map with paths as key
     */
    protected Map<PathMapping, Packet> splitBySourePathsForClaims(PacketList<Packet> claims) {
        // has to be a LinkedHashMap to make sure the shortest path is the first in the map and gets AGGREGATED as collecting mode
        Map<PathMapping, Packet> resultMap = new LinkedHashMap<PathMapping, Packet>(claims.size());
        if (claims == null || claims.size() == 0) {
            return resultMap;
        }

        for (Packet c : claims) {
            ClaimCashflowPacket claim = (ClaimCashflowPacket) c;
            PathMapping perilPath = null;
            PathMapping lobPath = null;

            if (!componentsExtensibleBy.containsKey(claim.sender)) {
                Component component = claim.sender;
                if (component instanceof DynamicComposedComponent) {
                    component = ((DynamicComposedComponent) component).createDefaultSubComponent();
                }
                if (component instanceof ISegmentMarker) {
                    componentsExtensibleBy.put(claim.sender, ISegmentMarker.class);
                } else if (component instanceof IReinsuranceContractMarker) {
                    componentsExtensibleBy.put(claim.sender, IReinsuranceContractMarker.class);
                } else if (component instanceof ILegalEntityMarker) {
                    componentsExtensibleBy.put(claim.sender, ILegalEntityMarker.class);
                } else if (component instanceof IStructureMarker) {
                    componentsExtensibleBy.put(claim.sender, IStructureMarker.class);
                }
            }
            Class markerInterface = componentsExtensibleBy.get(claim.sender);
            if (ISegmentMarker.class.equals(markerInterface)
                    || IReinsuranceContractMarker.class.equals(markerInterface)
                    || ILegalEntityMarker.class.equals(markerInterface)) {
                perilPath = getPathMapping(claim, claim.peril(), PERILS);
            }
            if (!(ISegmentMarker.class.equals(markerInterface))) {
                lobPath = getPathMapping(claim, claim.segment(), SEGMENTS);
            }
            PathMapping contractPath = null;
            if (!(IReinsuranceContractMarker.class.equals(markerInterface))) {
                contractPath = getPathMapping(claim, claim.reinsuranceContract(), CONTRACTS);
            }
            if (ISegmentMarker.class.equals(markerInterface)) {
                addToMap(claim, perilPath, resultMap);
                addToMap(claim, contractPath, resultMap);
            }
            if (IReinsuranceContractMarker.class.equals(markerInterface)) {
                addToMap(claim, lobPath, resultMap);
                addToMap(claim, perilPath, resultMap);
                if (lobPath != null && perilPath != null) {
                    PathMapping lobPerilPath = getPathMapping(claim, claim.segment(), SEGMENTS, claim.peril(), PERILS);
                    addToMap(claim, lobPerilPath, resultMap);
                }
            }
            if (ILegalEntityMarker.class.equals(markerInterface) || IStructureMarker.class.equals(markerInterface)) {
                addToMap(claim, perilPath, resultMap);
                addToMap(claim, contractPath, resultMap);
                addToMap(claim, lobPath, resultMap);
            }
        }
        return resultMap;
    }

    protected Map<PathMapping, Packet> splitBySourePathsForUwInfos(PacketList<Packet> underwritingInfos) {
        Map<PathMapping, Packet> resultMap = new HashMap<PathMapping, Packet>(underwritingInfos.size());
        if (underwritingInfos == null || underwritingInfos.size() == 0) {
            return resultMap;
        }

        for (Packet uwInfo : underwritingInfos) {
            UnderwritingInfoPacket underwritingInfo = (UnderwritingInfoPacket) uwInfo;
            String originPath = packetCollector.getSimulationScope().getStructureInformation().getPath(underwritingInfo);
            PathMapping path = mappingCache.lookupPath(originPath);
            addToMap(underwritingInfo, path, resultMap);

            PathMapping lobPath = null;
            if (!(underwritingInfo.sender instanceof ISegmentMarker)) {
                lobPath = getPathMapping(underwritingInfo, underwritingInfo.segment(), SEGMENTS);
            }
            PathMapping contractPath = null;
            if (!(underwritingInfo.sender instanceof IReinsuranceContractMarker)) {
                contractPath = getPathMapping(underwritingInfo, underwritingInfo.reinsuranceContract(), CONTRACTS);
            }
            if (underwritingInfo.sender instanceof ISegmentMarker) {
                addToMap(underwritingInfo, contractPath, resultMap);
            }
            if (underwritingInfo.sender instanceof IReinsuranceContractMarker) {
                addToMap(underwritingInfo, lobPath, resultMap);
            }
            if (underwritingInfo.sender instanceof ILegalEntityMarker) {
                addToMap(underwritingInfo, contractPath, resultMap);
                addToMap(underwritingInfo, lobPath, resultMap);
            }
        }
        return resultMap;
    }

    /**
     * @param packets
     * @return a map with paths as key
     */
    protected Map<PathMapping, Packet> splitByInceptionPeriodPaths(PacketList<Packet> packets) {
        // has to be a LinkedHashMap to make sure the shortest path is the first in the map and gets AGGREGATED as collecting mode
        Map<PathMapping, Packet> resultMap = new LinkedHashMap<PathMapping, Packet>(packets.size());
        if (packets == null || packets.size() == 0) {
            return resultMap;
        }

        for (Packet packet : packets) {
            PathMapping periodPath = getPathMapping(packet);
            addToMap(packet, periodPath, resultMap);
        }
        return resultMap;
    }

    protected void addToMap(Packet packet, PathMapping path, Map<PathMapping, Packet> resultMap) {
        if (packet instanceof ClaimCashflowPacket) {
            addToMap((ClaimCashflowPacket) packet, path, resultMap);
        } else if (packet instanceof UnderwritingInfoPacket) {
            addToMap((UnderwritingInfoPacket) packet, path, resultMap);
        } else if (packet instanceof ContractFinancialsPacket) {
            addToMap((ContractFinancialsPacket) packet, path, resultMap);
        } else if (packet instanceof FinancialsPacket) {
            addToMap((FinancialsPacket) packet, path, resultMap);
        } else if (packet instanceof AdditionalPremium) {
            addToMap((AdditionalPremium) packet, path, resultMap);
        } else {
            throw new IllegalArgumentException("Packet type " + packet.getClass() + " is not supported.");
        }
    }

    protected void addToMap(AdditionalPremium additionalPremium, PathMapping path, Map<PathMapping, Packet> resultMap) {
        if (path == null) return;
        if (resultMap.get(path) == null) {
            resultMap.put(path, additionalPremium);
        } else {
            AdditionalPremium aggregateMe = (AdditionalPremium) resultMap.get(path);
            resultMap.put(path, aggregateMe.plusForAggregateCollection(additionalPremium));
        }
    }

    protected void addToMap(ContractFinancialsPacket packet, PathMapping path, Map<PathMapping, Packet> resultMap) {
        if (path == null) return;
        if (resultMap.containsKey(path)) {
            ContractFinancialsPacket aggregatePacket = (ContractFinancialsPacket) resultMap.get(path);
            aggregatePacket.plus(packet);
            resultMap.put(path, aggregatePacket);
        } else {
            resultMap.put(path, packet.copy());
        }
    }

    protected void addToMap(FinancialsPacket packet, PathMapping path, Map<PathMapping, Packet> resultMap) {
        if (path == null) return;
        if (resultMap.containsKey(path)) {
            FinancialsPacket aggregatePacket = (FinancialsPacket) resultMap.get(path);
            aggregatePacket.plus(packet);
            resultMap.put(path, aggregatePacket);
        } else {
            resultMap.put(path, packet.copy());
        }
    }

    /**
     * @param packet
     * @return path extended by period:inceptionPeriod
     */
    private PathMapping getPathMapping(Packet packet) {
        String periodLabel = inceptionPeriod(packet);
        String pathExtension = "period" + PATH_SEPARATOR + periodLabel;
        String pathExtended = getExtendedPath(packet, pathExtension);
        return mappingCache.lookupPath(pathExtended);
    }

    private String inceptionPeriod(Packet packet) {
        DateTime date = null;
        if (packet instanceof ClaimCashflowPacket) {
            date = ((ClaimCashflowPacket) packet).getBaseClaim().getExposureStartDate();
        } else if (packet instanceof UnderwritingInfoPacket) {
            date = ((UnderwritingInfoPacket) packet).getExposure().getInceptionDate();
        } else if (packet instanceof ContractFinancialsPacket) {
            date = ((ContractFinancialsPacket) packet).getInceptionDate();
        } else if (packet instanceof FinancialsPacket) {
            date = ((FinancialsPacket) packet).getInceptionDate();
        } else {
            throw new IllegalArgumentException("Packet type " + packet.getClass() + " is not supported.");
        }
        if (displayUnderwritingYearOnly) {
            return String.valueOf(date.getYear());
        } else {
            return formatter.print(getPeriodStartDate(date));
        }
    }

    private DateTime getPeriodStartDate(DateTime date) {
        return packetCollector.getSimulationScope().getIterationScope().getPeriodScope().getPeriodCounter().startOfPeriod(date);
    }

    @Override
    protected void initSimulation() {
        super.initSimulation();
        IPeriodCounter periodCounter = packetCollector.getSimulationScope().getIterationScope().getPeriodScope().getPeriodCounter();
        boolean projectionStartsOnFirstJanuary = periodCounter.startOfFirstPeriod().dayOfYear().get() == 1;
        boolean annualPeriods = periodCounter.annualPeriodsOnly(false);
        displayUnderwritingYearOnly = projectionStartsOnFirstJanuary && annualPeriods;
    }

    @Override
    public List<String> filter() {
        return fieldFilter;
    }

    @Override
    public String getIdentifier() {
        StringBuilder identifier = new StringBuilder("AGGREGATE_");
        if (identifier_prefix != null) {
            identifier.append(identifier_prefix).append("_");
        }
        if (drillDownModes.size() == 0 && fieldFilter.size() == 0) {
            identifier.append("NO-SPLIT_NO-FILTER").append("_");
        }
        for (DrillDownMode splitMode : drillDownModes) {
            identifier.append(splitMode.name()).append("_");
        }
        for (String filter : fieldFilter) {
            identifier.append(filter).append("_");
        }
        return StringUtils.removeEnd(identifier.toString(), "_");
    }

    /**
     * Checks if the packet class is compatible with the configured compatible classes.
     * If any compatible class is provided the check is done exclusively on the provided list.
     * Otherwise the compatible list of the super class is taken into account.
     *
     * @param packetClass The packet class.
     * @return true if compatible, false otherwise.
     */
    @Override
    public boolean isCompatibleWith(Class packetClass) {
        boolean compatibleWith = false;
        if (compatibleClasses.size() > 0) {
            for (Class<Packet> compatibleClass : compatibleClasses) {
                compatibleWith |= compatibleClass.isAssignableFrom(packetClass);
            }
            return compatibleWith;
        } else {
            return super.isCompatibleWith(packetClass);
        }
    }

    public List<DrillDownMode> getDrillDownModes() {
        List<DrillDownMode> result = new ArrayList<DrillDownMode>();
        for (DrillDownMode mode : drillDownModes) {
            result.add(mode);
        }
        return result;
    }

    @Override
    public Object[] getArguments() {
        return new Object[]{drillDownModes, fieldFilter, compatibleClasses, identifier_prefix};
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SplitAndFilterCollectionModeStrategy))
            return false;

        SplitAndFilterCollectionModeStrategy that =
                (SplitAndFilterCollectionModeStrategy) o;

        if (compatibleClasses != null ?
                !compatibleClasses.equals(that.compatibleClasses) :
                that.compatibleClasses != null)
            return false;
        if (drillDownModes != null ?
                !drillDownModes.equals(that.drillDownModes) : that.drillDownModes != null)
            return false;
        if (fieldFilter != null ? !fieldFilter.equals(that.fieldFilter)
                : that.fieldFilter != null) return false;
        if (identifier_prefix != null ?
                !identifier_prefix.equals(that.identifier_prefix) :
                that.identifier_prefix != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = drillDownModes != null ? drillDownModes.hashCode()
                : 0;
        result = 31 * result + (fieldFilter != null ?
                fieldFilter.hashCode() : 0);
        result = 31 * result + (compatibleClasses != null ?
                compatibleClasses.hashCode() : 0);
        result = 31 * result + (identifier_prefix != null ?
                identifier_prefix.hashCode() : 0);
        return result;
    }

}
