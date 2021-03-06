package info.bitrich.xchangestream.binance;

import com.fasterxml.jackson.databind.JsonNode;

import info.bitrich.xchangestream.binance.dto.BaseBinanceWebSocketTransaction.BinanceWebSocketTypes;
import info.bitrich.xchangestream.service.netty.JsonNettyStreamingService;

import io.reactivex.Observable;

import java.io.IOException;

public class BinanceUserDataStreamingService extends JsonNettyStreamingService {

	private static final String USER_API_BASE_URI = "wss://stream.binance.com:9443/ws/";

    public static BinanceUserDataStreamingService create(String listenKey) {
        return new BinanceUserDataStreamingService(USER_API_BASE_URI + listenKey);
    }

    private BinanceUserDataStreamingService(String url) {
        super(url, Integer.MAX_VALUE);
    }

    public Observable<JsonNode> subscribeChannel(BinanceWebSocketTypes eventType) {
    	return super.subscribeChannel(eventType.getSerializedValue());
    }

    @Override
    public void messageHandler(String message) {
        super.messageHandler(message);
    }

    @Override
    protected void handleMessage(JsonNode message) {
        super.handleMessage(message);
    }

    @Override
    protected String getChannelNameFromMessage(JsonNode message) throws IOException {
        return message.get("e").asText();
    }

    @Override
    public String getSubscribeMessage(String channelName, Object... args) throws IOException {
        // No op. Disconnecting from the web socket will cancel subscriptions.
        return null;
    }

    @Override
    public String getUnsubscribeMessage(String channelName) throws IOException {
        // No op. Disconnecting from the web socket will cancel subscriptions.
        return null;
    }

    @Override
    public void sendMessage(String message) {
        // Subscriptions are made upon connection - no messages are sent.
    }
}
