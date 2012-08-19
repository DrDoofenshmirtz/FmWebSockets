(ns
  ^{:doc "A socket server for the HTML5 WebSockets protocol."
    :author "Frank Mosebach"}
  fm.websockets.server
  (:gen-class
    :name fm.websockets.Server
    :main true)
  (:require
    [fm.websockets.socket-server :as socket-server])
  (:use
    [clojure.contrib.command-line :only (with-command-line)]
    [fm.websockets.connection :only (connect)]))

(defn- wrap-connection-handler [connection-handler]
  (fn [socket]
    (connection-handler (connect socket))))

(defn start-up
  ([port connection-handler]
    (start-up port connection-handler nil))
  ([port connection-handler error-handler]
    (let [connection-handler (wrap-connection-handler connection-handler)]
      (socket-server/start-up port connection-handler error-handler))))

(defn -main [& args]
  (with-command-line args
    "WsServer"
    [[port "The WebSocket server's port number" 17500]
     [connection-handler "Fully qualified name of a connection handler function"]
     [error-handler "Fully qualified name of an error handler function"]]
    (println (str "Starting WsServer on port " port "..."))))
