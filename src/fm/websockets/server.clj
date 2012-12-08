(ns
  ^{:doc "A socket server for the HTML5 WebSockets protocol."
    :author "Frank Mosebach"}
  fm.websockets.server
  (:require
    [fm.websockets.socket-server :as socket-server])
  (:use
    [clojure.contrib.logging :only (debug error)]
    [fm.websockets.connection :only (connect)]))

(defn- create-connection [socket]
  (try
    (connect socket)
    (catch Exception x
      (error (format "Connection failed (remote address: %s)!"
                     (.getRemoteSocketAddress socket))
             x)
      nil)))

(defn- wrap-connection-handler [connection-handler]
  (fn [socket]
    (if-let [connection (connect socket)]
      (connection-handler connection))))

(defn start-up
  ([port connection-handler]
    (start-up port connection-handler nil))
  ([port connection-handler error-handler]
    (let [connection-handler (wrap-connection-handler connection-handler)]
      (socket-server/start-up port connection-handler error-handler))))
