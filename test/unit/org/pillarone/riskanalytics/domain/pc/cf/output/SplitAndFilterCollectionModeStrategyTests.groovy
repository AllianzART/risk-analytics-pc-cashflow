package org.pillarone.riskanalytics.domain.pc.cf.output

import grails.test.GrailsUnitTestCase
import models.gira.GIRAModel
import org.joda.time.DateTime
import org.joda.time.Period
import org.pillarone.riskanalytics.core.output.CollectorMapping
import org.pillarone.riskanalytics.core.output.FieldMapping
import org.pillarone.riskanalytics.core.output.PacketCollector
import org.pillarone.riskanalytics.core.output.PathMapping
import org.pillarone.riskanalytics.core.output.SingleValueResultPOJO
import org.pillarone.riskanalytics.core.packets.PacketList
import org.pillarone.riskanalytics.core.parameterization.StructureInformation
import org.pillarone.riskanalytics.core.simulation.LimitedContinuousPeriodCounter
import org.pillarone.riskanalytics.core.simulation.engine.IterationScope
import org.pillarone.riskanalytics.core.simulation.engine.MappingCache
import org.pillarone.riskanalytics.core.simulation.engine.PeriodScope
import org.pillarone.riskanalytics.core.simulation.engine.SimulationScope
import org.pillarone.riskanalytics.core.simulation.item.Simulation
import org.pillarone.riskanalytics.core.simulation.item.parameter.ParameterHolderFactory
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket
import org.pillarone.riskanalytics.domain.pc.cf.claim.generator.ClaimsGenerator
import org.pillarone.riskanalytics.domain.pc.cf.segment.Segment
import org.pillarone.riskanalytics.core.output.DrillDownMode
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.additionalPremium.AdditionalPremium
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.additionalPremium.APBasis
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.contracts.StatelessRIContract
import org.pillarone.riskanalytics.domain.utils.marker.IReinsuranceContractMarker
import org.pillarone.riskanalytics.core.packets.Packet
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.stateless.additionalPremium.CalcAPBasis

/**
 * @author detlef.brendle (at) canoo (dot) com
 */
class SplitAndFilterCollectionModeStrategyTests extends GrailsUnitTestCase {
    SplitAndFilterCollectionModeStrategy strategy

    private void setupStrategy(List<DrillDownMode> splitModes = [], List<String> fieldFilter = [], def simulationStart = new DateTime(System.currentTimeMillis())) {
        setupStrategy(splitModes, fieldFilter, simulationStart, [])
    }

    private void setupStrategy(List<DrillDownMode> splitModes = [], List<String> fieldFilter = [], DateTime simulationStart, List<Class<Packet>> compatibleClasses) {
        mockDomain(PathMapping)
        mockDomain(CollectorMapping)
        mockDomain(FieldMapping)
        strategy = new SplitAndFilterCollectionModeStrategy(splitModes, fieldFilter, compatibleClasses)
        PacketCollector packetCollector = new PacketCollector(strategy)
        packetCollector.setPath("Path:to:collect")
        SimulationScope simulationScope = new SimulationScope()
        simulationScope.setStructureInformation(new StructureInformation(new ConfigObject(), new GIRAModel()))
        simulationScope.setMappingCache(new MappingCache())
        simulationScope.simulation = new Simulation("SpiltAndFilterCollectionModeStrategyUnitTest");  //AR-111: sims will need to have and UpdateDate for the BY_UPDATEDATE split to work
        simulationScope.simulation.addParameter(ParameterHolderFactory.getHolder("runtimeUpdateDate",0,simulationStart.plusMonths(3)))
        IterationScope iterationScope = new IterationScope()
        PeriodScope periodScope = new PeriodScope()
        periodScope.setPeriodCounter(new LimitedContinuousPeriodCounter(simulationStart, new Period(1, 0, 0, 0), 10))
        iterationScope.setPeriodScope(periodScope)
        simulationScope.setIterationScope(iterationScope)
        packetCollector.setSimulationScope(simulationScope)
        strategy.setPacketCollector(packetCollector)
    }

    void testCollectChanges_no_filter_no_split() throws IllegalAccessException {
        PacketList packets = new PacketList()
        def packet = new ClaimCashflowPacket()
        packets.add(packet)
        setupStrategy()
        List<SingleValueResultPOJO> result = strategy.collect(packets, false)
        assert packet.valuesToSave.size() == result.size()
    }

    void testCollectChanges_with_filter_no_split() throws IllegalAccessException {
        PacketList packets = new PacketList()
        def packet = new ClaimCashflowPacket()
        packets.add(packet)
        setupStrategy([], [ClaimCashflowPacket.ULTIMATE, ClaimCashflowPacket.CHANGES_IN_IBNR_INDEXED], new DateTime(System.currentTimeMillis()))
        List<SingleValueResultPOJO> result = strategy.collect(packets, false)
        assert 2 == result.size(), 'only the ultimate and changes in IBNR fields expected'
    }

// AR-111 USED TO BREAK THIS
    void testCollectChanges_no_filter_split_by_period() {
        def simulationStart = new DateTime(System.currentTimeMillis())
        PacketList packets = new PacketList()
        def packet = new ClaimCashflowPacket()
        packet.baseClaim.exposureStartDate = simulationStart
        packets.add(packet)
        setupStrategy([DrillDownMode.BY_PERIOD], [], simulationStart)
        List<SingleValueResultPOJO> result = strategy.collect(packets, false)
        assert 2 * packet.valuesToSave.size() == result.size()
    }

    void testCollectChanges_no_filter_split_by_updatedate() {
        def simulationStart = new DateTime(System.currentTimeMillis())
        PacketList packets = new PacketList()
        def packet = new ClaimCashflowPacket()
        packet.baseClaim.occurrenceDate = simulationStart.plusDays(1)
        packets.add(packet)
        setupStrategy([DrillDownMode.BY_UPDATEDATE], [], simulationStart)
        List<SingleValueResultPOJO> result = strategy.collect(packets, false)
        assert 2 * packet.valuesToSave.size() == result.size()
    }

    void testCollectChanges_no_filter_split_by_source() {
        def simulationStart = new DateTime(System.currentTimeMillis())
        PacketList packets = new PacketList()
        def packet = new ClaimCashflowPacket()
        packets.add(packet)
        packet.senderChannelName = 'senderChannelName'
        packet.setSender(new Segment())
        packet.setMarker(new ClaimsGenerator(name: "testClaimsGenerator"))
        setupStrategy([DrillDownMode.BY_SOURCE], [], simulationStart)
        List<SingleValueResultPOJO> result = strategy.collect(packets, false)
        assert 2 * packet.valuesToSave.size() == result.size()
    }



// AR-111 USED TO BREAK THIS
    void testCollectChanges_no_filter_split_by_source_and_by_period() {
        def simulationStart = new DateTime(System.currentTimeMillis())
        PacketList packets = new PacketList()
        def packet = new ClaimCashflowPacket()
        packets.add(packet)
        packet.baseClaim.exposureStartDate = simulationStart
        packet.senderChannelName = 'senderChannelName'
        packet.setSender(new Segment())
        packet.setMarker(new ClaimsGenerator(name: "testClaimsGenerator"))
        setupStrategy([DrillDownMode.BY_SOURCE, DrillDownMode.BY_PERIOD], [], simulationStart)
        List<SingleValueResultPOJO> result = strategy.collect(packets, false)
        assert 3 * packet.valuesToSave.size() == result.size()
    }

    void testCollectChanges_no_filter_split_by_source_and_by_updatedate() {
        def simulationStart = new DateTime(System.currentTimeMillis())
        PacketList packets = new PacketList()
        def packet = new ClaimCashflowPacket()
        packets.add(packet)
        packet.baseClaim.exposureStartDate = simulationStart
        packet.baseClaim.occurrenceDate = simulationStart.plusDays(15)
        packet.senderChannelName = 'senderChannelName'
        packet.setSender(new Segment())
        packet.setMarker(new ClaimsGenerator(name: "testClaimsGenerator"))
        setupStrategy([DrillDownMode.BY_SOURCE, DrillDownMode.BY_UPDATEDATE], [], simulationStart)
        List<SingleValueResultPOJO> result = strategy.collect(packets, false)
        assert 3 * packet.valuesToSave.size() == result.size()
    }

// AR-111 USED TO BREAK THIS
    void testCollectChanges_with_filter_split_by_source_and_by_period() {
        def simulationStart = new DateTime(System.currentTimeMillis())
        PacketList packets = new PacketList()
        def packet = new ClaimCashflowPacket()
        packets.add(packet)
        packet.baseClaim.exposureStartDate = simulationStart
        packet.senderChannelName = 'senderChannelName'
        packet.setSender(new Segment())
        packet.setMarker(new ClaimsGenerator(name: "testClaimsGenerator"))
        setupStrategy([DrillDownMode.BY_SOURCE, DrillDownMode.BY_PERIOD], [ClaimCashflowPacket.CHANGES_IN_IBNR_INDEXED], simulationStart)
        List<SingleValueResultPOJO> result = strategy.collect(packets, false)
        assert 3 == result.size()
    }

    void testCollectChanges_with_filter_split_by_source_and_by_updatedate() {
        def simulationStart = new DateTime(System.currentTimeMillis())
        PacketList packets = new PacketList()
        def packet = new ClaimCashflowPacket()
        packets.add(packet)
        packet.baseClaim.exposureStartDate = simulationStart
        packet.baseClaim.occurrenceDate = simulationStart.plusDays(15)
        packet.senderChannelName = 'senderChannelName'
        packet.setSender(new Segment())
        packet.setMarker(new ClaimsGenerator(name: "testClaimsGenerator"))
        setupStrategy([DrillDownMode.BY_SOURCE, DrillDownMode.BY_UPDATEDATE], [ClaimCashflowPacket.CHANGES_IN_IBNR_INDEXED], simulationStart)
        List<SingleValueResultPOJO> result = strategy.collect(packets, false)
        assert 3 == result.size()
    }

    void testCollectType(){
        def simulationStart = new DateTime(System.currentTimeMillis())
        final StatelessRIContract contract = new StatelessRIContract(name: "testRIContract")
        PacketList packets = new PacketList()
        packets.add(makeAPPacket(simulationStart, contract))
        packets.add(makeAPPacket(simulationStart, contract))
        setupStrategy([DrillDownMode.BY_TYPE], [], simulationStart , [AdditionalPremium.class])
        List<SingleValueResultPOJO> result = strategy.collect(packets, false)
        assert 2 == result.size()
    }

    void testIdentifier(){
        strategy = new SplitAndFilterCollectionModeStrategy()
        assert 'AGGREGATE_NO-SPLIT_NO-FILTER' == strategy.identifier
        setupStrategy()
        assert 'AGGREGATE_NO-SPLIT_NO-FILTER' == strategy.identifier
        setupStrategy([DrillDownMode.BY_PERIOD])
        assert 'AGGREGATE_BY_PERIOD' == strategy.identifier
        setupStrategy([DrillDownMode.BY_SOURCE, DrillDownMode.BY_PERIOD])
        assert 'AGGREGATE_BY_SOURCE_BY_PERIOD' == strategy.identifier
        setupStrategy([DrillDownMode.BY_PERIOD],['field1'])
        assert 'AGGREGATE_BY_PERIOD_field1' == strategy.identifier
        setupStrategy([DrillDownMode.BY_TYPE])
        assert 'AGGREGATE_BY_TYPE' == strategy.identifier
        setupStrategy([DrillDownMode.BY_UPDATEDATE])              //AR-111
        assert 'AGGREGATE_BY_UPDATEDATE' == strategy.identifier   //AR-111
    }

    void testNewInstance() {
        setupStrategy()
        // calls the empty constructor.
        strategy.class.newInstance(new Object[0])
        // calls the constructor matching the given arguments
        strategy.class.newInstance(strategy.arguments)
    }

    AdditionalPremium makeAPPacket(DateTime simStart, StatelessRIContract contract){
        def packet = new AdditionalPremium(10, CalcAPBasis.NCB)
        packet.senderChannelName = 'senderChannelName'
        packet.addMarker(IReinsuranceContractMarker, contract )
        return packet
    }
}
