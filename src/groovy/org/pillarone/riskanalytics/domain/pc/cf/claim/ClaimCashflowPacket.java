package org.pillarone.riskanalytics.domain.pc.cf.claim;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.components.Component;
import org.pillarone.riskanalytics.core.components.IComponentMarker;
import org.pillarone.riskanalytics.core.packets.MultiValuePacket;
import org.pillarone.riskanalytics.core.packets.Packet;
import org.pillarone.riskanalytics.core.simulation.IPeriodCounter;
import org.pillarone.riskanalytics.core.simulation.NotInProjectionHorizon;
import org.pillarone.riskanalytics.core.simulation.engine.PeriodScope;
import org.pillarone.riskanalytics.domain.pc.cf.event.IEvent;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.ExposureInfo;
import org.pillarone.riskanalytics.domain.pc.cf.indexing.Factors;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.ClaimStorage;
import org.pillarone.riskanalytics.domain.utils.datetime.DateTimeUtilities;
import org.pillarone.riskanalytics.domain.utils.marker.IReinsuranceContractMarker;
import org.pillarone.riskanalytics.domain.utils.marker.IReserveMarker;
import org.pillarone.riskanalytics.domain.utils.marker.ISegmentMarker;
import org.pillarone.riskanalytics.domain.utils.marker.IPerilMarker;
import org.pillarone.riskanalytics.domain.utils.marker.ILegalEntityMarker;
import org.pillarone.riskanalytics.core.packets.IAggregatableSummable;


import java.util.*;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class ClaimCashflowPacket extends MultiValuePacket implements IAggregatableSummable{

    private static Log LOG = LogFactory.getLog(ClaimCashflowPacket.class);

    /**
     * This property is used for the calculation of derived figures and has to be individually set for every gross, ceded
     * and net claim.
     */
    private final IClaimRoot baseClaim;
    /**
     * This property is used as key and should be the same for claims derived of a common original claim. Don't use
     * it for any calculations!
     */
    private final IClaimRoot keyClaim;
    private ExposureInfo exposureInfo;

    /**
     * is different from 0 only in the occurrence period
     */
    private double ultimate;
    /**
     * contains the ultimate value of the occurrence period in every period
     */
    private double nominalUltimate;
    private double paidIncrementalIndexed;
    private double paidCumulatedIndexed;
    private double reportedIncrementalIndexed;
    private double reportedCumulatedIndexed;
    private double reservesIndexed;
    private double changeInReservesIndexed;
    private double changeInIBNRIndexed;
    private double appliedIndexValue;
    private double premiumRisk;
    private double reserveRisk;

    private DateTime updateDate;
    private Integer updatePeriod;

    /**
     * true only if this packet belongs to the occurrence period of the claim
     */
    @Deprecated
    private boolean hasUltimate;

    private IPerilMarker peril;
    private ISegmentMarker segment;
    private IReinsuranceContractMarker reinsuranceContract;
    private ILegalEntityMarker legalEntity;
    private IReserveMarker reserve;

    private List<Factors> discountFactors;

    /**
     * Used for 'zero' claims
     */
    public ClaimCashflowPacket() {
        this(new ClaimRoot(0, ClaimType.ATTRITIONAL, null, null));
    }

    // todo(sku): safer c'tor required, currently used for ultimate modelling
    private ClaimCashflowPacket(IClaimRoot baseClaim) {
        this(baseClaim, baseClaim);
    }

    public ClaimCashflowPacket(IClaimRoot baseClaim, DateTime date) {
        this(baseClaim, baseClaim);
        this.paidIncrementalIndexed = 0d;
        this.reportedIncrementalIndexed = 0d;
        this.changeInReservesIndexed = 0d;
        this.updateDate = date;
        setDate(date);
        initRiskBased();
    }

    public ClaimCashflowPacket(IClaimRoot baseClaim, IClaimRoot keyClaim) {
        this.baseClaim = baseClaim;
        this.keyClaim = keyClaim;
        hasUltimate = true;
        ultimate = baseClaim.getUltimate();
        nominalUltimate = ultimate;
        this.paidCumulatedIndexed = ultimate();
        this.paidIncrementalIndexed = ultimate();
        this.reportedCumulatedIndexed = ultimate();
        this.reportedIncrementalIndexed = ultimate();
        this.reservesIndexed = 0;
        updateDate = baseClaim.getOccurrenceDate();
        setDate(updateDate);
        initRiskBased();
    }

    public ClaimCashflowPacket(IClaimRoot baseClaim, double ultimate, double nominalUltimate, double paidIncrementalIndexed,
                               double paidCumulatedIndexed, double reservesIndexed, double changeInReservesIndexed,
                               double changeInIBNRIndexed, ExposureInfo exposureInfo, DateTime updateDate, IPeriodCounter periodCounter) {
        this(baseClaim, ultimate, nominalUltimate, paidIncrementalIndexed, paidCumulatedIndexed, ultimate, baseClaim.getUltimate(),
                reservesIndexed, changeInReservesIndexed, changeInIBNRIndexed, exposureInfo, updateDate, periodCounter);
    }

    public ClaimCashflowPacket(IClaimRoot baseClaim, double ultimate, double nominalUltimate, double paidIncrementalIndexed, double paidCumulatedIndexed,
                               double reportedIncrementalIndexed, double reportedCumulatedIndexed, double reservesIndexed,
                               double changeInReservesIndexed, double changeInIBNRIndexed, ExposureInfo exposureInfo, DateTime updateDate,
                               IPeriodCounter periodCounter) {
        this(baseClaim, ultimate, nominalUltimate, paidIncrementalIndexed, paidCumulatedIndexed, reportedIncrementalIndexed,
                reportedCumulatedIndexed, reservesIndexed, changeInReservesIndexed, changeInIBNRIndexed, exposureInfo,
                updateDate, getUpdatePeriod(periodCounter, updateDate));
    }

    public ClaimCashflowPacket(IClaimRoot baseClaim, IClaimRoot keyClaim, double ultimate,
                               double nominalUltimate, double paidIncrementalIndexed, double paidCumulatedIndexed,
                               double reportedIncrementalIndexed, double reportedCumulatedIndexed,
                               double reservesIndexed, double changeInReservesIndexed, double changeInIBNRIndexed,
                               ExposureInfo exposureInfo, DateTime updateDate, int updatePeriod) {
        this(baseClaim, keyClaim, ultimate, paidIncrementalIndexed, paidCumulatedIndexed, reportedIncrementalIndexed,
                reportedCumulatedIndexed, reservesIndexed, changeInReservesIndexed, changeInIBNRIndexed, exposureInfo,
                updateDate, updatePeriod);
        this.nominalUltimate = nominalUltimate;
    }


    public ClaimCashflowPacket(IClaimRoot baseClaim, double ultimate, double paidIncrementalIndexed, double paidCumulatedIndexed,
                               double reportedIncrementalIndexed, double reportedCumulatedIndexed, double reservesIndexed,
                               double changeInReservesIndexed, double changeInIBNRIndexed, ExposureInfo exposureInfo,
                               DateTime updateDate, int updatePeriod) {
        this(baseClaim, baseClaim, ultimate, paidIncrementalIndexed, paidCumulatedIndexed, reportedIncrementalIndexed,
                reportedCumulatedIndexed, reservesIndexed, changeInReservesIndexed, changeInIBNRIndexed, exposureInfo,
                updateDate, updatePeriod);
    }

    public ClaimCashflowPacket(IClaimRoot baseClaim, double ultimate, double nominalUltimate, double paidIncrementalIndexed, double paidCumulatedIndexed,
                               double reportedIncrementalIndexed, double reportedCumulatedIndexed, double reservesIndexed,
                               double changeInReservesIndexed, double changeInIBNRIndexed, ExposureInfo exposureInfo,
                               DateTime updateDate, int updatePeriod) {
        this(baseClaim, baseClaim, ultimate, nominalUltimate, paidIncrementalIndexed, paidCumulatedIndexed,
                reportedIncrementalIndexed, reportedCumulatedIndexed,
                reservesIndexed, changeInReservesIndexed, changeInIBNRIndexed, exposureInfo,
                updateDate, updatePeriod);
    }

    public ClaimCashflowPacket(IClaimRoot baseClaim, IClaimRoot keyClaim, double ultimate, double paidIncrementalIndexed, double paidCumulatedIndexed,
                               double reportedIncrementalIndexed, double reportedCumulatedIndexed, double reservesIndexed,
                               double changeInReservesIndexed, double changeInIBNRIndexed, ExposureInfo exposureInfo,
                               DateTime updateDate, int updatePeriod) {
        this(baseClaim, keyClaim);
        this.ultimate = ultimate;
        if (ultimate != 0) {
            nominalUltimate = ultimate;
        }
        this.paidCumulatedIndexed = paidCumulatedIndexed;
        this.paidIncrementalIndexed = paidIncrementalIndexed;
        this.reportedCumulatedIndexed = reportedCumulatedIndexed;
        this.reportedIncrementalIndexed = reportedIncrementalIndexed;
        this.reservesIndexed = reservesIndexed;
        this.changeInReservesIndexed = changeInReservesIndexed;
        this.changeInIBNRIndexed = changeInIBNRIndexed;
        this.updateDate = updateDate;
        this.updatePeriod = updatePeriod;
        this.exposureInfo = exposureInfo;
        setDate(updateDate);
        initRiskBased();
    }

    /**
     * Creates a ceded claim based on its gross claim and a ClaimStorage
     *
     * @param grossClaim
     * @param claimStorage
     * @param exposureInfo
     */
    public ClaimCashflowPacket(ClaimCashflowPacket grossClaim, ClaimStorage claimStorage, ExposureInfo exposureInfo) {
        this(claimStorage.getReferenceCeded(), grossClaim.getKeyClaim());
        ultimate = claimStorage.getCumulatedCeded(BasedOnClaimProperty.ULTIMATE_UNINDEXED);
        nominalUltimate = claimStorage.getReferenceCeded().getUltimate();
        paidIncrementalIndexed = claimStorage.getIncrementalPaidCeded();
        paidCumulatedIndexed = claimStorage.getCumulatedCeded(BasedOnClaimProperty.PAID);
        reportedIncrementalIndexed = claimStorage.getIncrementalReportedCeded();
        reportedCumulatedIndexed = claimStorage.getCumulatedCeded(BasedOnClaimProperty.REPORTED);
        reservesIndexed = claimStorage.cededReserves();
        changeInReservesIndexed = claimStorage.changeInCededReserves();
        changeInIBNRIndexed = claimStorage.changeInCededIBNR();
        this.exposureInfo = exposureInfo;
        updateDate = grossClaim.getUpdateDate();
        updatePeriod = grossClaim.updatePeriod;
        discountFactors = grossClaim.discountFactors;
        setDate(grossClaim.getUpdateDate());
        initRiskBased();
    }

    /**
     * Convenience method for setting a new payment amount based on an old Claim Cashflow
     */
    public ClaimCashflowPacket(IClaimRoot baseClaim, ClaimCashflowPacket grossCashflow, double paidIncremental, double cumulatedPaid, boolean setUltimate, final double ultimate) {
        this.baseClaim = baseClaim;
        this.keyClaim = grossCashflow.getBaseClaim();
        if(setUltimate) {
            this.ultimate = baseClaim.getUltimate();
            this.nominalUltimate = this.ultimate;
        } else {
            this.ultimate = 0d;
            this.nominalUltimate = 0d;
        }

        this.updateDate = grossCashflow.getUpdateDate();
        setDate(grossCashflow.getDate());
        this.paidIncrementalIndexed = paidIncremental;
        this.paidCumulatedIndexed = cumulatedPaid;

//      Danger here!!!!!
        this.reportedIncrementalIndexed = 0;
        this.reportedCumulatedIndexed = baseClaim.getUltimate();
        this.reservesIndexed = baseClaim.getUltimate() - cumulatedPaid;
        changeInReservesIndexed = 0;
        changeInIBNRIndexed = 0;


        updateDate = grossCashflow.getUpdateDate();
        updatePeriod = grossCashflow.getUpdatePeriod();
        discountFactors = grossCashflow.getDiscountFactors();
        initRiskBased();
    }

    public ClaimCashflowPacket(IClaimRoot baseClaim, IClaimRoot keyClaim, DateTime occurenceDate, double paidIncremental, double cumulatedPaid, int currentPeriod) {
        this.baseClaim = baseClaim;
        this.keyClaim = keyClaim;
        this.ultimate = baseClaim.getUltimate();
        this.nominalUltimate = ultimate;
        this.updateDate = occurenceDate;
        setDate(occurenceDate);
        this.paidIncrementalIndexed = paidIncremental;
        this.paidCumulatedIndexed = cumulatedPaid;

//      Danger here!!!!!
        this.reportedIncrementalIndexed = 0;
        this.reportedCumulatedIndexed = ultimate;
        this.reservesIndexed = ultimate - cumulatedPaid;
        changeInReservesIndexed = -paidIncremental;
        changeInIBNRIndexed = 0;

        updateDate = occurenceDate;
        updatePeriod = currentPeriod;
        discountFactors = null;
        initRiskBased();
    }

    /**
     * When building aggregate claims, this function is not sufficient. Instead the setter methods of premium and reserve
     * risk should be used.
     */
    private void initRiskBased() {
        double riskBased = reportedIncrementalIndexed + changeInIBNRIndexed;
        premiumRisk = (ultimate != 0 && !getClaimType().isReserveClaim()) ? riskBased : 0;
        reserveRisk = (ultimate == 0 || getClaimType().isReserveClaim()) ? riskBased : 0;
    }

    /**
     * Used to modify the packet date property according the persistence date on a cloned instance.
     *
     * @param persistenceDate
     */
    private ClaimCashflowPacket withDate(DateTime persistenceDate) {
        ClaimCashflowPacket packet = (ClaimCashflowPacket) super.clone();
        packet.setDate(persistenceDate);
        return packet;
    }

    /**
     * @return reserves - outstandingIndexed
     */
    public double ibnrIndexed() {
        return reservedIndexed() - outstandingIndexed();
    }

    /** reported = cumulated paid + outstandingIndexed */

    /**
     * @return reported cumulated - paid cumulated
     */
    public double outstandingIndexed() {
        return reportedCumulatedIndexed - paidCumulatedIndexed;
    }

    /**
     * @return reported incremental - paid incremental
     */
    public double changeInOutstandingIndexed() {
        return reportedIncrementalIndexed - paidIncrementalIndexed;
    }

    /**
     * @return ultimate * (1 - cumulated payout factor)
     */
    public double reservedIndexed() {
        return reservesIndexed;
    }

    /**
     * @return developed ultimate at time t_{i} and final ultimate if i = last paidIncrementalIndexed date of claim
     */
    public double developedUltimate() {
        return reservedIndexed() + paidCumulatedIndexed;
    }

    public double totalIncrementalIndexed() {
        return changeInReservesIndexed + paidIncrementalIndexed;
    }

    /**
     * @return 0 except for the occurrence period, nominal ultimate without any index applied
     */
    public double ultimate() {
        return ultimate;
    }

    public double nominalUltimate() {
        return nominalUltimate;
    }

    /**
     * @return difference between developed ultimate and nominal ultimate
     */
    public double developmentResultCumulative() {
        // check necessary due to rounding/rationale numbers
        if (Math.abs(developedUltimate() / nominalUltimate - 1) < 1E-4) return 0d;
        return developedUltimate() - nominalUltimate;
    }

    /**
     * @param periodCounter
     * @return update period in the context of the simulation engine
     */
    public Integer updatePeriod(IPeriodCounter periodCounter) {
        if (updatePeriod == null) {
            updatePeriod = getUpdatePeriod(periodCounter, updateDate);
        }
        return updatePeriod;
    }

    private static Integer getUpdatePeriod(IPeriodCounter periodCounter, DateTime updateDate) {
        try {
            return periodCounter.belongsToPeriod(updateDate);
        } catch (NotInProjectionHorizon ex) {
            LOG.debug(updateDate + " is not in projection horizon");
        }
        return null;
    }

    public int occurrencePeriod(IPeriodCounter periodCounter) {
        return baseClaim.getOccurrencePeriod(periodCounter);
    }

    /**
     * Delegates of method with same name of baseClaim
     *
     * @param periodScope
     * @return true if occurrence is in current period
     */
    public boolean occurrenceInCurrentPeriod(PeriodScope periodScope) {
        return baseClaim.occurrenceInCurrentPeriod(periodScope);
    }

    /**
     * Helper method to fill underwriting period channels
     *
     * @return a clone of the current instance with the date equal to the occurrence date
     */
    public ClaimCashflowPacket getClaimUnderwritingPeriod() {
        return withDate(baseClaim.getOccurrenceDate());
    }

    public double getPaidIncrementalIndexed() {
        return paidIncrementalIndexed;
    }

    public double getReportedIncrementalIndexed() {
        return reportedIncrementalIndexed;
    }

    public IPerilMarker peril() {
        return peril;
    }

    public IReserveMarker reserve() {
        return reserve;
    }

    public ISegmentMarker segment() {
        return segment;
    }

    public IReinsuranceContractMarker reinsuranceContract() {
        return reinsuranceContract;
    }

    public ILegalEntityMarker legalEntity() {
        return legalEntity;
    }

    public void setMarker(IComponentMarker marker) {
        if (marker == null) return;
        if (IPerilMarker.class.isAssignableFrom(marker.getClass())) {
            peril = (IPerilMarker) marker;
        } else if (ISegmentMarker.class.isAssignableFrom(marker.getClass())) {
            segment = (ISegmentMarker) marker;
        } else if (IReinsuranceContractMarker.class.isAssignableFrom(marker.getClass())) {
            reinsuranceContract = (IReinsuranceContractMarker) marker;
        } else if (ILegalEntityMarker.class.isAssignableFrom(marker.getClass())) {
            legalEntity = (ILegalEntityMarker) marker;
        } else if (IReserveMarker.class.isAssignableFrom(marker.getClass())) {
            reserve = (IReserveMarker) marker;
        }
    }

    public void removeMarker(Class<? extends IComponentMarker> marker) {
        if (marker == null) return;
        if (IPerilMarker.class.isAssignableFrom(marker)) {
            peril = null;
        } else if (ISegmentMarker.class.isAssignableFrom(marker)) {
            segment = null;
        } else if (IReinsuranceContractMarker.class.isAssignableFrom(marker)) {
            reinsuranceContract = null;
        } else if (ILegalEntityMarker.class.isAssignableFrom(marker)) {
            legalEntity = null;
        } else if (IReserveMarker.class.isAssignableFrom(marker)) {
            reserve = null;
        }
    }

    public void removeMarkers() {
        peril = null;
        segment = null;
        reinsuranceContract = null;
        legalEntity = null;
        reserve = null;
    }

    public ClaimType getClaimType() {
        return baseClaim.getClaimType();
    }

    public ExposureInfo getExposureInfo() {
        return exposureInfo;
    }

    public boolean hasExposureInfo() {
        return exposureInfo != null;
    }

    /**
     * Add 'properties' calculated on the fly
     *
     * @return
     * @throws IllegalAccessException
     */
    @Override
    public Map<String, Number> getValuesToSave() throws IllegalAccessException {
        Map<String, Number> valuesToSave = new HashMap<String, Number>();
        // total incremental unindexed
        valuesToSave.put(ULTIMATE, ultimate);    // this and missing default c'tor (final!) leads to failure during result tree building
        // total incremental
        valuesToSave.put(TOTAL_INCREMENTAL_INDEXED, totalIncrementalIndexed());
        // total cumulative
        valuesToSave.put(TOTAL_CUMULATIVE_INDEXED, developedUltimate());
        // paid incremental indexed
        valuesToSave.put(PAID_INDEXED, paidIncrementalIndexed);
        valuesToSave.put(PAID_CUMULATIVE_INDEXED, paidCumulatedIndexed);
        // reported incremental indexed
        valuesToSave.put(REPORTED_INDEXED, reportedIncrementalIndexed);
        valuesToSave.put(REPORTED_CUMULATIVE_INDEXED, reportedCumulatedIndexed);
        valuesToSave.put(CHANGES_IN_IBNR_INDEXED, changeInIBNRIndexed);
        valuesToSave.put(IBNR_INDEXED, ibnrIndexed());
        valuesToSave.put(CHANGES_IN_OUTSTANDING_INDEXED, changeInOutstandingIndexed());
        valuesToSave.put(OUTSTANDING_INDEXED, outstandingIndexed());
        // case reserve change
        valuesToSave.put(CHANGES_IN_RESERVES_INDEXED, changeInReservesIndexed);
        // case reserve
        valuesToSave.put(RESERVES_INDEXED, reservedIndexed());
        valuesToSave.put(PREMIUM_RISK_BASE, premiumRisk);
        valuesToSave.put(RESERVE_RISK_BASE, reserveRisk);
        valuesToSave.put(PREMIUM_AND_RESERVE_RISK_BASE, premiumRisk + reserveRisk);
        return valuesToSave;
    }

    /**
     * Add 'properties' calculated on the fly
     *
     * @return
     */
    @Override
    public List<String> getFieldNames() {
        return NON_TRIVIAL_PAYOUT_IBNR;
    }

    @Override
    public String toString() {
        String separator = ", ";
        StringBuilder result = new StringBuilder();
        result.append(ultimate);
        result.append(separator);
        result.append(reportedIncrementalIndexed);
        result.append(separator);
        result.append(paidIncrementalIndexed);
        result.append(separator);
        result.append(DateTimeUtilities.formatDate.print(updateDate));
        result.append(separator);
        result.append(baseClaim.getExposureStartDate());
        result.append(separator);
        result.append(baseClaim.getClaimType());
        if (peril() != null) {
            result.append(separator);
            result.append(((Component) peril()).getNormalizedName());
        }
        if (segment() != null) {
            result.append(separator);
            result.append(((Component) segment()).getNormalizedName());
        }
        return result.toString();
    }

    public final static String ULTIMATE = "ultimate";
    public final static String TOTAL_CUMULATIVE_INDEXED = "totalCumulativeIndexed";
    public final static String TOTAL_INCREMENTAL_INDEXED = "totalIncrementalIndexed";
    public final static String REPORTED_INDEXED = "reportedIncrementalIndexed";
    public final static String PAID_INDEXED = "paidIncrementalIndexed";
    public final static String IBNR_INDEXED = "IBNRIndexed";
    public final static String CHANGES_IN_IBNR_INDEXED = "changesInIBNRIndexed";
    public final static String RESERVES_INDEXED = "reservesIndexed";
    public final static String CHANGES_IN_RESERVES_INDEXED = "changesInReservesIndexed";
    public final static String OUTSTANDING_INDEXED = "outstandingIndexed";
    public final static String DEVELOPED_RESULT_INDEXED = "totalIncrementalIndexed";
    public final static String APPLIED_INDEX_VALUE = "appliedIndexValue";
    public final static String CHANGES_IN_OUTSTANDING_INDEXED = "changesInOutstandingIndexed";
    public final static String REPORTED_CUMULATIVE_INDEXED = "reportedCumulativeIndexed";
    public final static String PAID_CUMULATIVE_INDEXED = "paidCumulativeIndexed";
    public final static String RESERVE_RISK_BASE = "reserveRiskBase";
    public final static String PREMIUM_RISK_BASE = "premiumRiskBase";
    public final static String PREMIUM_AND_RESERVE_RISK_BASE = "premiumAndReserveRiskBase";



    public final static List<String> NON_TRIVIAL_PAYOUT_IBNR = Arrays.asList(ULTIMATE, PAID_INDEXED, RESERVES_INDEXED,
            REPORTED_INDEXED, IBNR_INDEXED, OUTSTANDING_INDEXED, DEVELOPED_RESULT_INDEXED, APPLIED_INDEX_VALUE);

    public IClaimRoot getBaseClaim() {
        return baseClaim;
    }

    /**
     * the updateDate might be the incurred, paidIncrementalIndexed, reportedIncrementalIndexed date, depending on the updated double properties above
     *
     * @return
     */
    public DateTime getUpdateDate() {
        return updateDate;
    }

    public DateTime getOccurrenceDate() {
        return baseClaim.getOccurrenceDate();
    }

    public double getPaidCumulatedIndexed() {
        return paidCumulatedIndexed;
    }

    public double getReportedCumulatedIndexed() {
        return reportedCumulatedIndexed;
    }

    public Integer getUpdatePeriod() {
        return updatePeriod;
    }

    @Deprecated
    public boolean hasUltimate() {
        return hasUltimate;
    }

    public double getNominalUltimate() {
        return nominalUltimate;
    }

    public boolean hasEvent() {
        return getBaseClaim().getClaimType().equals(ClaimType.EVENT) || getBaseClaim().getClaimType().equals(ClaimType.AGGREGATED_EVENT);
    }

    public IEvent getEvent() {
        return getBaseClaim().getEvent();
    }

    public double getAppliedIndexValue() {
        return appliedIndexValue;
    }

    public void setAppliedIndexValue(double appliedIndexValue) {
        this.appliedIndexValue = appliedIndexValue;
    }

    public IClaimRoot getKeyClaim() {
        return keyClaim;
    }

    /**
     * property is normally set in the gross phase of the segment
     */
    public List<Factors> getDiscountFactors() {
        return discountFactors;
    }

    public void setDiscountFactors(List<Factors> discountFactors) {
        this.discountFactors = discountFactors;
    }

    public double getChangeInReservesIndexed() {
        return changeInReservesIndexed;
    }

    public double getChangeInIBNRIndexed() {
        return changeInIBNRIndexed;
    }

    public double getPremiumRisk() {
        return premiumRisk;
    }

    public void setPremiumRisk(double premiumRisk) {
        this.premiumRisk = premiumRisk;
    }

    public double getReserveRisk() {
        return reserveRisk;
    }

    public void setReserveRisk(double reserveRisk) {
        this.reserveRisk = reserveRisk;
    }

    public double totalCumulatedIndexed() {
        return developedUltimate();
    }

    public Packet sum(Collection<Packet> claims)  {
        //From ClaimUtils. IAggregatableSummable implementation

        Iterator<Packet> iterator = claims.iterator();

        if (!iterator.hasNext()) return null; //return null if empty; consistency with legacy code

        ClaimCashflowPacket claim = (ClaimCashflowPacket) iterator.next();

        //these were originally initialised last using the first element in the "list"
        ClaimRoot baseClaim = new ClaimRoot(ultimate, claim.getBaseClaim());
        DateTime updateDate = claim.getUpdateDate();
        int updatePeriod = (claim.getUpdatePeriod() != null) ? claim.getUpdatePeriod() : 0;


        double ultimate = claim.ultimate();
        double nominalUltimate = claim.nominalUltimate();
        double paidIncremental = claim.getPaidIncrementalIndexed();
        double paidCumulated = claim.getPaidCumulatedIndexed();
        double reportedIncremental = claim.getReportedIncrementalIndexed();
        double reportedCumulated = claim.getReportedCumulatedIndexed();
        double reserves = claim.reservedIndexed();
        double appliedIndex = claim.getAppliedIndexValue();
        double changeInReservesIndexed = claim.getChangeInReservesIndexed();
        double changeInIBNRIndexed = claim.getChangeInIBNRIndexed();
        double premiumRisk = claim.getPremiumRisk();
        double reserveRisk = claim.getReserveRisk();

        ClaimCashflowPacket storedFirstClaimForMarkers = claim;

        while (iterator.hasNext()) {
            claim = (ClaimCashflowPacket) iterator.next();
            ultimate += claim.ultimate();
            nominalUltimate += claim.nominalUltimate();
            paidIncremental += claim.getPaidIncrementalIndexed();
            paidCumulated += claim.getPaidCumulatedIndexed();
            reportedIncremental += claim.getReportedIncrementalIndexed();
            reportedCumulated += claim.getReportedCumulatedIndexed();
            reserves += claim.reservedIndexed();
            appliedIndex *= claim.getAppliedIndexValue();
            changeInReservesIndexed += claim.getChangeInReservesIndexed();
            changeInIBNRIndexed += claim.getChangeInIBNRIndexed();
            premiumRisk += claim.getPremiumRisk();
            reserveRisk += claim.getReserveRisk();
        }
//        ClaimRoot baseClaim = new ClaimRoot(ultimate, claims.get(0).getBaseClaim()); //Done before the loop now!
//        DateTime updateDate = claims.get(0).getUpdateDate();
//        int updatePeriod = 0;
//        if (claims.get(0).getUpdatePeriod() != null) {
//            updatePeriod = claims.get(0).getUpdatePeriod();
//        }

        ClaimCashflowPacket summedClaims = new ClaimCashflowPacket(baseClaim, ultimate, nominalUltimate, paidIncremental,
                paidCumulated, reportedIncremental, reportedCumulated, reserves, changeInReservesIndexed,
                changeInIBNRIndexed, null, updateDate, updatePeriod);
        ClaimUtils.applyMarkers(storedFirstClaimForMarkers, summedClaims); //todo: fix this too at some point. ClaimUtils is just ugly.
        //by the way, I don't know why the first claim should be special. Just keeping this behaviour invariant to be sure.
        summedClaims.setAppliedIndexValue(claims.size() == 0 ? 1d : Math.pow(appliedIndex, 1d / claims.size()));
        summedClaims.setPremiumRisk(premiumRisk);
        summedClaims.setReserveRisk(reserveRisk);
        return summedClaims;
    }

    //TODO (dbr, sku) use method returning a map here. when doing so, check if test : AggregateSplitPerSourceCollectingModeStrategyTests works.
    public List<Packet> aggregateByBaseClaim(Collection<Packet> claims) { //ClaimUtils port to IAggegatableSummable
        List<Packet> aggregateByBaseClaim = new ArrayList<Packet>();
        ListMultimap<IClaimRoot, ClaimCashflowPacket> claimsByBaseClaim = ArrayListMultimap.create();
        for (Packet claim : claims) {
            claimsByBaseClaim.put(((ClaimCashflowPacket) claim).getBaseClaim(), (ClaimCashflowPacket) claim);
        }
        for (Collection<ClaimCashflowPacket> claimsWithSameBaseClaim : claimsByBaseClaim.asMap().values()) {
            if (claimsWithSameBaseClaim.size() == 1) {
                aggregateByBaseClaim.add(claimsWithSameBaseClaim.iterator().next());
            }
            else {
                double ultimate = 0;
                double nominalUltimate = 0;
                double paidIncremental = 0;
                double paidCumulated = 0;
                double reportedIncremental = 0;
                double reportedCumulated = 0;
                DateTime mostRecentClaimUpdate = null;
                double latestReserves = 0;
                double appliedIndex = 1;
                double changeInReservesIndexed = 0;
                double changeInIBNRIndexed = 0;
                double premiumRisk = 0;
                double reserveRisk = 0;
                for (ClaimCashflowPacket claim : claimsWithSameBaseClaim) {
                    ultimate += claim.ultimate();
                    nominalUltimate = claim.nominalUltimate();  // don't sum up as every CCP contains the same value!
                    paidIncremental += claim.getPaidIncrementalIndexed();
                    reportedIncremental += claim.getReportedIncrementalIndexed();
                    appliedIndex *= claim.getAppliedIndexValue();
                    premiumRisk += claim.getPremiumRisk();
                    reserveRisk += claim.getReserveRisk();
                    if (mostRecentClaimUpdate == null || claim.getUpdateDate().isAfter(mostRecentClaimUpdate)) {
                        mostRecentClaimUpdate = claim.getUpdateDate();
                        reportedCumulated = claim.getReportedCumulatedIndexed();
                        paidCumulated = claim.getPaidCumulatedIndexed();
                        latestReserves = claim.reservedIndexed();
                        changeInReservesIndexed += claim.getChangeInReservesIndexed();
                        changeInIBNRIndexed += claim.getChangeInIBNRIndexed();
                    }
                }
                IClaimRoot baseClaim = null;
                ClaimCashflowPacket firstClaim = (ClaimCashflowPacket) claims.iterator().next();
                if (firstClaim.getBaseClaim() instanceof GrossClaimRoot) {
                    baseClaim = new GrossClaimRoot((GrossClaimRoot) firstClaim.getBaseClaim());
                }
                else {
                    baseClaim = new ClaimRoot(ultimate, firstClaim.getBaseClaim());
                }

                int updatePeriod = 0;
                if (firstClaim.getUpdatePeriod() != null) {
                    updatePeriod = firstClaim.getUpdatePeriod();
                }
                ClaimCashflowPacket aggregateClaim = new ClaimCashflowPacket(baseClaim, ultimate, nominalUltimate,
                        paidIncremental, paidCumulated, reportedIncremental, reportedCumulated, latestReserves,
                        changeInReservesIndexed, changeInIBNRIndexed, null, mostRecentClaimUpdate, updatePeriod);
                aggregateClaim.setAppliedIndexValue(appliedIndex);
                aggregateClaim.setPremiumRisk(premiumRisk);
                aggregateClaim.setReserveRisk(reserveRisk);
                ClaimUtils.applyMarkers(firstClaim, aggregateClaim); //claimutils!
                aggregateByBaseClaim.add(aggregateClaim);
            }
        }
        return aggregateByBaseClaim;
    }




}
