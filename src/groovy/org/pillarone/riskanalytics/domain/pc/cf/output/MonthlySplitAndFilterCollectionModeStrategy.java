package org.pillarone.riskanalytics.domain.pc.cf.output;

//import com.allianz.art.riskanalytics.cf.generators.claims.RMSCatClaimsGenerator;
import com.google.common.collect.Maps;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.pillarone.riskanalytics.core.components.Component;
import org.pillarone.riskanalytics.core.model.ModelHelper;
import org.pillarone.riskanalytics.core.components.DynamicComposedComponent;
import org.pillarone.riskanalytics.core.output.DrillDownMode;
import org.pillarone.riskanalytics.core.output.PathMapping;
import org.pillarone.riskanalytics.core.output.SingleValueResultPOJO;
import org.pillarone.riskanalytics.core.packets.IAggregatableSummable;
import org.pillarone.riskanalytics.core.packets.Packet;
import org.pillarone.riskanalytics.core.packets.PacketList;
import org.pillarone.riskanalytics.core.packets.SingleValuePacket;
import org.pillarone.riskanalytics.core.simulation.IPeriodCounter;
import org.pillarone.riskanalytics.core.simulation.SimulationException;
import org.pillarone.riskanalytics.core.simulation.engine.GlobalReportingFrequency;
import org.pillarone.riskanalytics.core.simulation.engine.IterationScope;
import org.pillarone.riskanalytics.core.simulation.item.Simulation;
import org.pillarone.riskanalytics.core.util.PeriodLabelsUtil;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.pc.cf.claim.SingleValuePacketWithClaimRoot;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.ContractFinancialsPacket;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.additionalPremium.AdditionalPremium;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.additionalPremium.PaidAdditionalPremium;
import org.pillarone.riskanalytics.domain.pc.cf.segment.FinancialsPacket;
import org.pillarone.riskanalytics.domain.pc.reserves.cashflow.ClaimDevelopmentPacket;
import org.pillarone.riskanalytics.domain.utils.marker.*;
import java.text.MessageFormat;
import java.util.*;

/**
 * Copy/modify from AggregateSplitAndFilterCollectionModeStrategy.. refactor later.
 *
 * Generic way of collection information.
 * Make sure equals and hashCode implementation are base on arguments.
 * Whenever extending drill down functionality, make sure to check if SimulationConfiguration and ModelHelper in the
 * core plugin need to be extended.
 *
 * Note that packets must extend from either single value packet or multi value packet in order to be collectable.
 *
 *@author stefan.kunz (at) intuitive-collaboration (dot) com
 *Sorry Faz, no way I'm taking credit for the somewhat confusing comments above,
 * or the big part of the awful code below which is not mine... - P.
 */
public class MonthlySplitAndFilterCollectionModeStrategy extends AbstractMonthlySplitCollectionModeStrategy {
    protected static Log LOG = LogFactory.getLog(MonthlySplitAndFilterCollectionModeStrategy.class);

    private static final String PERILS = "claimsGenerators";
    private static final String RESERVES = "reservesGenerators";
    private static final String CONTRACTS = "reinsuranceContracts";
    private static final String SEGMENTS = "segments";

    private final Map<Component, Class> componentsExtensibleBy = new HashMap<Component, Class>();

    private final List<DrillDownMode> drillDownModes;
    private final List<String> fieldFilter;
    private boolean displayUnderwritingYearOnly = true;
    private static final DateTimeFormatter formatter = DateTimeFormat.forPattern(PeriodLabelsUtil.PARAMETER_DISPLAY_FORMAT);
    private final List<Class<Packet>> compatibleClasses;
    private final String identifier_prefix;

    private GlobalReportingFrequency reportingFrequency;
    private List<DateTime> reportingDates;
    private DateTime periodStart;
    private DateTime periodEnd;

    /**
     * @param drillDownModes might be void
     * @param fieldFilter might be void
     * @param compatibleClasses overwrites compatible classes defined in super class
     * @param identifier_prefix can be null, normally used to distinguish between packet types
     */
    public MonthlySplitAndFilterCollectionModeStrategy(List<DrillDownMode> drillDownModes, List<String> fieldFilter, List<Class<Packet>> compatibleClasses, String identifier_prefix) {
        this.drillDownModes = drillDownModes;
        this.fieldFilter = fieldFilter;
        this.compatibleClasses = compatibleClasses;
        this.identifier_prefix = identifier_prefix;
    }

    public MonthlySplitAndFilterCollectionModeStrategy(List<DrillDownMode> drillDownModes, List<String> fieldFilter, List<Class<Packet>> compatibleClasses) {
        this(drillDownModes, fieldFilter, compatibleClasses, null);
    }

    public MonthlySplitAndFilterCollectionModeStrategy(List<DrillDownMode> drillDownModes, List<String> fieldFilter) {
        this(drillDownModes, fieldFilter, new ArrayList<Class<Packet>>());
    }

    // required for serialization by gridgain
    public MonthlySplitAndFilterCollectionModeStrategy() {
        this(new ArrayList<DrillDownMode>(), new ArrayList<String>(), new ArrayList<Class<Packet>>());
    }

    List<Packet> flattenLists(List<List<Packet>> packetsByReportingDate,List<DateTime> reportingDates) throws IllegalArgumentException {

        if (reportingDates.size() != packetsByReportingDate.size()) {
            throw new IllegalArgumentException("Packet partition and list of reporting dates don't match");
        }

        List<Packet> result = new ArrayList<Packet>(reportingDates.size());

        for (int i = 0; i < reportingDates.size(); ++i) {
            if (packetsByReportingDate.get(i).isEmpty()) {
                continue;
            }

            IAggregatableSummable tmp = (IAggregatableSummable) packetsByReportingDate.get(i).get(0);
            Packet aggregatePkt = tmp.sum(tmp.aggregateByBaseClaim(packetsByReportingDate.get(i)));
            aggregatePkt.setDate(reportingDates.get(i));
            result.add(aggregatePkt);
        }

        return result;
    }

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
            List<Packet> packetsForPath = packetEntry.getValue();

            List<List<Packet>> packetsByReportingDate = reportingFrequency.PartitionByReportingDate(packetsForPath,periodStart, periodEnd);

            for (Packet packet: flattenLists(packetsByReportingDate, reportingDates)) {

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

    @Override
    public List<SingleValueResultPOJO> collect(PacketList packets, boolean crashSimulationOnError) throws IllegalAccessException {
        initSimulation();
        IterationScope currentIterationScope = packetCollector.getSimulationScope().getIterationScope();
        iteration = currentIterationScope.getCurrentIteration();
        period = currentIterationScope.getPeriodScope().getCurrentPeriod();

        reportingFrequency = (GlobalReportingFrequency) packetCollector.getSimulationScope().getSimulation().getParameter("runtimeReportingFrequency");
        periodStart = currentIterationScope.getPeriodScope().getCurrentPeriodStartDate();

        periodEnd = currentIterationScope.getPeriodScope().getNextPeriodStartDate();
        reportingDates = reportingFrequency.getReportingDatesForPeriod(periodStart, periodEnd);

        //called once per "collect" call - i.e. once per period. The cache created in the financial module could be accessed by
        //calling packetCollector.getSimulationScope().getModel().getAllComponents() and traversing the returned list - doesn't sound more efficient...
        //ideally the "reporting dates" should be cached somewhere accessible and related to where the parameter is set
        // (I'd say in the Simulation or SimulationScope, as the reporting frequency is a Simulation parameter - the reason
        // why I haven't done that is that those classes look like something that gets written in the database...)


        if (isCompatibleWith(packets.get(0).getClass())) {
            Map<PathMapping, List<Packet>> resultMap = allPathMappingsIncludingSplit(packets);
            return createSingleValueResults(resultMap, crashSimulationOnError);
        } else {
            String incompatibleMessage = ResourceBundle.getBundle(RESOURCE_BUNDLE).getString("SplitAndFilterCollectingModeStrategy.incompatibleCollector");
            throw new NotImplementedException(MessageFormat.format(incompatibleMessage, getIdentifier(), drillDownModes, fieldFilter, compatibleClasses, packets.get(0).getClass().getSimpleName()));
        }
    }

    private Map<PathMapping, List<Packet>> allPathMappingsIncludingSplit(PacketList<Packet> packets) throws IllegalAccessException {
        Map<PathMapping, List<Packet>> resultMap = new LinkedHashMap<PathMapping, List<Packet>>(packets.size());
        for (Packet packet : packets) {
            String originPath = packetCollector.getSimulationScope().getStructureInformation().getPath(packet);
            PathMapping path = mappingCache.lookupPath(originPath);
            addToMap(packet, path, resultMap);
        }

        if (drillDownModes.contains(DrillDownMode.BY_SOURCE)) {
//            if (packets.getType().equals(ClaimCashflowPacket.class)) {
            if (packets.get(0) instanceof ClaimCashflowPacket) {
                resultMap.putAll(splitBySourcePathsForClaims(packets));
//            } else if (packets.getType().equals(UnderwritingInfoPacket.class)) {
//            } else if (packets.get(0) instanceof UnderwritingInfoPacket) {
//                resultMap.putAll(splitBySourePathsForUwInfos(packets));
            }
        }
        if (drillDownModes.contains(DrillDownMode.BY_PERIOD)) {
            resultMap.putAll(splitByInceptionPeriodPaths(packets));
        }
        if (drillDownModes.contains(DrillDownMode.BY_UPDATEDATE)) {
            resultMap.putAll(splitByOccurrenceAgainstUpdateDatePaths(packets));
        }
        if (drillDownModes.contains(DrillDownMode.BY_CALENDARYEAR)) {
            resultMap.putAll(splitByCalendarYearOfOccurrence(packets));
        }
        if (drillDownModes.contains(DrillDownMode.BY_CAT_TYPE)) { // Start off with: Nat vs Non Nat - Cat
            resultMap.putAll(splitByCatType(packets));
        }

 /*       if (drillDownModes.contains(DrillDownMode.BY_TYPE)) {
            if(packets.get(0) instanceof AdditionalPremium) {
                resultMap.putAll(splitByAdditionalPremium(packets));
            }
            if(packets.get(0) instanceof PaidAdditionalPremium) {
                resultMap.putAll(splitByPaidAdditionalPremium(packets));
            }
        }
*/
        return resultMap;
    }
/* //uncomment this functions when uncommenting the DrillDownMode.BY_TYPE case above

    private Map<PathMapping, Packet> splitByAdditionalPremium(PacketList<Packet> packets) {
        Map<PathMapping, Packet> tempMap = Maps.newLinkedHashMap();
        for (Packet aPacket : packets) {
            String aPath = getExtendedPath(aPacket, ((AdditionalPremium) aPacket).typeDrillDownName());
            PathMapping pathMapping = mappingCache.lookupPath(aPath);
            if (tempMap.get(pathMapping) == null) {
                tempMap.put(pathMapping, aPacket);
            } else {
                AdditionalPremium aggregateMe = ((AdditionalPremium) tempMap.get(pathMapping));
                tempMap.put(pathMapping, aggregateMe.plusForAggregateCollection(((AdditionalPremium) aPacket))); // TODO ?? s/Aggregate/Monthly/ ??
            }
        }
        return tempMap;
    }

    private Map<PathMapping, Packet> splitByPaidAdditionalPremium(PacketList<Packet> packets) {
        Map<PathMapping, Packet> tempMap = Maps.newLinkedHashMap();
        for (Packet aPacket : packets) {
            String aPath = getExtendedPath(aPacket, ((PaidAdditionalPremium) aPacket).typeDrillDownName());
            PathMapping pathMapping = mappingCache.lookupPath(aPath);
            if (tempMap.get(pathMapping) == null) {
                tempMap.put(pathMapping, aPacket);
            } else {
                PaidAdditionalPremium aggregateMe = ((PaidAdditionalPremium) tempMap.get(pathMapping));
                tempMap.put(pathMapping, aggregateMe.plusForAggregateCollection(((PaidAdditionalPremium) aPacket)));// TODO ?? s/Aggregate/Monthly/ ??
            }
        }
        return tempMap;
    }

    /**
     * @param claims
     * @return a map with paths as key
     */
    protected Map<PathMapping, List<Packet>> splitBySourcePathsForClaims(PacketList<Packet> claims) {
        // has to be a LinkedHashMap to make sure the shortest path is the first in the map and gets AGGREGATED as collecting mode
        Map<PathMapping, List<Packet>> resultMap = new LinkedHashMap<PathMapping, List<Packet>>(claims.size());
        if (claims == null || claims.size() == 0) {
            return resultMap;
        }

        for (Packet c : claims) {
            ClaimCashflowPacket claim = (ClaimCashflowPacket) c;
            PathMapping perilPath = null;
            PathMapping reservePath = null;
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
                reservePath = getPathMapping(claim, claim.reserve(), RESERVES);
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
                addToMap(claim, reservePath, resultMap);
                addToMap(claim, contractPath, resultMap);
            }
            if (IReinsuranceContractMarker.class.equals(markerInterface)) {
                addToMap(claim, lobPath, resultMap);
                addToMap(claim, perilPath, resultMap);
                addToMap(claim, reservePath, resultMap);
                if (lobPath != null && perilPath != null) {
                    PathMapping lobPerilPath = getPathMapping(claim, claim.segment(), SEGMENTS, claim.peril(), PERILS);
                    addToMap(claim, lobPerilPath, resultMap);
                }
            }
            if (ILegalEntityMarker.class.equals(markerInterface) || IStructureMarker.class.equals(markerInterface)) {
                addToMap(claim, perilPath, resultMap);
                addToMap(claim, reservePath, resultMap);
                addToMap(claim, contractPath, resultMap);
                addToMap(claim, lobPath, resultMap);
            }
        }
        return resultMap;
    }
/*
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
*/
    /**
     * @param packets
     * @return a map with paths as key
     */
    protected Map<PathMapping, List<Packet>> splitByInceptionPeriodPaths(PacketList<Packet> packets) {
        // has to be a LinkedHashMap to make sure the shortest path is the first in the map and gets AGGREGATED as collecting mode
        Map<PathMapping, List<Packet>> resultMap = new LinkedHashMap<PathMapping, List<Packet>>(packets.size());
        if (packets == null || packets.size() == 0) {
            return resultMap;
        }

        for (Packet packet : packets) {
            PathMapping periodPath = getPathMappingForInceptionPeriod(packet);
            addToMap(packet, periodPath, resultMap);
        }
        return resultMap;
    }

    protected Map<PathMapping, List<Packet>> splitByOccurrenceAgainstUpdateDatePaths(PacketList<Packet> packets) {
        //ONE WORD OF DIFFERENCE WITH OTHER METHOD! Should be refactored!
        // has to be a LinkedHashMap to make sure the shortest path is the first in the map and gets AGGREGATED as collecting mode
        Map<PathMapping, List<Packet>> resultMap = new LinkedHashMap<PathMapping, List<Packet>>(packets.size());
        if (packets == null || packets.size() == 0) {
            return resultMap;
        }

        for (Packet packet : packets) {
            PathMapping periodPath = getPathMappingForOccurrencePeriodvsUpdateDate(packet);
            addToMap(packet, periodPath, resultMap);
        }
        return resultMap;
    }

    protected Map<PathMapping, List<Packet>> splitByCalendarYearOfOccurrence(PacketList<Packet> packets) {
        //ONE WORD OF DIFFERENCE WITH OTHER METHOD! Should be refactored!
        // has to be a LinkedHashMap to make sure the shortest path is the first in the map and gets AGGREGATED as collecting mode
        Map<PathMapping, List<Packet>> resultMap = new LinkedHashMap<PathMapping, List<Packet>>(packets.size());
        if (packets == null || packets.size() == 0) {
            return resultMap;
        }

        for (Packet packet : packets) {
            PathMapping periodPath = getPathMappingForCalendarYearOfOccurrence(packet);
            addToMap(packet, periodPath, resultMap);
        }
        return resultMap;
    }

    protected Map<PathMapping, List<Packet>> splitByCatType(PacketList<Packet> packets) {
        Map<PathMapping, List<Packet>> resultMap = new LinkedHashMap<PathMapping, List<Packet>>(packets.size());
        if (packets == null || packets.size() == 0) {
            return resultMap;
        }

        for (Packet packet : packets) {
            PathMapping periodPath = getPathMappingForCatType(packet);
            addToMap(packet, periodPath, resultMap);
        }
        return resultMap;
    }

//    protected void addToMap(Packet packet, PathMapping path, Map<PathMapping, Packet> resultMap) {
//        //can't we just check against the list of compatible classes instead of doing this?
//        if (packet instanceof ClaimCashflowPacket) {
//            addToMap((ClaimCashflowPacket) packet, path, resultMap);
//        } else if (packet instanceof UnderwritingInfoPacket) {
//            addToMap((UnderwritingInfoPacket) packet, path, resultMap);
//        } else if (packet instanceof ContractFinancialsPacket) {
//            addToMap((ContractFinancialsPacket) packet, path, resultMap);
//        } else if (packet instanceof FinancialsPacket) {
//            addToMap((FinancialsPacket) packet, path, resultMap);
//        } else if (packet instanceof AdditionalPremium) {
//            addToMap((AdditionalPremium) packet, path, resultMap);
//        } else if (packet instanceof PaidAdditionalPremium) {
//            addToMap((PaidAdditionalPremium) packet, path, resultMap);
//        } else if (packet instanceof SingleValuePacket){
//            addToMap((SingleValuePacket) packet, path, resultMap);
//        } else if (packet instanceof ClaimDevelopmentPacket){
//                    addToMap((ClaimDevelopmentPacket) packet, path, resultMap);
//        } else {
//            throw new IllegalArgumentException("Packet type " + packet.getClass() + " is not supported.");
//        }
//    }

    // Note: list of "compatibleClasses" has already been checked before we get here.
    //
    // In case of single packet collectors the kind of packet doesnt matter as no packet-specific behaviour (ie
    // aggregation) needs to discriminate the packet types. But for other collectors we may need to check the types as in
    // the commented code below, and throw exception for unsupported packets types.
    // As Paolo notes, we should even there be able to use a better approach by factoring out an interface or using
    // better object oriented design.
    protected void addToMap(Packet packet, PathMapping path, Map<PathMapping, List<Packet>> resultMap) {

//
//        if( (packet instanceof ClaimCashflowPacket) || (packet instanceof ClaimDevelopmentPacket) ) {

// Note 2: as this is called once per packet ie EXTREMELY frequetnly, any speedup is very useful, so dropping the instanceof checks is a GOOD THING

            if(path != null){
                List<Packet> packetList;
                // TODO use eg guava or spring map that will automatically create a missing entry on access
                //
                if (resultMap.containsKey(path)) {
                    packetList =  resultMap.get(path);
        } else {
                    packetList = new PacketList<Packet>();
        }
                packetList.add(packet);
                resultMap.put(path, packetList);
        } else {

                String sender = "N/A";
                String channel = "N/A";
                try{
                    sender = "" + packet.getSender();
                }catch(Exception e){
                    //nb Should not allow missing sender or channel to prevent logging of missing path!
                    //hence exceptions not rethrown
        }
                try{
                    channel = packet.getSenderChannelName();
                }catch(Exception e){
                    //nb Should not allow missing sender or channel to prevent logging of missing path!
                    //hence exceptions not rethrown
    }
                LOG.info("Dropping packet (no path!) Sender: " + sender + ", sender channel: " + channel + ")" );
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

    protected void addToMap(SingleValuePacket packet, PathMapping path, Map<PathMapping, Packet> resultMap) {
        if (path == null) return;
        if (resultMap.containsKey(path)) {
            SingleValuePacket aggregatePacket = (SingleValuePacket) resultMap.get(path);
            aggregatePacket.value += packet.value;
            resultMap.put(path, aggregatePacket);
        } else {
            resultMap.put(path, packet.copy());
        }
    }
    /**
     * @param packet
     * @return path extended by period:inceptionPeriod, the later being built using inceptionPeriod(packet)
     */
    private PathMapping getPathMappingForInceptionPeriod(Packet packet) {
        String periodLabel = inceptionPeriod(packet);
        String pathExtension = ModelHelper.PERIOD + ModelHelper.PATH_SEPARATOR + periodLabel;
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

    private PathMapping getPathMappingForOccurrencePeriodvsUpdateDate(Packet packet) {
        String periodLabel = occurrencePeriodvsUpdateDate(packet);
        String pathExtension = ModelHelper.PERIOD + ModelHelper.PATH_SEPARATOR  + periodLabel;
        String pathExtended = getExtendedPath(packet, pathExtension);
        return mappingCache.lookupPath(pathExtended);
    }

    private PathMapping getPathMappingForCalendarYearOfOccurrence(Packet packet) {
        String periodLabel = calendarYearOfOccurrence(packet);
        String pathExtension = ModelHelper.CALENDARYEAROFOCCURRENCE + ModelHelper.PATH_SEPARATOR  + periodLabel;
        String pathExtended = getExtendedPath(packet, pathExtension);
        return mappingCache.lookupPath(pathExtended);
    }

    // Will have two labels: Natcat and Non-natcat
    // ----- aggregate value here:        100
    // ----------'Nat Cat'    part here:   40
    // ----------'NonNat Cat' part here:   10
    //
    private PathMapping getPathMappingForCatType(Packet packet) {
        String pathExtension = ModelHelper.CAT_TYPE + ModelHelper.PATH_SEPARATOR  + NatOrNot(packet); //returns: 'Nat Cat' or 'NonNat Cat'
        String pathExtended = getExtendedPath(packet, pathExtension); // another method that should live in PathMapping
        return mappingCache.lookupPath(pathExtended);
    }



    private String calendarYearOfOccurrence(Packet packet) {

        DateTime occurrenceDate = null;
        Simulation simulation = packetCollector.getSimulationScope().getSimulation();

        occurrenceDate = getOccurrenceDate(packet);
        return ""+occurrenceDate.getYear();
    }

    // Initially simply treat it as Nat Cat if either :-
    // - claim generator class has rms in name (eg RMSCatClaimsGenerator), r
    // - claim generator has natcat in name
    //
    // Simplistic... but I like simple.
    //
    // TODO MOVE INTO DRILLDOWNMODE
    //
    private String NatOrNot(Packet packet) {

        if( !(packet instanceof ClaimCashflowPacket) ){
            throw new SimulationException("Nat vs Non Nat Cat Split only supported for ClaimCashflowPacket, not " +
                                           packet.getClass().getSimpleName());
        }
        ClaimCashflowPacket claimCashflowPacket = (ClaimCashflowPacket) packet;
        IPerilMarker peril = claimCashflowPacket.peril();
        String claimGeneratorName = claimCashflowPacket.peril().getClass().getSimpleName();

        if (StringUtils.containsIgnoreCase(claimGeneratorName, "RMS" ) ){
            return DrillDownMode.catType_Nat;
        }
        else
        {
            return DrillDownMode.catType_nonNat;
        }
    }

    private DateTime getOccurrenceDate(Packet packet) { //ToDo: make a method of packet classes
        DateTime occurrenceDate;
        if (packet instanceof ClaimCashflowPacket) {
            occurrenceDate = ((ClaimCashflowPacket) packet).getBaseClaim().getOccurrenceDate();
        } else if (packet instanceof SingleValuePacketWithClaimRoot) {
            occurrenceDate = ((SingleValuePacketWithClaimRoot) packet).getBaseClaimIncurredDate();
        } else if (packet instanceof ClaimDevelopmentPacket) {
            occurrenceDate = ((ClaimDevelopmentPacket) packet).getIncurredDate();
//            } else if (packet instanceof UnderwritingInfoPacket) {
//              date = ((UnderwritingInfoPacket) packet).getExposure().getInceptionDate();
//            } else if (packet instanceof ContractFinancialsPacket) {
//              date = ((ContractFinancialsPacket) packet).getInceptionDate();
//            } else if (packet instanceof FinancialsPacket) {
//               date = ((FinancialsPacket) packet).getInceptionDate();
        } else {
            throw new IllegalArgumentException("Packet type " + packet.getClass() + " not supported for split cashflow on occurrence date");
        }

        if(occurrenceDate == null ){
            throw new IllegalArgumentException("Null occurrenceDate" );
        }
        return occurrenceDate;
    }

    private String occurrencePeriodvsUpdateDate(Packet packet) {


        Simulation simulation = packetCollector.getSimulationScope().getSimulation();
        if(  simulation != null ){
            DateTime updateDate = ((DateTime) simulation.getParameter("runtimeUpdateDate")); //couldn't this be set once? PA

            if(updateDate == null ){
                throw new IllegalArgumentException("Null updateDate" );
            }

            DateTime occurrenceDate = getOccurrenceDate(packet);

            if (occurrenceDate.isBefore(updateDate)) {
                //    return formatter.print(PC.startOfPeriod(PC.belongsToPeriod(PC.endOfLastPeriod().minusMillis(500)) - 1)); //hack to use the second last sim period, unused in the test case, to go around the path problem
                return DrillDownMode.fromPastName;
            } else if (occurrenceDate.isBefore(updateDate.plusYears(1))) {
                //    return formatter.print(PC.startOfPeriod(PC.belongsToPeriod(PC.endOfLastPeriod().minusMillis(500)))); //hack to use the last sim period, unused in the test case, to go around the path problem
                return DrillDownMode.fromNextName;
            } else {
                //    return formatter.print(PC.startOfPeriod(PC.belongsToPeriod(PC.endOfLastPeriod().minusMillis(500)))); //hack to use the last sim period, unused in the test case, to go around the path problem
                return DrillDownMode.fromFutureName;
            }
        }else{
            // Some test was passing in a hollow object with no sim (and hence no update date).
            //
            // - that was done for tests that this is now passing
            throw new IllegalArgumentException("No update date in simulation context");
        }

    }

    /**
     * Initializes displayUnderwritingYearOnly
     */
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

    /**
     * @return composition of MONTHLY, prefix, drill down modes and filtered fields
     */
    @Override
    public String getIdentifier() {
        StringBuilder identifier = new StringBuilder("MONTHLY_");
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
        return drillDownModes;
    }

    /**
     * @return arguments used in c'tor
     */
    @Override
    public Object[] getArguments() {
        return new Object[]{drillDownModes, fieldFilter, compatibleClasses, identifier_prefix};
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MonthlySplitAndFilterCollectionModeStrategy))
            return false;

        MonthlySplitAndFilterCollectionModeStrategy that =
                (MonthlySplitAndFilterCollectionModeStrategy) o;

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
