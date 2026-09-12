package org.xcore.plugin.database;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Bridges a Reactive Streams publisher to a CompletionStage. */
public final class MongoAsync {
    private MongoAsync() {
    }

    public static <T> CompletionStage<T> first(Publisher<T> publisher) {
        CompletableFuture<T> result = new CompletableFuture<>();

        try {
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
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }

        return result;
    }

    public static <T> CompletionStage<List<T>> list(Publisher<T> publisher) {
        CompletableFuture<List<T>> result = new CompletableFuture<>();
        List<T> items = new ArrayList<>();

        try {
            publisher.subscribe(new Subscriber<>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(T value) {
                items.add(value);
            }

            @Override
            public void onError(Throwable error) {
                result.completeExceptionally(error);
            }

            @Override
            public void onComplete() {
                result.complete(List.copyOf(items));
            }
            });
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }

        return result;
    }
}
