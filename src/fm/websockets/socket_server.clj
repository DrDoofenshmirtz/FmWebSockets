(ns
  ^{:doc "A simple generic socket server."
    :author "Frank Mosebach"}
  fm.websockets.socket-server
  (:use
    [clojure.contrib.def :only (defvar-)]
    [fm.core.lazy-seqs :only (take-until)]
    [fm.core.exception :only (do-silently close-silently error?)])
  (:import
    (java.net ServerSocket)))

(defvar- closed-tag (Object.))

(defn- close-if-socket [socket]
  (if-not (or (= closed-tag socket) (error? socket))
    (close-silently socket)))

(defn- create-socket! [socket-access port]
  (socket-access
    (fn [current-socket]
      (let [socket @current-socket]
        (close-if-socket socket)
        (if (not= closed-tag socket)
           (let [socket (do-silently (ServerSocket. port))]
             (reset! current-socket socket)))))))

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
  (when-first [server-socket server-sockets]
    (lazy-cat
      (client-socket-seq server-socket)
      (connection-seq (rest server-sockets)))))

(defn- close-socket! [socket-access]
  (if-let [socket (socket-access
                    (fn [current-socket]
                      (let [socket @current-socket]
                        (reset! current-socket closed-tag)
                        socket)))]
    (close-if-socket socket)))

(defn- protected-access [mutable]
  (let [lock (Object.)]
    (fn [accessor & args]
      (locking lock
        (apply accessor mutable args)))))

(defn- connection-producer [port]
  (let [socket-access (protected-access (atom nil))
        server-sockets (server-socket-seq socket-access port)]
    {:connections (connection-seq server-sockets)
     :close! #(close-socket! socket-access)}))

(def cp (connection-producer 8080))

@(future
  (Thread/sleep 500)
  (println "closing...")
  (println ((:close! cp)))
  (println "closed!"))

(doseq [connection (:connections cp)]
  (println connection))
