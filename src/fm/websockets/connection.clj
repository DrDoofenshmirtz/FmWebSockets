(ns
  ^{:doc "Stuff for handling HTML5 WebSocket connections."
    :author "Frank Mosebach"}
  fm.websockets.connection
  (:refer-clojure :exclude [send])
  (:use
    [clojure.contrib.logging :only (debug)]
    [fm.core.threading :only (guarded-access with-guarded)]
    [fm.core.lazy-seqs :only (unsigned-byte-seq)]
    [fm.websockets.protocol :only (read-connect-request
                                   write-connect-response
                                   send-text-message
                                   send-binary-message
                                   send-ping
                                   pong?
                                   message-content
                                   message-seq)]))

(defn- socket-streams [socket]
  (try
    [(.getInputStream socket) (.getOutputStream socket)]
    (catch Exception x
      (if-not (.isClosed socket)
        (throw x)))))

(defn- byte-seq [socket input-stream]
  (letfn [(read-bytes [byte-seq]
            (lazy-seq
              (try
                (if (seq byte-seq)
                  (cons
                    (first byte-seq)
                    (read-bytes (rest byte-seq))))
                (catch Exception x
                  (if-not (.isClosed socket)
                    (throw x))))))]
    (read-bytes (unsigned-byte-seq input-stream))))

(defn- socket-io [socket]
  (if-let [[input-stream output-stream] (socket-streams socket)]
    (if-let [byte-seq (byte-seq socket input-stream)]
      [byte-seq output-stream])))

(defn- output-accessor [socket accessor]
  (fn [& args]
    (try
      (apply accessor args)
      (catch Exception x
        (if-not (.isClosed socket)
          (throw x))))))

(defn- guarded-output [socket output-stream]
  (let [guarded-output (guarded-access output-stream)]
    (fn [accessor & args]
      (apply guarded-output (output-accessor socket accessor) args))))

(defn- make-output [socket output-stream]
  (vary-meta (guarded-output socket output-stream) assoc :type ::output))

(defn- make-connection [connect-request byte-seq socket output-stream]
  (vary-meta
    {:request connect-request
     :messages (message-seq byte-seq)
     :output (make-output socket output-stream)}
    assoc :type ::connection))

(defn connect
  "Tries to establish a WebSocket connection, assuming the given socket is
  connected to a WebSocket client.
  Returns a map {:request {:request-line connect-request-line
                           :request-headers connect-request-headers}
                 :messages lazy-seq-of-incoming-messages
                 :output   guarded-access-to-connection-output}
  if the connection has been successfully established, nil otherwise."
  [socket]
  (let [[byte-seq output-stream] (socket-io socket)
        [connect-request byte-seq] (read-connect-request byte-seq)]
    (if connect-request
      (do
        (debug (format
                 "Connecting to WebSocket client (remote address: %s)..."
                 (.getRemoteSocketAddress socket)))
        (debug (format "Request: %s" connect-request))
        (write-connect-response output-stream connect-request)
        (debug "Connected to WebSocket client.")
        (make-connection connect-request byte-seq socket output-stream))
      (do
        (debug (format
                 "Failed to connect to WebSocket client (remote address: %s)!"
                 (.getRemoteSocketAddress socket)))
        nil))))

(defn take-message
  "Takes the next message from the given connection's lazy message sequence.
  Returns a collection of [next-message connection-with-remaining-messages]."
  [connection]
  [(first (:messages connection))
   (assoc connection :messages (rest (:messages connection)))])

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

(defmulti send-content
  "Sends content to an output stream using a send method suitable
  for the content type."
  {:private true}
  (fn [output-stream content] (type content)))

(defmethod send-content String [output-stream content]
  (send-text-message output-stream content))

(defmethod send-content (Class/forName "[B") [output-stream content]
  (send-binary-message output-stream content))

(defn send
  "Sends a collection of contents to the given target.
  The target may be a connection or its ouput."
  [target content & contents]
  (with-guarded (output target)
    (doseq [content (cons content contents)]
      (send-content % content))))

(defn- next-pong-message [messages ping-content]
  (some
    (fn [pong-message]
      (and
        (= ping-content (message-content pong-message))
        pong-message))
    (filter pong? messages)))

(defn ping
  "Sends a ping message over the given connection.
  Awaits and returns the corresponding pong message."
  [connection]
  (with-guarded (output connection)
    (let [ping-content (send-ping %)]
      (debug (format
               "Sent ping content: %s. Waiting for pong..."
               (print-str ping-content)))
      (let [pong-message (next-pong-message
                           (:messages connection)
                           ping-content)]
        (debug (format
                 "Received pong content: %s."
                 (print-str pong-message)))
        pong-message))))
