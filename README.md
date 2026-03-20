## XCore-plugin
#### Description
Multifunctional plugin for Mindustry server.

### Features

### Dependencies
- Java 25+
- MongoDB 6.0+
- Mindustry v155.4+ (or compatible forks)

### Installation
1. Install **Java SDK 25** (or newer).
2. Clone the repository:
   ```bash
   git clone https://github.com/XCore-mindustry/XCore-plugin.git
   cd XCore-plugin
   ```
3. Build the project using Gradle:
   ```bash
   ./gradlew shadowJar
   ```
4. Copy the resulting `.jar` file from `build/libs/` to your Mindustry server's `config/mods` folder.
5. Configure your MongoDB connection in `config/mods/XCore/servers.json`.

### Maven Publishing
- GitHub Actions publishes snapshots to `https://maven.x-core.org/snapshots` on every non-PR push.
- GitHub Actions publishes releases to `https://maven.x-core.org/releases` when a GitHub Release is published.
- Gradle repository names follow the Reposilite pattern: `xcoreRepositorySnapshots` and `xcoreRepositoryReleases`.
- GitHub Actions maps `XCORE_USERNAME` and `XCORE_PASSWORD` to the matching Gradle properties for snapshots and releases.
- Snapshot retention should be limited on the Reposilite side because GitHub Actions only uploads new `-SNAPSHOT` versions.

### License
This project is licensed under the **MIT** License. For more details, see the [LICENSE](LICENSE.txt) file.
