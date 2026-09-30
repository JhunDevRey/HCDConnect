# Contributing to HCDConnect

Thanks for helping improve HCDConnect. This guide covers how to set up the project, the conventions the code follows, and how to send a change.

Everyone taking part in this project is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to help

- **Report a bug:** open an issue with the steps to reproduce it, what you expected, what happened, and your Android version and device. A screenshot or screen recording helps.
- **Suggest a feature:** open an issue that describes the problem it solves and who it's for (students, organizers or admins).
- **Send a fix or feature:** for anything bigger than a small fix, open an issue first so we can agree on the approach before you write the code.

## Setting up

1. Install [Android Studio](https://developer.android.com/studio). The project builds with JDK 21. Android Studio's bundled JDK works.
2. Fork the repo and clone your fork:
   ```
   git clone https://github.com/<your-username>/HCDConnect.git
   ```
3. Use your own Firebase project for development, so your testing never touches the real data:
   - Create a Firebase project and add an Android app with the package name `com.hcdc.hcdconnect`.
   - Download its `google-services.json` and replace `app/google-services.json` with it. **Don't commit your copy.**
   - Turn on **Email/Password** sign-in, create a Cloud Firestore database, and publish the rules in `firestore.rules`.
   - Make yourself an admin: sign in to the app once, then in Firestore create a document in `admins` whose ID is your user UID.
4. Open the project in Android Studio and run it on an emulator or a device with Android 8.0 or later.

To test each role, make three accounts:

- **Admin:** listed in `admins`.
- **Organizer:** listed in `organizers` with one club.
- **Student:** no role.

Admins can set roles in the app under **⋮ → Manage users & clubs**.

## Building and testing

```
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run the unit tests
./gradlew lintDebug            # run Android lint
```

On Windows, use `gradlew.bat` in place of `./gradlew`.

Before you open a pull request, check that the build and unit tests pass. The CI workflow runs the same build, unit tests and lint on every pull request, and a pull request needs a green check before it's merged. Also try your change in the app with each role it affects. Add unit tests for new logic in `app/src/test`. `CampusEventTest` is an example.

## How the code is organized

- **MVVM:** each screen has an Activity, which draws the UI with ViewBinding, and a ViewModel, which holds the state in a `StateFlow`. Activities don't call Firebase directly.
- **Repositories** in `data/repository` do all the Firebase Authentication and Firestore work and return `Result` values. Keep new Firestore code there.
- **Roles:** `UserRoles` in `RoleRepository.kt` decides what the UI shows each user. The rules in `firestore.rules` enforce the same model on the server. If you change what a role can do, **update both**, and put the new rules in the pull request. The rules file must match what's published in the Firebase console.
- **Campus time:** show and enter event times with `CampusTime` (Asia/Manila), never the device's time zone.

## Code style

- Kotlin, following the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) and Android Studio's default formatter.
- Match the code around you: its naming, comment style and structure.
- Put all user-facing text in `res/values/strings.xml`. Don't hard-code it in Kotlin or layouts.
- Use the theme's colors and text appearances, not hard-coded values, so light and dark mode both work. Check your change in both.
- Write short, plain comments that explain *why*, not *what*.

## Commits and pull requests

1. Create a branch from `main` with a short, descriptive name, such as `fix-rsvp-count` or `add-event-images`.
2. Write commit messages in the imperative mood with a short first line, such as "Add club logos to event cards".
3. Add a line for your change under **[Unreleased]** in `CHANGELOG.md`.
4. If your change affects the UI, add before-and-after screenshots to the pull request. Update the README screenshots if they no longer match.
5. Open a pull request against `main` that explains what changed and why, and link the issue it fixes.

Keep each pull request to one change, which makes it quicker to review.

## Security and privacy

- Don't commit passwords, API keys other than the ones in `google-services.json`, or real users' data. Keep test account details in `test-account.local.txt`, which git ignores.
- Screenshots in issues and pull requests shouldn't show real people's emails. Use test accounts.
- If you find a security problem, such as a way around the Firestore rules, don't open a public issue. Follow [SECURITY.md](SECURITY.md) to report it privately.

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
