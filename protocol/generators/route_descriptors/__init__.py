"""Route descriptor builders for generated protocol outputs."""

from .core import (
    MessageDescriptor,
    RouteDescriptor,
    RouteResponseDescriptor,
    build_route_descriptors,
)
from .maps import build_maps_route_descriptors

__all__ = [
    "MessageDescriptor",
    "RouteDescriptor",
    "RouteResponseDescriptor",
    "build_route_descriptors",
    "build_maps_route_descriptors",
]
