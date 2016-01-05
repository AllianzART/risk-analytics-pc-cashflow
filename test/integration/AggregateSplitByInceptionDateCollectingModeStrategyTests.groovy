import org.pillarone.riskanalytics.core.simulation.engine.ModelTest
import org.pillarone.riskanalytics.core.output.PathMapping
import org.pillarone.riskanalytics.core.output.FieldMapping
import org.pillarone.riskanalytics.core.output.SingleValueResult
import org.pillarone.riskanalytics.core.output.ICollectorOutputStrategy
import models.gira.GIRAModel
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket
import org.apache.commons.lang.builder.HashCodeBuilder
import org.pillarone.riskanalytics.core.output.TestDBOutput

import static org.junit.Assert.*

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
class AggregateSplitByInceptionDateCollectingModeStrategyTests extends ModelTest {

    Class getModelClass() {
        GIRAModel
    }

    String getResultConfigurationFileName() {
        'TestGIRACYClaimUwResultConfiguration'
    }

    String getResultConfigurationDisplayName() {
        'CY Split for Claims and Underwriting'
    }

    String getParameterFileName() {
        'TestGIRAInceptionPeriodsParameters'
    }

    String getParameterDisplayName() {
        'Inception Periods'
    }

    protected ICollectorOutputStrategy getOutputStrategy() {
        new TestDBOutput()
    }

    int getIterationCount() {
        1
    }

    void postSimulationEvaluation() {
        correctPaths()
        correctFields(['totalIncrementalIndexed', 'IBNRIndexed', 'reservesIndexed', 'outstandingIndexed',
                'paidIncrementalIndexed', 'reportedIncrementalIndexed', 'ultimate', 'premiumWritten', 'premiumPaid',
                ClaimCashflowPacket.CHANGES_IN_OUTSTANDING_INDEXED, ClaimCashflowPacket.REPORTED_CUMULATIVE_INDEXED,
                ClaimCashflowPacket.PAID_CUMULATIVE_INDEXED, ClaimCashflowPacket.TOTAL_CUMULATIVE_INDEXED,
                ClaimCashflowPacket.RESERVE_RISK_BASE, ClaimCashflowPacket.PREMIUM_AND_RESERVE_RISK_BASE,
                ClaimCashflowPacket.PREMIUM_RISK_BASE])

        correctReportingClaimsResults()
        correctPaidClaimsResults()
        correctPremiumResults()
        correctPremiumRiskResults()
        correctReserveRiskResults()
    }

    void correctPaths() {
        List<String> paths = [
                'GIRA:underwritingSegments:subMarine:outUnderwritingInfo',
                'GIRA:underwritingSegments:subMarine:period:2012:outUnderwritingInfo',
                'GIRA:underwritingSegments:subMarine:period:2013:outUnderwritingInfo',
                'GIRA:underwritingSegments:subMarine:period:2014:outUnderwritingInfo',
                'GIRA:underwritingSegments:subMarine:period:2015:outUnderwritingInfo',
                'GIRA:claimsGenerators:subMarine:outClaims',
                'GIRA:claimsGenerators:subMarine:period:2012:outClaims',
                'GIRA:claimsGenerators:subMarine:period:2013:outClaims',
                'GIRA:claimsGenerators:subMarine:period:2014:outClaims',
                'GIRA:claimsGenerators:subMarine:period:2015:outClaims'
        ]
        def collectedPaths = PathMapping.list()
        // on the KTI branch paths are prepared before simulation starts in a generic way, therefore there are more than on the master
        assertTrue '# of paths correct', paths.size() < collectedPaths.size()

        for (int i = 0; i < collectedPaths.size(); i++) {
            if (collectedPaths[i].pathName.contains("subcomponents")) continue
            def init = paths.contains(collectedPaths[i].pathName)

            if (!paths.remove(collectedPaths[i].pathName)) {
                println "additionally collected path ${collectedPaths[i].pathName}"
            }
        }

        assertTrue "all paths found $paths.size()", paths.size() == 0

    }

    void correctFields(List<String> fields) {
        def collectedFields = FieldMapping.list()
        // on the KTI branch fields are prepared before simulation starts in a generic way, therefore there are more than on the master
        assertTrue '# of fields correct', fields.size() < collectedFields.size()

        for (FieldMapping field : collectedFields) {
            if (!fields.remove(field.fieldName)) {
                println "additionally collected field ${field.fieldName}"
            }
        }
        assertTrue "all field found $fields", fields.size() == 0
    }

    void correctReportingClaimsResults() {
        Map<PeriodPath, Double> resultsPerPath = new LinkedHashMap<PeriodPath, Double>()
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 0)]=-800
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2012:outClaims', 0)]=-800
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 1)]=-924
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2012:outClaims', 1)]=-108
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2013:outClaims', 1)]=-816
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 2)]=-997
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2012:outClaims', 2)]=-55
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2013:outClaims', 2)]=-110
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2014:outClaims', 2)]=-832
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 3)]=-1085
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2012:outClaims', 3)]=-55
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2013:outClaims', 3)]=-58
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2014:outClaims', 3)]=-116
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2015:outClaims', 3)]=-856

        def results = SingleValueResult.list()
        for (SingleValueResult result : results) {
            if (result.field.fieldName == "reportedIncrementalIndexed") {
                assertEquals "${result.path.pathName}, P${result.period}", resultsPerPath.get(new PeriodPath(result)), result.value, 1E-8d
            }
        }
    }

    void correctPaidClaimsResults() {
        Map<PeriodPath, Double> resultsPerPath = new LinkedHashMap<PeriodPath, Double>()
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 0)]=-500
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2012:outClaims', 0)]=-500
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 1)]=-765
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2012:outClaims', 1)]=-255
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2013:outClaims', 1)]=-510
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 2)]=-936
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2012:outClaims', 2)]=-156
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2013:outClaims', 2)]=-260
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2014:outClaims', 2)]=-520
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 3)]=-1070
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2012:outClaims', 3)]=-107
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2013:outClaims', 3)]=-160.5
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2014:outClaims', 3)]=-267.5
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2015:outClaims', 3)]=-535

        def results = SingleValueResult.list()
        for (SingleValueResult result : results) {
            if (result.field.fieldName == "paidIncrementalIndexed") {
                assertEquals "${result.path.pathName}, P${result.period}", resultsPerPath.get(new PeriodPath(result)), result.value, 1E-8d
            }
        }
    }

    void correctReserveRiskResults() {
        Map<PeriodPath, Double> resultsPerPath = new LinkedHashMap<PeriodPath, Double>()
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 0)]=null
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2012:outClaims', 0)]=null
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 1)]=-10d
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2012:outClaims', 1)]=-10d
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 2)]=-15d
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2012:outClaims', 2)]=-5d
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2013:outClaims', 2)]=-10d
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 3)]=-25.5d
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2012:outClaims', 3)]=-3d
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2013:outClaims', 3)]=-7.5d
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:period:2014:outClaims', 3)]=-15d

        def results = SingleValueResult.list()
        for (SingleValueResult result : results) {
            if (result.field.fieldName == "netReserveRisk") {
                assertEquals "${result.path.pathName}, P${result.period}", resultsPerPath.get(new PeriodPath(result)), result.value, 1E-8d
            }
        }
    }

    void correctPremiumRiskResults() {
        Map<PeriodPath, Double> resultsPerPath = new LinkedHashMap<PeriodPath, Double>()
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 0)]=-1000
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 1)]=-1020
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 2)]=-1040
        resultsPerPath[new PeriodPath('GIRA:claimsGenerators:subMarine:outClaims', 3)]=-1070

        def results = SingleValueResult.list()
        for (SingleValueResult result : results) {
            if (result.field.fieldName == "netPremiumRisk") {
                assertEquals "${result.path.pathName}, P${result.period}", resultsPerPath.get(new PeriodPath(result)), result.value, 1E-8d
            }
        }
    }

    void correctPremiumResults() {
        Map<String, Double> resultsPerPath = new LinkedHashMap<String, Double>()
        resultsPerPath[new PeriodPath('GIRA:underwritingSegments:subMarine:outUnderwritingInfo', 0)]=1000
        resultsPerPath[new PeriodPath('GIRA:underwritingSegments:subMarine:period:2012:outUnderwritingInfo', 0)]=1000
        resultsPerPath[new PeriodPath('GIRA:underwritingSegments:subMarine:outUnderwritingInfo', 1)]=1020
        resultsPerPath[new PeriodPath('GIRA:underwritingSegments:subMarine:period:2013:outUnderwritingInfo', 1)]=1020
        resultsPerPath[new PeriodPath('GIRA:underwritingSegments:subMarine:outUnderwritingInfo', 2)]=1040
        resultsPerPath[new PeriodPath('GIRA:underwritingSegments:subMarine:period:2014:outUnderwritingInfo', 2)]=1040
        resultsPerPath[new PeriodPath('GIRA:underwritingSegments:subMarine:outUnderwritingInfo', 3)]=1070
        resultsPerPath[new PeriodPath('GIRA:underwritingSegments:subMarine:period:2015:outUnderwritingInfo', 3)]=1070

        def results = SingleValueResult.list()
        for (SingleValueResult result : results) {
            if (result.field.fieldName == "premiumPaid") {
                assertEquals "${result.path.pathName}, P${result.period}", resultsPerPath.get(new PeriodPath(result)), result.value, 1E-8d
            }
        }
    }
    
}

class PeriodPath {
    String path
    int period

    PeriodPath(SingleValueResult result) {
        path = result.path
        period = result.period
    }

    PeriodPath(String path, int period) {
        this.path = path
        this.period = period
    }

    public int hashCode() {
        HashCodeBuilder hashCodeBuilder = new HashCodeBuilder();
        hashCodeBuilder.append(path);
        hashCodeBuilder.append(period);
        return hashCodeBuilder.toHashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof PeriodPath) {
            return ((PeriodPath) obj).path.equals(path) && ((PeriodPath) obj).period.equals(period)
        } else {
            return false;
        }
    }
}
