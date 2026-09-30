## What does this change?

<!-- A short summary of the change and why it's needed. -->

Fixes #<!-- issue number, if there is one -->

## Type of change

- [ ] Bug fix
- [ ] New feature
- [ ] UI or design change
- [ ] Refactor or cleanup (no change in behavior)
- [ ] Documentation

## How did you test it?

<!-- The steps you took, and the device or emulator and Android version you used. -->

Roles tested:

- [ ] Student
- [ ] Organizer
- [ ] Admin

## Screenshots

<!-- For UI changes, add before and after screenshots, in light and dark mode if the look changed. Use test accounts, not real people's emails. -->

## Checklist

- [ ] `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` pass.
- [ ] User-facing text is in `res/values/strings.xml`.
- [ ] If a role's permissions changed, `UserRoles` and `firestore.rules` are both updated. Include the new rules below.
- [ ] I added a line under **[Unreleased]** in `CHANGELOG.md`.
- [ ] I updated the README if needed, including its screenshots.
- [ ] No secrets, personal data or my own `google-services.json` are committed.
