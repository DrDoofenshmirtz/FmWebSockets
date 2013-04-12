(ns
  ^{:doc "RPC support for HTML5 WebSocket connections."
    :author "Frank Mosebach"}
  fm.websockets.rpc.core
  (:require
    [clojure.contrib.logging :as log]
    [fm.websockets.connection :as conn]
    [fm.websockets.protocol :as prot]
    [fm.websockets.rpc.format :as fmt]
    [fm.websockets.rpc.request :as req]
    [fm.websockets.rpc.types :as types]))

(defn with-rpc-format [connection rpc-format]
  (assert connection)
  (assert rpc-format)
  (assoc connection ::rpc-format rpc-format))

(defn rpc-format [connection]
  (assert connection)
  (if-let [rpc-format (::rpc-format connection)]
    rpc-format
    (throw (IllegalStateException. "Connection does not have an rpc format!"))))

(defn message->request [connection message]
  (assert connection)
  (fmt/message->request (rpc-format connection) message))

(defn result->content [connection id result]
  (assert connection)
  (fmt/result->content (rpc-format connection) id result))

(defn error->content [connection id error]
  (assert connection)
  (fmt/error->content (rpc-format connection) id error))

(defn notification->content [connection name args]
  (assert connection)
  (fmt/notification->content (rpc-format connection) name args))

(defn send-result [connection id result]
  (if-let [content (result->content connection id result)]
    (conn/send connection content)))

(defn send-error [connection id error]
  (if-let [content (error->content connection id error)]
    (conn/send connection content)))

(defn send-notification [connection name & args]
  (if-let [content (notification->content connection name args)]
    (conn/send connection content)))

(defn- check-request-id [connection-id request-id]
  (if-not (.startsWith (str request-id) (str connection-id))
    (throw (IllegalArgumentException.
             (format "Illegal request id: '%s'!" request-id)))))

(defn- dispatch-request [connection request-handler request]
  (let [{:keys [id name]} request]
    (log/debug (format "Dispatch request {id: %s name: %s}..." id name))
    (check-request-id (:id connection) id)
    (let [stripped (conn/drop-messages connection)
          result   (try 
                     (request-handler connection request)
                     (log/debug "...done.")
                     (catch Throwable error
                       (when (conn/caused-by-closed-connection? error)
                         (throw error))
                       (log/fatal "Failed to dispatch request!" error)
                       (req/failure stripped error)))]      
      result)))

(defn- send-response [id result]
  (let [connection (types/connection result)]
    (log/debug "Send response...")
    (try 
      ((if (types/error? result) send-error send-result) connection id @result)
      (log/debug "...done.")
      (catch Throwable error
        (when (conn/caused-by-closed-connection? error)
          (throw error))
        (log/fatal "Failed to send response!" error)))    
    connection))

(defn- process-message [connection request-handler message]
  (if-let [{id :id :as request} (message->request connection message)]
    (let [result (dispatch-request connection request-handler request)]
      (send-response id result))
    (do
      (log/debug (format "Skipped message {opcode: %s}." (prot/opcode message)))
      connection)))

(defn message-handler [request-handler]
  (assert request-handler)
  (fn [connection message]
    (log/debug "Handle next message...")
    (let [connection (process-message connection request-handler message)]
      (log/debug "...done.")
      connection)))

(defn acknowledge-connection [{id :id :as connection}]  
  (log/debug (format "Acknowledge connection '%s'..." id))
  (send-notification connection "connectionAcknowledged" id)
  (log/debug "...done.")
  connection)

(defn connection-handler [rpc-format]
  (assert rpc-format)
  (fn [connection]
    (-> connection
        (with-rpc-format rpc-format)
        acknowledge-connection)))

