"""Préparer exclusivement l'AAB Google Play signé avec le signataire Coran existant.

Aucune clé ou mot de passe ne doit être journalisé ni conservé dans GitHub.
"""
import base64
import os
from pathlib import Path
import subprocess
import tempfile

from assemble_signed_release import signing_config


def main():
    config = signing_config()
    try:
        keystore = base64.b64decode(config["keystoreBase64"], validate=True)
        env = {
            "KEYSTORE_PASSWORD": config["storePassword"],
            "KEY_ALIAS": config["alias"],
            "KEY_PASSWORD": config["keyPassword"],
        }
        if not keystore or any(not isinstance(value, str) or not value for value in env.values()):
            raise ValueError("Configuration incomplète")
    except (KeyError, ValueError, TypeError):
        raise SystemExit("Signataire Coran indisponible ou invalide.") from None

    for secret in set((env["KEYSTORE_PASSWORD"], env["KEY_PASSWORD"])):
        print("::add-mask::" + secret, flush=True)

    with tempfile.TemporaryDirectory(prefix="coran-play-", dir=os.environ["RUNNER_TEMP"]) as folder:
        path = Path(folder) / "coran-upload.p12"
        with os.fdopen(os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), "wb") as stream:
            stream.write(keystore)
        subprocess.run(
            ["./gradlew", "--no-daemon", ":playApp:bundleRelease"],
            env={**os.environ, **env, "KEYSTORE_PATH": str(path)},
            check=True,
        )


if __name__ == "__main__":
    main()
