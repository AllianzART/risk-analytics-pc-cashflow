package org.pillarone.riskanalytics.domain.pc.cf.claim;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.apache.commons.lang.NotImplementedException;
import org.joda.time.DateTime;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.ExposureInfo;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.ClaimStorage;
import org.pillarone.riskanalytics.domain.utils.marker.IReinsuranceContractMarker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class ClaimUtils {

    /**
     * Adds up all claims and builds a corresponding new base claim. It's exposure info and discount factor is null.
     * WARNING: if the updatePeriod of the first claim is not set the return claim has updatePeriod = 0.
     *
     * @param claims
     * @param sameBaseClaim: Marker interface of returned packet are all null if false
     * @return null if claims is empty. New object if claims.size() > 1
     */
    public static ClaimCashflowPacket sum(List<ClaimCashflowPacket> claims, boolean sameBaseClaim) {
        if (claims.isEmpty()) return null;
        if (claims.size() == 1) return claims.get(0);
        if (!sameBaseClaim) {
            throw new NotImplementedException("method is currently implemented for same base claim only");
        }
        double ultimate = 0;
        double nominalUltimate = 0;
        double paidIncremental = 0;
        double paidCumulated = 0;
        double reportedIncremental = 0;
        double reportedCumulated = 0;
        double reserves = 0;
        double appliedIndex = 1;
        double changeInReservesIndexed = 0;
        double changeInIBNRIndexed = 0;
        for (ClaimCashflowPacket claim : claims) {
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
        }
        ClaimRoot baseClaim = new ClaimRoot(ultimate, claims.get(0).getBaseClaim());
        DateTime updateDate = claims.get(0).getUpdateDate();
        int updatePeriod = 0;
        if (claims.get(0).getUpdatePeriod() != null) {
            updatePeriod = claims.get(0).getUpdatePeriod();
        }
        ClaimCashflowPacket summedClaims = new ClaimCashflowPacket(baseClaim, ultimate, nominalUltimate, paidIncremental,
                paidCumulated, reportedIncremental, reportedCumulated, reserves, changeInReservesIndexed,
                changeInIBNRIndexed, null, updateDate, updatePeriod);
        applyMarkers(claims.get(0), summedClaims);
        summedClaims.setAppliedIndexValue(claims.size() == 0 ? 1d : Math.pow(appliedIndex, 1d / claims.size()));
        return summedClaims;
    }

    public static List<ClaimCashflowPacket> aggregateByBaseClaim(List<ClaimCashflowPacket> claims) {
        List<ClaimCashflowPacket> aggregateByBaseClaim = new ArrayList<ClaimCashflowPacket>();
        ListMultimap<IClaimRoot, ClaimCashflowPacket> claimsByBaseClaim = ArrayListMultimap.create();
        for (ClaimCashflowPacket claim : claims) {
            claimsByBaseClaim.put(claim.getBaseClaim(), claim);
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
                for (ClaimCashflowPacket claim : claimsWithSameBaseClaim) {
                    ultimate += claim.ultimate();
                    nominalUltimate = claim.nominalUltimate();  // don't sum up as every CCP contains the same value!
                    paidIncremental += claim.getPaidIncrementalIndexed();
                    reportedIncremental += claim.getReportedIncrementalIndexed();
                    appliedIndex *= claim.getAppliedIndexValue();
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
                if (claims.get(0).getBaseClaim() instanceof GrossClaimRoot) {
                    baseClaim = new GrossClaimRoot((GrossClaimRoot) claims.get(0).getBaseClaim());
                }
                else {
                    baseClaim = new ClaimRoot(ultimate, claims.get(0).getBaseClaim());
                }
                int updatePeriod = 0;
                if (claims.get(0).getUpdatePeriod() != null) {
                    updatePeriod = claims.get(0).getUpdatePeriod();
                }
                ClaimCashflowPacket aggregateClaim = new ClaimCashflowPacket(baseClaim, ultimate, nominalUltimate,
                        paidIncremental, paidCumulated, reportedIncremental, reportedCumulated, latestReserves,
                        changeInReservesIndexed, changeInIBNRIndexed, null, mostRecentClaimUpdate, updatePeriod);
                aggregateClaim.setAppliedIndexValue(appliedIndex);
                applyMarkers(claims.get(0), aggregateClaim);
                aggregateByBaseClaim.add(aggregateClaim);
            }
        }
        return aggregateByBaseClaim;
    }

    /**
     * @param claim
     * @param factor
     * @return new ClaimCashflowPacket using current base as scaled base claim and keeping key claim
     */
    public static ClaimCashflowPacket scale(ClaimCashflowPacket claim, double factor) {
        return scale(claim, factor, claim.getBaseClaim(), true);
    }

    public static ClaimCashflowPacket scale(ClaimCashflowPacket claim, double factor, IClaimRoot scaledBaseClaim, boolean keepKeyClaim) {
        if (notTrivialValues(claim)) {
            double scaledReserves = (claim.developedUltimate() - claim.getPaidCumulatedIndexed()) * factor;
            // todo(sku): check impact on PMO-2072 and vice-versa
            double scaledUltimate = claim.ultimate() == 0d ? 0d : claim.ultimate() * factor;
            double scaledNominalUltimate = claim.nominalUltimate() * factor;
            double scaledPaidIncremental = claim.getPaidIncrementalIndexed() == 0d ? 0d : claim.getPaidIncrementalIndexed() * factor;
            double scaledPaidCumulated = claim.getPaidCumulatedIndexed() == 0d ? 0d : claim.getPaidCumulatedIndexed() * factor;
            double scaledReportedIncremental = claim.getReportedIncrementalIndexed() == 0d ? 0d : claim.getReportedIncrementalIndexed() * factor;
            double scaledReportedCumulated = claim.getReportedCumulatedIndexed() == 0d ? 0d : claim.getReportedCumulatedIndexed() * factor;
            IClaimRoot keyClaim = keepKeyClaim ? claim.getKeyClaim() : scaledBaseClaim;
            double scaledChangeInReserves = claim.getChangeInReservesIndexed() * factor;
            double scaledChangeInIBNR = claim.getChangeInIBNRIndexed() * factor;
            ClaimCashflowPacket scaledClaim = new ClaimCashflowPacket(scaledBaseClaim, keyClaim, scaledUltimate, scaledNominalUltimate,
                    scaledPaidIncremental, scaledPaidCumulated, scaledReportedIncremental, scaledReportedCumulated,
                    scaledReserves, scaledChangeInReserves, scaledChangeInIBNR, claim.getExposureInfo(), claim.getUpdateDate(),
                    claim.getUpdatePeriod());
            scaledClaim.setDiscountFactors(claim.getDiscountFactors());
            applyMarkers(claim, scaledClaim);
            return scaledClaim;
        }
        return claim;
    }

    /**
     * Hint: linked exposure info is not affected by scaling.
     *
     * @param claim
     * @param factor
     * @param scaleBaseClaim
     * @param keepKeyClaim
     * @return scales base claim before scaling the claim itself
     */
    public static ClaimCashflowPacket scale(ClaimCashflowPacket claim, double factor, boolean scaleBaseClaim, boolean keepKeyClaim) {
        if (!scaleBaseClaim) return scale(claim, factor);
        if (notTrivialValues(claim)) {
            IClaimRoot scaledBaseClaim = claim.getBaseClaim().withScale(factor);
            return scale(claim, factor, scaledBaseClaim, keepKeyClaim);
        }
        return claim;
    }

    public static IClaimRoot scale(IClaimRoot claimRoot, double factor) {
        return claimRoot.withScale(factor);
    }

    /**
     * Scales all figures either by the reported or paid scale factor. No distinction between incremental and cumulated
     * claim properties.
     *
     * @param grossClaim
     * @param storage
     * @param scaleFactorUltimate
     * @param scaleFactorReported
     * @param scaleFactorPaid
     * @param adjustExposureInfo
     * @return
     */
    public static ClaimCashflowPacket getCededClaim(ClaimCashflowPacket grossClaim, ClaimStorage storage, double scaleFactorUltimate,
                                                    double scaleFactorReported, double scaleFactorPaid, boolean adjustExposureInfo) {
        if (scaleFactorReported == -0) { scaleFactorReported = 0; }
        if (scaleFactorPaid == -0) { scaleFactorPaid = 0; }
        storage.lazyInitCededClaimRoot(scaleFactorUltimate);
        double cededPaidIncremental = grossClaim.getPaidIncrementalIndexed() * scaleFactorPaid;
        double cededReportedIncremental = grossClaim.getReportedIncrementalIndexed() * scaleFactorReported;
        storage.update(grossClaim.ultimate() * scaleFactorUltimate, BasedOnClaimProperty.ULTIMATE);
        adjustCumulatedUltimateDevelopedCeded(grossClaim, storage, scaleFactorUltimate, cededReportedIncremental);
        storage.update(cededPaidIncremental, BasedOnClaimProperty.PAID);
        storage.update(cededReportedIncremental, BasedOnClaimProperty.REPORTED);
        ExposureInfo cededExposureInfo = adjustExposureInfo && grossClaim.getExposureInfo() != null
                ? grossClaim.getExposureInfo().withScale(scaleFactorUltimate) : grossClaim.getExposureInfo();
        ClaimCashflowPacket cededClaim = new ClaimCashflowPacket(grossClaim, storage, cededExposureInfo);
        applyMarkers(grossClaim, cededClaim);
        return cededClaim;
    }

    public static ClaimCashflowPacket cededClaim(ClaimCashflowPacket grossClaim, ClaimStorage storage, double cededUltimate,
                                                    double cededReportedCummulated, double cededPaidCummulated, boolean adjustExposureInfo) {
        double scaleFactorUltimate = cededUltimate / grossClaim.developedUltimate();
        storage.lazyInitCededClaimRoot(scaleFactorUltimate);
        storage.set(cededUltimate, BasedOnClaimProperty.ULTIMATE);
        adjustCumulatedUltimateDevelopedCeded(grossClaim, storage, scaleFactorUltimate, cededReportedCummulated);
        storage.set(cededPaidCummulated, BasedOnClaimProperty.PAID);
        storage.set(cededReportedCummulated, BasedOnClaimProperty.REPORTED);
        ExposureInfo cededExposureInfo = adjustExposureInfo && grossClaim.getExposureInfo() != null
                ? grossClaim.getExposureInfo().withScale(scaleFactorUltimate) : grossClaim.getExposureInfo();
        ClaimCashflowPacket cededClaim = new ClaimCashflowPacket(grossClaim, storage, cededExposureInfo);
        applyMarkers(grossClaim, cededClaim);
        return cededClaim;
    }

    private static void adjustCumulatedUltimateDevelopedCeded(ClaimCashflowPacket grossClaim, ClaimStorage storage, double scaleFactorUltimate, double cededReportedIncremental) {
        // the following line has only an effect in the occurrence period of the claim as scaleFactorUltimate == 0 in all other periods
        storage.setCumulatedUltimateDevelopedCeded(avoidNegativeZero(grossClaim.developmentResultCumulative() * scaleFactorUltimate));
        if (scaleFactorUltimate == 0 && grossClaim.developmentResultCumulative() != 0) {
            double cumulatedUltimateDevelopedCeded = storage.getCumulatedCeded(BasedOnClaimProperty.REPORTED) + cededReportedIncremental - storage.getNominalUltimate();
            if (storage.getCumulatedCeded(BasedOnClaimProperty.REPORTED) + cededReportedIncremental == 0) {
                cumulatedUltimateDevelopedCeded = grossClaim.developmentResultCumulative() * storage.getNominalUltimate() / grossClaim.getNominalUltimate();
            }
            storage.setCumulatedUltimateDevelopedCeded(avoidNegativeZero(cumulatedUltimateDevelopedCeded));
        }
    }

    /**
     *
     * @param grossClaim
     * @param storage
     * @param scaleFactorUltimate
     * @param cededValueReported
     * @param scaleFactorPaid
     * @param adjustExposureInfo
     * @return
     */
    public static ClaimCashflowPacket getCededClaimReportedAbsolute(ClaimCashflowPacket grossClaim, ClaimStorage storage, double scaleFactorUltimate,
                                                    double cededValueReported, double scaleFactorPaid, boolean adjustExposureInfo) {
        if (scaleFactorPaid == -0) { scaleFactorPaid = 0; }
        storage.lazyInitCededClaimRoot(scaleFactorUltimate);
        double cededPaidIncremental = grossClaim.getPaidIncrementalIndexed() * scaleFactorPaid;
        storage.update(grossClaim.ultimate() * scaleFactorUltimate, BasedOnClaimProperty.ULTIMATE);
        adjustCumulatedUltimateDevelopedCeded(grossClaim, storage, scaleFactorUltimate, cededValueReported);
        storage.update(cededPaidIncremental, BasedOnClaimProperty.PAID);
        storage.update(cededValueReported, BasedOnClaimProperty.REPORTED);
        ExposureInfo cededExposureInfo = adjustExposureInfo && grossClaim.getExposureInfo() != null
                ? grossClaim.getExposureInfo().withScale(scaleFactorUltimate) : grossClaim.getExposureInfo();
        ClaimCashflowPacket cededClaim = new ClaimCashflowPacket(grossClaim, storage, cededExposureInfo);
        applyMarkers(grossClaim, cededClaim);
        return cededClaim;
    }

    public static double avoidNegativeZero(double value) {
        return value == -0 ? 0 : value;
    }

    /**
     * @param grossClaim
     * @param cededClaim
     * @return
     */
    public static ClaimCashflowPacket getNetClaim(ClaimCashflowPacket grossClaim, ClaimCashflowPacket cededClaim,
                                                  IReinsuranceContractMarker contractMarker) {
        if (grossClaim == null && cededClaim == null) {
            return null;
        }
        else if (cededClaim == null || cededClaim.getUpdateDate() == null) {
            ClaimCashflowPacket netClaim = (ClaimCashflowPacket) grossClaim.clone();
            netClaim.setMarker(contractMarker);
            return netClaim;
        }
        else if (grossClaim == null) {
            DateTime occurrenceDate = cededClaim.getOccurrenceDate();
            IClaimRoot baseClaim = new ClaimRoot(0, ClaimType.AGGREGATED, occurrenceDate, occurrenceDate);
            return new ClaimCashflowPacket(baseClaim, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, occurrenceDate, cededClaim.getUpdatePeriod());
        }
        else {
            return getNetClaim(grossClaim, cededClaim, getNetClaimRoot(grossClaim, cededClaim), contractMarker);
        }
    }

    public static ClaimCashflowPacket getNetClaim(ClaimCashflowPacket grossClaim, ClaimCashflowPacket cededClaim, 
                                                  IClaimRoot netBaseClaim, IReinsuranceContractMarker contractMarker) {
        if (cededClaim == null || cededClaim.getUpdateDate() == null) {
            ClaimCashflowPacket netClaim = (ClaimCashflowPacket) grossClaim.clone();
            netClaim.setMarker(contractMarker);
            return netClaim;
        }
        else if (grossClaim == null) {
            DateTime occurrenceDate = cededClaim.getOccurrenceDate();
            IClaimRoot baseClaim = new ClaimRoot(0, ClaimType.AGGREGATED, occurrenceDate, occurrenceDate);
            return new ClaimCashflowPacket(baseClaim, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, occurrenceDate, cededClaim.getUpdatePeriod());
        }
        else {
            boolean isProportionalContract = cededClaim.reinsuranceContract() != null && cededClaim.reinsuranceContract().isProportionalContract();
            double factor = 0;
            ExposureInfo netExposureInfo = isProportionalContract && grossClaim.getExposureInfo() != null
                    ? grossClaim.getExposureInfo().withScale(factor) : grossClaim.getExposureInfo();
            ClaimCashflowPacket netClaim = new ClaimCashflowPacket(
                netBaseClaim,
                grossClaim.getKeyClaim(),
                grossClaim.ultimate() + cededClaim.ultimate(),
                grossClaim.nominalUltimate() + cededClaim.nominalUltimate(),
                grossClaim.getPaidIncrementalIndexed() + cededClaim.getPaidIncrementalIndexed(),
                grossClaim.getPaidCumulatedIndexed() + cededClaim.getPaidCumulatedIndexed(),
                grossClaim.getReportedIncrementalIndexed() + cededClaim.getReportedIncrementalIndexed(),
                grossClaim.getReportedCumulatedIndexed() + cededClaim.getReportedCumulatedIndexed(),
                grossClaim.reservedIndexed() + cededClaim.reservedIndexed(),
                grossClaim.getChangeInReservesIndexed() + cededClaim.getChangeInReservesIndexed(),
                grossClaim.getChangeInIBNRIndexed() + cededClaim.getChangeInIBNRIndexed(),
                netExposureInfo,
                grossClaim.getUpdateDate(),
                grossClaim.getUpdatePeriod());
            netClaim.setDiscountFactors(grossClaim.getDiscountFactors());
            applyMarkers(cededClaim, netClaim);
            return netClaim;
        }
    }



    public static IClaimRoot getNetClaimRoot(ClaimCashflowPacket grossClaim, ClaimCashflowPacket cededClaim) {
        return new ClaimRoot(grossClaim.getNominalUltimate() + cededClaim.getNominalUltimate(),
                grossClaim.getClaimType(), grossClaim.getBaseClaim().getExposureStartDate(),
                grossClaim.getOccurrenceDate(), grossClaim.getEvent());
    }

    public static boolean notTrivialValues(ClaimCashflowPacket claim) {
        return (!(claim.ultimate() == 0 && claim.getPaidIncrementalIndexed() == 0 && claim.getPaidCumulatedIndexed() == 0
                && claim.getReportedIncrementalIndexed() == 0 && claim.getReportedCumulatedIndexed() == 0
                && claim.reservedIndexed() == 0));
    }

    public static void applyMarkers(ClaimCashflowPacket source, ClaimCashflowPacket target) {
        target.setMarker(source.peril());
        target.setMarker(source.segment());
        target.setMarker(source.reinsuranceContract());
        target.setMarker(source.legalEntity());
        target.setMarker(source.reserve());
    }

    public static List<ClaimCashflowPacket> calculateNetClaims(List<ClaimCashflowPacket> claimsGross,
                                                               List<ClaimCashflowPacket> claimsCeded) {
        List<ClaimCashflowPacket> claimsNet = new ArrayList<ClaimCashflowPacket>();
        ListMultimap<IClaimRoot, ClaimCashflowPacket> aggregateCededClaimPerRoot = ArrayListMultimap.create();
        for (ClaimCashflowPacket cededClaim : claimsCeded) {
            aggregateCededClaimPerRoot.put(cededClaim.getKeyClaim(), (ClaimCashflowPacket) cededClaim.copy());
        }
        for (ClaimCashflowPacket grossClaim : claimsGross) {
            List<ClaimCashflowPacket> cededClaims = aggregateCededClaimPerRoot.get(grossClaim.getKeyClaim());
            ClaimCashflowPacket aggregateCededClaim = sum(cededClaims, true);
            ClaimCashflowPacket netClaim = getNetClaim(grossClaim, aggregateCededClaim, null);
            claimsNet.add(netClaim);
        }
        return claimsNet;
    }

    public static ClaimCashflowPacket calculateNetClaim(List<ClaimCashflowPacket> claimsGross,
                                                               List<ClaimCashflowPacket> claimsCeded) {
        if (claimsGross == null && claimsCeded == null) return null;
        if (claimsGross.size() == 0 && claimsCeded.size() == 0) return null;
        List<ClaimCashflowPacket> aggregateClaimsCededByBaseClaim = ClaimUtils.aggregateByBaseClaim(claimsCeded);
        ClaimCashflowPacket claimCeded = ClaimUtils.sum(aggregateClaimsCededByBaseClaim, true);
        List<ClaimCashflowPacket> aggregateClaimsGrossByBaseClaim = ClaimUtils.aggregateByBaseClaim(claimsGross);
        ClaimCashflowPacket claimGross = ClaimUtils.sum(aggregateClaimsGrossByBaseClaim, true);
        if (claimCeded == null) {
            return getNetClaim(claimGross, null, null);
        }
        else {
            return getNetClaim(claimGross, claimCeded, claimCeded.reinsuranceContract());
        }
    }

}
