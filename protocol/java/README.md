# Java Modules

This area contains generated Java protocol DTO/model artifacts for the canonical protocol surface.

Current scope includes generated records, metadata constants, route descriptors, and envelope models under `java/core/src/main/java/org/xcore/protocol/generated/`.

Serializer-specific runtime wiring is intentionally out of scope here. Application repositories should depend on these generated artifacts and keep consumer-specific JSON integration, adapters, and runtime helpers on their own side.
