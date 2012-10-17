(ns
  ^{:doc "Utilities for processing the messages produced by a connection."
    :author "Frank Mosebach"}
  fm.websockets.messages
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
