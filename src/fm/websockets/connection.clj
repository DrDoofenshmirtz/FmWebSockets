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

(defmacro with-output
  "Evaluates the body expressions with the output stream of the given
  WebSocket connection bound to '%out'."
  [connection & body]
 `((:output ~connection) (fn [~'%out] ~@body)))
