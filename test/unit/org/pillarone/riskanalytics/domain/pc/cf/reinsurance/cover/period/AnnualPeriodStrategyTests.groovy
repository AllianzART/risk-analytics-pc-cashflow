package org.pillarone.riskanalytics.domain.pc.cf.reinsurance.cover.period

import org.joda.time.DateTime
import org.junit.Test
import org.pillarone.riskanalytics.core.simulation.IPeriodCounter
import org.pillarone.riskanalytics.core.simulation.TestPeriodCounterUtilities

/**
 * @author stefan (dot) kunz (at) intuitive-collaboration (dot) com
 */
class AnnualPeriodStrategyTests extends GroovyTestCase {

    @Test
    void currentPeriodContainsCover() {
        DateTime date20100101 = new DateTime(2010, 1, 1, 0, 0, 0, 0)
        IPeriodStrategy periodStrategy = PeriodStrategyType.getStrategy(PeriodStrategyType.ANNUAL, ['startCover': date20100101, 'numberOfYears': 3])
        IPeriodCounter periodCounter = TestPeriodCounterUtilities.getLimitedContinuousPeriodCounter(date20100101, 3)
        assertTrue periodStrategy.currentPeriodContainsCover(periodCounter)
        assertTrue periodStrategy.currentPeriodContainsCover(periodCounter.next())
        assertTrue periodStrategy.currentPeriodContainsCover(periodCounter.next())

        periodStrategy = PeriodStrategyType.getStrategy(PeriodStrategyType.ANNUAL, ['startCover': date20100101, 'numberOfYears': 2])
        periodCounter.reset()
        assertTrue periodStrategy.currentPeriodContainsCover(periodCounter)
        assertTrue periodStrategy.currentPeriodContainsCover(periodCounter.next())
        assertFalse periodStrategy.currentPeriodContainsCover(periodCounter.next())
    }
}