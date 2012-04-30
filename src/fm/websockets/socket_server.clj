(ns
  ^{:doc "A simple generic socket server."
    :author "Frank Mosebach"}
  fm.websockets.socket-server
  (:use
    [clojure.contrib.def :only (defvar-)]
    [fm.core.lazy-seqs :only (take-until)]
    [fm.core.exception :only (do-silently close-silently error?)]
    [fm.core.threading :only (guarded-ref do-async)])
  (:import
    (java.net ServerSocket)))

(defvar- closed-tag (Object.))

(defn- close-if-socket [socket]
  (if-not (or (= closed-tag socket) (error? socket))
    (close-silently socket)))

(defn- create-socket! [socket-access port]
  (socket-access
    (fn [socket]
      (close-if-socket socket)
      (if (= closed-tag socket)
        [socket]
        (let [socket (do-silently (ServerSocket. port))]
          [socket socket])))))

(defn- server-socket-seq [socket-access port]
  (take-while identity (repeatedly #(create-socket! socket-access port))))

(defn- wait-for-client-socket [server-socket]
  (if-let [client-socket (do-silently (.accept server-socket))]
    (if-not (.isClosed server-socket)
      client-socket)))

(defn- client-socket-seq [server-socket]
  (if (error? server-socket)
    [server-socket]
    (take-while
      identity
      (take-until
        error?
        (repeatedly #(wait-for-client-socket server-socket))))))

(defn- connection-seq [server-sockets]
  (lazy-cat
    (when-first [server-socket server-sockets]
      (client-socket-seq server-socket))
    (if (seq server-sockets)
      (connection-seq (rest server-sockets)))))

(defn- connection-producer [port]
  (let [socket-access (guarded-ref)
        server-sockets (server-socket-seq socket-access port)]
    {:connections (connection-seq server-sockets)
     :socket-access socket-access}))

(defn- close-socket! [socket-access]
  (if-let [socket (socket-access #(vector closed-tag %))]
    (close-if-socket socket)))

(defn- stop-production! [connection-producer]
  (close-socket! (:socket-access connection-producer)))

(defn- close-connections! [connections]
  (if-let [open-connections (seq (dosync
                                   (let [open-connections @connections]
                                     (ref-set connections nil)
                                     open-connections)))]
    (reduce
      #(if (error? %1) %1 %2)
      (map #(close-silently %) open-connections))))

(defn- log-error [error]
  (binding [*out* *err*]
    (println error)))

(defn- safe-connection-handler [connection-handler]
  (fn [connection]
    (let [result (do-silently (connection-handler connection))
          closed? (.isClosed connection)]
      (close-silently connection)
      (when (error? result)
        (log-error "Unhandled error in fm.websockets.socket-server!")
        (log-error result)
        (if closed?
          (log-error "--- The connection has been closed! ---")))
      result)))

(defn- handle-connection [connection-handler connection connections]
  (when (dosync (alter connections #(and % (conj % connection))))
    (do-async
      [(connection-handler connection)
       (dosync (alter connections #(and % (disj % connection))))]
      :thread-name "fm.websockets.socket-server.handle-connection")
    connection))

(defn- connection-processor [connection-handler error-handler connections]
  (let [connection-handler (safe-connection-handler connection-handler)]
    (fn [connection]
      (if connection
        (if-not (error? connection)
          (handle-connection connection-handler connection connections)
          (error-handler connection))))))

(defn- handle-connection-error [error]
  (log-error "Connection error in fm.websockets.socket-server!")
  (log-error error))

(defn start-up
  ([port connection-handler]
    (start-up port connection-handler handle-connection-error))
  ([port connection-handler error-handler]
    (let [error-handler (or error-handler handle-connection-error)
          connection-producer (connection-producer port)
          connections (ref (hash-set))
          shut-down (fn []
                      (stop-production! connection-producer)
                      (close-connections! connections))
          connection-processor (connection-processor
                                 connection-handler
                                 error-handler
                                 connections)]
      (do-async
        [(loop [connections (:connections connection-producer)]
           (if (connection-processor (first connections))
             (recur (rest connections))
             (shut-down)))]
        :thread-name "fm.websockets.socket-server.wait-for-clients")
      shut-down)))

(defn shut-down [socket-server]
  (and socket-server (socket-server)))
