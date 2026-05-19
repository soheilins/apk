[app]

# (str) Title of your application
title = Hail of Blades

# (str) Package name
package.name = hailofblades

# (str) Package domain (needs to be unique)
package.domain = org.soheil

# (str) Source code directory (where main.py lives)
source.dir = .

# (list) Source files to include (all .py, .kv, .png, etc.)
source.include_exts = py,png,jpg,kv,atlas,ttf,txt,ini,json

# (list) Version number (semantic)
version = 0.1

# (list) Requirements (python modules + android)
requirements = python3,kivy,requests,android,plyer,pyjnius,urllib3

# (str) Presplash image (optional)
# presplash.filename = %(source.dir)s/presplash.png

# (str) Icon image (place icon.png in the same folder as main.py)
icon.filename = %(source.dir)s/icon.png

# (str) Supported orientation (portrait, landscape, all)
orientation = portrait

# (bool) Use the android SDK and NDK from the automatic download
android.accept_sdk_license = True

# (list) Android permissions
android.permissions = INTERNET, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE

# (int) Android API level (target)
android.api = 30

# (int) Minimum Android API level
android.minapi = 21

# (str) Android NDK version to use
android.ndk = 23b

# (str) Android SDK version
android.sdk = 30

# (str) Android architecture (arm64-v8a is recommended)
android.arch = arm64-v8a

# (bool) Enable private storage (for app settings)
android.private_storage = True

# (list) Gradle dependencies (if needed)
# android.gradle_dependencies = 'com.android.support:appcompat-v7:28.0.0'

# (bool) Enable logcat logging
log_level = 2

# (str) Fullscreen mode (0 = off, 1 = on)
fullscreen = 0

# (str) Window size (desktop only)
# window.size = (800, 600)

# (str) Keyboard style (one of: 'sdl2', 'pygame')
keyboard = sdl2

# (str) Meta data (optional)
# meta_data = ...

# (str) Extra Java classes (if needed)
# java_classes = ...

# (str) Remote debugging (for Android)
# remote.debuggable = False

# (str) Custom AndroidManifest.xml (optional)
# android.manifest_extra = ...

# (bool) Enable AndroidX
android.enable_androidx = True

# (str) Google Services API key (not needed)
# google_api_key = ...

# (list) Extra libraries to include (e.g. libssl.so)
# android.add_src = ...

# (list) Patterns to exclude from APK (e.g. *.pyc)
# android.exclude_patterns = ...

# (list) Services to add (background services)
# android.services = ...

# (list) Activities to add
# android.activities = ...

# (str) Launch mode (standard, singleTop, singleTask, singleInstance)
# android.launch_mode = standard

# (str) Display name shown in launcher
# android.display_name = Hail of Blades
