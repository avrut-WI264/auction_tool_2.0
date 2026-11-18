package com.wissen.auction.auction.application;

import com.wissen.auction.auction.BidLogDTO;
import com.wissen.auction.auction.BidLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionHistoryService {
    private final BidLogRepository bids;

    public List<BidLogDTO> findByCity(String city) {
        return bids.findByLocationIgnoreCaseOrderByCreatedAtDesc(city).stream().map(BidLogDTO::from).toList();
    }
}
