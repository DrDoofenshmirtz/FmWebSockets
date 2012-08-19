(ns
  ^{:doc "Stuff for handling HTML5 WebSocket connections."
    :author "Frank Mosebach"}
  fm.websockets.connection
  (:refer-clojure :exclude [send])
  (:use
    [fm.core.threading :only (guarded-access with-guarded)]
    [fm.core.lazy-seqs :only (unsigned-byte-seq)]
    [fm.websockets.protocol :only (read-connect-request
                                   write-connect-response
                                   send-text-message
                                   send-binary-message
                                   message-seq)]))

(defn- make-output [output-stream]
  (vary-meta (guarded-access output-stream) assoc :type ::output))

(defn- make-connection [byte-seq output-stream]
  (vary-meta
    {:messages (message-seq byte-seq)
     :output (make-output output-stream)}
    assoc :type ::connection))

(defn connect
  "Tries to establish a WebSocket connection, assuming the given socket is
  connected to a WebSocket client.
  Returns a map {:messages lazy-seq-of-incoming-messages
                 :output   guarded-access-to-connection-output}."
  [socket]
  (let [input-stream (.getInputStream socket)
        output-stream (.getOutputStream socket)
        byte-seq (unsigned-byte-seq input-stream)
        [connect-request byte-seq] (read-connect-request byte-seq)]
    (write-connect-response output-stream connect-request)
    (make-connection byte-seq output-stream)))

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

(defmulti send-content
  "Sends content to an output stream using a send method suitable
  for the content type."
  {:private true}
  (fn [output-stream content] (type content)))

(defmethod send-content String [output-stream content]
  (send-text-message output-stream content))

(defmethod send-content (Class/forName "[B") [output-stream content]
  (send-binary-message output-stream content))

(defn send [target content & contents]
  "Sends a collection of contents to the given target."
  (with-guarded (output target)
    (doseq [content (cons content contents)]
      (send-content % content))))