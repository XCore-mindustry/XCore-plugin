from __future__ import annotations

from aiohttp import web

from .prometheus import render_metrics
from .runtime import GatewayRuntime
from .store import SeriesStore


def create_app(store: SeriesStore, runtime: GatewayRuntime) -> web.Application:
    app = web.Application()

    async def metrics_handler(_: web.Request) -> web.Response:
        snapshots, node_states = store.render_snapshot()
        body = render_metrics(snapshots, node_states, runtime.self_metrics_snapshot)
        return web.Response(text=body, content_type="text/plain; version=0.0.4")

    async def health_handler(_: web.Request) -> web.Response:
        _, node_states = store.render_snapshot()
        return web.json_response(
            runtime.health_snapshot(tracked_servers=len(node_states))
        )

    async def runtime_context(_: web.Application):
        await runtime.start()
        yield
        await runtime.stop()

    app.cleanup_ctx.append(runtime_context)
    app.router.add_get("/metrics", metrics_handler)
    app.router.add_get("/health", health_handler)
    return app
