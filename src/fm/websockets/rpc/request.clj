(ns
  ^{:doc 
  
  "Support for the construction of RPC request handlers and request routing."
    
    :author "Frank Mosebach"}
  fm.websockets.rpc.request
  (:require
    [clojure.contrib.logging :as log]
    [fm.core.hyphenate :as hy]
    [fm.websockets.rpc.types :as types]
    [fm.websockets.connection :as conn]))

(def ^{:dynamic true :private true} *connection* nil)

(defn- ensure-connection []
  (if (nil? *connection*)
    (throw (IllegalStateException. "No connection in current context!"))
    true))

(defn connection []
  (ensure-connection)
  *connection*)

(defn alter-connection! [func & args]
  (ensure-connection)
  (set! *connection* (apply func *connection* args)))

(defn result [connection value error?]
  (assert connection)
  (reify
    
    types/Result
    (connection [this]
      connection)
    (value [this]
      value)
    (error? [this]
      error?)    
    
    clojure.lang.IDeref
    (deref [this]
      value)))

(defn- value-type [connection value]
  (type value))

(defn- throwable->value [^Throwable throwable]
  {:error   (-> throwable class .getName)
   :message (.getMessage throwable)})

(defmulti success value-type)

(defmethod success Throwable [connection throwable]
  (success connection (throwable->value throwable)))

(defmethod success :default [connection value]
  (result connection value false))

(defmulti failure value-type)

(defmethod failure Throwable [connection throwable]
  (failure connection (throwable->value throwable)))

(defmethod failure :default [connection value]
  (result connection value true))

(defn call-procedure [connection request procedure]
  (let [{args :args} request]
    (binding [*connection* connection]
      (try
        (let [value (apply procedure args)]
          (success *connection* value))        
        (catch Throwable error
          (if (conn/caused-by-closed-connection? error)
            (throw error)
            (failure *connection* error)))))))

(defn- throw-undefined-procedure [{:keys [id name]}]
  (let [error-message (str "Undefined procedure for request {:id "
                           id 
                           " :name " 
                           name 
                           " ...}!")]
    (log/fatal error-message)
    (throw (IllegalStateException. error-message))))

(defn request-router [procedure-mapping]
  (assert procedure-mapping)
  (fn [connection request]
    (if-let [procedure (procedure-mapping connection request)]
      (call-procedure connection request procedure)
      (throw-undefined-procedure request))))

(defn map-router [route-map name-conversion]
  (assert route-map)
  (assert name-conversion)
  (request-router (fn [connection request]
                    (-> request 
                        :name 
                        name-conversion 
                        route-map 
                        (or (throw-undefined-procedure request))))))

(defn ns-router [ns-name]
  (require ns-name)
  (map-router (ns-interns ns-name) (comp symbol hy/hyphenate)))

