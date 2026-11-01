package org.xcore.plugin.rating.match;

import org.junit.jupiter.api.BeforeEach;

class InMemoryMatchStoreTest extends MatchStoreContract {
    private InMemoryMatchStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryMatchStore();
    }

    @Override
    MatchStore store() {
        return store;
    }
}
