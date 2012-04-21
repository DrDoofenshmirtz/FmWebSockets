(ns fm.websockets.server
  (:use
    [fm.core.exception :only (error? do-silently)]
    [fm.core.threading :only (do-async)]
    [fm.comm.server-connector :only (server-connector close!)]))

(defn- shut-down-connections [connections]
  (if-let [open-connections (seq (dosync
                                   (let [open-connections @connections]
                                     (ref-set connections nil)
                                     open-connections)))]
    (reduce 
      #(if (error? %1) %1 %2)
      (map #(.close %) open-connections))))

(defn- socket-server [port]
  {:connector (server-connector port) :connections (ref (hash-set))})

(defn- connection-seq [connector]
  (take-while identity (repeatedly connector)))

(defn- handle-connection [connections connection connection-handler]
  (when (dosync (alter connections #(and % (conj % connection))))
    (do-async
      [(connection-handler connection)
       (dosync (alter connections #(and % (disj % connection))))]
      :thread-name "fm.websockets.server.handle-connection")
    connection))

(defn- safe-connection-handler [connection-handler]
  (fn [connection]
    (let [result (do-silently (connection-handler connection))]
      (.close connection)
      result)))

(defn- connection-processor [connections connection-handler error-handler]
  (let [connection-handler (safe-connection-handler connection-handler)]
    (fn [connection]
      (if connection
        (if-not (error? connection)
          (handle-connection connections connection connection-handler)
          (error-handler connection))))))

(defn shut-down [socket-server]
  (if socket-server
    (let [{connector :connector connections :connections} socket-server]
      (close! connector)
      (shut-down-connections connections))))

(defn start-up [port connection-handler error-handler]
  (let [{connector :connector
         connections :connections
         :as server} (socket-server port)        
        connection-processor (connection-processor
                               connections
                               connection-handler
                               error-handler)]
    (do-async
      [(loop [connection-seq (connection-seq connector)]
        (if (connection-processor (first connection-seq))
          (recur (rest connection-seq))
          (shut-down server)))]
      :thread-name "fm.websockets.server.wait-for-clients")
    server))
