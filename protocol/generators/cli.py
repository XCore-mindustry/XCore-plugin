"""CLI entrypoints for xcore-protocol generation tooling."""

from __future__ import annotations

import argparse
from collections.abc import Sequence

from .discovery import load_generation_plan
from .families import default_family_name, family_argument_help
from .java_writer import check_java_outputs, write_java_outputs
from .python_writer import check_python_outputs, write_python_outputs


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="xcore-protocol-codegen",
        description="Generation tooling for xcore-protocol artifacts.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    generate_parser = subparsers.add_parser(
        "generate",
        help="Generate protocol artifacts for supported languages.",
    )
    generate_parser.add_argument(
        "--language",
        choices=("python", "java", "all"),
        default="all",
        help="Limit generation to one language or generate all outputs.",
    )
    generate_parser.add_argument(
        "--family",
        default=None,
        help=family_argument_help(),
    )

    check_parser = subparsers.add_parser(
        "check",
        help="Verify generated artifacts are up to date.",
    )
    check_parser.add_argument(
        "--language",
        choices=("python", "java", "all"),
        default="all",
        help="Limit stale-file checks to one language or inspect all outputs.",
    )
    check_parser.add_argument(
        "--family",
        default=None,
        help=family_argument_help(),
    )

    inspect_parser = subparsers.add_parser(
        "inspect",
        help="Inspect normalized inputs without writing output files.",
    )
    inspect_parser.add_argument(
        "--family",
        default=None,
        help=family_argument_help(),
    )

    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    if args.command == "generate":
        plan = load_generation_plan(family=args.family)
        if args.language in {"python", "all"}:
            write_python_outputs(plan)
        if args.language in {"java", "all"}:
            write_java_outputs(plan)
        return 0
    if args.command == "check":
        plan = load_generation_plan(family=args.family)
        stale_paths: tuple = ()
        if args.language in {"python", "all"}:
            stale_paths += check_python_outputs(plan)
        if args.language in {"java", "all"}:
            stale_paths += check_java_outputs(plan)
        if stale_paths:
            stale_display = "\n".join(str(path) for path in stale_paths)
            parser.exit(status=1, message=f"Stale generated outputs:\n{stale_display}\n")
        return 0
    if args.command == "inspect":
        plan = load_generation_plan(family=args.family)
        requested_family = args.family or default_family_name()
        print(
            "shared="
            + ", ".join(schema.title for schema in plan.shared_schemas)
            + "\nmessages="
            + ", ".join(schema.title for schema in plan.message_schemas_for(requested_family))
            + "\nroutes="
            + ", ".join(route.message_type for route in plan.routes_for(requested_family))
        )
        return 0

    parser.error(f"Unsupported command: {args.command}")
    return 2
