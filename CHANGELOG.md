# Changelog

All notable changes to HCDConnect are listed here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). The app uses the `versionName` in `app/build.gradle.kts` as its version number.

## [Unreleased]

### Added

- A **Student** role label for signed-in users who aren't admins or organizers. It shows in the dashboard header and in **Manage users & clubs**, where it replaces "Member".
- A "Signed in as <email>" line at the top of the dashboard's ⋮ menu.
- README with features, setup, the Firestore data model, the password reset steps, and screenshots of the student, organizer and admin views.
- MIT license.
- This changelog.
- A contributing guide (`CONTRIBUTING.md`).
- A code of conduct (`CODE_OF_CONDUCT.md`), based on the Contributor Covenant 2.1.
- A security policy (`SECURITY.md`) that explains how to report vulnerabilities privately.
- Issue forms for bug reports and feature requests, and a pull request template.

### Changed

- The dashboard header now shows only the role, such as "Organizer of Student Council". The email moved to the ⋮ menu. Before, long emails pushed the role off the edge of the header.
- The help text on **Manage users & clubs** now explains what students can do.

## [1.0] - 2026-10-01

First version.

### Added

- Sign in, create an account and reset a forgotten password with Firebase Authentication (email and password).
- Events dashboard with Upcoming and Past tabs, search, club filter chips and pull to refresh.
- Event details with RSVP ("I'm going"), add to calendar and share.
- A reminder notification an hour before events you've RSVP'd to.
- Event statuses: Upcoming, Ongoing, Completed and Cancelled. Events that have started show as Completed automatically.
- Event times always shown and entered in campus time (Asia/Manila).
- Roles:
  - Admins manage every event, club and user.
  - Organizers post and manage events for their one club.
  - Everyone else can view events and RSVP.
- **Manage users & clubs** screen for admins.
- Firestore security rules that enforce the same roles.
- HCDC maroon and gold theme with the Lato font, in light and dark mode.

[Unreleased]: https://github.com/JhunDevRey/HCDConnect/compare/a6be791...main
[1.0]: https://github.com/JhunDevRey/HCDConnect/commit/a6be791
