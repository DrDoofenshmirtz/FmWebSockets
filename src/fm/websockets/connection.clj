(ns
  ^{:doc "Stuff for handling HTML5 WebSocket connections."
    :author "Frank Mosebach"}
  fm.websockets.connection
  (:refer-clojure :exclude [send])
  (:require
    [fm.websockets.protocol :as prot])
  (:use
    [clojure.contrib.logging :only (debug)]
    [fm.core.exception :only (exception-chain caused-by)])
  (:import
    (java.util UUID)
    (java.net Socket)
    (fm.websockets.exceptions ConnectionFailed ConnectionClosed EndOfData)))

(defn- throw-connection-failed [^Socket socket exception]
  (let [error-message
        (format "Failed to connect to WebSocket client (remote address: %s)!"
                (.getRemoteSocketAddress socket))]
    (debug error-message)
    (throw (ConnectionFailed. error-message exception))))

(defn- throw-connection-closed
  ([socket]
    (throw-connection-closed socket nil))
  ([^Socket socket exception]
    (let [error-message
          (format "WebSocket connection has been closed (remote address: %s)!"
                  (.getRemoteSocketAddress socket))]
      (debug error-message)
      (throw (if exception
               (ConnectionClosed. error-message exception)
               (ConnectionClosed. error-message))))))

(defn- socket-streams [^Socket socket]
  (try
    [(.getInputStream socket) (.getOutputStream socket)]
    (catch Exception x
      (throw-connection-failed socket x))))

(defn- read-connect-request [^Socket socket input-stream]
  (try
    (if-let [connect-request (prot/read-connect-request input-stream)]
      connect-request
      (throw-connection-failed
        socket
        (IllegalArgumentException. "Connect request is empty!")))
    (catch Exception x
      (throw-connection-failed socket x))))

(defn- write-connect-response [^Socket socket output-stream connect-request]
  (try
    (prot/write-connect-response output-stream connect-request)
    (catch Exception x
      (throw-connection-failed socket x))))

(defn- guarded-output [^Socket socket output-stream]
  (let [guard (Object.)]
    (fn [output-accessor]
      (locking guard
        (try
          (output-accessor output-stream)
          (catch Exception x
            (if (.isClosed socket)
              (throw-connection-closed socket x)
              (throw x))))))))

(defn- make-output [socket output-stream]
  (vary-meta (guarded-output socket output-stream) assoc :type ::output))

(defn- fragment-seq [socket message]
  (lazy-seq
    (try
      (if (seq message)
        (cons (first message) (fragment-seq socket (rest message))))
      (catch Exception x
        (if-let [x (caused-by x EndOfData)]
          (throw-connection-closed socket x)
          (throw x))))))

(defn- message-seq [socket input-stream]
  (let [message-seq (prot/message-seq input-stream)
        wrapped-seq (fn wrapped-seq [message-seq]
                      (lazy-seq
                        (if (seq message-seq)
                          (try
                            (cons (fragment-seq socket (first message-seq))
                                  (wrapped-seq (rest message-seq)))
                            (catch Exception x
                              (if (.isClosed socket)
                                (throw-connection-closed socket x)
                                (throw x))))
                          (throw-connection-closed socket))))]
    (wrapped-seq message-seq)))

(defn- make-connection [connect-request socket input-stream output-stream]
  (vary-meta
    {:id       (str (UUID/randomUUID))
     :request  connect-request
     :messages (message-seq socket input-stream)
     :output   (make-output socket output-stream)}
    assoc :type ::connection))

(defn connect
  "Tries to establish a WebSocket connection, assuming the given socket is
  connected to a WebSocket client.

  Returns a map {:id      uuid-string
                 :request {:request-line    connect-request-line
                           :request-headers connect-request-headers}
                 :messages lazy-seq-of-incoming-messages
                 :output   guarded-access-to-connection-output}
  if the connection has been successfully established.

  Throws fm.websockets.exceptions.ConnectionFailed if a connection cannot
  be successfully established.

  Throws fm.websockets.exceptions.ConnectionClosed if the socket has been
  closed."
  [^Socket socket]
  (let [[input-stream output-stream] (socket-streams socket)
        connect-request (read-connect-request socket input-stream)]
    (debug (format "Connecting to WebSocket client (remote address: %s)..."
                   (.getRemoteSocketAddress socket)))
    (debug (format "Request: %s" connect-request))
    (write-connect-response socket output-stream connect-request)
    (debug "Connected to WebSocket client.")
    (make-connection connect-request socket input-stream output-stream)))

(defn caused-by-closed-connection?
  "Returns true if the given connection might have been caused by a closed
  WebSocket connection."
  [exception]
  (let [causes (exception-chain exception)]
    (some #(or (instance? ConnectionClosed %)
               (instance? EndOfData %))
          causes)))

(defn take-message
  "Takes the next message from the given connection's lazy message sequence.
  Returns a collection of [next-message connection-with-remaining-messages]."
  [connection]
  [(first (:messages connection))
   (assoc connection :messages (rest (:messages connection)))])

(defn drop-messages
  "Returns a connection with all messages dropped from its message sequence."
  [connection]
  (assoc connection :messages ()))

(defmulti output
  "Extracts a suitable connection output from a given target."
  {:private true}
  type)

(defmethod output ::connection [target]
  (:output target))

(defmethod output ::output [target]
  target)

(defmethod output :default [target]
  (throw (IllegalArgumentException. "Illegal output target!")))

(defmacro with-output-of
  "Evaluates the given body with the target's output bound to '%'."
  {:private true} 
  [target & body]
 `(let [output# (output ~target)]
    (output# (fn [~'%] ~@body))))

(defmulti send-content
  "Sends content to an output stream using a send method suitable
  for the content type."
  {:private true}
  (fn [output-stream content] (type content)))

(defmethod send-content String [output-stream content]
  (prot/send-text-message output-stream content))

(defmethod send-content (Class/forName "[B") [output-stream content]
  (prot/send-binary-message output-stream content))

(defn send
  "Sends a collection of contents to the given target.
  The target may be a connection or its ouput."
  [target content & contents]
  (with-output-of target
    (doseq [content (cons content contents)]
      (send-content % content))))

(defn- pong-for? [ping-content message]
  (and (prot/pong? message) (= ping-content (prot/message-content message))))

(defn ping
  "Sends a ping message over the given connection and awaits the corresponding
  pong message.
  Returns a collection of [pong-message connection-with-remaining-messages]."
  [connection]
  (let [ping-content (with-output-of connection (prot/send-ping %))]
    (debug (format
             "Sent ping content: %s. Waiting for pong..."
             (print-str ping-content)))
    ; We don't use destructuring bind [pong-message & messages] here, because
    ; it is not lazy enough and might cause the calling thread to block until
    ; more messages become available.
    (let [messages (drop-while
                     (complement (partial pong-for? ping-content))
                     (:messages connection))
          pong-message (first messages)
          messages (rest messages)]
      (debug (format
               "Received pong content: %s."
               (print-str pong-message)))
      [pong-message (assoc connection :messages messages)])))

