# Media Widget

A minimal, resizable, monochrome Android home-screen widget that controls **whatever is playing**: YouTube,
Spotify, KDE Connect, podcasts, browsers, and any other app that publishes a media session.

Built with Kotlin and Jetpack Glance.

<!-- Add a screenshot here: ![Media Widget](docs/screenshot.png) -->

## Features

- Works with **all** media apps, not one at a time. It follows whichever session is playing.
- Circular thumbnail (colour), title, artist, and a **"Playing on <app>"** label.
- Progress bar with elapsed / total time.
- Play/pause plus either **rewind/forward (seek 5-30 s)** or **previous/next track**.
- Tap the widget to open the playing app.
- Resizes to any size; the layout adapts (thumbnail and extra text drop away on small sizes).
- Settings page for transparency, frost tint, corner radius, light/dark/system theme, progress refresh
  speed (instant events only, 0.5 s, 1 s, 2 s, 5 s) and button behaviour.

## Requirements

- Android 12 (API 31) or newer
- Android Studio (Ladybug or newer) with JDK 17, or any machine with JDK 17 for command-line builds

## Build

```bash
git clone <your-repo-url>
cd <repo>
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew installDebug           # install on a connected device
```

Or open the folder in Android Studio and press Run. A GitHub Actions workflow also builds a debug APK on every push.

## Setup on the phone

1. Install the app, then **add the widget** from your launcher's widget picker.
2. Tap the widget (or open the app) and grant **Notification access**. Android requires this before any
   app may read other apps' media sessions.
3. Recommended: set the app's battery usage to **Unrestricted** so the system doesn't stop the listener.
   Samsung and Xiaomi devices are the most aggressive about this.
4. Long-press the widget → widget settings (or open the app icon) to customise it.

Permissions used:

| Permission | Why |
|---|---|
| Notification listener (user-granted) | Read media sessions from all apps |
| `QUERY_ALL_PACKAGES` | Show the app name and open the app when you tap the widget |

Nothing leaves the device. There is no internet permission.

## How it works

```
 media apps ──MediaSession──► MediaListenerService ──► MediaStore (state)
                                  (notification                │
                                   listener)                   ▼
                                                      refreshWidgets()
                                                               │
                                              MediaWidget (Jetpack Glance)
                                                               ▲
                              MediaAction (play/pause/seek) ───┘ taps → MediaController
```

- `MediaListenerService` is a `NotificationListenerService`. Being enabled lets it call
  `MediaSessionManager.getActiveSessions()` and register callbacks on every session.
- `MediaStore` holds the current track, artwork, playback state and position.
- `MediaWidget` draws that state. Position is extrapolated from the last playback state, so the bar is
  accurate between refreshes.
- Glance only redraws a live widget when its observable state changes, so `refreshWidgets()` bumps a counter in
  the widget's own state on every update. This is what makes play/pause update immediately.
- `SettingsActivity` is both the launcher screen and the widget's configure/reconfigure screen.

## Project layout

```
app/src/main/java/com/example/mediawidget/
  MediaListenerService.kt   session tracking, artwork, refresh ticker
  MediaStore.kt             shared playback state
  MediaWidget.kt            Glance UI, palette, button actions
  SettingsActivity.kt       Compose settings screen
  WidgetPrefs.kt            SharedPreferences-backed settings
```

## Limitations

- **No real blur.** Android doesn't let app widgets blur what's behind them. The "frost tint" is a tint only.
- A widget can't hide itself or collapse its space; it can only go empty.
- Controls only work if the media app implements transport controls properly.
- Progress refreshes on a timer (down to 0.5 s), not continuously.

## Ideas / roadmap

- Follow the media *notification* so dismissed players disappear
- "Hide when idle" mode
- Tap the "Playing on" label to switch between multiple active players

## Changing the package name

The package is `com.example.mediawidget`. Before publishing, rename it in `app/build.gradle.kts`
(`namespace`, `applicationId`), the source folders, the `package` lines, and the `.SettingsActivity` reference in
`res/xml/media_widget_info.xml`.

## License

MIT, see [LICENSE](LICENSE).
