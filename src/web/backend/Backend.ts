import { type Accessor, createSignal } from "solid-js";

import type { Command, InMessage, OutMessage, Request, RequestMain } from "./core";
import { requestKindToResponseKind } from "./core";
import { WSCloseCode } from "./ws";

const HOST = import.meta.env.DEV ? "localhost:4110" : window.location.host;
const WS_ENDPOINT_ADDR = `ws://${HOST}/ws`;
const RECONNECT_INTERVAL_MS = 5000;
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
  | "initial"
  | "first-connecting"
  | "connected"
  | "just-disconnected"
  | "reconnecting"
  | "disconnected-wont-reconnect";

export class Backend {
  #cbs: Callbacks;
  #ws?: WebSocket;
  #pendingRequests: Map<number, PendingRequest>;

  #connectionStateSignal = createSignal<BackendConnectionState>("initial");
  #connectionState = this.#connectionStateSignal[0];
  #setConnectionState = this.#connectionStateSignal[1];

  constructor(cbs: Callbacks) {
    this.#cbs = cbs;
    this.#pendingRequests = new Map();
    this.#connect();
    this.#setConnectionState("first-connecting");
  }

  reconnect() {
    const state = this.#connectionState();
    if (state === "just-disconnected" || state === "disconnected-wont-reconnect") {
      this.#setConnectionState("reconnecting");
      this.#connect();
    }
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

  get connectionState(): Accessor<BackendConnectionState> {
    return this.#connectionState;
  }

  static customCSSUrl(): string {
    return `http://${HOST}/custom.css`;
  }

  #connect() {
    const state = this.#connectionState();
    if (state !== "initial" && state !== "reconnecting") {
      return;
    }
    const ws = new WebSocket(WS_ENDPOINT_ADDR);
    ws.onopen = this.#handleConnectionOpen.bind(this);
    ws.onmessage = this.#handleMessage.bind(this);
    ws.onclose = this.#handleConnectionClose.bind(this);
    ws.onerror = this.#handleConnectionError.bind(this);
    this.#ws = ws;
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

  #handleConnectionOpen() {
    this.#setConnectionState("connected");
  }

  #handleConnectionClose(event: CloseEvent) {
    if (event.code !== WSCloseCode.AbnormalClosure) {
      // Isn't a failed connection attempt
      this.#setConnectionState("just-disconnected");
    }
    if (event.code === WSCloseCode.SupersededByAnotherClient) {
      // Don't reconnect
      this.#setConnectionState("disconnected-wont-reconnect");
      return;
    }

    // This point is reached after disconnecting AND after a failed connection attempt

    // If just disconnected, try to reconnect immediately, then try it recurrently
    switch (this.#connectionState()) {
      case "just-disconnected":
        this.reconnect();
        break;
      case "reconnecting":
        setTimeout(this.#connect.bind(this), RECONNECT_INTERVAL_MS);
        break;
    }
  }

  #handleConnectionError() {
    const state = this.#connectionState();
    if (state === "first-connecting" || state === "reconnecting") {
      this.reconnect();
    }
  }
}
