"""Importing a flow module registers its definition in ``flow_registry``."""

from importlib import import_module
from pkgutil import iter_modules

for _module in iter_modules(__path__):
    if not _module.name.startswith("_"):
        import_module(f"{__name__}.{_module.name}")
