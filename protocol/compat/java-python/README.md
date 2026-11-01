# Java/Python Compatibility

This area is for roundtrip, fixture-validation, and golden-style checks across generated Java and Python protocol artifacts.

The current documented scope is the generated maps, chat/heartbeat, discord, and moderation surface.

Within that scope:
- canonical specs remain authoritative
- generated Java/Python artifacts are strict canonical consumption surfaces, not alternate protocol definitions
- no tolerant-reader behavior is implied
- no legacy alias support is implied in generated canonical models
- no consumer-side migration adapters are part of this compatibility area

These checks verify only that generated language artifacts stay aligned with the current canonical generated surface. They do not claim broader protocol-family coverage, consumer integration coverage, or runtime/business-logic validation.
