package org.pillarone.riskanalytics.domain.pc.cf.pattern

import org.joda.time.Period

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
class PatternTests extends GroovyTestCase {

    static double EPSILON = 1E-10

    Pattern pattern


    static Pattern getPattern(List<Integer> periods, List<Double> cumulativeValues) {
        Pattern pattern0 = new Pattern()
        pattern0.cumulativePeriods = []
        periods.each { period -> pattern0.cumulativePeriods.add(Period.months(period)) }
        pattern0.cumulativeValues = cumulativeValues
        return pattern0
    }

    @Override protected void setUp() {
        pattern = getPattern([0, 12, 24, 36, 48], [0d, 0.7d, 0.8d, 0.95d, 1.0d])
    }

    void testOutstandingShare() {
        assertEquals 'remaining after 0 months', 1.0, pattern.outstandingShare(0)
        assertEquals 'remaining after 6 months', 0.65, pattern.outstandingShare(6)
        assertEquals 'remaining after 11 months', (0.3 + 0.7 * 1.0/12.0), pattern.outstandingShare(11), EPSILON
        assertEquals 'remaining after 12 months', 0.3, pattern.outstandingShare(12), EPSILON
        assertEquals 'remaining after 18 months', 0.25, pattern.outstandingShare(18)
        assertEquals 'remaining after 36 months', 0.05, pattern.outstandingShare(36), 1E-10
        assertEquals 'remaining after 48 months', 0.0, pattern.outstandingShare(48)
        assertEquals 'remaining after 60 months', 0.0, pattern.outstandingShare(60)
    }

    void testNextPayoutIndex() {
        assertEquals 'next payout index 0 months', 1, pattern.nextPayoutIndex(0)
        assertEquals 'next payout index 6 months', 1, pattern.nextPayoutIndex(6)
        assertEquals 'next payout index 12 months', 2, pattern.nextPayoutIndex(12), 1E-10
        assertEquals 'next payout index 18 months', 2, pattern.nextPayoutIndex(18)
        assertEquals 'next payout index 36 months', 4, pattern.nextPayoutIndex(36), 1E-10
        assertEquals 'next payout index 48 months', null, pattern.nextPayoutIndex(48)
        assertEquals 'next payout index 60 months', null, pattern.nextPayoutIndex(60)
    }

    void testThisOrNextPayoutIndex() {
        assertEquals 'next payout index 0 months', 0, pattern.thisOrNextPayoutIndex(0)
        assertEquals 'next payout index 6 months', 1, pattern.thisOrNextPayoutIndex(6)
        assertEquals 'next payout index 12 months', 1, pattern.thisOrNextPayoutIndex(12), 1E-10
        assertEquals 'next payout index 18 months', 2, pattern.thisOrNextPayoutIndex(18)
        assertEquals 'next payout index 36 months', 3, pattern.thisOrNextPayoutIndex(36), 1E-10
        assertEquals 'next payout index 48 months', 4, pattern.thisOrNextPayoutIndex(48)
        assertEquals 'next payout index 60 months', null, pattern.thisOrNextPayoutIndex(60)
    }

    void testIncrementFactor() {
        assertEquals 'development period 0', 0d, pattern.incrementFactor(0)
        assertEquals 'development period 1', 0.7d, pattern.incrementFactor(1)
        assertEquals 'development period 2', 0.1d, pattern.incrementFactor(2), EPSILON
        assertEquals 'development period 3', 0.15d, pattern.incrementFactor(3), EPSILON
        assertEquals 'development period 4', 0.05d, pattern.incrementFactor(4), EPSILON

        assertEquals 'elapsed months 0, oustanding share 1', 0d, pattern.incrementFactor(0, 1d)
        assertEquals 'elapsed months 0, oustanding share 0.5', 0d, pattern.incrementFactor(0, 0.5d)
    }

    void testIncrementMonths() {
        assertEquals 'increment months period 0', 0, pattern.incrementMonths(0)
        assertEquals 'increment months period 1', 12, pattern.incrementMonths(1)
        assertEquals 'increment months period 2', 12, pattern.incrementMonths(2)
        assertEquals 'increment months period 3', 12, pattern.incrementMonths(3)
        assertEquals 'increment months period 4', 12, pattern.incrementMonths(4)
        assertEquals 'increment months period 6', null, pattern.incrementMonths(6)
    }
}

