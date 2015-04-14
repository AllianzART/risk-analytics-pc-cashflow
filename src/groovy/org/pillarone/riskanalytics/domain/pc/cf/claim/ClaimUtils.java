package org.pillarone.riskanalytics.domain.pc.cf.claim;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.apache.commons.lang.NotImplementedException;
import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.simulation.SimulationException;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.ExposureInfo;
import org.pillarone.riskanalytics.domain.pc.cf.reinsurance.contract.ClaimStorage;
import org.pillarone.riskanalytics.domain.utils.marker.IPerilMarker;
import org.pillarone.riskanalytics.domain.utils.marker.IReinsuranceContractMarker;
import org.pillarone.riskanalytics.domain.utils.math.dependance.DependancePacket;

import org.pillarone.riskanalytics.domain.pc.reserves.cashflow.ClaimDevelopmentPacket; //AR-111
import org.pillarone.riskanalytics.domain.pc.claims.Claim; //AR-111

import java.util.*;

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
        /** PA Note during work on AR-111
        **  Substantial code duplication with aggregateByBaseClaim below.
        **  the two "static methods" are also
        **  other note: I think this at least should be a static method of ClaimCashflowPacket and
        **  other children of the Claim class, especially after pairing with duck-typing - now I see
        **  constructs that could be greatly simplified
        **/

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
        double premiumRisk = 0;
        double reserveRisk = 0;
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
            premiumRisk += claim.getPremiumRisk();
            reserveRisk += claim.getReserveRisk();
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
        summedClaims.setPremiumRisk(premiumRisk);
        summedClaims.setReserveRisk(reserveRisk);
        return summedClaims;
    }
    public static ClaimDevelopmentPacket sum(List<ClaimDevelopmentPacket> claims, boolean sameBaseClaim) {
        /** PA Note during work on AR-111
        **  This should probably be made a method of the class above...
        **/

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
        double premiumRisk = 0;
        double reserveRisk = 0;
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
            premiumRisk += claim.getPremiumRisk();
            reserveRisk += claim.getReserveRisk();
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
        summedClaims.setPremiumRisk(premiumRisk);
        summedClaims.setReserveRisk(reserveRisk);
        return summedClaims;
    }


    public static ClaimCashflowPacket findClaimByKeyClaim(List<ClaimCashflowPacket> claims, IClaimRoot keyClaim) {
        for (ClaimCashflowPacket claim : claims) {
            if (claim.getKeyClaim().equals(keyClaim)) {
                return claim;
            }
        }
        return null;
    }

    public static ClaimCashflowPacket findClaimByBaseClaim(List<ClaimCashflowPacket> claims, IClaimRoot baseClaim) {
        for (ClaimCashflowPacket claim : claims) {
            if (claim.getBaseClaim().equals(baseClaim)) {
                return claim;
            }
        }
        return null;
    }

    //TODO (dbr, sku) use method returning a map here. when doing so, check if test : AggregateSplitPerSourceCollectingModeStrategyTests works.
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
                aggregateClaim.setPremiumRisk(premiumRisk);
                aggregateClaim.setReserveRisk(reserveRisk);
                applyMarkers(claims.get(0), aggregateClaim);
                aggregateByBaseClaim.add(aggregateClaim);
            }
        }
        return aggregateByBaseClaim;
    }

    public static List<ClaimDevelopmentPacket> aggregateByBaseClaim(List<ClaimDevelopmentPacket> claims) {

        List<ClaimDevelopmentPacket> aggregateByBaseClaim = new ArrayList<ClaimDevelopmentPacket>();
        ListMultimap<Claim, ClaimDevelopmentPacket> claimsByBaseClaim = ArrayListMultimap.create();

        for (ClaimDevelopmentPacket claim : claims) {
            claimsByBaseClaim.put( claim.getOriginalClaim(), claim);
        }
        for (Collection<ClaimDevelopmentPacket> claimsWithSameBaseClaim : claimsByBaseClaim.asMap().values()) {
            if (claimsWithSameBaseClaim.size() == 1) {
                aggregateByBaseClaim.add(claimsWithSameBaseClaim.iterator().next());
            }
            else {
                double paid = 0;
                double reserved = 0;
                double changeInReserves = 0;

                for (ClaimDevelopmentPacket claim : claimsWithSameBaseClaim) {
                    paid = claim.getPaid();
                    reserved = claim.getReserved();
                    // I'm not summing as I'd guess these not to be incremental but total value
                    //assumption would be that we are looking at updates to the same claim, in chronological order
                    changeInReserves += claim.getChangeInReserves(); //changes in reserves should be incremental by def
                }
                Claim baseClaim = new Claim();
                //baseClaim.set(claims.get(0).getOriginalClaim());

//                int updatePeriod = 0;
//                if (claims.get(0). != null) {
//                    updatePeriod = claims.get(0).getUpdatePeriod();
//                }

                ClaimDevelopmentPacket aggregateClaim = new ClaimDevelopmentPacket(baseClaim);
                aggregateClaim.setPaid(paid);
                aggregateClaim.setReserved(reserved);
                aggregateClaim.setChangeInReserves(changeInReserves);
                //applyMarkers(claims.get(0), aggregateClaim); //Do we need them? Can't find the equivalent fields...
                aggregateByBaseClaim.add(aggregateClaim);
            }
        }
        return aggregateByBaseClaim;
    }
    
    /**
     * @param claims
     * @return key: original keyClaim, value aggregated claims
     */
    public static Map<IClaimRoot, ClaimCashflowPacket> aggregateByKeyClaim(List<ClaimCashflowPacket> claims) {
        Map<IClaimRoot, ClaimCashflowPacket> aggregateByKeyClaim = new HashMap<IClaimRoot, ClaimCashflowPacket>();
        ListMultimap<IClaimRoot, ClaimCashflowPacket> claimsByBaseClaim = ArrayListMultimap.create();
        for (ClaimCashflowPacket claim : claims) {
            claimsByBaseClaim.put(claim.getKeyClaim(), claim);
        }
        for (Collection<ClaimCashflowPacket> claimsWithSameBaseClaim : claimsByBaseClaim.asMap().values()) {
            ClaimCashflowPacket firstClaim = claimsWithSameBaseClaim.iterator().next();
            if (claimsWithSameBaseClaim.size() == 1) {
                aggregateByKeyClaim.put(firstClaim.getKeyClaim(), firstClaim);
            } else {
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
                    nominalUltimate += claim.nominalUltimate();
                    paidIncremental += claim.getPaidIncrementalIndexed();
                    reportedIncremental += claim.getReportedIncrementalIndexed();
                    appliedIndex *= claim.getAppliedIndexValue();
                    premiumRisk += claim.getPremiumRisk();
                    reserveRisk += claim.getReserveRisk();
                    mostRecentClaimUpdate = claim.getUpdateDate();
                    reportedCumulated += claim.getReportedCumulatedIndexed();
                    paidCumulated += claim.getPaidCumulatedIndexed();
                    latestReserves += claim.reservedIndexed();
                    changeInReservesIndexed += claim.getChangeInReservesIndexed();
                    changeInIBNRIndexed += claim.getChangeInIBNRIndexed();
                }
                IClaimRoot baseClaim = null;
                if (firstClaim.getBaseClaim() instanceof GrossClaimRoot) {
                    baseClaim = new GrossClaimRoot((GrossClaimRoot) firstClaim.getBaseClaim());
                } else {
                    baseClaim = new ClaimRoot(ultimate, firstClaim.getBaseClaim());
                }
                int updatePeriod = 0;
                if (firstClaim.getUpdatePeriod() != null) {
                    updatePeriod = firstClaim.getUpdatePeriod();
                }
                IClaimRoot keyClaim = firstClaim.getKeyClaim();
                ClaimCashflowPacket aggregateClaim = new ClaimCashflowPacket(baseClaim, keyClaim, ultimate, nominalUltimate,
                        paidIncremental, paidCumulated, reportedIncremental, reportedCumulated, latestReserves,
                        changeInReservesIndexed, changeInIBNRIndexed, null, mostRecentClaimUpdate, updatePeriod);
                aggregateClaim.setAppliedIndexValue(appliedIndex);
                aggregateClaim.setPremiumRisk(premiumRisk);
                aggregateClaim.setReserveRisk(reserveRisk);
                applyMarkers(firstClaim, aggregateClaim);
                aggregateByKeyClaim.put(firstClaim.getKeyClaim(), aggregateClaim);
            }
        }
        return aggregateByKeyClaim;
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
            double scaledPremiumRisk = claim.getPremiumRisk() * factor;
            double scaledReserveRisk = claim.getReserveRisk() * factor;
            ClaimCashflowPacket scaledClaim = new ClaimCashflowPacket(scaledBaseClaim, keyClaim, scaledUltimate, scaledNominalUltimate,
                    scaledPaidIncremental, scaledPaidCumulated, scaledReportedIncremental, scaledReportedCumulated,
                    scaledReserves, scaledChangeInReserves, scaledChangeInIBNR, claim.getExposureInfo(), claim.getUpdateDate(),
                    claim.getUpdatePeriod());
            scaledClaim.setDiscountFactors(claim.getDiscountFactors());
            applyMarkers(claim, scaledClaim);
            scaledClaim.setPremiumRisk(scaledPremiumRisk);
            scaledClaim.setReserveRisk(scaledReserveRisk);
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
     * @param scaleFactorUltimateUnindexed
     * @param scaleFactorReported
     * @param scaleFactorPaid
     * @param adjustExposureInfo
     * @return
     */
    public static ClaimCashflowPacket getCededClaim(ClaimCashflowPacket grossClaim, ClaimStorage storage,
                                                    double scaleFactorUltimateUnindexed, double scaleFactorUltimateIndexed,
                                                    double scaleFactorReported, double scaleFactorPaid, boolean adjustExposureInfo) {
        if (scaleFactorReported == -0) {
            scaleFactorReported = 0;
        }
        if (scaleFactorPaid == -0) {
            scaleFactorPaid = 0;
        }
        storage.lazyInitCededClaimRoot(scaleFactorUltimateUnindexed);
        double cededPaidIncremental = grossClaim.getPaidIncrementalIndexed() * scaleFactorPaid;
        double cededReportedIncremental = grossClaim.getReportedIncrementalIndexed() * scaleFactorReported;
        storage.update(grossClaim.ultimate() * scaleFactorUltimateUnindexed, BasedOnClaimProperty.ULTIMATE_UNINDEXED);
        storage.update(grossClaim.totalIncrementalIndexed() * scaleFactorUltimateIndexed, BasedOnClaimProperty.ULTIMATE_INDEXED);
        storage.update(cededPaidIncremental, BasedOnClaimProperty.PAID);
        storage.update(cededReportedIncremental, BasedOnClaimProperty.REPORTED);
        ExposureInfo cededExposureInfo = adjustExposureInfo && grossClaim.getExposureInfo() != null
                ? grossClaim.getExposureInfo().withScale(scaleFactorUltimateUnindexed) : grossClaim.getExposureInfo();
        ClaimCashflowPacket cededClaim = new ClaimCashflowPacket(grossClaim, storage, cededExposureInfo);
        applyMarkers(grossClaim, cededClaim);
        return cededClaim;
    }

    public static ClaimCashflowPacket cededClaim(ClaimCashflowPacket grossClaim, ClaimStorage storage, double cededUltimateUnIndexed,
                                                 double cededUltimateCummulated, double cededReportedCummulated,
                                                 double cededPaidCummulated, boolean adjustExposureInfo) {
        double scaleFactorUltimate = cededUltimateUnIndexed / grossClaim.developedUltimate();
        storage.lazyInitCededClaimRoot(scaleFactorUltimate);
        storage.set(cededUltimateUnIndexed, BasedOnClaimProperty.ULTIMATE_UNINDEXED);
        storage.set(cededUltimateCummulated, BasedOnClaimProperty.ULTIMATE_INDEXED);
        storage.set(cededPaidCummulated, BasedOnClaimProperty.PAID);
        storage.set(cededReportedCummulated, BasedOnClaimProperty.REPORTED);
        ExposureInfo cededExposureInfo = adjustExposureInfo && grossClaim.getExposureInfo() != null
                ? grossClaim.getExposureInfo().withScale(scaleFactorUltimate) : grossClaim.getExposureInfo();
        ClaimCashflowPacket cededClaim = new ClaimCashflowPacket(grossClaim, storage, cededExposureInfo);
        applyMarkers(grossClaim, cededClaim);
        return cededClaim;
    }

    /**
     *
     *
     * @param grossClaim
     * @param storage
     * @param scaleFactorUltimateUnIndexed
     * @param cededValueReported
     * @param scaleFactorPaid
     * @param adjustExposureInfo
     * @param scaleFactorUltimateIndexed
     * @return
     */
    public static ClaimCashflowPacket getCededClaimReportedAbsolute(ClaimCashflowPacket grossClaim, ClaimStorage storage, double scaleFactorUltimateUnIndexed,
                                                                    double cededValueReported, double scaleFactorPaid, boolean adjustExposureInfo, double scaleFactorUltimateIndexed) {
        if (scaleFactorPaid == -0) {
            scaleFactorPaid = 0;
        }
        storage.lazyInitCededClaimRoot(scaleFactorUltimateUnIndexed);
        double cededPaidIncremental = grossClaim.getPaidIncrementalIndexed() * scaleFactorPaid;
        storage.update(grossClaim.ultimate() * scaleFactorUltimateUnIndexed, BasedOnClaimProperty.ULTIMATE_UNINDEXED);
        storage.update(grossClaim.totalIncrementalIndexed() * scaleFactorUltimateIndexed, BasedOnClaimProperty.ULTIMATE_UNINDEXED);
        storage.update(cededPaidIncremental, BasedOnClaimProperty.PAID);
        storage.update(cededValueReported, BasedOnClaimProperty.REPORTED);
        ExposureInfo cededExposureInfo = adjustExposureInfo && grossClaim.getExposureInfo() != null
                ? grossClaim.getExposureInfo().withScale(scaleFactorUltimateUnIndexed) : grossClaim.getExposureInfo();
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
        } else if (grossClaim == null) {
            DateTime occurrenceDate = cededClaim.getOccurrenceDate();
            IClaimRoot baseClaim = new ClaimRoot(0, ClaimType.AGGREGATED, occurrenceDate, occurrenceDate);
            return new ClaimCashflowPacket(baseClaim, 0, 0, 0, 0, 0, 0, 0, 0, 0, null, occurrenceDate, cededClaim.getUpdatePeriod());
        } else {
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
        List<ClaimCashflowPacket> cededClaimsWithMatchingGrossClaim = new ArrayList<ClaimCashflowPacket>();
        for (ClaimCashflowPacket cededClaim : claimsCeded) {
            aggregateCededClaimPerRoot.put(cededClaim.getKeyClaim(), cededClaim);
        }
        for (ClaimCashflowPacket grossClaim : claimsGross) {
            List<ClaimCashflowPacket> cededClaims = aggregateCededClaimPerRoot.get(grossClaim.getKeyClaim());
            cededClaimsWithMatchingGrossClaim.addAll(cededClaims);
            ClaimCashflowPacket aggregateCededClaim = sum(cededClaims, true);
            ClaimCashflowPacket netClaim = getNetClaim(grossClaim, aggregateCededClaim, null);
            claimsNet.add(netClaim);
        }
        if (cededClaimsWithMatchingGrossClaim.size() < claimsCeded.size()) {
            // this block is used for retroactive contracts providing aggregate reserves without valid reference to gross
            // claims
            List<ClaimCashflowPacket> extraCededClaims = new ArrayList<ClaimCashflowPacket>(claimsCeded);
            extraCededClaims.removeAll(cededClaimsWithMatchingGrossClaim);
            for (ClaimCashflowPacket reserveClaim : extraCededClaims) {
                if (!reserveClaim.getClaimType().isReserveClaim()) {
                    throw new SimulationException("Extra ceded claim found with wrong claim type " + reserveClaim);
                }
            }
            List<ClaimCashflowPacket> aggregateClaimsByBaseClaim = aggregateByBaseClaim(extraCededClaims);
            claimsNet.add(sum(aggregateClaimsByBaseClaim, true));
        }
        return claimsNet;
    }

    /**
     * Use this method with care: it's difference to normally sum method is that it takes special care for ultimate
     * calculation in the ctx of reserve claims.
     * @param developedClaim
     * @param newReserveClaim
     * @return
     */
    private static ClaimCashflowPacket sumClaimWithClaimConvertedToReserve(ClaimCashflowPacket developedClaim, ClaimCashflowPacket newReserveClaim) {
        double ultimate = developedClaim.developmentResultCumulative() + newReserveClaim.ultimate();
        double nominalUltimate = developedClaim.nominalUltimate() + newReserveClaim.nominalUltimate();
        double paidIncremental = developedClaim.getPaidIncrementalIndexed() + newReserveClaim.getPaidIncrementalIndexed();
        double paidCumulated = developedClaim.getPaidCumulatedIndexed() + newReserveClaim.getPaidCumulatedIndexed();
        double reportedIncremental = developedClaim.getReportedIncrementalIndexed() + newReserveClaim.getReportedIncrementalIndexed();
        double reportedCumulated = developedClaim.getReportedCumulatedIndexed() + newReserveClaim.getReportedCumulatedIndexed();
        double reserves = developedClaim.reservedIndexed() + newReserveClaim.reservedIndexed();
        double appliedIndex = developedClaim.getAppliedIndexValue();
        double changeInReservesIndexed = developedClaim.getChangeInReservesIndexed() + newReserveClaim.getChangeInReservesIndexed();
        double changeInIBNRIndexed = developedClaim.getChangeInIBNRIndexed() + newReserveClaim.getChangeInIBNRIndexed();
        double premiumRisk = developedClaim.getPremiumRisk() + newReserveClaim.getPremiumRisk();
        double reserveRisk = developedClaim.getReserveRisk() + newReserveClaim.getReserveRisk();

        ClaimRoot baseClaim = new ClaimRoot(ultimate, developedClaim.getBaseClaim());
        DateTime updateDate = developedClaim.getUpdateDate();
        int updatePeriod = 0;
        if (developedClaim.getUpdatePeriod() != null) {
            updatePeriod = developedClaim.getUpdatePeriod();
        }
        ClaimCashflowPacket summedClaims = new ClaimCashflowPacket(baseClaim, ultimate, nominalUltimate, paidIncremental,
                paidCumulated, reportedIncremental, reportedCumulated, reserves, changeInReservesIndexed,
                changeInIBNRIndexed, null, updateDate, updatePeriod);
        applyMarkers(developedClaim, summedClaims);
        summedClaims.setAppliedIndexValue(appliedIndex);
        summedClaims.setPremiumRisk(premiumRisk);
        summedClaims.setReserveRisk(reserveRisk);
        return summedClaims;
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
        } else {
            return getNetClaim(claimGross, claimCeded, claimCeded.reinsuranceContract());
        }
    }

    public static List<ClaimCashflowPacket> correctClaimSign(List<ClaimCashflowPacket> claims, boolean invertSign) {
        if (invertSign) {
            List<ClaimCashflowPacket> result = new ArrayList<ClaimCashflowPacket>();
            ClaimValidator claimValidator = new ClaimValidator();
            for (ClaimCashflowPacket claim : claims) {
                result.add(claimValidator.invertClaimSign(claim));
            }
            return result;
        } else {
            return ClaimValidator.positiveNominalUltimates(claims);
        }
    }

    /*
    This method really checks that we haven't been supplied with two dependance packets for a single generator. I.e that the user
    has tried to 'double correlate' something. Ideally it would be called before and dependant claims generation.
     */
    public static DependancePacket checkForDependance(IPerilMarker filterCriteria, List<DependancePacket> dependancePacketList) {
        DependancePacket dependancePacket = new DependancePacket();
        boolean foundPacket = false;
        for(DependancePacket aPacket : dependancePacketList  ) {
            if(aPacket.isDependantGenerator(filterCriteria)) {
                if(foundPacket) {
                    throw new SimulationException("Found two dependance packets which claim to have information for this claims generator: " + filterCriteria.getName()
                            + " Have you selected this generator twice in two different dependance components? This is not allowed. If that is not the case please contact development");
                }
                foundPacket = true;
                dependancePacket = aPacket;
            }
        }
        return dependancePacket;
    }


    public static boolean uniqueKeyClaims(List<ClaimCashflowPacket> claims) {
        Set<IClaimRoot> keyClaims = new HashSet<IClaimRoot>();
        for (ClaimCashflowPacket claim : claims) {
            keyClaims.add(claim.getKeyClaim());
        }
        return keyClaims.size() == claims.size();
    }
}
