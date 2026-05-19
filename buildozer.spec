[app]

title = Hail of Blades
package.name = hailofblades
package.domain = org.soheil
source.dir = .
source.include_exts = py,png,jpg,kv,atlas,ttf,txt,ini,json
version = 0.1
requirements = python3,kivy,requests,android,plyer,pyjnius,urllib3
icon.filename = %(source.dir)s/icon.png
orientation = portrait
android.accept_sdk_license = True
android.permissions = INTERNET, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE
android.api = 30
android.minapi = 21
android.ndk = 27c
android.sdk = 30
android.arch = arm64-v8a
android.private_storage = True
log_level = 2
fullscreen = 0
keyboard = sdl2
android.enable_androidx = True
