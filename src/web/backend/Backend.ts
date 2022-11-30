import { createSignal, type Accessor } from "solid-js";

import type { Command, InMessage, Notification, OutMessage, Request, RequestMain } from "./core";
import { requestKindToResponseKind } from "./core";
import { WSCloseCode } from "./ws";

const HOST = import.meta.env.DEV ? "localhost:4110" : window.location.host;
const WS_ENDPOINT_ADDR = `ws://${HOST}/ws`;
const RECONNECT_INTERVAL_MS = 5000;
const CONNECTION_TIMEOUT = 1000;
const REQUEST_TIMEOUT = 3000;

type PendingRequest = {
  kind: Request["kind"],
  resolve: InMessagePromiseResolveFn,
};
type InMessagePromiseResolveFn = Parameters<ConstructorParameters<typeof Promise<InMessage>>[0]>[0];

type Callbacks = {
  onMessage: (msg: InMessage) => void,
};

export type BackendConnectionState =
  | "connecting"
  | "connected"
  | "disconnected-wont-reconnect";

export class Backend {
  #cbs: Callbacks;
  #ws?: WebSocket;
  #pendingRequests: Map<number, PendingRequest>;

  #connectionStateSignal = createSignal<BackendConnectionState>("connecting");
  #connectionState = this.#connectionStateSignal[0];
  #setConnectionState = this.#connectionStateSignal[1];

  constructor(cbs: Callbacks) {
    this.#cbs = cbs;
    this.#pendingRequests = new Map();
    void this.connect();
  }

  command(command: Command | Command["kind"]) {
    this.#send({
      kind: "command",
      body: typeof command === "string" ? { kind: command } : command,
    });
  }

  request<T extends InMessage>(request: RequestMain): Promise<T> {
    const timestamp = Date.now();

    const promise = new Promise<InMessage>((resolve, reject) => {
      this.#pendingRequests.set(timestamp, { kind: request.kind, resolve });

      setTimeout(() => {
        this.#pendingRequests.delete(timestamp);
        reject(new Error("Request timed out"));
      }, REQUEST_TIMEOUT);
    });
    const body = { timestamp, ...request };
    this.#send({ kind: "request", body });

    return promise as Promise<T>; // We verify the response kind later, before resolving
  }

  notify(notification: Notification) {
    this.#send({
      kind: "notification",
      body: notification,
    });
  }

  get connectionState(): Accessor<BackendConnectionState> {
    return this.#connectionState;
  }

  static customCSSUrl(): string {
    return `http://${HOST}/custom.css`;
  }

  async connect(): Promise<void> {
    this.#setConnectionState("connecting");
    try {
      const ws = await this.#doConnect(WS_ENDPOINT_ADDR);
      this.#setConnectionState("connected");
      ws.addEventListener("message", this.#handleMessage.bind(this));
      ws.addEventListener("close", this.#handleConnectionClose.bind(this));
      this.#ws = ws;
    } catch (_event) {
      // Timed out, try again
      setTimeout(() => void this.connect(), RECONNECT_INTERVAL_MS);
    }
  }

  #doConnect(endpoint: string): Promise<WebSocket> {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(endpoint);
      ws.addEventListener("open", handleOpen);
      ws.addEventListener("error", handleError);

      function handleOpen() {
        resolve(ws);
        cleanup();
      }

      function handleError(event: Event) {
        reject(event);
        cleanup();
      }

      const timeoutTimeout = setTimeout(() => {
        reject(new Error("WebSocket connection timed out"));
        cleanup();
        ws.close();
      }, CONNECTION_TIMEOUT);

      function cleanup() {
        clearTimeout(timeoutTimeout);
        ws.removeEventListener("error", handleError);
      }
    });
  }

  #send(msg: OutMessage) {
    if (this.#ws && this.#connectionState() === "connected") {
      this.#ws.send(JSON.stringify(msg));
    }
  }

  #handleMessage(e: MessageEvent) {
    const msg = JSON.parse(e.data as string) as InMessage;
    if (msg.kind === "response") {
      const req = this.#pendingRequests.get(msg.requestTimestamp);
      if (!req) {
        console.error("Received unexpected request response:", msg);
        return;
      }
      const inner = msg.innerMessage;
      const expectedResponseKind = requestKindToResponseKind[req.kind];
      if (inner.kind !== expectedResponseKind) {
        console.error(
          "Received request response of unexpected kind."
          + ` Got '${inner.kind}', expected ${expectedResponseKind}`
        );
        return;
      }
      req.resolve(inner);
      this.#pendingRequests.delete(msg.requestTimestamp);
    } else {
      this.#cbs.onMessage(msg);
    }
  }

  #handleConnectionClose({ code }: CloseEvent) {
    if (
      code === WSCloseCode.SupersededByAnotherClient
      || code === WSCloseCode.NoStatusReceived
      || code === WSCloseCode.AbnormalClosue
    ) {
      // if (!import.meta.env.DEV) {
        this.#setConnectionState("disconnected-wont-reconnect");
      // }
      return;
    }
    void this.connect();
  }
}
