# LootBox Premium Player Redesign

This bundle contains only the native Android player files changed for the LootBox video-player refresh.

## Compatibility
- Package: `com.siraanabin.lootbox`
- Language/UI: Kotlin + Android XML
- Player engine: AndroidX Media3 / ExoPlayer 1.4.1
- Java target: 17

No Gradle dependency, package name, downloader, application manifest, or updater files are changed by this redesign.

## Files to replace

Copy these files into the matching paths in your existing LootBox project:

| Bundle path | Replace in project |
|---|---|
| `app/src/main/java/com/siraanabin/lootbox/PlayerActivity.kt` | Same path |
| `app/src/main/res/layout/activity_player.xml` | Same path |
| `app/src/main/res/drawable/player_overlay_gradient.xml` | Same path (new file) |
| `app/src/main/res/drawable/player_panel.xml` | Same path (new file) |
| `app/src/main/res/drawable/player_control_circle.xml` | Same path (new file) |
| `app/src/main/res/drawable/player_primary_button.xml` | Same path (new file) |
| `app/src/main/res/drawable/player_side_panel.xml` | Same path (new file) |
| `app/src/main/res/drawable/player_gesture_pill.xml` | Same path (new file) |
| `app/src/main/res/drawable/ic_player_play.xml` | Same path (new file) |
| `app/src/main/res/drawable/ic_player_pause.xml` | Same path (new file) |

## Included behavior
- Premium dark player controls with animated show/hide.
- Brightness and volume sliders are part of the same controls overlay, so they hide together.
- Left/right vertical swipe gestures for brightness/volume.
- Horizontal swipe previews the target time and commits a single seek on finger-up to avoid repeated decoder seeks.
- The timeline seekbar supports touch scrubbing and keeps the preview time stable while dragging.
- Double-tap and buttons for ±10 seconds.
- Playback speed cycle: 1×, 1.25×, 1.5×, 2×; long-press temporarily uses 2× and restores the previous speed without recreating the player or resetting its timeline.
- Crisp vector play/pause icons and retained video frame during player reset.
- Screen lock with a dedicated unlock control.
- FIT, FILL and CROP modes.
- Playback position persistence using the existing `lootbox_player` preferences.
- Picture-in-picture action retained.

## Build
Open the existing Android project and run:

```bash
gradle assembleDebug
```

The repository's GitHub Actions workflow also builds the APK automatically on pushes to `main`.

## Important
This is a source replacement bundle, not a standalone Android project. Keep your existing root Gradle files, app Gradle file, manifest, downloader, updater, signing setup, and all unrelated resources unchanged. Always install and test the resulting APK on a device before distributing it.

