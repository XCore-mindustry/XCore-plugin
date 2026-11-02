import hashlib
import json
import subprocess
from pathlib import Path
from zipfile import BadZipFile, ZipFile

from .settings import CommandError, effective_game
from .targets import Plugin, Services, TARGETS


def normalize(name):
    return name.lower().replace(" ", "-")


def manifest(jar):
    try:
        with ZipFile(jar) as archive:
            if archive.getinfo("plugin.json").file_size > 1_048_576:
                raise ValueError()
            data = json.loads(archive.read("plugin.json"))
        if not isinstance(data, dict) or not isinstance(data.get("name"), str) or not data["name"].strip():
            raise ValueError()
        for field in ("dependencies", "softDependencies"):
            values = data.setdefault(field, [])
            if not isinstance(values, list) or any(not isinstance(x, str) or not x for x in values):
                raise ValueError()
        return data
    except (OSError, BadZipFile, KeyError, ValueError, UnicodeError):
        raise CommandError(f"Invalid or missing plugin.json in {jar.name}.") from None


def build(command, root):
    try:
        result = subprocess.run(command, cwd=root, capture_output=True)
    except OSError:
        raise CommandError("Cannot run Gradle; check the wrapper and Java installation.") from None
    if result.returncode:
        raise CommandError("Gradle build failed. Run the target's Gradle task to inspect build errors.")


def resolve(names, settings, root, run=build):
    plugins = {key: {"mod": value.mod, "task": value.task, "jar": root / value.jar}
               for key, value in TARGETS.items() if isinstance(value, Plugin)}
    plugins |= settings["satellite"]
    roots = set()
    services = set()
    for name in names:
        if name in plugins:
            roots.add(name)
        elif isinstance(TARGETS.get(name), Services):
            services.update(TARGETS[name].names)
        else:
            raise CommandError("Unknown target; use a registry name or configured satellite.")
    providers = {}
    for name, item in plugins.items():
        for mod in {normalize(name), normalize(item.get("mod", name))}:
            providers.setdefault(mod, set()).add(name)
    selected, metadata = set(roots), {}
    while pending := selected - metadata.keys():
        tasks = sorted({plugins[name]["task"] for name in pending if "repo" not in plugins[name] and name in TARGETS})
        if tasks:
            run([str(root / "gradlew"), *tasks, "--console=plain"], root)
        for name in sorted(pending):
            item = plugins[name]
            if "repo" in item:
                run([str(item["repo"] / "gradlew"), "-p", str(item["repo"]), item["task"], "--console=plain"], item["repo"])
            data = manifest(Path(item["jar"]))
            actual = normalize(data["name"])
            declared = item.get("mod")
            if declared and normalize(declared) != actual:
                raise CommandError(f"Plugin metadata does not match the configured mod for {name}.")
            metadata[name] = data
            providers.setdefault(actual, set()).add(name)
        for name in sorted(pending):
            for dependency in metadata[name]["dependencies"]:
                candidates = providers.get(normalize(dependency), set())
                if not candidates:
                    raise CommandError(f"No provider for {dependency}, required by {Path(plugins[name]['jar']).name}.")
                if len(candidates) != 1:
                    raise CommandError(f"Multiple providers for {dependency}.")
                selected.update(candidates)
    by_mod = {}
    for name, data in metadata.items():
        mod = normalize(data["name"])
        if mod in by_mod:
            raise CommandError("Two selected jars provide the same mod.")
        by_mod[mod] = name
    ordered, visiting, visited = [], set(), set()

    def visit(name):
        if name in visiting:
            raise CommandError("Plugin dependencies contain a cycle.")
        if name in visited:
            return
        visiting.add(name)
        data = metadata[name]
        for dep in data["dependencies"] + data["softDependencies"]:
            target = by_mod.get(normalize(dep))
            if target:
                visit(target)
            elif dep in data["dependencies"]:
                raise CommandError(f"The selected jar does not provide required mod {dep}.")
        visiting.remove(name)
        visited.add(name)
        ordered.append(name)
    for name in sorted(metadata):
        visit(name)
    game = effective_game(settings, ordered)
    if ordered:
        services.add("game")
    jars = [Path(plugins[name]["jar"]) for name in ordered]
    hashes = {normalize(metadata[name]["name"]): hashlib.sha256(Path(plugins[name]["jar"]).read_bytes()).hexdigest() for name in ordered}
    return jars, hashes, services, game, ordered
