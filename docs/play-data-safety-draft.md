# Google Play Console: Data safety and related answers (DRAFT)

Status: draft for the user to check, written 2026-11-25 and updated the same day for **sync** (version with guest, Google and email sign-in, server limits, one synced copy of the account's data on the server, no ads, no analytics). **Update it before adding ads, analytics, Pro billing or AI.** Wording of Play's form changes now and then; the meaning of each answer is what matters.

Policy and deletion URLs to enter:
- Privacy policy: `https://rucek.display.mk/privacy`
- Account deletion page: `https://rucek.display.mk/delete-account`
- Contact email: `mojrucek@gmail.com`

## 1. Data safety form: the yes/no questions

| Question | Answer | Why |
|---|---|---|
| Does the app collect or share any of the required user data types? | **Yes** | Account data goes to Firebase and our server. |
| Is all of the collected data encrypted in transit? | **Yes** | Everything uses HTTPS. |
| Do you provide a way for users to request that their data is deleted? | **Yes** | In the app (Settings > Account > Delete account) and on the web page above, plus email. |
| Does the app use data for advertising? | **No** | No ad SDK exists in the app. |

## 2. Data types

"Collected" means it leaves the phone (to us or to a service that works for us). Since sync, the account's pantry, own recipes, plans, shopping list and favourites DO leave the phone (one copy per account on our server). Only the reminder settings and the server catalog stay out.

| Play category and type | Collected? | Shared? | Required or optional | Purpose |
|---|---|---|---|---|
| **Personal info > Email address** | Yes (Google and email accounts) | No | Optional (a guest has none) | Account management |
| **Personal info > Name** | Yes (Google's profile name reaches Firebase when signing in with Google; the app does not use it) | No | Optional | Account management |
| **Personal info > User IDs** (Firebase id, our own account id) | Yes | No | Required | Account management, app functionality |
| **App activity > App interactions** (which server recipes were chosen for a list, and when) | Yes | No | Required to make a list | App functionality (the 72-hour rule and unlocking recipes) |
| **App activity > Other user-generated content** (pantry, own recipes with their steps, weekly plans, shopping list, favourites: the synced copy) | Yes | No | Not optional (sync runs automatically when signed in) | App functionality (restore on a new phone, use on a second phone) |
| **Device or other IDs** (IP address in server logs only; the app collects no device or advertising ID) | Yes (see section 4, point 1) | No | Required (technical) | Security / fraud prevention |

Types that are **not collected**: location, contacts, calendar, photos and videos, audio, files and documents (a backup file you save yourself is your own file, not sent to us), health and fitness, financial info (no payments yet), messages, web browsing, crash logs and diagnostics (no crash reporting tool is included).

"Shared": we send data only to services that process it for us (Google Firebase, Cloudflare, Netcup). Play does not count a service provider that works on our behalf as "sharing", so the answer is **No data shared with third parties**.

## 3. Other questions in the same area

| Question | Answer |
|---|---|
| App access: is anything behind a login that reviewers need? | **No special access needed.** The app works with "Продолжи како гостин". Google and email sign-in are optional. (Give reviewers nothing secret; mention guest mode in the note.) |
| Ads | **No ads.** (A placeholder exists in the code but no ad SDK.) |
| Target audience | **16–17 and 18+**, not designed for children (the policy says under 16 is not the target). |
| Families Policy | Not applicable. |
| Content rating questionnaire | Answer "no" to violence, sexual content, gambling, drugs, user-to-user chat and location sharing. Expect a low rating. |
| Permissions | `INTERNET`, `POST_NOTIFICATIONS` (reminders), `RECEIVE_BOOT_COMPLETED` (re-plan reminders after a restart). None needs a special declaration form. |
| Is the app a news, financial, health or government app? | No. |
| Data deletion: what is deleted? | Account, server record, list records, and the account's data on the phone. Nothing is kept. |

## 4. Points that need the user's OK or a check

1. **Server logs with IP addresses.** The web server and Cloudflare see the IP address of every request and keep logs (14 days at our server). Play has no separate "IP address" type. The safest honest choice is to declare **Device or other IDs > collected, not shared, purpose: security and fraud prevention**. Over-declaring is harmless; under-declaring is the risk. Recommended: declare it.
2. **Firebase's own identifiers: checked.** The release app contains only Firebase Authentication and Firebase Common. It does **not** contain Firebase Installations, Analytics, Crashlytics, any ad SDK or the Google data-transport library (checked in the dependency list on 2026-11-25). So the app collects no device or advertising identifiers itself; the only "Device or other IDs" item is the IP address in server logs (point 1).
3. **Name from Google.** Declared because Firebase receives it, even though the app does not use it.
4. **Encryption at rest:** the form only asks about transit. The synced copy sits unencrypted (by the app) in our server database, readable by whoever operates the server. This is fine for the form and the privacy page now says so. If you want, client-side encryption of the copy can be added later; it would change what the server can ever read.
5. **Android Auto Backup:** `allowBackup` is on, so Google may also keep a copy of the app data in the user's own Google backup. Seen in the real test: after a reinstall the phone came back with an older database, which sync then correctly reported as a conflict. This is the user's own backup (not shared with us); it is mentioned in the privacy page.

## 5. What changes later
- **Pro subscription (Play Billing):** add Financial info > Purchase history (collected, for app functionality) and update the privacy policy.
- **Ads (AdMob):** the answers on advertising, Device or other IDs, and sharing all change.
- **Analytics or crash reporting:** App activity and Diagnostics change.
- **Sync** is now included above. If sync ever starts sending reminder settings or other device data, or adds history, update the rows.
- **AI features:** depending on what is sent to the AI, user-generated content and sharing change.
