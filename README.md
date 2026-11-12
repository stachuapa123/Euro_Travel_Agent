# Multi-Agent Travel Planner

A JADE multi-agent system that plans a round trip between European cities. Provider agents compete with offers, a Recommendation agent ranks the plans, and the Planner negotiates the price of the best one.

Group: Kacper Neumann, Bartłomiej Śliwa, Stanisław Apanasiewicz

## Requirements

- **Java 17 or newer.** Check with `java -version`.
- **Maven 3.8 or newer.** Check with `mvn -v`. If the command is not recognized, see [Installing Maven](#installing-maven).

JADE is downloaded automatically by Maven from Maven Central (`de.enflexit.jade`, a build of the original JADE 4.6 code).

## Build and run

Run these from the project folder (the one containing `pom.xml`):

```bash
# compile and run directly
mvn compile exec:java

# or build a runnable jar (JADE is bundled inside) and run it
mvn package
java -jar target/travel-planner.jar

# run with a different config file
java -jar target/travel-planner.jar path/to/my-config.properties
```

The first build downloads JADE and the Maven plugins, so it takes a minute.

Expected output, at the end of the JADE startup log:

```
[planner] ready
[user] ready
[user] sent REQUEST: origin=Gdansk;destination=Krakow;...
[planner] REQUEST from user: origin=Gdansk;destination=Krakow;...
[user] INFORM from planner: Trip request accepted for planning
```

Stop it with `Ctrl+C`. Set `jade.gui=true` in `config.properties` to open the JADE management GUI (useful for the Sniffer and for viewing the DF).

## Running in IntelliJ IDEA (no Maven install needed)

IntelliJ comes with its own Maven, so you can work in it without installing Maven separately.

1. `File → Open` → choose the project folder. IntelliJ imports it as a Maven project. If it asks, click **Load Maven project**.
2. Open `src/main/java/travelplanner/Launcher.java` and click the green arrow next to `main`.

To run a Maven command, open the **Maven** tool window on the right, click **Execute Maven Goal** (the `m` icon), and type, for example, `mvn compile exec:java`.

If a Java version error appears, set `File → Project Structure → Project → SDK` to 17 or newer.

Note: IntelliJ's built-in Maven is **not** available in a terminal. To use `mvn` in PowerShell or cmd, install Maven as described below.

## Installing Maven

### Windows

1. **Check Java.** In PowerShell, `java -version` should show 17 or higher.
2. **Download Maven.** Get the *Binary zip archive* (`apache-maven-3.9.x-bin.zip`) from https://maven.apache.org/download.cgi.
3. **Extract it** to a permanent location, for example `C:\tools\apache-maven-3.9.11`. Make sure the zip isn't extracted into a doubled folder like `apache-maven-3.9.11\apache-maven-3.9.11`: the `bin` folder should be directly inside.
4. **Add Maven to PATH:**
    1. Press Start, type **environment variables**, and open **Edit environment variables for your account**.
    2. Select **Path** → **Edit** → **New**, and enter the `bin` folder, e.g. `C:\tools\apache-maven-3.9.11\bin`.
    3. Click OK on all windows.
5. **Set JAVA_HOME** (if not already set). In the same window, click **New** under user variables, name it `JAVA_HOME`, and set it to your JDK folder, e.g. `C:\Program Files\Java\jdk-17`. Use the JDK folder itself, not its `bin` subfolder. In IntelliJ, the JDK path is shown under `File → Project Structure → SDKs`.
6. **Open a new terminal.** Already-open windows, including IntelliJ's terminal until IntelliJ restarts, don't see the change.
7. **Verify:** `mvn -v` should print the Maven version and the Java version it uses.

Instead of steps 2–4, you can use a package manager: `choco install maven` (Chocolatey) or `scoop install maven` (Scoop).

**Troubleshooting**

- Check whether the terminal sees Maven on its PATH:
  ```powershell
  $env:Path -split ';' | Select-String -Pattern 'maven'
  ```
  No output means the PATH entry is missing or the terminal was opened before it was added.
- If you need `mvn` right away in one terminal, you can borrow IntelliJ's built-in Maven for that session. Adjust the IntelliJ version to match yours:
  ```powershell
  $env:Path += ";C:\Program Files\JetBrains\IntelliJ IDEA 2025.2\plugins\maven\lib\maven3\bin"
  ```

### macOS

```bash
brew install maven
mvn -v
```

### Linux (Debian/Ubuntu)

```bash
sudo apt update
sudo apt install maven
mvn -v
```

## Project layout

```
pom.xml                          build config, JADE dependency
src/main/java/travelplanner/
    Launcher.java                starts the JADE platform and the agents from config
    agents/                      one class per agent type
src/main/resources/
    config.properties            platform settings, agent list, sample trip request
    data/                        (to add) synthetic cities, connections, accommodation
```

## Adding an agent

1. Create the class in `src/main/java/travelplanner/agents/` with `package travelplanner.agents;`, extending `jade.core.Agent`.
2. Add it to `agents=` in `config.properties`, before `user`:

   ```
   agents=planner:travelplanner.agents.PlannerAgent;\
          transport1:travelplanner.agents.TransportAgent(STUBBORN);\
          user:travelplanner.agents.UserAgent
   ```

   Each agent receives the whole config as `getArguments()[0]`. Values in parentheses follow as `getArguments()[1]`, `[2]`, …

## Other problems

**`Port 1099 already in use`.** Another JADE platform is still running. Stop it, or change `jade.port` in `config.properties`.

**`ClassNotFoundException` for an agent.** The class name in `agents=` must match the class's package and name exactly, e.g. `travelplanner.agents.PlannerAgent`.