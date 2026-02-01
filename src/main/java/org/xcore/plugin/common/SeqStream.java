package org.xcore.plugin.common;

import arc.struct.Seq;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class SeqStream {

    /**
     * Convert Arc Seq to Java Stream
     */
    public static <T> Stream<T> of(Seq<T> seq) {
        return StreamSupport.stream(seq.spliterator(), false);
    }

    /**
     * Convert to parallel stream
     */
    public static <T> Stream<T> ofParallel(Seq<T> seq) {
        return StreamSupport.stream(seq.spliterator(), true);
    }
}