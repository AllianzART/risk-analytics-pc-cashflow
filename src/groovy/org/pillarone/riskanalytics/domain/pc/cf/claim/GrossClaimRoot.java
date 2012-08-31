package org.pillarone.riskanalytics.domain.pc.cf.claim;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.simulation.IPeriodCounter;
import org.pillarone.riskanalytics.core.simulation.engine.PeriodScope;
import org.pillarone.riskanalytics.domain.pc.cf.event.EventPacket;
import org.pillarone.riskanalytics.domain.pc.cf.indexing.Factors;
import org.pillarone.riskanalytics.domain.pc.cf.indexing.IndexUtils;
import org.pillarone.riskanalytics.domain.pc.cf.pattern.IReportingPatternMarker;
import org.pillarone.riskanalytics.domain.pc.cf.pattern.PatternPacket;
import org.pillarone.riskanalytics.domain.pc.cf.pattern.PatternUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Doc: https://issuetracking.intuitive-collaboration.com/jira/browse/PMO-1540
 * It contains all shared information of several ClaimCashflowPacket objects and is used as key.
 *
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
// todo(sku): implement pattern shifts
public final class GrossClaimRoot implements IClaimRoot {

    private static Log LOG = LogFactory.getLog(GrossClaimRoot.class);

    private ClaimRoot claimRoot;
    private double remainingReserves;
    private double previousIBNR;

    private PatternPacket payoutPattern;
    private PatternPacket reportingPattern;

    private Boolean synchronizedPatterns;

    // patterns are applied as of this date
    private DateTime startDateForPatterns;

    private double paidCumulatedIncludingAppliedFactors = 0d;
    private double reportedCumulatedIncludingAppliedFactors = 0d;

    /**
     * counts the currently existing ClaimCashflowPacket referencing this instance, used for debugging only
     */
    private int childCounter;

    public GrossClaimRoot(GrossClaimRoot original) {
        this(new ClaimRoot(original.claimRoot));
        if (original.payoutPattern != null) {
            payoutPattern = original.payoutPattern.clone().get();
        }
        if (original.reportingPattern != null) {
            reportingPattern = original.reportingPattern.clone().get();
        }
    }

    public GrossClaimRoot(ClaimRoot claimRoot) {
        this.claimRoot = claimRoot;
        startDateForPatterns = claimRoot.getOccurrenceDate();
    }

    /**
     * reportingPattern is left null
     * @param claimRoot
     * @param payoutPattern
     */
    public GrossClaimRoot(ClaimRoot claimRoot, PatternPacket payoutPattern, DateTime startDateForPatterns) {
        this(claimRoot, payoutPattern);
        this.startDateForPatterns = startDateForPatterns;
    }

    /**
     * reportingPattern is left null
     * @param claimRoot
     * @param payoutPattern
     */
    public GrossClaimRoot(ClaimRoot claimRoot, PatternPacket payoutPattern) {
        this(claimRoot, payoutPattern, PatternUtils.getTrivialSynchronizePatterns(payoutPattern, IReportingPatternMarker.class));
    }

    public GrossClaimRoot(ClaimRoot claimRoot, PatternPacket payoutPattern, PatternPacket reportingPattern) {
        this(claimRoot);
        this.payoutPattern = payoutPattern != null ? payoutPattern.get() : null;
        this.reportingPattern = reportingPattern != null ? reportingPattern.get() : null;
        // todo(sku): add check for synchronized patterns
    }

    public GrossClaimRoot(ClaimRoot claimRoot, PatternPacket payoutPattern, PatternPacket reportingPattern, DateTime startDateForPatterns) {
        this(claimRoot);
        this.payoutPattern = payoutPattern != null ? payoutPattern.get() : null;
        this.reportingPattern = reportingPattern != null ? reportingPattern.get() : null;
        this.startDateForPatterns = startDateForPatterns;
    }

    /**
     * This constructor preserves claim metadata, but alters the ultimate amount and occurrence date according to what's passed in.
     * @param claimRoot
     * @param ultimate
     * @param occurrenceDate
     * @param startDateForPatterns
     */
    public GrossClaimRoot(GrossClaimRoot claimRoot, double ultimate, DateTime occurrenceDate, DateTime startDateForPatterns){
        this(
                new ClaimRoot(ultimate, claimRoot.getClaimType(), claimRoot.getExposureStartDate(), occurrenceDate, claimRoot.getEvent()) ,
                claimRoot.payoutPattern,
                claimRoot.reportingPattern,
                startDateForPatterns
        );
    }

    public GrossClaimRoot(double ultimate, ClaimType claimType, DateTime exposureStartDate, DateTime occurrenceDate,
                          PatternPacket payoutPattern, PatternPacket reportingPattern) {
        this(
                new ClaimRoot(ultimate, claimType, exposureStartDate, occurrenceDate),
                payoutPattern,
                reportingPattern
        );
    }

    public GrossClaimRoot(double ultimate, ClaimType claimType, DateTime exposureStartDate, DateTime occurrenceDate,
                          PatternPacket payoutPattern, PatternPacket reportingPattern, DateTime startDateForPatterns) {
        this(
                new ClaimRoot(ultimate, claimType, exposureStartDate, occurrenceDate),
                payoutPattern,
                reportingPattern,
                startDateForPatterns
        );
    }

    public GrossClaimRoot(double ultimate, ClaimType claimType, DateTime exposureStartDate, DateTime occurrenceDate,
                          PatternPacket payoutPattern, PatternPacket reportingPattern, EventPacket event) {
        this(
                new ClaimRoot(ultimate, claimType, exposureStartDate, occurrenceDate, event),
                payoutPattern,
                reportingPattern
        );
    }

    /**
     * Utility method to derive cashflow claim of a base claim using its ultimate and pattern
     *
     * @param periodCounter
     * @return
     */
    public List<ClaimCashflowPacket> getClaimCashflowPackets(IPeriodCounter periodCounter) {
        return getClaimCashflowPackets(periodCounter, null);
    }

    /**
     * Hint for direct use in test cases: patterns need to be synchronized before calling this function!
     * @param periodCounter
     * @param factors
     * @return
     */
    public List<ClaimCashflowPacket> getClaimCashflowPackets(IPeriodCounter periodCounter, List<Factors> factors) {
        List<ClaimCashflowPacket> currentPeriodClaims = new ArrayList<ClaimCashflowPacket>();
        boolean isReservesClaim = claimRoot.getClaimType().equals(ClaimType.AGGREGATED_RESERVES) || claimRoot.getClaimType().equals(ClaimType.RESERVE);
        if (!hasTrivialPayout() || isReservesClaim) {
            List<DateFactors> payouts = payoutPattern.getDateFactorsForCurrentPeriod(startDateForPatterns, claimRoot.getOccurrenceDate(), periodCounter, true);
            List<DateFactors> reports = reportingPattern != null ?
                    reportingPattern.getDateFactorsForCurrentPeriod(startDateForPatterns, claimRoot.getOccurrenceDate(), periodCounter, true)
                    : null;
            boolean occurrenceInPeriod = periodCounter.belongsToCurrentPeriod(claimRoot.getOccurrenceDate());
            // no updates due to patterns
            if ((payouts.size() == 0 && reports == null) || (hasIBNR() && (payouts.size() + reports.size() == 0))) {
                if (claimRoot.getOccurrenceDate().plus(payoutPattern.getLastCumulativePeriod()).isAfter(periodCounter.getCurrentPeriodStart())) {
                    DateTime artificalPayoutDate = periodCounter.getCurrentPeriodStart();
                    payouts = payoutPattern.getDateFactorsTillStartOfCurrentPeriod(claimRoot.getOccurrenceDate(), periodCounter);
                    double payoutCumulatedFactor = 0; // if there was no payout before the current period, avoid boundary exception
                    if (payouts.size() > 0) {
                        payoutCumulatedFactor = payouts.get(payouts.size() - 1).getFactorCumulated();
                    }
                    double factor = IndexUtils.aggregateFactor(factors, artificalPayoutDate, periodCounter, claimRoot.getOccurrenceDate());
                    double reserves = claimRoot.getUltimate() * (1 - payoutCumulatedFactor) * factor;
                    double changeInReserves = reserves - remainingReserves;
                    remainingReserves = reserves;
                    double changeInIBNR = reserves - paidCumulatedIncludingAppliedFactors - previousIBNR;
                    previousIBNR = reserves - paidCumulatedIncludingAppliedFactors;
                    // todo(sku): why is the factor applied twice to reserves?
                    ClaimCashflowPacket cashflowPacket = new ClaimCashflowPacket(this, 0, 0, 0, paidCumulatedIncludingAppliedFactors,
                            reserves * factor, changeInReserves * factor, changeInIBNR * factor, claimRoot.getExposureInfo(),
                            artificalPayoutDate, periodCounter);

                    currentPeriodClaims.add(cashflowPacket);
                }
            }
            else {
                for (int i = 0; i < payouts.size(); i++) {
                    DateTime payoutDate = payouts.get(i).getDate();
                    double factor = IndexUtils.aggregateFactor(factors, payoutDate, periodCounter, claimRoot.getOccurrenceDate());
                    double payoutIncrementalFactor = payouts.get(i).getFactorIncremental();
                    double payoutCumulatedFactor = payouts.get(i).getFactorCumulated();
                    double nominalUltimate = claimRoot.getUltimate();
                    // check for occurrenceInPeriod is necessary as pattern synchronization is setting the payoutDate
                    // for missing trivial entries to the last date which might be the occurrence date in the last period
                    double ultimate = !isReservesClaim && occurrenceInPeriod && claimRoot.getOccurrenceDate() == payoutDate ? nominalUltimate : 0;
                    double reserves = nominalUltimate * (1 - payoutCumulatedFactor) * factor;
                    double changeInReserves = reserves - remainingReserves;
                    remainingReserves = reserves;
                    double paidIncremental = nominalUltimate * payoutIncrementalFactor;
                    double paidIncrementalIndexed = paidIncremental * factor;
                    double paidCumulated = nominalUltimate * payoutCumulatedFactor;
                    double paidCumulatedIndexed = paidCumulatedIncludingAppliedFactors + paidIncrementalIndexed;
                    paidCumulatedIncludingAppliedFactors = paidCumulatedIndexed;
                    ClaimCashflowPacket cashflowPacket;
                    if (!hasIBNR() && !isReservesClaim && factor == 1) {
                        cashflowPacket = new ClaimCashflowPacket(this, ultimate, nominalUltimate, paidIncrementalIndexed,
                                paidCumulatedIndexed, reserves, changeInReserves, 0, claimRoot.getExposureInfo(),
                                payoutDate, periodCounter);
                        reportedCumulatedIncludingAppliedFactors = nominalUltimate;
                    }
                    else {
                        double reportedCumulated = reportedCumulated(nominalUltimate, paidCumulated, 1, payoutCumulatedFactor, reports, i);
                        double outstanding = reportedCumulated - paidCumulated;
                        double outstandingIndexed = outstanding * factor;
                        double reportedCumulatedIndexed = outstandingIndexed + paidCumulatedIndexed;
                        double reportedIncrementalIndexed = reportedCumulatedIndexed - reportedCumulatedIncludingAppliedFactors;
                        reportedCumulatedIncludingAppliedFactors = reportedCumulatedIndexed;
                        double changeInIBNR = (reserves - reportedCumulatedIndexed + paidCumulatedIncludingAppliedFactors) - previousIBNR;
                        previousIBNR = reserves - reportedCumulatedIndexed + paidCumulatedIncludingAppliedFactors;
                        cashflowPacket = new ClaimCashflowPacket(this, ultimate, nominalUltimate, paidIncrementalIndexed,
                                paidCumulatedIndexed, reportedIncrementalIndexed, reportedCumulatedIndexed, reserves, changeInReserves,
                                changeInIBNR, claimRoot.getExposureInfo(), payoutDate, periodCounter);
                    }
                    cashflowPacket.setAppliedIndexValue(factor);
                    childCounter++;
                    checkCorrectDevelopment(cashflowPacket);
                    currentPeriodClaims.add(cashflowPacket);
                }
            }
        }
        else {
            currentPeriodClaims.add(singleClaimIndexed(periodCounter, factors));
        }
        return currentPeriodClaims;
    }

    /**
     * Helper method if there is no development, everything reported and paid at the same moment
     * @param periodCounter
     * @param factors
     * @return
     */
    private ClaimCashflowPacket singleClaimIndexed(IPeriodCounter periodCounter, List<Factors> factors) {
        double factor = IndexUtils.aggregateFactor(factors, getOccurrenceDate(), periodCounter, claimRoot.getOccurrenceDate());
        double scaledUltimate = claimRoot.getUltimate() * factor;
        return new ClaimCashflowPacket(this, claimRoot.getUltimate(), claimRoot.getUltimate(),
                scaledUltimate, scaledUltimate, scaledUltimate, scaledUltimate, 0, 0, 0, claimRoot.getExposureInfo(),
                getOccurrenceDate(), periodCounter);
    }

    private double reportedCumulated(double ultimate, double paidCumulated, double factor, double payoutCumulatedFactor,
                                     List<DateFactors> reports, int idx) {
        if (hasSynchronizedPatterns()) {
            // set reportsCumulatedFactor = 1 if payout pattern is longer than reported pattern
            double reportsCumulatedFactor = idx < reports.size() ? reports.get(idx).getFactorCumulated() : 1d;
            double outstanding = ultimate * (reportsCumulatedFactor - payoutCumulatedFactor) * factor;
            return outstanding + paidCumulated;
        }
        else if (!hasIBNR()) {
            return ultimate * factor;
        }
        return 0;
    }

    public void updateCumulatedValuesAtProjectionStart(IPeriodCounter periodCounter, List<Factors> factors) {
        List<DateFactors> payouts = payoutPattern.getDateFactorsTillStartOfCurrentPeriod(claimRoot.getOccurrenceDate(), periodCounter);
        List<DateFactors> reports = reportingPattern != null ?
                reportingPattern.getDateFactorsTillStartOfCurrentPeriod(claimRoot.getOccurrenceDate(), periodCounter)
                : null;
        for (int i = 0; i < payouts.size(); i++) {
            DateTime payoutDate = payouts.get(i).getDate();
            double factor = IndexUtils.aggregateFactor(factors, payoutDate, periodCounter, claimRoot.getOccurrenceDate());
            double payoutIncrementalFactor = payouts.get(i).getFactorIncremental();
            double payoutCumulatedFactor = payouts.get(i).getFactorCumulated();
            double ultimate = claimRoot.getUltimate();
            double paidIncrementalIndexed = ultimate * payoutIncrementalFactor * factor;
            double paidCumulatedIndexed = paidCumulatedIncludingAppliedFactors + paidIncrementalIndexed;
            paidCumulatedIncludingAppliedFactors = paidCumulatedIndexed;
            double paidCumulated = ultimate * payoutCumulatedFactor;
            double reportedCumulated = reportedCumulated(ultimate, paidCumulated, 1, payoutCumulatedFactor, reports, i);
            double outstanding = reportedCumulated - paidCumulated;
            double outstandingIndexed = outstanding * factor;
            double reportedCumulatedIndexed = outstandingIndexed + paidCumulatedIndexed;
            reportedCumulatedIncludingAppliedFactors = reportedCumulatedIndexed;
        }
    }

    public double getUltimate() {
        return claimRoot.getUltimate();
    }

    public boolean hasEvent() {
        return claimRoot.hasEvent();
    }

    public EventPacket getEvent() {
        return claimRoot.getEvent();
    }

    public ClaimType getClaimType() {
        return claimRoot.getClaimType();
    }

    public DateTime getExposureStartDate() {
        return claimRoot.getExposureStartDate();
    }

    public DateTime getOccurrenceDate() {
        return claimRoot.getOccurrenceDate();
    }

    public Integer getOccurrencePeriod(IPeriodCounter periodCounter) {
        return claimRoot.getOccurrencePeriod(periodCounter);
    }

    /**
     * Delegates to equally named method of claimRoot object
     * @param periodScope
     * @return
     */
    public boolean occurrenceInCurrentPeriod(PeriodScope periodScope) {
        return claimRoot.occurrenceInCurrentPeriod(periodScope);
    }

    public Integer getInceptionPeriod(IPeriodCounter periodCounter) {
        return claimRoot.getInceptionPeriod(periodCounter);
    }

    /**
     * @return payout and reported pattern have the same period entries. True even if one of them is null
     */
    public boolean hasSynchronizedPatterns() {
        if (synchronizedPatterns == null) {
            if (reportingPattern == null || payoutPattern == null) {
                synchronizedPatterns = false;
            }
            else {
                synchronizedPatterns = PatternUtils.hasSameCumulativePeriods(payoutPattern, reportingPattern, true);
            }
        }
        return synchronizedPatterns;
    }

    public boolean hasTrivialPayout() {
        return payoutPattern == null || payoutPattern.isTrivial();
    }

    public boolean hasIBNR() {
        return reportingPattern != null && !reportingPattern.isTrivial();
    }

    public IClaimRoot withScale(double scaleFactor) {
        GrossClaimRoot grossClaimRoot = new GrossClaimRoot((ClaimRoot) claimRoot.withScale(scaleFactor), payoutPattern, reportingPattern);
        grossClaimRoot.remainingReserves = remainingReserves * scaleFactor;
        grossClaimRoot.previousIBNR = previousIBNR * scaleFactor;
        return grossClaimRoot;
    }

    public DateTime getStartDateForPatterns() {
        return startDateForPatterns;
    }

    @Override
    public String toString() {
        String separator = ", ";
        StringBuilder result = new StringBuilder();
        result.append(getUltimate());
        result.append(separator);
        result.append(getClaimType());
        result.append(separator);
        result.append(getOccurrenceDate());
        return result.toString();
    }

    /**
     * check that a claim is fully reported and paid
     *
     * @param cashflowPacket
     */
    private void checkCorrectDevelopment(ClaimCashflowPacket cashflowPacket) {
        if (LOG.isTraceEnabled()) {
            if (childCounter == payoutPattern.size() & cashflowPacket.developedUltimate() != paidCumulatedIncludingAppliedFactors) {
                LOG.trace("developed ultimate: " + cashflowPacket.developedUltimate());
                LOG.trace("paid cumulated: " + paidCumulatedIncludingAppliedFactors);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GrossClaimRoot)) return false;

        GrossClaimRoot that = (GrossClaimRoot) o;

        if (childCounter != that.childCounter) return false;
        if (Double.compare(that.paidCumulatedIncludingAppliedFactors, paidCumulatedIncludingAppliedFactors) != 0)
            return false;
        if (Double.compare(that.previousIBNR, previousIBNR) != 0) return false;
        if (Double.compare(that.remainingReserves, remainingReserves) != 0) return false;
        if (Double.compare(that.reportedCumulatedIncludingAppliedFactors, reportedCumulatedIncludingAppliedFactors) != 0)
            return false;
        if (claimRoot != null ? !claimRoot.equals(that.claimRoot) : that.claimRoot != null) return false;
        if (payoutPattern != null ? !payoutPattern.equals(that.payoutPattern) : that.payoutPattern != null)
            return false;
        if (reportingPattern != null ? !reportingPattern.equals(that.reportingPattern) : that.reportingPattern != null)
            return false;
        if (startDateForPatterns != null ? !startDateForPatterns.equals(that.startDateForPatterns) : that.startDateForPatterns != null)
            return false;
        if (synchronizedPatterns != null ? !synchronizedPatterns.equals(that.synchronizedPatterns) : that.synchronizedPatterns != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = claimRoot != null ? claimRoot.hashCode() : 0;
        temp = remainingReserves != +0.0d ? Double.doubleToLongBits(remainingReserves) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = previousIBNR != +0.0d ? Double.doubleToLongBits(previousIBNR) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (payoutPattern != null ? payoutPattern.hashCode() : 0);
        result = 31 * result + (reportingPattern != null ? reportingPattern.hashCode() : 0);
        result = 31 * result + (synchronizedPatterns != null ? synchronizedPatterns.hashCode() : 0);
        result = 31 * result + (startDateForPatterns != null ? startDateForPatterns.hashCode() : 0);
        temp = paidCumulatedIncludingAppliedFactors != +0.0d ? Double.doubleToLongBits(paidCumulatedIncludingAppliedFactors) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = reportedCumulatedIncludingAppliedFactors != +0.0d ? Double.doubleToLongBits(reportedCumulatedIncludingAppliedFactors) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + childCounter;
        return result;
    }
}
