(ns
  ^{:doc "Utilities for creating connection handlers."
    :author "Frank Mosebach"}
  fm.websockets.connection-handlers
  (:use
    [fm.websockets.connection :only (take-message)]))

(defn- dispatch-messages [connection message-handler]
  (if (nil? connection)
    (throw (IllegalArgumentException. "Illegal connection: must not be nil!"))
    (let [[message connection] (take-message connection)]
      (if message
        (recur (message-handler connection message) message-handler)
        connection))))

(defn message-processor
  "Creates a connection handler that dipatches incoming messages to a
  message handler.
  A message handler is a function of two arguments that will be invoked
  with the connection as its first and the next incoming message as its
  second argument.
  The message handler is expected to return the (resulting state of) the
  connection."
  [message-handler]
  (if (nil? message-handler)
    (throw (IllegalArgumentException.
             "Illegal message handler: must not be nil!")))
  (fn [connection]
    (dispatch-messages connection message-handler)))
