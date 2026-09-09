# مربی سولفژ — Solfa Coach v1.1.0

Native Android app with microphone pitch detection and Free/Premium access.

## Free access
- Single notes: Do, Re, Mi
- Pattern section: first/easy pattern
- Songs: first song

## Premium
- 1 month: 199,000 toman
- 3 months: 499,000 toman
- 6 months: 799,000 toman
- Unlocks all notes, patterns and songs until subscription expiry.

## Payment mode in this test build
`DEMO_PAYMENT = true` in `MainActivity.kt`.
Selecting a plan simulates successful payment and stores the subscription expiry locally.
This is intentionally for testing the full UX.

## Production payment
Before publishing:
1. Change `DEMO_PAYMENT = false`.
2. Add a secure HTTPS backend.
3. Create payment request on the backend with your chosen Iranian payment gateway.
4. Open the returned gateway URL in Android.
5. After callback, verify the payment token on the backend.
6. Only after server verification should the app receive and store the subscription expiry.

Never activate premium only from a browser/deep-link `status=ok`, because that can be forged.

## Branding
- Installed app label: `مربی سولفژ`
- Launcher icon: music note
- Bardia Arman developer logo shown at top of the app.

## Build APK
GitHub -> Actions -> Build Android APK -> Run workflow.
Download the artifact `SolfaCoach-debug-apk`.
