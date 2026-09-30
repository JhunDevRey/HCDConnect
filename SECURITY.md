# Security Policy

## Supported versions

HCDConnect is a young project, so only the latest code on the `main` branch gets security fixes.

| Version | Supported |
|---|---|
| `main` (latest) | Yes |
| Older commits and builds | No |

## Reporting a vulnerability

**Please don't report security problems in a public issue, pull request or discussion.** Report them privately so they can be fixed before anyone can use them.

1. Go to the repo's [**Security** tab](https://github.com/JhunDevRey/HCDConnect/security) and click **Report a vulnerability**. This opens a private report that only you and the maintainer can see.
2. If that button isn't available, contact the maintainer, [@JhunDevRey](https://github.com/JhunDevRey), privately on GitHub and ask for a private way to share the details.

Include as much of this as you can:

- What the problem is and what an attacker could do with it.
- Steps to reproduce it, such as the account role you used and the requests or app actions involved.
- The commit or build you tested.
- Any ideas you have for a fix.

Test only with your own Firebase project or your own accounts. Don't access, change or delete other people's data, and don't run tests that could disrupt the app for others.

## What to expect

- You'll get a reply acknowledging your report, usually within a week.
- We'll confirm whether it's a real problem and keep you updated while it's being fixed.
- Once a fix is out, we'll publish a security advisory. We'll credit you there unless you'd rather stay anonymous.

This is a volunteer project with no bug bounty, so rewards can't be offered.

## Scope

Examples of what's in scope:

- Ways around the Firestore security rules in `firestore.rules`. For example:
  - a student creating or editing events;
  - an organizer posting for another club;
  - anyone but an admin changing roles or clubs.
- Reading or changing other users' data, such as their email or RSVPs, without permission.
- Problems with sign-in, account creation or password reset.
- Anything in the app that leaks personal data.

Out of scope:

- **The Firebase API key in `app/google-services.json`.** It identifies the Firebase project and isn't a secret, because it has to ship inside the app. Access is controlled by Firebase Authentication and the Firestore rules, not by keeping the key hidden. A way to misuse the key despite those rules is in scope.
- Problems in Firebase, Android or other third-party services themselves. Report those to the vendor.
- Problems that need a rooted or already-compromised phone.
- Denial of service, spam and social engineering.
