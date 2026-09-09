# Solfa Coach Android

Native Android solfege trainer with real microphone pitch detection.

## Features
- Single-note training: Do, Re, Mi, Fa, Sol, La, Ti, upper Do
- Plays the target note
- Records from the Android microphone using AudioRecord
- Detects pitch/frequency instead of speech-to-text
- Shows detected Hz, note name and cents error
- Guides the singer higher/lower until the note is correct
- Pattern practice with three difficulty levels
- Famous melodies: Ode to Joy, Twinkle Twinkle, Happy Birthday, Amazing Grace
- Every section has its own microphone practice mode

## Build APK with GitHub (no Android Studio needed)
1. Create a new GitHub repository.
2. Upload ALL files and folders from this project, including the `.github` folder.
3. Commit to `main`.
4. Open GitHub -> Actions -> `Build Android APK`.
5. Press `Run workflow` if it did not run automatically.
6. Wait for the green check mark.
7. Open the workflow run.
8. Under `Artifacts`, download `SolfaCoach-debug-apk`.
9. Extract the ZIP and install `app-debug.apk` on your Android phone.

## First launch
Android asks for Microphone permission. Choose **Allow while using the app**.

For best testing accuracy:
- Use a quiet room.
- Hold a vowel such as "Ah" steadily for 1–2 seconds.
- If playing the target note through the phone speaker, wait until playback finishes before singing.
- Headphones are even better because the microphone will not hear the reference tone.

## Pitch tolerance
The demo accepts a note when it stays within approximately ±35 cents for several consecutive detection frames.

## Package
`com.bardia.solfacoach`
