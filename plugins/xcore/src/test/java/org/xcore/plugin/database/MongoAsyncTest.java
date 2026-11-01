package org.xcore.plugin.database;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MongoAsyncTest {
    @Test
    void firstCompletesWithTheFirstValueAndRequestsOneItem() throws Exception {
        TrackingPublisher<String> publisher = new TrackingPublisher<>();

        CompletionStage<String> result = MongoAsync.first(publisher);
        publisher.emit("value");

        assertThat(result.toCompletableFuture().get(1, TimeUnit.SECONDS)).isEqualTo("value");
        assertThat(publisher.requested).isEqualTo(1);
        assertThat(publisher.cancelled).isTrue();
    }

    @Test
    void firstCompletesNullWhenPublisherIsEmpty() throws Exception {
        TrackingPublisher<String> publisher = new TrackingPublisher<>();

        CompletionStage<String> result = MongoAsync.first(publisher);
        publisher.complete();

        assertThat(result.toCompletableFuture().get(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void firstPropagatesPublisherFailure() {
        RuntimeException failure = new RuntimeException("mongo unavailable");
        CompletionStage<String> result = MongoAsync.first(subscriber -> subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                subscriber.onError(failure);
            }

            @Override
            public void cancel() {
            }
        }));

        assertThat(result.toCompletableFuture()).failsWithin(1, TimeUnit.SECONDS)
                .withThrowableThat()
                .withMessageContaining("mongo unavailable");
    }

    @Test
    void listCollectsAllItemsAndRequestsMax() throws Exception {
        TrackingPublisher<String> publisher = new TrackingPublisher<>();

        CompletionStage<java.util.List<String>> result = MongoAsync.list(publisher);
        publisher.emit("first");
        publisher.emit("second");
        publisher.complete();

        assertThat(result.toCompletableFuture().get(1, TimeUnit.SECONDS))
                .containsExactly("first", "second");
        assertThat(publisher.requested).isEqualTo(Long.MAX_VALUE);
    }

    private static final class TrackingPublisher<T> implements Publisher<T> {
        private Subscriber<? super T> subscriber;
        private long requested;
        private boolean cancelled;

        @Override
        public void subscribe(Subscriber<? super T> subscriber) {
            this.subscriber = subscriber;
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    requested += n;
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
        }

        private void emit(T value) {
            subscriber.onNext(value);
        }

        private void complete() {
            subscriber.onComplete();
        }
    }
}
