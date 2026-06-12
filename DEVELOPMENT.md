# Development

## Active Codespace

Use one persistent Codespace as the cloud development machine, then create normal PR branches from inside it.

Suggested setup:

```bash
git switch master
git pull
git switch -c codex/calm-active
git push -u origin codex/calm-active
```

Create the Codespace from `codex/calm-active` and keep reopening that same Codespace from `https://github.com/codespaces`.

For a specific change, branch from an up-to-date `master` inside the Codespace:

```bash
git switch master
git pull
git switch -c codex/my-change
```

## Checks

Run the Android checks before opening or updating a PR:

```bash
bash ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --stacktrace
```

## Codex CLI

Codex CLI is installed in the Codespace image. Start it from the repository root:

```bash
codex
```

Sign in with ChatGPT when prompted, or add `OPENAI_API_KEY` as a Codespaces secret before creating or rebuilding the Codespace if you prefer API-key auth.

## Test APK Releases

After the publish workflow exists on `master`, any branch can publish a test APK release without replacing `apks-latest`.

Use a branch-specific tag:

```bash
gh workflow run "Publish Calm APKs" \
  --ref codex/my-change \
  -f release_tag=apks-test-my-change \
  -f release_title="Calm test my-change"
```

Download the APKs from the prerelease assets on GitHub. Remove the test release when it is no longer needed:

```bash
gh release delete apks-test-my-change --cleanup-tag --yes
```
