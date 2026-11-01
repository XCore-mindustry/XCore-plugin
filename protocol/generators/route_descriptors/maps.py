"""Compatibility wrapper for legacy maps-branded route descriptor imports."""

from __future__ import annotations

from ..model import NormalizedRoute, NormalizedSchema
from .core import MessageDescriptor, RouteDescriptor, RouteResponseDescriptor, build_route_descriptors


def build_maps_route_descriptors(
    routes: tuple[NormalizedRoute, ...],
    map_schemas: tuple[NormalizedSchema, ...],
) -> tuple[RouteDescriptor, ...]:
    return build_route_descriptors(routes, map_schemas)
