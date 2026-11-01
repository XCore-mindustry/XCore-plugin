`jni-v2.history` was generated with the JNI release at e2cc21d (FFM ABI predecessor 2).
It contains a baseline with signed scalar config, a replacement with UTF-8/NUL text,
a byte-array config, a committed rollback restoring the baseline, and a 4 KiB config.
`NativeStoreTest.readsJournalProducedByThePreviousJniRelease` verifies the migrated reader.
Keep this captured file: regenerating it through the new FFM implementation would lose the interoperability check.
