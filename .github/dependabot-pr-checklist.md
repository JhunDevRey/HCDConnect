<!-- dependabot-checklist -->
## Review checklist for this dependency update

Dependabot writes its own pull request description, so this checklist is posted instead of the usual pull request template.

**Update type:** {{UPDATE_TYPE}}

- [ ] **CI passes:** the build, unit tests and lint are green.
- [ ] **Release notes read:** the links in the description above were checked for breaking changes, deprecations and new minimum versions.
- [ ] **Version requirements met:** for Android Gradle plugin, Kotlin or AndroidX updates, the Gradle wrapper, JDK (21), `compileSdk` and `minSdk` (26) are still new enough. If this needs a newer Gradle, merge the Gradle wrapper update first, then comment `@dependabot rebase`.
- [ ] **Firebase still works:** for Firebase updates, try signing in, loading events and RSVPing in the app.
- [ ] **App checked:** for major updates or anything that changes the UI, run the app and check the main screens in light and dark mode.
- [ ] **Changelog updated:** for notable updates, such as a major version or a fix users will notice, add a line under **[Unreleased]** in `CHANGELOG.md`.
