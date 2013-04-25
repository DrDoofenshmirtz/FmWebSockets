(ns
  ^{:doc 

  "Support for processing a HTML5 WebSocket connection's messages in a loop."

    :author "Frank Mosebach"}
  fm.websockets.message-loop
  (:require
    [clojure.contrib.logging :as log]
    [fm.websockets.connection :as conn]))

(defn- dispatch-messages [connection message-handler]
  (if (nil? connection)
    (throw (IllegalStateException. "Illegal connection: must not be nil!"))
    (try
      (let [[message connection] (conn/take-message connection)]
        (if message
          (recur (message-handler connection message) message-handler)
          connection))
      (catch Throwable error
        (if (conn/caused-by-closed-connection? error)
          (let [connection (conn/drop-messages connection)]
            (log/debug (format "WebSocket connection closed: %s." connection))
            connection)
          (throw error))))))

(defn connection-handler
  "Creates a connection handler that loops over the messages provided by a 
  connection, invoking the given message handler for each received message. 
  
  A connection handler is a function that accepts a connection as its single 
  argument and and yields a connection as return value.
  
  A message handler is a function that accepts a connection and a message as
  arguments and yields a connection as return value.
  
  An invocation of the returned connection handler will block until no more 
  messages are available."
  [message-handler]
  (assert (not (nil? message-handler)))
  (fn [connection]
    (dispatch-messages connection message-handler)))

