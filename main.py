import os
import re
import time
import threading
import requests
import zipfile
import tempfile
import shutil
import base64
from pathlib import Path
from datetime import datetime
from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.scrollview import ScrollView
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.progressbar import ProgressBar
from kivy.uix.popup import Popup
from kivy.clock import Clock
from kivy.storage.jsonstore import JsonStore
from kivy.utils import platform
from kivy.core.window import Window

# Android-specific imports
if platform == 'android':
    from android.permissions import request_permissions, Permission
    from android.storage import primary_external_storage_path
    from plyer import notification
else:
    # Dummy for desktop testing
    def request_permissions(*args, **kwargs):
        pass
    def notification(*args, **kwargs):
        print("Notification:", args, kwargs)

# ---------- Persistent settings (JSON) ----------
class SettingsStore:
    def __init__(self):
        if platform == 'android':
            from android.storage import app_storage_path
            storage_path = app_storage_path()
        else:
            storage_path = '.'
        self.store = JsonStore(os.path.join(storage_path, 'hob_settings.json'))

    def get(self, key, default=None):
        if self.store.exists(key):
            return self.store.get(key)['value']
        return default

    def set(self, key, value):
        self.store.put(key, value=value)

# ---------- Download worker (background thread) ----------
class DownloadWorker(threading.Thread):
    def __init__(self, url, custom_filename, settings, status_callback, progress_callback, done_callback):
        super().__init__()
        self.url = url
        self.custom_filename = custom_filename.strip()  # may be empty
        self.settings = settings
        self.status_callback = status_callback   # (message, is_error)
        self.progress_callback = progress_callback  # 0-100
        self.done_callback = done_callback      # (success, final_path_or_error)
        self._stop_event = threading.Event()

    def stop(self):
        self._stop_event.set()

    def run(self):
        try:
            final_path = self._run_workflow()
            Clock.schedule_once(lambda dt: self.done_callback(True, final_path), 0)
        except Exception as e:
            Clock.schedule_once(lambda dt: self.done_callback(False, str(e)), 0)

    def _status(self, msg, is_error=False):
        Clock.schedule_once(lambda dt: self.status_callback(msg, is_error), 0)

    def _progress(self, percent):
        Clock.schedule_once(lambda dt: self.progress_callback(percent), 0)

    def _get_session(self):
        session = requests.Session()
        session.trust_env = False
        proxy = self.settings.get('proxy', None)
        if proxy:
            session.proxies = {"http": proxy, "https": proxy}
        else:
            session.proxies = {}
        return session

    def _run_workflow(self):
        repo_owner = self.settings.get('repo_owner')
        repo_name = self.settings.get('repo_name')
        token = self.settings.get('github_token')
        if not repo_owner or not repo_name or not token:
            raise Exception("Missing GitHub settings (owner, repo, token)")

        api_base = f"https://api.github.com/repos/{repo_owner}/{repo_name}"
        headers = {"Authorization": f"token {token}", "Accept": "application/vnd.github.v3+json"}

        # 1. Generate folder name (timestamp)
        folder_name = datetime.now().strftime("%m%d%H%M%S")
        self._status(f"Using folder: {folder_name}")

        # 2. Trigger split workflow
        workflow_url = f"{api_base}/actions/workflows/download-split.yml/dispatches"
        payload = {"ref": "main", "inputs": {"file_url": self.url, "folder_name": folder_name}}
        session = self._get_session()
        resp = session.post(workflow_url, json=payload, headers=headers)
        if resp.status_code != 204:
            raise Exception(f"Failed to trigger workflow: {resp.status_code}")
        self._status("Workflow triggered, waiting for completion...")

        # 3. Poll for _complete.txt (every 10s, max 30min)
        start = time.time()
        timeout = 1800
        original_filename = None
        while time.time() - start < timeout:
            if self._stop_event.is_set():
                raise Exception("Cancelled by user")
            url = f"{api_base}/contents/{folder_name}/_complete.txt"
            resp = session.get(url, headers=headers)
            if resp.status_code == 200:
                data = resp.json()
                content = base64.b64decode(data['content']).decode('utf-8')
                for line in content.splitlines():
                    if line.startswith("filename="):
                        original_filename = line.split("=", 1)[1].strip()
                self._status(f"Workflow completed. Original filename: {original_filename}")
                break
            self._progress(20 + (time.time()-start)/timeout * 60)
            time.sleep(10)
        else:
            raise TimeoutError("Workflow did not finish in time")

        # 4. If user didn't provide a custom name, use the one from _complete.txt
        if self.custom_filename:
            final_filename = self.custom_filename
        else:
            final_filename = original_filename if original_filename else "reassembled.bin"

        # 5. Download repository ZIP
        self._status("Downloading repository ZIP...")
        zip_url = f"https://github.com/{repo_owner}/{repo_name}/archive/refs/heads/main.zip"
        session = self._get_session()
        session.headers.update({"Authorization": f"token {token}"})
        resp = session.get(zip_url, stream=True)
        resp.raise_for_status()
        total = int(resp.headers.get('content-length', 0))
        temp_zip = "repo_temp.zip"
        with open(temp_zip, 'wb') as f:
            downloaded = 0
            for chunk in resp.iter_content(chunk_size=8192):
                if self._stop_event.is_set():
                    raise Exception("Cancelled")
                if chunk:
                    f.write(chunk)
                    downloaded += len(chunk)
                    if total:
                        percent = 80 + (downloaded/total)*20
                        self._progress(min(percent, 100))
        self._status("Extracting and reassembling...")

        # 6. Extract and find chunks folder
        with tempfile.TemporaryDirectory() as tmpdir:
            with zipfile.ZipFile(temp_zip, 'r') as zf:
                zf.extractall(tmpdir)
            extract_root = Path(tmpdir)
            chunks_dir = None
            for item in extract_root.rglob(folder_name):
                if item.is_dir():
                    test_dir = item / "chunks"
                    if test_dir.is_dir() and any(test_dir.glob("chunk_*.part")):
                        chunks_dir = test_dir
                        break
            if not chunks_dir:
                raise Exception(f"Chunks folder not found for {folder_name}")

            # Reassemble chunks
            chunk_files = sorted(chunks_dir.glob("chunk_*.part"),
                                 key=lambda f: int(re.search(r'chunk_(\d+)\.part', f.name).group(1)))
            # Internal storage path for Android
            if platform == 'android':
                base_dir = Path(primary_external_storage_path()) / "hob_downloaded"
            else:
                base_dir = Path("hob_downloaded")
            base_dir.mkdir(parents=True, exist_ok=True)
            output_path = base_dir / final_filename
            with open(output_path, 'wb') as outfile:
                for cf in chunk_files:
                    if self._stop_event.is_set():
                        raise Exception("Cancelled")
                    with open(cf, 'rb') as infile:
                        shutil.copyfileobj(infile, outfile)
            os.remove(temp_zip)

        # 7. Trigger delete workflow (cleanup)
        del_url = f"{api_base}/actions/workflows/delete-folder.yml/dispatches"
        del_payload = {"ref": "main", "inputs": {"folder_name": folder_name}}
        session.post(del_url, json=del_payload, headers=headers)
        self._status(f"✅ Saved to: {output_path}")
        self._progress(100)
        return str(output_path)

# ---------- Main App ----------
class HailOfBladesApp(App):
    def build(self):
        self.settings_store = SettingsStore()
        self.worker = None
        self.settings_visible = False

        # Main vertical layout
        main_layout = BoxLayout(orientation='vertical', padding=10, spacing=10)

        # ---- Header with Settings toggle and Telegram ID ----
        header = BoxLayout(size_hint_y=0.08, spacing=10)
        self.settings_btn = Button(text="⚙️ Settings", size_hint_x=0.3)
        self.settings_btn.bind(on_press=self.toggle_settings)
        header.add_widget(self.settings_btn)
        header.add_widget(Label(text="Hail of Blades", bold=True))
        # Telegram ID small (right side)
        self.telegram_label = Label(text="📱 @Hailofblades", size_hint_x=0.3, font_size='12sp', color=(0.7,0.7,0.7,1))
        header.add_widget(self.telegram_label)
        main_layout.add_widget(header)

        # ---- Settings panel (initially hidden) ----
        self.settings_panel = GridLayout(cols=2, spacing=5, size_hint_y=None, height=0)
        self.settings_panel.bind(minimum_height=self.settings_panel.setter('height'))

        # Settings widgets
        self.settings_panel.add_widget(Label(text="GitHub Token:", size_hint_x=0.3))
        self.token_input = TextInput(text=self.settings_store.get('github_token', ''), multiline=False, password=True)
        self.settings_panel.add_widget(self.token_input)

        self.settings_panel.add_widget(Label(text="Repo Owner:", size_hint_x=0.3))
        self.owner_input = TextInput(text=self.settings_store.get('repo_owner', 'soheilins'), multiline=False)
        self.settings_panel.add_widget(self.owner_input)

        self.settings_panel.add_widget(Label(text="Repo Name:", size_hint_x=0.3))
        self.repo_input = TextInput(text=self.settings_store.get('repo_name', 'directandyt'), multiline=False)
        self.settings_panel.add_widget(self.repo_input)

        self.settings_panel.add_widget(Label(text="Proxy (optional):", size_hint_x=0.3))
        self.proxy_input = TextInput(text=self.settings_store.get('proxy', ''), multiline=False)
        self.settings_panel.add_widget(self.proxy_input)

        save_btn = Button(text="Save Settings", size_hint_y=None, height=40)
        save_btn.bind(on_press=self.save_settings)
        self.settings_panel.add_widget(Label())  # spacer
        self.settings_panel.add_widget(save_btn)

        main_layout.add_widget(self.settings_panel)

        # ---- Download area ----
        download_box = BoxLayout(orientation='vertical', spacing=10, size_hint_y=None, height=200)
        download_box.add_widget(Label(text="File URL:", size_hint_y=None, height=30))
        self.url_input = TextInput(multiline=False, hint_text="https://...")
        download_box.add_widget(self.url_input)
        download_box.add_widget(Label(text="Output filename (optional):", size_hint_y=None, height=30))
        self.filename_input = TextInput(multiline=False, hint_text="Leave empty to auto-detect")
        download_box.add_widget(self.filename_input)
        self.start_btn = Button(text="Start Download", size_hint_y=None, height=50)
        self.start_btn.bind(on_press=self.start_download)
        download_box.add_widget(self.start_btn)
        main_layout.add_widget(download_box)

        # ---- Progress bar ----
        self.progress_bar = ProgressBar(max=100, value=0)
        main_layout.add_widget(self.progress_bar)

        # ---- Log area with Clear button ----
        log_header = BoxLayout(size_hint_y=0.07, spacing=10)
        log_header.add_widget(Label(text="Logs:", size_hint_x=0.9))
        clear_btn = Button(text="Clear", size_hint_x=0.1)
        clear_btn.bind(on_press=self.clear_logs)
        log_header.add_widget(clear_btn)
        main_layout.add_widget(log_header)

        self.log_area = ScrollView(size_hint_y=1)
        self.log_label = Label(text="Ready", size_hint_y=None, markup=True, halign='left', valign='top')
        self.log_label.bind(size=self.log_label.setter('text_size'))
        self.log_area.add_widget(self.log_label)
        main_layout.add_widget(self.log_area)

        # Request Android permissions at startup
        if platform == 'android':
            request_permissions([Permission.WRITE_EXTERNAL_STORAGE, Permission.READ_EXTERNAL_STORAGE])
        return main_layout

    def toggle_settings(self, instance):
        self.settings_visible = not self.settings_visible
        if self.settings_visible:
            self.settings_panel.height = self.settings_panel.minimum_height
        else:
            self.settings_panel.height = 0

    def save_settings(self, instance):
        self.settings_store.set('github_token', self.token_input.text.strip())
        self.settings_store.set('repo_owner', self.owner_input.text.strip())
        self.settings_store.set('repo_name', self.repo_input.text.strip())
        self.settings_store.set('proxy', self.proxy_input.text.strip())
        self.update_log("Settings saved.", False)

    def update_log(self, msg, is_error=False):
        color = "[color=FF0000]" if is_error else "[color=00FF00]"
        self.log_label.text += f"\n{color}{msg}[/color]"
        Clock.schedule_once(lambda dt: setattr(self.log_area, 'scroll_y', 0), 0.1)

    def clear_logs(self, instance):
        self.log_label.text = "Ready"

    def start_download(self, instance):
        if self.worker and self.worker.is_alive():
            self.update_log("Download already running, cancel first?", True)
            return
        url = self.url_input.text.strip()
        if not url:
            self.update_log("Please enter a file URL", True)
            return
        custom_filename = self.filename_input.text.strip()
        self.start_btn.text = "Cancel"
        self.start_btn.unbind_on_press(self.start_download)
        self.start_btn.bind(on_press=self.cancel_download)
        self.progress_bar.value = 0
        self.log_label.text = "Starting..."
        self.worker = DownloadWorker(
            url=url,
            custom_filename=custom_filename,
            settings=self.settings_store,
            status_callback=self.update_log,
            progress_callback=self.update_progress,
            done_callback=self.download_finished
        )
        self.worker.start()

    def cancel_download(self, instance):
        if self.worker:
            self.update_log("Cancelling...", False)
            self.worker.stop()
        self.download_finished(False, "Cancelled by user")

    def update_progress(self, value):
        self.progress_bar.value = value

    def download_finished(self, success, message):
        self.start_btn.text = "Start Download"
        self.start_btn.unbind_on_press(self.cancel_download)
        self.start_btn.bind(on_press=self.start_download)
        self.worker = None
        if success:
            self.update_log(f"✅ Process complete. File: {message}", False)
            # Send notification
            if platform == 'android':
                try:
                    notification.notify(
                        title="Hail of Blades",
                        message=f"Download complete! File saved to {message}",
                        app_name="HailOfBlades",
                        timeout=5
                    )
                except Exception as e:
                    print("Notification error:", e)
        else:
            self.update_log(f"Failed or cancelled: {message}", True)

if __name__ == "__main__":
    HailOfBladesApp().run()
