package org.xcore.plugin.database;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Bridges a single-result Reactive Streams publisher to a CompletionStage. */
public final class MongoAsync {
    private MongoAsync() {
    }

    public static <T> CompletionStage<T> first(Publisher<T> publisher) {
        CompletableFuture<T> result = new CompletableFuture<>();

        publisher.subscribe(new Subscriber<>() {
            private Subscription subscription;
            private boolean received;

            @Override
            public void onSubscribe(Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(T value) {
                if (!received) {
                    received = true;
                    result.complete(value);
                    subscription.cancel();
                }
            }

            @Override
            public void onError(Throwable error) {
                result.completeExceptionally(error);
            }

            @Override
            public void onComplete() {
                if (!received) {
                    result.complete(null);
                }
            }
        });

        return result;
    }
}
