"""Compile the release with the persistent signer authorized by GitHub OIDC."""
import base64
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

AUDIENCE = "https://modeliseur-trellis-mcp.onrender.com/coran/ci/signing"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        return None


def fetch_json(url, bearer):
    request = urllib.request.Request(url, headers={"Authorization": "Bearer " + bearer})
    with urllib.request.build_opener(NoRedirect).open(request, timeout=45) as response:
        return json.load(response)


def signing_config():
    url = os.environ["ACTIONS_ID_TOKEN_REQUEST_URL"]
    token_url = url + ("&" if "?" in url else "?") + urllib.parse.urlencode({"audience": AUDIENCE})
    for attempt in range(7):
        try:
            identity = fetch_json(token_url, os.environ["ACTIONS_ID_TOKEN_REQUEST_TOKEN"])["value"]
            return fetch_json(AUDIENCE, identity)
        except urllib.error.HTTPError as error:
            if error.code not in {404, 429, 500, 502, 503, 504} or attempt == 6:
                raise SystemExit(f"Service de signature Coran : HTTP {error.code}") from None
            print(f"Service de signature en cours de démarrage (HTTP {error.code}).", flush=True)
        except (urllib.error.URLError, TimeoutError):
            if attempt == 6:
                raise SystemExit("Service de signature Coran injoignable.") from None
            print("Nouvelle tentative de connexion au service de signature.", flush=True)
        time.sleep(min(10 + attempt * 6, 30))


def main():
    config = signing_config()
    try:
        keystore = base64.b64decode(config["keystoreBase64"], validate=True)
        env = {name: config[field] for name, field in (
            ("KEYSTORE_PASSWORD", "storePassword"),
            ("KEY_ALIAS", "alias"),
            ("KEY_PASSWORD", "keyPassword"),
        )}
        if any(not isinstance(value, str) or not value for value in env.values()):
            raise ValueError("Signature invalide")
    except (ValueError, TypeError, KeyError):
        raise SystemExit("Configuration de signature Coran invalide.") from None
    for password in {env["KEYSTORE_PASSWORD"], env["KEY_PASSWORD"]}:
        print("::add-mask::" + password, flush=True)
    with tempfile.TemporaryDirectory(prefix="coran-signing-", dir=os.environ["RUNNER_TEMP"]) as folder:
        path = Path(folder) / "coran.p12"
        with os.fdopen(os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600), "wb") as stream:
            stream.write(keystore)
        subprocess.run(
            ["./gradlew", "--no-daemon", ":app:assembleRelease"],
            env={**os.environ, **env, "KEYSTORE_PATH": str(path)},
            check=True,
        )


if __name__ == "__main__":
    main()
