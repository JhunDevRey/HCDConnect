# HCDConnect

HCDConnect is an Android app for campus events at Holy Cross of Davao College (HCDC). Students can browse and RSVP to events. Club organizers post and manage events for their club, and admins manage clubs and users.

## Features

- **Event dashboard:** a live list of campus events with search, club filter chips, and tabs. Pull down to refresh.
- **Event details and RSVP:** open an event to see when and where it is and what it's about, mark yourself as going, or share it.
- **Event reminders:** a notification before events you've RSVP'd to.
- **Event status:** Upcoming, Ongoing, Completed or Cancelled. Events that have already started show as Completed automatically.
- **Campus time:** event times are always shown and entered in Asia/Manila time, whatever time zone the device is set to.
- **Roles:**
  - **Student:** any signed-in user. Can view events and RSVP.
  - **Organizer:** belongs to one club. Can create, edit and cancel that club's events.
  - **Admin:** can manage every event, club and organizer, and other admins.
- **HCDC branding:** maroon and gold theme and the Lato font, with light and dark mode.

## Tech stack

- Kotlin and Android Views with ViewBinding and Material Components
- MVVM with ViewModel, coroutines and `StateFlow`
- Firebase Authentication (email and password) and Cloud Firestore
- WorkManager for event reminders
- Min SDK 26, target SDK 36

## Project structure

```
app/src/main/java/com/hcdc/hcdconnect/
├── data/
│   ├── model/          # CampusEvent, EventStatus
│   └── repository/     # Auth, events, clubs, organizers, roles, users (Firestore)
├── reminders/          # Scheduling and posting event reminder notifications
├── ui/
│   ├── auth/           # Login
│   ├── dashboard/      # Event list, filters and adapter
│   ├── eventdetail/    # Event details and RSVP
│   ├── createevent/    # Create and edit events
│   ├── admin/          # Manage users, organizers and clubs
│   └── common/         # Campus time zone and system bar helpers
└── MainActivity.kt     # Dashboard (launcher screen)
firestore.rules         # Firestore security rules (mirrors the published rules)
```

## Getting started

1. Clone the repo:
   ```
   git clone https://github.com/JhunDevRey/HCDConnect.git
   ```
2. Open the project in Android Studio.
3. To use your own Firebase project:
   - Create a Firebase project and add an Android app with the package name `com.hcdc.hcdconnect`.
   - Download `google-services.json` and replace `app/google-services.json` with it.
   - Turn on **Email/Password** sign-in in Firebase Authentication.
   - Create a Cloud Firestore database and publish the rules in `firestore.rules`.
4. Build and run on an emulator or device (Android 8.0 or later).

## Firestore data model

| Collection | Document ID | Contents |
|---|---|---|
| `events` | auto | `title`, `organizerClub`, `date`, `location`, `description`, `status`, `createdBy`, `attendees` |
| `clubs` | club name | One document per club |
| `organizers` | user UID | `email`, `clubs` (at most one club) |
| `admins` | user UID | Marks the user as an admin |
| `users` | user UID | `email`, `lastSignIn` |

To make the first admin, create a document in `admins` with that user's UID as the document ID. After that, admins can manage roles in the app's **Manage users & clubs** screen.
