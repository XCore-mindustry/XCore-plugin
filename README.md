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

### Localization
We encourage you to localize our plugin into any language. We use Weblate for translation management. Please visit [our localization portal](https://xcore.eradication.fun/) to contribute.

### License
This project is licensed under the **MIT** License. For more details, see the [LICENSE](LICENSE.txt) file.