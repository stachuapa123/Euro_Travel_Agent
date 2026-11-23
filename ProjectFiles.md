# Project files overview

What each file in the project does, and how crucial it is.

**Rating scale**
- **10**: the project doesn't build or start without it
- **7–9**: core to how the system works or to the course requirements
- **4–6**: useful or needed for now, but small or easy to replace
- **1–3**: convenience only

## Summary

| File | Role | Rating |
|---|---|:---:|
| `pom.xml` | Build config, JADE dependency | **10** |
| `Launcher.java` | Starts JADE and all agents | **10** |
| `model/TripRequest.java` | The trip request data + validation | **9** |
| `config.properties` | Platform settings and agent list | **9** |
| `agents/PlannerAgent.java` | Coordinates CFP, recommendation, booking | **9** |
| `agents/UserAgent.java` | Represents the traveller | **8** |
| `model/TripRequestCsv.java` | CSV message format | **8** |
| `README.md` | Build/run instructions (required for release) | **7** |
| `ui/ConsoleUserInterface.java` | Command-line menu | **7** |
| `agents/RecommendationAgent.java` | Picks the best offers (placeholder) | **6** |
| `model/TransportMode.java` | Allowed `preferred_mode` values | **6** |
| `ui/UserInterface.java` | Contract between agent and UI | **5** |
| `agents/DummyProviderAgent.java` | Fake transport/hotel providers | **4** |
| `data/requests.csv` | Sample requests | **3** |
| `.gitignore` | Keeps build output out of Git | **3** |

All Java paths are under `src/main/java/travelplanner/`; resources are under `src/main/resources/`.

---

## Build and startup

### `pom.xml`: 10/10
Maven's project file. It sets Java 17, downloads JADE (`de.enflexit.jade` from Maven Central), and configures:
- `mvn compile exec:java`, which runs the program directly
- `mvn package`, which builds the runnable `target/travel-planner.jar`

**Why 10:** without it there's no build, no JADE and no way to run the project.

### `Launcher.java`: 10/10
The program's `main` method. It reads `config.properties`, starts the JADE main container (with the AMS and DF), then creates every agent listed in `agents=`, in that order. Each agent receives the whole config as its first argument.

**Why 10:** nothing starts without it.

### `src/main/resources/config.properties`: 9/10
All settings in one place:
- JADE host, port, and whether to open the JADE GUI
- `agents=`: which agents start, with which class and arguments. Providers come first and the User comes last.
- `user.*`: User agent settings (UI type, planner name, reply timeout, shutdown on quit)

**Why 9:** the Launcher fails without it. It's also the place to switch between dummy and real agents without touching code.

---

## Agents (`agents/`)

### `PlannerAgent.java`: 9/10
The coordinator and buyer. For each trip request (keyed by `request_id`) it:
1. parses the CSV request and replies AGREE to the user
2. searches the DF for `transport` and `accommodation` providers and sends them a CFP
3. collects PROPOSE/REFUSE answers
4. sends the proposals to the Recommendation agent
5. sends ACCEPT_PROPOSAL to the chosen offers and REJECT_PROPOSAL to the rest
6. waits for the INFORM confirmations, then sends the final plan to the user

**Why 9:** it's the centre of the system and covers DF discovery and the CFP protocol (checkpoint 2). Still missing: negotiation and timeouts.

### `UserAgent.java`: 8/10
Represents the traveller. It gets requests from the UI, sends each to the Planner as a REQUEST with CSV content, and shows the replies: AGREE as progress, INFORM as the result, REFUSE/FAILURE/NOT_UNDERSTOOD as errors. On quit, it shuts down the platform.

**Why 8:** it's the entry point for every request and one of the required agent roles.

### `RecommendationAgent.java`: 6/10
Registers in the DF as `recommendation`. It receives all proposals from the Planner and replies with the chosen transport and accommodation. **Placeholder:** it currently picks the cheapest of each type.

**Why 6:** the flow needs it, but the logic is temporary. The real version (weighted score of price, time and comfort based on the user's priority, with a ranked list) is one of the course's required algorithms, so the *future* version is around 9.

### `DummyProviderAgent.java`: 4/10
Fake Transport/Accommodation agent. It registers in the DF under the type given in the config, always proposes the same price and description, and always confirms when accepted. All 6 providers in the config use this one class.

**Why 4:** needed right now to test the Planner and User agents, but it will be replaced by the real `TransportAgent` and `AccommodationAgent`, possibly sharing a `ProviderAgent` base class.

---

## Data model (`model/`)

### `TripRequest.java`: 9/10
One trip request with all 11 fields (origin, destination, budgets, nights, mode, max duration in minutes, rating, distance, negotiation). The constructor checks every value, so an invalid request can't exist. `describe()` gives a readable summary.

**Why 9:** every agent that handles a request uses it. Without it, nothing compiles.

### `TripRequestCsv.java`: 8/10
Converts a `TripRequest` to and from CSV:
- the message content (header + one row) the User sends and the Planner reads
- reading `requests.csv`
- field parsers for typed input (accepts `243,87` as well as `243.87`)

**Why 8:** it defines the message format between agents. Kept separate from `TripRequest` so the format can change (e.g. to JSON) without touching the data class. It could be merged into `TripRequest` if you prefer fewer files.

### `TransportMode.java`: 6/10
Enum of allowed `preferred_mode` values: `ANY`, `TRAIN`, `PLANE`. It catches typos when a request is created.

**Why 6:** small, but `TripRequest` depends on it (the project doesn't compile without it). Update it if your transport data uses different names.

---

## User interface (`ui/`)

### `ConsoleUserInterface.java`: 7/10
The command-line menu:
1. type a request field by field (the transport time is asked in **hours** and stored in minutes)
2. paste a CSV line
3. send a sample request, or all of them

It runs on its own thread, so the agent can keep receiving messages, and it waits for each reply before showing the menu again.

**Why 7:** currently the only way to give the system input.

### `UserInterface.java`: 5/10
An interface listing what any UI must do: show status/result/error, and notify the agent on submit and quit. `UserAgent` only talks to this interface.

**Why 5:** not strictly needed today, but it's what makes adding a Swing GUI later a one-line change in `UserAgent`.

---

## Other files

### `README.md`: 7/10
Requirements, build and run commands, IntelliJ usage, Maven installation, project layout, how to add an agent, troubleshooting.

**Why 7:** not needed to run anything, but the final release **must** include build and run instructions the teacher can reproduce.

### `src/main/resources/data/requests.csv`: 3/10
The 7 sample trip requests, used by menu option 3 (pick one, or send all).

**Why 3:** handy for testing and the demo. Nothing breaks without it, but menu option 3 then finds no samples.

### `.gitignore`: 3/10
Keeps `target/`, IntelliJ files and the files JADE writes on every run out of Git.

**Why 3:** not needed to run anything, but without it the repository gets cluttered with build output.

---

## Generated files (don't commit)

| File / folder | Created by | Notes |
|---|---|---|
| `target/` | Maven | Compiled classes and the jar; rebuilt every time |
| `APDescription.txt` | JADE, on every run | Platform description; safe to delete |
| `MTPs-Main-Container.txt` | JADE, on every run | Message transport addresses; safe to delete |
| `.idea/`, `*.iml` | IntelliJ | Local IDE settings |

All of these are already in `.gitignore`.