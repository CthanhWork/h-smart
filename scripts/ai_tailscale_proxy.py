import os
import socket
import threading


LISTEN_HOST = os.environ.get("AI_PROXY_HOST", "0.0.0.0")
LISTEN_PORT = int(os.environ.get("AI_PROXY_PORT", "18002"))
TARGET_HOST = os.environ.get("AI_PROXY_TARGET_HOST", "127.0.0.1")
TARGET_PORT = int(os.environ.get("AI_PROXY_TARGET_PORT", "8002"))
BUFFER_SIZE = 1024 * 1024


def relay(source, destination):
    try:
        while True:
            chunk = source.recv(BUFFER_SIZE)
            if not chunk:
                break
            destination.sendall(chunk)
    except OSError:
        pass
    finally:
        try:
            destination.shutdown(socket.SHUT_WR)
        except OSError:
            pass


def handle(client):
    upstream = None
    try:
        upstream = socket.create_connection((TARGET_HOST, TARGET_PORT), timeout=15)
        upstream.settimeout(None)
        client.settimeout(None)

        request_relay = threading.Thread(target=relay, args=(client, upstream), daemon=True)
        response_relay = threading.Thread(target=relay, args=(upstream, client), daemon=True)
        request_relay.start()
        response_relay.start()
        request_relay.join()
        response_relay.join()
    except OSError as exception:
        print(f"AI proxy connection failed: {exception}", flush=True)
    finally:
        client.close()
        if upstream is not None:
            upstream.close()


with socket.create_server((LISTEN_HOST, LISTEN_PORT)) as server:
    print(
        f"AI TCP proxy listening on {LISTEN_HOST}:{LISTEN_PORT} -> {TARGET_HOST}:{TARGET_PORT}",
        flush=True,
    )
    while True:
        client_socket, _ = server.accept()
        threading.Thread(target=handle, args=(client_socket,), daemon=True).start()
