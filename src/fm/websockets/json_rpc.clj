(ns
  ^{:doc "JSON RPC layer for HTML5 WebSocket connections."
    :author "Frank Mosebach"}
  fm.websockets.json-rpc
  (:refer-clojure :exclude [send])
  (:use
    [clojure.contrib.logging :only (debug fatal)]
    [clojure.contrib.json :only (json-str read-json)]
    [fm.core.bytes :only (signed-byte)]
    [fm.core.hyphenate :only (hyphenate)]
    [fm.websockets.protocol :only (opcode text-message? message-content)]
    [fm.websockets.connection :only (send)]
    [fm.websockets.connection-handlers :only (message-processor)]))

(defn send-object [target object]
  (send target (json-str object)))

(defn send-result [target id result]
  (send-object target {:id id :result result :error nil}))

(defn send-error [target id error]
  (send-object target {:id id :result nil :error error}))

(defn send-notification [target method & params]
  (send-object target {:id nil :method method :params params}))

(defn- acknowledge-connection [{:keys [id] :as connection}]
  (send-notification connection "connectionAcknowledged" id)
  connection)

(defn- maybe-request? [message]
  (and message (text-message? message)))

(defn- try-read-request [message]
  (try
    (read-json (message-content message))
    (catch Exception _ nil)))

(defn- read-request [message]
  (if (maybe-request? message)
    (try-read-request message)))

(defn- check-request-id [connection-id request-id]
  (if-not (.startsWith (str request-id) (str connection-id))
    (throw (IllegalArgumentException.
             (format "Illegal request id: '%s'!" request-id)))))

(defn- result-type [connection & args]
  (if (nil? connection)
    (throw (IllegalArgumentException. "Illegal connection: nil!"))
    (type (first args))))

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

(defn- complete? [result]
  (contains? result :return-value))

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
  (let [{:keys [id method params]} request]
    (debug (format
             "Dispatch request {id: %s, method: %s, params: %s}..."
             id method params))
    (check-request-id (:id connection) id)
    (let [result (try
                   (result
                     connection
                     (request-handler connection method params))
                   (catch Throwable error (result connection error true)))]
      (debug (format "...done. Return value: %s." (:return-value result)))
      result)))

(defn- send-response [connection response]
  (debug "Send response...")
  (send-object connection response)
  (debug "...done."))

(defn- process-message [connection request-handler message]
  (if-let [{:keys [id] :as request} (read-request message)]
    (let [result (dispatch-request connection request-handler request)]
      (send-response connection (response id result))
      result)
    (do
      (debug (format "Skipped message {opcode: %s}." (opcode message)))
      (make-result connection))))

(defn- message-handler [request-handler]
  (fn [connection message]
    (debug "Handle next message...")
    (let [{connection :connection}
          (process-message connection request-handler message)]
      (debug "...done.")
      connection)))

(defn- process-messages [connection request-handler]
  ((message-processor (message-handler request-handler)) connection))

(defn connection-handler [request-handler]
  (fn [connection]
    (debug "JSON RPC connection established.")
    (debug (format "Request: %s" (:request connection)))
    (debug "Dispatching requests...")
    (let [connection (-> connection
                         acknowledge-connection
                         (process-messages request-handler))]
      (debug (format "JSON RPC connection closed: %s." connection))
      connection)))

(defn map-dispatcher [dispatch-map procedure-name-conversion]
  (fn [connection method params]
    (let [procedure-name (procedure-name-conversion method)]
      (if-let [procedure (dispatch-map procedure-name)]
        (apply procedure connection params)
        (let [error-message (format
                              "Undefined procedure: '%s'!"
                              procedure-name)]
          (fatal error-message)
          (throw (IllegalArgumentException. error-message)))))))

(defn ns-dispatcher [ns-name]
  (require ns-name)
  (map-dispatcher (ns-interns ns-name) #(symbol (hyphenate %))))
