(ns
  ^{:doc "RPC support for HTML5 WebSocket connections."
    :author "Frank Mosebach"}
  fm.websockets.rpc.core
  (:require
    [clojure.contrib.logging :as log]
    [fm.core.hyphenate :as hy]
    [fm.websockets.connection :as conn]
    [fm.websockets.protocol :as prot]
    [fm.websockets.rpc.format :as fmt]))

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

(defn send-object [connection object]
  (assert connection)
  (if-let [content (object->content connection object)]
    (conn/send connection content)))

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

(defn- result-type [connection & args]
  (assert connection)
  (type (first args)))
  
(defmulti result result-type)

(defmethod result ::result [connection & args]
  (first args))

(defmethod result Throwable [connection & args]
  (let [[throwable error?] args]
    (result
      connection
      {:error (-> throwable class .getName)
       :message (.getMessage throwable)}
      error?)))

(defmethod result nil [connection & args]
  (let [[return-value error?] args]
    (result connection (if error? false true) error?)))

(defn- make-result
  ([connection]
    (with-meta
      {:connection connection
       :error? false}
      {:type ::result}))
  ([connection return-value]
    (with-meta
      {:connection connection
       :return-value return-value
       :error? false}
      {:type ::result}))
  ([connection return-value error?]
    (with-meta
      {:connection connection
       :return-value return-value
       :error? (if error? true false)}
      {:type ::result})))

(defmethod result :default [connection & args]
  (let [[return-value error?] args]
    (make-result connection return-value error?)))

(defn- response [id result]
  (let [{:keys [return-value error?]} result
        response (if error?
                   {:result nil :error return-value}
                   {:result return-value :error nil})]
    (with-meta (assoc response :id id) {:type ::response})))

(defn- dispatch-request [connection request-handler request]
  (let [{:keys [id method]} request]
    (log/debug (format "Dispatch request {id: %s, method: %s}..." id method))
    (check-request-id (:id connection) id)
    (let [result (try
                   (result
                     connection
                     (request-handler connection request))
                   (catch Throwable error (result connection error true)))]
      (log/debug "...done.")
      result)))

(defn- send-response [connection response]
  (log/debug "Send response...")
  (send-object connection response)
  (log/debug "...done."))

(defn- process-message [connection request-handler message]
  (if-let [{id :id :as request} (message->request connection message)]
    (let [{connection :connection :as result} 
          (dispatch-request connection request-handler request)]
      (send-response connection (response id result))
      connection)
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

