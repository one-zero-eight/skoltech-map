import secrets
import shutil
from pathlib import Path

import yaml

BASE_DIR = Path(__file__).resolve().parents[1]
SETTINGS_TEMPLATE = BASE_DIR / "settings.example.yaml"
SETTINGS_FILE = BASE_DIR / "settings.yaml"


def get_settings():
    """
    Load and return the settings from `settings.yaml` if it exists.
    """
    if not SETTINGS_FILE.exists():
        raise RuntimeError("❌ No `settings.yaml` found.")

    try:
        with open(SETTINGS_FILE) as f:
            return yaml.safe_load(f) or {}
    except Exception as e:
        raise RuntimeError("❌ No `settings.yaml` found.") from e


def ensure_settings_file():
    """
    Ensure `settings.yaml` exists. If not, copy `settings.yaml.example`.
    """
    if not SETTINGS_TEMPLATE.exists():
        print("❌ No `settings.yaml.example` found. Skipping copying.")
        return

    if SETTINGS_FILE.exists():
        print("✅ `settings.yaml` exists.")
        return

    shutil.copy(SETTINGS_TEMPLATE, SETTINGS_FILE)
    print(f"✅ Copied `{SETTINGS_TEMPLATE}` to `{SETTINGS_FILE}`")


def check_and_generate_session_secret_key():
    """
    Ensure the session_secret_key is set in `settings.yaml`. If missing, generate random one
    """
    settings = get_settings()
    session_secret_key = settings.get("session_secret_key")

    if not session_secret_key or session_secret_key == "...":
        print("⚠️ `session_secret_key` is missing in `settings.yaml`.")
        print("  ➡️ Generate a random one")
        secret = secrets.token_hex(32)
        try:
            with open(SETTINGS_FILE) as f:
                as_text = f.read()
            as_text = as_text.replace("session_secret_key: null", f"session_secret_key: {secret}")
            as_text = as_text.replace("session_secret_key: ...", f"session_secret_key: {secret}")
            with open(SETTINGS_FILE, "w") as f:
                f.write(as_text)
            print("  ✅ `session_secret_key` has been updated in `settings.yaml`.")
        except Exception as e:
            print(f"  ❌ Error updating `settings.yaml`: {e}")

    else:
        print("✅ `session_secret_key` is specified.")


def prepare():
    """
    Prepare the project for the first run.
    """
    ensure_settings_file()
    check_and_generate_session_secret_key()
