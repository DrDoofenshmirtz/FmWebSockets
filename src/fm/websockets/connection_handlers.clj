(ns
  ^{:doc 

  "Functions for constructing connection handlers.

  A (valid) connection handler is a function that accepts a connection as 
  its single argument, 'does something' with the connection (consumes the 
  connection's messages, adds features to the connection, etc.), and finally 
  returns (the resulting state of) the connection"

    :author "Frank Mosebach"}
  fm.websockets.connection-handlers
  (:require
    [clojure.contrib.logging :as log]
    [fm.websockets.connection :as conn]))

(defn- dispatch-messages [connection message-handler]
  (if (nil? connection)
    (throw (IllegalArgumentException. "Illegal connection: must not be nil!"))
    (try
      (let [[message connection] (conn/take-message connection)]
        (if message
          (recur (message-handler connection message) message-handler)
          connection))
      (catch Exception x
        (if (conn/caused-by-closed-connection? x)
          (let [connection (conn/drop-messages connection)]
            (log/debug (format "WebSocket connection closed: %s." connection))
            connection)
          (throw x))))))

(defn message-processor
  "Creates a connection handler, i. e. a function of one argument accepting a
  connection, that dipatches the messages being produced by the connection to
  the given message handler.
  A message handler is a function of two arguments that will be invoked with
  the connection as its first and the next incoming message as its second
  argument.
  The message handler is expected to return the (resulting state of) the
  connection."
  [message-handler]
  (if (nil? message-handler)
    (throw (IllegalArgumentException.
             "Illegal message handler: must not be nil!")))
  (fn [connection]
    (dispatch-messages connection message-handler)))
