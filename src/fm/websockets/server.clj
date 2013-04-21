(ns
  ^{:doc "A socket server for the HTML5 WebSockets protocol."
    :author "Frank Mosebach"}
  fm.websockets.server
  (:require
    [fm.websockets.socket-server :as sos]
    [clojure.contrib.logging :as log]
    [fm.websockets.connection :as conn]))

(defn- connect [socket]
  (try
    (conn/connect socket)
    (catch Exception x
      (log/error (format "Connection failed (remote address: %s)!"
                         (.getRemoteSocketAddress socket))
                 x)
      nil)))

(defn- wrap-connection-handler [connection-handler]
  (fn [socket]
    (if-let [connection (connect socket)]
      (try
        (connection-handler connection)
        (catch Throwable error
          (log/fatal "Unhandled error in connection handler!" error)
          nil)))))

(defn start-up
  ([port connection-handler]
    (start-up port connection-handler nil))
  ([port connection-handler error-handler]
    (let [connection-handler (wrap-connection-handler connection-handler)]
      (sos/start-up port connection-handler error-handler))))

