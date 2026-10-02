package com.virtualinvestor.edge.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.virtualinvestor.core.book.DepthLevel;
import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.NewsStage;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentType;
import com.virtualinvestor.core.participant.Account;
import com.virtualinvestor.core.participant.Position;
import com.virtualinvestor.core.participant.Standing;
import com.virtualinvestor.edge.wire.WireMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class WireMapperTest {

    @Test
    void instrumentMapsToSymbolDto() {
        Instrument instrument = new Instrument("NVDO", "Nvido", "Semis", InstrumentType.EQUITY,
                1L, 1.0, 0.01, 1L, 100.0, 0.3, 1.0, 1.0);
        WireMessage.SymbolDto dto = WireMapper.symbol(instrument);
        assertEquals("NVDO", dto.ticker());
        assertEquals("Equity", dto.assetClass());
    }

    @Test
    void standingAccountAndPositionMap() {
        WireMessage.LeaderboardEntryDto standing = WireMapper.standing(
                new Standing(2, "rival-1", "MARK", 110_000.0, 100_000.0, 0.10));
        assertEquals(2, standing.rank());
        assertEquals(0.10, standing.returnFraction(), 1e-9);

        Account account = new Account("h1", 100_000.0);
        assertEquals(100_000.0, WireMapper.account(account).cash(), 0.0);

        WireMessage.PositionDto position = WireMapper.position("h1", new Position("ACME", 100L, 99.5));
        assertEquals(100L, position.netQty());
        assertEquals(99.5, position.avgPrice(), 0.0);
    }

    @Test
    void tradeEventMapsToTradeDtoAndOthersReturnNull() {
        GameEvent trade = new GameEvent.TradePrinted("ACME", 100.0, 10L, OrderSide.BUY, 5L);
        WireMessage.Payload mapped = WireMapper.event(trade);
        WireMessage.TradeDto dto = assertInstanceOf(WireMessage.TradeDto.class, mapped);
        assertEquals(100.0, dto.price(), 0.0);
        assertEquals("BUY", dto.aggressor());

        assertNull(WireMapper.event(new GameEvent.Tick(1L, 100L)));
    }

    @Test
    void newsEventMapsToNewsDto() {
        GameEvent news = new GameEvent.NewsReleased("n1", "THE_WIRE", NewsScope.MACRO, null, null,
                "Data beats", "body", 0.01, 300L, NewsStage.RUMOR, 5L);
        WireMessage.NewsDto dto = assertInstanceOf(WireMessage.NewsDto.class, WireMapper.event(news));
        assertEquals("THE_WIRE", dto.source());
        assertEquals("RUMOR", dto.stage());
    }

    @Test
    void depthMapsLevels() {
        WireMessage.DepthDto depth = WireMapper.depth("ACME",
                List.of(new DepthLevel(99.9, 300L, 2)),
                List.of(new DepthLevel(100.1, 250L, 1)), 100.0, 7L);
        assertEquals(1, depth.bids().size());
        assertEquals(300L, depth.bids().get(0).qty());
        assertEquals(100.0, depth.ltp(), 0.0);
    }
}
