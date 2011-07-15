package org.pillarone.riskanalytics.domain.pc.cf.segment;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.joda.time.DateTime;
import org.pillarone.riskanalytics.core.components.MultiPhaseComponent;
import org.pillarone.riskanalytics.core.components.PeriodStore;
import org.pillarone.riskanalytics.core.packets.PacketList;
import org.pillarone.riskanalytics.core.parameterization.ComboBoxTableMultiDimensionalParameter;
import org.pillarone.riskanalytics.core.parameterization.ConstrainedMultiDimensionalParameter;
import org.pillarone.riskanalytics.core.parameterization.ConstrainedString;
import org.pillarone.riskanalytics.core.parameterization.ConstraintsFactory;
import org.pillarone.riskanalytics.core.simulation.engine.IterationScope;
import org.pillarone.riskanalytics.core.simulation.engine.PeriodScope;
import org.pillarone.riskanalytics.core.util.GroovyUtils;
import org.pillarone.riskanalytics.domain.pc.cf.claim.*;
import org.pillarone.riskanalytics.domain.pc.cf.discounting.*;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.CededUnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoPacket;
import org.pillarone.riskanalytics.domain.pc.cf.exposure.UnderwritingInfoUtils;
import org.pillarone.riskanalytics.domain.pc.cf.indexing.Factors;
import org.pillarone.riskanalytics.domain.pc.cf.indexing.FactorsPacket;
import org.pillarone.riskanalytics.domain.utils.InputFormatConverter;
import org.pillarone.riskanalytics.domain.utils.constraint.PerilPortion;
import org.pillarone.riskanalytics.domain.utils.constraint.ReservePortion;
import org.pillarone.riskanalytics.domain.utils.constraint.UnderwritingPortion;
import org.pillarone.riskanalytics.domain.utils.marker.ISegmentMarker;
import org.pillarone.riskanalytics.domain.utils.marker.ILegalEntityMarker;

import java.util.*;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class Segment extends MultiPhaseComponent implements ISegmentMarker {

    private IterationScope iterationScope;
    private PeriodScope periodScope;
    private PeriodStore periodStore;
    private static final String NET_PRESENT_VALUE_GROSS = "netPresentValueGross";
    private static final String NET_PRESENT_VALUE_CEDED = "netPresentValueCeded";
    private static final String NET_PRESENT_VALUE_NET = "netPresentValueNet";

    private PacketList<ClaimCashflowPacket> inClaims = new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket.class);
    // todo(jwa): remove inReserves as soon as PMO-???? is solved (and collect within inClaims)
    private PacketList<ClaimCashflowPacket> inReserves = new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket.class);
    private PacketList<ClaimCashflowPacket> inClaimsCeded = new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket.class);
    private PacketList<UnderwritingInfoPacket> inUnderwritingInfo = new PacketList<UnderwritingInfoPacket>(UnderwritingInfoPacket.class);
    private PacketList<CededUnderwritingInfoPacket> inUnderwritingInfoCeded
            = new PacketList<CededUnderwritingInfoPacket>(CededUnderwritingInfoPacket.class);
    private PacketList<FactorsPacket> inFactors = new PacketList<FactorsPacket>(FactorsPacket.class);

    private PacketList<ClaimCashflowPacket> outClaimsGross = new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket.class);
    private PacketList<ClaimCashflowPacket> outClaimsNet = new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket.class);
    private PacketList<ClaimCashflowPacket> outClaimsCeded = new PacketList<ClaimCashflowPacket>(ClaimCashflowPacket.class);
    private PacketList<DiscountedValuesPacket> outDiscountedValues = new PacketList<DiscountedValuesPacket>(DiscountedValuesPacket.class);
    private PacketList<NetPresentValuesPacket> outNetPresentValues = new PacketList<NetPresentValuesPacket>(NetPresentValuesPacket.class);
    private PacketList<UnderwritingInfoPacket> outUnderwritingInfoGross = new PacketList<UnderwritingInfoPacket>(UnderwritingInfoPacket.class);
    private PacketList<UnderwritingInfoPacket> outUnderwritingInfoNet = new PacketList<UnderwritingInfoPacket>(UnderwritingInfoPacket.class);
    private PacketList<CededUnderwritingInfoPacket> outUnderwritingInfoCeded
            = new PacketList<CededUnderwritingInfoPacket>(CededUnderwritingInfoPacket.class);

    private ConstrainedString parmCompany = new ConstrainedString(ILegalEntityMarker.class, "");
    private ConstrainedMultiDimensionalParameter parmClaimsPortions = new ConstrainedMultiDimensionalParameter(
            GroovyUtils.toList("[[],[]]"), Arrays.asList(PERIL, PORTION), ConstraintsFactory.getConstraints(PerilPortion.IDENTIFIER));
    private ConstrainedMultiDimensionalParameter parmUnderwritingPortions = new ConstrainedMultiDimensionalParameter(
            GroovyUtils.toList("[[],[]]"), Arrays.asList(UNDERWRITING, PORTION),
            ConstraintsFactory.getConstraints(UnderwritingPortion.IDENTIFIER));
    private ConstrainedMultiDimensionalParameter parmReservesPortions = new ConstrainedMultiDimensionalParameter(
            GroovyUtils.toList("[[],[]]"), Arrays.asList(RESERVE, PORTION), ConstraintsFactory.getConstraints(ReservePortion.IDENTIFIER));
    private ComboBoxTableMultiDimensionalParameter parmDiscounting = new ComboBoxTableMultiDimensionalParameter(
            Arrays.asList(""), Arrays.asList("Discount Index"), IDiscountMarker.class);


    private static final String PERIL = "Claims Generator";
    private static final String RESERVE = "Reserves Generator";
    private static final String UNDERWRITING = "Underwriting";
    private static final String PORTION = "Portion";

    private static final String PHASE_GROSS = "Phase Gross";
    private static final String PHASE_NET = "Phase Net";
    private List<Factors> filteredFactors;
    private double discountedIncrementalPaidGross;
    private double discountedReservedGross;
    private double netPresentValueGross;

    @Override
    public void doCalculation(String phase) {
        if (phase.equals(PHASE_GROSS)) {
            filteredFactors = DiscountUtils.filterFactors(inFactors, parmDiscounting, periodScope.getPeriodCounter().startOfFirstPeriod());
            getSegmentClaims();
            getSegmentReserves();
            getSegmentUnderwritingInfo();

            discountedIncrementalPaidGross = getSumOfDiscountedIncrementalPaids(outClaimsGross);
            discountedReservedGross = getDiscountedReservedAtEndOfCurrentPeriod(outClaimsGross);
            double startNetPresentValueGross = getPeriodStore().exists(NET_PRESENT_VALUE_GROSS) ? (Double) getPeriodStore().get(NET_PRESENT_VALUE_GROSS, -1) : 0d;
            double endNetPresentValueGross = startNetPresentValueGross + discountedIncrementalPaidGross;
            periodStore.put(NET_PRESENT_VALUE_GROSS, endNetPresentValueGross);
        }
        else if (phase.equals(PHASE_NET)) {
            filterCededClaims();
            calculateNetClaims();
            filterCededUnderwritingInfo();
            calculateNetUnderwritingInfo();

            double discountedIncrementalPaidCeded = getSumOfDiscountedIncrementalPaids(outClaimsCeded);
            double discountedIncrementalPaidNet = getSumOfDiscountedIncrementalPaids(outClaimsNet);
            double discountedReservedCeded = getDiscountedReservedAtEndOfCurrentPeriod(outClaimsCeded);
            double discountedReservedNet = getDiscountedReservedAtEndOfCurrentPeriod(outClaimsNet);
            double startNetPresentValueCeded = getPeriodStore().exists(NET_PRESENT_VALUE_CEDED) ? (Double) getPeriodStore().get(NET_PRESENT_VALUE_CEDED, -1) : 0d;
            double startNetPresentValueNet = getPeriodStore().exists(NET_PRESENT_VALUE_NET) ? (Double) getPeriodStore().get(NET_PRESENT_VALUE_NET, -1) : 0d;
            double endNetPresentValueCeded = startNetPresentValueCeded + discountedIncrementalPaidCeded;
            double endNetPresentValueNet = startNetPresentValueNet + discountedIncrementalPaidNet;
            periodStore.put(NET_PRESENT_VALUE_CEDED, endNetPresentValueCeded);
            periodStore.put(NET_PRESENT_VALUE_NET, endNetPresentValueNet);
            DiscountedValuesPacket discountedValues = new DiscountedValuesPacket();
            discountedValues.setDiscountedPaidIncrementalGross(discountedIncrementalPaidGross);
            discountedValues.setDiscountedPaidIncrementalCeded(discountedIncrementalPaidCeded);
            discountedValues.setDiscountedPaidIncrementalNet(discountedIncrementalPaidNet);
            discountedValues.setDiscountedReservedGross(discountedReservedGross);
            discountedValues.setDiscountedReservedCeded(discountedReservedCeded);
            discountedValues.setDiscountedReservedNet(discountedReservedNet);
            outDiscountedValues.add(discountedValues);

            // todo(sku): simplify once PMO-1071 is resolved
            int period = periodScope.getCurrentPeriod();
            if (period + 1 == iterationScope.getNumberOfPeriods()) {
                NetPresentValuesPacket netPresentValues = new NetPresentValuesPacket();
                netPresentValues.setNetPresentValueGross((Double) periodStore.get(NET_PRESENT_VALUE_GROSS, 0));
                netPresentValues.setNetPresentValueCeded(endNetPresentValueCeded);
                netPresentValues.setNetPresentValueNet(endNetPresentValueNet);
                netPresentValues.period = 0;
                outNetPresentValues.add(netPresentValues);
            }
            /*     else if (period == 0) {
              netPresentValues.period = getIterationScope().getNumberOfPeriods() - 1;
          }
          outNetPresentValues.add(netPresentValues);  */
        }
    }

    private void calculateNetUnderwritingInfo() {
        ListMultimap<UnderwritingInfoPacket, CededUnderwritingInfoPacket> aggregateCededUnderwritingInfos
                = ArrayListMultimap.create();
        if (isSenderWired(outUnderwritingInfoNet)) {
            for (CededUnderwritingInfoPacket cededUnderwritingInfo : inUnderwritingInfoCeded) {
                aggregateCededUnderwritingInfos.put(cededUnderwritingInfo.getOriginal(), cededUnderwritingInfo);
            }
            for (UnderwritingInfoPacket grossUwInfo : outUnderwritingInfoGross) {
                List<CededUnderwritingInfoPacket> cededUnderwritingInfoPackets = aggregateCededUnderwritingInfos.get(grossUwInfo);
                CededUnderwritingInfoPacket aggregateCededUwInfo = UnderwritingInfoUtils.aggregate(cededUnderwritingInfoPackets);
                UnderwritingInfoPacket netUwInfo = grossUwInfo.getNet(aggregateCededUwInfo, true);
                outUnderwritingInfoNet.add(netUwInfo);
            }
        }
    }

    private void calculateNetClaims() {
        ListMultimap<IClaimRoot, ClaimCashflowPacket> aggregateCededClaimPerRoot = ArrayListMultimap.create();
        if (isSenderWired(outClaimsNet)) {
            for (ClaimCashflowPacket cededClaim : inClaimsCeded) {
                aggregateCededClaimPerRoot.put(cededClaim.getBaseClaim(), (ClaimCashflowPacket) cededClaim.copy());
            }
            for (ClaimCashflowPacket grossClaim : outClaimsGross) {
                List<ClaimCashflowPacket> cededClaims = aggregateCededClaimPerRoot.get(grossClaim.getBaseClaim());
                ClaimCashflowPacket aggregateCededClaim = ClaimUtils.sum(cededClaims, true);
                ClaimCashflowPacket netClaim = ClaimUtils.getNetClaim(grossClaim, aggregateCededClaim);
                outClaimsNet.add(netClaim);
            }
        }
    }

    private void filterCededUnderwritingInfo() {
        for (CededUnderwritingInfoPacket underwritingInfo : inUnderwritingInfoCeded) {
            if (underwritingInfo.segment().equals(this)) {
                outUnderwritingInfoCeded.add(underwritingInfo);
            }
        }
    }

    private void filterCededClaims() {
        for (ClaimCashflowPacket claim : inClaimsCeded) {
            if (claim.segment().equals(this)) {
                outClaimsCeded.add(claim);
            }
        }
    }

    private void getSegmentUnderwritingInfo() {
        if (inUnderwritingInfo.size() > 0) {
            List<UnderwritingInfoPacket> lobUnderwritingInfos = new ArrayList<UnderwritingInfoPacket>();
            int portionColumn = parmUnderwritingPortions.getColumnIndex(PORTION);
            for (UnderwritingInfoPacket underwritingInfo : inUnderwritingInfo) {
                String originName = underwritingInfo.riskBand().getNormalizedName();
                int row = parmUnderwritingPortions.getColumnByName(UNDERWRITING).indexOf(originName);
                if (row > -1) {
                    UnderwritingInfoPacket lobUnderwritingInfo = (UnderwritingInfoPacket) underwritingInfo.copy();
                    double segmentPortion = InputFormatConverter.getDouble(parmUnderwritingPortions.getValueAt(row + 1, portionColumn));
                    lobUnderwritingInfo.setPremiumWritten(lobUnderwritingInfo.getPremiumWritten() * segmentPortion);
                    lobUnderwritingInfo.setPremiumPaid(lobUnderwritingInfo.getPremiumPaid() * segmentPortion);
                    lobUnderwritingInfo.setSumInsured(lobUnderwritingInfo.getSumInsured() * segmentPortion);
                    lobUnderwritingInfo.setMaxSumInsured(lobUnderwritingInfo.getMaxSumInsured() * segmentPortion);
                    lobUnderwritingInfo.origin = this;
                    lobUnderwritingInfo.setSegment(this);
                    lobUnderwritingInfos.add(lobUnderwritingInfo);
                }
            }
            outUnderwritingInfoGross.addAll(lobUnderwritingInfos);
        }
    }

    private void getSegmentClaims() {
        if (inClaims.size() > 0) {
            List<ClaimCashflowPacket> segmentClaims = new ArrayList<ClaimCashflowPacket>();
            int portionColumn = parmClaimsPortions.getColumnIndex(PORTION);
            for (ClaimCashflowPacket marketClaim : inClaims) {
                // todo(jwa): if-statement needed as soon as reserves contained within inClaims
                if (marketClaim.peril() != null) {
                    String originName = marketClaim.peril().getNormalizedName();
                    int row = parmClaimsPortions.getColumnByName(PERIL).indexOf(originName);
                    if (row > -1) {
                        ClaimCashflowPacket segmentClaim = (ClaimCashflowPacket) marketClaim.copy();
                        // PMO-750: claim mergers in reinsurance program won't work with reference to market claims
                        segmentClaim.origin = this;
                        segmentClaim.setMarker(this);
                        double scaleFactor = InputFormatConverter.getDouble(parmClaimsPortions.getValueAt(row + 1, portionColumn));
                        segmentClaims.add(ClaimUtils.scale(segmentClaim, scaleFactor, true));
                    }
                }
            }
            outClaimsGross.addAll(segmentClaims);
        }
    }

    private void getSegmentReserves() {
        if (inReserves.size() > 0) {
            List<ClaimCashflowPacket> segmentReserves = new ArrayList<ClaimCashflowPacket>();
            int portionColumn = parmReservesPortions.getColumnIndex(PORTION);
            for (ClaimCashflowPacket marketClaim : inReserves) {
                // todo(jwa): if-statement needed as soon as reserves contained within inClaims
                if (marketClaim.reserve() != null) {
                    String originName = marketClaim.reserve().getNormalizedName();
                    int row = parmReservesPortions.getColumnByName(RESERVE).indexOf(originName);
                    if (row > -1) {
                        ClaimCashflowPacket segmentReserve = (ClaimCashflowPacket) marketClaim.copy();
                        // PMO-750: claim mergers in reinsurance program won't work with reference to market claims
                        segmentReserve.origin = this;
                        segmentReserve.setMarker(this);
                        double scaleFactor = InputFormatConverter.getDouble(parmReservesPortions.getValueAt(row + 1, portionColumn));
                        segmentReserves.add(ClaimUtils.scale(segmentReserve, scaleFactor, true));
                    }
                }
            }
            outClaimsGross.addAll(segmentReserves);
        }
    }

    private double getSumOfDiscountedIncrementalPaids(List<ClaimCashflowPacket> claims) {
        double discountedIncrementalPaid = 0d;
        for (ClaimCashflowPacket claim : claims) {
            DateTime date = claim.getUpdateDate();
            double discountFactor = DiscountUtils.getDiscountFactor(filteredFactors, date, periodScope.getPeriodCounter());
            discountedIncrementalPaid += claim.getPaidIncremental() * discountFactor;
        }
        return discountedIncrementalPaid;
    }

    private double getDiscountedReservedAtEndOfCurrentPeriod(List<ClaimCashflowPacket> claims) {
        Map<IClaimRoot, ClaimCashflowPacket> latestCashflowPerBaseClaim = new HashMap<IClaimRoot, ClaimCashflowPacket>();
        for (ClaimCashflowPacket claim : claims) {
            ClaimCashflowPacket latestCashflow = latestCashflowPerBaseClaim.get(claim.getBaseClaim());
            if (latestCashflow == null || claim.getUpdateDate().isAfter(latestCashflow.getUpdateDate()))
            {
                latestCashflow = claim;
                latestCashflowPerBaseClaim.put(claim.getBaseClaim(), latestCashflow);
            }
        }
        double discountedReserved = 0d;
        for (ClaimCashflowPacket claim : latestCashflowPerBaseClaim.values()) {
            DateTime date = claim.getUpdateDate();
            double discountFactor = DiscountUtils.getDiscountFactor(filteredFactors, date, periodScope.getPeriodCounter());
            discountedReserved += claim.reserved() * discountFactor;
        }
        return discountedReserved;
    }

    private double getDiscountedReserves(List<ClaimCashflowPacket> claims) {
        // note: per claim only last dated reserve
        return 0d;
    }

    public void allocateChannelsToPhases() {
        setTransmitterPhaseInput(inClaims, PHASE_GROSS);
        setTransmitterPhaseInput(inReserves, PHASE_GROSS);
        setTransmitterPhaseInput(inUnderwritingInfo, PHASE_GROSS);
        setTransmitterPhaseInput(inFactors, PHASE_GROSS);
        setTransmitterPhaseOutput(outClaimsGross, PHASE_GROSS);
        setTransmitterPhaseOutput(outUnderwritingInfoGross, PHASE_GROSS);

        setTransmitterPhaseInput(inClaimsCeded, PHASE_NET);
        setTransmitterPhaseInput(inUnderwritingInfoCeded, PHASE_NET);
        setTransmitterPhaseOutput(outClaimsNet, PHASE_NET);
        setTransmitterPhaseOutput(outClaimsCeded, PHASE_NET);
        setTransmitterPhaseOutput(outDiscountedValues, PHASE_NET);
        setTransmitterPhaseOutput(outNetPresentValues, PHASE_NET);
        setTransmitterPhaseOutput(outUnderwritingInfoNet, PHASE_NET);
        setTransmitterPhaseOutput(outUnderwritingInfoCeded, PHASE_NET);
    }

    @Override
    public String toString() {
        return getNormalizedName();
    }

    public PacketList<ClaimCashflowPacket> getInClaims() {
        return inClaims;
    }

    public void setInClaims(PacketList<ClaimCashflowPacket> inClaims) {
        this.inClaims = inClaims;
    }

    public PacketList<ClaimCashflowPacket> getInClaimsCeded() {
        return inClaimsCeded;
    }

    public void setInClaimsCeded(PacketList<ClaimCashflowPacket> inClaimsCeded) {
        this.inClaimsCeded = inClaimsCeded;
    }

    public PacketList<UnderwritingInfoPacket> getInUnderwritingInfo() {
        return inUnderwritingInfo;
    }

    public void setInUnderwritingInfo(PacketList<UnderwritingInfoPacket> inUnderwritingInfo) {
        this.inUnderwritingInfo = inUnderwritingInfo;
    }

    public PacketList<CededUnderwritingInfoPacket> getInUnderwritingInfoCeded() {
        return inUnderwritingInfoCeded;
    }

    public void setInUnderwritingInfoCeded(PacketList<CededUnderwritingInfoPacket> inUnderwritingInfoCeded) {
        this.inUnderwritingInfoCeded = inUnderwritingInfoCeded;
    }

    public PacketList<ClaimCashflowPacket> getOutClaimsGross() {
        return outClaimsGross;
    }

    public void setOutClaimsGross(PacketList<ClaimCashflowPacket> outClaimsGross) {
        this.outClaimsGross = outClaimsGross;
    }

    public PacketList<ClaimCashflowPacket> getOutClaimsNet() {
        return outClaimsNet;
    }

    public void setOutClaimsNet(PacketList<ClaimCashflowPacket> outClaimsNet) {
        this.outClaimsNet = outClaimsNet;
    }

    public PacketList<ClaimCashflowPacket> getOutClaimsCeded() {
        return outClaimsCeded;
    }

    public void setOutClaimsCeded(PacketList<ClaimCashflowPacket> outClaimsCeded) {
        this.outClaimsCeded = outClaimsCeded;
    }

    public PacketList<UnderwritingInfoPacket> getOutUnderwritingInfoGross() {
        return outUnderwritingInfoGross;
    }

    public void setOutUnderwritingInfoGross(PacketList<UnderwritingInfoPacket> outUnderwritingInfoGross) {
        this.outUnderwritingInfoGross = outUnderwritingInfoGross;
    }

    public PacketList<UnderwritingInfoPacket> getOutUnderwritingInfoNet() {
        return outUnderwritingInfoNet;
    }

    public void setOutUnderwritingInfoNet(PacketList<UnderwritingInfoPacket> outUnderwritingInfoNet) {
        this.outUnderwritingInfoNet = outUnderwritingInfoNet;
    }

    public PacketList<CededUnderwritingInfoPacket> getOutUnderwritingInfoCeded() {
        return outUnderwritingInfoCeded;
    }

    public void setOutUnderwritingInfoCeded(PacketList<CededUnderwritingInfoPacket> outUnderwritingInfoCeded) {
        this.outUnderwritingInfoCeded = outUnderwritingInfoCeded;
    }

    public ConstrainedMultiDimensionalParameter getParmClaimsPortions() {
        return parmClaimsPortions;
    }

    public void setParmClaimsPortions(ConstrainedMultiDimensionalParameter parmClaimsPortions) {
        this.parmClaimsPortions = parmClaimsPortions;
    }

    public ConstrainedMultiDimensionalParameter getParmUnderwritingPortions() {
        return parmUnderwritingPortions;
    }

    public void setParmUnderwritingPortions(ConstrainedMultiDimensionalParameter parmUnderwritingPortions) {
        this.parmUnderwritingPortions = parmUnderwritingPortions;
    }

    public ConstrainedString getParmCompany() {
        return parmCompany;
    }

    public void setParmCompany(ConstrainedString parmCompany) {
        this.parmCompany = parmCompany;
    }

    public ConstrainedMultiDimensionalParameter getParmReservesPortions() {
        return parmReservesPortions;
    }

    public void setParmReservesPortions(ConstrainedMultiDimensionalParameter parmReservesPortions) {
        this.parmReservesPortions = parmReservesPortions;
    }

    public PacketList<ClaimCashflowPacket> getInReserves() {
        return inReserves;
    }

    public void setInReserves(PacketList<ClaimCashflowPacket> inReserves) {
        this.inReserves = inReserves;
    }

    public ComboBoxTableMultiDimensionalParameter getParmDiscounting() {
        return parmDiscounting;
    }

    public void setParmDiscounting(ComboBoxTableMultiDimensionalParameter parmDiscounting) {
        this.parmDiscounting = parmDiscounting;
    }

    public PacketList<FactorsPacket> getInFactors() {
        return inFactors;
    }

    public void setInFactors(PacketList<FactorsPacket> inFactors) {
        this.inFactors = inFactors;
    }

    public PeriodScope getPeriodScope() {
        return periodScope;
    }

    public void setPeriodScope(PeriodScope periodScope) {
        this.periodScope = periodScope;
    }

    public PacketList<DiscountedValuesPacket> getOutDiscountedValues() {
        return outDiscountedValues;
    }

    public void setOutDiscountedValues(PacketList<DiscountedValuesPacket> outDiscountedValues) {
        this.outDiscountedValues = outDiscountedValues;
    }

    public PacketList<NetPresentValuesPacket> getOutNetPresentValues() {
        return outNetPresentValues;
    }

    public void setOutNetPresentValues(PacketList<NetPresentValuesPacket> outNetPresentValues) {
        this.outNetPresentValues = outNetPresentValues;
    }

    public PeriodStore getPeriodStore() {
        return periodStore;
    }

    public void setPeriodStore(PeriodStore periodStore) {
        this.periodStore = periodStore;
    }

    public IterationScope getIterationScope() {
        return iterationScope;
    }

    public void setIterationScope(IterationScope iterationScope) {
        this.iterationScope = iterationScope;
    }
}
