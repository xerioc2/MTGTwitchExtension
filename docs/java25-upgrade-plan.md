# Java 25 Bridge Upgrade Plan

This work is isolated on `feature/java25-spring-upgrade`. It must not alter the
current Twitch frontend, Supabase wire contract, production environment, or the
existing Bridge 0.0.12 release artifacts.

## Objective

Evaluate Java 25 as the bundled bridge runtime, with measured evidence for
startup time, memory use, installer size, and Windows behavior. Keep Java 21 as
the rollback baseline until every automated and manual gate passes.

Changing the source language level is not required. The first Java 25 candidate
should still compile with `--release 21` so runtime and framework changes remain
separate from language changes.

## Java 21 Baseline

Captured on 2026-07-31 from commit `44e1521`:

| Item | Baseline |
| --- | --- |
| Maven runtime | BellSoft Liberica NIK 21.0.4 |
| `jpackage` runtime | BellSoft Liberica NIK 21.0.4 |
| Shell `java` | Oracle Java 21.0.11 LTS |
| Maven | 3.9.16 |
| Spring Boot | 3.3.5 |
| Spring Framework | 6.1.14 |
| JNA / JNA Platform | 5.15.0 |
| Mockito | 5.11.0 |
| Byte Buddy / agent | 1.14.19 |
| Backend tests | 87 passed, 0 failures, 0 errors |
| Test time | 36.55 seconds |
| Desktop package time | 3.89 seconds |
| Executable jar | 27,892,851 bytes |
| Installer | 99,380,224 bytes |
| Portable zip | 97,896,586 bytes |

The baseline test run emits the existing Byte Buddy dynamic-agent warning. A
Java 25 candidate must either eliminate it through supported dependency/test
configuration or document an intentional, narrowly scoped JVM option.

Startup latency and idle working set still need a controlled five-run manual
measurement. Measure from process launch until `/api/status` reports healthy,
with MTGO closed and the same saved bridge configuration for both runtimes.

## Upgrade Sequence

Each phase gets its own commit. Do not combine phases merely to make the final
diff smaller; separate commits make regression isolation and rollback useful.

### Phase 1: Framework Compatibility

1. Upgrade Spring Boot from 3.3.5 to the latest 3.5.x maintenance release while
   keeping Java and `--release` at 21.
2. Accept only dependency changes managed by Spring Boot unless a direct
   dependency has a demonstrated compatibility issue.
3. Run the full backend suite and build the desktop jar.
4. Review deprecations and behavior changes before proceeding.

Spring Boot 3.3 does not officially support Java 25. Spring Boot 3.5 is the
lower-risk compatibility bridge. A later move to Spring Boot 4.x is a separate
major framework decision and is not required for this runtime experiment.

### Phase 2: Java 25 Runtime, Java 21 Bytecode

1. Install a Java 25 LTS JDK that includes `jpackage`.
2. Point Maven and `jpackage` to the same JDK to remove the current split-runtime
   setup.
3. Keep `<java.version>` and compiler `<release>` at 21.
4. Run tests and packaging on Java 25.
5. Update JNA, Mockito, or Byte Buddy only when a test/runtime failure or warning
   demonstrates the need.
6. Investigate native-access warnings from JNA. Add
   `--enable-native-access=ALL-UNNAMED` to the packaged runtime only if Java 25
   requires it for the foreground-window integration.

### Phase 3: Packaged Runtime Comparison

1. Build a versioned Java 25 app-image, portable zip, and installer in an
   isolated output directory.
2. Do not overwrite Bridge 0.0.12 or the Desktop release folder.
3. Record jar, app-image, zip, and installer sizes.
4. Measure five cold starts and five idle working-set samples for Java 21 and
   Java 25 under the same conditions.
5. Compare bridge logs for new JVM, native-access, reflection, and agent
   warnings.

Initial acceptance thresholds:

- No startup regression greater than 10 percent at the median.
- No idle working-set regression greater than 10 percent.
- No installer-size regression greater than 15 percent without a documented
  benefit.
- No new warnings that indicate future runtime incompatibility.

### Phase 4: Windows Manual Verification

Exercise the packaged Java 25 build, not only `spring-boot:run`:

- First launch and single-instance/port rolling.
- System tray icon, open, and shutdown actions.
- Twitch OAuth login and callback HTML.
- Saved relay credentials and reconnect.
- MTGO log discovery, bounded backfill, and live tailing.
- Multiple configured MTGO usernames.
- Foreground-window game switching through JNA.
- Multiple MTGO installs/logs and authoritative focus switching.
- Per-game deck refresh after sideboarding.
- Windows autostart registry self-healing.
- OBS `--quiet-if-running` launch path.
- Installer, upgrade, uninstall, and portable zip startup.

### Phase 5: Optional Java 25 Source Level

Only change compiler `<release>` from 21 to 25 if a specific Java 25 language or
API feature will simplify production code. Runtime improvements do not require
this step, and staying on Java 21 bytecode preserves an easier rollback path.

## Commands Per Phase

Run from `backend/`:

```powershell
mvn -q -DskipTests=false test
mvn -q -Pdesktop -DskipTests package
```

Build the isolated packaged candidates only after the Maven gates pass. Record
`java -version`, `mvn -version`, and `jpackage --version` with every comparison.

## Stop Conditions

Stop and return to the last green phase if any of these occur:

- A GameState or relay payload shape changes.
- JNA focus tracking becomes unreliable.
- The packaged app needs broad native-access or reflection permissions that
  cannot be narrowed and justified.
- OAuth, tray, autostart, OBS launch, or log discovery regresses.
- The Java 25 build only passes by suppressing a real compatibility failure.

No Java 25 artifact becomes the public bridge until it has completed the manual
stream test independently of the current release.

## Automated Candidate Results

Captured on 2026-07-31 from the same branch, without changing the Java source
or bytecode level:

| Item | Java 25 candidate |
| --- | --- |
| JDK / bundled runtime | BellSoft Liberica 25.0.4+9 LTS |
| Spring Boot | 3.5.16 |
| Spring Framework | 6.2.19 |
| Mockito | 5.17.0 |
| Byte Buddy / agent | 1.17.8 |
| Backend tests | 87 passed, 0 failures, 0 errors |
| Executable jar | 29,052,516 bytes |
| App-image | 165,678,350 bytes (158.00 MiB) |
| Installer | 79,002,624 bytes (75.34 MiB) |
| Portable zip | 77,201,530 bytes (73.63 MiB) |
| Candidate version | 0.0.14 |
| App-image path | `backend/dist/java25-live-candidate/MTGO Twitch Bridge` |
| Installer path | `backend/dist/java25-live-installer/MTGO Twitch Bridge-0.0.14.exe` |

The executable jar is 1,159,665 bytes (about 4.2 percent) larger than the Java
21/Spring Boot 3.3 baseline. The isolated app-image is 75,947,726 bytes (about
31.4 percent) smaller than the existing 230.43 MiB Java 21 app-image. This is an
observed package comparison, not a Java-only attribution: the existing image
uses Liberica NIK and bundles additional GraalVM and JDK modules.

The candidate installer is about 20.5 percent smaller than the baseline
installer, and the portable zip is about 21.1 percent smaller than the baseline
zip. As with the app-image measurement, these are end-to-end packaging results
and include differences in the bundled runtime module set.

The supported Mockito test agent removes the Java 21 dynamic-agent warning and
the same configuration passes on Java 25. Automated tests did not emit a JNA
native-access warning. Foreground-window tracking still requires the packaged
manual test because the unit suite does not exercise live Windows `User32`
calls.

The Java 25 app-image, installer, and portable zip were built in isolated
candidate directories. The existing `backend/dist/windows-package` output and
the Desktop 0.0.12 release folder were not modified. These candidates remain
blocked from release until the Windows manual verification checklist above is
complete.
