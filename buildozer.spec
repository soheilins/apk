[app]
title = Hail of Blades
package.name = hailofblades
package.domain = org.soheil
source.dir = .
source.include_exts = py,png,jpg,kv,atlas,ttf,txt,ini,json

version = 0.1
requirements = python3,kivy,requests,android,urllib3,pyjnius
osx.python_version = 3
osx.kivy_version = 2.2.0

# Android-specific
android.permissions = INTERNET, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE
android.api = 30
android.minapi = 21
android.ndk = 23b
android.sdk = 30
android.arch = arm64-v8a

# For storing settings on Android
android.private_storage = True

# Log level
log_level = 2

# (Optional) icon and splash screen – create your own
# icon.filename = %(source.dir)s/icon.png
# presetup = bash -c "cp -f %(source.dir)s/icon.png ..."
