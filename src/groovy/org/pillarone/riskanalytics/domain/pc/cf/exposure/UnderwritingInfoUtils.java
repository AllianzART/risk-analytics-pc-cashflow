package org.pillarone.riskanalytics.domain.pc.cf.exposure;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.pillarone.riskanalytics.domain.pc.cf.claim.ClaimCashflowPacket;
import org.pillarone.riskanalytics.domain.utils.marker.IReinsuranceContractMarker;
import org.pillarone.riskanalytics.domain.utils.marker.ISegmentMarker;

import java.util.ArrayList;
import java.util.List;

/**
 * @author stefan.kunz (at) intuitive-collaboration (dot) com
 */
public class UnderwritingInfoUtils {

    /**
     * @param underwritingInfos underwriting info packets to be filtered
     * @param coverCriteria     components such as RiskBands
     * @return an underwriting info packet is added to the list of filtered underwriting info packets if the
     *         originalUnderwritingInfo references an element of the cover criteria. If it didn't match or
     *         originalUnderwritingInfo property is null, the origin property is evaluated.
     */
    public static List<UnderwritingInfoPacket> filterUnderwritingInfo(List<UnderwritingInfoPacket> underwritingInfos, List coverCriteria) {
        List<UnderwritingInfoPacket> filterUnderwritingInfos = new ArrayList<UnderwritingInfoPacket>(underwritingInfos.size());
        if (coverCriteria != null) {
            for (UnderwritingInfoPacket underwritingInfo : underwritingInfos) {
                if (underwritingInfo.getOriginal() != null
                        && coverCriteria.contains(underwritingInfo.getOriginal())
                        || coverCriteria.contains(underwritingInfo.origin)) {
                    filterUnderwritingInfos.add(underwritingInfo);
                }
            }
        }
        return filterUnderwritingInfos;
    }

    private static double scalingFactor(List<UnderwritingInfoPacket> underwritingInfos, ExposureBase base) {
        if (base.equals(ExposureBase.ABSOLUTE)) return 1;
        double factor = 0d;
        for (UnderwritingInfoPacket underwritingInfo : underwritingInfos) {
            factor += underwritingInfo.scaleValue(base);
        }
        return factor;
    }

    private static double scalingFactor(List<UnderwritingInfoPacket> underwritingInfos, FrequencyBase base) {
        if (base.equals(FrequencyBase.ABSOLUTE)) return 1;
        double factor = 0d;
        for (UnderwritingInfoPacket underwritingInfo : underwritingInfos) {
            factor += underwritingInfo.scaleValue(base);
        }
        return factor;
    }

    public static double scalingFactor(List<UnderwritingInfoPacket> underwritingInfos, ExposureBase base, List coverCriteria) {
        if (underwritingInfos.isEmpty() || coverCriteria.isEmpty() || base.equals(ExposureBase.ABSOLUTE)) return 1d;
        return scalingFactor(filterUnderwritingInfo(underwritingInfos, coverCriteria), base);
    }

    public static double scalingFactor(List<UnderwritingInfoPacket> underwritingInfos, FrequencyBase base, List coverCriteria) {
        if (underwritingInfos.isEmpty() || coverCriteria.isEmpty() || base.equals(FrequencyBase.ABSOLUTE)) return 1d;
        return scalingFactor(filterUnderwritingInfo(underwritingInfos, coverCriteria), base);
    }

    static public UnderwritingInfoPacket aggregate(List<UnderwritingInfoPacket> underwritingInfos) {
        if (underwritingInfos == null || underwritingInfos.size() == 0) {
            return null;
        }
        UnderwritingInfoPacket summedUnderwritingInfo = new UnderwritingInfoPacket();
        for (UnderwritingInfoPacket underwritingInfo : underwritingInfos) {
            summedUnderwritingInfo.plus(underwritingInfo);
            summedUnderwritingInfo.setExposure(underwritingInfo.getExposure());
        }
        return correctMetaProperties(summedUnderwritingInfo, underwritingInfos);
    }

    static public CededUnderwritingInfoPacket aggregateCeded(List<CededUnderwritingInfoPacket> underwritingInfos) {
        if (underwritingInfos == null || underwritingInfos.size() == 0) {
            return null;
        }
        CededUnderwritingInfoPacket summedUnderwritingInfo = new CededUnderwritingInfoPacket();
        for (CededUnderwritingInfoPacket underwritingInfo : underwritingInfos) {
            summedUnderwritingInfo.plus(underwritingInfo);
            summedUnderwritingInfo.setExposure(underwritingInfo.getExposure());
        }
        return correctMetaProperties(summedUnderwritingInfo, underwritingInfos);
    }

    static public UnderwritingInfoPacket correctMetaProperties(UnderwritingInfoPacket result, List<UnderwritingInfoPacket> underwritingInfos) {
        UnderwritingInfoPacket verifiedResult = (UnderwritingInfoPacket) result.clone();
        ISegmentMarker lob = verifiedResult.segment();
        IReinsuranceContractMarker reinsuranceContract = verifiedResult.reinsuranceContract();
        boolean underwritingInfosOfDifferentLobs = lob == null;
        boolean underwritingInfosOfDifferentContracts = reinsuranceContract == null;
        for (UnderwritingInfoPacket underwritingInfo : underwritingInfos) {
            if (!underwritingInfosOfDifferentLobs && !lob.equals(underwritingInfo.segment())) {
                underwritingInfosOfDifferentLobs = true;
            }
            if (!underwritingInfosOfDifferentContracts && !reinsuranceContract.equals(underwritingInfo.reinsuranceContract())) {
                underwritingInfosOfDifferentContracts = true;
            }
        }
        if (underwritingInfosOfDifferentLobs) {
            verifiedResult.setSegment(null);
        }
        if (underwritingInfosOfDifferentContracts) {
            verifiedResult.setReinsuranceContract(null);
        }
        return verifiedResult;
    }

    static public CededUnderwritingInfoPacket correctMetaProperties(CededUnderwritingInfoPacket result, List<CededUnderwritingInfoPacket> underwritingInfos) {
        CededUnderwritingInfoPacket verifiedResult = (CededUnderwritingInfoPacket) result.clone();
        ISegmentMarker lob = verifiedResult.segment();
        IReinsuranceContractMarker reinsuranceContract = verifiedResult.reinsuranceContract();
        boolean underwritingInfosOfDifferentLobs = lob == null;
        boolean underwritingInfosOfDifferentContracts = reinsuranceContract == null;
        for (UnderwritingInfoPacket underwritingInfo : underwritingInfos) {
            if (!underwritingInfosOfDifferentLobs && !lob.equals(underwritingInfo.segment())) {
                underwritingInfosOfDifferentLobs = true;
            }
            if (!underwritingInfosOfDifferentContracts && !reinsuranceContract.equals(underwritingInfo.reinsuranceContract())) {
                underwritingInfosOfDifferentContracts = true;
            }
        }
        if (underwritingInfosOfDifferentLobs) {
            verifiedResult.setSegment(null);
        }
        if (underwritingInfosOfDifferentContracts) {
            verifiedResult.setReinsuranceContract(null);
        }
        return verifiedResult;
    }

    /**
     * @param underwritingInfos        the list of underwritingInfo packets to filter
     * @param contracts                the contract markers to filter by, if any; null means no filtering (all are accepted)
     * @param acceptedUnderwritingInfo the list of underwritingInfo packets whose contract is listed in contracts
     * @param rejectedUnderwritingInfo (if not null) the remaining underwritingInfo packets that were filtered out
     */
    public static void segregateUnderwritingInfoByContract(List<CededUnderwritingInfoPacket> underwritingInfos,
                                                           List<IReinsuranceContractMarker> contracts,
                                                           List<CededUnderwritingInfoPacket> acceptedUnderwritingInfo,
                                                           List<CededUnderwritingInfoPacket> rejectedUnderwritingInfo) {
        if (contracts == null || contracts.size() == 0) {
            acceptedUnderwritingInfo.addAll(underwritingInfos);
        }
        else {
            for (CededUnderwritingInfoPacket underwritingInfo : underwritingInfos) {
                if (contracts.contains(underwritingInfo.reinsuranceContract())) {
                    acceptedUnderwritingInfo.add(underwritingInfo);
                }
                else if (rejectedUnderwritingInfo != null) {
                    rejectedUnderwritingInfo.add(underwritingInfo);
                }
            }
        }
    }

    public static double sumPremiumWritten(List<UnderwritingInfoPacket> underwritingInfos) {
        double premiumWritten = 0;
        for (UnderwritingInfoPacket packet : underwritingInfos) {
            premiumWritten += packet.getPremiumWritten();
        }
        return premiumWritten;
    }

    public static double sumNumberOfPolicies(List<UnderwritingInfoPacket> underwritingInfos) {
        double policies = 0;
        for (UnderwritingInfoPacket packet : underwritingInfos) {
            policies += packet.getNumberOfPolicies();
        }
        return policies;
    }

    public static void applyMarkers(UnderwritingInfoPacket source, UnderwritingInfoPacket target) {
        target.setMarker(source.riskBand());
        target.setMarker(source.segment());
        target.setMarker(source.reinsuranceContract());
        target.setMarker(source.legalEntity());
    }

    public static List<UnderwritingInfoPacket> calculateNetUnderwritingInfo(List<UnderwritingInfoPacket> underwritingInfoGross,
                                                                            List<CededUnderwritingInfoPacket> underwritingInfoCeded) {
        List<UnderwritingInfoPacket> underwritingInfoNet = new ArrayList<UnderwritingInfoPacket>();
        ListMultimap<UnderwritingInfoPacket, CededUnderwritingInfoPacket> aggregateCededUnderwritingInfos
                = ArrayListMultimap.create();
        for (CededUnderwritingInfoPacket cededUnderwritingInfo : underwritingInfoCeded) {
            aggregateCededUnderwritingInfos.put(cededUnderwritingInfo.getOriginal(), cededUnderwritingInfo);
        }
        for (UnderwritingInfoPacket grossUwInfo : underwritingInfoGross) {
            List<CededUnderwritingInfoPacket> cededUnderwritingInfoPackets = aggregateCededUnderwritingInfos.get(grossUwInfo);
            CededUnderwritingInfoPacket aggregateCededUwInfo = aggregateCeded(cededUnderwritingInfoPackets);
            UnderwritingInfoPacket netUwInfo = grossUwInfo.getNet(aggregateCededUwInfo, true);
            underwritingInfoNet.add(netUwInfo);
        }
        return underwritingInfoNet;
    }

}
