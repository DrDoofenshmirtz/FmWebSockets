(ns
  ^{:doc "A simple generic socket server."
    :author "Frank Mosebach"}
  fm.websockets.socket-server
  (:use
    [clojure.contrib.def :only (defvar-)]
    [fm.core.lazy-seqs :only (take-until)]
    [fm.core.exception :only (do-silently close-silently error?)]
    [fm.core.threading :only (guarded-ref)])
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

(def cp (connection-producer 8080))

(future
  (Thread/sleep 500)
  (println "Stopping production...")
  (println (stop-production! cp))
  (println "...stopped!"))

(doseq [connection (:connections cp)]
  (println connection))
