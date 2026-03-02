"""
Mock proxy para testear GET /app/catalogs en la app Android.

- Intercepta GET /app/catalogs  → devuelve JSON de prueba local
- Todo lo demás                 → proxy transparente al MDW real

Uso:
    py tools/mock_catalogs_proxy.py

Luego en la app (Ajustes > URL del servidor):
    Emulador : http://10.0.2.2:8001/
    Dispositivo físico: http://<IP-de-tu-PC>:8001/

Para encontrar tu IP local:
    ipconfig  (buscar IPv4 de la red Wi-Fi)
"""

import json
import urllib.request
import urllib.error
from http.server import BaseHTTPRequestHandler, HTTPServer

MDW_BASE = "http://167.234.226.219:8000"
PORT = 8001

# ─── Catálogo de prueba ────────────────────────────────────────────────────────
# Editá estos valores para probar que la app los refleja sin actualizar la APK.
MOCK_CATALOGS = {
    "failure_observations": [
        "Sin falla",
        "Vinculada",
        "Display roto",
        "No enciende",
        "Tamper",
        "TEST - Falla de prueba 1",
        "TEST - Falla de prueba 2"
    ],
    "qa_options": [
        "Falta de limpieza: Carcasa posterior",
        "Falta de limpieza: Carcasa frontal",
        "Daño estetico: Display",
        "Faltan tornillos",
        "TEST - Observación QA de prueba"
    ],
    "recovered_parts": [
        "Carcasa frontal",
        "Carcasa posterior",
        "Bateria",
        "Display",
        "TEST - Repuesto de prueba"
    ],
    "statuses": [
        "Revisión inicial",
        "Reparación Técnica",
        "Limpieza",
        "Testeo",
        "Irreparable"
    ],
    "substatus_reparacion": [
        "Carga de firmware",
        "Reparación",
        "Carga de firmware + Inyección"
    ]
}
# ──────────────────────────────────────────────────────────────────────────────


class ProxyHandler(BaseHTTPRequestHandler):

    def log_message(self, format, *args):
        method = self.command
        path = self.path
        if path == "/app/catalogs":
            print(f"  [MOCK]  {method} {path}")
        else:
            print(f"  [PROXY] {method} {path}")

    def _send_json(self, data: dict, status: int = 200):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _proxy(self):
        url = MDW_BASE + self.path
        headers = {k: v for k, v in self.headers.items()
                   if k.lower() not in ("host", "content-length")}

        body = None
        length = int(self.headers.get("Content-Length", 0))
        if length:
            body = self.rfile.read(length)

        req = urllib.request.Request(url, data=body, headers=headers, method=self.command)
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                resp_body = resp.read()
                self.send_response(resp.status)
                for k, v in resp.getheaders():
                    if k.lower() in ("content-type", "content-length"):
                        self.send_header(k, v)
                self.end_headers()
                self.wfile.write(resp_body)
        except urllib.error.HTTPError as e:
            resp_body = e.read()
            self.send_response(e.code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(resp_body)))
            self.end_headers()
            self.wfile.write(resp_body)
        except Exception as e:
            error = json.dumps({"error": str(e)}).encode()
            self.send_response(502)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(error)))
            self.end_headers()
            self.wfile.write(error)

    def do_GET(self):
        if self.path == "/app/catalogs":
            self._send_json(MOCK_CATALOGS)
        else:
            self._proxy()

    def do_POST(self):
        self._proxy()

    def do_PATCH(self):
        self._proxy()

    def do_PUT(self):
        self._proxy()

    def do_DELETE(self):
        self._proxy()


if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", PORT), ProxyHandler)
    print(f"Proxy corriendo en puerto {PORT}")
    print(f"  → /app/catalogs  : respuesta MOCK local")
    print(f"  → resto          : proxy a {MDW_BASE}")
    print()
    print("Configurá la app (Ajustes > URL del servidor):")
    print("  Emulador         : http://10.0.2.2:8001/")
    import socket
    local_ip = socket.gethostbyname(socket.gethostname())
    print(f"  Dispositivo físico: http://{local_ip}:8001/")
    print()
    print("Para probar: editá MOCK_CATALOGS en este archivo.")
    print("Ctrl+C para detener.\n")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nServidor detenido.")
