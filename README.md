# TV Home — a tvOS-style launcher for Android TV

Kotlin + Jetpack Compose. Registers as a HOME app, so Android TV offers it as a
launcher replacement.

## Build & run
1. Open this folder in Android Studio (Ladybug or newer). Let Gradle sync.
2. Run on an Android TV device or an Android TV emulator image (API 26+).
3. Press the Home button; choose "TV Home" and "Always" when asked.
   - On devices that don't prompt: Settings > Apps > Default apps > Home app.
   - To go back: clear defaults for TV Home in Settings > Apps.

## What it does (tvOS-inspired)
- **Favorites row + All Apps row**, like the tvOS Home Screen
- **Hero area** shows the focused app's banner and name; the background tints to its colour
- **Focus effect**: focused card scales up, lifts with a shadow and gets a white ring
- **Hold OK on an app**: add/remove Favorites, move left/right, hide/unhide, app info, uninstall
- **Clock/date** and a Settings button at the top
- Updates live when apps are installed or removed
- Home or Back returns to the first app; Back never exits the launcher
- Layout (order, favorites, hidden) is saved across reboots

## Not included (tvOS features that need system access or media)
Top Shelf content from apps, aerial screensavers, Control Center, Siri-style voice,
folders, multi-user switching, and true tilt/parallax icon layers.

## Files
- `ui/HomeScreen.kt` — all UI
- `data/AppRepository.kt` — finds apps (TV and phone-style), saves layout
- `LauncherViewModel.kt`, `MainActivity.kt` — state and Home-button handling
