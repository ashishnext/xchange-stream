package info.bitrich.xchangestream.binance;

import info.bitrich.xchangestream.binance.BinanceUserDataChannel.NoActiveChannelException;
import info.bitrich.xchangestream.core.ProductSubscription;
import info.bitrich.xchangestream.core.StreamingExchange;
import info.bitrich.xchangestream.core.StreamingMarketDataService;

import io.reactivex.Completable;
import io.reactivex.Observable;

import si.mazi.rescu.RestProxyFactory;

import org.knowm.xchange.binance.BinanceAuthenticated;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.binance.service.BinanceMarketDataService;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.service.BaseExchangeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BinanceStreamingExchange extends BinanceExchange implements StreamingExchange {

    private static final Logger LOG = LoggerFactory.getLogger(BinanceStreamingExchange.class);
    private static final String API_BASE_URI = "wss://stream.binance.com:9443/";

    private BinanceStreamingService streamingService;
    private BinanceUserDataStreamingService userDataStreamingService;
    private BinanceStreamingMarketDataService streamingMarketDataService;
    private BinanceUserDataChannel userDataChannel;

    public BinanceStreamingExchange() {
    }

    /**
     * Binance streaming API expects connections to multiple channels to be defined at connection time. To define the channels for this
     * connection pass a `ProductSubscription` in at connection time.
     *
     * @param args A single `ProductSubscription` to define the subscriptions required to be available during this connection.
     * @return
     */
    @Override
    public Completable connect(ProductSubscription... args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("Subscriptions must be made at connection time");
        }
        if (streamingService != null) {
            throw new UnsupportedOperationException("Exchange only handles a single connection - disconnect the current connection.");
        }

        ProductSubscription subscriptions = args[0];
        streamingService = createStreamingService(subscriptions);

        List<Completable> completables = new ArrayList<>();

        if (!subscriptions.isEmpty()) {
            completables.add(streamingService.connect());
        }

        if (exchangeSpecification.getApiKey() != null) {
            LOG.info("Connecting to authenticated web socket");
            BinanceAuthenticated binance = RestProxyFactory.createProxy(
                BinanceAuthenticated.class,
                getExchangeSpecification().getSslUri(),
                new BaseExchangeService<BinanceExchange>(this) {}.getClientConfig()
            );
            userDataChannel = new BinanceUserDataChannel(binance, exchangeSpecification.getApiKey());
            try {
                completables.add(createAndConnectUserDataService(userDataChannel.getListenKey()));
            } catch (NoActiveChannelException e) {
                throw new IllegalStateException("Failed to establish user data channel", e);
            }
        }

        streamingMarketDataService = new BinanceStreamingMarketDataService(streamingService, (BinanceMarketDataService) marketDataService, userDataStreamingService);

        return Completable.concat(completables)
            .doOnComplete(() -> streamingMarketDataService.openSubscriptions(subscriptions));
    }

    private Completable createAndConnectUserDataService(String listenKey) {
        userDataStreamingService = BinanceUserDataStreamingService.create(listenKey);
        return userDataStreamingService.connect().doOnComplete(() -> {
            LOG.info("Connected to authenticated web socket");
            userDataChannel.onChangeListenKey(newListenKey -> {
                userDataStreamingService.disconnect().blockingAwait();
                createAndConnectUserDataService(newListenKey).blockingAwait();
                streamingMarketDataService.setUserDataStreamingService(userDataStreamingService);
            });
        });
    }

    @Override
    public Completable disconnect() {
        List<Completable> completables = new ArrayList<>();
        completables.add(streamingService.disconnect());
        streamingService = null;
        if (userDataStreamingService != null) {
            completables.add(userDataStreamingService.disconnect());
            userDataStreamingService = null;
        }
        if (userDataChannel != null) {
            userDataChannel.close();
            userDataChannel = null;
        }
        streamingMarketDataService = null;
        return Completable.concat(completables);
    }

    @Override
    public boolean isAlive() {
        return streamingService!= null && streamingService.isSocketOpen();
    }

    @Override
    public Observable<Throwable> reconnectFailure() {
        return streamingService.subscribeReconnectFailure();
    }

    @Override
    public Observable<Object> connectionSuccess() {
        return streamingService.subscribeConnectionSuccess();
    }

    @Override
    public StreamingMarketDataService getStreamingMarketDataService() {
        return streamingMarketDataService;
    }

    private BinanceStreamingService createStreamingService(ProductSubscription subscription) {
        String path = API_BASE_URI + "stream?streams=" + buildSubscriptionStreams(subscription);
        return new BinanceStreamingService(path, subscription);
    }

    public static String buildSubscriptionStreams(ProductSubscription subscription) {
        return Stream.of(buildSubscriptionStrings(subscription.getTicker(), "ticker"),
                buildSubscriptionStrings(subscription.getOrderBook(), "depth"),
                buildSubscriptionStrings(subscription.getTrades(), "trade"))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining("/"));
    }

    private static String buildSubscriptionStrings(List<CurrencyPair> currencyPairs, String subscriptionType) {
        return subscriptionStrings(currencyPairs).map( s -> s + "@" + subscriptionType).collect(Collectors.joining("/"));
    }

    private static Stream<String> subscriptionStrings(List<CurrencyPair> currencyPairs) {
        return currencyPairs.stream()
                .map(pair -> String.join("", pair.toString().split("/")).toLowerCase());
    }

    @Override
    public void useCompressedMessages(boolean compressedMessages) { streamingService.useCompressedMessages(compressedMessages); }

}

