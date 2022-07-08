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
  onConnectedChange: (connected: boolean) => void,
  onMessage: (msg: InMessage) => void,
};

export class Backend {
  #cbs: Callbacks;
  #ws?: WebSocket;
  #pendingRequests: Map<number, PendingRequest>;
  #firstReconnectAttempt = true;

  constructor(cbs: Callbacks) {
    this.#cbs = cbs;
    this.#pendingRequests = new Map();
  }

  connect() {
    const ws = new WebSocket(WS_ENDPOINT_ADDR);
    ws.onopen = this.#handleConnectionOpen.bind(this);
    ws.onmessage = this.#handleMessage.bind(this);
    ws.onclose = this.#handleConnectionClose.bind(this);
    this.#ws = ws;
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

  static customCSSUrl(): string {
    return `http://${HOST}/custom.css`;
  }

  #send(msg: OutMessage) {
    this.#ws && this.#ws.send(JSON.stringify(msg));
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
    this.#cbs.onConnectedChange(true);
    this.#firstReconnectAttempt = true;
  }

  #handleConnectionClose(event: CloseEvent) {
    if (event.code !== WSCloseCode.AbnormalClosure) {
      // Isn't a failed connection attempt
      this.#cbs.onConnectedChange(false);
    }
    if (event.code === WSCloseCode.SupersededByAnotherClient) {
      // Don't reconnect
      // TODO: Don't show "Waiting for backend connection…" message when we aren't actually
      //       trying to connect. Show a "Reconnect" button instead
      return;
    }

    // This is executed after disconnecting and after a failed connection attempt

    if (this.#firstReconnectAttempt) {
      this.connect();
      this.#firstReconnectAttempt = false;
    } else {
      setTimeout(this.connect.bind(this), RECONNECT_INTERVAL_MS);
    }
  }
}
