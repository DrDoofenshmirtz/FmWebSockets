(ns
  ^{:doc 
  
  "Support for the construction of RPC request handlers and request routing."
    
    :author "Frank Mosebach"}
  fm.websockets.rpc.request
  (:require
    [clojure.contrib.logging :as log]
    [fm.core.hyphenate :as hy]))

(def ^{:dynamic true :private true} *connection* nil)

(defn connection []
  (if (nil? *connection*)
    (throw (IllegalStateException. "No connection in current context!"))
    *connection*))

;; TODO: create result type.
(defn call-procedure [connection request procedure]
  (let [{args :args} request]
    (binding [*connection* connection]
      (apply procedure args))))

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
      (throw-undefined-procedure))))

;; TODO: make it work (wtf)!
(defn map-router [route-map name-conversion]
  (assert route-map)
  (assert name-conversion)
  (fn [connection request]
    (let [procedure-name (name-conversion method)]
      (if-let [procedure (dispatch-map procedure-name)]
        (apply procedure connection params)
        (let [error-message (format
                              "Undefined procedure: '%s'!"
                              procedure-name)]
          (log/fatal error-message)
          (throw (IllegalArgumentException. error-message)))))))

(defn ns-router [ns-name]
  (require ns-name)
  (map-router (ns-interns ns-name) (comp symbol hy/hyphenate)))

