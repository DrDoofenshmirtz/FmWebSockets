(ns
  ^{:doc "Stuff for handling HTML5 WebSocket connections."
    :author "Frank Mosebach"}
  fm.websockets.connection
  (:use
    [fm.core.threading :only (guarded-access)]
    [fm.core.lazy-seqs :only (unsigned-byte-seq)]
    [fm.websockets.protocol :only (read-connect-request
                                   write-connect-response
                                   message-seq-seq)]))

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
    {:messages (message-seq-seq byte-seq)
     :output (guarded-access output-stream)}))

(defn take-message
  "Takes the next message from the given connection's lazy message sequence.
  Returns a collection of [next-message connection-with-remaining-messages]."
  [connection]
  [(first (:messages connection))
   (assoc connection :messages (rest (:messages connection)))])
