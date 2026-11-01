# Routes

Route metadata manifests belong here. These manifests should become the source of truth for stream and RPC wiring semantics and should feed generated route/metadata bindings for consumer SDKs.

Families without routed transport semantics may still register an empty manifest here so they remain part of the canonical generator registry without inventing fake routes.
